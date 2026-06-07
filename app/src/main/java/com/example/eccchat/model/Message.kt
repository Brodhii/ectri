package com.example.eccchat.model

data class Message(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val encryptedContent: String = "",
    val timestamp: Long = 0L,
    val status: String = "SENT"
)

object MessageStatus {
    const val SENT = "SENT"
    const val DELIVERED = "DELIVERED"
    const val READ = "READ"
}
