package com.clutchalarm.app.ui

import androidx.lifecycle.viewModelScope
import com.clutchalarm.app.auth.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionFlowTest {
    private open class Repo(val store: SessionStore = InMemorySessionStore()) : AuthenticationRepository {
        override suspend fun signIn(email: String, password: String) = AuthenticationResult.Success
        override suspend fun clearLocalSession() = store.clear()
    }

    @Test fun checkingBlocksCredentialsAndSuccessRoutesOnlyOnce() = runTest {
        val result = CompletableDeferred<SessionRestoreResult>()
        val repo = object : Repo() { override suspend fun restoreSession() = result.await() }
        var ready = 0
        withModel(repo, onReady = { ready++ }) { model ->
            assertEquals(SessionPhase.CHECKING, model.state.value.sessionPhase)
            assertFalse(model.state.value.authenticated)
            model.setEmail("ignored@example.com"); model.openSignUp(); model.login()
            assertEquals("", model.state.value.email)
            assertFalse(model.showSignUp.value)
            result.complete(SessionRestoreResult.Authenticated); runCurrent()
            assertTrue(model.state.value.authenticated)
            assertEquals(1, ready)
        }
    }

    @Test fun temporaryNetworkFailureKeepsCredentialAndRetryCanRestore() = runTest {
        val store = InMemorySessionStore().apply { write(StoredSession("opaque", Long.MAX_VALUE)) }
        var calls = 0
        val repo = object : Repo(store) {
            override suspend fun restoreSession(): SessionRestoreResult {
                if (++calls == 1) throw IOException("offline")
                return SessionRestoreResult.Authenticated
            }
        }
        withModel(repo) { model ->
            runCurrent()
            assertEquals(SessionPhase.RETRYABLE, model.state.value.sessionPhase)
            assertEquals(SessionProblem.NETWORK, model.state.value.sessionProblem)
            assertNotNull(store.read())
            assertFalse(model.state.value.authenticated)
            model.restoreSession(); runCurrent()
            assertTrue(model.state.value.authenticated)
            assertNotNull(store.read())
        }
    }

    @Test fun confirmedExpirationOrRevocationClearsCredentialUnlikeNetworkFailure() = runTest {
        for (reason in listOf(SessionInvalidReason.EXPIRED, SessionInvalidReason.REVOKED, SessionInvalidReason.LOCAL_DATA_INVALID)) {
            val store = InMemorySessionStore().apply { write(StoredSession("opaque", Long.MAX_VALUE)) }
            val repo = object : Repo(store) { override suspend fun restoreSession() = SessionRestoreResult.Invalidated(reason) }
            var unavailable = 0
            withModel(repo, onUnavailable = { unavailable++ }) { model ->
                runCurrent()
                assertFalse(model.state.value.authenticated)
                assertEquals(reason, model.state.value.invalidSession)
                assertNull(store.read())
                assertEquals(1, unavailable)
            }
        }
    }

    @Test fun logoutCancelKeepsSessionAndConfirmClearsEvenWhenRemoteRevokeFails() = runTest {
        val store = InMemorySessionStore().apply { write(StoredSession("opaque", Long.MAX_VALUE)) }
        val repo = object : Repo(store) {
            override suspend fun restoreSession() = SessionRestoreResult.Authenticated
            override suspend fun signOut() { throw IOException("offline revoke") }
        }
        var clearedRoutes = 0
        withModel(repo, onLogout = { clearedRoutes++ }) { model ->
            runCurrent()
            model.requestLogout(); model.cancelLogout()
            assertFalse(model.state.value.logoutConfirmation)
            assertTrue(model.state.value.authenticated)
            assertNotNull(store.read())
            model.requestLogout(); model.confirmLogout()
            assertFalse(model.state.value.authenticated)
            assertEquals(SessionPhase.LOGGING_OUT, model.state.value.sessionPhase)
            runCurrent()
            assertNull(store.read())
            assertEquals(SessionPhase.IDLE, model.state.value.sessionPhase)
            assertEquals(1, clearedRoutes)
        }
        withModel(object : Repo(store) {}) { model ->
            runCurrent()
            assertFalse(model.state.value.authenticated)
        }
    }

    @Test fun removalFailureNeverShowsProtectedScreenAndAllowsRetry() = runTest {
        var fail = true
        val repo = object : Repo() {
            override suspend fun restoreSession() = SessionRestoreResult.Authenticated
            override suspend fun clearLocalSession() { if (fail) throw IOException("disk") }
        }
        withModel(repo) { model ->
            runCurrent(); model.requestLogout(); model.confirmLogout(); runCurrent()
            assertEquals(SessionPhase.LOGOUT_FAILED, model.state.value.sessionPhase)
            assertFalse(model.state.value.authenticated)
            fail = false
            model.confirmLogout(); runCurrent()
            assertEquals(SessionPhase.IDLE, model.state.value.sessionPhase)
            assertFalse(model.state.value.authenticated)
        }
    }

    @Test fun onlyQaProviderCanRestoreQaSessionAndRefreshKeepsNoPassword() = runTest {
        val store = InMemorySessionStore().apply { write(StoredSession("qa-session:test", System.currentTimeMillis() + 60_000)) }
        val repo = AuthenticationProvider.create(store)
        if (AuthenticationProvider.qaLoginAvailable) {
            assertEquals(SessionRestoreResult.Authenticated, repo.restoreSession())
            assertEquals(SessionRestoreResult.Authenticated, repo.refreshSession())
            assertTrue(store.read()!!.credential.startsWith("qa-session:"))
        } else {
            assertTrue(repo.restoreSession() is SessionRestoreResult.Retryable)
            assertTrue(repo.refreshSession() is SessionRestoreResult.Retryable)
        }
        repo.signOut()
        assertNull(store.read())
        assertEquals(SessionRestoreResult.SignedOut, repo.restoreSession())
    }

    private suspend fun TestScope.withModel(repo: AuthenticationRepository, onReady: () -> Unit = {},
        onUnavailable: () -> Unit = {}, onLogout: () -> Unit = {}, block: suspend TestScope.(LoginViewModel) -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val model = LoginViewModel(repo, onAuthenticated = onReady, onSessionUnavailable = onUnavailable, onLoggedOut = onLogout)
        try { block(model) } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }
}
