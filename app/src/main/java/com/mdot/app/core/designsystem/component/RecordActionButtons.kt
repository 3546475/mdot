package com.mdot.app.core.designsystem.component

import com.mdot.app.core.designsystem.engineShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.BottomBarSpec
import com.mdot.app.core.designsystem.GlassCardSpec

/*
 * 底栏「记加班」主操作按钮（两种布局共用）：
 * - [RecordPillButton]：居中布局用的方圆形主色按钮（52dp / 20dp 圆角）；
 * - [RecordCircleButton]：右侧独立圆钮（[BottomBarSpec.sideActionSize]，毛玻璃开时与该套玻璃一致）。
 *
 * ⚠️ 底栏配置页预览**直接复用**本文件（而不是另画一个简化版）——否则改了真实底栏就会漏改预览
 * （曾漏改：预览仍是实心蓝圆钮 + 实色胶囊，与真实毛玻璃底栏不一致）。
 * 出入场（弹簧弹入）/按压（pressScale + 阴影贴合）在此统一实现，调用方只传 [playEntrance]。
 */

/**
 * 底栏中央主操作按钮（记加班/记工）：主色圆形（M3 FAB 形态），M3E 动效——
 * 入场弹簧弹入（slowSpatialSpec）+ 按压 shape morph（圆形→超圆角方，fastSpatialSpec）+ 阴影贴合（fastEffectsSpec）。
 * spec 须先在 composable 上下文取出再传入动画 API（03 文档规则 7）。
 */
@Composable
fun RecordPillButton(
    /** null = 纯展示（配置页预览）：不挂 clickable、不播按压缩放 */
    onRecord: (() -> Unit)?,
    /** 仅首次出现播放弹簧弹入；底栏隐藏→再现（组合销毁重建）时不重播 */
    playEntrance: Boolean,
    onEntranceDone: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val entrance = remember { Animatable(if (playEntrance) 0f else 1f) }
    val entranceSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        if (playEntrance) {
            entrance.animateTo(1f, entranceSpec)
            onEntranceDone()
        }
    }
    val elevSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val elevation by animateFloatAsState(
        targetValue = if (pressed) 1f else 3f,
        animationSpec = elevSpec,
        label = "recordPillElevation",
    )
    // 方圆形 20dp，与底栏配置页预览完全一致（按压反馈由 pressScale 缩放 + 阴影贴合承担，无形状 morph）
    val shape = engineShape(Radius.textField)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = elevation.dp,
        modifier = Modifier
            .graphicsLayer {
                val e = entrance.value
                alpha = e
                val scale = 0.8f + 0.2f * e
                scaleX = scale
                scaleY = scale
                translationY = (1f - e) * 24.dp.toPx()
            }
            .then(if (onRecord != null) Modifier.pressScale(interaction, pressedScale = 0.9f) else Modifier)
            .clip(shape)
            .then(
                if (onRecord == null) Modifier
                else Modifier.clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                ) { onRecord?.invoke() },
            ),
    ) {
        Box(
            Modifier.size(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_ms_more_time), null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * 右侧独立圆形「记加班」按钮（v0.6.19 新增底栏布局）：**始终只显示图标、不显示文字**；
 * 出入场/按压与中央胶囊按钮一致。
 *
 * [backdropBlur] 非空时（「按钮右置」+ 外观「毛玻璃/液态玻璃」开）圆钮与底栏同一套玻璃质感：
 * 半透明中性底色渐变 + 模糊身后内容（液态玻璃再叠边缘折射）+ **沿外缘的 1dp 白亮线 / 2dp 内晕**
 * （`GlassLiftSpec` + `CapsuleLift.kt`，与底栏胶囊同源）——**不再是主色实心**（既然是玻璃就与该套中性玻璃一致）；
 * API<31 回退高不透明中性底。
 * 结构同底栏：模糊层在最底、底色与图标在兄弟层 → 只糊背景、图标保持锐利。
 *
 * ⚠️ **圆钮自己不画叠层环境阴影**：它与底栏胶囊是**同一块浮起的面**，阴影由 `JiabanBottomBar`
 * 沿胶囊向右多铺「间距 + 圆钮直径」一次画完。两边各画一遍会在间距处叠成近双倍暗
 * （`1-(1-α)²`），用户 2026-10 报「右置按钮的阴影和底栏阴影有重叠部分，观感较重」（docs/11 098）。
 * 加回这行前先想清楚：圆钮永远紧贴胶囊右侧，不存在「单独悬空」的场景。
 */
@Composable
fun RecordCircleButton(
    /** null = 纯展示（配置页预览）：不挂 clickable、不播按压缩放 */
    onRecord: (() -> Unit)?,
    /** 仅首次出现播放弹簧弹入；底栏隐藏→再现（组合销毁重建）时不重播 */
    playEntrance: Boolean,
    onEntranceDone: () -> Unit,
    /** 毛玻璃源（「按钮右置」布局 + 外观「毛玻璃/液态玻璃」开时传入；null = 实心主色） */
    backdropBlur: BackdropBlurState? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val entrance = remember { Animatable(if (playEntrance) 0f else 1f) }
    val entranceSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        if (playEntrance) {
            entrance.animateTo(1f, entranceSpec)
            onEntranceDone()
        }
    }
    val cs = MaterialTheme.colorScheme
    // 材质：毛玻璃与液态玻璃共用同一份磨砂（液态玻璃不改材质，见 JiabanBottomBar / 实现文档 §4），
    // 都需要 RenderEffect（API≥31）；API<31 回退高不透明中性底
    val frosted = backdropBlur != null &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val elevSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    // 浮起胶囊（圆形）的「阴影 + 亮线 + 内晕」：与底栏胶囊同一套（GlassLiftSpec / CapsuleLift.kt）
    val liftStyle = rememberCapsuleLiftStyle()
    val elevation by animateFloatAsState(
        targetValue = when {
            // 玻璃的投影改由叠层环境阴影承担（M3 投影在毛玻璃上只会是一圈硬边光晕）
            frosted -> 0f
            pressed -> 1f
            else -> 3f
        },
        animationSpec = elevSpec,
        label = "recordCircleElevation",
    )
    // 底色：毛玻璃 = 与底栏同套中性玻璃（surfaceContainer 半透明渐变，**不用主色**）；
    // API<31 回退高不透明；否则实心主色
    val tint = when {
        frosted -> Brush.verticalGradient(
            listOf(
                cs.surfaceContainer.copy(alpha = GlassCardSpec.alphaTop),
                cs.surfaceContainer.copy(alpha = GlassCardSpec.alphaBottom),
            ),
        )
        backdropBlur != null ->
            SolidColor(cs.surfaceContainer.copy(alpha = GlassCardSpec.fallbackAlpha))
        else -> SolidColor(cs.primary)
    }
    val scrimBrush = remember(cs) {
        Brush.verticalGradient(
            listOf(
                Color.Transparent,
                cs.surfaceContainerHighest.copy(alpha = GlassCardSpec.scrimAlpha),
            ),
        )
    }
    val shape = CircleShape
    Surface(
        shape = shape,
        color = Color.Transparent,
        shadowElevation = elevation.dp,
        modifier = Modifier
            .size(BottomBarSpec.sideActionSize)
            .graphicsLayer {
                val e = entrance.value
                alpha = e
                val scale = 0.8f + 0.2f * e
                scaleX = scale
                scaleY = scale
                translationY = (1f - e) * 24.dp.toPx()
            }
            .then(if (onRecord != null) Modifier.pressScale(interaction, pressedScale = 0.9f) else Modifier)
            // 叠层环境阴影（`capsuleAmbientShadow`）**故意不挂**：阴影由底栏胶囊一次画到圆钮右缘，
            // 两边各画一遍会在间距处叠成近双倍暗（用户 2026-10 报「观感较重」，见 docs/11 098）。
            // 亮线/内晕仍归本组件（挂链尾，画在内容之后）。
            .clip(shape)
            .then(
                if (onRecord == null) Modifier
                else Modifier.clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                ) { onRecord?.invoke() },
            )
            .then(if (frosted) Modifier.capsuleEdgeHighlight(liftStyle) else Modifier),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // 毛玻璃采样层（最底）：只画「身后内容」窗口并整层模糊（随父级 clip 裁进圆钮）
            if (frosted) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .backdropBlur(backdropBlur, GlassCardSpec.blurRadius, backdrop = scrimBrush),
                )
            }
            // 主色底：叠在模糊之上、图标之下
            Box(Modifier.fillMaxSize().background(tint))
            // 亮线/内晕不在这里画：由链尾的 `capsuleEdgeHighlight` 沿整圈外缘描（GlassLiftSpec）
            Icon(
                painterResource(R.drawable.ic_ms_more_time), null,
                tint = if (frosted) cs.onSurface else cs.onPrimary,
            )
        }
    }
}
