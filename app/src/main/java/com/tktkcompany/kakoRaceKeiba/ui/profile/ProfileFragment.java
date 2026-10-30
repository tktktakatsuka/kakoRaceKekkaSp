package com.tktkcompany.kakoRaceKeiba.ui.profile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.tktkcompany.kakoRaceKeiba.ui.entries.FavoriteHorseNames;
import com.tktkcompany.kakoRaceKeiba.ui.favorite.CardStyles;
import com.tktkcompany.kakoRaceKeiba.ui.favorite.Mitsugi;
import com.tktkcompany.kakoRaceKeiba.ui.favorite.MitsugiLoader;
import com.tktkcompany.kakoRaceKeiba.ui.horse.HorseViews;
import com.tktkcompany.kakoRaceKeiba.ui.theme.Palette;
import com.tktkcompany.kakoRaceKeiba.util.Lang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * わたしのプロフ帳。見る画面（シェア・端末に保存）と、書く画面を切りかえる。
 * 写真は端末の中だけ（ProfilePhoto）。文字と選んだ推しは Firebase（Profiles）。
 */
public class ProfileFragment extends Fragment {

    private LinearLayout content;
    @Nullable private Profile profile;
    private List<String> favorites = new ArrayList<>();
    private Map<String, Mitsugi.Tally> mitsugi = new HashMap<>();
    private boolean editing;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), uri -> {
                Context c = getContext();
                if (uri == null || c == null || profile == null) return;
                if (ProfilePhoto.save(c, uri)) {
                    profile.usePhoto = true;
                    render();
                } else {
                    Toast.makeText(c, Lang.t("写真を読み込めませんでした", "Couldn't load the photo"), Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Context c = requireContext();
        ScrollView scroll = new ScrollView(c);
        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = HorseViews.dp(c, 16);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content);
        content.addView(ProfileSheet.text(c, Lang.t("読み込み中…", "Loading…"), 14, Palette.inkSoft(c), false));
        return scroll;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        CardStyles.load(this::render);
        FavoriteHorseNames.load(f -> {
            favorites = new ArrayList<>(f);
            Collections.sort(favorites);
            render();
            MitsugiLoader.load(f, byFavorite -> {
                mitsugi = new HashMap<>(byFavorite);
                render();
            });
        });
        Profiles.load(p -> {
            profile = p;
            render();
        });
    }

    private void render() {
        Context c = getContext();
        if (c == null || content == null || profile == null || !isAdded()) return;
        if (profile.oshi.isEmpty() && !favorites.isEmpty()) profile.setOshi(Profile.defaultOshi(favorites));
        content.removeAllViews();
        if (editing) renderEdit(c);
        else renderView(c);
    }

    private void renderView(Context c) {
        Bitmap photo = ProfilePhoto.load(c);
        content.addView(ProfileSheet.build(c, profile, photo, mitsugi, false));
        content.addView(button(c, Lang.t("📤 画像にしてシェア", "📤 Share as an image"), true, v -> {
            Bitmap image = ProfileImage.render(c, ProfileSheet.build(c, profile, ProfilePhoto.load(c), mitsugi, true));
            if (!ProfileImage.share(c, image)) toast(c, Lang.t("シェアできませんでした", "Couldn't share"));
        }));
        if (ProfileImage.canSave()) {
            content.addView(button(c, Lang.t("💾 端末に保存", "💾 Save to this phone"), false, v -> {
                Bitmap image = ProfileImage.render(c, ProfileSheet.build(c, profile, ProfilePhoto.load(c), mitsugi, true));
                toast(c, ProfileImage.save(c, image)
                        ? Lang.t("写真（Pictures/推し馬ノート）に保存しました", "Saved to Pictures/推し馬ノート")
                        : Lang.t("保存できませんでした", "Couldn't save"));
            }));
        }
        content.addView(button(c, profile.isEmpty() ? Lang.t("✏️ プロフ帳を書く", "✏️ Fill in your profile") : Lang.t("✏️ 書きなおす", "✏️ Edit"), false, v -> {
            editing = true;
            render();
        }));
    }

    private void renderEdit(Context c) {
        TextView caution = ProfileSheet.text(c, Lang.t("※ 書いたことは画像になってシェアされます。本名や住所などは書かないでね",
                "※ What you write is shared as an image. Please don't include your real name or address"), 12, Palette.inkSoft(c), false);
        content.addView(caution);

        // アイコン：写真か絵文字
        content.addView(section(c, Lang.t("アイコン", "Icon")));
        HorizontalScrollView iconScroll = new HorizontalScrollView(c);
        LinearLayout icons = new LinearLayout(c);
        icons.setGravity(Gravity.CENTER_VERTICAL);
        iconScroll.addView(icons);
        icons.addView(pill(c, Lang.t("📷 写真", "📷 Photo"), profile.usePhoto, v -> pickPhoto.launch(
                new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build())));
        for (String icon : CardStyles.icons()) {
            boolean selected = !profile.usePhoto && icon.equals(profile.emoji != null ? profile.emoji : "🐴");
            icons.addView(pill(c, icon, selected, v -> {
                profile.emoji = icon;
                profile.usePhoto = false;
                ProfilePhoto.delete(c);
                render();
            }));
        }
        content.addView(iconScroll);
        TextView photoNote = ProfileSheet.text(c, Lang.t("写真はこのスマホの中だけに保存します（ネットには上げません。機種変更では引き継がれません）",
                "Photos stay on this phone only (never uploaded, not transferred to a new phone)"), 11, Palette.inkSoft(c), false);
        content.addView(photoNote);

        EditText name = field(c, Lang.t("名前（ニックネーム）", "Name (nickname)"), profile.name);
        EditText career = field(c, Lang.t("競馬歴（例：5か月）", "How long you've been a fan (e.g. 5 months)"), profile.career);

        content.addView(section(c, Lang.t("つぶやきの傾向", "What you post about")));
        LinearLayout tendencyRow = new LinearLayout(c);
        tendencyRow.setGravity(Gravity.CENTER_VERTICAL);
        tendencyRow.addView(ProfileSheet.text(c, Lang.t("日常", "Daily life"), 12, Palette.ink(c), false));
        SeekBar tendency = new SeekBar(c);
        tendency.setMax(Profile.TENDENCY_MAX);
        tendency.setProgress(Profile.tendency(profile.tendency));
        tendencyRow.addView(tendency, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tendencyRow.addView(ProfileSheet.text(c, Lang.t("馬", "Horses"), 12, Palette.ink(c), false));
        content.addView(tendencyRow);

        content.addView(section(c, Lang.t("現役の推し 1・2・3（3頭まで）", "Your top 3 horses (up to 3)")));
        List<CheckBox> boxes = new ArrayList<>();
        if (favorites.isEmpty()) {
            content.addView(ProfileSheet.text(c, Lang.t("推し馬を登録すると選べます", "Add favorite horses to pick them"), 12, Palette.inkSoft(c), false));
        }
        for (String horse : favorites) {
            CheckBox box = new CheckBox(c);
            box.setText(ProfileSheet.HorseNames.display(horse));
            box.setTextColor(Palette.ink(c));
            box.setChecked(profile.oshi.contains(horse));
            box.setTag(horse);
            box.setOnCheckedChangeListener((b, checked) -> {
                int count = 0;
                for (CheckBox other : boxes) if (other.isChecked()) count++;
                if (checked && count > 3) {
                    b.setChecked(false);
                    toast(c, Lang.t("推しは3頭までです", "Up to 3 horses"));
                }
            });
            boxes.add(box);
            content.addView(box);
        }

        EditText jockeys = field(c, Lang.t("推し騎手・厩舎など", "Favorite jockeys & stables"), profile.jockeys);
        EditText love = field(c, Lang.t("永遠のラブ（引退した馬など）", "Forever love (retired horses, etc.)"), profile.love);
        EditText race = field(c, Lang.t("心に残っているレース", "A race you'll never forget"), profile.race);
        EditText wish = field(c, Lang.t("ひとつ願いが叶うなら？", "If one wish came true?"), profile.wish);
        EditText note = field(c, Lang.t("何かひとこと", "A few words"), profile.note);

        content.addView(button(c, Lang.t("保存する", "Save"), true, v -> {
            profile.name = Profile.clamp(name.getText().toString());
            profile.career = Profile.clamp(career.getText().toString());
            profile.tendency = tendency.getProgress();
            List<String> picked = new ArrayList<>();
            for (CheckBox b : boxes) if (b.isChecked()) picked.add((String) b.getTag());
            profile.setOshi(picked);
            profile.jockeys = Profile.clamp(jockeys.getText().toString());
            profile.love = Profile.clamp(love.getText().toString());
            profile.race = Profile.clamp(race.getText().toString());
            profile.wish = Profile.clamp(wish.getText().toString());
            profile.note = Profile.clamp(note.getText().toString());
            Profiles.save(profile);
            editing = false;
            render();
        }));
        content.addView(button(c, Lang.t("やめる", "Cancel"), false, v -> {
            editing = false;
            Profiles.load(p -> {
                profile = p;
                render();
            });
        }));
    }

    private EditText field(Context c, String label, String value) {
        content.addView(section(c, label));
        EditText edit = new EditText(c);
        edit.setText(value);
        edit.setTextColor(Palette.ink(c));
        edit.setSingleLine(false);
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Profile.MAX * 2)});
        content.addView(edit);
        return edit;
    }

    private static TextView section(Context c, String s) {
        TextView tv = ProfileSheet.text(c, s, 13, Palette.accent(c), true);
        tv.setPadding(0, HorseViews.dp(c, 14), 0, HorseViews.dp(c, 2));
        return tv;
    }

    private static View pill(Context c, String s, boolean selected, View.OnClickListener onClick) {
        TextView tv = ProfileSheet.text(c, s, 18, ProfileSheet.INK, false);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? 0xFFDCEDC8 : 0xFFFFFFFF);
        bg.setCornerRadius(HorseViews.dp(c, 20));
        bg.setStroke(HorseViews.dp(c, selected ? 2 : 1), selected ? 0xFF4E7A27 : 0xFFDDE8CC);
        tv.setBackground(bg);
        int ph = HorseViews.dp(c, 12), pv = HorseViews.dp(c, 6);
        tv.setPadding(ph, pv, ph, pv);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = HorseViews.dp(c, 6);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(onClick);
        return tv;
    }

    private static View button(Context c, String s, boolean filled, View.OnClickListener onClick) {
        MaterialButton b = filled ? new MaterialButton(c)
                : new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = HorseViews.dp(c, 10);
        b.setLayoutParams(lp);
        return b;
    }

    private static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
    }
}
