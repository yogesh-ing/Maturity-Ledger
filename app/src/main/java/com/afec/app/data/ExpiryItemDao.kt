package com.afec.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.util.List

@Dao
interface ExpiryItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ExpiryItem)

    @Query("SELECT * FROM expiry_items WHERE is_active = 1 ORDER BY target_date ASC")
    suspend fun getAllActive(): List<ExpiryItem>

    @Query("SELECT * FROM expiry_items ORDER BY target_date ASC")
    fun getAllFlow(): List<ExpiryItem>

    @Query("SELECT COUNT(*) FROM expiry_items WHERE is_active = 1")
    suspend fun countActive(): Int

    @Query("DELETE FROM expiry_items")
    suspend fun clearAll()
}