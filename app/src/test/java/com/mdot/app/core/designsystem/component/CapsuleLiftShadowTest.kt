package com.mdot.app.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * ══ 底栏「越往外越淡」的阴影曲线守门 ══════════════════════════════
 * 纯 JVM，不依赖设备、不依赖 UI（与 `SpacingContractTest`/`DialogContractTest` 同族）。
 *
 * 被测的是 `CapsuleLift.kt` 里的纯函数 [computeShadowBands]：它把「紧贴形状处峰值 α、
 * 往外按 `(1-d/spread)^curve` 缓出到 0」这条目标曲线，翻译成一层层**已量化到 1/255** 的
 * 黑色圆角矩形（`ShadowBand`）。
 *
 * 为什么必须有这个测试：这套阴影的**唯一**问题是「看起来对不对」，而唯一的杠杆是 α 的量化
 * （8-bit 下每层只能是 1/255 的整数倍）——2026-10 已经因此翻车两次：
 * ① 16 层 × 名义 0.05 ⇒ 每层 0.82/255 → 舍成 1/255 ⇒ 实际 6.09%（比名义重，用户报「太明显」）；
 * ② 改成 8 层 × 名义 0.05 ⇒ 每层 1.63/255 → 舍成 **2/255** ⇒ 仍是 6.09%（「轻一档」白改了一轮）。
 * 这两次都不是算术错，而是**没人把量化后的真实曲线钉住**。本测试就钉这个：
 * 单调外淡、峰值收敛在名义峰值附近、每层都是合法量化值、曲线指数确实控制「贴边程度」。
 */
class CapsuleLiftShadowTest {

    /** 参考量：28dp 扩散在 3x 屏上的像素值（几何计算与密度无关，取整值即可） */
    private val spread = 84f
    private val layers = 14
    private val peak = 0.032f

    /** 距离 d 处的累计不透明度（被所有 `growPx >= d` 的圈叠加） */
    private fun cumulativeAt(bands: List<ShadowBand>, d: Float): Float {
        var a = 0f
        bands.forEach { if (it.growPx >= d) a = 1f - (1f - a) * (1f - it.alpha) }
        return a
    }

    @Test
    fun `每层 α 都是 1-255 的整数倍且大于 0（量化后不再被舍掉）`() {
        val bands = computeShadowBands(spread, layers, peak, 2f)
        assertTrue("阴影层不能为空", bands.isNotEmpty())
        bands.forEach { b ->
            val units = b.alpha * 255f
            assertTrue(
                "每层 α 必须是 1/255 的整数倍（否则渲染期会被舍入，曲线就不是算出来的那条）：${b.alpha}",
                abs(units - Math.round(units)) < 0.001f,
            )
            assertTrue("每层 α 必须 > 0（0 的圈应被跳过而不是画出来）：${b.alpha}", b.alpha > 0f)
        }
    }

    @Test
    fun `由外到内排列：growPx 递减、最外层不超过扩散半径`() {
        val bands = computeShadowBands(spread, layers, peak, 2f)
        assertTrue("最外层不能超出扩散半径", bands.first().growPx <= spread + 0.01f)
        bands.zipWithNext { a, b -> assertTrue("必须由外到内（先铺最外圈）：${a.growPx} → ${b.growPx}", a.growPx > b.growPx) }
        assertTrue("画出的圈数不能超过名义层数", bands.size <= layers)
    }

    @Test
    fun `越往外越淡：累计不透明度随距离单调不增`() {
        val bands = computeShadowBands(spread, layers, peak, 2f)
        var prev = cumulativeAt(bands, 0f)
        var d = 0f
        while (d <= spread) {
            val cur = cumulativeAt(bands, d)
            assertTrue("d=$d 处累计 ${cur} 比更靠内的 $prev 还深（应越往外越淡）", cur <= prev + 1e-4f)
            prev = cur
            d += spread / 16f
        }
        assertTrue("离形状超过扩散半径后必须完全没有阴影", prev == 0f)
    }

    @Test
    fun `贴边处累计收敛到名义峰值附近（±1-5%）`() {
        val bands = computeShadowBands(spread, layers, peak, 2f)
        val edge = cumulativeAt(bands, 0f)
        val tolerance = 1.5f / 255f
        assertTrue(
            "贴边累计 $edge 偏离名义峰值 $peak 超过 ±${tolerance}（量化后应仍在同一档附近）",
            abs(edge - peak) <= tolerance,
        )
    }

    @Test
    fun `曲线指数越大越贴边：同一距离处累计更浅`() {
        val d = spread * 0.6f
        val linear = cumulativeAt(computeShadowBands(spread, layers, peak, 1f), d)
        val quad = cumulativeAt(computeShadowBands(spread, layers, peak, 2f), d)
        val cubic = cumulativeAt(computeShadowBands(spread, layers, peak, 3f), d)
        assertTrue("curve=2 应比 curve=1 更淡：$quad vs $linear", quad <= linear)
        assertTrue("curve=3 应比 curve=2 更淡：$cubic vs $quad", cubic <= quad)
    }

    @Test
    fun `参数退化时不画阴影（夜间 peak=0 走这条）`() {
        assertEquals("peak=0 必须不产生任何层", emptyList<ShadowBand>(), computeShadowBands(spread, layers, 0f, 2f))
        assertEquals("layers=0 必须不产生任何层", emptyList<ShadowBand>(), computeShadowBands(spread, 0, peak, 2f))
        assertEquals("spread=0 必须不产生任何层", emptyList<ShadowBand>(), computeShadowBands(0f, layers, peak, 2f))
    }

    /**
     * 出厂档位自己也要过一遍曲线（用户实际看到的就是这套参数）：越往外越淡 + 贴边到峰值。
     * 与上面几条的区别是“参数取自 `GlassLiftSpec`”，改令牌配错了这里会立即变红。
     */
    @Test
    fun `出厂令牌（GlassLiftSpec）下也是越往外越淡且贴边到峰值`() {
        val density = androidx.compose.ui.unit.Density(3f, 1f)
        val spreadPx = with(density) { com.mdot.app.core.designsystem.GlassLiftSpec.shadowSpread.toPx() }
        val bands = computeShadowBands(
            spreadPx = spreadPx,
            layers = com.mdot.app.core.designsystem.GlassLiftSpec.shadowLayers,
            peakAlpha = com.mdot.app.core.designsystem.GlassLiftSpec.shadowAlphaLight,
            curve = com.mdot.app.core.designsystem.GlassLiftSpec.shadowFalloffCurve,
        )
        assertTrue("出厂档位下应至少画出 3 圈（太少就成硬边了）", bands.size >= 3)
        val edge = cumulativeAt(bands, 0f)
        assertTrue(
            "出厂贴边累计 $edge 偏离峰值 $peak 超过 3/255",
            abs(edge - com.mdot.app.core.designsystem.GlassLiftSpec.shadowAlphaLight) <= 3f / 255f,
        )
        // 越往外越淡：采样整条尾巴
        var prev = edge
        var d = 0f
        while (d <= spreadPx) {
            val cur = cumulativeAt(bands, d)
            assertTrue("出厂档位在 d=$d 处不再外淡（$cur > $prev）", cur <= prev + 1e-4f)
            prev = cur
            d += spreadPx / 14f
        }
        assertTrue("扩散半径外应彻底没阴影", prev == 0f)
    }

    /**
     * 回归锁 2026-10 的两次翻车：**同样的名义峰值，层数与峰值组合不同，真实观感必须一致地
     * 落在「峰值附近」**——而不是像旧制那样悄悄放大（16 层 × 0.05 实际 6.09%）。
     */
    @Test
    fun `名义峰值即实际峰值（不像旧制那样被量化放大到 6%）`() {
        listOf(0.024f, 0.032f, 0.040f).forEach { p ->
            val edge = cumulativeAt(computeShadowBands(spread, layers, p, 2f), 0f)
            assertTrue(
                "名义峰值 $p 的实际贴边累计 $edge 偏离过大 ⇒ 量化又把它放大了",
                abs(edge - p) <= 3f / 255f,
            )
        }
    }
}
