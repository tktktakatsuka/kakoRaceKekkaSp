package com.tktkcompany.kakoRaceKeiba.ui.home;

import static org.junit.Assert.assertEquals;

import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;
import com.tktkcompany.kakoRaceKeiba.ui.home.FavoritesStrip.Item;

import org.junit.Test;
import org.threeten.bp.LocalDateTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpotlightTest {

    // 2026年11月1日(日) 13:00
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 13, 0);

    @Test
    public void onboardingWhenNoFavorites() {
        assertEquals(Spotlight.Kind.ONBOARDING, Spotlight.pick(new ArrayList<>(), NOW).kind);
    }

    @Test
    public void soonRaceBeatsTodaysResult() {
        // イブキは13:40発走（1時間以内）、アウダーシアは今日1着
        Item soon = item("イブキ", entry("イブキ", "20261101", 8, "芝2400m 13:40発走"), null, null);
        Item won = item("アウダーシア", null, null, result("アウダーシア", "20261101", 5, "1"));
        Spotlight.Pick p = Spotlight.pick(list(won, soon), NOW);
        assertEquals(Spotlight.Kind.UPCOMING, p.kind);
        assertEquals("イブキ", p.item.name);
    }

    @Test
    public void todaysResultBeatsLaterRace() {
        // イブキは15:40発走（1時間より先）なので、今日の結果が主役
        Item later = item("イブキ", entry("イブキ", "20261101", 11, "芝2400m 15:40発走"), null, null);
        Item won = item("アウダーシア", null, null, result("アウダーシア", "20261101", 5, "1"));
        Spotlight.Pick p = Spotlight.pick(list(later, won), NOW);
        assertEquals(Spotlight.Kind.FINISHED, p.kind);
        assertEquals("アウダーシア", p.item.name);
        assertEquals("1", p.result.getRank());
    }

    @Test
    public void todaysImportedResultCountsAsFinished() {
        // 夜の取り込み後は live が無く、last が今日になる
        Item done = item("イブキ", null, result("イブキ", "20261101", 8, "3"), null);
        assertEquals(Spotlight.Kind.FINISHED, Spotlight.pick(list(done), NOW).kind);
    }

    @Test
    public void earliestUpcomingWhenNothingToday() {
        Item sun = item("イブキ", entry("イブキ", "20261108", 8, "芝2400m 13:40発走"), null, null);
        Item sat = item("カムアップローゼス", entry("カムアップローゼス", "20261107", 9, "芝2000m 14:10発走"), null, null);
        Spotlight.Pick p = Spotlight.pick(list(sun, sat), NOW);
        assertEquals(Spotlight.Kind.UPCOMING, p.kind);
        assertEquals("カムアップローゼス", p.item.name);
    }

    @Test
    public void recentResultWhenNoneRunThisWeek() {
        Item a = item("イブキ", null, result("イブキ", "20261011", 8, "1"), null);
        Item b = item("アウダーシア", null, result("アウダーシア", "20261025", 11, "9"), null);
        Spotlight.Pick p = Spotlight.pick(list(a, b), NOW);
        assertEquals(Spotlight.Kind.RECENT, p.kind);
        assertEquals("アウダーシア", p.item.name);
        assertEquals("9", p.result.getRank());
    }

    @Test
    public void nothingLoaded() {
        // 通信に失敗して、どの馬も出走予定も成績も読めなかった
        Spotlight.Pick p = Spotlight.pick(list(item("イブキ", null, null, null), item("アウダーシア", null, null, null)), NOW);
        assertEquals(Spotlight.Kind.WAITING, p.kind);
        assertEquals("イブキ", p.item.name);
    }

    @Test
    public void startedButNoResultYet() {
        // 今日発走済みで結果待ちの馬は next も live も無い。次走のある馬が主役になる
        Item waiting = item("イブキ", null, result("イブキ", "20261011", 8, "1"), null);
        Item next = item("カムアップローゼス", entry("カムアップローゼス", "20261107", 9, "芝2000m 14:10発走"), null, null);
        assertEquals("カムアップローゼス", Spotlight.pick(list(waiting, next), NOW).item.name);
    }

    @Test
    public void upcomingWithoutStartTime() {
        // 発走時刻が無いレースは「もうすぐ」にしない
        Item noTime = item("イブキ", entry("イブキ", "20261101", 8, "芝2400m"), null, null);
        Item won = item("アウダーシア", null, null, result("アウダーシア", "20261101", 5, "1"));
        assertEquals(Spotlight.Kind.FINISHED, Spotlight.pick(list(noTime, won), NOW).kind);
    }

    private static Item item(String name, SupabaseManager.RaceEntry next, SupabaseManager.RaceResult last,
                             SupabaseManager.RaceResult live) {
        Item item = new Item(name);
        item.next = next;
        item.last = last;
        item.live = live;
        return item;
    }

    private static List<Item> list(Item... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static SupabaseManager.RaceEntry entry(String name, String date, int raceNo, String info) {
        return new SupabaseManager.RaceEntry(null, date, "東京", raceNo, null, info, null, null, name,
                null, null, null, null, null, null, null, null, null, null, false, null, null, null, null);
    }

    private static SupabaseManager.RaceResult result(String name, String date, int raceNo, String rank) {
        return new SupabaseManager.RaceResult(null, date, "東京", raceNo, null, rank, null, null, name,
                null, null, null, "2", null, null, null, null, null, null, null, null);
    }
}
