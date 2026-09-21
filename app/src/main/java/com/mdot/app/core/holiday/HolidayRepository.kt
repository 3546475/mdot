package com.mdot.app.core.holiday

import android.content.Context
import com.mdot.app.core.database.HolidayCacheEntity
import com.mdot.app.core.database.HolidayDao
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.network.JsonFetcher
import com.mdot.app.core.util.AppError
import com.mdot.app.core.util.AppResult
import com.mdot.app.core.util.rethrowIfCancellation
import com.mdot.app.core.util.AppResult.Failure
import com.mdot.app.core.util.AppResult.Success
import com.mdot.app.domain.TierResolver
import com.mdot.app.domain.model.HolidayInfo
import com.mdot.app.domain.model.HolidayKind
import com.mdot.app.domain.model.RateTier
import com.mdot.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 节假日库（01 文档 F8-1 / 05 文档 §3 / 调研文档《节假日与农历数据源调研》§5）：
 * 内存缓存整年数据供档位判定（纯同步调用）；内置资产兜底，可从远程 JSON 刷新。
 *
 * 只承载**算不出来的**那一件事——「国务院今年怎么调休」：农历/节气/节日名称一律不进这里
 * （农历属本地算法层，见 [LunarEngine]；节日名称属静态规则表，见 [FestivalRepository]）。
 *
 * 契约兼容 v1 与 v2（见 [HolidayEntry]）；v2 新增 `statutory` 用于区分
 * 「法定节假日」与「调休拼出来的休息日」（调研文档 §2.4 坑三）。
 */
@Singleton
class HolidayRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val holidayDao: HolidayDao,
    private val settings: SettingsDataSource,
    private val jsonFetcher: JsonFetcher,
    private val cleartextGate: com.mdot.app.core.network.CleartextGate,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cache: Map<LocalDate, HolidayInfo> = emptyMap()

    init {
        appScope.launch { loadFromBuiltinOrDb() }
    }

    /**
     * 装载现库；**内置资产版本更新时以内置为准**。
     *
     * 内置资产随 APK 走，用户刚更新 APK 时它通常比库里的旧数据新（云端文件要等下次发版才同步），
     * 若一味「库非空就用库」，升级后仍会用旧节假日数据——补班日缺失会直接影响档位判定与「休/班」角标。
     */
    private suspend fun loadFromBuiltinOrDb() {
        val builtin = runCatching { readBuiltinFile() }.getOrNull()
        var rows = holidayDao.getAll()
        val current = holidayDao.observeDataVersion().first().orEmpty()
        if (builtin != null && (rows.isEmpty() || isHolidayDataNewer(builtin.dataVersion, current))) {
            upsertFile(builtin, source = SOURCE_BUILTIN)
            rows = holidayDao.getAll()
        }
        cache = rows.flatMap { it.toInfos() }.associateBy { it.date }
    }

    private suspend fun readBuiltinFile(): HolidaysFile {
        val text = context.assets.open(BUILTIN_ASSET).use { it.readBytes().decodeToString() }
        return json.decodeFromString<HolidaysFile>(text)
    }

    private suspend fun upsertFile(file: HolidaysFile, source: String) {
        val now = System.currentTimeMillis()
        val entities = file.years.mapNotNull { (yearText, entries) ->
            val year = yearText.toIntOrNull() ?: return@mapNotNull null
            HolidayCacheEntity(
                year = year,
                json = json.encodeToString(entries),
                dataVersion = file.dataVersion,
                source = source,
                fetchedAt = now,
            )
        }
        if (entities.isEmpty()) return
        holidayDao.upsertAll(entities)
        cache = entities.flatMap { it.toInfos() }
            .fold(cache) { acc, info -> acc + (info.date to info) }
    }

    private fun HolidayCacheEntity.toInfos(): List<HolidayInfo> = runCatching {
        val parser = this@HolidayRepository.json
        val entries = parser.decodeFromString<List<HolidayEntry>>(this.json)
        entries.mapNotNull { it.toHolidayInfo() }
    }.getOrDefault(emptyList())

    // ---- 判定接口（同步、内存缓存） ----

    fun holidayKindOf(date: LocalDate): HolidayKind? = cache[date]?.kind

    fun infoFor(date: LocalDate): HolidayInfo? = cache[date]

    /**
     * 区间内的放假日 / 调休补班日集合（10 文档 §4 应出勤天数计算用）。
     * 放假日 = 法定节假日 + 调休休息日（不含普通周末——那把由工作日设定处理，重复扣除会把
     * 落在周末的节假日在应出勤里扣两次）；返回 (放假日, 补班日)。
     * 依赖内存缓存已预热（进程启动即异步装载）。
     */
    fun holidaySetsInRange(from: LocalDate, to: LocalDate): Pair<Set<LocalDate>, Set<LocalDate>> {
        if (from.isAfter(to)) return emptySet<LocalDate>() to emptySet()
        val holidays = HashSet<LocalDate>()
        val makeups = HashSet<LocalDate>()
        var d = from
        while (!d.isAfter(to)) {
            when (cache[d]?.kind) {
                HolidayKind.STATUTORY, HolidayKind.REST -> holidays += d
                HolidayKind.WORKDAY -> makeups += d
                null -> {}
            }
            d = d.plusDays(1)
        }
        return holidays to makeups
    }

    fun tierResolver(workdays: Set<DayOfWeek>): TierResolver =
        TierResolver({ holidayKindOf(it) }, workdays)

    fun tierFor(date: LocalDate, workdays: Set<DayOfWeek>): RateTier =
        tierResolver(workdays).tierFor(date)

    val dataVersionFlow: Flow<String?> = holidayDao.observeDataVersion()

    /**
     * 启动时自动刷新（节流）：距上次成功 ≥7 天才真正拉取；失败按 1 天短节流回退
     * （成功→记 now；失败→记 now−6 天，使下次 1 天后到期）。全程静默，内置库/现缓存兜底。
     */
    suspend fun refreshIfStale(now: Long = System.currentTimeMillis()) {
        val last = settings.holidayLastFetchAtFlow.first()
        if (!holidayRefreshDue(last, now)) return
        val result = refresh()
        val persisted = when (result) {
            is Success -> now
            // 失败：回退 6 天 → 1 天后可重试，避免每次冷启动都打失败请求
            is Failure -> now - (HOLIDAY_REFRESH_INTERVAL_MS - HOLIDAY_RETRY_INTERVAL_MS)
        }
        settings.setHolidayLastFetchAt(persisted)
    }

    /**
     * 从远程 JSON 刷新整库（失败保留现库）。跳过写入的两种情况：
     * 1. 契约版本高于本端认识的上限（拒绝吃不下的格式，避免静默丢字段）；
     * 2. 远端版本不新于现库——**远端滞后时不能覆盖**：内置资产随 APK 走，通常比尚未重发的
     *    远端文件新，若无条件覆盖会把新数据打回旧的。
     */
    suspend fun refresh(): AppResult<String> {
        return try {
            val url = settings.holidayUrlFlow.first()
            cleartextGate.allowUrl(url) // 用户配置的节假日源允许明文（13 文档 B2-04）
            val text = jsonFetcher.fetchText(url)
            val file = json.decodeFromString<HolidaysFile>(text)
            when {
                file.years.isEmpty() -> Failure(AppError.Storage("远程节假日数据为空"))
                file.schemaVersion > SUPPORTED_SCHEMA_VERSION ->
                    Failure(AppError.Storage("远程节假日数据版本过新（schema ${file.schemaVersion}）"))
                else -> {
                    val current = holidayDao.observeDataVersion().first()
                    if (isHolidayDataNewer(file.dataVersion, current.orEmpty())) {
                        upsertFile(file, SOURCE_REMOTE)
                    }
                    // 未更新：跳过 upsert（零写入、零 UI 抖动）
                    Success(file.dataVersion)
                }
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Failure(AppError.Storage(e.message ?: "刷新失败"))
        }
    }

    companion object {
        const val BUILTIN_ASSET = "holidays.json"
        const val SOURCE_BUILTIN = "BUILTIN"
        const val SOURCE_REMOTE = "REMOTE"

        /** 本端能解析的最高契约版本（v2 = 加 type/label/statutory，见调研文档 §5） */
        const val SUPPORTED_SCHEMA_VERSION = 2

        /** 自动刷新成功间隔：7 天（节假日为年度数据，7 天极保守） */
        const val HOLIDAY_REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
        /** 失败后重试间隔：1 天 */
        const val HOLIDAY_RETRY_INTERVAL_MS = 1L * 24 * 60 * 60 * 1000
    }
}

/** 节假日库是否到刷新时机（纯函数，可单测）：距上次成功 ≥7 天；last=0 表示从未拉过（立即） */
fun holidayRefreshDue(lastFetchAt: Long, now: Long): Boolean =
    now - lastFetchAt >= HolidayRepository.HOLIDAY_REFRESH_INTERVAL_MS

/** dataVersion 里的数字段（`official-2026-02` → [2026, 2]）；无数字返回 null */
private val DATA_VERSION_NUMBERS = Regex("\\d+")

private fun holidayRevisionOf(dataVersion: String): List<Int>? =
    DATA_VERSION_NUMBERS.findAll(dataVersion).map { it.value.toInt() }.toList().ifEmpty { null }

/**
 * 候选数据是否比现库新（纯函数，可单测）：数字段逐个比较（`official-2026-01` < `official-2026-02`）；
 * 任一侧解析不出数字段时沿用「不同即更新」的老行为，保证自定义源仍可正常工作。
 * 远端刷新与「内置资产是否覆盖现库」两处共用。
 */
fun isHolidayDataNewer(candidate: String, current: String): Boolean {
    if (candidate.isBlank()) return false
    if (current.isBlank()) return true
    if (candidate == current) return false
    val r = holidayRevisionOf(candidate) ?: return true
    val c = holidayRevisionOf(current) ?: return true
    for (i in 0 until maxOf(r.size, c.size)) {
        val a = r.getOrElse(i) { 0 }
        val b = c.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return true // 数字段相同但字符串不同：视为更新（保守）
}

@Serializable
data class HolidaysFile(
    val schemaVersion: Int = 1,
    val dataVersion: String = "",
    /** 数据生成时间（ISO 8601；v2 起下发，供排查「线上数据是哪一年的」） */
    val generatedAt: String = "",
    val years: Map<String, List<HolidayEntry>> = emptyMap(),
)

/**
 * 节假日条目，兼容两版契约：
 *
 * | 字段 | v1 | v2 |
 * |---|---|---|
 * | `type` | `HOLIDAY`（放假）/ `WORKDAY`（补班） | `legal` / `transfer_holiday` / `weekend` / `workday_makeup` / `workday` |
 * | 节日名 | `name` | `holiday` |
 * | 展示文案 | — | `label`（如「调休上班」） |
 * | 是否法定 | — | `statutory` |
 *
 * 两版的 `type` 大小写不重合（v1 全大写，v2 全小写），故不需要带 schemaVersion 就能分派。
 * v1 无法区分「法定」与「调休连休」，统一按「放假 = 法定」解释——与改造前行为保持一致。
 */
@Serializable
data class HolidayEntry(
    val date: String,
    val type: String,
    val name: String? = null,
    val holiday: String? = null,
    val label: String? = null,
    val statutory: Boolean = false,
)

/** 补班日无节日名时的兜底文案（v1 数据即写死「补班」） */
internal const val MAKEUP_LABEL = "补班"

/**
 * 条目 → 日历语义；null = 该类型不入库（普通工作日/周末由「工作日设定」兜底，
 * 进库只会让档位判定多绕一层）。
 *
 * 补班日的 [HolidayInfo.name] 取 `label`（「调休上班」）而非被调的节日名：
 * 节日名放 [HolidayInfo.makeupFor]，防止被当成节日角标渲染（调研文档 §2.4 坑一）。
 */
internal fun HolidayEntry.toHolidayInfo(): HolidayInfo? {
    val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    return when (type) {
        "HOLIDAY" -> HolidayInfo(parsed, name.orEmpty(), HolidayKind.STATUTORY)   // v1 放假
        "WORKDAY" -> HolidayInfo(parsed, name ?: MAKEUP_LABEL, HolidayKind.WORKDAY) // v1 补班
        else -> when (type.lowercase()) {
            "legal" -> HolidayInfo(
                parsed,
                holiday.orEmpty(),
                if (statutory) HolidayKind.STATUTORY else HolidayKind.REST,
            )
            "transfer_holiday" -> HolidayInfo(parsed, holiday.orEmpty(), HolidayKind.REST)
            "workday_makeup" -> HolidayInfo(
                parsed,
                label ?: MAKEUP_LABEL,
                HolidayKind.WORKDAY,
                makeupFor = holiday,
            )
            // "weekend"（普通周末）与 "workday"（普通工作日）不入库：
            // 两者都由「工作日设定」兜底，进库只会让档位判定多绕一层
            // （连休里的周末属于连休，下发时应标 transfer_holiday）
            else -> null
        }
    }
}
