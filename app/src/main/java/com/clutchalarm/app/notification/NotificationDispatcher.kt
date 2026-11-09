package com.clutchalarm.app.notification

import com.clutchalarm.app.domain.notification.NotificationCandidate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

enum class NotificationAvailability { AVAILABLE, PERMISSION_REQUIRED, DISABLED }
enum class DeliveryStatus {
    SENT, PERMISSION_DENIED, DISABLED, RETENTION_LIMIT, ERROR, FILTERED, PREFERENCES_UNAVAILABLE,
}
enum class PublicationStatus { NOT_REQUESTED, CHECKING, CONFIRMED, UNCONFIRMED }

fun interface NotificationDispatcher {
    fun dispatch(candidate: NotificationCandidate): DeliveryStatus

    fun dispatchTracked(candidate: NotificationCandidate): NotificationSubmission =
        NotificationSubmission(dispatch(candidate))

    suspend fun confirm(submission: NotificationSubmission): NotificationConfirmation? = null
}

data class NotificationSubmission(
    val status: DeliveryStatus,
    val address: NotificationAddress? = null,
    val cleanupRequested: Set<NotificationAddress> = emptySet(),
)

data class NotificationConfirmation(
    val published: Boolean,
    val cleaned: Set<NotificationAddress>,
)

data class NotificationRecord(
    val candidate: NotificationCandidate,
    val status: DeliveryStatus,
    val address: NotificationAddress? = null,
    val publication: PublicationStatus = PublicationStatus.NOT_REQUESTED,
    val cleaned: Boolean = false,
    val cleanupCount: Int = 0,
    val recordId: Long = 0,
)

data class NotificationAddress(val tag: String, val id: Int)

/** The tag keeps system identities distinct even when a new process starts numbering at 1. */
internal class NotificationIds {
    private val tag = UUID.randomUUID().toString()
    private val counter = AtomicInteger()
    fun next() = NotificationAddress(tag, counter.incrementAndGet())
}
