package com.mdot.app.core.designsystem

import androidx.compose.runtime.mutableStateOf
import com.mdot.app.domain.model.ThemeEngine

/**
 * 当前主题引擎（MD3 / MIUIX）的**唯一**运行期来源。
 *
 * ⚠️ 必须是**快照状态**（`mutableStateOf`）而不是普通 val：读点散在各组件的组合体内，
 * 普通 val 在引擎切换后不触发重组、圆角会残留到下次无关重组才变。
 * 写入由 `JiabanTheme` 组装内容前调 `Radius.applyEngine`（同值写入不触发重组，安全）；
 * 单测也经它切/复位。
 *
 * 形状与图标分发读 `Radius.engine` / `EngineStyle`；令牌值本身仍留在 `Tokens.kt` 的 `Radius`。
 */
object ThemeEngineState {
    private val state = mutableStateOf(ThemeEngine.MD3)

    val engine: ThemeEngine get() = state.value

    fun set(engine: ThemeEngine) {
        state.value = engine
    }
}
