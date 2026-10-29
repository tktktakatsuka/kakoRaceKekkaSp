package com.tktkcompany.kakoRaceKeiba.ui.favorite;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.tktkcompany.kakoRaceKeiba.ui.horse.HorseViews;
import com.tktkcompany.kakoRaceKeiba.ui.prediction.Bets;
import com.tktkcompany.kakoRaceKeiba.util.Lang;
import com.tktkcompany.kakoRaceKeiba.util.WeekendDays;

/**
 * 貢いだ金額と称号の見た目。カードは淡い面の上に置くので、夜のテーマでも昼の色（SpotlightCard と同じ）。
 */
public final class MitsugiViews {

    private static final int INK = 0xFF2F3B22;
    private static final int SOFT = 0xFF56664A;
    private static final int ACCENT = 0xFF4A7A1E;

    private MitsugiViews() {
    }

    /** 白い丸い札に「🌟 応援団長」（withLabel=false なら絵文字だけ）。恩返しなら 🎁 の札も並べる。0円なら null */
    @Nullable
    public static View badge(Context c, Mitsugi.Tally t, boolean withLabel) {
        Mitsugi.Title title = Mitsugi.title(t.spent);
        if (title == null) return null;
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(pill(c, withLabel ? title.emoji + " " + title.label() : title.emoji));
        if (Mitsugi.paidBack(t)) {
            View gift = pill(c, withLabel ? "🎁 " + Mitsugi.paidBackLabel() : "🎁");
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.leftMargin = HorseViews.dp(c, 4);
            row.addView(gift, p);
        }
        return row;
    }

    /** 白い半透明の角丸の帯に「貢いだ 23,000円／回収 8,400円」 */
    public static TextView moneyLine(Context c, Mitsugi.Tally t) {
        TextView tv = text(c, Mitsugi.moneyLabel(t), 12, INK, true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xB3FFFFFF);
        bg.setCornerRadius(HorseViews.dp(c, 10));
        tv.setBackground(bg);
        int ph = HorseViews.dp(c, 9), pv = HorseViews.dp(c, 4);
        tv.setPadding(ph, pv, ph, pv);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = HorseViews.dp(c, 6);
        tv.setLayoutParams(p);
        return tv;
    }

    /** 馬カルテ用。見出し、合計とバッジ、レースごとの行 */
    public static View breakdown(Context c, Mitsugi.Tally t) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(text(c, Lang.t("💰 貢いだ金額", "💰 Money spent"), 14, ACCENT, true));
        View badge = badge(c, t, true);
        if (badge != null) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = HorseViews.dp(c, 6);
            box.addView(badge, p);
        }
        box.addView(moneyLine(c, t));
        for (Bets.Bet b : t.bets) {
            String head = WeekendDays.formatCompact(b.date) + " " + Lang.race(b.course, b.raceNo) + "  " + b.label();
            String tail = b.payout == null ? Lang.t("結果待ち", "Pending") : "→ " + Lang.yen(b.payout);
            TextView line = text(c, head + "  " + tail, 12, b.payout != null && b.payout > 0 ? ACCENT : SOFT, b.payout != null && b.payout > 0);
            line.setPadding(0, HorseViews.dp(c, 4), 0, 0);
            box.addView(line);
        }
        return box;
    }

    private static TextView pill(Context c, String s) {
        TextView tv = text(c, s, 11, INK, true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(HorseViews.dp(c, 12));
        bg.setStroke(Math.max(1, HorseViews.dp(c, 1)), 0xFFDDE8CC);
        tv.setBackground(bg);
        tv.setGravity(Gravity.CENTER);
        int ph = HorseViews.dp(c, 7), pv = HorseViews.dp(c, 2);
        tv.setPadding(ph, pv, ph, pv);
        return tv;
    }

    private static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        return tv;
    }
}
