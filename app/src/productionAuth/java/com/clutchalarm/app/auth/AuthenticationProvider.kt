package com.clutchalarm.app.auth

/** This provider is selected for ordinary debug builds and every release build. */
object AuthenticationProvider {
    const val qaLoginAvailable = false
    fun create(sessionStore: SessionStore = InMemorySessionStore()): AuthenticationRepository = if (com.clutchalarm.app.BuildConfig.API_BASE_URL.isBlank()) UnavailableAuthenticationRepository(sessionStore)
        else HttpAuthenticationRepository(sessionStore, com.clutchalarm.app.network.HttpApi(com.clutchalarm.app.BuildConfig.API_BASE_URL))
}
