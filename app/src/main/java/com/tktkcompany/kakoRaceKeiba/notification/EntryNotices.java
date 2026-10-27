package com.tktkcompany.kakoRaceKeiba.notification;

import androidx.annotation.Nullable;

import com.tktkcompany.kakoRaceKeiba.ui.entries.EntryForm;
import com.tktkcompany.kakoRaceKeiba.util.Lang;
import com.tktkcompany.kakoRaceKeiba.util.WeekendDays;

import java.util.List;

/**
 * お気に入り馬・メモした馬の「出走予定」通知の文面を作る。
 * 画面や Firebase に依存しない文字列の組み立てだけを置く。
 */
public final class EntryNotices {

    /** 1頭・1レース分の出走予定 */
    public static final class Item {
        final String horseName;
        final String date;
        final String course;
        final int raceNo;
        @Nullable final String raceTitle;
        @Nullable final String raceInfo;
        final boolean favorite;
        /** メモした馬ならメモ本文。メモが無ければ null */
        @Nullable final String memo;

        public Item(String horseName, String date, String course, int raceNo, @Nullable String raceTitle,
                    @Nullable String raceInfo, boolean favorite, @Nullable String memo) {
            this.horseName = horseName;
            this.date = date;
            this.course = course;
            this.raceNo = raceNo;
            this.raceTitle = raceTitle;
            this.raceInfo = raceInfo;
            this.favorite = favorite;
            this.memo = memo;
        }

        /** 同じ出走を2回通知しないための目印 */
        public String key() {
            return date + "|" + course + "|" + raceNo + "|" + horseName;
        }
    }

    private EntryNotices() {
    }

    public static String title(List<Item> items) {
        if (items.size() == 1) {
            Item item = items.get(0);
            return Lang.t(mark(item) + item.horseName + "が出走予定", mark(item) + item.horseName + " is entered");
        }
        return Lang.t("推し馬・メモした馬が" + items.size() + "頭出走予定", items.size() + " of your horses are entered");
    }

    public static String body(List<Item> items) {
        if (items.size() == 1) {
            Item item = items.get(0);
            String body = raceLabel(item, true);
            if (item.memo != null) body += "\n📝 " + item.memo;
            return body;
        }
        StringBuilder sb = new StringBuilder();
        for (Item item : items) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(mark(item)).append(item.horseName).append("　").append(raceLabel(item, false));
        }
        return sb.toString();
    }

    private static String mark(Item item) {
        return item.favorite ? "★" : "📝";
    }

    /** 「10/17(土) 京都9R もみじステークス（芝1400m）」。withCourse=false なら距離を省く */
    static String raceLabel(Item item, boolean withCourse) {
        StringBuilder sb = new StringBuilder();
        sb.append(WeekendDays.formatShort(item.date)).append(' ').append(Lang.race(item.course, item.raceNo));
        if (item.raceTitle != null && !item.raceTitle.trim().isEmpty()) sb.append(' ').append(item.raceTitle.trim());
        String course = EntryForm.courseText(EntryForm.courseLabel(item.raceInfo));
        if (withCourse && !course.isEmpty()) sb.append(Lang.t("（" + course + "m）", " (" + course + "m)"));
        return sb.toString();
    }
}
