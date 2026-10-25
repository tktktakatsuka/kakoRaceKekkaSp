package com.tktkcompany.kakoRaceKeiba.ads;

import android.app.Activity;
import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

/**
 * レース結果を何回か見て戻るときに出す全画面広告。
 * 操作の途中や起動直後には出さず、間隔と1回の起動あたりの回数も絞る。
 * 読み込みは「あと1回で出せる」ところまで来てから行う（出さずに捨てる広告を減らすため）。
 */
public final class InterstitialPacer {

    /** レース結果を何回見たら出すか */
    private static final int RESULTS_PER_AD = 3;
    /** 前に出してから、次に出せるまでの時間 */
    private static final long MIN_INTERVAL_MS = 3 * 60 * 1000L;
    /** 起動してから、出せるようになるまでの時間 */
    private static final long STARTUP_GRACE_MS = 60 * 1000L;
    /** 1回の起動（プロセス）あたりに出す上限 */
    private static final int MAX_PER_SESSION = 3;
    /** 読み込んだ広告を使える時間。古いものは捨てて読み直す */
    private static final long AD_LIFETIME_MS = 60 * 60 * 1000L;
    /** 戻る動きが終わってから出す */
    private static final long SHOW_DELAY_MS = 300L;

    private static final String TAG = "InterstitialPacer";

    @Nullable private static InterstitialAd ad;
    private static long loadedAt;
    private static boolean loading;
    private static boolean showing;
    private static int resultsSinceLastAd;
    private static int shownThisSession;
    private static long lastShownAt = -MIN_INTERVAL_MS;

    private InterstitialPacer() {
    }

    /** レース結果を開いたとき（新しく開いたときだけ。回転などで作り直したときは呼ばない） */
    public static void onRaceResultOpened(Context context) {
        resultsSinceLastAd++;
        if (resultsSinceLastAd >= RESULTS_PER_AD - 1 && withinLimits(context)) preload(context);
    }

    /** レース結果を閉じたとき（戻る・タブの切り替え）。条件がそろっていれば広告を出す */
    public static void onRaceResultClosed(Activity activity) {
        if (resultsSinceLastAd < RESULTS_PER_AD || !withinLimits(activity) || !hasFreshAd()) return;
        activity.getWindow().getDecorView().postDelayed(() -> show(activity), SHOW_DELAY_MS);
    }

    /** 起動直後・間隔・回数の制限の内側にいるか */
    private static boolean withinLimits(Context context) {
        if (AdFree.isOn(context)) return false;
        if (shownThisSession >= MAX_PER_SESSION) return false;
        long now = SystemClock.elapsedRealtime();
        if (now - Process.getStartElapsedRealtime() < STARTUP_GRACE_MS) return false;
        return now - lastShownAt >= MIN_INTERVAL_MS;
    }

    private static boolean hasFreshAd() {
        if (ad != null && SystemClock.elapsedRealtime() - loadedAt > AD_LIFETIME_MS) ad = null;
        return ad != null;
    }

    private static void preload(Context context) {
        if (loading || hasFreshAd()) return;
        loading = true;
        InterstitialAd.load(context.getApplicationContext(), AdUnits.INTERSTITIAL, new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd loaded) {
                        loading = false;
                        ad = loaded;
                        loadedAt = SystemClock.elapsedRealtime();
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        // 次にレース結果を開いたとき、もう一度読み込む
                        loading = false;
                        Log.w(TAG, "インタースティシャル広告の読み込みに失敗: " + error.getMessage());
                    }
                });
    }

    private static void show(Activity activity) {
        InterstitialAd current = ad;
        if (current == null || showing || activity.isFinishing() || activity.isDestroyed()) return;
        // 待っている間にアプリを離れていたら出さない
        if (activity instanceof LifecycleOwner
                && !((LifecycleOwner) activity).getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            return;
        }
        ad = null;
        showing = true;
        current.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                shownThisSession++;
                lastShownAt = SystemClock.elapsedRealtime();
                resultsSinceLastAd = 0;
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                showing = false;
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                showing = false;
                Log.w(TAG, "インタースティシャル広告を表示できませんでした: " + error.getMessage());
            }
        });
        current.show(activity);
    }
}
