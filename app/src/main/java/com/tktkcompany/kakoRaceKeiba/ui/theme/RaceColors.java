package com.tktkcompany.kakoRaceKeiba.ui.theme;

/**
 * 競馬の意味がある色（着順・予想の印）。見分けがつくことを優先する。
 * 淡いカードの上に置くので、夜のテーマでも同じ色。
 */
public final class RaceColors {

    private static final int GOLD = 0xFFB86A00;
    private static final int GREEN = 0xFF4E7A27;
    private static final int BROWN = 0xFF8B5A2B;
    private static final int INK = 0xFF2F3B22;

    private RaceColors() {
    }

    /** 1着は金、2着は緑、3着は茶、それ以外は本文の色 */
    public static int rank(String rank) {
        switch (rank == null ? "" : rank.trim()) {
            case "1": return GOLD;
            case "2": return GREEN;
            case "3": return BROWN;
            default: return INK;
        }
    }

    /** ◎（本命）は金で目立たせる。ほかの印は本文の色 */
    public static int mark(String mark) {
        return "◎".equals(mark) ? GOLD : INK;
    }
}
