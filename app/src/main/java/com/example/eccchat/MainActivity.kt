package com.example.eccchat

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import android.os.Build
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.eccchat.ecc.ECCHelper
import com.example.eccchat.repository.FirebaseRepository
import com.example.eccchat.ui.LoginScreen
import com.example.eccchat.ui.RegisterScreen
import com.example.eccchat.ui.ChatScreen
import com.example.eccchat.ui.theme.ECCChatTheme
import com.example.eccchat.ui.UserListScreen
import com.example.eccchat.model.MessageStatus
import com.google.firebase.database.ChildEventListener

class MainActivity : ComponentActivity() {

    companion object {
        var isAppInForeground = false
    }

    private var navigationState = mutableStateOf<NotificationData?>(null)

    data class NotificationData(val chatId: String, val senderId: String, val senderName: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != 
                PackageManager.PERMISSION_GRANTED) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { }.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        enableEdgeToEdge()
        setContent {
            ECCChatTheme {
                val context = LocalContext.current
                
                // Pantau status login secara real-time
                LaunchedEffect(Unit) {
                    val uid = FirebaseRepository.getCurrentUserId()
                    if (uid != null) {
                        val serviceIntent = Intent(context, NotificationService::class.java)
                        context.startService(serviceIntent)
                    }
                }

                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        val uid = FirebaseRepository.getCurrentUserId()
                        
                        // Track foreground state
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isAppInForeground = true
                        } else if (event == Lifecycle.Event.ON_PAUSE) {
                            isAppInForeground = false
                        }

                        if (uid != null) {
                            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                                FirebaseRepository.setUserPresence(uid, true)
                                FirebaseRepository.updateFcmToken(uid)
                                
                                // Jalankan Background Service untuk Notifikasi & Centang 2
                                val serviceIntent = Intent(this@MainActivity, NotificationService::class.java)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    startForegroundService(serviceIntent)
                                } else {
                                    startService(serviceIntent)
                                }
                            } else if (event == Lifecycle.Event.ON_STOP) {
                                FirebaseRepository.setUserPresence(uid, false)
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { 
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(navigationState.value) { navigationState.value = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val chatId = intent?.getStringExtra("CHAT_ID")
        val senderId = intent?.getStringExtra("SENDER_ID")
        val senderName = intent?.getStringExtra("SENDER_NAME")
        if (chatId != null && senderId != null && senderName != null) {
            navigationState.value = NotificationData(chatId, senderId, senderName)
        }
    }
}

@Composable
fun AppNavigation(notificationData: MainActivity.NotificationData?, onConsumed: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("ecc_prefs", Context.MODE_PRIVATE)

    var currentScreen by remember { mutableStateOf("login") }
    var currentUserId by remember { mutableStateOf("") }
    var otherUserId by remember { mutableStateOf("") }
    var otherUserEmail by remember { mutableStateOf("") }
    var privateKeyHex by remember { mutableStateOf("") }

    // Fungsi pembantu untuk memulihkan kunci privat jika hilang (instal ulang)
    // Sekarang: Jika kunci tidak ada di lokal, buat kunci baru dan update ke Firebase
    fun restorePrivateKeyIfNeeded(uid: String, passwordTyped: String, onDone: () -> Unit) {
        val existingKey = prefs.getString("private_key_$uid", "") ?: ""
        if (existingKey.isEmpty()) {
            val (newPrivate, newPublic) = ECCHelper.generateKeyPair()
            val privHex = newPrivate.toString(16)
            val pubStr = ECCHelper.publicKeyToString(newPublic)
            
            // Simpan lokal
            prefs.edit()
                .putString("private_key_$uid", privHex)
                .putLong("private_key_time_$uid", System.currentTimeMillis())
                .apply()
            
            // Update Public Key di Firebase agar orang lain tahu kunci kita berubah
            FirebaseRepository.updatePublicKey(uid, pubStr) {
                // Hapus waktu mulai chat lama agar sesi chat dimulai dari awal (pesan lama tersembunyi)
                // Kita akan reset waktu mulai chat untuk semua orang yang pernah dichat
                FirebaseRepository.listenRecentChats(uid) { chats ->
                    chats.forEach { chat ->
                        val otherUid = chat["uid"].toString()
                        FirebaseRepository.updateChatStartTime(uid, otherUid)
                    }
                }
                privateKeyHex = privHex
                onDone()
            }
        } else {
            privateKeyHex = existingKey
            onDone()
        }
    }

    LaunchedEffect(notificationData) {
        if (notificationData != null) {
            val uid = FirebaseRepository.getCurrentUserId()
            if (uid != null) {
                currentUserId = uid
                privateKeyHex = prefs.getString("private_key_$uid", "") ?: ""
                otherUserId = notificationData.senderId
                otherUserEmail = notificationData.senderName
                currentScreen = "chat"
                onConsumed()
            }
        }
    }

    LaunchedEffect(Unit) {
        val uid = FirebaseRepository.getCurrentUserId()
        if (uid != null) {
            currentUserId = uid
            // Langsung sinkronkan kunci saat aplikasi dibuka (Auto-Sync)
            restorePrivateKeyIfNeeded(uid, "") {
                if (notificationData == null) currentScreen = "userlist"
            }
        }
    }

    when (currentScreen) {
        "login" -> LoginScreen(
            onLoginSuccess = { password ->
                val uid = FirebaseRepository.getCurrentUserId() ?: ""
                currentUserId = uid
                restorePrivateKeyIfNeeded(uid, password) {
                    currentScreen = "userlist"
                }
            },
            onGoToRegister = { currentScreen = "register" }
        )
        "register" -> RegisterScreen(
            onRegisterSuccess = {
                val uid = FirebaseRepository.getCurrentUserId() ?: ""
                currentUserId = uid
                privateKeyHex = prefs.getString("private_key_$uid", "") ?: ""
                currentScreen = "userlist"
            },
            onGoToLogin = { currentScreen = "login" }
        )
        "chat" -> ChatScreen(
            currentUserId = currentUserId,
            otherUserId = otherUserId,
            otherUserEmail = otherUserEmail,
            privateKeyHex = privateKeyHex,
            onBack = { currentScreen = "userlist" }
        )
        "userlist" -> UserListScreen(
            currentUserId = currentUserId,
            privateKeyHex = privateKeyHex,
            onUserSelected = { uid, email ->
                otherUserId = uid
                otherUserEmail = email
                currentScreen = "chat"
            },
            onLogout = {
                val serviceIntent = Intent(context, NotificationService::class.java)
                context.stopService(serviceIntent)
                FirebaseRepository.logout()
                currentScreen = "login"
            }
        )
    }
}
