package com.mdot.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.update.ChangelogRepository
import com.mdot.app.core.update.UpdateRepository
import com.mdot.app.core.update.shouldAutoCheckUpdate
import com.mdot.app.domain.ChangelogParser
import com.mdot.app.domain.model.AppearanceConfig
import com.mdot.app.domain.model.BottomBarConfig
import com.mdot.app.domain.model.ChangelogEntry
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.feature.record.RecordSheetController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** 应用壳级状态：外观、底栏配置、引导标记、全局记录弹层请求、冷启动更新提示 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val settings: SettingsDataSource,
    val recordSheetController: RecordSheetController,
    private val holidayRepo: HolidayRepository,
    private val updateRepo: UpdateRepository,
    private val changelogRepo: ChangelogRepository,
) : ViewModel() {

    /** 已知有新版本未更新时的提示载体（新版 versionName）；null = 无/已处理。
     *  每次冷启动、只要 known 赶不上当前版本就置一条（AppRoot 渲染成底部悬浮「去更新」→ 关于页）；
     *  关于页的星形红点由 [SettingsDataSource.updateKnownCodeFlow] 持久承担，更新后自动消失。 */
    private val _updateAvailable = MutableStateFlow<String?>(null)
    val updateAvailable: StateFlow<String?> = _updateAvailable.asStateFlow()

    fun dismissUpdateAvailable() {
        _updateAvailable.value = null
    }

    /**
     * 首启「本次更新」引导的内容（对应线上 CHANGELOG.md 里**当前版本**的那一段）；null = 不展示。
     * ⚠️ 声明必须在 init 之前（属性初始化按声明顺序执行）。
     */
    private val _changelogGuide = MutableStateFlow<ChangelogEntry?>(null)
    val changelogGuide: StateFlow<ChangelogEntry?> = _changelogGuide.asStateFlow()

    /** 关闭引导：记下「本版本已展示」，以后冷启动不再打扰 */
    fun dismissChangelogGuide() {
        _changelogGuide.value = null
        viewModelScope.launch {
            settings.setLastShownChangelogVersionCode(BuildConfig.VERSION_CODE)
        }
    }

    init {
        // 启动即触发节假日库远程刷新（内置资产/现缓存兜底，失败静默；7 天节流）
        viewModelScope.launch { holidayRepo.refreshIfStale() }
        // 冷启动自动检查更新（v0.7.6）：24h 节流；发现新版**只**走底部悬浮提示，不弹窗
        viewModelScope.launch { autoCheckUpdate() }
        // 升级后首次启动：一次性「本次更新」引导（版本没变过就不打扰）
        viewModelScope.launch { maybeShowChangelogGuide() }
    }

    /**
     * 决定要不要弹「本次更新」引导：
     * - 新装（引导流程还没走完）⇒ 不弹（用户还没见过这个 App），并记为已展示（以后升级了再弹）
     * - 本版本已展示过 ⇒ 不弹
     * - 线上日志里没有当前版本段（还没发版 / 版本号对不上）⇒ 不弹，但记为已展示（不反复打扰）
     * - 拉不到内容 ⇒ 不弹、**也不记**（下次冷启动重试）
     */
    private suspend fun maybeShowChangelogGuide() {
        if (!settings.firstLaunchDoneFlow.first()) {
            // 新装：不是「升级」，不打扰；但记下本版本已处理，以后真升级了才弹
            settings.setLastShownChangelogVersionCode(BuildConfig.VERSION_CODE)
            return
        }
        val shown = settings.lastShownChangelogVersionCodeFlow.first()
        if (shown >= BuildConfig.VERSION_CODE) return
        val result = changelogRepo.load(forceRefresh = true)
        if (result !is ChangelogRepository.LoadResult.Success) return
        val entry = ChangelogParser.findByVersion(result.entries, BuildConfig.VERSION_NAME)
        if (entry == null) {
            settings.setLastShownChangelogVersionCode(BuildConfig.VERSION_CODE)
            return
        }
        _changelogGuide.value = entry
    }

    private suspend fun autoCheckUpdate() {
        // 首启引导期间不检查（新装必为最新，没必要打请求）
        if (!settings.firstLaunchDoneFlow.first()) return
        val now = System.currentTimeMillis()
        if (shouldAutoCheckUpdate(settings.lastUpdateCheckAtFlow.first(), now)) {
            updateRepo.check() // 成功才落 known/lastCheckAt；失败不落 → 下次冷启动重试
        }
        val code = settings.updateKnownCodeFlow.first()
        val name = settings.updateKnownNameFlow.first()
        if (code > BuildConfig.VERSION_CODE && name.isNotEmpty()) {
            _updateAvailable.value = name
        }
    }

    val appearance: StateFlow<AppearanceConfig> = settings.appearanceFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceConfig())

    val firstLaunchDone: StateFlow<Boolean> = settings.firstLaunchDoneFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val bottomBar: StateFlow<BottomBarConfig> = settings.bottomBarFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, BottomBarConfig())

    /** 底栏仅图标模式（v0.6.21 起出厂默认关闭） */
    val bottomBarIconOnly: StateFlow<Boolean> = settings.bottomBarIconOnlyFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 底栏布局：记加班按钮置右（右侧独立圆钮） */
    val bottomBarSideAction: StateFlow<Boolean> = settings.bottomBarSideActionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 底栏固定长度（4 个槽位宽；默认关闭 = 随槽位数自适应） */
    val bottomBarFixedWidth: StateFlow<Boolean> = settings.bottomBarFixedWidthFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 底栏毛玻璃（v0.6.21 起默认关闭） */
    val bottomBarFrosted: StateFlow<Boolean> = settings.bottomBarFrostedFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 底栏液态玻璃（**交互**：按压液滴透镜；与毛玻璃材质互相独立，默认关闭） */
    val bottomBarLiquidGlass: StateFlow<Boolean> = settings.bottomBarLiquidGlassFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 当前工时制度（顶栏标签、记录弹层文案等使用） */
    val workSystem: StateFlow<WorkSystem> = settings.salaryFlow
        .map { it.workSystem }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WorkSystem.STANDARD)

    val recordRequest = recordSheetController.request

    /** 日历页选中的日期（FAB 补记时使用） */
    private val _calendarSelectedDate = MutableStateFlow(LocalDate.now())
    val calendarSelectedDate: StateFlow<LocalDate> = _calendarSelectedDate.asStateFlow()

    fun setCalendarSelectedDate(date: LocalDate) {
        _calendarSelectedDate.value = date
    }
}
