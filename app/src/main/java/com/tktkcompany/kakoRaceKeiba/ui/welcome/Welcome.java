package com.tktkcompany.kakoRaceKeiba.ui.welcome;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/**
 * ウェルカム画面を出すかどうか。アプリを入れて初めて開いたときに1回だけ出す。
 * アップデートで入った人（前から使っている人）には出さない。
 */
public final class Welcome {

    private static final String PREFS = "welcome";
    private static final String KEY_DONE = "done";

    private Welcome() {
    }

    /** 入れた時刻と最後に更新した時刻が同じ＝アップデートではなく、初めて入れた */
    public static boolean shouldShow(boolean done, long firstInstallTime, long lastUpdateTime) {
        return !done && firstInstallTime == lastUpdateTime;
    }

    public static boolean shouldShow(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return shouldShow(isDone(context), info.firstInstallTime, info.lastUpdateTime);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isDone(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false);
    }

    public static void markDone(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply();
    }
}
