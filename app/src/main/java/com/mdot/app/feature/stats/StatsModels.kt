package com.mdot.app.feature.stats

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.SegmentBar
import com.mdot.app.domain.SitePayCalculator
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.StatsRangeKind
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.AdvancePurpose
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.util.TimeUtils
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate

enum class StatsDimension(val labelRes: Int) {
    CYCLE(R.string.stats_dim_cycle), MONTH(R.string.stats_dim_month), YEAR(R.string.stats_dim_year), CUSTOM(R.string.stats_dim_custom),
    /** 工地记工维度（21 文档 B2）：工地以项目为核心，无固定周期——本期待结 / 项目全周期 */
    SITE_PENDING(R.string.stats_dim_site_pending), SITE_SPAN(R.string.stats_dim_site_span),
}

/**
 * 界面维度 → domain 的区间粒度。工地两个维度没有对应粒度（它走 `SiteRanges` 项目口径），
 * 这里回落到 CYCLE 只是取出一个**不会被使用**的值——工地分支在 `flatMapLatest` 里先分叉，
 * 从不读这个 range。别把 SITE_* 当成真的走考勤周期。
 */
val StatsDimension.rangeKind: StatsRangeKind
    get() = when (this) {
        StatsDimension.CYCLE -> StatsRangeKind.CYCLE
        StatsDimension.MONTH -> StatsRangeKind.MONTH
        StatsDimension.YEAR -> StatsRangeKind.YEAR
        StatsDimension.CUSTOM -> StatsRangeKind.CUSTOM
        StatsDimension.SITE_PENDING, StatsDimension.SITE_SPAN -> StatsRangeKind.CYCLE
    }

/**
 * 区间胶囊**下方 Spacer** 的取值——统计/记月/明细三页共用同一个常量。
 *
 * 为什么不用 Spacing 令牌：上方的留白不是页面自己定的，而是宿主把页签条
 * `SegmentBar`（42dp）塞进 `CenterAlignedTopAppBar`（固定 64dp）后多出来的余量（实测 64px），
 * 三页完全一致、也不由这三页控制；令牌表里没有对应值，硬凑会漂。
 * 所以显式记常量并三页共用：改一次三页同步。
 *
 * 取值依据：用户要求把胶囊上下留白收到上一版（约 86px）的 1/3，故取 10dp(≈28px)。
 * 页面顶部已不再加 Spacer（页签条自带余量），上方因此固定在 64px。
 * ⚠️ 比"距"时注意下方**总距** = 本 Spacer + 卡片自身上内边距(约 12dp)，
 * 二者不同源，别把卡片内边距算进胶囊留白。
 */
val StatsRangeSpacing: androidx.compose.ui.unit.Dp
    get() = 10.dp

/** 非工地：自定义起止快照（两流合成一路——`combine` 的定长重载上限是 5 流） */
internal data class StatsCustomRange(val from: LocalDate?, val to: LocalDate?)

/** 非工地：区间推导的输入快照（combine 五流解包，避免再嵌一层 Triple） */
internal data class StatsRangeParams(
    val dim: StatsDimension,
    val range: CycleCalculator.Period?,
    val salaryWorkdays: Pair<com.mdot.app.domain.model.SalaryConfig, Set<java.time.DayOfWeek>>,
    val offset: Int,
)

enum class PieMode(val labelRes: Int) { SHIFT(R.string.stats_pie_shift), LEAVE(R.string.stats_pie_leave) }

/** 本周柱状单日复用共享组件（value=强度：非工地=加班分钟，工地=工数） */
typealias WeekBar = com.mdot.app.core.designsystem.component.WeekBar

/** 饼图分片：value 随模式取值（非工地=分钟，工地=工数），展示层负责格式化 */
data class PieSlice(
    val label: String,
    val value: Float,
    /** 稳定色索引（工地项目饼图用 projectId，保证同一项目跨页面同色；null=按序取色） */
    val colorIndex: Int? = null,
)

/** 工地明细行（与汇总同口径：当前项目） */
data class SiteDetailRow(
    val date: LocalDate,
    val kind: SiteDetailKind,
    /** 源记录 id（出勤/包工/借支/结算单各表内自增）——与 kind 组合成列表稳定 key。
     *  同一天同用途可有多笔（如两笔借支），故不能用 date+用途做 key（重复 → LazyColumn 崩溃）。 */
    val id: Long = 0,
    /** 出勤工数×1000 */
    val worksMilli: Long = 0,
    val otMinutes: Int = 0,
    val amountCents: Long = 0,
    val itemName: String = "",
    val purpose: AdvancePurpose? = null,
    /** 留证照片（v0.7.9.0）：明细行上显示张数，点开看大图——照片以前只在编辑弹层里能看到 */
    val photos: List<String> = emptyList(),
) {
    /** LazyColumn 稳定 key：跨表自增 id 会撞，必须带 kind 段前缀（同 11 文档 005 教训） */
    val stableKey: String get() = "${kind}_$id"
}

enum class SiteDetailKind {
    /** 出工（含加班） */
    WORK,
    /** 显式休息 */
    REST,
    /** 包工/工量 */
    PIECE,
    /** 借支 */
    ADVANCE,
    /** 部分结算（本次结算金额，从待结余额拿走一笔） */
    PARTIAL,
}

data class StatsUiState(
    val dimension: StatsDimension = StatsDimension.CYCLE,
    val rangeLabel: String = "",
    /** 非工地：区间选择弹窗所需的现状快照（当前粒度 + 当前区间）；工地为 null */
    val picker: StatsRangePicker? = null,
    /** 相对当前区间的**步进次数**（0=当前区间，-1=前一条）；自定义时按区间天数整段平移 */
    val rangeOffset: Int = 0,
    /** 「往前还可以走几步」：0 ⇒ 前进箭头置灰（已贴到今天） */
    val maxForwardSteps: Int = 0,
    val showMoney: Boolean = false,
    val workSystem: WorkSystem = WorkSystem.STANDARD,
    /** 汇总（非工地，PayrollCalculator） */
    val output: PayrollCalculator.Output? = null,
    /** 汇总（工地，SitePayCalculator） */
    val siteSummary: SitePayCalculator.Output? = null,
    /** 柱状图每日值（**所选区间内**，工地=工数/其他=加班分钟；与热点图窗口解耦，21 文档 B3） */
    val barValues: Map<LocalDate, Float> = emptyMap(),
    /** 统一热点图（所有模式）：非工地=加班小时，工地=工数 */
    val heatValues: Map<LocalDate, Float> = emptyMap(),
    /** 热点图铺格终止日（固定今天，与所选区间无关） */
    val heatEnd: LocalDate = LocalDate.now(),
    /** 热点图铺格起始日（本月月初向前推五个月的月初，共六个月） */
    val heatStart: LocalDate? = null,
    /** 区间起止日（月柱状图铺柱用，不截断到今天） */
    val rangeFrom: LocalDate? = null,
    val rangeTo: LocalDate? = null,
    /** 统一柱状：本周周一~周日 7 柱，空日/未来日占位 */
    val weekBars: List<WeekBar> = emptyList(),
    /** 饼图（非工地）：班次分布 / 请假类型 */
    val pieShift: List<PieSlice> = emptyList(),
    val pieLeave: List<PieSlice> = emptyList(),
    /** 饼图（工地）：项目工数（跨项目聚合） */
    val pieSiteProjects: List<PieSlice> = emptyList(),
    /** 工地明细（当前项目，日期倒序） */
    val siteDetails: List<SiteDetailRow> = emptyList(),
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
)

/**
 * 统计页区间胶囊的文案：与明细页不同，这里**要带维度前缀**（"周期 …"/"自然月 …"/"年 …"）。
 * 原先那行常驻 chip 承担的正是「现在是什么口径」的提示，收进弹窗后必须由文案补回来
 * —— 这是方案 C 的固有代价，别为了好看把前缀去掉。
 */
@Composable
internal fun statsRangeLabel(kind: StatsRangeKind, period: CycleCalculator.Period?): String {
    if (period == null) return ""
    val prefix = stringResource(
        when (kind) {
            StatsRangeKind.CYCLE -> R.string.stats_dim_cycle
            StatsRangeKind.MONTH -> R.string.stats_dim_month
            StatsRangeKind.YEAR -> R.string.stats_dim_year
            StatsRangeKind.CUSTOM -> R.string.stats_dim_custom
        }
    )
    return if (kind == StatsRangeKind.YEAR) {
        "$prefix ${period.from.year}"
    } else {
        "$prefix ${TimeUtils.mdCn(period.from)} – ${TimeUtils.mdCn(period.to)}"
    }
}

/** 「回去」胶囊文案：按粒度说人话（周期叫「回本期」、年叫「回今年」、月才叫「回本月」） */
@Composable
internal fun statsBackToCurrentLabel(kind: StatsRangeKind): String = when (kind) {
    StatsRangeKind.MONTH -> stringResource(R.string.month_stepper_back_to_current)
    StatsRangeKind.YEAR -> stringResource(R.string.ds_back_this_year)
    else -> stringResource(R.string.stats_range_back_to_current)
}

/** 步进箭头的无障碍描述：按维度说人话（读屏用户听到「上一期」才知道一步走多远） */
@Composable
internal fun statsStepDescription(forward: Boolean, kind: StatsRangeKind): String = stringResource(
    when (kind) {
        StatsRangeKind.CYCLE ->
            if (forward) R.string.month_stepper_next else R.string.month_stepper_prev
        StatsRangeKind.MONTH ->
            if (forward) R.string.stats_step_next_month else R.string.stats_step_prev_month
        StatsRangeKind.YEAR ->
            if (forward) R.string.stats_step_next_year else R.string.stats_step_prev_year
        StatsRangeKind.CUSTOM ->
            if (forward) R.string.stats_step_next_span else R.string.stats_step_prev_span
    }
)
