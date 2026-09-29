package com.mdot.app.core.designsystem.miuix

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import com.mdot.app.core.designsystem.component.BackdropBlurState
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 把本 App 的**共享录制源**（[BackdropBlurState]）适配成 miuix-blur 的 [Backdrop] 接口。
 *
 * 坐标换算（2026-09-30 帧取证定案）：`positionInWindow` 是**各自窗口内坐标**——跨窗口时
 * 还差一个**窗口屏幕原点**（装饰嵌入/圆角屏/状态栏会让两窗原点不同），漏掉就是用户看到的
 * 「快照偏右上」+ 启动期窗口原点微调导致的「画面抖动」。故偏移 =
 * `(selfInWindow + selfWinOrigin) - (srcInWindow + srcWinOrigin)`，originView 非空时
 * **每次绘制现算**两窗原点（同窗场景传 null，原点相消 = 退化为窗口内差值）。
 */
class MiuixSourceBackdrop(
    private val state: BackdropBlurState,
    private val originView: android.view.View? = null,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: androidx.compose.ui.unit.Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        val src = state.layer ?: return
        val srcCoords = state.sourceCoords ?: return
        val self = coordinates
        val offset =
            if (self != null && self.isAttached && srcCoords.isAttached) {
                val delta =
                    if (originView != null) {
                        windowScreenOrigin(originView) - windowScreenOrigin(
                            state.sourceView ?: return,
                        )
                    } else Offset.Zero
                self.positionInWindow() - srcCoords.positionInWindow() + delta
            } else {
                Offset.Zero
            }
        // downscaleFactor：库按 1/ds 采样录制背景（录制面 = 元素/ds），故须同步缩放背景以适配；
        // ⚠️ 缩放轴必须是**原点**（pivot = Offset.Zero）——用默认的中心轴会让 (0,0) 映射到
        // ((w/ds - w/ds²), (h/ds - h/ds²))，库再放大 ds 倍即 ~(w/4,h/4) 的错乱位移
        //（2026-09-30 标记实测：(0,0) 标记被画到 (279,594) ≈ (1080/4, 2400/4)，ds=2 时正中此坑）
        val ds = if (downscaleFactor > 1) downscaleFactor.toFloat() else 1f
        if (ds != 1f) {
            scale(1f / ds, 1f / ds, pivot = Offset.Zero) {
                translate(left = -offset.x, top = -offset.y) {
                    drawLayer(src)
                }
            }
        } else {
            translate(left = -offset.x, top = -offset.y) {
                drawLayer(src)
            }
        }
    }
}

/** 窗口屏幕原点 = View 的屏幕坐标 − 窗口内坐标（每次调用现算，捕捉启动期原点微调） */
fun windowScreenOrigin(view: android.view.View): Offset {
    val screen = IntArray(2)
    val window = IntArray(2)
    view.getLocationOnScreen(screen)
    view.getLocationInWindow(window)
    return Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat())
}
