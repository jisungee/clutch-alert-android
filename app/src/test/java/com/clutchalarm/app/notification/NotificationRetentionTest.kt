package com.clutchalarm.app.notification

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRetentionTest {
    @Test
    fun afterProcessRestartUsesSystemSnapshotAndEvictsOldestOrdinaryFirst() {
        val active = listOf(post(1, important = true)) + (2..25).map { post(it) }
        val removals = NotificationRetention().makeRoom(active.reversed(), false, 0)
        assertEquals((2..15).map { post(it).address }, removals)
        assertEquals(12, active.size - requireNotNull(removals).size + 1)
    }

    @Test
    fun normalPostDoesNotEvictImportantAndImportantPostReplacesOldestIfNeeded() {
        val active = (1..12).map { post(it, important = true) }
        val policy = NotificationRetention()
        assertNull(policy.makeRoom(active, false, 0))
        assertEquals(listOf(post(1).address), policy.makeRoom(active, true, 0))
    }

    @Test
    fun burstReservesCapacityWhileSystemSnapshotHasNotCaughtUp() {
        val policy = NotificationRetention()
        val removed = mutableListOf<NotificationAddress>()
        for (id in 1..30) {
            val evictions = requireNotNull(policy.makeRoom(emptyList(), false, 0))
            for (address in evictions) {
                removed.add(address)
                policy.cancelled(address, 0)
            }
            policy.submitted(post(id), 0)
        }
        assertEquals((1..18).map { post(it).address }, removed)
        assertEquals(listOf(post(19).address), policy.makeRoom(emptyList(), false, 0))
        assertEquals(emptyList<NotificationAddress>(), policy.makeRoom(emptyList(), false, 2_000))
    }

    @Test
    fun delayedSnapshotSeparatesSubmissionFromConfirmedPresenceAndRemoval() = runTest {
        val previous = post(1).address
        val next = post(2).address
        val submission = NotificationSubmission(DeliveryStatus.SENT, next, setOf(previous))
        val snapshots = listOf(setOf(previous), setOf(next)).iterator()
        val result = confirmFromActiveList(submission) { snapshots.next() }
        assertTrue(result.published)
        assertEquals(setOf(previous), result.cleaned)
    }

    @Test
    fun noActiveEvidenceDoesNotConfirmPublicationOrFailedQueryCleanup() = runTest {
        val submission = NotificationSubmission(DeliveryStatus.SENT, post(2).address, setOf(post(1).address))
        val unavailable = confirmFromActiveList(submission) { null }
        assertFalse(unavailable.published)
        assertTrue(unavailable.cleaned.isEmpty())
        val missing = confirmFromActiveList(submission) { setOf(post(1).address) }
        assertFalse(missing.published)
        assertTrue(missing.cleaned.isEmpty())
    }

    private fun post(id: Int, important: Boolean = false) =
        ActiveGameNotification(NotificationAddress("previous-process", id), id.toLong(), important)
}
