package com.afec.app

import java.time.Instant

/** Real-world SMS shapes (anonymised): trailing periods, multiple spaces,
 *  "credited" vs "credit", VPA patterns, Indian digit grouping. */
object TestFixtures {

    val SWIGGY_DEBIT = """
        You have spent Rs.340.00 with SWIGGY on 26/11/25.
        Txn Ref: 1234567890. Avl Bal Rs.50,000.00.
    """.trimIndent()

    val UPI_DEBIT_VPA = """
        Rs.1,299.00 debited from a/c ****1234.
        UPI Ref: 9876543210. VPA: swiggy@okaxis.
        26-Nov-25 10:32.
    """.trimIndent()

    val CARD_SPEND = """
        Card spend Rs.1,299.00 on CREDIT CARD xx1234.
        Merchant: AMAZON. 26/11/2025.
        Avail Bal Rs.25,000.00.
    """.trimIndent()

    val REFUND = """
        Rs.499.00 has been credited to your a/c ****1234.
        Refund against order #ORD-789.
        Txn Ref: 5555555555. 24/11/2025.
    """.trimIndent()

    val BALANCE_ONLY = """
        Your current balance is Rs.50,000.00.
        Thank you for banking with us.
    """.trimIndent()

    val SALARY_CREDIT = """
        Rs.85,000.00 credited to a/c ****1234.
        Salary transfer from ABC CORP.
        01/11/2025.
    """.trimIndent()

    val CREDIT_CARD_PAYMENT = """
        Payment received Rs.5,000.00 towards
        credit card bill. Thank you for your payment.
    """.trimIndent()

    val SCHEDULED_DEBIT = """
        Your mandate of Rs.5,000.00 is scheduled
        for debit on 01/12/2025.
    """.trimIndent()

    val OTP_SMS = """
        Your OTP for UPI transaction of Rs.250.00
        is 482913. Valid for 5 minutes.
    """.trimIndent()

    val DECLINED = """
        Your UPI request of Rs.1,000.00 has been
        declined. Txn Ref: 1111111111.
    """.trimIndent()

    val INDIAN_FORMAT_AMOUNT = """
        You have spent Rs.1,23,456.78 at DMART on 20/11/25.
        Avl Bal Rs.50,000.00.
    """.trimIndent()

    val ATM_WITHDRAWAL = """
        Rs.2,000.00 withdrawn from ATM at
        HDFC ATM, MG ROAD. 25/11/2025 14:22.
    """.trimIndent()

    val SETTLED_MANDATE = """
        Rs.5,000.00 debited from a/c ****1234
        for EMI payment to HDFC CARD.
        Txn Ref: 5556667778. 05/11/2025.
    """.trimIndent()

    val VOUCHER_APPLIED = """
        You have spent Rs.500.00 at SWIGGY.
        Voucher Rs.50.00 applied.
        Net amount Rs.450.00.
        Avl Bal Rs.50,000.00. 26/11/25.
    """.trimIndent()

    fun instant(day: String): Instant = Instant.parse("${day}T12:00:00Z")
}
