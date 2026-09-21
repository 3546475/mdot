package com.mdot.app.core.holiday

import com.mdot.app.domain.model.HolidayKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * 节假日 JSON 契约（v1/v2 兼容）与内置资产自检。
 * 资产文件的校验放在单测里：JSON 打错字、法定天数算错，编译期都看不出来。
 */
class HolidayContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun entry(text: String) = json.decodeFromString<HolidayEntry>(text)

    // ---- v2 ----

    @Test
    fun `v2 法定日映射为法定档`() {
        val info = entry(
            """{"date":"2026-09-25","type":"legal","holiday":"中秋节","label":"中秋节","statutory":true}"""
        ).toHolidayInfo()
        assertEquals(HolidayKind.STATUTORY, info?.kind)
        assertEquals("中秋节", info?.name)
        assertNull(info?.makeupFor)
    }

    @Test
    fun `v2 连休日非法定时映射为休息档`() {
        val info = entry(
            """{"date":"2026-10-05","type":"transfer_holiday","holiday":"国庆节","label":"国庆节","statutory":false}"""
        ).toHolidayInfo()
        assertEquals(HolidayKind.REST, info?.kind)
        assertEquals("国庆节", info?.name)
    }

    @Test
    fun `v2 补班日映射为补班档且节日名只作备注`() {
        val info = entry(
            """{"date":"2026-02-14","type":"workday_makeup","holiday":"春节","label":"调休上班","statutory":false}"""
        ).toHolidayInfo()
        assertEquals(HolidayKind.WORKDAY, info?.kind)
        assertEquals("调休上班", info?.name)   // 角标文案，不是节日名
        assertEquals("春节", info?.makeupFor)  // 被调的节日进备注（调研文档 §2.4 坑一）
    }

    @Test
    fun `v2 普通工作日与普通周末不入库`() {
        assertNull(entry("""{"date":"2026-08-03","type":"workday"}""").toHolidayInfo())
        assertNull(
            entry("""{"date":"2026-08-01","type":"weekend","holiday":"","label":""}""")
                .toHolidayInfo()
        )
    }
    // ---- v1 兼容（线上远端文件与老库里的旧格式） ----

    @Test
    fun `v1 放假与补班仍可解析`() {
        val holiday = entry("""{"date":"2026-01-01","name":"元旦","type":"HOLIDAY"}""").toHolidayInfo()
        assertEquals(HolidayKind.STATUTORY, holiday?.kind) // v1 分不出调休，按改造前行为一律法定
        assertEquals("元旦", holiday?.name)

        val makeup = entry("""{"date":"2025-01-26","name":"补班","type":"WORKDAY"}""").toHolidayInfo()
        assertEquals(HolidayKind.WORKDAY, makeup?.kind)
        assertEquals("补班", makeup?.name)
    }

    @Test
    fun `日期非法不入库`() {
        assertNull(entry("""{"date":"2026-13-45","type":"HOLIDAY","name":"元旦"}""").toHolidayInfo())
    }

    // ---- 版本比较：远端滞后不得覆盖内置；内置比现库新时要以内置为准 ----

    @Test
    fun `远端更新才覆盖`() {
        assertTrue(isHolidayDataNewer("official-2026-03", "official-2026-02"))
        assertFalse(isHolidayDataNewer("official-2026-01", "official-2026-02"))
        assertFalse(isHolidayDataNewer("official-2026-02", "official-2026-02"))
        assertFalse(isHolidayDataNewer("", "official-2026-02"))
    }

    @Test
    fun `现库为空时接受远端`() {
        assertTrue(isHolidayDataNewer("official-2026-02", ""))
    }

    @Test
    fun `自定义版本方案（解析不出数字段）沿用不同即更新`() {
        assertTrue(isHolidayDataNewer("vNext", "official-2026-02"))
        assertTrue(isHolidayDataNewer("official-2026-02", "draft"))
    }

    @Test
    fun `内置资产版本高于老库时会被采用`() {
        val file = json.decodeFromString<HolidaysFile>(asset("holidays.json"))
        // 老安装（v0.6.21 及以前）库里的 dataVersion 是 official-2026-01：只有法定日、没有调休补班日
        assertTrue(isHolidayDataNewer(file.dataVersion, "official-2026-01"))
    }

    // ---- 内置资产自检 ----

    private fun asset(name: String): String =
        File("src/main/assets/$name").readText(Charsets.UTF_8)

    @Test
    fun `内置节假日资产满足 v2 契约且法定天数为13`() {
        val file = json.decodeFromString<HolidaysFile>(asset("holidays.json"))
        assertEquals(2, file.schemaVersion)
        assertTrue(file.dataVersion.isNotBlank())
        assertTrue(file.generatedAt.isNotBlank())
        assertTrue(file.years.keys.containsAll(listOf("2025", "2026")))

        file.years.forEach { (year, entries) ->
            val infos = entries.mapNotNull { it.toHolidayInfo() }
            assertEquals("每一条都应能解析出语义（$year）", entries.size, infos.size)
            assertEquals("$year 法定节假日应为 13 天", 13, infos.count { it.kind == HolidayKind.STATUTORY })
            val makeups = infos.filter { it.kind == HolidayKind.WORKDAY }
            assertTrue("$year 应有调休补班日", makeups.isNotEmpty())
            assertTrue("$year 补班日应带被调节日名", makeups.all { !it.makeupFor.isNullOrBlank() })
            assertTrue("$year 日期都应落在本年", infos.all { it.date.year.toString() == year })
        }
    }

    @Test
    fun `跨年边界_国庆补班落在9月也能入库`() {
        // 2026-09-20 是国庆的补班日（调研文档 §2.4 坑二：不能按月份猜节日年份）
        val file = json.decodeFromString<HolidaysFile>(asset("holidays.json"))
        val info = file.years.getValue("2026")
            .first { it.date == "2026-09-20" }
            .toHolidayInfo()
        assertEquals(HolidayKind.WORKDAY, info?.kind)
        assertEquals("国庆节", info?.makeupFor)
    }

    @Test
    fun `内置节日资产可解析且字段完整`() {
        val file = json.decodeFromString<FestivalsFile>(asset("festivals.json"))
        assertTrue("节日表应有数十条", file.festivals.size >= 20)
        val kinds = setOf("solar", "lunar", "term", "weekday")
        file.festivals.forEach { rule ->
            assertTrue("未知 kind：${rule.kind}", rule.kind in kinds)
            assertTrue("id 重复：${rule.id}", file.festivals.count { it.id == rule.id } == 1)
            assertTrue("短名截断后会为空：${rule.name}", rule.name.take(DAY_LABEL_MAX_CHARS).isNotBlank())
            when (rule.kind) {
                "solar" -> assertTrue("${rule.id} 缺月日", rule.month != null && rule.day != null)
                "weekday" -> assertTrue("${rule.id} 缺序号/星期", rule.ordinal != null && rule.weekday != null)
                "lunar" -> assertTrue("${rule.id} 缺农历月日", rule.lunarMonth != null && rule.lunarDay != null)
                "term" -> assertTrue("${rule.id} 缺节气名", !rule.term.isNullOrBlank())
            }
        }
        // 法定/传统节日应排在纪念日之前（同一天命中多条时决定右槽显示哪个）
        val teacher = file.festivals.first { it.id == "teachers" }
        val newYear = file.festivals.first { it.id == "new_year" }
        assertTrue(teacher.priority > newYear.priority)
    }

    @Test
    fun `节日表的公历纪念日能命中且教师节不放假`() {
        val file = json.decodeFromString<FestivalsFile>(asset("festivals.json"))
        val lunar = LunarEngine() // 未接入：农历/节气类一律不命中
        val teacherDay = LocalDate.parse("2026-09-10")
        val hit = file.festivals.topFestivalOn(teacherDay, lunar)
        assertEquals("教师节", hit?.name)
        assertFalse("教师节不放假（办法第五条）", hit!!.offDay)
    }
}
