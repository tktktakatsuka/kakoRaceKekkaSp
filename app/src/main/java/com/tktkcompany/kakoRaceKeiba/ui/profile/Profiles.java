package com.tktkcompany.kakoRaceKeiba.ui.profile;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * プロフ帳を Firebase の users/{uid}/profile に読み書きする（ゲストも使える）。
 * 写真は保存しない（端末の中だけ。ProfilePhoto）。
 */
public final class Profiles {

    public interface Callback {
        void onLoaded(Profile profile);
    }

    private Profiles() {
    }

    /** 読めなかったとき・未ログインのときは空のプロフ帳 */
    public static void load(Callback cb) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            cb.onLoaded(new Profile());
            return;
        }
        ref(user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                Profile p = new Profile();
                p.name = str(s, "name");
                p.career = str(s, "career");
                Integer t = s.child("tendency").getValue(Integer.class);
                p.tendency = Profile.tendency(t != null ? t : 2);
                p.jockeys = str(s, "jockeys");
                p.love = str(s, "love");
                p.race = str(s, "race");
                p.wish = str(s, "wish");
                p.note = str(s, "note");
                List<String> oshi = new ArrayList<>();
                for (DataSnapshot o : s.child("oshi").getChildren()) {
                    String n = o.getValue(String.class);
                    if (n != null) oshi.add(n);
                }
                p.setOshi(oshi);
                p.emoji = s.child("emoji").getValue(String.class);
                p.usePhoto = Boolean.TRUE.equals(s.child("usePhoto").getValue(Boolean.class));
                cb.onLoaded(p);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                cb.onLoaded(new Profile());
            }
        });
    }

    public static void save(Profile p) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        Map<String, Object> v = new HashMap<>();
        v.put("name", Profile.clamp(p.name));
        v.put("career", Profile.clamp(p.career));
        v.put("tendency", Profile.tendency(p.tendency));
        v.put("jockeys", Profile.clamp(p.jockeys));
        v.put("love", Profile.clamp(p.love));
        v.put("race", Profile.clamp(p.race));
        v.put("wish", Profile.clamp(p.wish));
        v.put("note", Profile.clamp(p.note));
        v.put("oshi", new ArrayList<>(p.oshi));
        if (p.emoji != null) v.put("emoji", p.emoji);
        v.put("usePhoto", p.usePhoto);
        ref(user.getUid()).setValue(v);
    }

    private static String str(DataSnapshot s, String key) {
        String v = s.child(key).getValue(String.class);
        return v != null ? v : "";
    }

    private static DatabaseReference ref(String uid) {
        return FirebaseDatabase.getInstance().getReference().child("users").child(uid).child("profile");
    }
}
