package com.mdot.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.repository.ShiftRepository
import com.mdot.app.core.util.AppResult
import com.mdot.app.domain.model.NightShift
import com.mdot.app.domain.model.Shift
import com.mdot.app.feature.record.toText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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
        val target = shifts.value.firstOrNull { it.id == id }
        if (target?.builtin == true && target.name == NightShift.NAME) {
            messageIsError.value = true
            messageCanUndo.value = false
            message.value = "内置夜班名称固定，不能改名"
            return@launch
        }
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
