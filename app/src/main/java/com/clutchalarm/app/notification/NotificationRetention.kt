package com.clutchalarm.app.notification

import kotlinx.coroutines.delay

internal data class ActiveGameNotification(
    val address: NotificationAddress,
    val postedAt: Long,
    val important: Boolean,
)

/** Called serially by the dispatcher. Briefly reserves room for operations still in flight. */
internal class NotificationRetention(private val limit: Int = 12) {
    private val pending = mutableMapOf<NotificationAddress, Pair<ActiveGameNotification, Long>>()
    private val cancelling = mutableMapOf<NotificationAddress, Long>()

    fun makeRoom(
        active: List<ActiveGameNotification>,
        incomingImportant: Boolean,
        elapsedMs: Long,
    ): List<NotificationAddress>? {
        val activeAddresses = active.mapTo(mutableSetOf()) { it.address }
        pending.entries.removeAll { (address, entry) ->
            address in activeAddresses || elapsedMs - entry.second >= SETTLE_MS
        }
        cancelling.values.removeAll { elapsedMs - it >= SETTLE_MS }
        val occupied = (active + pending.values.map { it.first }).filterNot { it.address in cancelling }
        val needed = occupied.size - limit + 1
        if (needed <= 0) return emptyList()
        val removable = occupied.filter { incomingImportant || !it.important }
            .sortedWith(compareBy({ it.important }, { it.postedAt }, { it.address.tag }, { it.address.id }))
        return if (removable.size < needed) null else removable.take(needed).map { it.address }
    }

    fun submitted(post: ActiveGameNotification, elapsedMs: Long) {
        pending[post.address] = post to elapsedMs
    }

    fun cancelled(address: NotificationAddress, elapsedMs: Long) {
        pending.remove(address)
        cancelling[address] = elapsedMs
    }

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}

internal suspend fun confirmFromActiveList(
    submission: NotificationSubmission,
    snapshot: () -> Set<NotificationAddress>?,
): NotificationConfirmation {
    var published = false
    var cleaned = emptySet<NotificationAddress>()
    // Posting and cancellation are asynchronous. A missing record is inconclusive.
    for (waitMs in listOf(350L, 550L, 900L)) {
        delay(waitMs)
        val active = snapshot() ?: continue
        published = published || submission.address?.let { it in active } == true
        cleaned = submission.cleanupRequested - active
        if ((published || submission.address == null) && cleaned == submission.cleanupRequested) break
    }
    return NotificationConfirmation(published, cleaned)
}
