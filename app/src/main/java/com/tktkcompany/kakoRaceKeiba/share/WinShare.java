package com.tktkcompany.kakoRaceKeiba.share;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.tktkcompany.kakoRaceKeiba.util.Lang;
import com.tktkcompany.kakoRaceKeiba.util.WeekendDays;

/**
 * 推し馬が勝ったときのシェア。文章だけを、Android の共有メニュー（X・LINE など）に渡す。
 * 払い戻しや買い目は入れない（「いくら当たった」が広まると、馬券をあおる見え方になるため）。
 */
public final class WinShare {

    /** ストアのページ。シェアを見た人がすぐ入れられるように付ける */
    static final String STORE_URL = "https://play.google.com/store/apps/details?id=com.tktkcompany.kakoRaceKeiba";

    private WinShare() {
    }

    /** 1着なら true（"1"。速報や結果の着順の書き方に合わせる） */
    public static boolean isWin(@Nullable String rank) {
        return rank != null && "1".equals(rank.trim());
    }

    /**
     * 例:
     * 🎉 推し馬のイブキが1着！
     * 11/1(日) 東京8R 本栖湖特別
     * #イブキ #競馬 #推し馬
     * 推し馬の出走・結果をお知らせしてくれるアプリ👇
     * https://play.google.com/...
     */
    public static String text(String horseName, String date, String course, int raceNo, @Nullable String raceTitle) {
        String name = displayName(horseName);
        StringBuilder sb = new StringBuilder();
        sb.append(Lang.t("🎉 推し馬の" + name + "が1着！", "🎉 My horse " + name + " won!")).append('\n');
        sb.append(WeekendDays.formatShort(date)).append(' ').append(Lang.race(course, raceNo));
        String title = raceTitle != null ? raceTitle.trim().replaceFirst("^サラ系", "").trim() : "";
        if (!title.isEmpty()) sb.append(' ').append(title);
        sb.append('\n');
        sb.append('#').append(name.replaceAll("\\s+", "")).append(Lang.t(" #競馬 #推し馬", " #keiba #horseracing")).append('\n');
        sb.append(Lang.t("推し馬の出走・結果をお知らせしてくれるアプリ👇", "The app that tells me when my horses run👇")).append('\n');
        sb.append(STORE_URL);
        return sb.toString();
    }

    /** 共有メニューを開く Intent（通知のボタンからも使えるよう、画面を開く前の形で返す） */
    public static Intent chooser(String text) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        return Intent.createChooser(send, Lang.t("勝利をシェア", "Share the win"));
    }

    /** 画面から共有メニューを開く */
    public static void open(Context context, String text) {
        context.startActivity(chooser(text));
    }

    /** 「(外)フロムダスク」→「フロムダスク」 */
    static String displayName(String name) {
        return name == null ? "" : name.trim().replaceFirst("^[\\(（\\[［][^\\)）\\]］]{1,2}[\\)）\\]］]\\s*", "");
    }
}
