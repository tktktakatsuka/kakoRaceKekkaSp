package com.tktkcompany.kakoRaceKeiba.notification;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class EntryNoticesTest {

    private static final EntryNotices.Item FAVORITE = new EntryNotices.Item("アンムート", "20261017", "京都", 9,
            "もみじステークス", "オープン (国)(特) 別定 芝1400m 14頭", true, null);
    private static final EntryNotices.Item MEMO = new EntryNotices.Item("セタキト", "20261018", "東京", 11,
            null, "ダ1600m", false, "4角で詰まった。次走は買い");

    @Test
    public void singleFavorite() {
        assertEquals("★アンムートが出走予定", EntryNotices.title(Collections.singletonList(FAVORITE)));
        assertEquals("2026年10月17日(土) 京都9R もみじステークス（芝1400m）", EntryNotices.body(Collections.singletonList(FAVORITE)));
    }

    @Test
    public void singleMemoShowsTheMemo() {
        assertEquals("📝セタキトが出走予定", EntryNotices.title(Collections.singletonList(MEMO)));
        assertEquals("2026年10月18日(日) 東京11R（ダ1600m）\n📝 4角で詰まった。次走は買い",
                EntryNotices.body(Collections.singletonList(MEMO)));
    }

    @Test
    public void severalHorsesAreListedOnePerLine() {
        assertEquals("推し馬・メモした馬が2頭出走予定", EntryNotices.title(Arrays.asList(FAVORITE, MEMO)));
        assertEquals("★アンムート　2026年10月17日(土) 京都9R もみじステークス\n📝セタキト　2026年10月18日(日) 東京11R",
                EntryNotices.body(Arrays.asList(FAVORITE, MEMO)));
    }

    @Test
    public void keyIdentifiesTheRaceAndHorse() {
        assertEquals("20261017|京都|9|アンムート", FAVORITE.key());
    }
}
