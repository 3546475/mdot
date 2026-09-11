package com.mdot.app.core.sync

import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.WorkSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 备份恢复对未知 workSystem 的防御单测（10 文档 §3.3）：
 * 未知制度值必须显式拒绝并提示升级，禁止静默入库（否则读取时 valueOf 崩溃）。
 */
class BackupRestoreGuardTest {

    private fun record(workSystem: String) = RecordDto(
        date = "2026-09-06",
        type = "OT",
        durationMinutes = 480,
        createdAt = 0L,
        updatedAt = 0L,
        workSystem = workSystem,
    )

    private fun file(vararg systems: String) = BackupFile(
        records = systems.map { record(it) },
        settings = SettingsDto(salary = SalaryConfig()),
    )

    @Test
    fun `已知三种制度与空记录放行`() {
        // 不抛异常即通过
        validateBackupWorkSystems(file("STANDARD"))
        validateBackupWorkSystems(file("HOURLY"))
        validateBackupWorkSystems(file("COMPREHENSIVE"))
        validateBackupWorkSystems(file("STANDARD", "HOURLY", "COMPREHENSIVE"))
        validateBackupWorkSystems(BackupFile(records = emptyList()))
    }

    @Test
    fun `未知制度显式拒绝`() {
        try {
            // SITE 已于工地记工批次成为合法制度（v0.6.x 落地）；未知值改用 FUTURE 表达
            validateBackupWorkSystems(file("STANDARD", "FUTURE"))
            fail("未知 workSystem 应被拒绝")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("工时制度"))
            assertTrue(e.message!!.contains("升级"))
        }
    }

    @Test
    fun `拒绝信息列出全部未知值且不重复`() {
        try {
            validateBackupWorkSystems(file("OLD", "OLD", "FUTURE"))
            fail("未知 workSystem 应被拒绝")
        } catch (e: IllegalArgumentException) {
            val msg = e.message!!
            assertTrue(msg.contains("OLD"))
            assertTrue(msg.contains("FUTURE"))
            assertEquals(1, msg.split("OLD").size - 1)
        }
    }

    @Test
    fun `旧备份缺省STANDARD放行`() {
        // RecordDto.workSystem 默认 "STANDARD"（旧版包无该字段时反序列化兜底）
        val legacy = BackupFile(records = listOf(RecordDto(date = "2026-08-01", type = "OT", durationMinutes = 60, createdAt = 0L, updatedAt = 0L)))
        assertEquals("STANDARD", legacy.records.single().workSystem)
        validateBackupWorkSystems(legacy)
    }

    @Test
    fun `当前版本自身导出的COMPREHENSIVE可回读`() {
        val exported = RecordDto(
            date = "2026-09-06",
            type = "OT",
            durationMinutes = 720,
            createdAt = 0L,
            updatedAt = 0L,
            workSystem = WorkSystem.COMPREHENSIVE.name,
        )
        validateBackupWorkSystems(BackupFile(records = listOf(exported)))
    }
}
