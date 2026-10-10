package com.mdot.app.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mdot.app.R
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.DayPickDialog
import com.mdot.app.core.designsystem.component.JiabanAlertDialog
import com.mdot.app.core.designsystem.component.JiabanButton
import com.mdot.app.core.designsystem.component.JiabanButtonRole
import com.mdot.app.core.designsystem.dialogContainerColor
import com.mdot.app.domain.util.TimeUtils
import java.time.LocalDate

/**
 * 工地区间口径选择弹窗——**统计页与明细页共用**（同功能必须同实现）。
 *
 * 入口是 [RangePill]，与两页非工地口径的方案 C 同构：一颗胶囊 = 唯一入口，
 * 点开弹窗选口径 / 框自定义起止。
 *
 * ⚠️ 工地**不给 ‹ › 步进**：区间是「本期待结 / 项目全周期 / 自定义」三选一，
 * 前两者随结算与项目滚动，不存在「上一期」的概念，硬加步进是假语义。
 * （步进只有非工地那侧有：stepBack/stepForward。）
 */
@Composable
fun SiteRangeDialog(
    mode: SiteDetailRangeMode,
    customFrom: LocalDate?,
    customTo: LocalDate?,
    onMode: (SiteDetailRangeMode) -> Unit,
    onCustomFrom: (LocalDate) -> Unit,
    onCustomTo: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // 嵌套的起止日期选择（"from"/"to"）；DayPickDialog 走覆盖层栈，可叠在本弹窗之上
    var picking by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now()

    JiabanAlertDialog(containerColor = dialogContainerColor(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.range_pick_title)) },
        text = {
            Column {
                SiteRangeChips(selected = mode, onSelect = onMode)
                if (mode == SiteDetailRangeMode.CUSTOM) {
                    Spacer(Modifier.height(Spacing.m))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        JiabanButton(
                            text = stringResource(
                                R.string.stats_custom_from,
                                customFrom?.let(TimeUtils::mdCn)
                                    ?: stringResource(R.string.stats_custom_from_default),
                            ),
                            onClick = { picking = "from" },
                            role = JiabanButtonRole.SECONDARY,
                        )
                        JiabanButton(
                            text = stringResource(
                                R.string.stats_custom_to,
                                customTo?.let(TimeUtils::mdCn)
                                    ?: stringResource(R.string.stats_custom_to_default),
                            ),
                            onClick = { picking = "to" },
                            role = JiabanButtonRole.SECONDARY,
                        )
                    }
                }
            }
        },
        confirmButton = {
            JiabanButton(
                text = stringResource(R.string.ds_cancel),
                onClick = onDismiss,
                role = JiabanButtonRole.GHOST,
            )
        },
        // 本弹窗只有「取消」一个动作 ⇒ 无次要槽。⚠️ 勿写 `dismissButton = {}`：空槽在 MIUIX 分栏行里
        // 仍占半格 ⇒ 左半格空白 + 一条孤立的竖分隔线（DialogContractTest 守门）
    )

    // 起止日期选择：不关闭本弹窗，选完回到本弹窗（覆盖层栈支持弹窗叠弹窗）
    picking?.let { which ->
        DayPickDialog(
            title = stringResource(
                if (which == "from") R.string.ds_pick_start else R.string.ds_pick_end,
            ),
            initial = if (which == "from") customFrom ?: today.withDayOfMonth(1) else customTo ?: today,
            onPick = { d ->
                if (which == "from") onCustomFrom(d) else onCustomTo(d)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}