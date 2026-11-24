package com.clutchalarm.app.notification

import android.content.Intent
import android.os.Bundle

/** Explicit notification route. Legacy game-only notifications still navigate but are not counted. */
internal object NotificationEntryIntent {
    const val POST_ID = "com.clutchalarm.app.POST_ID"
    const val SEQUENCE = "com.clutchalarm.app.POST_SEQUENCE"
    const val SCOPE_ID = "com.clutchalarm.app.POST_SEQUENCE_SCOPE_ID"
    const val SCOPE = "com.clutchalarm.app.POST_SEQUENCE_SCOPE"

    fun put(intent: Intent, delivery: NotificationDelivery) {
        intent.putExtra(AndroidNotificationDispatcher.EXTRA_GAME_ID, delivery.gameId)
            .putExtra(POST_ID, delivery.postId).putExtra(SEQUENCE, delivery.sequence)
            .putExtra(SCOPE_ID, delivery.sequenceScopeId).putExtra(SCOPE, delivery.sequenceScope.name)
    }

    fun read(intent: Intent): GameDetailEntry? {
        if (intent.action != AndroidNotificationDispatcher.ACTION_OPEN_GAME) return null
        val game = intent.getStringExtra(AndroidNotificationDispatcher.EXTRA_GAME_ID)?.takeIf { it.isNotBlank() } ?: return null
        val delivery = runCatching {
            NotificationDelivery(game, requireNotNull(intent.getStringExtra(POST_ID)), intent.getLongExtra(SEQUENCE, 0),
                requireNotNull(intent.getStringExtra(SCOPE_ID)), DispatchSequenceScope.valueOf(requireNotNull(intent.getStringExtra(SCOPE))))
        }.getOrNull()
        return GameDetailEntry(game, DetailEntrySource.NOTIFICATION, delivery,
            if (delivery == null) NotificationReadStatus.LEGACY else NotificationReadStatus.PENDING)
    }

    fun save(bundle: Bundle, entry: GameDetailEntry) {
        bundle.putString("entry_game", entry.gameId)
        bundle.putString("entry_source", entry.source.name)
        bundle.putBoolean("entry_confirmed", entry.readStatus in setOf(NotificationReadStatus.NEW,
            NotificationReadStatus.DUPLICATE, NotificationReadStatus.RESTORED, NotificationReadStatus.LEGACY))
        entry.delivery?.let {
            bundle.putString(POST_ID, it.postId); bundle.putLong(SEQUENCE, it.sequence)
            bundle.putString(SCOPE_ID, it.sequenceScopeId); bundle.putString(SCOPE, it.sequenceScope.name)
        }
    }

    fun restore(bundle: Bundle): GameDetailEntry? = runCatching {
        val game = requireNotNull(bundle.getString("entry_game"))
        val source = DetailEntrySource.valueOf(requireNotNull(bundle.getString("entry_source")))
        val delivery = bundle.getString(POST_ID)?.let {
            NotificationDelivery(game, it, bundle.getLong(SEQUENCE), requireNotNull(bundle.getString(SCOPE_ID)),
                DispatchSequenceScope.valueOf(requireNotNull(bundle.getString(SCOPE))))
        }
        val status = when {
            source == DetailEntrySource.LIST -> NotificationReadStatus.NOT_APPLICABLE
            delivery == null -> NotificationReadStatus.LEGACY
            bundle.getBoolean("entry_confirmed") -> NotificationReadStatus.RESTORED
            else -> NotificationReadStatus.PENDING
        }
        GameDetailEntry(game, source, delivery, status)
    }.getOrNull()
}
