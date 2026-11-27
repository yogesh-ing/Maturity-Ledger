# Expense Tracking Slice — Implementation Plan

**Branch:** `feature/expense-tracking`
**Status:** Ready for implementation
**Last updated:** 2026-11-26

---

## 1. Goal

A read-only, debit-only expense view inside the existing app. The user sees **one number** (this month's net spend), **five category bars**, and a **transaction count**. Nothing else. No balance. No income. No credits. No add/edit/delete. No ledger. No calculator. Credits appear only as refunds that subtract from the spend total.

The data source is the `sms_messages` table (30-day retention). No new table, no migration, no schema change.

---

## 2. Ground Truth in Current Code

| Fact | Location |
|---|---|
| Every non-expiry SMS routes to `SILENT_LOG`; fields are null without a maturity keyword | `domain/SmsParser.kt:120` |
| `extractAmount` takes the **first** `Rs./INR/₹` match — on a debit SMS this is usually the trailing `Avl Bal` | `domain/SmsParser.kt:167` |
| Every SMS, whatever its route, is inserted into `sms_messages` | `data/Repository.kt:71` |
| Those rows are purged after 30 days | `data/Repository.kt:216` |
| `SmsMessage` PK is a random UUID with `@Insert(REPLACE)` — a re-scan **duplicates** rows | `data/Entities.kt:47`, `data/Daos.kt:50` |
| Database is version 1, no migrations, `fallbackToDestructiveMigration()` | `data/AFECDatabase.kt:24,48` |
| `ingestSms` passes `LocalDate.now()`, **not** the SMS's own `dateSent` | `data/Repository.kt:52` |

**Consequences we accept:**
- Duplicate rows exist in `sms_messages`. Dedup happens at **read time** in the aggregator, not at write time.
- The 30-day purge means the current calendar month is always (almost) fully present. On the 31st of a 31-day month, the 1st may be purged. We label the number "This Month" and add a footnote.
- `SmsParser.kt` is **untouched**. The new `TransactionParser` is an independent code path. Both coexist.

---

## 3. What Counts, What Never Does

### SPEND — money leaving an account or card

Debit, spent, paid, withdrawn, UPI/IMPS/NEFT out, ATM withdrawal, card spend, settled autopay/mandate debit.

### REFUND — money coming back for something already counted

Refund, reversed, chargeback. Subtracts from the month total. No matching to the original purchase.

### Never counted, never stored, never shown

| Category | Examples | Why excluded |
|---|---|---|
| Balance figures | `Avl Bal`, `Avail Bal`, `Available Balance`, `Bal:`, `Total Bal`, `Closing Bal`, `Opening Bal` | Reveals income / account state |
| Income credits | Salary, interest, deposit, transfer-in, cashback, cheque clearing | Income |
| Credit-card bill payments | "Payment received", "Thank you for your payment", "Bill paid" | Double-counts the card spends already recorded |
| Intent / failure | "Will be debited", "Due on", "Scheduled", "Requested", "Declined", "Failed", "Cancelled", OTPs, promo, limit changes | Not a settled transaction |
| Non-INR amounts | `USD 50.00`, `EUR 12.00` | No FX conversion in this slice |

`kind` is therefore `SPEND | REFUND`. There is no `CREDIT` enum value.

---

## 4. What We Are NOT Building (the cut list)

The only legitimate test: **"Does it change one of the three numbers the user sees?"** If no, cut it.

| Cut | Why it was in the first draft | Why it goes |
|---|---|---|
| `Transaction` entity, DAO, `MIGRATION_1_2`, version bump | Persist spends | Current month is always inside the 30-day SMS retention. Compute from `sms_messages`. No schema change, no migration, no destructive-fallback risk. |
| `dedupeKey` hashing, reference/RRN extraction | Survive re-scans | Natural key `(sender, kind, amountMinor, occurredOn)` dedupes at read time. No stored key, no hash-contract test. |
| Refund matching to original purchase, 90-day window | Attribute refunds | Refunds just subtract from the month. Matching has no visible output. |
| Time-window collapse for two SMS per payment | One payment, two messages | Fuzzy matching costs more than the small overcount it prevents. |
| EMI detection | One purchase, thirteen debits | Rare enough to accept. Needs the purchase matching just cut. |
| Confidence scoring + "uncertain" bucket | Trust signal | The parser is a filter: understood or null. |
| Learned / sticky categories, `rawMerchant`, reference fields | Better categorisation later | A fixed keyword map is enough for five bars. |
| Category count > 7 | Detail | An overview wants five bars. |

**Kept** (each protects a number the user sees):
- Balance-token rejection at the parser level
- Income and bill-payment exclusion
- Settled-vs-intent filter
- `SPEND | REFUND` split
- Integer minor units (paise), never `Double`
- SMS timestamp as the transaction date
- Purity: `TransactionParser` and `MonthlySpend` are pure Kotlin + `java.time`, no Android imports, testable as JVM unit tests

---

## 5. Resolved Decisions

| Decision | Resolution | Rationale |
|---|---|---|
| Time window | **Calendar month.** Footnote in UI: *"May exclude transactions from the 1st."* | "Last 30 days" changes daily and is more confusing than a 1-day edge. |
| Wallet / PPI loads | **Ignore the load, count the payment.** | The load is topping up a wallet; the payment is the actual spend. |
| Categories | **8 in the enum. 5 bars: top 4 by amount + "Other" (merged remainder).** | 8 categories is enough for a coarse split. 5 bars is the right visual density. |
| Refunds | **Subtract from the window. No matching to original.** | No visible output from matching. |
| Date source | **Fix `ingestSms` to use `SmsMessage.dateSent`. Parser prefers a date parsed from the SMS body; falls back to the stored `receivedAt`.** | Fixes the `LocalDate.now()` bug. No migration needed (column already exists). |
| Amount extraction | **All-tokens + context-rejection algorithm** (see §7). | Fixes the "first match is the balance" bug. |

---

## 6. File Tree

```
domain/
  TransactionParser.kt          ← NEW  (enum + data class + parser object)
  MonthlySpend.kt               ← NEW  (aggregator + MonthlyTotal data class)

data/
  Repository.kt                 ← MODIFY  (add getSpendForMonth, fix dateSent)
  Daos.kt                       ← MODIFY  (add since(cutoff) to SmsMessageDao)
  Entities.kt                   ← MODIFY  (add Index("date") to sms_messages)

ui/expense/
  ExpenseSummaryFragment.kt     ← NEW
  ExpenseSummaryViewModel.kt    ← NEW

res/layout/
  fragment_expense_summary.xml  ← NEW

test/
  TransactionParserTest.kt      ← NEW
  MonthlySpendTest.kt           ← NEW
  TestFixtures.kt               ← NEW  (real anonymised SMS strings)
```

No new entity. No new DAO. No migration. No version bump.

---

## 7. `domain/TransactionParser.kt`

Pure Kotlin. `java.time` only. No Android imports. No I/O.

```kotlin
package domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class TxnKind { SPEND, REFUND }

enum class SpendCategory {
    FOOD, GROCERY, TRANSPORT, BILLS, SUBSCRIPTION, SHOPPING, CASH, OTHER
}

data class Spend(
    val amountMinor: Long,          // paise; never Double
    val kind: TxnKind,
    val occurredOn: LocalDate,      // from SMS body date, fallback to receivedAt
    val merchant: String?,
    val category: SpendCategory
)

object TransactionParser {

    /**
     * Returns a Spend if the SMS represents a settled debit or refund.
     * Returns null for: balance-only messages, income, bill payments,
     * intents, failures, OTPs, non-INR amounts, or any unparseable input.
     *
     * Contract: this function MUST NEVER THROW.
     * Any malformed input → null.
     */
    fun parse(
        sender: String,
        body: String,
        receivedAt: Instant,
        zone: ZoneId = ZoneId.systemDefault()
    ): Spend?
}
```

### 7.1 Parsing Algorithm (step by step)

```
parse(sender, body, receivedAt, zone):

  1. NORMALISE
     - Trim body. Replace non-breaking spaces with regular spaces.
     - If body is empty → null.

  2. EXTRACT ALL AMOUNT TOKENS
     - Regex: (?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)
     - Find ALL matches, not just the first.
     - For each match, record:
         value:  the numeric string, commas stripped → Long in paise
         start:  character index of the match in body
         end:    character index after the match
     - If zero matches → null.

  3. REJECT BALANCE TOKENS
     - For each token, examine the 30 characters BEFORE `start`.
     - If that window matches (case-insensitive):
         "Avl Bal" | "Avail Bal" | "Available Balance" |
         "Bal:" | "Total Bal" | "Closing Bal" | "Opening Bal" |
         "Current Bal" | "Current Balance" | "Balance is" |
         "balance of"
       → discard the token.
     - If ALL tokens are discarded → null (balance-only message).

  4. CLASSIFY DIRECTION
     - Search body (case-insensitive) for REFUND keywords:
         "refund" | "reversed" | "chargeback" | "reversal"
     - Search body for SPEND keywords:
         "spent" | "debited" | "debit" | "paid" | "withdrawn" |
         "UPI" | "IMPS" | "NEFT" | "RTGS" | "ATM" |
         "card" | "mandate" | "EMI"
     - If REFUND keywords present and no SPEND keywords → kind = REFUND.
     - If SPEND keywords present and no REFUND keywords → kind = SPEND.
     - If BOTH present → null (ambiguous; don't guess).
     - If NEITHER present → null.

  5. EXCLUDE INCOME / BILL-PAYMENT / INTENT
     - If body matches (case-insensitive):
         "salary" | "interest credited" | "deposit" | "transfer in" |
         "cashback" | "cheque" | "payment received" |
         "thank you for your payment" | "bill paid" |
         "will be debited" | "scheduled" | "due on" |
         "requested" | "declined" | "failed" | "cancelled" |
         "OTP" | "promo" | "limit"
       → null.

  6. SELECT THE AMOUNT
     - From the surviving (non-balance) tokens:
       a. If exactly one → use it.
       b. If multiple → pick the token whose `start` is closest to
          the SPEND/REFUND keyword found in step 4.
       c. If still ambiguous (two tokens equidistant) → null.
     - Convert to paise: strip commas, parse as BigDecimal,
       multiply by 100, toLong.
     - If the result is ≤ 0 → null.

  7. EXTRACT DATE
     - Try to parse a date from the body:
         dd/MM/yyyy  |  dd-MM-yy  |  dd MMM yyyy  |  dd-MMM-yyyy
     - If found → occurredOn = that date.
     - If not found → occurredOn = receivedAt.atZone(zone).toLocalDate().

  8. EXTRACT MERCHANT (best-effort)
     - Look for a proper-noun token (≥ 2 capitalised words, or a
       known VPA pattern `xxx@yyy`) near the amount.
     - If found → merchant = that string (trimmed, max 40 chars).
     - If not found → merchant = null.

  9. ASSIGN CATEGORY
     - Fixed keyword map (case-insensitive, checked against
       merchant + body):
         FOOD:          swiggy, zomato, domino, pizza, burger,
                        restaurant, cafe, bar, hotel, food
         GROCERY:       big basket, blinkit, zepto, grofers,
                        dmart, grocery, kirana, supermarket
         TRANSPORT:     uber, ola, rapido, metro, bus, train,
                        flight, indigo, air india, fuel, petrol,
                        diesel, highway, toll
         BILLS:         electricity, water, gas, broadband,
                        internet, phone bill, mobile recharge,
                        rent, property tax, insurance premium
         SUBSCRIPTION:  spotify, netflix, prime, hotstar,
                        subscription, monthly, annual, plan
         SHOPPING:      amazon, flipkart, myntra, ajio,
                        shopping, store, mall
         CASH:          ATM, cash, withdrawal, cashback
         OTHER:         (default)
     - First match wins. No match → OTHER.

 10. RETURN Spend(amountMinor, kind, occurredOn, merchant, category)
```

### 7.2 Category Keyword Map (initial, server-updatable later)

```kotlin
private val CATEGORY_KEYWORDS: Map<SpendCategory, List<String>> = mapOf(
    SpendCategory.FOOD to listOf(
        "swiggy","zomato","domino","pizza","burger","restaurant",
        "cafe","bar","hotel","food","dine","kitchen"
    ),
    SpendCategory.GROCERY to listOf(
        "big basket","blinkit","zepto","grofers","dmart","grocery",
        "kirana","supermarket","fresh","vegetable"
    ),
    SpendCategory.TRANSPORT to listOf(
        "uber","ola","rapido","metro","bus","train","flight",
        "indigo","air india","fuel","petrol","diesel","highway","toll"
    ),
    SpendCategory.BILLS to listOf(
        "electricity","water","gas","broadband","internet","phone",
        "bill","mobile recharge","rent","property tax","insurance"
    ),
    SpendCategory.SUBSCRIPTION to listOf(
        "spotify","netflix","prime","hotstar","subscription",
        "monthly","annual","plan","youtube"
    ),
    SpendCategory.SHOPPING to listOf(
        "amazon","flipkart","myntra","ajio","shopping","store","mall"
    ),
    SpendCategory.CASH to listOf(
        "atm","cash","withdrawal","cashback"
    )
)
// No entry → OTHER
```

---

## 8. `domain/MonthlySpend.kt`

Pure Kotlin. No Android imports.

```kotlin
package domain

import java.time.YearMonth

data class CategoryTotal(
    val category: SpendCategory,
    val totalMinor: Long,
    val count: Int
)

data class MonthlyTotal(
    val spentMinor: Long,                      // SPEND − REFUND, ≥ 0
    val categoryTotals: List<CategoryTotal>,   // ≤ 5, sorted desc by totalMinor
    val transactionCount: Int                  // count of SPEND entries only
)

object MonthlySpend {

    /**
     * Deduplicates on (sender, kind, amountMinor, occurredOn).
     * Net = sum(SPEND) − sum(REFUND), floored at 0.
     * Categories: top 4 by totalMinor get their own entry.
     * All remaining categories merge into a single OTHER entry.
     * If fewer than 4 categories have data, show only those.
     */
    fun summarize(
        spends: List<Spend>,
        month: YearMonth
    ): MonthlyTotal
}
```

### 8.1 Aggregation Algorithm

```
summarize(spends, month):

  1. FILTER to the month:
     occurredOn.year == month.year AND occurredOn.monthValue == month.monthValue

  2. DEDUPE:
     Group by (sender, kind, amountMinor, occurredOn).
     Keep one entry per group. (Duplicates from @Insert(REPLACE)
     with random UUIDs are collapsed here.)

  3. SPLIT:
     spendEntries  = entries where kind == SPEND
     refundEntries = entries where kind == REFUND

  4. COMPUTE:
     totalSpendMinor  = spendEntries.sumOf { it.amountMinor }
     totalRefundMinor = refundEntries.sumOf { it.amountMinor }
     spentMinor       = max(0, totalSpendMinor − totalRefundMinor)
     transactionCount = spendEntries.size

  5. CATEGORY TOTALS (from spendEntries only):
     Group by category. Sum amountMinor per category.
     Sort desc by totalMinor.
     Take top 4.
     If any categories remain beyond the top 4:
       merge their totals + counts into a single OTHER entry.
     If OTHER is already in the top 4, merge into it.
     Result: ≤ 5 CategoryTotal entries.

  6. RETURN MonthlyTotal(spentMinor, categoryTotals, transactionCount)
```

---

## 9. `data/` Changes

### 9.1 `Daos.kt` — add one method

```kotlin
@Dao
interface SmsMessageDao {
    // ... existing methods ...

    @Query("SELECT * FROM sms_messages WHERE date >= :cutoff")
    fun since(cutoff: LocalDate): List<SmsMessage>
}
```

### 9.2 `Entities.kt` — add index (no-op migration)

```kotlin
@Entity(
    tableName = "sms_messages",
    indices = [Index("date")]   // ← add this
)
data class SmsMessage( ... )    // unchanged
```

Adding an index to an existing table is additive. Room handles it in `onOpen` without a version bump. No data change. No destructive fallback triggered.

### 9.3 `Repository.kt` — two changes

**Fix the date bug (line ~52):**

```kotlin
// BEFORE:
val date = LocalDate.now()

// AFTER:
val date = smsMessage.dateSent
    .let { if (it > 0) Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
           else LocalDate.now() }
```

**Add the expense query method:**

```kotlin
suspend fun getSpendForMonth(month: YearMonth): MonthlyTotal {
    val cutoff = month.atDay(1)
    val nextMonthStart = month.plusMonths(1).atDay(1)
    return withContext(Dispatchers.IO) {
        val rows = smsMessageDao.since(cutoff)
            .filter { it.date < nextMonthStart }
        val spends = rows.mapNotNull { row ->
            TransactionParser.parse(
                sender = row.sender,
                body = row.body,
                receivedAt = row.date.atStartOfDay(zone).toInstant()
            )
        }
        MonthlySpend.summarize(spends, month)
    }
}
```

---

## 10. UI Layer

### 10.1 `ExpenseSummaryViewModel.kt`

```kotlin
class ExpenseSummaryViewModel(
    private val repo: SmsRepository
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Ready(val total: MonthlyTotal, val month: YearMonth) : UiState
        data object Empty : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            val month = YearMonth.now()
            val total = repo.getSpendForMonth(month)
            _state.value = if (total.transactionCount == 0 && total.spentMinor == 0L)
                UiState.Empty
            else
                UiState.Ready(total, month)
        }
    }
}
```

No caching. Every `load()` re-queries and re-parses. At ≤ 200 SMS in 30 days this is < 10 ms. A cache would add invalidation complexity for no user-visible benefit.

### 10.2 `fragment_expense_summary.xml` (or Compose equivalent)

Layout structure:

```
┌─────────────────────────────────────────────┐
│  Expenses                        [Nov 2026] │  ← header (month label, no picker)
├─────────────────────────────────────────────┤
│                                             │
│     ₹ 4,320                                 │  ← big number (spentMinor)
│     This month · 14 transactions            │  ← sub-label
│     *May exclude transactions from the 1st  │  ← footnote (small, grey)
│                                             │
├─────────────────────────────────────────────┤
│  Food & Dining    ████████████░░  ₹1,200   │
│  Transport        ██████░░░░░░░░  ₹850     │  ← 5 horizontal bars
│  Bills            █████░░░░░░░░░  ₹600     │     (top 4 + Other)
│  Shopping         ████████░░░░░░  ₹1,100   │
│  Other            ███░░░░░░░░░░░  ₹570     │
│                                             │
├─────────────────────────────────────────────┤
│  Refunds & Vouchers                         │
│  • Swiggy refund          +₹50  (12 Jun)   │  ← collapsible, default collapsed
│  • Amazon voucher         +₹200 (05 Jun)   │     shows count + total only
└─────────────────────────────────────────────┘
```

**Rules:**
- No "Add" button. No "Edit". No "Delete". No swipe-to-delete. No long-press menu.
- No balance anywhere. No "Available". No "Credit".
- The big number is **net spend** (SPEND − REFUND), never a balance.
- The month label is static (`YearMonth.now()`). No month picker in this slice.
- Empty state: *"No expenses detected this month. Make sure SMS permission is enabled."* + a button to open app settings.
- Loading state: skeleton rows (number placeholder + 5 grey bar placeholders).
- Dark mode: match existing app theme.
- Accessibility: `contentDescription` on the big number ("Total spent this month: four thousand three hundred twenty rupees"). Bars have `contentDescription` per category.

### 10.3 Wiring

- Add the fragment to the existing navigation graph (bottom nav / drawer / wherever the other tabs live).
- Tab icon: a simple receipt or arrow-down icon.
- No deep link needed in this slice.

---

## 11. Tests

All tests are **JVM unit tests** (no Robolectric, no Android). They run in the existing `testDebugUnitTest` task.

### 11.1 `TestFixtures.kt`

```kotlin
package test

import java.time.Instant
import java.time.ZoneOffset

object TestFixtures {

    // Real-world shapes (anonymised). Trailing periods, multiple spaces,
    // "credited" vs "credit", VPA patterns.

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

    fun instant(day: String): Instant =
        Instant.parse("$dayT12:00:00Z")
}
```

### 11.2 `TransactionParserTest.kt`

```kotlin
class TransactionParserTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    // ── Amount extraction ──────────────────────────────

    @Test fun `debit with trailing Avl Bal returns the debit not the balance`() {
        val spend = TransactionParser.parse(
            "HDFCBANK", TestFixtures.SWIGGY_DEBIT,
            TestFixtures.instant("2025-11-26"), zone
        )
        assertNotNull(spend)
        assertEquals(34_000, spend!!.amountMinor)   // ₹340.00
        assertEquals(TxnKind.SPEND, spend.kind)
    }

    @Test fun `balance-only message returns null`() {
        assertNull(TransactionParser.parse(
            "HDFCBANK", TestFixtures.BALANCE_ONLY,
            TestFixtures.instant("2025-11-26"), zone
        ))
    }

    @Test fun `Indian-format amount 1,23,456.78 parses to 12345678 paise`() {
        val spend = TransactionParser.parse(
            "DMART", TestFixtures.INDIAN_FORMAT_AMOUNT,
            TestFixtures.instant("2025-11-20"), zone
        )
        assertNotNull(spend)
        assertEquals(12_345_678, spend!!.amountMinor)
    }

    @Test fun `voucher applied returns the gross spend not the net`() {
        val spend = TransactionParser.parse(
            "SWIGGY", TestFixtures.VOUCHER_APPLIED,
            TestFixtures.instant("2025-11-26"), zone
        )
        assertNotNull(spend)
        assertEquals(50_000, spend!!.amountMinor)   // ₹500 gross
    }

    // ── Direction ──────────────────────────────────────

    @Test fun `UPI debit with VPA is SPEND`() {
        val spend = TransactionParser.parse(
            "OKICICI", TestFixtures.UPI_DEBIT_VPA,
            TestFixtures.instant("2025-11-26"), zone
        )
        assertNotNull(spend)
        assertEquals(TxnKind.SPEND, spend!!.kind)
        assertEquals(SpendCategory.FOOD, spend.category)
    }

    @Test fun `card spend is SPEND`() {
        val spend = TransactionParser.parse(
            "HDFCCARD", TestFixtures.CARD_SPEND,
            TestFixtures.instant("2025-11-26"), zone
        )
        assertNotNull(spend)
        assertEquals(TxnKind.SPEND, spend!!.kind)
    }

    @Test fun `refund is REFUND`() {
        val spend = TransactionParser.parse(
            "AMAZON", TestFixtures.REFUND,
            TestFixtures.instant("2025-11-24"), zone
        )
        assertNotNull(spend)
        assertEquals(TxnKind.REFUND, spend!!.kind)
        assertEquals(49_900, spend.amountMinor)
    }

    @Test fun `ATM withdrawal is SPEND CASH`() {
        val spend = TransactionParser.parse(
            "HDFCBANK", TestFixtures.ATM_WITHDRAWAL,
            TestFixtures.instant("2025-11-25"), zone
        )
        assertNotNull(spend)
        assertEquals(TxnKind.SPEND, spend!!.kind)
        assertEquals(SpendCategory.CASH, spend.category)
    }

    // ── Exclusions ─────────────────────────────────────

    @Test fun `salary credit returns null`() {
        assertNull(TransactionParser.parse(
            "SALARY", TestFixtures.SALARY_CREDIT,
            TestFixtures.instant("2025-11-01"), zone
        ))
    }

    @Test fun `credit card bill payment returns null`() {
        assertNull(TransactionParser.parse(
            "HDFCCARD", TestFixtures.CREDIT_CARD_PAYMENT,
            TestFixtures.instant("2025-11-26"), zone
        ))
    }

    @Test fun `scheduled mandate returns null`() {
        assertNull(TransactionParser.parse(
            "HDFCBANK", TestFixtures.SCHEDULED_DEBIT,
            TestFixtures.instant("2025-11-26"), zone
        ))
    }

    @Test fun `OTP returns null`() {
        assertNull(TransactionParser.parse(
            "OKICICI", TestFixtures.OTP_SMS,
            TestFixtures.instant("2025-11-26"), zone
        ))
    }

    @Test fun `declined transaction returns null`() {
        assertNull(TransactionParser.parse(
            "OKICICI", TestFixtures.DECLINED,
            TestFixtures.instant("2025-11-26"), zone
        ))
    }

    @Test fun `settled mandate is SPEND`() {
        val spend = TransactionParser.parse(
            "HDFCBANK", TestFixtures.SETTLED_MANDATE,
            TestFixtures.instant("2025-11-05"), zone
        )
        assertNotNull(spend)
        assertEquals(TxnKind.SPEND, spend!!.kind)
        assertEquals(500_000, spend.amountMinor)
    }

    // ── Date ───────────────────────────────────────────

    @Test fun `date from SMS body is used not receivedAt`() {
        val spend = TransactionParser.parse(
            "HDFCBANK", TestFixtures.SWIGGY_DEBIT,
            TestFixtures.instant("2025-12-01"),  // receivedAt is wrong
            zone
        )
        assertNotNull(spend)
        assertEquals(LocalDate.of(2025, 11, 26), spend!!.occurredOn)
    }

    @Test fun `receivedAt in 2025 gives occurredOn in 2025 not today`() {
        val body = "You have spent Rs.100.00 at CAFE. 15/06/2025."
        val spend = TransactionParser.parse(
            "Cafe", body,
            TestFixtures.instant("2025-06-15"), zone
        )
        assertNotNull(spend)
        assertEquals(2025, spend!!.occurredOn.year)
    }

    // ── Safety ─────────────────────────────────────────

    @Test fun `empty body returns null`() {
        assertNull(TransactionParser.parse("X", "", Instant.now(), zone))
    }

    @Test fun `no amount token returns null`() {
        assertNull(TransactionParser.parse(
            "X", "Thank you for banking with us.",
            Instant.now(), zone
        ))
    }

    @Test fun `non-INR currency returns null`() {
        val body = "USD 50.00 spent at GLOBAL STORE. 26/11/2025."
        assertNull(TransactionParser.parse("X", body, Instant.now(), zone))
    }
}
```

### 11.3 `MonthlySpendTest.kt`

```kotlin
class MonthlySpendTest {

    private val month = YearMonth.of(2025, 11)

    @Test fun `spend plus refund in one month are netted`() {
        val spends = listOf(
            Spend(100_000, TxnKind.SPEND, LocalDate.of(2025,11,5), "A", SpendCategory.FOOD),
            Spend(50_000,  TxnKind.REFUND, LocalDate.of(2025,11,10), "A", SpendCategory.FOOD)
        )
        val total = MonthlySpend.summarize(spends, month)
        assertEquals(50_000, total.spentMinor)
        assertEquals(1, total.transactionCount)  // only SPEND counted
    }

    @Test fun `same SMS present twice is counted once`() {
        val s = Spend(100_000, TxnKind.SPEND, LocalDate.of(2025,11,5), "A", SpendCategory.FOOD)
        val total = MonthlySpend.summarize(listOf(s, s.copy()), month)
        assertEquals(100_000, total.spentMinor)
        assertEquals(1, total.transactionCount)
    }

    @Test fun `message from another month is excluded`() {
        val spends = listOf(
            Spend(100_000, TxnKind.SPEND, LocalDate.of(2025,12,1), "A", SpendCategory.FOOD),
            Spend(50_000,  TxnKind.SPEND, LocalDate.of(2025,11,15), "B", SpendCategory.CASH)
        )
        val total = MonthlySpend.summarize(spends, month)
        assertEquals(50_000, total.spentMinor)
        assertEquals(1, total.transactionCount)
    }

    @Test fun `more than 4 categories merge into Other`() {
        val spends = listOf(
            Spend(100_000, TxnKind.SPEND, LocalDate.of(2025,11,1), "A", SpendCategory.FOOD),
            Spend(80_000,  TxnKind.SPEND, LocalDate.of(2025,11,2), "B", SpendCategory.TRANSPORT),
            Spend(60_000,  TxnKind.SPEND, LocalDate.of(2025,11,3), "C", SpendCategory.BILLS),
            Spend(40_000,  TxnKind.SPEND, LocalDate.of(2025,11,4), "D", SpendCategory.SHOPPING),
            Spend(30_000,  TxnKind.SPEND, LocalDate.of(2025,11,5), "E", SpendCategory.SUBSCRIPTION),
            Spend(20_000,  TxnKind.SPEND, LocalDate.of(2025,11,6), "F", SpendCategory.GROCERY)
        )
        val total = MonthlySpend.summarize(spends, month)
        assertEquals(5, total.categoryTotals.size)  // 4 + Other
        val other = total.categoryTotals.last()
        assertEquals(SpendCategory.OTHER, other.category)
        assertEquals(50_000, other.totalMinor)      // 30k + 20k
        assertEquals(2, other.count)
    }

    @Test fun `fewer than 4 categories shows only those`() {
        val spends = listOf(
            Spend(100_000, TxnKind.SPEND, LocalDate.of(2025,11,1), "A", SpendCategory.FOOD),
            Spend(50_000,  TxnKind.SPEND, LocalDate.of(2025,11,2), "B", SpendCategory.CASH)
        )
        val total = MonthlySpend.summarize(spends, month)
        assertEquals(2, total.categoryTotals.size)
    }

    @Test fun `refund exceeds spend floors at zero`() {
        val spends = listOf(
            Spend(10_000, TxnKind.SPEND,  LocalDate.of(2025,11,1), "A", SpendCategory.FOOD),
            Spend(20_000, TxnKind.REFUND, LocalDate.of(2025,11,2), "A", SpendCategory.FOOD)
        )
        val total = MonthlySpend.summarize(spends, month)
        assertEquals(0L, total.spentMinor)
    }

    @Test fun `empty list returns zero total`() {
        val total = MonthlySpend.summarize(emptyList(), month)
        assertEquals(0L, total.spentMinor)
        assertEquals(0, total.transactionCount)
        assertTrue(total.categoryTotals.isEmpty())
    }
}
```

---

## 12. Edge Cases

| Scenario | Handling |
|---|---|
| SMS in a non-bank format / unrecognised sender | Parser returns null. Silently ignored. |
| Amount with Indian commas (`1,23,456.78`) | Stripped before parse. Test covers it. |
| Multiple non-balance amounts in one SMS | Pick the one closest to the spend/refund keyword. If equidistant → null. |
| Two SMS for one payment (e.g., debit + confirmation) | Both parsed as SPEND. Small overcount. Accepted. |
| Refund amount ≠ original (partial refund) | Subtracts its own amount. No matching needed. |
| Gift voucher applied at checkout (reduces debit) | The gross debit is recorded. The voucher is not a separate entry. (The `VOUCHER_APPLIED` test covers this.) |
| SMS arrives while app is in background | Processed on next foreground (existing `ingestSms` flow). |
| User deletes SMS from phone before app reads it | Accepted. Documented. No recovery. |
| Duplicate SMS (carrier retry) | Deduped at read time in `MonthlySpend.summarize`. |
| 31st of a 31-day month, 1st is purged | Footnote in UI. Accepted. |
| `dateSent` is 0 (some OEMs don't set it) | Fallback to `LocalDate.now()` in `ingestSms`. Parser then falls back to body-parsed date. |
| Empty `sms_messages` table (new install) | UI shows empty state. |
| SMS permission revoked | Existing app flow handles it. Expense tab shows empty state with "Enable SMS access" button. |

---

## 13. Privacy

- SMS bodies (including financial transaction text) are stored **locally** in `sms_messages` and **purged after 30 days**.
- No SMS body, parsed amount, merchant name, or category is sent to any remote server, analytics SDK, or crash-reporting pipeline.
- If the app uses Firebase Analytics or Bugsnag: verify that the `sms_messages` table and `TransactionParser` output are **not** in any logged field. Add a `@DoNotLog` annotation or scrub in the analytics layer.
- The expense tab does not require any new permission. It uses the existing `READ_SMS` permission already granted for the app's SMS-reading feature.

---

## 14. ProGuard / R8

```proguard
# Keep enum values if they are ever serialised or referenced by name
-keep class domain.TxnKind { *; }
-keep class domain.SpendCategory { *; }
-keep class domain.Spend { *; }
-keep class domain.MonthlyTotal { *; }
-keep class domain.CategoryTotal { *; }
```

String constants in the keyword maps are safe from inlining, but the keep rules are cheap insurance.

---

## 15. CI / Verification

- CI runs `testDebugUnitTest` on `main` and `feature/**` (`.github/workflows/build-apk.yml`), pinned to Gradle 8.7.
- The slice is **done** when:
  1. All tests in `TransactionParserTest` pass.
  2. All tests in `MonthlySpendTest` pass.
  3. Existing `SmsParserTest` still passes (no regression in the untouched parser).
  4. The app builds and the expense tab renders (manual smoke test on one device).
  5. No new lint warnings.

---

## 16. Definition of Done

- [ ] User opens the expense tab → sees one number (net spend), five category bars, a transaction count, and a collapsible refunds section.
- [ ] A new bank debit SMS arrives → appears in the total within the next app foreground.
- [ ] A refund SMS arrives → subtracts from the total. Shown in the refunds section.
- [ ] No UI element anywhere displays a balance, credit amount, or income figure.
- [ ] No add / edit / delete affordance exists in the UI or in the data layer.
- [ ] Revoking SMS permission → graceful empty state, no crash.
- [ ] All financial data stays on-device. Purged after 30 days.
- [ ] `SmsParser.kt` is unmodified. `SmsParserTest` passes.
- [ ] No database migration. No version bump. No new entity. No new DAO.
- [ ] `TransactionParser` and `MonthlySpend` are pure Kotlin, testable without Android.
- [ ] CI is green.

---

## 17. Out of Scope (explicitly deferred)

- Month picker / historical months
- Per-transaction list (only the aggregate is shown)
- Manual add / edit / delete
- EMI detection
- Refund-to-purchase matching
- Learned / sticky categories
- Cloud sync
- CSV / PDF export
- Push notification on new expense
- Multi-currency / FX conversion
- Credit-card statement import
- Bank API integration (SMS is the only source)

These may be future slices. They are **not** in this branch.