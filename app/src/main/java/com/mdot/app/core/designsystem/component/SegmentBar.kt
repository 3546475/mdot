package com.mdot.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mdot.app.core.designsystem.Radius

/**
 * 滑块式分段控件（统计页顶栏 统计/记月、工地记工顶栏 记账/记借支结算、及其子页签共用）：
 * 凹槽轨道 + primaryContainer 浮动滑块（用户 2026-09-21 定稿：轨道整体凹陷，滑块在凹槽里浮起），
 * 滑块 offset 跟随 [position] 连续位移
 * （pager 传 currentPage+offsetFraction 可随手势实时跟随；纯页签传动画值即得弹簧滑动）。
 * 按压反馈即滑块位移本身，不叠涟漪。
 *
 * 本组件是全 app 「从 N 选 1」的**页签/滑块**形态（04 文档 §4.1）——设置类页面同样走它，
 * 不再混用 `FilterChip`：chip 的「描边 → 实心填充」与滑块语言不同，且块宽随标签长度变化，
 * 一行里会出现 60/60/88dp 的参差右边界。
 * **2026-09-24 起（用户定，docs/03 §16）：多选一「选项」以 [ChoicePillRow] 为模板**，
 * 本组件只保留现役**页签/滑块**场景（滑块随手势连续跟随是它独有），
 * **不再新增选项行用例**；外观页「深浅色 / 弹层背景」两行已于 2026-09-23 改走
 * [ChoicePillRow] 图标选项药丸（用户参考图定稿：图标 + 文字、选中整颗实底）。
 *
 * @param position 连续选中位置（0 起；如 0.35 表示滑在 0/1 段之间）
 * @param fillWidth true = 撑满可用宽度、把宽度**等分**给各段（段宽由测量得出，忽略 [segWidth]）；
 *   用于设置页那种「整行等分、右边界与卡片对齐」的场景
 * @param trackSunken 轨道整体凹陷（用户 2026-09-21 定稿规格，默认开）：tonal 轨道换成
 *   凹槽光影（[sunkenWell]），选中滑块保持 primaryContainer 浮起——「凹槽里浮起一颗」
 *   成为全 app 分段控件的统一形态；如某处确需回归平铺轨道可显式传 false
 */
@Composable
fun SegmentBar(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    segWidth: Dp = 96.dp,
    position: Float = selected.toFloat(),
    fillWidth: Boolean = false,
    trackSunken: Boolean = true,
) {
    val gap = 4.dp
    val pillShape = RoundedCornerShape(Radius.pill)
    Box(
        modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(pillShape)
            .then(
                if (trackSunken) Modifier.sunkenWell(pillShape)
                else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            .padding(gap),
    ) {
        // 等分模式下段宽取自「轨道实测宽 - 段间距」再均分：BoxWithConstraints 在布局前就给出，
        // 不需要 onSizeChanged 的额外一帧（否则首帧滑块会用默认段宽画错位置，闪一下）。
        // 非等分模式仍用固定 segWidth，此时约束可能是无界的，不做任何算术。
        BoxWithConstraints {
            val seg = if (fillWidth && labels.isNotEmpty()) {
                ((maxWidth - gap * (labels.size - 1)) / labels.size).coerceAtLeast(0.dp)
            } else {
                segWidth
            }
            val pillShape = RoundedCornerShape(Radius.pill)
            Box(
                Modifier
                    .width(seg)
                    .height(34.dp)
                    .offset(x = (seg + gap) * position)
                    .clip(pillShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Row(
                Modifier.height(34.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                val interactions = remember(labels.size) { List(labels.size) { MutableInteractionSource() } }
                labels.forEachIndexed { index, label ->
                    Box(
                        Modifier
                            .width(seg)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = interactions[index],
                                indication = null,
                            ) { onSelect(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        val sel = index == selected
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
