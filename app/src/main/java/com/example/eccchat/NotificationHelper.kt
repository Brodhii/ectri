package com.example.eccchat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.eccchat.ecc.ECCHelper
import com.example.eccchat.repository.FirebaseRepository
import java.math.BigInteger

object NotificationHelper {
    private const val CHANNEL_ID = "chat_notification_v6" 
    private const val CHANNEL_NAME = "Chat Messages"
    private const val GROUP_KEY = "com.example.eccchat.CHAT_GROUP"

    // Simpan riwayat pesan yang belum dibaca per sender
    private val unreadMessages = mutableMapOf<String, MutableList<Pair<String, Long>>>()

    private fun getTotalUnreadCount(): Int {
        var count = 0
        for (list in unreadMessages.values) {
            count += list.size
        }
        return count
    }

    fun showNotification(context: Context, senderName: String, senderId: String, chatId: String, encryptedContent: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Dekripsi pesan jika memungkinkan
        val decryptedContent = try {
            val myUid = FirebaseRepository.getCurrentUserId()
            val prefs = context.getSharedPreferences("ecc_prefs", Context.MODE_PRIVATE)
            val privateKeyHex = prefs.getString("private_key_$myUid", "") ?: ""
            
            if (privateKeyHex.isNotEmpty() && encryptedContent.isNotEmpty()) {
                val privateKey = BigInteger(privateKeyHex, 16)
                ECCHelper.decrypt(encryptedContent, privateKey)
            } else {
                "Pesan Terenkripsi"
            }
        } catch (e: Exception) {
            "Pesan Baru"
        }

        // Tambahkan ke daftar belum dibaca
        val messages = unreadMessages.getOrPut(senderId) { mutableListOf() }
        messages.add(decryptedContent to System.currentTimeMillis())

        val totalUnread = getTotalUnreadCount()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pesan masuk dari teman chat Anda"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                setSound(soundUri, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CHAT_ID", chatId)
            putExtra("SENDER_ID", senderId)
            putExtra("SENDER_NAME", senderName)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 
            senderId.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Gunakan MessagingStyle agar seperti WhatsApp
        val person = androidx.core.app.Person.Builder()
            .setName(senderName)
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(person)
        // Tambahkan SEMUA pesan yang belum dibaca ke dalam style
        for (msg in messages) {
            messagingStyle.addMessage(msg.first, msg.second, person)
        }

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setStyle(messagingStyle)
            .setNumber(totalUnread)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_KEY) // Mengelompokkan notifikasi
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        // Tampilkan notifikasi utama (per sender agar update pesan terakhir)
        notificationManager.notify(senderId.hashCode(), notificationBuilder.build())

        // Tampilkan Summary Notification (agar grup rapi di laci notifikasi)
        val summaryNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setNumber(totalUnread)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(0, summaryNotification)
    }

    // Fungsi untuk menghapus riwayat pesan saat chat dibuka
    fun clearUnreadMessages(context: Context, senderId: String) {
        unreadMessages.remove(senderId)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(senderId.hashCode())
        
        val totalUnread = getTotalUnreadCount()
        if (totalUnread > 0) {
            val summaryNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setNumber(totalUnread)
                .build()
            notificationManager.notify(0, summaryNotification)
        } else {
            notificationManager.cancel(0)
        }
    }
}
