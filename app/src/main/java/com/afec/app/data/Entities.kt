package com.afec.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/*
 * Data model — PRD §07 "Data Model — Room Entities".
 * Four core tables: TrackedInstrument, SmsMessage, NotificationPreference, ParserPattern.
 * Field names/types follow the HTML PRD schema; `deletedAtEpoch` is the only addition,
 * required by FR-08 (soft-delete with 30-day recovery window).
 */

enum class InstrumentType { FD, RD, CARD, SUBSCRIPTION, OTHER }
enum class InstrumentSource { SMS_PARSED, MANUAL_ENTRY }
enum class InstrumentStatus { ACTIVE, COMPLETED, DISMISSED, OVERDUE }
enum class ParseResult { SUCCESS, PARTIAL, FAILED }
enum class SmsUserAction { PENDING, APPROVED, DISMISSED }

@Entity(tableName = "tracked_instruments")
data class TrackedInstrument(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,                                   // "HDFC FD Withdrawal"
    val institutionName: String,                         // "HDFC Bank"
    val instrumentType: InstrumentType = InstrumentType.OTHER,
    val principalAmount: Double? = null,
    val maturityDateEpoch: Long,                         // start-of-day, local time
    val source: InstrumentSource = InstrumentSource.MANUAL_ENTRY,
    val sourceSmsId: String? = null,                     // FK -> SmsMessage.id
    val status: InstrumentStatus = InstrumentStatus.ACTIVE,
    val confidenceScore: Int? = null,                    // 0-100, null if manual
    /** Per-item custom alert offsets in days, e.g. [14,7] (FR-10). */
    val alertOverrides: List<Int> = emptyList(),
    /** When true, [alertOverrides] replaces the global cascade; otherwise inherit global. */
    val useCustomAlerts: Boolean = false,
    val notes: String? = null,
    /** Which milestone offsets already fired, e.g. [14,7] (PRD: notificationsSent JSON). */
    val notificationsSent: List<Int> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Soft-delete timestamp (FR-08); null while not archived. */
    val deletedAtEpoch: Long? = null
)

@Entity(tableName = "sms_messages")
data class SmsMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val rawBody: String,
    val sender: String,
    val receivedAtEpoch: Long,
    val parseResult: ParseResult = ParseResult.FAILED,
    /** JSON of extracted fields (org.json), for the Verification Queue preview. */
    val extractedFields: String? = null,
    val confidenceScore: Int = 0,
    val userAction: SmsUserAction = SmsUserAction.PENDING
)

@Entity(tableName = "notification_preferences")
data class NotificationPreference(
    @PrimaryKey val id: String = SINGLETON_ID,
    val alert14Days: Boolean = true,
    val alert7Days: Boolean = true,
    val alert3Days: Boolean = true,
    val alertDayOf: Boolean = true,
    val customOffsets: List<Int> = emptyList()           // e.g. [21, 45]
) {
    /** All enabled global offsets, descending. Day-of is offset 0. */
    fun enabledOffsets(): List<Int> = buildList {
        if (alert14Days) add(14)
        if (alert7Days) add(7)
        if (alert3Days) add(3)
        addAll(customOffsets)
        if (alertDayOf) add(0)
    }.distinct().sortedDescending()

    companion object { const val SINGLETON_ID = "global" }
}

@Entity(tableName = "parser_patterns")
data class ParserPattern(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bankSenderId: String,                            // "HDFCBK", "SBIINB"
    val keywordSet: List<String> =
        listOf("matures", "maturity", "expiry", "value date", "lock-in"),
    val dateRegexTemplate: String = "",
    val baseConfidenceWeight: Int = 30
)
