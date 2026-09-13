package com.mdot.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdot.app.R
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SegmentBar
import kotlinx.coroutines.launch

/**
 * 首页卡片 + 底栏配置合并页（v0.6.7）：双页签（首页 / 底栏）。
 * 外观页两个入口共用本页——入口仅决定默认页签（首页卡片入口落「首页」，底栏配置入口落「底栏」）。
 */
@Composable
fun HomeBottomConfigScreen(
    initialTab: Int,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialTab.coerceIn(0, 1), pageCount = { 2 })

    Column(Modifier.fillMaxSize()) {
        JiabanTopBar(
            title = null,
            titleContent = {
                SegmentBar(
                    labels = listOf(
                        stringResource(R.string.home_bottom_tab_home),
                        stringResource(R.string.home_bottom_tab_bottombar),
                    ),
                    selected = pagerState.currentPage,
                    onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                    segWidth = 112.dp,
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
                0 -> HomeCardsPane()
                else -> BottomBarPane()
            }
        }
    }
}
