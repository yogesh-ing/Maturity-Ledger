package com.afec.app.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.afec.app.data.TrackedInstrument
import com.afec.app.domain.DateMath
import java.io.File

/** FR-12: export all tracked instruments to CSV (shared via system chooser). */
object CsvExporter {
    fun buildCsv(items: List<TrackedInstrument>): String = buildString {
        appendLine("Title,Institution,Type,Principal Amount,Maturity Date,Status,Source,Confidence,Notes")
        items.forEach {
            appendLine(
                listOf(
                    it.title, it.institutionName, it.instrumentType.name,
                    it.principalAmount?.toString() ?: "",
                    DateMath.formatDate(it.maturityDateEpoch),
                    it.status.name, it.source.name,
                    it.confidenceScore?.toString() ?: "",
                    it.notes ?: ""
                ).joinToString(",") { v -> "\"${v.replace("\"", "\"\"")}\"" }
            )
        }
    }

    fun shareCsv(context: Context, items: List<TrackedInstrument>) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "afec-instruments.csv")
        file.writeText(buildCsv(items))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export AFEC data").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
