package com.tktkcompany.kakoRaceKeiba.ui.notifications;

import com.tktkcompany.kakoRaceKeiba.ads.AdUnits;
import com.tktkcompany.kakoRaceKeiba.ads.BannerAd;
import com.tktkcompany.kakoRaceKeiba.util.Lang;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.tktkcompany.kakoRaceKeiba.databinding.FragmentNotificationsBinding;
import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;
import com.tktkcompany.kakoRaceKeiba.dto.SharedViewModel;
import com.tktkcompany.kakoRaceKeiba.util.WeekendDays;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationsFragment extends Fragment {
    private FragmentNotificationsBinding binding;
    private List<TableLayout> tableLayouts = new ArrayList<>();
    private List<TextView> dateTextViews = new ArrayList<>();
    private List<String> joNameList = new ArrayList<>();
    private List<String> dateList = new ArrayList<>();
    // 読み込み待ちの日付の数と、傾向があった日付の数（どの日も無ければ案内を出す）
    private int pendingDates;
    private int datesWithData;
    private ProgressBar progressBar;
    private int refreshGeneration;

    private enum TrendCategory {
        HORSE, KYAKUSITU, JOCKEY, TYOKYOSI, FARTHER, MATHER, HARAIMODOSI, BANUSI, SEISAN
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        initViews();
        setupViewModel();
        setupAd();

        return root;
    }

    private void initViews() {
        progressBar = binding.progressBar;

        // 20日分の表と日付。フィールド名から探す（リフレクション）と、
        // リリース版の難読化で名前が変わって見つからないので、直接並べる
        tableLayouts.addAll(java.util.Arrays.asList(
                binding.tableLayout1, binding.tableLayout2, binding.tableLayout3, binding.tableLayout4,
                binding.tableLayout5, binding.tableLayout6, binding.tableLayout7, binding.tableLayout8,
                binding.tableLayout9, binding.tableLayout10, binding.tableLayout11, binding.tableLayout12,
                binding.tableLayout13, binding.tableLayout14, binding.tableLayout15, binding.tableLayout16,
                binding.tableLayout17, binding.tableLayout18, binding.tableLayout19, binding.tableLayout20));
        dateTextViews.addAll(java.util.Arrays.asList(
                binding.textDate1, binding.textDate2, binding.textDate3, binding.textDate4,
                binding.textDate5, binding.textDate6, binding.textDate7, binding.textDate8,
                binding.textDate9, binding.textDate10, binding.textDate11, binding.textDate12,
                binding.textDate13, binding.textDate14, binding.textDate15, binding.textDate16,
                binding.textDate17, binding.textDate18, binding.textDate19, binding.textDate20));

        binding.showDialogButton.setOnClickListener(v -> showRaceCourseDialog());

        // 絞り込み条件を変えたらすぐ表に反映する
        binding.checkboxExample.setOnCheckedChangeListener((buttonView, isChecked) -> refreshData());
        binding.checkboxShiba.setOnCheckedChangeListener((buttonView, isChecked) -> refreshData());
        binding.checkboxDart.setOnCheckedChangeListener((buttonView, isChecked) -> refreshData());
        binding.checkboxHani.setOnCheckedChangeListener((buttonView, isChecked) -> refreshData());
    }

    private void setupViewModel() {
        SharedViewModel sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        sharedViewModel.getJoNames().observe(getViewLifecycleOwner(), joNames -> {
            if (joNames == null || joNames.isEmpty()) {
                binding.selectRaceText.setText(Lang.t("現在、開催中の競馬場はありません", "No racecourses are running now"));
                return;
            }
            joNameList = new ArrayList<>(joNames);
            updateRaceText();
            refreshData();
            setupTabs();
        });
    }

    private void setupTabs() {
        List<String> tabTitles = List.of("好走馬", "脚質", "騎手", "調教師", "種牡馬", "母父", "払戻金", "馬主", "生産者");
        TabPagerAdapter adapter = new TabPagerAdapter(requireActivity(), tabTitles);
        binding.viewPager.setAdapter(adapter);

        // タブの中身は日本語の名前で選ぶので、表示だけ英語にする
        List<String> tabLabels = Lang.ja() ? tabTitles
                : List.of("Top horses", "Running style", "Jockeys", "Trainers", "Sires", "Dam sires", "Payouts", "Owners", "Breeders");
        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> tab.setText(tabLabels.get(position))).attach();

        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                refreshData();
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void refreshData() {
        if (binding == null || joNameList.isEmpty()) return;
        // 取得は非同期なので、後から届いた古い条件の結果を表に混ぜないよう世代で見分ける
        final int generation = ++refreshGeneration;

        // 日付リストの取得とソート
        dateList = WeekendDays.getPastWeekendsInCurrentMonth();
        if (binding.checkboxExample.isChecked()) {
            dateList.sort(Collections.reverseOrder());
        } else {
            Collections.sort(dateList);
        }

        clearTables();
        
        int tabPosition = binding.tabLayout.getSelectedTabPosition();
        TrendCategory category = TrendCategory.values()[Math.max(0, tabPosition)];
        
        int displayLimit = binding.checkboxHani.isChecked() ? dateList.size() : Math.min(dateList.size(), 8);
        
        int count = Math.min(displayLimit, tableLayouts.size());
        pendingDates = count;
        datesWithData = 0;
        for (int i = 0; i < count; i++) {
            String date = dateList.get(i);
            loadTrendsForDate(generation, date, joNameList.get(0), tableLayouts.get(i), dateTextViews.get(i), category);
        }
    }

    private void clearTables() {
        for (TableLayout tl : tableLayouts) tl.removeAllViews();
        for (TextView tv : dateTextViews) tv.setText("");
    }

    private void loadTrendsForDate(int generation, String date, String joName, TableLayout table, TextView dateText, TrendCategory category) {
        // 全てのRを一括で取得するのは難しい(スキーマ依存)ため、元のロジックを尊重しつつ1R〜12Rをループ
        // ただし、クエリは一回にまとめられるならまとめたい。元のコードは1RごとにqueryDataを呼んでいた。
        // ここでは効率化のため、一回のクエリでその日の全レースを取得し、メモリ上でフィルタリングする。
        
        progressBar.setVisibility(View.VISIBLE);
        
        SupabaseManager.fetchRaceTrends(date, joName, new SupabaseManager.SupabaseCallback<List<SupabaseManager.RaceTrend>>() {
            @Override
            public void onSuccess(List<SupabaseManager.RaceTrend> trends) {
                if (!isAdded() || binding == null || generation != refreshGeneration) return;

                // その日に開催がなければ日付見出しも出さない
                if (trends.isEmpty()) {
                    progressBar.setVisibility(View.GONE);
                    finishDate(generation, false);
                    return;
                }
                finishDate(generation, true);
                boolean isHeaderSet = false;
                dateText.setText(joName + "   " + WeekendDays.formatWithDayOfWeek(date));
                dateText.setPadding(16, 12, 16, 12);
                dateText.setTextColor(0xFF4E7A27);
                dateText.setTypeface(dateText.getTypeface(), android.graphics.Typeface.BOLD);

                // 取り込みが2回動いた日は同じレースが2行入っているので、レース番号ごとに1行にする
                java.util.Map<String, SupabaseManager.RaceTrend> byRace = new java.util.LinkedHashMap<>();
                for (SupabaseManager.RaceTrend t : trends) byRace.put(t.getRaceNum(), t);
                // レース番号でソート (1R, 2R... 12R)
                List<SupabaseManager.RaceTrend> sortedRaces = new ArrayList<>(byRace.values());
                sortedRaces.sort((o1, o2) -> Integer.compare(raceNumber(o1), raceNumber(o2)));

                for (SupabaseManager.RaceTrend trend : sortedRaces) {
                    if (!isHeaderSet) {
                        table.addView(createHeaderRow(category));
                        isHeaderSet = true;
                    }
                    addRaceRow(table, trend, category);
                }

                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onFailure(@NonNull String message) {
                Log.e("NotificationsFragment", "Supabase load failed: " + message);
                if (isAdded() && generation == refreshGeneration) {
                    progressBar.setVisibility(View.GONE);
                    finishDate(generation, false);
                }
            }
        });
    }

    /**
     * 日付ごとの読み込みが終わるたびに呼ぶ。どの日にも傾向が無ければ、その旨を出す
     * （その週の開催が始まったばかりの競馬場は、夜の取り込みまでデータが無い）
     */
    private void finishDate(int generation, boolean hasData) {
        if (generation != refreshGeneration || binding == null) return;
        if (hasData) datesWithData++;
        if (--pendingDates > 0 || datesWithData > 0 || dateTextViews.isEmpty()) return;
        dateTextViews.get(0).setText(Lang.t(
                "この競馬場の傾向データはまだありません。開催日の夜に取り込まれます",
                "No trend data for this racecourse yet. It is added on the evening of each race day"));
        dateTextViews.get(0).setPadding(16, 24, 16, 24);
    }

    // 「11R」のようなレース番号を数値にする（読み取れない場合は末尾に並べる）
    private static int raceNumber(SupabaseManager.RaceTrend trend) {
        String raceNum = trend.getRaceNum();
        if (raceNum == null) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(raceNum.replace("R", "").trim());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private TableRow createHeaderRow(TrendCategory category) {
        TableRow row = new TableRow(getContext());
        row.setBackgroundColor(0xFF4E7A27);
        String[] headers;
        if (category == TrendCategory.HARAIMODOSI) {
            headers = Lang.ja() ? new String[]{"レース", "条件", "馬場・天候", "荒れ", "単勝", "馬連", "３連単"}
                    : new String[]{"Race", "Conditions", "Track/Weather", "Upset", "Win", "Quinella", "Trifecta"};
        } else {
            headers = Lang.ja() ? new String[]{"レース", "条件", "馬場・天候", "１着(人気)", "２着(人気)", "３着(人気)"}
                    : new String[]{"Race", "Conditions", "Track/Weather", "1st (fav)", "2nd (fav)", "3rd (fav)"};
        }
        for (String h : headers) {
            TextView tv = createTextView(h);
            tv.setTextColor(Color.WHITE);
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
            row.addView(tv);
        }
        return row;
    }

    private void addRaceRow(TableLayout table, SupabaseManager.RaceTrend trend, TrendCategory category) {
        String distance = trend.getRaceDistance();
        if (distance == null) return;

        boolean isShiba = binding.checkboxShiba.isChecked() && distance.contains("芝");
        boolean isDart = binding.checkboxDart.isChecked() && distance.contains("ダ");

        if (!isShiba && !isDart) return;

        TableRow row = new TableRow(getContext());
        // 1行おきに背景色を変えて、横に長い表でも行を追いやすくする
        if (table.getChildCount() % 2 == 0) {
            row.setBackgroundColor(0xFFF8FBF2);
        }
        // レース番号とレース名を1列にまとめて列数を減らす
        TextView race = createTextView(joinNonEmpty(trend.getRaceNum(), trend.getRaceName()));
        race.setTextColor(0xFF4E7A27);
        race.setTypeface(race.getTypeface(), android.graphics.Typeface.BOLD);
        row.addView(race);
        TextView course = createTextView(distance);
        // 芝は緑、ダートは茶色
        course.setTextColor(distance.contains("芝") ? 0xFF2E7D32 : distance.contains("ダ") ? 0xFF8D6E63 : 0xFF333333);
        row.addView(course);
        row.addView(conditionView(trend.getRaceCondition()));

        if (category == TrendCategory.HARAIMODOSI) {
            row.addView(upsetView(trend.getHaraimodosiAre()));
            row.addView(payoutView(trend.getHaraimodosiTansyo()));
            row.addView(payoutView(trend.getHaraimodosiUmaren()));
            row.addView(payoutView(trend.getHaraimodosi3Rentan()));
        } else {
            String v1 = trend.getUma1Name(), v2 = trend.getUma2Name(), v3 = trend.getUmaName(); // Default HORSE
            switch (category) {
                case KYAKUSITU: v1 = trend.getUma1Kyakusitu(); v2 = trend.getUma2Kyakusitu(); v3 = trend.getUma3Kyakusitu(); break;
                case JOCKEY: v1 = trend.getUma1Jockey(); v2 = trend.getUma2Jockey(); v3 = trend.getUma3Jockey(); break;
                case TYOKYOSI: v1 = trend.getUma1tyokyosi(); v2 = trend.getUma2tyokyosi(); v3 = trend.getUma3tyokyosi(); break;
                case FARTHER: v1 = trend.getUma1father(); v2 = trend.getUma2father(); v3 = trend.getUma3father(); break;
                case MATHER: v1 = trend.getUma1mather(); v2 = trend.getUma2mather(); v3 = trend.getUma3mather(); break;
                case BANUSI: v1 = trend.getBanusi1(); v2 = trend.getBanusi2(); v3 = trend.getBanusi3(); break;
                case SEISAN: v1 = trend.getSeisann1(); v2 = trend.getSeisann2(); v3 = trend.getSeisann3(); break;
            }
            // 馬番と内容を1つのセルにまとめる（例: 「3番 ○○(1人)」）
            row.addView(placeView(1, trend.getUma1Ban(), v1));
            row.addView(placeView(2, trend.getUma2Ban(), v2));
            row.addView(placeView(3, trend.getUma3Ban(), v3));
        }
        table.addView(row);
    }

    /** 1〜3着のセル。「1着」は赤、2・3着は緑の帯を左に付け、人気（①②…）が5番人気以下なら目立たせる */
    private TextView placeView(int place, String ban, String value) {
        TextView tv = createTextView(joinNonEmpty(withBan(ban), value));
        int color = place == 1 ? 0xFF4A7A1E : 0xFF4E7A27;
        GradientDrawable bg = new GradientDrawable();
        bg.setStroke(2, 0xFFDDE8CC);
        GradientDrawable mark = new GradientDrawable();
        mark.setColor(color);
        android.graphics.drawable.LayerDrawable layer = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{bg, mark});
        layer.setLayerGravity(1, android.view.Gravity.START | android.view.Gravity.FILL_VERTICAL);
        layer.setLayerWidth(1, 8);
        tv.setBackground(layer);
        if (value != null && value.matches(".*[⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱].*")) {
            tv.setTextColor(0xFF4A7A1E);
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return tv;
    }

    /** 馬場・天候。良は緑、稍重は橙、重・不良は赤 */
    private TextView conditionView(String condition) {
        TextView tv = createTextView(condition);
        if (condition != null) {
            if (condition.startsWith("不良") || condition.startsWith("重")) tv.setTextColor(0xFF4A7A1E);
            else if (condition.startsWith("稍")) tv.setTextColor(0xFF4A7A1E);
            else if (condition.startsWith("良")) tv.setTextColor(0xFF4E7A27);
        }
        return tv;
    }

    /** 荒れ具合。大荒・超荒は赤、中荒は橙、それ以外（本命など）は緑のラベル */
    private TextView upsetView(String upset) {
        TextView tv = createTextView(upset);
        if (upset == null || upset.isEmpty()) return tv;
        int color = (upset.contains("大") || upset.contains("超")) ? 0xFFC62828 : upset.contains("中") ? 0xFFE65100 : 0xFF2E7D32;
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setGravity(android.view.Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setStroke(2, 0xFFDDE8CC);
        tv.setBackground(bg);
        return tv;
    }

    /** 払戻金。万馬券は赤の太字 */
    private TextView payoutView(String yen) {
        TextView tv = createTextView(yen);
        tv.setGravity(android.view.Gravity.END);
        if (yen != null) {
            String digits = yen.replaceAll("[^0-9]", "");
            if (!digits.isEmpty() && Long.parseLong(digits) >= 10000) {
                tv.setTextColor(0xFF4A7A1E);
                tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
            }
        }
        return tv;
    }

    private static String withBan(String ban) {

        return ban == null || ban.isEmpty() ? null : Lang.t(ban + "番", "#" + ban);
    }

    private static String joinNonEmpty(String a, String b) {
        if (a == null || a.isEmpty()) return b != null ? b : "";
        if (b == null || b.isEmpty()) return a;
        return a + " " + b;
    }

    private TextView createTextView(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text != null ? text : "");
        tv.setPadding(16, 10, 16, 10);
        tv.setTextColor(0xFF2F3B22);
        GradientDrawable gd = new GradientDrawable();
        gd.setStroke(2, 0xFFDDE8CC);
        tv.setBackground(gd);
        return tv;
    }

    private void showRaceCourseDialog() {
        if (joNameList.isEmpty()) return;
        String[] items = joNameList.toArray(new String[0]);
        new AlertDialog.Builder(requireContext())
                .setTitle(Lang.t("競馬場を選択する", "Choose a racecourse"))
                .setItems(items, (dialog, which) -> {
                    String selected = joNameList.remove(which);
                    joNameList.add(0, selected);
                    updateRaceText();
                    refreshData();
                })
                .show();
    }

    private void updateRaceText() {
        if (joNameList != null && !joNameList.isEmpty()) {
            binding.selectRaceText.setText(Lang.t(joNameList.get(0) + "競馬場の傾向を表示", Lang.course(joNameList.get(0)) + " trends"));
        } else {
            binding.selectRaceText.setText(Lang.t("競馬場が選択されていません", "No racecourse selected"));
        }
    }

    private void setupAd() {
        BannerAd.show(this, binding.adContainer, AdUnits.BANNER_TRENDS);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}