package com.clutchalarm.app.behavior

import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
data class BehaviorEvent(
    val name: String,
    val action: String? = null,
    val notificationId: String? = null,
    val notificationSequence: Long? = null,
    val installationId: String? = null,
    val gameId: String? = null,
    val flowId: String? = null,
)

fun interface BehaviorEventRecorder { fun record(event: BehaviorEvent) }
object NoBehaviorEvents : BehaviorEventRecorder { override fun record(event: BehaviorEvent) = Unit }

class ProcessAppOpen(private val recorder: BehaviorEventRecorder) {
    private val opened = AtomicBoolean()
    fun onScreenVisible() {
        if (opened.compareAndSet(false, true)) recorder.record(BehaviorEvent("app_open"))
    }
}
