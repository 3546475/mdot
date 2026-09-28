package com.mdot.app.feature.stats

import com.mdot.app.core.database.SiteSettlementEntity
import com.mdot.app.domain.model.AdvancePurpose
import com.mdot.app.domain.model.SiteAdvance
import com.mdot.app.domain.model.SiteAttendance
import com.mdot.app.domain.model.SitePieceWork
import java.time.LocalDate

/**
 * 工地流水行构造（21 文档 B5 重构）：明细页 / 统计页 / 记工页保存预览**三处共用**——
 * 同一数据源禁止各写一套 buildList（改造前明细页与统计页各存一份，行口径已漂移：
 * 一处判 REST 一处恒为 WORK）。返回日期倒序（同日多笔保持传入顺序：出工 → 包工 → 借支 → 部分结算）。
 */
fun buildSiteDetailRows(
    attendance: List<SiteAttendance>,
    pieceWorks: List<SitePieceWork>,
    advances: List<SiteAdvance>,
    partials: List<SiteSettlementEntity> = emptyList(),
): List<SiteDetailRow> {
    fun worksMilliOf(minutes: Int, base: Int): Long = minutes * 1000L / base.coerceAtLeast(1)
    return buildList {
        attendance.forEach { a ->
            add(
                SiteDetailRow(
                    date = LocalDate.parse(a.date),
                    kind = if (a.dayStatus == "REST") SiteDetailKind.REST else SiteDetailKind.WORK,
                    id = a.id,
                    worksMilli = worksMilliOf(a.workMinutes, a.baseMinutes),
                    otMinutes = a.otMinutes,
                    amountCents = a.workPayCents + a.otPayCents,
                )
            )
        }
        pieceWorks.forEach { p ->
            add(
                SiteDetailRow(
                    date = LocalDate.parse(p.date),
                    kind = SiteDetailKind.PIECE,
                    id = p.id,
                    amountCents = p.amountCents,
                    itemName = p.itemName,
                )
            )
        }
        advances.forEach { a ->
            add(
                SiteDetailRow(
                    date = LocalDate.parse(a.date),
                    kind = SiteDetailKind.ADVANCE,
                    id = a.id,
                    amountCents = a.amountCents,
                    purpose = runCatching { AdvancePurpose.valueOf(a.purpose) }.getOrNull(),
                )
            )
        }
        partials.forEach { p ->
            add(
                SiteDetailRow(
                    date = p.periodStart,
                    kind = SiteDetailKind.PARTIAL,
                    id = p.id,
                    amountCents = p.netCents,
                )
            )
        }
    }.sortedByDescending { it.date }
}
