package com.tktkcompany.kakoRaceKeiba.ui.horse;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.card.MaterialCardView;
import com.tktkcompany.kakoRaceKeiba.R;
import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;
import com.tktkcompany.kakoRaceKeiba.ui.entries.FavoriteHorseNames;
import com.tktkcompany.kakoRaceKeiba.util.Lang;
import com.tktkcompany.kakoRaceKeiba.util.WeekendDays;

import org.threeten.bp.LocalDate;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 馬を名前で探して、お気に入りに登録する画面。行を押すとその馬のカルテを開く */
public class HorseSearchFragment extends Fragment {

    // 打ち終わるまで待ってから探す（1文字ごとに問い合わせないため）
    private static final long DEBOUNCE_MS = 400;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable pending;
    private int generation;

    private EditText input;
    private LinearLayout list;
    private TextView status;
    private ProgressBar progress;
    private Set<String> favorites = new HashSet<>();
    private List<HorseSearch.Hit> lastHits;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = HorseViews.dp(context, 12);
        root.setPadding(pad, pad, pad, 0);
        root.setBackgroundColor(0xFFF8FBF2);

        MaterialCardView box = HorseViews.card(context, 0xFFFFFFFF);
        input = new EditText(context);
        input.setHint(Lang.t("馬名の一部を入力（例: ドウデュース）", "Part of a horse name"));
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setTextSize(17);
        input.setBackground(null);
        HorseViews.inner(box).setPadding(pad, HorseViews.dp(context, 4), pad, HorseViews.dp(context, 4));
        HorseViews.inner(box).addView(input);
        root.addView(box);

        status = HorseViews.text(context, Lang.t("2文字以上入れると探します。☆を押すと推し馬にできます",
                "Type at least 2 letters. Tap ☆ to add a favorite"), 13, HorseViews.GRAY, false);
        status.setPadding(HorseViews.dp(context, 4), HorseViews.dp(context, 4), 0, HorseViews.dp(context, 4));
        root.addView(status);

        progress = new ProgressBar(context);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        progress.setLayoutParams(pp);
        root.addView(progress);

        ScrollView scroll = new ScrollView(context);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, HorseViews.dp(context, 16));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override
            public void afterTextChanged(Editable s) {
                if (pending != null) main.removeCallbacks(pending);
                String query = s.toString();
                pending = () -> search(query);
                main.postDelayed(pending, DEBOUNCE_MS);
            }
        });

        FavoriteHorseNames.load(loaded -> {
            favorites = new HashSet<>(loaded);
            if (lastHits != null) render(lastHits);
            else renderDiscover();
        });
        loadDiscover();
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 開いたらすぐ入力できるように
        input.requestFocus();
        main.postDelayed(() -> {
            if (!isAdded()) return;
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 200);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pending != null) main.removeCallbacks(pending);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void search(String raw) {
        String query = HorseSearch.toKatakana(raw);
        int gen = ++generation;
        if (query.length() < 2) {
            lastHits = null;
            status.setText(Lang.t("2文字以上入れると探します。☆を押すと推し馬にできます", "Type at least 2 letters. Tap ☆ to add a favorite"));
            progress.setVisibility(View.GONE);
            renderDiscover();
            return;
        }
        progress.setVisibility(View.VISIBLE);
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        executor.execute(() -> {
            List<HorseSearch.Hit> hits = null;
            try {
                hits = HorseSearch.merge(query, SupabaseManager.searchHorseResultsBlocking(query),
                        SupabaseManager.searchHorseEntriesBlocking(query, today));
            } catch (Exception e) {
                Log.w("HorseSearch", "検索に失敗", e);
            }
            List<HorseSearch.Hit> result = hits;
            main.post(() -> {
                if (!isAdded() || gen != generation) return;
                progress.setVisibility(View.GONE);
                if (result == null) {
                    status.setText(Lang.t("探せませんでした。通信状態を確かめてください", "Search failed. Check your connection"));
                    return;
                }
                lastHits = result;
                status.setText(result.isEmpty()
                        ? Lang.t("「" + query + "」を含む馬は見つかりませんでした（2025年10月以降に走った馬・出走予定の馬から探します）",
                        "No horse found for \"" + query + "\"")
                        : Lang.t(result.size() + "頭見つかりました。☆で推し馬に登録、行を押すと馬カルテを開きます",
                        result.size() + " horses. Tap ☆ to add, tap a row to open"));
                render(result);
            });
        });
    }

    // 名前を入れる前に出す候補（今週の重賞の出走馬・みんなのお気に入り）
    private List<HorseDiscover.GradedRace> gradedRaces;
    private List<SupabaseManager.FavoriteRank> popular;

    private void loadDiscover() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        executor.execute(() -> {
            List<HorseDiscover.GradedRace> races = java.util.Collections.emptyList();
            List<SupabaseManager.FavoriteRank> ranking = null;
            try {
                races = HorseDiscover.gradedRaces(SupabaseManager.fetchUpcomingEntriesBlocking(today));
            } catch (Exception e) {
                Log.w("HorseSearch", "今週の重賞の出走馬の取得に失敗", e);
            }
            try {
                ranking = SupabaseManager.fetchFavoriteRankingBlocking(30);
            } catch (Exception e) {
                // ランキングのビューがまだ無いときは出さない
                Log.w("HorseSearch", "みんなのお気に入りの取得に失敗", e);
            }
            List<HorseDiscover.GradedRace> r = races;
            List<SupabaseManager.FavoriteRank> p = HorseDiscover.popular(ranking, 20);
            main.post(() -> {
                if (!isAdded()) return;
                gradedRaces = r;
                popular = p;
                if (lastHits == null) renderDiscover();
            });
        });
    }

    private void renderDiscover() {
        Context context = getContext();
        if (context == null || gradedRaces == null) return;
        list.removeAllViews();
        if (list.getParent() instanceof ScrollView) ((ScrollView) list.getParent()).scrollTo(0, 0);
        if (popular != null && !popular.isEmpty()) {
            list.addView(HorseViews.section(context, Lang.t("❤ みんなの推し馬", "❤ Popular with everyone")));
            MaterialCardView card = HorseViews.card(context, 0xFFFFFFFF);
            com.google.android.material.chip.ChipGroup chips = new com.google.android.material.chip.ChipGroup(context);
            for (SupabaseManager.FavoriteRank r : popular) {
                chips.addView(chip(context, r.getHorseName(), Lang.t(r.getFans() + "人", String.valueOf(r.getFans()))));
            }
            HorseViews.inner(card).addView(chips);
            list.addView(card);
        }
        if (!gradedRaces.isEmpty()) {
            list.addView(HorseViews.section(context, Lang.t("🏆 今週の重賞に出る馬", "🏆 Runners in this week's graded races")));
            for (HorseDiscover.GradedRace race : gradedRaces) list.addView(gradedCard(context, race));
        }
        if (gradedRaces.isEmpty() && (popular == null || popular.isEmpty())) {
            list.addView(HorseViews.text(context, Lang.t("今週の重賞の出馬表が出たら、ここに出走馬が並びます",
                    "Runners of this week's graded races will appear here"), 13, HorseViews.GRAY, false));
        }
    }

    /** 重賞1レース分のカード（レース名の下に、出走馬を☆つきのチップで並べる） */
    private View gradedCard(Context context, HorseDiscover.GradedRace race) {
        MaterialCardView card = HorseViews.card(context, 0xFFFFFFFF);
        LinearLayout inner = HorseViews.inner(card);
        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView grade = HorseViews.text(context, gradeText(race.grade), 12, 0xFFFFFFFF, true);
        int padH = HorseViews.dp(context, 8), padV = HorseViews.dp(context, 2);
        grade.setPadding(padH, padV, padH, padV);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(HorseViews.dp(context, 10));
        bg.setColor(race.grade == com.tktkcompany.kakoRaceKeiba.ui.home.MainRaces.G1 ? 0xFF4A7A1E
                : race.grade == com.tktkcompany.kakoRaceKeiba.ui.home.MainRaces.G2 ? 0xFF4A7A1E : 0xFF4E7A27);
        grade.setBackground(bg);
        head.addView(grade);
        TextView title = HorseViews.text(context, "  " + race.title, 16, HorseViews.TEXT, true);
        head.addView(title);
        inner.addView(head);
        inner.addView(HorseViews.text(context, WeekendDays.formatCompact(race.date) + " " + Lang.race(race.course, race.raceNo)
                + Lang.t("・" + race.horses.size() + "頭", " · " + race.horses.size() + " runners"), 12, HorseViews.GRAY, false));
        com.google.android.material.chip.ChipGroup chips = new com.google.android.material.chip.ChipGroup(context);
        chips.setPadding(0, HorseViews.dp(context, 6), 0, 0);
        for (String horse : race.horses) chips.addView(chip(context, horse, null));
        inner.addView(chips);
        return card;
    }

    private static String gradeText(int grade) {
        String label = com.tktkcompany.kakoRaceKeiba.ui.home.MainRaces.gradeLabel(grade);
        return label != null ? label : "";
    }

    /** 馬1頭のチップ。押すとお気に入りに登録・解除、長押しで馬カルテ */
    private View chip(Context context, String horse, @Nullable String badge) {
        com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(context);
        Runnable paint = () -> {
            boolean on = favorites.contains(FavoriteHorseNames.normalize(horse));
            chip.setText((on ? "★ " : "☆ ") + horse + (badge != null ? "  " + badge : ""));
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(on ? 0xFFFFECB3 : 0xFFF8FBF2));
            chip.setTextColor(on ? 0xFF56664A : HorseViews.TEXT);
        };
        paint.run();
        chip.setOnClickListener(v -> {
            if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) {
                FavoriteHorseNames.showCannotSave(context);
                return;
            }
            boolean added = FavoriteHorseNames.toggle(favorites, horse);
            paint.run();
            if (added) {
                FavoriteHorseNames.showAdded(context, horse);
                com.tktkcompany.kakoRaceKeiba.notification.FavoriteResultWorker.runOnce(context);
            }
        });
        chip.setOnLongClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.navigation_horse, HorseFragment.args(horse));
            return true;
        });
        return chip;
    }

    private void render(List<HorseSearch.Hit> hits) {
        Context context = getContext();
        if (context == null) return;
        list.removeAllViews();
        for (HorseSearch.Hit hit : hits) list.addView(row(context, hit));
    }

    private View row(Context context, HorseSearch.Hit hit) {
        MaterialCardView card = HorseViews.card(context, hit.next != null ? 0xFFF1F8E4 : 0xFFFFFFFF);
        LinearLayout inner = HorseViews.inner(card);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);

        TextView star = HorseViews.text(context, "", 28, 0xFFFFB300, false);
        star.setPadding(0, 0, HorseViews.dp(context, 12), 0);
        bindStar(star, hit.name);
        star.setOnClickListener(v -> {
            // 匿名でも入れていない（通信できなかったなど）と保存先が無い
            if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) {
                FavoriteHorseNames.showCannotSave(context);
                return;
            }
            boolean added = FavoriteHorseNames.toggle(favorites, hit.name);
            bindStar(star, hit.name);
            if (added) {
                FavoriteHorseNames.showAdded(context, hit.name);
                // 出走予定があれば発走前の通知をすぐ予約する
                com.tktkcompany.kakoRaceKeiba.notification.FavoriteResultWorker.runOnce(context);
            } else {
                Toast.makeText(context, Lang.t(hit.name + "を推し馬から外しました", "Removed " + hit.name), Toast.LENGTH_SHORT).show();
            }
        });
        inner.addView(star);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(HorseViews.text(context, hit.name, 17, HorseViews.TEXT, true));
        if (hit.next != null) {
            texts.addView(HorseViews.text(context, Lang.t("次走 ", "Next ") + raceLabel(hit.next.getKaisaibi(), hit.next.getKaisaijo(),
                    hit.next.getRaceNo(), hit.next.getRaceTitle()), 13, HorseViews.GREEN, true));
        }
        if (hit.last != null) {
            texts.addView(HorseViews.text(context, Lang.t("前走 ", "Last ") + raceLabel(hit.last.getKaisaibi(), hit.last.getKaisaijo(),
                    hit.last.getRaceNo(), hit.last.getRaceTitle()) + " " + Lang.rank(hit.last.getRank()), 13, HorseViews.GRAY, false));
        }
        inner.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        inner.addView(HorseViews.text(context, "›", 22, 0xFFBDBDBD, false));

        card.setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.navigation_horse, HorseFragment.args(hit.name)));
        return card;
    }

    private void bindStar(TextView star, String name) {
        boolean on = favorites.contains(FavoriteHorseNames.normalize(name));
        star.setText(on ? "★" : "☆");
        star.setTextColor(on ? 0xFFFFB300 : 0xFFBDBDBD);
        star.setContentDescription(on ? Lang.t("推し馬から外す", "Remove favorite") : Lang.t("推し馬にする", "Add favorite"));
    }

    static String raceLabel(String date, String course, int raceNo, @Nullable String title) {
        String label = WeekendDays.formatCompact(date) + " " + Lang.race(course, raceNo);
        String t = title != null ? title.trim().replaceFirst("^サラ系", "") : "";
        return t.isEmpty() ? label : label + " " + t;
    }
}
