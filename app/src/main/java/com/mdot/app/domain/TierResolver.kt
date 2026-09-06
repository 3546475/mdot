package com.mdot.app.domain

import com.mdot.app.domain.model.HolidayKind
import com.mdot.app.domain.model.RateTier
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 档位自动判定（优先级见 01 文档 §5.2）：
 * 1. 记录级手动补改（在 PayrollCalculator 处理，不在此处）
 * 2. 节假日库：HOLIDAY → 法定；WORKDAY（补班）→ 平时
 * 3. 工作日设定：命中 → 平时；其余（含周末与自定义休息日）→ 周末档
 */
class TierResolver(
    private val holidayKindOf: (LocalDate) -> HolidayKind?,
    private val workdays: Set<DayOfWeek>,
) {
    fun tierFor(date: LocalDate): RateTier = when (holidayKindOf(date)) {
        HolidayKind.HOLIDAY -> RateTier.STATUTORY
        HolidayKind.WORKDAY -> RateTier.WEEKDAY
        null -> if (date.dayOfWeek in workdays) RateTier.WEEKDAY else RateTier.WEEKEND
    }
}
