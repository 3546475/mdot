package com.mdot.app.domain.model

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
    /** 引擎回填时的原始值（分）——同步后手改也保留，供对账展示「引擎 X → 现值 Y」 */
    val engineCents: Long? = null,
    /** 引擎回填日期（yyyy-MM-dd），展示为「来自考勤 · M月d日 同步」 */
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
)

/** 记月行金额来源 */
@Serializable
enum class PayMonthSource {
    /** 「同步本月考勤」回填，且未被手改 */
    SYNCED,

    /** 同步后又被手改（[PayMonthItem.engineCents] 保留引擎值） */
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

        /** 「全勤奖」出厂行 id（补贴组；手填金额，不参与引擎推导） */
        const val FULL_ATTENDANCE_ROW_ID = 11L

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
            PayMonthItem(4, "其它补贴", builtin = true),
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
