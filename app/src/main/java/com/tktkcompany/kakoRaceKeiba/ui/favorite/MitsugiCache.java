package com.tktkcompany.kakoRaceKeiba.ui.favorite;

import androidx.annotation.Nullable;

import com.tktkcompany.kakoRaceKeiba.ui.prediction.Bets;

import java.util.List;

/**
 * 読んだ買い目を、アプリが動いている間だけ覚えておく。
 * - 読み込みの途中で invalidate されたら、その読み込みの結果は覚えない（足したばかりの買い目が消えないように）
 * - ログインし直して利用者が変わったら、前の人の買い目は使わない
 * - 空（読み込みの失敗と見分けがつかない）は覚えない
 */
final class MitsugiCache {

    private long version;
    @Nullable private String uid;
    @Nullable private List<Bets.Bet> bets;

    /** 読み込みを始める前に取っておき、put に渡す */
    synchronized long version() {
        return version;
    }

    synchronized void invalidate() {
        version++;
        bets = null;
        uid = null;
    }

    synchronized void put(String uid, long startedAt, List<Bets.Bet> list) {
        if (startedAt != version || list.isEmpty()) return;
        this.uid = uid;
        this.bets = list;
    }

    @Nullable
    synchronized List<Bets.Bet> get(String uid) {
        return uid.equals(this.uid) ? bets : null;
    }
}
