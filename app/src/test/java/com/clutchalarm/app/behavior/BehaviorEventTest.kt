package com.clutchalarm.app.behavior

import org.junit.Assert.assertEquals
import org.junit.Test

class BehaviorEventTest {
    @Test fun appOpenOccursOncePerProcessObjectRegardlessOfResumeAndRotation() {
        val events = mutableListOf<BehaviorEvent>()
        val recorder = BehaviorEventRecorder { events.add(it) }
        val process = ProcessAppOpen(recorder)
        repeat(10) { process.onScreenVisible() }
        assertEquals(listOf("app_open"), events.map { it.name })
        ProcessAppOpen(recorder).onScreenVisible()
        assertEquals(2, events.size)
    }
}
