package com.example.eccchat

import android.os.PowerManager
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.eccchat.repository.FirebaseRepository
import com.example.eccchat.model.MessageStatus
import com.google.firebase.auth.FirebaseAuth

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM", "Pesan diterima dari: ${remoteMessage.from}")
        
        // Gunakan WakeLock agar CPU tidak tidur saat proses update status di database
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ECCChat:FCMWakeLock")
        wakeLock.acquire(3000) // Tahan CPU selama 3 detik
        
        try {
            val data = remoteMessage.data
            if (data.isNotEmpty()) {
                processMessage(data)
            } else {
                // Jika payload masuk sebagai "notification" murni (untuk beberapa HP)
                remoteMessage.notification?.let {
                    Log.d("FCM", "Payload notification ditemukan: ${it.title}")
                    // Jika data kosong tapi ada notification, kita tetap tampilkan default
                    // Namun idealnya data harus ada untuk status delivered
                }
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private fun processMessage(data: Map<String, String>) {
        val senderId = data["senderId"] ?: ""
        val chatId = data["chatId"] ?: ""
        val senderName = data["senderName"] ?: "Pesan Baru"
        val messageId = data["messageId"] ?: ""
        val content = data["content"] ?: ""
        
        // Mark as DELIVERED segera setelah data diterima oleh HP (walau di background)
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        
        if (messageId.isNotEmpty() && myUid != null) {
            Log.d("FCM", "Mengubah status pesan $messageId menjadi DELIVERED")
            FirebaseRepository.updateMessageStatus(myUid, senderId, messageId, MessageStatus.DELIVERED)
        }

        // Notifikasi muncul jika aplikasi di background/mengambang ATAU sedang tidak di room tersebut
        if (!MainActivity.isAppInForeground || chatId != ChatSession.activeChatId) {
            NotificationHelper.showNotification(applicationContext, senderName, senderId, chatId, content)
        }
    }

    override fun onNewToken(token: String) {
        FirebaseRepository.getCurrentUserId()?.let { uid ->
            FirebaseRepository.updateFcmToken(uid)
        }
    }
}
