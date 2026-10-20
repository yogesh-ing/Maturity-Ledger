package com.afec.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
                val appContext = context.applicationContext
                runCatching { System.loadLibrary("sqlcipher") }
                appContext.deleteDatabase("afec-database") // legacy plain DB from dev builds
                INSTANCE ?: Room.databaseBuilder(
                    appContext,
                    AFECDatabase::class.java,
                    "afec-secure-database"
                ).openHelperFactory(
                    SupportOpenHelperFactory(DatabaseKeyProvider.passphrase(appContext))
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
