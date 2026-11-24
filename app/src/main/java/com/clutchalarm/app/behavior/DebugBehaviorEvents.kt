package com.clutchalarm.app.behavior

import android.util.Log
import com.clutchalarm.app.BuildConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** This is a debug sink only. It does not queue, upload, or call a collection API. */
object DebugBehaviorEvents : BehaviorEventRecorder {
    private val processId = UUID.randomUUID().toString()
    val appOpen = ProcessAppOpen(this)
    override fun record(event: BehaviorEvent) {
        if (BuildConfig.DEBUG) Log.d("ClutchBehavior", "process=$processId ${Json.encodeToString(event)}")
    }
}
