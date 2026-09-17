package com.mdot.app.feature.comp

import androidx.compose.ui.res.stringResource
import com.mdot.app.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.AnimatedNumberText
import com.mdot.app.core.designsystem.component.InlineConfirmButton
import com.mdot.app.core.designsystem.component.InlineConfirmStyle
import com.mdot.app.core.designsystem.component.MessageSnackbarHost
import com.mdot.app.core.designsystem.component.rememberMessageSnackbar
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.core.util.AppResult
import com.mdot.app.domain.model.CompAdjustment
import com.mdot.app.domain.util.TimeUtils
import com.mdot.app.feature.record.toText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class CompBalanceUiState(
    val balanceMinutes: Int = 0,
    val adjustments: List<CompAdjustment> = emptyList(),
)

@HiltViewModel
class CompBalanceViewModel @Inject constructor(
    private val recordRepo: RecordRepository,
) : ViewModel() {

    private val message = MutableStateFlow<Message?>(null)
    data class Message(val text: String, val isError: Boolean = false, val canUndo: Boolean = false)

    val uiState: StateFlow<CompBalanceUiState> = combine(
        recordRepo.observeCompBalance(),
        recordRepo.observeAdjustments(),
    ) { balance, list ->
        CompBalanceUiState(
            balanceMinutes = balance,
            adjustments = list.sortedByDescending { it.createdAt },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompBalanceUiState())

    val messageFlow: StateFlow<Message?> = message

    fun clearMessage() {
        message.value = null
    }

    /** 提交手动调整；add=true 增加、false 扣减。成功后清空由 UI 侧处理。 */
    fun submit(add: Boolean, hours: Double, note: String) {
        if (hours <= 0) return
        val minutes = kotlin.math.round(hours * 60).toInt().coerceAtMost(12 * 60)
        val delta = if (add) minutes else -minutes
        viewModelScope.launch {
            when (val r = recordRepo.adjustComp(LocalDate.now(), delta, note)) {
                is AppResult.Success ->
                    message.value = Message("已${if (add) "增加" else "扣减"} ${TimeUtils.prettyDuration(minutes)}")
                is AppResult.Failure ->
                    message.value = Message(r.error.toText(), isError = true)
            }
        }
    }

    fun delete(adj: CompAdjustment) {
        viewModelScope.launch {
            when (val r = recordRepo.deleteAdjustment(adj.id)) {
                is AppResult.Success -> {
                    // 立即删除 + 提示窗给撤销（本行随即从列表消失，无法在按钮内原地撤销）
                    lastDeleted = adj
                    message.value = Message("已删除调整记录", canUndo = true)
                }
                is AppResult.Failure -> message.value = Message(r.error.toText(), isError = true)
            }
        }
    }

    /** 撤销最近一次删除：按原记录插回（仅最近一次有效） */
    fun undoDelete() {
        val adj = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            when (val r = recordRepo.restoreAdjustment(adj)) {
                is AppResult.Success -> message.value = Message("已撤销删除")
                is AppResult.Failure -> message.value = Message(r.error.toText(), isError = true)
            }
        }
    }

    /** 最近一次删除的调整记录（撤销用） */
    private var lastDeleted: CompAdjustment? = null
}

/** 调休余额内容主体（设定多页签「调休」页签复用）：查看余额、手动补差/扣减（留痕）、删除误操作调整 */
@Composable
fun CompBalancePane(
    vm: CompBalanceViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val msg by vm.messageFlow.collectAsStateWithLifecycle()

    var add by remember { mutableStateOf(true) }
    var hours by remember { mutableStateOf<Double?>(null) }
    var note by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .contentBottomPadding(showBottomBar = false)
            .padding(horizontal = Spacing.page),
    ) {
        Spacer(Modifier.height(Spacing.s))

        // ---- 当前余额 ----
        SectionCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.comp_balance_current),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AnimatedNumberText(
                            value = state.balanceMinutes,
                            text = { m ->
                                if (m < 0) "-${TimeUtils.prettyDuration(-m)}"
                                else TimeUtils.prettyDuration(m)
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (state.balanceMinutes < 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.tertiary,
                            label = "compBalance",
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.comp_balance_explain),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.m))

        // ---- 手动调整 ----
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.comp_adjust_title), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = add,
                        onClick = { add = true },
                        label = { Text(stringResource(R.string.comp_chip_add)) },
                    )
                    FilterChip(
                        selected = !add,
                        onClick = { add = false },
                        label = { Text(stringResource(R.string.comp_chip_deduct)) },
                    )
                }
                com.mdot.app.feature.record.DurationGrid(
                    selectedHours = hours,
                    onPreset = { hours = it },
                    onCustomCommit = { text ->
                        text.toDoubleOrNull()?.takeIf { it > 0 }?.let { hours = it }
                    },
                )
                OutlinedTextField(
                shape = RoundedCornerShape(Radius.textField),
                    value = note,
                    onValueChange = { note = it.take(50) },
                    label = { Text(stringResource(R.string.comp_note_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        vm.submit(add, hours ?: return@Button, note)
                        hours = null
                        note = ""
                    },
                    enabled = hours != null && (hours ?: 0.0) > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(if (add) R.string.comp_btn_add else R.string.comp_btn_deduct)) }
            }
        }

        Spacer(Modifier.height(Spacing.m))

        // ---- 调整记录 ----
        SectionCard {
            Column {
                Text(stringResource(R.string.comp_history_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.s))
                if (state.adjustments.isEmpty()) {
                    Text(
                        stringResource(R.string.comp_history_empty),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.adjustments.forEach { adj ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${TimeUtils.mdCn(adj.date)} ${TimeUtils.weekdayCn(adj.date)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        if (adj.deltaMinutes >= 0) "+${TimeUtils.prettyDuration(adj.deltaMinutes)}"
                                        else "-${TimeUtils.prettyDuration(-adj.deltaMinutes)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (adj.deltaMinutes >= 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error,
                                    )
                                }
                                adj.note?.let {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            // 删除调整：原地确认（只确认，不原地撤销：删除后本行即从列表消失）
                            // → 撤销由下方信息提示窗（Snackbar）承担
                            InlineConfirmButton(
                                idleText = stringResource(R.string.comp_delete),
                                confirmText = stringResource(R.string.comp_delete_confirm),
                                cancelText = stringResource(R.string.comp_cancel),
                                undoText = stringResource(R.string.comp_undo),
                                onConfirm = { vm.delete(adj) },
                                style = InlineConfirmStyle.Compact,
                            )
                        }
                        Spacer(Modifier.height(Spacing.xs))
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
    }

    val undoLabel = stringResource(R.string.comp_undo)
    val (snackbarHostState, snackbarIsError) = rememberMessageSnackbar(
        message = msg?.text,
        onClear = vm::clearMessage,
        isError = msg?.isError == true,
        actionLabel = if (msg?.canUndo == true) undoLabel else null,
        onAction = vm::undoDelete,
    )
    MessageSnackbarHost(snackbarHostState, snackbarIsError, Modifier.align(Alignment.BottomCenter))
    }
}
