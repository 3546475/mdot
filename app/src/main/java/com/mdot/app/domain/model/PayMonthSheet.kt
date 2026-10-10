package com.mdot.app.domain.model

import com.mdot.app.domain.PayMonthDayBasis
import kotlinx.serialization.Serializable

/**
 * 记月（月度工资单，13 文档设计稿）：按月存 DataStore JSON。
 * 分组固定四类（基本/补贴/扣款/其他），行 = 名称 + 金额（分，扣款组按正数存、展示层加负号）。
 * 条目名是用户数据种子（出厂行中文名，与 WorkSystem.displayName 同属 domain displayName 例外）。
 *
 * v0.7.4 起行带**金额来源**（[PayMonthSource]）：区分「引擎算的」与「手填的」，
 * 让加班费可追溯、可对账（docs/20 §P0-3）。字段均有默认值，旧 JSON / 旧备份包解码自动降级。
 */
@Serializable
data class PayMonthItem(
    val id: Long,
    val name: String,
    val amountCents: Long = 0,
    /** 出厂固定行：不可删除（基本组三行与各组成员） */
    val builtin: Boolean = false,
    /** 金额来源；null = 纯手填（含出厂零值） */
    val source: PayMonthSource? = null,
    /** 引擎算出的值（分）——手改后也保留，供对账展示「引擎 X → 现值 Y」（实时预览里 X 随**当前**引擎刷新） */
    val engineCents: Long? = null,
    /** 引擎计算/回填日期（yyyy-MM-dd）；实时预览徽章口径为「自动计算」，仅个税估算行展示日期 */
    val syncedAt: String? = null,
    /**
     * 推导依据的分钟数（如「调休折现」= 本月转调休分钟）——UI 用 TimeUtils.prettyDuration 渲染成
     * 「本月转调休 1.5 小时」，让自动算出来的金额可解释。存**数值**而非文案（硬规则 10）。
     */
    val derivationMinutes: Int? = null,
    /** 推导依据：缴费/计算基数（分），如社保「基数 5000 × 10.5%」 */
    val derivationBaseCents: Long? = null,
    /** 推导依据：比例（**基点**，1 基点 = 0.01%），如 1050 = 10.5% */
    val derivationRateBp: Int? = null,
    // ---- 「按日计算」：行级属性（v0.7.8.4，2026-10-08 用户定「这是添加的某一行的属性」）----
    /**
     * **日单价（分）**；> 0 表示这一行按日计算，引擎每月自动算 `单价 × 天数` 填进 [amountCents]。
     * 0 = 不按日算，保持纯手填（与社保/公积金「比例 0 = 未启用」的语义一致，不清零用户填的值）。
     */
    val dayRateCents: Long = 0,
    /** 按日计算时的**天数口径**（应出勤日 / 实际记录日 / 周期自然日） */
    val dayBasis: PayMonthDayBasis = PayMonthDayBasis.STANDARD,
    /** 按日计算时是否扣掉请假（用户开关，默认不扣——避免与事假/病假的金额扣减双重计账） */
    val dayDeductLeave: Boolean = false,
    /** 推导依据：本次用的**天数**（可为 0.5 这类小数），行上渲染成「21.5 天 × ¥50」 */
    val derivationDays: Double? = null,
    /** 推导依据：本次用的**日单价（分）**，与天数配对展示 */
    val derivationUnitCents: Long? = null,
) {
    /** 本行是否按日计算（引擎会每月自动填金额） */
    val isDailyComputed: Boolean get() = dayRateCents > 0

    /**
     * 本行金额是否由**引擎管**（每月自动重算，用户改的是配置而不是金额）。
     *
     * 目前只有**按日行**：用户改的是 [dayRateCents]，金额是算出来的。
     * （全勤奖 2026-10-08 已从自动降为手动填，故**不**在此列 —— 见 `applyAutoRows` 的说明。）
     *
     * ⚠️ 这一类行**不能**被 [com.mdot.app.domain.isUserOwned] 判成"用户自己的"：
     * 存盘里它们 amountCents 非 0、又没有引擎戳（首次同步前），
     * 若被保护住，实时预览就会永远保留旧值、自动推导等于没做。
     */
    val isAutoManaged: Boolean
        get() = isDailyComputed

    /**
     * 本行是否**允许**配「按日计算」（决定行弹窗里显不显示那个开关）。
     *
     * 用户 2026-10-08 定：**出厂固定行里只有「其它补贴」允许**，其余出厂项一律不给——
     * 它们要么已有确定的算法（基本工资/加班工资/调休折现/事假/病假/社保/公积金/个税），
     * 要么就是手填项（全勤奖）。给这些行开按日只会误导用户以为能改口径。
     * 用户**自己添加**的行（餐补/高温补贴/夜班补贴…）允许——按日补贴本来就是这类项的需求。
     */
    val supportsDailyRate: Boolean
        get() = !builtin || id == PayMonthSheet.OTHER_SUBSIDY_ROW_ID
}

/** 记月行金额来源 */
@Serializable
enum class PayMonthSource {
    /** 引擎算出且未被手改（实时预览/历史同步均可打上） */
    SYNCED,

    /** 引擎行被手改（[PayMonthItem.engineCents] 保留引擎值；实时预览里随当前引擎刷新） */
    EDITED,

    /** 由「个税估算」页填入（engineCents 存估算值，手改后仍可对账） */
    ESTIMATED,
}

@Serializable
data class PayMonthSheet(
    val basic: List<PayMonthItem> = emptyList(),
    val subsidy: List<PayMonthItem> = emptyList(),
    val deduction: List<PayMonthItem> = emptyList(),
    val other: List<PayMonthItem> = emptyList(),
) {
    /** 应发 = 基本 + 补贴 */
    val incomeCents: Long get() = basic.sumOf { it.amountCents } + subsidy.sumOf { it.amountCents }

    /** 扣款合计（存储为正数，展示加负号） */
    val deductionCents: Long get() = deduction.sumOf { it.amountCents }

    /** 其他合计（社保/公积金/个税；存储为正数，展示加负号） */
    val otherCents: Long get() = other.sumOf { it.amountCents }

    /**
     * 实发 = 应发 − 扣款 − 其他。
     * 金额全程「分」（Long）逐条求和，无除法故无舍入问题（硬规则 1）。
     */
    val netCents: Long get() = incomeCents - deductionCents - otherCents

    /**
     * 应发构成（v0.7.4 汇总卡占比条）：基本工资 / 加班工资 / 其他应发（调休折现 + 补贴）。
     * 顺序固定为「基本 → 加班 → 其他」（不按金额排序，环比时图例位置不乱跳），0 段不出现在条上。
     * 推导放 domain（硬规则 12）：UI 不自己拿行金额去凑百分比。
     */
    fun incomeSlices(): List<IncomeSlice> {
        val base = basic.firstOrNull { it.id == BASE_ROW_ID }?.amountCents ?: 0
        val overtime = basic.firstOrNull { it.id == OT_ROW_ID }?.amountCents ?: 0
        // 顺序固定为「基本 → 加班 → 其他」（不按金额排序）：环比时图例位置不乱跳
        return listOf(
            IncomeSlice(IncomeSliceKind.BASE, base),
            IncomeSlice(IncomeSliceKind.OVERTIME, overtime),
            IncomeSlice(IncomeSliceKind.OTHER_INCOME, incomeCents - base - overtime),
        ).filter { it.cents > 0 }
    }

    /**
     * 补齐出厂固定行（builtin）：老版本存的单子没有新加的固定行（如 2026-09-27 新增的「全勤奖」），
     * 在**读取侧**补上，让已存月份也能看到。固定行本身**不可删除**（UI 层禁了删除入口，见
     * `PayMonthContent` 的 `if (!item.builtin)`），所以补回来不会与用户操作冲突。
     * 已有行的相对顺序不动，缺的按**出厂相对位置**插（不是一律堆末尾）。
     */
    fun withBuiltinRows(): PayMonthSheet {
        val d = default()
        return copy(
            basic = basic.withMissingBuiltin(d.basic),
            subsidy = subsidy.withMissingBuiltin(d.subsidy),
            deduction = deduction.withMissingBuiltin(d.deduction),
            other = other.withMissingBuiltin(d.other),
        )
    }

    companion object {
        /** 「基本工资」出厂行 id（「调休折现」的日薪默认值 = 它 ÷ [MONTHLY_PAID_DAYS]） */
        const val BASE_ROW_ID = 1L

        /** 「调休折现」出厂行 id（自动折算据此定位，与行名无关，兼容历史月份） */
        const val COMP_ROW_ID = 3L

        /** 「加班工资」出厂行 id（汇总卡占比条据此分档） */
        const val OT_ROW_ID = 2L

        /** 「社保」出厂行 id（按薪资设定自动回填） */
        const val SOCIAL_ROW_ID = 8L

        /** 「公积金」出厂行 id（按薪资设定自动回填） */
        const val FUND_ROW_ID = 9L

        /** 「个人所得税（新）」出厂行 id（点它进个税估算页） */
        const val TAX_ROW_ID = 10L

        /** 「全勤奖」出厂行 id（补贴组；**手填金额，不参与引擎推导**） */
        const val FULL_ATTENDANCE_ROW_ID = 11L

        /**
         * 「其它补贴」出厂行 id（补贴组的**兜底项**）。
         * 它是出厂行里**唯一**允许配「按日计算」的——见 [PayMonthItem.supportsDailyRate]：
         * 其它出厂项要么有确定算法、要么本身是配置项，只有这一项是"兜底的杂项补贴"。
         */
        const val OTHER_SUBSIDY_ROW_ID = 4L

        /**
         * **用户新增行**的 id 起点。
         * ⚠️ 出厂固定行的 id（1–11）只在**组内**唯一、**跨组会撞车**——扣款组出厂是 [5,6,7]，
         * 用户加的两行就拿到 8/9，恰好是 [SOCIAL_ROW_ID]/[FUND_ROW_ID]，曾致普通扣款行弹出
         * 社保/公积金设置（2026-09-27 用户报，同 docs/11 005 的跨表 id 教训）。
         * 用户行从这个号段起自增，**永不**与出厂固定行相撞。
         */
        const val USER_ROW_ID_BASE = 1000L

    /** 出厂月度工资单（与记月页设计稿一致；id 组内唯一即可）。
     *  ⚠️ 补贴组的**固定行**会经 [withBuiltinRows] 补进老单子，故新增固定行只需改这里。 */
    fun default(): PayMonthSheet = PayMonthSheet(
        basic = listOf(
            PayMonthItem(BASE_ROW_ID, "基本工资", builtin = true),
            PayMonthItem(2, "加班工资", builtin = true),
            // v0.7.4 由「调休」改名：它在这张单里是**折现金额**（天数 × 日薪），不是余额
            PayMonthItem(COMP_ROW_ID, "调休折现", builtin = true),
        ),
        // 具名项在前、「其它补贴」是兑底项所以在后（用户 2026-09-27 要求默认带全勤奖）
        subsidy = listOf(
            PayMonthItem(FULL_ATTENDANCE_ROW_ID, "全勤奖", builtin = true),
            PayMonthItem(OTHER_SUBSIDY_ROW_ID, "其它补贴", builtin = true),
        ),
            deduction = listOf(
                PayMonthItem(5, "其它扣款", builtin = true),
                PayMonthItem(6, "事假", builtin = true),
                PayMonthItem(7, "病假", builtin = true),
            ),
            other = listOf(
                PayMonthItem(8, "社保", builtin = true),
                PayMonthItem(9, "公积金", builtin = true),
                PayMonthItem(10, "个人所得税（新）", builtin = true),
            ),
        )
    }
}

/** 对账行：某条同步过的行被手改后，现值与引擎值的差（docs/20 P1-2） */
data class Reconciliation(
    val group: PayGroup,
    val item: PayMonthItem,
    /** 现值 − 引擎值（分）；正 = 改高了，负 = 改低了 */
    val diffCents: Long,
)

/** 已手改的行（有引擎值且现值不同）；相同则视为一致，不入列 */
fun PayMonthSheet.reconciliations(): List<Reconciliation> = buildList {
    fun scan(group: PayGroup, items: List<PayMonthItem>) = items.forEach { item ->
        val engine = item.engineCents ?: return@forEach
        if (item.amountCents != engine) add(Reconciliation(group, item, item.amountCents - engine))
    }
    scan(PayGroup.BASIC, basic)
    scan(PayGroup.SUBSIDY, subsidy)
    scan(PayGroup.DEDUCTION, deduction)
    scan(PayGroup.OTHER, other)
}

/** 记月分组 */
/**
 * 「社保 / 公积金」设置行的辨识。
 *
 * ⚠️ **必须连同 [PayGroup] 与 builtin 一起判定**：行 id 只在**组内**唯一、跨组会撞车。
 * 2026-09-27 用户报的 bug：扣款组出厂是 [5,6,7]，用户加的两行就拿到 id 8/9，
 * 恰好是 [PayMonthSheet.SOCIAL_ROW_ID]/[PayMonthSheet.FUND_ROW_ID]，只看 id 时
 * 普通扣款行弹出了社保/公积金设置（同 docs/11 005 的跨表 id 教训）。
 */
enum class InsuranceKind { SOCIAL, FUND }

/** 该行是不是「社保 / 公积金」设置行；是则回 [InsuranceKind]，否则 null */
fun PayMonthItem.insuranceKindOf(group: PayGroup): InsuranceKind? = when {
    group != PayGroup.OTHER || !builtin -> null
    id == PayMonthSheet.SOCIAL_ROW_ID -> InsuranceKind.SOCIAL
    id == PayMonthSheet.FUND_ROW_ID -> InsuranceKind.FUND
    else -> null
}

/**
 * 下一个**用户新增行**的 id：从 [PayMonthSheet.USER_ROW_ID_BASE] 起自增，
 * **避开出厂固定行的 id 号段**（否则扣款组会撞上 [PayMonthSheet.SOCIAL_ROW_ID]/[PayMonthSheet.FUND_ROW_ID]）。
 */
fun List<PayMonthItem>.nextUserRowId(): Long =
    maxOf((maxOfOrNull { it.id } ?: 0L) + 1L, PayMonthSheet.USER_ROW_ID_BASE)

/** 把 [defaults] 里缺的固定行插进来（按出厂相对位置）；已有行的相对顺序不动，用户加的行不丢 */
private fun List<PayMonthItem>.withMissingBuiltin(defaults: List<PayMonthItem>): List<PayMonthItem> {
    val missing = defaults.filter { d -> none { it.id == d.id } }
    if (missing.isEmpty()) return this
    val out = ArrayList<PayMonthItem>(size + missing.size)
    forEach { item ->
        // 出厂顺序里排在本行之前的缺失固定行 → 先落位
        val here = defaults.indexOfFirst { it.id == item.id }
        if (here >= 0) {
            missing.forEach { m ->
                if (defaults.indexOf(m) < here && out.none { it.id == m.id }) out.add(m)
            }
        }
        out.add(item)
    }
    // 前面找不到落位点的（本组只剩用户行）：末尾补上，不丢项
    missing.forEach { m -> if (out.none { it.id == m.id }) out.add(m) }
    return out
}

enum class PayGroup { BASIC, SUBSIDY, DEDUCTION, OTHER }

/**
 * **行模板**的一条：记住"这个分组下有这么一行"以及它的**按日计算配置**（v0.7.8.4）。
 *
 * 为什么需要：工资单按月存，新月份底稿只有出厂行，用户自己加的补贴行**下个月就没有了**
 * （用户 2026-10-08 问"这个月添加的非默认项目下个月还会有吗"——原本的答案是"不会"，
 * 只有「导入上月」按钮能整张带过来，而它是**全量覆盖**、连金额一起、还会冲掉本月改动）。
 *
 * 模板**只记行与配置，不记金额**：金额每月按当月数据重算，所以单价能继承、金额不会过期。
 *
 * @property builtinId 出厂固定行记其 id（按 id 匹配，改名也不丢配置）；
 *   用户新增行为 null（按 [group] + [name] 匹配——用户行的 id 每月重新分配，不可靠）。
 */
@Serializable
data class PayMonthRowTemplate(
    val group: String,
    val name: String,
    val builtinId: Long? = null,
    val dayRateCents: Long = 0,
    val dayBasis: PayMonthDayBasis = PayMonthDayBasis.STANDARD,
    val dayDeductLeave: Boolean = false,
)

/** 全部行模板（跨月持久化；进备份包，换手机/恢复不丢） */
@Serializable
data class PayMonthTemplates(val rows: List<PayMonthRowTemplate> = emptyList()) {    /**
     * 由当前单据**重算**模板：用户行进模板；出厂行只在**配了按日计算**时进模板
     * （没配的话出厂行本来就每月都有，没必要记）。
     */
    fun of(sheet: PayMonthSheet): PayMonthTemplates = PayMonthTemplates(
        buildList {
            addAll(templatesOf(PayGroup.BASIC, sheet.basic))
            addAll(templatesOf(PayGroup.SUBSIDY, sheet.subsidy))
            addAll(templatesOf(PayGroup.DEDUCTION, sheet.deduction))
            addAll(templatesOf(PayGroup.OTHER, sheet.other))
        }
    )

    private fun templatesOf(group: PayGroup, list: List<PayMonthItem>): List<PayMonthRowTemplate> =
        list.mapNotNull { item ->
            when {
                item.builtin && !item.isDailyComputed -> null   // 出厂行且没配按日 → 不必记
                else -> PayMonthRowTemplate(
                    group = group.name,
                    name = item.name,
                    builtinId = item.id.takeIf { item.builtin },
                    dayRateCents = item.dayRateCents,
                    dayBasis = item.dayBasis,
                    dayDeductLeave = item.dayDeductLeave,
                )
            }
        }

}

/**
 * 用户**自定义的添加预设**（按分组存；跨月持久化，进备份包）。
 *
 * 背景：添加条目弹窗的预设原先是写死的 string-array，用户想加个"季度奖"这类
 * 自己常用的项，只能每次手敲名称（而且要记得自己敲过什么）。这里让用户能把
 * 自用项**沉淀成预设**——建一次，以后每个月在弹窗里点一下就出来。
 *
 * ⚠️ 与 [PayMonthTemplates] 的分工，别混：
 * - [PayMonthTemplates] 管**单据里的行**（这个月加的行，下个月自动还在）
 * - 本类管**弹窗里的候选**（"我常加哪几项"的清单，与月份无关）
 */
@Serializable
data class PayMonthCustomPresets(
    /** 分组名 → 该分组下用户自建的预设名（保持添加顺序） */
    val byGroup: Map<String, List<String>> = emptyMap(),
) {
    /** 某分组的自定义预设 */
    fun of(group: PayGroup): List<String> = byGroup[group.name].orEmpty()

    /**
     * 追加一条预设：去空白、**同名去重**（含该组已有的自定义项），返回新实例。
     * 返回 null 表示这次没有实际变化（空名或已存在），调用方据此跳过写盘与自动勾选。
     */
    fun plus(group: PayGroup, name: String): PayMonthCustomPresets? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val current = of(group)
        if (trimmed in current) return null
        return copy(byGroup = byGroup + (group.name to current + trimmed))
    }

    /**
     * 删掉一条预设，返回新实例；**不在列表里则返回 null**（调用方据此跳过写盘）。
     *
     * 只是把候选从清单里去掉，**不动任何月份已录入的行**（那些行属于单据/模板，与本清单无关）。
     */
    fun minus(group: PayGroup, name: String): PayMonthCustomPresets? {
        val current = of(group)
        if (name !in current) return null
        val left = current - name
        return copy(
            byGroup = if (left.isEmpty()) byGroup - group.name else byGroup + (group.name to left),
        )
    }
}

/** 取某分组的行列表（记月页与行模板共用的唯一映射点） */
fun PayMonthSheet.rowsOf(group: PayGroup): List<PayMonthItem> = when (group) {
    PayGroup.BASIC -> basic
    PayGroup.SUBSIDY -> subsidy
    PayGroup.DEDUCTION -> deduction
    PayGroup.OTHER -> other
}

/** 换掉某分组的行列表 */
fun PayMonthSheet.replaceRows(group: PayGroup, rows: List<PayMonthItem>): PayMonthSheet = when (group) {
    PayGroup.BASIC -> copy(basic = rows)
    PayGroup.SUBSIDY -> copy(subsidy = rows)
    PayGroup.DEDUCTION -> copy(deduction = rows)
    PayGroup.OTHER -> copy(other = rows)
}

/**
 * 新月份的底稿 = 出厂行 + 模板里的用户行（**只算一次，不在读取侧补**）。
 *
 * ⚠️ 为什么不能像 [withBuiltinRows] 那样放读取侧：出厂行不可删除所以读取侧补没问题，
 * 但**用户行可以删**——读取侧补会在"删除后立刻再读"时把刚删的行**复活**，
 * 表现为"我明明删了它怎么又出现"。所以只在本月**尚无存盘单据时**走一次
 * （`PayMonthViewModel.freshSheet` 与展示流共用本函数），首次写入后就固化。
 */
fun PayMonthSheet.withTemplateRows(templates: PayMonthTemplates): PayMonthSheet {
    if (templates.rows.isEmpty()) return this
    var sheet = this
    for (t in templates.rows) {
        val group = PayGroup.entries.firstOrNull { it.name == t.group } ?: continue
        val rows = sheet.rowsOf(group)
        val hit = rows.indexOfFirst { r ->
            if (t.builtinId != null) r.id == t.builtinId else r.name == t.name
        }
        val updated = if (hit >= 0) {
            // 已有的行只同步**按日配置**，不碰金额/名称（那是本月用户的）
            val old = rows[hit]
            rows.toMutableList().also {
                it[hit] = old.copy(
                    dayRateCents = t.dayRateCents,
                    dayBasis = t.dayBasis,
                    dayDeductLeave = t.dayDeductLeave,
                )
            }
        } else {
            // 模板里有、这张单没有 ⇒ 用户行（上月加过）：按模板补出来，金额留 0 交给引擎算
            rows + PayMonthItem(
                id = rows.nextUserRowId(),
                name = t.name,
                dayRateCents = t.dayRateCents,
                dayBasis = t.dayBasis,
                dayDeductLeave = t.dayDeductLeave,
            )
        }
        sheet = sheet.replaceRows(group, updated)
    }
    return sheet
}

/**
 * 分组折叠状态的**默认值**：四个分组全折叠（v0.7.4 用户要求——进页面先看汇总卡，
 * 别一上来铺四张展开卡；用户一旦自己动过折叠就按持久化的值走）。
 */
val DEFAULT_COLLAPSED_GROUPS: Set<String> = PayGroup.entries.map { it.name }.toSet()

/** 应发构成的一段（汇总卡占比条）；UI 层负责把 [IncomeSliceKind] 映射成文案 */
enum class IncomeSliceKind {
    /** 基本工资 */
    BASE,

    /** 加班工资 */
    OVERTIME,

    /** 其他应发（调休折现 + 补贴） */
    OTHER_INCOME,
}

/** 应发构成的一段：类别 + 金额（分） */
data class IncomeSlice(val kind: IncomeSliceKind, val cents: Long)
