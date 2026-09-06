package com.mdot.app.core.designsystem.component

import androidx.compose.ui.res.stringResource
import com.mdot.app.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mdot.app.core.designsystem.Spacing

/**
 * 统一顶栏：中间当前页面名（回答"我在哪"）。
 * - showBack=true：左侧返回箭头（回答"怎么回去"），用于所有非底栏页面；
 * - showBack=false：底栏一级页面（回去的方式就是底栏）；
 * - actions：右侧动作区（如首页的设置齿轮）；
 * - leading：showBack=false 时的左侧内容（替代返回箭头位置，如首页的"标准工时"入口）；
 * - title=null：一级页面无标题形态，不渲染任何文字、不留占位。
 * 自带状态栏避让。
 */
@Composable
fun JiabanTopBar(
    title: String?,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    onBack: () -> Unit = {},
    actions: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Spacing.s)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            showBack -> {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.ds_topbar_back_cd),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            leading != null -> leading()
            else -> Spacer(Modifier.width(48.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        if (actions != null) {
            actions()
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}
