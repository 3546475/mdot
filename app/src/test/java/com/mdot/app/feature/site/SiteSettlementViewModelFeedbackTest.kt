package com.mdot.app.feature.site

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mdot.app.core.database.AppDatabase
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.repository.SiteRepository
import com.mdot.app.core.util.AppResult
import com.mdot.app.domain.model.AdvancePurpose
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.SiteAttendance
import com.mdot.app.domain.model.WorkSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 结算页写操作失败反馈回归（docs/14 §3.1 缺口 #1 / docs/15 T0-1）：
 * 旧版 VM 五处 message 均无 UI 渲染点，且 confirmSettlement 的失败（乐观校验）分支完全静默。
 * 本测试锁定 VM→UI 的契约：失败必置 message、成功必无 message、confirm 失败同时作废 preview。
 *
 * Robolectric 提供 Room，Main 用 UnconfinedTestDispatcher 让 viewModelScope 立即执行；
 * 断言一律轮询条件（DataStore/Room 在真实 IO 线程发射），不用裸 sleep 定长等待。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SiteSettlementViewModelFeedbackTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: SiteRepository
    private lateinit var settings: SettingsDataSource
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(mainDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsDataSource(
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
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
        Dispatchers.resetMain()
        db.close()
    }

    private suspend fun seedProject(): Long =
        db.siteProjectDao().insert(
            com.mdot.app.core.database.SiteProjectEntity(
                name = "测试项目", sort = 0, createdAt = 0, updatedAt = 0,
            )
        )

    /** 切到工地制度 + 当前项目指针后构造 VM（init 里的 salaryFlow collect 会驱动首次 refresh） */
    private suspend fun newVmOnSite(pid: Long): SiteSettlementViewModel {
        settings.setSalary(SalaryConfig(workSystem = WorkSystem.SITE, siteCurrentProjectId = pid))
        return SiteSettlementViewModel(repo, settings)
    }

    private fun attDraft(pid: Long, date: LocalDate) = SiteAttendance(
        projectId = pid, date = date.toString(), dayStatus = "WORK",
        workMinutes = 480, otMinutes = 0,
        rateCents = 26_000, baseMinutes = 480, otMode = "BY_DAY",
        otBaseMinutes = 360, otHourlyCents = 0,
        createdAt = 0, updatedAt = 0,
    )

    /** 轮询直到条件成立（真实 IO 发射需要让出线程），超时保留断言信息便于定位 */
    private suspend fun awaitUntil(
        what: String,
        vm: SiteSettlementViewModel,
        cond: (SiteSettlementUi) -> Boolean,
    ) {
        var tries = 0
        while (!cond(vm.state.value) && tries < 100) {
            delay(20)
            tries++
        }
        assertTrue("等待超时：$what（最后状态 ${vm.state.value}）", cond(vm.state.value))
    }

    private suspend fun awaitLoaded(vm: SiteSettlementViewModel) =
        awaitUntil("loading 应结束", vm) { !it.loading }

    @Test
    fun `confirm乐观校验失败时置message并作废preview`() = runBlocking {
        val pid = seedProject()
        val today = LocalDate.now()
        assertTrue(repo.saveAttendance(attDraft(pid, today)) is AppResult.Success)
        val vm = newVmOnSite(pid)
        awaitLoaded(vm)

        vm.buildPreview()
        awaitUntil("预览应已构建", vm) { it.preview != null }

        // 用户停在确认页时补记一笔 → confirm 应识别区间变化而拒绝
        assertTrue(
            repo.saveAdvance(pid, today, 50_000, AdvancePurpose.LIVING, null) is AppResult.Success,
        )
        vm.confirmSettlement()
        awaitUntil("失败应置 message", vm) { it.message != null }

        val s = vm.state.value
        assertEquals("区间数据已变化，请刷新结算预览后重试", s.message)
        assertNull("confirm 失败后旧预览应作废（否则重复点确认）", s.preview)
        assertEquals("失败不得污染库", 0, db.siteSettlementDao().getAll().size)
    }

    @Test
    fun `confirm成功时静默且落结算单`() = runBlocking {
        val pid = seedProject()
        val today = LocalDate.now()
        assertTrue(repo.saveAttendance(attDraft(pid, today)) is AppResult.Success)
        val vm = newVmOnSite(pid)
        awaitLoaded(vm)

        vm.buildPreview()
        awaitUntil("预览应已构建", vm) { it.preview != null }
        vm.confirmSettlement()
        awaitUntil("历史结算单应出现", vm) { it.settlements.isNotEmpty() }

        val s = vm.state.value
        assertNull("成功不应有 message", s.message)
        assertNull(s.preview)
        assertEquals(1, db.siteSettlementDao().getAll().size)
    }

    @Test
    fun `删除已结算借支失败时置message且成功后清除`() = runBlocking {
        val pid = seedProject()
        val today = LocalDate.now()
        assertTrue(repo.saveAttendance(attDraft(pid, today)) is AppResult.Success)
        assertTrue(
            repo.saveAdvance(pid, today, 30_000, AdvancePurpose.LIVING, null) is AppResult.Success,
        )
        val vm = newVmOnSite(pid)
        awaitLoaded(vm)

        // 先结清：借支随区间锁定
        vm.buildPreview()
        awaitUntil("预览应已构建", vm) { it.preview != null }
        vm.confirmSettlement()
        awaitUntil("结清应落单", vm) { it.settlements.isNotEmpty() }
        val adv = db.siteAdvanceDao().getAllForProject(pid).first()

        // 已结算的借支删不掉 → message 必须出现（T0-1 前：写了 message 但 UI 无渲染点）
        vm.deleteAdvance(adv.id)
        awaitUntil("删除已结算借支应置 message", vm) { it.message != null }
        assertEquals("该借支已结算，请先撤销结算单", vm.state.value.message)
        assertTrue("记录应仍在库", db.siteAdvanceDao().getAllForProject(pid).isNotEmpty())

        // 撤销结算后同一条可删：成功路径不得留 message
        vm.clearMessage()
        val settle = db.siteSettlementDao().getAll().first()
        vm.revertSettlement(settle.id)
        awaitUntil("撤销后结算单应清空", vm) { it.settlements.isEmpty() }
        assertNull("clearMessage 生效", vm.state.value.message)

        // 撤销后借支回到未结算流水（refresh 已带出），删除成功后从流水消失
        awaitUntil("撤销后借支应回到未结算流水", vm) { it.advances.any { a -> a.id == adv.id } }
        vm.deleteAdvance(adv.id)
        awaitUntil("借支应被删除", vm) { it.advances.none { a -> a.id == adv.id } }
        assertNull("成功删除不应有 message", vm.state.value.message)
    }
}
