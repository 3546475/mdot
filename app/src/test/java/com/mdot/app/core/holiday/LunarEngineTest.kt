package com.mdot.app.core.holiday

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 农历 / 节气引擎：回归用例全部取自官方口径，不取自算法自身输出。
 *
 * 农历对照点来自国务院办公厅《关于 2025 / 2026 年部分节假日安排的通知》——春节、端午、
 * 中秋三节都锚在固定农历日上，通知里的公历日期即「公历 ↔ 农历」的权威映射；
 * 节气对照点见各用例注释。
 */
class LunarEngineTest {

    private val engine = LunarEngine()

    private fun assertLunar(iso: String, month: Int, day: Int) {
        val got = engine.lunarMonthDayOf(LocalDate.parse(iso))
        assertEquals("$iso 的农历月", month, got?.month)
        assertEquals("$iso 的农历日", day, got?.day)
    }

    // ---- 农历换算 ----

    @Test
    fun `春节端午中秋对照官方放假通知`() {
        // 2025-01-29 春节(正月初一)、2025-05-31 端午(五月初五)、2025-10-06 中秋(八月十五)
        assertLunar("2025-01-29", 1, 1)
        assertLunar("2025-05-31", 5, 5)
        assertLunar("2025-10-06", 8, 15)
        // 2026-02-17 春节、2026-06-19 端午、2026-09-25 中秋
        assertLunar("2026-02-17", 1, 1)
        assertLunar("2026-06-19", 5, 5)
        assertLunar("2026-09-25", 8, 15)
    }

    @Test
    fun `连休中段能取到农历日`() {
        // 右槽「只标首日」规则生效后，中秋假期第二三天靠农历填位
        assertLunar("2026-09-26", 8, 16)
        assertLunar("2026-09-27", 8, 17)
        assertEquals("十六", engine.lunarDayText(LocalDate.parse("2026-09-26")))
        assertEquals("十七", engine.lunarDayText(LocalDate.parse("2026-09-27")))
    }

    @Test
    fun `腊月小月时除夕是廿九并标记为该月最后一天`() {
        // 2026 春节通知写「2月15日(农历腊月二十八)至23日(农历正月初七)」→ 腊月只有 29 天，无大年三十
        val chuxi = engine.lunarMonthDayOf(LocalDate.parse("2026-02-16"))
        assertEquals(12, chuxi?.month)
        assertEquals(29, chuxi?.day)
        assertTrue("除夕靠 isLastOfMonth 判定，不能写成固定的腊月三十", chuxi?.isLastOfMonth == true)

        assertLunar("2026-02-15", 12, 28)
        assertLunar("2026-02-17", 1, 1)
    }

    @Test
    fun `连续腊月小月时除夕落在廿九`() {
        // 2025–2028 这四年腊月均为 29 天（连续无大年三十）：以正月初一为锚反推除夕，
        // 断言它必须是「腊月最后一天」且为廿九——除夕判定走 isLastOfMonth，不能写死腊月三十
        val yearStarts = (0..1500).map { LocalDate.of(2025, 1, 1).plusDays(it.toLong()) }
            .mapNotNull { d -> engine.lunarMonthDayOf(d)?.let { d to it } }
            .filter { (d, md) -> md.month == 1 && md.day == 1 && d.year in 2025..2028 }

        assertEquals("应覆盖 2025–2028 四个正月初一", 4, yearStarts.size)
        yearStarts.forEach { (newYear, _) ->
            val chuxi = engine.lunarMonthDayOf(newYear.minusDays(1))
            assertEquals("$newYear 前一天应落在腊月", 12, chuxi?.month)
            assertTrue("$newYear 前一天应是腊月最后一天", chuxi?.isLastOfMonth == true)
            assertEquals("$newYear 所属腊月是小月，除夕该是廿九", 29, chuxi?.day)
        }
    }

    @Test
    fun `闰月年份的农历换算正确`() {
        // 2033 年闰十一月是百年一遇的罕见置闰，表的高位编码错了这里就崩
        assertEquals(11, ChineseLunar.leapMonth(2033))
        assertEquals(6, ChineseLunar.leapMonth(2025))
        assertEquals(0, ChineseLunar.leapMonth(2026))
        // 2025-07-25 是闰六月初一
        assertLunar("2025-07-25", 6, 1)
        assertLunar("2025-07-24", 6, 30)
    }

    @Test
    fun `农历日名一律两个字`() {
        val d = LocalDate.parse("2026-01-01")
        repeat(400) {
            val text = engine.lunarDayText(d.plusDays(it.toLong()))
            assertEquals("$text 不是两个字", 2, text?.length)
            assertTrue(text!!.startsWith("初") || text.startsWith("十") || text.startsWith("廿") || text == "二十" || text == "三十")
        }
    }

    @Test
    fun `表外年份返回空而不是猜`() {
        assertNull(engine.lunarMonthDayOf(LocalDate.parse("1899-12-31")))
        assertNull(engine.lunarMonthDayOf(LocalDate.parse("2101-01-01")))
        assertNull(engine.lunarDayText(LocalDate.parse("2150-06-01")))
    }

    // ---- 节气 ----

    @Test
    fun `节气对照公开历法`() {
        // 2025 清明 4/4；2026 清明 4/5（国办通知把 2026-04-05 定为清明法定当天，两者互为印证）
        assertEquals("清明", engine.solarTermOf(LocalDate.parse("2025-04-04")))
        assertEquals("清明", engine.solarTermOf(LocalDate.parse("2026-04-05")))
        assertEquals("冬至", engine.solarTermOf(LocalDate.parse("2025-12-21")))
        assertEquals("秋分", engine.solarTermOf(LocalDate.parse("2026-09-23")))
        assertEquals("立春", engine.solarTermOf(LocalDate.parse("2026-02-04")))
        assertEquals("夏至", engine.solarTermOf(LocalDate.parse("2026-06-21")))
    }

    @Test
    fun `每个公历年正好二十四个节气且互不重叠`() {
        (2025..2030).forEach { year ->
            val terms = ChineseLunar.solarTermsOf(year)
            assertEquals("$year 年节气数", 24, terms.size)
            assertTrue("$year 年节气应覆盖 1 月（小寒大寒）", terms.keys.any { it.monthValue == 1 })
            assertTrue("$year 年节气应覆盖 12 月（冬至）", terms.keys.any { it.monthValue == 12 })
        }
    }

    @Test
    fun `非节气日返回空`() {
        assertNull(engine.solarTermOf(LocalDate.parse("2026-09-25")))
    }

    @Test
    fun `同一天重复查询命中缓存且结果一致`() {
        val date = LocalDate.parse("2026-09-25")
        val first = engine.lunarMonthDayOf(date)
        repeat(5) { assertEquals(first, engine.lunarMonthDayOf(date)) }
    }

    @Test
    fun `闰月与同名平月是两个月`() {
        // 2025 年闰六月：七月廿四(7-24)仍是六月三十（六月 30 天），7-25 才进闰六月初一
        val liuYueMo = engine.lunarMonthDayOf(LocalDate.parse("2025-07-24"))
        assertEquals(6, liuYueMo?.month)
        assertEquals(30, liuYueMo?.day)
        assertTrue("六月三十应是六月最后一天", liuYueMo?.isLastOfMonth == true)

        val runLiuYue = engine.lunarMonthDayOf(LocalDate.parse("2025-07-25"))
        assertEquals(6, runLiuYue?.month)
        assertEquals(1, runLiuYue?.day)
        assertFalse("闰六月初一不是月末", runLiuYue?.isLastOfMonth == true)
    }
}
