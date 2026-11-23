package com.mdot.app.feature.stats

import com.mdot.app.core.designsystem.EngineIcons
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.AnimatedNumberText
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.PayGroup
import com.mdot.app.domain.PayMonthDayBasis
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthSource
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import java.time.LocalDate

@Composable
internal fun PayGroupCard(
    group: PayGroup,
    items: List<PayMonthItem>,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onEdit: (PayMonthItem) -> Unit,
    onAdd: (() -> Unit)? = null,
    extraAction: (@Composable () -> Unit)? = null,
) {
    val expanded = !collapsed
    val total = items.sumOf { it.amountCents }
    val negative = group == PayGroup.DEDUCTION || group == PayGroup.OTHER
    val container = when (group) {
        PayGroup.BASIC -> MaterialTheme.colorScheme.primary
        PayGroup.SUBSIDY -> MaterialTheme.colorScheme.tertiary
        PayGroup.DEDUCTION -> MaterialTheme.colorScheme.error
        PayGroup.OTHER -> MaterialTheme.colorScheme.secondary
    }
    val onContainer = when (group) {
        PayGroup.BASIC -> MaterialTheme.colorScheme.onPrimary
        PayGroup.SUBSIDY -> MaterialTheme.colorScheme.onTertiary
        PayGroup.DEDUCTION -> MaterialTheme.colorScheme.onError
        PayGroup.OTHER -> MaterialTheme.colorScheme.onSecondary
    }
    val labelRes = when (group) {
        PayGroup.BASIC -> R.string.paymonth_group_basic
        PayGroup.SUBSIDY -> R.string.paymonth_group_subsidy
        PayGroup.DEDUCTION -> R.string.paymonth_group_deduction
        PayGroup.OTHER -> R.string.paymonth_group_other
    }
    val glyph = when (group) {
        PayGroup.BASIC -> "¥"
        PayGroup.SUBSIDY -> "+"
        else -> "−"
    }
    val addRes = when (group) {
        PayGroup.BASIC -> R.string.paymonth_add_basic
        PayGroup.SUBSIDY -> R.string.paymonth_add_subsidy
        PayGroup.DEDUCTION -> R.string.paymonth_add_deduction
        PayGroup.OTHER -> R.string.paymonth_add_other
    }

    SectionCard {
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec()),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleCollapse)
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(container),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(glyph, style = MaterialTheme.typography.labelLarge, color = onContainer, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(Spacing.s))
                Text(
                    stringResource(labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                AnimatedNumberText(
                    value = total,
                    // ⚠️ 0 不带负号：扣款/其他组默认显示 "-0"（2026-10-09 记月页巡查发现），
                    // 零就是零。与 [SumCell] 同一口径（那里是 `negative && !zero`）
                    text = { cents ->
                        if (negative && cents != 0L) "-${Money.yuanTrimText(cents)}" else Money.yuanTrimText(cents)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    label = "payGroupTotal",
                )
                // 尾部折叠箭头与行尾 chevron 同尺寸(IconSpec.inline=16)、同间隙(Spacing.xs=4)，
                // 使本卡小计数字的右缘与下方各行金额右缘对齐（跨行同列；原为默认 24dp + s=8，右缘内缩 12dp）
                Spacer(Modifier.width(Spacing.xs))
                Icon(
                    painterResource(if (expanded) R.drawable.ic_ms_expand_less else R.drawable.ic_ms_expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSpec.inline),
                )
            }
            if (expanded) {
                items.forEachIndexed { index, item ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(item) }
                            .padding(vertical = Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                // ⚠️ 0 不带负号（与分组小计、[SumCell] 同一口径）：否则空行显示 "-0"
                                if (negative && item.amountCents != 0L) "-${Money.yuanTrimText(item.amountCents)}"
                                else Money.yuanTrimText(item.amountCents),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                            )
                            // 金额来源（docs/20 P0-3）：引擎算的与手填的分得出来；
                            // 有推导依据的（如「调休折现」）额外附上依据，自动值可解释
                            item.source?.let { src ->
                                Spacer(Modifier.height(Spacing.xs))
                                val derivation = when {
                                    // 夜班联动行先说清楚口径，否则「3 天 × ¥50」看不出只数了夜班
                                    item.nightShiftLinked == true &&
                                        item.derivationDays != null && item.derivationUnitCents != null -> stringResource(
                                        R.string.paymonth_daily_derivation_night,
                                        Money.dayCountText(item.derivationDays),
                                        Money.yuanTrimText(item.derivationUnitCents),
                                    )
                                    // 按日计算：最具体的依据，优先展示（「21.5 天 × ¥50」）
                                    item.derivationDays != null && item.derivationUnitCents != null -> stringResource(
                                        R.string.paymonth_daily_derivation,
                                        // 天数去掉无意义的小数尾巴：整数显示 21，半点显示 21.5
                                        Money.dayCountText(item.derivationDays),
                                        Money.yuanTrimText(item.derivationUnitCents),
                                    )
                                    item.derivationMinutes != null -> stringResource(
                                        R.string.paymonth_derivation_comp,
                                        TimeUtils.prettyDuration(item.derivationMinutes),
                                    )
                                    item.derivationBaseCents != null && item.derivationRateBp != null -> stringResource(
                                        R.string.paymonth_derivation_rate,
                                        Money.yuanTrimText(item.derivationBaseCents),
                                        Money.ratePercentText(item.derivationRateBp),
                                    )
                                    else -> null
                                }
                                Text(
                                    when (src) {
                                        // 实时预览下引擎值随考勤实时算（不存在「哪天同步的」），口径是「自动计算」
                                        PayMonthSource.SYNCED -> if (derivation == null) {
                                            stringResource(R.string.paymonth_source_synced)
                                        } else {
                                            stringResource(R.string.paymonth_source_synced_derived, derivation)
                                        }
                                        PayMonthSource.EDITED -> stringResource(
                                            R.string.paymonth_source_edited,
                                            Money.yuanTrimText(item.engineCents ?: 0L),
                                        )
                                        PayMonthSource.ESTIMATED -> stringResource(
                                            R.string.paymonth_source_estimated,
                                            syncedDayLabel(item.syncedAt),
                                        )
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                        Spacer(Modifier.width(Spacing.xs))
                        Icon(
                            EngineIcons.chevron(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(IconSpec.inline),
                        )
                    }
                }
                onAdd?.let {
                    TextButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(addRes))
                    }
                }
                extraAction?.invoke()
            }
        }
    }
}

/** "2026-09-22" → "9/22"；解析失败或缺失则返回空串（旧数据/异常值不崩） */
internal fun syncedDayLabel(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val d = java.time.LocalDate.parse(iso)
        "${d.monthValue}/${d.dayOfMonth}"
    }.getOrDefault(iso)
}

/** 编辑条目：出厂行只可改金额，新增行可改名称/金额/删除。
 *
 * 注意：**不提供「天数 × 日薪」这类手算入口**——能自动算的（如「调休折现」）由引擎口径自动出数
 * （[PayrollCalculator.compCashCents]，实时预览），用户只在结果不对时直接改金额（docs/20 设计原则）。
 */
/** 社保个人比例预设（基点）：不算 / 仅养老 8% / 北京广州等 10.2% / 多数城市 10.5% */
internal val SOCIAL_RATE_PRESETS = listOf(0, 800, 1020, 1050)

/** 公积金个人比例预设（基点）：法定区间 5%–12%，取常见档 */
internal val FUND_RATE_PRESETS = listOf(0, 500, 700, 800, 1000, 1200)

/** 「按日计算」天数口径的展示顺序（应出勤 → 记录 → 自然日，从最推荐到最粗） */
internal val DAY_BASIS_ORDER = listOf(
    PayMonthDayBasis.STANDARD,
    PayMonthDayBasis.RECORD,
    PayMonthDayBasis.CALENDAR,
)

/** 天数口径的文案（选项名 + 一句说明它怎么算、坑在哪） */
internal val PayMonthDayBasis.labelRes: Int
    get() = when (this) {
        PayMonthDayBasis.STANDARD -> R.string.paymonth_daily_basis_standard
        PayMonthDayBasis.RECORD -> R.string.paymonth_daily_basis_record
        PayMonthDayBasis.CALENDAR -> R.string.paymonth_daily_basis_calendar
    }

internal val PayMonthDayBasis.hintRes: Int
    get() = when (this) {
        PayMonthDayBasis.STANDARD -> R.string.paymonth_daily_hint_standard
        PayMonthDayBasis.RECORD -> R.string.paymonth_daily_hint_record
        PayMonthDayBasis.CALENDAR -> R.string.paymonth_daily_hint_calendar
    }

/**
 * 社保/公积金行的弹窗附加设置（docs/20：**设置放进它自己那一行的弹窗**——
 * 工资设定页签内容太多，那边不再放这张卡）。比例用预设选项、基数默认跟随底薪。
 */
internal data class InsuranceEditUi(
    val social: Boolean,
    /** 当前比例（基点，0 = 不算） */
    val rateBp: Int,
    /** 当前缴费基数（分）；0 = 跟随底薪 */
    val baseCents: Long,
    /** 底薪（跟随时的基数；未设则为 0） */
    val baseSalaryCents: Long,
)
