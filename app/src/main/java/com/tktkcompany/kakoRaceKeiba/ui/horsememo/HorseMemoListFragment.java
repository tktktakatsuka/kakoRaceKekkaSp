package com.tktkcompany.kakoRaceKeiba.ui.horsememo;

import com.tktkcompany.kakoRaceKeiba.ads.AdUnits;
import com.tktkcompany.kakoRaceKeiba.ads.BannerAd;
import com.tktkcompany.kakoRaceKeiba.util.Lang;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tktkcompany.kakoRaceKeiba.R;
import com.tktkcompany.kakoRaceKeiba.util.WeekendDays;
import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;
import com.tktkcompany.kakoRaceKeiba.ui.entries.FavoriteHorseNames;

import org.threeten.bp.LocalDate;
import org.threeten.bp.format.DateTimeFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 馬メモの一覧。今週出走するメモ馬を上に、それ以外は新しく書いた順に並べる。
 */
public class HorseMemoListFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyText;

    private Map<String, HorseMemos.Memo> memos = new HashMap<>();
    // 印を外した馬名 → いちばん近い出走予定
    private final Map<String, SupabaseManager.RaceEntry> nextEntries = new HashMap<>();
    private final List<String> keys = new ArrayList<>();
    private final Adapter adapter = new Adapter();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_horse_memos, container, false);
        recyclerView = root.findViewById(R.id.recycler_horse_memos);
        progressBar = root.findViewById(R.id.progress_horse_memos);
        emptyText = root.findViewById(R.id.text_horse_memos_empty);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        BannerAd.show(this, root.findViewById(R.id.ad_container), AdUnits.BANNER_MEMOS);
        root.findViewById(R.id.card_open_predictions).setOnClickListener(v ->
                androidx.navigation.Navigation.findNavController(v).navigate(R.id.navigation_predictions));
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 出走表やレース結果で書いたメモを反映するため、戻ってくるたびに読み直す
        load();
    }

    private void load() {
        if (!HorseMemos.isSignedIn()) {
            keys.clear();
            adapter.notifyDataSetChanged();
            showEmpty(Lang.t("ログインすると、馬ごとのメモを残せます", "Sign in to keep notes on horses"));
            return;
        }
        progressBar.setVisibility(keys.isEmpty() ? View.VISIBLE : View.GONE);
        HorseMemos.load(loaded -> {
            if (!isAdded() || getView() == null) return;
            memos = new HashMap<>(loaded);
            progressBar.setVisibility(View.GONE);
            refresh();
            loadNextEntries();
        });
    }

    private void loadNextEntries() {
        if (memos.isEmpty()) return;
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        SupabaseManager.fetchUpcomingEntries(today, new SupabaseManager.SupabaseCallback<List<SupabaseManager.RaceEntry>>() {
            @Override
            public void onSuccess(List<SupabaseManager.RaceEntry> entries) {
                if (!isAdded() || getView() == null) return;
                nextEntries.clear();
                // entries は開催日・競馬場・レース番号の順なので、最初に見つかったものが次走
                for (SupabaseManager.RaceEntry e : entries) {
                    String key = FavoriteHorseNames.normalize(e.getHorseName());
                    if (memos.containsKey(key) && !nextEntries.containsKey(key)) nextEntries.put(key, e);
                }
                refresh();
            }

            @Override
            public void onFailure(@NonNull String message) {
                Log.w("HorseMemoList", "Upcoming entries load failed: " + message);
            }
        });
    }

    private void refresh() {
        keys.clear();
        keys.addAll(memos.keySet());
        Collections.sort(keys, (a, b) -> {
            SupabaseManager.RaceEntry ea = nextEntries.get(a);
            SupabaseManager.RaceEntry eb = nextEntries.get(b);
            if (ea != null && eb != null) return ea.getKaisaibi().compareTo(eb.getKaisaibi());
            if (ea != null) return -1;
            if (eb != null) return 1;
            return Long.compare(memos.get(b).updatedAt, memos.get(a).updatedAt);
        });
        adapter.notifyDataSetChanged();
        if (keys.isEmpty()) {
            showEmpty(Lang.t("まだメモはありません。\nレース結果や出走表で、馬の横の📝をタップして書いてみましょう", "No notes yet.\nTap 📝 next to a horse on results or race cards to write one"));
        } else {
            emptyText.setVisibility(View.GONE);
        }
    }

    private void showEmpty(String message) {
        progressBar.setVisibility(View.GONE);
        emptyText.setText(message);
        emptyText.setVisibility(View.VISIBLE);
    }

    private static String metaLabel(HorseMemos.Memo memo) {
        StringBuilder sb = new StringBuilder();
        if (memo.updatedAt > 0) {
            sb.append(new SimpleDateFormat(WeekendDays.datePattern(), Lang.ja() ? Locale.JAPAN : Locale.US).format(new Date(memo.updatedAt))).append(Lang.t(" 更新", " updated"));
        }
        if (memo.from != null && !memo.from.isEmpty()) {
            if (sb.length() > 0) sb.append("・");
            sb.append(memo.from);
        }
        return sb.toString();
    }

    private class Adapter extends RecyclerView.Adapter<Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_horse_memo, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            String key = keys.get(position);
            HorseMemos.Memo memo = memos.get(key);
            if (memo == null) return;
            holder.name.setText(memo.name);
            holder.body.setText(memo.text);
            String meta = metaLabel(memo);
            holder.meta.setText(meta);
            holder.meta.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);

            SupabaseManager.RaceEntry next = nextEntries.get(key);
            if (next == null) {
                holder.nextEntry.setVisibility(View.GONE);
                holder.nextEntry.setOnClickListener(null);
            } else {
                holder.nextEntry.setText(FavoriteHorseNames.nextEntryLabel(next));
                holder.nextEntry.setVisibility(View.VISIBLE);
                holder.nextEntry.setOnClickListener(v -> FavoriteHorseNames.openEntry(v, next));
            }

            holder.itemView.setOnClickListener(v -> HorseMemoDialog.show(requireContext(), memos, memo.name, null,
                    changed -> refresh()));
        }

        @Override
        public int getItemCount() {
            return keys.size();
        }
    }

    private static class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView nextEntry;
        final TextView body;
        final TextView meta;

        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_memo_horse);
            nextEntry = itemView.findViewById(R.id.text_memo_next_entry);
            body = itemView.findViewById(R.id.text_memo_body);
            meta = itemView.findViewById(R.id.text_memo_meta);
        }
    }
}
