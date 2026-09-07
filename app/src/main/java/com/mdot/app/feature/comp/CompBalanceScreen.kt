package com.mdot.app.feature.comp

import androidx.compose.ui.res.stringResource
import com.mdot.app.R
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
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
import com.mdot.app.core.designsystem.component.ConfirmDialog
import com.mdot.app.core.designsystem.component.JiabanTopBar
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
    data class Message(val text: String, val isError: Boolean = false)

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

    fun delete(id: Long) {
        viewModelScope.launch {
            recordRepo.deleteAdjustment(id)
        }
    }
}

/** 调休余额管理页：查看余额、手动补差/扣减（留痕）、删除误操作调整 */
@Composable
fun CompBalanceScreen(
    onBack: () -> Unit,
    vm: CompBalanceViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val msg by vm.messageFlow.collectAsStateWithLifecycle()

    var add by remember { mutableStateOf(true) }
    var hours by remember { mutableStateOf<Double?>(null) }
    var note by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<CompAdjustment?>(null) }

    msg?.let { m ->
        LaunchedEffect(m) {
            delay(3000)
            vm.clearMessage()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .contentBottomPadding(showBottomBar = false)
            .padding(horizontal = Spacing.page),
    ) {
        JiabanTopBar(title = stringResource(R.string.comp_balance_title), onBack = onBack)
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
                        Text(
                            if (state.balanceMinutes < 0) "-${TimeUtils.prettyDuration(-state.balanceMinutes)}"
                            else TimeUtils.prettyDuration(state.balanceMinutes),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (state.balanceMinutes < 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.tertiary,
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
                hours?.let { h ->
                    Text(
                        if (add) stringResource(R.string.comp_preview_add, TimeUtils.prettyDuration(kotlin.math.round(h * 60).toInt()))
                        else stringResource(R.string.comp_preview_deduct, TimeUtils.prettyDuration(kotlin.math.round(h * 60).toInt())),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (add) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                }
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
                msg?.let {
                    Text(
                        it.text,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (it.isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                }
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
                            IconButton(onClick = { deleting = adj }) {
                                Icon(
                                    painterResource(R.drawable.ic_ms_delete),
                                    contentDescription = stringResource(R.string.comp_delete_cd),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(Spacing.xs))
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
    }

    deleting?.let { adj ->
        ConfirmDialog(
            title = stringResource(R.string.comp_delete_confirm_title),
            text = "${TimeUtils.mdCn(adj.date)} ${if (adj.deltaMinutes >= 0) "+" else "-"}" +
                "${TimeUtils.prettyDuration(kotlin.math.abs(adj.deltaMinutes))}" +
                (adj.note?.let { stringResource(R.string.comp_note_paren, it) } ?: "") +
                stringResource(R.string.comp_delete_confirm_suffix),
            onConfirm = {
                vm.delete(adj.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}
