package com.mdot.app.core.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mdot.app.core.database.AppDatabase
import com.mdot.app.core.database.SiteAttendanceEntity
import com.mdot.app.core.database.SiteSettlementEntity
import com.mdot.app.core.util.AppResult
import com.mdot.app.domain.model.AdvancePurpose
import com.mdot.app.domain.model.SiteAttendance
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 工地记工结算不变量回归（13 文档 B3-01/02/13/14 修复防线）：
 * - 已结算出勤不可编辑（锁读库自证，不信任入参 settlementId）
 * - 借支/包工日期不得早于最近结算截止日（防永不结算的黑洞账）
 * - 未来日期拒绝
 * - preview→confirm 窗口内新增记录时 confirm 拒绝（乐观校验）
 * 纯内存 Room（无 SQLCipher），SettingsDataSource 用假 DataStore。
 */
@RunWith(RobolectricTestRunner::class)
class SiteRepositorySettlementGuardTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: SiteRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val settings = com.mdot.app.core.datastore.SettingsDataSource(
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                scope = kotlinx.coroutines.CoroutineScope(
                    kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()),
                produceFile = { java.io.File(context.cacheDir, "t_${System.nanoTime()}.preferences_pb") },
            )
        )
        repo = SiteRepository(
            db = db,
            projectDao = db.siteProjectDao(),
            attDao = db.siteAttendanceDao(),
            advanceDao = db.siteAdvanceDao(),
            pieceDao = db.sitePieceWorkDao(),
            settlementDao = db.siteSettlementDao(),
            settings = settings,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedProject(): Long =
        db.siteProjectDao().insert(
            com.mdot.app.core.database.SiteProjectEntity(
                name = "测试项目", sort = 0, createdAt = 0, updatedAt = 0,
            )
        )

    private fun attDraft(pid: Long, date: LocalDate, workMinutes: Int = 480) = SiteAttendance(
        projectId = pid, date = date.toString(), dayStatus = "WORK",
        workMinutes = workMinutes, otMinutes = 0,
        rateCents = 26_000, baseMinutes = 480, otMode = "BY_DAY",
        otBaseMinutes = 360, otHourlyCents = 0,
        createdAt = 0, updatedAt = 0,
    )

    @Test
    fun `已结算出勤不可编辑`() = runBlocking {
        val pid = seedProject()
        val today = LocalDate.now()
        assertTrue(repo.saveAttendance(attDraft(pid, today)) is AppResult.Success)

        // 结清全部区间（记录被锁定）
        val preview = repo.previewSettlement(pid, today, today)
        assertTrue(repo.confirmSettlement(preview, null) is AppResult.Success)

        // settlementId=null 的草稿再保存：必须被拒（旧实现里守卫形同虚设）
        val r = repo.saveAttendance(attDraft(pid, today, workMinutes = 600))
        assertTrue("已结算日出勤编辑应被拒", r is AppResult.Failure)
    }

    @Test
    fun `借支日期早于结算截止日被拒`() = runBlocking {
        val pid = seedProject()
        val today = LocalDate.now()
        assertTrue(repo.saveAttendance(attDraft(pid, today)) is AppResult.Success)
        val preview = repo.previewSettlement(pid, today, today)
        assertTrue(repo.confirmSettlement(preview, null) is AppResult.Success)

        val r = repo.saveAdvance(pid, today, 100_000, AdvancePurpose.LIVING, null)
        assertTrue("结算截止日当天新增借支应被拒", r is AppResult.Failure)
    }

    @Test
    fun `结算截止日次日新增借支放行`() = runBlocking {
        val pid = seedProject()
        val yesterday = LocalDate.now().minusDays(1)
        assertTrue(repo.saveAttendance(attDraft(pid, yesterday)) is AppResult.Success)
        val preview = repo.previewSettlement(pid, yesterday, yesterday)
        assertTrue(repo.confirmSettlement(preview, null) is AppResult.Success)

        // 结算只锁昨天；今天借支不受影响（今天的记录不在锁定区间，直接可借）
        val r = repo.saveAdvance(pid, LocalDate.now(), 100_000, AdvancePurpose.LIVING, null)
        assertTrue("结算后次日新增借支应放行", r is AppResult.Success)
        // 且计入待结：借支合计应包含这笔
        assertEquals(100_000, repo.summarizeUnsettled(pid).advanceTotalCents)
    }

    @Test
    fun `未来日期的出勤与借支被拒`() = runBlocking {
        val pid = seedProject()
        val tomorrow = LocalDate.now().plusDays(1)
        assertTrue(repo.saveAttendance(attDraft(pid, tomorrow)) is AppResult.Failure)
        assertTrue(
            repo.saveAdvance(pid, tomorrow, 100, AdvancePurpose.OTHER, null) is AppResult.Failure,
        )
        assertTrue(
            repo.savePieceWork(pid, tomorrow, "砌墙", "m²", 1000, 4500, 0, null) is AppResult.Failure,
        )
    }

    @Test
    fun `preview后窗口内新增记录时confirm拒绝`() = runBlocking {
        val pid = seedProject()
        val today = LocalDate.now()
        assertTrue(repo.saveAttendance(attDraft(pid, today)) is AppResult.Success)
        val preview = repo.previewSettlement(pid, today, today)

        // 用户停在确认页时补记一笔（同区间新借支）
        assertTrue(repo.saveAdvance(pid, today, 50_000, AdvancePurpose.LIVING, null) is AppResult.Success)

        // confirm 应识别区间已变化而拒绝（旧实现会带病落单：锁了不在快照里的记录）
        val r = repo.confirmSettlement(preview, null)
        assertTrue("区间变化后 confirm 应拒绝", r is AppResult.Failure)

        // 库未被污染：无结算单、记录未锁定
        assertEquals(0, db.siteSettlementDao().getAll().size)
        assertTrue(db.siteAttendanceDao().getUnsettledRange(pid, today, today).isNotEmpty())
    }

    @Test
    fun `无变化的confirm照常成功且锁定区间`() = runBlocking {
        val pid = seedProject()
        val today = LocalDate.now()
        assertTrue(repo.saveAttendance(attDraft(pid, today)) is AppResult.Success)
        val preview = repo.previewSettlement(pid, today, today)
        val r = repo.confirmSettlement(preview, null)
        assertTrue(r is AppResult.Success)
        // 区间锁定：出勤带 settlementId
        val atts = db.siteAttendanceDao().getByDate(pid, today)
        atts?.let { assertEquals(it.settlementId, (r as AppResult.Success).data) } ?: fail("出勤应存在")
        // 直接读 observeRange 验证锁
        val locked = db.siteAttendanceDao().getAll().first()
        assertTrue(locked.settlementId != null)
    }
}
