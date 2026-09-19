package com.mdot.app.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

/** 预测性返回手势映射（进度 → 横向位移，UIfix 预测性返回；刻意不做缩放） */
class PredictiveBackSpecTest {

    @Test
    fun `进度 0 时无位移`() {
        assertEquals(0f, PredictiveBackSpec.translationFor(0f).value, 0.0001f)
    }

    @Test
    fun `进度 1 时到最大位移`() {
        assertEquals(36f, PredictiveBackSpec.translationFor(1f).value, 0.0001f)
        assertEquals(36f, PredictiveBackSpec.maxTranslationX.value, 0.0001f)
    }

    @Test
    fun `进度 0_5 线性映射`() {
        assertEquals(18f, PredictiveBackSpec.translationFor(0.5f).value, 0.0001f)
        assertEquals(9f, PredictiveBackSpec.translationFor(0.25f).value, 0.0001f)
    }

    @Test
    fun `越界进度被钳制`() {
        assertEquals(36f, PredictiveBackSpec.translationFor(1.4f).value, 0.0001f)
        assertEquals(0f, PredictiveBackSpec.translationFor(-0.3f).value, 0.0001f)
        assertEquals(1f, PredictiveBackSpec.clampProgress(3f), 0.0001f)
        assertEquals(0f, PredictiveBackSpec.clampProgress(-3f), 0.0001f)
    }

    @Test
    fun `位移单调递增且不超过上限`() {
        var prev = -1f
        for (i in 0..10) {
            val x = PredictiveBackSpec.translationFor(i / 10f).value
            assert(x >= prev) { "位移应单调不减：i=$i x=$x prev=$prev" }
            assert(x <= 36f + 0.0001f) { "位移不得超过 36dp：x=$x" }
            prev = x
        }
    }
}
