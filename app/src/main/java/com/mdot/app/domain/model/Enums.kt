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

/** 工时制度（08 文档 §2.1）：标准工时=底薪+加班费；小时工=纯时薪制；综合工时=按周期总工时判定加班；工地记工=项目制工天+借支+结算（12 文档） */
enum class WorkSystem(val displayName: String) {
    STANDARD("标准工时"),
    HOURLY("小时工"),
    COMPREHENSIVE("综合工时"),
    SITE("工地记工"),
}

// ---- 工地记工（12 文档 §3.3） ----

/** 出勤日状态：出勤 / 显式休息（区分"没记"与"休了"） */
enum class SiteDayStatus(val displayName: String) {
    WORK("出勤"),
    REST("休息"),
}

/** 半天归属（半天出勤时指明上午/下午，仅展示语义） */
enum class HalfOfDay(val displayName: String) { AM("上午"), PM("下午") }

/** 加班计钱方式：按加班工（Z 小时=1 个加班工）或按小时单价 */
enum class SiteOtMode(val displayName: String) {
    BY_DAY("按加班工"),
    BY_HOUR("按小时算"),
}

/** 借支用途（竞品同款 12 类，选填；显示名经 labelRes 解析，见 strings_site.xml） */
enum class AdvancePurpose { WAGE, LIVING, LODGING, LODGING_ALLOW, MEALS, MEALS_ALLOW, REWARD, MATERIALS, TRANSPORT, PROJECT_PAYMENT, POCKET, OTHER }

/** 深浅色三档 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** 节假日库条目类型 */
enum class HolidayKind { HOLIDAY, WORKDAY }
