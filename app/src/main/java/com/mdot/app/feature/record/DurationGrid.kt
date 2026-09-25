package com.mdot.app.feature.record

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.Duration
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.component.pressScale
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

private const val COLUMNS = 6

/** 预设档数（半小时步进）：默认 48 档 = 0.5–24h；工地记工「选工天」用 6 档 = 0.5–3 */
private fun presetLabels(steps: Int): List<String> = (1..steps).map { i ->
    if (i % 2 == 0) "${i / 2}" else "${i / 2}.5"
}

private const val CUSTOM_LABEL = "…"

/** 自定义格回显值：与 RecordSheetViewModel.minutesToHoursText 同款归一（100 分 → "1.67"） */
private fun formatHoursCell(h: Double): String =
    String.format(java.util.Locale.US, "%.2f", h).trimEnd('0').trimEnd('.')

/** 单元格 44dp + 行间距 8dp（= `Spacing.s`）= 行距 pitch，用于滚动定位选中行。⚠️ 改行间距必须同步改这里 */
private const val ROW_PITCH_DP = 52

/**
 * 时长选择网格（纯小时，6 列）：预设 0.5–24 + 末位自定义输入格。
 * 可视 3 行（44×3+8×2 ≈148dp，与分钟滚轮等高），其余行在组件内上下滚动，不影响外围弹层。
 * 输入格默认显示"…"，点击进入输入态、键入数字直接生效；选中预设后输入格回到"…"。
 * 既有值为非 0.5 网格时（如 1 小时 40 分），自定义格**回显该值并呈选中态**、网格滚到末行。
 */
@Composable
fun DurationGrid(
    selectedHours: Double?,
    onPreset: (Double) -> Unit,
    onCustomCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    presetSteps: Int = 48,
) {
    val haptic = LocalHapticFeedback.current
    val presets = remember(presetSteps) { presetLabels(presetSteps) }
    // 非 0.5 网格的既有值（如 1 小时 40 分 = 1.67）：预设格里没有它，
    // 由末位自定义格承担「选中」表现（回显值 + 高亮）——打开弹层预选当天记录时用
    val isCustomSelected = selectedHours != null && presets.none { it.toDoubleOrNull() == selectedHours }
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    // onFocusChanged 首次组合会以"未聚焦"回调一次，需等真正获得过焦点后才允许失焦提交
    var hasBeenFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val density = LocalDensity.current

    fun commit() {
        if (!editing) return
        editing = false
        if ((text.toDoubleOrNull() ?: 0.0) > 0.0) {
            onCustomCommit(text)
        } else {
            text = ""
        }
    }

    LaunchedEffect(editing) {
        if (editing) focusRequester.requestFocus()
    }

    // 进入时已有选中值（补改/编辑历史记录）→ 滚到对应行让高亮可见；
    // 非网格值滚到末行，让自定义格的回显选中态可见
    LaunchedEffect(Unit) {
        val sel = selectedHours ?: return@LaunchedEffect
        val idx = presets.indexOfFirst { it.toDoubleOrNull() == sel }
        val row = when {
            idx >= 0 -> idx / COLUMNS
            isCustomSelected -> (presets.size + COLUMNS - 1) / COLUMNS // 自定义格所在末行
            else -> return@LaunchedEffect
        }
        if (row >= 1) {
            // 等首次布局算出可滚动范围后再定位
            snapshotFlow { scroll.maxValue }.filter { it > 0 }.first()
            scroll.scrollTo(with(density) { (row * ROW_PITCH_DP).dp.roundToPx() })
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 44.dp * 3 + Spacing.s * 2)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        (presets + CUSTOM_LABEL).chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                row.forEach { label ->
                    if (label == CUSTOM_LABEL) {
                        // 末位自定义输入格：有非网格既有值时呈选中态（同预设格的高亮语言）
                        val inputInteraction = remember { MutableInteractionSource() }
                        val customSelected = !editing && isCustomSelected
                        val customBg by animateColorAsState(
                            targetValue = if (customSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "customCellBg",
                        )
                        val customStroke by animateColorAsState(
                            targetValue = if (customSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "customCellStroke",
                        )
                        val customTextColor by animateColorAsState(
                            targetValue = if (customSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "customCellText",
                        )
                        val cellShape = RoundedCornerShape(Radius.button)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .pressScale(inputInteraction, pressedScale = 0.93f)
                                .height(44.dp)
                                .background(customBg, cellShape)
                                .border(1.5.dp, customStroke, cellShape)
                                .clip(cellShape)
                                .clickable(
                                    interactionSource = inputInteraction,
                                    indication = LocalIndication.current,
                                    enabled = !editing,
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    hasBeenFocused = false
                                    // 已有非网格值时点自定义格 → 回显该值继续编辑
                                    if (text.isEmpty()) {
                                        text = selectedHours?.let { formatHoursCell(it) } ?: ""
                                    }
                                    editing = true
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (editing) {
                                BasicTextField(
                                    value = text,
                                    onValueChange = { v ->
                                        text = v.filter { c -> c.isDigit() || c == '.' }.take(6)
                                    },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Done,
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { commit() }),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .focusRequester(focusRequester)
                                        .onFocusChanged { state ->
                                            if (state.isFocused) {
                                                hasBeenFocused = true
                                            } else if (hasBeenFocused && editing) {
                                                commit()
                                            }
                                        },
                                )
                            } else {
                                Text(
                                    when {
                                        text.isNotEmpty() -> text
                                        isCustomSelected -> formatHoursCell(selectedHours!!)
                                        else -> CUSTOM_LABEL
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = customTextColor,
                                )
                            }
                        }
                    } else {
                        DurationCell(
                            label = label,
                            selected = selectedHours != null && label.toDoubleOrNull() == selectedHours,
                            modifier = Modifier.weight(1f),
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            editing = false
                            text = ""
                            label.toDoubleOrNull()?.let(onPreset)
                        }
                    }
                }
                // 末行不足 6 格时补空位，保持所有单元格等宽
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DurationCell(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.button)
    val interaction = remember { MutableInteractionSource() }
    // 选中态平滑过渡：背景/描边/文字颜色渐变 + 按压缩放
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "cellBg",
    )
    val stroke by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "cellStroke",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "cellText",
    )
    Box(
        modifier = modifier
            .pressScale(interaction, pressedScale = 0.93f)
            .height(44.dp)
            .background(bg, shape)
            .border(1.5.dp, stroke, shape)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current) {
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = textColor,
        )
    }
}
