package com.mdot.app.domain.model

import kotlinx.serialization.Serializable

/**
 * 记月（月度工资单，13 文档设计稿）：按月存 DataStore JSON。
 * 分组固定四类（基本/补贴/扣款/其他），行 = 名称 + 金额（分，扣款组按正数存、展示层加负号）。
 * 条目名是用户数据种子（出厂行中文名，与 WorkSystem.displayName 同属 domain displayName 例外）。
 */
@Serializable
data class PayMonthItem(
    val id: Long,
    val name: String,
    val amountCents: Long = 0,
    /** 出厂固定行：不可删除（基本组三行与各组成员） */
    val builtin: Boolean = false,
)

@Serializable
data class PayMonthSheet(
    val basic: List<PayMonthItem> = emptyList(),
    val subsidy: List<PayMonthItem> = emptyList(),
    val deduction: List<PayMonthItem> = emptyList(),
    val other: List<PayMonthItem> = emptyList(),
) {
    companion object {
        /** 出厂月度工资单（与记月页设计稿一致；id 组内唯一即可） */
        fun default(): PayMonthSheet = PayMonthSheet(
            basic = listOf(
                PayMonthItem(1, "基本工资", builtin = true),
                PayMonthItem(2, "加班工资", builtin = true),
                PayMonthItem(3, "调休", builtin = true),
            ),
            subsidy = listOf(PayMonthItem(4, "其它补贴", builtin = true)),
            deduction = listOf(
                PayMonthItem(5, "其它扣款", builtin = true),
                PayMonthItem(6, "事假", builtin = true),
                PayMonthItem(7, "病假", builtin = true),
            ),
            other = listOf(
                PayMonthItem(8, "社保", builtin = true),
                PayMonthItem(9, "公积金", builtin = true),
                PayMonthItem(10, "个人所得税（新）", builtin = true),
            ),
        )
    }
}

/** 记月分组 */
enum class PayGroup { BASIC, SUBSIDY, DEDUCTION, OTHER }
