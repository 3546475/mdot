package com.mdot.app.core.holiday

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 节日层（调研文档 §4）：没有任何机构发布权威的「中国节日清单」，故自建 `assets/festivals.json`
 * 静态规则表——几十条覆盖主流需求、几 KB、文案与优先级可随时改。
 *
 * 职责边界：
 * - 法定放假日的节日名以 [HolidayRepository] 为准（库里逐日下发，比规则表权威）；
 *   本表负责兜住**库里没有的**日子（公历纪念日、星期规则节日，以及接入 [LunarEngine] 后的
 *   农历传统节日与节气）。
 * - 同日可命中多条，[topFestivalOn] 只取 priority 最小的一条：法定/传统 10 > 节气 20
 *   > 纪念日 30 > 民间 40。日历格只有一槽，详情面板要列全部时改用 [festivalsOn]。
 * - **不负责「放不放假」**：「休/班」标记一律来自节假日库，规则表里的 `offDay` 只是数据标注，
 *   所以教师节这类「办法第五条明确不放假」的纪念日不可能被误标成休假日（调研文档 §4.2）。
 */
@Singleton
class FestivalRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 表只有几 KB（调研文档 §4.3），启动时一次性读入内存，避免首帧缺角标 */
    private val rules: List<FestivalRule> = runCatching {
        val text = context.assets.open(ASSET).use { it.readBytes().decodeToString() }
        json.decodeFromString<FestivalsFile>(text).festivals
    }.getOrDefault(emptyList())

    /** 当日命中的节日，按 priority 升序（静态表按日过滤几十条，无需索引） */
    fun festivalsOn(date: LocalDate, lunar: LunarEngine): List<FestivalRule> =
        rules.festivalsOn(date, lunar)

    /** 当日优先级最高的节日；无命中返回 null */
    fun topFestivalOn(date: LocalDate, lunar: LunarEngine): FestivalRule? =
        rules.topFestivalOn(date, lunar)

    companion object {
        const val ASSET = "festivals.json"
    }
}

/** 纯函数（不依赖 Context，可单测） */
internal fun List<FestivalRule>.festivalsOn(date: LocalDate, lunar: LunarEngine): List<FestivalRule> =
    filter { it.matches(date, lunar) }.sortedBy { it.priority }

/** 纯函数（不依赖 Context，可单测） */
internal fun List<FestivalRule>.topFestivalOn(date: LocalDate, lunar: LunarEngine): FestivalRule? =
    festivalsOn(date, lunar).firstOrNull()

/** 是否命中当日；`lunar`/`term` 类由 [LunarEngine] 提供——查表范围外的年份它返回 null，此处即不命中（不猜） */
internal fun FestivalRule.matches(date: LocalDate, lunar: LunarEngine): Boolean =
    when (kind.lowercase()) {
        "solar" -> month == date.monthValue && day == date.dayOfMonth
        "weekday" -> month == date.monthValue &&
            weekday == date.dayOfWeek.value &&
            ordinal == (date.dayOfMonth - 1) / 7 + 1
        "term" -> term != null && lunar.solarTermOf(date) == term
        "lunar" -> {
            val md = lunar.lunarMonthDayOf(date)
            md != null && lunarMonth == md.month &&
                (lunarDay == md.day || (lunarDay == LUNAR_LAST_DAY && md.isLastOfMonth))
        }
        else -> false
    }

/** `lunarDay = -1` 表示「该农历月最后一日」（除夕；连续 5 年腊月为小月，不能写死三十） */
const val LUNAR_LAST_DAY = -1

/**
 * 一条节日规则。`kind` 取值（调研文档 §4.1 的五类归并）：
 * `solar` 公历固定月日 · `weekday` 某月第 N 个星期几 · `term` 二十四节气 · `lunar` 农历月日。
 * 未知取值一律视为不命中（而非解析失败），这样将来加新类型不会把整张表拖垮。
 */
@Serializable
data class FestivalRule(
    val id: String,
    val name: String,
    val kind: String,
    val month: Int? = null,
    val day: Int? = null,
    val lunarMonth: Int? = null,
    val lunarDay: Int? = null,
    val term: String? = null,
    /** 第几个（配合 [weekday]） */
    val ordinal: Int? = null,
    /** 星期几，ISO 编号 1=周一 … 7=周日（与 `java.time.DayOfWeek.value` 一致） */
    val weekday: Int? = null,
    /** 是否法定假日（调研文档 §4.2；角标不用，详情面板用） */
    val official: Boolean = false,
    /** 是否放假（调研文档 §4.2；角标不用，详情面板用） */
    val offDay: Boolean = false,
    val priority: Int = 50,
)

@Serializable
data class FestivalsFile(
    val schemaVersion: Int = 1,
    val festivals: List<FestivalRule> = emptyList(),
)
