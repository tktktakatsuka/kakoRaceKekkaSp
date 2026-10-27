package com.tktkcompany.kakoRaceKeiba.notification;

import androidx.annotation.Nullable;

import com.tktkcompany.kakoRaceKeiba.util.Lang;
import com.tktkcompany.kakoRaceKeiba.util.WeekendDays;

import org.threeten.bp.LocalDateTime;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * 開催日の朝の「今日はお気に入りの馬が○頭走ります」通知の文面（画面や Firebase に依存しない部分）。
 * その日に走るお気に入り馬を発走順に並べる
 */
public final class RaceDayDigest {

    /** 朝の通知を出す時刻（日本時間） */
    public static final int HOUR = 8;
    public static final int MINUTE = 30;

    /** 1頭分 */
    public static final class Run {
        final String horseName;
        final String course;
        final int raceNo;
        @Nullable final LocalDateTime start;

        public Run(String horseName, String course, int raceNo, @Nullable LocalDateTime start) {
            this.horseName = horseName;
            this.course = course;
            this.raceNo = raceNo;
            this.start = start;
        }
    }

    private RaceDayDigest() {
    }

    public static String title(String date, List<Run> runs) {
        if (runs.size() == 1) {
            return Lang.t("今日は★" + runs.get(0).horseName + "が走ります", "★" + runs.get(0).horseName + " runs today");
        }
        return Lang.t("今日は推し馬が" + runs.size() + "頭走ります🏇", runs.size() + " of your favorites run today");
    }

    /** 「2026年10月24日(土)\n11:20 京都5R ★アンムート\n…」。発走時刻の早い順 */
    public static String body(String date, List<Run> runs) {
        List<Run> sorted = new ArrayList<>(runs);
        sorted.sort((a, b) -> {
            if (a.start == null || b.start == null) return a.start == null ? (b.start == null ? 0 : 1) : -1;
            return a.start.compareTo(b.start);
        });
        StringBuilder sb = new StringBuilder(WeekendDays.formatShort(date));
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("H:mm");
        for (Run run : sorted) {
            sb.append('\n');
            if (run.start != null) sb.append(run.start.format(hm)).append(' ');
            sb.append(Lang.race(run.course, run.raceNo)).append(" ★").append(run.horseName);
        }
        sb.append(Lang.t("\n発走30分前にもお知らせします", "\nWe'll also remind you 30 minutes before post time"));
        return sb.toString();
    }
}
