package com.mdot.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.mdot.app.R
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SegmentBar

/**
 * 外观 / 首页 / 底栏 合并页（v0.6.9 起为三页签）：外观页与「首页卡片」「底栏配置」的两个入口
 * 都进入本页，入口仅决定默认页签（外观→0、首页卡片→1、底栏配置→2）。
 * 页签用 HorizontalPager（可横滑）；各页签内容各自垂直滚动、互不嵌套——
 * 垂直滚动「套娃」在 NavHost 转场的无界测量下会抛「infinite maximum height」（实测高概率崩溃）。
 */
@Composable
fun HomeBottomConfigScreen(
    initialTab: Int,
    onBack: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialTab.coerceIn(0, 2), pageCount = { 3 })
    val scope = rememberCoroutineScope()

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
                    selected = pagerState.currentPage,
                    onSelect = { i -> scope.launch { pagerState.animateScrollToPage(i) } },
                    segWidth = 84.dp,
                    position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                )
            },
            showBack = true,
            onBack = onBack,
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 -> AppearanceTabContent()
                1 -> HomeCardsTabContent()
                else -> BottomBarTabContent()
            }
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
