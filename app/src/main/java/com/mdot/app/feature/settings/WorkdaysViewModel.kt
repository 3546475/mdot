package com.mdot.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

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
