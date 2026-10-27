package com.tktkcompany.kakoRaceKeiba.ui.home;

import androidx.annotation.Nullable;

import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;
import com.tktkcompany.kakoRaceKeiba.notification.RaceStartNotices;

import org.threeten.bp.LocalDateTime;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.List;

/**
 * ホームの主役のカードに出す推し馬を選ぶ。画面に依存しない規則だけを置く。
 * 上から順に、最初に当てはまったもの：
 * 1. 推し馬がいない → 推し馬を見つける案内
 * 2. 1時間以内に発走する推し馬 → その馬の次走（発走の早い馬）
 * 3. 今日走り終えた推し馬 → その結果（何頭もいればレース番号の大きい馬）
 * 4. 出走予定のある推し馬 → 発走の早い馬の次走
 * 5. 成績のある推し馬 → いちばん最近走った馬の結果
 * 6. どれも読めなかった → 先頭の馬を「出走予定を待っています」で
 */
final class Spotlight {

    enum Kind { ONBOARDING, UPCOMING, FINISHED, RECENT, WAITING }

    /** 走り終えた馬の結果より、次の推し馬の発走を優先しはじめる時間（分） */
    static final int SOON_MINUTES = 60;

    /** 選んだ結果 */
    static final class Pick {
        final Kind kind;
        @Nullable final FavoritesStrip.Item item;
        /** FINISHED・RECENT のときに出す結果 */
        @Nullable final SupabaseManager.RaceResult result;

        Pick(Kind kind, @Nullable FavoritesStrip.Item item, @Nullable SupabaseManager.RaceResult result) {
            this.kind = kind;
            this.item = item;
            this.result = result;
        }
    }

    private Spotlight() {
    }

    /** items の next は、まだ発走していないレースだけが入っていること */
    static Pick pick(List<FavoritesStrip.Item> items, LocalDateTime now) {
        if (items.isEmpty()) return new Pick(Kind.ONBOARDING, null, null);

        FavoritesStrip.Item soon = null;
        for (FavoritesStrip.Item item : items) {
            if (item.next == null) continue;
            LocalDateTime start = RaceStartNotices.startTime(item.next.getKaisaibi(), item.next.getRaceInfo());
            if (start == null || start.isAfter(now.plusMinutes(SOON_MINUTES))) continue;
            if (soon == null || FavoritesStrip.order(item.next).compareTo(FavoritesStrip.order(soon.next)) < 0) soon = item;
        }
        if (soon != null) return new Pick(Kind.UPCOMING, soon, null);

        String today = now.toLocalDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        FavoritesStrip.Item finished = null;
        SupabaseManager.RaceResult finishedResult = null;
        for (FavoritesStrip.Item item : items) {
            SupabaseManager.RaceResult r = item.live != null ? item.live
                    : item.last != null && today.equals(item.last.getKaisaibi()) ? item.last : null;
            if (r == null) continue;
            if (finishedResult == null || r.getRaceNo() > finishedResult.getRaceNo()) {
                finished = item;
                finishedResult = r;
            }
        }
        if (finished != null) return new Pick(Kind.FINISHED, finished, finishedResult);

        FavoritesStrip.Item next = null;
        for (FavoritesStrip.Item item : items) {
            if (item.next == null) continue;
            if (next == null || FavoritesStrip.order(item.next).compareTo(FavoritesStrip.order(next.next)) < 0) next = item;
        }
        if (next != null) return new Pick(Kind.UPCOMING, next, null);

        FavoritesStrip.Item recent = null;
        for (FavoritesStrip.Item item : items) {
            if (item.last == null) continue;
            if (recent == null || item.last.getKaisaibi().compareTo(recent.last.getKaisaibi()) > 0) recent = item;
        }
        if (recent != null) return new Pick(Kind.RECENT, recent, recent.last);

        return new Pick(Kind.WAITING, items.get(0), null);
    }
}
