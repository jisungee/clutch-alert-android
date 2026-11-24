package com.clutchalarm.app.auth

import kotlinx.coroutines.delay
import java.util.Locale
import java.util.UUID

/** Included only by -PbehaviorQa=true assembleDebug; never included in release sources. */
object AuthenticationProvider {
    const val qaLoginAvailable = true
    private val accounts = mutableMapOf("qa@example.com" to "clutch-qa")
    private val testSessions = InMemorySessionStore()
    fun create(sessionStore: SessionStore = testSessions): AuthenticationRepository = QaAuthenticationRepository(accounts, sessionStore)
}

/** Process-local test accounts only. No production account or persistent session storage. */
private class QaAuthenticationRepository(private val accounts: MutableMap<String, String>, private val sessions: SessionStore) : AuthenticationRepository {
    private fun key(email: String) = email.trim().lowercase(Locale.ROOT)

    override suspend fun signIn(email: String, password: String): AuthenticationResult {
        delay(600)
        if (!synchronized(accounts) { accounts[key(email)] == password }) return AuthenticationResult.Failure(AuthenticationFailure.INVALID_CREDENTIALS)
        try { sessions.write(StoredSession("qa-session:${UUID.randomUUID()}", System.currentTimeMillis() + 86_400_000, key(email))) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { return AuthenticationResult.Failure(AuthenticationFailure.STORAGE) }
        return AuthenticationResult.Success
    }

    override suspend fun signUp(email: String, password: String): RegistrationResult {
        delay(600)
        return synchronized(accounts) {
            if (key(email) in accounts) RegistrationResult.Failure(RegistrationFailure.EMAIL_IN_USE)
            else {
                accounts[key(email)] = password
                RegistrationResult.Success
            }
        }
    }

    override suspend fun restoreSession(): SessionRestoreResult {
        delay(600)
        val saved = try { sessions.read() } catch (_: InvalidLocalSession) {
            return SessionRestoreResult.Invalidated(SessionInvalidReason.LOCAL_DATA_INVALID)
        } catch (_: java.io.IOException) {
            return SessionRestoreResult.Retryable(SessionProblem.STORAGE)
        }
        if (saved == null) return SessionRestoreResult.SignedOut
        if (!saved.credential.startsWith("qa-session:")) {
            return SessionRestoreResult.Invalidated(SessionInvalidReason.LOCAL_DATA_INVALID)
        }
        if (saved.expiresAtMillis <= System.currentTimeMillis()) {
            return SessionRestoreResult.Invalidated(SessionInvalidReason.EXPIRED)
        }
        return SessionRestoreResult.Authenticated
    }

    override suspend fun refreshSession(): SessionRestoreResult {
        val result = restoreSession()
        if (result == SessionRestoreResult.Authenticated) {
            val old = sessions.read() ?: return SessionRestoreResult.SignedOut
            try { sessions.write(old.copy(credential = "qa-session:${UUID.randomUUID()}", expiresAtMillis = System.currentTimeMillis() + 86_400_000)) }
            catch (_: java.io.IOException) { return SessionRestoreResult.Retryable(SessionProblem.STORAGE) }
        }
        return result
    }

    override suspend fun currentAccount(): AccountProfileResult {
        delay(300)
        val saved = sessions.read()
        if (saved == null || !saved.credential.startsWith("qa-session:") || saved.expiresAtMillis <= System.currentTimeMillis())
            return AccountProfileResult.Failure(AccountFailure.REAUTH_REQUIRED)
        val email = saved.accountEmail ?: return AccountProfileResult.Failure(AccountFailure.REAUTH_REQUIRED)
        return AccountProfileResult.Available(AccountProfile(email))
    }

    override suspend fun deleteAccount(): AccountDeletionResult {
        delay(600)
        val saved = sessions.read()
        val email = saved?.accountEmail
        if (saved == null || email == null || !saved.credential.startsWith("qa-session:") || saved.expiresAtMillis <= System.currentTimeMillis())
            return AccountDeletionResult.Failure(AccountFailure.REAUTH_REQUIRED)
        // Revoke this QA session before confirming deletion, even if later local removal fails.
        try { sessions.write(saved.copy(expiresAtMillis = 0)) }
        catch (_: java.io.IOException) { return AccountDeletionResult.Failure(AccountFailure.STORAGE) }
        synchronized(accounts) { accounts.remove(key(email)) }
        return AccountDeletionResult.Success
    }

    override suspend fun clearLocalSession() = sessions.clear()
}
