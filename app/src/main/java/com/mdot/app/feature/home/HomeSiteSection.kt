package com.mdot.app.feature.home

import com.mdot.app.core.designsystem.engineShape
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.SiteMoneyColors
import com.mdot.app.feature.stats.pieColor
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.AnimatedMoneyText
import com.mdot.app.core.designsystem.component.AnimatedNumberText
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.domain.util.TimeUtils
import com.mdot.app.core.designsystem.engineIsMiuix
import com.mdot.app.core.designsystem.emphasisCardInk

/** 首页 SITE 数据区（12 文档 F-S6；v0.7.7 起大数字口径=本期待结，21 文档 B1）：本期待结工数大数字 + 加班副行 + 项目名 */
@Composable
internal fun SiteDataContent(state: HomeUiState, site: SiteHomeUi, onOpenRecord: () -> Unit) {
    Column {
        Text(
            if (state.salary.siteDisplayUnit == "HOUR") stringResource(R.string.site_home_works_label_hour)
            else stringResource(R.string.site_home_works_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val hourMode = state.salary.siteDisplayUnit == "HOUR"
        // 数值变化时滚动滑入（与工地记工工钱同款滑动动画）
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedNumberText(
                value = site.totalWorksMilli,
                text = { milli ->
                    if (hourMode) String.format(java.util.Locale.US, "%.1f", milli / 1000.0 * (site.siteBaseMinutes / 60.0))
                    else String.format(java.util.Locale.US, "%.1f", milli / 1000.0)
                },
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                label = "siteBigNumber",
            )
            Text(
                if (hourMode) stringResource(R.string.detail_worked_hours_unit)
                else stringResource(R.string.stats_works_unit),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.s, start = Spacing.xs),
            )
        }
        if (site.otMinutes > 0 || site.projectName.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(
                    listOfNotNull(
                        if (site.otMinutes > 0) stringResource(R.string.home_overtime_hours, TimeUtils.hoursDecimal(site.otMinutes)) else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (site.projectName.isNotEmpty()) {
                    // 项目色点：与统计饼图同一稳定色（projectId 索引）
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(pieColor((site.projectId % 7).toInt()), androidx.compose.foundation.shape.CircleShape),
                    )
                    Text(
                        site.projectName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // 今日胶囊两态（口径随显示单位偏好）
        if (site.todayWorksMilli > 0 || site.todayOtMinutes > 0) {
            TodayPill(
                text = if (hourMode)
                    stringResource(R.string.site_home_today_hours, TimeUtils.hoursDecimal(((site.todayWorksMilli * site.siteBaseMinutes / 1000L) + site.todayOtMinutes * 60L).toInt()))
                else
                    stringResource(R.string.site_home_today_works, site.todayWorksMilli / 1000L),
                filled = true,
                onRecord = onOpenRecord,
            )
        } else {
            TodayPill(text = stringResource(R.string.home_today_empty), filled = false, onRecord = onOpenRecord)
        }
    }
}

/** 首页 SITE 待结卡：应得 / 已借支 / 待结 + 待结区间行 + 「明细 ›」入口（primaryContainer hero；整卡与小字均进明细页）。
 *  数字口径 =「本期待结」（上次结算次日 → 今天，21 文档 B1），与结算页摘要同源逐分一致。 */
@Composable
internal fun SitePendingCard(site: SiteHomeUi, onOpenDetail: () -> Unit) {
    SectionCard(
        onClick = onOpenDetail,
        // 首页收色（2026-09-30 用户口径）：MIUIX 卡底走中性（强调整色只留给数字/链接）；MD3 保持原强调色底
        containerColor = if (engineIsMiuix) MaterialTheme.colorScheme.surfaceContainer
        else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // 待结区间（M/d – M/d）：口径可见，与结算页「本期待结摘要」对账用（21 文档 B1）
                if (site.rangeFrom != null && site.rangeTo != null) {
                    Text(
                        stringResource(
                            R.string.site_pending_range,
                            "${TimeUtils.mdCn(site.rangeFrom)} – ${TimeUtils.mdCn(site.rangeTo)}",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = emphasisCardInk().copy(alpha = 0.7f),
                    )
                }
                Text(
                    stringResource(R.string.site_settlement_receivable),
                    style = MaterialTheme.typography.labelSmall,
                    color = emphasisCardInk().copy(alpha = 0.7f),
                )
                AnimatedMoneyText(
                    site.workPayCents,
                    style = MaterialTheme.typography.titleMedium,
                    color = emphasisCardInk(),
                    label = "siteReceivable",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.site_stat_advance),
                            style = MaterialTheme.typography.labelSmall,
                            color = SiteMoneyColors.ReceivedGreen,
                        )
                        Spacer(Modifier.width(4.dp))
                        AnimatedMoneyText(
                            site.advanceCents,
                            style = MaterialTheme.typography.labelSmall,
                            color = SiteMoneyColors.ReceivedGreen,
                            label = "siteAdvance",
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.site_stat_pending),
                            style = MaterialTheme.typography.labelSmall,
                            color = SiteMoneyColors.PendingOrange,
                        )
                        Spacer(Modifier.width(4.dp))
                        AnimatedMoneyText(
                            site.pendingCents,
                            style = MaterialTheme.typography.labelSmall,
                            color = SiteMoneyColors.PendingOrange,
                            label = "sitePending",
                        )
                    }
                }
            }
            val interaction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .pressScale(interaction, pressedScale = 0.88f)
                    .clip(engineShape(Radius.button))
                    .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onOpenDetail)
                    .padding(horizontal = Spacing.s, vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.home_income_detail), style = MaterialTheme.typography.labelMedium, color = emphasisCardInk())
                Text("›", style = MaterialTheme.typography.titleMedium, color = emphasisCardInk())
            }
        }
    }
}
