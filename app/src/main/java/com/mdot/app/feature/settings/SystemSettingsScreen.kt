package com.mdot.app.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SegmentBar
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.feature.comp.CompBalancePane
import com.mdot.app.feature.payroll.PayrollPane
import com.mdot.app.feature.site.SiteProjectsPane
import com.mdot.app.feature.site.SiteSettlementPane
import kotlinx.coroutines.launch

/** 按工时制度页签（v0.6.12：取代原工时设置入口列表，顶栏齿轮进入） */
private enum class SystemTab(val labelRes: Int) {
    SALARY(R.string.sysset_tab_salary),
    COMP(R.string.sysset_tab_comp),
    CYCLE(R.string.sysset_tab_cycle),
    WORKDAYS(R.string.sysset_tab_workdays),
    SHIFTS(R.string.sysset_tab_shifts),
    PROJECTS(R.string.site_projects_title),
    SETTLEMENT(R.string.site_settlement_title),
}

/**
 * 工时制度设定多页签页（顶栏齿轮进入；标题=当前制度名）：
 * 标准=工资/调休/周期/工作日/班次；综合=工资/周期/工作日/班次；
 * 小时工=工资/周期/班次（无工作日：纯时薪无档位）；工地=项目/结算（点工标准在项目设置内维护）。
 * 页签 HorizontalPager 横滑、滑块连续跟随（同统计页）；各页根 fillMaxSize 顶对齐、自带滚动互不嵌套
 * （docs/11 007/008：严禁页内再套滚动，防 Pager 垂直居中留白）。
 */
@Composable
fun SystemSettingsScreen(
    onBack: () -> Unit,
    onOpenProject: (Long) -> Unit = {},
    hub: SettingsHubViewModel = hiltViewModel(),
) {
    val salary by hub.salary.collectAsStateWithLifecycle()
    val system = salary.workSystem
    val tabs = when (system) {
        WorkSystem.STANDARD -> listOf(SystemTab.SALARY, SystemTab.COMP, SystemTab.CYCLE, SystemTab.WORKDAYS, SystemTab.SHIFTS)
        WorkSystem.COMPREHENSIVE -> listOf(SystemTab.SALARY, SystemTab.CYCLE, SystemTab.WORKDAYS, SystemTab.SHIFTS)
        WorkSystem.HOURLY -> listOf(SystemTab.SALARY, SystemTab.CYCLE, SystemTab.SHIFTS)
        WorkSystem.SITE -> listOf(SystemTab.PROJECTS, SystemTab.SETTLEMENT)
    }
    // 段宽按页签数收缩：总宽 = n×seg + (n-1)×4 + 8 ≤ 360dp 屏的内容区 328dp
    val segWidth = when (tabs.size) {
        2 -> 116.dp
        3 -> 92.dp
        4 -> 72.dp
        else -> 58.dp
    }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    Column(Modifier.fillMaxSize()) {
        JiabanTopBar(title = system.displayName, onBack = onBack)
        Spacer(Modifier.height(Spacing.m))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SegmentBar(
                labels = tabs.map { stringResource(it.labelRes) },
                selected = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                segWidth = segWidth,
                position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
            )
        }
        Spacer(Modifier.height(Spacing.s))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            when (tabs[page]) {
                SystemTab.SALARY -> PayrollPane()
                SystemTab.COMP -> CompBalancePane()
                SystemTab.CYCLE -> CyclePane()
                SystemTab.WORKDAYS -> WorkdaysPane()
                SystemTab.SHIFTS -> ShiftsPane()
                SystemTab.PROJECTS -> SiteProjectsPane(onOpenProject = onOpenProject)
                SystemTab.SETTLEMENT -> SiteSettlementPane()
            }
        }
    }
}
