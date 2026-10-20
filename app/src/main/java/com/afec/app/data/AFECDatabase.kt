package com.afec.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/*
 * PRD §11 requires encryption at rest (SQLCipher / Keystore AES-256-GCM).
 * Phase 1 ships the plain Room database so the build stays dependency-light and
 * reproducible; wiring SQLCipher is a one-line `.openHelperFactory(...)` change here
 * and is tracked in IMPLEMENTATION_STATUS.md. No INTERNET permission is declared,
 * so data cannot leave the device (PRD §09/§11 zero-network guarantee).
 */
@Database(
    entities = [
        TrackedInstrument::class,
        SmsMessage::class,
        NotificationPreference::class,
        ParserPattern::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AFECDatabase : RoomDatabase() {
    abstract fun instrumentDao(): TrackedInstrumentDao
    abstract fun smsDao(): SmsMessageDao
    abstract fun preferenceDao(): NotificationPreferenceDao
    abstract fun patternDao(): ParserPatternDao

    companion object {
        @Volatile private var INSTANCE: AFECDatabase? = null

        fun getInstance(context: Context): AFECDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AFECDatabase::class.java,
                    "afec-database"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
