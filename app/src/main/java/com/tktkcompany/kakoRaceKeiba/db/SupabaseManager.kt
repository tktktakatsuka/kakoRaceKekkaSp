package com.tktkcompany.kakoRaceKeiba.db

import android.util.Log
import com.tktkcompany.kakoRaceKeiba.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object SupabaseManager {

    private const val TAG = "SupabaseManager"
    private const val BLOCKING_TIMEOUT_MS = 30_000L
    // 直近の開催日から何日前までを「開催中」とみなすか（土・日・祝月の3日間開催を1つの開催週として扱う）
    private const val ACTIVE_COURSE_DAYS = 2L
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    private var client: SupabaseClient? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @JvmStatic
    fun init() {
        if (client != null) {
            Log.d(TAG, "Supabase already initialized")
            return
        }

        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_KEY

        Log.d(TAG, "Initializing Supabase...")

        if (url.isEmpty() || url == "null") {
            Log.e(TAG, "CRITICAL: Supabase URL is empty!")
            return
        }
        if (key.isEmpty() || key == "null") {
            Log.e(TAG, "CRITICAL: Supabase Key is empty!")
            return
        }

        try {
            client = createSupabaseClient(url, key) {
                install(Postgrest)
            }
            Log.d(TAG, "Supabase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Supabase: ${e.message}", e)
        }
    }

    @Serializable
    data class FavoriteHorse(
        @SerialName("firebase_uid") val firebase_uid: String,
        @SerialName("horse_name") val horse_name: String
    )

    @Serializable
    data class RaceResult(
        @SerialName("id") val id: Long? = null,
        @SerialName("kaisaibi") val kaisaibi: String,
        @SerialName("kaisaijo") val kaisaijo: String,
        @SerialName("race_no") val raceNo: Int,
        @SerialName("race_title") val raceTitle: String? = null,
        @SerialName("rank") val rank: String? = null,
        @SerialName("waku") val waku: String? = null,
        @SerialName("horse_number") val horseNumber: String? = null,
        @SerialName("horse_name") val horseName: String? = null,
        @SerialName("age") val age: String? = null,
        @SerialName("weight") val weight: String? = null,
        @SerialName("jockey") val jockey: String? = null,
        @SerialName("popular") val popular: String? = null,
        @SerialName("win_odds") val winOdds: String? = null,
        @SerialName("time") val time: String? = null,
        @SerialName("tyakusa") val tyakusa: String? = null,
        @SerialName("tuukazyun") val tuukazyun: String? = null,
        @SerialName("nobori") val nobori: String? = null,
        @SerialName("tyoukyousi") val tyoukyousi: String? = null,
        @SerialName("horse_weight") val horseWeight: String? = null,
        @SerialName("hassou_time") val hassouTime: String? = null
    )

    @Serializable
    data class RaceTrend(
        @SerialName("id") val id: Long? = null,
        @SerialName("kaisaibi") val kaisaibi: String,
        @SerialName("kaisaijo") val kaisaijo: String,
        @SerialName("racenum") val raceNum: String? = null,
        @SerialName("racename") val raceName: String? = null,
        @SerialName("racedistance") val raceDistance: String? = null,
        @SerialName("racecondition") val raceCondition: String? = null,
        @SerialName("pace") val pace: String? = null,
        @SerialName("uma1ban") val uma1Ban: String? = null,
        @SerialName("uma1name") val uma1Name: String? = null,
        @SerialName("uma1jockey") val uma1Jockey: String? = null,
        @SerialName("uma1kyakusitu") val uma1Kyakusitu: String? = null,
        @SerialName("uma1tyokyosi") val uma1tyokyosi: String? = null,
        @SerialName("uma1father") val uma1father: String? = null,
        @SerialName("uma1mather") val uma1mather: String? = null,
        @SerialName("uma2ban") val uma2Ban: String? = null,
        @SerialName("uma2name") val uma2Name: String? = null,
        @SerialName("uma2jockey") val uma2Jockey: String? = null,
        @SerialName("uma2kyakusitu") val uma2Kyakusitu: String? = null,
        @SerialName("uma2tyokyosi") val uma2tyokyosi: String? = null,
        @SerialName("uma2father") val uma2father: String? = null,
        @SerialName("uma2mather") val uma2mather: String? = null,
        @SerialName("uma3ban") val uma3Ban: String? = null,
        @SerialName("umaname") val umaName: String? = null, // Note: This is the 3rd horse's name
        @SerialName("uma3jockey") val uma3Jockey: String? = null,
        @SerialName("uma3kyakusitu") val uma3Kyakusitu: String? = null,
        @SerialName("uma3tyokyosi") val uma3tyokyosi: String? = null,
        @SerialName("uma3father") val uma3father: String? = null,
        @SerialName("uma3mather") val uma3mather: String? = null,
        @SerialName("banusi1") val banusi1: String? = null,
        @SerialName("banusi2") val banusi2: String? = null,
        @SerialName("banusi3") val banusi3: String? = null,
        @SerialName("seisann1") val seisann1: String? = null,
        @SerialName("seisann2") val seisann2: String? = null,
        @SerialName("seisann3") val seisann3: String? = null,
        @SerialName("haraimodosiare") val haraimodosiAre: String? = null,
        @SerialName("haraimodositansyo") val haraimodosiTansyo: String? = null,
        @SerialName("haraimodosiumaren") val haraimodosiUmaren: String? = null,
        @SerialName("haraimodosi3rentan") val haraimodosi3Rentan: String? = null
    )

    @Serializable
    data class RaceEntry(
        @SerialName("id") val id: Long? = null,
        @SerialName("kaisaibi") val kaisaibi: String,
        @SerialName("kaisaijo") val kaisaijo: String,
        @SerialName("race_no") val raceNo: Int,
        @SerialName("race_title") val raceTitle: String? = null,
        @SerialName("race_info") val raceInfo: String? = null,
        @SerialName("waku") val waku: String? = null,
        @SerialName("horse_number") val horseNumber: String? = null,
        @SerialName("horse_name") val horseName: String,
        @SerialName("sex_age") val sexAge: String? = null,
        @SerialName("jockey") val jockey: String? = null,
        @SerialName("weight") val weight: String? = null,
        @SerialName("trainer") val trainer: String? = null,
        // 出馬表の取り込み・当日のオッズ更新で入る単勝オッズと人気（列が無い・未取得なら null）
        @SerialName("win_odds") val winOdds: String? = null,
        @SerialName("popular") val popular: String? = null,
        @SerialName("odds_updated_at") val oddsUpdatedAt: String? = null,
        // 開催日に PC のバッチ（live）が入れる、複勝オッズと当日の出馬表の情報（未取得なら null）
        @SerialName("place_odds_min") val placeOddsMin: String? = null,
        @SerialName("place_odds_max") val placeOddsMax: String? = null,
        @SerialName("horse_weight") val horseWeight: String? = null,
        @SerialName("scratched") val scratched: Boolean = false,
        @SerialName("card_jockey") val cardJockey: String? = null,
        @SerialName("weather") val weather: String? = null,
        @SerialName("ground") val ground: String? = null,
        @SerialName("card_updated_at") val cardUpdatedAt: String? = null
    )

    /** 払い戻し1件（PC のバッチが結果と一緒に入れる） */
    @Serializable
    data class RacePayout(
        @SerialName("kaisaibi") val kaisaibi: String,
        @SerialName("kaisaijo") val kaisaijo: String,
        @SerialName("race_no") val raceNo: Int,
        @SerialName("bet_type") val betType: String,
        @SerialName("numbers") val numbers: String,
        @SerialName("yen") val yen: Int,
        @SerialName("sort") val sort: Int = 0
    )

    @JvmStatic
    fun sanitizeKey(key: String?): String {
        if (key == null) return ""
        return key.trim().replace("[.\\$#\\[\\]/]".toRegex(), "")
    }

    @JvmStatic
    fun addFavorite(uid: String, horseName: String) {
        val currentClient = client ?: return
        val safeName = sanitizeKey(horseName)
        if (safeName.isEmpty()) return
        scope.launch {
            try {
                currentClient.from("favorite_horses").insert(FavoriteHorse(uid, safeName))
                Log.d(TAG, "Added favorite: $safeName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add favorite", e)
            }
        }
    }

    @JvmStatic
    fun removeFavorite(uid: String, horseName: String) {
        val currentClient = client ?: return
        val safeName = sanitizeKey(horseName)
        if (safeName.isEmpty()) return
        scope.launch {
            try {
                currentClient.from("favorite_horses").delete {
                    filter {
                        eq("firebase_uid", uid)
                        eq("horse_name", safeName)
                    }
                }
                Log.d(TAG, "Removed favorite: $safeName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove favorite", e)
            }
        }
    }

    /** そのユーザーのお気に入りをすべて消す（退会のとき） */
    @JvmStatic
    fun removeAllFavorites(uid: String, callback: SupabaseCallback<Boolean>) {
        launchWithCallback("remove all favorites", callback) { currentClient ->
            currentClient.from("favorite_horses").delete {
                filter { eq("firebase_uid", uid) }
            }
            true
        }
    }

    @JvmStatic
    fun fetchFavorites(uid: String, callback: SupabaseCallback<List<String>>) {
        launchWithCallback("fetch favorites", callback) { currentClient ->
            val results: List<FavoriteHorse> = currentClient.from("favorite_horses").select {
                filter { eq("firebase_uid", uid) }
            }.decodeList()
            results.map { it.horse_name }
        }
    }

    /** 指定した開催日・競馬場の全レース結果を、レース番号・着順の順で取得する */
    @JvmStatic
    fun fetchRaceResults(date: String, course: String, callback: SupabaseCallback<List<RaceResult>>) {
        launchWithCallback("fetch race results", callback) { currentClient ->
            currentClient.from("race_results").select {
                filter {
                    eq("kaisaibi", date)
                    eq("kaisaijo", course)
                }
                order("race_no", Order.ASCENDING)
                order("id", Order.ASCENDING)
            }.decodeList<RaceResult>()
        }
    }

    /** 指定した競馬場の最新のメインレース(11R)の結果を着順の順で取得する */
    @JvmStatic
    fun fetchLatestMainRace(course: String, callback: SupabaseCallback<List<RaceResult>>) {
        launchWithCallback("fetch latest main race", callback) { currentClient ->
            val rows = currentClient.from("race_results").select {
                filter {
                    eq("kaisaijo", course)
                    eq("race_no", 11)
                }
                order("kaisaibi", Order.DESCENDING)
                order("id", Order.ASCENDING)
                limit(30)
            }.decodeList<RaceResult>()
            val latestDate = rows.firstOrNull()?.kaisaibi
            rows.filter { it.kaisaibi == latestDate }
        }
    }

    /** 直近の開催週にレースがあった競馬場名を、開催日の新しい順で取得する */
    @JvmStatic
    fun fetchActiveCourses(callback: SupabaseCallback<List<String>>) {
        launchWithCallback("fetch active courses", callback) { currentClient ->
            queryActiveCourses(currentClient)
        }
    }

    /** 指定した競馬場のメインレース(11R)名を、開催日(yyyyMMdd)をキーにして取得する */
    @JvmStatic
    fun fetchMainRaceTitles(course: String, callback: SupabaseCallback<Map<String, String>>) {
        launchWithCallback("fetch main race titles", callback) { currentClient ->
            currentClient.from("race_results").select(Columns.list("kaisaibi", "kaisaijo", "race_no", "race_title")) {
                filter {
                    eq("kaisaijo", course)
                    eq("race_no", 11)
                    eq("rank", "1")
                }
                order("kaisaibi", Order.DESCENDING)
                limit(60)
            }.decodeList<RaceResult>()
                .filter { !it.raceTitle.isNullOrBlank() }
                .associate { it.kaisaibi to it.raceTitle!!.trim() }
        }
    }

    /**
     * 指定日(yyyyMMdd)以降の出走馬を、開催日・競馬場・レース番号・馬番の順で取得する。
     * race_entries テーブルがまだ無い場合は空のリストを返す（デバッグビルドでは仮の出馬表を返す）。
     */
    @JvmStatic
    fun fetchUpcomingEntries(fromDate: String, callback: SupabaseCallback<List<RaceEntry>>) {
        launchWithCallback("fetch upcoming entries", callback) { currentClient ->
            queryUpcomingEntries(currentClient, fromDate)
        }
    }

    /** 指定したレースの出走馬を馬番順で取得する */
    @JvmStatic
    fun fetchRaceEntries(date: String, course: String, raceNo: Int, callback: SupabaseCallback<List<RaceEntry>>) {
        launchWithCallback("fetch race entries", callback) { currentClient ->
            queryUpcomingEntries(currentClient, date)
                .filter { it.kaisaibi == date && it.kaisaijo == course && it.raceNo == raceNo }
        }
    }

    /**
     * fetchUpcomingEntries の同期版（通知用）。必ずバックグラウンドスレッドから呼ぶこと。
     * 実在しない出走を知らせないよう、デバッグ用の仮の出馬表は使わない。
     */
    @JvmStatic
    @Throws(Exception::class)
    fun fetchUpcomingEntriesBlocking(fromDate: String): List<RaceEntry> {
        val currentClient = client ?: throw IllegalStateException("Supabase client not initialized")
        return runBlocking {
            withTimeout(BLOCKING_TIMEOUT_MS) { queryUpcomingEntries(currentClient, fromDate, allowDemo = false) }
        }
    }

    private suspend fun queryUpcomingEntries(currentClient: SupabaseClient, fromDate: String, allowDemo: Boolean = true): List<RaceEntry> {
        val entries = try {
            currentClient.from("race_entries").select {
                filter { gte("kaisaibi", fromDate) }
                order("kaisaibi", Order.ASCENDING)
                order("kaisaijo", Order.ASCENDING)
                order("race_no", Order.ASCENDING)
                limit(1000)
            }.decodeList<RaceEntry>()
        } catch (e: Exception) {
            Log.w(TAG, "race_entries is not available yet: ${e.message}")
            emptyList()
        }
        return entries.ifEmpty { if (allowDemo) DemoEntries.build(currentClient, fromDate) else emptyList() }
            .sortedWith(compareBy({ it.kaisaibi }, { it.kaisaijo }, { it.raceNo }, { it.horseNumber?.toIntOrNull() ?: 99 }))
    }

    /** 指定した馬たちの過去成績を新しい順で取得する（1頭あたり最大 perHorse 件） */
    @JvmStatic
    fun fetchRecentResultsForHorses(horseNames: List<String>, perHorse: Int, callback: SupabaseCallback<Map<String, List<RaceResult>>>) {
        launchWithCallback("fetch recent results for horses", callback) { currentClient ->
            if (horseNames.isEmpty()) return@launchWithCallback emptyMap()
            currentClient.from("race_results").select {
                filter { isIn("horse_name", horseNames) }
                order("kaisaibi", Order.DESCENDING)
                limit((horseNames.size * perHorse * 3).toLong())
            }.decodeList<RaceResult>()
                .groupBy { it.horseName ?: "" }
                .mapValues { (_, rows) -> rows.distinctBy { it.kaisaibi to it.raceNo }.take(perHorse) }
        }
    }

    /** 指定した競馬場の開催日(yyyyMMdd)を新しい順で取得する */
    @JvmStatic
    fun fetchRaceDates(course: String, callback: SupabaseCallback<List<String>>) {
        launchWithCallback("fetch race dates", callback) { currentClient ->
            queryRaceDays(currentClient, course).map { it.kaisaibi }.distinct()
        }
    }

    @JvmStatic
    fun fetchRaceTrends(date: String, course: String, callback: SupabaseCallback<List<RaceTrend>>) {
        launchWithCallback("fetch race trends", callback) { currentClient ->
            currentClient.from("race_trends").select {
                filter {
                    eq("kaisaibi", date)
                    eq("kaisaijo", course)
                }
            }.decodeList<RaceTrend>()
        }
    }

    /** fetchActiveCourses の同期版。必ずバックグラウンドスレッドから呼ぶこと。 */
    @JvmStatic
    @Throws(Exception::class)
    fun fetchActiveCoursesBlocking(): List<String> {
        val currentClient = client ?: throw IllegalStateException("Supabase client not initialized")
        return runBlocking {
            withTimeout(BLOCKING_TIMEOUT_MS) { queryActiveCourses(currentClient) }
        }
    }

    /**
     * 指定した馬名の結果を、指定した競馬場から取得する。
     * 必ずバックグラウンドスレッドから呼ぶこと。
     */
    @JvmStatic
    @Throws(Exception::class)
    fun fetchResultsForHorsesBlocking(horseNames: Collection<String>, courses: Collection<String>?): List<RaceResult> {
        // courses が null ならすべての競馬場から取る（お気に入り馬の前走は、どこで走ったかに関係なく出したい）
        if (horseNames.isEmpty() || (courses != null && courses.isEmpty())) return emptyList()
        val currentClient = client ?: throw IllegalStateException("Supabase client not initialized")
        return runBlocking {
            withTimeout(BLOCKING_TIMEOUT_MS) {
                currentClient.from("race_results").select {
                    filter {
                        isIn("horse_name", horseNames.toList())
                        if (courses != null) isIn("kaisaijo", courses.toList())
                    }
                    order("kaisaibi", Order.DESCENDING)
                }.decodeList<RaceResult>()
            }
        }
    }

    /**
     * 1レース分の全着順（着順の表の並び）。結果がまだ入っていなければ空。
     * 開催日は PC のバッチが発走の数分後に入れる。必ずバックグラウンドスレッドから呼ぶこと
     */
    @JvmStatic
    @Throws(Exception::class)
    fun fetchRaceResultBlocking(date: String, course: String, raceNo: Int): List<RaceResult> =
        blocking { it.from("race_results").select { filter { raceFilter(date, course, raceNo) }; order("id", Order.ASCENDING) }.decodeList<RaceResult>() }

    /** 1レース分の出馬表（オッズ・当日の情報つき）。必ずバックグラウンドスレッドから呼ぶこと */
    @JvmStatic
    @Throws(Exception::class)
    fun fetchRaceEntriesBlocking(date: String, course: String, raceNo: Int): List<RaceEntry> =
        blocking { it.from("race_entries").select { filter { raceFilter(date, course, raceNo) }; order("id", Order.ASCENDING) }.decodeList<RaceEntry>() }

    /** 1レース分の払い戻し（表の並び）。確定前なら空。必ずバックグラウンドスレッドから呼ぶこと */
    @JvmStatic
    @Throws(Exception::class)
    fun fetchPayoutsBlocking(date: String, course: String, raceNo: Int): List<RacePayout> =
        blocking { it.from("race_payouts").select { filter { raceFilter(date, course, raceNo) }; order("sort", Order.ASCENDING) }.decodeList<RacePayout>() }

    /**
     * 馬名に query を含む馬の結果（新しい順、最大300行）。馬を探す画面で、馬ごとの前走を出すのに使う。
     * 必ずバックグラウンドスレッドから呼ぶこと
     */
    @JvmStatic
    @Throws(Exception::class)
    fun searchHorseResultsBlocking(query: String): List<RaceResult> = blocking {
        it.from("race_results").select(Columns.list("horse_name", "kaisaibi", "kaisaijo", "race_no", "race_title", "rank")) {
            filter { ilike("horse_name", "%${likeSafe(query)}%") }
            order("kaisaibi", Order.DESCENDING)
            limit(300)
        }.decodeList<RaceResult>()
    }

    /** 馬名に query を含む、fromDate 以降の出走予定（デビュー前の馬も見つけられるように）。必ずバックグラウンドスレッドから呼ぶこと */
    @JvmStatic
    @Throws(Exception::class)
    fun searchHorseEntriesBlocking(query: String, fromDate: String): List<RaceEntry> = blocking {
        it.from("race_entries").select(Columns.list("horse_name", "kaisaibi", "kaisaijo", "race_no", "race_title", "race_info")) {
            filter {
                ilike("horse_name", "%${likeSafe(query)}%")
                gte("kaisaibi", fromDate)
            }
            order("kaisaibi", Order.ASCENDING)
            limit(100)
        }.decodeList<RaceEntry>()
    }

    /** みんなのお気に入りの1頭（何人が登録しているか）。favorite_ranking ビューから読む */
    @Serializable
    data class FavoriteRank(
        @SerialName("horse_name") val horseName: String,
        @SerialName("fans") val fans: Int
    )

    /**
     * お気に入りに登録している人の多い馬（多い順）。ビューがまだ無ければ例外になる。
     * 必ずバックグラウンドスレッドから呼ぶこと
     */
    @JvmStatic
    @Throws(Exception::class)
    fun fetchFavoriteRankingBlocking(limit: Int): List<FavoriteRank> = blocking {
        it.from("favorite_ranking").select {
            order("fans", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<FavoriteRank>()
    }

    // ilike の「%」「_」は何にでも合ってしまうので外す
    private fun likeSafe(query: String) = query.replace("%", "").replace("_", "").trim()

    private fun io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder.raceFilter(date: String, course: String, raceNo: Int) {
        eq("kaisaibi", date)
        eq("kaisaijo", course)
        eq("race_no", raceNo)
    }

    private fun <T> blocking(block: suspend (SupabaseClient) -> T): T {
        val currentClient = client ?: throw IllegalStateException("Supabase client not initialized")
        return runBlocking { withTimeout(BLOCKING_TIMEOUT_MS) { block(currentClient) } }
    }

    // 1Rの1着の行を「その日その競馬場で開催があった」印として使う（開催日ごとに1行で済む）
    private suspend fun queryRaceDays(currentClient: SupabaseClient, course: String?): List<RaceResult> {
        return currentClient.from("race_results").select(Columns.list("kaisaibi", "kaisaijo", "race_no")) {
            filter {
                eq("race_no", 1)
                eq("rank", "1")
                if (course != null) eq("kaisaijo", course)
            }
            order("kaisaibi", Order.DESCENDING)
            order("id", Order.ASCENDING)
            limit(200)
        }.decodeList<RaceResult>()
    }

    private suspend fun queryActiveCourses(currentClient: SupabaseClient): List<String> {
        val raceDays = queryRaceDays(currentClient, null)
        val latest = raceDays.firstOrNull()?.kaisaibi ?: return emptyList()
        val since = try {
            LocalDate.parse(latest, DATE_FORMAT).minusDays(ACTIVE_COURSE_DAYS).format(DATE_FORMAT)
        } catch (e: Exception) {
            latest
        }
        return raceDays.filter { it.kaisaibi >= since }.map { it.kaisaijo }.distinct()
    }

    private fun <T> launchWithCallback(
        what: String,
        callback: SupabaseCallback<T>,
        block: suspend (SupabaseClient) -> T
    ) {
        val currentClient = client ?: return callback.onFailure("Supabase client not initialized")
        scope.launch {
            try {
                val result = block(currentClient)
                withContext(Dispatchers.Main) { callback.onSuccess(result) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to $what", e)
                withContext(Dispatchers.Main) { callback.onFailure(e.message ?: "Unknown error") }
            }
        }
    }

    interface SupabaseCallback<T> {
        fun onSuccess(result: T)
        fun onFailure(message: String)
    }
}
