package com.mdot.app.core.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 设计令牌（04 文档 §4.1：feature 层禁用魔法值）；M3 Expressive：形状整体加圆、动效弹簧化 */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val page = 16.dp
}

/** 工地记工语义金额色（跨深浅主题取中饱和度保证可读）：已到手（借支/结算单）= 绿；待结余额 = 橙 */
object SiteMoneyColors {
    val ReceivedGreen = Color(0xFF1E8E3E)
    val PendingOrange = Color(0xFFE8930C)
}

/**
 * 彩虹色板（致谢卡跑马灯边框用）：固定的七色顺序，不随主题变化——
 * 彩虹的语义就是色彩本身，取中高饱和度并在深浅主题下均可辨（alpha 由使用方控制）。
 */
object RainbowColors {
    /**
     * 低饱和七色光谱（HSV 饱和 ×0.50、明度上限 0.88）——柔和不刺眼，
     * 保留色相辨识度但不喧宾夺主（用户反馈原方案过于鲜艳）。
     * 彩虹的语义就是色彩本身，故不随主题变化；alpha 由调用方控制。
     */
    val spectrum = listOf(
        Color(0xFFE08A85), // 红
        Color(0xFFE0B270), // 橙
        Color(0xFFE0CA70), // 黄
        Color(0xFF7EC790), // 绿
        Color(0xFF89C5E0), // 青
        Color(0xFF9796D6), // 蓝
        Color(0xFFC798DE), // 紫
    )

    /** 取色环上 [t]（0..1，自动回绕）处的颜色（线性插值，用于生成平滑跑马灯） */
    fun sample(t: Float): Color {
        val n = spectrum.size
        val pos = ((t % 1f) + 1f) % 1f * n
        val i = pos.toInt() % n
        val j = (i + 1) % n
        return androidx.compose.ui.graphics.lerp(spectrum[i], spectrum[j], pos - pos.toInt())
    }
}

object Radius {
    val xs = 8.dp
    val small = 12.dp
    val button = 16.dp
    val card = 24.dp
    val bar = 32.dp
    val sheet = 28.dp
    val pill = 100.dp
    val textField = 20.dp
}

object Duration {
    const val normal = 200
    const val slow = 300
}

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
 * 外观页色卡（配色方案）规格。
 *
 * ⚠️ **选中环必须画在外层、彩色圆尺寸恒定**：早先把环画在 40dp 圆自己身上
 * （`border` 是节点**内侧**描边），选中时彩色圆实际从 38dp 缩到 34dp——选中反而让色卡「变小」，
 * 且四张色卡大小不一。现在外层固定 48dp 只负责画环，内层彩色圆恒定 40dp。
 */
object SwatchSpec {
    /** 外层方框：环的绘制范围，同时是触达区（≥48dp 无障碍最小尺寸） */
    val ringBox = 48.dp
    /** 彩色圆直径——**恒定**，不随选中/覆盖状态变化 */
    val disc = 40.dp
    /** 当前生效配色的环宽（未选中不画环，理由见 `PaletteSwatch`） */
    val ringWidth = 2.dp
    /** 勾选图标尺寸（选中态的第二条线索：不依赖环的粗细/颜色，色弱可辨） */
    val checkIcon = 18.dp
    /** 动态取色开启时彩色圆的不透明度（此时**不画任何选中标记**，只降权） */
    const val overriddenAlpha = 0.38f
    /** 色卡行的纵向间距：色卡 → 标签 */
    val labelGap = Spacing.xs
}

/** 在给定底色上取可读的前景色（亮度阈值 0.5）——色卡勾选用，四个色卡深浅不一 */
fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF1A1B21) else Color.White

/**
 * M3 Expressive 弹簧动效：按下干脆利落（无过冲），松手带轻微弹性回弹。
 * 用于 pressScale、底栏指示胶囊滑动等位置/缩放动画。
 */
object Springs {
    /** 干脆利落：按下/常态过渡 */
    val snappy: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** 带弹性：松手回弹、指示条滑动 */
    val bouncy: AnimationSpec<Float> = spring(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMedium,
    )
}

/** 底栏规格 */
object BottomBarSpec {
    val height = 64.dp
    val horizontalMargin = 16.dp
    val bottomMargin = 12.dp
    /** 带文字模式单槽基准宽（胶囊宽 = 槽位数 × 单槽宽，超出可用宽度时自动收窄） */
    val slotWidth = 80.dp
    /** 仅图标模式单槽基准宽 */
    val slotWidthIconOnly = 60.dp
    /** 「按钮右置」布局：胶囊与圆钮间距 */
    val sideActionGap = 8.dp
    /** 「按钮右置」布局：右侧圆钮直径（用户定：约与底栏内「带文字按钮」等高，比底栏高矮 6dp；
     *  演进：v0.6.19 初版 64dp（当时仅图标底栏，显大）→ v0.6.20 缩到 52dp（与居中胶囊同尺寸）
     *  → 当前 58dp（52dp 在带文字底栏旁偏小）） */
    val sideActionSize = 58.dp
    /** 底栏浮层效果：投影高度（让底栏看起来浮在页面内容之上） */
    val barElevation = 6.dp
    /** 底栏浮层效果：0.5dp outlineVariant 细描边 */
    val barBorderWidth = 0.5.dp
    /** 底栏毛玻璃（背景模糊）：模糊半径（API≥31 生效，与弹层背景模糊同量级） */
    val frostBlurRadius = 20.dp
    /** 毛玻璃底色垂直渐变：上缘不透明度（更透，配合内高光） */
    val frostedAlphaTop = 0.50f
    /** 毛玻璃底色垂直渐变：下缘不透明度（更实，衔接页面、消除色块感） */
    val frostedAlphaBottom = 0.68f
    /** 毛玻璃底色不透明度回退（API<31 无模糊能力：保持可读性的高不透明） */
    val frostedFallbackAlpha = 0.9f
    /** 毛玻璃内高光：上缘内侧白色高光不透明度（空内容时也呈现玻璃质感） */
    val frostedHighlightAlpha = 0.12f
    /** 毛玻璃衬托渐变（模糊层内）：下缘淡色不透明度（空内容时给模糊「有物可糊」） */
    val frostedScrimAlpha = 0.10f
    /** 毛玻璃开启时的投影高度（玻璃不投影：空白背景时阴影光晕观感差；非毛玻璃仍 [barElevation] 6dp） */
    val frostedElevation = 0.dp
    /** 毛玻璃开启时的描边透明度（弱化硬边；非毛玻璃仍全不透明 outlineVariant） */
    val frostedBorderAlpha = 0.35f
    /** 「固定长度」档：胶囊固定为该个数槽位宽（仍受可用宽上限约束） */
    val fixedCellCount = 4
}

/**
 * 预测性返回（PredictiveBackHandler）手势规格：进度 0→1 映射为**小幅横向偏移**（不缩放）。
 *
 * ⚠️ 不做缩放：缩放会在松手衔接 pop 转场时多出一段「缩回全屏」的视觉（闪一下）；
 * 位移与二级页 pop 的右滑出屏**同向**，可直接与转场衔接成一段动画（见 AppRoot 的衔接衰减）。
 */
object PredictiveBackSpec {
    /** 手势滑到底（progress=1）时当前页向右的最大位移：仅小幅偏移，主体留在屏上、侧边只露极窄一条 */
    val maxTranslationX = 36.dp

    /** 进度钳制到 [0,1]（系统事件偶尔给出越界值） */
    fun clampProgress(progress: Float): Float = progress.coerceIn(0f, 1f)

    /** 进度 → 横向位移（progress=1 时为 [maxTranslationX]） */
    fun translationFor(progress: Float): androidx.compose.ui.unit.Dp =
        maxTranslationX * clampProgress(progress)
}

/** 响应式断点与限宽（docs 03 §3.2：两档断点 600/840，宽屏限宽居中 + 一级页双栏） */
object AdaptiveSpecs {
    /** MEDIUM 断点：≥600dp 进入限宽居中（平板竖屏/折叠屏展开） */
    val mediumBreakpoint = 600.dp
    /** EXPANDED 断点：≥840dp 启用双栏布局（平板横屏/桌面窗口） */
    val expandedBreakpoint = 840.dp
    /** 单列内容限宽（MEDIUM/EXPANDED 统一） */
    val contentMaxWidth = 600.dp
    /** EXPANDED 双栏布局总宽 */
    val twoPaneMaxWidth = 720.dp
    /** 记录弹层限宽（宽屏下底部弹层居中，不全宽拉通） */
    val sheetMaxWidth = 640.dp
}

/**
 * 日历格子规格（04 文档 §4.1：feature 层禁用魔法值）。
 *
 * 一格的可用宽只有约 45dp（手机单列 7 等分），一行里要放下「休/班 + 日期 + 节日/农历」。
 * 主行用 Box 三段定位（日期居中、两侧贴边），**不是按内容拼接**——所以宽度约束是
 * 「日期居中块 + 两侧各一个字」互不重叠即可，而不是三者相加：
 * 日期 ≈ 日期字号 × 1.1（两位数字），每个汉字 ≈ 侧栏字号 × 1.0。
 * 侧栏字号低于 M3 字号表最小档（labelSmall 11sp）是刻意的，改大前先算一遍。
 */
object CalendarCellSpec {
    /** 格子宽高比（宽 / 高）；长按拖动按日期定位时也用它换算行高，改这里即可 */
    const val aspectRatio = 0.95f

    /**
     * 日期字号——**格子里的主体**（用户 2026-09-20 二轮规格：比侧栏明显大，且居中于本列与表头星期对齐）。
     * M3 titleMedium = 16sp 同档。
     */
    val dateFontSize = 16.sp
    val dateLineHeight = 18.sp

    /** 左槽「休/班」与右槽「节日/农历」字号（低于字号表最小档，见类注释）——挂靠信息，不争主次 */
    val sideFontSize = 9.sp
    val sideLineHeight = 11.sp

    /**
     * 两侧槽位的**固定宽度**（一个汉字宽 + 余量）。
     *
     * 左右等宽是「日期恒定居中、与表头星期对齐」的前提：主行整体居中时，只有两侧占位相等
     * 才能把日期顶到列中心，而日期本身不必知道列宽。
     */
    val sideSlotWidth = 10.dp

    /**
     * 左槽「休/班」与日期的间距——**紧贴**。
     * 早先两侧贴的是格子边缘（离日期约 20dp），视觉上「休」反而更靠近左边那一格的农历，
     * 像是旁边日期的信息（用户 2026-09-20 第四轮规格）。
     */
    val sideBadgeGap = 0.5.dp

    /** 右槽节日/农历与日期的间距——允许一点点，但远小于贴边缘的做法 */
    val sideLabelGap = 2.dp

    /** 第一行（休/班 + 日期 + 节日）与第二行（+小时 / N 工）之间的纵向间距 */
    val lineGap = 1.dp
}
