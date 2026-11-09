package com.clutchalarm.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationIdsTest {
    @Test
    fun differentPostsAndProcessNamespacesHaveDistinctSystemIdentities() {
        val ids = NotificationIds()
        val addresses = List(100) { ids.next() }
        assertEquals(100, addresses.map { it.id }.toSet().size)
        val firstInNextProcess = NotificationIds().next()
        assertEquals(addresses.first().id, firstInNextProcess.id)
        assertNotEquals(addresses.first(), firstInNextProcess)
    }
}
