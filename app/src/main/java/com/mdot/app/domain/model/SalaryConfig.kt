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
    /** 工地记工当前项目 id（12 文档 §3.3）；0 = 未初始化，首次切入自动建「首个记工项目」 */
    val siteCurrentProjectId: Long = 0,
    /** 工地记工显示单位偏好：DAY 按工天 | HOUR 按小时（仅影响展示，存储恒为分钟；F-S11 Phase 2） */
    val siteDisplayUnit: String = "DAY",
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

/**
 * 首页卡片配置（v0.6.0 首页卡片可编辑）。
 * cards = 显示中的卡片 id 按显示顺序；null = 用户未配置过（走各页内置默认布局，向后兼容旧数据）。
 * 配置页交互对齐底栏配置：点选开/关 + 拖拽排序；至少保留一张卡片。
 */
@Serializable
data class HomeCardsConfig(
    val cards: List<String>? = null,
) {
    companion object {
        /** 卡片功能池（含默认隐藏的统计卡；「记加班」主按钮为固定悬浮胶囊，不参与配置） */
        val POOL = listOf("data", "income", "entries", "heatmap", "weekbar")
        /** 出厂默认显示顺序（热点图/本周柱状默认隐藏，可在配置页开启） */
        val DEFAULT_CARDS = listOf("data", "income", "entries")
        /** 至少保留的卡片数（防整页清空无从下手） */
        const val MIN_CARDS = 1
    }
}

/**
 * 工地出勤草稿/域模型（12 文档；Room Entity 经 Repository 映射，金额为快照分）。
 * dayStatus/halfOfDay/otMode 为字符串枚举名（与 Entity 列一致，converter 手动映射）。
 */
@Serializable
data class SiteAttendance(
    val id: Long = 0,
    val projectId: Long,
    /** ISO yyyy-MM-dd */
    val date: String,
    /** SiteDayStatus 名：WORK | REST */
    val dayStatus: String,
    /** HalfOfDay 名或 null：AM | PM */
    val halfOfDay: String? = null,
    val workMinutes: Int = 0,
    val otMinutes: Int = 0,
    /** 当日日价快照（分） */
    val rateCents: Long,
    val baseMinutes: Int,
    /** SiteOtMode 名 */
    val otMode: String,
    val otBaseMinutes: Int,
    val otHourlyCents: Long,
    /** 金额快照（分） */
    val workPayCents: Long = 0,
    val otPayCents: Long = 0,
    val note: String? = null,
    /** 照片路径串（SOH 分隔；备份包不含，D10） */
    val photos: String? = null,
    val settlementId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 工地包工/工量域模型（12 文档 D4-rev：量×价自动，量价未知直填金额；quantityMilli=数量×1000） */
@Serializable
data class SitePieceWork(
    val id: Long = 0,
    val projectId: Long,
    val date: String,
    /** 工作项快照（如"砌墙"；空串=无名工量） */
    val itemName: String = "",
    /** 单位快照（m²/m³/件…） */
    val unit: String = "",
    /** 数量 ×1000；0=未填量直填金额 */
    val quantityMilli: Long = 0,
    /** 每单位单价快照（分）；0=未填价直填金额 */
    val unitPriceCents: Long = 0,
    /** 工钱（分；量价齐=量×价 HALF_UP，否则直填） */
    val amountCents: Long,
    val note: String? = null,
    val settlementId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 工地借支域模型（金额分；purpose 为 AdvancePurpose 名） */
@Serializable
data class SiteAdvance(
    val id: Long = 0,
    val projectId: Long,
    val date: String,
    val amountCents: Long,
    val purpose: String = "OTHER",
    val note: String? = null,
    val photos: String? = null,
    val settlementId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 工地项目域模型（点工标准随行） */
@Serializable
data class SiteProject(
    val id: Long = 0,
    val name: String,
    val sort: Int = 0,
    val archived: Boolean = false,
    /** 上班 X 分钟 = 1 个工 */
    val baseMinutes: Int = 480,
    /** 1 个工 = Y 元（分） */
    val dailyRateCents: Long = 0,
    /** SiteOtMode 名 */
    val otMode: String = "BY_DAY",
    /** 加班 Z 分钟 = 1 个加班工 */
    val otBaseMinutes: Int = 360,
    /** 按小时算时的加班时薪（分/h）；0=自动 日价÷上班基准 */
    val otHourlyCents: Long = 0,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
