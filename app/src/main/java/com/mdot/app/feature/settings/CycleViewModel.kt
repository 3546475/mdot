package com.mdot.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CycleViewModel @Inject constructor(
    private val settings: SettingsDataSource,
) : ViewModel() {
    val anchorDay: StateFlow<Int> = settings.cycleAnchorDayFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    /** 日历月历是否按考勤周期展示（默认关 = 自然月） */
    val calendarCycleView: StateFlow<Boolean> = settings.calendarCycleViewFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun set(day: Int) = viewModelScope.launch { settings.setCycleAnchorDay(day) }

    fun setCalendarCycleView(value: Boolean) = viewModelScope.launch {
        settings.setCalendarCycleView(value)
    }
}
