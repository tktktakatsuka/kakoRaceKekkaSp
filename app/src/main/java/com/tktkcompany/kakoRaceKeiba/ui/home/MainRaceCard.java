package com.tktkcompany.kakoRaceKeiba.ui.home;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.navigation.Navigation;

import com.tktkcompany.kakoRaceKeiba.R;
import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;
import com.tktkcompany.kakoRaceKeiba.util.Lang;

import java.util.List;

/** ホームの「前回の開催のメインレース」1レース分のカードを作る */
final class MainRaceCard {

    // 見出しの色。JRA の格の色（G1 青・G2 赤・G3 緑）に合わせ、それ以外は落ち着いた灰青
    private static final int[][] HEADER = {
            {0xFF0D47A1, 0xFF1E88E5},
            {0xFFB71C1C, 0xFFE53935},
            {0xFF1B5E20, 0xFF43A047},
            {0xFF4A148C, 0xFF7B1FA2},
            {0xFF37474F, 0xFF607D8B}};
    // 1〜3着の丸の色（金・銀・銅）
    private static final int[] MEDAL = {0xFFD4A017, 0xFF9E9E9E, 0xFFB0703C};
    // 枠番ごとの色（1白 2黒 3赤 4青 5黄 6緑 7橙 8桃）。出走表・結果と同じ
    private static final int[] WAKU_BACKGROUND = {
            0xFFFFFFFF, 0xFF222222, 0xFFE53935, 0xFF1E88E5,
            0xFFFDD835, 0xFF43A047, 0xFFFB8C00, 0xFFF06292};
    private static final int[] WAKU_TEXT = {
            0xFF222222, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF,
            0xFF222222, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF};

    private MainRaceCard() {
    }

    static View create(LayoutInflater inflater, ViewGroup parent, List<SupabaseManager.RaceResult> results) {
        View card = inflater.inflate(R.layout.item_main_race, parent, false);
        SupabaseManager.RaceResult first = results.get(0);
        String course = first.getKaisaijo();
        String date = first.getKaisaibi();
        int grade = MainRaces.grade(first.getRaceTitle());
        float density = parent.getResources().getDisplayMetrics().density;

        int[] colors = HEADER[grade - 1];
        // 上の面はアプリの色（濃い茶）。グレードの色は、下のバッジに残す
        GradientDrawable header = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF5A3A1A, 0xFF8B5A2B});
        card.findViewById(R.id.header).setBackground(header);

        TextView gradeView = card.findViewById(R.id.grade);
        String label = MainRaces.gradeLabel(grade);
        if (label != null) {
            gradeView.setText(label);
            GradientDrawable chip = new GradientDrawable();
            chip.setCornerRadius(12 * density);
            // G1 は金色で目立たせる。ほかはグレードの色の札に白い文字
            chip.setColor(grade == MainRaces.G1 ? 0xFFFFD54F : colors[0]);
            gradeView.setBackground(chip);
            gradeView.setTextColor(grade == MainRaces.G1 ? 0xFF3E2723 : 0xFFFFFFFF);
        } else {
            gradeView.setVisibility(View.GONE);
        }

        // 日付は上の見出しに出すので、カードには競馬場とレース番号だけ
        ((TextView) card.findViewById(R.id.date_course)).setText(Lang.race(course, first.getRaceNo()));
        ((TextView) card.findViewById(R.id.race_name)).setText(MainRaces.plainTitle(first.getRaceTitle()));
        TextView meta = card.findViewById(R.id.meta);
        String metaText = MainRaces.meta(first.getHassouTime());
        meta.setText(metaText);
        meta.setVisibility(metaText.isEmpty() ? View.GONE : View.VISIBLE);

        LinearLayout rows = card.findViewById(R.id.rows);
        for (int place = 1; place <= 3; place++) {
            SupabaseManager.RaceResult r = find(results, place);
            if (r != null) rows.addView(row(inflater, rows, place, r, density));
        }

        ((TextView) card.findViewById(R.id.more)).setText(
                Lang.t(course + "のレース結果をもっとみる ›", "More " + Lang.course(course) + " results ›"));
        card.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString("key", date);
            bundle.putString("jo", course);
            Navigation.findNavController(v).navigate(R.id.navigation_raceResults, bundle);
        });
        return card;
    }

    private static View row(LayoutInflater inflater, ViewGroup parent, int place, SupabaseManager.RaceResult r, float density) {
        View row = inflater.inflate(R.layout.item_main_race_row, parent, false);

        TextView rank = row.findViewById(R.id.rank);
        rank.setText(String.valueOf(place));
        GradientDrawable medal = new GradientDrawable();
        medal.setShape(GradientDrawable.OVAL);
        medal.setColor(MEDAL[place - 1]);
        rank.setBackground(medal);

        TextView number = row.findViewById(R.id.number);
        int waku = parse(r.getWaku()) - 1;
        if (r.getHorseNumber() != null && waku >= 0 && waku < WAKU_BACKGROUND.length) {
            number.setText(r.getHorseNumber().trim());
            GradientDrawable box = new GradientDrawable();
            box.setCornerRadius(4 * density);
            box.setColor(WAKU_BACKGROUND[waku]);
            box.setStroke((int) density, 0xFFBDBDBD);
            number.setBackground(box);
            number.setTextColor(WAKU_TEXT[waku]);
        } else {
            number.setVisibility(View.GONE);
        }

        ((TextView) row.findViewById(R.id.name)).setText(r.getHorseName());
        StringBuilder sub = new StringBuilder();
        if (r.getPopular() != null && !r.getPopular().trim().isEmpty()) sub.append(Lang.popularity(r.getPopular().trim()));
        if (r.getJockey() != null && !r.getJockey().trim().isEmpty()) {
            if (sub.length() > 0) sub.append("・");
            sub.append(r.getJockey().trim());
        }
        TextView subView = row.findViewById(R.id.sub);
        subView.setText(sub);
        subView.setVisibility(sub.length() == 0 ? View.GONE : View.VISIBLE);

        TextView odds = row.findViewById(R.id.odds);
        String win = r.getWinOdds();
        odds.setText(win == null || win.trim().isEmpty() ? "" : Lang.t(win.trim() + "倍", win.trim()));
        // 1着は金色の文字で
        if (place == 1) odds.setTextColor(0xFFB8860B);
        return row;
    }

    private static SupabaseManager.RaceResult find(List<SupabaseManager.RaceResult> results, int place) {
        for (SupabaseManager.RaceResult r : results) {
            if (String.valueOf(place).equals(r.getRank() != null ? r.getRank().trim() : null)) return r;
        }
        return null;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s != null ? s.trim() : "");
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
