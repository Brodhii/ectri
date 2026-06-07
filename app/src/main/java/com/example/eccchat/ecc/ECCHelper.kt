package com.example.eccchat.ecc

import java.math.BigInteger
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

object ECCHelper {
    // --- Fitur Enkripsi Kunci Privat dengan Password (AES-256) ---
    private const val ITERATIONS = 1000
    private const val KEY_LENGTH = 256
    private val salt = "ECC_CHAT_SALT_STRING".toByteArray() // Garam tetap untuk derivasi password

    private fun deriveKey(password: String): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    fun encryptWithPassword(data: String, password: String): String {
        return try {
            val key = deriveKey(password)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)
            
            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)
            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            
            val combined = iv + encryptedBytes
            Base64.encodeToString(combined, Base64.DEFAULT)
        } catch (e: Exception) {
            "ERROR_ENCRYPT_PWD"
        }
    }

    fun decryptWithPassword(encryptedData: String, password: String): String {
        return try {
            val key = deriveKey(password)
            val combined = Base64.decode(encryptedData, Base64.DEFAULT)
            val iv = combined.copyOfRange(0, 16)
            val encryptedBytes = combined.copyOfRange(16, combined.size)
            
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val ivSpec = IvParameterSpec(iv)
            
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "ERROR_DECRYPT_PWD"
        }
    }

    // --- Parameter kurva secp192r1 ---
    private val p = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFFFFFFFFFF", 16)
    private val a = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFFFFFFFFFC", 16)
    private val b = BigInteger("64210519E59C80E70FA7E9AB72243049FEB8DEECC146B9B1", 16)
    private val Gx = BigInteger("188DA80EB03090F67CBF20EB43A18800F4FF0AFD82FF1012", 16)
    private val Gy = BigInteger("07192B95FFC8DA78631011ED6B24CDD573F977A11E794811", 16)
    private val n = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFF99DEF836146BC9B1B4D22831", 16)
    private val G = Pair(Gx, Gy)

    // Point Addition: P + Q
    private fun pointAdd(
        P: Pair<BigInteger, BigInteger>?,
        Q: Pair<BigInteger, BigInteger>?
    ): Pair<BigInteger, BigInteger>? {
        if (P == null) return Q
        if (Q == null) return P
        if (P.first == Q.first && P.second == Q.second) return pointDouble(P)

        val (x1, y1) = P
        val (x2, y2) = Q

        if (x1 == x2) return null

        val lam = ((y2 - y1).mod(p) * (x2 - x1).modInverse(p)).mod(p)
        val x3 = (lam.pow(2) - x1 - x2).mod(p)
        val y3 = (lam * (x1 - x3) - y1).mod(p)
        return Pair(x3, y3)
    }

    // Point Doubling: P + P
    private fun pointDouble(
        P: Pair<BigInteger, BigInteger>?
    ): Pair<BigInteger, BigInteger>? {
        if (P == null) return null
        val (x1, y1) = P

        if (y1.signum() == 0) return null

        val lam = ((BigInteger.valueOf(3) * x1.pow(2) + a).mod(p) * (BigInteger.valueOf(2) * y1).modInverse(p)).mod(p)
        val x3 = (lam.pow(2) - BigInteger.valueOf(2) * x1).mod(p)
        val y3 = (lam * (x1 - x3) - y1).mod(p)
        return Pair(x3, y3)
    }

    // Scalar Multiplication: k × P (inti ECC)
    fun scalarMultiply(k: BigInteger, P: Pair<BigInteger, BigInteger>): Pair<BigInteger, BigInteger>? {
        var result: Pair<BigInteger, BigInteger>? = null
        var addend: Pair<BigInteger, BigInteger>? = P
        var kTemp = k

        while (kTemp > BigInteger.ZERO) {
            if (kTemp.testBit(0)) {
                result = pointAdd(result, addend)
            }
            addend = pointDouble(addend)
            if (addend == null && kTemp.shiftRight(1) > BigInteger.ZERO) return null
            kTemp = kTemp.shiftRight(1)
        }
        return result
    }

    // Generate Key Pair
    fun generateKeyPair(): Pair<BigInteger, Pair<BigInteger, BigInteger>> {
        val privateKey = BigInteger(192, java.security.SecureRandom()).mod(n - BigInteger.ONE) + BigInteger.ONE
        val publicKey = scalarMultiply(privateKey, G)!!
        return Pair(privateKey, publicKey)
    }

    // Enkripsi pesan (XOR sederhana dengan shared secret)
    fun encrypt(message: String, publicKey: Pair<BigInteger, BigInteger>): String {
        val k = BigInteger(192, java.security.SecureRandom()).mod(n - BigInteger.ONE) + BigInteger.ONE
        val sharedPoint = scalarMultiply(k, publicKey) ?: return "ERROR_ENCRYPT"
        val kG = scalarMultiply(k, G) ?: return "ERROR_ENCRYPT"
        
        // Gunakan Hex String dari shared secret agar panjangnya konsisten dan tidak meleset
        val secretKey = sharedPoint.first.toString(16)
        val secretBytes = secretKey.toByteArray(Charsets.UTF_8)
        val msgBytes = message.toByteArray(Charsets.UTF_8)
        
        val encrypted = ByteArray(msgBytes.size) { i -> (msgBytes[i].toInt() xor secretBytes[i % secretBytes.size].toInt()).toByte() }
        val encHex = encrypted.joinToString("") { "%02x".format(it) }
        val kGHex = "${kG.first.toString(16)},${kG.second.toString(16)}"
        return "$kGHex|$encHex"
    }

    // Dekripsi pesan
    fun decrypt(encrypted: String, privateKey: BigInteger): String {
        return try {
            val parts = encrypted.split("|")
            if (parts.size < 2) return "Pesan tidak valid"
            val kGParts = parts[0].split(",")
            if (kGParts.size < 2) return "Kunci tidak valid"
            val kG = Pair(BigInteger(kGParts[0], 16), BigInteger(kGParts[1], 16))
            val sharedPoint = scalarMultiply(privateKey, kG) ?: return "Dekripsi gagal!"
            
            // Samakan dengan proses enkripsi: gunakan Hex String
            val secretKey = sharedPoint.first.toString(16)
            val secretBytes = secretKey.toByteArray(Charsets.UTF_8)
            
            val encHex = parts[1]
            val encBytes = ByteArray(encHex.length / 2) {
                encHex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
            }
            val decrypted = ByteArray(encBytes.size) { i ->
                (encBytes[i].toInt() xor secretBytes[i % secretBytes.size].toInt()).toByte()
            }
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            "Dekripsi gagal!"
        }
    }

    // Konversi public key ke String untuk disimpan di Firebase
    fun publicKeyToString(publicKey: Pair<BigInteger, BigInteger>): String {
        return "${publicKey.first.toString(16)},${publicKey.second.toString(16)}"
    }

    // Konversi String kembali ke public key
    fun stringToPublicKey(str: String): Pair<BigInteger, BigInteger>? {
        return try {
            val parts = str.split(",")
            if (parts.size < 2) return null
            Pair(BigInteger(parts[0], 16), BigInteger(parts[1], 16))
        } catch (e: Exception) {
            null
        }
    }
}