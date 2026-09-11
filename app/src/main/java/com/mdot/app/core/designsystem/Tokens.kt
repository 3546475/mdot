package com.mdot.app.core.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
