package com.mdot.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.miuix.SquircleShape
import com.mdot.app.domain.model.ThemeEngine
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown

/**
 * ══ 引擎样式分发（MD3 / MIUIX 的形状与图标差异入口，v0.7.8）═══
 *
 * [engineShape]：圆角**形状**工厂——MD3 = 标准 [RoundedCornerShape]，
 * MIUIX = [SquircleShape]（超椭圆连续圆角，HyperOS 观感）。圆角**尺寸**仍走 `Radius`
 * 令牌（随引擎切 16dp 等，见 Tokens.kt）。全部 `RoundedCornerShape(Radius.*)` 调用点
 * 统一换成 `engineShape(Radius.*)`：两引擎各自所见即所得，不靠 clip 兜底。
 *
 * [EngineIcons]：图标**风格**分发——MIUIX 用 miuix 图标词表（`ic_ms_miuix_*`，
 * 路径移植自开源项目 miuix 的 `icon/basic`），MD3 用 Material Symbols（`ic_ms_*`）。
 * 一律编译期 `when`/分支引用（硬规则「位图资源禁用动态查找」），两引擎图标均被引用
 * （IconContractTest 孤儿守门）。
 *
 * 引擎状态的唯一来源是 `Radius.applyEngine`（JiabanTheme 组装前写入；快照状态，
 * 读点在组合内自动订阅重组）。
 */

/** 按当前引擎返回圆角形状（四角同径；语义同 `RoundedCornerShape(Dp)`） */
fun engineShape(cornerRadius: Dp): Shape =
    engineShape(cornerRadius, cornerRadius, cornerRadius, cornerRadius)

/**
 * 按当前引擎返回圆角形状（四角可不等；参数名与 `RoundedCornerShape` 对齐，可命名传参）。
 * 刻意**不给默认值**：早期版本四角带默认值，`SquircleShape(8.dp)` 这类单参调用会落进
 * 「只给 topStart 赋值」的重载 → 只有左上角圆角（真机验收 bug），无默认值后漏传角直接编译错。
 */
fun engineShape(
    topStart: Dp,
    topEnd: Dp,
    bottomEnd: Dp,
    bottomStart: Dp,
): Shape = when (Radius.engine) {
    ThemeEngine.MIUIX -> SquircleShape(topStart, topEnd, bottomEnd, bottomStart)
    ThemeEngine.MD3 -> RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
}

/**
 * 引擎相关图标：MD3 = Material Symbols 资源（res id 同时喂图标契约的引用层守门）；
 * MIUIX = **miuix-icons 库本体**（top.yukonga.miuix.kmp:miuix-icons 的 ImageVector，
 * 经 rememberVectorPainter 渲染，`Icon` 的 tint 照常着色）。调用点：`Icon(EngineIcons.chevron(), …)`。
 *
 * ⚠️ 返回箭头不分引擎（2026-09-30 曾做 miuix 化，用户反馈「原来的好看」已回退）；
 * 行箭头/选中勾随引擎。
 */
object EngineIcons {
    /** MD3 行尾箭头资源 id（图标契约引用层） */
    fun chevronRes(): Int = R.drawable.ic_ms_keyboard_arrow_right

    /** MD3 选中勾资源 id（图标契约引用层） */
    fun checkRes(): Int = R.drawable.ic_ms_check

    /** 行尾箭头：MD3 = Material `keyboard_arrow_right`，MIUIX = miuix-icons `ArrowRight` */
    @Composable
    fun chevron(): Painter = when (Radius.engine) {
        ThemeEngine.MIUIX -> rememberVectorPainter(MiuixIcons.Basic.ArrowRight)
        ThemeEngine.MD3 -> painterResource(chevronRes())
    }

    /** 选中勾：MD3 = Material `check`，MIUIX = miuix-icons `Check` */
    @Composable
    fun check(): Painter = when (Radius.engine) {
        ThemeEngine.MIUIX -> rememberVectorPainter(MiuixIcons.Basic.Check)
        ThemeEngine.MD3 -> painterResource(checkRes())
    }

    /** 交换/切换（首页顶栏「工时制度」入口）：MIUIX = miuix-icons `ArrowUpDown`（10×16dp 纵向双箭头） */
    @Composable
    fun swap(): Painter = when (Radius.engine) {
        ThemeEngine.MIUIX -> rememberVectorPainter(MiuixIcons.Basic.ArrowUpDown)
        ThemeEngine.MD3 -> painterResource(R.drawable.ic_ms_swap_horiz)
    }
}

/**
 * 弹层进出场空间档（[androidx.compose.material3.MaterialTheme.motionScheme] 三档内选档，
 * 不绕动效体系）：**MIUIX = fastSpatial**（用户 2026-09-30 反馈 miuix 下弹层出来太慢）、
 * **MD3 = slowSpatial（原速，用户明示不动）**。记加班/工地弹层与 SheetBackdropLayer 背景进度同用。
 */
@Composable
fun <T> sheetSpatialSpec(): FiniteAnimationSpec<T> = when (Radius.engine) {
    ThemeEngine.MIUIX -> MaterialTheme.motionScheme.fastSpatialSpec()
    ThemeEngine.MD3 -> MaterialTheme.motionScheme.slowSpatialSpec()
}

/**
 * Switch 配色（8 处 `Switch` 调用点统一走这里）：
 * MIUIX = HyperOS 开关观感（白圆钮常驻 + 强调色轨道 / 凹陷灰轨道，对齐 miuix Switch
 * 的 white thumb + track 二色语言）；MD3 = `SwitchDefaults.colors()` 默认本尊，行为不变。
 */
@Composable
fun engineSwitchColors(): SwitchColors = when (Radius.engine) {
    ThemeEngine.MIUIX -> SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        checkedBorderColor = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        uncheckedBorderColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    ThemeEngine.MD3 -> SwitchDefaults.colors()
}

/**
 * 对话框容器底色（M3 `AlertDialog` 的 `containerColor` 统一走这里）：
 * MIUIX = 白卡 `surfaceContainer`（对齐 miuix Dialog 白卡观感——M3 默认取
 * `surfaceContainerHigh`，在 miuix 映射里是「卡内凹陷」灰档，对话框会发灰）；
 * MD3 = [AlertDialogDefaults.containerColor]（即 M3 默认值，行为分毫不变）。
 */
@Composable
fun dialogContainerColor(): Color = when (Radius.engine) {
    ThemeEngine.MIUIX -> MaterialTheme.colorScheme.surfaceContainer
    ThemeEngine.MD3 -> AlertDialogDefaults.containerColor
}

/** 当前是否 MIUIX 引擎（组件按引擎分流用；与 [Radius.engine] 同源） */
val engineIsMiuix: Boolean get() = Radius.engine == ThemeEngine.MIUIX

/**
 * 「强调卡」（hero 数据卡/待结卡等）的**卡底**：MIUIX 走中性 surfaceContainer、
 * MD3 保持 primaryContainer —— 收色口径（2026-09-30 用户）：HyperOS 里强调色只做"点"不做"面"。
 */
@Composable
fun emphasisCardSurface() = if (engineIsMiuix) MaterialTheme.colorScheme.surfaceContainer
else MaterialTheme.colorScheme.primaryContainer

/** 强调卡内的文字色：MD3 卡底为强调色 ⇒ onPrimaryContainer（与改动前逐像素一致）；MIUIX 卡底中性 ⇒ onSurface */
@Composable
fun emphasisCardInk() = if (engineIsMiuix) MaterialTheme.colorScheme.onSurface
else MaterialTheme.colorScheme.onPrimaryContainer

/**
 * hero 卡内**浅层胶囊**（信息胶囊）的底色——本口径的**唯一出处**。
 *
 * = `surface` α0.6 叠在卡底上 ⇒ 卡面上的一层**亮**色薄片；配 [emphasisCardInk] 全不透明文字。
 *
 * 沿革（2026-10-09 用户指出「记月页胶囊是暗的、统计和明细页是亮的」，两种主题都有）：
 * 记月汇总卡的考勤徽章与「较上月」药丸此前用的是 `emphasisCardInk().copy(alpha = 0.12f)`
 * ——**深色墨**叠浅卡底 = 暗蓝灰一层；而统计/明细 hero 的分档胶囊（`TierChip`）用
 * `surface.copy(alpha = 0.6f)` = 亮一层。同样的卡底、同样叫"信息胶囊"，两种观感。
 * 现两边统一到本函数；**新增 hero 胶囊一律走它**，别再各写各的 alpha。
 *
 * ⚠️ 不适用于：hero 卡上的**进度条**（记月「周期进度」用 ink α0.12/0.55，是轨道不是胶囊）、
 * 以及卡内无底色的次级文字（那些用 `emphasisCardInk().copy(alpha = …)` 降层级）。
 */
@Composable
fun heroChipSurface(): androidx.compose.ui.graphics.Color =
    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)

/**
 * 次级标签文字色（农历/节日、计划标注等"挂靠信息"）：
 * - MD3：`outline`（不透明中灰，与改动前逐像素一致）；
 * - MIUIX：`onSurfaceVariant`（60% 黑 / 50% 白，次级文字角色）。
 *   ⚠️ **别在 MIUIX 用 `outline` 写字**：该方案的 `outline` 是**分隔线**级浅灰（#D9D9D9 / #404040），
 *   当文字色等于白底白字；2026-09-30 用户报「miuix 模式下月历页农历日期文字太淡，看不清」。
 */@Composable
fun secondaryLabelColor(): Color =
    if (engineIsMiuix) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.outline

/**
 * 筛选/次要 chip（`FilterChip`）的配色：**MIUIX 收色** —— 选中态 = 中性底 + 主色字/图标
 * （强调色只做"点"，见收色口径 2026-09-30）；MD3 保持 M3 默认（选中 `secondaryContainer`，
 * 与改动前逐像素一致）。
 */
@Composable
fun jiabanFilterChipColors() = if (engineIsMiuix) {
    androidx.compose.material3.FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        selectedLabelColor = MaterialTheme.colorScheme.primary,
        selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
    )
} else {
    androidx.compose.material3.FilterChipDefaults.filterChipColors()
}
