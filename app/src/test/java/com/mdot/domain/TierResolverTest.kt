package com.mdot.app.domain

import com.mdot.app.domain.model.HolidayKind
import com.mdot.app.domain.model.RateTier
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class TierResolverTest {

    private val holidays = mapOf(
        LocalDate.parse("2026-10-01") to HolidayKind.HOLIDAY,
        LocalDate.parse("2026-09-27") to HolidayKind.WORKDAY, // 周日补班
    )

    private val standard = TierResolver(
        holidays::get,
        workdays = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        ),
    )

    @Test
    fun `法定节假日为法定档`() {
        assertEquals(RateTier.STATUTORY, standard.tierFor(LocalDate.parse("2026-10-01")))
    }

    @Test
    fun `周末补班为平时档`() {
        assertEquals(RateTier.WEEKDAY, standard.tierFor(LocalDate.parse("2026-09-27")))
    }

    @Test
    fun `普通周六日为周末档`() {
        assertEquals(RateTier.WEEKEND, standard.tierFor(LocalDate.parse("2026-08-01"))) // 周六
        assertEquals(RateTier.WEEKEND, standard.tierFor(LocalDate.parse("2026-08-02"))) // 周日
    }

    @Test
    fun `工作日为平时档`() {
        assertEquals(RateTier.WEEKDAY, standard.tierFor(LocalDate.parse("2026-08-03"))) // 周一
    }

    @Test
    fun `自定义工作日做一休一`() {
        val resolver = TierResolver({ null }, workdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))
        assertEquals(RateTier.WEEKDAY, resolver.tierFor(LocalDate.parse("2026-08-03"))) // 周一
        assertEquals(RateTier.WEEKEND, resolver.tierFor(LocalDate.parse("2026-08-05"))) // 周三（休息日）
    }
}
