package com.mdot.app.domain

import com.mdot.app.domain.model.SiteAdvance
import com.mdot.app.domain.model.SiteAttendance
import com.mdot.app.domain.model.SitePieceWork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工地留证的可见性口径（v0.7.9.0）：照片串解析 + 月历角标计数。
 *
 * 守两件事：
 * 1. 照片串**只有一个解析出处**（`\u0001` 分隔，空串/空元素都不算一张）；
 * 2. 角标计数把"出勤上的照片"和"借支上的照片"都算进去（借支也要留证，别漏）。
 */
class SiteDayBadgesTest {

    private fun att(id: Long, date: String, photos: String? = null) = SiteAttendance(
        id = id, projectId = 1, date = date, dayStatus = "WORK",
        rateCents = 0, baseMinutes = 600, otMode = "NONE", otBaseMinutes = 0, otHourlyCents = 0,
        photos = photos, createdAt = 0, updatedAt = 0,
    )

    private fun advance(id: Long, date: String, photos: String? = null) = SiteAdvance(
        id = id, projectId = 1, date = date, amountCents = 1000, photos = photos,
        createdAt = 0, updatedAt = 0,
    )

    private fun piece(id: Long, date: String) = SitePieceWork(
        id = id, projectId = 1, date = date, amountCents = 500, createdAt = 0, updatedAt = 0,
    )

    @Test
    fun `照片串解析 空值与空元素都不算`() {
        assertTrue(parsePhotoPaths(null).isEmpty())
        assertTrue(parsePhotoPaths("").isEmpty())
        assertTrue(parsePhotoPaths("   ").isEmpty())
        assertTrue("空元素不该算一张", parsePhotoPaths("\u0001\u0001").isEmpty())
        assertEquals(listOf("/a.jpg", "/b.jpg"), parsePhotoPaths("/a.jpg\u0001/b.jpg"))
    }

    @Test
    fun `角标计数 出勤与借支的照片都算`() {
        val badges = SiteDayBadges.of(
            attendances = listOf(att(1, "2026-10-08", "/a.jpg\u0001/b.jpg"), att(2, "2026-10-08", null)),
            advances = listOf(advance(1, "2026-10-08", "/c.jpg")),
        )
        assertEquals(3, badges.photoCount)
        assertEquals(1, badges.advanceCount)
        assertFalse(badges.hasPiece)
    }

    @Test
    fun `角标计数 包工与借支笔数`() {
        val badges = SiteDayBadges.of(
            pieces = listOf(piece(1, "2026-10-08"), piece(2, "2026-10-08")),
            advances = listOf(advance(1, "2026-10-08")),
        )
        assertEquals(2, badges.pieceCount)
        assertEquals(1, badges.advanceCount)
        assertTrue(badges.hasPiece && badges.hasAdvance)
        assertFalse("没有照片就不画照片点", badges.hasPhotos)
    }

    @Test
    fun `什么都没有时为空`() {
        assertTrue(SiteDayBadges().isEmpty)
        assertTrue(SiteDayBadges.of(attendances = listOf(att(1, "2026-10-08"))).isEmpty)
    }
}
