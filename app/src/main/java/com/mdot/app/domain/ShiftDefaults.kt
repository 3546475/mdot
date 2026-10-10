package com.mdot.app.domain

import com.mdot.app.domain.model.Shift

/**
 * 「默认班次」的唯一定义（v0.7.8.5，用户定：**排在第一个的就是默认班次**）。
 *
 * 默认班次 = 按 [Shift.sort] 升序后的**第一个未隐藏**班次。两条例外都要照顾到：
 * ① **隐藏项不算**——记录弹层的班次候选本就只列未隐藏项，若把隐藏项当默认，
 *    会出现「列表徽标标着默认、记录时却选不到」的自相矛盾；
 * ② **顺序即默认**——没有额外的「设为默认」字段：用户在班次管理里长按拖动排序，
 *    拖到第一位的那个自然成为默认，无需第二处设置（否则两处状态会打架、还要进备份）。
 *
 * 纯函数（domain 零 Android 依赖，硬规则 1）；**记录侧与列表徽标必须都调这个函数**——
 * 两边各写一次 `firstOrNull { !hidden }` 迟早漂移（硬规则 12：推导复用同一份算法）。
 */
object ShiftDefaults {

    /** 排序键：`sort` 升序，同 `sort` 时用 `id` 兜底（与 `ShiftDao` 的 `ORDER BY sort, id` 一致） */
    private val order = compareBy<Shift>({ it.sort }, { it.id })

    /** 默认班次；全部隐藏或列表为空时返回 null（此时记录弹层不预选，由用户自己点） */
    fun of(shifts: List<Shift>): Shift? = shifts.sortedWith(order).firstOrNull { !it.hidden }

    /** 默认班次的 id（列表行标徽标用；无需比较整个对象） */
    fun idOf(shifts: List<Shift>): Long? = of(shifts)?.id
}
