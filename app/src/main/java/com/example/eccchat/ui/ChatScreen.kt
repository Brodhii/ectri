package com.example.eccchat.ui

import androidx.activity.compose.BackHandler
import com.example.eccchat.MainActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.eccchat.ChatSession
import com.example.eccchat.NotificationHelper
import com.example.eccchat.ecc.ECCHelper
import com.example.eccchat.model.Message
import com.example.eccchat.model.MessageStatus
import com.example.eccchat.repository.FirebaseRepository
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUserId: String,
    otherUserId: String,
    otherUserEmail: String,
    privateKeyHex: String,
    onBack: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<Pair<Message, String>>()) }
    var otherPublicKey by remember { mutableStateOf("") }
    var isLoadingKey by remember { mutableStateOf(true) }
    var inspectMessage by remember { mutableStateOf<Pair<Message, String>?>(null) }
    var messageOptions by remember { mutableStateOf<Message?>(null) }
    
    var otherUserStatus by remember { mutableStateOf("offline") }
    var otherUserLastSeen by remember { mutableLongStateOf(0L) }

    val context = LocalContext.current
    val localSentStorage = remember { context.getSharedPreferences("sent_messages", android.content.Context.MODE_PRIVATE) }
    val eccPrefs = remember { context.getSharedPreferences("ecc_prefs", android.content.Context.MODE_PRIVATE) }
    val privateKeyCreatedAt = remember(currentUserId) { eccPrefs.getLong("private_key_time_$currentUserId", 0L) }
    
    val messageIds = remember { mutableStateOf(setOf<String>()) }
    var isInitialLoad by remember { mutableStateOf(true) }
    
    val listState = rememberLazyListState()
    val roomId = remember { FirebaseRepository.getRoomId(currentUserId, otherUserId) }

    var chatStartedAt by remember { mutableLongStateOf(0L) }
    var isReadyToListen by remember { mutableStateOf(false) }

    LaunchedEffect(otherUserId) {
        FirebaseRepository.getChatStartTime(currentUserId, otherUserId) { startTime ->
            // Jika ini chat pertama kali (setelah reinstall), kita set startTime-nya sekarang
            if (startTime == 0L) {
                val now = System.currentTimeMillis()
                FirebaseRepository.updateChatStartTime(currentUserId, otherUserId)
                chatStartedAt = now
            } else {
                chatStartedAt = startTime
            }
            isReadyToListen = true
        }
    }

    BackHandler { onBack() }

    DisposableEffect(otherUserId, isReadyToListen) {
        if (!isReadyToListen) return@DisposableEffect onDispose {}

        ChatSession.activeChatId = roomId
        NotificationHelper.clearUnreadMessages(context, otherUserId)
        FirebaseRepository.resetUnreadCount(currentUserId, otherUserId) // Reset angka unread
        
        val presenceListener = FirebaseRepository.listenUserPresence(otherUserId) { status, lastSeen ->
            otherUserStatus = status
            otherUserLastSeen = lastSeen
        }

        // Sekarang kita mendengarkan perubahan kunci secara real-time
        val keyListener = FirebaseRepository.listenPublicKey(otherUserId) { key ->
            if (key != null && key != otherPublicKey) {
                otherPublicKey = key
            }
            isLoadingKey = false
        }
        
        val messageListener = FirebaseRepository.listenMessages(
            currentUserId = currentUserId, 
            otherUserId = otherUserId,
            startAtTimestamp = chatStartedAt,
            onNewMessage = { msg ->
                // Mark as READ if it's for us and we're in the chat AND app is in foreground
                if (msg.receiverId == currentUserId && msg.status != MessageStatus.READ) {
                    if (MainActivity.isAppInForeground) {
                        FirebaseRepository.updateMessageStatus(currentUserId, otherUserId, msg.id, MessageStatus.READ)
                    } else if (msg.status == MessageStatus.SENT) {
                        // Jika background/mengambang, cukup set ke DELIVERED dulu
                        FirebaseRepository.updateMessageStatus(currentUserId, otherUserId, msg.id, MessageStatus.DELIVERED)
                    }
                }

                val decrypted: String? = if (msg.senderId == currentUserId) {
                    localSentStorage.getString(msg.id, null)
                        ?: localSentStorage.getString("ts_${msg.timestamp}", null)
                        ?: "Pesan Terkirim (E2EE)"
                } else {
                    try {
                        if (privateKeyHex.isNotEmpty()) {
                            val privateKey = BigInteger(privateKeyHex, 16)
                            val result = ECCHelper.decrypt(msg.encryptedContent, privateKey)
                            if (result != "Dekripsi gagal!") result else null
                        } else null
                    } catch (e: Exception) { null }
                }

                // Dengan startAt di database, kita tidak perlu filter manual lagi di sini
                // Pesan yang muncul sudah pasti pesan dari sesi ini.
                val finalDisplayMessage = decrypted ?: "🔄 Menunggu sinkronisasi kunci keamanan..."
                
                val existingIndex = messages.indexOfFirst { it.first.id == msg.id }
                if (existingIndex != -1) {
                    val newMessages = messages.toMutableList()
                    newMessages[existingIndex] = Pair(msg, finalDisplayMessage)
                    messages = newMessages
                } else {
                    messageIds.value = messageIds.value + msg.id
                    messages = messages + Pair(msg, finalDisplayMessage)
                }
            },
            onMessageRemoved = { removedId ->
                messageIds.value = messageIds.value - removedId
                messages = messages.filter { it.first.id != removedId }
            }
        )

        onDispose {
            ChatSession.activeChatId = null
            FirebaseRepository.removeUserPresenceListener(otherUserId, presenceListener)
            FirebaseRepository.removeMessageListener(currentUserId, otherUserId, messageListener)
            FirebaseRepository.removePublicKeyListener(otherUserId, keyListener)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && isInitialLoad) {
            isInitialLoad = false
        }
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                otherUserEmail.take(1).uppercase(),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(otherUserEmail, 
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val statusText = if (otherUserStatus == "online") "Online" 
                                            else if (otherUserLastSeen > 0) "Terakhir terlihat ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(otherUserLastSeen))}"
                                            else "Offline"
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (otherUserStatus == "online") {
                                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF4CAF50), CircleShape))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(statusText, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
        ) {
            if (privateKeyHex.isEmpty()) {
                Surface(
                    color = Color(0xFFFFEBEE),
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "⚠️ Kunci Privat tidak ditemukan di perangkat ini. Pesan lama tidak dapat dibaca.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB71C1C),
                        textAlign = TextAlign.Center
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(16.dp)
            ) {
                items(messages, key = { it.first.id }) { (msg, decrypted) ->
                    val isMine = msg.senderId == currentUserId
                    MessageBubble(
                        text = decrypted,
                        timestamp = msg.timestamp,
                        status = msg.status,
                        isMine = isMine,
                        onLongClick = { messageOptions = msg }
                    )
                }
            }

            Surface(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Ketik pesan...") },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            disabledPlaceholderColor = Color.Gray
                        )
                    )
                    
                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank() && otherPublicKey.isNotBlank()) {
                                val pubKey = ECCHelper.stringToPublicKey(otherPublicKey)
                                if (pubKey != null) {
                                    val encrypted = ECCHelper.encrypt(messageText, pubKey)
                                    if (encrypted != "ERROR_ENCRYPT") {
                                        val originalText = messageText
                                        val timestamp = System.currentTimeMillis()
                                        
                                        // Simpan ke storage LOKAL dulu dengan kunci timestamp agar langsung bisa didekripsi saat listener terpanggil
                                        localSentStorage.edit().putString("ts_$timestamp", originalText).apply()
                                        
                                        val msg = Message(
                                            senderId = currentUserId,
                                            receiverId = otherUserId,
                                            encryptedContent = encrypted,
                                            timestamp = timestamp
                                        )
                                        
                                        FirebaseRepository.sendMessage(msg,
                                            onSuccess = { sentMsg ->
                                                localSentStorage.edit()
                                                    .putString(sentMsg.id, originalText)
                                                    .putString("last_${otherUserId}", originalText)
                                                    .apply()
                                                messageText = "" 
                                            },
                                            onError = { }
                                        )
                                    }
                                }
                            }
                        },
                        enabled = otherPublicKey.isNotEmpty() && messageText.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim", 
                            tint = if (messageText.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray)
                    }
                }
            }
        }
    }

    messageOptions?.let { msg ->
        val decrypted = messages.find { it.first.id == msg.id }?.second ?: ""
        AlertDialog(
            onDismissRequest = { messageOptions = null },
            title = { Text("Opsi Pesan") },
            text = { Text("Pilih tindakan untuk pesan ini.") },
            confirmButton = {
                TextButton(onClick = { 
                    FirebaseRepository.deleteMessage(currentUserId, otherUserId, msg.id)
                    messageOptions = null
                }) {
                    Text("Hapus", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    inspectMessage = Pair(msg, decrypted)
                    messageOptions = null
                }) { Text("Inspect") }
            }
        )
    }

    inspectMessage?.let { (msg, decrypted) ->
        InspectDialog(
            msg = msg,
            decrypted = decrypted,
            privateKey = privateKeyHex,
            publicKey = otherPublicKey,
            onDismiss = { inspectMessage = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    text: String,
    timestamp: Long,
    status: String,
    isMine: Boolean,
    onLongClick: () -> Unit
) {
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 0.dp,
                bottomEnd = if (isMine) 0.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isMine) MaterialTheme.colorScheme.primary else Color(0xFFE0E0E0)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.combinedClickable(onClick = { }, onLongClick = onLongClick)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = text, 
                    color = if (isMine) Color.White else Color.Black,
                    fontSize = 16.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                ) {
                    Text(
                        text = timeStr, 
                        style = MaterialTheme.typography.labelSmall, 
                        color = (if (isMine) Color.White else Color.Black).copy(alpha = 0.6f)
                    )
                    if (isMine) {
                        Spacer(modifier = Modifier.width(4.dp))
                        val icon = when (status) {
                            MessageStatus.READ -> Icons.Default.DoneAll
                            MessageStatus.DELIVERED -> Icons.Default.DoneAll
                            else -> Icons.Default.Done
                        }
                        // Warna ungu untuk READ, putih/abu untuk lainnya
                        val tint = if (status == MessageStatus.READ) Color(0xFFBB86FC) else Color.White.copy(alpha = 0.7f)
                        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
                    }
                }
            }
        }
    }
}

@Composable
fun InspectDialog(
    msg: Message,
    decrypted: String,
    privateKey: String,
    publicKey: String,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔐 Inspect Encryption") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InspectItem("Pesan Asli", decrypted)
                InspectItem(
                    label = "Encrypted Content", 
                    value = msg.encryptedContent,
                    onCopy = { clipboardManager.setText(AnnotatedString(msg.encryptedContent)) }
                )
                InspectItem(
                    label = "Other Public Key", 
                    value = if (publicKey.isNotEmpty()) publicKey else "Belum tersedia",
                    onCopy = { if (publicKey.isNotEmpty()) clipboardManager.setText(AnnotatedString(publicKey)) }
                )
                if (privateKey.isNotEmpty()) {
                    InspectItem(
                        label = "Your Private Key", 
                        value = privateKey,
                        onCopy = { clipboardManager.setText(AnnotatedString(privateKey)) }
                    )
                }
                InspectItem("Timestamp", Date(msg.timestamp).toString())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
fun InspectItem(label: String, value: String, onCopy: (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (onCopy != null) {
                IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
