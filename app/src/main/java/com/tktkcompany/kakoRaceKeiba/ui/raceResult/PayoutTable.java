package com.tktkcompany.kakoRaceKeiba.ui.raceResult;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tktkcompany.kakoRaceKeiba.db.LivePayouts;
import com.tktkcompany.kakoRaceKeiba.util.Lang;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 払い戻しの表。券種・組番・払戻の3列をそろえ、券種は色付きのラベルにする。
 * 複勝・ワイドのように的中が何件もある券種は、組番と払戻を行ごとに並べる。
 * 出走表とレース結果の両方の画面で使う。
 */
public final class PayoutTable {

    private PayoutTable() {
    }

    /** 券種ごとのラベルの色（JRA の馬券の色に近いもの）。枠番の色と同じく、競馬の決まった色なので変えない */
    private static int typeColor(String type) {
        switch (type) {
            case "単勝": return 0xFFC62828;
            case "複勝": return 0xFF1565C0;
            case "枠連": return 0xFF6A1B9A;
            case "馬連": return 0xFF2E7D32;
            case "ワイド": return 0xFF00838F;
            case "馬単": return 0xFFEF6C00;
            case "3連複": return 0xFF5D4037;
            default: return 0xFF37474F;  // 3連単
        }
    }

    public static View create(Context context, List<LivePayouts.Payout> payouts) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout table = new LinearLayout(context);
        table.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * density);
        table.setPadding(pad, (int) (10 * density), pad, (int) (10 * density));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10 * density);
        bg.setColor(0xFFFFFFFF);
        bg.setStroke((int) Math.max(1, density), 0xFFDDE8CC);
        table.setBackground(bg);

        // 見出しを押すと表を閉じる・開く
        TextView title = new TextView(context);
        title.setTextSize(15);
        title.setTextColor(0xFF2F3B22);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setPadding(0, 0, 0, (int) (6 * density));
        table.addView(title);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        table.addView(body);
        Runnable paintTitle = () -> title.setText(body.getVisibility() == View.VISIBLE
                ? "💴 " + Lang.t("払い戻し", "Payouts") + "　▲ " + Lang.t("閉じる", "Hide")
                : "💴 " + Lang.t("払い戻し", "Payouts") + "　▼ " + Lang.t("開く", "Show"));
        paintTitle.run();
        title.setOnClickListener(v -> {
            body.setVisibility(body.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            title.setPadding(0, 0, 0, body.getVisibility() == View.VISIBLE ? (int) (6 * density) : 0);
            paintTitle.run();
        });

        // 券種ごとにまとめる（表示順は TYPES のとおり）
        Map<String, List<LivePayouts.Payout>> byType = new LinkedHashMap<>();
        for (String type : LivePayouts.TYPES) byType.put(type, new ArrayList<>());
        for (LivePayouts.Payout p : payouts) {
            List<LivePayouts.Payout> list = byType.get(p.type);
            if (list != null) list.add(p);
        }

        boolean striped = false;
        for (Map.Entry<String, List<LivePayouts.Payout>> e : byType.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            LinearLayout group = new LinearLayout(context);
            group.setOrientation(LinearLayout.HORIZONTAL);
            group.setGravity(Gravity.CENTER_VERTICAL);
            group.setPadding(0, (int) (5 * density), 0, (int) (5 * density));
            if (striped) group.setBackgroundColor(0xFFF8FBF2);
            striped = !striped;

            TextView label = new TextView(context);
            label.setText(Lang.betType(e.getKey()));
            label.setTextSize(12);
            label.setTextColor(0xFFFFFFFF);
            label.setTypeface(label.getTypeface(), Typeface.BOLD);
            label.setGravity(Gravity.CENTER);
            GradientDrawable chip = new GradientDrawable();
            chip.setCornerRadius(6 * density);
            chip.setColor(typeColor(e.getKey()));
            label.setBackground(chip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (72 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.TOP;
            label.setLayoutParams(lp);
            label.setPadding(0, (int) (3 * density), 0, (int) (3 * density));
            group.addView(label);

            // 組番と払戻を1件1行で並べる
            LinearLayout lines = new LinearLayout(context);
            lines.setOrientation(LinearLayout.VERTICAL);
            lines.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            for (LivePayouts.Payout p : e.getValue()) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding((int) (12 * density), (int) (2 * density), 0, (int) (2 * density));

                TextView numbers = new TextView(context);
                numbers.setText(p.numbers);
                numbers.setTextSize(15);
                numbers.setTextColor(0xFF2F3B22);
                numbers.setTypeface(Typeface.MONOSPACE);
                numbers.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(numbers);

                TextView yen = new TextView(context);
                yen.setText(Lang.yen(p.yen));
                yen.setTextSize(15);
                yen.setGravity(Gravity.END);
                // 万馬券は目立たせる
                yen.setTextColor(p.yen >= 10000 ? 0xFF4A7A1E : 0xFF2F3B22);
                yen.setTypeface(yen.getTypeface(), Typeface.BOLD);
                row.addView(yen);
                lines.addView(row);
            }
            group.addView(lines);
            body.addView(group);
        }
        return table;
    }
}
