package com.tktkcompany.kakoRaceKeiba.auth;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;

import java.util.HashMap;
import java.util.Map;

/**
 * ログインしなくても、お気に入り馬・メモ・予想・買い目を使えるようにする。
 * 初めて開いたときに Firebase の匿名アカウントで入り、データは users/{匿名の uid} に保存する。
 * あとで Google でログインしたら、匿名アカウントを Google アカウントにつなぎ替えて、データをそのまま引き継ぐ。
 * その Google アカウントが別の端末などですでに使われていたら、匿名のデータをその Google アカウントのデータに足す。
 */
public final class GuestAccount {

    private static final String TAG = "GuestAccount";

    public interface Callback {
        /** 終わったときの今のアカウント（失敗したら null のこともある） */
        void onDone(@Nullable FirebaseUser user, boolean success);
    }

    private GuestAccount() {
    }

    /** Google でログインしていれば true（匿名で使っているだけなら false） */
    public static boolean isGoogle(@Nullable FirebaseUser user) {
        return user != null && !user.isAnonymous();
    }

    /** 表示名。匿名アカウントをつなぎ替えた直後は空のことがあるので、Google の表示名を使う */
    public static String displayName(FirebaseUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) return user.getDisplayName();
        for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
            if (info.getDisplayName() != null && !info.getDisplayName().isEmpty()) return info.getDisplayName();
        }
        return user.getEmail() != null ? user.getEmail() : "";
    }

    /** ログインしていなければ匿名で入る（すでに入っていれば何もしない） */
    public static void ensure(@Nullable Callback callback) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            if (callback != null) callback.onDone(auth.getCurrentUser(), true);
            return;
        }
        auth.signInAnonymously().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) Log.w(TAG, "匿名でのログインに失敗", task.getException());
            if (callback != null) callback.onDone(auth.getCurrentUser(), task.isSuccessful());
        });
    }

    /** Google のアカウントでログインする。匿名で使っていたデータは引き継ぐ */
    public static void signIn(AuthCredential credential, Callback callback) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser current = auth.getCurrentUser();
        if (current == null || !current.isAnonymous()) {
            auth.signInWithCredential(credential).addOnCompleteListener(task ->
                    callback.onDone(auth.getCurrentUser(), task.isSuccessful()));
            return;
        }
        // 匿名アカウントを Google アカウントにつなぐと、uid が変わらないのでデータもそのまま
        current.linkWithCredential(credential).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onDone(auth.getCurrentUser(), true);
            } else if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                moveToExistingAccount(current.getUid(), credential, callback);
            } else {
                Log.w(TAG, "Google アカウントへのつなぎ替えに失敗", task.getException());
                callback.onDone(auth.getCurrentUser(), false);
            }
        });
    }

    /** すでにある Google アカウントでログインし、匿名のときのデータを足す（同じ馬・同じレースは匿名のほうで上書き） */
    private static void moveToExistingAccount(String guestUid, AuthCredential credential, Callback callback) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        DatabaseReference users = FirebaseDatabase.getInstance().getReference("users");
        users.child(guestUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot guestData) {
                auth.signInWithCredential(credential).addOnCompleteListener(task -> {
                    FirebaseUser user = auth.getCurrentUser();
                    if (!task.isSuccessful() || user == null) {
                        callback.onDone(user, false);
                        return;
                    }
                    Map<String, Object> merged = flatten(guestData);
                    if (merged.isEmpty()) {
                        callback.onDone(user, true);
                        return;
                    }
                    users.child(user.getUid()).updateChildren(merged).addOnCompleteListener(write -> {
                        if (!write.isSuccessful()) Log.w(TAG, "匿名のときのデータの引き継ぎに失敗", write.getException());
                        callback.onDone(user, true);
                    });
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "匿名のときのデータを読めませんでした: " + error.getMessage());
                auth.signInWithCredential(credential).addOnCompleteListener(task ->
                        callback.onDone(auth.getCurrentUser(), task.isSuccessful()));
            }
        });
    }

    public enum DeleteResult {
        DELETED,
        /** 前にログインしてから時間がたっているので、Google でもう一度ログインしてから消し直す */
        NEEDS_SIGN_IN,
        FAILED
    }

    public interface DeleteCallback {
        void onDone(DeleteResult result);
    }

    /**
     * 退会する。推し馬・メモ・予想・買い目（Firebase の users/{uid} と Supabase のお気に入り）を消してから、アカウントを消す。
     * 途中で失敗しても、もう一度呼べば続きから消せる
     */
    public static void deleteAccount(DeleteCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onDone(DeleteResult.FAILED);
            return;
        }
        String uid = user.getUid();
        // アカウントを消すと自分のデータに書き込めなくなるので、データを先に消す
        FirebaseDatabase.getInstance().getReference("users").child(uid).removeValue().addOnCompleteListener(data -> {
            if (!data.isSuccessful()) {
                Log.w(TAG, "退会: データを消せませんでした", data.getException());
                callback.onDone(DeleteResult.FAILED);
                return;
            }
            SupabaseManager.removeAllFavorites(uid, new SupabaseManager.SupabaseCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    deleteUser(user, callback);
                }

                @Override
                public void onFailure(@NonNull String message) {
                    Log.w(TAG, "退会: お気に入りを消せませんでした: " + message);
                    callback.onDone(DeleteResult.FAILED);
                }
            });
        });
    }

    private static void deleteUser(FirebaseUser user, DeleteCallback callback) {
        user.delete().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onDone(DeleteResult.DELETED);
            } else if (task.getException() instanceof FirebaseAuthRecentLoginRequiredException) {
                callback.onDone(DeleteResult.NEEDS_SIGN_IN);
            } else {
                Log.w(TAG, "退会: アカウントを消せませんでした", task.getException());
                callback.onDone(DeleteResult.FAILED);
            }
        });
    }

    /** 退会の前の本人確認。今の Google アカウントでもう一度ログインし直す */
    public static void reauthenticate(AuthCredential credential, Callback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onDone(null, false);
            return;
        }
        user.reauthenticate(credential).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) Log.w(TAG, "本人確認のログインに失敗", task.getException());
            callback.onDone(FirebaseAuth.getInstance().getCurrentUser(), task.isSuccessful());
        });
    }

    /**
     * users/{uid} の下を「favoriteHorses/馬名」「predictions/日付/レース」のように2段下までの道にして並べる。
     * 道ごとに書き込むので、Google アカウントにある別の日付・別の馬のデータは消えない
     */
    static Map<String, Object> flatten(DataSnapshot root) {
        Map<String, Object> paths = new HashMap<>();
        for (DataSnapshot kind : root.getChildren()) {
            for (DataSnapshot first : kind.getChildren()) {
                boolean nested = first.hasChildren();
                for (DataSnapshot second : first.getChildren()) {
                    if (!second.hasChildren()) {
                        nested = false;
                        break;
                    }
                }
                if (nested) {
                    for (DataSnapshot second : first.getChildren()) {
                        paths.put(kind.getKey() + "/" + first.getKey() + "/" + second.getKey(), second.getValue());
                    }
                } else {
                    paths.put(kind.getKey() + "/" + first.getKey(), first.getValue());
                }
            }
        }
        return paths;
    }
}
