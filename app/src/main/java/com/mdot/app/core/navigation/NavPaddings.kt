package com.mdot.app.core.navigation

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import com.mdot.app.core.designsystem.BottomBarSpec

/** 内容底部避让底栏（03 文档 §4.1：滚动内容从底栏下方穿过）；
 *  无底栏形态（showBottomBar=false）只避让系统导航栏，避免底部大片空隙 */
fun Modifier.contentBottomPadding(showBottomBar: Boolean = true): Modifier = composed {
    if (showBottomBar) {
        padding(bottom = bottomBarContentPaddingValues().calculateBottomPadding())
    } else {
        navigationBarsPadding()
    }
}

/** 供 LazyColumn contentPadding 使用的底部避让值 */
@Composable
fun bottomBarContentPaddingValues(): androidx.compose.foundation.layout.PaddingValues {
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return androidx.compose.foundation.layout.PaddingValues(
        bottom = BottomBarSpec.height + BottomBarSpec.bottomMargin + 8.dp + navBar,
    )
}

/** 按底栏形态返回内容底部避让值（无底栏时只避让系统导航栏） */
@Composable
fun contentPaddingValues(showBottomBar: Boolean): androidx.compose.foundation.layout.PaddingValues {
    return if (showBottomBar) {
        bottomBarContentPaddingValues()
    } else {
        androidx.compose.foundation.layout.PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        )
    }
}
