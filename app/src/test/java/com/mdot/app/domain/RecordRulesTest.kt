package com.mdot.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「只记班次」的 0 时长规则（v0.7.9.0，docs/22 F-20261017-1）。
 *
 * 这组用例守两件事：
 * 1. **仓库层与弹层保存按钮共用同一套规则**——UI 的 `SheetUiState.canSave` 与 `RecordRepository.saveOt`
 *    都调这里，避免"按钮亮着、存盘被拒"的不一致；
 * 2. 0 时长**必须带班次或备注**（否则是空记录），请假 tab **不放宽**（0 小时请假没意义）。
 */
class RecordRulesTest {

    @Test
    fun `正常加班 1 分钟到 24 小时都可保存`() {
        assertTrue(RecordRules.canSaveOt(1, null, null))
        assertTrue(RecordRules.canSaveOt(60, "白班", null))
        assertTrue(RecordRules.canSaveOt(RecordRules.MAX_MINUTES, null, "备注"))
    }

    @Test
    fun `时长越界拒绝`() {
        assertFalse(RecordRules.canSaveOt(-1, "白班", null))
        assertFalse(RecordRules.canSaveOt(RecordRules.MAX_MINUTES + 1, "白班", "备注"))
    }

    @Test
    fun `0 时长必须带班次或备注`() {
        assertFalse("空记录不允许", RecordRules.canSaveOt(0, null, null))
        assertFalse("空白班次不算", RecordRules.canSaveOt(0, "  ", null))
        assertFalse("空白备注不算", RecordRules.canSaveOt(0, null, "  "))
        assertTrue(RecordRules.canSaveOt(0, "夜班", null))
        assertTrue(RecordRules.canSaveOt(0, null, "今天没加班"))
    }

    @Test
    fun `请假不放宽 0 小时仍拒绝`() {
        assertFalse(RecordRules.canSaveLeave(0))
        assertTrue(RecordRules.canSaveLeave(1))
        assertTrue(RecordRules.canSaveLeave(RecordRules.MAX_MINUTES))
        assertFalse(RecordRules.canSaveLeave(RecordRules.MAX_MINUTES + 1))
    }

    @Test
    fun `只记班次判定`() {
        assertTrue(RecordRules.isShiftOnly(0))
        assertFalse(RecordRules.isShiftOnly(1))
        assertFalse(RecordRules.isShiftOnly(RecordRules.MAX_MINUTES))
    }

    @Test
    fun `上限与仓库层常量同一口径（24 小时）`() {
        assertEquals(1440, RecordRules.MAX_MINUTES)
    }
}
