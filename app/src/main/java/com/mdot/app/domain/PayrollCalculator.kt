package com.mdot.app.domain

import com.mdot.app.domain.model.CompAdjustment
import com.mdot.app.domain.model.DailyRecord
import com.mdot.app.domain.model.LeaveType
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.RecordType
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.SalaryMode
import com.mdot.app.domain.model.TierSource
import com.mdot.app.domain.model.WorkSystem
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * 工资引擎（纯函数，无 Android 依赖，07 文档 §3）。
 * 规则：金额全程以「分」计算；逐条记录金额 roundHalfUp 到分后再求和。
 * 21.75 为法定月计薪天数（不可改），日薪 = 底薪/21.75，时薪 = 日薪/8。
 *
 * v0.4.x 引入策略模式：按 SalaryConfig.workSystem 选择 PayrollStrategy 实现。
 */
object PayrollCalculator {

    const val MONTHLY_FACTOR_DAYS = "21.75"
    const val DAILY_HOURS = 8

    internal val HOURS_PER_MONTH = BigDecimal("174") // 21.75 × 8
    internal val MONTHLY_DAYS = BigDecimal(MONTHLY_FACTOR_DAYS)
    internal val MINUTES_PER_WORKDAY = BigDecimal(DAILY_HOURS * 60L)

    data class Input(
        val salary: SalaryConfig,
        val records: List<DailyRecordLite>,
        val compAdjustments: List<CompAdjustmentLite> = emptyList(),
        val tierOf: (LocalDate) -> RateTier,
        /**
         * 周期标准工时（分钟），仅综合工时使用（10 文档 §4）。
         * 由调用方按「应出勤天数 × 8h」自动算好后注入（可测）；salary.comprehensiveStandardMinutes > 0 时被手动值覆盖。
         */
        val standardMinutes: Int? = null,
    )

    /** 单日单条明细（加班费或请假扣款） */
    data class RecordBreakdown(
        val record: DailyRecordLite,
        /** 加班记录的实际计酬档位（手动补改优先）；请假为 null */
        val tier: RateTier?,
        /** OT=加班费；LEAVE=扣款 */
        val amountCents: Long,
    )

    data class Output(
        val breakdowns: List<RecordBreakdown>,
        val otMinutes: Int,
        /** 实际计酬加班分钟（扣除转调休部分） */
        val paidOtMinutes: Int,
        val otPayCents: Long,
        val leaveMinutes: Int,
        val leaveDeductCents: Long,
        /** 调休余额 = Σ转调休 − Σ调休请假 + Σ手动调整（可负） */
        val compBalanceMinutes: Int,
        val baseIncludedCents: Long,
        /** 底薪(可选计入) + 加班费 − 请假扣款 */
        val incomeCents: Long,
        val otPayByTier: Map<RateTier, Long>,
        val leaveDeductByType: Map<LeaveType, Long>,
        // ---- 综合工时派生字段（其他制度恒 0/0，10 文档 §4）----
        /** 周期标准工时（实际生效值：手动覆盖优先，否则注入的自动值；分钟） */
        val periodStandardMinutes: Int = 0,
        /** 周期超时分钟 = max(0, 普通工时 − 标准) */
        val overtimeMinutes: Int = 0,
        /** 法定节假日工作分钟（即时计酬、不计入周期总工时，D3 口径） */
        val holidayWorkMinutes: Int = 0,
    )

    /** 域内轻量记录（避免依赖数据层实体） */
    data class DailyRecordLite(
        val date: LocalDate,
        val type: RecordType,
        val durationMinutes: Int,
        val tier: RateTier? = null,
        val tierSource: TierSource? = null,
        val toCompMinutes: Int = 0,
        val leaveType: LeaveType? = null,
        val shiftName: String? = null,
        val note: String? = null,
    )

    data class CompAdjustmentLite(val deltaMinutes: Int)

    /** 按工时制度选择策略 */
    fun strategyFor(system: WorkSystem): PayrollStrategy = when (system) {
        WorkSystem.STANDARD -> StandardPayrollStrategy
        WorkSystem.HOURLY -> HourlyPayrollStrategy
        WorkSystem.COMPREHENSIVE -> ComprehensivePayrollStrategy
    }

    /** 兼容门面：根据 input.salary.workSystem 自动选择策略 */
    fun summarize(input: Input): Output =
        strategyFor(input.salary.workSystem).summarize(input)

    /** 记录级档位：手动补改（MANUAL）优先，否则自动判定 */
    fun effectiveTier(record: DailyRecordLite, tierOf: (LocalDate) -> RateTier): RateTier =
        if (record.tierSource == TierSource.MANUAL) record.tier ?: tierOf(record.date)
        else tierOf(record.date)
}

/** 工资计算策略接口（08 文档 §4.1） */
interface PayrollStrategy {
    fun summarize(input: PayrollCalculator.Input): PayrollCalculator.Output
}

/**
 * 标准工时策略：底薪 + 加班费 − 请假扣款。
 * 加班费 = 时薪 × 倍率 × 加班小时；请假扣款 = 日薪 × 系数 × 小时/8。
 */
object StandardPayrollStrategy : PayrollStrategy {

    override fun summarize(input: PayrollCalculator.Input): PayrollCalculator.Output {
        val breakdowns = ArrayList<PayrollCalculator.RecordBreakdown>(input.records.size)
        var otMinutes = 0
        var paidOtMinutes = 0
        var leaveMinutes = 0
        var compFromOt = 0
        var compLeaveMinutes = 0
        val otPayByTier = linkedMapOf<RateTier, Long>()
        val leaveDeductByType = linkedMapOf<LeaveType, Long>()

        for (r in input.records) {
            when (r.type) {
                RecordType.OT -> {
                    otMinutes += r.durationMinutes
                    compFromOt += r.toCompMinutes
                    val tier = PayrollCalculator.effectiveTier(r, input.tierOf)
                    val payMinutes = r.durationMinutes - r.toCompMinutes
                    val cents = overtimeCents(input.salary, tier, payMinutes)
                    if (payMinutes > 0) paidOtMinutes += payMinutes
                    otPayByTier.merge(tier, cents, Long::plus)
                    breakdowns += PayrollCalculator.RecordBreakdown(r, tier, cents)
                }

                RecordType.LEAVE -> {
                    leaveMinutes += r.durationMinutes
                    val type = r.leaveType ?: LeaveType.OTHER
                    if (type == LeaveType.COMP) compLeaveMinutes += r.durationMinutes
                    val cents = leaveDeductCents(input.salary, type, r.durationMinutes)
                    leaveDeductByType.merge(type, cents, Long::plus)
                    breakdowns += PayrollCalculator.RecordBreakdown(r, null, cents)
                }
            }
        }

        val otPay = otPayByTier.values.sum()
        val deduct = leaveDeductByType.values.sum()
        val base = if (input.salary.includeBase) input.salary.baseSalaryCents else 0L
        val compAdjust = input.compAdjustments.sumOf { it.deltaMinutes }

        return PayrollCalculator.Output(
            breakdowns = breakdowns.sortedBy { it.record.date },
            otMinutes = otMinutes,
            paidOtMinutes = paidOtMinutes,
            otPayCents = otPay,
            leaveMinutes = leaveMinutes,
            leaveDeductCents = deduct,
            compBalanceMinutes = compFromOt - compLeaveMinutes + compAdjust,
            baseIncludedCents = base,
            incomeCents = base + otPay - deduct,
            otPayByTier = otPayByTier,
            leaveDeductByType = leaveDeductByType,
        )
    }

    /** 加班费（分）：BASE=时薪×倍率×小时；MANUAL=三档单价×小时；HALF_UP 到分 */
    fun overtimeCents(salary: SalaryConfig, tier: RateTier, minutes: Int): Long {
        if (minutes <= 0) return 0
        val minutesBd = BigDecimal(minutes)
        return when (salary.mode) {
            SalaryMode.BASE -> {
                val hourly = BigDecimal(salary.baseSalaryCents)
                    .divide(PayrollCalculator.HOURS_PER_MONTH, 10, RoundingMode.HALF_UP)
                val mult = BigDecimal.valueOf(salary.multipliers[tier] ?: 1.5)
                hourly.multiply(mult).multiply(minutesBd)
                    .divide(BigDecimal(60), 0, RoundingMode.HALF_UP).toLong()
            }

            SalaryMode.MANUAL -> {
                val rate = BigDecimal(salary.manualRatesCents[tier] ?: 0L)
                rate.multiply(minutesBd)
                    .divide(BigDecimal(60), 0, RoundingMode.HALF_UP).toLong()
            }
        }
    }

    /** 请假扣款（分）= 日薪 × 扣薪系数 × 小时/8，HALF_UP 到分 */
    fun leaveDeductCents(salary: SalaryConfig, type: LeaveType, minutes: Int): Long {
        if (minutes <= 0) return 0
        val coefficient = salary.leaveCoefficients[type] ?: type.defaultCoefficient
        if (coefficient <= 0) return 0
        val daily = BigDecimal(salary.baseSalaryCents)
            .divide(PayrollCalculator.MONTHLY_DAYS, 10, RoundingMode.HALF_UP)
        return daily.multiply(BigDecimal.valueOf(coefficient)).multiply(BigDecimal(minutes))
            .divide(PayrollCalculator.MINUTES_PER_WORKDAY, 0, RoundingMode.HALF_UP).toLong()
    }
}

/**
 * 小时工策略（08 文档 §4.2）：纯时薪制，无底薪无调休。
 * 工作收入 = 档位时薪 × 工作小时；请假扣款 = 平时时薪 × 系数 × 小时。
 */
object HourlyPayrollStrategy : PayrollStrategy {

    override fun summarize(input: PayrollCalculator.Input): PayrollCalculator.Output {
        val breakdowns = ArrayList<PayrollCalculator.RecordBreakdown>(input.records.size)
        var workMinutes = 0
        var leaveMinutes = 0
        val workPayByTier = linkedMapOf<RateTier, Long>()
        val leaveDeductByType = linkedMapOf<LeaveType, Long>()

        for (r in input.records) {
            when (r.type) {
                RecordType.OT -> {
                    // 小时工模式下 OT 语义为"工作"
                    workMinutes += r.durationMinutes
                    val tier = PayrollCalculator.effectiveTier(r, input.tierOf)
                    val cents = workCents(input.salary, tier, r.durationMinutes)
                    workPayByTier.merge(tier, cents, Long::plus)
                    breakdowns += PayrollCalculator.RecordBreakdown(r, tier, cents)
                }

                RecordType.LEAVE -> {
                    leaveMinutes += r.durationMinutes
                    val type = r.leaveType ?: LeaveType.OTHER
                    val cents = leaveDeductCents(input.salary, type, r.durationMinutes)
                    leaveDeductByType.merge(type, cents, Long::plus)
                    breakdowns += PayrollCalculator.RecordBreakdown(r, null, cents)
                }
            }
        }

        val workPay = workPayByTier.values.sum()
        val deduct = leaveDeductByType.values.sum()

        return PayrollCalculator.Output(
            breakdowns = breakdowns.sortedBy { it.record.date },
            otMinutes = workMinutes,        // 复用字段，语义为"工作分钟"
            paidOtMinutes = workMinutes,
            otPayCents = workPay,           // 复用字段，语义为"工时收入"
            leaveMinutes = leaveMinutes,
            leaveDeductCents = deduct,
            compBalanceMinutes = 0,         // 小时工无调休
            baseIncludedCents = 0,          // 小时工无底薪
            incomeCents = workPay - deduct,
            otPayByTier = workPayByTier,
            leaveDeductByType = leaveDeductByType,
        )
    }

    /** 工作收入（分）= 档位时薪 × 小时，HALF_UP 到分 */
    fun workCents(salary: SalaryConfig, tier: RateTier, minutes: Int): Long {
        if (minutes <= 0) return 0
        val rate = BigDecimal(salary.hourlyRatesCents[tier] ?: 0L)
        return rate.multiply(BigDecimal(minutes))
            .divide(BigDecimal(60), 0, RoundingMode.HALF_UP).toLong()
    }

    /** 请假扣款（分）= 平时时薪 × 扣薪系数 × 小时，HALF_UP 到分 */
    fun leaveDeductCents(salary: SalaryConfig, type: LeaveType, minutes: Int): Long {
        if (minutes <= 0) return 0
        val coefficient = salary.leaveCoefficients[type] ?: type.defaultCoefficient
        if (coefficient <= 0) return 0
        val hourly = BigDecimal(salary.hourlyRatesCents[RateTier.WEEKDAY] ?: 0L)
        return hourly.multiply(BigDecimal.valueOf(coefficient))
            .multiply(BigDecimal(minutes))
            .divide(BigDecimal(60), 0, RoundingMode.HALF_UP).toLong()
    }
}

/**
 * 综合工时策略（10 文档 §4）：加班判定从「单日超 8h」变为「周期总工时超标准」。
 * - 法定节假日（STATUTORY 档）＝唯一即时档：按法定倍率逐条计酬，且不计入周期总工时（D3 主流口径）；
 * - 其余工作分钟（含 WEEKEND 档，D5：综合工时下无 200% 档）一律累计，周期末超出标准的部分
 *   按超时倍率（WEEKDAY 档倍率）一次性结算，月中为实时预演值；
 * - 转调休不生效（D4），toCompMinutes 忽略、调休余额恒 0；
 * - 请假扣款同标准工时（日薪 × 系数 × 小时/8），底薪结构同标准工时。
 * Output 复用：otMinutes=周期普通工时；otPayCents=法定即时 + 超时结算合计（WEEKDAY 键=超时部分）。
 */
object ComprehensivePayrollStrategy : PayrollStrategy {

    override fun summarize(input: PayrollCalculator.Input): PayrollCalculator.Output {
        val breakdowns = ArrayList<PayrollCalculator.RecordBreakdown>(input.records.size)
        var workMinutes = 0        // 普通工时（非法定节假日），计入周期总工时
        var holidayMinutes = 0     // 法定节假日工时，即时计酬
        var leaveMinutes = 0
        val payByTier = linkedMapOf<RateTier, Long>()
        val leaveDeductByType = linkedMapOf<LeaveType, Long>()

        for (r in input.records) {
            when (r.type) {
                RecordType.OT -> {
                    val tier = PayrollCalculator.effectiveTier(r, input.tierOf)
                    if (tier == RateTier.STATUTORY) {
                        holidayMinutes += r.durationMinutes
                        val cents =
                            StandardPayrollStrategy.overtimeCents(input.salary, RateTier.STATUTORY, r.durationMinutes)
                        payByTier.merge(RateTier.STATUTORY, cents, Long::plus)
                        breakdowns += PayrollCalculator.RecordBreakdown(r, tier, cents)
                    } else {
                        // 计入周期工时，不即时计酬（是否超时由周期末统一判定）
                        workMinutes += r.durationMinutes
                        breakdowns += PayrollCalculator.RecordBreakdown(r, tier, 0L)
                    }
                }

                RecordType.LEAVE -> {
                    leaveMinutes += r.durationMinutes
                    val type = r.leaveType ?: LeaveType.OTHER
                    val cents = StandardPayrollStrategy.leaveDeductCents(input.salary, type, r.durationMinutes)
                    leaveDeductByType.merge(type, cents, Long::plus)
                    breakdowns += PayrollCalculator.RecordBreakdown(r, null, cents)
                }
            }
        }

        // 周期标准：手动覆盖（>0）优先，否则用调用方注入的自动值；两者皆缺省按 0（全量按超时结算）
        val standard = input.salary.comprehensiveStandardMinutes.takeIf { it > 0 }
            ?: input.standardMinutes ?: 0
        val overtimeMinutes = (workMinutes - standard).coerceAtLeast(0)
        val overtimePayCents = surplusPayCents(input.salary, overtimeMinutes)
        if (overtimeMinutes > 0) payByTier.merge(RateTier.WEEKDAY, overtimePayCents, Long::plus)

        val otPay = payByTier.values.sum()
        val deduct = leaveDeductByType.values.sum()
        val base = if (input.salary.includeBase) input.salary.baseSalaryCents else 0L

        return PayrollCalculator.Output(
            breakdowns = breakdowns.sortedBy { it.record.date },
            otMinutes = workMinutes,                    // 语义：周期内普通工时（法定节假日另计）
            paidOtMinutes = holidayMinutes + overtimeMinutes,
            otPayCents = otPay,
            leaveMinutes = leaveMinutes,
            leaveDeductCents = deduct,
            compBalanceMinutes = 0,                     // 综合工时无调休（D4）
            baseIncludedCents = base,
            incomeCents = base + otPay - deduct,
            otPayByTier = payByTier,
            leaveDeductByType = leaveDeductByType,
            periodStandardMinutes = standard,
            overtimeMinutes = overtimeMinutes,
            holidayWorkMinutes = holidayMinutes,
        )
    }

    /** 超时加班费（分）= 时薪 × 超时倍率（WEEKDAY 档）× 超时分钟；MANUAL 模式用平时档单价；HALF_UP 到分 */
    private fun surplusPayCents(salary: SalaryConfig, minutes: Int): Long {
        if (minutes <= 0) return 0
        val minutesBd = BigDecimal(minutes)
        return when (salary.mode) {
            SalaryMode.BASE -> {
                val hourly = BigDecimal(salary.baseSalaryCents)
                    .divide(PayrollCalculator.HOURS_PER_MONTH, 10, RoundingMode.HALF_UP)
                val mult = BigDecimal.valueOf(salary.multipliers[RateTier.WEEKDAY] ?: 1.5)
                hourly.multiply(mult).multiply(minutesBd)
                    .divide(BigDecimal(60), 0, RoundingMode.HALF_UP).toLong()
            }

            SalaryMode.MANUAL -> {
                val rate = BigDecimal(salary.manualRatesCents[RateTier.WEEKDAY] ?: 0L)
                rate.multiply(minutesBd)
                    .divide(BigDecimal(60), 0, RoundingMode.HALF_UP).toLong()
            }
        }
    }
}

/** 域模型 → 引擎轻量模型 */
fun DailyRecord.toCalcLite() = PayrollCalculator.DailyRecordLite(
    date = date,
    type = type,
    durationMinutes = durationMinutes,
    tier = tier,
    tierSource = tierSource,
    toCompMinutes = toCompMinutes,
    leaveType = leaveType,
    shiftName = shiftName,
    note = note,
)

fun CompAdjustment.toCalcLite() = PayrollCalculator.CompAdjustmentLite(deltaMinutes)
