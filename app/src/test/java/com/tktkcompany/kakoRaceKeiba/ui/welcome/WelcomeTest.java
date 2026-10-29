package com.tktkcompany.kakoRaceKeiba.ui.welcome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WelcomeTest {

    @Test
    public void showsOnFreshInstall() {
        assertTrue(Welcome.shouldShow(false, 1000L, 1000L));
    }

    @Test
    public void hiddenAfterDone() {
        assertFalse(Welcome.shouldShow(true, 1000L, 1000L));
    }

    @Test
    public void hiddenForUpdatedInstall() {
        // アップデートで入った人（前から使っている人）には出さない
        assertFalse(Welcome.shouldShow(false, 1000L, 2000L));
    }
}
