package com.mdot.app.core.util

import com.mdot.app.domain.model.LeaveType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvReaderTest {

    private val sampleCsv = buildString {
        append('﻿') // BOM
        append("考勤周期,2026-08-28 – 2026-09-27\r\n")
        append("导出时间,2026-09-04T20:00\r\n")
        append("日期,星期,班次,加班时长(小时),档位,倍率,加班费(元),转调休(小时),请假类型,请假时长(小时),请假扣款(元),备注\r\n")
        append("\"=\"\"2026-09-01\"\"\",周二,,2,平时,1.5,34.48,1,,,,,\r\n")
        append("\"=\"\"2026-09-02\"\"\",周三,,,,,,,事假,4,,\r\n")
        append("\"=\"\"2026-09-03\"\"\",周四,,1.5,周末,2,,,,,,\r\n")
        append("合计,,,3.5,,,51.72,,,4,0,\r\n")
        append("调休余额(小时),1\r\n")
    }

    @Test
    fun parsesOtAndLeaveRows_skipsMetaAndSummary() {
        val result = CsvReader.parse(sampleCsv)

        assertEquals(2, result.ot.size)
        val first = result.ot.first()
        assertEquals(2 * 60, first.durationMinutes)
        assertEquals(60, first.toCompMinutes)
        assertEquals(1, result.leave.size)
        assertEquals(LeaveType.PERSONAL, result.leave.first().leaveType)
        assertEquals(4 * 60, result.leave.first().durationMinutes)
        assertTrue(result.skipped >= 4)
    }

    @Test
    fun emptyOrGarbageYieldsNothing() {
        val r = CsvReader.parse("这不是一个,CSV文件\n随便,内容")
        assertEquals(0, r.ot.size)
        assertEquals(0, r.leave.size)
    }
}
