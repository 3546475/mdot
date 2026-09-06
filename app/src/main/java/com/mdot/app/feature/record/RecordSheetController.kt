package com.mdot.app.feature.record

import com.mdot.app.domain.model.RecordType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** 打开记录弹层的请求（07 文档 §7：全局单例 controller，任何页面零成本唤起） */
data class RecordRequest(
    val date: LocalDate,
    val tab: RecordType,
    /** 每次打开自增/取时间戳，强制弹层重新绑定数据 */
    val token: Long,
)

@Singleton
class RecordSheetController @Inject constructor() {

    private val _request = MutableStateFlow<RecordRequest?>(null)
    val request: StateFlow<RecordRequest?> = _request.asStateFlow()

    /** 日历页选中的日期（FAB 补记时使用） */
    private val _calendarSelectedDate = MutableStateFlow(LocalDate.now())
    val calendarSelectedDate: StateFlow<LocalDate> = _calendarSelectedDate.asStateFlow()

    fun setCalendarSelectedDate(date: LocalDate) {
        _calendarSelectedDate.value = date
    }

    fun open(date: LocalDate, tab: RecordType = RecordType.OT) {
        _request.value = RecordRequest(date, tab, token = System.currentTimeMillis())
    }

    fun dismiss() {
        _request.value = null
    }
}
