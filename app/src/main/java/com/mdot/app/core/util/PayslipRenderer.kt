package com.mdot.app.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.util.TimeUtils
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 工资单长图（01 文档 F5-2 / 04 文档 §6.4）：
 * android.graphics.Canvas 直接绘制到 Bitmap，样式随主题色传入。
 */
object PayslipRenderer {

    data class Palette(val primary: Int, val onSurface: Int, val muted: Int, val surface: Int, val line: Int)

    fun render(
        context: Context,
        title: String,
        range: CycleCalculator.Period,
        output: PayrollCalculator.Output,
        showMoney: Boolean,
        palette: Palette,
        workSystem: WorkSystem = WorkSystem.STANDARD,
    ): Bitmap {
        val width = 1080
        val pad = 64f
        val rowH = 72f
        val isHourly = workSystem == WorkSystem.HOURLY
        val isComp = workSystem == WorkSystem.COMPREHENSIVE
        val rows = buildList {
            add("考勤周期" to range.toString())
            if (isComp) {
                // 综合工时：周期口径字段（10 文档 F-Z6）
                add("周期工时合计" to TimeUtils.prettyDuration(output.otMinutes + output.holidayWorkMinutes))
                add("标准工时" to TimeUtils.prettyDuration(output.periodStandardMinutes))
                add("超时工时" to TimeUtils.prettyDuration(output.overtimeMinutes))
                add("加班费" to (if (showMoney) Money.yuanText(output.otPayCents) else "-"))
            } else {
                add((if (isHourly) "工时" else "加班时长") to TimeUtils.prettyDuration(output.otMinutes))
                add((if (isHourly) "工作收入" else "加班费") to (if (showMoney) Money.yuanText(output.otPayCents) else "-"))
            }
            add("请假时长" to TimeUtils.prettyDuration(output.leaveMinutes))
            add("请假扣款" to if (showMoney) Money.yuanText(output.leaveDeductCents) else "-")
            if (!isHourly && !isComp) {
                add("调休余额" to TimeUtils.prettyDuration(output.compBalanceMinutes))
            }
            add("净收入" to if (showMoney) Money.yuanText(output.incomeCents) else "-")
        }
        val height = (pad * 2 + 160 + rows.size * rowH + 40 + 120).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.surface)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onSurface
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.muted
            textSize = 40f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onSurface
            textSize = 40f
            textAlign = Paint.Align.RIGHT
        }
        val highlightPaint = Paint(valuePaint).apply {
            color = palette.primary
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val linePaint = Paint().apply { color = palette.line; strokeWidth = 2f }

        var y = pad + 80f
        canvas.drawText(title, width / 2f, y, titlePaint)
        y += 80f
        canvas.drawLine(pad, y, width - pad, y, linePaint)
        y += 56f

        rows.forEachIndexed { index, (label, value) ->
            val isLast = index == rows.lastIndex
            canvas.drawText(label, pad, y, labelPaint)
            canvas.drawText(
                if (showMoney && (label.contains("费") || label.contains("款") || label.contains("收入")))
                    "¥$value" else value,
                width - pad, y,
                if (isLast) highlightPaint else valuePaint,
            )
            if (isLast) {
                canvas.drawLine(pad, y + 24f, width - pad, y + 24f, linePaint)
            }
            y += rowH
        }

        val footer = "${context.getString(context.applicationInfo.labelRes)} · ${LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))} 生成"
        val footerPaint = Paint(labelPaint).apply { color = palette.muted; textAlign = Paint.Align.CENTER; textSize = 32f }
        canvas.drawText(footer, width / 2f, height - pad / 2, footerPaint)
        return bitmap
    }

    /**
     * 工地结算单长图（Phase 2）：项目名 + 周期 + 明细行 + 应结大字 + 工人/老板/日期签字栏。
     * rows = (标签 to 值) 列表，末行以 highlight 绘制。
     */
    fun renderSettlement(
        context: Context,
        projectName: String,
        range: CycleCalculator.Period,
        rows: List<Pair<String, String>>,
        palette: Palette,
    ): Bitmap {
        val width = 1080
        val pad = 64f
        val rowH = 72f
        val height = (pad * 2 + 200 + rows.size * rowH + 80 + 160 + 120).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.surface)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onSurface; textSize = 64f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.muted; textSize = 36f; textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.onSurface; textSize = 40f }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onSurface; textSize = 40f; textAlign = Paint.Align.RIGHT
        }
        val highlightPaint = Paint(valuePaint).apply { color = palette.primary; isFakeBoldText = true; textSize = 52f }
        val linePaint = Paint().apply { color = palette.line; strokeWidth = 2f }
        val signPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.muted; textSize = 36f }

        var y = pad + 64f
        canvas.drawText("工地结算单", width / 2f, y, titlePaint)
        y += 56f
        canvas.drawText("$projectName · $range", width / 2f, y, subPaint)
        y += 48f
        canvas.drawLine(pad, y, width - pad, y, linePaint)
        y += rowH
        rows.forEachIndexed { i, (label, value) ->
            val isLast = i == rows.lastIndex
            canvas.drawText(label, pad, y, labelPaint)
            canvas.drawText(value, width - pad, y, if (isLast) highlightPaint else valuePaint)
            if (!isLast) canvas.drawLine(pad, y + 24f, width - pad, y + 24f, linePaint)
            y += rowH
        }
        y += 40f
        canvas.drawLine(pad, y, width - pad, y, linePaint)
        y += 80f
        canvas.drawText("工人签字：______________    老板签字：______________", pad, y, signPaint)
        y += 64f
        canvas.drawText("日期：____ 年 __ 月 __ 日", pad, y, signPaint)
        val footerPaint = Paint(subPaint).apply { textSize = 30f }
        canvas.drawText("马的加班 · 本地记账导出", width / 2f, height - pad / 2, footerPaint)
        return bitmap
    }

    fun savePng(bitmap: Bitmap, context: Context, fileName: String): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, fileName)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }
}
