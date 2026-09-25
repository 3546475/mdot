package com.mdot.app.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.mdot.app.core.designsystem.ChoicePillSpec
import com.mdot.app.core.designsystem.IconSpec

/**
 * 单个药丸选项：图标 + 标签。资源 id 随身携带、显示点才解析（硬规则 10：非 Composable 数据用
 * `labelRes: Int` 携带）。
 */
data class ChoicePillOption(
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
)

/**
 * 图标选项药丸行（2026-09-23 按用户参考图新增）：「图标 + 文字」圆角药丸并排**等分**一行，
 * 单选其一。外观页「深浅色 / 弹层背景」两行在用（替代这两处的 [SegmentBar]）。
 *
 * 形态与取舍（规格见 [ChoicePillSpec]，数值来自对参考图的像素级量测，经用户两轮指正定稿）：
 * - **圆角三档联动——「三个按钮是联动的」指的就是它**：**选中 = 四角全圆胶囊**
 *   （`cornerSelected` = 半高）；**未选中的行首左缘 / 行尾右缘 = 全圆端**
 *   （`cornerEdge`，行整体轮廓恒为胶囊端）、**其余内侧角与中间项 = 8dp 圆角矩形**
 *   （`cornerIdle`）；左右角各自 animateDpAsState，切换选中时各行互相形变；
 *   缝是均匀的 [ChoicePillSpec.gap]（4dp，参考图真实 ≈12px），不随选中变
 *   （⚠️ 曾按截断假象误做过「选中旁 8dp/未选中间 4dp」的联动缝，已撤销）。
 * - **选中整颗实底**（primary + onPrimary），未选中是「板」上的墨色（surfaceContainerHigh +
 *   onSurface）——选中与否不靠描边/勾选表达，就是「实心 vs 空心」的语言，与参考图一致；
 *   底色/墨色均以 motionScheme effects 档做颜色过渡，切换不生硬突变。
 * - **等分而非按内容宽**：`weight(1f)` 瓜分行宽，三行右缘恒与卡片内容右缘对齐（docs/17
 *   「统一右边界」原则）；标签 maxLines=1，超宽字号下尾部省略兜底。
 * - 语义是「从 N 选 1」单选：`selectable(role = RadioButton)` 而不是 clickable，
 *   无障碍读屏能读出选中态；按压反馈 [pressScale] 与 selectable **共用同一 interactionSource**
 *   （硬规则 7），涟漪仍由 LocalIndication 提供。
 *
 * **2026-09-24 起（用户定，docs/03 §16）：之后的多选一「选项」一律以本组件为模板**——
 * 图标 + 文字、圆角三档联动、选中实底；[SegmentBar] 只保留现役**页签/滑块**场景
 * （统计页顶栏页签、备份双页签、制度设定多页签——滑块随手势连续跟随是它独有），
 * **不再新增选项行用例**。分工：本组件图标承担语义（太阳/月亮/半明半暗…）、选项各自独立成颗；
 * SegmentBar 是「凹槽里浮起一颗滑块」的页签语言、无图标槽。
 */
@Composable
fun ChoicePillRow(
    options: List<ChoicePillOption>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ChoicePillSpec.gap),
    ) {
        val interactions = remember(options.size) { List(options.size) { MutableInteractionSource() } }
        options.forEachIndexed { index, option ->
            val isSelected = index == selected
            // 圆角联动（三档）：选中 = 四角全圆胶囊；未选中的行首左缘/行尾右缘 = 全圆端，
            // 其余内侧角与中间项 = 8dp——各行随选中位互相形变，行整体轮廓恒为胶囊端
            val cornerL by animateDpAsState(
                targetValue = when {
                    isSelected -> ChoicePillSpec.cornerSelected
                    index == 0 -> ChoicePillSpec.cornerEdge
                    else -> ChoicePillSpec.cornerIdle
                },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "choicePillCornerL",
            )
            val cornerR by animateDpAsState(
                targetValue = when {
                    isSelected -> ChoicePillSpec.cornerSelected
                    index == options.lastIndex -> ChoicePillSpec.cornerEdge
                    else -> ChoicePillSpec.cornerIdle
                },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "choicePillCornerR",
            )
            val container by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "choicePillContainer",
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "choicePillContent",
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(ChoicePillSpec.pillHeight)
                    // pressScale 必须排在 background 之前：graphicsLayer 只缩放它之后的绘制，
                    // 放晚了药丸底色不跟着缩
                    .pressScale(interactions[index])
                    .clip(
                        RoundedCornerShape(
                            topStart = cornerL,
                            topEnd = cornerR,
                            bottomEnd = cornerR,
                            bottomStart = cornerL,
                        )
                    )
                    .background(container)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        interactionSource = interactions[index],
                        indication = LocalIndication.current,
                    ) { onSelect(index) }
                    .padding(horizontal = ChoicePillSpec.paddingH),
                horizontalArrangement = Arrangement.spacedBy(
                    ChoicePillSpec.contentGap,
                    Alignment.CenterHorizontally,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(option.iconRes),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(IconSpec.dense),
                )
                Text(
                    stringResource(option.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                    maxLines = 1,
                )
            }
        }
    }
}
