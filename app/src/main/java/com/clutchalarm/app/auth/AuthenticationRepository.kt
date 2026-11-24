package com.clutchalarm.app.auth

/** Implement against the agreed authentication contract. Success must mean verified authentication. */
fun interface AuthenticationRepository {
    suspend fun signIn(email: String, password: String): AuthenticationResult

    /** Registration does not authenticate the user. Confirmation is validated locally. */
    suspend fun signUp(email: String, password: String): RegistrationResult =
        RegistrationResult.Failure(RegistrationFailure.NOT_CONFIGURED)

    /** Retryable errors must retain the stored credential. Invalidated means authoritative rejection. */
    suspend fun restoreSession(): SessionRestoreResult = SessionRestoreResult.SignedOut
    suspend fun refreshSession(): SessionRestoreResult = SessionRestoreResult.Retryable(SessionProblem.NOT_CONFIGURED)
    suspend fun clearLocalSession() = Unit
    /** Implement remote revocation if required; the caller also clears local state on network failure. */
    suspend fun signOut() { clearLocalSession() }

    /** Fetch the authenticated subject; never infer it from the login form. */
    suspend fun currentAccount(): AccountProfileResult = AccountProfileResult.Failure(AccountFailure.NOT_CONFIGURED)
    /** Success means confirmed deletion of the authenticated subject, not local sign-out. */
    suspend fun deleteAccount(): AccountDeletionResult = AccountDeletionResult.Failure(AccountFailure.NOT_CONFIGURED)
}

data class AccountProfile(val email: String)
enum class AccountFailure { NETWORK, NOT_CONFIGURED, REAUTH_REQUIRED, STORAGE, UNKNOWN }
sealed interface AccountProfileResult {
    data class Available(val account: AccountProfile) : AccountProfileResult
    data class Failure(val reason: AccountFailure) : AccountProfileResult
}
sealed interface AccountDeletionResult {
    data object Success : AccountDeletionResult
    data class Failure(val reason: AccountFailure) : AccountDeletionResult
}

sealed interface AuthenticationResult {
    data object Success : AuthenticationResult
    data class Failure(val reason: AuthenticationFailure) : AuthenticationResult
}

enum class AuthenticationFailure { INVALID_CREDENTIALS, NETWORK, NOT_CONFIGURED, UNKNOWN, STORAGE }

sealed interface RegistrationResult {
    data object Success : RegistrationResult
    data class Failure(val reason: RegistrationFailure) : RegistrationResult
}

enum class RegistrationFailure { EMAIL_IN_USE, NETWORK, NOT_CONFIGURED, UNKNOWN }

/** No endpoint or authentication contract has been agreed. Never grants a session. */
class UnavailableAuthenticationRepository(private val sessions: SessionStore? = null) : AuthenticationRepository {
    override suspend fun signIn(email: String, password: String) =
        AuthenticationResult.Failure(AuthenticationFailure.NOT_CONFIGURED)

    override suspend fun restoreSession(): SessionRestoreResult {
        val stored = try { sessions?.read() } catch (invalid: InvalidLocalSession) { throw invalid
        } catch (_: java.io.IOException) { return SessionRestoreResult.Retryable(SessionProblem.STORAGE) }
        return if (stored == null) SessionRestoreResult.SignedOut else SessionRestoreResult.Retryable(SessionProblem.NOT_CONFIGURED)
    }
    override suspend fun clearLocalSession() { sessions?.clear() }
}
