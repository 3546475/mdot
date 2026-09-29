package com.mdot.app.core.designsystem.component

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import androidx.compose.runtime.DisposableEffect

/**
 * 背景模糊（毛玻璃）共享状态：桥接「源」（被模糊的内容）与「效果方」（如悬浮底栏）。
 *
 * 原理（与弹层背景模糊同宗，均基于 RenderEffect，只是对象从「内容自身」换成「身后内容」）：
 * - 源：每帧用 **DrawScope 作用域内的 `GraphicsLayer.record(size) { drawContent() }`** 把自身内容
 *   录进离屏 [GraphicsLayer]（该重载会把当前绘制上下文重定向到层的录制 canvas，内容真正进层），
 *   再 `drawLayer` 上屏；
 * - 效果方：把该层按两者在共同坐标系中的相对位置平移、直接绘进自身节点层，并给该节点层挂模糊
 *   （`Modifier.blur`，内部即 RenderEffect + 离屏合成，与弹层背景模糊同款）——效果方区域因此呈现
 *   「身后内容被模糊」的毛玻璃质感，而源层本身不带 Effect、不受污染。
 *
 * ⚠️ 必须用 DrawScope 作用域内的 `record(size) { drawContent() }` 重载（虚拟分发到
 * LayoutNodeDrawScope 的重定向实现）；直接调 `GraphicsLayer.record(density, layoutDirection, size)`
 * 4 参成员会把 `drawContent()` 画到屏幕而层保持为空。
 *
 * ⚠️⚠️ **录制层必须自带不透明实底**（见 [backdropBlurSource]）：页面自身背景是**透明**的
 * （浅色底由 MainActivity 的 Surface 提供，在导航宿主**之外**）。不补实底时录制层绝大部分像素
 * alpha=0，模糊后仍是「半透明、且颜色与身后内容完全相同」的一层 —— 把它盖在锐利的页面之上
 * **等于什么都没盖**，毛玻璃会呈现为「只有底色 tint、完全没糊」。这与弹层背景模糊当年踩的是同一个
 * 坑（`SheetBackdropLayer` 内部的「不透明实底」处理，docs/11 036）。
 *
 * 效果方必须作为独立**子层**使用（在胶囊里位于半透明底色之下、图标之上）：`Modifier.blur`
 * 语义是模糊节点的整层内容，若直接挂在含图标的整条胶囊链上，图标也会一起糊掉。
 *
 * [backdrop]：模糊层内额外衬托渐变（随模糊一起生效）。页面内容不延伸到底栏正后方时
 * （`contentBottomPadding` 让位），身后是纯平背景、无物可糊——此时胶囊内叠一层极淡渐变，
 * 让玻璃在空白处也有层次（iOS TabBar 同款思路）。
 *
 * API<31 无 RenderEffect：[backdropBlur] 整体不挂载，回退由调用方用更高不透明度底色承担
 * （与 SheetBackdropLayer 的回退策略一致）。
 */
@Stable
class BackdropBlurState internal constructor() {
    /** 源录制的内容层（首帧前为 null，效果方该帧跳过模糊只画半透明底色）。
     *  ⚠️ 快照状态 + neverEqualPolicy（2026-09-28 修「冷启动圆钮不模糊、切页才正常」）：
     *  普通 var 时效果方感知不到源的重录（失效链只靠 recordTick），首帧走 fallback 后
     *  可能不重画直到节点重建；neverEqual 保证源每次 record 赋值必触发效果方重画 */
    internal var layer: GraphicsLayer? by mutableStateOf(null, androidx.compose.runtime.neverEqualPolicy())
    /** 源的 LayoutCoordinates（效果方据此把「身后」区域对齐到自身原点）；同上快照状态 */
    internal var sourceCoords: LayoutCoordinates? by mutableStateOf(null, androidx.compose.runtime.neverEqualPolicy())
    /** 源所在窗口的宿主 View（跨窗口消费者用它取窗口屏幕原点：screen = positionInWindow + 窗口原点） */
    internal var sourceView: android.view.View? = null
    /** 录制序号（**普通变量**，非快照）：源在 draw 里自增用，见 [onRecorded] 的 ⚠️ */
    private var recordSeq = 0
    /**
     * 录制通知：效果方在 draw 里读它决定何时重新采样。
     *
     * ⚠️ 源**只能写、绝不读**：若写成 `tick++`（先读后写），源就在自己的 draw 里观察了自己——
     * 同一帧的写入立即让源自身失效 → 下一帧又重录又自增 → **每帧全屏重录的死循环**
     * （实测：静止不动也是 60 次/秒重录，帧 p90 24ms、19% 帧超 16ms）。
     */
    internal var recordTick by mutableIntStateOf(0)

    /**
     * 本源的**录制需求计数**：消费该源的效果方（玻璃 [backdropBlur] / 对话框背景）挂载时 +1、卸载 -1；
     * 为 0 时源只透传不录（省一次全屏/全页重录）。
     *
     * ⚠️ **必须按源各自计数**：玻璃消费的是导航宿主小源、对话框背景消费的是全画面源；
     * 用全局单计数器会让「开玻璃」连带让全画面源每帧重录（正是需求计数要省掉的开销）。
     */
    internal var demand by mutableIntStateOf(0)

    /** 源录完一帧：普通计数器自增（不产生快照观察）后，把值**只写**进快照通知 */
    internal fun onRecorded() {
        recordSeq++
        recordTick = recordSeq
    }
}

@Composable
fun rememberBackdropBlurState(): BackdropBlurState = remember { BackdropBlurState() }

/** 全局玻璃源下发（MainActivity provides；null = 毛玻璃关/不可用）：
 *  底部悬浮提示（MessageSnackbar）等**源之外无自有源**的零散玻璃从这里取共享源采样 */
val LocalBackdropGlassState = compositionLocalOf<BackdropBlurState?> { null }



/**
 * 背景模糊源：把自身内容录进共享 [BackdropBlurState.layer] 并上屏。挂在「会被效果方盖住」的内容上（如导航宿主）。
 *
 * ⚠️ 是否录制**在 draw 内读需求计数**、不做成取模参数——做成参数会让对话框开关时该 modifier 链
 * 变更 → 内容根**重新布局一帧** → 源正好录到那一帧 = 用户看到的「点击时画面往下抖一下」
 * （2026-09-30 日志取证：offset 恒 0，故抖动只可能来自被录内容）。draw 内读状态只触发重绘。
 */
fun Modifier.backdropBlurSource(state: BackdropBlurState): Modifier = composed {
    val graphicsLayer = rememberGraphicsLayer()
    val hostView = androidx.compose.ui.platform.LocalView.current
    // 录制层实底色 = 宿主 Surface 同色（MainActivity `Surface(color = colorScheme.background)`）。
    // 缺了它 → 层里只有透明像素 + 内容元素，模糊后压不住身后锐利页面（见类注释 ⚠️⚠️）。
    val backdropColor = MaterialTheme.colorScheme.background
    this
        .onGloballyPositioned {
            state.sourceCoords = it
            state.sourceView = hostView
        }
        .drawWithContent {
            // 读需求计数（draw 内订阅：变化只重绘、不改 modifier 结构）
            if (state.demand <= 0) {
                this@drawWithContent.drawContent()
                return@drawWithContent
            }
            // DrawScope 作用域内的 record 重载：重定向绘制上下文到录制 canvas（见类注释 ⚠️）
            graphicsLayer.record(size.toIntSize()) {
                // ⚠️ 先铺不透明实底，再画内容：否则层是透明的，模糊层盖不住身后锐利页面
                drawRect(color = backdropColor, size = this.size)
                this@drawWithContent.drawContent()
            }
            state.layer = graphicsLayer
            state.onRecorded()
            drawLayer(graphicsLayer)
        }
}

/**
 * 背景模糊效果方（独立子层）——**2026-09-28 按 AndroidLiquidGlass（Kyant0）工程方案重写**，
 * 三招治本项目在真机上暴露的采样顽疾（黑影整块闪断/错位无影/边缘露锐利）：
 *  1. **外扩录制层**：把「平移后的源 + 衬托渐变」录进四周外扩 blur 半径的自有 GraphicsLayer，
 *     blur（RenderEffect）挂在**层**上——羽化/边缘采样发生在外扩区（可见窗外），窗口边缘平滑；
 *  2. **TileMode.Clamp**：模糊采样越界取边缘像素，源层边缘（屏幕底边）不发黑不透空；
 *  3. **坐标换算双路兜底**：`localPositionOf` 在外层变换下会算错（LiquidGlass 作者 TODO 同记），
 *     异常/失配时改用 `positionInWindow` 差值。
 *  另：外扩区铺背景实底——窗边外的羽化过渡到实底，绝不透出身后锐利内容。
 *
 * 调用方负责修饰符顺序：放在 **clip 之后**（模糊绘制随形状裁切）、**半透明底色与图标之前**
 * （即作为胶囊最底层子层；图标在兄弟层保持锐利）。API<31 / [radius]≤0 时整体不挂载。
 */
fun Modifier.backdropBlur(
    state: BackdropBlurState,
    radius: Dp,
    /** 模糊层内衬托渐变（随模糊一起生效），null = 不画 */
    backdrop: Brush? = null,
): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radius <= 0.dp) return@composed this
    val density = androidx.compose.ui.platform.LocalDensity.current
    val blurPx = with(density) { radius.toPx() }
    val pad = blurPx.toInt()
    val backdropColor = MaterialTheme.colorScheme.background
    // 效果层 + 模糊效果（Clamp 边缘；BlurEffect 可复用，radius 变化才重建）
    val layer = rememberGraphicsLayer()
    // 玻璃消费方即录制需求方：挂载期间提高**本源**的需求计数。
    // ⚠️ 缺了它：无对话框时源不录制 → 玻璃采到空层 → 底栏/圆钮"变透明"（2026-09-30 用户报告）
    DisposableEffect(state) {
        state.demand++
        onDispose { state.demand-- }
    }
    val blurEffect = remember(radius) {
        BlurEffect(with(density) { radius.toPx() }, with(density) { radius.toPx() }, TileMode.Clamp)
    }
    // 自身布局坐标（每帧绘制时现算相对偏移；只存引用，draw 内只读）
    var myCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // 启动期续画重试（2026-09-28 修「冷启动圆钮画一帧即定格」）：首帧可能采到源尚未同步的
    // 空纹理，之后若无失效即永久定格。draw 内**读写分离**（读 selfRetry 建立依赖、
    // 用普通计数器 retrySeq 生成新值只写回）——有限 3 次重试，非 tick++ 自触发死循环（坑④）
    val selfRetry = remember { mutableIntStateOf(0) }
    val retrySeq = remember { intArrayOf(0) }
    this
        .onGloballyPositioned { coords -> myCoords = coords }
        .drawWithContent {
            // 读录制通知建立快照依赖：源每重录一帧，本 draw 失效重采样
            state.recordTick
            selfRetry.intValue // 读：建立重试依赖（与下方写分离，非读-改-写）
            if (retrySeq[0] < 3) {
                retrySeq[0] = retrySeq[0] + 1
                selfRetry.intValue = retrySeq[0] // 只写：安排下一帧续画（启动期防首帧空纹理定格）
            }
            val src = state.layer
            val self = myCoords
            val srcCoords = state.sourceCoords
            val offset =
                if (src != null && self != null && srcCoords != null &&
                    self.isAttached && srcCoords.isAttached
                ) {
                    runCatching { srcCoords.localPositionOf(self, Offset.Zero) }
                        .getOrElse { self.positionInWindow() - srcCoords.positionInWindow() }
                } else null
            if (src != null && offset != null) {
                val winW = size.width
                val winH = size.height
                layer.record(IntSize(winW.toInt() + pad * 2, winH.toInt() + pad * 2)) {
                    // 外扩区铺背景实底：羽化带过渡到实底，绝不透出身后锐利内容
                    drawRect(color = backdropColor, size = this.size)
                    translate(left = pad - offset.x, top = pad - offset.y) {
                        drawLayer(src)
                    }
                    // 衬托渐变在模糊**之内**（只画窗口区，随 blur 一起糊）
                    backdrop?.let { brush ->
                        translate(left = pad.toFloat(), top = pad.toFloat()) {
                            drawRect(brush = brush, size = Size(winW, winH))
                        }
                    }
                }
                layer.topLeft = IntOffset(-pad, -pad)
                layer.renderEffect = blurEffect
                drawLayer(layer)
            } else {
                backdrop?.let { drawRect(brush = it, size = size) }
            }
            drawContent()
        }
}
