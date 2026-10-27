package com.tktkcompany.kakoRaceKeiba.share;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WinShareTest {

    @Test
    public void text() {
        assertEquals("🎉 推し馬のイブキが1着！\n"
                        + "2026年11月01日(日) 東京8R 本栖湖特別\n"
                        + "#イブキ #競馬 #推し馬\n"
                        + "推し馬の出走・結果をお知らせしてくれるアプリ👇\n"
                        + WinShare.STORE_URL,
                WinShare.text("イブキ", "20261101", "東京", 8, "本栖湖特別"));
    }

    @Test
    public void dropsMarksAndPrefix() {
        // 「(外)」などの印と「サラ系」は外す。レース名が無ければ付けない
        assertEquals("🎉 推し馬のフロムダスクが1着！\n"
                        + "2026年10月17日(土) 京都8R もみじステークス\n"
                        + "#フロムダスク #競馬 #推し馬\n"
                        + "推し馬の出走・結果をお知らせしてくれるアプリ👇\n"
                        + WinShare.STORE_URL,
                WinShare.text("(外)フロムダスク", "20261017", "京都", 8, "サラ系 もみじステークス"));
        assertEquals("🎉 推し馬のイブキが1着！\n"
                        + "2026年11月01日(日) 東京8R\n"
                        + "#イブキ #競馬 #推し馬\n"
                        + "推し馬の出走・結果をお知らせしてくれるアプリ👇\n"
                        + WinShare.STORE_URL,
                WinShare.text("イブキ", "20261101", "東京", 8, null));
    }

    @Test
    public void isWin() {
        assertTrue(WinShare.isWin("1"));
        assertTrue(WinShare.isWin(" 1 "));
        assertFalse(WinShare.isWin("2"));
        assertFalse(WinShare.isWin("11"));
        assertFalse(WinShare.isWin("取消"));
        assertFalse(WinShare.isWin(null));
    }
}
