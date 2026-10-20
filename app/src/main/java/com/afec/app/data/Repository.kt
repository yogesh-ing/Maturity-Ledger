package com.afec.app.data

import android.content.Context
import com.afec.app.domain.SmsParser
import com.afec.app.notifications.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.LocalDate

/**
 * MVVM Repository (PRD §07): single source of truth over Room.
 * Also owns alarm (re)scheduling so every mutation keeps notifications correct.
 * Never logs raw SMS / amounts (PRD §11 — no sensitive logging).
 */
class AfecRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = AFECDatabase.getInstance(appContext)
    private val instruments = db.instrumentDao()
    private val sms = db.smsDao()
    private val prefs = db.preferenceDao()
    private val patterns = db.patternDao()
    private val scheduler = AlarmScheduler(appContext)

    fun observeInstruments(): Flow<List<TrackedInstrument>> = instruments.observeAll()
    fun observeInstrument(id: String): Flow<TrackedInstrument?> = instruments.observeById(id)
    fun observeQueue(): Flow<List<SmsMessage>> = sms.observePending()
    fun observePreferences(): Flow<NotificationPreference> =
        prefs.observe().map { it ?: NotificationPreference() }

    suspend fun getPreferences(): NotificationPreference = prefs.get() ?: NotificationPreference()
    suspend fun savePreferences(p: NotificationPreference) {
        prefs.upsert(p)
        rescheduleAll()
    }

    suspend fun seedPatternsIfEmpty() {
        if (patterns.count() == 0) {
            patterns.insertAll(SmsParser.KNOWN_SENDERS.keys.map {
                ParserPattern(bankSenderId = it, baseConfidenceWeight = 30)
            })
        }
    }

    /** Entry point for every parsed SMS (live receiver, historical scan, tests). */
    suspend fun ingestSms(sender: String, body: String, receivedAtEpoch: Long): IngestResult {
        val outcome = SmsParser.parse(sender, body, LocalDate.now())
        val fields = outcome.fields
        val msg = SmsMessage(
            rawBody = body,
            sender = sender,
            receivedAtEpoch = receivedAtEpoch,
            parseResult = when {
                fields == null -> ParseResult.FAILED
                outcome.confidence >= 85 -> ParseResult.SUCCESS
                else -> ParseResult.PARTIAL
            },
            extractedFields = fields?.let { extractedJson(it, outcome.confidence) },
            confidenceScore = outcome.confidence,
            userAction = if (outcome.route == SmsParser.Route.VERIFICATION_QUEUE)
                SmsUserAction.PENDING else SmsUserAction.APPROVED
        )
        sms.insert(msg)

        return when (outcome.route) {
            SmsParser.Route.AUTO_ADD -> {
                val item = upsertFromParsed(requireNotNull(fields), msg.id, outcome.confidence)
                IngestResult.AutoAdded(item)
            }
            SmsParser.Route.VERIFICATION_QUEUE -> IngestResult.Queued(msg)
            SmsParser.Route.SILENT_LOG -> IngestResult.SilentlyLogged(msg)
        }
    }

    /** Duplicate-aware insert (PRD §12): same amount+date+institution updates in place. */
    private suspend fun upsertFromParsed(
        f: SmsParser.ParsedFields, smsId: String, confidence: Int
    ): TrackedInstrument {
        val existing = instruments.findDuplicate(f.maturityDateEpoch, f.institutionName, f.principalAmount)
        val item = if (existing != null) {
            existing.copy(
                sourceSmsId = smsId, confidenceScore = confidence,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            TrackedInstrument(
                title = f.title, institutionName = f.institutionName,
                instrumentType = f.instrumentType, principalAmount = f.principalAmount,
                maturityDateEpoch = f.maturityDateEpoch,
                source = InstrumentSource.SMS_PARSED, sourceSmsId = smsId,
                confidenceScore = confidence,
                status = if (f.maturityDateEpoch < startOfToday()) InstrumentStatus.OVERDUE
                else InstrumentStatus.ACTIVE
            )
        }
        if (existing != null) instruments.update(item) else instruments.insert(item)
        scheduler.scheduleFor(item, getPreferences())
        return item
    }

    /** Queue action: Approve & Add (PRD §05 Screen 02), with any user-edited fields. */
    suspend fun approveSms(msg: SmsMessage, edited: TrackedInstrument): TrackedInstrument {
        val item = edited.copy(
            source = InstrumentSource.SMS_PARSED, sourceSmsId = msg.id,
            status = if (edited.maturityDateEpoch < startOfToday()) InstrumentStatus.OVERDUE
            else InstrumentStatus.ACTIVE
        )
        instruments.insert(item)
        sms.update(msg.copy(userAction = SmsUserAction.APPROVED, parseResult = ParseResult.SUCCESS))
        scheduler.scheduleFor(item, getPreferences())
        return item
    }

    suspend fun dismissSms(msg: SmsMessage) =
        sms.update(msg.copy(userAction = SmsUserAction.DISMISSED))

    suspend fun getSms(id: String): SmsMessage? = sms.getById(id)

    suspend fun saveInstrument(item: TrackedInstrument) {
        val stamped = item.copy(updatedAt = System.currentTimeMillis())
        if (instruments.getById(item.id) == null) instruments.insert(stamped) else instruments.update(stamped)
        scheduler.scheduleFor(stamped, getPreferences())
    }

    suspend fun markCompleted(item: TrackedInstrument) {
        val done = item.copy(status = InstrumentStatus.COMPLETED, updatedAt = System.currentTimeMillis())
        instruments.update(done)
        scheduler.cancelFor(done)
    }

    /** FR-05: one-click rollover to the next yearly cycle. */
    suspend fun renewYearly(item: TrackedInstrument) {
        val rolled = item.copy(
            maturityDateEpoch = plusOneYear(item.maturityDateEpoch),
            status = InstrumentStatus.ACTIVE, notificationsSent = emptyList(),
            deletedAtEpoch = null, updatedAt = System.currentTimeMillis()
        )
        instruments.update(rolled)
        scheduler.scheduleFor(rolled, getPreferences())
    }

    /** FR-08: soft-delete into Archived (recoverable 30 days). */
    suspend fun archive(item: TrackedInstrument) {
        val archived = item.copy(
            status = InstrumentStatus.DISMISSED, deletedAtEpoch = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        instruments.update(archived)
        scheduler.cancelFor(archived)
    }

    suspend fun restore(item: TrackedInstrument) {
        val back = item.copy(
            status = if (item.maturityDateEpoch < startOfToday()) InstrumentStatus.OVERDUE
            else InstrumentStatus.ACTIVE,
            deletedAtEpoch = null, updatedAt = System.currentTimeMillis()
        )
        instruments.update(back)
        scheduler.scheduleFor(back, getPreferences())
    }

    suspend fun deleteForever(item: TrackedInstrument) {
        scheduler.cancelFor(item)
        instruments.deleteById(item.id)
    }

    suspend fun markMilestoneSent(item: TrackedInstrument, offsetDays: Int) {
        if (offsetDays !in item.notificationsSent) {
            instruments.update(
                item.copy(notificationsSent = item.notificationsSent + offsetDays)
            )
        }
    }

    suspend fun rescheduleAll() {
        val pref = getPreferences()
        instruments.getAll()
            .filter { it.status == InstrumentStatus.ACTIVE || it.status == InstrumentStatus.OVERDUE }
            .forEach { scheduler.scheduleFor(it, pref) }
    }

    /** §11/§12 maintenance: purge raw SMS > 30d and archived items > 30d. */
    suspend fun runPurge() {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        sms.purgeOlderThan(cutoff)
        instruments.purgeArchivedBefore(cutoff)
    }

    /** §11 explicit data deletion ("Reset Local Data"). */
    suspend fun resetAllData() {
        instruments.getAll().forEach { scheduler.cancelFor(it) }
        instruments.clearAll()
        sms.clearAll()
        prefs.clearAll()
    }

    suspend fun allInstruments(): List<TrackedInstrument> = instruments.getAll()

    private fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun plusOneYear(epoch: Long): Long {
        val z = java.time.ZoneId.systemDefault()
        return java.time.Instant.ofEpochMilli(epoch).atZone(z).toLocalDate()
            .plusYears(1).atStartOfDay(z).toInstant().toEpochMilli()
    }

    sealed interface IngestResult {
        data class AutoAdded(val item: TrackedInstrument) : IngestResult
        data class Queued(val msg: SmsMessage) : IngestResult
        data class SilentlyLogged(val msg: SmsMessage) : IngestResult
    }

    companion object {
        fun extractedJson(f: SmsParser.ParsedFields, confidence: Int): String = JSONObject().apply {
            put("title", f.title)
            put("institutionName", f.institutionName)
            put("instrumentType", f.instrumentType.name)
            put("principalAmount", f.principalAmount ?: JSONObject.NULL)
            put("maturityDateEpoch", f.maturityDateEpoch)
            put("confidence", confidence)
        }.toString()

        /** Decode queue preview JSON back into a draft instrument. */
        fun draftFromSms(msg: SmsMessage): TrackedInstrument? {
            val j = runCatching { JSONObject(msg.extractedFields ?: return null) }.getOrNull() ?: return null
            return TrackedInstrument(
                title = j.optString("title", "Expiry"),
                institutionName = j.optString("institutionName", msg.sender),
                instrumentType = runCatching {
                    InstrumentType.valueOf(j.optString("instrumentType"))
                }.getOrDefault(InstrumentType.OTHER),
                principalAmount = if (j.isNull("principalAmount")) null else j.optDouble("principalAmount"),
                maturityDateEpoch = j.optLong("maturityDateEpoch"),
                source = InstrumentSource.SMS_PARSED, sourceSmsId = msg.id,
                confidenceScore = msg.confidenceScore
            )
        }
    }
}
