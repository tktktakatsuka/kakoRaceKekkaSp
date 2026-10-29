package com.tktkcompany.kakoRaceKeiba.ui.welcome;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tktkcompany.kakoRaceKeiba.R;
import com.tktkcompany.kakoRaceKeiba.ui.favorite.Mitsugi;
import com.tktkcompany.kakoRaceKeiba.ui.favorite.MitsugiViews;
import com.tktkcompany.kakoRaceKeiba.ui.horse.HorseViews;
import com.tktkcompany.kakoRaceKeiba.util.Lang;

/**
 * ウェルカム画面の3枚（あいさつ・カードを飾って集める・お知らせ）。
 * カードの馬の名前は作りもの（実在の馬は使わない）。
 */
final class WelcomePagerAdapter extends RecyclerView.Adapter<WelcomePagerAdapter.Holder> {

    static final int PAGES = 3;
    private static final int INK = 0xFF2F3B22;
    private static final int ACCENT = 0xFF4A7A1E;

    static final class Holder extends RecyclerView.ViewHolder {
        Holder(@NonNull View itemView) {
            super(itemView);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout page = new LinearLayout(parent.getContext());
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new Holder(page);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        LinearLayout page = (LinearLayout) holder.itemView;
        page.removeAllViews();
        Context c = page.getContext();
        FrameLayout art = new FrameLayout(c);
        page.addView(art, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        switch (position) {
            case 0:
                art.addView(pegasus(c, 220), centered());
                addTexts(page, Lang.t("ようこそ、\n推し馬ノートへ！", "Welcome to\nOshiuma Note!"),
                        Lang.t("あなたの「推し」の馬を見つけて、\n応援しよう", "Find the horses you love\nand cheer them on"));
                break;
            case 1:
                art.addView(cardFan(c), centered());
                addTexts(page, Lang.t("カードを飾って、\nコレクション", "Decorate your cards\nand collect them"),
                        Lang.t("アイコンと背景を選んで、\n推し馬ごとに自分だけのカードに", "Pick an icon and a background\nto make a card for each horse"));
                break;
            default:
                art.addView(bubbles(c), centered());
                addTexts(page, Lang.t("走る日も、結果も\nお知らせ", "Alerts on race day\nand for results"),
                        Lang.t("出番を見逃さないから、\n応援がもっと楽しくなる", "Never miss a race,\nand cheering gets even more fun"));
                break;
        }
    }

    @Override
    public int getItemCount() {
        return PAGES;
    }

    private static void addTexts(LinearLayout page, String title, String sub) {
        Context c = page.getContext();
        TextView t = text(c, title, 26, INK, true);
        t.setLineSpacing(0, 1.15f);
        page.addView(t);
        TextView s = text(c, sub, 15, ACCENT, false);
        s.setPadding(0, HorseViews.dp(c, 10), 0, HorseViews.dp(c, 12));
        s.setLineSpacing(0, 1.2f);
        page.addView(s);
    }

    private static FrameLayout.LayoutParams centered() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
    }

    /** 羽のある子。うしろに白い丸 */
    private static View pegasus(Context c, int sizeDp) {
        FrameLayout box = new FrameLayout(c);
        View halo = new View(c);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0x99FFFFFF);
        halo.setBackground(circle);
        int haloSize = HorseViews.dp(c, sizeDp * 0.8f);
        box.addView(halo, new FrameLayout.LayoutParams(haloSize, haloSize, Gravity.CENTER));
        ImageView image = new ImageView(c);
        image.setImageResource(R.drawable.welcome_pegasus);
        int size = HorseViews.dp(c, sizeDp);
        box.addView(image, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        return box;
    }

    /** 飾ったカード3枚を扇形に。真ん中のカードに称号のバッジ、右下に小さな羽の子 */
    private static View cardFan(Context c) {
        FrameLayout fan = new FrameLayout(c);
        int w = HorseViews.dp(c, 104), h = HorseViews.dp(c, 136);
        fan.setMinimumWidth(HorseViews.dp(c, 304));
        fan.setMinimumHeight(HorseViews.dp(c, 230));
        // 重なり順は elevation で決まる（左右のカード → 真ん中のカード → バッジと羽の子）
        View left = miniCard(c, "🌸", "サクラノユメ", 0xFFFFD6E5, 0xFFFF9EC0);
        left.setRotation(-12);
        fan.addView(left, at(w, h, HorseViews.dp(c, 16), HorseViews.dp(c, 44)));
        View right = miniCard(c, "🍀", "ソラノカケラ", 0xFFCDEBFF, 0xFF7CC3F5);
        right.setRotation(12);
        fan.addView(right, at(w, h, HorseViews.dp(c, 184), HorseViews.dp(c, 44)));
        View middle = miniCard(c, "👑", "キンイロダイヤ", 0xFFFFF3B0, 0xFFF5C542);
        middle.setElevation(HorseViews.dp(c, 10));
        fan.addView(middle, at(w, h, HorseViews.dp(c, 100), HorseViews.dp(c, 28)));
        // 真ん中のカードの上のふちに、称号のバッジ（🌟 応援団長）
        Mitsugi.Tally sample = new Mitsugi.Tally();
        sample.spent = 20000;
        View badge = MitsugiViews.badge(c, sample, true);
        if (badge != null) {
            badge.setElevation(HorseViews.dp(c, 14));
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            p.topMargin = HorseViews.dp(c, 14);
            fan.addView(badge, p);
        }
        View small = pegasus(c, 76);
        small.setElevation(HorseViews.dp(c, 14));
        fan.addView(small, at(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, HorseViews.dp(c, 222), HorseViews.dp(c, 150)));
        return fan;
    }

    private static FrameLayout.LayoutParams at(int w, int h, int left, int top) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h);
        p.leftMargin = left;
        p.topMargin = top;
        return p;
    }

    private static View miniCard(Context c, String emoji, String name, int from, int to) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{from, to});
        bg.setCornerRadius(HorseViews.dp(c, 18));
        card.setBackground(bg);
        card.setElevation(HorseViews.dp(c, 6));
        card.addView(text(c, emoji, 34, INK, false));
        TextView n = text(c, name, 10, INK, true);
        n.setPadding(0, HorseViews.dp(c, 6), 0, 0);
        card.addView(n);
        return card;
    }

    /** お知らせの吹き出し3つ */
    private static View bubbles(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(bubble(c, Lang.t("🏇 サクラノユメが出走します", "🏇 Sakura no Yume is entered"), Lang.t("出走が決まったら", "When the entry is set")));
        box.addView(bubble(c, Lang.t("⏰ まもなく発走！ 単勝 3.2倍", "⏰ Post time soon! Win odds 3.2"), Lang.t("発走30分前", "30 minutes before post")));
        box.addView(bubble(c, Lang.t("🎉 サクラノユメが1着！", "🎉 Sakura no Yume won!"), Lang.t("結果が出たらすぐ（シェアもできる）", "As soon as it's official (and you can share it)")));
        return box;
    }

    private static View bubble(Context c, String title, String note) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(HorseViews.dp(c, 16));
        b.setBackground(bg);
        b.setElevation(HorseViews.dp(c, 3));
        int ph = HorseViews.dp(c, 14), pv = HorseViews.dp(c, 10);
        b.setPadding(ph, pv, ph, pv);
        b.addView(text(c, title, 14, INK, true));
        b.addView(text(c, note, 11, 0xFF56664A, false));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(HorseViews.dp(c, 280), ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = HorseViews.dp(c, 10);
        b.setLayoutParams(p);
        return b;
    }

    private static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        if (bold) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        return tv;
    }
}
