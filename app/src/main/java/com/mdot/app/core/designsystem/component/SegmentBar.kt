package com.mdot.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
 * tonal 轨道 + primaryContainer 浮动滑块，滑块 offset 跟随 [position] 连续位移
 * （pager 传 currentPage+offsetFraction 可随手势实时跟随；纯页签传动画值即得弹簧滑动）。
 * 按压反馈即滑块位移本身，不叠涟漪。
 *
 * @param position 连续选中位置（0 起；如 0.35 表示滑在 0/1 段之间）
 */
@Composable
fun SegmentBar(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    segWidth: Dp = 96.dp,
    position: Float = selected.toFloat(),
) {
    Box(
        modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .width(segWidth)
                .height(34.dp)
                .offset(x = (segWidth + 4.dp) * position)
                .clip(RoundedCornerShape(Radius.pill))
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Row(
            Modifier.height(34.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val interactions = remember(labels.size) { List(labels.size) { MutableInteractionSource() } }
            labels.forEachIndexed { index, label ->
                Box(
                    Modifier
                        .width(segWidth)
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
