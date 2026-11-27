package com.clutchalarm.app.auth

import com.clutchalarm.app.network.ApiResponse
import com.clutchalarm.app.network.ApiTransport
import com.clutchalarm.app.network.apiBody
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Actual server authentication; persistent refresh request IDs make a lost response retryable. */
internal class HttpAuthenticationRepository(
    private val sessions: SessionStore,
    private val api: ApiTransport,
) : AuthenticationRepository {
    private val lock = Mutex()
    private class Rejected : IOException("Server rejected session")
    private class RemoteProblem : IOException("Server unavailable")
    private class LocalProblem : IOException("Local session storage unavailable")

    private fun ApiResponse.string(key: String): String? = (body[key] as? JsonPrimitive)?.contentOrNull
    private fun ApiResponse.number(key: String): Long? = (body[key] as? JsonPrimitive)?.longOrNull
    private fun transient(status: Int) = status == 408 || status == 425 || status == 429 || status >= 500
    private suspend fun read(): StoredSession? = try { sessions.read() }
        catch (e: InvalidLocalSession) { throw e }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { throw LocalProblem() }
    private suspend fun write(value: StoredSession) = try { sessions.write(value) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { throw LocalProblem() }

    private fun tokens(response: ApiResponse): StoredSession {
        val access = response.string("accessToken")?.takeIf { it.isNotBlank() } ?: throw RemoteProblem()
        val refresh = response.string("refreshToken")?.takeIf { it.isNotBlank() } ?: throw RemoteProblem()
        val accessExpiry = response.number("accessExpiresAtMillis") ?: throw RemoteProblem()
        val refreshExpiry = response.number("refreshExpiresAtMillis") ?: throw RemoteProblem()
        if (accessExpiry <= 0 || refreshExpiry < accessExpiry) throw RemoteProblem()
        return StoredSession(access, accessExpiry, refreshToken = refresh, refreshExpiresAtMillis = refreshExpiry)
    }

    override suspend fun signUp(email: String, password: String): RegistrationResult = lock.withLock {
        try {
            val result = api.request("POST", "/api/auth/signup", apiBody("email" to email, "password" to password), null)
            when {
                result.status == 201 -> RegistrationResult.Success
                result.status == 409 && result.string("detail") == "EMAIL_IN_USE" -> RegistrationResult.Failure(RegistrationFailure.EMAIL_IN_USE)
                transient(result.status) -> RegistrationResult.Failure(RegistrationFailure.NETWORK)
                else -> RegistrationResult.Failure(RegistrationFailure.UNKNOWN)
            }
        } catch (_: IOException) { RegistrationResult.Failure(RegistrationFailure.NETWORK) }
    }

    override suspend fun signIn(email: String, password: String): AuthenticationResult = lock.withLock {
        try {
            val result = api.request("POST", "/api/auth/login", apiBody("email" to email, "password" to password), null)
            when {
                result.status == 200 -> {
                    val session = tokens(result)
                    try { write(session) } catch (e: LocalProblem) {
                        // Best effort: avoid leaving a new remote session if local persistence failed.
                        try { api.request("POST", "/api/auth/logout", apiBody("refreshToken" to session.refreshToken!!), null) }
                        catch (_: IOException) { }
                        throw e
                    }
                    AuthenticationResult.Success
                }
                result.status == 401 -> AuthenticationResult.Failure(AuthenticationFailure.INVALID_CREDENTIALS)
                transient(result.status) -> AuthenticationResult.Failure(AuthenticationFailure.NETWORK)
                else -> AuthenticationResult.Failure(AuthenticationFailure.UNKNOWN)
            }
        } catch (_: LocalProblem) { AuthenticationResult.Failure(AuthenticationFailure.STORAGE) }
        catch (_: IOException) { AuthenticationResult.Failure(AuthenticationFailure.NETWORK) }
    }

    private suspend fun rotate(stored: StoredSession): StoredSession {
        val refresh = stored.refreshToken ?: throw Rejected()
        val id = stored.pendingRefreshId ?: UUID.randomUUID().toString().also {
            write(stored.copy(pendingRefreshId = it))
        }
        val result = api.request("POST", "/api/auth/refresh", apiBody("refreshToken" to refresh, "requestId" to id), null)
        if (result.status == 401) throw Rejected()
        if (result.status != 200) throw RemoteProblem()
        return tokens(result).also { write(it) }
    }

    /** Refresh at most once per authoritative 401, except a pending rotation is recovered first. */
    private suspend fun account(stored: StoredSession, force: Boolean = false): AccountProfile {
        var current = stored
        var rotated = false
        if (force || current.pendingRefreshId != null || current.expiresAtMillis <= System.currentTimeMillis() + 30_000) {
            current = rotate(current)
            rotated = true
        }
        var result = api.request("GET", "/api/auth/me", null, current.credential)
        if (result.status == 401 && (!rotated || stored.pendingRefreshId != null || current.expiresAtMillis <= System.currentTimeMillis())) {
            current = rotate(current)
            result = api.request("GET", "/api/auth/me", null, current.credential)
        }
        if (result.status == 401) throw Rejected()
        if (result.status != 200) throw RemoteProblem()
        return AccountProfile(result.string("email")?.takeIf { it.isNotBlank() } ?: throw RemoteProblem())
    }

    private suspend fun restore(force: Boolean): SessionRestoreResult = lock.withLock {
        try {
            val stored = read() ?: return@withLock SessionRestoreResult.SignedOut
            if (stored.refreshToken == null) return@withLock SessionRestoreResult.Invalidated(SessionInvalidReason.LOCAL_DATA_INVALID)
            account(stored, force)
            SessionRestoreResult.Authenticated
        } catch (_: Rejected) { SessionRestoreResult.Invalidated(SessionInvalidReason.REVOKED) }
        catch (_: InvalidLocalSession) { SessionRestoreResult.Invalidated(SessionInvalidReason.LOCAL_DATA_INVALID) }
        catch (_: LocalProblem) { SessionRestoreResult.Retryable(SessionProblem.STORAGE) }
        catch (_: IOException) { SessionRestoreResult.Retryable(SessionProblem.NETWORK) }
    }
    override suspend fun restoreSession() = restore(false)
    override suspend fun refreshSession() = restore(true)

    override suspend fun currentAccount(): AccountProfileResult = lock.withLock {
        try {
            val stored = read() ?: throw Rejected()
            AccountProfileResult.Available(account(stored))
        } catch (_: Rejected) { AccountProfileResult.Failure(AccountFailure.REAUTH_REQUIRED) }
        catch (_: InvalidLocalSession) { AccountProfileResult.Failure(AccountFailure.REAUTH_REQUIRED) }
        catch (_: LocalProblem) { AccountProfileResult.Failure(AccountFailure.STORAGE) }
        catch (_: IOException) { AccountProfileResult.Failure(AccountFailure.NETWORK) }
    }

    override suspend fun signOut() = lock.withLock {
        val stored = read() ?: return@withLock
        // Server accepts the previous refresh only for revocation (never for ordinary use).
        stored.refreshToken?.let { token ->
            val body = stored.pendingRefreshId?.let { apiBody("refreshToken" to token, "requestId" to it) }
                ?: apiBody("refreshToken" to token)
            api.request("POST", "/api/auth/logout", body, null)
        }
        Unit
    }

    override suspend fun deleteAccount(): AccountDeletionResult = lock.withLock {
        try {
            var stored = read() ?: throw Rejected()
            if (stored.pendingRefreshId != null) stored = rotate(stored)
            val refresh = stored.refreshToken ?: throw Rejected()
            val result = api.request("DELETE", "/api/auth/account", apiBody("refreshToken" to refresh), null)
            when {
                result.status == 204 -> AccountDeletionResult.Success
                result.status == 401 -> AccountDeletionResult.Failure(AccountFailure.REAUTH_REQUIRED)
                transient(result.status) -> AccountDeletionResult.Failure(AccountFailure.NETWORK)
                else -> AccountDeletionResult.Failure(AccountFailure.UNKNOWN)
            }
        } catch (_: Rejected) { AccountDeletionResult.Failure(AccountFailure.REAUTH_REQUIRED) }
        catch (_: InvalidLocalSession) { AccountDeletionResult.Failure(AccountFailure.REAUTH_REQUIRED) }
        catch (_: LocalProblem) { AccountDeletionResult.Failure(AccountFailure.STORAGE) }
        catch (_: IOException) { AccountDeletionResult.Failure(AccountFailure.NETWORK) }
    }

    override suspend fun clearLocalSession() = lock.withLock { sessions.clear() }
}
