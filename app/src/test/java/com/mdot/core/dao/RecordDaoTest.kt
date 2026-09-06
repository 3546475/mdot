package com.mdot.app.core.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mdot.app.core.database.AppDatabase
import com.mdot.app.core.database.CompAdjustmentEntity
import com.mdot.app.core.database.DailyRecordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * DAO 层测试（Robolectric + 纯内存 Room，不带 SQLCipher）：
 * 唯一约束 (date,type,work_system)、调休余额口径 SQL、预置班次种入、制度隔离。
 */
@RunWith(RobolectricTestRunner::class)
class RecordDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(AppDatabase.SEED_CALLBACK)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun record(
        date: String,
        type: String = "OT",
        minutes: Int = 120,
        toComp: Int = 0,
        leaveType: String? = null,
        workSystem: String = "STANDARD",
    ) = DailyRecordEntity(
        date = LocalDate.parse(date),
        type = type,
        workSystem = workSystem,
        shiftId = null,
        shiftName = "白班",
        durationMinutes = minutes,
        tier = if (type == "OT") "WEEKDAY" else null,
        tierSource = if (type == "OT") "AUTO" else null,
        toCompMinutes = toComp,
        leaveType = leaveType,
        note = null,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `预置班次随建库种入 6 条`() = runBlocking {
        assertEquals(6, db.shiftDao().getAll().size)
    }

    @Test
    fun `同日同类型同制度重复 insert 抛异常且不落库`() = runBlocking {
        val dao = db.recordDao()
        dao.insert(record("2026-08-30"))
        val second = runCatching { dao.insert(record("2026-08-30", minutes = 90)) }
        assertTrue(second.isFailure)
        assertEquals(1, dao.count())
    }

    @Test
    fun `同一天加班与请假可共存`() = runBlocking {
        val dao = db.recordDao()
        dao.insert(record("2026-08-30"))
        dao.insert(record("2026-08-30", type = "LEAVE", minutes = 240, leaveType = "PERSONAL"))
        assertEquals(2, dao.count())
    }

    @Test
    fun `同一天同类型不同制度可共存`() = runBlocking {
        val dao = db.recordDao()
        dao.insert(record("2026-08-30", workSystem = "STANDARD"))
        dao.insert(record("2026-08-30", workSystem = "HOURLY", minutes = 180))
        assertEquals(2, dao.count())
        val std = dao.getByDateAndType(LocalDate.parse("2026-08-30"), "OT", "STANDARD")!!
        val hourly = dao.getByDateAndType(LocalDate.parse("2026-08-30"), "OT", "HOURLY")!!
        assertEquals(120, std.durationMinutes)
        assertEquals(180, hourly.durationMinutes)
    }

    @Test
    fun `observeRange 按制度过滤`() = runBlocking {
        val dao = db.recordDao()
        dao.insert(record("2026-08-30", workSystem = "STANDARD", minutes = 120))
        dao.insert(record("2026-08-30", workSystem = "HOURLY", minutes = 180))
        val stdRows = dao.observeRange(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"), "STANDARD").first()
        val hourlyRows = dao.observeRange(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"), "HOURLY").first()
        assertEquals(1, stdRows.size)
        assertEquals(120, stdRows.first().durationMinutes)
        assertEquals(1, hourlyRows.size)
        assertEquals(180, hourlyRows.first().durationMinutes)
    }

    @Test
    fun `upsert 语义 按 id 更新不产生第二行`() = runBlocking {
        val dao = db.recordDao()
        dao.insert(record("2026-08-30", minutes = 120))
        val existing = dao.getByDateAndType(LocalDate.parse("2026-08-30"), "OT", "STANDARD")!!
        dao.update(record("2026-08-30", minutes = 480).copy(id = existing.id, updatedAt = 1))
        val rows = dao.getAll()
        assertEquals(1, rows.size)
        assertEquals(480, rows.first().durationMinutes)
    }

    @Test
    fun `调休余额口径 转调休减调休请假加手动调整`() = runBlocking {
        val dao = db.recordDao()
        dao.insert(record("2026-08-10", minutes = 150, toComp = 60))            // +60
        dao.insert(record("2026-08-12", type = "LEAVE", minutes = 60, leaveType = "COMP")) // -60
        db.compAdjustmentDao().insert(
            CompAdjustmentEntity(
                date = LocalDate.parse("2026-08-15"),
                deltaMinutes = -30,
                note = null,
                createdAt = 0,
            )
        )
        dao.insert(record("2026-08-20", minutes = 60, toComp = 30))             // +30
        assertEquals(0, dao.observeCompBalance().first())
    }

    @Test
    fun `调休余额只统计标准工时制度`() = runBlocking {
        val dao = db.recordDao()
        dao.insert(record("2026-08-10", minutes = 150, toComp = 60, workSystem = "STANDARD")) // +60
        dao.insert(record("2026-08-11", minutes = 150, toComp = 120, workSystem = "HOURLY"))  // 不计入
        assertEquals(60, dao.observeCompBalance().first())
    }

    @Test
    fun `deleteAll 清空后 count 归零`() = runBlocking {
        val dao = db.recordDao()
        dao.insert(record("2026-08-10"))
        dao.insert(record("2026-08-11"))
        dao.deleteAll()
        assertEquals(0, dao.count())
    }
}
