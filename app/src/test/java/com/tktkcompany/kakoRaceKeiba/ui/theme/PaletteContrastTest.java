package com.tktkcompany.kakoRaceKeiba.ui.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 昼と夜の colors.xml を読んで、文字が背景に沈まないことを確かめる（WCAG の 4.5 以上） */
public class PaletteContrastTest {

    private static final Pattern COLOR = Pattern.compile("<color name=\"(oshi_[a-z_]+)\">#([0-9A-Fa-f]{6,8})</color>");

    private static Map<String, Integer> read(String dir) throws Exception {
        File file = new File("src/main/res/" + dir + "/colors.xml");
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        Map<String, Integer> colors = new LinkedHashMap<>();
        Matcher m = COLOR.matcher(xml);
        while (m.find()) {
            String hex = m.group(2);
            if (hex.length() == 6) hex = "FF" + hex;
            colors.put(m.group(1), (int) Long.parseLong(hex, 16));
        }
        return colors;
    }

    private static void assertReadable(Map<String, Integer> colors, String fg, String bg) {
        double c = Palette.contrast(colors.get(fg), colors.get(bg));
        assertTrue(fg + " on " + bg + " = " + c, c >= 4.5);
    }

    @Test
    public void nightHasEveryOshiColor() throws Exception {
        Map<String, Integer> day = read("values");
        Map<String, Integer> night = read("values-night");
        assertTrue(day.size() >= 15);
        // oshi_card_* は淡いカードの上の文字なので、わざと夜に書かない（cardColorsStayTheSameAtNight）
        for (String name : day.keySet()) {
            if (!name.startsWith("oshi_card_")) assertTrue("夜に無い: " + name, night.containsKey(name));
        }
    }

    @Test
    public void inkOnBgIsReadableDay() throws Exception {
        assertReadable(read("values"), "oshi_ink", "oshi_bg");
    }

    @Test
    public void inkOnBgIsReadableNight() throws Exception {
        assertReadable(read("values-night"), "oshi_ink", "oshi_bg");
    }

    @Test
    public void inkSoftOnSurfaceIsReadable() throws Exception {
        assertReadable(read("values"), "oshi_ink_soft", "oshi_surface");
        assertReadable(read("values-night"), "oshi_ink_soft", "oshi_surface");
    }

    @Test
    public void onPrimaryIsReadable() throws Exception {
        assertReadable(read("values"), "oshi_on_primary", "oshi_primary");
        assertReadable(read("values-night"), "oshi_on_primary", "oshi_primary");
    }

    @Test
    public void accentOnSurfaceIsReadable() throws Exception {
        assertReadable(read("values"), "oshi_accent", "oshi_surface");
        assertReadable(read("values-night"), "oshi_accent", "oshi_surface");
    }

    /** 淡いカード（着せ替えの背景も）の上の文字は、夜も昼と同じ濃い色にする（夜に明るい文字だとカードに沈む） */
    @Test
    public void cardColorsStayTheSameAtNight() throws Exception {
        Map<String, Integer> day = read("values");
        Map<String, Integer> night = read("values-night");
        for (String name : new String[]{"oshi_card_ink", "oshi_card_ink_soft", "oshi_card_accent"}) {
            assertTrue("昼に無い: " + name, day.containsKey(name));
            assertTrue("夜で変えてはいけない: " + name, !night.containsKey(name));
        }
        assertReadable(day, "oshi_card_ink", "oshi_spot_to");
        assertReadable(day, "oshi_card_ink_soft", "oshi_surface");
        assertReadable(day, "oshi_card_accent", "oshi_surface");
    }

    @Test
    public void lightCardLayoutsUseCardColors() throws Exception {
        for (String layout : new String[]{"item_favorite_horse", "item_entry_horse"}) {
            String xml = new String(Files.readAllBytes(new File("src/main/res/layout/" + layout + ".xml").toPath()), StandardCharsets.UTF_8);
            for (String themed : new String[]{"@color/oshi_ink\"", "@color/oshi_ink_soft\"", "@color/oshi_accent\""}) {
                assertTrue(layout + " が夜に変わる色を使っている: " + themed, !xml.contains(themed));
            }
        }
    }

    @Test
    public void contrastOfBlackAndWhiteIs21() {
        assertEquals(21.0, Palette.contrast(0xFF000000, 0xFFFFFFFF), 0.01);
    }
}
