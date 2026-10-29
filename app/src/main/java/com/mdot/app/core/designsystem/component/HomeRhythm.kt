package com.mdot.app.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mdot.app.core.designsystem.Spacing

/**
 * ══ 首页「数据区」的垂直节奏（跨页面复用）════════════════════════════
 *
 * 首页单列布局里，「数据区」是**铺在页面背景上**的一段（周期胶囊 → hero 大数字 → 今日胶囊），
 * 其后才是第一张卡（收入卡）。「我的」页想让**个人资料铺在同一块区域**上，好让
 * 「本年数据卡」落在与「收入卡」相同的 y（两页观感对齐，见 docs/03 §5.5）。
 *
 * 所以这里把这段高度按**首页实际用到的排印行高 + 内边距**算出来，而不是写死一个 dp：
 * - 两引擎排印不同（MIUIX 的 labelMedium 是 13sp）⇒ 写死会在某个引擎上偏；
 * - 首页调排版（换 hero 字号/加副行）时只改 [com.mdot.app.feature.home.DataSection]，
 *   这里跟着行高自动跟上，不用两边对数字。
 *
 * ⚠️ 与 `HomeScreen.DataSection` 一一对应，那边改结构（增删行/改内边距）时**必须同步这里**：
 * ```
 * 周期胶囊      labelMedium 行高 + 上下各 Spacing.xs(4)
 * 间距          pillToHero(6)
 * hero 大数字   displayLarge 行高（单位标签比它矮，不撑高）
 * 今日胶囊行    上 Spacing.s(8) + labelLarge 行高 + 上下各 Spacing.xs(4)
 * ```
 * 标准工时（默认卡内容 = 加班时长、无「综合工时」副行）下 ≈ 130dp。
 */
object HomeRhythmSpec {
    /** 周期胶囊与 hero 大数字之间的间距（对应 `HomeScreen.DataSection` 里的 `Spacer(6.dp)`） */
    val pillToHero = 6.dp
}

/**
 * 首页数据区的高度（见 [HomeRhythmSpec]）。返回**最小值**语义：调用方用 `heightIn(min = …)`，
 * 内容更高时以内容为准，不会被裁。
 */
@Composable
fun rememberHomeDataAreaHeight(): Dp {
    val typo = MaterialTheme.typography
    val density = LocalDensity.current
    return with(density) {
        val pill = typo.labelMedium.lineHeight.toDp() + Spacing.xs * 2
        val hero = typo.displayLarge.lineHeight.toDp()
        val today = Spacing.s + typo.labelLarge.lineHeight.toDp() + Spacing.xs * 2
        pill + HomeRhythmSpec.pillToHero + hero + today
    }
}
