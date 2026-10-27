package com.tktkcompany.kakoRaceKeiba.ui.entries;

import com.tktkcompany.kakoRaceKeiba.util.Lang;
import com.tktkcompany.kakoRaceKeiba.ads.AdUnits;
import com.tktkcompany.kakoRaceKeiba.ads.BannerAd;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.card.MaterialCardView;
import com.tktkcompany.kakoRaceKeiba.R;
import com.tktkcompany.kakoRaceKeiba.databinding.FragmentEntriesBinding;
import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;
import com.tktkcompany.kakoRaceKeiba.notification.RaceStartNotices;
import com.tktkcompany.kakoRaceKeiba.util.WeekendDays;

import org.threeten.bp.LocalDate;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 今週の出走馬：開催日・競馬場ごとにレースを並べ、タップで出走馬の近走成績へ進む */
public class EntriesFragment extends Fragment {

    public static final String ARG_DATE = "date";
    public static final String ARG_COURSE = "course";
    public static final String ARG_RACE_NO = "raceNo";
    public static final String ARG_RACE_TITLE = "raceTitle";

    private FragmentEntriesBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentEntriesBinding.inflate(inflater, container, false);
        binding.textEntriesLead.setText(Lang.t("レースを選ぶと、出走馬ごとの近走成績を確認できます", "Pick a race to see each runner's recent form"));
        BannerAd.show(this, binding.adContainer, AdUnits.BANNER_ENTRIES);
        loadEntries();
        return binding.getRoot();
    }

    private void loadEntries() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        SupabaseManager.fetchUpcomingEntries(today, new SupabaseManager.SupabaseCallback<List<SupabaseManager.RaceEntry>>() {
            @Override
            public void onSuccess(List<SupabaseManager.RaceEntry> entries) {
                if (binding == null || !isAdded()) return;
                binding.progressBar.setVisibility(View.GONE);
                if (entries.isEmpty()) {
                    showEmpty(Lang.t("今週の出走馬はまだ発表されていません。\n出馬表が確定すると、ここに表示されます。", "This week's runners aren't out yet.\nThey'll appear here once the race cards are published."));
                    return;
                }
                FavoriteHorseNames.load(favorites -> {
                    if (binding == null || !isAdded()) return;
                    render(entries, favorites);
                });
            }

            @Override
            public void onFailure(@NonNull String message) {
                Log.e("EntriesFragment", "Supabase load failed: " + message);
                if (binding == null || !isAdded()) return;
                binding.progressBar.setVisibility(View.GONE);
                showEmpty(Lang.t("出走馬の読み込みに失敗しました", "Couldn't load runners"));
            }
        });
    }

    private void showEmpty(String message) {
        binding.textEntriesEmpty.setText(message);
        binding.textEntriesEmpty.setVisibility(View.VISIBLE);
    }

    private void render(List<SupabaseManager.RaceEntry> entries, Set<String> favorites) {
        // 開催日・競馬場 → レース番号 → 出走馬 の順にまとめる（取得時点で並び替え済み）
        Map<String, Map<Integer, List<SupabaseManager.RaceEntry>>> meetings = new LinkedHashMap<>();
        for (SupabaseManager.RaceEntry entry : entries) {
            String key = entry.getKaisaibi() + "\t" + entry.getKaisaijo();
            Map<Integer, List<SupabaseManager.RaceEntry>> races = meetings.get(key);
            if (races == null) {
                races = new LinkedHashMap<>();
                meetings.put(key, races);
            }
            List<SupabaseManager.RaceEntry> horses = races.get(entry.getRaceNo());
            if (horses == null) {
                horses = new ArrayList<>();
                races.put(entry.getRaceNo(), horses);
            }
            horses.add(entry);
        }

        LayoutInflater inflater = getLayoutInflater();
        binding.entriesContainer.removeAllViews();
        View firstNextRace = null;
        for (Map.Entry<String, Map<Integer, List<SupabaseManager.RaceEntry>>> meeting : meetings.entrySet()) {
            String[] key = meeting.getKey().split("\t");
            String date = key[0];
            String course = key[1];

            TextView header = new TextView(requireContext());
            header.setText(WeekendDays.formatShort(date) + " " + Lang.course(course));
            header.setTextSize(18);
            header.setTextColor(0xFF333333);
            header.setTypeface(header.getTypeface(), Typeface.BOLD);
            header.setPadding(0, 32, 0, 8);
            binding.entriesContainer.addView(header);
            int nextRaceNo = nextRaceNo(date, meeting.getValue());

            for (Map.Entry<Integer, List<SupabaseManager.RaceEntry>> race : meeting.getValue().entrySet()) {
                int raceNo = race.getKey();
                SupabaseManager.RaceEntry first = race.getValue().get(0);
                String title = first.getRaceTitle() != null ? first.getRaceTitle() : "";

                View item = inflater.inflate(R.layout.item_race_day, binding.entriesContainer, false);
                // 「7R」の下に小さく発走時刻
                LocalDateTime start = RaceStartNotices.startTime(date, first.getRaceInfo());
                SpannableStringBuilder number = new SpannableStringBuilder(raceNo + "R");
                if (start != null) {
                    int from = number.length();
                    number.append("\n").append(start.format(DateTimeFormatter.ofPattern("H:mm"))).append(Lang.t("発走", ""));
                    number.setSpan(new RelativeSizeSpan(0.75f), from, number.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    number.setSpan(new ForegroundColorSpan(0xFF666666), from, number.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                ((TextView) item.findViewById(R.id.text_race_day_date)).setText(number);
                boolean next = raceNo == nextRaceNo;
                if (next) {
                    highlightNextRace((MaterialCardView) item);
                    if (firstNextRace == null) firstNextRace = item;
                }
                boolean beforeDraw = true;
                int favoriteCount = 0;
                for (SupabaseManager.RaceEntry e : race.getValue()) {
                    if (e.getHorseNumber() != null) beforeDraw = false;
                    if (FavoriteHorseNames.contains(favorites, e.getHorseName())) favoriteCount++;
                }
                String courseLabel = EntryForm.courseText(EntryForm.courseLabel(first.getRaceInfo()));
                String count = Lang.ja() ? (beforeDraw ? "登録" : "") + race.getValue().size() + "頭"
                        : race.getValue().size() + (beforeDraw ? " nominated" : " runners");
                ((TextView) item.findViewById(R.id.text_race_day_main)).setText(title);
                TextView sub = item.findViewById(R.id.text_race_day_sub);
                String subText = courseLabel.isEmpty() ? count : courseLabel + "m・" + count;
                if (next) subText = Lang.t("▶ 次のレース・", "▶ Next race・") + subText;
                if (favoriteCount > 0) {
                    // お気に入り馬が出るレースは目立たせる
                    subText += Lang.t("・★推し馬" + favoriteCount + "頭", "・★" + favoriteCount + " favorite" + (favoriteCount == 1 ? "" : "s"));
                    sub.setTextColor(0xFFE65100);
                }
                sub.setText(subText);
                sub.setVisibility(View.VISIBLE);
                item.setOnClickListener(v -> {
                    Bundle args = new Bundle();
                    args.putString(ARG_DATE, date);
                    args.putString(ARG_COURSE, course);
                    args.putInt(ARG_RACE_NO, raceNo);
                    args.putString(ARG_RACE_TITLE, title);
                    Navigation.findNavController(v).navigate(R.id.action_navigation_entries_to_navigation_entryDetail, args);
                });
                binding.entriesContainer.addView(item);
            }
        }
        // 開催日は、最初の競馬場の次のレースが画面の上の方に来るようにスクロールする
        if (firstNextRace != null) {
            View target = firstNextRace;
            binding.scrollEntries.post(() -> {
                if (binding == null) return;
                int y = binding.entriesContainer.getTop() + target.getTop()
                        - (int) (48 * getResources().getDisplayMetrics().density);
                binding.scrollEntries.scrollTo(0, Math.max(0, y));
            });
        }
    }

    /** 今日の開催なら、まだ発走していない最初のレース番号。今日でない・全レース発走済みなら -1 */
    static int nextRaceNo(String date, Map<Integer, List<SupabaseManager.RaceEntry>> races) {
        LocalDateTime now = LocalDateTime.now();
        if (!date.equals(now.toLocalDate().format(DateTimeFormatter.ofPattern("yyyyMMdd")))) return -1;
        for (Map.Entry<Integer, List<SupabaseManager.RaceEntry>> race : races.entrySet()) {
            LocalDateTime start = RaceStartNotices.startTime(date, race.getValue().get(0).getRaceInfo());
            if (start != null && start.isAfter(now)) return race.getKey();
        }
        return -1;
    }

    /** 次のレースは緑の枠と背景で目立たせる */
    private void highlightNextRace(MaterialCardView card) {
        float density = getResources().getDisplayMetrics().density;
        card.setCardBackgroundColor(0xFFE8F5E9);
        card.setStrokeColor(0xFF43A047);
        card.setStrokeWidth((int) (2 * density));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
