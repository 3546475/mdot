package com.mdot.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.domain.model.AppearanceConfig
import com.mdot.app.domain.model.BottomBarConfig
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.feature.record.RecordSheetController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** 应用壳级状态：外观、底栏配置、引导标记、全局记录弹层请求 */
@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsDataSource,
    val recordSheetController: RecordSheetController,
) : ViewModel() {

    val appearance: StateFlow<AppearanceConfig> = settings.appearanceFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceConfig())

    val firstLaunchDone: StateFlow<Boolean> = settings.firstLaunchDoneFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val bottomBar: StateFlow<BottomBarConfig> = settings.bottomBarFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, BottomBarConfig())

    /** 底栏仅图标模式 */
    val bottomBarIconOnly: StateFlow<Boolean> = settings.bottomBarIconOnlyFlow
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
