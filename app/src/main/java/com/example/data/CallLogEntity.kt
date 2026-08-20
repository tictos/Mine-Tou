package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey val id: String,
    val contactId: Int? = null,
    val name: String,
    val phoneNumber: String,
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val callType: Int // 1: Incoming (Reçu), 2: Outgoing (Émis), 3: Missed (Manqué)
)
