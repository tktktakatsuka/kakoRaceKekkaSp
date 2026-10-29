package com.tktkcompany.kakoRaceKeiba.ui.prediction;

import com.tktkcompany.kakoRaceKeiba.ui.theme.Palette;
import com.tktkcompany.kakoRaceKeiba.ui.theme.RaceColors;
import com.tktkcompany.kakoRaceKeiba.util.Lang;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.tktkcompany.kakoRaceKeiba.R;
import com.tktkcompany.kakoRaceKeiba.databinding.FragmentEntriesBinding;
import com.tktkcompany.kakoRaceKeiba.db.LivePayouts;
import com.tktkcompany.kakoRaceKeiba.db.LiveResults;
import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;
import com.tktkcompany.kakoRaceKeiba.ui.entries.EntriesFragment;
import com.tktkcompany.kakoRaceKeiba.ui.entries.FavoriteHorseNames;
import com.tktkcompany.kakoRaceKeiba.ui.raceResult.RaceResultsFragment;
import com.tktkcompany.kakoRaceKeiba.util.WeekendDays;

import org.threeten.bp.LocalDate;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 自分の予想（◎○▲△）の答え合わせ。予想したレースを新しい順に並べ、各印の馬の着順と◎の成績を出す。
 * 着順は race_results から読む（今日のレースも PC のバッチが発走の数分後に入れる）。
 */
public class PredictionsFragment extends Fragment {

    private static final String TAG = "PredictionsFragment";
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private FragmentEntriesBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentEntriesBinding.inflate(inflater, container, false);
        binding.textEntriesLead.setText(Lang.t("出走表で馬の左の「印」をタップすると、◎○▲△の予想を付けられます。レース後にここで答え合わせできます",
                "Tap \"Pick\" next to a horse on a race card to mark ◎○▲△. Check how you did here after the race"));
        Predictions.loadAll(races -> Bets.loadAll(bets -> {
            if (binding == null || !isAdded()) return;
            if (races.isEmpty() && bets.isEmpty()) {
                binding.progressBar.setVisibility(View.GONE);
                binding.textEntriesEmpty.setText(Lang.t("まだ予想も買い目の記録もありません", "No picks or bets yet"));
                binding.textEntriesEmpty.setVisibility(View.VISIBLE);
                return;
            }
            EXECUTOR.execute(() -> {
                Map<String, Map<String, String>> ranks = races.isEmpty() ? new HashMap<>() : loadRanks(races);
                List<Bets.Bet> settled = settleFinished(bets);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (binding == null || !isAdded()) return;
                    binding.progressBar.setVisibility(View.GONE);
                    render(races, ranks);
                    renderBets(settled);
                });
            });
        }));
        return binding.getRoot();
    }

    /** 結果のまだ入っていない買い目のうち、レースが終わっているものの払い戻しを取って入れる（レースごとに1回） */
    private static List<Bets.Bet> settleFinished(List<Bets.Bet> bets) {
        String today = LocalDate.now().format(YMD);
        Map<String, List<LivePayouts.Payout>> cache = new HashMap<>();
        List<Bets.Bet> result = new java.util.ArrayList<>();
        for (Bets.Bet b : bets) {
            if (b.isSettled() || b.date.compareTo(today) > 0) {
                result.add(b);
                continue;
            }
            String key = raceKey(b.date, b.course, b.raceNo);
            List<LivePayouts.Payout> payouts = cache.get(key);
            if (payouts == null) {
                try {
                    payouts = LivePayouts.fetchBlocking(b.date, b.course, b.raceNo);
                } catch (Exception e) {
                    Log.w(TAG, "払い戻しの取得に失敗", e);
                    payouts = new java.util.ArrayList<>();
                }
                cache.put(key, payouts);
            }
            result.addAll(Bets.settle(java.util.Collections.singletonList(b), payouts));
        }
        return result;
    }

    /** 買い目の収支（全体と最新の開催日）と、買い目の一覧 */
    private void renderBets(List<Bets.Bet> bets) {
        if (bets.isEmpty()) return;
        Bets.Summary all = new Bets.Summary();
        Bets.Summary latest = new Bets.Summary();
        String latestDate = bets.get(0).date;
        for (Bets.Bet b : bets) {
            all.add(b);
            if (b.date.equals(latestDate)) latest.add(b);
        }
        StringBuilder lead = new StringBuilder(binding.textEntriesLead.getText());
        lead.append(Lang.t("\n\n🎫 買い目の収支", "\n\n🎫 Bet results"));
        if (latest.count > 0) lead.append("\n").append(WeekendDays.formatShort(latestDate)).append("　").append(latest.label());
        if (all.count > latest.count) lead.append(Lang.t("\nこれまで　", "\nAll time  ")).append(all.label());
        if (all.count == 0) lead.append(Lang.t("\n結果が出たら、ここに投資・払戻・回収率を出します", "\nOnce results are in, your stake, returns and ROI appear here"));
        binding.textEntriesLead.setText(lead);

        TextView header = new TextView(requireContext());
        header.setText(Lang.t("🎫 記録した買い目", "🎫 Logged bets"));
        header.setTextSize(18);
        header.setTextColor(Palette.ink(requireContext()));
        header.setTypeface(header.getTypeface(), Typeface.BOLD);
        header.setPadding(0, 32, 0, 8);
        binding.entriesContainer.addView(header);
        for (Bets.Bet b : bets) {
            TextView row = new TextView(requireContext());
            SpannableStringBuilder text = new SpannableStringBuilder();
            text.append(WeekendDays.formatShort(b.date)).append(" ").append(Lang.race(b.course, b.raceNo)).append("　")
                    .append(b.label());
            if (b.isSettled()) {
                text.append("　");
                int from = text.length();
                text.append(b.payout > 0 ? Lang.t("的中 ", "Won ") + Lang.yen(b.payout) : Lang.t("はずれ", "Lost"));
                if (b.payout > 0) {
                    text.setSpan(new ForegroundColorSpan(0xFF4A7A1E), from, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    text.setSpan(new StyleSpan(Typeface.BOLD), from, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                text.append(Lang.t("　結果待ち", "  Awaiting result"));
            }
            row.setText(text);
            row.setTextSize(13);
            row.setTextColor(Palette.ink(requireContext()));
            row.setPadding(8, 10, 8, 10);
            binding.entriesContainer.addView(row);
        }
    }

    private static String raceKey(String date, String course, int raceNo) {
        return date + "|" + course + "|" + raceNo;
    }

    /** レース → (normalize した馬名 → 着順)。結果がまだのレースは入らない */
    private static Map<String, Map<String, String>> loadRanks(List<Predictions.Race> races) {
        Map<String, Map<String, String>> ranks = new HashMap<>();
        String today = LocalDate.now().format(YMD);
        Set<String> names = new HashSet<>();
        Set<String> courses = new HashSet<>();
        for (Predictions.Race race : races) {
            if (race.date.equals(today)) {
                try {
                    List<SupabaseManager.RaceResult> rows = LiveResults.fetchRaceBlocking(race.date, race.course, race.raceNo);
                    if (!rows.isEmpty()) ranks.put(raceKey(race.date, race.course, race.raceNo), toRanks(rows));
                } catch (Exception e) {
                    Log.w(TAG, "今日の結果の取得に失敗", e);
                }
            } else {
                names.addAll(race.marks.keySet());
                courses.add(race.course);
            }
        }
        if (!names.isEmpty()) {
            try {
                for (SupabaseManager.RaceResult r : SupabaseManager.fetchResultsForHorsesBlocking(names, courses)) {
                    String key = raceKey(r.getKaisaibi(), r.getKaisaijo(), r.getRaceNo());
                    Map<String, String> race = ranks.get(key);
                    if (race == null) {
                        race = new HashMap<>();
                        ranks.put(key, race);
                    }
                    race.put(FavoriteHorseNames.normalize(r.getHorseName()), r.getRank());
                }
            } catch (Exception e) {
                Log.w(TAG, "結果の取得に失敗", e);
            }
        }
        return ranks;
    }

    private static Map<String, String> toRanks(List<SupabaseManager.RaceResult> rows) {
        Map<String, String> ranks = new HashMap<>();
        for (SupabaseManager.RaceResult r : rows) ranks.put(FavoriteHorseNames.normalize(r.getHorseName()), r.getRank());
        return ranks;
    }

    private void render(List<Predictions.Race> races, Map<String, Map<String, String>> ranks) {
        binding.entriesContainer.removeAllViews();
        if (races.isEmpty()) {
            binding.textEntriesLead.setText(Lang.t("出走表で「印」をタップすると◎○▲△の予想を、「🎫 買い目を記録する」で買い目を残せます",
                    "On a race card, tap \"Pick\" to mark ◎○▲△, or \"🎫 Log a bet\" to keep your bets"));
            return;
        }
        PredictionStats all = new PredictionStats();
        PredictionStats latest = new PredictionStats();
        String latestDate = races.get(0).date;
        for (Predictions.Race race : races) {
            Map<String, String> r = ranks.get(raceKey(race.date, race.course, race.raceNo));
            if (r == null) continue;
            all.add(race, r);
            if (race.date.equals(latestDate)) latest.add(race, r);
        }

        StringBuilder lead = new StringBuilder();
        if (latest.races > 0) lead.append(WeekendDays.formatShort(latestDate)).append(Lang.t("の成績\n", "\n")).append(latest.summary());
        if (all.races > latest.races) {
            if (lead.length() > 0) lead.append("\n\n");
            lead.append(Lang.t("これまでの成績\n", "All time\n")).append(all.summary());
        }
        if (lead.length() == 0) lead.append(Lang.t("結果が出たら、ここに◎の成績を出します", "Once results are in, your ◎ record appears here"));
        binding.textEntriesLead.setText(lead);
        binding.textEntriesLead.setTextColor(Palette.inkSoft(requireContext()));

        LayoutInflater inflater = getLayoutInflater();
        binding.entriesContainer.removeAllViews();
        String shownDate = null;
        for (Predictions.Race race : races) {
            if (!race.date.equals(shownDate)) {
                shownDate = race.date;
                TextView header = new TextView(requireContext());
                header.setText(WeekendDays.formatShort(race.date));
                header.setTextSize(18);
                header.setTextColor(Palette.ink(requireContext()));
                header.setTypeface(header.getTypeface(), Typeface.BOLD);
                header.setPadding(0, 32, 0, 8);
                binding.entriesContainer.addView(header);
            }
            Map<String, String> r = ranks.get(raceKey(race.date, race.course, race.raceNo));
            View item = inflater.inflate(R.layout.item_race_day, binding.entriesContainer, false);
            ((TextView) item.findViewById(R.id.text_race_day_date)).setText(Lang.race(race.course, race.raceNo));
            ((TextView) item.findViewById(R.id.text_race_day_main)).setText(race.title != null ? race.title.trim() : "");
            TextView sub = item.findViewById(R.id.text_race_day_sub);
            sub.setText(marksLine(race, r));
            sub.setTextSize(13);
            sub.setVisibility(View.VISIBLE);
            item.setOnClickListener(v -> openRace(v, race));
            binding.entriesContainer.addView(item);
        }
    }

    /** 「◎アミラル 3着　○ビズロード 1着」（結果がまだなら着順なし） */
    private static CharSequence marksLine(Predictions.Race race, Map<String, String> ranks) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (String mark : Predictions.MARKS) {
            for (Map.Entry<String, String> e : race.marks.entrySet()) {
                if (!mark.equals(e.getValue())) continue;
                if (sb.length() > 0) sb.append("　");
                int from = sb.length();
                sb.append(mark);
                sb.setSpan(new ForegroundColorSpan(RaceColors.mark(mark)), from, sb.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append(e.getKey());
                String rank = ranks != null ? ranks.get(e.getKey()) : null;
                if (rank != null) {
                    from = sb.length();
                    sb.append(" ").append(Lang.rank(rank));
                    if (rank.matches("[123]")) {
                        sb.setSpan(new ForegroundColorSpan(RaceColors.rank(rank)), from, sb.length(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        sb.setSpan(new StyleSpan(Typeface.BOLD), from, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
        }
        if (ranks == null) sb.append(Lang.t("\n結果待ち", "\nAwaiting result"));
        return sb;
    }

    /** 出走表（今日以降）か、その日のレース結果を開く */
    private void openRace(View v, Predictions.Race race) {
        String today = LocalDate.now().format(YMD);
        Bundle args = new Bundle();
        if (race.date.compareTo(today) >= 0) {
            args.putString(EntriesFragment.ARG_DATE, race.date);
            args.putString(EntriesFragment.ARG_COURSE, race.course);
            args.putInt(EntriesFragment.ARG_RACE_NO, race.raceNo);
            args.putString(EntriesFragment.ARG_RACE_TITLE, race.title != null ? race.title : "");
            Navigation.findNavController(v).navigate(R.id.navigation_entryDetail, args);
        } else {
            // そのレースまでスクロールし、◎の馬の行に色を付けて開く
            Navigation.findNavController(v).navigate(R.id.navigation_raceResults,
                    RaceResultsFragment.args(race.date, race.course, race.raceNo, race.honmei()));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
