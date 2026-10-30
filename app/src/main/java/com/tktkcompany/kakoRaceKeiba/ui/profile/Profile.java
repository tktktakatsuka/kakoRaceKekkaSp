package com.tktkcompany.kakoRaceKeiba.ui.profile;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * プロフ帳の中身（自分で書く文字と、選んだ推し）。Android と Firebase には依存しない。
 * 写真はここには入れない（端末の中だけ。ProfilePhoto）。
 */
public final class Profile {

    /** 1項目の長さ（文字数） */
    public static final int MAX = 40;
    /** つぶやきの傾向（0=日常 … 4=馬）。真ん中が 2 */
    public static final int TENDENCY_MAX = 4;

    public String name = "";
    public String career = "";
    public int tendency = 2;
    public String jockeys = "";
    public String love = "";
    public String race = "";
    public String wish = "";
    public String note = "";
    /** 現役の推し 1・2・3（推し馬の名前） */
    public List<String> oshi = new ArrayList<>();
    /** アイコンの絵文字（写真を使わないとき） */
    @Nullable public String emoji;
    /** 端末に保存した写真をアイコンに使う */
    public boolean usePhoto;

    /** 前後の空白を取り、40文字（絵文字も1文字）までにする */
    public static String clamp(@Nullable String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.codePointCount(0, t.length()) <= MAX) return t;
        return t.substring(0, t.offsetByCodePoints(0, MAX));
    }

    public static int tendency(int value) {
        return value < 0 || value > TENDENCY_MAX ? 2 : value;
    }

    /** まだ選んでいないときの推し：推し馬の並びの先頭3頭 */
    public static List<String> defaultOshi(List<String> favorites) {
        Profile p = new Profile();
        p.setOshi(favorites);
        return p.oshi;
    }

    /** 空と重複を除いて、最大3頭 */
    public void setOshi(List<String> names) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String n : names) {
            if (n != null && !n.trim().isEmpty()) set.add(n.trim());
            if (set.size() == 3) break;
        }
        oshi = new ArrayList<>(set);
    }

    /** まだ何も書いていない（ホームに「つくろう」を出すかどうか） */
    public boolean isEmpty() {
        return name.isEmpty() && note.isEmpty() && oshi.isEmpty();
    }
}
