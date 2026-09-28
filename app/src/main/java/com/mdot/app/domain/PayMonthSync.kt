package com.mdot.app.domain

import com.mdot.app.domain.model.LeaveType
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthSheet
import com.mdot.app.domain.model.PayMonthSource
import com.mdot.app.domain.model.SalaryConfig

/**
 * 记月（月度工资单）的**引擎推导**（纯函数，供单测）：
 * 存盘回填（[applyRecordSync]）与实时预览（[livePreviewSheet]）。
 * 两处共用同一套推导，避免两套算法漂移（硬规则 12：推导必须复用 domain 引擎函数）。
 * 原在 `feature/stats/PayMonthViewModel.kt`，2026-09-28 首页实时预览需要复用时迁入 domain。
 *
 * 实时预览的使用者：首页数据区「实发工资」与**记月页本身**（2026-09-28 起记月页整页实时出数，
 * 「同步本月考勤」按钮移除）——[applyRecordSync] 仍作为两者的共同推导内核。
 */

/** 记月同步（纯函数，供单测）：仅回填存在的行——加班工资/事假/病假；基本工资仅在薪资含底薪时回填；其余行与分组不动。
 *
 * 回填时打**来源戳**（[PayMonthSource.SYNCED] + engineCents 引擎原值 + syncedAt 日期），
 * 让「哪个数是引擎算的、哪个是手写的」可追溯（docs/20 P0-3）；[syncedAt] 由调用方传（VM 用当天日期，便于单测固定值）。
 */
internal fun applyRecordSync(
    sheet: PayMonthSheet,
    out: PayrollCalculator.Output,
    fillBase: Boolean,
    syncedAt: String,
    /** 「调休折现」金额（分），由 [PayrollCalculator.compCashCents] 算好传入 */
    compCashCents: Long = 0,
    /** 推导依据：本月转调休分钟，UI 渲染成「本月转调休 1.5 小时」 */
    compMinutes: Int = 0,
    /** 社保/公积金自动回填载荷（比例 0 = 不算，行保持原值） */
    insurance: InsuranceFill = InsuranceFill(),
): PayMonthSheet {
    fun set(
        list: List<PayMonthItem>,
        id: Long,
        cents: Long,
        derivationMinutes: Int? = null,
        derivationBaseCents: Long? = null,
        derivationRateBp: Int? = null,
    ) = list.map {
        if (it.id == id) {
            it.copy(
                amountCents = cents,
                source = PayMonthSource.SYNCED,
                engineCents = cents,
                syncedAt = syncedAt,
                derivationMinutes = derivationMinutes,
                derivationBaseCents = derivationBaseCents,
                derivationRateBp = derivationRateBp,
            )
        } else {
            it
        }
    }

    val basic = if (fillBase) set(sheet.basic, PayMonthSheet.BASE_ROW_ID, out.baseIncludedCents) else sheet.basic
    return sheet.copy(
        // 加班工资 ← 引擎；「调休折现」← 转调休分钟按同一套口径折算（自动，不用手填）
        basic = set(
            set(basic, 2L, out.otPayCents),
            PayMonthSheet.COMP_ROW_ID,
            compCashCents,
            derivationMinutes = compMinutes.takeIf { it > 0 },
        ),
        deduction = set(
            set(sheet.deduction, 6L, out.leaveDeductByType[LeaveType.PERSONAL] ?: 0L),
            7L, out.leaveDeductByType[LeaveType.SICK] ?: 0L,
        ),
        // 社保/公积金 ← 薪资设定里的「基数 × 比例」（设一次、之后每月自动）。
        // ⚠️ 比例 0 = 用户没启用该项，**不动该行**——否则会把用户手填的金额清零。
        other = sheet.other
            .let {
                if (insurance.socialRateBp <= 0) it
                else set(
                    it,
                    PayMonthSheet.SOCIAL_ROW_ID,
                    insurance.socialCents,
                    derivationBaseCents = insurance.socialBaseCents,
                    derivationRateBp = insurance.socialRateBp,
                )
            }
            .let {
                if (insurance.fundRateBp <= 0) it
                else set(
                    it,
                    PayMonthSheet.FUND_ROW_ID,
                    insurance.fundCents,
                    derivationBaseCents = insurance.fundBaseCents,
                    derivationRateBp = insurance.fundRateBp,
                )
            },
    )
}

/**
 * 社保/公积金自动回填载荷（由薪资设定算出，docs/20「自动 > 选项 > 手动」）。
 * 比例 0 = 用户没启用该项，对应行保持手填不动。
 */
internal data class InsuranceFill(
    val socialCents: Long = 0,
    val socialBaseCents: Long = 0,
    val socialRateBp: Int = 0,
    val fundCents: Long = 0,
    val fundBaseCents: Long = 0,
    val fundRateBp: Int = 0,
) {
    companion object {
        /** 从薪资设定算出两个载荷（比例 0 时金额也是 0） */
        fun of(salary: SalaryConfig) = InsuranceFill(
            socialCents = PayrollCalculator.socialInsuranceCents(salary),
            socialBaseCents = PayrollCalculator.socialInsuranceBaseCents(salary),
            socialRateBp = salary.socialInsuranceRateBp,
            fundCents = PayrollCalculator.housingFundCents(salary),
            fundBaseCents = PayrollCalculator.housingFundBaseCents(salary),
            fundRateBp = salary.housingFundRateBp,
        )
    }
}

/**
 * 首页数据区「实发工资」的**实时预览单据**（只算不写盘）：
 * 引擎算得出的行（基本工资/加班工资/调休折现/事假/病假/社保/公积金）用**当前**考勤与薪资设定
 * 实时推导——与「同步本月考勤」（[applyRecordSync]）完全同一套算法；用户自有的行保留手改值
 * （[PayMonthItem.isUserOwned]），其余手填行（全勤奖/其它补贴/个税…）照存盘单据计入。
 *
 * 因此首页 hero 与记月页汇总卡的实发 = 本预览的 `netCents`，**不需要任何「同步」动作**（2026-09-28 用户要求）。
 * [syncedAt] 仅供 [applyRecordSync] 的来源戳格式完整；预览不落盘，该日期无展示用途。
 */
internal fun livePreviewSheet(
    sheet: PayMonthSheet,
    out: PayrollCalculator.Output,
    fillBase: Boolean,
    syncedAt: String,
    compCashCents: Long = 0,
    compMinutes: Int = 0,
    insurance: InsuranceFill = InsuranceFill(),
): PayMonthSheet {
    val synced = applyRecordSync(sheet, out, fillBase, syncedAt, compCashCents, compMinutes, insurance)
    return synced.copy(
        basic = liveMerged(sheet.basic, synced.basic),
        subsidy = liveMerged(sheet.subsidy, synced.subsidy),
        deduction = liveMerged(sheet.deduction, synced.deduction),
        other = liveMerged(sheet.other, synced.other),
    )
}

/**
 * 逐行合并预览：**用户自有的行保留存盘值**（手改优先，docs/20 P0-3/P2-2），
 * 其余行用引擎实时值；行序以同步结果为准（同步不改行序）。
 *
 * 曾引擎回填过的手改行（engineCents 非空）顺带把**引擎元数据**（engineCents/syncedAt/推导依据）
 * 刷新到实时引擎值——金额仍是用户的，但「已手改（引擎 X）」的 X 与对账差额跟**当前**引擎走，
 * 不冻结在手改当时（2026-09-28 记月页实时化）；纯手填行（engineCents 为空）原样保留、不进对账。
 * ⚠️ 归属判定只看**存盘行**（[isUserOwned] 读 old），刷新只发生在预览结果里、不落盘。
 */
private fun liveMerged(old: List<PayMonthItem>, new: List<PayMonthItem>): List<PayMonthItem> =
    new.map { n ->
        val o = old.firstOrNull { it.id == n.id } ?: return@map n
        when {
            !o.isUserOwned() -> n
            // 纯手填行（从未被引擎戳过）：整行保留，连 engineCents 也不补——保持「不进对账」的口径
            o.engineCents == null -> o
            else -> o.copy(
                engineCents = n.engineCents,
                syncedAt = n.syncedAt,
                derivationMinutes = n.derivationMinutes,
                derivationBaseCents = n.derivationBaseCents,
                derivationRateBp = n.derivationRateBp,
            )
        }
    }

/**
 * 该行金额是不是**用户自己的**：`EDITED`（同步后手改）或「无引擎戳且金额非 0」（纯手填）。
 * 实时预览对这种行保留用户值、不被引擎当前值覆盖；金额为 0 且从未同步的行视为「待引擎填」，
 * 正是实时预览要补上的部分。
 */
internal fun PayMonthItem.isUserOwned(): Boolean =
    source == PayMonthSource.EDITED || (engineCents == null && amountCents != 0L)
