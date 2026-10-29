package com.tktkcompany.kakoRaceKeiba.ui.favorite;

import androidx.annotation.Nullable;

import com.tktkcompany.kakoRaceKeiba.ui.entries.FavoriteHorseNames;
import com.tktkcompany.kakoRaceKeiba.ui.prediction.Bets;
import com.tktkcompany.kakoRaceKeiba.util.Lang;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 推し馬ごとの「貢いだ金額」と「回収した金額」、金額で決まる称号。Android と Firebase には依存しない（テストできるように）。
 * 推し馬が入っている買い目は、組み合わせ（馬連・3連単など）でもまるごと数える。枠連だけは枠番で見る。
 */
public final class Mitsugi {

    /** 1レースの出走馬1頭（馬番・枠番は先頭の0なし） */
    public static final class Runner {
        public final String number;
        @Nullable public final String waku;
        public final String name;

        public Runner(String number, @Nullable String waku, String name) {
            this.number = number;
            this.waku = waku;
            this.name = name;
        }
    }

    /** 1頭分の合計。bets はその馬が入っている買い目 */
    public static final class Tally {
        public int spent;
        public int returned;
        public final List<Bets.Bet> bets = new ArrayList<>();
    }

    /** 貢いだ金額で決まる称号（from 円から） */
    public enum Title {
        SPROUT("🌱", "はじめの一歩", "First step", 1),
        FAN("🍀", "ファン", "Fan", 5000),
        CHEER("🌟", "応援団長", "Cheer captain", 20000),
        PATRON("👑", "パトロン", "Patron", 50000),
        LEGEND("💎", "伝説の貢ぎ手", "Legendary backer", 100000);

        public final String emoji;
        private final String ja;
        private final String en;
        public final int from;

        Title(String emoji, String ja, String en, int from) {
            this.emoji = emoji;
            this.ja = ja;
            this.en = en;
            this.from = from;
        }

        public String label() {
            return Lang.t(ja, en);
        }
    }

    private Mitsugi() {
    }

    public static String raceKey(String date, String course, int raceNo) {
        return date + "|" + course + "|" + raceNo;
    }

    /**
     * 推し馬ごとに数える。戻り値のキーは favoriteKeys の要素（FavoriteHorseNames.normalize 済みの名前）。
     * 買い目が1件も無い推し馬と、出走馬がわからないレースの買い目は入れない。
     */
    public static Map<String, Tally> tally(List<Bets.Bet> bets, Map<String, List<Runner>> runnersByRace, Set<String> favoriteKeys) {
        Map<String, Tally> result = new LinkedHashMap<>();
        for (Bets.Bet b : bets) {
            List<Runner> runners = runnersByRace.get(raceKey(b.date, b.course, b.raceNo));
            if (runners == null) continue;
            Set<String> picked = numbers(b.numbers);
            boolean byWaku = "枠連".equals(b.type);
            for (Runner r : runners) {
                String key = FavoriteHorseNames.normalize(r.name);
                if (!favoriteKeys.contains(key)) continue;
                if (!picked.contains(byWaku ? r.waku : r.number)) continue;
                Tally t = result.get(key);
                if (t == null) {
                    t = new Tally();
                    result.put(key, t);
                }
                // 同じ買い目に同じ馬が2回出ることはないが、念のため1回だけ数える
                if (t.bets.contains(b)) continue;
                t.bets.add(b);
                t.spent += b.amount;
                if (b.payout != null) t.returned += b.payout;
            }
        }
        return result;
    }

    /** 0円なら null */
    @Nullable
    public static Title title(int spent) {
        Title found = null;
        for (Title t : Title.values()) {
            if (spent >= t.from) found = t;
        }
        return found;
    }

    /** 🎁 恩返し：回収が貢いだ金額を上回った */
    public static boolean paidBack(Tally t) {
        return t.returned > t.spent;
    }

    /** 「貢いだ 23,000円／回収 8,400円」 */
    public static String moneyLabel(Tally t) {
        return Lang.t("貢いだ " + Lang.yen(t.spent) + "／回収 " + Lang.yen(t.returned),
                "Spent " + Lang.yen(t.spent) + " / Returned " + Lang.yen(t.returned));
    }

    /** 「恩返し」 */
    public static String paidBackLabel() {
        return Lang.t("恩返し", "Paid back");
    }

    /** 今日より前で、まだ払戻の入っていない買い目のレース（重複なし）。日付は Bet.date と同じ書式 */
    public static List<String> racesToSettle(List<Bets.Bet> bets, String today) {
        Set<String> keys = new LinkedHashSet<>();
        for (Bets.Bet b : bets) {
            if (b.payout == null && b.date.compareTo(today) < 0) keys.add(raceKey(b.date, b.course, b.raceNo));
        }
        return new ArrayList<>(keys);
    }

    /** 「5-10-9」→ {5, 10, 9}（先頭の0なし） */
    private static Set<String> numbers(String numbers) {
        Set<String> set = new HashSet<>();
        for (String part : numbers.split("[^0-9]+")) {
            if (!part.isEmpty()) set.add(String.valueOf(Integer.parseInt(part)));
        }
        return set;
    }
}
