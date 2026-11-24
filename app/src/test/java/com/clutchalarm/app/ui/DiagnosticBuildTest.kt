package com.clutchalarm.app.ui

import com.clutchalarm.app.BuildConfig
import com.clutchalarm.app.auth.AuthenticationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class DiagnosticBuildTest {
    @Test fun diagnosticsAreLimitedToTheQaDebugSourceSet() {
        assertEquals(AuthenticationProvider.qaLoginAvailable, BuildConfig.QA_DIAGNOSTICS)
        if (!BuildConfig.DEBUG) assertFalse(BuildConfig.QA_DIAGNOSTICS)
    }
    @Test fun productionUiCannotOfferFixtureCredentialsOrQaNavigation() {
        if (AuthenticationProvider.qaLoginAvailable) {
            assertNotNull(BuildUiContent.loginHint)
            assertNotNull(BuildUiContent.registrationHint)
            assertNotNull(BuildUiContent.homeLoginLabel)
        } else {
            assertEquals("로그인", BuildUiContent.loginLabel)
            assertNull(BuildUiContent.loginHint)
            assertNull(BuildUiContent.registrationHint)
            assertNull(BuildUiContent.homeLoginLabel)
        }
    }
}
