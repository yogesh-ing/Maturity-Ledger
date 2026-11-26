package com.afec.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/*
 * PRD SS11: Room DB encrypted at rest with SQLCipher; the passphrase is wrapped
 * by an Android Keystore AES-256-GCM key (see DatabaseKeyProvider).
 * The encrypted database uses a new file name: the pre-encryption plain database
 * (dev builds only, never released) is deleted on first launch of this build.
 * No network permission is declared, so data cannot leave the device (SS09/SS11).
 */
@Database(
    entities = [
        TrackedInstrument::class,
        SmsMessage::class,
        NotificationPreference::class,
        ParserPattern::class,
        CardMandate::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AFECDatabase : RoomDatabase() {
    abstract fun instrumentDao(): TrackedInstrumentDao
    abstract fun smsDao(): SmsMessageDao
    abstract fun preferenceDao(): NotificationPreferenceDao
    abstract fun patternDao(): ParserPatternDao
    abstract fun mandateDao(): CardMandateDao

    companion object {
        /** v2 adds recurring card mandates; existing countdown data is preserved. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `card_mandates` (" +
                        "`id` TEXT NOT NULL, " +
                        "`merchantName` TEXT NOT NULL, " +
                        "`cardIssuer` TEXT NOT NULL, " +
                        "`cardLast4` TEXT NOT NULL, " +
                        "`amount` REAL, " +
                        "`frequency` TEXT NOT NULL, " +
                        "`nextDebitEpoch` INTEGER NOT NULL, " +
                        "`lastDebitEpoch` INTEGER, " +
                        "`mandateEndEpoch` INTEGER, " +
                        "`reference` TEXT, " +
                        "`status` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`sourceSmsId` TEXT, " +
                        "`notes` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        @Volatile private var INSTANCE: AFECDatabase? = null

        fun getInstance(context: Context): AFECDatabase =
            INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                runCatching { System.loadLibrary("sqlcipher") }
                appContext.deleteDatabase("afec-database") // legacy plain DB from dev builds
                INSTANCE ?: Room.databaseBuilder(
                    appContext,
                    AFECDatabase::class.java,
                    "afec-secure-database"
                ).openHelperFactory(
                    SupportOpenHelperFactory(DatabaseKeyProvider.passphrase(appContext))
                ).addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
