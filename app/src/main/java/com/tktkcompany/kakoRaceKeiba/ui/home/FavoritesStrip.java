package com.tktkcompany.kakoRaceKeiba.ui.home;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.navigation.Navigation;

import com.google.android.material.card.MaterialCardView;
import com.tktkcompany.kakoRaceKeiba.R;
import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;
import com.tktkcompany.kakoRaceKeiba.ui.entries.FavoriteHorseNames;
import com.tktkcompany.kakoRaceKeiba.ui.favorite.CardStyles;
import com.tktkcompany.kakoRaceKeiba.ui.horse.HorseFragment;
import com.tktkcompany.kakoRaceKeiba.ui.horse.HorseViews;
import com.tktkcompany.kakoRaceKeiba.util.Lang;
import com.tktkcompany.kakoRaceKeiba.util.WeekendDays;

import org.threeten.bp.LocalDate;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ホームの上の「あなたのお気に入り馬」。登録した馬を横に並べたカードで出す。
 * 次に走る予定のある馬を先頭に（発走の早い順）、それ以外は最近走った順。最後に「＋ 馬を追加」
 */
final class FavoritesStrip {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Pattern START = Pattern.compile("(\\d{1,2}):(\\d{2})発走");

    /** 1頭分 */
    static final class Item {
        final String name;
        @Nullable SupabaseManager.RaceEntry next;
        @Nullable SupabaseManager.RaceResult last;
        /** 今日走り終えたレースの速報（夜の取り込み前） */
        @Nullable SupabaseManager.RaceResult live;

        Item(String name) {
            this.name = name;
        }
    }

    private final LinearLayout container;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int generation;
    // 最後に並べた馬（飾りを読み込んだあとや、飾りを変えたあとに描き直すため）
    private List<Item> shown = new ArrayList<>();

    FavoritesStrip(LinearLayout container) {
        this.container = container;
    }

    /** お気に入りの馬の次走・前走を読んでから並べる（読む前は名前だけで並べておく） */
    void show(Set<String> favorites) {
        int gen = ++generation;
        List<Item> items = new ArrayList<>();
        for (String name : favorites) items.add(new Item(name));
        render(items);
        // カードの飾り（アイコン・背景）を読んだら描き直す
        CardStyles.load(() -> {
            if (gen == generation) render(shown);
        });
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        List<String> names = new ArrayList<>(favorites);
        EXECUTOR.execute(() -> {
            Map<String, Item> byName = new HashMap<>();
            for (String name : names) byName.put(name, new Item(name));
            try {
                for (SupabaseManager.RaceEntry e : SupabaseManager.fetchUpcomingEntriesBlocking(today)) {
                    Item item = byName.get(FavoriteHorseNames.normalize(e.getHorseName()));
                    if (item != null && !e.getScratched() && (item.next == null || order(e).compareTo(order(item.next)) < 0)) item.next = e;
                }
                // 新しい順に返ってくるので、最初に出てきたものが前走
                for (SupabaseManager.RaceResult r : SupabaseManager.fetchResultsForHorsesBlocking(names, null)) {
                    Item item = byName.get(FavoriteHorseNames.normalize(r.getHorseName()));
                    if (item != null && item.last == null) item.last = r;
                }
            } catch (Exception e) {
                Log.w("FavoritesStrip", "お気に入り馬の次走・前走の取得に失敗", e);
            }
            List<Item> sorted = sort(new ArrayList<>(byName.values()));
            main.post(() -> {
                if (gen == generation) render(sorted);
            });
        });
    }

    /** 次走のある馬が先（発走の早い順）、次に前走の新しい順、最後に名前順 */
    static List<Item> sort(List<Item> items) {
        items.sort((a, b) -> {
            if ((a.next != null) != (b.next != null)) return a.next != null ? -1 : 1;
            if (a.next != null) return order(a.next).compareTo(order(b.next));
            String la = a.last != null ? a.last.getKaisaibi() : "";
            String lb = b.last != null ? b.last.getKaisaibi() : "";
            int c = lb.compareTo(la);
            return c != 0 ? c : a.name.compareTo(b.name);
        });
        return items;
    }

    /** 開催日と発走時刻で並べるための文字列（発走時刻が無ければ最後） */
    static String order(SupabaseManager.RaceEntry e) {
        Matcher m = START.matcher(e.getRaceInfo() != null ? e.getRaceInfo() : "");
        String time = m.find() ? String.format("%02d%s", Integer.parseInt(m.group(1)), m.group(2)) : "9999";
        return e.getKaisaibi() + time + String.format("%02d", e.getRaceNo());
    }

    private void render(List<Item> items) {
        shown = items;
        Context context = container.getContext();
        container.removeAllViews();
        for (Item item : items) container.addView(card(context, item));
        container.addView(addCard(context));
        // 並べ直したら左端（次走の近い馬）から見せる
        if (container.getParent() instanceof android.widget.HorizontalScrollView) {
            android.widget.HorizontalScrollView scroll = (android.widget.HorizontalScrollView) container.getParent();
            scroll.post(() -> scroll.scrollTo(0, 0));
        }
    }

    private View card(Context context, Item item) {
        MaterialCardView card = baseCard(context, 0xFFFFFFFF);
        CardStyles.applyBackground(card, item.name, item.next != null ? 0xFFFFF8E1 : 0xFFFFFFFF);
        LinearLayout inner = (LinearLayout) card.getChildAt(0);
        inner.addView(CardStyles.avatar(context, item.name, 44));

        TextView name = text(context, item.name, 15, 0xFF1B1B1B, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, HorseViews.dp(context, 8), 0, HorseViews.dp(context, 4));
        inner.addView(name);

        if (item.next != null) {
            SupabaseManager.RaceEntry e = item.next;
            Matcher m = START.matcher(e.getRaceInfo() != null ? e.getRaceInfo() : "");
            String time = m.find() ? " " + Integer.parseInt(m.group(1)) + ":" + m.group(2) : "";
            inner.addView(text(context, Lang.t("▶ 次走", "▶ Next"), 11, 0xFFE65100, true));
            inner.addView(text(context, WeekendDays.formatCompact(e.getKaisaibi()) + time, 13, 0xFFE65100, true));
            inner.addView(text(context, Lang.race(e.getKaisaijo(), e.getRaceNo()), 13, 0xFF5D4037, false));
        } else if (item.last != null) {
            SupabaseManager.RaceResult r = item.last;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(HorseViews.rankBadge(context, r.getRank(), 26));
            TextView label = text(context, "  " + Lang.t("前走", "Last"), 12, 0xFF757575, false);
            row.addView(label);
            inner.addView(row);
            inner.addView(text(context, WeekendDays.formatCompact(r.getKaisaibi()) + " " + Lang.race(r.getKaisaijo(), r.getRaceNo()),
                    12, 0xFF757575, false));
        } else {
            inner.addView(text(context, Lang.t("出走予定を待っています", "Waiting for an entry"), 12, 0xFF9E9E9E, false));
        }
        card.setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.navigation_horse, HorseFragment.args(item.name)));
        // 長押しで、アイコンと背景を選ぶ
        card.setOnLongClickListener(v -> {
            CardStyles.showEditor(context, item.name, item.name, () -> render(shown));
            return true;
        });
        return card;
    }

    /** 最後の「＋ 馬を追加」 */
    private View addCard(Context context) {
        MaterialCardView card = baseCard(context, 0xFFE8F5E9);
        card.setCardElevation(0);
        LinearLayout inner = (LinearLayout) card.getChildAt(0);
        inner.setGravity(Gravity.CENTER);
        inner.setMinimumHeight(HorseViews.dp(context, 150));
        TextView plus = text(context, "＋", 34, 0xFF2E7D32, true);
        plus.setGravity(Gravity.CENTER);
        inner.addView(plus);
        TextView label = text(context, Lang.t("馬を追加", "Add a horse"), 14, 0xFF2E7D32, true);
        label.setGravity(Gravity.CENTER);
        inner.addView(label);
        card.setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.navigation_horseSearch));
        return card;
    }

    private static MaterialCardView baseCard(Context context, int background) {
        MaterialCardView card = new MaterialCardView(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(HorseViews.dp(context, 150), ViewGroup.LayoutParams.MATCH_PARENT);
        int m = HorseViews.dp(context, 5);
        params.setMargins(m, m, m, m);
        card.setLayoutParams(params);
        card.setRadius(HorseViews.dp(context, 16));
        card.setCardElevation(HorseViews.dp(context, 3));
        card.setCardBackgroundColor(background);
        card.setStrokeWidth(0);
        LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(LinearLayout.VERTICAL);
        int pad = HorseViews.dp(context, 12);
        inner.setPadding(pad, pad, pad, pad);
        card.addView(inner, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return card;
    }

    private static TextView text(Context context, String s, float sp, int color, boolean bold) {
        TextView tv = new TextView(context);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        return tv;
    }
}
