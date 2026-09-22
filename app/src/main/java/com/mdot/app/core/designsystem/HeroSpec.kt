package com.mdot.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp

/**
 * **hero 卡规范**（v0.7.4 起，所有 hero 卡共用；完整规则见 docs/03 §3.5）。
 *
 * hero 卡 = 页面「一眼要看到的那个数」，统一形态为：
 * `SectionCard(containerColor = primaryContainer)` +
 * `labelSmall` 说明行（`onPrimaryContainer` α0.8） +
 * **金额**（[heroAmountStyle] 两档之一，`onPrimaryContainer` + Bold；¥ 与数字同一文本，或可编辑时 ¥ 作前缀） +
 * `labelMedium` 副行（同样 α0.8）。
 *
 * 为什么有「两档字号」：形态可变的 hero（如记月汇总卡 完整 ⇄ 精简）需要在两档之间**连续插值**，
 * 才有「数字整体缩小」而不是「换了一个数」的观感；单形态的 hero 用 [HeroAmountTier.Standard] 一档即可。
 */
object HeroSpec {
    /**
     * 标记瓦片尺寸：**26dp 圆 + 字形**——与记月页分组卡（`PayGroupCard` 的「基本项目」等）**完全同款**
     * （用户 2026-09-23 定：就照那一个做）。内容 = `IconSpec.inline`（图标型 hero 用）。
     */
    val tileBox = 26.dp
    val tileIcon = IconSpec.inline
}

/** hero 金额字号档：标准档 = 常规 hero；大档 = 形态可变的 hero 展开时（两者之间可插值） */
enum class HeroAmountTier { Standard, Large }

/** 取某一档 hero 金额字号（**别在调用点直接写 displaySmall/headlineMedium**，档位统一在这里） */
@Composable
fun heroAmountStyle(tier: HeroAmountTier): TextStyle = when (tier) {
    HeroAmountTier.Standard -> MaterialTheme.typography.headlineMedium
    HeroAmountTier.Large -> MaterialTheme.typography.displaySmall
}

/** 两档 hero 字号之间按分数插值（只插字号/行高）——形态形变时用 */
@Composable
fun heroAmountStyle(
    from: HeroAmountTier,
    to: HeroAmountTier,
    fraction: Float,
): TextStyle {
    val a = heroAmountStyle(from)
    val b = heroAmountStyle(to)
    val size = a.fontSize.value + (b.fontSize.value - a.fontSize.value) * fraction
    val line = a.lineHeight.value + (b.lineHeight.value - a.lineHeight.value) * fraction
    return a.copy(
        fontSize = androidx.compose.ui.unit.TextUnit(size, a.fontSize.type),
        lineHeight = androidx.compose.ui.unit.TextUnit(line, a.lineHeight.type),
    )
}

/**
 * hero 卡左上角的**标记瓦片**：**26dp 圆 + `primary` 实底 + `onPrimary` 字形**，
 * 与记月页分组卡（`PayGroupCard` 的「基本项目 / 补贴项目 …」）**同一个做法**——用户指定照它做。
 *
 * 沿革（都是真机取色后改的，别退回去）：
 * - `secondaryContainer` 底 ✗：hero 卡底就是 `primaryContainer`，浅色下两者几乎同色
 *   （实测 `(214,228,247)` vs `(208,228,255)`），圆环看不见；
 * - `surface` 底 ✗：虽然对比够（`(248,249,255)`）但白瓦片在浅蓝卡上显得飘、就是「丑」；
 * - **`primary` 实底 ✓**：深色圆 + 白字，与分组卡一样重而稳（也是本项目最早那版的样子）。
 *
 * **可选，不是 hero 的通用要求**（2026-09-23 定）：曾把「每张 hero 都要有」写进规范并铺到四处，
 * 用户判定「不好看」→ **只保留两处**：记月汇总卡的这枚（26dp 圆 + `primary` 实底 + `onPrimary` 字形，
 * 与分组卡同款）+ 工地 hero 自带的 36dp 方形瓦片（`PayHeroCard` 里的 surface 盒 + `ic_ms_paid`）。
 * **首页 / 明细页 / 个税页的 hero 不要瓦片。**
 *
 * @param glyph 文字标记（金额类 hero 用「¥」）
 * @param iconRes 图标标记（非金额类 hero，如工时用 `ic_ms_more_time`）；与 [glyph] 二选一
 */
@Composable
fun HeroTile(
    glyph: String? = null,
    iconRes: Int? = null,
) {
    Box(
        Modifier
            .size(HeroSpec.tileBox)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        when {
            glyph != null -> Text(
                glyph,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )

            iconRes != null -> Icon(
                painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(HeroSpec.tileIcon),
            )
        }
    }
}
