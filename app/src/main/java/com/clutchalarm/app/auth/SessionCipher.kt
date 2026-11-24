package com.clutchalarm.app.auth

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Versioned AES-GCM envelope. The platform generates a fresh nonce for every write. */
class SessionCipher(private val key: () -> SecretKey, private val associatedData: ByteArray) {
    fun encrypt(bytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(associatedData)
        check(cipher.iv.size == 12)
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(bytes)
    }

    fun decrypt(envelope: ByteArray): ByteArray {
        if (envelope.size < 29 || envelope[0] != 1.toByte()) throw GeneralSecurityException("Invalid session envelope")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, envelope.copyOfRange(1, 13)))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(envelope.copyOfRange(13, envelope.size))
    }
}
