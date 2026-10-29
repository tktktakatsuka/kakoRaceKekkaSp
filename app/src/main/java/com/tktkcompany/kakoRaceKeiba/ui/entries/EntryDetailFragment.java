package com.tktkcompany.kakoRaceKeiba.ui.entries;

import com.tktkcompany.kakoRaceKeiba.ads.AdFree;
import com.tktkcompany.kakoRaceKeiba.ads.AdUnits;
import com.tktkcompany.kakoRaceKeiba.ads.BannerAd;
import com.tktkcompany.kakoRaceKeiba.util.Lang;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.tktkcompany.kakoRaceKeiba.R;
import com.tktkcompany.kakoRaceKeiba.ads.RecentRunsReward;
import com.tktkcompany.kakoRaceKeiba.databinding.FragmentEntriesBinding;
import com.tktkcompany.kakoRaceKeiba.db.LiveOdds;
import com.tktkcompany.kakoRaceKeiba.db.LivePayouts;
import com.tktkcompany.kakoRaceKeiba.db.LiveRaceCard;
import com.tktkcompany.kakoRaceKeiba.db.LiveResults;
import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;
import com.tktkcompany.kakoRaceKeiba.notification.FavoriteResultWorker;
import com.tktkcompany.kakoRaceKeiba.notification.RaceStartNotices;
import com.tktkcompany.kakoRaceKeiba.ui.prediction.Bets;
import com.tktkcompany.kakoRaceKeiba.ui.prediction.Predictions;
import com.tktkcompany.kakoRaceKeiba.ui.raceResult.PayoutTable;
import com.tktkcompany.kakoRaceKeiba.ui.raceResult.RaceResultsFragment;
import com.tktkcompany.kakoRaceKeiba.ui.horsememo.HorseMemoDialog;
import com.tktkcompany.kakoRaceKeiba.ui.horsememo.HorseMemos;
import com.tktkcompany.kakoRaceKeiba.util.WeekendDays;

import org.threeten.bp.DateTimeException;
import org.threeten.bp.LocalTime;
import org.threeten.bp.OffsetDateTime;
import org.threeten.bp.ZoneId;
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

/** 1レース分の出走馬と、それぞれの近走成績・予想の手がかりを表示する */
public class EntryDetailFragment extends Fragment {

    // 近走を10走まで広げるリワード広告（画面を開いたときに読み込んでおく）
    private final RecentRunsReward reward = new RecentRunsReward();
    // 読み込んだ近走（10走分）。広告を見たあとに読み直さずに表示を広げるため
    private Map<String, List<SupabaseManager.RaceResult>> lastResults = new HashMap<>();

    // 枠番ごとの色（1白 2黒 3赤 4青 5黄 6緑 7橙 8桃）
    private static final int[] WAKU_BACKGROUND = {
            0xFFFFFFFF, 0xFF222222, 0xFFE53935, 0xFF1E88E5,
            0xFFFDD835, 0xFF43A047, 0xFFFB8C00, 0xFFF06292};
    private static final int[] WAKU_TEXT = {
            0xFF222222, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF,
            0xFF222222, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF};

    private FragmentEntriesBinding binding;
    private String raceDate;
    // メモを書いた場面として残す「10/17(土) 京都9R もみじステークス」
    private String raceLabel;
    private Set<String> favorites = new HashSet<>();
    private Map<String, HorseMemos.Memo> memos = new HashMap<>();
    // 馬番 → オッズを出す欄（オッズは出走表を出したあとに読み込む）
    private final Map<String, TextView> oddsViews = new HashMap<>();
    // 自分の予想の印（normalize した馬名 → 印）と、印・結果を出す欄
    private Map<String, String> marks = new HashMap<>();
    private final Map<String, TextView> markViews = new HashMap<>();
    private final Map<String, TextView> resultViews = new HashMap<>();
    // 馬番（先頭の0なし）→ 馬のカード（当日の馬体重・取消を出すため）
    private final Map<String, View> horseCards = new HashMap<>();
    // このレースの買い目と払い戻し
    @Nullable private LinearLayout betsSection;
    @Nullable private SupabaseManager.RaceEntry raceForBets;
    private List<Bets.Bet> raceBets = new ArrayList<>();
    private List<LivePayouts.Payout> payouts = new ArrayList<>();
    private static final ExecutorService ODDS_EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentEntriesBinding.inflate(inflater, container, false);
        BannerAd.show(this, binding.adContainer, AdUnits.BANNER_ENTRY_DETAIL);

        Bundle args = getArguments();
        String date = args != null ? args.getString(EntriesFragment.ARG_DATE) : null;
        String course = args != null ? args.getString(EntriesFragment.ARG_COURSE) : null;
        int raceNo = args != null ? args.getInt(EntriesFragment.ARG_RACE_NO) : 0;
        if (date == null || course == null) return binding.getRoot();

        raceDate = date;
        setScreenTitle(WeekendDays.formatShort(date) + " " + course + raceNo + "R");
        String raceTitle = args.getString(EntriesFragment.ARG_RACE_TITLE, "");
        raceLabel = (WeekendDays.formatShort(date) + " " + course + raceNo + "R " + raceTitle.trim()).trim();
        binding.textEntriesLead.setText(raceTitle);
        loadEntries(date, course, raceNo);
        return binding.getRoot();
    }

    private void setScreenTitle(String title) {
        if (getActivity() instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) actionBar.setTitle(title);
        }
    }

    private void loadEntries(String date, String course, int raceNo) {
        SupabaseManager.fetchRaceEntries(date, course, raceNo, new SupabaseManager.SupabaseCallback<List<SupabaseManager.RaceEntry>>() {
            @Override
            public void onSuccess(List<SupabaseManager.RaceEntry> entries) {
                if (binding == null || !isAdded()) return;
                if (entries.isEmpty()) {
                    binding.progressBar.setVisibility(View.GONE);
                    showMessage(Lang.t("出走馬のデータがありません", "No runners found"));
                    return;
                }
                showHeader(entries);
                FavoriteHorseNames.load(loaded -> {
                    if (binding == null || !isAdded()) return;
                    favorites = new HashSet<>(loaded);
                    HorseMemos.load(loadedMemos -> {
                        if (binding == null || !isAdded()) return;
                        memos = new HashMap<>(loadedMemos);
                        Predictions.loadRace(date, course, raceNo, loadedMarks -> {
                            if (binding == null || !isAdded()) return;
                            marks = loadedMarks;
                            loadPastResults(entries);
                        });
                    });
                });
            }

            @Override
            public void onFailure(@NonNull String message) {
                Log.e("EntryDetailFragment", "Supabase load failed: " + message);
                if (binding == null || !isAdded()) return;
                binding.progressBar.setVisibility(View.GONE);
                showMessage(Lang.t("出走馬の読み込みに失敗しました", "Couldn't load runners"));
            }
        });
    }

    /** レース名・条件・枠順確定の状況を画面上部に出す */
    private void showHeader(List<SupabaseManager.RaceEntry> entries) {
        SupabaseManager.RaceEntry first = entries.get(0);
        SpannableStringBuilder text = new SpannableStringBuilder();
        String title = first.getRaceTitle() != null ? first.getRaceTitle() : "";
        text.append(title, new StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new RelativeSizeSpan(1.3f), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        String conditions = conditions(first.getRaceInfo());
        if (!conditions.isEmpty()) text.append("\n").append(conditions);

        text.append("\n");
        if (isBeforeDraw(entries)) {
            text.append(Lang.t("枠順確定前（特別登録 " + entries.size() + "頭）。出走できる頭数を超える場合は抽選・除外があります",
                    "Before the draw (" + entries.size() + " nominated). Some may be balloted out"));
        } else {
            text.append(Lang.t(entries.size() + "頭立て", entries.size() + " runners"));
        }
        int shownRuns = RecentRunsReward.runs(requireContext());
        text.append(Lang.t("\n各馬の近走" + shownRuns + "走（新しい順）。行をタップするとそのレースの結果を表示します",
                "\nLast " + shownRuns + " runs for each horse (newest first). Tap a row to see that race"));
        binding.textEntriesLead.setText(text);
        binding.textEntriesLead.setTextColor(0xFF2F3B22);
    }

    /** 「1勝クラス　(混) 定量 芝2200m 34頭 発走」から頭数と「発走」を除いた条件を返す */
    static String conditions(String raceInfo) {
        if (raceInfo == null) return "";
        return raceInfo.replaceAll("\\s*\\d+頭", "")
                .replaceAll("\\s*発走.*$", "")
                .replaceAll("[\\s　]+", " ")
                .trim();
    }

    private static boolean isBeforeDraw(List<SupabaseManager.RaceEntry> entries) {
        for (SupabaseManager.RaceEntry e : entries) {
            if (e.getHorseNumber() != null) return false;
        }
        return true;
    }

    private void loadPastResults(List<SupabaseManager.RaceEntry> entries) {
        List<String> names = new ArrayList<>();
        for (SupabaseManager.RaceEntry entry : entries) names.add(entry.getHorseName());

        SupabaseManager.fetchRecentResultsForHorses(names, RecentRunsReward.REWARDED_RUNS, new SupabaseManager.SupabaseCallback<Map<String, List<SupabaseManager.RaceResult>>>() {
            @Override
            public void onSuccess(Map<String, List<SupabaseManager.RaceResult>> results) {
                if (binding == null || !isAdded()) return;
                binding.progressBar.setVisibility(View.GONE);
                render(entries, results);
            }

            @Override
            public void onFailure(@NonNull String message) {
                Log.e("EntryDetailFragment", "Past results load failed: " + message);
                if (binding == null || !isAdded()) return;
                binding.progressBar.setVisibility(View.GONE);
                // 成績が取れなくても出走馬の一覧は出す
                render(entries, Collections.emptyMap());
            }
        });
    }

    private void showMessage(String message) {
        binding.textEntriesEmpty.setText(message);
        binding.textEntriesEmpty.setVisibility(View.VISIBLE);
    }

    private void render(List<SupabaseManager.RaceEntry> entries, Map<String, List<SupabaseManager.RaceResult>> results) {
        lastResults = results;
        LayoutInflater inflater = getLayoutInflater();
        binding.entriesContainer.removeAllViews();
        oddsViews.clear();
        markViews.clear();
        resultViews.clear();
        horseCards.clear();
        // 4走以上ある馬が1頭もいなければ、広告を見ても増えないのでボタンを出さない
        boolean anyHidden = false;
        for (List<SupabaseManager.RaceResult> runs : results.values()) {
            if (runs.size() > RecentRunsReward.NORMAL_RUNS) anyHidden = true;
        }
        if (anyHidden || RecentRunsReward.isUnlocked(requireContext())) {
            binding.entriesContainer.addView(createRewardView(entries));
        }
        // 買い目の記録（このレースの分）と、確定後の払い戻し
        betsSection = new LinearLayout(requireContext());
        betsSection.setOrientation(LinearLayout.VERTICAL);
        binding.entriesContainer.addView(betsSection);
        raceForBets = entries.get(0);
        loadBets();
        for (SupabaseManager.RaceEntry entry : entries) {
            View card = inflater.inflate(R.layout.item_entry_horse, binding.entriesContainer, false);

            TextView number = card.findViewById(R.id.text_entry_number);
            if (entry.getHorseNumber() != null) {
                number.setText(entry.getHorseNumber());
                styleWaku(number, entry.getWaku());
            } else {
                // 枠順確定前は馬番の枠を出さない
                number.setVisibility(View.GONE);
            }

            TextView name = card.findViewById(R.id.text_entry_horse);
            name.setText(entry.getHorseName());
            bindFavorite(card, entry.getHorseName());
            bindMemo(card, entry.getHorseName());
            bindMark(card, entry);
            resultViews.put(FavoriteHorseNames.normalize(entry.getHorseName()), card.findViewById(R.id.text_entry_result));

            List<String> right = new ArrayList<>();
            if (entry.getSexAge() != null && !entry.getSexAge().endsWith("0")) right.add(entry.getSexAge());
            if (entry.getJockey() != null) right.add(entry.getJockey());
            if (entry.getWeight() != null) right.add(entry.getWeight());
            ((TextView) card.findViewById(R.id.text_entry_jockey)).setText(android.text.TextUtils.join(" ", right));

            List<SupabaseManager.RaceResult> runs = results.get(entry.getHorseName());
            showBadges(card.findViewById(R.id.entry_badges), entry, runs);

            LinearLayout pastContainer = card.findViewById(R.id.entry_past_results);
            if (runs == null || runs.isEmpty()) {
                TextView none = new TextView(requireContext());
                none.setText(Lang.t("過去の成績データはありません", "No past runs"));
                none.setTextSize(12);
                none.setTextColor(0xFF999999);
                none.setPadding(0, 6, 0, 6);
                pastContainer.addView(none);
            } else {
                // 印の計算には取れた分を全部使い、表示はふだん3走（広告を見たら10走）にする
                int shown = Math.min(runs.size(), RecentRunsReward.runs(requireContext()));
                for (SupabaseManager.RaceResult run : runs.subList(0, shown)) {
                    pastContainer.addView(createPastRunView(inflater, pastContainer, run));
                }
                if (runs.size() > shown) {
                    TextView more = new TextView(requireContext());
                    more.setText(Lang.t("▶ ほか" + (runs.size() - shown) + "走（タップして広告を見ると表示）",
                            "▶ " + (runs.size() - shown) + " more runs (tap to watch an ad)"));
                    more.setOnClickListener(v -> watchRewardAd(entries));
                    more.setTextSize(12);
                    more.setTextColor(0xFF4A7A1E);
                    more.setPadding(0, 6, 0, 6);
                    pastContainer.addView(more);
                }
            }
            binding.entriesContainer.addView(card);
            if (entry.getHorseNumber() != null) {
                oddsViews.put(entry.getHorseNumber().trim(), card.findViewById(R.id.text_entry_odds));
            }
            // 当日の出馬表と照合するため、馬名で引けるようにしておく（取り込み時に馬番が無くても照合できる）
            horseCards.put(FavoriteHorseNames.normalize(entry.getHorseName()), card);
        }
        // 行がフォーカスを取ってスクロール位置がずれないよう、先頭から表示する
        binding.scrollEntries.post(() -> {
            if (binding != null) binding.scrollEntries.scrollTo(0, 0);
        });
        loadOdds(entries);
        loadRaceCard(entries);
        loadTodayResult(entries.get(0));
    }

    /**
     * 当日の馬体重・出走取消・騎手変更と、天気・馬場を出す（PC のバッチが発走前に入れたもの）。
     * 朝の取り込みより後の変更を反映するため、出走表を開くたびに読み直す。枠順確定前は何もしない
     */
    private void loadRaceCard(List<SupabaseManager.RaceEntry> entries) {
        // 終わった開催は取りに行かない（取り込み済みの馬番で足りる）
        String today = org.threeten.bp.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (horseCards.isEmpty() || entries.get(0).getKaisaibi().compareTo(today) < 0) return;
        SupabaseManager.RaceEntry first = entries.get(0);
        ODDS_EXECUTOR.execute(() -> {
            LiveRaceCard.Card card;
            try {
                card = LiveRaceCard.fetchBlocking(first.getKaisaibi(), first.getKaisaijo(), first.getRaceNo());
            } catch (Exception e) {
                Log.w("EntryDetailFragment", "当日の出馬表の取得に失敗", e);
                return;
            }
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (binding == null || !isAdded()) return;
                showRaceCard(entries, card);
            });
        });
    }

    private void showRaceCard(List<SupabaseManager.RaceEntry> entries, LiveRaceCard.Card card) {
        String condition = card.conditionLabel();
        if (!condition.isEmpty()) binding.textEntriesLead.append("\n" + condition);
        int scratchedCount = 0;
        Map<String, LiveRaceCard.Horse> byName = new HashMap<>();
        for (LiveRaceCard.Horse h : card.horses.values()) {
            if (h.name != null) byName.put(FavoriteHorseNames.normalize(h.name), h);
        }
        boolean drawnNow = false;
        for (SupabaseManager.RaceEntry entry : entries) {
            String key = FavoriteHorseNames.normalize(entry.getHorseName());
            View view = horseCards.get(key);
            LiveRaceCard.Horse horse = byName.get(key);
            if (view == null || horse == null) continue;
            // 取り込み時は枠順確定前で馬番が無かった馬にも、当日の出馬表の馬番と枠の色を付ける
            if (entry.getHorseNumber() == null && !horse.number.isEmpty()) {
                TextView number = view.findViewById(R.id.text_entry_number);
                number.setText(horse.number);
                number.setVisibility(View.VISIBLE);
                styleWaku(number, horse.waku);
                drawnNow = true;
            }
            TextView today = view.findViewById(R.id.text_entry_today);
            SpannableStringBuilder text = new SpannableStringBuilder();
            if (horse.scratched) {
                scratchedCount++;
                appendStyled(text, Lang.t("出走取消・除外", "Scratched"), 0xFF4A7A1E);
                // 取り消された馬は薄く表示し、オッズは出さない
                view.setAlpha(0.45f);
                view.findViewById(R.id.text_entry_odds).setVisibility(View.GONE);
            } else {
                if (horse.weight != null) {
                    text.append(Lang.t("馬体重 ", "Weight "));
                    appendStyled(text, horse.weight, weightColor(horse.weight));
                }
                String jockeyBefore = entry.getJockey() != null ? entry.getJockey().trim() : null;
                if (horse.jockey != null && jockeyBefore != null && !sameJockey(jockeyBefore, horse.jockey)) {
                    if (text.length() > 0) text.append("　");
                    appendStyled(text, Lang.t("騎手変更 ", "Jockey change ") + jockeyBefore + "→" + horse.jockey, 0xFF4A7A1E);
                }
            }
            if (text.length() == 0) continue;
            today.setText(text);
            today.setVisibility(View.VISIBLE);
        }
        if (scratchedCount > 0) binding.textEntriesLead.append("\n" + Lang.t("出走取消・除外 ", "Scratched: ") + Lang.horses(scratchedCount));
        if (drawnNow) binding.textEntriesLead.append(Lang.t("\n枠順が確定しました（馬番は最新の出馬表から表示）", "\nThe draw is out (numbers from the latest race card)"));
    }

    private void loadBets() {
        if (!Bets.isSignedIn() || raceForBets == null) {
            renderBets();
            return;
        }
        SupabaseManager.RaceEntry race = raceForBets;
        Bets.loadAll(all -> {
            if (binding == null || !isAdded()) return;
            List<Bets.Bet> mine = new ArrayList<>();
            for (Bets.Bet b : all) {
                if (b.sameRace(race.getKaisaibi(), race.getKaisaijo(), race.getRaceNo())) mine.add(b);
            }
            raceBets = payouts.isEmpty() ? mine : Bets.settle(mine, payouts);
            renderBets();
        });
    }

    /** 買い目の一覧・記録ボタン・払い戻しの表を出し直す */
    private void renderBets() {
        if (betsSection == null || binding == null) return;
        betsSection.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        if (!payouts.isEmpty()) betsSection.addView(createPayoutView(density));

        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * density);
        box.setPadding(pad, (int) (8 * density), pad, (int) (8 * density));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10 * density);
        bg.setColor(0xFFF8FBF2);
        box.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, (int) (8 * density));
        box.setLayoutParams(params);

        Bets.Summary summary = new Bets.Summary();
        for (Bets.Bet b : raceBets) {
            summary.add(b);
            TextView row = new TextView(requireContext());
            SpannableStringBuilder text = new SpannableStringBuilder("🎫 " + b.label());
            if (b.isSettled()) {
                text.append("　");
                if (b.payout > 0) appendStyled(text, Lang.t("的中 ", "Won ") + Lang.yen(b.payout), 0xFF4A7A1E);
                else text.append(Lang.t("はずれ", "Lost"));
            }
            row.setText(text);
            row.setTextSize(14);
            row.setTextColor(0xFF2F3B22);
            row.setPadding(0, (int) (4 * density), 0, (int) (4 * density));
            row.setOnLongClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setMessage(Lang.t(b.label() + " を消しますか？", "Delete " + b.label() + "?"))
                        .setPositiveButton(Lang.t("消す", "Delete"), (d, w) -> {
                            Bets.delete(b);
                            raceBets.remove(b);
                            renderBets();
                        })
                        .setNegativeButton(Lang.t("キャンセル", "Cancel"), null)
                        .show();
                return true;
            });
            box.addView(row);
        }
        if (summary.count > 0) {
            TextView total = new TextView(requireContext());
            total.setText(summary.label());
            total.setTextSize(13);
            total.setTextColor(0xFF56664A);
            box.addView(total);
        }
        TextView add = new TextView(requireContext());
        add.setText(raceBets.isEmpty() ? Lang.t("🎫 買い目を記録する（記録だけで、馬券は買いません）", "🎫 Log a bet (records only, no real betting)") : Lang.t("＋ 買い目を追加（長押しで消す）", "+ Add a bet (long-press to delete)"));
        add.setTextSize(14);
        add.setTextColor(0xFF4A7A1E);
        add.setPadding(0, (int) (6 * density), 0, (int) (6 * density));
        add.setOnClickListener(v -> showAddBetDialog());
        box.addView(add);
        betsSection.addView(box);
    }

    /** 払い戻しの表（券種ごとに1行） */
    private View createPayoutView(float density) {
        View view = PayoutTable.create(requireContext(), payouts);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, (int) (8 * density));
        view.setLayoutParams(params);
        return view;
    }

    private void showAddBetDialog() {
        if (!Bets.isSignedIn()) {
            Toast.makeText(getContext(), Lang.t("買い目を記録するにはログインが必要です", "Sign in to log bets"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (raceForBets == null) return;
        float density = getResources().getDisplayMetrics().density;
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * density);
        form.setPadding(pad, (int) (8 * density), pad, 0);

        android.widget.Spinner type = new android.widget.Spinner(requireContext());
        // 表示は言語に合わせ、記録は日本語の券種名で残す（払い戻しとの照合に使うため）
        List<String> typeLabels = new ArrayList<>();
        for (String t : LivePayouts.TYPES) typeLabels.add(Lang.betType(t));
        type.setAdapter(new android.widget.ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, typeLabels));
        form.addView(type);
        android.widget.EditText numbers = new android.widget.EditText(requireContext());
        numbers.setHint(Lang.t("馬番（例: 5-10。枠連は枠番）", "Numbers (e.g. 5-10; gate numbers for Bracket Q)"));
        numbers.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        form.addView(numbers);
        android.widget.EditText amount = new android.widget.EditText(requireContext());
        amount.setHint(Lang.t("金額（円）", "Amount (¥)"));
        amount.setText("100");
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        form.addView(amount);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(Lang.t("買い目を記録", "Log a bet"))
                .setView(form)
                .setPositiveButton(Lang.t("記録", "Save"), null)
                .setNegativeButton(Lang.t("キャンセル", "Cancel"), null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String t = LivePayouts.TYPES.get(type.getSelectedItemPosition());
            String n = numbers.getText().toString().trim();
            int yen;
            try {
                yen = Integer.parseInt(amount.getText().toString().trim());
            } catch (NumberFormatException e) {
                yen = 0;
            }
            String error = Bets.validate(t, n, yen);
            if (error != null) {
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                return;
            }
            SupabaseManager.RaceEntry r = raceForBets;
            Bets.add(r.getKaisaibi(), r.getKaisaijo(), r.getRaceNo(), r.getRaceTitle(), t, n, yen);
            dialog.dismiss();
            loadBets();
        }));
        dialog.show();
    }

    /** 増減が大きい（±10kg以上）ときは目立たせる */
    private static int weightColor(String weight) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(([+-]?\\d+)\\)").matcher(weight);
        if (m.find() && Math.abs(Integer.parseInt(m.group(1))) >= 10) return 0xFF4A7A1E;
        return 0xFF2F3B22;
    }

    /** 出馬表の騎手名は略称で、取り込み時と同じとは限らないので、片方がもう片方で始まれば同じ騎手とみなす */
    static boolean sameJockey(String a, String b) {
        String x = a.replaceAll("[\\s▲△☆★◇]", ""), y = b.replaceAll("[\\s▲△☆★◇]", "");
        return x.startsWith(y) || y.startsWith(x);
    }

    private static void appendStyled(SpannableStringBuilder sb, String text, int color) {
        int from = sb.length();
        sb.append(text);
        sb.setSpan(new android.text.style.ForegroundColorSpan(color), from, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), from, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * 出走馬の一覧の上に出す、近走を10走まで広げるボタン（リワード広告）。
     * 今日すでに見ていれば、10走を表示中であることだけ出す
     */
    private View createRewardView(List<SupabaseManager.RaceEntry> entries) {
        float density = getResources().getDisplayMetrics().density;
        TextView view = new TextView(requireContext());
        // 広告なしのアカウントには、広告のボタンも「10走を表示中」も出さない
        if (AdFree.isOn(requireContext())) {
            view.setVisibility(View.GONE);
            return view;
        }
        int pad = (int) (12 * density);
        view.setPadding(pad, pad, pad, pad);
        view.setTextSize(14);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, (int) (4 * density), 0, (int) (8 * density));
        view.setLayoutParams(params);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10 * density);
        if (RecentRunsReward.isUnlocked(requireContext())) {
            view.setText(Lang.t("✓ 今日は近走を" + RecentRunsReward.REWARDED_RUNS + "走まで表示しています",
                    "✓ Showing the last " + RecentRunsReward.REWARDED_RUNS + " runs today"));
            view.setTextColor(0xFF4E7A27);
            bg.setColor(0xFFF1F8E4);
        } else {
            view.setText(Lang.t("▶ 広告を見ると、全馬の近走を" + RecentRunsReward.NORMAL_RUNS + "走→" + RecentRunsReward.REWARDED_RUNS + "走に増やせます（今日いっぱい）",
                    "▶ Watch an ad to see " + RecentRunsReward.REWARDED_RUNS + " recent runs instead of " + RecentRunsReward.NORMAL_RUNS + " for every horse (all day)"));
            view.setTextColor(0xFF4E7A27);
            view.setTypeface(view.getTypeface(), Typeface.BOLD);
            bg.setColor(0xFFF1F8E4);
            bg.setStroke((int) Math.max(1, density), 0xFF4A7A1E);
            // ボタンを出すときに読み込んでおく（押されそうなときだけリクエストする）
            reward.preload(requireContext());
            view.setOnClickListener(v -> watchRewardAd(entries));
        }
        view.setBackground(bg);
        return view;
    }

    /** リワード広告を出し、最後まで見たら近走を10走まで広げて描き直す */
    private void watchRewardAd(List<SupabaseManager.RaceEntry> entries) {
        reward.show(requireActivity(), new RecentRunsReward.Listener() {
            @Override
            public void onRewarded() {
                if (binding == null || !isAdded()) return;
                Toast.makeText(getContext(), Lang.t("今日いっぱい、近走を" + RecentRunsReward.REWARDED_RUNS + "走まで表示します",
                        "Showing the last " + RecentRunsReward.REWARDED_RUNS + " runs for the rest of today"),
                        Toast.LENGTH_SHORT).show();
                // 10走分はもう読んであるので、表示だけ広げる
                showHeader(entries);
                render(entries, lastResults);
            }

            @Override
            public void onNotShown(String message) {
                if (isAdded()) Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 印をタップするたびに ◎→○→▲→△→なし。◎○▲は1頭だけなので、ほかの馬の印も書き直す */
    private void bindMark(View card, SupabaseManager.RaceEntry entry) {
        TextView view = card.findViewById(R.id.text_entry_mark);
        String key = FavoriteHorseNames.normalize(entry.getHorseName());
        markViews.put(key, view);
        paintMark(view, marks.get(key));
        view.setOnClickListener(v -> {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Toast.makeText(getContext(), Lang.t("予想の印を付けるにはログインが必要です", "Sign in to mark picks"), Toast.LENGTH_SHORT).show();
                return;
            }
            Predictions.setMark(entry.getKaisaibi(), entry.getKaisaijo(), entry.getRaceNo(), entry.getRaceTitle(),
                    marks, entry.getHorseName(), Predictions.nextMark(marks.get(key)));
            for (Map.Entry<String, TextView> e : markViews.entrySet()) paintMark(e.getValue(), marks.get(e.getKey()));
        });
    }

    private static void paintMark(TextView view, @Nullable String mark) {
        view.setText(mark != null ? mark : Lang.t("印", "Pick"));
        view.setTextSize(mark != null ? 20 : 12);
        view.setTextColor(mark == null ? 0xFFBDBDBD : com.tktkcompany.kakoRaceKeiba.ui.theme.RaceColors.mark(mark));
    }

    /**
     * 今日のレースで発走から時間がたっていれば、結果（PC のバッチが発走の数分後に入れたもの）を読んで各馬に着順を出す。
     * 印を付けていれば、◎の着順も画面の上に出す（答え合わせ）
     */
    private void loadTodayResult(SupabaseManager.RaceEntry race) {
        org.threeten.bp.LocalDateTime start = RaceStartNotices.startTime(race.getKaisaibi(), race.getRaceInfo());
        if (start == null || org.threeten.bp.LocalDateTime.now().isBefore(start.plusMinutes(10))) return;
        if (!race.getKaisaibi().equals(org.threeten.bp.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))) return;
        ODDS_EXECUTOR.execute(() -> {
            List<SupabaseManager.RaceResult> rows;
            List<LivePayouts.Payout> fetched;
            try {
                rows = LiveResults.fetchRaceBlocking(race.getKaisaibi(), race.getKaisaijo(), race.getRaceNo());
                fetched = rows.isEmpty() ? new ArrayList<>()
                        : LivePayouts.fetchBlocking(race.getKaisaibi(), race.getKaisaijo(), race.getRaceNo());
            } catch (Exception e) {
                Log.w("EntryDetailFragment", "結果の取得に失敗", e);
                return;
            }
            if (rows.isEmpty() || getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (binding == null || !isAdded()) return;
                showResult(rows);
                payouts = fetched;
                raceBets = Bets.settle(raceBets, payouts);
                renderBets();
            });
        });
    }

    private void showResult(List<SupabaseManager.RaceResult> rows) {
        String honmeiLine = null;
        for (SupabaseManager.RaceResult r : rows) {
            String key = FavoriteHorseNames.normalize(r.getHorseName());
            TextView view = resultViews.get(key);
            if (view == null) continue;
            String rank = r.getRank() != null ? r.getRank() : "-";
            String label = Lang.t("結果 ", "Result ") + Lang.rank(rank);
            if (r.getPopular() != null) label += " (" + Lang.popularity(r.getPopular()) + ")";
            view.setText(label);
            view.setTextColor(rank.matches("[123]") ? com.tktkcompany.kakoRaceKeiba.ui.theme.RaceColors.rank(rank) : 0xFF56664A);
            view.setVisibility(View.VISIBLE);
            if ("◎".equals(marks.get(key))) {
                honmeiLine = "◎" + r.getHorseName() + Lang.t("は", ": ") + Lang.rank(rank)
                        + ("1".equals(rank) ? Lang.t("　的中！", "  Winner!") : "");
            }
        }
        binding.textEntriesLead.append("\n" + (honmeiLine != null ? honmeiLine : Lang.t("結果が確定しました", "Result is official")));
    }

    /**
     * 今の単勝・複勝オッズ（PC のバッチが入れたもの）を読んで各馬に出す。
     * 取れなければ、取り込み済みの単勝オッズ（当日のバッチで更新）を出す。枠順確定前は何もしない
     */
    private void loadOdds(List<SupabaseManager.RaceEntry> entries) {
        if (oddsViews.isEmpty()) return;
        SupabaseManager.RaceEntry first = entries.get(0);
        ODDS_EXECUTOR.execute(() -> {
            Map<String, LiveOdds.HorseOdds> live = Collections.emptyMap();
            try {
                live = LiveOdds.fetchOddsTableBlocking(first.getKaisaibi(), first.getKaisaijo(), first.getRaceNo());
            } catch (Exception e) {
                Log.w("EntryDetailFragment", "オッズの取得に失敗（取り込み済みのオッズを出す）", e);
            }
            Map<String, LiveOdds.HorseOdds> odds = live;
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("H:mm"));
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (binding == null || !isAdded()) return;
                showOdds(entries, odds, time);
            });
        });
    }

    private void showOdds(List<SupabaseManager.RaceEntry> entries, Map<String, LiveOdds.HorseOdds> live, String liveTime) {
        String shownTime = null;
        for (SupabaseManager.RaceEntry entry : entries) {
            if (entry.getHorseNumber() == null) continue;
            String number = entry.getHorseNumber().trim();
            TextView view = oddsViews.get(number);
            if (view == null) continue;
            LiveOdds.HorseOdds odds = live.get(number.replaceFirst("^0+(?=\\d)", ""));
            String time = liveTime;
            if (odds == null && entry.getWinOdds() != null) {
                odds = new LiveOdds.HorseOdds(entry.getWinOdds(), null, null, parseIntOrNull(entry.getPopular()));
                time = storedTime(entry.getOddsUpdatedAt());
            }
            String label = odds != null ? odds.label() : "";
            if (label.isEmpty()) continue;
            view.setText(label);
            view.setVisibility(View.VISIBLE);
            if (shownTime == null) shownTime = time;
        }
        if (shownTime != null) {
            binding.textEntriesLead.append(Lang.t("\nオッズは" + shownTime + "時点（開き直すと更新します）", "\nOdds as of " + shownTime + " (reopen to refresh)"));
        }
    }

    @Nullable
    private static Integer parseIntOrNull(@Nullable String s) {
        if (s == null) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 取り込んだ時刻（"2026-10-17T04:15:00+00:00"）を端末の時刻の "13:15" にする。読めなければ null */
    @Nullable
    private static String storedTime(@Nullable String timestamp) {
        if (timestamp == null) return null;
        try {
            return OffsetDateTime.parse(timestamp).atZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("H:mm"));
        } catch (DateTimeException e) {
            return null;
        }
    }

    private void bindFavorite(View card, String horseName) {
        TextView star = card.findViewById(R.id.text_entry_favorite);
        View cardView = card.findViewById(R.id.card_entry_horse);
        Runnable paint = () -> {
            boolean fav = FavoriteHorseNames.contains(favorites, horseName);
            star.setText(fav ? "★" : "☆");
            star.setTextColor(fav ? 0xFFFFB300 : 0xFFBDBDBD);
            ((MaterialCardView) cardView)
                    .setCardBackgroundColor(fav ? 0xFFF1F8E4 : 0xFFFFFFFF);
        };
        paint.run();
        star.setOnClickListener(v -> {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                FavoriteHorseNames.showCannotSave(requireContext());
                return;
            }
            boolean added = FavoriteHorseNames.toggle(favorites, horseName);
            paint.run();
            if (added) {
                // このレースの発走前の通知をすぐ予約する（定期チェックを待つと間に合わないことがある）
                FavoriteResultWorker.runOnce(requireContext());
                FavoriteHorseNames.showAdded(requireContext(), horseName);
            } else {
                Toast.makeText(getContext(), Lang.t(horseName + "を推し馬から外しました", "Removed " + horseName + " from favorites"), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindMemo(View card, String horseName) {
        TextView button = card.findViewById(R.id.text_entry_memo);
        TextView body = card.findViewById(R.id.text_entry_memo_body);
        Runnable paint = () -> {
            HorseMemos.Memo memo = HorseMemos.find(memos, horseName);
            button.setAlpha(memo != null ? 1f : 0.35f);
            if (memo == null) {
                body.setVisibility(View.GONE);
            } else {
                body.setText("📝 " + memo.text);
                body.setVisibility(View.VISIBLE);
            }
        };
        paint.run();
        View.OnClickListener edit = v -> HorseMemoDialog.show(requireContext(), memos, horseName, raceLabel,
                memo -> paint.run());
        button.setOnClickListener(edit);
        body.setOnClickListener(edit);
    }

    private void showBadges(ChipGroup group, SupabaseManager.RaceEntry entry, List<SupabaseManager.RaceResult> runs) {
        List<EntryForm.Run> formRuns = new ArrayList<>();
        if (runs != null) {
            for (SupabaseManager.RaceResult r : runs) {
                formRuns.add(new EntryForm.Run(r.getKaisaibi(), r.getHassouTime(), r.getRank(), r.getJockey()));
            }
        }
        List<String> badges = EntryForm.badges(raceDate, entry.getRaceInfo(), entry.getJockey(), formRuns);
        if (badges.isEmpty()) {
            group.setVisibility(View.GONE);
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        for (String text : badges) {
            // チップより背の低い枠線つきのラベルにする（1頭あたりの高さを抑えるため）
            boolean caution = text.equals("休み明け") || text.equals("連闘") || text.equals("乗り替わり") || text.contains("→");
            TextView badge = new TextView(requireContext());
            badge.setText(EntryForm.label(text));
            badge.setTextSize(11);
            badge.setTextColor(caution ? 0xFFB86A00 : 0xFF4E7A27);
            badge.setPadding((int) (8 * density), (int) (2 * density), (int) (8 * density), (int) (2 * density));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(10 * density);
            bg.setStroke((int) Math.max(1, density), caution ? 0xFFB86A00 : 0xFF7CB342);
            badge.setBackground(bg);
            group.addView(badge);
        }
    }

    private void styleWaku(TextView view, String waku) {
        int index = 0;
        try {
            index = Integer.parseInt(waku != null ? waku.trim() : "") - 1;
        } catch (NumberFormatException ignored) {
        }
        if (index < 0 || index >= WAKU_BACKGROUND.length) index = 0;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(6 * getResources().getDisplayMetrics().density);
        bg.setColor(WAKU_BACKGROUND[index]);
        bg.setStroke(2, 0xFFBDBDBD);
        view.setBackground(bg);
        view.setTextColor(WAKU_TEXT[index]);
    }

    private View createPastRunView(LayoutInflater inflater, ViewGroup parent, SupabaseManager.RaceResult run) {
        View row = inflater.inflate(R.layout.item_past_run, parent, false);
        String date = WeekendDays.formatShort(run.getKaisaibi());
        // 「2026年10月12日(月)」→「2026年10月12日」（列の幅を揃えるため曜日は省く）
        int paren = date.indexOf('(');
        ((TextView) row.findViewById(R.id.text_run_date)).setText(paren > 0 ? date.substring(0, paren) : date);
        ((TextView) row.findViewById(R.id.text_run_course)).setText(run.getKaisaijo());
        ((TextView) row.findViewById(R.id.text_run_title)).setText(run.getRaceTitle() != null ? run.getRaceTitle().trim() : "");
        ((TextView) row.findViewById(R.id.text_run_distance)).setText(EntryForm.courseText(EntryForm.courseLabel(run.getHassouTime())));

        String rank = run.getRank() != null ? run.getRank() : "-";
        String result = Lang.rank(rank);
        if (run.getPopular() != null && !run.getPopular().isEmpty()) result += " " + Lang.t(run.getPopular() + "人", "(" + run.getPopular() + ")");
        TextView resultView = row.findViewById(R.id.text_run_result);
        resultView.setText(result);
        if ("1".equals(rank)) {
            resultView.setTextColor(0xFF4A7A1E);
            resultView.setTypeface(resultView.getTypeface(), Typeface.BOLD);
        } else if ("2".equals(rank) || "3".equals(rank)) {
            resultView.setTextColor(0xFF4E7A27);
            resultView.setTypeface(resultView.getTypeface(), Typeface.BOLD);
        } else {
            resultView.setTextColor(0xFF2F3B22);
        }

        // その日の結果画面を、このレースまでスクロールしてこの馬の行に色を付けた状態で開く
        row.setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.navigation_raceResults,
                RaceResultsFragment.args(run.getKaisaibi(), run.getKaisaijo(), run.getRaceNo(), run.getHorseName())));
        return row;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
