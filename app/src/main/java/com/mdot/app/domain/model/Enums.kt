package com.mdot.app.domain.model

/** 记录类型：加班 / 请假 */
enum class RecordType { OT, LEAVE }

/** 三档倍率档位 */
enum class RateTier(val displayName: String) {
    WEEKDAY("平时"),
    WEEKEND("周末"),
    STATUTORY("法定"),
}

/** 档位来源：自动判定 / 用户手动补改 */
enum class TierSource { AUTO, MANUAL }

/** 请假类型（含默认扣薪系数，见 01 文档 §5） */
enum class LeaveType(val defaultCoefficient: Double, val displayName: String) {
    PERSONAL(1.0, "事假"),
    SICK(0.5, "病假"),
    ANNUAL(0.0, "年假"),
    COMP(0.0, "调休"),
    ABSENT(1.0, "旷工"),
    OTHER(0.0, "其他"),
}

/** 计薪模式 */
enum class SalaryMode(val displayName: String) {
    BASE("底薪折算"),
    MANUAL("手动单价"),
}

/** 工时制度（08 文档 §2.1）：标准工时=底薪+加班费；小时工=纯时薪制；综合工时=按周期总工时判定加班 */
enum class WorkSystem(val displayName: String) {
    STANDARD("标准工时"),
    HOURLY("小时工"),
    COMPREHENSIVE("综合工时"),
}

/** 深浅色三档 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** 节假日库条目类型 */
enum class HolidayKind { HOLIDAY, WORKDAY }
