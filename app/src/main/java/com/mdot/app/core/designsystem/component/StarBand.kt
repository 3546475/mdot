package com.mdot.app.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Random

/**
 * 星光带：横向缓慢滚动的星点装饰（玻璃卡主题语言的组成部分，与 [GlassCard] 搭配使用）。
 *
 * 用 `drawBehind` 直绘星点 + 正弦闪烁，不引入大量 composable 节点；星点故意不做成
 * 逐个动画——闪烁在绘制期按相位算即可，既省开销也避免几十个动画同时跑。
 * **固定随机种子**：每次进入形态一致，避免重组时星点跳动。
 *
 * @param shift 相位（0→1 循环；周期建议用 [GLASS_AMBIENT_PERIOD_MS]，与彩虹跑马灯速度一致）
 */
@Composable
fun StarBand(
    shift: Float,
    tint: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val stars = remember {
        val rnd = Random(STAR_SEED)
        List(STAR_COUNT) {
            StarSpec(
                y = rnd.nextFloat(),
                size = 1.3f + rnd.nextFloat() * 2.0f,
                phase = rnd.nextFloat(),
                accent = rnd.nextFloat() > 0.7f,
            )
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .drawBehind {
                val w = size.width
                val h = size.height
                stars.forEach { s ->
                    // 横向循环位移：出右边界后从左重新进入
                    val x = ((s.phase + shift) % 1f) * w
                    val cy = s.y * h
                    // 正弦闪烁：让星点有呼吸感
                    val twinkle = 0.45f + 0.55f * kotlin.math.abs(
                        kotlin.math.sin((s.phase + shift) * 6.28318f + s.phase * 3f),
                    )
                    val color = (if (s.accent) accent else tint).copy(alpha = 0.5f * twinkle)
                    drawCircle(color = color, radius = s.size, center = Offset(x, cy))
                    // 少数亮点加十字光芒（四角星效果）
                    if (s.accent && s.size > 2.6f) {
                        val r = s.size * 2.2f
                        drawLine(color, Offset(x - r, cy), Offset(x + r, cy), strokeWidth = 0.8f)
                        drawLine(color, Offset(x, cy - r), Offset(x, cy + r), strokeWidth = 0.8f)
                    }
                }
            },
    )
}

private data class StarSpec(
    val y: Float,
    val size: Float,
    val phase: Float,
    val accent: Boolean,
)

private const val STAR_COUNT = 14
private const val STAR_SEED = 20260915L
