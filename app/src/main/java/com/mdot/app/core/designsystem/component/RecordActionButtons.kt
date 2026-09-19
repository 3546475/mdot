package com.mdot.app.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.mdot.app.core.designsystem.BottomBarSpec

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
    onRecord: () -> Unit,
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
    val shape = RoundedCornerShape(20.dp)
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
            .pressScale(interaction, pressedScale = 0.9f)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onRecord),
    ) {
        Box(
            Modifier.size(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_ms_more_time), null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * 右侧独立圆形「记加班」按钮（v0.6.19 新增底栏布局）：**始终只显示图标、不显示文字**；
 * 出入场/按压与中央胶囊按钮一致。
 *
 * [backdropBlur] 非空时（「按钮右置」+ 外观「毛玻璃」开）圆钮与底栏同一套玻璃质感：
 * 半透明中性底色渐变 + 模糊身后内容 + 上缘内高光 + **去投影**（玻璃不投影）——
 * **不再是主色实心**（既然是玻璃就与该套中性玻璃一致）；API<31 回退高不透明中性底。
 * 结构同底栏：模糊层在最底、底色与图标在兄弟层 → 只糊背景、图标保持锐利。
 */
@Composable
fun RecordCircleButton(
    onRecord: () -> Unit,
    /** 仅首次出现播放弹簧弹入；底栏隐藏→再现（组合销毁重建）时不重播 */
    playEntrance: Boolean,
    onEntranceDone: () -> Unit,
    /** 毛玻璃源（「按钮右置」布局 + 外观开关开时传入；null = 实心主色） */
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
    val frosted = backdropBlur != null &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val elevSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val elevation by animateFloatAsState(
        targetValue = when {
            frosted -> 0f // 玻璃不投影（同底栏毛玻璃：空白背景时阴影光晕观感差）
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
                cs.surfaceContainer.copy(alpha = BottomBarSpec.frostedAlphaTop),
                cs.surfaceContainer.copy(alpha = BottomBarSpec.frostedAlphaBottom),
            ),
        )
        backdropBlur != null ->
            SolidColor(cs.surfaceContainer.copy(alpha = BottomBarSpec.frostedFallbackAlpha))
        else -> SolidColor(cs.primary)
    }
    val highlightBrush = remember {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = BottomBarSpec.frostedHighlightAlpha),
                Color.Transparent,
            ),
        )
    }
    val scrimBrush = remember(cs) {
        Brush.verticalGradient(
            listOf(
                Color.Transparent,
                cs.surfaceContainerHighest.copy(alpha = BottomBarSpec.frostedScrimAlpha),
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
            .pressScale(interaction, pressedScale = 0.9f)
            .clip(shape)
            .then(
                if (frosted) {
                    Modifier.border(
                        BottomBarSpec.barBorderWidth,
                        cs.outlineVariant.copy(alpha = BottomBarSpec.frostedBorderAlpha),
                        shape,
                    )
                } else Modifier
            )
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onRecord),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // 毛玻璃采样层（最底）：只画「身后内容」窗口并整层模糊（随 clip 裁进圆钮）
            if (frosted) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .backdropBlur(backdropBlur!!, BottomBarSpec.frostBlurRadius, backdrop = scrimBrush),
                )
            }
            // 主色底：叠在模糊之上、图标之下
            Box(Modifier.fillMaxSize().background(tint))
            // 内高光（仅毛玻璃）：上缘内侧白色渐变
            if (frosted) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(highlightBrush),
                )
            }
            Icon(
                painterResource(R.drawable.ic_ms_more_time), null,
                tint = if (frosted) cs.onSurface else cs.onPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
