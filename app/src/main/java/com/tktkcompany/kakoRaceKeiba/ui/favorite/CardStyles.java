package com.tktkcompany.kakoRaceKeiba.ui.favorite;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.tktkcompany.kakoRaceKeiba.db.FirebaseManager;
import com.tktkcompany.kakoRaceKeiba.ui.entries.FavoriteHorseNames;
import com.tktkcompany.kakoRaceKeiba.ui.horse.HorseViews;
import com.tktkcompany.kakoRaceKeiba.util.Lang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 推し馬カードの飾り（アイコンと背景）。馬ごとに選べて、Firebase の users/{uid}/cardStyles/{馬名} に保存する。
 * 写真は使わず、用意した中から選ぶ（他人が撮った写真が広まるのを避けるため）。
 * 背景は明るい色だけにする（カードの文字が濃い色なので、暗い背景だと読めなくなる）。
 */
public final class CardStyles {

    private static final String TAG = "CardStyles";

    /** アイコン。null は今までの馬のイラスト */
    static final String[] ICONS = {null, "🏇", "🐴", "🦄", "🏆", "👑", "⭐", "❤️", "🍀", "🌸", "🔥", "🌈"};

    /** 背景。null は今まで通り（今週走る馬はクリーム色、ほかは白） */
    static final class Background {
        final String id;
        final String label;
        final int from;
        final int to;

        Background(String id, String label, int from, int to) {
            this.id = id;
            this.label = label;
            this.from = from;
            this.to = to;
        }
    }

    static final Background[] BACKGROUNDS = {
            null,
            new Background("sakura", Lang.t("桜", "Sakura"), 0xFFFFF0F5, 0xFFF8BBD0),
            new Background("wakakusa", Lang.t("若草", "Meadow"), 0xFFF9FBE7, 0xFFC5E1A5),
            new Background("sora", Lang.t("空", "Sky"), 0xFFF1F8FF, 0xFFBBDEFB),
            new Background("yuyake", Lang.t("夕焼け", "Sunset"), 0xFFFFF8E1, 0xFFFFCC80),
            new Background("kin", Lang.t("金", "Gold"), 0xFFFFFDE7, 0xFFFFE082),
            new Background("fuji", Lang.t("藤", "Wisteria"), 0xFFFAF5FF, 0xFFE1BEE7),
            new Background("mint", Lang.t("ミント", "Mint"), 0xFFF0FBF9, 0xFFB2DFDB),
            new Background("momiji", Lang.t("紅葉", "Maple"), 0xFFFFF5F2, 0xFFFFAB91),
            new Background("kiri", Lang.t("霧", "Mist"), 0xFFFFFFFF, 0xFFE0E0E0),
    };

    /** 1頭分の飾り */
    static final class Style {
        @Nullable final String icon;
        @Nullable final String background;

        Style(@Nullable String icon, @Nullable String background) {
            this.icon = icon;
            this.background = background;
        }
    }

    // 馬名（印を外した名前）→ 飾り。読み込む前は空
    private static final Map<String, Style> STYLES = new HashMap<>();
    @Nullable private static String loadedUid;

    private CardStyles() {
    }

    /** 飾りを読み込む（ログイン中のアカウントで1回だけ）。読み終わったら onLoaded を呼ぶ */
    public static void load(Runnable onLoaded) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            STYLES.clear();
            loadedUid = null;
            onLoaded.run();
            return;
        }
        String uid = user.getUid();
        if (uid.equals(loadedUid)) {
            onLoaded.run();
            return;
        }
        FirebaseManager.readData(path(uid), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                STYLES.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    if (child.getKey() == null) continue;
                    STYLES.put(child.getKey(), new Style(child.child("icon").getValue(String.class),
                            child.child("background").getValue(String.class)));
                }
                loadedUid = uid;
                onLoaded.run();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "カードの飾りを読めませんでした: " + error.getMessage());
                onLoaded.run();
            }
        });
    }

    private static String path(String uid) {
        return "users/" + uid + "/cardStyles";
    }

    @Nullable
    private static Style get(String horseName) {
        return STYLES.get(FavoriteHorseNames.normalize(horseName));
    }

    private static void save(String horseName, @Nullable String icon, @Nullable String background) {
        String key = FavoriteHorseNames.normalize(horseName);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || key.isEmpty()) return;
        String path = path(user.getUid()) + "/" + key;
        if (icon == null && background == null) {
            STYLES.remove(key);
            FirebaseManager.deleteData(path);
            return;
        }
        STYLES.put(key, new Style(icon, background));
        Map<String, Object> data = new HashMap<>();
        data.put("icon", icon);
        data.put("background", background);
        FirebaseManager.updateData(path, data);
    }

    /** 馬のアイコン（選んだ絵文字か、いつもの馬のイラスト） */
    public static View avatar(Context context, String horseName, float sizeDp) {
        Style style = get(horseName);
        return avatarOf(context, style != null ? style.icon : null, sizeDp);
    }

    private static View avatarOf(Context context, @Nullable String icon, float sizeDp) {
        if (icon == null) return HorseViews.avatar(context, sizeDp);
        TextView view = new TextView(context);
        view.setText(icon);
        view.setTextSize(sizeDp * 0.5f);
        view.setGravity(Gravity.CENTER);
        // 絵文字の上下の余白で中心がずれないようにする
        view.setIncludeFontPadding(false);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xFFE8F5E9);
        view.setBackground(circle);
        int size = HorseViews.dp(context, sizeDp);
        view.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return view;
    }

    /**
     * カードに背景を付ける。選んでいなければ defaultColor（今まで通り）。
     * card の中身（getChildAt(0)）にグラデーションを敷き、角の丸みをカードに合わせる
     */
    public static void applyBackground(MaterialCardView card, String horseName, int defaultColor) {
        Style style = get(horseName);
        applyBackgroundOf(card, find(style != null ? style.background : null), defaultColor);
    }

    private static void applyBackgroundOf(MaterialCardView card, @Nullable Background bg, int defaultColor) {
        View inner = card.getChildAt(0);
        if (bg == null) {
            card.setCardBackgroundColor(defaultColor);
            if (inner != null) inner.setBackground(null);
            return;
        }
        card.setCardBackgroundColor(bg.from);
        if (inner != null) inner.setBackground(gradient(bg, card.getRadius()));
    }

    private static GradientDrawable gradient(Background bg, float radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{bg.from, bg.to});
        drawable.setCornerRadius(radius);
        return drawable;
    }

    @Nullable
    private static Background find(@Nullable String id) {
        if (id == null) return null;
        for (Background bg : BACKGROUNDS) {
            if (bg != null && bg.id.equals(id)) return bg;
        }
        return null;
    }

    /** 飾りを選ぶ画面。上に見本のカード、下にアイコンと背景の一覧。保存したら onSaved を呼ぶ */
    public static void showEditor(Context context, String horseName, String displayName, Runnable onSaved) {
        Style current = get(horseName);
        String[] icon = {current != null ? current.icon : null};
        Background[] bg = {current != null ? find(current.background) : null};

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = HorseViews.dp(context, 20);
        root.setPadding(pad, HorseViews.dp(context, 8), pad, 0);

        // 見本のカード（選ぶたびに描き直す）
        FrameLayout previewHolder = new FrameLayout(context);
        root.addView(previewHolder);
        Runnable[] refresh = new Runnable[1];

        root.addView(label(context, Lang.t("アイコン", "Icon")));
        GridLayout icons = grid(context);
        List<View> iconCells = new ArrayList<>();
        for (String choice : ICONS) {
            View cell = avatarOf(context, choice, 40);
            cell.setLayoutParams(cellParams(context));
            cell.setOnClickListener(v -> {
                icon[0] = choice;
                refresh[0].run();
            });
            iconCells.add(cell);
            icons.addView(cell);
        }
        root.addView(icons);

        root.addView(label(context, Lang.t("背景", "Background")));
        GridLayout backgrounds = grid(context);
        List<View> bgCells = new ArrayList<>();
        for (Background choice : BACKGROUNDS) {
            TextView cell = new TextView(context);
            cell.setLayoutParams(cellParams(context));
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(10);
            cell.setTextColor(0xFF555555);
            cell.setText(choice != null ? choice.label : Lang.t("いつもの", "Default"));
            cell.setOnClickListener(v -> {
                bg[0] = choice;
                refresh[0].run();
            });
            bgCells.add(cell);
            backgrounds.addView(cell);
        }
        root.addView(backgrounds);

        refresh[0] = () -> {
            previewHolder.removeAllViews();
            previewHolder.addView(preview(context, icon[0], bg[0], displayName));
            for (int i = 0; i < iconCells.size(); i++) markSelected(iconCells.get(i), ICONS[i] == null ? icon[0] == null : ICONS[i].equals(icon[0]), 0xFFE8F5E9, null);
            for (int i = 0; i < bgCells.size(); i++) {
                Background b = BACKGROUNDS[i];
                markSelected(bgCells.get(i), b == bg[0], 0xFFFFFFFF, b);
            }
        };
        refresh[0].run();

        ScrollView scroll = new ScrollView(context);
        scroll.addView(root);
        new AlertDialog.Builder(context)
                .setTitle(Lang.t(displayName + "のカードを飾る", "Decorate " + displayName + "'s card"))
                .setView(scroll)
                .setPositiveButton(Lang.t("保存", "Save"), (d, w) -> {
                    save(horseName, icon[0], bg[0] != null ? bg[0].id : null);
                    onSaved.run();
                })
                .setNeutralButton(Lang.t("元に戻す", "Reset"), (d, w) -> {
                    save(horseName, null, null);
                    onSaved.run();
                })
                .setNegativeButton(Lang.t("キャンセル", "Cancel"), null)
                .show();
    }

    private static View preview(Context context, @Nullable String icon, @Nullable Background bg, String displayName) {
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(HorseViews.dp(context, 16));
        card.setCardElevation(HorseViews.dp(context, 2));
        card.setStrokeWidth(0);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = HorseViews.dp(context, 4);
        params.setMargins(m, m, m, m);
        card.setLayoutParams(params);
        LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        int pad = HorseViews.dp(context, 14);
        inner.setPadding(pad, pad, pad, pad);
        card.addView(inner, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        inner.addView(avatarOf(context, icon, 48));
        TextView name = new TextView(context);
        name.setText(displayName);
        name.setTextSize(18);
        name.setTextColor(0xFF1B1B1B);
        name.setTypeface(name.getTypeface(), Typeface.BOLD);
        name.setPadding(HorseViews.dp(context, 12), 0, 0, 0);
        inner.addView(name);
        applyBackgroundOf(card, bg, 0xFFFFFFFF);
        return card;
    }

    /** 選んでいるものに緑の枠を付ける。背景の見本は、その色のグラデーションで塗る */
    private static void markSelected(View cell, boolean selected, int fill, @Nullable Background bg) {
        GradientDrawable drawable = bg != null
                ? new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{bg.from, bg.to})
                : new GradientDrawable();
        if (bg == null) drawable.setColor(fill);
        drawable.setShape(GradientDrawable.OVAL);
        Context context = cell.getContext();
        drawable.setStroke(HorseViews.dp(context, selected ? 3 : 1), selected ? 0xFF2E7D32 : 0xFFDDDDDD);
        cell.setBackground(drawable);
    }

    private static TextView label(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(0xFF388E3C);
        view.setTypeface(view.getTypeface(), Typeface.BOLD);
        view.setPadding(0, HorseViews.dp(context, 14), 0, HorseViews.dp(context, 4));
        return view;
    }

    private static GridLayout grid(Context context) {
        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(6);
        return grid;
    }

    private static GridLayout.LayoutParams cellParams(Context context) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        int size = HorseViews.dp(context, 40);
        params.width = size;
        params.height = size;
        int m = HorseViews.dp(context, 4);
        params.setMargins(m, m, m, m);
        return params;
    }
}
