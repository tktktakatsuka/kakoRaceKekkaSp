package com.tktkcompany.kakoRaceKeiba.ui.theme;

import android.content.Context;

import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

import com.tktkcompany.kakoRaceKeiba.R;

/**
 * アプリの色（金色とクリーム）。値は res/values/colors.xml と res/values-night/colors.xml の oshi_*。
 * コードで色を付けるときは 0xFF… を直接書かず、ここから読む（夜のテーマでも読みやすくするため）
 */
public final class Palette {

    private Palette() {
    }

    public static int color(Context c, @ColorRes int id) {
        return ContextCompat.getColor(c, id);
    }

    /** 本文の文字 */
    public static int ink(Context c) {
        return color(c, R.color.oshi_ink);
    }

    /** 補足の文字 */
    public static int inkSoft(Context c) {
        return color(c, R.color.oshi_ink_soft);
    }

    /** 強調（太字と合わせて使う）・文字のボタン */
    public static int accent(Context c) {
        return color(c, R.color.oshi_accent);
    }

    /** 画面の背景 */
    public static int bg(Context c) {
        return color(c, R.color.oshi_bg);
    }

    /** カードの面 */
    public static int surface(Context c) {
        return color(c, R.color.oshi_surface);
    }

    /** 2色のコントラスト比（WCAG。1〜21。文字は 4.5 以上がめやす） */
    public static double contrast(int argb1, int argb2) {
        double l1 = luminance(argb1);
        double l2 = luminance(argb2);
        return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
    }

    private static double luminance(int argb) {
        return 0.2126 * channel((argb >> 16) & 0xFF) + 0.7152 * channel((argb >> 8) & 0xFF) + 0.0722 * channel(argb & 0xFF);
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
}
