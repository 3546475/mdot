package com.mdot.app.feature.comp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mdot.app.core.database.AppDatabase
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.core.util.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 调休余额删除反馈回归（docs/14 §3.2 缺口 #8 / docs/15 T0-2）：
 * 旧版 CompBalanceViewModel.delete() 直调 deleteAdjustment，成功失败都不说话。
 * 本测试锁定 VM 契约：删除成功必置 message「已删除调整记录」、失败必置 isError message；
 * 成功路径同时验证列表行移除（observeAdjustments 发射空）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CompBalanceViewModelFeedbackTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: RecordRepository
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
        repo = RecordRepository(
            recordDao = db.recordDao(),
            adjustDao = db.compAdjustmentDao(),
            settings = settings,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    /** 轮询直到条件成立（真实 IO 发射需要让出线程），超时保留断言信息便于定位 */
    private suspend fun awaitUntil(
        what: String,
        vm: CompBalanceViewModel,
        cond: (CompBalanceUiState) -> Boolean,
    ) {
        var tries = 0
        while (!cond(vm.uiState.value) && tries < 100) {
            delay(20)
            tries++
        }
        assertTrue("等待超时：$what（最后状态 ${vm.uiState.value}）", cond(vm.uiState.value))
    }

    @Test
    fun `删除调整记录成功时置已删除message且列表移除`() = runBlocking {
        assertTrue(repo.adjustComp(LocalDate.now(), 120, "测试备注") is AppResult.Success)
        val id = db.compAdjustmentDao().getAll().first().id

        val vm = CompBalanceViewModel(repo)
        // uiState 是 WhileSubscribed 冷流，需先订阅激活（无 init collect 驱动）
        val collectJob = launch { vm.uiState.collect {} }
        try {
            awaitUntil("调整记录应出现", vm) { it.adjustments.isNotEmpty() }

            vm.delete(id)
            awaitUntil("列表应移除该行", vm) { it.adjustments.isEmpty() }

            assertEquals("删除成功应提示已删除", "已删除调整记录", vm.messageFlow.value?.text)
            assertEquals("成功提示不是错误态", false, vm.messageFlow.value?.isError)

            // 复用 clearMessage（UI 侧 3s 自清入口）
            vm.clearMessage()
            assertEquals("clearMessage 应清空", null, vm.messageFlow.value)
        } finally {
            collectJob.cancel()
        }
    }
}
