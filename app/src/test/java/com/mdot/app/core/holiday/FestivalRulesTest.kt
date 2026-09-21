package com.mdot.app.core.holiday

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 节日规则匹配（调研文档 §4.1 四类确定方式）。
 * 农历/节气两类靠 [LunarEngine]，此处用桩引擎验证接线——接入真实算法库时这套匹配无需改动。
 */
class FestivalRulesTest {

    /** 桩引擎：只认「2026-08-15 是农历八月十五、2026-02-16 是腊月最后一天、2026-10-08 是寒露」 */
    private class FakeLunar : LunarEngine() {
        override fun lunarDayText(date: LocalDate): String? =
            if (date == LocalDate.parse("2026-08-15")) "八月十五" else null

        override fun solarTermOf(date: LocalDate): String? =
            if (date == LocalDate.parse("2026-10-08")) "寒露" else null

        override fun lunarMonthDayOf(date: LocalDate): LunarMonthDay? =
            when (date) {
                LocalDate.parse("2026-08-15") -> LunarMonthDay(8, 15)
                LocalDate.parse("2026-02-16") -> LunarMonthDay(12, 29, isLastOfMonth = true)
                else -> null
            }
    }

    private val stub = FakeLunar()

    private val rules = listOf(
        FestivalRule(id = "new_year", name = "元旦", kind = "solar", month = 1, day = 1, priority = 10),
        FestivalRule(id = "mid_autumn", name = "中秋节", kind = "lunar", lunarMonth = 8, lunarDay = 15, priority = 10),
        FestivalRule(id = "chuxi", name = "除夕", kind = "lunar", lunarMonth = 12, lunarDay = LUNAR_LAST_DAY, priority = 10),
        FestivalRule(id = "hanlu", name = "寒露", kind = "term", term = "寒露", priority = 20),
        FestivalRule(id = "mother", name = "母亲节", kind = "weekday", month = 5, ordinal = 2, weekday = 7, priority = 40),
        FestivalRule(id = "teachers", name = "教师节", kind = "solar", month = 9, day = 10, priority = 30),
        // 故意和中秋撞在同一天、优先级更低：验证同日取 priority 最小者
        FestivalRule(id = "dummy", name = "陪跑节", kind = "solar", month = 8, day = 15, priority = 30),
        FestivalRule(id = "weird", name = "未知型", kind = "solarx", month = 1, day = 1, priority = 1),
    )

    @Test
    fun `公历节日按固定月日命中`() {
        assertEquals("元旦", rules.topFestivalOn(LocalDate.parse("2026-01-01"), stub)?.name)
        assertNull(rules.topFestivalOn(LocalDate.parse("2026-01-02"), stub))
    }

    @Test
    fun `农历节日接入引擎后按农历月日命中且同一天取优先级最高的`() {
        assertEquals("中秋节", rules.topFestivalOn(LocalDate.parse("2026-08-15"), stub)?.name)
        assertEquals(
            listOf("中秋节", "陪跑节"),
            rules.festivalsOn(LocalDate.parse("2026-08-15"), stub).map { it.name },
        )
    }

    @Test
    fun `真实农历引擎接入后农历节日端到端命中`() {
        // 桩只证明接线，这里换真实引擎跑一遍（农历已落地，不再是预留接口）
        val real = LunarEngine()
        assertEquals("中秋节", rules.topFestivalOn(LocalDate.parse("2026-09-25"), real)?.name)
        assertEquals("除夕", rules.topFestivalOn(LocalDate.parse("2026-02-16"), real)?.name)
    }

    @Test
    fun `农历查不到的日期不命中而不是抛错`() {
        // 超出 1900–2100 表范围时引擎返回 null，规则表应安静地不命中
        assertNull(rules.topFestivalOn(LocalDate.parse("2101-09-25"), LunarEngine()))
    }

    @Test
    fun `lunarDay_负一表示该农历月最后一天`() {
        // 连续 5 年腊月为小月：除夕是腊月廿九而不是三十（调研文档 §3.4 坑三）
        assertEquals("除夕", rules.topFestivalOn(LocalDate.parse("2026-02-16"), stub)?.name)
    }

    @Test
    fun `节气按节气当日命中`() {
        assertEquals("寒露", rules.topFestivalOn(LocalDate.parse("2026-10-08"), stub)?.name)
        assertNull(rules.topFestivalOn(LocalDate.parse("2026-10-09"), stub))
    }

    @Test
    fun `浮动公历按某月第N个星期几命中`() {
        // 2026-05-10 是 5 月第二个周日；5-03 是第一个、5-17 是第三个
        assertEquals("母亲节", rules.topFestivalOn(LocalDate.parse("2026-05-10"), stub)?.name)
        assertNull(rules.topFestivalOn(LocalDate.parse("2026-05-03"), stub))
        assertNull(rules.topFestivalOn(LocalDate.parse("2026-05-17"), stub))
    }

    @Test
    fun `未知kind视为不命中而不是让整张表失效`() {
        assertFalse(rules.first { it.id == "weird" }.matches(LocalDate.parse("2026-01-01"), stub))
        assertEquals("元旦", rules.topFestivalOn(LocalDate.parse("2026-01-01"), stub)?.name)
    }
}
