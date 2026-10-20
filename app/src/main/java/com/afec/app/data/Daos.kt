package com.afec.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedInstrumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TrackedInstrument)

    @Update
    suspend fun update(item: TrackedInstrument)

    @Query("SELECT * FROM tracked_instruments ORDER BY maturityDateEpoch ASC")
    fun observeAll(): Flow<List<TrackedInstrument>>

    @Query("SELECT * FROM tracked_instruments ORDER BY maturityDateEpoch ASC")
    suspend fun getAll(): List<TrackedInstrument>

    @Query("SELECT * FROM tracked_instruments WHERE id = :id")
    suspend fun getById(id: String): TrackedInstrument?

    @Query("SELECT * FROM tracked_instruments WHERE id = :id")
    fun observeById(id: String): Flow<TrackedInstrument?>

    /** Duplicate detection (PRD §12): same amount + date + institution. */
    @Query(
        "SELECT * FROM tracked_instruments WHERE status IN ('ACTIVE','OVERDUE') " +
            "AND maturityDateEpoch = :maturity AND institutionName = :institution " +
            "AND ((:amount IS NULL AND principalAmount IS NULL) OR principalAmount = :amount) LIMIT 1"
    )
    suspend fun findDuplicate(maturity: Long, institution: String, amount: Double?): TrackedInstrument?

    @Query("DELETE FROM tracked_instruments WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tracked_instruments WHERE deletedAtEpoch IS NOT NULL AND deletedAtEpoch < :cutoff")
    suspend fun purgeArchivedBefore(cutoff: Long)

    @Query("DELETE FROM tracked_instruments")
    suspend fun clearAll()
}

@Dao
interface SmsMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(msg: SmsMessage)

    @Update
    suspend fun update(msg: SmsMessage)

    /** Verification Queue (FR-09): medium-confidence parses awaiting approval. */
    @Query("SELECT * FROM sms_messages WHERE userAction = 'PENDING' ORDER BY receivedAtEpoch DESC")
    fun observePending(): Flow<List<SmsMessage>>

    @Query("SELECT * FROM sms_messages WHERE id = :id")
    suspend fun getById(id: String): SmsMessage?

    /** §11: raw SMS bodies auto-purged after 30 days. */
    @Query("DELETE FROM sms_messages WHERE receivedAtEpoch < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)

    @Query("DELETE FROM sms_messages")
    suspend fun clearAll()
}

@Dao
interface NotificationPreferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: NotificationPreference)

    @Query("SELECT * FROM notification_preferences WHERE id = :id")
    fun observe(id: String = NotificationPreference.SINGLETON_ID): Flow<NotificationPreference?>

    @Query("SELECT * FROM notification_preferences WHERE id = :id")
    suspend fun get(id: String = NotificationPreference.SINGLETON_ID): NotificationPreference?

    @Query("DELETE FROM notification_preferences")
    suspend fun clearAll()
}

@Dao
interface ParserPatternDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(patterns: List<ParserPattern>)

    @Query("SELECT * FROM parser_patterns")
    suspend fun getAll(): List<ParserPattern>

    @Query("SELECT COUNT(*) FROM parser_patterns")
    suspend fun count(): Int
}
