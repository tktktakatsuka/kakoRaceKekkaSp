# ホーム画面の作り直し 実装計画

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ホームを「主役のカード（推し馬1頭）→ ほかの推し馬 → メインレース」に作り直し、ほかの箱を外す。

**Architecture:** 主役にする馬を選ぶ規則を、画面に依存しないクラス `Spotlight` に分けて単体テストする。今の `FavoritesStrip` が推し馬のデータ（次走・前走・当日の速報）を1回だけ読み、`Spotlight` で主役を選んで、主役のカード（`SpotlightCard`）と横並びに振り分ける。`HomeFragment` からは今週の開催・今週の出走馬・今日の開催の処理を消す。

**Tech Stack:** Java（Android View・XML）、JUnit 4、threetenbp、既存の `CardStyles`・`WinShare`・`LiveResults`・`RaceStartNotices.startTime`

**Spec:** `docs/superpowers/specs/2026-10-28-home-redesign-design.md`

## Global Constraints
- ブランチは `feature/card-share`（391）。versionCode は 391 のまま
- 画面は Android View（XML ＋ Java）。Compose にしない
- 文言は日本語と英語の両方。コードの中は `Lang.t(ja, en)`、レイアウトの文字は `values/strings.xml` と `values-ja/strings.xml`
- 色は今のものにそろえる：見出し `#2E7D32`、次走 `#E65100`、1着 `#C62828`、薄い字 `#757575`。新しい色を増やさない
- 推し馬の次走・前走は2回読まない（主役のカードと横並びで同じデータを使う）
- 単体テストは `gradlew.bat testDebugUnitTest`（日本語で実行される設定済み）

## Review Focus
1. 今日もう発走したが、まだ結果が入っていない推し馬（発走〜確定の数分〜数十分）：主役から外れても、横並びには必ず残る → Task 1 `startedButNoResultYet`
2. 推し馬がたくさんいるのに、どれも出走予定も成績も読めなかった（通信の失敗）：落ちずに、先頭の馬を「出走予定を待っています」で出す → Task 1 `nothingLoaded`
3. 出走表に発走時刻が無いレース（`raceInfo` に「15:00発走」が無い）：「もうすぐ」扱いにせず、同じ日のレースの中で最後に回す → Task 1 `upcomingWithoutStartTime`
4. 取消・除外で着順が「取消」などの結果：主役に結果として出すが、シェアボタンは出さない → Task 2 Step 3（`WinShare.isWin` が false）
5. 長い馬名（9文字のカタカナ＋外国産の印）：主役のカードで2行までに収め、レイアウトが崩れない → Task 3 Step 5（実機で確認）

---

### Task 1: 主役を選ぶ規則（`Spotlight`）

**Files:**
- Create: `app/src/main/java/com/tktkcompany/kakoRaceKeiba/ui/home/Spotlight.java`
- Modify: `app/src/main/java/com/tktkcompany/kakoRaceKeiba/ui/home/FavoritesStrip.java`（`Item` に `live` を足すだけ）
- Test: `app/src/test/java/com/tktkcompany/kakoRaceKeiba/ui/home/SpotlightTest.java`

**Interfaces:**
- Consumes: `FavoritesStrip.Item`（`name`、`@Nullable RaceEntry next`、`@Nullable RaceResult last`）、`FavoritesStrip.order(RaceEntry)`、`RaceStartNotices.startTime(String date, @Nullable String raceInfo)`
- Produces:
  - `FavoritesStrip.Item` に `@Nullable SupabaseManager.RaceResult live;`（今日の速報）を追加
  - `final class Spotlight`
    - `enum Kind { ONBOARDING, UPCOMING, FINISHED, RECENT, WAITING }`
    - `static final int SOON_MINUTES = 60;`
    - `static final class Pick { final Kind kind; @Nullable final FavoritesStrip.Item item; @Nullable final SupabaseManager.RaceResult result; }`
    - `static Pick pick(List<FavoritesStrip.Item> items, LocalDateTime now)`

**規則（上から順に、最初に当てはまったもの）**
1. `items` が空 → `ONBOARDING`（item・result は null）
2. `next` の発走が `now + 60分` 以内の馬がいる → そのうち `order(next)` が一番早い馬で `UPCOMING`
3. 今日の結果がある馬がいる（`live`、または `last.kaisaibi` が今日） → `FINISHED`。何頭もいればレース番号が一番大きい馬。`result` は `live` を優先
4. `next` がある馬がいる → `order(next)` が一番早い馬で `UPCOMING`
5. `last` がある馬がいる → `last.kaisaibi` が一番新しい馬で `RECENT`（`result` = `last`）
6. それ以外 → `items.get(0)` で `WAITING`

`next` は「まだ発走していないレース」だけが入る前提（Task 2 で `FavoritesStrip` がそうする）。

- [ ] **Step 1: 失敗するテストを書く**

`SpotlightTest`。`NOW = LocalDateTime.of(2026, 11, 1, 13, 0)`。テスト用に `item(name, next, last, live)`（`new FavoritesStrip.Item(name)` を作って3つのフィールドを入れる）、`list(Item...)`（`new ArrayList<>(Arrays.asList(...))`）、`entry(name, date, raceNo, info)`、`result(name, date, raceNo, rank)` を作る（`RaceEntry` は24引数、`raceInfo` は6番目・馬名は9番目・`scratched` は21番目。`RaceResult` は21引数、`rank` は6番目・馬名は9番目・`popular` は13番目。`HorseDiscoverTest`・`MainRacesTest` の作り方と同じ）。

```java
@Test public void onboardingWhenNoFavorites() {
    assertEquals(Spotlight.Kind.ONBOARDING, Spotlight.pick(new ArrayList<>(), NOW).kind);
}
@Test public void soonRaceBeatsTodaysResult() {
    // 13:00。イブキは13:40発走（1時間以内）、アウダーシアは今日1着
    Item soon = item("イブキ", entry("イブキ", "20261101", 8, "芝2400m 13:40発走"), null, null);
    Item won = item("アウダーシア", null, null, result("アウダーシア", "20261101", 5, "1"));
    Spotlight.Pick p = Spotlight.pick(list(won, soon), NOW);
    assertEquals(Spotlight.Kind.UPCOMING, p.kind);
    assertEquals("イブキ", p.item.name);
}
@Test public void todaysResultBeatsLaterRace() {
    // イブキは15:40発走（1時間より先）なので、今日の結果が主役
    Item later = item("イブキ", entry("イブキ", "20261101", 11, "芝2400m 15:40発走"), null, null);
    Item won = item("アウダーシア", null, null, result("アウダーシア", "20261101", 5, "1"));
    Spotlight.Pick p = Spotlight.pick(list(later, won), NOW);
    assertEquals(Spotlight.Kind.FINISHED, p.kind);
    assertEquals("アウダーシア", p.item.name);
    assertEquals("1", p.result.getRank());
}
@Test public void todaysImportedResultCountsAsFinished() {
    // 夜の取り込み後は live が無く、last が今日になる
    Item done = item("イブキ", null, result("イブキ", "20261101", 8, "3"), null);
    assertEquals(Spotlight.Kind.FINISHED, Spotlight.pick(list(done), NOW).kind);
}
@Test public void earliestUpcomingWhenNothingToday() {
    Item sun = item("イブキ", entry("イブキ", "20261108", 8, "芝2400m 13:40発走"), null, null);
    Item sat = item("カムアップローゼス", entry("カムアップローゼス", "20261107", 9, "芝2000m 14:10発走"), null, null);
    assertEquals("カムアップローゼス", Spotlight.pick(list(sun, sat), NOW).item.name);
}
@Test public void recentResultWhenNoneRunThisWeek() {
    Item a = item("イブキ", null, result("イブキ", "20261011", 8, "1"), null);
    Item b = item("アウダーシア", null, result("アウダーシア", "20261025", 11, "9"), null);
    Spotlight.Pick p = Spotlight.pick(list(a, b), NOW);
    assertEquals(Spotlight.Kind.RECENT, p.kind);
    assertEquals("アウダーシア", p.item.name);
}
@Test public void nothingLoaded() {
    Spotlight.Pick p = Spotlight.pick(list(item("イブキ", null, null, null), item("アウダーシア", null, null, null)), NOW);
    assertEquals(Spotlight.Kind.WAITING, p.kind);
    assertEquals("イブキ", p.item.name);
}
@Test public void startedButNoResultYet() {
    // 今日発走済みで結果待ちの馬は next も live も無い。次走のある馬が主役になる
    Item waiting = item("イブキ", null, result("イブキ", "20261011", 8, "1"), null);
    Item next = item("カムアップローゼス", entry("カムアップローゼス", "20261107", 9, "芝2000m 14:10発走"), null, null);
    assertEquals("カムアップローゼス", Spotlight.pick(list(waiting, next), NOW).item.name);
}
@Test public void upcomingWithoutStartTime() {
    // 発走時刻が無いレースは「もうすぐ」にしない
    Item noTime = item("イブキ", entry("イブキ", "20261101", 8, "芝2400m"), null, null);
    Item won = item("アウダーシア", null, null, result("アウダーシア", "20261101", 5, "1"));
    assertEquals(Spotlight.Kind.FINISHED, Spotlight.pick(list(noTime, won), NOW).kind);
}
```

- [ ] **Step 2: 失敗することを確かめる**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.tktkcompany.kakoRaceKeiba.ui.home.SpotlightTest"`
Expected: コンパイルエラー（`Spotlight` が無い）

- [ ] **Step 3: `FavoritesStrip.Item` に `live` を足し、`Spotlight.pick` を書く**

「今日」は `now.toLocalDate()` を `yyyyMMdd` にしたもの。発走時刻は `RaceStartNotices.startTime(next.getKaisaibi(), next.getRaceInfo())`（null なら「もうすぐ」ではない）。

- [ ] **Step 4: 通ることを確かめる**

Run: 同じコマンド
Expected: `SpotlightTest` の9件が PASS。続けて `.\gradlew.bat testDebugUnitTest` で全件 PASS（今の51件＋9件）

- [ ] **Step 5: コミット**

```bash
git add app/src/main/java/com/tktkcompany/kakoRaceKeiba/ui/home/Spotlight.java app/src/main/java/com/tktkcompany/kakoRaceKeiba/ui/home/FavoritesStrip.java app/src/test/java/com/tktkcompany/kakoRaceKeiba/ui/home/SpotlightTest.java
git commit -m "ホームの主役にする推し馬を選ぶ規則（Spotlight）を足す"
```

---

### Task 2: 主役のカード（`SpotlightCard`）と、横並びへの振り分け

**Files:**
- Create: `app/src/main/java/com/tktkcompany/kakoRaceKeiba/ui/home/SpotlightCard.java`
- Modify: `app/src/main/java/com/tktkcompany/kakoRaceKeiba/ui/home/FavoritesStrip.java`
- Modify: `app/src/main/res/layout/fragment_home.xml`（`spotlight_container` を足すだけ）
- Modify: `app/src/main/java/com/tktkcompany/kakoRaceKeiba/ui/home/HomeFragment.java`（`new FavoritesStrip(...)` の1行と、`refreshFavorites` で空でも `show` を呼ぶところだけ）

**Interfaces:**
- Consumes: Task 1 の `Spotlight.pick`・`Spotlight.Pick`・`Item.live`。`CardStyles.avatar/applyBackground/showEditor`、`WinShare.isWin/text/open`、`LiveResults.fetchRaceBlocking(date, course, raceNo)`、`RaceStartNotices.startTime`
- Produces:
  - `final class SpotlightCard { static View create(Context context, Spotlight.Pick pick, Runnable onStyleChanged) }`
  - `FavoritesStrip(FrameLayout spotlight, View section, LinearLayout container)`（引数を3つに変える。`section` は「ほかの推し馬」の欄全体）
  - `FavoritesStrip.show(Set<String> favorites)`：空の集合でも呼べる（主役のカードに案内を出し、`section` を隠す）

- [ ] **Step 1: `FavoritesStrip.show()` の読み込みを直す**

出走予定のうち、`startTime` が `now` より前のレースは `next` にしない。そのうち推し馬が出ていて、今日の、発走から10分以上たったレースは、レースごとに1回だけ `LiveResults.fetchRaceBlocking` で読み、その馬の `live` に入れる（`FavoriteHorsesFragment.loadLiveResults` と同じ考え方）。

- [ ] **Step 2: `render()` を振り分けに変える**

`Spotlight.pick(items, LocalDateTime.now())` で主役を決め、`spotlight` に `SpotlightCard.create(...)` を入れる。横並びには、主役の馬を除いた馬と最後の「＋ 馬を追加」を並べる。`ONBOARDING` なら `section` を `GONE`、ほかは `VISIBLE`。`onStyleChanged` は `render(shown)`。

- [ ] **Step 3: `SpotlightCard.create` を書く**

今の横並びのカードより一回り大きい `MaterialCardView`（横幅いっぱい）。`CardStyles.applyBackground(card, name, 既定色)` と `CardStyles.avatar(context, name, 56)`。押すと馬カルテ（`R.id.navigation_horse`、`HorseFragment.args(name)`）、長押しで `CardStyles.showEditor`。馬名は2行まで（`setMaxLines(2)`、末尾を「…」）。

| Kind | 一番上の小さな見出し | 中身 | 既定色 |
|---|---|---|---|
| UPCOMING | `▶ 次に走る推し馬` / `▶ Next up` | 馬名、`▶ 日付 競馬場レース番号 発走時刻`（オレンジ）、レース名（芝/ダ 距離）、前走の着順と人気 | `#FFF8E1` |
| FINISHED | `🏁 今日の結果` / `🏁 Today's result` | 馬名、着順を大きく（1着は赤で「1着！」）、日付・レース、人気。`WinShare.isWin(rank)` なら「🎉 勝利をシェア」（`WinShare.open(context, WinShare.text(...))`） | `#FFFFFF` |
| RECENT | `前回の結果` / `Last run` | 馬名、日付・レース・着順・人気、`次の出走が決まったらお知らせします` / `We'll tell you when the next entry is set` | `#FFFFFF` |
| WAITING | `★ 推し馬` / `★ Your horse` | 馬名、`出走予定を待っています` / `Waiting for an entry` | `#FFFFFF` |
| ONBOARDING | ― | `推し馬を見つけよう` / `Find a horse to follow`、ボタン `今週の重賞に出る馬から選ぶ` / `Pick from this week's graded races`（`R.id.navigation_horseSearch`） | `#E8F5E9` |

レース名と距離・着順の書き方は、今の `FavoriteHorsesAdapter.entryLabel/resultLabel` に合わせる。

- [ ] **Step 4: ホームから呼ぶ**

`fragment_home.xml` の `layout_my_horses` のすぐ上に `FrameLayout` `spotlight_container`（幅いっぱい・高さ wrap）を足す。`HomeFragment.refreshFavorites` で `new FavoritesStrip(binding.spotlightContainer, binding.layoutMyHorses, binding.myHorsesContainer)` にし、推し馬が空でも `favoritesStrip.show(favorites)` を呼ぶ（`card_onboarding` はこの時点では隠したままにする。消すのは Task 3）。

- [ ] **Step 5: ビルドを通す**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL、テストは全件 PASS

- [ ] **Step 6: コミット**

```bash
git add app/src/main/java/com/tktkcompany/kakoRaceKeiba/ui/home/ app/src/main/res/layout/fragment_home.xml
git commit -m "ホームに推し馬の主役のカードを出し、ほかの推し馬を横並びにする"
```

---

### Task 3: ホームから外す箱を消し、メインレースをいつも出す

**Files:**
- Modify: `app/src/main/res/layout/fragment_home.xml`
- Modify: `app/src/main/java/com/tktkcompany/kakoRaceKeiba/ui/home/HomeFragment.java`
- Modify: `app/src/main/res/values/strings.xml`、`app/src/main/res/values-ja/strings.xml`
- Delete: `app/src/main/java/com/tktkcompany/kakoRaceKeiba/ui/home/TodayCard.java`（`HomeFragment` からしか使っていない）

**Interfaces:**
- Consumes: Task 2 の `FavoritesStrip(FrameLayout, View, LinearLayout)`、`FavoritesStrip.show(Set<String>)`
- Produces: なし（画面の完成）

- [ ] **Step 1: レイアウトを直す**

- 消す：`card_onboarding`、`card_today`、`layout_quick_buttons`、`card_upcoming`、`card_entries`
- `layout_my_horses` の見出しの行：見出しの文字を `my_horses` から新しい `other_horses`（ja `★ ほかの推し馬` ／ en `★ Your other horses`）に。右に今の「すべて見る ›」と、新しい `text_notification_settings`（`🔔 通知の設定 ›`、`#1565C0`、13sp）を並べる
- `layout_last_results` は最初から `visible`
- 使わなくなった文字列（`my_horses`、`onboarding_title`、`onboarding_body`、`this_week_meeting`、`this_week_entries`、`today_meeting` など、ほかで使っていないもの）を両方の `strings.xml` から消す

- [ ] **Step 2: `HomeFragment` を直す**

- 消す：`todayCard`、`entriesSummaryShown`、`updateUpcomingCard`、`loadUpcomingEntries`、`courseNames`、`runnersLabel`（テストでも使っていなければ）、`btnFavoriteHorses`・`btnPredictions`・`btnOnboardingFind`・`cardEntries` のリスナー、`onDestroyView` の `todayCard.stop()`
- `refreshFavorites`：ユーザーがいなければ主役のカードも欄も隠す。いれば `favoritesStrip.show(favorites)`（空でも呼ぶ）
- `updateUpcomingHint` → `textNotificationSettings` に `showNotificationSettings` をつなぐ
- `progressHome` はメインレースを読み終わったら（失敗しても）隠す
- `joNames` の observe からは `updateUpcomingCard` の呼び出しを消し、`loadMainRaces` だけにする

- [ ] **Step 3: ビルドとテスト**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease`
Expected: BUILD SUCCESSFUL、テスト全件 PASS（release も通る＝R8 で消えた参照が無い）

- [ ] **Step 4: 消し忘れの確認**

Run: `git grep -n "TodayCard\|card_today\|card_upcoming\|card_entries\|card_onboarding\|layout_quick_buttons" -- app/src`
Expected: 何も出ない

- [ ] **Step 5: Pixel 3a（949AY0L98Y）で確かめる**

debug 版を入れて、スクリーンショットで見る：
- 今（推し馬が今週走る）：主役のカードが「▶ 次に走る推し馬」で、発走の一番早い馬。横並びに主役以外の推し馬と「＋ 馬を追加」。その下にメインレース。今週の開催・今週の出走馬・今日の開催が無い
- 長い馬名（例：カムアップローゼス）が主役のとき、2行までで崩れない
- 「🔔 通知の設定 ›」で通知の設定のダイアログが開く
- 推し馬を全部外した状態（テスト用の匿名アカウントで）：主役のカードが「推し馬を見つけよう」、ボタンで馬を探す画面が開く、横並びの欄が無い

FINISHED・RECENT は Task 1 のテストで規則を確かめ、画面は次の開催日（11/1）に実機で見る。

- [ ] **Step 6: コミット**

```bash
git add -A app/src/main/res app/src/main/java/com/tktkcompany/kakoRaceKeiba/ui/home
git commit -m "ホームから今週の開催・今週の出走馬・今日の開催を外し、メインレースをいつも出す"
```
