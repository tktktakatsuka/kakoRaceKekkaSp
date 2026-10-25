package com.tktkcompany.kakoRaceKeiba.ads;

import com.tktkcompany.kakoRaceKeiba.BuildConfig;

/**
 * 広告ユニットの ID をまとめたもの。
 * debug 版では本番の広告を出さないよう（自分で押すと無効なクリックになる）、Google のテスト用 ID を使う。
 */
public final class AdUnits {

    // Google のテスト用 ID（何度表示・タップしても問題ない）
    private static final String TEST_BANNER = "ca-app-pub-3940256099942544/9214589741";
    private static final String TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712";
    private static final String TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917";

    public static final String BANNER_HOME = pick(TEST_BANNER, "ca-app-pub-4855274440005459/6616268445");
    public static final String BANNER_RACE_RESULTS = pick(TEST_BANNER, "ca-app-pub-4855274440005459/4453139536");
    public static final String BANNER_RESULT_LIST = pick(TEST_BANNER, "ca-app-pub-4855274440005459/3128258657");
    public static final String BANNER_MEMOS = pick(TEST_BANNER, "ca-app-pub-4855274440005459/8405629625");
    public static final String BANNER_TRENDS = pick(TEST_BANNER, "ca-app-pub-4855274440005459/6029945088");

    /** レース結果を何回か見て戻るときの全画面広告 */
    public static final String INTERSTITIAL = pick(TEST_INTERSTITIAL, "ca-app-pub-4855274440005459/4078939329");

    /** 出走表の近走を10走まで広げるリワード広告 */
    public static final String REWARDED_RECENT_RUNS = pick(TEST_REWARDED, "ca-app-pub-4855274440005459/7569100804");

    private AdUnits() {
    }

    private static String pick(String debug, String release) {
        return BuildConfig.DEBUG ? debug : release;
    }
}
