package com.tktkcompany.kakoRaceKeiba.ui.horse;

import android.content.Context;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

/** 馬を探す画面・馬カルテで使う、カードと文字の部品 */
public final class HorseViews {

    static final int GREEN = 0xFF4E7A27;
    static final int GRAY = 0xFF56664A;
    static final int TEXT = 0xFF2F3B22;

    private HorseViews() {
    }

    public static int dp(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    /** 角丸のカード。中身は縦に並べる LinearLayout（戻り値の getChildAt(0)） */
    static MaterialCardView card(Context context, int background) {
        MaterialCardView card = new MaterialCardView(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(context, 6), 0, dp(context, 6));
        card.setLayoutParams(params);
        card.setRadius(dp(context, 14));
        card.setCardElevation(dp(context, 2));
        card.setCardBackgroundColor(background);
        card.setStrokeWidth(0);
        LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 14);
        inner.setPadding(pad, pad, pad, pad);
        card.addView(inner);
        return card;
    }

    static LinearLayout inner(MaterialCardView card) {
        return (LinearLayout) card.getChildAt(0);
    }

    static TextView text(Context context, CharSequence text, float sizeSp, int color, boolean bold) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        return tv;
    }

    /** 見出し（カードの外に置く、緑の太字） */
    static TextView section(Context context, String title) {
        TextView tv = text(context, title, 15, GREEN, true);
        tv.setPadding(dp(context, 4), dp(context, 14), 0, dp(context, 2));
        return tv;
    }

    /**
     * 着順のメダル。1〜3着は金・銀・銅の丸、4着以下は薄い灰色の丸、取消などは灰色の小さな文字。
     * sizeDp は丸の直径
     */
    public static TextView rankBadge(Context context, String rank, float sizeDp) {
        TextView tv = new TextView(context);
        Integer r = HorseRecord.rank(rank);
        int size = dp(context, sizeDp);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        tv.setLayoutParams(params);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        if (r == null) {
            tv.setText(rank != null ? rank.trim() : "-");
            tv.setTextSize(sizeDp * 0.26f);
            tv.setTextColor(GRAY);
            circle.setColor(0xFFF8FBF2);
            circle.setStroke(Math.max(1, dp(context, 1)), 0xFFDDE8CC);
        } else {
            tv.setText(String.valueOf(r));
            tv.setTextSize(sizeDp * 0.4f);
            int[] medal = {0xFFD4A017, 0xFF9E9E9E, 0xFFB0703C};
            if (r <= 3) {
                circle.setColor(medal[r - 1]);
                tv.setTextColor(0xFFFFFFFF);
            } else {
                circle.setColor(0xFFF8FBF2);
                tv.setTextColor(0xFF455A64);
            }
        }
        tv.setBackground(circle);
        return tv;
    }

    /** 馬のアイコン（薄い緑の丸の中に馬の絵） */
    public static android.view.View avatar(Context context, float sizeDp) {
        android.widget.ImageView image = new android.widget.ImageView(context);
        image.setImageResource(com.tktkcompany.kakoRaceKeiba.R.drawable.uma);
        int size = dp(context, sizeDp);
        int pad = dp(context, sizeDp * 0.16f);
        image.setPadding(pad, pad, pad, pad);
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(0xFFF1F8E4);
        image.setBackground(circle);
        image.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return image;
    }

    /** 着順の色（1着赤・2着青・3着緑・それ以外は黒） */
    public static int rankColor(String rank) {
        Integer r = HorseRecord.rank(rank);
        if (r == null) return GRAY;
        if (r == 1) return 0xFF4A7A1E;
        if (r == 2) return 0xFF4A7A1E;
        if (r == 3) return GREEN;
        return TEXT;
    }
}
