package com.tktkcompany.kakoRaceKeiba.notification;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.tktkcompany.kakoRaceKeiba.MainActivity;
import com.tktkcompany.kakoRaceKeiba.db.FavoriteResultFetcher;
import com.tktkcompany.kakoRaceKeiba.db.LiveResults;
import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;
import com.tktkcompany.kakoRaceKeiba.share.WinShare;
import com.tktkcompany.kakoRaceKeiba.ui.entries.FavoriteHorseNames;
import com.tktkcompany.kakoRaceKeiba.util.WeekendDays;

import org.threeten.bp.Duration;
import org.threeten.bp.LocalDate;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZoneId;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * お気に入り馬が出たレースの結果を、確定したらすぐ知らせる（夜のまとめの通知を待たずに）。
 * 発走の少しあとに動き、まだ確定していなければ間をあけて確かめ直す。
 */
public class RaceResultWorker extends Worker {

    private static final String TAG = "RaceResultWorker";
    private static final String PREFS = "race_result_worker";
    private static final String KEY_NOTIFIED = "notified_";
    private static final String ARG_DATE = "date";
    private static final String ARG_COURSE = "course";
    private static final String ARG_RACE_NO = "raceNo";
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    // 発走から確定までおよそ10分。少し余裕をみて最初に確かめる
    private static final int FIRST_CHECK_MINUTES = 12;
    // 予約のとき、発走からこの時間を過ぎていたら速報はあきらめる（夜のまとめの通知に任せる）
    private static final int GIVE_UP_MINUTES = 120;
    private static final int MAX_RETRIES = 6;

    public RaceResultWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedule(Context context, SupabaseManager.RaceEntry race) {
        LocalDateTime start = RaceStartNotices.startTime(race.getKaisaibi(), race.getRaceInfo());
        if (start == null) return;
        LocalDateTime now = LocalDateTime.now(JST);
        if (now.isAfter(start.plusMinutes(GIVE_UP_MINUTES))) return;
        long delayMillis = Math.max(0, Duration.between(now, start.plusMinutes(FIRST_CHECK_MINUTES)).toMillis());

        Data input = new Data.Builder()
                .putString(ARG_DATE, race.getKaisaibi())
                .putString(ARG_COURSE, race.getKaisaijo())
                .putInt(ARG_RACE_NO, race.getRaceNo())
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(RaceResultWorker.class)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .setInputData(input)
                .build();
        String name = "race_result|" + race.getKaisaibi() + "|" + race.getKaisaijo() + "|" + race.getRaceNo();
        // 確定待ちで送り直している最中に予約し直すと待ち時間が延びるので、予約済みならそのままにする
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request);
    }

    /** 速報で知らせた馬か（夜のまとめの通知で同じ結果を2回知らせないため） */
    public static boolean alreadyNotified(Context context, String uid, String key) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_NOTIFIED + uid, new HashSet<>()).contains(key);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return Result.success();
        Context context = getApplicationContext();
        if (!NotificationHelper.canPostNotifications(context)) return Result.success();

        String date = getInputData().getString(ARG_DATE);
        String course = getInputData().getString(ARG_COURSE);
        int raceNo = getInputData().getInt(ARG_RACE_NO, 0);
        if (date == null || course == null) return Result.success();

        try {
            Set<String> favorites = new HashSet<>();
            for (String key : FavoriteResultFetcher.fetchFavoriteHorseKeys(user.getUid())) {
                favorites.add(FavoriteHorseNames.normalize(key));
            }
            if (favorites.isEmpty()) return Result.success();

            List<SupabaseManager.RaceResult> rows = LiveResults.fetchRaceBlocking(date, course, raceNo);
            if (rows.isEmpty()) return stillWaiting();

            List<LiveResultNotices.Finish> finishes = new ArrayList<>();
            String raceTitle = null;
            for (SupabaseManager.RaceResult r : rows) {
                raceTitle = r.getRaceTitle();
                if (r.getHorseName() == null || !favorites.contains(FavoriteHorseNames.normalize(r.getHorseName()))) continue;
                finishes.add(new LiveResultNotices.Finish(r.getHorseName(), r.getRank(), r.getPopular(), r.getWinOdds(), r.getTime()));
            }
            if (finishes.isEmpty()) return Result.success();

            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String prefKey = KEY_NOTIFIED + user.getUid();
            String recent = LocalDate.now(JST).minusDays(7).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            // 1週間より前の記録は消す（夜のまとめの通知が済んだあとは要らない）
            Set<String> kept = new HashSet<>();
            for (String key : prefs.getStringSet(prefKey, new HashSet<>())) {
                if (key.compareTo(recent) >= 0) kept.add(key);
            }
            List<LiveResultNotices.Finish> fresh = new ArrayList<>();
            for (LiveResultNotices.Finish f : finishes) {
                if (kept.add(LiveResultNotices.key(date, course, raceNo, f.horseName))) fresh.add(f);
            }
            if (fresh.isEmpty()) return Result.success();

            // タップしたら、その馬（何頭かいれば先頭の馬）のメモを書く画面を開く
            LiveResultNotices.Finish first = fresh.get(0);
            String title = raceTitle != null ? raceTitle.trim() : "";
            Bundle extras = new Bundle();
            extras.putString(MainActivity.EXTRA_MEMO_HORSE, first.horseName);
            extras.putString(MainActivity.EXTRA_MEMO_FROM, (WeekendDays.formatShort(date) + " " + course + raceNo + "R "
                    + title + " " + LiveResultNotices.rankLabel(first.rank)).replaceAll("\\s+", " ").trim());

            // 推し馬が勝っていたら、通知に「🎉 シェア」ボタンを付ける
            String shareText = null;
            for (LiveResultNotices.Finish f : fresh) {
                if (WinShare.isWin(f.rank)) {
                    shareText = WinShare.text(f.horseName, date, course, raceNo, raceTitle);
                    break;
                }
            }
            NotificationHelper.show(context, NotificationHelper.LIVE_RESULT_CHANNEL_ID,
                    ("result" + date + course + raceNo).hashCode(),
                    LiveResultNotices.title(course, raceNo, fresh),
                    LiveResultNotices.body(date, course, raceNo, raceTitle, fresh),
                    extras, shareText);
            prefs.edit().putStringSet(prefKey, kept).apply();
            return Result.success();
        } catch (Exception e) {
            Log.w(TAG, "結果の速報の確認に失敗", e);
            return stillWaiting();
        }
    }

    /**
     * まだ確定していない。間をあけて確かめ直す。
     * 待ち時間は 5, 10, 15 … 分と延びるので、6回（合わせて約1時間45分）であきらめて夜のまとめの通知に任せる
     */
    private Result stillWaiting() {
        return getRunAttemptCount() < MAX_RETRIES ? Result.retry() : Result.success();
    }
}
