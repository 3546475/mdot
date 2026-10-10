package com.mdot.app.domain.model

import com.mdot.app.domain.PayMonthDayBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记月「行模板」（用户 2026-10-08 定：加的行和按日单价要能继承到下个月，金额每月重算）。
 *
 * 重点守两条：**删掉的行不能复活**（模板补齐绝不能放读取侧）、
 * **金额不继承**（单价继承、金额按当月重算）。
 */
class PayMonthRowTemplateTest {

    private fun sheetWithUserRow(
        group: PayGroup = PayGroup.SUBSIDY,
        name: String = "餐补",
        rate: Long = 5_000,
    ): PayMonthSheet {
        val base = PayMonthSheet.default()
        val row = PayMonthItem(
            id = base.subsidy.nextUserRowId(),
            name = name,
            dayRateCents = rate,
        )
        val updated = base.replaceRows(group, base.subsidy + row)
        return updated
    }

    // ---- 模板重算 ----

    @Test
    fun `模板 - 用户行进模板并带上按日配置`() {
        val sheet = sheetWithUserRow(rate = 5_000)
        val tpl = PayMonthTemplates().of(sheet)
        assertEquals(1, tpl.rows.size)
        val r = tpl.rows.first()
        assertEquals("餐补", r.name)
        assertEquals(PayGroup.SUBSIDY.name, r.group)
        assertEquals(5_000L, r.dayRateCents)
        assertNull("用户行的 builtinId 应为 null（按名字匹配）", r.builtinId)
    }

    @Test
    fun `模板 - 没配按日的出厂行不进模板`() {
        val tpl = PayMonthTemplates().of(PayMonthSheet.default())
        assertTrue("出厂行默认不带按日配置，不该占模板", tpl.rows.isEmpty())
    }

    @Test
    fun `模板 - 配了按日的出厂行进模板且记 id`() {
        val sheet = PayMonthSheet.default().let { s ->
            s.replaceRows(PayGroup.SUBSIDY,
                s.subsidy.map {
                    if (it.id == PayMonthSheet.FULL_ATTENDANCE_ROW_ID) it.copy(dayRateCents = 3_000) else it
                },
            )
        }
        val row = PayMonthTemplates().of(sheet).rows.single()
        assertEquals(PayMonthSheet.FULL_ATTENDANCE_ROW_ID, row.builtinId)
        assertEquals(3_000L, row.dayRateCents)
    }

    // ---- 新月份套模板 ----

    @Test
    fun `套模板 - 新月份按模板补出用户行`() {
        val tpl = PayMonthTemplates().of(sheetWithUserRow())
        val fresh = PayMonthSheet.default().withTemplateRows(tpl)
        val hit = fresh.subsidy.first { it.name == "餐补" }
        assertEquals(5_000L, hit.dayRateCents)
        assertEquals("补出来的行金额应留 0 交给引擎算", 0L, hit.amountCents)
    }

    @Test
    fun `套模板 - 单价继承但金额不继承`() {
        val usedSheet = sheetWithUserRow(rate = 5_000).let { s ->
            // 上月这行已经算出了金额
            s.replaceRows(PayGroup.SUBSIDY,
                s.subsidy.map { if (it.name == "餐补") it.copy(amountCents = 100_000) else it },
            )
        }
        val tpl = PayMonthTemplates().of(usedSheet)
        val fresh = PayMonthSheet.default().withTemplateRows(tpl)
        val hit = fresh.subsidy.single { it.name == "餐补" }
        assertEquals(5_000L, hit.dayRateCents)
        assertEquals("金额必须不继承", 0L, hit.amountCents)
    }

    @Test
    fun `套模板 - 出厂行只同步按日配置不碰金额`() {
        val tpl = PayMonthTemplates(
            listOf(
                PayMonthRowTemplate(
                    group = PayGroup.SUBSIDY.name,
                    name = "全勤奖",
                    builtinId = PayMonthSheet.FULL_ATTENDANCE_ROW_ID,
                    dayRateCents = 3_000,
                    dayBasis = PayMonthDayBasis.RECORD,
                    dayDeductLeave = true,
                )
            )
        )
        val base = PayMonthSheet.default()
        val filled = base.replaceRows(PayGroup.SUBSIDY,
            base.subsidy.map {
                if (it.id == PayMonthSheet.FULL_ATTENDANCE_ROW_ID) it.copy(amountCents = 30_000) else it
            },
        )
        val out = filled.withTemplateRows(tpl)
        val hit = out.subsidy.single { it.id == PayMonthSheet.FULL_ATTENDANCE_ROW_ID }
        assertEquals(3_000L, hit.dayRateCents)
        assertEquals(PayMonthDayBasis.RECORD, hit.dayBasis)
        assertTrue(hit.dayDeductLeave)
        assertEquals("出厂行原有金额不该被动", 30_000L, hit.amountCents)
    }

    @Test
    fun `套模板 - 空模板不动单据`() {
        val base = PayMonthSheet.default()
        assertEquals(base, base.withTemplateRows(PayMonthTemplates()))
    }

    // ---- 删掉的行不能复活（本次最容易踩的坑）----

    @Test
    fun `不复活 - 删除后从模板移除则新月份不再补出`() {
        val sheet = sheetWithUserRow()
        val tpl = PayMonthTemplates().of(sheet)
        // 用户把「餐补」删了：单据里没了，重新算模板时也不该再有它
        val deleted = sheet.replaceRows(PayGroup.SUBSIDY, sheet.subsidy.filter { it.name != "餐补" },
        )
        val afterDelete = PayMonthTemplates().of(deleted)
        assertTrue("删除后模板应同步移除", afterDelete.rows.none { it.name == "餐补" })
        // 下个月按新模板补 → 不会复活
        val fresh = PayMonthSheet.default().withTemplateRows(afterDelete)
        assertTrue("下个月不该复活已删的行", fresh.subsidy.none { it.name == "餐补" })
    }

    @Test
    fun `不复活 - 已存盘的月份读取不再套模板（模板只在建底稿时用一次）`() {
        val sheet = sheetWithUserRow()
        val tpl = PayMonthTemplates().of(sheet)
        val deleted = sheet.replaceRows(PayGroup.SUBSIDY, sheet.subsidy.filter { it.name != "餐补" },
        )
        // 删除已落盘 → 之后读的是存盘单据本身，不再补模板，故不会复活
        assertTrue(deleted.subsidy.none { it.name == "餐补" })
        assertTrue(tpl.rows.any { it.name == "餐补" })   // 模板里本来还有
    }

    // ---- id 不撞车 ----

    @Test
    fun `id - 补出的用户行 id 不与出厂行撞车`() {
        val tpl = PayMonthTemplates(
            listOf(PayMonthRowTemplate(PayGroup.SUBSIDY.name, "餐补", dayRateCents = 5_000)),
        )
        val fresh = PayMonthSheet.default().withTemplateRows(tpl)
        val hit = fresh.subsidy.single { it.name == "餐补" }
        assertTrue("用户行 id 必须 >= USER_ROW_ID_BASE", hit.id >= PayMonthSheet.USER_ROW_ID_BASE)
    }

    @Test
    fun `id - 多个模板行连补 id 互不相同`() {
        val tpl = PayMonthTemplates(
            listOf(
                PayMonthRowTemplate(PayGroup.SUBSIDY.name, "餐补", dayRateCents = 5_000),
                PayMonthRowTemplate(PayGroup.SUBSIDY.name, "驻场补贴", dayRateCents = 8_000),
                PayMonthRowTemplate(PayGroup.SUBSIDY.name, "夜班补贴", dayRateCents = 3_000),
            )
        )
        val fresh = PayMonthSheet.default().withTemplateRows(tpl)
        val ids = fresh.subsidy.filter { !it.builtin }.map { it.id }
        assertEquals(3, ids.size)
        assertEquals("连补的行 id 不能互相撞车（否则改一行会改错行）", ids.size, ids.distinct().size)
    }

    @Test
    fun `模板 - 四种分组都能补（映射无遗漏）`() {
        for (g in PayGroup.entries) {
            val tpl = PayMonthTemplates(
                listOf(PayMonthRowTemplate(g.name, "测试项-${g.name}", dayRateCents = 1_000)),
            )
            val fresh = PayMonthSheet.default().withTemplateRows(tpl)
            val rows = fresh.rowsOf(g)
            assertTrue("分组 ${g.name} 未补出行", rows!!.any { it.name == "测试项-${g.name}" })
        }
    }

    @Test
    fun `模板 - 未知分组名被忽略不崩`() {
        val tpl = PayMonthTemplates(listOf(PayMonthRowTemplate("NOT_A_GROUP", "x")))
        assertEquals(PayMonthSheet.default(), PayMonthSheet.default().withTemplateRows(tpl))
    }

    @Test
    fun `行属性 - dayRate 为 0 即非按日计算`() {
        assertFalse(PayMonthItem(1, "x").isDailyComputed)
        assertTrue(PayMonthItem(1, "x", dayRateCents = 1).isDailyComputed)
    }

    // ---- 哪些行允许按日（用户 2026-10-08：出厂行只留「其它补贴」）----

    @Test
    fun `允许按日 - 出厂固定行里只有其它补贴允许`() {
        val d = PayMonthSheet.default()
        val allowed = (d.basic + d.subsidy + d.deduction + d.other).filter { it.supportsDailyRate }
        assertEquals("出厂行里应只有一行允许按日", 1, allowed.size)
        assertEquals(PayMonthSheet.OTHER_SUBSIDY_ROW_ID, allowed.single().id)
        assertEquals("其它补贴", allowed.single().name)
    }

    @Test
    fun `允许按日 - 有确定算法的出厂行一律不允许`() {
        val d = PayMonthSheet.default()
        val byId = (d.basic + d.subsidy + d.deduction + d.other).associateBy { it.id }
        listOf(
            PayMonthSheet.BASE_ROW_ID,
            PayMonthSheet.OT_ROW_ID,
            PayMonthSheet.COMP_ROW_ID,
            PayMonthSheet.SOCIAL_ROW_ID,
            PayMonthSheet.FUND_ROW_ID,
            PayMonthSheet.TAX_ROW_ID,
        ).forEach { id ->
            assertFalse("出厂行 id=$id 不该允许按日", byId.getValue(id).supportsDailyRate)
        }
    }

    @Test
    fun `允许按日 - 全勤奖不允许（它是手填项，不参与引擎推导）`() {
        val row = PayMonthSheet.default().subsidy.single { it.id == PayMonthSheet.FULL_ATTENDANCE_ROW_ID }
        assertFalse(row.supportsDailyRate)
    }

    @Test
    fun `允许按日 - 用户新增行允许`() {
        val user = PayMonthItem(id = 1000L, name = "餐补", builtin = false)
        assertTrue("用户自己加的行必须允许按日", user.supportsDailyRate)
    }
}
