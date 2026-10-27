package com.tktkcompany.kakoRaceKeiba.ui.favorite;

import com.tktkcompany.kakoRaceKeiba.ads.AdUnits;
import com.tktkcompany.kakoRaceKeiba.ads.BannerAd;
import com.tktkcompany.kakoRaceKeiba.util.Lang;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.tktkcompany.kakoRaceKeiba.R;
import com.tktkcompany.kakoRaceKeiba.db.FavoriteResultFetcher;
import com.tktkcompany.kakoRaceKeiba.db.FirebaseManager;
import com.tktkcompany.kakoRaceKeiba.db.HorseResult;
import com.tktkcompany.kakoRaceKeiba.db.LiveResults;
import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;
import com.tktkcompany.kakoRaceKeiba.notification.RaceStartNotices;
import com.tktkcompany.kakoRaceKeiba.ui.entries.EntriesFragment;
import com.tktkcompany.kakoRaceKeiba.ui.entries.FavoriteHorseNames;
import com.tktkcompany.kakoRaceKeiba.ui.raceResult.RaceResultsFragment;

import org.threeten.bp.LocalDate;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FavoriteHorsesFragment extends Fragment {

    private RecyclerView recyclerView;
    private FavoriteHorsesAdapter adapter;
    private ProgressBar progressBar;
    private TextView noDataText;
    private List<String> favoriteHorses = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String listeningUid;
    private ValueEventListener favoritesListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_favorite_horses, container, false);

        recyclerView = root.findViewById(R.id.recycler_view_favorites);
        progressBar = root.findViewById(R.id.progress_bar);
        noDataText = root.findViewById(R.id.text_no_data);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FavoriteHorsesAdapter(favoriteHorses, this::onRemoveClick, this::onResultClick, this::onEntryClick);
        recyclerView.setAdapter(adapter);
        root.findViewById(R.id.btn_find_horse).setOnClickListener(v ->
                androidx.navigation.Navigation.findNavController(v).navigate(R.id.navigation_horseSearch));

        BannerAd.show(this, root.findViewById(R.id.ad_container), AdUnits.BANNER_FAVORITES);
        loadFavorites();

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // リアルタイム監視を解除する（画面を離れた後に呼ばれ続けないように）
        if (listeningUid != null && favoritesListener != null) {
            FirebaseManager.removeFavoriteHorsesListener(listeningUid, favoritesListener);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void loadFavorites() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            noDataText.setText(Lang.t("通信できないため、推し馬を読み込めませんでした", "Couldn't load favorites. Check your connection"));
            noDataText.setVisibility(View.VISIBLE);
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        noDataText.setVisibility(View.GONE);

        // addValueEventListener に変更してリアルタイム更新を有効にする
        favoritesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                favoriteHorses.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String name = child.getKey();
                    if (name != null) {
                        favoriteHorses.add(name);
                    }
                }

                progressBar.setVisibility(View.GONE);
                if (favoriteHorses.isEmpty()) {
                    noDataText.setVisibility(View.VISIBLE);
                    noDataText.setText(Lang.t("まだ推し馬がいません。\n上のボタンから応援したい馬を探して登録すると、出走が決まったとき・発走前・結果をお知らせします",
                            "No favorites yet.\nFind a horse above to get entry, post-time and result notifications"));
                } else {
                    noDataText.setVisibility(View.GONE);
                }
                adapter.clearLatestResults();
                adapter.refresh();
                loadLatestResults(new ArrayList<>(favoriteHorses));
                loadNextEntries(new ArrayList<>(favoriteHorses));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) {
                    progressBar.setVisibility(View.GONE);
                    Log.e("FavoriteHorses", "Load failed: " + error.getMessage());
                }
            }
        };
        listeningUid = user.getUid();
        FirebaseManager.getFavoriteHorses(listeningUid, favoritesListener);
    }

    // 各お気に入り馬の直近の結果をバックグラウンドで取得する
    private void loadLatestResults(List<String> horseKeys) {
        if (horseKeys.isEmpty() || executor.isShutdown()) return;
        executor.execute(() -> {
            Map<String, HorseResult> latest = new HashMap<>();
            try {
                // 前走はどの競馬場でもよいので、競馬場で絞らずに取る
                Map<String, List<HorseResult>> results = FavoriteResultFetcher.fetchResults(new HashSet<>(horseKeys), null);
                for (Map.Entry<String, List<HorseResult>> e : results.entrySet()) {
                    if (!e.getValue().isEmpty()) latest.put(e.getKey(), e.getValue().get(0));
                }
            } catch (Exception e) {
                Log.w("FavoriteHorses", "Latest results load failed", e);
            }
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (isAdded() && getView() != null) adapter.setLatestResults(latest);
            });
        });
    }

    // 今日以降の出馬表から、各お気に入り馬のいちばん近い出走予定を探す
    private void loadNextEntries(List<String> horseKeys) {
        if (horseKeys.isEmpty()) {
            adapter.setNextEntries(null);
            return;
        }
        Set<String> favorites = new HashSet<>();
        for (String key : horseKeys) favorites.add(FavoriteHorseNames.normalize(key));
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        SupabaseManager.fetchUpcomingEntries(today, new SupabaseManager.SupabaseCallback<List<SupabaseManager.RaceEntry>>() {
            @Override
            public void onSuccess(List<SupabaseManager.RaceEntry> entries) {
                if (!isAdded() || getView() == null) return;
                // entries は開催日・競馬場・レース番号の順に並んでいるので、最初に見つかったものが次走
                Map<String, SupabaseManager.RaceEntry> next = new HashMap<>();
                List<SupabaseManager.RaceEntry> finishedToday = new ArrayList<>();
                org.threeten.bp.LocalDateTime now = org.threeten.bp.LocalDateTime.now();
                for (SupabaseManager.RaceEntry e : entries) {
                    // 今日もう発走したレースは「出走予定」にせず、確定していそうなら速報の着順を取りに行く
                    org.threeten.bp.LocalDateTime start = RaceStartNotices.startTime(e.getKaisaibi(), e.getRaceInfo());
                    if (start != null && start.isBefore(now)) {
                        if (favorites.contains(FavoriteHorseNames.normalize(e.getHorseName()))
                                && start.plusMinutes(10).isBefore(now)) finishedToday.add(e);
                        continue;
                    }
                    String name = FavoriteHorseNames.normalize(e.getHorseName());
                    if (favorites.contains(name) && !next.containsKey(name)) next.put(name, e);
                }
                adapter.setNextEntries(next);
                loadLiveResults(horseKeys, finishedToday);
            }

            @Override
            public void onFailure(@NonNull String message) {
                Log.w("FavoriteHorses", "Upcoming entries load failed: " + message);
            }
        });
    }

    // 出走予定をタップしたら、そのレースの出走表（出走馬と近走成績）を開く
    private void onEntryClick(SupabaseManager.RaceEntry entry) {
        if (getView() == null) return;
        Bundle args = new Bundle();
        args.putString(EntriesFragment.ARG_DATE, entry.getKaisaibi());
        args.putString(EntriesFragment.ARG_COURSE, entry.getKaisaijo());
        args.putInt(EntriesFragment.ARG_RACE_NO, entry.getRaceNo());
        args.putString(EntriesFragment.ARG_RACE_TITLE, entry.getRaceTitle() != null ? entry.getRaceTitle() : "");
        Navigation.findNavController(getView()).navigate(R.id.navigation_entryDetail, args);
    }

    // 直近の結果をタップしたら、そのレース結果画面を開く
    private void onResultClick(HorseResult result) {
        if (result.kaisaibi == null || result.kaisaijo == null || getView() == null) return;
        int raceNo;
        try {
            raceNo = Integer.parseInt(result.raceNo.trim());
        } catch (RuntimeException e) {
            raceNo = 0;
        }
        Navigation.findNavController(getView()).navigate(R.id.navigation_raceResults,
                RaceResultsFragment.args(result.kaisaibi, result.kaisaijo, raceNo, result.horseName));
    }

    /**
     * 今日走り終えたお気に入り馬の着順を読む（PC のバッチが発走の数分後に入れたもの）。
     * 同じレースに何頭いても1回だけ読む
     */
    private void loadLiveResults(List<String> horseKeys, List<SupabaseManager.RaceEntry> finished) {
        if (finished.isEmpty() || executor.isShutdown()) return;
        Map<String, String> keyByName = new HashMap<>();
        for (String key : horseKeys) keyByName.put(FavoriteHorseNames.normalize(key), key);
        executor.execute(() -> {
            Map<String, HorseResult> live = new HashMap<>();
            Set<String> fetched = new HashSet<>();
            for (SupabaseManager.RaceEntry race : finished) {
                if (!fetched.add(race.getKaisaibi() + race.getKaisaijo() + race.getRaceNo())) continue;
                List<SupabaseManager.RaceResult> rows;
                try {
                    rows = LiveResults.fetchRaceBlocking(race.getKaisaibi(), race.getKaisaijo(), race.getRaceNo());
                } catch (Exception e) {
                    Log.w("FavoriteHorses", "速報の取得に失敗", e);
                    continue;
                }
                for (SupabaseManager.RaceResult r : rows) {
                    String key = keyByName.get(FavoriteHorseNames.normalize(r.getHorseName()));
                    if (key == null) continue;
                    live.put(key, new HorseResult("live/" + r.getKaisaibi() + r.getKaisaijo() + r.getRaceNo(),
                            r.getHorseName(), r.getKaisaibi(), r.getKaisaijo(), String.valueOf(r.getRaceNo()),
                            r.getRaceTitle(), r.getRank(), r.getPopular()));
                }
            }
            if (live.isEmpty() || getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (isAdded() && getView() != null) adapter.setLiveResults(live);
            });
        });
    }

    // 押し間違えで消えないよう、確認してから削除する
    private void onRemoveClick(String horseName) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || getContext() == null) return;
        String name = FavoriteHorsesAdapter.displayName(horseName);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setMessage(Lang.t(name + "を推し馬から外しますか？", "Remove " + name + " from favorites?"))
                .setPositiveButton(Lang.t("外す", "Remove"), (dialog, which) -> {
                    // 一覧は Firebase の変更を監視しているので、削除すると自動で消える
                    FirebaseManager.removeFavoriteHorse(user.getUid(), horseName);
                    SupabaseManager.removeFavorite(user.getUid(), horseName);
                    Toast.makeText(getContext(), Lang.t(name + "を推し馬から外しました", "Removed " + name + " from favorites"), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(Lang.t("キャンセル", "Cancel"), null)
                .show();
    }
}
