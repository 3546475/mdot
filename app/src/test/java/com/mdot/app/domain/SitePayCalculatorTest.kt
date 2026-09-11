package com.mdot.app.domain

import com.mdot.app.domain.model.SiteAdvance
import com.mdot.app.domain.model.SiteAttendance
import org.junit.Assert.assertEquals
import org.junit.Test

/** 工地记工计算器（12 文档 §4）：两链单测（≥15 用例） */
class SitePayCalculatorTest {

    private fun std(
        rate: Long = 260_00,
        base: Int = 480,
        otMode: String = "BY_DAY",
        otBase: Int = 360,
        otHourly: Long = 0,
    ) = SitePayCalculator.DayStandard(rate, base, otMode, otBase, otHourly)

    private fun att(
        work: Int = 0,
        ot: Int = 0,
        status: String = "WORK",
        rate: Long = 260_00,
        base: Int = 480,
        otMode: String = "BY_DAY",
        otBase: Int = 360,
        otHourly: Long = 0,
        workPay: Long = 0,
        otPay: Long = 0,
    ) = SiteAttendance(
        projectId = 1, date = "2026-09-01", dayStatus = status, workMinutes = work, otMinutes = ot,
        rateCents = rate, baseMinutes = base, otMode = otMode, otBaseMinutes = otBase,
        otHourlyCents = otHourly, workPayCents = workPay, otPayCents = otPay,
        createdAt = 0, updatedAt = 0,
    )

    // ---- 链 1：单条出勤 → 金额 ----

    @Test
    fun `上班 8h 按 1 工计 260 元`() {
        assertEquals(260_00L, SitePayCalculator.workPayCents(std(), 480))
    }

    @Test
    fun `半天 4h = 半工 130 元`() {
        assertEquals(130_00L, SitePayCalculator.workPayCents(std(), 240))
    }

    @Test
    fun `按小时 6h 折 0_75 工 HALF_UP 到分`() {
        // 360/480×26000 = 19500 整除
        assertEquals(195_00L, SitePayCalculator.workPayCents(std(), 360))
    }

    @Test
    fun `HALF_UP 舍入 179分钟x26000除480 = 9695_83 → 9696 分`() {
        // 179×26000/480 = 9695.83… → 9696
        assertEquals(9696L, SitePayCalculator.workPayCents(std(), 179))
    }

    @Test
    fun `零分钟与 REST 与零单价均为 0`() {
        assertEquals(0L, SitePayCalculator.workPayCents(std(), 0))
        assertEquals(0L, SitePayCalculator.workPayCents(std(rate = 0), 480))
    }

    @Test
    fun `加班 BY_DAY 6h=1 加班工 按日价`() {
        assertEquals(260_00L, SitePayCalculator.otPayCents(std(), 360))
    }

    @Test
    fun `加班 BY_DAY 半格 3h = 半个加班工`() {
        assertEquals(130_00L, SitePayCalculator.otPayCents(std(), 180))
    }

    @Test
    fun `加班 BY_HOUR 手动时薪 50元 2h = 100 元`() {
        assertEquals(100_00L, SitePayCalculator.otPayCents(std(otMode = "BY_HOUR", otHourly = 50_00), 120))
    }

    @Test
    fun `加班 BY_HOUR 未填时薪自动折算 日价除上班基准`() {
        // 26000×60/480 = 3250 分/h = 32.5 元/h；2h = 6500 分
        assertEquals(3250L, SitePayCalculator.otHourlyCentsPerHour(std(otMode = "BY_HOUR")))
        assertEquals(6500L, SitePayCalculator.otPayCents(std(otMode = "BY_HOUR"), 120))
    }

    @Test
    fun `加班 BY_HOUR 舍入 90分钟 x 3250分h = 4875 分`() {
        assertEquals(4875L, SitePayCalculator.otPayCents(std(otMode = "BY_HOUR"), 90))
    }

    @Test
    fun `加班零分钟或零日价为 0`() {
        assertEquals(0L, SitePayCalculator.otPayCents(std(), 0))
        assertEquals(0L, SitePayCalculator.otPayCents(std(rate = 0), 120))
    }

    // ---- 链 2：区间汇总 ----

    @Test
    fun `汇总 工数按毫工累计且 REST 不计`() {
        val out = SitePayCalculator.summarize(
            SitePayCalculator.Input(
                attendance = listOf(
                    att(work = 480, workPay = 260_00),                      // 1.0 工
                    att(work = 240, status = "REST", workPay = 0),          // 休息不计
                    att(work = 240, workPay = 130_00, ot = 60, otPay = 43_33), // 0.5 工 + 加班
                ),
                advances = emptyList(),
            )
        )
        assertEquals(2, out.daysWork)
        assertEquals(1, out.daysRest)
        assertEquals(1500L, out.totalWorksMilli)
        assertEquals(60, out.otMinutes)
        assertEquals(260_00 + 130_00 + 43_33, out.workPayCents)
        assertEquals(0L, out.advanceTotalCents)
        assertEquals(433_33L, out.pendingCents)
    }

    @Test
    fun `汇总 借支合计与待结可为负`() {
        val adv = listOf(
            SiteAdvance(projectId = 1, date = "2026-09-02", amountCents = 500_00, createdAt = 0, updatedAt = 0),
            SiteAdvance(projectId = 1, date = "2026-09-03", amountCents = 300_00, createdAt = 0, updatedAt = 0),
        )
        val out = SitePayCalculator.summarize(
            SitePayCalculator.Input(attendance = listOf(att(work = 480, workPay = 260_00)), advances = adv)
        )
        assertEquals(800_00L, out.advanceTotalCents)
        assertEquals(260_00 - 800_00, out.pendingCents)
        assertEquals(260_00L, out.receivableCents)
    }

    @Test
    fun `汇总 空区间全部为零`() {
        val out = SitePayCalculator.summarize(SitePayCalculator.Input(attendance = emptyList(), advances = emptyList()))
        assertEquals(0L, out.totalWorksMilli)
        assertEquals(0L, out.pendingCents)
    }

    // ---- 包工/工量（Phase 2） ----

    @Test
    fun `包工 量价齐自动算钱 15_000m2 x 45元 = 675000 分`() {
        // quantityMilli=15000 (15.0), price=4500 分 → 15000×4500/1000 = 67500
        assertEquals(67_500L, SitePayCalculator.pieceAmountCents(15_000, 4_500, 0))
    }

    @Test
    fun `包工 量价 HALF_UP 舍入 0_333 吨 x 700 分 = 233_1 → 233`() {
        assertEquals(233L, SitePayCalculator.pieceAmountCents(333, 700, 0))
    }

    @Test
    fun `包工 量价缺失直填金额优先`() {
        assertEquals(50_000L, SitePayCalculator.pieceAmountCents(0, 0, 50_000))
        assertEquals(50_000L, SitePayCalculator.pieceAmountCents(1000, 0, 50_000))
    }

    @Test
    fun `包工 手填工钱优先于量价自动（工钱可直接编辑）`() {
        assertEquals(50_000L, SitePayCalculator.pieceAmountCents(15_000, 4_500, 50_000))
    }

    @Test
    fun `汇总 应得与待结含包工`() {
        val piece = com.mdot.app.domain.model.SitePieceWork(
            projectId = 1, date = "2026-09-02", itemName = "砌墙", unit = "m²",
            quantityMilli = 10_000, unitPriceCents = 2_000, amountCents = 20_000,
            createdAt = 0, updatedAt = 0,
        )
        val out = SitePayCalculator.summarize(
            SitePayCalculator.Input(
                attendance = listOf(att(work = 480, workPay = 260_00)),
                pieceWorks = listOf(piece),
                advances = listOf(
                    SiteAdvance(projectId = 1, date = "2026-09-03", amountCents = 100_00, createdAt = 0, updatedAt = 0),
                ),
            )
        )
        assertEquals(260_00L, out.workPayCents)
        assertEquals(200_00L, out.piecePayCents)
        assertEquals(460_00L, out.receivableCents)
        assertEquals(360_00L, out.pendingCents)
    }

    @Test
    fun `汇总 上班零分钟不记出勤天数`() {
        val out = SitePayCalculator.summarize(
            SitePayCalculator.Input(attendance = listOf(att(work = 0, ot = 120, otPay = 65_00)), advances = emptyList())
        )
        assertEquals(0, out.daysWork)
        assertEquals(120, out.otMinutes)
        assertEquals(65_00L, out.workPayCents)
    }
}
