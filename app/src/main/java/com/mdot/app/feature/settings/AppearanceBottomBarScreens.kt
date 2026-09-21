package com.mdot.app.feature.settings

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.SwatchSpec
import com.mdot.app.core.designsystem.onColorFor
import com.mdot.app.core.designsystem.paletteOptions
import com.mdot.app.core.designsystem.palettePrimary
import com.mdot.app.core.designsystem.component.HorizontalScrollRow
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.SegmentBar
import com.mdot.app.core.designsystem.component.SwitchRow
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.domain.model.SheetBackdropMode
import com.mdot.app.domain.model.ThemeMode

/** 深浅色三档的固定顺序（索引即 SegmentBar 的段位） */
private val ThemeModeOrder = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)

/** 弹层背景三档的固定顺序（索引即 SegmentBar 的段位） */
private val SheetBackdropOrder =
    listOf(SheetBackdropMode.DIM, SheetBackdropMode.BLUR, SheetBackdropMode.BLUR_SCALE)

/**
 * 外观内容（v0.7.x 视觉重构）：独立外观页已删除，本 Pane 是「外观/首页/底栏」合并页 tab0 的唯一内容。
 *
 * 结构 = **一卡一事**（对齐 04 文档 §4.1 与全 app 的 `SectionCard { 标题 + 内容 }` 范式）：
 * 深浅色 / 配色方案（含动态取色）/ 弹层背景 / 日历，四张卡。
 *
 * 间距阶梯（改前是 8/12/14/16/22/24dp 六种、且排序与语义无关）：
 * 卡内「标题 → 内容」8dp、「内容 → 说明」4dp，**卡与卡之间 12dp**——
 * 组内恒小于组间，分组由间距 + 卡片底色断口双重表达。
 *
 * 选择控件只用 [SegmentBar] 与色卡（不再用 FilterChip）：后者「描边 → 实心填充」的语言
 * 与全 app 的滑块不一致，块宽还随标签长度变化（实测 60/60/88dp）。
 */
@Composable
fun AppearancePane(vm: AppearanceViewModel = hiltViewModel()) {
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    // 动态取色开启时：配色仍被记忆，但当前不生效——着色降权 + 弱环标注 + 标题右侧状态胶囊
    val dynamicOn = appearance.dynamicColor

    Column(
        Modifier
            // ⚠️ 必须是 `fillMaxSize`（不能只 `fillMaxWidth`）：HorizontalPager 把**高度小于页面的**
            // 内容在页内垂直居中——实测 `fillMaxWidth` 时整页内容上下各留 214px（77.8dp），
            // 顶部凭空多出一大块空白；换成 fillMaxSize 后与 首页卡片 / 底栏 两个页签完全对齐。
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.page),
    ) {
        Spacer(Modifier.height(Spacing.s))

        // ---- 卡 1：深浅色 ----
        SectionCard {
            Column {
                Text(
                    stringResource(R.string.appearance_theme_heading),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(Spacing.s))
                SegmentBar(
                    labels = ThemeModeOrder.map { stringResource(it.labelRes()) },
                    selected = ThemeModeOrder.indexOf(appearance.themeMode).coerceAtLeast(0),
                    onSelect = { vm.setMode(ThemeModeOrder[it]) },
                    fillWidth = true,
                )
            }
        }
        Spacer(Modifier.height(Spacing.m))

        // ---- 卡 2：配色方案（+ 动态取色：两者是因果同一件事，放在同一张卡里更易理解）----
        SectionCard {
            Column {
                Text(
                    stringResource(R.string.appearance_palette_heading),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(Spacing.m))
                // 8 套配色 → **单行横向滚动**（既不换行、也不分两行）：
                // 换行/分行会让卡片高度随套数增长（4 套 → 8 套时卡片凭空变高），
                // 横向滚动则行高恒定，卡片长宽不随配色套数变化，将来再加套数也不会撑高。
                //
                // ⚠️ 必须用 HorizontalScrollRow，不能用裸 horizontalScroll：外层是 HorizontalPager，
                // 裸滚动会被 Pager 抢走横向手势（在色卡行上左滑直接切页签）——与致谢名单同款问题。
                // 该容器经 nestedScroll 让内层优先消费横向位移，且是横向手势的**终点**：
                // 滑到两端也不把余量上抛给 Pager，故不会「滑到头突然换页」；纵向手势完全不拦，
                // 页面垂直滚动不受影响。
                HorizontalScrollRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    paletteOptions().forEach { (id, labelRes) ->
                        PaletteSwatch(
                            id = id,
                            labelRes = labelRes,
                            active = appearance.paletteId == id && !dynamicOn,
                            overridden = dynamicOn,
                            onClick = { vm.setPalette(id) },
                        )
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Spacer(Modifier.height(Spacing.m))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow(
                        title = stringResource(R.string.appearance_dynamic_color),
                        desc = stringResource(R.string.appearance_dynamic_color_desc),
                        checked = dynamicOn,
                        onCheckedChange = { vm.setDynamic(it) },
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.m))

        // ---- 卡 3：弹层背景 ----
        SectionCard {
            Column {
                Text(
                    stringResource(R.string.appearance_sheet_backdrop_heading),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(Spacing.s))
                SegmentBar(
                    labels = SheetBackdropOrder.map { stringResource(it.labelRes()) },
                    selected = SheetBackdropOrder.indexOf(appearance.sheetBackdropMode).coerceAtLeast(0),
                    onSelect = { vm.setSheetBackdrop(SheetBackdropOrder[it]) },
                    fillWidth = true,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.appearance_sheet_backdrop_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 回退说明只对 Android 11 及以下有意义：常驻渲染等于对绝大多数用户放噪音
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    Text(
                        stringResource(R.string.appearance_sheet_backdrop_no_blur),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.m))

        // ---- 卡 4：日历 ----
        SectionCard {
            Column {
                Text(
                    stringResource(R.string.appearance_calendar_heading),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(Spacing.s))
                SwitchRow(
                    title = stringResource(R.string.appearance_hide_lunar),
                    desc = stringResource(R.string.appearance_hide_lunar_desc),
                    checked = appearance.hideLunarDate,
                    onCheckedChange = { vm.setHideLunar(it) },
                )
            }
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.LIGHT -> R.string.appearance_theme_light
    ThemeMode.DARK -> R.string.appearance_theme_dark
    ThemeMode.SYSTEM -> R.string.appearance_theme_system
}

private fun SheetBackdropMode.labelRes(): Int = when (this) {
    SheetBackdropMode.DIM -> R.string.appearance_sheet_backdrop_dim
    SheetBackdropMode.BLUR -> R.string.appearance_sheet_backdrop_blur
    SheetBackdropMode.BLUR_SCALE -> R.string.appearance_sheet_backdrop_blur_scale
}

/**
 * 配色色卡（8 套，**单行横向滚动**排列，见 [AppearancePane] 卡 2）。
 *
 * 单元宽度不写死：由 `max(48dp 触达区, 标签固有宽)` 自然撑开——当前八套标签均为 2–3 字
 * （≤42dp），故单元恒为 48dp，首项左缘与卡片内容左缘精确对齐；将来若出现更长的标签，
 * 单元会随之变宽而**不会裁字**（这正是横向滚动换来的余地）。标签 `maxLines = 1`：
 * 横向滚动里没有换行的余地，换行只会让行高忽高忽低。
 *
 * 尺寸稳定性：外层 [SwatchSpec.ringBox] 恒定（环画在这里），内层彩色圆恒定 [SwatchSpec.disc]——
 * 早先把环画在 40dp 圆**内侧**，选中时彩色圆被环吃掉一圈，从 38dp 缩到 34dp，选中反而变小。
 *
 * 选中态有**两条线索**：`primary` 环 + 圆内勾选（勾的深浅按底色亮度自动取，八套配色明暗不一）。
 * 未选中则**不画环**（环宽与环色一起归零，见下方 ringColor）：一行彩色圆本身就是最强的识别信息，
 * 再给每个圆套一圈 outlineVariant 只是把「选中」这件事摊薄；色卡自身的 0.5dp 细边已足够定轮廓。
 *
 * **动态取色开启时不留任何选中痕迹**：彩色圆降到 [SwatchSpec.overriddenAlpha] 表达「当前不由它决定」，
 * 但环与勾全部消失——早先给「记忆中的那套」留了一圈弱环，实测那是个误导：动态取色生效时
 * 没有任何一套配色在起作用，画个环等于宣告了一套并未生效的选择。也不在标题旁挂「由壁纸决定」
 * 状态字样：下面的动态取色开关本身就是这个状态，再复述一遍是噪音。
 */
@Composable
private fun PaletteSwatch(
    id: String,
    labelRes: Int,
    active: Boolean,
    overridden: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val discColor = palettePrimary(id)
    val ringWidth by animateDpAsState(
        targetValue = if (active) SwatchSpec.ringWidth else 0.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "swatchRingWidth",
    )
    // 未选中时环宽是 0.dp，颜色必须**一并**转到透明：`Modifier.border` 的 0 宽描边在 Skia 里
    // 会被当成 hairline 画出 1px 实线（实测未选中色卡外围都挂着一圈 outline 深灰细线）。
    val ringColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "swatchRingColor",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(SwatchSpec.ringBox)
                .pressScale(interaction, pressedScale = 0.9f)
                .border(ringWidth, ringColor, CircleShape)
                // ⚠️ 涟漪必须被裁成圆形：clickable 画在**本节点**上，默认是矩形指示器，
                // 不裁剪就会出现一个 48dp 方形涟漪罩在圆形色卡上。clip 要排在 clickable **之前**
                // （Modifier 链上更靠外层才会作用于其后的绘制）。
                .clip(CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                ) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(SwatchSpec.disc)
                    // 不透明度走 graphicsLayer（绘制期），避免逐帧重组
                    .graphicsLayer { alpha = if (overridden) SwatchSpec.overriddenAlpha else 1f }
                    .background(discColor, CircleShape)
                    // 细边让深色卡上的深色色卡（如青绿）仍有轮廓
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (active) {
                    Icon(
                        painterResource(R.drawable.ic_ms_check),
                        contentDescription = null,
                        tint = onColorFor(discColor),
                        modifier = Modifier.size(SwatchSpec.checkIcon),
                    )
                }
            }
        }
        Spacer(Modifier.height(SwatchSpec.labelGap))
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // 横向滚动内不给换行的余地：换行会让行高随标签长短浮动（「紫罗兰」比「青绿」多一行时整行变高）
            maxLines = 1,
        )
    }
}
