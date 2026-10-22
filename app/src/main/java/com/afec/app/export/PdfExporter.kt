package com.afec.app.export

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.afec.app.data.TrackedInstrument
import com.afec.app.domain.DateMath
import com.afec.app.notifications.NotificationHelper
import java.io.File
import java.io.FileOutputStream

/** FR-12 (PDF half): export all tracked instruments to a shareable PDF. */
object PdfExporter {

    fun writePdf(context: Context, items: List<TrackedInstrument>): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "afec-instruments.pdf")
        val doc = PdfDocument()
        val titlePaint = Paint().apply { textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val bodyPaint = Paint().apply { textSize = 11f }
        val subPaint = Paint().apply { textSize = 9f; alpha = 140 }

        val pageWidth = 595; val pageHeight = 842 // A4 @ 72dpi
        val lines = buildList {
            items.sortedBy { it.maturityDateEpoch }.forEach { item ->
                val amount = item.principalAmount?.let { "Rs. ${NotificationHelper.formatInr(it)}" } ?: "-"
                add("${item.title}  -  ${DateMath.daysLeft(item.maturityDateEpoch)} days left" to bodyPaint)
                add("    ${item.institutionName} | ${item.instrumentType.name} | $amount | Matures ${DateMath.formatDate(item.maturityDateEpoch)} | ${item.status.name}" to subPaint)
                add("" to bodyPaint)
            }
            if (items.isEmpty()) add("No instruments tracked yet." to bodyPaint)
        }

        var pageNumber = 1
        var idx = 0
        while (idx < lines.size || pageNumber == 1) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            val canvas = page.canvas
            var y = 48f
            canvas.drawText("Dues - Maturity Ledger", 40f, y, titlePaint); y += 22f
            canvas.drawText("Exported ${DateMath.formatDate(System.currentTimeMillis())} - ${items.size} instruments (on-device export)", 40f, y, subPaint); y += 28f
            while (idx < lines.size && y < pageHeight - 48f) {
                val (text, paint) = lines[idx]
                canvas.drawText(text, 40f, y, paint)
                y += 15f; idx++
            }
            doc.finishPage(page)
            pageNumber++
            if (idx >= lines.size) break
        }
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun sharePdf(context: Context, items: List<TrackedInstrument>) {
        val file = writePdf(context, items)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Dues data (PDF)").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
