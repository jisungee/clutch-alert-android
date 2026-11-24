package com.clutchalarm.app.ui

import androidx.lifecycle.viewModelScope
import com.clutchalarm.app.auth.*
import com.clutchalarm.app.behavior.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @Test fun invalidInputNeverStartsAuthentication() = runTest {
        var calls = 0
        withModel(AuthenticationRepository { _, _ -> calls++; AuthenticationResult.Success }) { model ->
            model.login()
            model.setEmail("invalid")
            model.setPassword("password")
            model.login()
            runCurrent()
            assertFalse(model.state.value.canSubmit)
            assertEquals(0, calls)
            assertFalse(model.state.value.authenticated)
        }
    }

    @Test fun loadingPreventsDoubleSubmitThenFailureKeepsInputsAndRetryCanSucceed() = runTest {
        val response = CompletableDeferred<AuthenticationResult>()
        var calls = 0
        var successes = 0
        withModel(AuthenticationRepository { email, password ->
            assertEquals("qa@example.com", email)
            assertEquals("password", password)
            calls++
            if (calls == 1) response.await() else AuthenticationResult.Success
        }, onSuccess = { successes++ }) { model ->
            model.setEmail(" qa@example.com ")
            model.setPassword("password")
            model.login()
            model.login()
            runCurrent()
            assertTrue(model.state.value.busy)
            assertFalse(model.state.value.canSubmit)
            model.setEmail("ignored@example.com")
            response.complete(AuthenticationResult.Failure(AuthenticationFailure.INVALID_CREDENTIALS))
            runCurrent()
            assertEquals(1, calls)
            assertEquals(AuthenticationFailure.INVALID_CREDENTIALS, model.state.value.error)
            assertEquals(" qa@example.com ", model.state.value.email)
            assertEquals("password", model.state.value.password)
            assertTrue(model.state.value.canSubmit)
            model.login()
            runCurrent()
            assertTrue(model.state.value.authenticated)
            assertEquals("", model.state.value.password)
            assertEquals("", model.state.value.email)
            model.login()
            runCurrent()
            assertEquals(2, calls)
            assertEquals(1, successes)
        }
    }

    @Test fun networkFailureDoesNotAuthenticateAndCanBeRetried() = runTest {
        withModel(AuthenticationRepository { _, _ -> throw IOException("offline") }) { model ->
            fill(model)
            model.login()
            runCurrent()
            assertEquals(AuthenticationFailure.NETWORK, model.state.value.error)
            assertFalse(model.state.value.authenticated)
            assertTrue(model.state.value.canSubmit)
        }
    }

    @Test fun productionUnavailableRepositoryCannotGrantQaCredentials() = runTest {
        withModel(UnavailableAuthenticationRepository()) { model ->
            fill(model)
            model.login()
            runCurrent()
            assertFalse(model.qaLoginAvailable)
            assertFalse(model.state.value.authenticated)
            assertEquals(AuthenticationFailure.NOT_CONFIGURED, model.state.value.error)
        }
    }

    @Test fun qaReturnIsNotEnabledForProductionAuthentication() = runTest {
        withModel(AuthenticationRepository { _, _ -> AuthenticationResult.Success }) { model ->
            fill(model)
            model.login()
            runCurrent()
            model.returnToLoginForQa()
            assertTrue(model.state.value.authenticated)
        }
    }

    @Test fun loginHomeLoginHomeAndRepeatedResumeDoNotDuplicateAppOpen() = runTest {
        val events = mutableListOf<BehaviorEvent>()
        val process = ProcessAppOpen(BehaviorEventRecorder { events.add(it) })
        withModel(AuthenticationRepository { _, _ -> AuthenticationResult.Success }, qa = true) { model ->
            repeat(2) {
                process.onScreenVisible() // login root resumed
                fill(model)
                model.login()
                runCurrent()
                assertTrue(model.state.value.authenticated)
                process.onScreenVisible() // home resumed / rotation
                model.returnToLoginForQa()
                assertFalse(model.state.value.authenticated)
                assertEquals("", model.state.value.password)
            }
        }
        assertEquals(listOf("app_open"), events.map { it.name })
    }

    @Test fun selectedBuildProviderHasNoSuccessUnlessItIsQa() = runTest {
        if (!com.clutchalarm.app.BuildConfig.DEBUG) assertFalse(AuthenticationProvider.qaLoginAvailable)
        val result = AuthenticationProvider.create().signIn("qa@example.com", "clutch-qa")
        if (AuthenticationProvider.qaLoginAvailable) {
            assertEquals(AuthenticationResult.Success, result)
            assertTrue(AuthenticationProvider.create().signIn("qa@example.com", "wrong") is AuthenticationResult.Failure)
        } else {
            assertEquals(AuthenticationResult.Failure(AuthenticationFailure.NOT_CONFIGURED), result)
        }
    }

    private fun fill(model: LoginViewModel) {
        model.setEmail("qa@example.com")
        model.setPassword("clutch-qa")
    }

    private suspend fun TestScope.withModel(repo: AuthenticationRepository, qa: Boolean = false,
        onSuccess: () -> Unit = {}, block: suspend TestScope.(LoginViewModel) -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val model = LoginViewModel(repo, qa, onSuccess, restoreOnStart = false)
        try { block(model) } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }
}
