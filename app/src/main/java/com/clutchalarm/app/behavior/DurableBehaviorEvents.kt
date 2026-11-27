package com.clutchalarm.app.behavior

import android.content.Context
import android.util.Log
import android.util.AtomicFile
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.clutchalarm.app.BuildConfig
import com.clutchalarm.app.data.notificationInstallationKey
import com.clutchalarm.app.data.notificationOpenStore
import com.clutchalarm.app.data.notificationPreferencesStore
import com.clutchalarm.app.network.HttpApi
import java.io.IOException
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
internal data class QueuedBehaviorEvent(
    val event_id: String,
    val name: String,
    val installationId: String,
    val occurredAtMillis: Long,
    val schemaVersion: Int = 1,
    val action: String? = null,
    val notificationId: String? = null,
    val notificationSequence: Long? = null,
    val gameId: String? = null,
    val flowId: String? = null,
)

@Serializable
private data class PendingBehavior(val id: String, val occurred: Long, val event: BehaviorEvent)

private val journalLock = Any()
private fun journal(context: Context) = File(context.noBackupFilesDir, "behavior-journal-v1")
private fun saveJournal(context: Context, pending: PendingBehavior) = synchronized(journalLock) {
    val directory = journal(context)
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create behavior journal")
    val file = AtomicFile(File(directory, "${pending.id}.json"))
    val output = file.startWrite()
    try {
        output.write(wireJson.encodeToString(pending).toByteArray(Charsets.UTF_8))
        file.finishWrite(output)
    } catch (error: Exception) {
        file.failWrite(output)
        throw error
    }
}

private val outboxKey = stringSetPreferencesKey("behavior_outbox_v1")
private val deadKey = stringSetPreferencesKey("behavior_outbox_rejected_v1")
private val wireJson = Json { encodeDefaults = true }
private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

/** The existing preference and open-receipt stores each hold their own transactional outbox. */
class DurableBehaviorEvents private constructor(private val context: Context) : BehaviorEventRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val appOpen = ProcessAppOpen(this)

    internal fun stage(values: MutablePreferences, event: BehaviorEvent,
                       eventId: String = UUID.randomUUID().toString(), occurred: Long = System.currentTimeMillis()) {
        // Notification events carry the installation ID from the publisher. Other events share its key.
        val install = event.installationId ?: values[notificationInstallationKey] ?: UUID.randomUUID().toString().also {
            values[notificationInstallationKey] = it
        }
        val queued = QueuedBehaviorEvent(eventId, event.name, install, occurred, action = event.action,
            notificationId = event.notificationId, notificationSequence = event.notificationSequence,
            gameId = event.gameId, flowId = event.flowId)
        values[outboxKey] = values[outboxKey].orEmpty() + wireJson.encodeToString(queued)
    }

    internal fun committed(event: BehaviorEvent) {
        DebugBehaviorEvents.record(event)
        scheduleUpload()
    }

    override fun record(event: BehaviorEvent) {
        // A tiny fsynced journal write is intentional: the synchronous recorder must not lose
        // app_open/drop merely because the process dies before a coroutine can run. No network here.
        val pending = PendingBehavior(UUID.randomUUID().toString(), System.currentTimeMillis(), event)
        try {
            saveJournal(context, pending)
            committed(event)
        } catch (_: IOException) {
            // Storage failure cannot block the UI; retry while this process is alive.
            scope.launch {
                var pause = 1000L
                while (true) {
                    try {
                        saveJournal(context, pending)
                        committed(event)
                        break
                    } catch (_: IOException) {
                        delay(pause)
                        pause = (pause * 2).coerceAtMost(60_000L)
                    }
                }
            }
        }
    }

    internal suspend fun promoteJournal() = withContext(Dispatchers.IO) {
        val files = synchronized(journalLock) {
            journal(context).listFiles()?.filter { it.name.endsWith(".json") }.orEmpty()
        }
        for (entry in files) {
            val raw = synchronized(journalLock) {
                try { AtomicFile(entry).readFully().toString(Charsets.UTF_8) }
                catch (missing: java.io.FileNotFoundException) { if (entry.exists()) throw missing else null }
            } ?: continue
            val pending = try { wireJson.decodeFromString<PendingBehavior>(raw) }
            catch (_: IllegalArgumentException) {
                // Retain a corrupted journal for local diagnosis; do not silently discard it.
                synchronized(journalLock) { entry.renameTo(File(entry.parentFile, entry.name + ".rejected")) }
                continue
            }
            context.notificationPreferencesStore.edit { stage(it, pending.event, pending.id, pending.occurred) }
            synchronized(journalLock) { AtomicFile(entry).delete() }
        }
    }

    private fun scheduleUpload() {
        try {
            val work = OneTimeWorkRequestBuilder<BehaviorUploadWorker>()
                .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("clutch-behavior-upload", ExistingWorkPolicy.APPEND_OR_REPLACE, work)
        } catch (_: Exception) {
            if (BuildConfig.DEBUG) Log.w("ClutchBehavior", "Upload scheduling failed; outbox retained")
        }
    }

    fun scheduleRecovery() {
        try {
            val periodic = PeriodicWorkRequestBuilder<BehaviorUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("clutch-behavior-recovery", ExistingPeriodicWorkPolicy.KEEP, periodic)
            scheduleUpload()
        } catch (_: Exception) {
            if (BuildConfig.DEBUG) Log.w("ClutchBehavior", "Recovery scheduling failed; outbox retained")
        }
    }

    companion object {
        @Volatile private var instance: DurableBehaviorEvents? = null
        fun get(context: Context): DurableBehaviorEvents = instance ?: synchronized(this) {
            instance ?: DurableBehaviorEvents(context.applicationContext).also { instance = it }
        }
    }
}

/** Public class/constructor required by WorkManager reflection. No account credential is uploaded. */
class BehaviorUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (BuildConfig.QA_DIAGNOSTICS || BuildConfig.API_BASE_URL.isBlank()) return Result.success()
        val api = HttpApi(BuildConfig.API_BASE_URL)
        val stores = listOf(applicationContext.notificationPreferencesStore, applicationContext.notificationOpenStore)
        try {
            DurableBehaviorEvents.get(applicationContext).promoteJournal()
            // Bounded batch; retry also wakes a queue that has more than one batch.
            var count = 0
            for (store in stores) {
                val pending = store.data.first()[outboxKey].orEmpty().sortedBy {
                    runCatching { wireJson.decodeFromString<QueuedBehaviorEvent>(it).occurredAtMillis }.getOrDefault(0L)
                }
                for (raw in pending) {
                    if (count++ >= 50) return Result.retry()
                    val event = try { wireJson.decodeFromString<QueuedBehaviorEvent>(raw) }
                    catch (_: IllegalArgumentException) { remove(store, raw, true); continue }
                    val payload = wireJson.parseToJsonElement(raw) as JsonObject
                    val response = api.request("POST", "/api/events", payload, null)
                    val ackId = (response.body["event_id"] as? JsonPrimitive)?.contentOrNull
                    val ackStatus = (response.body["status"] as? JsonPrimitive)?.contentOrNull
                    when {
                        response.status == 200 && ackId == event.event_id && ackStatus in setOf("stored", "duplicate") -> remove(store, raw, false)
                        response.status in setOf(400, 409, 422) -> remove(store, raw, true)
                        else -> return Result.retry()
                    }
                }
            }
            return Result.success()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { return Result.retry() }
    }

    private suspend fun remove(store: DataStore<Preferences>, raw: String, rejected: Boolean) {
        store.edit { values ->
            values[outboxKey] = values[outboxKey].orEmpty() - raw
            if (rejected) values[deadKey] = values[deadKey].orEmpty() + raw
        }
        if (rejected && BuildConfig.DEBUG) Log.w("ClutchBehavior", "Rejected event retained for diagnosis")
    }
}
