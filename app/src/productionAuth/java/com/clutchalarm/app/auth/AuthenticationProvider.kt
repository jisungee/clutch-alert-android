package com.clutchalarm.app.auth

/** This provider is selected for ordinary debug builds and every release build. */
object AuthenticationProvider {
    const val qaLoginAvailable = false
    fun create(sessionStore: SessionStore = InMemorySessionStore()): AuthenticationRepository = UnavailableAuthenticationRepository(sessionStore)
}
