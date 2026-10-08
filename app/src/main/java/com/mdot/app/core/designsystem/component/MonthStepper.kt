package com.mdot.app.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.engineShape

/**
 * 区间胶囊的**固定高度** = 24dp（内容 16dp + 上下各 `Spacing.xs` 4dp，真机实测 66px @density2.75）。
 *
 * ⚠️ 本组件的行**刻意不做自适应高度**：两侧 `JiabanIconButton` 的 48dp 最小触达会把行撑到 48dp，
 * 胶囊在其中居中 ⇒ 比"只有胶囊、没有箭头"的工地口径（`feature/detail/RangePill`）**低 12dp**，
 * 两种口径的胶囊上下留白对不上（用户要求统一）。
 * 故行高锁到本值，两种口径的胶囊便落在同一垂直位置、上下留白完全相同。
 *
 * 代价与补偿：箭头按钮的**触达区仍是 48dp**——最小触达尺寸修饰符不受父行高约束，
 * 只是绘制时不裁剪地溢出（按钮本身无底色，视觉上看不出来），无障碍不受影响；
 * 胶囊文案另设 `maxLines = 1`，标签再长也只会被截断、不会把行撑破。
 */
val RangeCapsuleHeight: androidx.compose.ui.unit.Dp
    get() = 24.dp

/**
 * 时间段切换器（`[‹] (标签 ▾) [回本月] [›]`）——**记月页与明细页共用同一实现**。
 *
 * 两页语义其实同构，只是取数粒度不同：
 * - 记月页：一期 = 一个自然月，[label] = 「2026年10月」；
 * - 明细页：一期 = 一个考勤周期，[label] = 「10月1日 – 10月31日」。
 * 故步进/跳月/回本期的交互完全一致，**禁止两页各画一套**（同 `SiteRangeChips` 的共用约定——
 * 同功能必须同样式同实现，否则必然漂移）。
 *
 * 布局口径（**当前区间胶囊恒定居中**）：左右各放一个**等权重**弹性空位，
 * 胶囊与「回本期」夹在中间。等权重保证两侧余量相等 ⇒ 胶囊居中；
 * 同时权重会把两侧**压缩到 0**，让内容自己决定最小宽度 —— 这比「`Box` 里 Center 叠 CenterEnd」
 * 安全：后者在窄屏（360dp）上「胶囊 + 回本期 + 两个箭头」会互相压住。
 * ⚠️ **不要改成 `Arrangement.Center` 的顺序排布**：那样「回本期」一出现就把胶囊整体挤偏
 * （实测左移约 100px，步进时画面跳一下）。
 *
 * 样式口径：控件是**次要信息**（收色口径：只做「点」不做「面」，层次靠灰阶 + 字重/留白），
 * 故胶囊用 `labelMedium` + `surfaceContainerHigh` 药丸底 + `IconSpec.inline` 图标，
 * **不做强调色实底**（一屏最多一个实底留给主按钮）；「回本期」用主色文字胶囊（可点的「点」，
 * 与记录弹层日期选择里的「回今天」同款，比 FilterChip 省一截宽度）。
 *
 * @param label 当前区间文案（由调用方按自己的粒度格式化——组件不猜「月」还是「周期」）
 * @param onBackToCurrent 传 null 表示当前已在最新一期，不渲染「回去」胶囊
 * @param backToCurrentLabel 「回去」胶囊的文案。默认「回本月」——**调用方在粒度不是月时必须覆盖**：
 *   统计页按考勤周期时该说「回本期」，按年时该说「回今年」
 */
@Composable
fun MonthStepper(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
    nextEnabled: Boolean = true,
    onBackToCurrent: (() -> Unit)? = null,
    backToCurrentLabel: String = stringResource(R.string.month_stepper_back_to_current),
    prevContentDescription: String = stringResource(R.string.month_stepper_prev),
    nextContentDescription: String = stringResource(R.string.month_stepper_next),
) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    // 动效统一走 motionScheme 三档（硬规则 7：禁止硬编码 tween/时长）；
    // 泛型 spec 是 @Composable，须先在 Composable 上下文取值再传给 AnimatedVisibility
    val backFadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val backSlideSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    Row(
        // 高度锁到胶囊高度（见 RangeCapsuleHeight）：否则箭头按钮的 48dp 最小触达撑高整行、
        // 胶囊被居中而比工地口径低 12dp
        modifier = modifier.fillMaxWidth().height(RangeCapsuleHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlexSlot()
        JiabanIconButton(onClick = onPrev, enabled = true) {
            Icon(
                painterResource(R.drawable.ic_ms_keyboard_arrow_left),
                prevContentDescription,
                tint = ink,
                modifier = Modifier.size(IconSpec.inline),
            )
        }
        // 当前区间胶囊（居中主体）——带日历图标标识"这是时间段"，右侧 ▾ 提示可点开选区间
        Row(
            modifier = Modifier
                .clip(engineShape(Radius.pill))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onOpenPicker)
                .padding(horizontal = Spacing.m, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_ms_calendar_month),
                null,
                tint = ink,
                modifier = Modifier.size(IconSpec.inline),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = ink,
                // 行高已锁死，长标签只截断、不换行（换行会把行撑破、破坏对齐）
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                painterResource(R.drawable.ic_ms_expand_more),
                stringResource(R.string.month_stepper_pick_cd),
                tint = ink,
                modifier = Modifier.size(IconSpec.inline),
            )
        }
        // 「回本期」：进出都要有动效。**整块 AnimatedVisibility 永远在组合里**（不带 if 包裹）——
        // AnimatedVisibility 自己会把子内容留到退场动画结束，故点击回调在退场期间仍然有效；
        // 若外面再套 `if (可见)`，节点会在点击瞬间被摘掉，退场动画根本不会播（就是「突兀消失」的成因）。
        AnimatedVisibility(
            visible = onBackToCurrent != null,
            enter = fadeIn(backFadeSpec) + expandHorizontally(animationSpec = backSlideSpec),
            exit = fadeOut(backFadeSpec) + shrinkHorizontally(animationSpec = backSlideSpec),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    backToCurrentLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(engineShape(Radius.pill))
                        .clickable { onBackToCurrent?.invoke() }
                        .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                )
            }
        }
        JiabanIconButton(onClick = onNext, enabled = nextEnabled) {
            Icon(
                painterResource(R.drawable.ic_ms_keyboard_arrow_right),
                nextContentDescription,
                tint = if (nextEnabled) ink else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(IconSpec.inline),
            )
        }
        FlexSlot()
    }
}

/**
 * 弹性空位：两侧各一个、**权重相同** ⇒ 余量均分 ⇒ 中间内容居中；空间不足时压缩到 0 而不挤压内容。
 * 不能写成 `Spacer(Modifier.weight(1f))`——`RowScope.Spacer` 带 `weight` 重载在库版本间解析不稳，
 * 这里用 `Box` 显式挂 `weight` 更稳（同 `MonthPickDialog` 月份网格的补位写法）。
 */
@Composable
private fun RowScope.FlexSlot() {
    Box(Modifier.weight(1f))
}
