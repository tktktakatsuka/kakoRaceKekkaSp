# ウェルカム画面・貢いだ金額・デザインの見直し 作業計画

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 394 で、アプリ全体を「金色とクリーム」の Material 3 の見た目にそろえ、初回だけのウェルカム画面（羽のある子・3枚）と、推し馬ごとの「貢いだ／回収」金額と称号バッジを足す。

**Architecture:** 色は `colors.xml`（昼・夜）に名前を付けて集め、Java からは `Palette` 経由で読む。テーマは `Theme.Material3.DayNight.NoActionBar` ＋ `MaterialToolbar`。金額の計算は Android に依存しない `Mitsugi`（純粋な Java、単体テスト）、読み込みと払戻の書き戻しは `MitsugiLoader`、表示は `MitsugiViews`。ウェルカム画面は `WelcomeActivity`（ViewPager2）で、出すかどうかは純粋な `Welcome.shouldShow`。

**Tech Stack:** Java / XML Views、Material Components 1.14（Material 3 テーマ）、ViewPager2、Firebase Realtime DB、Supabase（`SupabaseManager.kt`）、JUnit 4。

**Spec:** `design/superpowers/specs/2026-10-29-welcome-mitsugi-redesign-design.md`

## Global Constraints

- 3つまとめて versionCode **394**（`app/build.gradle` は既に 394）。
- 作る順番：デザインの見直し（Task 1〜6）→ 貢いだ金額（Task 7〜9）→ ウェルカム画面（Task 10〜11）。仕様は「3 → 1 → 2」だが、ウェルカム画面の2枚目に称号バッジの絵を使うので、バッジ（Task 9）を先に作る（Ruling 済み）。
- 色：背景クリーム `#FFFBF1`、文字の濃い茶 `#4A3420`、強調の金 `#F5C542`／こはく `#B86A00`、濃い面の茶 `#5A3A1A`→`#8B5A2B`。ウェルカム画面の背景は `#FFFBEA`→`#FFE9A8`→`#F7C255`、文字 `#5A3A1A`、強調 `#C46A00`。
- 使わない色：今の緑（`#4CAF50` `#388E3C` `#1B5E20` `#2E7D32`）、青のリンク（`#1565C0`）、オレンジ・赤の文字。赤は取り消せない操作の確認（退会など）だけ。
- 変えない色：枠番の色（1〜8）、G1/G2/G3 のバッジの色、`CardStyles` の着せ替えの背景。
- 画面に出す文字は、すべて日本語と英語（`Lang.t(ja, en)` か `values/strings.xml`＋`values-ja/strings.xml`。周りのコードに合わせる）。
- 称号：🌱はじめの一歩 1円〜／🍀ファン 5,000円〜／🌟応援団長 20,000円〜／👑パトロン 50,000円〜／💎伝説の貢ぎ手 100,000円〜。別に、回収 > 貢いだ で 🎁恩返し。
- 金額の文：「貢いだ 23,000円／回収 8,400円」、英語「Spent ¥23,000 / Returned ¥8,400」（`Lang.yen` を使う）。
- 賭けをあおる文は書かない。カードのアイコンを称号で変えない。アプリのアイコンは変えない。Web 版（`docs/`）は触らない。
- `.idea/*`、`.artifacts/`、`keystore.properties` はコミットしない。コミットのたびに対象のファイルを名指しする。
- テストの実行：`./gradlew :app:testDebugUnitTest --tests '<クラス>'`、ビルド：`./gradlew :app:assembleDebug`（リポジトリの一番上で）。

## Review Focus

- 買い目が0件の推し馬：カードに金額もバッジも出ない（0円や🌱を出さない）。→ Task 7 `noBetsMeansNoTally`、Task 9 の表示確認。
- 結果がまだの買い目しかない推し馬：貢いだ金額は出て、回収は0円、恩返しは付かない。→ Task 7 `unsettledCountsSpentOnly`。
- 馬名の表記ゆれ（「(外)」付き・前後の空白）：推し馬と同じ馬と見なす。→ Task 7 `matchesNormalizedName`。
- 出走表が取れないレースの買い目：数えず、ほかのレースの合計はこわれない。→ Task 7 `raceWithoutRunnersIsSkipped`。
- 夜のテーマ（ダークモード）で、文字が背景に沈まない。→ Task 1 `PaletteContrastTest`（昼・夜の両方）。

---

### Task 1: 色に名前を付けて集める（Palette）

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values-night/colors.xml`
- Create: `app/src/main/java/com/tktkcompany/kakoRaceKeiba/ui/theme/Palette.java`
- Test: `app/src/test/java/com/tktkcompany/kakoRaceKeiba/ui/theme/PaletteContrastTest.java`

**Interfaces:**
- Produces: 色の名前 `oshi_bg`（`#FFFBF1`）、`oshi_surface`（`#FFFFFF`）、`oshi_ink`（`#4A3420`）、`oshi_ink_soft`（`#7A5A38`）、`oshi_gold`（`#F5C542`）、`oshi_amber`（`#B86A00`）、`oshi_brown`（`#5A3A1A`）、`oshi_brown_light`（`#8B5A2B`）、`oshi_spot_from`（`#FFF6D6`）、`oshi_spot_to`（`#FFE3A0`）、`oshi_line`（`#EADFC8`）、`oshi_nav`（`#FFF6E2`）、`oshi_nav_active`（`#FFE1A0`）、`oshi_on_brown`（`#FFF3D0`）、`oshi_danger`（`#B3261E`）。夜は暗い茶の土台（`oshi_bg` `#1E1712` あたり、`oshi_ink` は明るいクリーム）で作る人が値を決める。
- Produces: `public static int Palette.color(Context c, @ColorRes int id)`（`ContextCompat.getColor` の薄い包み）と、よく使うものの近道 `Palette.ink(Context)` `Palette.inkSoft(Context)` `Palette.amber(Context)` `Palette.bg(Context)` `Palette.surface(Context)`。
- Produces: `static double Palette.contrast(int argb1, int argb2)`（WCAG のコントラスト比）。

- [ ] **Step 1: 失敗するテストを書く**

`PaletteContrastTest` は `src/main/res/values/colors.xml` と `src/main/res/values-night/colors.xml` をファイルとして読み（作業ディレクトリは `app/`）、`<color name="oshi_…">` を取り出して確かめる。

```java
@Test public void nightHasEveryOshiColor()      // 昼の oshi_* の名前が、夜にもすべてある
@Test public void inkOnBgIsReadableDay()        // contrast(oshi_ink, oshi_bg) >= 4.5
@Test public void inkOnBgIsReadableNight()      // 夜も >= 4.5
@Test public void inkSoftOnSurfaceIsReadable()  // 昼・夜とも contrast(oshi_ink_soft, oshi_surface) >= 4.5
@Test public void onBrownIsReadable()           // 昼・夜とも contrast(oshi_on_brown, oshi_brown) >= 4.5
@Test public void contrastOfBlackAndWhiteIs21() // assertEquals(21.0, Palette.contrast(0xFF000000, 0xFFFFFFFF), 0.01)
```

- [ ] **Step 2: 実行して失敗を見る** — `./gradlew :app:testDebugUnitTest --tests '*PaletteContrastTest'` → FAIL（`Palette` が無い／夜の colors.xml が無い）
- [ ] **Step 3: 色と `Palette` を作る**（今ある `green` などの色は、まだ使っている所があるので消さない。Task 6 の最後で使われなくなったものを消す）
- [ ] **Step 4: 実行して通るのを見る** — 同じコマンド → PASS
- [ ] **Step 5: コミット** — `git add` に上の4ファイル。「色に名前を付けて、昼と夜の両方で読みやすいことをテストで確かめる」

### Task 2: テーマを Material 3 にし、上の帯と下のメニューを新しくする

**Files:**
- Modify: `app/src/main/res/values/themes.xml`、`app/src/main/res/values-night/themes.xml`
- Modify: `app/src/main/res/layout/activity_main.xml`（`MaterialToolbar` を足す）
- Modify: `app/src/main/java/com/tktkcompany/kakoRaceKeiba/MainActivity.java`（`setSupportActionBar(toolbar)` を `setupActionBarWithNavController` の前に）
- Create: `app/src/main/res/color/nav_item.xml`（下のメニューの文字・アイコンの色。選択中 `oshi_ink`、ほか `oshi_ink_soft`）

**Interfaces:**
- Consumes: Task 1 の色の名前。
- Produces: テーマ `Theme.MyApplication`（親 `Theme.Material3.DayNight.NoActionBar`）。`colorPrimary`=`oshi_brown`、`colorOnPrimary`=`oshi_on_brown`、`colorPrimaryContainer`=`oshi_gold`、`colorSecondaryContainer`=`oshi_nav_active`（下のメニューの丸い囲み）、`colorSurface`=`oshi_surface`、`android:colorBackground`=`oshi_bg`、`colorOnSurface`=`oshi_ink`、`colorOnSurfaceVariant`=`oshi_ink_soft`、`colorOutline`=`oshi_line`、`colorError`=`oshi_danger`。ステータスバーは `oshi_bg` で、アイコンは暗い色（夜は明るい色）。

- [ ] **Step 1: テーマと帯を変える。** 帯は背景と同じ色・影なし。題名は画面名（今の `label`）のまま。ホームだけは、左に小さいアイコン（`@mipmap/ic_launcher_round` 24dp）と「推し馬ノート」（`app_name` ではなく新しい文字列 `brand_name`、ja「推し馬ノート」／en「Oshiuma Note」）。下のメニューは `BottomNavigationView` の Material 3 の形（選択中だけ丸い囲み）、背景 `oshi_nav`。
- [ ] **Step 2: ビルド** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- [ ] **Step 3: 実機で確かめる** — `adb install -r app/build/outputs/apk/debug/app-debug.apk`、5つのタブと、戻る矢印の出る画面（馬カルテなど）を開いて `adb exec-out screencap -p > $CLAUDE_JOB_DIR/tmp/t2_<画面>.png`。見ること：帯が細く背景となじむ、戻る矢印が見える、下のメニューの丸い囲み、ダイアログや Snackbar が崩れていない。夜のテーマ（`adb shell cmd uimode night yes`）でも1枚撮る。
- [ ] **Step 4: 全テスト** — `./gradlew :app:testDebugUnitTest` → 全部 PASS
- [ ] **Step 5: コミット** — 上のファイルを名指し。「テーマを Material 3 にし、上の帯を細く、下のメニューを新しい形にする」

### Task 3: ホームを新しい見た目にする

**Files:**
- Modify: `app/src/main/res/layout/fragment_home.xml`
- Modify: `app/src/main/java/com/tktkcompany/kakoRaceKeiba/ui/home/SpotlightCard.java`、`FavoritesStrip.java`、`MainRaceCard.java`、`HomeFragment.java`
- Modify: `app/src/main/res/layout/item_main_race.xml`、`item_main_race_row.xml`

**Interfaces:**
- Consumes: `Palette`（Task 1）、テーマ（Task 2）。

- [ ] **Step 1: 書きかえる。** たたき台は `.superpowers/brainstorm/776-1793283226/content/home-modern.html` の「案1」。
  - 主役のカード：背景 `oshi_spot_from`→`oshi_spot_to`、角 22dp。「次に走る推し馬」などの見出しは、濃い茶（`oshi_brown`）の丸い札に `oshi_on_brown` の文字。日付・レースは `oshi_ink` の太字。前走は `oshi_ink_soft`。オレンジ・赤の文字は使わない。
  - 横に並ぶカード：角 18dp、名前は1行（`maxLines=1`、`ellipsize=end`）、次走は `oshi_ink_soft`。着せ替えの背景（`CardStyles`）はそのまま。
  - 見出し（「ほかの推し馬」「…のメインレース」）は `oshi_ink`。「すべて見る ›」「通知の設定 ›」は `oshi_amber` の文字ボタン。
  - メインレースのカード：上の面を `oshi_brown`→`oshi_brown_light` に（今の青をやめる）。G1 などのバッジの色と、枠番の色は変えない。
  - ログイン：Google ログインのボタンは Material 3 の塗りのボタン（`colorPrimary`）。ログアウトは枠だけの控えめなボタン（`colorOutline`、文字 `oshi_ink`）。赤は使わない。
- [ ] **Step 2: ビルドと実機** — Task 2 Step 2〜3 と同じやり方で、推し馬がいるとき・いないとき（オンボーディングのカード）・ログイン中のホームを撮る。見ること：オレンジ・赤・青・緑の文字が無い、名前が折り返さない。
- [ ] **Step 3: 全テスト**（`SpotlightTest` `MainRacesTest` を含む）→ PASS
- [ ] **Step 4: コミット** — 「ホームを金色とクリームの見た目にし、赤とオレンジの文字をやめる」

### Task 4: 出走表の画面をそろえる

**Files:** `ui/entries/EntriesFragment.java`、`EntryDetailFragment.java`、`res/layout/fragment_entries.xml`、`item_entry_horse.xml`、`item_race_day.xml`

- [ ] **Step 1: 書きかえる。** Java の `0xFF…` と XML の `#…` の文字・背景の色を `Palette`／`@color/oshi_*` に置きかえる。決まり：本文 `oshi_ink`、補足 `oshi_ink_soft`、強調（的中・オッズの上位など）は太字＋`oshi_amber`。今の `0xFFC62828`（的中の赤）も `oshi_amber` の太字に。枠番の色・G のバッジの色は残す（どれがそれかは、変数名やコメントで確かめる。迷ったら残す）。ボタンは Material 3 の形（塗り／枠）。
- [ ] **Step 2: ビルドと実機** — 出走表の一覧、レースの詳細（買い目を記録する所、払戻の表）を撮る。
- [ ] **Step 3: 全テスト**（`EntryFormTest` を含む）→ PASS
- [ ] **Step 4: コミット** — 「出走表の画面を新しい色とボタンにそろえる」

### Task 5: 結果・払戻・通知の設定・ダッシュボードをそろえる

**Files:** `ui/raceResult/RaceResultsFragment.java`、`PayoutTable.java`、`ui/notifications/NotificationsFragment.java`、`ui/dashboard/DashboardFragment.java`、`res/layout/fragment_dashboard.xml`、`fragment_notifications.xml`、`item_past_run.xml`

- [ ] **Step 1: 書きかえる**（決まりは Task 4 と同じ。払戻の表の「券種ごとの色分け」は、緑・青をやめて `oshi_gold` 系の淡い色の濃淡にする。枠番の色は残す）
- [ ] **Step 2: ビルドと実機** — 結果の一覧、1レースの結果と払戻、通知の設定、レース傾向を撮る。
- [ ] **Step 3: 全テスト** → PASS
- [ ] **Step 4: コミット** — 「結果・払戻・通知の設定・傾向の画面をそろえる」

### Task 6: 馬カルテ・推し馬・メモ・予想・ダイアログをそろえ、古い色を消す

**Files:** `ui/horse/HorseFragment.java`、`HorseSearchFragment.java`、`HorseViews.java`、`ui/favorite/FavoriteHorsesAdapter.java`、`FavoriteHorsesFragment.java`、`ui/prediction/PredictionsFragment.java`、`ui/horsememo/*`、`ui/dialog/*`、`res/layout/fragment_favorite_horses.xml`、`fragment_horse_memos.xml`、`item_favorite_horse.xml`、`item_horse_memo.xml`、`layout_share_image.xml`、`res/values/colors.xml`

- [ ] **Step 1: 書きかえる**（決まりは Task 4 と同じ。`item_favorite_horse.xml` の `#C62828` もやめる。`CardStyles` の背景はそのまま）
- [ ] **Step 2: 古い色が残っていないか調べる** — `grep -rnE '4CAF50|388E3C|1B5E20|2E7D32|1565C0|D32F2F|C62828|E65100|purple_|teal_' app/src/main` → 枠番・G のバッジ・`CardStyles` 以外に出てこない。使われなくなった `colors.xml` の色を消す。
- [ ] **Step 3: ビルドと実機** — 馬カルテ、馬を探す画面、推し馬の一覧、カードの飾りのダイアログ、メモ、予想、シェア用の画像を撮る。夜のテーマでホームと馬カルテを撮る。
- [ ] **Step 4: 全テスト** → PASS
- [ ] **Step 5: コミット** — 「残りの画面とダイアログをそろえ、使わなくなった色を消す」

### Task 7: 貢いだ金額を数える（Mitsugi）

**Files:**
- Create: `app/src/main/java/com/tktkcompany/kakoRaceKeiba/ui/favorite/Mitsugi.java`
- Test: `app/src/test/java/com/tktkcompany/kakoRaceKeiba/ui/favorite/MitsugiTest.java`

**Interfaces:**
- Consumes: `Bets.Bet`（既存。`date, course, raceNo, type, numbers, amount, @Nullable Integer payout`）、`FavoriteHorseNames.normalize(String)`（既存）。
- Produces:
  - `public static String Mitsugi.raceKey(String date, String course, int raceNo)` → `date + "|" + course + "|" + raceNo`
  - `public static final class Mitsugi.Runner { public final String number; @Nullable public final String waku; public final String name; }`（`number` と `waku` は先頭の0なし）
  - `public static final class Mitsugi.Tally { public int spent; public int returned; public final List<Bets.Bet> bets; }`
  - `public static Map<String, Tally> Mitsugi.tally(List<Bets.Bet> bets, Map<String, List<Runner>> runnersByRace, Set<String> favoriteKeys)` — 戻り値のキーは `favoriteKeys` の要素（`normalize` 済みの推し馬の名前）。買い目が1件も無い推し馬は入れない。
  - `public enum Mitsugi.Title { SPROUT("🌱","はじめの一歩","First step",1), FAN("🍀","ファン","Fan",5000), CHEER("🌟","応援団長","Cheer captain",20000), PATRON("👑","パトロン","Patron",50000), LEGEND("💎","伝説の貢ぎ手","Legendary backer",100000); public final String emoji; public String label(); public final int from; }`
  - `@Nullable public static Title Mitsugi.title(int spent)`（0円は null）
  - `public static boolean Mitsugi.paidBack(Tally t)` → `t.returned > t.spent`（🎁 恩返し。ja「恩返し」／en「Paid back」）
  - `public static List<String> Mitsugi.racesToSettle(List<Bets.Bet> bets, String today)` → `payout == null` で `date` が `today` より前（`Bet.date` と同じ書式の文字列で比べる）のレースの `raceKey`。重複なし。

数え方：`numbers` を `[^0-9]+` で分けた番号のどれかが、そのレースの推し馬の番号（枠連だけは `waku`）に当たれば、その買い目の `amount` をまるごと `spent` に、`payout` があればまるごと `returned` に足す。1つの買い目に推し馬が2頭いれば、2頭それぞれに足す。

- [ ] **Step 1: 失敗するテストを書く**（`Bets.Bet` はコンストラクタで作る。推し馬は「サクラ」「ソラ」、レース `2026-10-25|東京|11`、出走馬 5番サクラ（枠3）・10番ソラ（枠5）・9番ほか（枠5）など）

```java
@Test public void winBetOnFavoriteCountsFully()     // 単勝 "5" 1000円 payout 3200 → サクラ spent 1000 returned 3200
@Test public void comboCountsFullyForEachFavorite() // 馬連 "5-10" 1000円 payout 0 → サクラ・ソラともに spent 1000
@Test public void comboWithoutFavoriteIsIgnored()   // 馬連 "9-12" → 結果に推し馬が入らない
@Test public void wakurenUsesBracket()              // 枠連 "3-5" 500円 → サクラ（枠3）とソラ（枠5）に spent 500。番号5の馬（枠3でない）では数えない
@Test public void unsettledCountsSpentOnly()        // payout null → spent に入り returned 0
@Test public void noBetsMeansNoTally()              // 推し馬「キン」は買い目なし → tally に「キン」のキーが無い
@Test public void matchesNormalizedName()           // 出走表の名前 "(外)サクラ " → 推し馬「サクラ」として数える
@Test public void raceWithoutRunnersIsSkipped()     // runnersByRace に無いレースの買い目は数えず、ほかのレースの合計は正しい
@Test public void titleThresholds()                 // title(0)==null, title(1)==SPROUT, title(4999)==SPROUT, title(5000)==FAN, title(20000)==CHEER, title(50000)==PATRON, title(100000)==LEGEND
@Test public void paidBackOnlyWhenReturnedExceedsSpent() // 1000/1000 → false, 1000/1001 → true
@Test public void racesToSettlePicksPastUnsettled() // 今日より前で payout null のレースだけ、重複なし。今日・未来のレース、payout 済みは入らない
```

- [ ] **Step 2: 実行して失敗を見る** — `./gradlew :app:testDebugUnitTest --tests '*MitsugiTest'` → FAIL（`Mitsugi` が無い）
- [ ] **Step 3: `Mitsugi` を作る**（Android と Firebase に依存しない）
- [ ] **Step 4: 実行して通るのを見る** → PASS 11/11
- [ ] **Step 5: コミット** — 「推し馬ごとに貢いだ金額・回収した金額・称号を数える」

### Task 8: 買い目と出走表を読み、払戻を書き戻す（MitsugiLoader）

**Files:**
- Create: `app/src/main/java/com/tktkcompany/kakoRaceKeiba/ui/favorite/MitsugiLoader.java`
- Modify: `app/src/main/java/com/tktkcompany/kakoRaceKeiba/ui/prediction/Bets.java`（`add` と `delete` の最後で `MitsugiLoader.invalidate()`）

**Interfaces:**
- Consumes: `Bets.loadAll(Callback)`、`Bets.settle(List<Bet>, List<LivePayouts.Payout>)`、`LivePayouts.fetchBlocking(date, course, raceNo)`、`SupabaseManager.INSTANCE.fetchRaceEntriesBlocking(date, course, raceNo)`（`RaceEntry.horseNumber / waku / horseName`）、無ければ `fetchRaceResultBlocking`（`RaceResult.horseNumber / waku / horseName`）、`LiveOdds.stripZeros`、`FavoriteHorseNames.load` か今の推し馬の集合、Task 7 の `Mitsugi`。
- Produces:
  - `public interface MitsugiLoader.Callback { void onLoaded(Map<String, Mitsugi.Tally> byFavorite); }`（キーは `FavoriteHorseNames.normalize` 済みの名前）
  - `public static void MitsugiLoader.load(Set<String> favoriteKeys, Callback cb)` — 背景のスレッドで読み、メインスレッドで `cb` を呼ぶ。失敗しても空の Map で呼ぶ（画面はこわさない）。
  - `public static void MitsugiLoader.invalidate()`
  - 読んだ結果は、アプリが動いている間だけ覚えておく（`invalidate` で忘れる）。出走表はレースごとに覚える。

- [ ] **Step 1: 書く。** 順番：`Bets.loadAll` → `Mitsugi.racesToSettle(bets, 今日)` のレースだけ `LivePayouts.fetchBlocking` → 払戻の表が空でなければ `Bets.settle` で書き戻し、手元の買い目を置きかえる → 買い目のあるレースの出走馬を読む（`race_entries`、0件なら `race_results`）→ `Mitsugi.tally`。「今日」は `Bet.date` と同じ書式（`RecentRunsReward.today()` か `DashboardFragment.today()` の書式に合わせる）。1レースの読み込みが失敗しても、そのレースだけ飛ばす。
- [ ] **Step 2: ビルド** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- [ ] **Step 3: 全テスト** → PASS
- [ ] **Step 4: コミット** — 「買い目と出走表を読んで貢いだ金額を出し、まだの払戻を書き戻す」

### Task 9: カード・一覧・馬カルテに金額と称号を出す

**Files:**
- Create: `app/src/main/java/com/tktkcompany/kakoRaceKeiba/ui/favorite/MitsugiViews.java`
- Modify: `ui/home/SpotlightCard.java`、`ui/home/FavoritesStrip.java`、`ui/home/HomeFragment.java`、`ui/favorite/FavoriteHorsesAdapter.java`、`ui/favorite/FavoriteHorsesFragment.java`、`ui/horse/HorseFragment.java`
- Test: `app/src/test/java/com/tktkcompany/kakoRaceKeiba/ui/favorite/MitsugiTextTest.java`

**Interfaces:**
- Consumes: Task 7・8。
- Produces:
  - `public static String Mitsugi.moneyLabel(Tally t)` → `Lang.t("貢いだ " + Lang.yen(spent) + "／回収 " + Lang.yen(returned), "Spent " + Lang.yen(spent) + " / Returned " + Lang.yen(returned))`（`Mitsugi.java` に足す）
  - `public static View MitsugiViews.badge(Context c, Mitsugi.Tally t, boolean withLabel)` — 白い丸い札に「絵文字＋称号」（`withLabel=false` なら絵文字だけ）。恩返しなら🎁の札をもう1つ並べる。`title` が null なら `null` を返す。
  - `public static View MitsugiViews.moneyLine(Context c, Mitsugi.Tally t)` — 白い半透明の角丸の帯に `moneyLabel`。
  - `public static View MitsugiViews.breakdown(Context c, Mitsugi.Tally t)` — 馬カルテ用。合計（`moneyLabel`＋バッジ）と、レースごとの行「日付 競馬場R 券種 番号 金額 → 払戻（まだなら「結果待ち」/"Pending"）」。

- [ ] **Step 1: 失敗するテストを書く**（`MitsugiTextTest`、`Lang` は日本語の設定で。`LangTest` のやり方に合わせる）

```java
@Test public void moneyLabelJa()        // spent 23000 returned 8400 → "貢いだ 23,000円／回収 8,400円"
@Test public void titleLabelJa()        // Title.CHEER.label() → "応援団長"
```

- [ ] **Step 2: 実行して失敗を見る** — `--tests '*MitsugiTextTest'` → FAIL
- [ ] **Step 3: `moneyLabel` と `MitsugiViews` を作り、つなぐ。** ホーム：`HomeFragment` が推し馬を読み終えたら `MitsugiLoader.load` を呼び、主役のカード（バッジは右上、絵文字＋称号。金額の帯はカードの下）と横のカード（右上に絵文字だけ。金額は出さない）に渡す。推し馬の一覧（`FavoriteHorsesAdapter`）：カードの右上に絵文字＋称号、名前の下に金額の帯。馬カルテ（`HorseFragment`）：その馬が推し馬で買い目があれば、成績の上に `breakdown`。買い目が0件の馬には何も出さない。読み込み中は場所を空けない（あとから出てきてもカードの高さが大きく跳ねないよう、帯は `SpotlightCard.MIN_HEIGHT_DP` の中に収める）。
- [ ] **Step 4: 実行して通るのを見る** → PASS。全テスト → PASS
- [ ] **Step 5: 実機で確かめる** — テストの買い目を出走表の画面から3件ほど記録して（単勝・馬連・枠連）、ホーム・推し馬の一覧・馬カルテを撮る。買い目のない推し馬にバッジが出ないことも見る。確かめたら、記録した買い目は消す。
- [ ] **Step 6: コミット** — 「推し馬のカードと馬カルテに、貢いだ金額と称号のバッジを出す」

### Task 10: ペガサスの絵を作る

**Files:**
- Modify: `store/icon/make-icon.js`（`face` の部品はそのまま使い、羽を足した絵を書き出す処理を足す）
- Create（書き出し）: `app/src/main/res/drawable/welcome_pegasus.xml`

**Interfaces:**
- Produces: `@drawable/welcome_pegasus`（108x108、背景なし、縮めない。羽は白・ふち `#6B4423`・羽の筋 `#E0B060`）。羽の形は `.superpowers/brainstorm/776-1793283226/content/welcome-flow.html` の `#wing` と `#wingline`（右の羽は左右反転）。

- [ ] **Step 1: `make-icon.js` に羽の部品と書き出しを足し、`node store/icon/make-icon.js` を実行。** アプリのアイコン（`ic_launcher_*`）と `icon.svg` の中身が変わっていないことを `git diff --stat` で確かめる（`welcome_pegasus.xml` と `make-icon.js` だけが変わる）。
- [ ] **Step 2: ビルド** → BUILD SUCCESSFUL
- [ ] **Step 3: コミット** — 「ウェルカム画面に出す、羽のある子の絵を作る」

### Task 11: ウェルカム画面

**Files:**
- Create: `app/src/main/java/com/tktkcompany/kakoRaceKeiba/ui/welcome/Welcome.java`、`WelcomeActivity.java`、`WelcomePagerAdapter.java`
- Create: `app/src/main/res/layout/activity_welcome.xml`、`item_welcome_page.xml`、`res/drawable/welcome_bg.xml`（`#FFFBEA`→`#FFE9A8`→`#F7C255`）
- Modify: `app/src/main/AndroidManifest.xml`（`WelcomeActivity` を足す。`exported=false`）
- Modify: `app/src/main/java/com/tktkcompany/kakoRaceKeiba/MainActivity.java`
- Modify: `app/build.gradle`・`gradle/libs.versions.toml`（`androidx.viewpager2:viewpager2:1.1.0` をはっきり足す）
- Test: `app/src/test/java/com/tktkcompany/kakoRaceKeiba/ui/welcome/WelcomeTest.java`

**Interfaces:**
- Consumes: `@drawable/welcome_pegasus`（Task 10）、`MitsugiViews.badge`（Task 9）、`CardStyles` の背景。
- Produces:
  - `public static boolean Welcome.shouldShow(boolean done, long firstInstallTime, long lastUpdateTime)` → `!done && firstInstallTime == lastUpdateTime`（アップデートで入った人には出さない）
  - `public static boolean Welcome.isDone(Context)` / `public static void Welcome.markDone(Context)`（SharedPreferences `welcome`、キー `done`）
  - `MainActivity` が `onCreate`（`savedInstanceState == null`）で `Welcome.shouldShow(...)` なら `WelcomeActivity` を `registerForActivityResult` で開き、そのときは起動時の `requestNotificationPermissionIfNeeded()` を呼ばない。ウェルカム画面が閉じたら（はじめる・スキップ・戻るボタンのどれでも）`markDone` してから `requestNotificationPermissionIfNeeded()`。

- [ ] **Step 1: 失敗するテストを書く**

```java
@Test public void showsOnFreshInstall()        // shouldShow(false, 1000L, 1000L) == true
@Test public void hiddenAfterDone()            // shouldShow(true, 1000L, 1000L) == false
@Test public void hiddenForUpdatedInstall()    // shouldShow(false, 1000L, 2000L) == false
```

- [ ] **Step 2: 実行して失敗を見る** — `--tests '*WelcomeTest'` → FAIL
- [ ] **Step 3: `Welcome` を作り、テストを通す** → PASS 3/3
- [ ] **Step 4: 画面を作る。** たたき台は `welcome-flow.html`。3枚の文（日本語／英語）：
  1. 「ようこそ、推し馬ノートへ！」／「あなたの『推し』の馬を見つけて、応援しよう」— "Welcome to Oshiuma Note!" / "Find the horses you love and cheer them on"。`welcome_pegasus` を大きく。ボタン「つぎへ」/"Next"
  2. 「カードを飾って、コレクション」／「アイコンと背景を選んで、推し馬ごとに自分だけのカードに」— "Decorate your cards and collect them" / "Pick an icon and a background to make a card for each horse"。`CardStyles` の背景で作った小さなカード3枚（🌸サクラノユメ・👑キンイロダイヤ・🍀ソラノカケラ、±12度の扇形）、真ん中のカードの右上に `MitsugiViews.badge`（🌟応援団長）、右下に小さな `welcome_pegasus`。ボタン「つぎへ」
  3. 「走る日も、結果もお知らせ」／「出番を見逃さないから、応援がもっと楽しくなる」— "Alerts on race day and for results" / "Never miss a race, and cheering gets even more fun"。吹き出し3つ（「🏇 サクラノユメが出走します」「⏰ まもなく発走！ 単勝 3.2倍」「🎉 サクラノユメが1着！」、英語も）。ボタン「はじめる」/"Get started"。この画面だけ「スキップ」を出さない。
  - 右上「スキップ」/"Skip"、下に位置の点（今の位置は横長）。文字 `#5A3A1A`、強調 `#C46A00`、ボタンは `#5A3A1A` の塗りに `#FFF7DD` の文字、角は丸く。
- [ ] **Step 5: ビルドと実機** — `adb shell pm clear com.tktkcompany.kakoRaceKeiba` してから起動 → ウェルカム画面の3枚を撮る → 「はじめる」で通知の許可を聞かれる → ホームに行く。もう一度起動して出ないこと。英語（`adb shell cmd locale set-app-locales com.tktkcompany.kakoRaceKeiba --locales en-US`）でも3枚を撮り、終わったら `ja-JP` に戻す。`adb install -r` で上書きしただけの場合に出ないことも見る（端末のアプリは pm clear で消えるので、確かめる端末をユーザーに確認してから行う）。
- [ ] **Step 6: 全テスト** → PASS
- [ ] **Step 7: コミット** — 「初めて開いたときだけ出る、ウェルカム画面を足す」

### Task 12: 仕上げ

- [ ] **Step 1: 全テストとビルド** — `./gradlew :app:testDebugUnitTest :app:assembleDebug` → 全部 PASS、BUILD SUCCESSFUL
- [ ] **Step 2: 古い色の検査をもう一度**（Task 6 Step 2 のコマンド）
- [ ] **Step 3: ストアの「新機能」の文を `store/release-notes-394.txt` に書く**（日本語・英語。ウェルカム画面、貢いだ金額と称号、見た目の一新）
- [ ] **Step 4: コミット** — 「394 の新機能の文を足す」
