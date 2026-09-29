package com.mdot.app.core.designsystem.component

import com.mdot.app.core.designsystem.engineShape
import com.mdot.app.domain.model.ThemeEngine
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.mdot.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Spacing
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.material3.LocalContentColor

/**
 * 统一顶栏：中间当前页面名（回答"我在哪"）。
 * - showBack=true：左侧返回箭头（回答"怎么回去"），用于所有非底栏页面；
 * - showBack=false：底栏一级页面（回去的方式就是底栏）；
 * - actions：右侧动作区（如首页的设置齿轮）；
 * - leading：showBack=false 时的左侧内容（替代返回箭头位置，如首页的"标准工时"入口）；
 * - title=null：一级页面无标题形态，不渲染任何文字、不留占位。
 * M3E 改造：内部实现换 M3 `CenterAlignedTopAppBar`（稳定组件），
 * 主题切 `MaterialExpressiveTheme` 后自动获得 M3E 顶栏动效；
 * 状态栏避让、标题居中、两侧对称占位与旧实现保持一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JiabanTopBar(
    title: String?,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    onBack: () -> Unit = {},
    actions: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    /** 自定义标题区内容（如统计页的分段控件）；非空时覆盖 title 文本 */
    titleContent: (@Composable () -> Unit)? = null,
) {
    // 标题区（两引擎共用同一条胶囊实现，规格见下方注释）
    val titleSlot: @Composable () -> Unit = {
        when {
            titleContent != null -> titleContent()
            title != null -> {
                // 2026-09-30 用户定稿：二级页标题 = SegmentBar「**唯一一个选中页签**」的观感
                // ——凹槽轨道（sunkenWell）里浮一颗滑块，滑块宽度跟随标题文字；
                // 令牌与 SegmentBar 逐条对齐（轨道 gap=4dp、labelLarge Semibold、
                // 滑块/文字色随引擎：MIUIX=白滑块+onSurface，MD3=primaryContainer+onPrimaryContainer）。
                Box(
                    Modifier
                        .clip(engineShape(Radius.pill))
                        .sunkenWell(engineShape(Radius.pill))
                        .padding(TopBarTrackGap),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = titleSliderTextColor(),
                        maxLines = 1,
                        modifier = Modifier
                            .clip(engineShape(Radius.pill))
                            .background(titleSliderColor())
                            .padding(horizontal = Spacing.l, vertical = Spacing.s),
                    )
                }
            }
        }
    }
    // 左右两侧槽（引擎共用）：空位用 48dp 占位，保证标题居中（与 M3 顶栏同一对称策略）
    // 顶栏图标统一主题色（用户 2026-09-30「也要主题色，统一」）：返回箭头显式 tint；
    // 左右槽经 LocalContentColor 下发 primary ⇒ 各调用点的 Icon 默认继承主题色
    // （调用点若自行指定 tint 仍以显式为准）。
    val topIconTint = MaterialTheme.colorScheme.primary
    val leadingSlot: @Composable () -> Unit = {
        androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides topIconTint) {
            when {
                showBack -> IconButton(onClick = onBack) {
                    Icon(
                        painterResource(R.drawable.ic_ms_arrow_back),
                        contentDescription = stringResource(R.string.ds_topbar_back_cd),
                        tint = topIconTint,
                    )
                }
                leading != null -> leading()
                else -> Spacer(Modifier.width(48.dp))
            }
        }
    }
    val actionsSlot: @Composable () -> Unit = {
        androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides topIconTint) {
            if (actions != null) actions() else Spacer(Modifier.width(48.dp))
        }
    }

    // MIUIX：**自绘顶栏**，高度走本 App 的 TopBarHeight(56dp)——
    // 与一级页的 TopLevelBar、以及「统计页页签避让 = statusBar + TopBarHeight」自洽
    // （此前二级页用 M3 顶栏 = 64dp，与本 App 令牌不一致，即“顶栏不够 miuix”的实处）；
    // MD3 保持 M3 CenterAlignedTopAppBar（不动被点名的另一侧）。
    if (Radius.engine == ThemeEngine.MIUIX) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .height(TopBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingSlot()
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { titleSlot() }
            actionsSlot()
        }
        return
    }

    CenterAlignedTopAppBar(
        title = titleSlot,
        modifier = modifier.fillMaxWidth(),
        navigationIcon = leadingSlot,
        actions = { actionsSlot() },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
        windowInsets = WindowInsets.statusBars.only(WindowInsetsSides.Top),
    )
}


/** 顶栏标题滑块的轨道间隙：与 [SegmentBar] 的 gap 保持一致（4dp） */
private val TopBarTrackGap = 4.dp

/**
 * 顶栏标题滑块底色：与 [SegmentBar] 同规则——MIUIX 用白卡浮起（HyperOS 分段观感）、
 * MD3 用 primaryContainer 强调色浮起。
 */
@Composable
private fun titleSliderColor() = when (Radius.engine) {
    ThemeEngine.MIUIX -> MaterialTheme.colorScheme.surfaceContainer
    ThemeEngine.MD3 -> MaterialTheme.colorScheme.primaryContainer
}

/** 顶栏标题滑块上的文字色：与 [SegmentBar] 选中态同规则 */
@Composable
private fun titleSliderTextColor() = when (Radius.engine) {
    ThemeEngine.MIUIX -> MaterialTheme.colorScheme.onSurface
    ThemeEngine.MD3 -> MaterialTheme.colorScheme.onPrimaryContainer
}
