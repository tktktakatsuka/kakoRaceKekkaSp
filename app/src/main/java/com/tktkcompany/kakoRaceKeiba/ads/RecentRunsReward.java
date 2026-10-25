package com.tktkcompany.kakoRaceKeiba.ads;

import com.tktkcompany.kakoRaceKeiba.util.Lang;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import org.threeten.bp.LocalDate;
import org.threeten.bp.format.DateTimeFormatter;

/**
 * 出走表の近走を10走まで広げるリワード広告。
 * 広告を最後まで見たら、その日の終わりまで10走を表示する（何度も見せないように）。
 */
public final class RecentRunsReward {

    /** ふだんの近走の数と、広告を見たあとの数 */
    public static final int NORMAL_RUNS = 3;
    public static final int REWARDED_RUNS = 10;

    private static final String PREFS = "rewards";
    private static final String KEY_UNLOCKED_DATE = "recent_runs_unlocked_date";
    // 読み込んだ広告を使える時間。古いものは捨てて読み直す
    private static final long AD_LIFETIME_MS = 60 * 60 * 1000L;
    private static final String TAG = "RecentRunsReward";

    public interface Listener {
        /** 広告を見終わって特典を受け取った */
        void onRewarded();

        /** 広告を出せなかった（読み込み失敗・途中で閉じた） */
        void onNotShown(String message);
    }

    // 読み込んだ広告はアプリ全体で1つだけ持つ（画面を閉じても捨てず、次に開いたときに使う）
    @Nullable private static RewardedAd ad;
    private static boolean loading;
    private static long loadedAt;

    /** 今日すでに広告を見ていれば（広告なしのアカウントならいつでも） true */
    public static boolean isUnlocked(Context context) {
        if (AdFree.isOn(context)) return true;
        String date = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_UNLOCKED_DATE, null);
        return today().equals(date);
    }

    /** 表示する近走の数 */
    public static int runs(Context context) {
        return isUnlocked(context) ? REWARDED_RUNS : NORMAL_RUNS;
    }

    private static void unlockForToday(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_UNLOCKED_DATE, today()).apply();
    }

    private static String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    /** ボタンを押してから待たせないよう、ボタンを出すときに先に読み込んでおく */
    public void preload(Context context) {
        dropStale();
        if (ad != null || loading || isUnlocked(context)) return;
        loading = true;
        RewardedAd.load(context.getApplicationContext(), AdUnits.REWARDED_RECENT_RUNS, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd loaded) {
                loading = false;
                ad = loaded;
                loadedAt = SystemClock.elapsedRealtime();
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                loading = false;
                Log.w(TAG, "リワード広告の読み込みに失敗: " + error.getMessage());
            }
        });
    }

    private static void dropStale() {
        if (ad != null && SystemClock.elapsedRealtime() - loadedAt > AD_LIFETIME_MS) ad = null;
    }

    /** 広告を出す。最後まで見たら今日の分を解放して onRewarded を呼ぶ */
    public void show(Activity activity, Listener listener) {
        dropStale();
        RewardedAd current = ad;
        if (current == null) {
            listener.onNotShown(loading ? Lang.t("広告を読み込み中です。少し待ってからもう一度押してください", "The ad is loading. Please tap again in a moment")
                    : Lang.t("今は広告を表示できません。時間をおいてもう一度お試しください", "No ad is available right now. Please try again later"));
            preload(activity);
            return;
        }
        ad = null;
        boolean[] rewarded = {false};
        current.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError error) {
                listener.onNotShown(Lang.t("今は広告を表示できません。時間をおいてもう一度お試しください", "No ad is available right now. Please try again later"));
                preload(activity);
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                if (rewarded[0]) {
                    listener.onRewarded();
                } else {
                    listener.onNotShown(Lang.t("広告を最後まで見ると、近走を" + REWARDED_RUNS + "走まで表示できます",
                            "Watch the whole ad to see the last " + REWARDED_RUNS + " runs"));
                    preload(activity);
                }
            }
        });
        current.show(activity, rewardItem -> {
            rewarded[0] = true;
            unlockForToday(activity);
        });
    }
}
