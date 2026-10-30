package com.tktkcompany.kakoRaceKeiba.ui.profile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.google.android.material.card.MaterialCardView;
import com.tktkcompany.kakoRaceKeiba.ui.entries.FavoriteHorseNames;
import com.tktkcompany.kakoRaceKeiba.ui.favorite.CardStyles;
import com.tktkcompany.kakoRaceKeiba.ui.favorite.Mitsugi;
import com.tktkcompany.kakoRaceKeiba.ui.favorite.MitsugiViews;
import com.tktkcompany.kakoRaceKeiba.ui.horse.HorseViews;
import com.tktkcompany.kakoRaceKeiba.util.Lang;

import java.util.Map;

/**
 * プロフ帳の1枚（画面に出すものと、シェアの画像は同じもの）。
 * 淡い黄緑の面なので、夜のテーマでも昼の色の文字にする。絵柄は推し馬ノートのオリジナル。
 */
final class ProfileSheet {

    static final int INK = 0xFF2F3B22;
    static final int SOFT = 0xFF56664A;
    static final int ACCENT = 0xFF4A7A1E;

    private ProfileSheet() {
    }

    /** withBrand=true なら、上に「推し馬ノートのプロフ帳」、下に「推し馬ノートでつくったよ」（シェアの画像用） */
    static View build(Context c, Profile p, @Nullable Bitmap photo, Map<String, Mitsugi.Tally> mitsugi, boolean withBrand) {
        LinearLayout sheet = new LinearLayout(c);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFF8FBF2, 0xFFDCEDC8});
        bg.setCornerRadius(HorseViews.dp(c, withBrand ? 0 : 22));
        sheet.setBackground(bg);
        int pad = HorseViews.dp(c, 16);
        sheet.setPadding(pad, pad, pad, pad);

        if (withBrand) {
            LinearLayout top = row(c);
            TextView brand = text(c, Lang.t("🐴 推し馬ノートのプロフ帳", "🐴 My Oshiuma Note profile"), 13, INK, true);
            top.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            top.addView(text(c, "#推し馬ノート", 11, SOFT, false));
            sheet.addView(top);
        }

        // 名前とアイコン
        LinearLayout head = row(c);
        head.setPadding(0, HorseViews.dp(c, withBrand ? 10 : 0), 0, HorseViews.dp(c, 10));
        head.addView(avatar(c, p, photo, 64));
        LinearLayout who = new LinearLayout(c);
        who.setOrientation(LinearLayout.VERTICAL);
        who.setPadding(HorseViews.dp(c, 12), 0, 0, 0);
        who.addView(text(c, p.name.isEmpty() ? Lang.t("（名前はまだ）", "(No name yet)") : p.name, 22, INK, true));
        StringBuilder sub = new StringBuilder();
        if (!p.career.isEmpty()) sub.append(Lang.t("競馬歴 ", "Fan for ")).append(p.career).append("　");
        sub.append(Lang.t("日常 ", "Daily life ")).append(tendencyBar(p.tendency)).append(Lang.t(" 馬", " Horses"));
        who.addView(text(c, sub, 12, SOFT, false));
        head.addView(who, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        sheet.addView(head);

        // 現役の推し 1・2・3
        sheet.addView(label(c, Lang.t("現役の推し 1・2・3", "My top 3 horses")));
        LinearLayout oshi = row(c);
        oshi.setGravity(Gravity.TOP);
        if (p.oshi.isEmpty()) {
            oshi.addView(text(c, Lang.t("推し馬を登録すると、ここに出ます", "Your favorite horses appear here"), 12, SOFT, false));
        }
        for (int i = 0; i < p.oshi.size(); i++) {
            String name = p.oshi.get(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) lp.leftMargin = HorseViews.dp(c, 6);
            oshi.addView(oshiCard(c, i + 1, name, mitsugi.get(FavoriteHorseNames.normalize(name))), lp);
        }
        sheet.addView(oshi);

        // 自分で書いたこと（書いたものだけ）
        String[][] items = {
                {Lang.t("推し騎手・厩舎など", "Favorite jockeys & stables"), p.jockeys},
                {Lang.t("永遠のラブ", "Forever love"), p.love},
                {Lang.t("心に残っているレース", "A race I'll never forget"), p.race},
                {Lang.t("ひとつ願いが叶うなら", "If one wish came true"), p.wish},
                {Lang.t("ひとこと", "A few words"), p.note},
        };
        for (String[] item : items) {
            if (item[1].isEmpty()) continue;
            sheet.addView(cell(c, item[0], item[1]));
        }

        if (withBrand) {
            TextView foot = text(c, Lang.t("推し馬ノートでつくったよ", "Made with Oshiuma Note"), 11, SOFT, false);
            foot.setGravity(Gravity.END);
            foot.setPadding(0, HorseViews.dp(c, 10), 0, 0);
            sheet.addView(foot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return sheet;
    }

    /** 写真があれば丸い写真、なければ絵文字 */
    static View avatar(Context c, Profile p, @Nullable Bitmap photo, int sizeDp) {
        if (p.usePhoto && photo != null) {
            ImageView image = new ImageView(c);
            RoundedBitmapDrawable round = RoundedBitmapDrawableFactory.create(c.getResources(), photo);
            round.setCircular(true);
            image.setImageDrawable(round);
            int size = HorseViews.dp(c, sizeDp);
            image.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            return image;
        }
        return CardStyles.emojiAvatar(c, p.emoji != null ? p.emoji : "🐴", sizeDp);
    }

    /** 「━━●━━」 */
    static String tendencyBar(int tendency) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= Profile.TENDENCY_MAX; i++) sb.append(i == Profile.tendency(tendency) ? "●" : "━");
        return sb.toString();
    }

    private static View oshiCard(Context c, int no, String name, @Nullable Mitsugi.Tally tally) {
        MaterialCardView card = new MaterialCardView(c);
        card.setRadius(HorseViews.dp(c, 16));
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        LinearLayout inner = new LinearLayout(c);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = HorseViews.dp(c, 8);
        inner.setPadding(pad, pad, pad, pad);
        card.addView(inner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        CardStyles.applyBackground(card, name, 0xFFFFFFFF);
        inner.addView(text(c, String.valueOf(no), 11, SOFT, true));
        inner.addView(CardStyles.avatar(c, name, 40));
        TextView n = text(c, HorseNames.display(name), 12, INK, true);
        n.setGravity(Gravity.CENTER);
        n.setMaxLines(2);
        n.setEllipsize(TextUtils.TruncateAt.END);
        n.setPadding(0, HorseViews.dp(c, 4), 0, 0);
        inner.addView(n);
        View badge = tally != null ? MitsugiViews.badge(c, tally, false) : null;
        if (badge != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = HorseViews.dp(c, 4);
            inner.addView(badge, lp);
        }
        return card;
    }

    private static View cell(Context c, String label, String value) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(HorseViews.dp(c, 14));
        box.setBackground(bg);
        int ph = HorseViews.dp(c, 12), pv = HorseViews.dp(c, 8);
        box.setPadding(ph, pv, ph, pv);
        box.addView(text(c, label, 11, ACCENT, true));
        box.addView(text(c, value, 15, INK, true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = HorseViews.dp(c, 8);
        box.setLayoutParams(lp);
        return box;
    }

    private static TextView label(Context c, String s) {
        TextView tv = text(c, s, 12, ACCENT, true);
        tv.setPadding(0, 0, 0, HorseViews.dp(c, 6));
        return tv;
    }

    private static LinearLayout row(Context c) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        return tv;
    }

    /** 「(外)オフトレイル」→「オフトレイル」（カードに出す名前） */
    static final class HorseNames {
        private HorseNames() {
        }

        static String display(String name) {
            return name == null ? "" : name.trim().replaceFirst("^[\\(（\\[［][^\\)）\\]］]{1,2}[\\)）\\]］]\\s*", "");
        }
    }
}
