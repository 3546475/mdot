package com.mdot.app.core.designsystem.component

import com.mdot.app.R
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.domain.model.WorkSystem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn

/** 一级页面顶栏高度（不含状态栏） */
val TopBarHeight = 56.dp

/**
 * 一级页面统一顶栏（M3 Expressive）：无标题；
 * 左侧「标准工时 ⇄」tonal 胶囊入口，右侧圆形 tonal 图标按钮（设置）。
 * 挂在 AppRoot 的 NavHost 之外，页面切换时不参与转场动画、保持不动。
 * 不透底：使用 surface 实底，滚动内容从下方穿过时被遮住。
 */
@Composable
fun TopLevelBar(
    workSystem: WorkSystem = WorkSystem.STANDARD,
    onOpenWorkSystem: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 响应式：背景（AppRoot 提供）全宽，内容限宽居中与页面内容对齐（docs 03 §3.2）
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    Row(
        modifier = Modifier
            .widthIn(max = AdaptiveSpecs.contentMaxWidth)
            .fillMaxWidth()
            .statusBarsPadding()
            .height(TopBarHeight)
            .padding(horizontal = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 工时制度：tonal 胶囊（clickable 置于 Surface 内部，涟漪被形状裁剪）
        val switchInteraction = remember { MutableInteractionSource() }
        Surface(
            shape = RoundedCornerShape(Radius.pill),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.pressScale(switchInteraction, pressedScale = 0.94f),
        ) {
            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = switchInteraction,
                        indication = LocalIndication.current,
                        onClick = onOpenWorkSystem,
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    workSystem.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Icon(
                    painterResource(R.drawable.ic_ms_swap_horiz),
                    contentDescription = stringResource(R.string.ds_topbar_switch_cd),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        // 设置：圆形 tonal 图标按钮（M3E 改造：换 FilledTonalIconButton，涟漪/尺寸由组件自带）
        val settingsInteraction = remember { MutableInteractionSource() }
        FilledTonalIconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(40.dp)
                .pressScale(settingsInteraction, pressedScale = 0.9f),
        ) {
            Icon(
                painterResource(R.drawable.ic_ms_settings),
                contentDescription = stringResource(R.string.ds_topbar_settings_cd),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
    }
}

/**
 * 一级页面内容顶部避让：固定顶栏（状态栏 + 56dp）不在页面内，
 * 页面自身内容需要顶部让出同样的高度，避免被固定顶栏遮住。
 */
@Composable
fun topLevelTopGap(): androidx.compose.ui.unit.Dp {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return statusBar + TopBarHeight
}
