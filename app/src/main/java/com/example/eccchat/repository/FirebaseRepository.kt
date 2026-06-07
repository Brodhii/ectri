package com.example.eccchat.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.example.eccchat.model.Message
import com.example.eccchat.model.MessageStatus
import com.google.firebase.database.ServerValue
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ChildEventListener

object FirebaseRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance()

    fun register(email: String, username: String, password: String, publicKey: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                
                val userData = mapOf(
                    "email" to email,
                    "username" to username,
                    "publicKey" to publicKey,
                    "status" to "online",
                    "lastSeen" to ServerValue.TIMESTAMP
                )
                
                db.reference.child("users").child(uid).updateChildren(userData)
                    .addOnSuccessListener {
                        updateFcmToken(uid)
                        onSuccess()
                    }
                    .addOnFailureListener { onError(it.message ?: "Simpan data gagal") }
            }
            .addOnFailureListener { onError(it.message ?: "Register gagal") }
    }

    fun login(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { 
                auth.currentUser?.uid?.let { 
                    updateFcmToken(it)
                    setUserPresence(it, true)
                }
                onSuccess() 
            }
            .addOnFailureListener { onError(it.message ?: "Login gagal") }
    }

    fun setUserPresence(uid: String, isOnline: Boolean) {
        val userRef = db.reference.child("users").child(uid)
        if (isOnline) {
            userRef.child("status").setValue("online")
            userRef.child("lastSeen").setValue(ServerValue.TIMESTAMP)
            userRef.child("status").onDisconnect().setValue("offline")
            userRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)
        } else {
            userRef.child("status").setValue("offline")
            userRef.child("lastSeen").setValue(ServerValue.TIMESTAMP)
        }
    }

    fun listenUserPresence(uid: String, onResult: (String, Long) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").value as? String ?: "offline"
                val lastSeen = snapshot.child("lastSeen").value as? Long ?: 0L
                onResult(status, lastSeen)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.reference.child("users").child(uid).addValueEventListener(listener)
        return listener
    }

    fun removeUserPresenceListener(uid: String, listener: ValueEventListener) {
        db.reference.child("users").child(uid).removeEventListener(listener)
    }

    fun updatePublicKey(uid: String, publicKey: String, onComplete: () -> Unit) {
        db.reference.child("users").child(uid).child("publicKey").setValue(publicKey)
            .addOnCompleteListener { onComplete() }
    }

    fun removePublicKeyListener(userId: String, listener: ValueEventListener) {
        db.reference.child("users").child(userId).child("publicKey").removeEventListener(listener)
    }

    fun getPublicKey(userId: String, onResult: (String?) -> Unit) {
        db.reference.child("users").child(userId).child("publicKey").get()
            .addOnSuccessListener { onResult(it.value as? String) }
            .addOnFailureListener { onResult(null) }
    }

    fun listenPublicKey(userId: String, onResult: (String?) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onResult(snapshot.value as? String)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.reference.child("users").child(userId).child("publicKey").addValueEventListener(listener)
        return listener
    }

    fun checkUsernameExists(username: String, onResult: (Boolean) -> Unit) {
        db.reference.child("users").orderByChild("username").equalTo(username)
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.exists() && snapshot.childrenCount > 0)
            }
            .addOnFailureListener { onResult(false) }
    }

    fun findUserBySearch(query: String, onResult: (String?, String?, String?) -> Unit) {
        val cleanQuery = query.trim().lowercase()
        db.reference.child("users").get().addOnSuccessListener { snapshot ->
            var foundUid: String? = null
            var foundEmail: String? = null
            var foundUsername: String? = null
            for (child in snapshot.children) {
                val email = (child.child("email").value as? String ?: "").lowercase()
                val username = (child.child("username").value as? String ?: "").lowercase()
                if (email == cleanQuery || username == cleanQuery) {
                    foundUid = child.key
                    foundEmail = child.child("email").value as? String
                    foundUsername = child.child("username").value as? String
                    break
                }
            }
            onResult(foundUid, foundEmail, foundUsername)
        }.addOnFailureListener { onResult(null, null, null) }
    }

    fun getRoomId(uid1: String, uid2: String) = if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"

    fun sendMessage(message: Message, onSuccess: (Message) -> Unit, onError: (String) -> Unit) {
        val msgId = db.reference.child("userMessages").child(message.senderId).child(message.receiverId).push().key ?: return
        
        val msgTimestamp = if (message.timestamp > 0L) message.timestamp else System.currentTimeMillis()
        val msg = message.copy(id = msgId, status = MessageStatus.SENT, timestamp = msgTimestamp)
        
        // Simpan metadata di userChats agar loading daftar chat sangat cepat (instant)
        val updates = hashMapOf<String, Any>(
            "userMessages/${message.senderId}/${message.receiverId}/$msgId" to msg,
            "userMessages/${message.receiverId}/${message.senderId}/$msgId" to msg,
            
            // Update metadata untuk pengirim
            "userChats/${message.senderId}/${message.receiverId}/lastMessage" to msg.encryptedContent,
            "userChats/${message.senderId}/${message.receiverId}/lastMsgTimestamp" to msgTimestamp,
            "userChats/${message.senderId}/${message.receiverId}/lastMsgSenderId" to message.senderId,
            
            // Update metadata untuk penerima + Tambah unread count
            "userChats/${message.receiverId}/${message.senderId}/lastMessage" to msg.encryptedContent,
            "userChats/${message.receiverId}/${message.senderId}/lastMsgTimestamp" to msgTimestamp,
            "userChats/${message.receiverId}/${message.senderId}/lastMsgSenderId" to message.senderId,
            "userChats/${message.receiverId}/${message.senderId}/unreadCount" to ServerValue.increment(1)
        )
        
        db.reference.updateChildren(updates)
            .addOnSuccessListener { 
                // Kirim event notifikasi
                val eventKey = db.reference.child("notificationEvents").push().key
                if (eventKey != null) {
                    db.reference.child("users").child(message.senderId).child("username").get()
                        .addOnSuccessListener { nameSnap ->
                            val senderDisplayName = nameSnap.value as? String ?: auth.currentUser?.email ?: "Seseorang"
                            val eventData = mapOf(
                                "senderId" to message.senderId,
                                "receiverId" to message.receiverId,
                                "chatId" to getRoomId(message.senderId, message.receiverId),
                                "messageId" to msgId,
                                "senderName" to senderDisplayName,
                                "type" to "new_message",
                                "content" to msg.encryptedContent,
                                "timestamp" to ServerValue.TIMESTAMP
                            )
                            db.reference.child("notificationEvents").child(eventKey).setValue(eventData)
                        }
                }
                onSuccess(msg) 
            }
            .addOnFailureListener { onError(it.message ?: "Gagal mengirim") }
    }

    fun updateMessageStatus(currentUserId: String, otherUserId: String, messageId: String, status: String) {
        // Update status di kedua tabel agar centang berubah di kedua HP
        val updates = hashMapOf<String, Any>(
            "userMessages/$currentUserId/$otherUserId/$messageId/status" to status,
            "userMessages/$otherUserId/$currentUserId/$messageId/status" to status
        )
        db.reference.updateChildren(updates)
    }

    fun updateChatStartTime(myUid: String, otherUid: String) {
        val timestamp = System.currentTimeMillis()
        db.reference.child("userChats").child(myUid).child(otherUid).child("chatStartedAt").setValue(timestamp)
    }

    fun getChatStartTime(myUid: String, otherUid: String, onResult: (Long) -> Unit) {
        db.reference.child("userChats").child(myUid).child(otherUid).child("chatStartedAt").get()
            .addOnSuccessListener { onResult(it.value as? Long ?: 0L) }
            .addOnFailureListener { onResult(0L) }
    }

    fun listenMessages(currentUserId: String, otherUserId: String, startAtTimestamp: Long, onNewMessage: (Message) -> Unit, onMessageRemoved: (String) -> Unit = {}): ChildEventListener {
        // Dengarkan HANYA dari tabel milik sendiri
        val query = db.reference.child("userMessages").child(currentUserId).child(otherUserId)
            .orderByChild("timestamp")
            .startAt(startAtTimestamp.toDouble())

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, prev: String?) {
                snapshot.getValue(Message::class.java)?.let { onNewMessage(it) }
            }
            override fun onChildChanged(snapshot: DataSnapshot, p: String?) {
                snapshot.getValue(Message::class.java)?.let { onNewMessage(it) }
            }
            override fun onChildRemoved(snapshot: DataSnapshot) {
                snapshot.key?.let { onMessageRemoved(it) }
            }
            override fun onChildMoved(s: DataSnapshot, p: String?) {}
            override fun onCancelled(e: DatabaseError) {}
        }
        query.addChildEventListener(listener)
        return listener
    }

    fun removeMessageListener(currentUserId: String, otherUserId: String, listener: ChildEventListener) {
        db.reference.child("userMessages").child(currentUserId).child(otherUserId).removeEventListener(listener)
    }

    fun listenRecentChats(currentUserId: String, onResult: (List<Map<String, Any>>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) { onResult(emptyList()); return }
                val contactIds = snapshot.children.mapNotNull { it.key }
                val resultList = mutableListOf<Map<String, Any>>()
                var count = 0
                for (id in contactIds) {
                    db.reference.child("users").child(id).get().addOnSuccessListener { userSnap ->
                        val email = userSnap.child("email").value as? String ?: ""
                        val username = userSnap.child("username").value as? String ?: email
                        val status = userSnap.child("status").value as? String ?: "offline"
                        
                        // Ambil metadata langsung dari snapshot userChats yang sudah kita punya
                        val chatMeta = snapshot.child(id)
                        val lastMsg = chatMeta.child("lastMessage").value as? String ?: ""
                        val lastTs = chatMeta.child("lastMsgTimestamp").value as? Long ?: 0L
                        val lastSender = chatMeta.child("lastMsgSenderId").value as? String ?: ""
                        val unread = (chatMeta.child("unreadCount").value as? Long ?: 0L).toInt()

                        resultList.add(mapOf(
                            "uid" to id, "email" to email, "username" to username, 
                            "status" to status, "unreadCount" to unread,
                            "lastMessage" to lastMsg,
                            "lastMsgSenderId" to lastSender,
                            "lastMsgTimestamp" to lastTs
                        ))
                        count++
                        if (count == contactIds.size) onResult(resultList.sortedByDescending { it["lastMsgTimestamp"] as Long })
                    }.addOnFailureListener { 
                        count++
                        if (count == contactIds.size) onResult(resultList) 
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.reference.child("userChats").child(currentUserId).addValueEventListener(listener)
        return listener
    }

    fun resetUnreadCount(myUid: String, otherUid: String) {
        db.reference.child("userChats").child(myUid).child(otherUid).child("unreadCount").setValue(0)
    }

    fun removeRecentChatsListener(currentUserId: String, listener: ValueEventListener) {
        db.reference.child("userChats").child(currentUserId).removeEventListener(listener)
    }

    fun deleteMessage(currentUserId: String, otherUserId: String, msgId: String) {
        db.reference.child("userMessages").child(currentUserId).child(otherUserId).child(msgId).removeValue()
    }

    fun deleteConversation(uid1: String, uid2: String, onComplete: () -> Unit) {
        // Hapus HANYA dari folder milik sendiri (uid1)
        db.reference.child("userMessages").child(uid1).child(uid2).removeValue().addOnSuccessListener {
            db.reference.child("userChats").child(uid1).child(uid2).removeValue()
            onComplete()
        }
    }

    fun getUserData(uid: String, onResult: (DataSnapshot?) -> Unit) {
        db.reference.child("users").child(uid).get()
            .addOnSuccessListener { onResult(it) }
            .addOnFailureListener { onResult(null) }
    }

    fun getCurrentUserId() = auth.currentUser?.uid
    fun logout() {
        getCurrentUserId()?.let { uid ->
            setUserPresence(uid, false)
            // Hapus token FCM saat logout agar tidak menerima notifikasi lagi
            db.reference.child("users").child(uid).child("fcmToken").removeValue()
        }
        auth.signOut()
    }

    fun updateFcmToken(uid: String) {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            db.reference.child("users").child(uid).child("fcmToken").setValue(token)
        }
    }

    fun listenNotificationEvents(currentUserId: String, onNotification: (senderName: String, senderId: String, chatId: String, messageId: String, content: String) -> Unit): ChildEventListener {
        val query = db.reference.child("notificationEvents")
            .orderByChild("receiverId")
            .equalTo(currentUserId)
        
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                // Hanya proses jika timestamp event baru (misal dalam 1 menit terakhir) untuk menghindari notifikasi lama muncul lagi
                val timestamp = snapshot.child("timestamp").value as? Long ?: 0L
                if (System.currentTimeMillis() - timestamp < 60000) {
                    val senderName = snapshot.child("senderName").value as? String ?: "Pesan Baru"
                    val senderId = snapshot.child("senderId").value as? String ?: ""
                    val chatId = snapshot.child("chatId").value as? String ?: ""
                    val messageId = snapshot.child("messageId").value as? String ?: ""
                    val content = snapshot.child("content").value as? String ?: ""
                    
                    if (senderId.isNotEmpty() && chatId.isNotEmpty()) {
                        onNotification(senderName, senderId, chatId, messageId, content)
                    }
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        query.addChildEventListener(listener)
        return listener
    }

    fun removeNotificationListener(listener: ChildEventListener) {
        db.reference.child("notificationEvents").removeEventListener(listener)
    }
}
