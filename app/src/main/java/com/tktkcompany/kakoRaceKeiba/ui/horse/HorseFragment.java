package com.tktkcompany.kakoRaceKeiba.ui.horse;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.tktkcompany.kakoRaceKeiba.R;
import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;
import com.tktkcompany.kakoRaceKeiba.ui.entries.FavoriteHorseNames;
import com.tktkcompany.kakoRaceKeiba.ui.horsememo.HorseMemoDialog;
import com.tktkcompany.kakoRaceKeiba.ui.horsememo.HorseMemos;
import com.tktkcompany.kakoRaceKeiba.ui.prediction.Predictions;
import com.tktkcompany.kakoRaceKeiba.ui.raceResult.RaceResultsFragment;
import com.tktkcompany.kakoRaceKeiba.util.Lang;
import com.tktkcompany.kakoRaceKeiba.util.WeekendDays;

import org.threeten.bp.LocalDate;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 馬カルテ。1頭の次走・自分のメモ・自分が付けた印の答え合わせ・全成績を1画面にまとめ、お気に入りに登録できる。
 * お気に入り一覧・馬を探す画面から開く
 */
public class HorseFragment extends Fragment {

    /** 馬名（ナビゲーションのラベルにも使う） */
    public static final String ARG_HORSE = "horse";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private String name;
    private LinearLayout content;
    private ProgressBar progress;

    // 読み込んだもの（すべて揃ったら描く）
    private List<SupabaseManager.RaceResult> results;
    private SupabaseManager.RaceEntry next;
    private Set<String> favorites;
    private Map<String, HorseMemos.Memo> memos;
    private List<Predictions.Race> predictions;

    public static Bundle args(String horseName) {
        Bundle bundle = new Bundle();
        bundle.putString(ARG_HORSE, FavoriteHorseNames.normalize(horseName));
        return bundle;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        name = getArguments() != null ? getArguments().getString(ARG_HORSE, "") : "";
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(0xFFF5F5F5);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = HorseViews.dp(context, 12);
        root.setPadding(pad, pad, pad, HorseViews.dp(context, 24));
        progress = new ProgressBar(context);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        pp.topMargin = HorseViews.dp(context, 40);
        root.addView(progress, pp);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);
        scroll.addView(root);
        load();
        return scroll;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void load() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        executor.execute(() -> {
            List<SupabaseManager.RaceResult> loaded = new ArrayList<>();
            SupabaseManager.RaceEntry upcoming = null;
            try {
                loaded = SupabaseManager.fetchResultsForHorsesBlocking(Collections.singletonList(name), null);
                for (SupabaseManager.RaceEntry e : SupabaseManager.fetchUpcomingEntriesBlocking(today)) {
                    if (FavoriteHorseNames.normalize(e.getHorseName()).equals(name)) {
                        upcoming = e;
                        break;
                    }
                }
            } catch (Exception e) {
                Log.w("HorseFragment", "成績の取得に失敗", e);
            }
            List<SupabaseManager.RaceResult> r = loaded;
            SupabaseManager.RaceEntry n = upcoming;
            main.post(() -> {
                results = r;
                next = n;
                render();
            });
        });
        FavoriteHorseNames.load(f -> {
            favorites = new HashSet<>(f);
            render();
        });
        HorseMemos.load(m -> {
            memos = new HashMap<>(m);
            render();
        });
        Predictions.loadAll(p -> {
            predictions = p;
            render();
        });
    }

    private void render() {
        Context context = getContext();
        if (context == null || results == null || favorites == null || memos == null || predictions == null) return;
        progress.setVisibility(View.GONE);
        content.removeAllViews();
        content.addView(header(context));
        if (next != null) content.addView(nextCard(context));
        content.addView(memoCard(context));

        List<HorseAptitude.Group> aptitude = HorseAptitude.of(results, next);
        if (!aptitude.isEmpty()) {
            content.addView(HorseViews.section(context, Lang.t("条件別の成績", "Results by condition")));
            content.addView(aptitudeCard(context, aptitude));
        }

        List<HorseRecord.MarkedRace> marked = HorseRecord.marked(name, predictions, results);
        if (!marked.isEmpty()) {
            content.addView(HorseViews.section(context, Lang.t("自分が付けた印", "Your picks")));
            MaterialCardView card = HorseViews.card(context, 0xFFFFFFFF);
            for (HorseRecord.MarkedRace m : marked) HorseViews.inner(card).addView(markRow(context, m));
            content.addView(card);
        }

        content.addView(HorseViews.section(context, Lang.t("成績（2025年10月以降）", "Results (since Oct 2025)")));
        MaterialCardView card = HorseViews.card(context, 0xFFFFFFFF);
        if (results.isEmpty()) {
            HorseViews.inner(card).addView(HorseViews.text(context, Lang.t("まだ成績はありません", "No results yet"), 14, HorseViews.GRAY, false));
        }
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) HorseViews.inner(card).addView(divider(context));
            HorseViews.inner(card).addView(resultRow(context, results.get(i)));
        }
        content.addView(card);
    }

    private View header(Context context) {
        MaterialCardView card = HorseViews.card(context, 0xFFFFFFFF);
        card.setCardElevation(HorseViews.dp(context, 6));
        card.setRadius(HorseViews.dp(context, 20));
        // 深い緑のグラデーションに、右下へ大きな馬の絵をうっすら重ねる
        android.widget.FrameLayout frame = new android.widget.FrameLayout(context);
        frame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF0B3D20, 0xFF1B5E20, 0xFF2E7D32}));
        android.widget.ImageView watermark = new android.widget.ImageView(context);
        watermark.setImageResource(R.drawable.uma);
        watermark.setAlpha(0.13f);
        int mark = HorseViews.dp(context, 170);
        android.widget.FrameLayout.LayoutParams wp = new android.widget.FrameLayout.LayoutParams(mark, mark, Gravity.END | Gravity.BOTTOM);
        wp.setMargins(0, 0, -HorseViews.dp(context, 24), -HorseViews.dp(context, 24));
        frame.addView(watermark, wp);
        LinearLayout inner = HorseViews.inner(card);
        card.removeView(inner);
        frame.addView(inner);
        card.addView(frame);
        int pad = HorseViews.dp(context, 20);
        inner.setPadding(pad, pad, pad, pad);

        // 馬のアイコンと名前
        LinearLayout title = new LinearLayout(context);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(HorseViews.avatar(context, 52));
        LinearLayout names = new LinearLayout(context);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(HorseViews.dp(context, 14), 0, 0, 0);
        names.addView(HorseViews.text(context, favorites.contains(name) ? Lang.t("★ 推し馬", "★ Favorite") : Lang.t("馬カルテ", "Horse profile"),
                12, 0xFFFFD54F, true));
        names.addView(HorseViews.text(context, name, 26, 0xFFFFFFFF, true));
        title.addView(names);
        inner.addView(title);

        // 成績の数字を4つ並べる
        HorseRecord record = HorseRecord.of(results);
        if (record.starts > 0) {
            LinearLayout stats = new LinearLayout(context);
            stats.setOrientation(LinearLayout.HORIZONTAL);
            stats.setPadding(0, HorseViews.dp(context, 16), 0, 0);
            stats.addView(statTile(context, String.valueOf(record.starts), Lang.t("出走", "Starts")), tileParams(context, false));
            stats.addView(statTile(context, String.valueOf(record.wins), Lang.t("勝利", "Wins")), tileParams(context, true));
            stats.addView(statTile(context, HorseRecord.percent(record.top2, record.starts) + "%", Lang.t("連対率", "Top 2")), tileParams(context, true));
            stats.addView(statTile(context, HorseRecord.percent(record.top3, record.starts) + "%", Lang.t("複勝率", "Top 3")), tileParams(context, true));
            inner.addView(stats);

            // 近5走の着順（左が新しい）
            LinearLayout form = new LinearLayout(context);
            form.setOrientation(LinearLayout.HORIZONTAL);
            form.setGravity(Gravity.CENTER_VERTICAL);
            form.setPadding(0, HorseViews.dp(context, 14), 0, 0);
            form.addView(HorseViews.text(context, Lang.t("近走  ", "Form  "), 12, 0xCCFFFFFF, true));
            for (int i = 0; i < Math.min(5, results.size()); i++) {
                TextView badge = HorseViews.rankBadge(context, results.get(i).getRank(), 30);
                ((LinearLayout.LayoutParams) badge.getLayoutParams()).setMarginEnd(HorseViews.dp(context, 6));
                form.addView(badge);
            }
            inner.addView(form);
        }

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, HorseViews.dp(context, 16), 0, 0);
        MaterialButton favorite = new MaterialButton(context);
        favorite.setCornerRadius(HorseViews.dp(context, 24));
        favorite.setTextSize(15);
        bindFavoriteButton(favorite);
        favorite.setOnClickListener(v -> {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                FavoriteHorseNames.showCannotSave(context);
                return;
            }
            boolean added = FavoriteHorseNames.toggle(favorites, name);
            // 見出しの「★ お気に入り」も変わるので描き直す
            render();
            if (added) {
                FavoriteHorseNames.showAdded(context, name);
                // 出走予定があれば発走前の通知をすぐ予約する
                com.tktkcompany.kakoRaceKeiba.notification.FavoriteResultWorker.runOnce(context);
            }
        });
        buttons.addView(favorite, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        inner.addView(buttons);
        return card;
    }

    private void bindFavoriteButton(MaterialButton button) {
        boolean on = favorites.contains(name);
        button.setText(on ? Lang.t("★ 推し馬として応援中", "★ Following") : Lang.t("☆ 推し馬にして、出走・結果を通知で受け取る", "☆ Follow and get notified"));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(on ? 0x33FFFFFF : 0xFFFFC107));
        button.setTextColor(on ? 0xFFFFFFFF : 0xFF3E2723);
        button.setStrokeColor(android.content.res.ColorStateList.valueOf(0x99FFFFFF));
        button.setStrokeWidth(on ? HorseViews.dp(button.getContext(), 1) : 0);
    }

    /** 見出しの中の数字1つ（半透明の白い角丸に、大きな数字と小さなラベル） */
    private View statTile(Context context, String value, String label) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        int pad = HorseViews.dp(context, 8);
        tile.setPadding(0, pad, 0, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(HorseViews.dp(context, 12));
        bg.setColor(0x26FFFFFF);
        tile.setBackground(bg);
        TextView v = HorseViews.text(context, value, 22, 0xFFFFFFFF, true);
        v.setGravity(Gravity.CENTER);
        TextView l = HorseViews.text(context, label, 11, 0xCCFFFFFF, false);
        l.setGravity(Gravity.CENTER);
        tile.addView(v);
        tile.addView(l);
        return tile;
    }

    private LinearLayout.LayoutParams tileParams(Context context, boolean gap) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        if (gap) p.setMarginStart(HorseViews.dp(context, 8));
        return p;
    }

    private View nextCard(Context context) {
        MaterialCardView card = HorseViews.card(context, 0xFFFFF8E1);
        LinearLayout inner = HorseViews.inner(card);
        inner.addView(HorseViews.text(context, Lang.t("📅 次走", "📅 Next race"), 13, 0xFFE65100, true));
        inner.addView(HorseViews.text(context, HorseSearchFragment.raceLabel(next.getKaisaibi(), next.getKaisaijo(), next.getRaceNo(),
                next.getRaceTitle()), 17, HorseViews.TEXT, true));
        String info = next.getHorseNumber() != null ? Lang.t(next.getHorseNumber() + "番・", "No. " + next.getHorseNumber() + " · ") : "";
        if (next.getJockey() != null) info += next.getJockey();
        if (!info.isEmpty()) inner.addView(HorseViews.text(context, info, 13, HorseViews.GRAY, false));
        inner.addView(HorseViews.text(context, Lang.t("出走表を見る ›", "Open the race card ›"), 14, HorseViews.GREEN, true));
        card.setOnClickListener(v -> FavoriteHorseNames.openEntry(v, next));
        return card;
    }

    /**
     * 条件別の成績。各行に複勝率のバーを付け、次走と同じ条件の行は「次走」の印と薄い黄色で目立たせる
     */
    private View aptitudeCard(Context context, List<HorseAptitude.Group> groups) {
        MaterialCardView card = HorseViews.card(context, 0xFFFFFFFF);
        LinearLayout inner = HorseViews.inner(card);
        boolean first = true;
        for (HorseAptitude.Group group : groups) {
            TextView title = HorseViews.text(context, group.title, 12, HorseViews.GRAY, true);
            title.setPadding(0, first ? 0 : HorseViews.dp(context, 12), 0, HorseViews.dp(context, 4));
            inner.addView(title);
            first = false;
            for (HorseAptitude.Row row : group.rows) inner.addView(aptitudeRow(context, row));
        }
        if (next != null) {
            TextView note = HorseViews.text(context, Lang.t("★次走 … 次のレースと同じ条件", "★Next … same condition as the next race"),
                    11, 0xFFE65100, false);
            note.setPadding(0, HorseViews.dp(context, 10), 0, 0);
            inner.addView(note);
        }
        return card;
    }

    private View aptitudeRow(Context context, HorseAptitude.Row row) {
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        int pad = HorseViews.dp(context, 6);
        line.setPadding(pad, pad, pad, pad);
        if (row.matchesNext) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(HorseViews.dp(context, 8));
            bg.setColor(0xFFFFF8E1);
            line.setBackground(bg);
        }

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView label = HorseViews.text(context, (row.matchesNext ? "★" : "") + row.label, 14,
                row.matchesNext ? 0xFFE65100 : HorseViews.TEXT, true);
        labels.addView(label);
        labels.addView(HorseViews.text(context, row.record(), 11, HorseViews.GRAY, false));
        line.addView(labels, new LinearLayout.LayoutParams(HorseViews.dp(context, 110), ViewGroup.LayoutParams.WRAP_CONTENT));

        // 複勝率のバー（緑。50%以上は濃い緑）
        android.widget.FrameLayout bar = new android.widget.FrameLayout(context);
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(HorseViews.dp(context, 5));
        track.setColor(0xFFEEEEEE);
        bar.setBackground(track);
        View fill = new View(context);
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setCornerRadius(HorseViews.dp(context, 5));
        fillBg.setColor(row.top3Rate() >= 50 ? 0xFF2E7D32 : 0xFF81C784);
        fill.setBackground(fillBg);
        bar.addView(fill, new android.widget.FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, HorseViews.dp(context, 10), 1);
        bp.setMarginEnd(HorseViews.dp(context, 10));
        line.addView(bar, bp);
        long rate = row.top3Rate();
        bar.post(() -> {
            ViewGroup.LayoutParams p = fill.getLayoutParams();
            p.width = (int) (bar.getWidth() * rate / 100);
            fill.setLayoutParams(p);
        });

        TextView percent = HorseViews.text(context, Lang.t("複勝率 ", "Top3 ") + rate + "%", 13,
                rate >= 50 ? HorseViews.GREEN : HorseViews.TEXT, true);
        percent.setMinWidth(HorseViews.dp(context, 92));
        percent.setGravity(Gravity.END);
        line.addView(percent);
        return line;
    }

    private View memoCard(Context context) {
        MaterialCardView card = HorseViews.card(context, 0xFFFFFFFF);
        LinearLayout inner = HorseViews.inner(card);
        HorseMemos.Memo memo = HorseMemos.find(memos, name);
        inner.addView(HorseViews.text(context, Lang.t("📝 メモ", "📝 Note"), 13, HorseViews.GREEN, true));
        if (memo == null) {
            inner.addView(HorseViews.text(context, Lang.t("気づいたことをメモしておけます（押して書く）", "Tap to write a note"), 14, HorseViews.GRAY, false));
        } else {
            inner.addView(HorseViews.text(context, memo.text, 15, HorseViews.TEXT, false));
            if (memo.from != null) inner.addView(HorseViews.text(context, memo.from, 12, HorseViews.GRAY, false));
        }
        card.setOnClickListener(v -> HorseMemoDialog.show(context, memos, name, null, changed -> {
            if (changed == null) memos.remove(name);
            else memos.put(name, changed);
            render();
        }));
        return card;
    }

    private View markRow(Context context, HorseRecord.MarkedRace m) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, HorseViews.dp(context, 4), 0, HorseViews.dp(context, 4));
        row.addView(HorseViews.text(context, m.mark, 20, 0xFFC62828, true));
        TextView label = HorseViews.text(context, "  " + WeekendDays.formatShort(m.race.date) + " " + Lang.race(m.race.course, m.race.raceNo),
                14, HorseViews.TEXT, false);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(HorseViews.text(context, m.rank != null ? Lang.rank(m.rank) : Lang.t("結果待ち", "Pending"), 15,
                m.rank != null ? HorseViews.rankColor(m.rank) : HorseViews.GRAY, true));
        return row;
    }

    private View resultRow(Context context, SupabaseManager.RaceResult r) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, HorseViews.dp(context, 8), 0, HorseViews.dp(context, 8));

        row.addView(HorseViews.rankBadge(context, r.getRank(), 40));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(HorseViews.dp(context, 14), 0, 0, 0);
        String title = r.getRaceTitle() != null ? r.getRaceTitle().trim().replaceFirst("^サラ系", "") : "";
        texts.addView(HorseViews.text(context, title.isEmpty() ? Lang.race(r.getKaisaijo(), r.getRaceNo()) : title, 15, HorseViews.TEXT, true));
        texts.addView(HorseViews.text(context, WeekendDays.formatShort(r.getKaisaibi()) + "　" + Lang.race(r.getKaisaijo(), r.getRaceNo()),
                12, HorseViews.GREEN, false));
        StringBuilder sub = new StringBuilder();
        if (r.getPopular() != null) sub.append(Lang.popularity(r.getPopular()));
        if (r.getJockey() != null) sub.append(sub.length() > 0 ? "・" : "").append(r.getJockey());
        if (r.getTime() != null) sub.append(sub.length() > 0 ? "・" : "").append(r.getTime());
        if (sub.length() > 0) texts.addView(HorseViews.text(context, sub, 12, HorseViews.GRAY, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(HorseViews.text(context, "›", 20, 0xFFBDBDBD, false));

        row.setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.navigation_raceResults,
                RaceResultsFragment.args(r.getKaisaibi(), r.getKaisaijo(), r.getRaceNo(), r.getHorseName())));
        return row;
    }

    private View divider(Context context) {
        View line = new View(context);
        line.setBackgroundColor(0xFFEEEEEE);
        line.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, HorseViews.dp(context, 1))));
        return line;
    }
}
