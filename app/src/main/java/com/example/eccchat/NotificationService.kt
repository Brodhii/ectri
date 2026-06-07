package com.example.eccchat

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.eccchat.model.MessageStatus
import com.example.eccchat.repository.FirebaseRepository
import com.google.firebase.database.ChildEventListener

class NotificationService : Service() {
    private var notificationListener: ChildEventListener? = null
    private var currentUserId: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uid = FirebaseRepository.getCurrentUserId()
        if (uid != null && uid != currentUserId) {
            currentUserId = uid
            startForegroundService()
            startListening(uid)
        } else if (uid != null) {
            // Re-ensure foreground service is running with notification
            startForegroundService()
        }
        return START_STICKY 
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Pemicu agar service tetap hidup/restart saat aplikasi di-swipe tutup dari Task Manager
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
        super.onTaskRemoved(rootIntent)
    }

    private fun startForegroundService() {
        val channelId = "chat_service_channel"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Chat Background Service",
                android.app.NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("ECCChat Berjalan")
            .setContentText("Memantau pesan baru...")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
        
        startForeground(1001, notification)
    }

    private fun startListening(uid: String) {
        notificationListener?.let { FirebaseRepository.removeNotificationListener(it) }
        
        notificationListener = FirebaseRepository.listenNotificationEvents(uid) { name, sId, cId, mId, content ->
            // BUG FIX: Selalu update ke DELIVERED agar pengirim dapat centang 2 
            FirebaseRepository.updateMessageStatus(uid, sId, mId, MessageStatus.DELIVERED)

            // Jika aplikasi di background atau mengambang (ON_PAUSE), tetap munculkan notifikasi
            // Jika aplikasi di foreground, hanya munculkan jika pesan dari chat lain
            if (!MainActivity.isAppInForeground || cId != ChatSession.activeChatId) {
                NotificationHelper.showNotification(applicationContext, name, sId, cId, content)
            }
        }
    }

    override fun onDestroy() {
        notificationListener?.let { FirebaseRepository.removeNotificationListener(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
