package com.mdot.app.core.designsystem.component

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.mdot.app.R
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    CenterAlignedTopAppBar(
        title = {
            when {
                titleContent != null -> titleContent()
                title != null -> {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
        navigationIcon = {
            when {
                showBack -> IconButton(onClick = onBack) {
                    Icon(
                        painterResource(R.drawable.ic_ms_arrow_back),
                        contentDescription = stringResource(R.string.ds_topbar_back_cd),
                        modifier = Modifier.size(24.dp),
                    )
                }
                leading != null -> leading()
                else -> Spacer(Modifier.width(48.dp))
            }
        },
        actions = {
            if (actions != null) actions() else Spacer(Modifier.width(48.dp))
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
        windowInsets = WindowInsets.statusBars.only(WindowInsetsSides.Top),
    )
}
