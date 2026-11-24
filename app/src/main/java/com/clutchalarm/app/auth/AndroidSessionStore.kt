package com.clutchalarm.app.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class InvalidLocalSession : IOException("Stored session cannot be verified")

/** Keystore key is non-exportable; only authenticated ciphertext goes into noBackupFilesDir. */
class AndroidSessionStore(context: Context) : SessionStore {
    private val app = context.applicationContext
    private val file = AtomicFile(File(app.noBackupFilesDir, "auth-session.bin"))
    private val cipher = SessionCipher(::key, "${app.packageName}:auth-session:v1".toByteArray(Charsets.UTF_8))

    override suspend fun read(): StoredSession? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val bytes = try { file.readFully() } catch (missing: java.io.FileNotFoundException) {
                if (file.baseFile.exists()) throw missing
                return@synchronized null
            }
            try {
                Json.decodeFromString<StoredSession>(cipher.decrypt(bytes).toString(Charsets.UTF_8))
                    .also { if (it.credential.isBlank()) throw GeneralSecurityException("Empty credential") }
            } catch (_: GeneralSecurityException) {
                throw InvalidLocalSession()
            } catch (_: SerializationException) {
                throw InvalidLocalSession()
            }
        }
    }

    override suspend fun write(session: StoredSession) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            require(session.credential.isNotBlank())
            val bytes = cipher.encrypt(Json.encodeToString(session).toByteArray(Charsets.UTF_8))
            val output = file.startWrite()
            try {
                output.write(bytes)
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            file.delete()
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists() || File(file.baseFile.path + ".new").exists())
                throw IOException("Session removal failed")
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    private companion object {
        const val ALIAS = "clutch_auth_session_v1"
        val lock = Any()
    }
}
