package com.tktkcompany.kakoRaceKeiba.ads;

import android.content.Context;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * 広告を出さないアカウント（開発者本人など）。
 * Firebase Realtime Database の adFree/{uid} が true のアカウントでログインしていれば、
 * バナーを出さず、近走10走もリワード広告なしで表示する。
 * adFree はアプリからは書き込めない（Firebase のコンソールで設定する）。
 */
public final class AdFree {

    private static final String PREFS = "ads";
    // 広告なしと確かめたアカウントの uid。次に起動したときにすぐ広告を隠せるよう覚えておく
    private static final String KEY_UID = "ad_free_uid";
    private static final String TAG = "AdFree";

    private AdFree() {
    }

    /** 今ログインしているアカウントが広告なしなら true */
    public static boolean isOn(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return false;
        String uid = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_UID, null);
        return user.getUid().equals(uid);
    }

    /** Firebase で広告なしかを確かめ直して覚える。ログイン・ログアウトのあとと起動時に呼ぶ */
    public static void refresh(Context context, @Nullable Runnable onChanged) {
        Context app = context.getApplicationContext();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            save(app, null, onChanged);
            return;
        }
        String uid = user.getUid();
        FirebaseDatabase.getInstance().getReference("adFree").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        save(app, Boolean.TRUE.equals(snapshot.getValue(Boolean.class)) ? uid : null, onChanged);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // 読めない（ルール未設定など）ときは広告を出す
                        Log.w(TAG, "広告なしの設定を読めませんでした: " + error.getMessage());
                        save(app, null, onChanged);
                    }
                });
    }

    private static void save(Context app, @Nullable String uid, @Nullable Runnable onChanged) {
        android.content.SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String before = prefs.getString(KEY_UID, null);
        if (uid == null ? before == null : uid.equals(before)) return;
        prefs.edit().putString(KEY_UID, uid).apply();
        if (onChanged != null) onChanged.run();
    }

    /** 表示中の画面のバナーを、今の設定に合わせて隠す（ログインした直後など） */
    public static void applyTo(View root) {
        if (root instanceof AdView) {
            if (isOn(root.getContext())) root.setVisibility(View.GONE);
            return;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) applyTo(group.getChildAt(i));
        }
    }
}
