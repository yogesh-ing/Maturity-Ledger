package com.afec.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/*
 * Recurring card mandates (e-mandates / standing instructions on credit cards).
 *
 * Deliberately a separate table from TrackedInstrument: a mandate never "matures"
 * or completes, it repeats, and its useful summary is a monthly committed spend.
 * Folding it into the countdown model would corrupt urgency tiers and counts.
 */

enum class MandateFrequency(val label: String, val months: Int) {
    MONTHLY("Monthly", 1),
    QUARTERLY("Quarterly", 3),
    HALF_YEARLY("Half-yearly", 6),
    YEARLY("Yearly", 12),

    /**
     * SBI Card (and others) register mandates with "Frequency: As Presented":
     * the merchant decides each debit, so there is no computable recurrence.
     * months == 0 marks that; the app shows the last known debit instead of
     * inventing a schedule.
     */
    AS_PRESENTED("As presented", 0);

    /** "every month", "every quarter", … */
    val every: String get() = when (this) {
        MONTHLY -> "every month"
        QUARTERLY -> "every quarter"
        HALF_YEARLY -> "every 6 months"
        YEARLY -> "every year"
        AS_PRESENTED -> "as presented by the merchant"
    }

    val isFixedSchedule: Boolean get() = months > 0
}

enum class MandateStatus { ACTIVE, PAUSED, REVOKED }

@Entity(tableName = "card_mandates")
data class CardMandate(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** Merchant or purpose, e.g. "Netflix", "LIC premium". */
    val merchantName: String,
    /** Issuer of the card the mandate sits on, e.g. "HDFC Bank". */
    val cardIssuer: String,
    /** Last 4 digits; blank when unknown. */
    val cardLast4: String = "",
    /** Null when the debit amount varies (e.g. utility bill). */
    val amount: Double? = null,
    val frequency: MandateFrequency = MandateFrequency.MONTHLY,
    /** The next known debit date (start-of-day epoch millis, local zone). */
    val nextDebitEpoch: Long,
    /** Last debit actually seen (from a debit SMS); null when unknown. */
    val lastDebitEpoch: Long? = null,
    /** Mandate validity end; null means "till cancelled". */
    val mandateEndEpoch: Long? = null,
    /** UMN / SI reference from the bank SMS, when known. */
    val reference: String? = null,
    val status: MandateStatus = MandateStatus.ACTIVE,
    val source: InstrumentSource = InstrumentSource.MANUAL_ENTRY,
    val sourceSmsId: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** "HDFC Bank ••1234" / "HDFC Bank" when the last 4 are unknown. */
    val cardLabel: String
        get() = if (cardLast4.isBlank()) cardIssuer else "$cardIssuer ••$cardLast4"
}
