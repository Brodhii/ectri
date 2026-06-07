package com.example.eccchat.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.eccchat.ecc.ECCHelper
import com.example.eccchat.repository.FirebaseRepository

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onGoToLogin: () -> Unit
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Regex untuk username: huruf kecil, angka, titik, garis bawah (minimal 3 karakter)
    val usernameRegex = Regex("^[a-z0-9._]{3,20}$")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📝 Daftar Akun", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { 
                // Otomatis ubah ke huruf kecil dan hapus spasi
                username = it.lowercase().replace(" ", "")
                errorMsg = ""
            },
            label = { Text("Username") },
            placeholder = { Text("hanya huruf kecil, . dan _") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = username.isNotEmpty() && !usernameRegex.matches(username)
        )
        if (username.isNotEmpty() && !usernameRegex.matches(username)) {
            Text(
                "Gunakan 3-20 karakter (a-z, 0-9, . , _)",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.Start)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (errorMsg.isNotEmpty()) {
            Text(errorMsg, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                val cleanEmail = email.trim().lowercase()
                val cleanUsername = username.trim()

                if (cleanEmail.isEmpty()) {
                    errorMsg = "Email tidak boleh kosong"
                    return@Button
                }
                if (!usernameRegex.matches(cleanUsername)) {
                    errorMsg = "Format username tidak valid"
                    return@Button
                }
                
                isLoading = true
                errorMsg = ""

                // Cek apakah username sudah dipakai
                FirebaseRepository.checkUsernameExists(cleanUsername) { exists ->
                    if (exists) {
                        isLoading = false
                        errorMsg = "Username sudah digunakan orang lain"
                    } else {
                        val (privateKey, publicKey) = ECCHelper.generateKeyPair()
                        val publicKeyStr = ECCHelper.publicKeyToString(publicKey)
                        val privateKeyStr = privateKey.toString(16)
                        
                        FirebaseRepository.register(
                            cleanEmail, cleanUsername, password, publicKeyStr,
                            onSuccess = {
                                val uid = FirebaseRepository.getCurrentUserId() ?: ""
                                // Simpan Kunci Privat dan waktu pembuatannya HANYA di lokal
                                val prefs = context.getSharedPreferences(
                                    "ecc_prefs", Context.MODE_PRIVATE
                                )
                                prefs.edit()
                                    .putString("private_key_$uid", privateKeyStr)
                                    .putLong("private_key_time_$uid", System.currentTimeMillis())
                                    .apply()
                                isLoading = false
                                onRegisterSuccess()
                            },
                            onError = {
                                isLoading = false
                                errorMsg = it
                            }
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && usernameRegex.matches(username)
        ) {
            Text(if (isLoading) "Loading..." else "Daftar")
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onGoToLogin) {
            Text("Sudah punya akun? Login")
        }
    }
}
