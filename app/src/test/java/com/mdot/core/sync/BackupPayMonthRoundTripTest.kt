package com.mdot.core.sync

import com.mdot.app.core.sync.BackupFile
import com.mdot.app.core.sync.SettingsDto
import com.mdot.app.domain.PayMonthDayBasis
import com.mdot.app.domain.model.PayGroup
import com.mdot.app.domain.model.PayMonthCustomPresets
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthRowTemplate
import com.mdot.app.domain.model.PayMonthSheet
import com.mdot.app.domain.model.PayMonthTemplates
import com.mdot.app.domain.model.replaceRows
import com.mdot.app.domain.model.withTemplateRows
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记月数据进备份的**往返**单测（v0.7.8.4，2026-10-08 补）。
 *
 * 记月工资单是用户逐行手工维护的数据，补进备份前换手机/恢复会整份丢失。
 * 本测试锁三件事：单据能原样编解码、**旧包缺字段能降级**、空包**不会覆盖**本机数据。
 */
class BackupPayMonthRoundTripTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun sheetWithDailyRow() = PayMonthSheet.default().let { base ->
        base.replaceRows(
            PayGroup.SUBSIDY,
            base.subsidy + PayMonthItem(
                id = 1000L,
                name = "餐补",
                amountCents = 105_000,
                dayRateCents = 5_000,
                dayBasis = PayMonthDayBasis.RECORD,
                dayDeductLeave = true,
                derivationDays = 21.0,
                derivationUnitCents = 5_000,
            ),
        )
    }

    // ---- 往返 ----

    @Test
    fun `往返 - 单据原样编解码`() {
        val sheets = mapOf("paymonth_2026-09" to sheetWithDailyRow())
        val dto = SettingsDto(payMonthSheets = sheets)
        val text = json.encodeToString(SettingsDto.serializer(), dto)
        val back = json.decodeFromString(SettingsDto.serializer(), text)
        assertEquals(1, back.payMonthSheets.size)
        assertEquals(sheets, back.payMonthSheets)
    }

    @Test
    fun `往返 - 按日配置与推导依据不丢`() {
        val dto = SettingsDto(payMonthSheets = mapOf("paymonth_2026-09" to sheetWithDailyRow()))
        val back = json.decodeFromString(
            SettingsDto.serializer(),
            json.encodeToString(SettingsDto.serializer(), dto),
        )
        val row = back.payMonthSheets.getValue("paymonth_2026-09").subsidy.single { it.name == "餐补" }
        assertEquals(5_000L, row.dayRateCents)
        assertEquals(PayMonthDayBasis.RECORD, row.dayBasis)
        assertTrue(row.dayDeductLeave)
        assertEquals(21.0, row.derivationDays!!, 1e-9)
        assertEquals(5_000L, row.derivationUnitCents)
        assertEquals(105_000L, row.amountCents)
        assertTrue(row.isDailyComputed)
    }

    @Test
    fun `往返 - 行模板编解码`() {
        val tpl = PayMonthTemplates(
            listOf(
                PayMonthRowTemplate("SUBSIDY", "餐补", dayRateCents = 5_000),
                PayMonthRowTemplate(
                    "SUBSIDY", "全勤奖", builtinId = PayMonthSheet.FULL_ATTENDANCE_ROW_ID,
                    dayRateCents = 3_000, dayBasis = PayMonthDayBasis.CALENDAR, dayDeductLeave = true,
                ),
            )
        )
        val dto = SettingsDto(payMonthTemplates = tpl)
        val back = json.decodeFromString(
            SettingsDto.serializer(),
            json.encodeToString(SettingsDto.serializer(), dto),
        )
        assertEquals(tpl, back.payMonthTemplates)
    }

    @Test
    fun `往返 - 出厂行（未配按日）不进模板`() {
        // 这条是保险：模板里只该有用户行和配了按日的出厂行
        val tpl = PayMonthTemplates().of(PayMonthSheet.default())
        assertTrue(tpl.rows.isEmpty())
    }

    // ---- 旧包兼容（降级）----

    @Test
    fun `旧包兼容 - 缺记月字段解码为空且不报错`() {
        // 模拟旧包：JSON 里完全没有 payMonth* 键
        val legacy = """{"salary":{},"cycleAnchorDay":1,"workdays":["MONDAY"]}"""
        val back = json.decodeFromString(SettingsDto.serializer(), legacy)
        assertTrue("旧包解码出的单据应为空（恢复侧据此保留本机不覆盖）", back.payMonthSheets.isEmpty())
        assertTrue(back.payMonthTemplates.rows.isEmpty())
        assertTrue(back.payMonthCustomPresets.byGroup.isEmpty())
    }

    // ---- 自定义添加预设（2026-10-09：+ chip 新建并持久化）----

    @Test
    fun `自定义预设 - 随备份往返不丢`() {
        val dto = SettingsDto(
            payMonthCustomPresets = PayMonthCustomPresets()
                .plus(PayGroup.SUBSIDY, "季度奖")!!
                .plus(PayGroup.SUBSIDY, "项目奖")!!
                .plus(PayGroup.OTHER, "宿舍费")!!,
        )
        val back = json.decodeFromString(
            SettingsDto.serializer(),
            json.encodeToString(SettingsDto.serializer(), dto),
        )
        assertEquals(listOf("季度奖", "项目奖"), back.payMonthCustomPresets.of(PayGroup.SUBSIDY))
        assertEquals(listOf("宿舍费"), back.payMonthCustomPresets.of(PayGroup.OTHER))
    }

    @Test
    fun `自定义预设 - 空集合表示旧包没有这项而非清空预设`() {
        val back = json.decodeFromString(
            SettingsDto.serializer(),
            json.encodeToString(SettingsDto.serializer(), SettingsDto()),
        )
        // 恢复侧的条件是 byGroup.isNotEmpty() 才回灌；锁住默认值确实是空
        assertFalse(back.payMonthCustomPresets.byGroup.isNotEmpty())
    }

    @Test
    fun `旧包兼容 - 单据缺按日字段时默认不按日算`() {
        val legacyItem = """{"id":1000,"name":"餐补","amountCents":1000}"""
        val item = json.decodeFromString(PayMonthItem.serializer(), legacyItem)
        assertFalse("旧行不应被当成按日计算", item.isDailyComputed)
        assertEquals(0L, item.dayRateCents)
        assertEquals(PayMonthDayBasis.STANDARD, item.dayBasis)
        assertFalse(item.dayDeductLeave)
    }

    // ---- 空包不覆盖（恢复语义）----

    @Test
    fun `空包 - 空单据集合表示旧包没有记月而非清空记月`() {
        val back = json.decodeFromString(
            SettingsDto.serializer(),
            json.encodeToString(SettingsDto.serializer(), SettingsDto()),
        )
        // 恢复侧的条件是 isNotEmpty() 才回灌；这里锁住默认值确实是空
        assertFalse(back.payMonthSheets.isNotEmpty())
        assertFalse(back.payMonthTemplates.rows.isNotEmpty())
    }

    @Test
    fun `模板 - 空模板时新月份不受影响`() {
        assertEquals(PayMonthSheet.default(), PayMonthSheet.default().withTemplateRows(PayMonthTemplates()))
    }
}
