package com.mdot.app.core.holiday

import android.content.Context
import com.mdot.app.core.database.HolidayCacheEntity
import com.mdot.app.core.database.HolidayDao
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.network.JsonFetcher
import com.mdot.app.core.util.AppError
import com.mdot.app.core.util.AppResult
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
 * 节假日库（01 文档 F8-1 / 05 文档 §3）：
 * 内存缓存整年数据供档位判定（纯同步调用）；内置资产兜底，可从远程 JSON 刷新。
 */
@Singleton
class HolidayRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val holidayDao: HolidayDao,
    private val settings: SettingsDataSource,
    private val jsonFetcher: JsonFetcher,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cache: Map<LocalDate, HolidayInfo> = emptyMap()

    init {
        appScope.launch { loadFromDbOrBuiltin() }
    }

    private suspend fun loadFromDbOrBuiltin() {
        var rows = holidayDao.getAll()
        if (rows.isEmpty()) {
            runCatching { loadBuiltinAsset() }
            rows = holidayDao.getAll()
        }
        cache = rows.flatMap { it.toInfos() }.associateBy { it.date }
    }

    private suspend fun loadBuiltinAsset() {
        val text = context.assets.open(BUILTIN_ASSET).use { it.readBytes().decodeToString() }
        val file = json.decodeFromString<HolidaysFile>(text)
        upsertFile(file, source = SOURCE_BUILTIN)
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
        entries.mapNotNull { entry ->
            val date = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return@mapNotNull null
            HolidayInfo(date, entry.name, HolidayKind.valueOf(entry.type))
        }
    }.getOrDefault(emptyList())

    // ---- 判定接口（同步、内存缓存） ----

    fun holidayKindOf(date: LocalDate): HolidayKind? = cache[date]?.kind

    fun infoFor(date: LocalDate): HolidayInfo? = cache[date]

    /**
     * 区间内的法定放假日 / 调休补班日集合（10 文档 §4 应出勤天数计算用）。
     * 返回 (放假日, 补班日)；依赖内存缓存已预热（进程启动即异步装载）。
     */
    fun holidaySetsInRange(from: LocalDate, to: LocalDate): Pair<Set<LocalDate>, Set<LocalDate>> {
        if (from.isAfter(to)) return emptySet<LocalDate>() to emptySet()
        val holidays = HashSet<LocalDate>()
        val makeups = HashSet<LocalDate>()
        var d = from
        while (!d.isAfter(to)) {
            when (cache[d]?.kind) {
                HolidayKind.HOLIDAY -> holidays += d
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

    /** 从远程 JSON 刷新整库（失败保留现库；远端 dataVersion 与现库相同则跳过写入） */
    suspend fun refresh(): AppResult<String> {
        return try {
            val url = settings.holidayUrlFlow.first()
            val text = jsonFetcher.fetchText(url)
            val file = json.decodeFromString<HolidaysFile>(text)
            if (file.years.isEmpty()) {
                Failure(AppError.Storage("远程节假日数据为空"))
            } else {
                val current = holidayDao.observeDataVersion().first()
                if (file.dataVersion.isNotBlank() && file.dataVersion == current) {
                    // 远端未更新：跳过 upsert（零写入、零 UI 抖动）
                    Success(file.dataVersion)
                } else {
                    upsertFile(file, SOURCE_REMOTE)
                    Success(file.dataVersion)
                }
            }
        } catch (e: Exception) {
            Failure(AppError.Storage(e.message ?: "刷新失败"))
        }
    }

    companion object {
        const val BUILTIN_ASSET = "holidays.json"
        const val SOURCE_BUILTIN = "BUILTIN"
        const val SOURCE_REMOTE = "REMOTE"

        /** 自动刷新成功间隔：7 天（节假日为年度数据，7 天极保守） */
        const val HOLIDAY_REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
        /** 失败后重试间隔：1 天 */
        const val HOLIDAY_RETRY_INTERVAL_MS = 1L * 24 * 60 * 60 * 1000
    }
}

/** 节假日库是否到刷新时机（纯函数，可单测）：距上次成功 ≥7 天；last=0 表示从未拉过（立即） */
fun holidayRefreshDue(lastFetchAt: Long, now: Long): Boolean =
    now - lastFetchAt >= HolidayRepository.HOLIDAY_REFRESH_INTERVAL_MS

@Serializable
data class HolidaysFile(
    val schemaVersion: Int = 1,
    val dataVersion: String = "",
    val years: Map<String, List<HolidayEntry>> = emptyMap(),
)

@Serializable
data class HolidayEntry(
    val date: String,
    val name: String,
    val type: String,
)
