package com.tktkcompany.kakoRaceKeiba.ui.favorite;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.tktkcompany.kakoRaceKeiba.db.LivePayouts;
import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;
import com.tktkcompany.kakoRaceKeiba.ui.prediction.Bets;

import org.threeten.bp.LocalDate;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 買い目（Firebase）と出走馬（Supabase）を読んで、推し馬ごとの貢いだ金額を Mitsugi で数える。
 * まだ払戻の入っていない過去のレースは、払戻を取って Bets.settle で書き戻す（そのレースの画面を開いていなくても回収に入るように）。
 * 読んだ買い目と出走馬は、アプリが動いている間だけ覚えておく。買い目を足す・消すと invalidate で忘れる。
 */
public final class MitsugiLoader {

    private static final String TAG = "MitsugiLoader";

    public interface Callback {
        /** キーは FavoriteHorseNames.normalize 済みの推し馬の名前。失敗したときは空 */
        void onLoaded(Map<String, Mitsugi.Tally> byFavorite);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** レースごとの出走馬。過去のレースは変わらないので、ずっと覚えておく */
    private static final Map<String, List<Mitsugi.Runner>> RUNNERS = Collections.synchronizedMap(new HashMap<>());
    @Nullable private static volatile List<Bets.Bet> cachedBets;

    private MitsugiLoader() {
    }

    public static void invalidate() {
        cachedBets = null;
    }

    public static void load(Set<String> favoriteKeys, Callback cb) {
        if (favoriteKeys.isEmpty()) {
            cb.onLoaded(Collections.emptyMap());
            return;
        }
        List<Bets.Bet> bets = cachedBets;
        if (bets != null) {
            count(bets, favoriteKeys, cb);
            return;
        }
        Bets.loadAll(loaded -> count(loaded, favoriteKeys, cb));
    }

    private static void count(List<Bets.Bet> loaded, Set<String> favoriteKeys, Callback cb) {
        EXECUTOR.execute(() -> {
            Map<String, Mitsugi.Tally> result;
            try {
                List<Bets.Bet> bets = settlePast(loaded);
                cachedBets = bets;
                result = Mitsugi.tally(bets, runners(bets), favoriteKeys);
            } catch (Exception e) {
                Log.w(TAG, "load failed", e);
                result = Collections.emptyMap();
            }
            Map<String, Mitsugi.Tally> done = result;
            MAIN.post(() -> cb.onLoaded(done));
        });
    }

    /** 過去のレースで払戻の入っていない買い目に、払戻を入れる（取れなかったレースはそのまま） */
    private static List<Bets.Bet> settlePast(List<Bets.Bet> bets) {
        List<String> keys = Mitsugi.racesToSettle(bets, today());
        if (keys.isEmpty()) return bets;
        List<Bets.Bet> out = new ArrayList<>(bets);
        for (String key : keys) {
            List<Bets.Bet> race = new ArrayList<>();
            for (Bets.Bet b : out) {
                if (Mitsugi.raceKey(b.date, b.course, b.raceNo).equals(key)) race.add(b);
            }
            Bets.Bet first = race.get(0);
            try {
                List<LivePayouts.Payout> payouts = LivePayouts.fetchBlocking(first.date, first.course, first.raceNo);
                if (payouts.isEmpty()) continue;
                List<Bets.Bet> settled = Bets.settle(race, payouts);
                out.removeAll(race);
                out.addAll(settled);
            } catch (Exception e) {
                Log.w(TAG, "payout " + key, e);
            }
        }
        return out;
    }

    /** 買い目のあるレースの出走馬。出馬表が無ければ結果から作る。取れなかったレースは入れない */
    private static Map<String, List<Mitsugi.Runner>> runners(List<Bets.Bet> bets) {
        Map<String, List<Mitsugi.Runner>> map = new HashMap<>();
        for (Bets.Bet b : bets) {
            String key = Mitsugi.raceKey(b.date, b.course, b.raceNo);
            if (map.containsKey(key)) continue;
            List<Mitsugi.Runner> runners = RUNNERS.get(key);
            if (runners == null) {
                try {
                    runners = fetchRunners(b.date, b.course, b.raceNo);
                } catch (Exception e) {
                    Log.w(TAG, "runners " + key, e);
                    continue;
                }
                // 結果が出たあとのレースだけ覚える（出走取消などで変わることがあるため）
                if (!runners.isEmpty() && b.date.compareTo(today()) < 0) RUNNERS.put(key, runners);
            }
            if (!runners.isEmpty()) map.put(key, runners);
        }
        return map;
    }

    private static List<Mitsugi.Runner> fetchRunners(String date, String course, int raceNo) throws Exception {
        List<Mitsugi.Runner> list = new ArrayList<>();
        for (SupabaseManager.RaceEntry e : SupabaseManager.INSTANCE.fetchRaceEntriesBlocking(date, course, raceNo)) {
            String number = strip(e.getHorseNumber());
            if (number != null) list.add(new Mitsugi.Runner(number, strip(e.getWaku()), e.getHorseName()));
        }
        if (!list.isEmpty()) return list;
        for (SupabaseManager.RaceResult r : SupabaseManager.INSTANCE.fetchRaceResultBlocking(date, course, raceNo)) {
            String number = strip(r.getHorseNumber());
            if (number != null && r.getHorseName() != null) list.add(new Mitsugi.Runner(number, strip(r.getWaku()), r.getHorseName()));
        }
        return list;
    }

    /** 「05」→「5」。数字でなければ null */
    @Nullable
    private static String strip(@Nullable String number) {
        if (number == null) return null;
        try {
            return String.valueOf(Integer.parseInt(number.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
}
