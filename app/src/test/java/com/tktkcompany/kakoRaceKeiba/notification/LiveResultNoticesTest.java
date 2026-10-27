package com.tktkcompany.kakoRaceKeiba.notification;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class LiveResultNoticesTest {

    @Test
    public void winner() {
        LiveResultNotices.Finish f = new LiveResultNotices.Finish("アンムート", "1", "3", "5.3", "1:21.5");
        assertEquals("★アンムートが1着！（3番人気）", LiveResultNotices.title("京都", 8, Collections.singletonList(f)));
        assertEquals("2026年10月17日(土) 京都8R もみじステークス\n単勝5.3倍・1:21.5\n📝 タップしてメモを残せます",
                LiveResultNotices.body("20261017", "京都", 8, "もみじステークス", Collections.singletonList(f)));
    }

    @Test
    public void notWinner() {
        LiveResultNotices.Finish f = new LiveResultNotices.Finish("アンムート", "3", null, null, null);
        assertEquals("★アンムート 3着", LiveResultNotices.title("京都", 8, Collections.singletonList(f)));
        assertEquals("2026年10月17日(土) 京都8R\n📝 タップしてメモを残せます",
                LiveResultNotices.body("20261017", "京都", 8, null, Collections.singletonList(f)));
    }

    @Test
    public void severalHorses() {
        LiveResultNotices.Finish a = new LiveResultNotices.Finish("デニスバローズ", "2", "4", null, null);
        LiveResultNotices.Finish b = new LiveResultNotices.Finish("ダルマレッド", "取消", null, null, null);
        assertEquals("★京都6R 推し馬の結果", LiveResultNotices.title("京都", 6, Arrays.asList(a, b)));
        assertEquals("2026年10月17日(土) 京都6R\n2着 デニスバローズ（4番人気）\n取消 ダルマレッド\n📝 タップしてメモを残せます",
                LiveResultNotices.body("20261017", "京都", 6, null, Arrays.asList(a, b)));
    }

    @Test
    public void keyMatchesTheNightlyResult() {
        assertEquals("20261017|京都|8|アンムート", LiveResultNotices.key("20261017", "京都", 8, "アンムート"));
    }
}
