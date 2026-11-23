package com.mdot.app.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.WorkHeatmap
import com.mdot.app.domain.model.WorkSystem
import java.time.LocalDate

/** 首页本周柱状卡：与统计页同款共享组件与同款接线（点柱出浮窗 / 长按进记加班弹层） */
@Composable
internal fun HomeWeekBarCard(
    bars: List<com.mdot.app.core.designsystem.component.WeekBar>,
    workSystem: WorkSystem,
    onChartDay: (LocalDate) -> Unit,
) {
    var selected by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    com.mdot.app.core.designsystem.component.WeekBarCard(
        bars = bars,
        // 与统计页同一取值函数（不再自带一套数字格式，避免两页分叉）
        valueText = { com.mdot.app.core.designsystem.component.modeValueText(workSystem, it) },
        selectedLabel = selected,
        onSelect = { selected = if (selected == it) null else it },
        onDayLongPress = onChartDay,
        hintValueText = com.mdot.app.core.designsystem.component.chartHintText(workSystem),
    )
}

/** 首页热点图卡：与统计页一致（GitHub 贡献图风格、固定六个月窗口）＋同款接线（点击浮窗 / 长按记录） */
@Composable
internal fun HomeHeatmapCard(
    heatValues: Map<LocalDate, Float>,
    workSystem: WorkSystem,
    onChartDay: (LocalDate) -> Unit,
) {
    SectionCard {
        val today = LocalDate.now()
        WorkHeatmap(
            values = heatValues,
            start = today.withDayOfMonth(1).minusMonths(5),
            end = today,
            onDayLongPress = onChartDay,
            hintValueText = com.mdot.app.core.designsystem.component.chartHintText(workSystem),
        )
    }
}
