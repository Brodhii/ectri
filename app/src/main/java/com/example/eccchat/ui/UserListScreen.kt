package com.example.eccchat.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.eccchat.ecc.ECCHelper
import com.example.eccchat.repository.FirebaseRepository
import java.math.BigInteger

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserListScreen(
    currentUserId: String,
    privateKeyHex: String,
    onUserSelected: (String, String) -> Unit,
    onLogout: () -> Unit
) {
    var searchInput by remember { mutableStateOf("") }
    var recentChats by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var errorMsg by remember { mutableStateOf("") }
    var isLoadingRecent by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<Map<String, Any>?>(null) }
    
    val context = LocalContext.current
    val localSentStorage = remember { context.getSharedPreferences("sent_messages", android.content.Context.MODE_PRIVATE) }
    val eccPrefs = remember { context.getSharedPreferences("ecc_prefs", android.content.Context.MODE_PRIVATE) }
    val privateKeyCreatedAt = remember(currentUserId) { eccPrefs.getLong("private_key_time_$currentUserId", 0L) }

    DisposableEffect(currentUserId) {
        val recentListener = FirebaseRepository.listenRecentChats(currentUserId) { list ->
            // Filter daftar chat agar hanya menampilkan unread count dari pesan yang dikirim SETELAH kunci dibuat
            // Namun tetap tampilkan user-nya agar user lama tetap muncul di daftar
            recentChats = list.map { chat ->
                val lastTs = chat["lastMsgTimestamp"] as? Long ?: 0L
                if (lastTs < privateKeyCreatedAt) {
                    chat.toMutableMap().apply {
                        this["unreadCount"] = 0
                        this["lastMessage"] = ""
                    }
                } else {
                    chat
                }
            }
            isLoadingRecent = false
        }
        onDispose { FirebaseRepository.removeRecentChatsListener(currentUserId, recentListener) }
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Ectri Chat", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            TextButton(onClick = onLogout) { Text("Logout") }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = searchInput,
            onValueChange = { searchInput = it; errorMsg = "" },
            placeholder = { Text("Cari User...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )

        if (errorMsg.isNotEmpty()) {
            Text(errorMsg, color = Color.Red, style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.Start))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val cleanQuery = searchInput.trim().lowercase()
                if (cleanQuery.isBlank()) { errorMsg = "Kosong"; return@Button }
                isSearching = true
                FirebaseRepository.findUserBySearch(cleanQuery) { uid, email, username ->
                    isSearching = false
                    if (uid != null) {
                        if (uid == currentUserId) errorMsg = "Diri sendiri"
                        else onUserSelected(uid, username ?: email ?: "User")
                    } else errorMsg = "Tidak ditemukan"
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isSearching
        ) {
            if (isSearching) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mulai Chat")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Pesan", modifier = Modifier.align(Alignment.Start), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        
        Spacer(modifier = Modifier.height(12.dp))

        if (isLoadingRecent) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(recentChats, key = { it["uid"].toString() }) { user ->
                    val lastEncrypted = user["lastMessage"].toString()
                    val senderId = user["lastMsgSenderId"].toString()
                    
                    // Dekripsi pesan terakhir untuk preview
                    val previewText = if (lastEncrypted.isEmpty()) "Mulai obrolan baru"
                    else if (senderId == currentUserId) {
                        val ts = user["lastMsgTimestamp"] as? Long ?: 0L
                        localSentStorage.getString("last_${user["uid"]}", null) 
                            ?: localSentStorage.getString("ts_$ts", null)
                            ?: "Obrolan Aktif"
                    } else {
                        try {
                            if (privateKeyHex.isNotEmpty()) {
                                val result = ECCHelper.decrypt(lastEncrypted, BigInteger(privateKeyHex, 16))
                                if (result != "Dekripsi gagal!") result else "Obrolan Aktif"
                            } else "Obrolan Aktif"
                        } catch (e: Exception) { 
                            "Obrolan Aktif"
                        }
                    }

                    RecentChatItem(
                        name = user["username"].toString(),
                        status = user["status"].toString(),
                        unreadCount = user["unreadCount"] as? Int ?: 0,
                        lastMessage = previewText,
                        onClick = { onUserSelected(user["uid"].toString(), user["username"].toString()) },
                        onLongClick = { userToDelete = user }
                    )
                }
            }
        }
    }

    if (userToDelete != null) {
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Hapus Obrolan?") },
            confirmButton = {
                TextButton(onClick = {
                    FirebaseRepository.deleteConversation(currentUserId, userToDelete!!["uid"].toString()) { userToDelete = null }
                }) { Text("Hapus", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { userToDelete = null }) { Text("Batal") } }
        )
    }
}

@Composable
fun RecentChatItem(
    name: String,
    status: String,
    unreadCount: Int,
    lastMessage: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                if (status == "online") {
                    Box(modifier = Modifier.size(14.dp).align(Alignment.BottomEnd).background(Color.White, CircleShape).padding(2.dp).background(Color(0xFF4CAF50), CircleShape))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = lastMessage.ifEmpty { "Mulai obrolan baru" }, 
                    style = MaterialTheme.typography.bodySmall, 
                    color = if (unreadCount > 0) MaterialTheme.colorScheme.primary else Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (unreadCount > 0) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.size(24.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(unreadCount.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
