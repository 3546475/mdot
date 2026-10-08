package com.mdot.app.feature.stats

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mdot.app.core.database.AppDatabase
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.network.CleartextGate
import com.mdot.app.core.network.JsonFetcher
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.domain.model.PayGroup
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

/**
 * 记月**写盘路径**回归（2026-10-07 用户报的严重数据丢失）。
 *
 * 症状：① 已记录的数据在「增加项目」后清 0；② 新增的项目（非默认项）在修改任意其它项目数据时消失。
 *
 * 根因：[PayMonthViewModel] 的 `sheet` 是 `stateIn(WhileSubscribed)`，而页面只订阅
 * `displaySheet`/`attendance`/`collapsed`、**从不订阅 `sheet`** ⇒ 无订阅者 ⇒ 上游 DataStore 流
 * 永不启动 ⇒ `sheet.value` 恒为种子值 `PayMonthSheet.default()`（全 0、无用户行）。
 * 写路径（增/改/删/一键恢复/导入快照）却拿它当底稿，于是每次写盘都把整张单子覆盖成
 * 「出厂默认 + 那一处改动」，其余金额与用户行当场蒸发。UI 读 `displaySheet`（每次从存盘单据重算），
 * 所以看上去「有数据」，把写盘侧持续丢数的问题盖住了。
 *
 * 修复：写路径改为经 [PayMonthViewModel.freshSheet] 现取存盘单据，并串行化（sheetEditMutex）。
 *
 * ⚠️ 本测试**刻意不订阅 `sheet`**——那正是页面现状（bug 的成立前提）。
 * 一旦订阅上，`sheet.value` 会变新鲜，这个 bug 反而测不出来（订阅会掩盖缺陷）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PayMonthWritePathTest {

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsDataSource
    private val mainDispatcher = UnconfinedTestDispatcher()

    private val monthKey = YearMonth.now().toString()

    @Before
    fun setup() {
        Dispatchers.setMain(mainDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsDataSource(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { java.io.File(context.cacheDir, "pm_${System.nanoTime()}.preferences_pb") },
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    /** 组装真依赖：写路径只用 [SettingsDataSource]，其余两个仅为满足构造签名 */
    private fun newVm(): PayMonthViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = RecordRepository(db.recordDao(), db.compAdjustmentDao(), settings)
        val holidays = HolidayRepository(
            context = context,
            holidayDao = db.holidayDao(),
            settings = settings,
            jsonFetcher = JsonFetcher(OkHttpClient(), Dispatchers.IO),
            cleartextGate = CleartextGate(),
            appScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        )
        return PayMonthViewModel(settings, repo, holidays)
    }

    /** 存盘真值（读取侧会补出厂固定行） */
    private suspend fun stored(): PayMonthSheet =
        settings.payMonthFlow(monthKey).first() ?: PayMonthSheet.default()

    /**
     * 轮询等 DataStore 落盘（真实 IO 发射要让出线程）。
     * 预算 5s：CI（--no-daemon、冷 Robolectric）远慢于本地（沿用 docs/16 坑 15 的教训）。
     */
    private suspend fun awaitStored(what: String, cond: (PayMonthSheet) -> Boolean) {
        var tries = 0
        var last = PayMonthSheet.default()
        while (tries < 250) {
            last = stored()
            if (cond(last)) return
            delay(20)
            tries++
        }
        assertTrue("等待超时：$what（最后存盘 basic=${last.basic.map { it.id to it.amountCents }} " +
            "subsidy=${last.subsidy.map { it.id to it.amountCents }} " +
            "deduction=${last.deduction.map { it.id to it.amountCents }} " +
            "other=${last.other.map { it.id to it.amountCents }}）", cond(last))
    }

    private fun PayMonthSheet.row(group: PayGroup, id: Long): PayMonthItem? = when (group) {
        PayGroup.BASIC -> basic
        PayGroup.SUBSIDY -> subsidy
        PayGroup.DEDUCTION -> deduction
        PayGroup.OTHER -> other
    }.firstOrNull { it.id == id }

    // ---- 症状 ①：新增项目后，已记录数据不得清 0 ----

    @Test
    fun `新增项目后已记录金额不清零`() = runBlocking {
        val vm = newVm()

        // 先在基本工资/事假/社保三处记下金额
        vm.saveItem(PayGroup.BASIC, PayMonthItem(PayMonthSheet.BASE_ROW_ID, "基本工资", 500_000L, builtin = true))
        vm.saveItem(PayGroup.DEDUCTION, PayMonthItem(6L, "事假", 12_300L, builtin = true))
        vm.saveItem(PayGroup.OTHER, PayMonthItem(PayMonthSheet.SOCIAL_ROW_ID, "社保", 52_500L, builtin = true))
        awaitStored("三处金额应落盘") {
            it.row(PayGroup.BASIC, PayMonthSheet.BASE_ROW_ID)?.amountCents == 500_000L &&
                it.row(PayGroup.DEDUCTION, 6L)?.amountCents == 12_300L &&
                it.row(PayGroup.OTHER, PayMonthSheet.SOCIAL_ROW_ID)?.amountCents == 52_500L
        }

        // 再新增一个补贴项（症状①的触发动作）
        vm.addItem(PayGroup.SUBSIDY, "夜班补贴", 30_000L)
        awaitStored("新增项应出现") { it.subsidy.any { i -> i.name == "夜班补贴" } }

        // 关键断言：之前记的金额一条都不能被这次写盘抹掉
        val after = stored()
        assertEquals(
            "新增项目后基本工资被清 0（写盘用了过期底稿）",
            500_000L, after.row(PayGroup.BASIC, PayMonthSheet.BASE_ROW_ID)?.amountCents,
        )
        assertEquals(
            "新增项目后事假被清 0",
            12_300L, after.row(PayGroup.DEDUCTION, 6L)?.amountCents,
        )
        assertEquals(
            "新增项目后社保被清 0",
            52_500L, after.row(PayGroup.OTHER, PayMonthSheet.SOCIAL_ROW_ID)?.amountCents,
        )
        assertEquals(
            "新增项目本身的金额应正确",
            30_000L, after.subsidy.first { it.name == "夜班补贴" }.amountCents,
        )
    }

    // ---- 症状 ②：改任意其它项时，用户新增项不得消失 ----

    @Test
    fun `修改其它项目时用户新增项不消失`() = runBlocking {
        val vm = newVm()

        vm.addItem(PayGroup.SUBSIDY, "夜班补贴", 30_000L)
        vm.addItem(PayGroup.SUBSIDY, "交通补贴", 15_000L)
        awaitStored("两个新增项都应落盘") {
            it.subsidy.count { s -> s.name == "夜班补贴" || s.name == "交通补贴" } == 2
        }

        // 改一个**出厂**行（症状②的触发动作）
        vm.saveItem(PayGroup.BASIC, PayMonthItem(PayMonthSheet.BASE_ROW_ID, "基本工资", 800_000L, builtin = true))
        awaitStored("基本工资应落盘") {
            it.row(PayGroup.BASIC, PayMonthSheet.BASE_ROW_ID)?.amountCents == 800_000L
        }

        val after = stored()
        assertNotNull("改基本工资把「夜班补贴」冲掉了", after.subsidy.firstOrNull { it.name == "夜班补贴" })
        assertNotNull("改基本工资把「交通补贴」冲掉了", after.subsidy.firstOrNull { it.name == "交通补贴" })
        assertEquals(30_000L, after.subsidy.first { it.name == "夜班补贴" }.amountCents)
        assertEquals(15_000L, after.subsidy.first { it.name == "交通补贴" }.amountCents)

        // 再改一次**另一个组**，确认不是只对单组成立
        vm.saveItem(PayGroup.DEDUCTION, PayMonthItem(6L, "事假", 9_900L, builtin = true))
        awaitStored("事假应落盘") { it.row(PayGroup.DEDUCTION, 6L)?.amountCents == 9_900L }

        val after2 = stored()
        // 只数**用户新增行**（`!builtin`）：出厂的「其它补贴」也以「补贴」结尾，按名字数会多算一行。
        // 断言用集合：本条要验的是「行还在」，不是行的排序（中文排序与书写序不一致）
        assertEquals(
            "跨组改动也必须保住新增项",
            setOf("夜班补贴", "交通补贴"),
            after2.subsidy.filter { !it.builtin }.map { it.name }.toSet(),
        )
        assertEquals(800_000L, after2.row(PayGroup.BASIC, PayMonthSheet.BASE_ROW_ID)?.amountCents)
    }

    // ---- 同族隐患：连加两项的 id 不得撞车 ----

    @Test
    fun `连续新增的项目 id 不撞车`() = runBlocking {
        val vm = newVm()

        vm.addItem(PayGroup.SUBSIDY, "夜班补贴", 30_000L)
        awaitStored("第一个新增项落盘") { it.subsidy.any { s -> s.name == "夜班补贴" } }
        vm.addItem(PayGroup.SUBSIDY, "交通补贴", 15_000L)
        awaitStored("第二个新增项落盘") { it.subsidy.any { s -> s.name == "交通补贴" } }

        val userRows = stored().subsidy.filter { !it.builtin }
        assertEquals("应有两个用户新增行", 2, userRows.size)
        assertEquals(
            "两个新增行撞了同一个 id（saveItem/removeItem 按 id 匹配会改错删错行）",
            userRows.size, userRows.map { it.id }.distinct().size,
        )

        // 按 id 改其中一个，另一个必须原样不动
        val target = userRows.first { it.name == "夜班补贴" }
        vm.saveItem(PayGroup.SUBSIDY, target.copy(amountCents = 88_800L))
        awaitStored("按 id 改金额应落盘") {
            it.subsidy.firstOrNull { s -> s.id == target.id }?.amountCents == 88_800L
        }
        val after = stored()
        assertEquals("改 A 不应动到 B", 15_000L, after.subsidy.first { it.name == "交通补贴" }.amountCents)
        assertEquals(88_800L, after.subsidy.first { it.name == "夜班补贴" }.amountCents)
    }

    // ---- 删除只删目标行，不牵连别的数据 ----

    @Test
    fun `删除新增项只删该行且不动其它数据`() = runBlocking {
        val vm = newVm()

        vm.saveItem(PayGroup.BASIC, PayMonthItem(PayMonthSheet.BASE_ROW_ID, "基本工资", 500_000L, builtin = true))
        vm.addItem(PayGroup.SUBSIDY, "夜班补贴", 30_000L)
        vm.addItem(PayGroup.SUBSIDY, "交通补贴", 15_000L)
        awaitStored("前置数据落盘") { it.subsidy.count { s -> !s.builtin } == 2 }

        val target = stored().subsidy.first { it.name == "夜班补贴" }
        vm.removeItem(PayGroup.SUBSIDY, target.id)
        awaitStored("目标行应被删") { it.subsidy.none { s -> s.id == target.id } }

        val after = stored()
        assertEquals("同组另一新增行不该被牵连", 1, after.subsidy.count { !it.builtin })
        assertEquals(15_000L, after.subsidy.first { it.name == "交通补贴" }.amountCents)
        assertEquals(
            "删除操作把出厂行金额抹了",
            500_000L, after.row(PayGroup.BASIC, PayMonthSheet.BASE_ROW_ID)?.amountCents,
        )
    }

    // ---- 撤销「导入上月」的快照也必须是真存盘值 ----

    @Test
    fun `导入上月的撤销快照是真存盘值而非出厂默认`() = runBlocking {
        val vm = newVm()

        // 本月先记下用户数据
        vm.saveItem(PayGroup.BASIC, PayMonthItem(PayMonthSheet.BASE_ROW_ID, "基本工资", 500_000L, builtin = true))
        vm.addItem(PayGroup.SUBSIDY, "夜班补贴", 30_000L)
        awaitStored("前置数据落盘") {
            it.row(PayGroup.BASIC, PayMonthSheet.BASE_ROW_ID)?.amountCents == 500_000L &&
                it.subsidy.any { s -> s.name == "夜班补贴" }
        }

        vm.importPrevMonth()
        vm.undoImport()
        // 导入+撤销都落盘后，本月应回到导入前的样子（数据不能被快照的默认值吞掉）
        awaitStored("撤销后应恢复本月原值") {
            it.row(PayGroup.BASIC, PayMonthSheet.BASE_ROW_ID)?.amountCents == 500_000L &&
                it.subsidy.any { s -> s.name == "夜班补贴" }
        }
    }

    // ---- 添加条目（2026-10-09 改为"只选条目、不带金额"）----

    @Test
    fun `添加不带金额 - 新增行金额为 0 等用户逐行填`() = runBlocking {
        val vm = newVm()

        vm.addItems(PayGroup.SUBSIDY, listOf("餐补", "交通补贴"))
        awaitStored("两行都应落盘") { it.subsidy.count { s -> !s.builtin } == 2 }

        stored().subsidy.filter { !it.builtin }.forEach {
            assertEquals("添加时不该带金额（弹窗已不收集金额）", 0L, it.amountCents)
        }
    }

    @Test
    fun `添加与已有行同名时不加出重复行`() = runBlocking {
        val vm = newVm()

        // 「全勤奖」是补贴组的出厂固定行，而预设里一度也有它 —— 曾会再加一行同名的出来
        vm.addItems(PayGroup.SUBSIDY, listOf("全勤奖"))
        delay(200)

        assertEquals(
            "与已有行同名必须跳过，否则会加出重复的「全勤奖」",
            1, stored().subsidy.count { it.name == "全勤奖" },
        )

        // 自己先加一行，再用同名加一次，同样不该重复
        vm.addItems(PayGroup.SUBSIDY, listOf("餐补"))
        awaitStored("餐补应落盘") { it.subsidy.any { s -> s.name == "餐补" } }
        vm.addItems(PayGroup.SUBSIDY, listOf("餐补"))
        delay(200)
        assertEquals("重复添加同名项应被忽略", 1, stored().subsidy.count { it.name == "餐补" })
    }

    @Test
    fun `添加多行时 传入名单内部重复也只加一行`() = runBlocking {
        val vm = newVm()

        vm.addItems(PayGroup.SUBSIDY, listOf("餐补", "餐补", "  ", "夜班补贴"))
        awaitStored("应只加两行") { it.subsidy.count { s -> !s.builtin } == 2 }
        assertEquals(1, stored().subsidy.count { it.name == "餐补" })
    }
}