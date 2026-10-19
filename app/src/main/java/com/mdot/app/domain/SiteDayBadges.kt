package com.mdot.app.domain

import com.mdot.app.domain.model.SiteAdvance
import com.mdot.app.domain.model.SiteAttendance
import com.mdot.app.domain.model.SitePieceWork

/**
 * 工地「留证/记录」的**可见性口径**（纯函数，v0.7.9.0）。
 *
 * 背景：照片在 12 文档 D10 就存了（`SiteAttendance.photos` / `SiteAdvance.photos`，SOH 分隔），
 * 但只在**编辑弹层**里能看到——记完之后翻明细、看月历全都看不见，等于"留证留了个寂寞"。
 * 月历格子只有 40dp 见方，放不下文字，所以口径收成两类：
 * 1. [parsePhotoPaths] 串 → 路径列表（**唯一出处**，编辑页/明细/月历都走它，别再各写一遍 split）；
 * 2. [SiteDayBadges] 某一天"有哪些记录"的三颗角标（照片 / 包工 / 借支），供月历右上角点阵。
 */
fun parsePhotoPaths(raw: String?): List<String> =
    raw?.split('\u0001')?.filter { it.isNotBlank() } ?: emptyList()

/**
 * 工地某一天的角标（v0.7.9.0）：月历上除了「N 工」，还要能看出这天有没有留证据、有没有包工/借支。
 * 三个字段都是**计数**（照片可能多张），UI 只取"有没有"和"张数"。
 *
 * @property photoCount 当天出勤 + 借支上挂的照片总张数
 * @property pieceCount 当天包工/工量笔数
 * @property advanceCount 当天借支笔数
 */
data class SiteDayBadges(
    val photoCount: Int = 0,
    val pieceCount: Int = 0,
    val advanceCount: Int = 0,
) {
    val hasPhotos: Boolean get() = photoCount > 0
    val hasPiece: Boolean get() = pieceCount > 0
    val hasAdvance: Boolean get() = advanceCount > 0

    /** 什么都没有 ⇒ 当月历不画点阵（也不占高） */
    val isEmpty: Boolean get() = photoCount == 0 && pieceCount == 0 && advanceCount == 0

    companion object {
        /** 由当天各表的记录汇总（同一天可能多笔出勤/借支） */
        fun of(
            attendances: List<SiteAttendance> = emptyList(),
            pieces: List<SitePieceWork> = emptyList(),
            advances: List<SiteAdvance> = emptyList(),
        ) = SiteDayBadges(
            photoCount = attendances.sumOf { parsePhotoPaths(it.photos).size } +
                advances.sumOf { parsePhotoPaths(it.photos).size },
            pieceCount = pieces.size,
            advanceCount = advances.size,
        )
    }
}
