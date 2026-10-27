package com.tktkcompany.kakoRaceKeiba.notification;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.threeten.bp.LocalDateTime;

import java.util.Arrays;
import java.util.Collections;

public class RaceDayDigestTest {

    @Test
    public void oneHorse() {
        RaceDayDigest.Run run = new RaceDayDigest.Run("アンムート", "京都", 8, LocalDateTime.of(2026, 10, 24, 13, 45));
        assertEquals("今日は★アンムートが走ります", RaceDayDigest.title("20261024", Collections.singletonList(run)));
    }

    @Test
    public void severalHorsesInPostOrder() {
        RaceDayDigest.Run late = new RaceDayDigest.Run("アンムート", "京都", 11, LocalDateTime.of(2026, 10, 24, 15, 40));
        RaceDayDigest.Run early = new RaceDayDigest.Run("ダルマレッド", "東京", 5, LocalDateTime.of(2026, 10, 24, 12, 25));
        assertEquals("今日は推し馬が2頭走ります🏇", RaceDayDigest.title("20261024", Arrays.asList(late, early)));
        assertEquals("2026年10月24日(土)\n12:25 東京5R ★ダルマレッド\n15:40 京都11R ★アンムート\n発走30分前にもお知らせします",
                RaceDayDigest.body("20261024", Arrays.asList(late, early)));
    }
}
