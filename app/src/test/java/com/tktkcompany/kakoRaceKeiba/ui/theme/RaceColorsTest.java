package com.tktkcompany.kakoRaceKeiba.ui.theme;

import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class RaceColorsTest {

    @Test
    public void topThreeRanksAreDistinct() {
        assertNotEquals(RaceColors.rank("1"), RaceColors.rank("2"));
        assertNotEquals(RaceColors.rank("2"), RaceColors.rank("3"));
        assertNotEquals(RaceColors.rank("1"), RaceColors.rank("3"));
        assertNotEquals(RaceColors.rank("3"), RaceColors.rank("4"));
    }

    @Test
    public void honmeiMarkStandsOut() {
        assertNotEquals(RaceColors.mark("◎"), RaceColors.mark("○"));
    }
}
