package com.mdot.app.feature.profile

import com.mdot.app.domain.model.SiteAttendance
import com.mdot.app.domain.model.SitePieceWork
import org.junit.Assert.assertEquals
import org.junit.Test

/** 我的页工地记工摘要聚合（computeSiteProfileSummary）用例 */
class ProfileSiteSummaryTest {

    private fun att(date: String, status: String, workPay: Long = 0, otPay: Long = 0) = SiteAttendance(
        projectId = 1,
        date = date,
        dayStatus = status,
        rateCents = 0,
        baseMinutes = 480,
        otMode = "BY_DAY",
        otBaseMinutes = 360,
        otHourlyCents = 0,
        workPayCents = workPay,
        otPayCents = otPay,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun piece(date: String, amountCents: Long) = SitePieceWork(
        projectId = 1,
        date = date,
        amountCents = amountCents,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `工天按WORK状态且日期去重`() {
        val s = computeSiteProfileSummary(
            listOf(
                att("2026-09-01", "WORK"),
                att("2026-09-01", "WORK"), // 同日第二笔（另一项目）不重复计天
                att("2026-09-01", "REST"),
                att("2026-09-02", "WORK"),
                att("2026-09-03", "REST"), // 显式休息不计工天
            ),
            emptyList(),
            completedProjects = 0,
        )
        assertEquals(2, s.yearWorkDays)
    }

    @Test
    fun `工钱为点工加班与包工之和`() {
        val s = computeSiteProfileSummary(
            listOf(
                att("2026-09-01", "WORK", workPay = 38_400, otPay = 9_600),
                att("2026-09-02", "REST"), // 休息日工钱 0
            ),
            listOf(piece("2026-09-02", 12_000), piece("2026-09-03", 5_500)),
            completedProjects = 0,
        )
        assertEquals(65_500, s.yearPayCents)
    }

    @Test
    fun `完工项目数透传且空数据全零`() {
        val empty = computeSiteProfileSummary(emptyList(), emptyList(), completedProjects = 0)
        assertEquals(SiteProfileSummary(), empty)
        val s = computeSiteProfileSummary(emptyList(), emptyList(), completedProjects = 3)
        assertEquals(3, s.completedProjects)
    }
}
