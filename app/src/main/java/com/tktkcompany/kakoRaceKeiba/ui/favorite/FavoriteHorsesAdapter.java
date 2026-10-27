package com.tktkcompany.kakoRaceKeiba.ui.favorite;

import com.tktkcompany.kakoRaceKeiba.util.Lang;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.tktkcompany.kakoRaceKeiba.R;
import com.tktkcompany.kakoRaceKeiba.db.HorseResult;
import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;
import com.tktkcompany.kakoRaceKeiba.share.WinShare;
import com.tktkcompany.kakoRaceKeiba.ui.entries.EntryForm;
import com.tktkcompany.kakoRaceKeiba.ui.entries.FavoriteHorseNames;
import com.tktkcompany.kakoRaceKeiba.util.WeekendDays;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * お気に入り馬の一覧。「今週出走」「今日走った」「そのほか」に分けて見出しを付ける。
 */
public class FavoriteHorsesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_HORSE = 1;
    private static final Pattern START_TIME = Pattern.compile("(\\d{1,2}):(\\d{2})発走");

    private final List<String> horseNames;
    private final OnRemoveClickListener listener;
    private final OnResultClickListener resultClickListener;
    private final OnEntryClickListener entryClickListener;
    // 馬名 → 直近の結果。null の間は読み込み中
    private Map<String, HorseResult> latestResults;
    // 馬名（印を外した名前）→ いちばん近い出走予定。出走予定がない馬は入っていない
    private Map<String, SupabaseManager.RaceEntry> nextEntries = new HashMap<>();
    // お気に入りのキー → 今日のレースの速報
    private Map<String, HorseResult> liveResults = new HashMap<>();
    // 見出し（String）と馬（Horse）を並べたもの
    private final List<Object> rows = new ArrayList<>();

    public interface OnRemoveClickListener {
        void onRemoveClick(String horseName);
    }

    public interface OnResultClickListener {
        void onResultClick(HorseResult result);
    }

    public interface OnEntryClickListener {
        void onEntryClick(SupabaseManager.RaceEntry entry);
    }

    private static final class Horse {
        final String key;

        Horse(String key) {
            this.key = key;
        }
    }

    public FavoriteHorsesAdapter(List<String> horseNames, OnRemoveClickListener listener,
                                 OnResultClickListener resultClickListener,
                                 OnEntryClickListener entryClickListener) {
        this.horseNames = horseNames;
        this.listener = listener;
        this.resultClickListener = resultClickListener;
        this.entryClickListener = entryClickListener;
    }

    public void setNextEntries(Map<String, SupabaseManager.RaceEntry> nextEntries) {
        this.nextEntries = nextEntries != null ? nextEntries : new HashMap<>();
        rebuildRows();
        notifyDataSetChanged();
    }

    public void setLatestResults(Map<String, HorseResult> latestResults) {
        this.latestResults = latestResults != null ? latestResults : new HashMap<>();
        rebuildRows();
        notifyDataSetChanged();
    }

    public void clearLatestResults() {
        this.latestResults = null;
    }

    /** 今日のレースの速報（お気に入りのキー → 結果）。夜の取り込み前でも着順を出すため */
    public void setLiveResults(Map<String, HorseResult> liveResults) {
        this.liveResults = liveResults != null ? liveResults : new HashMap<>();
        rebuildRows();
        notifyDataSetChanged();
    }

    /** データが変わるたびに、グループ分けと並び順を作り直す */
    private void rebuildRows() {
        List<String> upcoming = new ArrayList<>();
        List<String> ranToday = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (String name : horseNames) {
            if (nextEntries.containsKey(FavoriteHorseNames.normalize(name))) upcoming.add(name);
            else if (liveResults.containsKey(name)) ranToday.add(name);
            else others.add(name);
        }
        upcoming.sort((a, b) -> raceOrder(nextEntries.get(FavoriteHorseNames.normalize(a)))
                .compareTo(raceOrder(nextEntries.get(FavoriteHorseNames.normalize(b)))));
        ranToday.sort((a, b) -> displayName(a).compareTo(displayName(b)));
        // 出走予定の無い馬は、前走の新しい順（前走が分からなければ名前順で最後）
        others.sort((a, b) -> {
            String da = latestDate(a), db = latestDate(b);
            if (!da.equals(db)) return db.compareTo(da);
            return displayName(a).compareTo(displayName(b));
        });

        rows.clear();
        addGroup(Lang.t("今週出走", "Running this week"), upcoming);
        addGroup(Lang.t("今日走った", "Ran today"), ranToday);
        addGroup(upcoming.isEmpty() && ranToday.isEmpty() ? null : Lang.t("そのほか", "Others"), others);
    }

    private void addGroup(@Nullable String title, List<String> names) {
        if (names.isEmpty()) return;
        if (title != null) rows.add(title + Lang.t("（" + names.size() + "頭）", " (" + names.size() + ")"));
        for (String name : names) rows.add(new Horse(name));
    }

    private String latestDate(String name) {
        HorseResult r = latestResults != null ? latestResults.get(name) : null;
        return r != null && r.kaisaibi != null ? r.kaisaibi : "";
    }

    private static String raceOrder(SupabaseManager.RaceEntry e) {
        String info = e.getRaceInfo() != null ? e.getRaceInfo() : "";
        Matcher m = START_TIME.matcher(info);
        String time = m.find() ? String.format("%02d%s", Integer.parseInt(m.group(1)), m.group(2)) : "9999";
        return e.getKaisaibi() + time;
    }

    /** 「(外)フロムダスク」→「フロムダスク」 */
    static String displayName(String name) {
        return name == null ? "" : name.trim().replaceFirst("^[\\(（\\[［][^\\)）\\]］]{1,2}[\\)）\\]］]\\s*", "");
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof Horse ? TYPE_HORSE : TYPE_HEADER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            TextView header = new TextView(parent.getContext());
            float density = parent.getResources().getDisplayMetrics().density;
            header.setPadding((int) (4 * density), (int) (14 * density), 0, (int) (4 * density));
            header.setTextSize(14);
            header.setTextColor(0xFF388E3C);
            header.setTypeface(header.getTypeface(), Typeface.BOLD);
            header.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new HeaderHolder(header);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_favorite_horse, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((TextView) holder.itemView).setText((String) row);
            return;
        }
        bindHorse((ViewHolder) holder, ((Horse) row).key);
    }

    private void bindHorse(ViewHolder holder, String name) {
        holder.textView.setText(displayName(name) + " ›");
        holder.deleteButton.setOnClickListener(v -> listener.onRemoveClick(name));
        // 馬名（カードの空いたところ）を押すと馬カルテを開く
        holder.card.setOnClickListener(v -> androidx.navigation.Navigation.findNavController(v)
                .navigate(R.id.navigation_horse, com.tktkcompany.kakoRaceKeiba.ui.horse.HorseFragment.args(name)));
        // 🎨 か長押しで、アイコンと背景を選ぶ
        holder.decorateButton.setOnClickListener(v -> CardStyles.showEditor(v.getContext(), name, displayName(name), this::refresh));
        holder.card.setOnLongClickListener(v -> {
            CardStyles.showEditor(v.getContext(), name, displayName(name), this::refresh);
            return true;
        });
        holder.avatarContainer.removeAllViews();
        View avatar = CardStyles.avatar(holder.avatarContainer.getContext(), name, 48);
        avatar.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        holder.avatarContainer.addView(avatar);

        SupabaseManager.RaceEntry next = nextEntries.get(FavoriteHorseNames.normalize(name));
        CardStyles.applyBackground(holder.card, name, next != null ? 0xFFFFF8E1 : 0xFFFFFFFF);
        if (next == null) {
            holder.nextEntryView.setVisibility(View.GONE);
            holder.nextEntryView.setOnClickListener(null);
        } else {
            holder.nextEntryView.setText(entryLabel(next));
            holder.nextEntryView.setVisibility(View.VISIBLE);
            holder.nextEntryView.setOnClickListener(v -> entryClickListener.onEntryClick(next));
        }

        HorseResult latest = latestResults != null ? latestResults.get(name) : null;
        // 今日の速報があり、取り込み済みの結果より新しければ速報を出す
        HorseResult live = liveResults.get(name);
        boolean isLive = live != null && (latest == null || nz(live.kaisaibi).compareTo(nz(latest.kaisaibi)) > 0);
        holder.latestResultView.setVisibility(View.VISIBLE);
        if (isLive) {
            holder.latestResultView.setText(resultLabel(Lang.t("速報", "Live"), live));
            holder.latestResultView.setOnClickListener(v -> resultClickListener.onResultClick(live));
        } else if (latestResults == null) {
            holder.latestResultView.setText(Lang.t("前走を読み込み中…", "Loading last run…"));
            holder.latestResultView.setOnClickListener(null);
        } else if (latest == null) {
            // 出走予定を出している馬に「出走はありません」と並べると矛盾して見えるので出さない
            holder.latestResultView.setVisibility(next != null ? View.GONE : View.VISIBLE);
            holder.latestResultView.setText(Lang.t("最近の出走はありません", "No recent runs"));
            holder.latestResultView.setOnClickListener(null);
        } else {
            holder.latestResultView.setText(resultLabel(Lang.t("前走", "Last"), latest));
            holder.latestResultView.setOnClickListener(v -> resultClickListener.onResultClick(latest));
        }

        // 表示している結果（速報か前走）が1着なら、勝利をシェアできるようにする。
        // カードは使い回されるので、1着でないときは必ず隠す
        HorseResult shown = isLive ? live : latest;
        if (shown != null && WinShare.isWin(shown.tyaku)) {
            holder.shareButton.setText(Lang.t("🎉 勝利をシェア", "🎉 Share the win"));
            holder.shareButton.setVisibility(View.VISIBLE);
            holder.shareButton.setOnClickListener(v -> WinShare.open(v.getContext(),
                    WinShare.text(name, nz(shown.kaisaibi), nz(shown.kaisaijo), raceNo(shown.raceNo), shown.raceTitle)));
        } else {
            holder.shareButton.setVisibility(View.GONE);
            holder.shareButton.setOnClickListener(null);
        }
    }

    private static int raceNo(String raceNo) {
        try {
            return Integer.parseInt(nz(raceNo).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 「▶ 2026年10月18日(日) 京都11R 15:40 秋華賞（芝2000m）2番 ›」 */
    private static CharSequence entryLabel(SupabaseManager.RaceEntry e) {
        SpannableStringBuilder sb = new SpannableStringBuilder("▶ ");
        sb.append(WeekendDays.formatCompact(e.getKaisaibi())).append(' ')
                .append(Lang.race(e.getKaisaijo(), e.getRaceNo()));
        Matcher m = START_TIME.matcher(e.getRaceInfo() != null ? e.getRaceInfo() : "");
        if (m.find()) sb.append(' ').append(String.valueOf(Integer.parseInt(m.group(1)))).append(':').append(m.group(2));
        String title = e.getRaceTitle() != null ? e.getRaceTitle().trim().replaceFirst("^サラ系", "") : "";
        if (!title.isEmpty()) sb.append('\n').append(title);
        String course = EntryForm.courseText(EntryForm.courseLabel(e.getRaceInfo()));
        if (!course.isEmpty()) sb.append(Lang.t("（" + course + "m）", " (" + course + "m)"));
        String number = e.getHorseNumber();
        if (number != null && !number.trim().isEmpty()) sb.append(Lang.t(" " + number.trim() + "番", " #" + number.trim()));
        sb.append(" ›");
        sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /** 「前走 2026年10月04日(日) 京都7R 8着（6人気）›」。着順は1〜3着なら色を付ける */
    private static CharSequence resultLabel(String prefix, HorseResult r) {
        SpannableStringBuilder sb = new SpannableStringBuilder(prefix);
        sb.append(' ').append(nz(WeekendDays.formatCompact(r.kaisaibi))).append(' ')
                .append(Lang.ja() ? nz(r.kaisaijo) + nz(r.raceNo) + "R " : Lang.course(r.kaisaijo) + " R" + nz(r.raceNo) + " ");
        String rank = nz(r.tyaku);
        int from = sb.length();
        sb.append(Lang.rank(rank));
        if (rank.matches("[123]")) {
            sb.setSpan(new ForegroundColorSpan("1".equals(rank) ? 0xFFC62828 : 0xFF2E7D32), from, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new StyleSpan(Typeface.BOLD), from, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (r.popular != null && !r.popular.isEmpty()) sb.append(Lang.t("（" + r.popular + "人気）", " (" + Lang.popularity(r.popular) + ")"));
        sb.append(" ›");
        return sb;
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    /** お気に入りの馬が増えた・減ったとき（Firebase の変更）に呼ぶ */
    public void refresh() {
        rebuildRows();
        notifyDataSetChanged();
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        HeaderHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView textView;
        TextView latestResultView;
        TextView nextEntryView;
        ImageButton deleteButton;
        FrameLayout avatarContainer;
        TextView decorateButton;
        TextView shareButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_favorite);
            textView = itemView.findViewById(R.id.text_horse_name);
            latestResultView = itemView.findViewById(R.id.text_latest_result);
            nextEntryView = itemView.findViewById(R.id.text_next_entry);
            deleteButton = itemView.findViewById(R.id.btn_delete);
            avatarContainer = itemView.findViewById(R.id.avatar_container);
            decorateButton = itemView.findViewById(R.id.btn_decorate);
            shareButton = itemView.findViewById(R.id.btn_share_win);
        }
    }
}
