package com.clutchalarm.app.auth

import com.clutchalarm.app.network.ApiResponse
import com.clutchalarm.app.network.ApiTransport
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class HttpAuthenticationRepositoryTest {
    private fun tokenResponse(suffix: String) = ApiResponse(200, buildJsonObject {
        put("accessToken", "access-$suffix")
        put("refreshToken", "refresh-$suffix")
        put("accessExpiresAtMillis", Long.MAX_VALUE - 1)
        put("refreshExpiresAtMillis", Long.MAX_VALUE)
    })

    @Test fun registrationDoesNotCreateSessionAndLoginDoes() = runTest {
        val store = InMemorySessionStore()
        val repo = HttpAuthenticationRepository(store, ApiTransport { _, path, _, _ ->
            when (path) {
                "/api/auth/signup" -> ApiResponse(201)
                "/api/auth/login" -> tokenResponse("a")
                else -> error("Unexpected request")
            }
        })
        assertEquals(RegistrationResult.Success, repo.signUp("a@example.com", "password123"))
        assertNull(store.read())
        assertEquals(AuthenticationResult.Success, repo.signIn("a@example.com", "password123"))
        assertEquals("refresh-a", store.read()?.refreshToken)
    }

    @Test fun lostRefreshResponseKeepsSameRequestIdAcrossRepositoryRecreation() = runTest {
        val store = InMemorySessionStore().apply {
            write(StoredSession("old", 1, refreshToken = "refresh-old", refreshExpiresAtMillis = Long.MAX_VALUE))
        }
        var requestId: String? = null
        val first = HttpAuthenticationRepository(store, ApiTransport { _, path, body, _ ->
            assertEquals("/api/auth/refresh", path)
            requestId = (body?.get("requestId") as JsonPrimitive).content
            throw IOException("lost response")
        })
        assertEquals(SessionRestoreResult.Retryable(SessionProblem.NETWORK), first.restoreSession())
        assertEquals(requestId, store.read()?.pendingRefreshId)
        assertEquals("refresh-old", store.read()?.refreshToken)
        val second = HttpAuthenticationRepository(store, ApiTransport { _, path, body, bearer ->
            if (path == "/api/auth/refresh") {
                assertEquals(requestId, (body?.get("requestId") as JsonPrimitive).content)
                tokenResponse("new")
            } else {
                assertEquals("/api/auth/me", path)
                assertEquals("access-new", bearer)
                ApiResponse(200, buildJsonObject { put("email", "a@example.com") })
            }
        })
        assertEquals(SessionRestoreResult.Authenticated, second.restoreSession())
        assertNull(store.read()?.pendingRefreshId)
        assertEquals("refresh-new", store.read()?.refreshToken)
    }

    @Test fun deletionRequiresConfirmedServerResponse() = runTest {
        val store = InMemorySessionStore().apply {
            write(StoredSession("access", Long.MAX_VALUE - 1, refreshToken = "refresh", refreshExpiresAtMillis = Long.MAX_VALUE))
        }
        var status = 503
        val repo = HttpAuthenticationRepository(store, ApiTransport { method, path, _, _ ->
            assertEquals("DELETE", method)
            assertEquals("/api/auth/account", path)
            ApiResponse(status)
        })
        assertEquals(AccountDeletionResult.Failure(AccountFailure.NETWORK), repo.deleteAccount())
        assertNotNull(store.read())
        status = 204
        assertEquals(AccountDeletionResult.Success, repo.deleteAccount())
        repo.clearLocalSession()
        assertNull(store.read())
    }
}
