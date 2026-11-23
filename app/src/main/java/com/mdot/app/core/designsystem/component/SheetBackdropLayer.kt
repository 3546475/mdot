package com.mdot.app.core.designsystem.component

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.mdot.app.core.designsystem.SheetBackdrop
import com.mdot.app.domain.model.SheetBackdropMode

/**
 * 弹层背景层（RecordSheet / SiteBottomSheet 同构）：弹层出现时背景内容
 * **缩小成圆角卡片 + 模糊**，四周露出压暗底衬；上方再叠一层轻压暗。
 *
 * 用法——把「会被弹层压住的页面内容」（导航宿主/顶栏/底栏/FAB）整体包进来，
 * 弹层自身留在外层兄弟位置（不被缩放模糊）：
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     SheetBackdropLayer(visible = sheetVisible.targetState) { 页面内容 }
 *     sheet?.let { RecordSheet(visibleState = sheetVisible, ...) }   // 弹层在最上层
 * }
 * ```
 *
 * - [visible] 与弹层共享同一过渡状态（`MutableTransitionState.targetState`），
 *   进出场因此严格同步；离开时本层停留在 display 内（仅回调 [onDismiss] 后由调用方摘除弹层）
 * - 缩放/模糊/压暗/圆角全部由**单一 progress** 派生，禁止各自为政（相位错开会露馅）
 * - 效果组合由 [LocalGlassEnvironment] 的 `sheetMode` 决定：压暗＝只压暗（与改造前逐像素一致）；
 *   模糊＝模糊 + 轻压暗；模糊缩小＝再叠缩放 0.94 + 28dp 圆角（缩小时才裁切，否则四角露底）
 * - API<31 无 RenderEffect：模糊档自动不挂载，压暗回退到 [SheetBackdrop.contentScrimNoBlur]
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SheetBackdropLayer(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val mode = LocalGlassEnvironment.current.sheetMode
    // 单一进度驱动全部效果：0=静止（无遮罩）1=弹层完全展开
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        // Spatial spec：与弹层自身滑入/滑出同档（背景后退与面板上滑同拍；档位随引擎）
        animationSpec = com.mdot.app.core.designsystem.sheetSpatialSpec<Float>(),
        label = "sheetBackdrop",
    )
    // 模糊只在 API 31+ 生效（更低版本置 0 让 Modifier.blur 整体不挂载）
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val blurActive = blurSupported && mode != SheetBackdropMode.DIM && SheetBackdrop.blurRadius > 0.dp
    val targetBlur = if (blurActive) SheetBackdrop.blurRadius else 0.dp
    val targetDim = if (blurActive) SheetBackdrop.contentScrim else SheetBackdrop.contentScrimNoBlur
    // 仅「模糊缩小」档缩放并裁圆角：其余档整屏铺满，裁圆角会在四角露底
    val targetScale = if (mode == SheetBackdropMode.BLUR_SCALE) SheetBackdrop.scale else 1f
    val clipProgress = if (targetScale < 1f) progress else 0f
    // 模糊两引擎统一走内容自糊（下方 Modifier.blur）：本层与共享源同处一个窗口，用 miuix-blur 的
    // overlay 采样会 RenderNode 自引用（与源的同帧录制冲突 → 闪退，见 docs/11 062）。

    Box(
        modifier
            .fillMaxSize()
            // 底衬：内容缩小后四周露出的压暗层（内容铺满时完全被盖住）
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = SheetBackdrop.backdropScrim * progress))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // 外层负责缩放 + 圆角裁切（裁在模糊结果之上，圆角保持锐利）；
                // 内层模糊先作用于内容本身
                .graphicsLayer {
                    val scale = 1f + (targetScale - 1f) * progress
                    scaleX = scale
                    scaleY = scale
                    if (clipProgress > 0f) {
                        shape = RoundedCornerShape(SheetBackdrop.cornerRadius.toPx() * clipProgress)
                        clip = true
                    }
                }
                .blur(targetBlur * progress)
        ) {
            // 不透明实底：页面自身多为透明（浅色底由 MainActivity 的 Surface 提供，在本层**之外**）——
            // 不补实底的话，模糊会把透明像素与被压暗的背板混成脏影（实测背景整片发黑，见 docs/11 036）
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            content()
        }
        // 内容上方压暗（点击仍可穿透到内容，但弹层遮罩挡板在更上层）
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = targetDim * progress))
        )
    }
}
