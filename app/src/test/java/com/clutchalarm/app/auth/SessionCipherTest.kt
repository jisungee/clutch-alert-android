package com.clutchalarm.app.auth

import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class SessionCipherTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun roundTripUsesFreshNonceAndContainsNoPlainCredential() {
        val key = key()
        val cipher = SessionCipher({ key }, "qa:session".toByteArray())
        val secret = "opaque-session-credential".toByteArray()
        val one = cipher.encrypt(secret)
        val two = cipher.encrypt(secret)
        assertFalse(one.contentEquals(two))
        assertFalse(one.toString(Charsets.UTF_8).contains("opaque-session-credential"))
        assertArrayEquals(secret, cipher.decrypt(one))
        assertFalse(StoredSession("opaque-session-credential", 1).toString().contains("opaque-session-credential"))
    }

    @Test fun tamperedCiphertextAndWrongAppBindingCannotBeDecrypted() {
        val key = key()
        val cipher = SessionCipher({ key }, "qa:session".toByteArray())
        val bytes = cipher.encrypt("opaque".toByteArray())
        val changed = bytes.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(changed) }
        assertThrows(GeneralSecurityException::class.java) { SessionCipher({ key }, "other:session".toByteArray()).decrypt(bytes) }
        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(byteArrayOf(1)) }
    }
}
