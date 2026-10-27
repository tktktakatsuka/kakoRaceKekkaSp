package com.tktkcompany.kakoRaceKeiba.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.threeten.bp.LocalDateTime;

import java.util.Arrays;
import java.util.Collections;

public class RaceStartNoticesTest {

    private static final String INFO = "3歳上3勝クラス　(混) 牝 (特) 定量 ダ1800m 15頭 15:30発走 本賞金　1870万　750万";

    @Test
    public void readsTheStartTime() {
        assertEquals(LocalDateTime.of(2026, 10, 17, 15, 30), RaceStartNotices.startTime("20261017", INFO));
        assertEquals(LocalDateTime.of(2026, 10, 17, 9, 55),
                RaceStartNotices.startTime("20261017", "　[指] 馬齢 ダ1400m 10頭 09:55発走 本賞金　590万"));
    }

    @Test
    public void noStartTimeWhenMissing() {
        assertNull(RaceStartNotices.startTime("20261017", "ダ1800m 15頭"));
        assertNull(RaceStartNotices.startTime("20261017", null));
    }

    @Test
    public void singleHorse() {
        RaceStartNotices.Horse horse = new RaceStartNotices.Horse("アンムート", "5", null, null);
        assertEquals("★アンムートがまもなく出走", RaceStartNotices.title(Collections.singletonList(horse)));
        assertEquals("15:30発走 京都11R トルマリンステークス（ダ1800m）\n5番 アンムート",
                RaceStartNotices.body(LocalDateTime.of(2026, 10, 17, 15, 30), "京都", 11, "トルマリンステークス", INFO,
                        Collections.singletonList(horse), null));
    }

    @Test
    public void severalHorsesInOneRace() {
        RaceStartNotices.Horse a = new RaceStartNotices.Horse("アンムート", "5", null, null);
        RaceStartNotices.Horse b = new RaceStartNotices.Horse("クラリス", null, null, null);
        assertEquals("★推し馬2頭がまもなく出走！", RaceStartNotices.title(Arrays.asList(a, b)));
        assertEquals("9:55発走 京都1R\n5番 アンムート\nクラリス",
                RaceStartNotices.body(LocalDateTime.of(2026, 10, 17, 9, 55), "京都", 1, null, null, Arrays.asList(a, b), "9:25"));
    }

    @Test
    public void showsOddsAndWhenTheyWereTaken() {
        RaceStartNotices.Horse a = new RaceStartNotices.Horse("アンムート", "3", "5.3", 3);
        RaceStartNotices.Horse b = new RaceStartNotices.Horse("クラリス", "7", null, null);
        assertEquals("13:45発走 京都8R\n3番 アンムート 単勝5.3倍（3番人気）\n7番 クラリス\nオッズは13:15時点",
                RaceStartNotices.body(LocalDateTime.of(2026, 10, 17, 13, 45), "京都", 8, null, null, Arrays.asList(a, b), "13:15"));
    }
}
