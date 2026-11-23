package com.mdot.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.util.AppResult
import com.mdot.app.feature.record.toText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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
