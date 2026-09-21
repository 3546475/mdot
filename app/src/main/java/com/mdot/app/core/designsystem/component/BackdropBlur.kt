package com.mdot.app.core.designsystem.component

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize

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
    /** 源录制的内容层（首帧前为 null，效果方该帧跳过模糊只画半透明底色） */
    internal var layer: GraphicsLayer? = null
    /** 源的 LayoutCoordinates（效果方据此把「身后」区域对齐到自身原点） */
    internal var sourceCoords: LayoutCoordinates? = null
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

    /** 源录完一帧：普通计数器自增（不产生快照观察）后，把值**只写**进快照通知 */
    internal fun onRecorded() {
        recordSeq++
        recordTick = recordSeq
    }
}

@Composable
fun rememberBackdropBlurState(): BackdropBlurState = remember { BackdropBlurState() }

/** 背景模糊源：每帧把自身内容录进共享 [BackdropBlurState.layer] 并上屏。挂在「会被效果方盖住」的内容上（如导航宿主） */
fun Modifier.backdropBlurSource(state: BackdropBlurState): Modifier = composed {
    val graphicsLayer = rememberGraphicsLayer()
    // 录制层实底色 = 宿主 Surface 同色（MainActivity `Surface(color = colorScheme.background)`）。
    // 缺了它 → 层里只有透明像素 + 内容元素，模糊后压不住身后锐利页面（见类注释 ⚠️⚠️）。
    val backdropColor = MaterialTheme.colorScheme.background
    this
        .onGloballyPositioned { state.sourceCoords = it }
        .drawWithContent {
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
 * 背景模糊效果方（独立子层）：把源录制的内容层平移对齐后直接绘入自身节点层，再整层模糊
 * （`Modifier.blur`）。
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
    // 自身原点在源局部坐标系中的位置（localPositionOf 经共同祖先双向换算，
    // 祖先自身的缩放/平移变换两两抵消；布局与位移动画期间随坐标回调即时刷新）
    var offsetInSource by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { coords ->
            val src = state.sourceCoords
            if (src != null && src.isAttached && coords.isAttached) {
                val p = src.localPositionOf(coords, Offset.Zero)
                if (p != offsetInSource) offsetInSource = p
            }
        }
        // ⚠️ blur 必须在链首（最外层）：Modifier 靠前的修饰符包住后续绘制。
        // 若放 drawWithContent 之后（最内层），drawLayer(src) 由外层直接画到画布、
        // blur 只包住空内容 → 采样层锐利、模糊不生效（实为「透出锐利页面」）。
        .blur(radius)
        .drawWithContent {
            // 读录制通知建立快照依赖：源每重录一帧，本 draw 失效重采样
            state.recordTick
            val src = state.layer
            if (src != null) {
                translate(left = -offsetInSource.x, top = -offsetInSource.y) {
                    drawLayer(src)
                }
            }
            // 衬托渐变在模糊**之内**：与身后内容一起被糊（空内容时给玻璃一层淡渐变可糊）
            backdrop?.let { drawRect(brush = it, size = size) }
            drawContent()
        }
}
