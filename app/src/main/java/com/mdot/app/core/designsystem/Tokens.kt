package com.mdot.app.core.designsystem

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
 * 「选项药丸 + 列表卡」规格（底栏配置页「More」区块；形态移植自 **Bencho 的 Assignees 组件**，v0.7.2 之后、未发版；
 * 规范见 docs/03 §13，施工坑见 docs/11 053）。
 *
 * 形态：一颗实底药丸 —— **药丸本身就是读数**：左侧叠放「已开启项」的圆形图标，全关时退化为一个状态词，
 * 右侧箭头；点开在其下方**就地撑出**一张实底列表卡，每行「圆图标 + 文案 + 开关」，行依次错峰入场。
 *
 * 药丸为什么是读数：Bencho 原文——没有计数徽标、也没有「已选 3 项」的副标题，**图标本身就说清了「哪些开着」**；
 * 一个数字只告诉你数量，一叠图标才告诉你选对没有。全关时写状态词而不是动词（「未开启」而非「设置」）——
 * 药丸是**读数**，没内容时就该报告「没有内容」。
 *
 * **令牌映射**（Bencho 的 CSS 自定义属性 → 本项目已有色槽/令牌，**不新增全局变量**，移植的核心要求）：
 *
 * | Bencho | 本项目 |
 * |---|---|
 * | `--card`（区块底色） | `colorScheme.surfaceContainer`（分区卡 [com.mdot.app.core.designsystem.component.SectionCard] 的底色） |
 * | `--fill-slab`（药丸与列表卡的实底） | `colorScheme.surfaceContainerHigh`（比卡片底色深/亮一档，自成一块「板」） |
 * | `--fill-on` / `--ink` | `colorScheme.onSurface`（板上的墨色） |
 * | `--ink-rgb` / `--fill-on-rgb` | **不需要**：Bencho 用三个裸数字拼 `rgba()` 是因为 CSS 拿不到 alpha，Compose 里 `Color.copy(alpha = …)` 直接得到同一件事 |
 * | `--pane-edge`（描边） | `colorScheme.outlineVariant`——本移植**不画**：Bencho 的描边只在 Stroke 开关打开时出现，本页没有这个开关（原文：无投影、无描边，药丸与卡片已经靠「板比底亮一档」自己分开了） |
 * | `--font-ui` | 主题默认字体（`MaterialTheme.typography.*`，不引入第二套字族） |
 *
 * **动效映射**：Bencho 里的裸弹簧系数（stiffness 460/600/660、damping 21/23/34）与缓动曲线一律换成
 * `MaterialTheme.motionScheme` 三档（[com.mdot.app.core.designsystem.component.OptionPillCard] 内）。
 * 这是本仓库硬规则（不许新写硬编码 tween），且换来一件 Bencho 要自己写分支才有的东西：
 * 系统的「动画时长缩放」（开发者选项）由 Compose 的 MotionDurationScale 自动接管，等价于
 * Bencho 的 `@media (prefers-reduced-motion: reduce)`，无需自己判断。
 */
object OptionPillSpec {
    /**
     * 药丸高。Bencho 原值 44px —— 低于 Android 无障碍最小触达 48dp，
     * 故直接对齐页面级按钮高 [ButtonSpec.heightL]，与本页其它主操作同一量纲。
     */
    val pillHeight = ButtonSpec.heightL

    /**
     * 药丸左内边距 —— **比右边小**。Bencho 原文：左边常常是一枚实心圆，
     * 16 的空白挨着圆比挨着字要空得多（`padding: 0 12px 0 8px`）。
     */
    val pillPaddingStart = Spacing.s

    /** 药丸右内边距：挨着文字/箭头，需要多一点空气 */
    val pillPaddingEnd = Spacing.m

    /** 药丸内元素间距：读数 → 标题 → 箭头（Bencho `gap: 10px`） */
    val pillGap = 10.dp

    /** 读数圆的直径（Bencho 的 `FACE = 28`） */
    val readoutSize = 28.dp

    /** 读数圆里的图标尺寸（比例对齐 [com.mdot.app.core.designsystem.component.SettingRow] 的 34 → 20） */
    val readoutIconSize = 16.dp

    /**
     * 后一个圆压住前一个的量。**叠压而不是排成一行**——
     * 一排互不重叠的圆会把药丸撑到近半屏；叠起来才是「一叠东西」而不是「一列东西」。
     * 后加的人排在最右、**压在别人身上**：Bencho 用反向 z 序（第一个在最上层），
     * 这样栈从左往右读、与列表同向；反过来画的话，最后来的会盖住之前所有人，
     * 加第四个人看起来像丢了前三个人。
     *
     * ⚠️ **本项目取 8dp 而非 Bencho 的 10px，这是有意的换算，不是抄错**：
     * Bencho 标称叠压 10/28（36%），但它的环是**向外**画的 `box-shadow: 0 0 0 2px`，
     * 所以**实际遮挡 10+2 = 12px（43%）**。照片被盖掉 43% 仍然是一张人脸；
     * 但本项目的读数是**字形**——实测叠压 10dp 时「固定长度」那把尺子被吃掉左半截、认不出是什么。
     * 故把**实际**遮挡对齐回 Bencho 的标称值：叠压 8 + 环 2 = 10px（36%），
     * 叠起来的观感与 Bencho 同量级，字形只损失左缘 25%。
     */
    val readoutLap = 8.dp

    /**
     * 叠压圆之间的描边宽。**颜色取「它背后是什么」**（即药丸/列表卡自己的实底），
     * 而不是写死白或黑——这圈描边是「两个圆之间的缝」，缝里应该是什么东西就是什么东西，
     * 否则深色模式下叠起来的圆会长出一圈光晕（Bencho 2px）。
     */
    val readoutRing = 2.dp

    /**
     * 读数全关时那句占位文字的不透明度。
     * 不用满墨色：它是**占位**（标题暂时借住在读数位），不是状态；图标一进来它就退场，
     * 降一档才不跟旁边真正的内容抢。
     */
    const val emptyTextAlpha = 0.7f

    /**
     * 箭头尺寸。Bencho 是 lucide 描边图标（24 viewBox、内边距大）画在 16px；
     * Material Symbols 是实心字形、几乎不留内边距，同光学尺寸要大一档。
     */
    val chevronSize = 18.dp

    /** 箭头常态不透明度 */
    const val chevronAlpha = 0.4f

    /**
     * 按压/展开时箭头的不透明度 —— **箭头就是按压态**。
     * Bencho 原文：投影去掉后药丸只剩它能回应手指，而在「整体就是形状」的控件上再塞一块填充
     * 等于凭空多出一个形状；于是改用同一句话说给箭头听（同页配色卡刷新按钮的同一取舍）。
     */
    const val chevronActiveAlpha = 0.75f

    /**
     * 药丸与列表卡之间的竖向间距。
     * Bencho 原值 10px（药丸 44 → 卡片 top 54）；本项目取 [Spacing.m]=12dp ——
     * **因为两块「板」同色**（见 [slabColor]），靠 8dp 分不干净、会看成一块。
     */
    val pillCardGap = Spacing.m

    /**
     * 列表卡内边距。Bencho 用 6px 是为了让行圆角 = 卡片圆角 − 6 = 16；
     * 本项目卡片圆角 [Radius.card] = 24、内边距 [Spacing.s] = 8 → 行圆角同样是 [Radius.button] = 16。
     * **同心圆角**：一个东西装在另一个里面时，它的圆角 = 外面的 − 两者间距（否则内框看着比外框方）。
     */
    val listPadding = Spacing.s

    /**
     * 药丸与列表卡的圆角 —— **同一个值**：Bencho 里两者是同一块「板」的两种形态，不拆成两个数。
     * 24dp 落在 48dp 高的药丸上约等于胶囊（半径 ≥ 半高），与 Bencho 的 22/44 同一观感。
     */
    val slabRadius = Radius.card

    /** 列表行圆角（同心，推导见 [listPadding]） */
    val rowRadius = Radius.button

    /** 行高（Bencho `.pik-row` height 48） */
    val rowHeight = 48.dp

    /** 行左右内边距（Bencho `padding: 0 8px`） */
    val rowPadding = Spacing.s

    /**
     * 行按下时的墨色填充不透明度。
     * Bencho 分了两档（hover 0.05 / focus-visible 0.07）；Android 没有 hover，按下就等价于它的焦点态，取后者。
     */
    const val rowPressedAlpha = 0.07f

    /**
     * 行入场的错峰间隔（Bencho 40ms）。Bencho 原文：四行**一起**出现是一个面板，
     * 四行**依次**到位才是有人把一张名单递给你；要能感觉到，但不该让人等。
     */
    const val rowStaggerMillis = 40L

    /** 行入场的纵向位移：从上方 -8 → 0（Bencho `initial={{ y: -8 }}`） */
    val rowEnterOffset = 8.dp

    /**
     * 读数圆进场/退场的旋转角（Bencho -22° 进 / +14° 出）。
     * Bencho 原文：圆在缩放的同时**转一点**，才像一件东西被放下；只缩放，圆还是圆。
     */
    const val readoutEnterRotation = -22f
    const val readoutExitRotation = 14f

    /** 读数圆进场起始缩放（Bencho `scale: 0.2`） */
    const val readoutEnterScale = 0.2f

    /** 读数圆进场起始纵向偏移（Bencho `y: ty - 10` → 从上方 10 落下） */
    val readoutDropOffset = 10.dp

    /** 读数圆退场时上浮的距离（Bencho exit `y: ty - 6`）——**比进场小**：撤走比放上要短促 */
    val readoutExitRise = 6.dp

    /** 列表卡从药丸底下展开时的起始纵向偏移（Bencho `y: -10`） */
    val cardEnterOffset = 10.dp

    /** 列表卡退场时的纵向偏移（Bencho `y: -8`）。
     *  另：Bencho 用 `transform-origin: 0 0` 配非等比缩放（scaleX .86 / scaleY .72）做「从左上角展开」；
     *  Compose 的 [androidx.compose.animation.scaleIn] 只能等比，且「就地撑开」已经用高度增长
     *  表达了同一件事，故本移植只用高度增长 + 淡入，不加等比缩放（加了反而像「弹出一块」。） */
    val cardExitOffset = 8.dp
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
    /** 底栏浮层效果：投影高度（让底栏看起来浮在页面内容之上；6dp → 3dp → 2dp 逐次调淡） */
    val barElevation = 2.dp
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
 * 图表小格 / 小柱的圆角（月柱状的小柱、热力图的小格）。
 *
 * 3dp 是刻意的：这些格子只有 8–10dp 宽，用 `Radius.xs`(8dp) 会接近半圆、看着像胶囊而不是「格子」；
 * 3dp 才读得出方块轮廓。2026-09-22 之前这两处各写一个 `RoundedCornerShape(3.dp)` 魔法值，现收进本对象。
 */
object ChartSpec {
    /** 柱/格的圆角（月柱状小柱、热力图小格） */
    val cellRadius = 3.dp
}

/**
 * 按钮规格（按钮规范化第一批，docs/03 §按钮）：feature 层禁止手写按钮高度/内边距，
 * 统一从 [ButtonSpec] 取值（组件 [com.mdot.app.core.designsystem.component.JiabanButton]）。
 *
 * - **L（48dp）**：页面级主操作——保存/提交/新增/下一步，与 ShrinkFeedbackButton、
 *   InlineConfirmButton Standalone 同一量纲（它们的默认值就是本档）
 * - **M（40dp）**：M3 默认档——描边次要操作、弹窗内按钮
 * - 高度之外零手写：圆角统一 pill（[Radius.pill]），字重统一 labelLarge，
 *   按压反馈统一 pressScale + 涟漪叠加（工资设置保存按钮基准）
 */
object ButtonSpec {
    /** L 档高度：页面级主操作（对齐 ShrinkFeedbackButton.pillHeight = Spacing.xl*2） */
    val heightL = 48.dp

    /** M 档高度：M3 默认（描边次要操作/弹窗内按钮） */
    val heightM = 40.dp

    /** L 档文案态最小宽（对齐 ShrinkFeedbackButton.minWidth = Spacing.xl*6）：避免短文案被压窄 */
    val minWidthL = 144.dp

    /** L 档水平内边距（对齐 ShrinkFeedbackButton.contentPadding = Spacing.xl） */
    val contentPaddingL = 24.dp

    /** 图标按钮的图标尺寸（热区另由 IconButton 默认 48dp 保证） */
    val iconSize = 20.dp
}

/**
 * 图标选项药丸行规格（组件 [com.mdot.app.core.designsystem.component.ChoicePillRow]）：
 * 「图标 + 文字」的药丸**并排等分**一行，单选其一——外观页「深浅色 / 弹层背景」两行用
 * （2026-09-23 按用户参考图改造，替代这两处的 SegmentBar）。
 *
 * 形态来源（参考图两态 2160px 截图逐像素量测；**用户两轮指正后定稿**）：
 * - **圆角随选中联动——「三个按钮是联动的」指的就是这个**：**选中 = 全圆胶囊**
 *   （R = h/2，有解析解：选中棕段在文字上方取样行 dy=41 处左缘内缩 33.6px，代入
 *   `R−√(82R−1681)=33.6` 解得 R=127.1 = 行高 254 的一半，分毫不差）；**未选中 = 圆角矩形**
 *   （R≈0.16–0.19h ≈8dp → [Radius.xs]，左侧自由边直测可信）。切换选中时圆角做过渡，
 *   胶囊与矩形互相形变。
 *   ⚠️ 三轮踩坑：初版全做成胶囊（未选中也圆过头）、二版全做成 8dp 矩形（选中不够圆、
 *   外缘也没做圆端）——只有**选中那颗**是全圆胶囊**加上行首左缘/行尾右缘是全圆端**，
 *   其余内侧分隔角才是 8dp（用户第三轮明确的规则；参考图左右页边距 89.4px 对称即外缘
 *   R=h/2 的证明）。二版的另一病根：**在文字上方取样做边界扫描，取样行落在胶囊曲线区间，
 *   扫描窗口被曲线内缩截断 → 量出 46px 的假圆角**，还连带把内缩当成缝宽、误做出假的
 *   8/4 联动缝（真实缝均匀 ≈12px）。量胶囊圆角要在行带中线取样或解方程，别在圆角区间取样；
 *   阴影/AA 会污染顶行取样，外缘可信判据是**页边距对称**。
 * - **缝均匀**：参考图真实缝 ≈12px ≈2.2dp → 取 [Spacing.xs] 4dp 下限，不随选中变。
 * - **选中整颗实底**（primary + onPrimary），未选中是「板」（`surfaceContainerHigh`，与
 *   [OptionPillSpec] 的 fill-slab 同档）上的墨色（`onSurface`）；底/墨/圆角一起以
 *   motionScheme effects 档过渡。
 *
 * **等分而非按内容宽**（参考图两态宽度随手工绘图略有出入，不按内容宽）：三颗药丸 `weight(1f)`
 * 瓜分行宽，右缘恒与卡片内容右缘对齐（docs/17「统一右边界」原则）；4 字标签（跟随系统/模糊缩小）
 * 内容下限「图标 18 + 间距 4 + 文字 ≈57 + 内边距 4×2」= 87dp，360dp 窄屏最窄药丸 ≈93dp 放得下，
 * 字号放大时标签 maxLines=1 尾部省略兜底。
 */
object ChoicePillSpec {
    /** 药丸高：与页面级主操作按钮同量纲（参考图药丸就是这个块头） */
    val pillHeight = ButtonSpec.heightL

    /** 未选中药丸圆角：参考图内侧分隔角 R≈43–48px ≈8dp（圆角矩形） */
    val cornerIdle = Radius.xs

    /** 行首左缘 / 行尾右缘的外侧圆角：**全圆端 = 半高**（参考图左右页边距 89.4px 对称，
     *  外缘 R=h/2）——行的整体轮廓因此是胶囊；中间未选中项四角都走 [cornerIdle] */
    val cornerEdge = pillHeight / 2

    /** 选中药丸圆角：**全圆胶囊 = 四角半高**（参考图解析解 R = h/2） */
    val cornerSelected = pillHeight / 2

    /** 药丸之间的均匀缝：参考图原始 11–15px ≈2–2.7dp，取 Spacing.xs 做下限；不随选中变 */
    val gap = Spacing.xs

    /** 药丸内水平内边距：等分模式下实际留白由行宽决定，这只是窄屏的下限（4 字标签地板 87dp） */
    val paddingH = Spacing.xs

    /** 图标与文字的间距 */
    val contentGap = Spacing.xs

    // 图标尺寸走 [IconSpec.dense]（18dp）——比 boxed(20) 小一档才塞得进 4 字标签（见类注释）
}

/**
 * 图标尺寸档位（**按角色记，别按数字记**）。
 *
 * 角色 → 档位的完整表、盒+图标配对、跨页共用表、新增图标 SOP 全在 **docs/03 §3.4**；
 * 代码里写**裸数字**（如 `.size(17.dp)`）会被 `IconContractTest` 拦下，改用这里的令牌。
 *
 * ⚠️ **改档位要同时改三处**：① 本对象 ② docs/03 §3.4 的角色表 ③ `IconContractTest.允许的裸数字`。
 * ⚠️ **装饰档不进本对象**：致谢卡跑马灯（11/15/21）、首页收入卡（28）、弹层拖拽箭头（30）
 * 是一次性的装饰尺寸，抽成令牌只会多一层间接——它们由测试的「装饰档」集合显式豁免（勿“统一”）。
 */
object IconSpec {
    /** 行内：正文旁的小图标（首页/统计/明细的行内日历、我的页相机、「新建」胶囊） */
    val inline = 16.dp

    /** 紧凑行：消息条、弹层加减号、右侧辅助图标 */
    val dense = 18.dp

    /**
     * tonal 盒内：`SettingRow` / `TappableTonalRow` / `BigAmountRow` / `RowIconAction` / `EquationCard`
     * 里的图标（盒与图标是一对，配对表见 docs/03 §3.4）。
     */
    val boxed = 20.dp

    /** 底栏槽位（`JiabanBottomBar`） */
    val bar = 22.dp

    /** hero：导出页空态大图标（配固定品牌底，见 docs/03 §3.4「允许的例外」） */
    val hero = 52.dp
}

/** 盒 + 图标 成对（[box] 是容器圆/方尺寸，[icon] 是里面的图标） */
data class IconBox(val box: androidx.compose.ui.unit.Dp, val icon: androidx.compose.ui.unit.Dp)

/**
 * 「盒 + 图标」配对表（docs/03 §3.4）。**盒与图标是一对**：只改盒不改图标会出现
 * “图标在盒里显空/显挤”，所以成对取用、别两处各写一个数。
 */
object IconBoxSpec {
    /**
     * 通用图标方块：**36dp 方底（`Radius.small`）+ `IconSpec.boxed`(20dp)**。
     *
     * 2026-09-22 合并：原先三种几乎一样的盒（`SettingRow` 34 / 工地各卡 36 / `RowIconAction` 40）
     * 视觉上分不出差别，现统一为 36；且**盒与图标同源**（图标尺寸引用 `IconSpec.boxed`，不再各写一个数）。
     *
     * ⚠️ **只管几何、不管底色**：底色由调用方给 —— 普通行用 `secondaryContainer`，
     * primary 卡上的图标块用 `surface`（两者几何相同，故同一个令牌，见 docs/03 §3.4）。
     */
    val tile = IconBox(box = 36.dp, icon = IconSpec.boxed)

    /** 空态大图标：72dp 圆底 + 32dp 图标（[com.mdot.app.core.designsystem.component.EmptyState]） */
    val hero = IconBox(box = 72.dp, icon = 32.dp)

    /** 首页入口卡：42dp 圆角方底 + 22dp 图标（`HomeScreen` 的 `EntryCard`） */
    val entry = IconBox(box = 42.dp, icon = 22.dp)
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
     *
     * ⚠️ 必须盖住 `sideLabelGap(2dp) + 9sp 汉字全宽(9dp) + 余量`：槽内文字可用宽 =
     * slot - gap，旧值 10dp 时只剩 8dp < 9dp，宽字（初/廿/秋等）右侧被裁掉约 3px，
     * 表现为「有些农历日期有一点点裁切」（窄字一/二/十/八看不出）。
     */
    val sideSlotWidth = 13.dp

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
