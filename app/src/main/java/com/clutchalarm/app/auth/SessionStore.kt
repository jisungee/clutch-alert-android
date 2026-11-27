package com.clutchalarm.app.auth

import kotlinx.serialization.Serializable

/** Minimal credential plus optional encrypted QA profile cache. Never store passwords. */
@Serializable
data class StoredSession(val credential: String, val expiresAtMillis: Long, val accountEmail: String? = null,
    val refreshToken: String? = null, val refreshExpiresAtMillis: Long? = null,
    val pendingRefreshId: String? = null) {
    override fun toString() = "StoredSession([redacted])"
}

interface SessionStore {
    suspend fun read(): StoredSession?
    suspend fun write(session: StoredSession)
    suspend fun clear()
}

class InMemorySessionStore : SessionStore {
    private var value: StoredSession? = null
    override suspend fun read() = value
    override suspend fun write(session: StoredSession) { value = session }
    override suspend fun clear() { value = null }
}

enum class SessionProblem { NETWORK, NOT_CONFIGURED, STORAGE }
enum class SessionInvalidReason { EXPIRED, REVOKED, LOCAL_DATA_INVALID }
sealed interface SessionRestoreResult {
    data object Authenticated : SessionRestoreResult
    data object SignedOut : SessionRestoreResult
    data class Retryable(val reason: SessionProblem) : SessionRestoreResult
    data class Invalidated(val reason: SessionInvalidReason) : SessionRestoreResult
}
