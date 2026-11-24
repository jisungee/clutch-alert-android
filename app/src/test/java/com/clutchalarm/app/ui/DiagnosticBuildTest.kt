package com.clutchalarm.app.ui

import com.clutchalarm.app.BuildConfig
import com.clutchalarm.app.auth.AuthenticationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiagnosticBuildTest {
    @Test fun diagnosticsAreLimitedToTheQaDebugSourceSet() {
        assertEquals(AuthenticationProvider.qaLoginAvailable, BuildConfig.QA_DIAGNOSTICS)
        if (!BuildConfig.DEBUG) assertFalse(BuildConfig.QA_DIAGNOSTICS)
    }
}
