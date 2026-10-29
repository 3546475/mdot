package com.mdot.app.core.designsystem.component

import com.mdot.app.core.designsystem.engineShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.BottomBarSpec
import com.mdot.app.core.designsystem.GlassCardSpec
import com.mdot.app.core.designsystem.GlassLiftSpec
import com.mdot.app.core.designsystem.GlassLiquidSpec
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.mdot.app.core.designsystem.Radius
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults
import com.mdot.app.domain.model.ThemeEngine
import kotlinx.coroutines.launch

/** 底栏功能池槽位定义（03 文档 §4.2）；MD3E：图标为 Material Symbols Rounded 单色形状，选中态由 tint（onSecondaryContainer/primary）+ 弹性放大区分。label 为资源 id，显示点用 stringResource 解析 */
data class SlotSpec(
    val id: String,
    val labelRes: Int,
    val route: String,
    @DrawableRes val iconRes: Int,
    @DrawableRes val iconFilledRes: Int = iconRes,
)

object SlotRegistry {
    val ALL = mapOf(
        "home" to SlotSpec("home", R.string.ds_slot_home, "home", R.drawable.ic_ms_home),
        "calendar" to SlotSpec("calendar", R.string.ds_slot_calendar, "calendar", R.drawable.ic_ms_calendar_month),
        "stats" to SlotSpec("stats", R.string.ds_slot_stats, "stats", R.drawable.ic_ms_bar_chart),
        "export" to SlotSpec("export", R.string.ds_slot_export, "export", R.drawable.ic_ms_file_download),
        "sync" to SlotSpec("sync", R.string.ds_slot_sync, "sync", R.drawable.ic_ms_cloud_sync),
        "settings" to SlotSpec("settings", R.string.ds_slot_settings, "settings", R.drawable.ic_ms_settings),
        "profile" to SlotSpec("profile", R.string.ds_slot_profile, "profile", R.drawable.ic_ms_person),
    )

    fun resolve(id: String): SlotSpec? = ALL[id]
}

/**
 * 悬浮胶囊底栏（M3 Expressive 导航形态）：功能槽位等宽排列；
 * 选中槽位由一枚「滑动胶囊」标示（secondaryContainer，弹簧滑动），图标切换为填充变体并弹性放大。
 * 仅图标模式只显示图标；点击无涟漪无阴影（保留按压缩放）。
 *
 * 两种布局（外观页可切换）：
 * - 居中动作（[centerAction]，默认）：胶囊内为「左槽位 + 中央按钮 + 右槽位」；
 * - 侧边圆钮（[sideAction]）：胶囊内只有槽位，右侧另放一个独立圆钮（如「记加班」，始终仅图标）。
 *   两者互斥，调用方二选一传入。
 *
 * 玻璃质感分**两层，互相独立**（2026-10-30 边界重规划）：
 * - 毛玻璃（[backdropBlur] 非空）= **材质**：半透明底色 + 自实现外扩录制层模糊 + 沿外缘的高光与叠层阴影；
 * - 液态玻璃（[liquidGlass]=true）= **交互**：按下才长出一颗放大「该处底栏」的液滴透镜
 *   （放大 + 边缘折射 + 色散，见 `LiquidDroplet.kt`，需 API 33+）；**不改静止材质**。
 *   四象限：毛开+液开=磨砂底栏+液滴 / 只毛=磨砂底栏 / 只液=实色底栏+液滴 / 都关=实色底栏。
 *
 * ⚠️ **阴影只画一遍 / 亮线画一遍**：[sideAction] 非空（「按钮右置」）且玻璃时，那枚圆钮与胶囊是同一块浮起的面，
 * 阴影由本组件沿胶囊向右多铺「间距 + 圆钮直径」一次画完，**圆钮自己不再画阴影**——
 * 各画一遍会在间距处叠成近双倍暗（`1-(1-α)²`），观感发黑发沉（docs/11 098）。
 */
@Composable
fun JiabanBottomBar(
    slots: List<SlotSpec>,
    selectedRoute: String?,
    visible: Boolean,
    /** 槽位点击；**null = 纯展示**（配置页预览）：不挂 clickable、不播按压缩放，
     *  避免「按下去会缩、松手什么都不发生」的假可点 */
    onSlotClick: ((SlotSpec) -> Unit)?,
    modifier: Modifier = Modifier,
    /** 仅图标模式：隐藏槽位文字标签 */
    iconOnly: Boolean = false,
    /** 是否显示选中胶囊指示（预览场景可关闭） */
    showIndicator: Boolean = true,
    /** 底栏中央固定操作（如「记加班」主按钮）；null = 无（配置页预览）。槽位左右分组让出中央位 */
    centerAction: (@Composable () -> Unit)? = null,
    /** 右侧独立圆钮（v0.6.19「记加班按钮置右」布局）：与 [centerAction] 互斥；非空时胶囊只放槽位、
     *  整组（胶囊 + 间距 + 圆钮）居中 */
    sideAction: (@Composable () -> Unit)? = null,
    /** 背景模糊（毛玻璃）状态：非空时胶囊半透明 + 模糊身后内容；null = 实色底（配置页预览） */
    backdropBlur: BackdropBlurState? = null,
    /** 液态玻璃（**交互**开关，与毛玻璃**材质**互相独立）：按压时长出「放大底栏的液滴透镜」，
     *  不改静止材质（磨砂与否由 [backdropBlur] 决定）。需 API 33+（RuntimeShader），更低版本无液滴 */
    liquidGlass: Boolean = false,
    /** 拖动到中央按钮上松手时触发（液态玻璃档用）：拖动期间子项 clickable 已被消费，
     *  由这里补一次「落点在按钮上」的动作；点击按钮仍走它自己的 clickable（不会重复触发） */
    onCenterAction: (() -> Unit)? = null,
    /** 「固定长度」档：>0 时胶囊宽固定为该个数槽位宽（仍受可用宽上限约束），槽位在固定宽内等分；
     *  0 = 随槽位数自适应（默认）。见 BottomBarSpec.fixedCellCount */
    fixedCells: Int = 0,
) {
    // MD3E 出入场（双向对称）：一级→二级弹簧下沉退出、二级→一级弹簧浮入（defaultSpatialSpec）；
    // 进度归零且动画结束后跳过绘制。motionScheme 仅 composable 可调用——先取 spec 再传入（03 文档规则 7）
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    val progressSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(visible) {
        progress.animateTo(if (visible) 1f else 0f, progressSpec)
    }
    // 组合期不得逐帧读 progress.value（否则动画期间整个底栏每帧重组=转场高峰掉帧）；
    // derivedStateOf 收敛为布尔翻转：仅「完全隐藏」瞬间重组一次
    val fullyHidden by remember {
        androidx.compose.runtime.derivedStateOf { progress.value <= 0f }
    }
    if (!visible && fullyHidden) return
    val shape = engineShape(Radius.bar)
    val cs = MaterialTheme.colorScheme
    // 两层互相独立（2026-10-30 边界重规划）：
    // - 毛玻璃 = **材质**：半透明 + 模糊身后内容（API≥31；API<31 或无源时回退高不透明实底）
    // - 液态玻璃 = **交互**：按压时放大该处底栏的液滴透镜（API≥33），**不动静止材质**
    //   四象限：毛+液=磨砂底栏+液滴 / 只毛=磨砂底栏 / 只液=实色底栏+液滴 / 都关=实色底栏
    val glassOn = backdropBlur != null
    val glass = glassOn && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    // 液滴交互独立于材质：实色底栏也能有液滴。RuntimeShader 需 API 33+；预览（onSlotClick=null）不启用
    val liquid = liquidGlass && onSlotClick != null &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    val barColor = cs.surfaceContainer
    val tintTop = cs.surfaceContainer.copy(alpha = GlassCardSpec.alphaTop)
    val tintBottom = cs.surfaceContainer.copy(alpha = GlassCardSpec.alphaBottom)
    val tintFallback = cs.surfaceContainer.copy(alpha = GlassCardSpec.fallbackAlpha)
    // 底色画刷：玻璃=垂直渐变（上缘更透、下缘更实）；无模糊能力（API<31）=高不透明回退；非玻璃=实色
    val tintBrush = when {
        glass -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(tintTop, tintBottom))
        glassOn -> androidx.compose.ui.graphics.SolidColor(tintFallback)
        else -> androidx.compose.ui.graphics.SolidColor(barColor)
    }
    // 浮起胶囊的「阴影 + 亮线 + 内晕」（玻璃档与液滴共用；非玻璃仍走 M3 投影 + 细描边）
    val liftStyle = rememberCapsuleLiftStyle()
    // 衬托渐变：模糊层内淡色（空内容时给玻璃可糊之物）
    val scrimBrush = remember(cs) {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(
                androidx.compose.ui.graphics.Color.Transparent,
                cs.surfaceContainerHighest.copy(alpha = GlassCardSpec.scrimAlpha),
            ),
        )
    }
    val pillColor = cs.secondaryContainer
    val selectedIndex = slots.indexOfFirst { it.route == selectedRoute }
    var barWidth by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var screenW by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val density = LocalDensity.current

    // ---- 液态玻璃的液滴状态（实现文档 §7）----
    // 显隐/尺寸共用一个 Animatable；珠心 x 用普通 state：手势里同步写，只有滑行用 animate 写
    val press = remember { Animatable(0f) }
    var beadX by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var hovered by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    // 液滴在场期间选中态整体让位（文档 §8）
    val beadVisible by remember {
        androidx.compose.runtime.derivedStateOf { press.value > 0.01f }
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // 底栏自身录一份：液滴放大的是「玻璃 + 图标」，不是页面（文档 §3）
    val barBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()

    // ---- 液态玻璃手势（实现文档 §7）----
    // 按下→跟手→抬手全在同一条 awaitEachGesture 里（不要拆成两个探测器：抬手事件被槽位 clickable
    // 消费时会走取消分支，液滴会永远留在屏幕上，文档 §11.6）。
    // 拖动才 consume，且放在 **Initial pass**（父→子）：子项 clickable 收到「已消费」的移动即取消，
    // 于是拖动松手由这里导航；单击不 consume → 仍由槽位自己的 clickable 导航（只触发一次）。
    val liquidGesture = if (!liquid) {
        Modifier
    } else {
        Modifier.pointerInput(slots, selectedIndex, centerAction != null) {
            val leftCount = if (centerAction != null) slots.size / 2 else 0
            val cells = slots.size + if (centerAction != null) 1 else 0
            fun cellWidth(): Float = if (cells > 0) barWidth.toFloat() / cells else 0f
            fun cellAt(x: Float): Int {
                val w = cellWidth()
                if (w <= 0f) return -1
                return (x / w).toInt().coerceIn(0, cells - 1)
            }
            fun overCenter(x: Float): Boolean = centerAction != null && cellAt(x) == leftCount
            fun slotCenter(index: Int): Float {
                val cell = if (centerAction != null && index >= leftCount) index + 1 else index
                return (cell + 0.5f) * cellWidth()
            }
            fun centerX(): Float = (leftCount + 0.5f) * cellWidth()
            fun slotAt(x: Float): Int {
                val cell = cellAt(x)
                if (cell < 0 || slots.isEmpty()) return -1
                if (centerAction != null && cell == leftCount) return -1
                val index = if (centerAction != null && cell > leftCount) cell - 1 else cell
                return index.coerceIn(0, slots.size - 1)
            }
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (barWidth <= 0) return@awaitEachGesture
                var fingerX = down.position.x
                // 液滴从「当前选中项」长出，而不是手指位置：点击切换才画得成「选中从 A 滑到 B」
                val downOnCenter = overCenter(fingerX)
                beadX = if (downOnCenter) centerX() else slotCenter(selectedIndex.coerceAtLeast(0))
                hovered = if (downOnCenter) -1 else slotAt(fingerX)
                scope.launch { press.animateTo(1f, GlassLiquidSpec.pressSpec) }
                var dragged = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (dragged) change.consume()
                        break
                    }
                    if (!dragged && (change.position - down.position).getDistance() > slop) {
                        dragged = true
                    }
                    if (dragged) {
                        change.consume()
                        fingerX = change.position.x
                        beadX = change.position.x
                        hovered = if (overCenter(change.position.x)) -1 else slotAt(change.position.x)
                    }
                }
                hovered = -1
                if (dragged) {
                    // 拖动松手不滑行：珠子已在落点上，直接淡出；落点导航由这里补（clickable 已被消费）
                    if (centerAction != null && overCenter(fingerX)) {
                        onCenterAction?.invoke()
                    } else {
                        val target = slotAt(fingerX)
                        if (target >= 0 && target != selectedIndex) onSlotClick?.invoke(slots[target])
                    }
                    scope.launch { press.animateTo(0f, GlassLiquidSpec.releaseSpec) }
                } else {
                    // 点击不自己导航（槽位 clickable 会调）：这里只画「滑到点击项再消失」
                    val upOnCenter = overCenter(fingerX)
                    val to = if (upOnCenter) beadX else slotCenter(slotAt(fingerX).coerceAtLeast(0))
                    scope.launch {
                        if (!upOnCenter && kotlin.math.abs(to - beadX) > 1f) {
                            // 先立住再滑：快速点击时液滴还没亮就被淡出，滑行会整个看不到（文档 §11.9）
                            press.snapTo(1f)
                            androidx.compose.animation.core.animate(
                                initialValue = beadX,
                                targetValue = to,
                                animationSpec = GlassLiquidSpec.travelSpec,
                            ) { value, _ -> beadX = value }
                        }
                        press.animateTo(0f, GlassLiquidSpec.releaseSpec)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = progress.value.coerceIn(0f, 1f)
                translationY = (1f - progress.value) * 24.dp.toPx()
            }
            .fillMaxWidth()
            .onSizeChanged { screenW = it.width }
            // ⚠️ 给「浮起胶囊阴影」留出绘制余量（**只在玻璃时**）：叠层阴影是画到胶囊之外的，
            // 而上面那层 `graphicsLayer { alpha }`（出入场动画）在 alpha < 1 时会把节点内容
            // **裁到节点自身边界**——不补这段上边距，从二级页返回一级页时「底栏上方那圈阴影」
            // 会等到动画结束（alpha 回到 1、图层被丢弃）才突然冒出来（用户 2026-10-23 报）。
            // 只补上方：左右已被 `fillMaxWidth` 覆盖，下方就是屏幕底（再往外本来就看不见）。
            // 圆角+下移量取令牌 [GlassLiftSpec.bleed]，改阴影配方时这里跟着走。
            .padding(top = if (glass) GlassLiftSpec.bleed else 0.dp)
            .padding(horizontal = BottomBarSpec.horizontalMargin)
            .navigationBarsPadding()
            .padding(bottom = BottomBarSpec.bottomMargin),
    ) {
        val cellDp = if (iconOnly) BottomBarSpec.slotWidthIconOnly else BottomBarSpec.slotWidth
        val marginPx = with(density) { BottomBarSpec.horizontalMargin.toPx() }
        // 侧边圆钮占位（圆钮直径 + 间距）先从可用宽里扣掉，胶囊按剩余宽收窄
        val sideReservePx = if (sideAction != null) {
            with(density) { BottomBarSpec.sideActionGap.toPx() + BottomBarSpec.sideActionSize.toPx() }
        } else 0f
        val maxWpx = (screenW - 2 * marginPx - sideReservePx).roundToInt().coerceAtLeast(0)
        // 宽度档：固定长度（fixedCells>0）按固定槽位数取宽；自适应按实际槽位数（含中央按钮位）
        val actualCells = slots.size + if (centerAction != null) 1 else 0
        val widthCells = if (fixedCells > 0) fixedCells else actualCells
        val idealWpx = (widthCells * with(density) { cellDp.toPx() }).roundToInt()
        // 首帧 screenW 尚未测量：先按理想宽渲染，随后收进可用宽度
        val barWpx = if (screenW > 0) idealWpx.coerceAtMost(maxWpx) else idealWpx
        // 有侧边圆钮时：胶囊左移、圆钮右移，等价于「胶囊 + 间距 + 圆钮」整组居中（无需改成 Row 结构）
        val sideShiftPx = if (sideAction != null) {
            with(density) { BottomBarSpec.sideActionGap.toPx() + BottomBarSpec.sideActionSize.toPx() }
        } else 0f
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset((-sideShiftPx / 2f).roundToInt(), 0) }
                .width(with(density) { barWpx.toDp() })
                .height(BottomBarSpec.height)
                // 浮层效果两条路：
                // - 非玻璃：M3 投影 + 0.5dp outlineVariant 细描边；
                // - 玻璃（毛玻璃/液态玻璃同款）：配方卡「浮起胶囊」——叠层环境阴影（仅日间）+ 1dp 白亮线 + 2dp 内晕，
                //   见 component/CapsuleLift.kt 与 GlassLiftSpec。
                // ⚠️ 阴影必须挂在 `.clip()` **之前**（画在裁切之外）；亮线/内晕必须挂在链尾
                //   （画在内容之后，否则选中胶囊/图标会盖住亮线）。
                .then(
                    if (glass) Modifier.capsuleAmbientShadow(
                        liftStyle,
                        // 「按钮右置」：圆钮与胶囊并排、是**同一块浮起的面** ⇒ 阴影由胶囊一处
                        // 一直画到圆钮右缘（间距 + 直径），圆钮自己不再画。
                        // 两边各画一遍会在 8dp 间距处叠成近双倍暗（1-(1-α)²），用户 2026-10 报
                        // 「右置按钮的阴影与底栏阴影重叠导致观感较重」，见 docs/11 098。
                        extraWidth = if (sideAction != null) {
                            BottomBarSpec.sideActionGap + BottomBarSpec.sideActionSize
                        } else {
                            0.dp
                        },
                    )
                    else Modifier.shadow(elevation = BottomBarSpec.barElevation, shape = shape),
                )
                .clip(shape)
                .then(
                    if (glass) Modifier
                    else Modifier.border(BottomBarSpec.barBorderWidth, cs.outlineVariant, shape),
                )
                .onSizeChanged { barWidth = it.width }
                .then(if (glass) Modifier.capsuleEdgeHighlight(liftStyle) else Modifier),
        ) {
            // 材质 + 图标一起录一份：液滴放大的是「它正下方那块底栏」，不是页面（文档 §3）。
            // 只在液态玻璃档挂（静止时没人读它；上游库仍会随底栏重绘录制一份，底栏尺寸很小）
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (liquid) Modifier.layerBackdrop(barBackdrop) else Modifier),
            ) {
                // 玻璃采样层（胶囊最底层子层）：毛玻璃与液态玻璃**共用同一份磨砂**（文档 §4）
                if (glass) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .backdropBlur(backdropBlur, GlassCardSpec.blurRadius, backdrop = scrimBrush),
                    )
                }
                // 底色：叠在模糊之上、图标之下（玻璃渐变 / API<31 高不透明回退 / 非玻璃实色）。
                // 内高光/亮线不在这里画：玻璃改由链尾的 `capsuleEdgeHighlight` 沿整个外缘描
                // （1dp 白亮线 + 2dp 内晕，GlassLiftSpec）——原来只有顶部 2dp 一条白条，上下缘不对称
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(tintBrush),
                )

                // 滑动胶囊：M3 Expressive 活动指示，弹簧滑动到目标槽位
                // MIUIX：库 NavigationBar 没有滑动指示条（选中靠色彩/字重表达）→ 跳过 M3 胶囊指示
                // 液滴在场时让位（文档 §8）：淡出交给 alpha（不拆掉节点，否则新选中态会「啪」地出现）；
                // 珠子本身是不透明的（里面画的就是底栏），淡出后才露出下面的新选中态
                if (showIndicator && Radius.engine != ThemeEngine.MIUIX &&
                    barWidth > 0 && selectedIndex >= 0 && slots.isNotEmpty()
                ) {
                    // 胶囊数学用实际槽位数（固定长度档下槽位在固定宽内等分，与 Row 的 weight 一致）
                    val centerW = if (centerAction != null) barWidth.toFloat() / actualCells else 0f
                    val cellPx = (barWidth.toFloat() - centerW) / slots.size
                    val pillW = cellPx - with(density) { 12.dp.toPx() }
                    val pillH = with(density) { (if (iconOnly) 44.dp else 54.dp).toPx() }
                    val leftCount = if (centerAction != null) slots.size / 2 else 0
                    val flowIdx = selectedIndex + if (selectedIndex >= leftCount && centerAction != null) 1 else 0
                    val targetLeft = (flowIdx + 0.5f) * cellPx - pillW / 2
                    // MD3E：胶囊滑动走 motionScheme 空间 spec（expressive 弹簧过冲），禁硬编码 spring（03 文档规则 7）
                    val pillSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
                    val left by animateFloatAsState(
                        targetValue = targetLeft,
                        animationSpec = pillSpec,
                        label = "navPillSlide",
                    )
                    // 液滴让位：选中态在液滴在场时淡出、液滴消失后淡入（文档 §8 的「180ms 渐显」）
                    val pillAlpha by animateFloatAsState(
                        targetValue = if (beadVisible) 0f else 1f,
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "navPillAlpha",
                    )
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset { IntOffset(left.roundToInt(), 0) }
                            .size(width = with(density) { pillW.toDp() }, height = with(density) { pillH.toDp() })
                            .graphicsLayer { alpha = pillAlpha }
                            .background(pillColor, engineShape(Radius.pill)),
                    )
                }

                Row(
                    Modifier
                        .fillMaxSize()
                        .then(liquidGesture),
                ) {
                    val leftCount = if (centerAction != null) slots.size / 2 else 0
                    slots.take(leftCount).forEachIndexed { idx, slot ->
                        SlotCell(
                            slot = slot,
                            selected = idx == selectedIndex && !beadVisible,
                            hovered = beadVisible && hovered == idx,
                            iconOnly = iconOnly,
                            onClick = onSlotClick,
                        )
                    }
                    if (centerAction != null) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) { centerAction() }
                    }
                    slots.drop(leftCount).forEachIndexed { idx, slot ->
                        SlotCell(
                            slot = slot,
                            selected = leftCount + idx == selectedIndex && !beadVisible,
                            hovered = beadVisible && hovered == leftCount + idx,
                            iconOnly = iconOnly,
                            onClick = onSlotClick,
                        )
                    }
                }
            }
            // 液滴：压在录制层之上的一颗放大镜（只在按压期间存在，文档 §5）
            if (liquid) {
                LiquidDropletLayer(
                    press = press,
                    centerX = { beadX },
                    barWidthPx = { barWidth.toFloat() },
                    backdrop = barBackdrop,
                    liftStyle = liftStyle,
                )
            }
        }
        if (sideAction != null) {
            // 右侧独立圆钮（如记加班）：容器与胶囊同高、内容居中 → 圆钮与胶囊垂直居中对齐；
            // 水平紧贴胶囊右侧，与胶囊一起构成居中组
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .height(BottomBarSpec.height)
                    .offset {
                        IntOffset(
                            ((barWpx + with(density) { BottomBarSpec.sideActionGap.toPx() }) / 2f).roundToInt(),
                            0,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                sideAction()
            }
        }
    }
}

/** 底栏槽位单元：等分宽 + 按压缩放 + 点击（[onClick] 为 null 时完全不响应，纯展示） */
@Composable
private fun androidx.compose.foundation.layout.RowScope.SlotCell(
    slot: SlotSpec,
    selected: Boolean,
    iconOnly: Boolean,
    /** null = 不可点（配置页预览）：不挂 clickable、也不播按压缩放 */
    onClick: ((SlotSpec) -> Unit)?,
    /** 液滴在场时手指正压着的槽位：只换主题色图标，不画深色底（文档 §8） */
    hovered: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(
                if (onClick == null) Modifier
                else Modifier
                    .pressScale(interaction, pressedScale = 0.9f)
                    .clickable(indication = null, interactionSource = interaction) { onClick?.invoke(slot) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        SlotBody(slot, selected = selected, hovered = hovered, iconOnly = iconOnly)
    }
}

/** 槽位内容：图标（选中切填充变体 + 弹性放大）+ 可选文字，整体在槽内居中 */
@Composable
private fun SlotBody(
    slot: SlotSpec,
    selected: Boolean,
    hovered: Boolean = false,
    iconOnly: Boolean,
) {
    // MIUIX：对齐库 NavigationBarItem 的色彩语义——选中 = 强调色，未选 = onSurface × 库的
    // UnselectedAlpha(0.4)（库靠透明度而非另一种灰度表达"未选中"）；MD3 保持原观感
    val miuix = Radius.engine == ThemeEngine.MIUIX
    val tint by animateColorAsState(
        targetValue = when {
            // 液滴在场：手指下的槽位只亮主题色（镜片会盖住深色底，画了也看不见，文档 §8）
            hovered -> MaterialTheme.colorScheme.primary
            miuix -> {
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = NavigationBarDefaults.UnselectedAlpha)
            }
            selected -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "slotTint",
    )
    // MD3E：图标放大走 motionScheme 空间 spec
    val scaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val iconScale by animateFloatAsState(
        targetValue = if (!miuix && selected) 1.1f else 1f,
        animationSpec = scaleSpec,
        label = "slotIconScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(if (selected) slot.iconFilledRes else slot.iconRes),
            contentDescription = stringResource(slot.labelRes),
            tint = tint,
            modifier = Modifier
                .size(if (miuix) NavigationBarDefaults.IconSize else IconSpec.bar)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
        )
        if (!iconOnly) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(slot.labelRes),
                style = if (!miuix && selected) MaterialTheme.typography.labelMedium
                else MaterialTheme.typography.labelSmall,
                fontWeight = if (miuix) {
                    if (selected) FontWeight.Bold else FontWeight.Normal
                } else if (selected) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
                color = tint,
            )
        }
    }
}

/**
 * 液滴透镜（实现文档 §5）：只在按压期间存在，压在录制好的底栏之上。
 *
 * ⚠️ 长出靠**改盒子尺寸**，不能在 `graphicsLayer` 里做 scale——图层缩放会连 `drawBackdrop` 的取样
 * 一起缩，生长期间会看到珠子从左上角开始长、内容是底栏最左边，比例到 1 才归位（文档 §11.2）。
 * 图层只承载淡出（alpha）。
 *
 * 位置只沿底栏横向（珠心 x 来自手势/滑行动画），纵向恒居中；珠子比底栏高时会被父级量回底栏高度，
 * 所以垂直偏移按「实际布局高度」算（文档 §11.3）。
 */
@Composable
private fun LiquidDropletLayer(
    press: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    centerX: () -> Float,
    barWidthPx: () -> Float,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    liftStyle: CapsuleLiftStyle,
) {
    // 在组合里读：液滴尺寸是它布局的一部分（文档 §5.2）
    val grown = press.value
    if (grown <= 0.001f) return
    val scale = androidx.compose.ui.util.lerp(GlassLiquidSpec.growthStart, 1f, grown)
    val beadWidth = GlassLiquidSpec.dropletWidth * scale
    val beadHeight = GlassLiquidSpec.dropletHeight * scale
    val barHeight = BottomBarSpec.height
    // 高光按明暗分两档（用户 2026-10-30 真机反馈）：
    // - 浅色：白高光在浅底上看不清 → 用「专用玻璃高光 + 深色描边」把边界立住；
    // - 深色：白高光本身够清楚，但之前那版（capsuleEdgeHighlight）更好看 → 换回，且**不加描边**。
    val gloss: Modifier = if (liftStyle.isLight) {
        Modifier
            .liquidDropletGloss()
            .border(
                GlassLiquidSpec.borderWidth,
                androidx.compose.ui.graphics.Color.Black.copy(alpha = GlassLiquidSpec.borderAlphaLight),
                LiquidDropletShape,
            )
    } else {
        Modifier.capsuleEdgeHighlight(liftStyle)
    }
    Box(
        Modifier
            .offset {
                val half = beadWidth.toPx() / 2f
                // 透镜实际能读到的半径：放大把取样往回拉、边缘弯折再拉一次 ⇒ 珠心离两端至少这么远，
                // 否则边缘像素会取到录制层之外，珠子上下出现「透出页面」的横带（文档 §11.4）
                val reach = half * (1f / GlassLiquidSpec.magnification - GlassLiquidSpec.bendDepth * 2f)
                val sampled = reach.coerceIn(0f, barWidthPx() / 2f)
                val x = centerX().coerceIn(sampled, (barWidthPx() - sampled).coerceAtLeast(sampled))
                val laidOutHeight = minOf(beadHeight, barHeight)
                IntOffset(
                    (x - half).roundToInt(),
                    ((barHeight - laidOutHeight).toPx() / 2f).roundToInt(),
                )
            }
            .width(beadWidth)
            .height(beadHeight)
            .graphicsLayer { alpha = grown }
            .clip(LiquidDropletShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { LiquidDropletShape },
                effects = {
                    // 液滴节点就是整颗珠子，所以珠子矩形就是节点矩形（文档 §6.3）
                    val halfWidth = size.width / 2f
                    val halfHeight = size.height / 2f
                    liquidDroplet(
                        magnification = GlassLiquidSpec.magnification,
                        centerX = halfWidth,
                        centerY = halfHeight,
                        halfWidth = halfWidth,
                        halfHeight = halfHeight,
                        rimHeight = halfHeight * 2f * GlassLiquidSpec.rimBand,
                        bendDepth = halfHeight * 2f * GlassLiquidSpec.bendDepth,
                        dispersion = GlassLiquidSpec.dispersion,
                    )
                },
            )
            // 高光/描边按明暗分档，见上方 gloss
            .then(gloss),
    )
}
