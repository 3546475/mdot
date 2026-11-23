package com.mdot.app.core.designsystem

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 玻璃簇令牌（毛玻璃卡 / 液态玻璃 / 浮起光影 / 弹层背景）——从 `Tokens.kt` 拆出，
 * 改玻璃观感只动这一个文件。数值来历与硬约束见 docs/11 060–110 相关条目。
 */

/**
 * 弹层背景「模糊 + 缩小」规格（04 文档 §4.1：业务层禁用魔法值）。
 * 底部弹层出现时，背景内容缩小成圆角卡片并模糊——用「背景后退」表达层级，
 * 替代原先整屏压暗。四周露出的底衬 [backdropScrim] 负责空间感。
 * ⚠️ 模糊依赖 RenderEffect（**Android 12 / API 31 起**）；更低版本 [blurRadius] 置 0
 * 自动回退为纯压暗（[contentScrimNoBlur]），勿依赖模糊承担可读性。
 */
object SheetBackdrop {
    /** 背景内容静止态缩放（缩小约 6%，边缘露出底衬形成卡片层级感） */
    const val scale = 0.94f
    /** 背景模糊半径 */
    val blurRadius = 12.dp
    /** 缩放后卡片圆角（与弹层自身圆角一致） */
    val cornerRadius = Radius.sheet
    /** 内容上方压暗（模糊生效时较轻：层级已由模糊区分） */
    const val contentScrim = 0.25f
    /** 无模糊能力（API<31）时的回退压暗强度（沿用改造前观感） */
    const val contentScrimNoBlur = 0.4f
    /** 四周底衬压暗（仅内容缩小后露出） */
    const val backdropScrim = 0.6f
}
/**
 * 毛玻璃质感统一令牌（**全 App 唯一真源**：底栏 JiabanBottomBar / 记加班圆钮 RecordActionButtons /
 * 悬浮玻璃卡（记工页保存后预览）同款质感）。2026-09-28 用户定：玻璃=模糊半径 32dp、
 * 底色 0.16–0.36 渐变、内高光 0.18——「更透更糊」的观感（此前底栏用 20dp/0.50–0.68，
 * 用户反馈模糊不明显）。API<31 无模糊能力时由调用方用 [fallbackAlpha] 回退实色底。
 */
object GlassCardSpec {
    /** 模糊半径。16dp（2026-09-28 按 AndroidLiquidGlass 方案从 32dp 降档：大半径是驱动采样
     *  波动的放大器，玻璃感由内高光/衬托渐变/底色补偿；其官方底栏仅 8dp blur + 折射） */
    val blurRadius = 16.dp
    /** 底色渐变上缘不透明度（更透，透出模糊层；二调：0.30 → 0.16 更透看效果） */
    val alphaTop = 0.16f
    /** 底色渐变下缘不透明度（二调：0.52 → 0.36） */
    val alphaBottom = 0.36f
    /** 无模糊能力回退底色不透明度（保持文字可读） */
    val fallbackAlpha = 0.92f
    /** 内高光不透明度（上缘内侧白渐变，空内容也显玻璃感）。
     *  **仅悬浮玻璃卡**用：底栏与圆钮的内高光改走 [GlassLiftSpec] 的内晕（四周一圈） */
    val highlightAlpha = 0.18f
    /** 衬托渐变（模糊层内）下缘不透明度 */
    val scrimAlpha = 0.14f
    /** 玻璃描边不透明度（**仅悬浮玻璃卡**用；底栏胶囊与「记加班」圆钮改走
     *  [GlassLiftSpec] 的 1dp 白色外缘亮线） */
    val borderAlpha = 0.40f
}

/**
 * 液态玻璃规格（外观-底栏-More「液态玻璃」开关；照抄 memos-m
 * `docs/liquid-glass-bottom-bar.md`，实现见 `component/LiquidDroplet.kt`）。
 *
 * ⚠️ 液态玻璃是**交互**、毛玻璃是**材质**，两者**互相独立**（2026-10-30 边界重规划）：
 * 液态玻璃**不改静止材质**，只在**手指按下之后**长出一颗放大该处底栏（图标 + 材质）的液滴透镜，
 * 拖动跟手、抬手滑向新选中项再淡出。四象限见 docs/03 §4.4。
 *
 * 能力门槛：液滴需要 RuntimeShader（**API 33+**，`isRuntimeShaderSupported()`）；
 * 更低版本液态玻璃开关不产生任何视觉变化（材质仍由毛玻璃开关决定）。
 *
 * 数值来自实现文档 §9，**这些数字就是观感本身**（改之前先真机对比）：
 * 放大 1.14、边缘环带 24%、弯曲深度 14%、色散 35%、滑行与 Tab 切页同一条弹簧、淡出 80ms。
 */
object GlassLiquidSpec {
    /** 液滴宽（文档 112dp；底栏固定 64dp 高，珠子高度会被父级约束量回 64dp）。 */
    val dropletWidth = 112.dp

    /** 液滴高（文档 76dp；**实际布局高度见 `min(高度, 底栏高)`**，再据此垂直居中）。 */
    val dropletHeight = 76.dp

    /** 中心放大倍数：液滴中段就是被放大的底栏；1.3 以上会明显「鱼眼」。 */
    const val magnification = 1.14f

    /** 边缘折射环带占液滴高度的比例（越大折射区越厚）。 */
    const val rimBand = 0.24f

    /** 边缘把取样向内拉的量（× 液滴高度；越大边缘折叠越狠）。 */
    const val bendDepth = 0.14f

    /** 色散强度：红/蓝取样沿弯曲方向错开，只在边缘出现细彩边；0 关闭。 */
    const val dispersion = 0.35f

    /** 液滴深色描边宽度（**仅浅色主题**：白高光在浅底上看不清，靠深色描边立边界） */
    val borderWidth = 1.dp

    /** 深色描边不透明度（浅色主题）。0.28 → **0.35**（2026-10-30 用户定：浅色档描边提到 0.35） */
    const val borderAlphaLight = 0.35f

    /** 生长起始尺寸比例（越小长得越夸张）。 */
    const val growthStart = 0.72f

    /** 长出：文档 `spring(1f, 1000f)`（约 150ms）。 */
    val pressSpec = spring<Float>(dampingRatio = 1f, stiffness = 1000f)

    /** 淡出：tween(80)。160 → **80**（2026-11-02 用户定：160ms 拖沓，珠子消失跟不上手指）。 */
    val releaseSpec = tween<Float>(durationMillis = 80)

    /**
     * 点击切换的滑行：**与底栏 Tab 切页同一条曲线**（2026-10-30 用户定：液滴滑行的速率曲线
     * 要跟页面切换一致，两件事本来就是同一拍动作）。
     * 曲线实体在 [MotionSpec.tabSwitch]——`AppRoot` 的 NavHost Tab 转场与本液滴滑行共用它，
     * 改曲线只需改那一处（不要再在这里写一条独立的 tween/spring）。
     */
    val travelSpec = MotionSpec.tabSwitch<Float>()
}

/**
 * 浮起胶囊的「高光 + 阴影」（模糊态底栏胶囊 / 右侧「记加班」圆钮共用）。
 *
 * 来源：参考项目 memos-m 的配方卡 `docs/floating-capsule-highlight-card.md`（2026-10 移植）。
 * 分工是固定的三件事，**别用其中一件替代另一件**：
 * - **叠层阴影**负责「浮起」：16 层同形胶囊叠出总 α 仅 0.05 的环境暗部（不是方向投影）；
 * - **外缘亮线**负责「边界」：1dp 白、上亮下暗，替代原先的 `outlineVariant` 描边；
 * - **内晕**负责「厚度」：2dp 白只在边缘一圈渐隐，玻璃才看着是块实体而非一层薄片。
 *
 * 日/夜两档（底色亮度 > 0.5 判日）：夜间不投影（深色卡片自己够沉，细白亮线已足够分界）。
 * 实现与三条硬约束（阴影必须在 clip 之外、亮线必须在内容之后、禁用 `Modifier.shadow`）
 * 见 `component/CapsuleLift.kt`。
 */
object GlassLiftSpec {
    /** 外缘亮线宽度 */
    val edgeWidth = 1.dp

    /** 内晕宽度（紧贴外缘内侧的那一圈） */
    val glowWidth = 2.dp

    /** 外缘亮线渐变的停靠点（上→下）：日间上缘最亮、往下收暗 = 上缘反光 */
    val edgeStopsLight = listOf(0f, 0.45f, 1f)

    /** 外缘亮线白色不透明度（日间） */
    val edgeAlphasLight = listOf(0.55f, 0.18f, 0.08f)

    /** 外缘亮线渐变的停靠点（夜间）：夜间下缘反而略亮（0.20 > 0.15），像被底光托住 */
    val edgeStopsDark = listOf(0f, 0.4f, 1f)

    /** 外缘亮线白色不透明度（夜间）：整体比日间淡，深底上白线太亮就成「描边」了 */
    val edgeAlphasDark = listOf(0.35f, 0.15f, 0.20f)

    /** 内晕渐变停靠点（日间） */
    val glowStopsLight = listOf(0f, 1f)

    /** 内晕白色不透明度（日间）：0.05 已可见，再淡会被 8-bit 混合舍入掉 */
    val glowAlphasLight = listOf(0.05f, 0f)

    /** 内晕渐变停靠点（夜间） */
    val glowStopsDark = listOf(0f, 0.5f, 1f)

    /** 内晕白色不透明度（夜间）：中段最淡，往下回一点 */
    val glowAlphasDark = listOf(0.10f, 0.03f, 0.05f)

    /** 叠层阴影：向外扩散的**总半径**（到此处彻底淡出）。
     *  34dp（用户 2026-10-23 定：先收到 24dp 觉得太窄，又回到 34dp 配峰值 0.04）。 */
    val shadowSpread = 34.dp

    /** 叠层阴影：整体下移（只改这个不改柔度） */
    val shadowOffsetY = 3.dp

    /**
     * 叠层阴影向外画出的**最大距离**（扩散半径 + 下移量）。
     *
     * **需要把阴影圈在节点边界内**的容器（如底栏的出入场动画图层：`graphicsLayer { alpha }`
     * 在 alpha < 1 时会把内容裁到节点边界）要按它留余量——否则会出现「阴影上半圈等动画结束
     * 才突然出现」（见 docs/11 099）。
     */
    val bleed: androidx.compose.ui.unit.Dp get() = shadowSpread + shadowOffsetY

    /**
     * 叠层阴影：圈数（等分 [shadowSpread]）。**只是采样密度**：实际画出的圈数由 [computeShadowBands]
     * 按「1/255 量化 + 尾部逐圈跳过」决定，通常少于这个值。
     *
     * ⚠️ 每层 α 会被量化到 1/255（8-bit 混合的粒度）：若按「每层 α 相同」画（旧制），
     * 层数越多每层越小 ⇒ 落进 [0.5,1.5]/255 就一律舍入成 1/255，实际总深只由层数决定
     * （旧值 16 层 × 名义 0.05 ⇒ 实际 6.09%）；8 层 × 0.05 ⇒ 每层 1.63/255 舍入成 **2/255**
     * ⇒ 6.09%，反而比名义更重。现在的做法是**逐圈求差**、峰值由 [shadowAlphaLight] 定，
     * 不再有这种「同名不同实」的错位。
     */
    const val shadowLayers = 14

    /** 叠层阴影：紧贴胶囊处的**峰值**不透明度（日间，仅在贴边一圈），往外按 [shadowFalloffCurve]
     *  缓出到 0。**用户 2026-10-23 定 0.04**（先试 0.032 偏淡）；这是「贴边最暗处」的值，
     *  不再像旧制那样是「名义总量」（旧制 0.05/8 层实际画出 6.09%）。 */
    const val shadowAlphaLight = 0.04f

    /** 叠层阴影：总不透明度（夜间）。0 = 夜间不投影 */
    const val shadowAlphaDark = 0f

    /**
     * 「越往外越淡」的曲线指数：距离 `d` 处的累计不透明度 = `峰值 × (1 − d/扩散半径)^指数`。
     *
     * - **1.0** = 线性（各圈衰减量相等 ⇒ 看着是等宽暗带，旧制就是这个观感）；
     * - **2.0** = 二次缓出（**当前**：暗部集中在贴边一圈，外侧尾巴薄且长）；
     * - **3.0** = 更贴边（只剩一条很细的边影）。
     */
    const val shadowFalloffCurve = 2f
}
