package com.mdot.app.core.designsystem.component

import androidx.compose.ui.res.stringResource
import com.mdot.app.R
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 通用日期选择对话框：默认只能选今天及以前（统计/导出的自定义区间用） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePick(
    initial: LocalDate,
    allowFuture: Boolean = false,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                allowFuture || utcTimeMillis <= todayUtc
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis != null) {
                    onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                } else {
                    onDismiss()
                }
            }) { Text(stringResource(R.string.ds_pick_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ds_cancel)) } },
    ) {
        DatePicker(state = pickerState)
    }
}
