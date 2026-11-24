package com.clutchalarm.app.notification

/** Transport ordering only. Analytics ordering is deliberately not decided here. */
enum class DispatchSequenceScope { PROCESS, INSTALLATION }

data class NotificationDelivery(
    val gameId: String,
    val postId: String,
    val sequence: Long,
    val sequenceScopeId: String,
    val sequenceScope: DispatchSequenceScope = DispatchSequenceScope.PROCESS,
) {
    init {
        require(gameId.isNotBlank() && sequenceScopeId.isNotBlank())
        require(sequence > 0 && postId.isNotBlank())
        require(sequenceScope != DispatchSequenceScope.PROCESS || postId == "$sequenceScopeId:$sequence")
    }
    companion object {
        fun from(gameId: String, address: NotificationAddress) = NotificationDelivery(
            gameId, "${address.tag}:${address.id}", address.id.toLong(), address.tag,
        )
    }
}

enum class DetailEntrySource { LIST, NOTIFICATION }
enum class NotificationReadStatus { PENDING, NEW, DUPLICATE, RESTORED, UNCONFIRMED, NOT_APPLICABLE, LEGACY }

data class GameDetailEntry(
    val gameId: String,
    val source: DetailEntrySource,
    val delivery: NotificationDelivery? = null,
    val readStatus: NotificationReadStatus = if (delivery == null) NotificationReadStatus.NOT_APPLICABLE else NotificationReadStatus.PENDING,
    val requestId: Long = 0,
) {
    init {
        require(gameId.isNotBlank())
        require(delivery == null || (source == DetailEntrySource.NOTIFICATION && delivery.gameId == gameId))
    }
}

/** True only when this post ID has never been confirmed open on this installation. */
fun interface NotificationOpenRepository {
    suspend fun markOpened(delivery: NotificationDelivery): Boolean
}

internal class InMemoryNotificationOpenRepository : NotificationOpenRepository {
    private val opened = mutableSetOf<String>()
    override suspend fun markOpened(delivery: NotificationDelivery) = synchronized(opened) { opened.add(delivery.postId) }
}

/** Allocates a success number only when the supplied Android publisher returns normally. */
fun interface NotificationPublisher {
    suspend fun publish(fallback: NotificationDelivery, send: (NotificationDelivery) -> Unit): NotificationDelivery
}
