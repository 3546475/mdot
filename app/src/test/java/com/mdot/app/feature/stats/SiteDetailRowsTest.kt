package com.mdot.app.feature.stats

import com.mdot.app.core.database.SiteSettlementEntity
import com.mdot.app.domain.model.SiteAdvance
import com.mdot.app.domain.model.SiteAttendance
import com.mdot.app.domain.model.SitePieceWork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 21 文档 B5：工地流水行共用构造（三处调用点同口径的守门测试） */
class SiteDetailRowsTest {

    private fun att(id: Long, date: String, rest: Boolean = false) = SiteAttendance(
        id = id, projectId = 1, date = date,
        dayStatus = if (rest) "REST" else "WORK", halfOfDay = null,
        workMinutes = if (rest) 0 else 480, otMinutes = 0,
        rateCents = 26_000, baseMinutes = 480,
        otMode = "BY_DAY", otBaseMinutes = 360, otHourlyCents = 0,
        workPayCents = if (rest) 0 else 26_000, otPayCents = 0,
        note = null, photos = null, settlementId = null,
        createdAt = 0, updatedAt = 0,
    )

    @Test
    fun `四类行合并按日期倒序`() {
        val rows = buildSiteDetailRows(
            attendance = listOf(att(1, "2026-09-25")),
            pieceWorks = listOf(
                SitePieceWork(
                    id = 2, projectId = 1, date = "2026-09-26", itemName = "地砖",
                    unit = "平方米", quantityMilli = 0, unitPriceCents = 0, amountCents = 45_000,
                    createdAt = 0, updatedAt = 0,
                ),
            ),
            advances = listOf(
                SiteAdvance(
                    id = 3, projectId = 1, date = "2026-09-27", amountCents = 20_000,
                    purpose = "LIVING", createdAt = 0, updatedAt = 0,
                ),
            ),
            partials = listOf(
                SiteSettlementEntity(
                    id = 4, projectId = 1,
                    periodStart = LocalDate.of(2026, 9, 28), periodEnd = LocalDate.of(2026, 9, 28),
                    workPayCents = 0, piecePayCents = 0, advanceTotalCents = 0,
                    netCents = 10_000, attCount = 0, advanceCount = 0, snapshotJson = "{}",
                    isPartial = true, createdAt = 0,
                ),
            ),
        )
        assertEquals(listOf("2026-09-28", "2026-09-27", "2026-09-26", "2026-09-25"), rows.map { it.date.toString() })
        assertEquals(SiteDetailKind.PARTIAL, rows[0].kind)
        assertEquals(SiteDetailKind.ADVANCE, rows[1].kind)
        assertEquals(SiteDetailKind.PIECE, rows[2].kind)
        assertEquals(SiteDetailKind.WORK, rows[3].kind)
    }

    @Test
    fun `休息日行判 REST 且金额为 0`() {
        val rows = buildSiteDetailRows(
            attendance = listOf(att(1, "2026-09-25", rest = true)),
            pieceWorks = emptyList(),
            advances = emptyList(),
        )
        assertEquals(SiteDetailKind.REST, rows.single().kind)
        assertEquals(0L, rows.single().amountCents)
    }

    @Test
    fun `stableKey 带 kind 前缀跨表不撞`() {
        val rows = buildSiteDetailRows(
            attendance = listOf(att(1, "2026-09-25")),
            pieceWorks = emptyList(),
            advances = listOf(
                SiteAdvance(
                    id = 1, projectId = 1, date = "2026-09-25", amountCents = 100,
                    purpose = "OTHER", createdAt = 0, updatedAt = 0,
                ),
            ),
        )
        assertEquals(2, rows.map { it.stableKey }.distinct().size)
        assertTrue(rows.map { it.stableKey }.containsAll(listOf("WORK_1", "ADVANCE_1")))
    }
}
