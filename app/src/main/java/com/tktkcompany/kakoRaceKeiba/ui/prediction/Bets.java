package com.tktkcompany.kakoRaceKeiba.ui.prediction;

import com.tktkcompany.kakoRaceKeiba.util.Lang;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.tktkcompany.kakoRaceKeiba.db.LivePayouts;
import com.tktkcompany.kakoRaceKeiba.ui.favorite.MitsugiLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自分の買い目と収支（ログイン中のユーザーのもの）。実際の馬券は買わず、記録するだけ。
 * Firebase の users/{uid}/bets/{開催日}/{id} に
 * {course, raceNo, title, type, numbers, amount, payout} を保存する。payout は結果が出たら入れる（外れは 0）。
 */
public final class Bets {

    /** 1件の買い目 */
    public static final class Bet {
        public final String id;
        public final String date;
        public final String course;
        public final int raceNo;
        @Nullable public final String title;
        /** 「単勝」〜「3連単」（{@link LivePayouts#TYPES}） */
        public final String type;
        /** 「5」「5-10」「10-5-9」 */
        public final String numbers;
        /** 購入金額（円） */
        public final int amount;
        /** 払戻（円）。結果がまだなら null */
        @Nullable public final Integer payout;

        public Bet(String id, String date, String course, int raceNo, @Nullable String title, String type,
                   String numbers, int amount, @Nullable Integer payout) {
            this.id = id;
            this.date = date;
            this.course = course;
            this.raceNo = raceNo;
            this.title = title;
            this.type = type;
            this.numbers = numbers;
            this.amount = amount;
            this.payout = payout;
        }

        public boolean isSettled() {
            return payout != null;
        }

        public boolean sameRace(String date, String course, int raceNo) {
            return this.date.equals(date) && this.course.equals(course) && this.raceNo == raceNo;
        }

        /** 「馬連 5-10 1,000円」 */
        public String label() {
            return Lang.betType(type) + " " + numbers + " " + Lang.yen(amount);
        }
    }

    public interface Callback {
        void onLoaded(List<Bet> bets);
    }

    private Bets() {
    }

    public static boolean isSignedIn() {
        return FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    /** 買い目を追加する */
    public static void add(String date, String course, int raceNo, @Nullable String title, String type,
                           String numbers, int amount) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        Map<String, Object> value = new HashMap<>();
        value.put("course", course);
        value.put("raceNo", raceNo);
        if (title != null) value.put("title", title);
        value.put("type", type);
        value.put("numbers", numbers);
        value.put("amount", amount);
        ref(user.getUid()).child(date).push().setValue(value);
        // 貢いだ金額を、次に開いたときに数え直す
        MitsugiLoader.invalidate();
    }

    public static void delete(Bet bet) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        ref(user.getUid()).child(bet.date).child(bet.id).removeValue();
        MitsugiLoader.invalidate();
    }

    /**
     * 払い戻しの表から、そのレースのまだ結果の無い買い目の払戻を計算して保存する。
     * 戻り値は払戻を入れたあとの買い目（画面にすぐ反映するため）
     */
    public static List<Bet> settle(List<Bet> bets, List<LivePayouts.Payout> payouts) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        List<Bet> settled = new ArrayList<>();
        for (Bet b : bets) {
            if (b.isSettled() || payouts.isEmpty()) {
                settled.add(b);
                continue;
            }
            int yen = LivePayouts.payoutFor(payouts, b.type, b.numbers) * (b.amount / 100);
            if (user != null) ref(user.getUid()).child(b.date).child(b.id).child("payout").setValue(yen);
            settled.add(new Bet(b.id, b.date, b.course, b.raceNo, b.title, b.type, b.numbers, b.amount, yen));
        }
        return settled;
    }

    /** すべての買い目を読む（開催日の新しい順） */
    public static void loadAll(Callback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onLoaded(Collections.emptyList());
            return;
        }
        ref(user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Bet> bets = new ArrayList<>();
                for (DataSnapshot day : snapshot.getChildren()) {
                    for (DataSnapshot b : day.getChildren()) {
                        Bet bet = parse(day.getKey(), b);
                        if (bet != null) bets.add(bet);
                    }
                }
                bets.sort((a, b) -> {
                    int c = b.date.compareTo(a.date);
                    if (c != 0) return c;
                    c = a.course.compareTo(b.course);
                    return c != 0 ? c : Integer.compare(a.raceNo, b.raceNo);
                });
                callback.onLoaded(bets);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onLoaded(Collections.emptyList());
            }
        });
    }

    @Nullable
    private static Bet parse(@Nullable String date, DataSnapshot b) {
        if (date == null || b.getKey() == null) return null;
        String course = b.child("course").getValue(String.class);
        Integer raceNo = b.child("raceNo").getValue(Integer.class);
        String type = b.child("type").getValue(String.class);
        String numbers = b.child("numbers").getValue(String.class);
        Integer amount = b.child("amount").getValue(Integer.class);
        if (course == null || raceNo == null || type == null || numbers == null || amount == null) return null;
        return new Bet(b.getKey(), date, course, raceNo, b.child("title").getValue(String.class), type, numbers, amount,
                b.child("payout").getValue(Integer.class));
    }

    /** 収支のまとめ（結果の出た買い目だけで数える） */
    public static final class Summary {
        public int invested;
        public int returned;
        public int hits;
        public int count;

        public void add(Bet b) {
            if (!b.isSettled()) return;
            invested += b.amount;
            returned += b.payout;
            count++;
            if (b.payout > 0) hits++;
        }

        /** 「投資 3,000円・払戻 4,030円（回収率 134%）・的中 1/3」。結果が無ければ空文字 */
        public String label() {
            if (count == 0) return "";
            long roi = Math.round(returned * 100.0 / invested);
            return Lang.t("投資 " + Lang.yen(invested) + "・払戻 " + Lang.yen(returned) + "（回収率 " + roi + "%）・的中 " + hits + "/" + count,
                    "Staked " + Lang.yen(invested) + " · Returned " + Lang.yen(returned) + " (ROI " + roi + "%) · Hits " + hits + "/" + count);
        }
    }

    /** 買い目が券種に合っているか（頭数と同じ馬番の重なり）。合っていなければ理由を返す */
    @Nullable
    public static String validate(String type, String numbers, int amount) {
        List<Integer> list = new ArrayList<>();
        for (String part : numbers.split("[^0-9]+")) {
            if (!part.isEmpty()) list.add(Integer.parseInt(part));
        }
        int need;
        switch (type) {
            case "単勝":
            case "複勝":
                need = 1;
                break;
            case "枠連":
            case "馬連":
            case "ワイド":
            case "馬単":
                need = 2;
                break;
            default:
                need = 3;
        }
        if (list.size() != need) return Lang.t(type + "は" + need + "頭（" + (need == 1 ? "馬番" : "馬番をハイフンで区切って") + "）で入力してください",
                Lang.betType(type) + " needs " + need + (need == 1 ? " number" : " numbers separated by hyphens"));
        if (!type.equals("枠連") && new java.util.HashSet<>(list).size() != list.size()) return Lang.t("同じ馬番が入っています", "The same number is entered twice");
        if (amount < 100 || amount % 100 != 0) return Lang.t("金額は100円単位で入力してください", "Enter the amount in units of ¥100");
        return null;
    }

    private static DatabaseReference ref(String uid) {
        return FirebaseDatabase.getInstance().getReference().child("users").child(uid).child("bets");
    }
}
