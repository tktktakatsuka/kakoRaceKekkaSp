package com.tktkcompany.kakoRaceKeiba.ui.favorite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.tktkcompany.kakoRaceKeiba.ui.prediction.Bets;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class MitsugiCacheTest {

    private static final List<Bets.Bet> BETS = Collections.singletonList(
            new Bets.Bet("a", "20261025", "東京", 11, null, "単勝", "5", 100, null));

    @Test
    public void hitForSameUser() {
        MitsugiCache cache = new MitsugiCache();
        cache.put("u1", cache.version(), BETS);
        assertEquals(BETS, cache.get("u1"));
    }

    @Test
    public void staleWriteAfterInvalidateIsDropped() {
        MitsugiCache cache = new MitsugiCache();
        long started = cache.version();
        cache.invalidate(); // 読み込み中に買い目が足された
        cache.put("u1", started, BETS);
        assertNull(cache.get("u1"));
    }

    @Test
    public void otherUserMissesCache() {
        MitsugiCache cache = new MitsugiCache();
        cache.put("guest", cache.version(), BETS);
        assertNull(cache.get("google"));
    }

    @Test
    public void emptyListNotCached() {
        MitsugiCache cache = new MitsugiCache();
        cache.put("u1", cache.version(), Collections.emptyList());
        assertNull(cache.get("u1"));
    }
}
