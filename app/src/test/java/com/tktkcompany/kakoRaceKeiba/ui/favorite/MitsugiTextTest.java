package com.tktkcompany.kakoRaceKeiba.ui.favorite;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Test;

import java.util.Locale;

public class MitsugiTextTest {

    private final Locale original = Locale.getDefault();

    @After
    public void restore() {
        Locale.setDefault(original);
    }

    private static Mitsugi.Tally tally(int spent, int returned) {
        Mitsugi.Tally t = new Mitsugi.Tally();
        t.spent = spent;
        t.returned = returned;
        return t;
    }

    @Test
    public void moneyLabelJa() {
        Locale.setDefault(Locale.JAPAN);
        assertEquals("貢いだ 23,000円／回収 8,400円", Mitsugi.moneyLabel(tally(23000, 8400)));
    }

    @Test
    public void moneyLabelEn() {
        Locale.setDefault(Locale.US);
        assertEquals("Spent ¥23,000 / Returned ¥8,400", Mitsugi.moneyLabel(tally(23000, 8400)));
    }

    @Test
    public void titleLabelJa() {
        Locale.setDefault(Locale.JAPAN);
        assertEquals("応援団長", Mitsugi.Title.CHEER.label());
    }
}
