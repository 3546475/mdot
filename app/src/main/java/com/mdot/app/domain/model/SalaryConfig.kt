package com.mdot.app.domain.model

import kotlinx.serialization.Serializable

/**
 * 工资设定（存 DataStore，随备份包同步）。
 * 金额一律以「分」存储，避免浮点误差。
 */
@Serializable
data class SalaryConfig(
    val mode: SalaryMode = SalaryMode.BASE,
    /** 月薪底薪（分）；0 = 未设置 */
    val baseSalaryCents: Long = 0,
    /** 手动单价模式下是否计入底薪 */
    val includeBase: Boolean = true,
    /** 三档倍率（0.1–10） */
    val multipliers: Map<RateTier, Double> = mapOf(
        RateTier.WEEKDAY to 1.5,
        RateTier.WEEKEND to 2.0,
        RateTier.STATUTORY to 3.0,
    ),
    /** 手动模式三档每小时单价（分/小时） */
    val manualRatesCents: Map<RateTier, Long> = mapOf(
        RateTier.WEEKDAY to 0,
        RateTier.WEEKEND to 0,
        RateTier.STATUTORY to 0,
    ),
    /** 六类请假扣薪系数（0–1） */
    val leaveCoefficients: Map<LeaveType, Double> = mapOf(
        LeaveType.PERSONAL to 1.0,
        LeaveType.SICK to 0.5,
        LeaveType.ANNUAL to 0.0,
        LeaveType.COMP to 0.0,
        LeaveType.ABSENT to 1.0,
        LeaveType.OTHER to 0.0,
    ),
    /** 工时制度（08 文档）：STANDARD=标准工时底薪制；HOURLY=小时工纯时薪制 */
    val workSystem: WorkSystem = WorkSystem.STANDARD,
    /** 小时工三档时薪（分/小时），仅 workSystem=HOURLY 时生效 */
    val hourlyRatesCents: Map<RateTier, Long> = mapOf(
        RateTier.WEEKDAY to 0L,
        RateTier.WEEKEND to 0L,
        RateTier.STATUTORY to 0L,
    ),
    /**
     * 综合工时周期标准工时（分钟），仅 workSystem=COMPREHENSIVE 时生效（10 文档 §3.2）。
     * 0 = 自动（应出勤天数 × 8h，由调用方经 PayrollCalculator.Input.standardMinutes 注入）；
     * >0 = 用户手填覆盖（审批文件口径）。
     */
    val comprehensiveStandardMinutes: Int = 0,
) {
    val hasBaseSalary: Boolean get() = baseSalaryCents > 0
}

/** 外观设置 */
@Serializable
data class AppearanceConfig(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val paletteId: String = PALETTE_CLASSIC_BLUE,
    /** Android 12+ 动态取色开关 */
    val dynamicColor: Boolean = true,
) {
    companion object {
        const val PALETTE_CLASSIC_BLUE = "classic_blue"
        const val PALETTE_TEAL = "teal"
        const val PALETTE_WARM_ORANGE = "warm_orange"
        const val PALETTE_VIOLET = "violet"
    }
}

/** 底栏配置：槽位从功能池选择，首页固定不可移（记录入口统一在首页大按钮，不占底栏） */
@Serializable
data class BottomBarConfig(
    val slots: List<String> = DEFAULT_SLOTS,
) {
    companion object {
        /** 功能池（设置、工资固定为二级页面，不入底栏） */
        val POOL = listOf("home", "calendar", "stats", "export", "sync", "profile")
        /** 简洁方案（出厂默认） */
        val DEFAULT_SLOTS = listOf("home", "profile")
        /** 高效方案 */
        val EFFICIENT_SLOTS = listOf("home", "calendar", "stats", "profile")
        const val MAX_SLOTS = 4
    }
}
