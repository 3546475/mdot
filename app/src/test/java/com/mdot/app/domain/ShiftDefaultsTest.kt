package com.mdot.app.domain

import com.mdot.app.domain.model.Shift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 「默认班次」纯函数：默认 = 排序后第一个**未隐藏**班次（v0.7.8.5，用户定「排第一个就是默认」） */
class ShiftDefaultsTest {

    private fun shift(id: Long, name: String, sort: Int, hidden: Boolean = false) =
        Shift(id = id, name = name, sort = sort, builtin = false, hidden = hidden, rest = false)

    @Test
    fun `取排序第一的未隐藏班次`() {
        val list = listOf(
            shift(3, "早班", sort = 2),
            shift(1, "白班", sort = 0),
            shift(2, "夜班", sort = 1),
        )
        assertEquals("白班", ShiftDefaults.of(list)?.name)
        assertEquals(1L, ShiftDefaults.idOf(list))
    }

    @Test
    fun `第一个已隐藏则顺延到下一个可见班次`() {
        val list = listOf(
            shift(1, "休息", sort = 0, hidden = true),
            shift(2, "白班", sort = 1),
            shift(3, "夜班", sort = 2),
        )
        assertEquals("白班", ShiftDefaults.of(list)?.name)
    }

    @Test
    fun `隐藏项夹在中间不影响取第一`() {
        val list = listOf(
            shift(1, "白班", sort = 0),
            shift(2, "夜班", sort = 1, hidden = true),
            shift(3, "中班", sort = 2),
        )
        assertEquals("白班", ShiftDefaults.of(list)?.name)
    }

    @Test
    fun `输入乱序也按 sort 取第一`() {
        val list = listOf(
            shift(9, "晚班", sort = 5),
            shift(7, "白班", sort = 3),
            shift(8, "中班", sort = 4),
        )
        assertEquals(7L, ShiftDefaults.idOf(list))
    }

    @Test
    fun `同 sort 时按 id 兜底（与 DAO 的 ORDER BY sort, id 一致）`() {
        val list = listOf(
            shift(5, "后建的", sort = 0),
            shift(4, "先建的", sort = 0),
        )
        assertEquals(4L, ShiftDefaults.idOf(list))
    }

    @Test
    fun `全部隐藏时没有默认班次`() {
        val list = listOf(
            shift(1, "白班", sort = 0, hidden = true),
            shift(2, "夜班", sort = 1, hidden = true),
        )
        assertNull(ShiftDefaults.of(list))
        assertNull(ShiftDefaults.idOf(list))
    }

    @Test
    fun `空列表没有默认班次`() {
        assertNull(ShiftDefaults.of(emptyList()))
        assertNull(ShiftDefaults.idOf(emptyList()))
    }
}
