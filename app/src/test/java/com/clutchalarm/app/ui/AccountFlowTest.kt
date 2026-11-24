package com.clutchalarm.app.ui

import androidx.lifecycle.viewModelScope
import com.clutchalarm.app.auth.*
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountFlowTest {
    private open class Repo : AuthenticationRepository {
        var clears = 0
        override suspend fun signIn(email: String, password: String) = AuthenticationResult.Success
        override suspend fun restoreSession() = SessionRestoreResult.Authenticated
        override suspend fun currentAccount(): AccountProfileResult = AccountProfileResult.Available(AccountProfile("verified@example.com"))
        override suspend fun clearLocalSession() { clears++ }
    }

    @Test fun restoredAccountUsesRepositoryIdentityAndProfileFailureCanRetry() = runTest {
        var attempts = 0
        val repo = object : Repo() {
            override suspend fun currentAccount(): AccountProfileResult {
                if (++attempts == 1) throw IOException("offline")
                return super.currentAccount()
            }
        }
        withModel(repo) { model ->
            runCurrent()
            assertTrue(model.state.value.authenticated)
            assertNull(model.state.value.accountEmail)
            assertEquals(AccountFailure.NETWORK, model.state.value.accountError)
            model.requestDeletion()
            assertFalse(model.state.value.deletionConfirmation)
            model.loadAccount(); runCurrent()
            assertEquals("verified@example.com", model.state.value.accountEmail)
            assertNull(model.state.value.accountError)
        }
    }

    @Test fun delayedOldProfileCannotLeakIntoNextLogin() = runTest {
        val old = CompletableDeferred<AccountProfileResult>()
        var profiles = 0
        val repo = object : Repo() {
            override suspend fun currentAccount(): AccountProfileResult = if (++profiles == 1) old.await()
                else AccountProfileResult.Available(AccountProfile("new@example.com"))
        }
        withModel(repo) { model ->
            runCurrent()
            assertTrue(model.state.value.accountLoading)
            model.requestLogout(); model.confirmLogout(); runCurrent()
            model.setEmail("typed@example.com"); model.setPassword("password"); model.login(); runCurrent()
            assertEquals("new@example.com", model.state.value.accountEmail)
            old.complete(AccountProfileResult.Available(AccountProfile("old@example.com"))); runCurrent()
            assertEquals("new@example.com", model.state.value.accountEmail)
        }
    }

    @Test fun cancelAndFailedDeletionKeepAuthenticationThenConfirmedRetryCleansUp() = runTest {
        val response = CompletableDeferred<AccountDeletionResult>()
        var calls = 0
        var routesCleared = 0
        val repo = object : Repo() {
            override suspend fun deleteAccount(): AccountDeletionResult = if (++calls == 1) response.await() else AccountDeletionResult.Success
        }
        withModel(repo, onLogout = { routesCleared++ }) { model ->
            runCurrent()
            model.requestDeletion(); model.cancelDeletion()
            assertEquals(0, calls)
            assertTrue(model.state.value.authenticated)
            model.requestDeletion(); model.confirmDeletion(); model.confirmDeletion(); runCurrent()
            assertEquals(SessionPhase.DELETING, model.state.value.sessionPhase)
            assertEquals(1, calls)
            response.complete(AccountDeletionResult.Failure(AccountFailure.NETWORK)); runCurrent()
            assertTrue(model.state.value.authenticated)
            assertEquals("verified@example.com", model.state.value.accountEmail)
            assertEquals(AccountFailure.NETWORK, model.state.value.deletionError)
            assertEquals(0, repo.clears)
            assertEquals(0, routesCleared)
            model.requestDeletion(); model.confirmDeletion(); runCurrent()
            assertFalse(model.state.value.authenticated)
            assertTrue(model.state.value.accountDeleted)
            assertNull(model.state.value.accountEmail)
            assertEquals(1, repo.clears)
            assertEquals(1, routesCleared)
        }
    }

    @Test fun confirmedDeletionCleanupRetryNeverRepeatsDeleteRequest() = runTest {
        var calls = 0
        var failClear = true
        val repo = object : Repo() {
            override suspend fun deleteAccount(): AccountDeletionResult { calls++; return AccountDeletionResult.Success }
            override suspend fun clearLocalSession() { if (failClear) throw IOException("disk") }
        }
        withModel(repo) { model ->
            runCurrent(); model.requestDeletion(); model.confirmDeletion(); runCurrent()
            assertEquals(SessionPhase.DELETE_CLEANUP_FAILED, model.state.value.sessionPhase)
            assertFalse(model.state.value.authenticated)
            model.confirmDeletion(); model.restoreSession(); runCurrent()
            assertEquals(1, calls)
            failClear = false
            model.retryDeletionCleanup(); runCurrent()
            assertTrue(model.state.value.accountDeleted)
            assertEquals(1, calls)
        }
    }

    @Test fun unconnectedDeleteNeverSucceedsEvenWithTestAuthentication() = runTest {
        val repo = Repo() // default deleteAccount contract is explicitly NOT_CONFIGURED
        withModel(repo) { model ->
            runCurrent(); model.requestDeletion(); model.confirmDeletion(); runCurrent()
            assertEquals(AccountFailure.NOT_CONFIGURED, model.state.value.deletionError)
            assertTrue(model.state.value.authenticated)
            assertEquals(0, repo.clears)
        }
    }

    @Test fun onlyQaCanLoadPersistedIdentityAndDeleteAuthenticatedAccount() = runTest {
        val store = InMemorySessionStore()
        val repo = AuthenticationProvider.create(store)
        val email = "account-${UUID.randomUUID()}@example.com"
        if (AuthenticationProvider.qaLoginAvailable) {
            assertEquals(RegistrationResult.Success, repo.signUp(email, "password1"))
            assertEquals(AuthenticationResult.Success, repo.signIn(email, "password1"))
            val recreated = AuthenticationProvider.create(store)
            assertEquals(SessionRestoreResult.Authenticated, recreated.restoreSession())
            assertEquals(AccountProfileResult.Available(AccountProfile(email)), recreated.currentAccount())
            assertEquals(SessionRestoreResult.Authenticated, recreated.refreshSession())
            assertEquals(email, store.read()!!.accountEmail)
            assertEquals(AccountDeletionResult.Success, recreated.deleteAccount())
            assertEquals(AuthenticationResult.Failure(AuthenticationFailure.INVALID_CREDENTIALS), repo.signIn(email, "password1"))
            assertTrue(recreated.restoreSession() is SessionRestoreResult.Invalidated)
        } else {
            assertFalse(com.clutchalarm.app.BuildConfig.DEBUG && AuthenticationProvider.qaLoginAvailable)
            assertEquals(AccountProfileResult.Failure(AccountFailure.NOT_CONFIGURED), repo.currentAccount())
            assertEquals(AccountDeletionResult.Failure(AccountFailure.NOT_CONFIGURED), repo.deleteAccount())
        }
    }

    private suspend fun TestScope.withModel(repo: AuthenticationRepository, onLogout: () -> Unit = {},
        block: suspend TestScope.(LoginViewModel) -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val model = LoginViewModel(repo, onLoggedOut = onLogout)
        try { block(model) } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }
}
