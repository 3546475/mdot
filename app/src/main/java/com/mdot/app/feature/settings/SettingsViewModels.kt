package com.mdot.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.repository.CompDataSnapshot
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.core.repository.ShiftRepository
import com.mdot.app.core.sync.SyncEngine
import com.mdot.app.core.sync.SyncStatus
import com.mdot.app.core.util.AppResult
import com.mdot.app.domain.model.AppearanceConfig
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.model.BottomBarConfig
import com.mdot.app.domain.model.HomeCardsConfig
import com.mdot.app.domain.model.Shift
import com.mdot.app.domain.model.ThemeMode
import com.mdot.app.feature.record.toText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

@HiltViewModel
class CycleViewModel @Inject constructor(
    private val settings: SettingsDataSource,
) : ViewModel() {
    val anchorDay: StateFlow<Int> = settings.cycleAnchorDayFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    fun set(day: Int) = viewModelScope.launch { settings.setCycleAnchorDay(day) }
}

@HiltViewModel
class WorkdaysViewModel @Inject constructor(
    private val settings: SettingsDataSource,
) : ViewModel() {
    val workdays: StateFlow<Set<DayOfWeek>> = settings.workdaysFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_WORKDAYS)

    fun toggle(day: DayOfWeek) = viewModelScope.launch {
        val current = workdays.value
        val next = if (day in current) current - day else current + day
        if (next.isNotEmpty()) settings.setWorkdays(next)
    }

    companion object {
        val DEFAULT_WORKDAYS = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
    }
}

@HiltViewModel
class ShiftsViewModel @Inject constructor(
    private val repo: ShiftRepository,
) : ViewModel() {
    val shifts: StateFlow<List<Shift>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val message = MutableStateFlow<String?>(null)
    /** message 的错误语义：失败类提示 true（渲染 ⚠ + error 描边），成功类 false（✓） */
    val messageIsError = MutableStateFlow(false)
    /** message 是否带「撤销」动作（删除班次成功时为 true） */
    val messageCanUndo = MutableStateFlow(false)
    /** 最近一次删除的班次（撤销用；仅最近一次有效） */
    private var lastDeleted: Shift? = null

    fun create(name: String) = viewModelScope.launch {
        repo.create(name).let { r -> postResultMessage(r, "已添加") }
    }

    fun rename(id: Long, name: String) = viewModelScope.launch {
        repo.rename(id, name).let { r -> postResultMessage(r, "已改名") }
    }

    fun delete(shift: Shift) = viewModelScope.launch {
        when (val r = repo.delete(shift.id)) {
            is AppResult.Success -> {
                // 立即删除 + 提示窗给撤销（本行随即从列表消失，无法在按钮内原地撤销）
                lastDeleted = shift
                messageIsError.value = false
                messageCanUndo.value = true
                message.value = "已删除班次「${shift.name}」"
            }
            is AppResult.Failure -> postResultMessage(r, "已删除")
        }
    }

    /** 撤销最近一次删除：按原 id/排序插回（仅最近一次有效） */
    fun undoDelete() {
        val shift = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            when (val r = repo.restore(shift)) {
                is AppResult.Success -> {
                    messageIsError.value = false
                    messageCanUndo.value = false
                    message.value = "已撤销删除"
                }
                is AppResult.Failure -> postResultMessage(r, "已撤销删除")
            }
        }
    }

    private fun postResultMessage(r: AppResult<*>, ok: String) {
        messageIsError.value = r is AppResult.Failure
        messageCanUndo.value = false
        message.value = r.messageOrSuccess(ok)
    }

    fun setHidden(id: Long, hidden: Boolean) = viewModelScope.launch {
        repo.setHidden(id, hidden)
    }

    fun move(id: Long, up: Boolean) = viewModelScope.launch {
        val list = shifts.value.sortedBy { it.sort }
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return@launch
        val target = if (up) index - 1 else index + 1
        if (target !in list.indices) return@launch
        val swapped = list.toMutableList().apply {
            val tmp = this[index]; this[index] = this[target]; this[target] = tmp
        }
        repo.reorder(swapped.map { it.id })
    }

    /** 拖拽排序后的顺序提交（班次管理六点手柄拖动用） */
    fun reorder(ids: List<Long>) = viewModelScope.launch {
        repo.reorder(ids)
    }

    fun clearMessage() {
        message.value = null
        messageIsError.value = false
        messageCanUndo.value = false
    }
}

private fun AppResult<*>.messageOrSuccess(ok: String): String = when (this) {
    is AppResult.Success -> ok
    is AppResult.Failure -> error.toText()
}

/** 外观摘要（我的页外观行右值），如"浅色 · 抹茶绿"/"跟随系统 · 动态取色" */
fun appearanceSummary(context: android.content.Context, config: AppearanceConfig): String {
    val theme = when (config.themeMode) {
        ThemeMode.LIGHT -> "浅色"
        ThemeMode.DARK -> "深色"
        ThemeMode.SYSTEM -> "跟随系统"
    }
    if (config.dynamicColor &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    ) return "$theme · 动态取色"
    val paletteRes = com.mdot.app.core.designsystem.paletteOptions()
        .firstOrNull { it.first == config.paletteId }?.second
    val palette = paletteRes?.let { context.getString(it) } ?: "自定义"
    return "$theme · $palette"
}

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settings: SettingsDataSource,
) : ViewModel() {
    val appearance: StateFlow<AppearanceConfig> = settings.appearanceFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceConfig())

    val bottomBarCount: StateFlow<Int> = settings.bottomBarFlow
        .map { it.slots.count { id -> id != BottomBarConfig.HOME } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 2)

    /** 首页卡片数（清洗未知 id 后的实际张数；未配置时显示出厂默认张数，与首页一致） */
    val homeCardsCount: StateFlow<Int> = settings.homeCardsFlow
        .map {
            it.enabledCards.size
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeCardsConfig().enabledCards.size)

    fun setMode(mode: ThemeMode) = viewModelScope.launch {
        settings.setAppearance(appearance.value.copy(themeMode = mode))
    }

    fun setPalette(id: String) = viewModelScope.launch {
        settings.setAppearance(appearance.value.copy(paletteId = id, dynamicColor = false))
    }

    fun setDynamic(value: Boolean) = viewModelScope.launch {
        settings.setAppearance(appearance.value.copy(dynamicColor = value))
    }

    /** 弹层背景效果：压暗 / 模糊 / 模糊缩小 */
    fun setSheetBackdrop(mode: com.mdot.app.domain.model.SheetBackdropMode) = viewModelScope.launch {
        settings.setAppearance(appearance.value.copy(sheetBackdropMode = mode))
    }
}

@HiltViewModel
class BottomBarViewModel @Inject constructor(
    private val settings: SettingsDataSource,
) : ViewModel() {
    val config: StateFlow<BottomBarConfig> = settings.bottomBarFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, BottomBarConfig())

    val iconOnly: StateFlow<Boolean> = settings.bottomBarIconOnlyFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 底栏布局：记加班按钮置右（右侧独立圆钮） */
    val sideAction: StateFlow<Boolean> = settings.bottomBarSideActionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 底栏固定长度（4 个槽位宽；默认关闭 = 随槽位数自适应） */
    val fixedWidth: StateFlow<Boolean> = settings.bottomBarFixedWidthFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 底栏毛玻璃（半透明 + 模糊身后内容；v0.6.21 起默认关闭） */
    val frosted: StateFlow<Boolean> = settings.bottomBarFrostedFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setIconOnly(value: Boolean) = viewModelScope.launch {
        settings.setBottomBarIconOnly(value)
    }

    fun setSideAction(value: Boolean) = viewModelScope.launch {
        settings.setBottomBarSideAction(value)
    }

    fun setFixedWidth(value: Boolean) = viewModelScope.launch {
        settings.setBottomBarFixedWidth(value)
    }

    fun setFrosted(value: Boolean) = viewModelScope.launch {
        settings.setBottomBarFrosted(value)
    }

    /** 打开/关闭某个槽位：**只改 disabled，不动 order**（开关不影响顺序，对齐班次卡片） */
    fun setEnabled(slotId: String, enabled: Boolean) = viewModelScope.launch {
        if (slotId == BottomBarConfig.HOME) return@launch // 首页固定不可关
        val cfg = config.value
        val disabled = cfg.disabled.toMutableList()
        if (enabled) disabled.remove(slotId) else if (slotId !in disabled) disabled.add(slotId)
        settings.setBottomBar(cfg.copy(disabled = disabled.distinct()))
    }

    /** 拖拽排序后的顺序提交（disabled 原样保留；池内其余项补在后面，不丢项） */
    fun setOrder(order: List<String>) = viewModelScope.launch {
        val cfg = config.value
        val cleaned = order.filter { it in BottomBarConfig.CONFIGURABLE }.distinct()
        settings.setBottomBar(cfg.copy(order = cleaned + BottomBarConfig.DEFAULT_ORDER.filter { it !in cleaned }))
    }
}

@HiltViewModel
class DataSourceViewModel @Inject constructor(
    private val settings: SettingsDataSource,
    private val holidayRepo: HolidayRepository,
) : ViewModel() {

    val holidayVersion: StateFlow<String?> = holidayRepo.dataVersionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val holidayUrl = MutableStateFlow("")
    val updateUrl = MutableStateFlow("")
    val holidayUrls = MutableStateFlow<List<String>>(emptyList())
    val updateUrls = MutableStateFlow<List<String>>(emptyList())
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            holidayUrl.value = settings.holidayUrlFlow.first()
            updateUrl.value = settings.updateUrlFlow.first()
            // 历史版本可能往 DataStore 存过重复条目（重复会双行同亮），加载时去重
            updateUrls.value = settings.updateUrlsFlow.first().ifEmpty { SettingsDataSource.DEFAULT_UPDATE_URLS }.distinct()
            holidayUrls.value = settings.holidayUrlsFlow.first().ifEmpty { SettingsDataSource.DEFAULT_HOLIDAY_URLS }.distinct()
        }
    }

    fun setHolidayUrl(url: String) = viewModelScope.launch {
        holidayUrl.value = url
        settings.setHolidayUrl(url.trim())
    }

    fun setUpdateUrl(url: String) = viewModelScope.launch {
        updateUrl.value = url
        settings.setUpdateUrl(url.trim())
    }

    /** 添加候选地址（非法/重复时置 message，不入库） */
    fun addUpdateUrl(url: String) = addUrl(updateUrls, url) { list -> settings.setUpdateUrls(list) }

    fun removeUpdateUrl(url: String) = removeUrl(updateUrls, updateUrl, url, SettingsDataSource.DEFAULT_UPDATE_URLS) { list ->
        settings.setUpdateUrls(list)
    }

    fun addHolidayUrl(url: String) = addUrl(holidayUrls, url) { list -> settings.setHolidayUrls(list) }

    fun removeHolidayUrl(url: String) = removeUrl(holidayUrls, holidayUrl, url, SettingsDataSource.DEFAULT_HOLIDAY_URLS) { list ->
        settings.setHolidayUrls(list)
    }

    private inline fun MutableStateFlow<List<String>>.mod(
        crossinline persist: suspend (List<String>) -> Unit,
        crossinline block: (List<String>) -> List<String>,
    ) = viewModelScope.launch {
        val next = block(value)
        value = next
        persist(next)
    }

    private fun addUrl(
        flow: MutableStateFlow<List<String>>,
        raw: String,
        persist: suspend (List<String>) -> Unit,
    ) = viewModelScope.launch {
        val u = raw.trim()
        when {
            !(u.startsWith("http://") || u.startsWith("https://")) ->
                message.value = "地址需以 http:// 或 https:// 开头"
            u in flow.value -> message.value = "该地址已在列表中"
            else -> {
                flow.value = flow.value + u
                persist(flow.value)
                message.value = null
            }
        }
    }

    private fun removeUrl(
        flow: MutableStateFlow<List<String>>,
        current: MutableStateFlow<String>,
        url: String,
        fallback: List<String>,
        persist: suspend (List<String>) -> Unit,
    ) = viewModelScope.launch {
        val next = (flow.value - url).ifEmpty { fallback }
        flow.value = next
        persist(next)
        // 删掉的是当前生效值 → 自动切到列表首个
        if (current.value !in next) {
            current.value = next.first()
            persistSelected(current.value, fallback)
        }
    }

    private suspend fun persistSelected(url: String, fallback: List<String>) {
        // update/holiday 的选中值落库（setUpdateUrl/setHolidayUrl）
        if (fallback === SettingsDataSource.DEFAULT_UPDATE_URLS || fallback == SettingsDataSource.DEFAULT_UPDATE_URLS) {
            settings.setUpdateUrl(url)
        } else {
            settings.setHolidayUrl(url)
        }
    }

    fun refreshHoliday() = viewModelScope.launch {
        busy.value = true
        when (val r = holidayRepo.refresh()) {
            is AppResult.Success -> message.value = "节假日库已更新：${r.data}"
            is AppResult.Failure -> message.value = "刷新失败：${r.error.toText()}"
        }
        busy.value = false
    }

    fun clearMessage() {
        message.value = null
    }
}


/** 设置中心/工时设置的行右值摘要 */
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

    /** 工地记工当前项目（工时设置 SITE 形态「项目与结算」行摘要用；指针变化自动刷新） */
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

/** 首页卡片配置（v0.6.0 首页卡片可编辑）：开关显隐 + 拖拽排序；至少保留一张 */
@HiltViewModel
class HomeCardsViewModel @Inject constructor(
    private val settings: SettingsDataSource,
) : ViewModel() {
    val config: StateFlow<HomeCardsConfig> = settings.homeCardsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeCardsConfig())

    /** 显示/隐藏某张卡片：**只改 disabled，不动 order**（开关不影响顺序，对齐班次卡片） */
    fun setEnabled(id: String, enabled: Boolean) = viewModelScope.launch {
        if (id == HomeCardsConfig.DATA) return@launch // 数据区固定不可隐藏
        val cfg = config.value
        val disabled = cfg.disabled.toMutableList()
        if (enabled) disabled.remove(id) else if (id !in disabled) disabled.add(id)
        settings.setHomeCards(cfg.copy(disabled = disabled.distinct()))
    }

    /** 拖拽排序后的顺序提交（disabled 原样保留；池内其余项补在后面，不丢项） */
    fun setOrder(order: List<String>) = viewModelScope.launch {
        val cfg = config.value
        val cleaned = order.filter { it in HomeCardsConfig.POOL }.distinct()
        settings.setHomeCards(cfg.copy(order = cleaned + HomeCardsConfig.DEFAULT_ORDER.filter { it !in cleaned }))
    }
}


object SettingsHubDefaults {
    val STANDARD_WORKDAYS: Set<java.time.DayOfWeek> = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
}
