package com.mdot.app.domain

import com.mdot.app.core.sync.BackupFile
import com.mdot.app.core.sync.RecordDto
import com.mdot.app.core.sync.SiteAttendanceDto
import com.mdot.app.core.sync.validateBackupInvariants
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 13 文档阶段二修复的纯函数防线：
 * - B4-04 parseYuanToCents 负数拒绝
 * - B4-02/B6-07 worksMilliOf HALF_UP 统一（引擎与日历同源）
 * - B4-03 validateBackupInvariants 备份包业务不变量
 */
class Stage2GuardTest {

    // ---- B4-04 金额解析 ----

    @Test
    fun `负数金额被拒绝`() {
        assertNull(Money.parseYuanToCents("-5"))
        assertNull(Money.parseYuanToCents("-0.01"))
        assertNull(Money.parseYuanToCents("-,5"))
    }

    @Test
    fun `合法金额不受影响`() {
        assertEquals(500L, Money.parseYuanToCents("5"))
        assertEquals(50050L, Money.parseYuanToCents("500.5"))
        assertEquals(500050L, Money.parseYuanToCents("5,000.50"))
        assertEquals(501L, Money.parseYuanToCents("5.005"))   // HALF_UP 到分
        assertEquals(0L, Money.parseYuanToCents("0"))
    }

    // ---- B4-02/B6-07 毫工 HALF_UP ----

    @Test
    fun `毫工换算HALF_UP不截断`() {
        // 441 分钟 ÷ 480 基准 = 918.75 毫工 → 919（旧 Long 整除截断为 918）
        assertEquals(919L, SitePayCalculator.worksMilliOf(441, 480))
        // 恰好整除
        assertEquals(1000L, SitePayCalculator.worksMilliOf(480, 480))
        assertEquals(500L, SitePayCalculator.worksMilliOf(240, 480))
        // 半毫工边界：241 分钟 = 502.08 毫工 → 502
        assertEquals(502L, SitePayCalculator.worksMilliOf(241, 480))
        // 恰在 .5 上：169 分钟 = 352.08；取真 .5 用 240.24 不可整——用 341/400=852.5 → 853
        assertEquals(853L, SitePayCalculator.worksMilliOf(341, 400))
        // 边界：0 或非法基准
        assertEquals(0L, SitePayCalculator.worksMilliOf(0, 480))
        assertEquals(0L, SitePayCalculator.worksMilliOf(480, 0))
    }

    @Test
    fun `汇总毫工与单条换算一致`() {
        val att = com.mdot.app.domain.model.SiteAttendance(
            projectId = 1, date = "2026-09-14", dayStatus = "WORK",
            workMinutes = 441, otMinutes = 0, rateCents = 26_000, baseMinutes = 480,
            otMode = "BY_DAY", otBaseMinutes = 360, otHourlyCents = 0,
            createdAt = 0, updatedAt = 0,
        )
        val out = SitePayCalculator.summarize(SitePayCalculator.Input(attendance = listOf(att), advances = emptyList()))
        assertEquals(SitePayCalculator.worksMilliOf(441, 480), out.totalWorksMilli)
    }

    // ---- B4-03 备份不变量 ----

    private fun record(duration: Int = 120, toComp: Int = 0) = RecordDto(
        date = "2026-09-14", type = "OT", durationMinutes = duration,
        toCompMinutes = toComp, createdAt = 0, updatedAt = 0,
    )

    private fun siteAtt(dayStatus: String = "WORK", work: Int = 480, ot: Int = 0) =
        com.mdot.app.core.sync.SiteAttendanceDto(
            projectId = 1, date = "2026-09-14", dayStatus = dayStatus,
            workMinutes = work, otMinutes = ot, rateCents = 26_000,
            baseMinutes = 480, otMode = "BY_DAY", otBaseMinutes = 360, otHourlyCents = 0,
            createdAt = 0, updatedAt = 0,
        )

    @Test
    fun `合法包通过校验`() {
        validateBackupInvariants(
            BackupFile(
                records = listOf(record(), record(duration = 480, toComp = 60)),
                siteAttendance = listOf(siteAtt(), siteAtt(dayStatus = "REST", work = 0, ot = 0)),
                settings = com.mdot.app.core.sync.SettingsDto(salary = SalaryConfig()),
            )
        )
    }

    @Test
    fun `REST带工时被拒`() {
        try {
            validateBackupInvariants(
                BackupFile(
                    siteAttendance = listOf(siteAtt(dayStatus = "REST", work = 480)),
                    settings = com.mdot.app.core.sync.SettingsDto(salary = SalaryConfig()),
                )
            )
            org.junit.Assert.fail("REST 带工时应被拒")
        } catch (e: IllegalArgumentException) {
            assert(e.message!!.contains("休息"))
        }
    }

    @Test
    fun `转调休超界被拒`() {
        try {
            validateBackupInvariants(
                BackupFile(
                    records = listOf(record(duration = 120, toComp = 180)),
                    settings = com.mdot.app.core.sync.SettingsDto(salary = SalaryConfig()),
                )
            )
            org.junit.Assert.fail("toComp > duration 应被拒")
        } catch (e: IllegalArgumentException) {
            assert(e.message!!.contains("转调休"))
        }
    }

    @Test
    fun `非法时长与未知出勤状态被拒`() {
        val cases = listOf(
            BackupFile(records = listOf(record(duration = 0))),
            BackupFile(records = listOf(record(duration = 1441))),
            BackupFile(siteAttendance = listOf(siteAtt(work = -1))),
            BackupFile(siteAttendance = listOf(siteAtt(dayStatus = "SLEEP"))),
        )
        cases.forEach { f ->
            try {
                validateBackupInvariants(f)
                org.junit.Assert.fail("非法数据应被拒：$f")
            } catch (_: IllegalArgumentException) {
                // 预期
            }
        }
    }

    @Test
    fun `非正数借支与负数包工被拒`() {
        val badAdvance = BackupFile(
            siteAdvances = listOf(
                com.mdot.app.core.sync.SiteAdvanceDto(
                    projectId = 1, date = "2026-09-14", amountCents = 0, createdAt = 0, updatedAt = 0,
                )
            ),
        )
        val badPiece = BackupFile(
            sitePieceWorks = listOf(
                com.mdot.app.core.sync.SitePieceWorkDto(
                    projectId = 1, date = "2026-09-14", amountCents = -100, createdAt = 0, updatedAt = 0,
                )
            ),
        )
        listOf(badAdvance, badPiece).forEach { f ->
            try {
                validateBackupInvariants(f)
                org.junit.Assert.fail("应被拒：$f")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}
