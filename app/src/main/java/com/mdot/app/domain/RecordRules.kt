package com.mdot.app.domain

/**
 * 记录「可保存性」规则（纯函数，**仓库层与记加班弹层的保存按钮共用同一套**——硬规则 12：
 * 别在 UI 另写一遍，否则按钮亮着、存盘被拒这类不一致必然出现）。
 *
 * v0.7.9.0 起允许**「只记班次」的 0 时长加班记录**（用户 2026-10-17 拍板：正常上班没加班的日子
 * 也想留下「今天上的是白班/夜班」）。两条硬边界：
 * 1. **0 时长必须带班次或备注**：什么都没有的 0 时长就是空记录，只会污染「已记录 N 天」「实际记录日」
 *    这类按"有记录"计数的天数口径，也会在明细/导出里变成无意义的「0 小时」行；
 * 2. **请假不放宽**：0 小时请假没有意义（请假 tab 仍要求 1..[MAX_MINUTES]）。
 *
 * 语义补充：0 时长记录**计入**「已记录 N 天 / 实际记录日」——这是同一天用户拍板的口径
 * （"我上班了就该算"），实现上表现为各天数统计只按 `type == OT` 过滤、不按分钟数过滤，
 * 见 [com.mdot.app.domain.PayMonthDaily] 的 KDoc 与 docs/22 F-20261017-1。
 */
object RecordRules {

    /** 单条记录时长上限（分钟）= 24 小时 */
    const val MAX_MINUTES = 24 * 60

    /**
     * 加班记录可否保存：
     * - 1..[MAX_MINUTES]：正常加班；
     * - 0：**仅当**带了班次或备注（= 只记班次）；否则拒绝（空记录）。
     * - 越界（负数/>24h）：拒绝。
     */
    fun canSaveOt(durationMinutes: Int, shiftName: String?, note: String?): Boolean {
        if (durationMinutes == 0) {
            return !shiftName.isNullOrBlank() || !note.isNullOrBlank()
        }
        return durationMinutes in 1..MAX_MINUTES
    }

    /** 请假可否保存：必须 1..[MAX_MINUTES]（0 小时请假没有意义） */
    fun canSaveLeave(durationMinutes: Int): Boolean =
        durationMinutes in 1..MAX_MINUTES

    /** 是否「只记班次」的记录（0 时长）——日历小标记/明细行文案据此区分，别各处再判 `== 0` */
    fun isShiftOnly(durationMinutes: Int): Boolean = durationMinutes == 0
}
