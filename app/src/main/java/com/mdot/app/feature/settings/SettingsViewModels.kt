package com.mdot.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.holiday.HolidayRepository
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

    fun create(name: String) = viewModelScope.launch {
        repo.create(name).let { r -> message.value = r.messageOrSuccess("已添加") }
    }

    fun rename(id: Long, name: String) = viewModelScope.launch {
        repo.rename(id, name).let { r -> message.value = r.messageOrSuccess("已改名") }
    }

    fun delete(id: Long) = viewModelScope.launch {
        repo.delete(id).let { r -> message.value = r.messageOrSuccess("已删除") }
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
        .map { it.slots.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 2)

    /** 首页卡片数（清洗未知 id 后的实际张数；未配置时显示出厂默认张数，与首页一致） */
    val homeCardsCount: StateFlow<Int> = settings.homeCardsFlow
        .map {
            val pool = com.mdot.app.domain.model.HomeCardsConfig.POOL
            it.cards?.filter { c -> c in pool }?.distinct()?.size
                ?: com.mdot.app.domain.model.HomeCardsConfig.DEFAULT_CARDS.size
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.mdot.app.domain.model.HomeCardsConfig.DEFAULT_CARDS.size)

    fun setMode(mode: ThemeMode) = viewModelScope.launch {
        settings.setAppearance(appearance.value.copy(themeMode = mode))
    }

    fun setPalette(id: String) = viewModelScope.launch {
        settings.setAppearance(appearance.value.copy(paletteId = id, dynamicColor = false))
    }

    fun setDynamic(value: Boolean) = viewModelScope.launch {
        settings.setAppearance(appearance.value.copy(dynamicColor = value))
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

    fun setIconOnly(value: Boolean) = viewModelScope.launch {
        settings.setBottomBarIconOnly(value)
    }

    val selectedSlot = MutableStateFlow(-1)

    fun selectSlot(index: Int) {
        selectedSlot.value = if (selectedSlot.value == index) -1 else index
    }

    /** 功能池点选：已选则移除，未选则追加（超过上限忽略） */
    fun toggle(slotId: String) = viewModelScope.launch {
        val slots = config.value.slots.toMutableList()
        if (slotId in slots) {
            slots.remove(slotId)
        } else {
            if (slots.size >= BottomBarConfig.MAX_SLOTS) return@launch
            slots.add(slotId)
        }
        settings.setBottomBar(BottomBarConfig(slots.distinct()))
        selectedSlot.value = -1
    }

    /** 功能池点选：填充到选中槽位，否则追加到第一个空位 */
    fun assign(slotId: String) = viewModelScope.launch {
        val slots = config.value.slots.toMutableList()
        val sel = selectedSlot.value
        when {
            sel in slots.indices -> slots[sel] = slotId
            slots.size < BottomBarConfig.MAX_SLOTS -> slots.add(slotId)
            else -> return@launch
        }
        settings.setBottomBar(BottomBarConfig(slots))
    }

    fun removeAt(index: Int) = viewModelScope.launch {
        val slots = config.value.slots.toMutableList()
        if (index in slots.indices) {
            slots.removeAt(index)
            settings.setBottomBar(BottomBarConfig(slots))
            selectedSlot.value = -1
        }
    }

    fun moveSlot(index: Int, up: Boolean) = viewModelScope.launch {
        val slots = config.value.slots.toMutableList()
        val target = if (up) index - 1 else index + 1
        if (index !in slots.indices || target !in slots.indices) return@launch
        val tmp = slots[index]; slots[index] = slots[target]; slots[target] = tmp
        settings.setBottomBar(BottomBarConfig(slots))
    }

    /** 预览拖拽排序：把 index 处的槽移到 to 处（首页固定 index0，不与其它槽交换） */
    fun reorder(index: Int, to: Int) = viewModelScope.launch {
        val slots = config.value.slots.toMutableList()
        if (index !in slots.indices) return@launch
        // 首页固定在首位：不把它当作可拖源，也禁止别的槽插入到 0
        if (slots[index] == "home") return@launch
        val to2 = to.coerceIn(if (slots.getOrNull(0) == "home") 1 else 0, slots.lastIndex)
        if (to2 == index) return@launch
        val item = slots.removeAt(index)
        slots.add(to2, item)
        settings.setBottomBar(BottomBarConfig(slots))
    }

    /** 拖拽排序后的顺序提交 */
    fun applySlots(slots: List<String>) = viewModelScope.launch {
        settings.setBottomBar(
            BottomBarConfig(slots.distinct().take(BottomBarConfig.MAX_SLOTS))
        )
    }

    fun preset(efficient: Boolean) = viewModelScope.launch {
        settings.setBottomBar(
            BottomBarConfig(
                if (efficient) BottomBarConfig.EFFICIENT_SLOTS else BottomBarConfig.DEFAULT_SLOTS
            )
        )
        selectedSlot.value = -1
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
            updateUrls.value = settings.updateUrlsFlow.first().ifEmpty { SettingsDataSource.DEFAULT_UPDATE_URLS }
            holidayUrls.value = settings.holidayUrlsFlow.first().ifEmpty { SettingsDataSource.DEFAULT_HOLIDAY_URLS }
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
     * 转调休清零 + 删除调休手动调整记录。
     */
    fun switchWorkSystem(system: WorkSystem) {
        viewModelScope.launch {
            val current = settings.salaryFlow.first()
            if (current.workSystem == system) return@launch
            val next = current.copy(workSystem = system)
            settings.setSalary(next)
            if (system != WorkSystem.STANDARD) {
                recordRepo.clearCompData()
            }
            // 工地记工：确保「当前项目」存在（首切自动建「首个记工项目」，D1-rev）
            if (system == WorkSystem.SITE) {
                siteRepo.currentProjectId()
            }
        }
    }
}

/** 首页卡片配置（v0.6.0 首页卡片可编辑）：开关显隐 + 拖拽排序；至少保留一张 */
@HiltViewModel
class HomeCardsViewModel @Inject constructor(
    private val settings: SettingsDataSource,
) : ViewModel() {
    val config: StateFlow<HomeCardsConfig> = settings.homeCardsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeCardsConfig())

    /** 点选开关：显示中→隐藏（至少保留一张，否则忽略）；隐藏→追加到显示序列末尾 */
    fun toggle(id: String) = viewModelScope.launch {
        // 未配置基线 = DEFAULT_CARDS（与配置页草稿/首页实际显示一致，避免 POOL 下点开变关闭）
        val current = config.value.cards ?: HomeCardsConfig.DEFAULT_CARDS
        val next = if (id in current) {
            if (current.size <= HomeCardsConfig.MIN_CARDS) return@launch
            current - id
        } else {
            current + id
        }
        settings.setHomeCards(HomeCardsConfig(next))
    }

    /** 拖拽排序后的顺序提交（持久化为显式配置，此后不再走底栏联动的默认推导） */
    fun applyOrder(cards: List<String>) = viewModelScope.launch {
        val cleaned = cards.filter { it in HomeCardsConfig.POOL }.distinct()
        if (cleaned.size < HomeCardsConfig.MIN_CARDS) return@launch
        settings.setHomeCards(HomeCardsConfig(cleaned))
    }
}


object SettingsHubDefaults {
    val STANDARD_WORKDAYS: Set<java.time.DayOfWeek> = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
}
