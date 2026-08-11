package com.example.data

data class CallLogEntry(
    val id: String,
    val contact: Contact,
    val timestamp: Long,
    val callType: Int
)
