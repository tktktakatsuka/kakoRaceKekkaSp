package com.tktkcompany.kakoRaceKeiba.ui.favorite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.tktkcompany.kakoRaceKeiba.ui.prediction.Bets;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MitsugiTest {

    private static final String RACE = Mitsugi.raceKey("20261025", "東京", 11);
    private static final Set<String> FAVORITES = new HashSet<>(Arrays.asList("サクラ", "ソラ", "キン"));

    /** 5番サクラ（枠3）・10番ソラ（枠5）・9番ほか（枠5）・12番ほか（枠6） */
    private static Map<String, List<Mitsugi.Runner>> runners() {
        Map<String, List<Mitsugi.Runner>> map = new HashMap<>();
        map.put(RACE, Arrays.asList(
                new Mitsugi.Runner("5", "3", "サクラ"),
                new Mitsugi.Runner("10", "5", "ソラ"),
                new Mitsugi.Runner("9", "5", "ホカノウマ"),
                new Mitsugi.Runner("12", "6", "マタホカ")));
        return map;
    }

    private static Bets.Bet bet(String type, String numbers, int amount, Integer payout) {
        return new Bets.Bet("id" + numbers + type, "20261025", "東京", 11, null, type, numbers, amount, payout);
    }

    @Test
    public void winBetOnFavoriteCountsFully() {
        Map<String, Mitsugi.Tally> t = Mitsugi.tally(Arrays.asList(bet("単勝", "5", 1000, 3200)), runners(), FAVORITES);
        assertEquals(1000, t.get("サクラ").spent);
        assertEquals(3200, t.get("サクラ").returned);
    }

    @Test
    public void comboCountsFullyForEachFavorite() {
        Map<String, Mitsugi.Tally> t = Mitsugi.tally(Arrays.asList(bet("馬連", "5-10", 1000, 0)), runners(), FAVORITES);
        assertEquals(1000, t.get("サクラ").spent);
        assertEquals(1000, t.get("ソラ").spent);
    }

    @Test
    public void comboWithoutFavoriteIsIgnored() {
        Map<String, Mitsugi.Tally> t = Mitsugi.tally(Arrays.asList(bet("馬連", "9-12", 1000, 0)), runners(), FAVORITES);
        assertTrue(t.isEmpty());
    }

    @Test
    public void wakurenUsesBracket() {
        // 枠連 3-5：枠3のサクラ、枠5のソラに数える。馬番5（サクラ）とは関係なく、枠で見る
        Map<String, Mitsugi.Tally> t = Mitsugi.tally(Arrays.asList(bet("枠連", "3-5", 500, null)), runners(), FAVORITES);
        assertEquals(500, t.get("サクラ").spent);
        assertEquals(500, t.get("ソラ").spent);
        // 枠連 5-6：枠3のサクラは入らない（馬番5があっても）
        Map<String, Mitsugi.Tally> u = Mitsugi.tally(Arrays.asList(bet("枠連", "5-6", 500, null)), runners(), FAVORITES);
        assertFalse(u.containsKey("サクラ"));
        assertEquals(500, u.get("ソラ").spent);
    }

    @Test
    public void unsettledCountsSpentOnly() {
        Map<String, Mitsugi.Tally> t = Mitsugi.tally(Arrays.asList(bet("複勝", "5", 700, null)), runners(), FAVORITES);
        assertEquals(700, t.get("サクラ").spent);
        assertEquals(0, t.get("サクラ").returned);
        assertFalse(Mitsugi.paidBack(t.get("サクラ")));
    }

    @Test
    public void noBetsMeansNoTally() {
        Map<String, Mitsugi.Tally> t = Mitsugi.tally(Arrays.asList(bet("単勝", "5", 1000, 0)), runners(), FAVORITES);
        assertFalse(t.containsKey("キン"));
    }

    @Test
    public void matchesNormalizedName() {
        Map<String, List<Mitsugi.Runner>> map = new HashMap<>();
        map.put(RACE, Arrays.asList(new Mitsugi.Runner("5", "3", "(外)サクラ ")));
        Map<String, Mitsugi.Tally> t = Mitsugi.tally(Arrays.asList(bet("単勝", "5", 1000, 0)), map, FAVORITES);
        assertEquals(1000, t.get("サクラ").spent);
    }

    @Test
    public void raceWithoutRunnersIsSkipped() {
        Bets.Bet unknown = new Bets.Bet("x", "20261018", "京都", 3, null, "単勝", "5", 9000, 0);
        Map<String, Mitsugi.Tally> t = Mitsugi.tally(Arrays.asList(unknown, bet("単勝", "5", 1000, 2000)), runners(), FAVORITES);
        assertEquals(1000, t.get("サクラ").spent);
        assertEquals(2000, t.get("サクラ").returned);
        assertEquals(1, t.get("サクラ").bets.size());
    }

    @Test
    public void titleThresholds() {
        assertNull(Mitsugi.title(0));
        assertEquals(Mitsugi.Title.SPROUT, Mitsugi.title(1));
        assertEquals(Mitsugi.Title.SPROUT, Mitsugi.title(4999));
        assertEquals(Mitsugi.Title.FAN, Mitsugi.title(5000));
        assertEquals(Mitsugi.Title.CHEER, Mitsugi.title(20000));
        assertEquals(Mitsugi.Title.PATRON, Mitsugi.title(50000));
        assertEquals(Mitsugi.Title.LEGEND, Mitsugi.title(100000));
    }

    @Test
    public void paidBackOnlyWhenReturnedExceedsSpent() {
        Mitsugi.Tally even = new Mitsugi.Tally();
        even.spent = 1000;
        even.returned = 1000;
        assertFalse(Mitsugi.paidBack(even));
        Mitsugi.Tally more = new Mitsugi.Tally();
        more.spent = 1000;
        more.returned = 1001;
        assertTrue(Mitsugi.paidBack(more));
    }

    @Test
    public void racesToSettlePicksPastUnsettled() {
        List<Bets.Bet> bets = Arrays.asList(
                new Bets.Bet("a", "20261025", "東京", 11, null, "単勝", "5", 100, null),
                new Bets.Bet("b", "20261025", "東京", 11, null, "複勝", "5", 100, null),  // 同じレース（重複なし）
                new Bets.Bet("c", "20261025", "京都", 11, null, "単勝", "1", 100, 0),     // 払戻済み
                new Bets.Bet("d", "20261101", "東京", 11, null, "単勝", "1", 100, null),  // 今日
                new Bets.Bet("e", "20261108", "東京", 11, null, "単勝", "1", 100, null)); // 未来
        assertEquals(Arrays.asList(Mitsugi.raceKey("20261025", "東京", 11)), Mitsugi.racesToSettle(bets, "20261101"));
    }
}
