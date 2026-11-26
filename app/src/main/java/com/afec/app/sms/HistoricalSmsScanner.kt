package com.afec.app.sms

import android.content.Context
import android.provider.Telephony
import com.afec.app.data.AfecRepository
import com.afec.app.data.AfecRepository.IngestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.ZoneId

/** What part of the inbox an explicit scan should read. */
sealed interface SmsScanRange {
    data object All : SmsScanRange
    data class LastDays(val days: Int) : SmsScanRange
    data class Month(val year: Int, val month: Int) : SmsScanRange
}

/**
 * Counts from one scan.
 *
 * [error] is set when the inbox could not be read at all — a permission that is
 * off, or a provider that refused the query. Previously every failure was
 * swallowed into "0 found", which read to the user as "nothing in my SMS".
 */
data class SmsScanOutcome(
    val scanned: Int = 0,
    val matched: Int = 0,
    val added: Int = 0,
    val error: String? = null
)

/**
 * Reads the SMS inbox for existing instruments and mandates.
 *
 * Runs on [Dispatchers.IO]: a phone inbox can hold thousands of messages and the
 * cursor walk used to block the main thread, which made the scan look like it
 * never ran.
 */
object HistoricalSmsScanner {

    suspend fun scan(context: Context, range: SmsScanRange): SmsScanOutcome =
        withContext(Dispatchers.IO) {
            val repo = AfecRepository(context)
            val zone = ZoneId.systemDefault()
            val (selection, args) = bounds(range, zone)

            val cursor = try {
                context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    selection,
                    args,
                    "${Telephony.Sms.DATE} ASC"
                )
            } catch (e: SecurityException) {
                return@withContext SmsScanOutcome(error = "SMS permission is not granted")
            } catch (e: Exception) {
                return@withContext SmsScanOutcome(
                    error = e.message ?: e.javaClass.simpleName
                )
            } ?: return@withContext SmsScanOutcome(error = "SMS inbox could not be opened")

            var scanned = 0
            var matched = 0
            var added = 0
            try {
                cursor.use { c ->
                    val addr = c.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                    val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                    while (c.moveToNext()) {
                        val sender = if (addr >= 0) c.getString(addr) ?: continue else continue
                        val body = if (bodyIdx >= 0) c.getString(bodyIdx) ?: continue else continue
                        val date =
                            if (dateIdx >= 0) c.getLong(dateIdx) else System.currentTimeMillis()
                        scanned++
                        if (!looksFinancial(body)) continue
                        matched++
                        val result = runCatching { repo.ingestSms(sender, body, date) }
                            .getOrNull()
                        when (result) {
                            is IngestResult.AutoAdded -> added++
                            is IngestResult.MandateTracked -> if (result.created) added++
                            else -> Unit
                        }
                    }
                }
            } catch (e: Exception) {
                return@withContext SmsScanOutcome(
                    scanned = scanned,
                    matched = matched,
                    added = added,
                    error = e.message ?: e.javaClass.simpleName
                )
            }
            SmsScanOutcome(scanned = scanned, matched = matched, added = added)
        }

    private fun bounds(range: SmsScanRange, zone: ZoneId): Pair<String?, Array<String>?> =
        when (range) {
            SmsScanRange.All -> null to null
            is SmsScanRange.LastDays -> {
                val cutoff = System.currentTimeMillis() - range.days * 24L * 60 * 60 * 1000
                "${Telephony.Sms.DATE} >= ?" to arrayOf(cutoff.toString())
            }
            is SmsScanRange.Month -> {
                val month = YearMonth.of(range.year, range.month)
                val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = month.plusMonths(1).atDay(1).atStartOfDay(zone)
                    .toInstant().toEpochMilli()
                "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?" to
                    arrayOf(start.toString(), end.toString())
            }
        }

    /**
     * Cheap pre-filter mirroring FR-01 keywords; full scoring happens in ingest.
     * Mandate registrations/debits use their own vocabulary (e-mandate, Si-Hub).
     */
    private fun looksFinancial(body: String): Boolean =
        com.afec.app.domain.SmsParser.KEYWORDS.any { k -> body.contains(k, true) } ||
            com.afec.app.domain.MandateParser.looksLikeMandate(body)
}
