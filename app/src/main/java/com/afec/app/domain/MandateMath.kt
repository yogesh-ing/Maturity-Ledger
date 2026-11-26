package com.afec.app.domain

import com.afec.app.data.CardMandate
import com.afec.app.data.MandateFrequency
import com.afec.app.data.MandateStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Recurrence math for card mandates.
 *
 * Every occurrence is computed from the original anchor date (`start + n*step`),
 * never by repeatedly adding one month. Chaining would drift: a 31st mandate
 * clamped to Feb 28 would stay on the 28th forever. Computing from the anchor
 * gives 31 Jan → 28 Feb → 31 Mar, which is how banks run e-mandates.
 */
object MandateMath {

    fun toLocalDate(epoch: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()

    /** The n-th debit date from the anchor. */
    fun occurrence(anchorEpoch: Long, frequency: MandateFrequency, n: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        val anchor = toLocalDate(anchorEpoch, zone)
        return if (frequency.isFixedSchedule) anchor.plusMonths(frequency.months * n) else anchor
    }

    /** Next debit after an observed debit; "as presented" mandates keep the date. */
    fun nextAfterDebit(observedEpoch: Long, frequency: MandateFrequency, zone: ZoneId = ZoneId.systemDefault()): Long {
        if (!frequency.isFixedSchedule) return observedEpoch
        return occurrence(observedEpoch, frequency, 1, zone)
            .atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /**
     * Next debit on or after [today], or null when the mandate has already ended.
     * The anchor is normally the next debit date the user entered; if that date has
     * passed (app unused for a cycle) the recurrence rolls forward.
     */
    fun nextDebit(
        anchorEpoch: Long,
        frequency: MandateFrequency,
        today: LocalDate,
        mandateEndEpoch: Long? = null,
        zone: ZoneId = ZoneId.systemDefault()
    ): LocalDate? {
        // "As presented" has no computable recurrence — never invent a date.
        if (!frequency.isFixedSchedule) return null
        val anchor = toLocalDate(anchorEpoch, zone)
        val end = mandateEndEpoch?.let { toLocalDate(it, zone) }
        if (end != null && end.isBefore(today)) return null

        var cycles = 0L
        val monthsApart = ChronoUnit.MONTHS.between(
            anchor.withDayOfMonth(1), today.withDayOfMonth(1)
        )
        if (monthsApart > 0) cycles = monthsApart / frequency.months

        var date = occurrence(anchorEpoch, frequency, cycles, zone)
        while (date.isBefore(today)) {
            cycles++
            date = occurrence(anchorEpoch, frequency, cycles, zone)
        }
        if (end != null && date.isAfter(end)) return null
        return date
    }

    fun nextDebit(mandate: CardMandate, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
        nextDebit(mandate.nextDebitEpoch, mandate.frequency, today, mandate.mandateEndEpoch, zone)

    /** Committed spend per month; null when the amount or the schedule varies. */
    fun monthlyEquivalent(amount: Double?, frequency: MandateFrequency): Double? =
        if (amount == null || !frequency.isFixedSchedule) null else amount / frequency.months

    /** Total committed per month across the mandates that still debit. */
    fun monthlyTotal(mandates: List<CardMandate>, today: LocalDate = LocalDate.now()): Double =
        mandates.filter { it.status == MandateStatus.ACTIVE && nextDebit(it, today) != null }
            .sumOf { monthlyEquivalent(it.amount, it.frequency) ?: 0.0 }

    fun daysUntil(date: LocalDate, today: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(today, date)

    fun daysUntilDebit(mandate: CardMandate, today: LocalDate = LocalDate.now()): Long? =
        nextDebit(mandate, today)?.let { daysUntil(it, today) }

    /** 1 -> "1st", 22 -> "22nd", 11 -> "11th". */
    fun ordinal(day: Int): String {
        val suffix = when {
            day % 100 in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$day$suffix"
    }

    /** "5th of every month" — how the bank statement describes it. */
    fun scheduleLabel(mandate: CardMandate): String =
        if (!mandate.frequency.isFixedSchedule) "As presented by the merchant"
        else "${ordinal(toLocalDate(mandate.nextDebitEpoch).dayOfMonth)} of ${mandate.frequency.every}"

    fun formatRupees(amount: Double): String {
        val rounded = Math.round(amount * 100.0) / 100.0
        val whole = rounded == Math.floor(rounded)
        val format = java.text.NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = if (whole) 0 else 2
            minimumFractionDigits = 0
        }
        return "₹" + format.format(rounded)
    }
}
