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
class SignUpFlowTest {
    @Test fun invalidEmailShortBlankAndMismatchedPasswordsNeverSubmit() = runTest {
        var calls = 0
        val repo = object : AuthenticationRepository {
            override suspend fun signIn(email: String, password: String) = AuthenticationResult.Success
            override suspend fun signUp(email: String, password: String): RegistrationResult { calls++; return RegistrationResult.Success }
        }
        withModel(repo) { model ->
            model.openSignUp()
            for (input in listOf(Triple("bad", "password1", "password1"), Triple("a@b.com", "short", "short"),
                Triple("a@b.com", "        ", "        "), Triple("a@b.com", "password1", "password2"))) {
                model.setSignUpEmail(input.first); model.setSignUpPassword(input.second); model.setSignUpConfirmation(input.third)
                assertFalse(model.signUpState.value.canSubmit)
                model.signUp()
            }
            runCurrent()
            assertEquals(0, calls)
        }
    }

    @Test fun registrationPrefillsEmailButDoesNotAuthenticateUntilExplicitLogin() = runTest {
        var authCalls = 0
        val repo = object : AuthenticationRepository {
            private var registered = false
            override suspend fun signUp(email: String, password: String): RegistrationResult { registered = true; return RegistrationResult.Success }
            override suspend fun signIn(email: String, password: String): AuthenticationResult {
                assertTrue(registered); assertEquals("new@example.com", email); assertEquals("password1", password)
                return AuthenticationResult.Success
            }
        }
        withModel(repo, onSuccess = { authCalls++ }) { model ->
            model.setPassword("old-password")
            model.openSignUp(); fill(model)
            model.signUp(); runCurrent()
            assertFalse(model.showSignUp.value)
            assertFalse(model.state.value.authenticated)
            assertEquals(0, authCalls)
            assertTrue(model.state.value.registrationSucceeded)
            assertEquals("new@example.com", model.state.value.email)
            assertEquals("", model.state.value.password)
            assertEquals(SignUpState(), model.signUpState.value)
            model.setPassword("password1"); model.login(); runCurrent()
            assertTrue(model.state.value.authenticated)
            assertEquals(1, authCalls)
        }
    }

    @Test fun busyFlowRejectsDuplicateAndBackThenDuplicateEmailRetainsInputsForRetry() = runTest {
        val response = CompletableDeferred<RegistrationResult>()
        var calls = 0
        val repo = object : AuthenticationRepository {
            override suspend fun signIn(email: String, password: String) = AuthenticationResult.Success
            override suspend fun signUp(email: String, password: String): RegistrationResult {
                calls++; return if (calls == 1) response.await() else RegistrationResult.Success
            }
        }
        withModel(repo) { model ->
            model.openSignUp(); fill(model)
            model.signUp(); model.signUp(); runCurrent()
            assertTrue(model.signUpState.value.busy)
            model.setSignUpEmail("ignored@example.com"); model.closeSignUp()
            assertTrue(model.showSignUp.value)
            assertEquals(" new@example.com ", model.signUpState.value.email)
            response.complete(RegistrationResult.Failure(RegistrationFailure.EMAIL_IN_USE)); runCurrent()
            assertEquals(1, calls)
            assertEquals(RegistrationFailure.EMAIL_IN_USE, model.signUpState.value.error)
            assertEquals("password1", model.signUpState.value.password)
            assertEquals("password1", model.signUpState.value.confirmation)
            model.setSignUpEmail("retry@example.com")
            assertNull(model.signUpState.value.error)
            model.signUp(); runCurrent()
            assertEquals(2, calls)
            assertEquals("retry@example.com", model.state.value.email)
            assertFalse(model.state.value.authenticated)
        }
    }

    @Test fun offlineRegistrationLeavesEditableDraftAndDoesNotOpenHome() = runTest {
        val repo = object : AuthenticationRepository {
            override suspend fun signIn(email: String, password: String) = AuthenticationResult.Success
            override suspend fun signUp(email: String, password: String): RegistrationResult = throw IOException("offline")
        }
        withModel(repo) { model ->
            model.openSignUp(); fill(model); model.signUp(); runCurrent()
            assertEquals(RegistrationFailure.NETWORK, model.signUpState.value.error)
            assertTrue(model.signUpState.value.canSubmit)
            assertFalse(model.state.value.authenticated)
        }
    }

    @Test fun backDiscardsPasswordsAndPreservesPreviousLoginEmail() = runTest {
        withModel(UnavailableAuthenticationRepository()) { model ->
            model.setEmail("old@example.com"); model.openSignUp(); fill(model)
            model.closeSignUp()
            assertFalse(model.showSignUp.value)
            assertEquals(SignUpState(), model.signUpState.value)
            assertEquals("old@example.com", model.state.value.email)
            assertFalse(model.state.value.registrationSucceeded)
        }
    }

    @Test fun ordinaryRepositoryRegistrationNeverCreatesAuthentication() = runTest {
        withModel(UnavailableAuthenticationRepository()) { model ->
            model.openSignUp(); fill(model); model.signUp(); runCurrent()
            assertEquals(RegistrationFailure.NOT_CONFIGURED, model.signUpState.value.error)
            assertFalse(model.state.value.authenticated)
            assertTrue(model.showSignUp.value)
        }
    }

    @Test fun qaRegisteredAccountCanLoginAndDuplicateCannotOverwriteItWhileReleaseCannotRegister() = runTest {
        val repo = AuthenticationProvider.create()
        if (!com.clutchalarm.app.BuildConfig.DEBUG) assertFalse(AuthenticationProvider.qaLoginAvailable)
        val email = "new-${UUID.randomUUID()}@example.com"
        val result = repo.signUp(email, "password1")
        if (AuthenticationProvider.qaLoginAvailable) {
            assertEquals(RegistrationResult.Success, result)
            assertEquals(RegistrationResult.Failure(RegistrationFailure.EMAIL_IN_USE), repo.signUp(email.uppercase(), "password2"))
            assertEquals(AuthenticationResult.Success, AuthenticationProvider.create().signIn(email, "password1"))
            assertEquals(AuthenticationResult.Failure(AuthenticationFailure.INVALID_CREDENTIALS), repo.signIn(email, "password2"))
            assertEquals(AuthenticationResult.Success, repo.signIn("qa@example.com", "clutch-qa"))
        } else {
            assertEquals(RegistrationResult.Failure(RegistrationFailure.NOT_CONFIGURED), result)
            assertEquals(AuthenticationResult.Failure(AuthenticationFailure.NOT_CONFIGURED), repo.signIn(email, "password1"))
        }
    }

    private fun fill(model: LoginViewModel) {
        model.setSignUpEmail(" new@example.com ")
        model.setSignUpPassword("password1")
        model.setSignUpConfirmation("password1")
    }

    private suspend fun TestScope.withModel(repo: AuthenticationRepository, onSuccess: () -> Unit = {},
        block: suspend TestScope.(LoginViewModel) -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val model = LoginViewModel(repo, onAuthenticated = onSuccess, restoreOnStart = false)
        try { block(model) } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }
}
