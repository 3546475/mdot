package com.mdot.app.core.holiday

import com.mdot.app.domain.model.HolidayInfo
import com.mdot.app.domain.model.HolidayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 日历格组合层：休/班 + 节日/农历 的取用规则（用户 2026-09-20 规格 + 调研文档 §2.4 坑一） */
class CalendarDayUseCaseTest {

    private val day = LocalDate.parse("2026-09-25")

    private fun info(kind: HolidayKind, name: String, makeupFor: String? = null) =
        HolidayInfo(day, name, kind, makeupFor)

    private fun rule(name: String) = FestivalRule(id = "t", name = name, kind = "solar", month = 9, day = 25)

    private fun prev(kind: HolidayKind, name: String) =
        HolidayInfo(day.minusDays(1), name, kind)

    @Test
    fun `法定节假日显示休与两字节日名`() {
        val label = composeDayCellLabel(info(HolidayKind.STATUTORY, "中秋节"), null, null)
        assertEquals(RestBadge.REST, label.badge)
        assertEquals("中秋", label.text)          // 三个字截成两个
    }

    @Test
    fun `调休休息日同样显示休`() {
        val label = composeDayCellLabel(info(HolidayKind.REST, "国庆节"), null, null)
        assertEquals(RestBadge.REST, label.badge)
        assertEquals("国庆", label.text)
    }

    @Test
    fun `补班日显示班且不把被调节日当节日名显示`() {
        val label = composeDayCellLabel(
            info(HolidayKind.WORKDAY, "调休上班", makeupFor = "春节"),
            null,
            null,
        )
        assertEquals(RestBadge.MAKEUP, label.badge)
        assertNull("补班日右槽不该显示「春节」（调研文档 §2.4 坑一）", label.text)
    }

    @Test
    fun `纪念日只出现在右槽并截两字_不带休假标记`() {
        val label = composeDayCellLabel(null, rule("教师节"), null)
        assertNull("教师节不放假，不能带休标记", label.badge)
        assertEquals("教师", label.text)
    }

    @Test
    fun `有节日时不显示农历`() {
        val label = composeDayCellLabel(info(HolidayKind.STATUTORY, "中秋节"), rule("中秋节"), "十五")
        assertEquals("中秋", label.text)
    }

    @Test
    fun `没有节日时农历补位`() {
        val label = composeDayCellLabel(null, null, "十五")
        assertNull(label.badge)
        assertEquals("十五", label.text)
    }

    @Test
    fun `节假日库缺数据时规则表兜底`() {
        // 远端数据没下发到这天：节日名仍由静态规则表给出（休标记则确实没有）
        val label = composeDayCellLabel(null, rule("元旦"), null)
        assertEquals("元旦", label.text)
        assertNull(label.badge)
    }

    @Test
    fun `一年里的普通日什么都不显示`() {
        val label = composeDayCellLabel(null, null, null)
        assertEquals(DayCellLabel.EMPTY, label)
    }

    @Test
    fun `节日名为空串时不占右槽`() {
        val label = composeDayCellLabel(info(HolidayKind.REST, ""), null, "十五")
        assertEquals("十五", label.text)
    }

    // ---- 外观页「隐藏农历日期」开关（用户 2026-09-20 追加；默认关 = 显示农历） ----
    // 开关开启时 labelFor 直接传 lunarText = null（不查农历引擎），故这里以 null 代指「已隐藏」

    @Test
    fun `隐藏农历后连休中段留白`() {
        val label = composeDayCellLabel(
            info = info(HolidayKind.REST, "中秋节"),
            festivalRule = null,
            lunarText = null,
            prevInfo = prev(HolidayKind.STATUTORY, "中秋节"),
        )
        assertEquals(RestBadge.REST, label.badge)   // 休/班 不受开关影响
        assertNull(label.text)
    }

    @Test
    fun `隐藏农历不影响节日名`() {
        val label = composeDayCellLabel(info(HolidayKind.STATUTORY, "中秋节"), null, null)
        assertEquals(RestBadge.REST, label.badge)
        assertEquals("中秋", label.text)
    }

    // ---- 连休段只标首日（用户 2026-09-20 追加规格） ----

    @Test
    fun `连休中段不再重复标节日名_让位给农历`() {
        // 2026-09-26：前一天 9-25 也是中秋节，属同一连休段
        val label = composeDayCellLabel(
            info = info(HolidayKind.REST, "中秋节"),
            festivalRule = null,
            lunarText = "十六",
            prevInfo = prev(HolidayKind.STATUTORY, "中秋节"),
        )
        assertEquals(RestBadge.REST, label.badge)
        assertEquals("十六", label.text)
    }

    @Test
    fun `连休首日照常标节日名`() {
        val label = composeDayCellLabel(
            info = info(HolidayKind.STATUTORY, "中秋节"),
            festivalRule = null,
            lunarText = "十五",
            prevInfo = prev(HolidayKind.REST, "国庆节"), // 前一天是别的节日 → 今天是本段首日
        )
        assertEquals("中秋", label.text)
    }

    @Test
    fun `前一天是补班日时不并入连休段`() {
        // 2026-02-15 春节首日，前一天 2-14 是春节的补班日：补班日不放假，不构成连休
        val label = composeDayCellLabel(
            info = info(HolidayKind.REST, "春节"),
            festivalRule = null,
            lunarText = "廿八",
            prevInfo = prev(HolidayKind.WORKDAY, "春节"),
        )
        assertEquals("春节", label.text)
    }

    @Test
    fun `普通日不受前一天的节假日影响`() {
        val label = composeDayCellLabel(
            info = null,
            festivalRule = rule("教师节"),
            lunarText = "十九",
            prevInfo = prev(HolidayKind.STATUTORY, "中秋节"),
        )
        assertEquals("教师", label.text)
    }

    @Test
    fun `连休中段没有农历时右槽留空`() {
        val label = composeDayCellLabel(
            info = info(HolidayKind.REST, "国庆节"),
            festivalRule = null,
            lunarText = null,
            prevInfo = prev(HolidayKind.STATUTORY, "国庆节"),
        )
        assertEquals(RestBadge.REST, label.badge)
        assertNull(label.text)
        assertEquals(DayCellLabel(badge = RestBadge.REST), label)
    }

    // ---- 除夕特例（用户 2026-09-20 二轮规格） ----

    @Test
    fun `连休中段命中另一个节日时照标_除夕`() {
        // 2026-02-16：库里给的仍是「春节」（与 2-15 段首重复），但规则表按「腊月最后一天」
        // 命中「除夕」，两者不同名 → 保留除夕，不被连休压缩吃掉
        val label = composeDayCellLabel(
            info = info(HolidayKind.REST, "春节"),
            festivalRule = rule("除夕"),
            lunarText = "廿九",
            prevInfo = prev(HolidayKind.REST, "春节"),
        )
        assertEquals(RestBadge.REST, label.badge)
        assertEquals("除夕", label.text)
    }

    @Test
    fun `连休中段命中同名节日仍算重复_让位农历`() {
        // 2026-02-17 正月初一：规则表命中的是「春节」，与段首同名 → 仍算重复，让位「初一」
        val label = composeDayCellLabel(
            info = info(HolidayKind.REST, "春节"),
            festivalRule = rule("春节"),
            lunarText = "初一",
            prevInfo = prev(HolidayKind.REST, "春节"),
        )
        assertEquals("初一", label.text)
    }
}
