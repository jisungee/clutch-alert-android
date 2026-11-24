package com.clutchalarm.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clutchalarm.app.auth.AuthenticationFailure
import com.clutchalarm.app.auth.AuthenticationRepository
import com.clutchalarm.app.auth.AuthenticationResult
import com.clutchalarm.app.auth.RegistrationFailure
import com.clutchalarm.app.auth.RegistrationResult
import com.clutchalarm.app.auth.SessionRestoreResult
import com.clutchalarm.app.auth.SessionProblem
import com.clutchalarm.app.auth.SessionInvalidReason
import com.clutchalarm.app.auth.InvalidLocalSession
import com.clutchalarm.app.auth.AccountProfileResult
import com.clutchalarm.app.auth.AccountDeletionResult
import com.clutchalarm.app.auth.AccountFailure
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class SessionPhase { IDLE, CHECKING, RETRYABLE, LOGGING_OUT, LOGOUT_FAILED, DELETING, DELETE_CLEANUP_FAILED }

data class LoginState(
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val authenticated: Boolean = false,
    val error: AuthenticationFailure? = null,
    val registrationSucceeded: Boolean = false,
    val sessionPhase: SessionPhase = SessionPhase.IDLE,
    val sessionProblem: SessionProblem? = null,
    val invalidSession: SessionInvalidReason? = null,
    val logoutConfirmation: Boolean = false,
    val accountEmail: String? = null,
    val accountLoading: Boolean = false,
    val accountError: AccountFailure? = null,
    val deletionConfirmation: Boolean = false,
    val deletionError: AccountFailure? = null,
    val accountDeleted: Boolean = false,
) {
    val canSubmit: Boolean get() = sessionPhase == SessionPhase.IDLE && !busy && !authenticated &&
        validEmail(email) && password.isNotBlank()
}

class LoginViewModel(
    private val repository: AuthenticationRepository,
    val qaLoginAvailable: Boolean = false,
    private val onAuthenticated: () -> Unit = {},
    private val onSessionUnavailable: () -> Unit = {},
    private val onLoggedOut: () -> Unit = {},
    private val sessionEvent: (String) -> Unit = {},
    restoreOnStart: Boolean = true,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginState())
    val state = mutableState.asStateFlow()
    private val mutableShowSignUp = MutableStateFlow(false)
    val showSignUp = mutableShowSignUp.asStateFlow()
    private val mutableSignUp = MutableStateFlow(SignUpState())
    val signUpState = mutableSignUp.asStateFlow()
    private var accountGeneration = 0L

    init { if (restoreOnStart) restoreSession() }

    fun restoreSession(refresh: Boolean = false) {
        if ((state.value.sessionPhase != SessionPhase.IDLE && state.value.sessionPhase != SessionPhase.RETRYABLE) || state.value.busy || signUpState.value.busy) return
        accountGeneration++
        mutableShowSignUp.value = false
        mutableSignUp.value = SignUpState()
        mutableState.value = LoginState(sessionPhase = SessionPhase.CHECKING)
        sessionEvent("restore state=CHECKING")
        viewModelScope.launch {
            val result = try {
                if (refresh) repository.refreshSession() else repository.restoreSession()
            } catch (_: InvalidLocalSession) {
                SessionRestoreResult.Invalidated(SessionInvalidReason.LOCAL_DATA_INVALID)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: IOException) { SessionRestoreResult.Retryable(SessionProblem.NETWORK)
            } catch (_: Exception) { SessionRestoreResult.Retryable(SessionProblem.STORAGE) }
            when (result) {
                SessionRestoreResult.Authenticated -> {
                    onAuthenticated()
                    mutableState.value = LoginState(authenticated = true)
                    loadAccount()
                    sessionEvent("restore result=AUTHENTICATED")
                }
                SessionRestoreResult.SignedOut -> {
                    onSessionUnavailable()
                    mutableState.value = LoginState()
                    sessionEvent("restore result=SIGNED_OUT")
                }
                is SessionRestoreResult.Retryable -> {
                    mutableState.value = LoginState(sessionPhase = SessionPhase.RETRYABLE, sessionProblem = result.reason)
                    sessionEvent("restore result=RETRYABLE reason=${result.reason}")
                }
                is SessionRestoreResult.Invalidated -> {
                    try {
                        repository.clearLocalSession()
                        onSessionUnavailable()
                        mutableState.value = LoginState(invalidSession = result.reason)
                        sessionEvent("restore result=INVALIDATED reason=${result.reason}")
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (_: Exception) {
                        mutableState.value = LoginState(sessionPhase = SessionPhase.RETRYABLE, sessionProblem = SessionProblem.STORAGE)
                    }
                }
            }
        }
    }

    fun loadAccount() {
        if (!state.value.authenticated || state.value.sessionPhase != SessionPhase.IDLE || state.value.accountLoading) return
        val generation = ++accountGeneration
        mutableState.value = state.value.copy(accountLoading = true, accountError = null)
        viewModelScope.launch {
            val result = try { repository.currentAccount()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: IOException) { AccountProfileResult.Failure(AccountFailure.NETWORK)
            } catch (_: Exception) { AccountProfileResult.Failure(AccountFailure.UNKNOWN) }
            if (generation != accountGeneration || !state.value.authenticated || state.value.sessionPhase != SessionPhase.IDLE) return@launch
            mutableState.value = when (result) {
                is AccountProfileResult.Available -> if (result.account.email.isNotBlank()) state.value.copy(
                    accountLoading = false, accountEmail = result.account.email, accountError = null)
                    else state.value.copy(accountLoading = false, accountEmail = null, accountError = AccountFailure.UNKNOWN)
                is AccountProfileResult.Failure -> state.value.copy(accountLoading = false, accountEmail = null, accountError = result.reason)
            }
        }
    }

    fun requestLogout() { if (state.value.authenticated && state.value.sessionPhase == SessionPhase.IDLE) mutableState.value = state.value.copy(logoutConfirmation = true, deletionConfirmation = false) }
    fun cancelLogout() { mutableState.value = state.value.copy(logoutConfirmation = false) }

    fun confirmLogout() {
        if (!state.value.logoutConfirmation && state.value.sessionPhase != SessionPhase.LOGOUT_FAILED) return
        accountGeneration++
        mutableState.value = LoginState(sessionPhase = SessionPhase.LOGGING_OUT)
        mutableShowSignUp.value = false
        mutableSignUp.value = SignUpState()
        onLoggedOut()
        viewModelScope.launch {
            try {
                // A remote revoke failure cannot prevent local logout.
                withTimeout(5_000) { repository.signOut() }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { /* local removal below is authoritative on this device */
            } finally {
                withContext(NonCancellable) {
                    try {
                        repository.clearLocalSession()
                        mutableState.value = LoginState()
                        sessionEvent("logout local=CLEARED")
                    } catch (_: Exception) {
                        mutableState.value = LoginState(sessionPhase = SessionPhase.LOGOUT_FAILED)
                        sessionEvent("logout local=FAILED")
                    }
                }
            }
        }
    }

    fun requestDeletion() {
        if (state.value.authenticated && state.value.accountEmail != null && !state.value.accountLoading && state.value.sessionPhase == SessionPhase.IDLE)
            mutableState.value = state.value.copy(deletionConfirmation = true, deletionError = null, logoutConfirmation = false)
    }

    fun cancelDeletion() { mutableState.value = state.value.copy(deletionConfirmation = false) }

    fun confirmDeletion() {
        val previous = state.value
        if (!previous.deletionConfirmation || !previous.authenticated || previous.sessionPhase != SessionPhase.IDLE) return
        accountGeneration++
        mutableState.value = previous.copy(sessionPhase = SessionPhase.DELETING, deletionConfirmation = false)
        viewModelScope.launch {
            val result = try { repository.deleteAccount()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: IOException) { AccountDeletionResult.Failure(AccountFailure.NETWORK)
            } catch (_: Exception) { AccountDeletionResult.Failure(AccountFailure.UNKNOWN) }
            when (result) {
                AccountDeletionResult.Success -> {
                    sessionEvent("account-delete result=CONFIRMED")
                    onLoggedOut()
                    mutableState.value = LoginState(sessionPhase = SessionPhase.DELETE_CLEANUP_FAILED)
                    finishDeletion()
                }
                is AccountDeletionResult.Failure -> {
                    mutableState.value = previous.copy(sessionPhase = SessionPhase.IDLE, deletionConfirmation = false, deletionError = result.reason)
                    sessionEvent("account-delete result=FAILED reason=${result.reason}")
                }
            }
        }
    }

    /** Retry local cleanup only; never repeat an already confirmed destructive API call. */
    fun retryDeletionCleanup() {
        if (state.value.sessionPhase != SessionPhase.DELETE_CLEANUP_FAILED) return
        mutableState.value = LoginState(sessionPhase = SessionPhase.DELETING)
        viewModelScope.launch { finishDeletion() }
    }

    private suspend fun finishDeletion() = withContext(NonCancellable) {
        try {
            repository.clearLocalSession()
            mutableState.value = LoginState(accountDeleted = true)
            sessionEvent("account-delete local=CLEARED")
        } catch (_: Exception) {
            mutableState.value = LoginState(sessionPhase = SessionPhase.DELETE_CLEANUP_FAILED)
        }
    }

    fun setEmail(value: String) {
        if (state.value.sessionPhase == SessionPhase.IDLE && !state.value.busy && !state.value.authenticated) mutableState.value = state.value.copy(email = value, error = null, registrationSucceeded = false)
    }

    fun setPassword(value: String) {
        if (state.value.sessionPhase == SessionPhase.IDLE && !state.value.busy && !state.value.authenticated) mutableState.value = state.value.copy(password = value, error = null)
    }

    fun login() {
        val submitted = state.value
        if (showSignUp.value || !submitted.canSubmit) return
        mutableState.value = submitted.copy(busy = true, error = null)
        viewModelScope.launch {
            val result = try {
                repository.signIn(submitted.email.trim(), submitted.password)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                AuthenticationResult.Failure(AuthenticationFailure.NETWORK)
            } catch (_: Exception) {
                AuthenticationResult.Failure(AuthenticationFailure.UNKNOWN)
            }
            when (result) {
                AuthenticationResult.Success -> {
                    onAuthenticated()
                    mutableState.value = LoginState(authenticated = true)
                    loadAccount()
                    sessionEvent("authenticated source=login")
                }
                is AuthenticationResult.Failure -> mutableState.value = submitted.copy(error = result.reason)
            }
        }
    }

    fun openSignUp() {
        if (state.value.sessionPhase != SessionPhase.IDLE || state.value.busy || state.value.authenticated || showSignUp.value) return
        mutableSignUp.value = SignUpState(email = state.value.email)
        mutableShowSignUp.value = true
    }

    fun closeSignUp() {
        if (signUpState.value.busy) return
        mutableSignUp.value = SignUpState()
        mutableShowSignUp.value = false
    }

    fun setSignUpEmail(value: String) = editSignUp { it.copy(email = value) }
    fun setSignUpPassword(value: String) = editSignUp { it.copy(password = value) }
    fun setSignUpConfirmation(value: String) = editSignUp { it.copy(confirmation = value) }

    private fun editSignUp(transform: (SignUpState) -> SignUpState) {
        if (showSignUp.value && !signUpState.value.busy) mutableSignUp.value = transform(signUpState.value).copy(error = null)
    }

    fun signUp() {
        val submitted = signUpState.value
        if (!showSignUp.value || !submitted.canSubmit) return
        mutableSignUp.value = submitted.copy(busy = true, error = null)
        viewModelScope.launch {
            val result = try {
                repository.signUp(submitted.email.trim(), submitted.password)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                RegistrationResult.Failure(RegistrationFailure.NETWORK)
            } catch (_: Exception) {
                RegistrationResult.Failure(RegistrationFailure.UNKNOWN)
            }
            when (result) {
                RegistrationResult.Success -> {
                    mutableState.value = LoginState(email = submitted.email.trim(), registrationSucceeded = true)
                    mutableSignUp.value = SignUpState()
                    mutableShowSignUp.value = false
                }
                is RegistrationResult.Failure -> mutableSignUp.value = submitted.copy(error = result.reason)
            }
        }
    }

    /** QA-only screen transition, not a production sign-out or persistent session implementation. */
    fun returnToLoginForQa() {
        if (qaLoginAvailable && state.value.sessionPhase == SessionPhase.IDLE && !state.value.busy && !signUpState.value.busy) {
            accountGeneration++
            mutableState.value = LoginState()
            closeSignUp()
        }
    }
}
