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
    /**
     * 社保个人比例（**基点**，1 基点 = 0.01%；0 = 不自动算）。
     * 例：1050 = 10.5%（养老 8% + 医疗 2% + 失业 0.5%，多数城市）。
     */
    val socialInsuranceRateBp: Int = 0,
    /** 社保缴费基数（分）；0 = **跟随底薪**（自动，见 [PayrollCalculator.socialInsuranceCents]） */
    val socialInsuranceBaseCents: Long = 0,
    /** 公积金个人比例（基点；0 = 不自动算）；法定区间 5%–12% */
    val housingFundRateBp: Int = 0,
    /** 公积金缴费基数（分）；0 = 跟随底薪 */
    val housingFundBaseCents: Long = 0,
    /** 个税专项附加扣除：**月合计**（分）。由个税页的勾选项相加，也允许直接改 */
    val taxAdditionalDeductionCents: Long = 0,
    /** 个税页勾选的专项附加扣除项 key（仅为回显；金额以 [taxAdditionalDeductionCents] 为准） */
    val taxDeductionItemKeys: Set<String> = emptySet(),
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
    /** 底部弹层背景效果（压暗 / 模糊 / 模糊缩小），默认模糊（v0.6.19 出厂默认） */
    val sheetBackdropMode: SheetBackdropMode = SheetBackdropMode.BLUR,
    /** 日历格隐藏农历日期（默认关 = 显示农历；节日名不受影响） */
    val hideLunarDate: Boolean = false,
    /** 主题引擎（v0.7.8）：MD3 = 现行 Material3 主题；MIUIX = miuix 风格引擎。缺省 MD3（老配置/老备份兼容） */
    val themeEngine: ThemeEngine = ThemeEngine.MD3,
) {
    companion object {
        const val PALETTE_CLASSIC_BLUE = "classic_blue"
        const val PALETTE_TEAL = "teal"
        const val PALETTE_WARM_ORANGE = "warm_orange"
        const val PALETTE_VIOLET = "violet"

        // v0.7.x 扩展（docs/18）。id 一经发布**不可改名**：`paletteId` 是持久化字段，
        // 改名等于清空所有老用户的配色选择（`paletteOf` 的兜底会静默回落到经典蓝）。
        const val PALETTE_CINNABAR = "cinnabar"
        const val PALETTE_ROSE = "rose"
        const val PALETTE_LAKE = "lake"
        const val PALETTE_GRAPHITE = "graphite"
    }
}

/** 底栏配置：槽位从功能池选择，首页固定不可移（记录入口统一在首页大按钮，不占底栏） */
@Serializable
data class BottomBarConfig(
    /**
     * **完整顺序**（含已关闭项；对齐班次卡片：开关只影响是否显示、**不影响顺序**）。
     * 不含固定的「首页」——它恒在底栏首位、不可关闭（保证导航与设置入口永远可达）。
     */
    val order: List<String> = DEFAULT_ORDER,
    /** 已关闭（不在底栏显示）的槽位 id；缺省 = 出厂默认（除「我的」外全关 → 底栏默认只有 首页 + 我的） */
    val disabled: List<String> = DEFAULT_DISABLED,
    /** 旧字段（≤v0.6.21）：只有「已启用列表」。仅用于读取旧配置/旧备份时迁移，见 [migrated] */
    @kotlinx.serialization.SerialName("slots")
    private val legacySlots: List<String>? = null,
) {
    /** 底栏**实际显示**的槽位：固定的首页 + 未关闭项（顺序取自 [order]） */
    val slots: List<String>
        get() = listOf(HOME) + order.filter { it in CONFIGURABLE && it !in disabled }

    /** 旧格式 → 新格式：旧启用项按原顺序在前，其余按出厂默认顺序补后；未启用的进 [disabled] */
    fun migrated(): BottomBarConfig {
        val legacy = legacySlots ?: return this
        val enabled = legacy.filter { it in CONFIGURABLE }.distinct()
        val order = enabled + DEFAULT_ORDER.filter { it !in enabled }
        // disabled 按最终顺序派生（成员判断与顺序无关，但存起来规整、便于比对）
        return BottomBarConfig(order = order, disabled = order.filter { it !in enabled })
    }

    companion object {
        /** 固定首位、不可关闭（首页恒在 ⇒ 一级页顶栏恒在 ⇒ 设置永远进得去） */
        const val HOME = "home"
        /** 功能池（含固定首页；设置/工资固定为二级页面，不入底栏） */
        val POOL = listOf("home", "calendar", "stats", "export", "sync", "profile")
        /** 可配置槽位（除固定首页） */
        val CONFIGURABLE = POOL.filter { it != HOME }
        /** 出厂默认顺序（用户定 2026-09-20，**列表顺序、与开关无关**）：同步 - 统计 - 日历 - 导出（分析）- 我的 */
        val DEFAULT_ORDER = listOf("sync", "stats", "calendar", "export", "profile")
        /** 出厂默认**关闭**：除「我的」外全部关闭 —— 底栏默认只有 首页 + 我的（出厂默认行为，勿改） */
        val DEFAULT_DISABLED = DEFAULT_ORDER.filter { it != "profile" }
    }
}

/**
 * 首页卡片的**内容项** id（卡片内容编辑）：纯字符串常量，**零 Android 依赖**（硬规则 1）。
 * 对应的文案/图标映射在 `core/designsystem/component/HomeCardRegistry.kt` 的 [HomeCardContentsUi]。
 *
 * 当前只「数据区」开放内容自定义（用户 2026-09-27 要求「数据区可自定义编辑」，其余卡暂未开放）。
 */
object HomeCardContents {
    /** 数据区 hero 可显示的内容（**单选**） */
    const val DATA_OT_HOURS = "otHours"
    const val DATA_NET_PAY = "netPay"

    /** 快捷入口卡可放的入口（**多选**，选中项按序全部渲染成入口卡） */
    const val ENTRY_CALENDAR = "calendar"
    const val ENTRY_STATS = "stats"
    const val ENTRY_PAYMONTH = "paymonth"
    const val ENTRY_DETAIL = "detail"
    const val ENTRY_PROFILE = "profile"

    /** 每张卡可选的内容项（列表顺序 = 展示顺序）；不在表里的卡 = 不支持内容自定义。
     *  「本月收入」**不入列**：收入卡已经有它，数据区再给一份是重复（用户 2026-09-27 定）。 */
    val OPTIONS: Map<String, List<String>> = mapOf(
        HomeCardsConfig.DATA to listOf(DATA_OT_HOURS, DATA_NET_PAY),
        HomeCardsConfig.ENTRIES to listOf(ENTRY_CALENDAR, ENTRY_STATS, ENTRY_PAYMONTH, ENTRY_DETAIL, ENTRY_PROFILE),
    )

    /** 出厂默认内容（用户没选过就用它） */
    val DEFAULTS: Map<String, List<String>> = mapOf(
        HomeCardsConfig.DATA to listOf(DATA_OT_HOURS),
        HomeCardsConfig.ENTRIES to listOf(ENTRY_CALENDAR, ENTRY_STATS),
    )

    /** **多选**卡：选中的内容项**都**渲染出来（快捷入口）；其余卡单选、只取第一个 */
    val MULTI_SELECT: Set<String> = setOf(HomeCardsConfig.ENTRIES)

    fun optionsOf(cardId: String): List<String> = OPTIONS[cardId].orEmpty()

    /** 该卡的出厂默认内容（没登记则回落到全部可选项） */
    fun defaultsOf(cardId: String): List<String> = DEFAULTS[cardId] ?: optionsOf(cardId)

    /** 该卡是否支持内容自定义 */
    fun supports(cardId: String): Boolean = cardId in OPTIONS

    /** 该卡是否多选 */
    fun isMultiSelect(cardId: String): Boolean = cardId in MULTI_SELECT

    /** 校验某个内容项是否属于该卡（存储里可能有旧/未知值，一律否掉） */
    fun isValid(cardId: String, contentId: String): Boolean = contentId in optionsOf(cardId)
}

/**
 * 首页卡片配置（v0.6.0 首页卡片可编辑）。
 * cards = 显示中的卡片 id 按显示顺序；null = 用户未配置过（走各页内置默认布局，向后兼容旧数据）。
 * 配置页交互对齐底栏配置：点选开/关 + 拖拽排序；至少保留一张卡片。
 */
@Serializable
data class HomeCardsConfig(
    /** **完整顺序**（含已隐藏项；开关只影响是否显示、**不影响顺序**，对齐班次卡片）。缺省 = 出厂默认顺序 */
    val order: List<String> = DEFAULT_ORDER,
    /** 已隐藏（不在首页显示）的卡片 id；缺省 = 出厂默认隐藏（月柱状 / 热点图） */
    val disabled: List<String> = DEFAULT_DISABLED,
    /** 卡片内容自定义（卡片内容编辑）：卡片 id → **选中的内容项 id 列表**（顺序即显示顺序）。
     *  单选卡（数据区）长度恒为 1；多选卡（快捷入口）选中项按序都渲染。只存**偏离默认**的项。 */
    val content: Map<String, List<String>> = emptyMap(),
    /** 旧字段（≤v0.6.21）：null=未配置、非 null=显式启用列表。仅迁移用，见 [migrated] */
    @kotlinx.serialization.SerialName("cards")
    private val legacyCards: List<String>? = null,
) {
    /** 首页**实际显示**的卡片（顺序取自 [order]；「数据区」固定显示、不可隐藏） */
    val enabledCards: List<String>
        get() {
            val on = order.filter { it in POOL && it !in disabled }
            return if (DATA in on) on else listOf(DATA) + on
        }

    /** 某张卡当前选中的内容项（过滤未知值、去重；没配置 / 全非法 = 该卡出厂默认） */
    fun contentList(cardId: String): List<String> {
        val valid = content[cardId].orEmpty().filter { HomeCardContents.isValid(cardId, it) }.distinct()
        return valid.ifEmpty { HomeCardContents.defaultsOf(cardId) }
    }

    /** 单选卡的当前内容项（多选卡取第一个） */
    fun contentOf(cardId: String): String = contentList(cardId).firstOrNull().orEmpty()

    /** 旧格式 → 新格式：显式配置过的按原顺序在前、其余按默认顺序补后；未启用的进 [disabled]。
     *  旧格式 `cards == null`（未配置 = 出厂默认布局）→ 只做 content 归一化。 */
    fun migrated(): HomeCardsConfig {
        val base = legacyCards?.let { legacy ->
            val enabled = legacy.filter { it in POOL }.distinct()
            HomeCardsConfig(
                order = enabled + DEFAULT_ORDER.filter { it !in enabled },
                disabled = POOL.filter { it !in enabled },
            )
        } ?: this
        // content 归一化：丢掉已下线的卡片 id 与不属于该卡的内容项（升级/降级后存里可能有残留）
        val clean = buildMap {
            base.content.forEach { (cardId, ids) ->
                val valid = ids.filter { HomeCardContents.isValid(cardId, it) }.distinct()
                if (cardId in POOL && valid.isNotEmpty()) put(cardId, valid)
            }
        }
        return if (clean == base.content) base else base.copy(content = clean)
    }

    companion object {
        /** 固定显示、不可隐藏（保证首页至少有一张卡） */
        const val DATA = "data"
        /** 「快捷入口」卡（v0.7.8 由「日历/统计入口」改名；内容可自定义，见 [HomeCardContents]） */
        const val ENTRIES = "entries"
        /** 卡片功能池（v0.6.21 起顺序 = 出厂默认顺序） */
        val POOL = listOf("data", "income", "entries", "weekbar", "monthbar", "heatmap")
        /** 出厂默认顺序（用户定 2026-09-20）：数据区（不可关闭）- 收入卡 - 日历统计入口 - 周柱状 - 月柱状 - 热点图 */
        val DEFAULT_ORDER = POOL
        /** 出厂默认隐藏（保持既有首页默认布局）：月柱状 / 热点图 */
        val DEFAULT_DISABLED = listOf("monthbar", "heatmap")
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
