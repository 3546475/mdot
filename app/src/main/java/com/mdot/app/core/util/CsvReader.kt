package com.mdot.app.core.util

import com.mdot.app.domain.model.LeaveDraft
import com.mdot.app.domain.model.OtDraft
import com.mdot.app.domain.model.LeaveType
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.TierSource
import java.time.LocalDate

/**
 * CSV 导入：解析本 App「导出 CSV 明细」生成的加班/请假记录草稿。
 * 仅取与记录直接相关的列（日期、加班/请假时长、转调休、请假类型、备注），
 * 金额/档位由工资引擎现算；无法识别的行（元信息/合计/格式错误）跳过。
 */
object CsvReader {

    data class ImportResult(
        val ot: List<OtDraft> = emptyList(),
        val leave: List<LeaveDraft> = emptyList(),
        val skipped: Int = 0,
    )

    // 列序与 CsvWriter.HEADER 对齐
    private const val C_DATE = 0
    private const val C_OT_HOURS = 3
    private const val C_TO_COMP_HOURS = 7
    private const val C_LEAVE_TYPE = 8
    private const val C_LEAVE_HOURS = 9
    private const val C_NOTE = 11

    fun parse(csv: String): ImportResult {
        val ot = mutableListOf<OtDraft>()
        val leave = mutableListOf<LeaveDraft>()
        var skipped = 0

        // 去 BOM，按行切（导出用 \r\n）
        csv.trimStart('\uFEFF')
            .lines()
            .map { it.trim('\r') }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val cols = parseCsvLine(line)
                val date = parseDateCell(cols.getOrNull(C_DATE)) ?: run {
                    // 首列不是日期 → 元信息/表头/合计行，跳过
                    skipped++
                    return@forEach
                }
                if (date.isAfter(LocalDate.now())) {
                    skipped++
                    return@forEach
                }

                val note = cols.getOrNull(C_NOTE)?.trim()?.takeIf { it.isNotEmpty() }

                // 加班
                val otHours = cols.getOrNull(C_OT_HOURS)?.toDoubleOrNull()
                if (otHours != null && otHours > 0) {
                    val minutes = Math.round(otHours * 60).toInt()
                    val toComp = Math.round(
                        (cols.getOrNull(C_TO_COMP_HOURS)?.toDoubleOrNull() ?: 0.0) * 60
                    ).toInt().coerceIn(0, minutes)
                    ot += OtDraft(
                        date = date,
                        shiftId = null,
                        shiftName = null,
                        durationMinutes = minutes,
                        tier = RateTier.WEEKDAY,
                        tierSource = TierSource.AUTO,
                        toCompMinutes = toComp,
                        note = note,
                    )
                }

                // 请假
                val leaveHours = cols.getOrNull(C_LEAVE_HOURS)?.toDoubleOrNull()
                val leaveType = cols.getOrNull(C_LEAVE_TYPE)?.let(::matchLeaveType)
                if (leaveHours != null && leaveHours > 0 && leaveType != null) {
                    leave += LeaveDraft(
                        date = date,
                        shiftId = null,
                        shiftName = null,
                        durationMinutes = Math.round(leaveHours * 60).toInt(),
                        leaveType = leaveType,
                        note = note,
                    )
                }

                if (otHours == null && leaveHours == null) skipped++
            }

        return ImportResult(ot = ot, leave = leave, skipped = skipped)
    }

    /** 日期单元格：导出为 ="yyyy-MM-dd"，也兼容纯 yyyy-MM-dd */
    private fun parseDateCell(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        // 去掉 ="..." 包裹与所有引号
        val s = raw.trim().trim('"').removePrefix("=").trim('"').trim()
        return runCatching { LocalDate.parse(s.take(10)) }.getOrNull()
    }

    /** 请假中文名 → 枚举 */
    private fun matchLeaveType(displayName: String): LeaveType? =
        LeaveType.entries.firstOrNull { it.displayName == displayName.trim() }

    /** 简单 CSV 行解析（支持引号包裹与 "" 转义） */
    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"'); i++
                    } else inQuotes = false
                }
                !inQuotes && c == '"' -> inQuotes = true
                !inQuotes && c == ',' -> { out += cur.toString(); cur.setLength(0) }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }
}
