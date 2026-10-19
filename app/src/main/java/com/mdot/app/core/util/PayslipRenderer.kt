package com.mdot.app.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.mdot.app.R
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthSheet
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

    /** 长图一行：数值 + 是否金额（金额按 [showMoney] 加 ¥ 或显占位）+ 是否加粗（分组小计） */
    private data class Row(
        val label: String,
        val value: String,
        val money: Boolean = false,
        val bold: Boolean = false,
    )

    /**
     * **记月工资单长图**（docs/20 P0-1）：四分组逐行 + 组小计 + 实发。
     *
     * v0.7.9.0 起这是**唯一**的工资单长图口径（用户 2026-10-20 抹掉「记月单据 / 考勤计算」分叉：
     * 考勤本来就是记月的子集）。调用方应传 [PayMonthSheet.displayNonZero] 的结果 ——
     * **图只画有金额的项目**；需要全部行（含 0 值）的场合走 CSV，不在这里加参数。
     * 存扣款/其他组为正数，长图上也渲为负数（与记月页一致）。
     */
    fun renderSheet(
        context: Context,
        title: String,
        monthLabel: String,
        sheet: PayMonthSheet,
        palette: Palette,
    ): Bitmap {
        fun groupRows(label: String, items: List<PayMonthItem>, negative: Boolean): List<Row> =
            if (items.isEmpty()) {
                emptyList()
            } else {
                listOf(moneyRow(label, items.sumOf { it.amountCents }, negative, bold = true)) +
                    items.map { moneyRow("    ${it.name}", it.amountCents, negative) }
            }
        val rows = buildList {
            add(Row("月份", monthLabel))
            addAll(groupRows(context.getString(R.string.paymonth_group_basic), sheet.basic, negative = false))
            addAll(groupRows(context.getString(R.string.paymonth_group_subsidy), sheet.subsidy, negative = false))
            addAll(groupRows(context.getString(R.string.paymonth_group_deduction), sheet.deduction, negative = true))
            addAll(groupRows(context.getString(R.string.paymonth_group_other), sheet.other, negative = true))
            add(moneyRow(context.getString(R.string.paymonth_net_title), sheet.netCents, negative = false, bold = true))
        }
        return draw(context, title, rows, showMoney = true, palette = palette)
    }

    /** 金额行（存正数，扣款/其他组渲为负数；组小计加粗） */
    private fun moneyRow(label: String, cents: Long, negative: Boolean, bold: Boolean = false): Row =
        Row(
            label = label,
            value = if (negative) "-${Money.yuanTrimText(cents)}" else Money.yuanTrimText(cents),
            money = true,
            bold = bold,
        )

    /** 行渲染 + 页脚（两个入口共用；[rows] 末行加下划线并用主题色加粗 = 「合计」行） */
    private fun draw(
        context: Context,
        title: String,
        rows: List<Row>,
        showMoney: Boolean,
        palette: Palette,
    ): Bitmap {
        val width = 1080
        val pad = 64f
        val rowH = 72f
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
        val groupLabelPaint = Paint(labelPaint).apply {
            color = palette.onSurface
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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

        rows.forEachIndexed { index, row ->
            val isLast = index == rows.lastIndex
            canvas.drawText(row.label, pad, y, if (row.bold) groupLabelPaint else labelPaint)
            canvas.drawText(
                when {
                    row.money && !showMoney -> "-"
                    row.money -> "¥${row.value}"
                    else -> row.value
                },
                width - pad, y,
                when {
                    isLast -> highlightPaint
                    row.bold -> highlightPaint
                    else -> valuePaint
                },
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
