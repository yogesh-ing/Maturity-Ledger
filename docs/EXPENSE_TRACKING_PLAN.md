# Expense tracking — plan (spend-only overview)

Branch: `feature/expense-tracking`. Status: **awaiting review, no implementation started.**

Goal: a high-level view of **money out** only — this month's spend, a coarse category
split, and nothing else. Not a calculator, not a ledger. Credits are ignored unless they
are a refund, and available-balance figures must never appear anywhere.

## 1. Ground truth in the current code

| Fact | Where |
|---|---|
| Every non-expiry SMS routes to `SILENT_LOG`; `fields` is null without a maturity keyword *and* a date | `domain/SmsParser.kt:120` |
| `extractAmount` takes the **first** `Rs./INR/₹` match — on a debit that is usually the balance | `domain/SmsParser.kt:167` |
| Every SMS (whatever its route) is inserted into `sms_messages` | `data/Repository.kt:71` |
| Those rows are purged after 30 days | `data/Repository.kt:216` |
| `SmsMessage` PK is a random UUID with `@Insert(REPLACE)` — a re-scan duplicates rows | `data/Entities.kt:47`, `data/Daos.kt:50` |
| Database is version 1, no migrations, `fallbackToDestructiveMigration()` | `data/AFECDatabase.kt:24,48` |
| `ingestSms` passes `LocalDate.now()`, not the SMS's own date | `data/Repository.kt:52` |

## 2. What counts, and what never does

**SPEND:** money leaving an account or card — `debited`, `spent`, `paid`, `withdrawn`,
UPI/IMPS/NEFT out, ATM withdrawal, card spend, a settled autopay/mandate debit.

**REFUND:** money coming back for something already counted — `refund`, `reversed`,
`chargeback`.

**Never counted, never stored, never shown:**

- **Balance figures** — `Avl Bal`, `Avail Bal`, `Available Balance`, `Bal:`, `Total Bal`.
  Refused at the source: if no amount survives once balance candidates are dropped, the
  message yields nothing. No downstream filter, no hidden rows.
- **Income credits** — salary, interest, deposit, transfer-in, cashback, cheque clearing.
- **Credit-card bill payments** — `payment received`, `thank you for your payment`,
  `bill paid`. Counting these double-counts the card spends already recorded.
- **Intent and failure** — `will be debited`, `due on`, `scheduled`, `requested`,
  `declined`, `failed`, `cancelled`, OTPs, promo, limit changes.

`kind` is therefore `SPEND | REFUND` — `CREDIT` exists only as a refund.

## 3. Challenge: most of the earlier machinery is over-built

The legitimate test for every feature here is "does it change one of three numbers?". Most
of what the first draft proposed does not. **Cut:**

| Cut | Why it was there | Why it goes |
|---|---|---|
| `Transaction` entity, DAO, `MIGRATION_1_2`, version bump | Persist spends | The current month is always inside the 30-day SMS retention. Compute it from `sms_messages`; no schema change, no migration, no destructive-fallback risk, and no `resetAllData()` change |
| `dedupeKey` hashing, reference/RRN extraction | Survive re-scans | A natural key — `sender + kind + amountMinor + day` — dedupe in memory. No stored key, no hash-contract test |
| Refund **matching** to the original purchase, 90-day window | Attribute refunds | Refunds just subtract from the month. Matching is a feature with no visible output here |
| Time-window collapse for two SMS per payment | One payment, two messages | Fuzzy matching costs more than the small overcount it prevents |
| EMI detection | One purchase, thirteen debits | Rare enough to accept, and needs purchase-level matching we just cut |
| Confidence scoring + "uncertain" bucket | Trust signal | The parser is a filter: understood or `null`. The trust signal becomes copy ("counted from SMS") |
| Learned/sticky categories, `rawMerchant`, `reference` fields | Better categorisation later | A fixed keyword map is enough for a handful of bars |
| Category count > 7 | Detail | Overview wants five bars |

**Kept, because each one protects a number the user will see:** the balance refusal, income
and bill-payment exclusion, settled-vs-intent, the `SPEND | REFUND` split, integer minor
units, the SMS timestamp as the date, and purity so it runs as a JVM test.

Known edge accepted with the no-table design: on the **31st** of a 31-day month the purge
cutoff reaches the 1st, so a few early-month messages can drop out. Alternative, if that
matters: label the number "last 30 days" instead of "this month".

## 4. The slice

1. `domain/TransactionParser.kt` — pure Kotlin + `java.time`, no Android imports.

```kotlin
enum class TxnKind { SPEND, REFUND }
enum class SpendCategory { FOOD, GROCERY, TRANSPORT, BILLS, SUBSCRIPTION, SHOPPING, CASH, OTHER }

data class Spend(
    val amountMinor: Long,        // paise; never Double
    val kind: TxnKind,
    val occurredOn: LocalDate,    // from the SMS timestamp
    val merchant: String?,
    val category: SpendCategory
)

object TransactionParser {
    fun parse(sender: String, body: String, receivedAt: Instant,
              zone: ZoneId = ZoneId.systemDefault()): Spend?   // null = not a settled spend/refund
}
```

2. `domain/MonthlySpend.kt` — pure aggregator:
   `summarize(spends: List<Spend>, month: YearMonth): MonthlyTotal` →
   `spentMinor` (`SPEND` − `REFUND`), up to five category totals, count; dedupes on the
   natural key.
3. `SmsMessageDao.since(cutoff)` + one repository method that reads and maps on
   `Dispatchers.IO`.
4. Tests next to the existing JVM-only ones.

No entity, no DAO for spends, no migration.

## 5. Tests

`TransactionParserTest`:

1. debit with trailing `Avl Bal Rs.50,000` → the debit, not the balance
2. balance-only `Avl Bal Rs.50,000` → `null`
3. `Rs.85,000 credited ... salary` → `null`, not a refund
4. `credit card payment received ... Rs.5,000` → `null`
5. UPI debit with `VPA swiggy@okaxis` → `FOOD`
6. card spend `Rs.1,299 ... Credit Card xx1234` → `SPEND`
7. refund `Rs.499 credited ... refund against order` → `REFUND`
8. OTP, `will be debited on 05/12`, `declined` → `null`
9. `Rs.1,23,456.78` → `12345678` minor units exactly
10. `receivedAt` in 2025 → `occurredOn` is 2025, not today

`MonthlySpendTest`:

11. spend + refund in one month → netted
12. the same SMS present twice → counted once
13. a message from another month → excluded

## 6. Decisions

Defaults in brackets; say "go" and I use them.

1. **Window** — calendar month, accepting the 31st edge [default], or "last 30 days".
2. **Wallet/PPI loads** — ignore the load, count the payment [default], or the reverse.
3. **Categories** — the seven above [default]; learning later.

Refunds are settled by the cut: they subtract from the window, no matching.

## 7. Verification

CI now runs `testDebugUnitTest` on `main` and `feature/**` (`.github/workflows/build-apk.yml`)
using the pinned Gradle 8.7, since the repo ships no `gradlew`. The slice is done when the
tests above pass there and `SmsParserTest` still passes.
