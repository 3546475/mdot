package com.mdot.app.domain.model

/**
 * 内置夜班。记月「夜班补贴」行按 [NAME] 识别记录，故这个班次**不允许改名**。
 *
 * 用户 2026-11-22 定：夜班补贴要跟「记加班时选了夜班」联动。记录里的班次名是快照，
 * 改名会让已有记录与新记录分成两个名字，按名字匹配就漏账，所以直接锁住名称。
 */
object NightShift {
    const val NAME = "夜班"
}

/** 记月补贴预设「夜班补贴」：开启「与夜班记录联动」后按 [NightShift] 的记录日计天。 */
object NightSubsidy {
    const val NAME = "夜班补贴"
}
