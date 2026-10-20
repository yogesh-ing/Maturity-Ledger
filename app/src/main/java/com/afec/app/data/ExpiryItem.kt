package com.afec.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expiry_items")
data class ExpiryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "target_date") val targetDate: Long, // epoch milliseconds
    @ColumnInfo(name = "principal_amount") val principalAmount: String?,
    @ColumnInfo(name = "fd_bank") val fdBank: String?,
    @ColumnInfo(name = "sms_raw") val smsRaw: String?,
    @ColumnInfo(name = "days_left") val daysLeft: Int,
    @ColumnInfo(name = "color_index") val colorIndex: Int,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)