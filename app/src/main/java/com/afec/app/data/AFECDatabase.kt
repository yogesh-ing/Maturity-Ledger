package com.afec.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ExpiryItem::class], version = 1, exportSchemas = false)
abstract class AFECDatabase : RoomDatabase() {
    abstract fun expiryItemDao(): ExpiryItemDao

    companion object {
        @Volatile
        private var INSTANCE: AFECDatabase? = null

        fun getInstance(context: Context): AFECDatabase {
            synchronized(this) {
                INSTANCE?.let { return it }
                val appContext = context.applicationContext
                INSTANCE = Room.databaseBuilder(
                    appContext,
                    AFECDatabase::class.java,
                    "afec-database"
                ).build()
            }
            return INSTANCE!!
        }

        fun destroyInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}