package com.mdot.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SegmentBar

/**
 * 外观 / 首页 / 底栏 合并页（v0.6.9 起为三页签）：外观页与「首页卡片」「底栏配置」的两个入口
 * 都进入本页，入口仅决定默认页签（外观→0、首页卡片→1、底栏配置→2）。
 * 页签用静态切换（不用 Pager）：NavHost 转场 forceMeasure 会把无限高约束传给 pager 页内容，
 * 页内 verticalScroll 会抛「infinite maximum height」（实测高概率崩溃）。
 */
@Composable
fun HomeBottomConfigScreen(
    initialTab: Int,
    onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 2)) }

    Column(Modifier.fillMaxSize()) {
        JiabanTopBar(
            title = null,
            titleContent = {
                SegmentBar(
                    labels = listOf(
                        stringResource(R.string.appearance_title),
                        stringResource(R.string.home_bottom_tab_home),
                        stringResource(R.string.home_bottom_tab_bottombar),
                    ),
                    selected = tab,
                    onSelect = { tab = it },
                    segWidth = 84.dp,
                    position = tab.toFloat(),
                )
            },
            showBack = true,
            onBack = onBack,
        )
        when (tab) {
            0 -> AppearanceTabContent()
            1 -> HomeCardsTabContent()
            else -> BottomBarTabContent()
        }
    }
}

/** 外观页签：与独立外观页一致的上下留白 */
@Composable
private fun AppearanceTabContent() {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(Spacing.m))
        AppearancePane()
    }
}

@Composable
private fun HomeCardsTabContent() {
    HomeCardsPane()
}

@Composable
private fun BottomBarTabContent() {
    BottomBarPane()
}
