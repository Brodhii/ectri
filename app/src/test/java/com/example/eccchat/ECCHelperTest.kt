package com.example.eccchat

import com.example.eccchat.ecc.ECCHelper
import org.junit.Test
import org.junit.Assert.*
import java.math.BigInteger

class ECCHelperTest {

    @Test
    fun testStringToPublicKey_Valid() {
        val pubKey = Pair(BigInteger("123", 16), BigInteger("456", 16))
        val str = ECCHelper.publicKeyToString(pubKey)
        val parsed = ECCHelper.stringToPublicKey(str)
        assertNotNull(parsed)
        assertEquals(pubKey, parsed)
    }

    @Test
    fun testStringToPublicKey_Invalid() {
        assertNull(ECCHelper.stringToPublicKey("invalid"))
        assertNull(ECCHelper.stringToPublicKey("123"))
        assertNull(ECCHelper.stringToPublicKey("123,invalid"))
        assertNull(ECCHelper.stringToPublicKey("invalid,456"))
    }
    
    @Test
    fun testDecrypt_InvalidFormat() {
        val result = ECCHelper.decrypt("invalid_format", BigInteger.ONE)
        assertEquals("Pesan tidak valid", result)
    }

    @Test
    fun testDecrypt_InvalidKeyParts() {
        val result = ECCHelper.decrypt("123|abc", BigInteger.ONE)
        assertEquals("Kunci tidak valid", result)
    }

    @Test
    fun testEncryptionDecryption_Cycle() {
        val (_, _) = ECCHelper.generateKeyPair()
        val (privB, pubB) = ECCHelper.generateKeyPair()

        val originalMessage = "Halo! Ini pesan rahasia dengan simbol @#$%^&*()"
        
        // A kirim ke B (pakai pubB)
        val encrypted = ECCHelper.encrypt(originalMessage, pubB)
        
        // B terima (pakai privB)
        val decrypted = ECCHelper.decrypt(encrypted, privB)
        
        assertEquals(originalMessage, decrypted)
    }
}
