package com.mdot.app.feature.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.mdot.app.R
import com.mdot.app.core.holiday.CalendarDayUseCase
import com.mdot.app.core.holiday.DayCellLabel
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth

data class CalendarCell(
    val date: LocalDate?,
    val otMinutes: Int = 0,
    val leaveMinutes: Int = 0,
    val shiftName: String? = null,
    /** 左槽「休/班」+ 右槽节日/农历（组合层产出，见 CalendarDayUseCase；UI 只认这两个语义） */
    val label: DayCellLabel = DayCellLabel.EMPTY,
    /** 工地记工：当日工数×1000（非 SITE=0） */
    val worksMilli: Long = 0,
    /**
     * 工地角标（v0.7.9.0）：这天有没有留证照片 / 包工 / 借支。
     * 月历格子只有 ~40dp，写字放不下 ⇒ UI 只画角落小点（口径见 [com.mdot.app.domain.SiteDayBadges]）。
     */
    val badges: com.mdot.app.domain.SiteDayBadges = com.mdot.app.domain.SiteDayBadges(),
)

/** 月网格动画的稳定目标态：仅月+格子参与相等性，选日期不触发切换动画 */
internal data class MonthGridData(
    val month: YearMonth,
    val cells: List<CalendarCell>,
)

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val cells: List<CalendarCell> = emptyList(),
    val monthOtMinutes: Int = 0,
    val monthLeaveMinutes: Int = 0,
    val monthOtPayCents: Long? = null,
    val recordedDays: Int = 0,
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedOtMinutes: Int = 0,
    val selectedLeaveMinutes: Int = 0,
    val selectedOtPayCents: Long? = null,
    val selectedShiftName: String? = null,
    val workSystem: com.mdot.app.domain.model.WorkSystem = com.mdot.app.domain.model.WorkSystem.STANDARD,
    /** 工地记工：选中日工数×1000 / 本月工数×1000（非 SITE=0） */
    val selectedWorksMilli: Long = 0,
    val monthWorksMilli: Long = 0,
    /**
     * 本次实际展示的区间：自然月，或（开启「按考勤周期展示」时）该锚点月对应的完整考勤周期。
     * **网格与小结都按它取数**——「本月小结」的月份数字、加班/请假合计、已记录天数全在这里，
     * 所以周期视图下这些数字是「本期」而非「自然月」口径。
     */
    val period: com.mdot.app.domain.CycleCalculator.Period =
        com.mdot.app.domain.CycleCalculator.naturalMonth(YearMonth.now()),
    /** 是否按考勤周期展示（标题副行与小结文案随之变化） */
    val isCycleView: Boolean = false,
    /** 前进方向是否可用（不越今天）：自然月 = 今天所在月，周期 = 含今天的周期的锚点月 */
    val canGoNext: Boolean = false,
)

/**
 * 月历一次查询的口径：展示月（已收敛到上限内）+ 选中日 + 考勤周期设置。
 * 单独成类是为了让 uiState 的 combine 链只多一层，周期起始日/展示方式变化能整体重算
 * （起始日改了 ⇒ 网格区间与小结口径都得跟着变，不能只算日期）。
 */
internal data class CalendarQuery(
    val month: YearMonth,
    val selectedDate: LocalDate,
    val anchorDay: Int,
    val byCycle: Boolean,
)

/** 小结卡工时行标签随制度变化（综合工时=上班、小时工=工作、标准=加班） */
internal fun otNoun(workSystem: com.mdot.app.domain.model.WorkSystem): Int = when (workSystem) {
    com.mdot.app.domain.model.WorkSystem.HOURLY -> R.string.calendar_ot_hourly
    com.mdot.app.domain.model.WorkSystem.COMPREHENSIVE -> R.string.calendar_ot_comp
    com.mdot.app.domain.model.WorkSystem.STANDARD -> R.string.calendar_ot
    com.mdot.app.domain.model.WorkSystem.SITE -> R.string.site_cycle_label
}
