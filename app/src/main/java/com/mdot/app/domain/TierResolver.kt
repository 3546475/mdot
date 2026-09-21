package com.mdot.app.domain

import com.mdot.app.domain.model.HolidayKind
import com.mdot.app.domain.model.RateTier
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 档位自动判定（优先级见 01 文档 §5.2）：
 * 1. 记录级手动补改（在 PayrollCalculator 处理，不在此处）
 * 2. 节假日库（三档语义见 [HolidayKind]）：法定 → 法定档；调休休息日（连休里的周末与
 *    被调成休息的工作日）→ 周末档；补班日（周末上班）→ 平时档
 * 3. 工作日设定：命中 → 平时；其余（含周末与自定义休息日）→ 周末档
 *
 * ⚠️ 第 2 条不能简化成「放假日一律法定档」：调休拼出来的休息日是休息日加班，按 2 倍而非 3 倍
 * （调研文档 §2.4 坑三）。
 */
class TierResolver(
    private val holidayKindOf: (LocalDate) -> HolidayKind?,
    private val workdays: Set<DayOfWeek>,
) {
    fun tierFor(date: LocalDate): RateTier = when (holidayKindOf(date)) {
        HolidayKind.STATUTORY -> RateTier.STATUTORY
        HolidayKind.REST -> RateTier.WEEKEND
        HolidayKind.WORKDAY -> RateTier.WEEKDAY
        null -> if (date.dayOfWeek in workdays) RateTier.WEEKDAY else RateTier.WEEKEND
    }
}
