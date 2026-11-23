package com.mdot.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.repository.CompDataSnapshot
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.core.repository.ShiftRepository
import com.mdot.app.core.sync.SyncEngine
import com.mdot.app.core.sync.SyncStatus
import com.mdot.app.domain.model.AppearanceConfig
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.model.BottomBarConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

/** 设置页签（原设置中心）/制度设定的行右值摘要 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsHubViewModel @Inject constructor(
    private val settings: SettingsDataSource,
    syncEngine: SyncEngine,
    shiftRepo: ShiftRepository,
    private val recordRepo: RecordRepository,
    private val siteRepo: com.mdot.app.core.repository.SiteRepository,
) : ViewModel() {

    val syncStatus: StateFlow<SyncStatus> = syncEngine.status
        .stateIn(viewModelScope, SharingStarted.Eagerly, SyncStatus())

    val bottomBar: StateFlow<BottomBarConfig> = settings.bottomBarFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, BottomBarConfig())

    val appearance: StateFlow<AppearanceConfig> = settings.appearanceFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceConfig())

    val salary: StateFlow<SalaryConfig> = settings.salaryFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SalaryConfig())

    val cycleAnchorDay: StateFlow<Int> = settings.cycleAnchorDayFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    val workdays: StateFlow<Set<DayOfWeek>> = settings.workdaysFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))

    val shiftCount: StateFlow<Int> = shiftRepo.observeAll()
        .map { list -> list.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val compBalance: StateFlow<Int> = recordRepo.observeCompBalance()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** 工地记工当前项目（制度设定 SITE 形态「项目与结算」行摘要用；指针变化自动刷新） */
    val siteCurrentProject: StateFlow<com.mdot.app.domain.model.SiteProject?> =
        settings.salaryFlow
            .map { it.siteCurrentProjectId }
            .flatMapLatest { pid ->
                if (pid == 0L) kotlinx.coroutines.flow.flowOf(null)
                else siteRepo.observeProject(pid)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * 切换工时制度（08 文档 §2.3 F-H1、10 文档 F-Z1）。
     * 切到非标准工时（小时工/综合工时均无调休基础，10 文档 D4）时清理调休数据：
     * 转调休清零 + 删除调休手动调整记录。清理前快照调休数据，供按钮原地撤销。
     */
    fun switchWorkSystem(system: WorkSystem) {
        viewModelScope.launch {
            val current = settings.salaryFlow.first()
            if (current.workSystem == system) return@launch
            // 先快照（切到非标准工时会清空调休数据）→ 供 undoSwitchWorkSystem 还原
            val snapshot = recordRepo.snapshotCompData()
            val next = current.copy(workSystem = system)
            settings.setSalary(next)
            if (system != WorkSystem.STANDARD) {
                recordRepo.clearCompData()
            }
            // 工地记工：确保「当前项目」存在（首切自动建「首个记工项目」，D1-rev）
            if (system == WorkSystem.SITE) {
                siteRepo.currentProjectId()
            }
            switchBackup = current.workSystem to snapshot
        }
    }

    /** 撤销最近一次切换工时制度：切回原制度 + 还原被清空的调休数据（仅最近一次有效） */
    fun undoSwitchWorkSystem() {
        val (back, snapshot) = switchBackup ?: return
        switchBackup = null
        viewModelScope.launch {
            val current = settings.salaryFlow.first()
            if (current.workSystem == back) return@launch
            settings.setSalary(current.copy(workSystem = back))
            recordRepo.restoreCompData(snapshot)
        }
    }

    /** 最近一次切换工时的撤销快照：原制度 + 被清空的调休数据 */
    private var switchBackup: Pair<WorkSystem, CompDataSnapshot>? = null
}
