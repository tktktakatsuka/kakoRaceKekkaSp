package com.tktkcompany.kakoRaceKeiba.ui.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class ProfileTest {

    @Test
    public void clampsTo40() {
        String long50 = "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん";
        assertEquals(40, Profile.clamp(long50).codePointCount(0, Profile.clamp(long50).length()));
        assertEquals("むぎ", Profile.clamp("  むぎ  "));
        assertEquals("", Profile.clamp(null));
    }

    @Test
    public void clampKeepsEmojiWhole() {
        // 絵文字（サロゲートペア）を途中で切らない
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 45; i++) sb.append("🐴");
        String s = Profile.clamp(sb.toString());
        assertEquals(40, s.codePointCount(0, s.length()));
    }

    @Test
    public void tendencyOutOfRangeIsMiddle() {
        assertEquals(0, Profile.tendency(0));
        assertEquals(4, Profile.tendency(4));
        assertEquals(2, Profile.tendency(-1));
        assertEquals(2, Profile.tendency(5));
    }

    @Test
    public void defaultOshiTakesFirstThree() {
        assertEquals(Arrays.asList("A", "B", "C"), Profile.defaultOshi(Arrays.asList("A", "B", "C", "D")));
        assertEquals(Arrays.asList("A"), Profile.defaultOshi(Arrays.asList("A")));
    }

    @Test
    public void oshiHasNoDuplicates() {
        Profile p = new Profile();
        p.setOshi(Arrays.asList("A", "A", "B", "", "C", "D"));
        assertEquals(Arrays.asList("A", "B", "C"), p.oshi);
    }

    @Test
    public void emptyProfile() {
        Profile p = new Profile();
        assertTrue(p.isEmpty());
        p.name = "むぎ";
        assertFalse(p.isEmpty());
    }
}
