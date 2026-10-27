package com.tktkcompany.kakoRaceKeiba.ui.entries;

import com.tktkcompany.kakoRaceKeiba.util.Lang;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.navigation.Navigation;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.tktkcompany.kakoRaceKeiba.R;
import com.tktkcompany.kakoRaceKeiba.db.FirebaseManager;
import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;
import com.tktkcompany.kakoRaceKeiba.util.WeekendDays;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * ログイン中のユーザーのお気に入り馬を、出走馬と照合しやすい形で扱う。
 * 結果の馬名には「(外)」などの印が付いていることがあるので、照合は印を外した名前で行う。
 */
public final class FavoriteHorseNames {

    public interface Callback {
        void onLoaded(Set<String> favorites);
    }

    private FavoriteHorseNames() {
    }

    /** お気に入り馬を1回だけ読み込む。未ログインなら空の集合を返す */
    public static void load(Callback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onLoaded(Collections.emptySet());
            return;
        }
        FirebaseManager.readData("users/" + user.getUid() + "/favoriteHorses", new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Set<String> names = new HashSet<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    if (Boolean.TRUE.equals(child.getValue(Boolean.class)) && child.getKey() != null) {
                        names.add(normalize(child.getKey()));
                    }
                }
                callback.onLoaded(names);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onLoaded(Collections.emptySet());
            }
        });
    }

    public static boolean contains(Set<String> favorites, String horseName) {
        return favorites.contains(normalize(horseName));
    }

    /** 「(外)オフトレイル」→「オフトレイル」。Firebase のキーに使えない文字も除く */
    public static String normalize(String horseName) {
        if (horseName == null) return "";
        String name = horseName.trim().replaceFirst("^[\\(（\\[［][^\\)）\\]］]{1,2}[\\)）\\]］]\\s*", "");
        // FirebaseManager.sanitizeKey と同じ（あちらは読み込むと Firebase に接続するので、ここでは使わない）
        return name.trim().replaceAll("[.\\$#\\[\\]/]", "");
    }

    /** 例: 「★出走予定: 10/18(土) 東京11R 富士S（芝1600m）5番 ›」 */
    public static String nextEntryLabel(SupabaseManager.RaceEntry e) {
        StringBuilder sb = new StringBuilder(Lang.t("★出走予定: ", "★Next: "));
        sb.append(WeekendDays.formatShort(e.getKaisaibi())).append(' ')
                .append(Lang.race(e.getKaisaijo(), e.getRaceNo()));
        String title = e.getRaceTitle();
        if (title != null && !title.trim().isEmpty()) sb.append(' ').append(title.trim());
        String course = EntryForm.courseText(EntryForm.courseLabel(e.getRaceInfo()));
        if (!course.isEmpty()) sb.append(Lang.t("（" + course + "m）", " (" + course + "m)"));
        String number = e.getHorseNumber();
        if (number != null && !number.trim().isEmpty()) sb.append(Lang.t(" " + number.trim() + "番", " #" + number.trim()));
        return sb.append(" ›").toString();
    }

    /** 出走予定のレースの出走表を開く */
    public static void openEntry(View view, SupabaseManager.RaceEntry entry) {
        Bundle args = new Bundle();
        args.putString(EntriesFragment.ARG_DATE, entry.getKaisaibi());
        args.putString(EntriesFragment.ARG_COURSE, entry.getKaisaijo());
        args.putInt(EntriesFragment.ARG_RACE_NO, entry.getRaceNo());
        args.putString(EntriesFragment.ARG_RACE_TITLE, entry.getRaceTitle() != null ? entry.getRaceTitle() : "");
        Navigation.findNavController(view).navigate(R.id.navigation_entryDetail, args);
    }

    /** お気に入りに登録したときの案内（登録すると何が起きるかを伝える）。長めに表示する */
    public static void showAdded(android.content.Context context, String horseName) {
        android.widget.Toast.makeText(context, Lang.t("★" + horseName + "を推し馬にしました。\n出走が決まったとき・発走30分前・レース結果をお知らせします。一緒に応援しましょう",
                "★ Added " + horseName + ".\nWe'll notify you of entries, 30 minutes before post time and results"), android.widget.Toast.LENGTH_LONG).show();
    }

    /** 匿名でも入れていない（通信できなかったなど）ときの案内 */
    public static void showCannotSave(android.content.Context context) {
        android.widget.Toast.makeText(context, Lang.t("推し馬を保存できませんでした。通信状態を確かめてください",
                "Couldn't save the favorite. Check your connection"), android.widget.Toast.LENGTH_SHORT).show();
    }

    /** お気に入りに追加・削除する（Firebase と Supabase の両方）。戻り値は追加後の状態 */
    public static boolean toggle(Set<String> favorites, String horseName) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return false;
        String key = normalize(horseName);
        if (favorites.remove(key)) {
            FirebaseManager.removeFavoriteHorse(user.getUid(), key);
            SupabaseManager.removeFavorite(user.getUid(), key);
            return false;
        }
        favorites.add(key);
        FirebaseManager.addFavoriteHorse(user.getUid(), key);
        SupabaseManager.addFavorite(user.getUid(), key);
        return true;
    }
}
