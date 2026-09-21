package com.mdot.app.core.holiday

import com.mdot.app.domain.model.HolidayInfo
import com.mdot.app.domain.model.HolidayKind
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** 日历格左侧的「休息 / 调班」标记（用户定义：休 = 请假红，班 = 加班蓝） */
enum class RestBadge { REST, MAKEUP }

/** 右槽文案上限：节日名与农历都只显示两个字（用户规则，2026-09-20） */
const val DAY_LABEL_MAX_CHARS = 2

/**
 * 一个日期在日历格上要显示的两处信息。
 * [badge] 为空 = 该日既非休息日也非补班日；[text] 为空 = 右槽留白。
 */
data class DayCellLabel(
    val badge: RestBadge? = null,
    val text: String? = null,
) {
    companion object {
        val EMPTY = DayCellLabel()
    }
}

/**
 * 日历格组合层（调研文档 §6 的 `CalendarDayUseCase`）：把四个独立图层压成格子上那一行信息。
 *
 * ```
 * 法定状态层 HolidayRepository   → 左槽「休 / 班」
 * 节日层     FestivalRepository  → 右槽节日名
 * 农历层     LunarEngine         → 右槽农历文案
 * 节气层     LunarEngine         → 同上（节气并入节日规则表匹配）
 * ```
 *
 * 右槽只有一格，故按「有节日就不显示农历」定序（用户规则；与调研文档 §1 的
 * 「法定节日 > 传统节日 > 节气 > 纪念日」一致）；连休段内节日名只标首日（用户 2026-09-20 规格），
 * 中段同样让位给农历，于是「休 + 廿六」这类格子也能给出农历信息。
 */
@Singleton
class CalendarDayUseCase @Inject constructor(
    private val holidayRepo: HolidayRepository,
    private val festivalRepo: FestivalRepository,
    private val lunarEngine: LunarEngine,
) {

    /**
     * @param showLunar 是否允许右槽回落显示农历（外观页「隐藏农历日期」开关，默认显示）。
     *   关闭时不查农历引擎，右槽在无节日名时留白——注意节日匹配仍走农历，不受此开关影响。
     */
    fun labelFor(date: LocalDate, showLunar: Boolean = true): DayCellLabel = composeDayCellLabel(
        info = holidayRepo.infoFor(date),
        festivalRule = festivalRepo.topFestivalOn(date, lunarEngine),
        lunarText = if (showLunar) lunarEngine.lunarDayText(date) else null,
        prevInfo = holidayRepo.infoFor(date.minusDays(1)),
    )
}

/**
 * 纯函数（不依赖 Context/仓库，可单测）：把三层数据压成格子上的一行。
 *
 * - 左槽：法定 / 调休休息日 → 休；补班日 → 班；其余无标记。
 * - 右槽：节日优先于农历；两者都截到 [DAY_LABEL_MAX_CHARS] 字。
 * - 补班日不算节日——节假日库里的 `holiday` 是**被调的**那个节日，直接渲染会出现
 *   「2/14 显示春节却标注上班」的自相矛盾（调研文档 §2.4 坑一），故补班日只看规则表。
 * - 连休段（同一节日连续若干天）**只在首日标节日名**，中段让位给农历：否则「中秋」会在一格里
 *   连标三天，既不增量信息又挤掉农历。
 * - **例外：规则表点出了「另一个」节日就照标**——除夕就是靠这条活下来的。
 *   2/16 在春节连休段里，节假日库给的名字是「春节」（与段首重复），但静态规则表按
 *   「腊月最后一天」命中了「除夕」，两者不同名 → 保留除夕。而 2/17 正月初一命中规则表的
 *   「春节」，与库里的段首同名 → 仍算重复，让位农历「初一」。
 */
internal fun composeDayCellLabel(
    info: HolidayInfo?,
    festivalRule: FestivalRule?,
    lunarText: String?,
    prevInfo: HolidayInfo? = null,
): DayCellLabel {
    val badge = when (info?.kind) {
        HolidayKind.STATUTORY, HolidayKind.REST -> RestBadge.REST
        HolidayKind.WORKDAY -> RestBadge.MAKEUP
        null -> null
    }
    // 放假日（法定 / 调休休息日）；补班日与平常日都归 else
    val offDay = info != null && info.kind != HolidayKind.WORKDAY
    // 与前一天同属一个节日 = 连休中段。补班日不参与连线（它不放假）
    val isSegmentLead = when {
        !offDay -> true
        prevInfo == null || prevInfo.kind == HolidayKind.WORKDAY -> true
        else -> prevInfo.name != info?.name
    }
    val festivalName = when {
        offDay && !isSegmentLead -> festivalRule?.name?.takeIf { it != info?.name }
        offDay -> info?.name?.takeIf { it.isNotBlank() }
        else -> festivalRule?.name
    }
    val festival = festivalName?.take(DAY_LABEL_MAX_CHARS)
    val lunar = if (festival == null) lunarText?.take(DAY_LABEL_MAX_CHARS) else null
    return DayCellLabel(
        badge = badge,
        text = festival ?: lunar,
    )
}
