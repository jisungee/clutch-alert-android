package com.clutchalarm.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationIdsTest {
    @Test
    fun postIdsAndExplicitProcessScopeSurviveTransportWithoutConflatingReplays() {
        val ids = NotificationIds()
        val first = NotificationDelivery.from("same-game", ids.next())
        val second = NotificationDelivery.from("same-game", ids.next())
        assertNotEquals(first.postId, second.postId)
        assertEquals(1L, first.sequence)
        assertEquals(2L, second.sequence)
        assertEquals(first.sequenceScopeId, second.sequenceScopeId)
        assertEquals(DispatchSequenceScope.PROCESS, first.sequenceScope)
        val restarted = NotificationDelivery.from("same-game", NotificationIds().next())
        assertEquals(1L, restarted.sequence)
        assertNotEquals(first.postId, restarted.postId)
        assertNotEquals(first.sequenceScopeId, restarted.sequenceScopeId)
    }

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
