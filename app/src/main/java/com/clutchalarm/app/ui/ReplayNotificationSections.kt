package com.clutchalarm.app.ui

import com.clutchalarm.app.BuildConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clutchalarm.app.domain.model.GameState
import com.clutchalarm.app.domain.model.InningHalf
import com.clutchalarm.app.notification.DeliveryStatus
import com.clutchalarm.app.notification.NotificationRecord
import com.clutchalarm.app.notification.PublicationStatus

@Composable
internal fun ReplayNotificationSections(detail: GameDetailState.Loaded) {
    key(detail.playback.gameState.gameId, detail.notificationRound) {
        val sent = detail.sentNotifications
        NotificationSection("이번 재생 알림 (${sent.size})", initiallyExpanded = true) {
            if (sent.isEmpty()) {
                Text("이번 재생에서 발송된 알림이 없어요")
            } else {
                if (BuildConfig.QA_DIAGNOSTICS) Text("시스템 발송 요청이 성공한 알림입니다.", style = MaterialTheme.typography.bodySmall)
                RecentRecords(sent) { record ->
                    var showBody by rememberSaveable { mutableStateOf(false) }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(record.candidate.title, Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { showBody = !showBody }) {
                                Text(if (showBody) "본문 접기" else "본문 보기")
                            }
                        }
                        Text(record.gameState.notificationSituation(), style = MaterialTheme.typography.bodySmall)
                        if (showBody) Text(record.candidate.body)
                    }
                }
            }
        }

        if (BuildConfig.QA_DIAGNOSTICS) NotificationSection("알림 판정 상세", initiallyExpanded = false) {
            if (detail.notifications.isEmpty()) Text("이번 재생에서 생성된 알림 후보가 없어요")
            RecentRecords(detail.notifications.asReversed()) { record ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(record.candidate.title, color = MaterialTheme.colorScheme.primary)
                    Text("${record.candidate.type.title} · 이벤트 ${record.candidate.eventId}")
                    Text("후보 생성 · ${record.status.label()}")
                    record.publication.label()?.let { Text(it) }
                    if (record.cleaned) Text("이 알림은 시스템 목록에서 정리됐어요")
                    if (record.cleanupRequestedCount > 0) {
                        Text(when {
                            !record.cleanupChecked -> "기존 시스템 알림 정리 확인 중"
                            record.cleanupCount == record.cleanupRequestedCount ->
                                "기존 시스템 알림 ${record.cleanupCount}개 정리 확인"
                            else -> "기존 시스템 알림 ${record.cleanupRequestedCount}개 중 ${record.cleanupCount}개 정리 확인"
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationSection(title: String, initiallyExpanded: Boolean, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "접기" else "펼치기") }
            }
            if (expanded) content()
        }
    }
}

@Composable
private fun RecentRecords(records: List<NotificationRecord>, row: @Composable (NotificationRecord) -> Unit) {
    var visibleCount by rememberSaveable { mutableIntStateOf(RECORD_PAGE_SIZE) }
    for (record in records.take(visibleCount)) {
        key(record.candidate.id) { row(record) }
    }
    if (records.size > visibleCount) {
        TextButton(onClick = { visibleCount += RECORD_PAGE_SIZE }) { Text("더 보기") }
    }
}

private fun GameState.notificationSituation(): String {
    val inningLabel = "${inning}회 ${if (half == InningHalf.TOP) "초" else "말"}"
    val runners = listOfNotNull(
        bases.first?.let { "1루 ${it.name}" },
        bases.second?.let { "2루 ${it.name}" },
        bases.third?.let { "3루 ${it.name}" },
    )
    return "$inningLabel · ${outs ?: "미상"}아웃 · ${if (!bases.known) "주자 미상" else runners.joinToString(", ").ifEmpty { "주자 없음" }}"
}

private const val RECORD_PAGE_SIZE = 3

private fun DeliveryStatus.label() = when (this) {
    DeliveryStatus.SENT -> "시스템 발송 요청 성공"
    DeliveryStatus.PERMISSION_DENIED -> "권한 거부"
    DeliveryStatus.DISABLED -> "시스템 알림 꺼짐"
    DeliveryStatus.RETENTION_LIMIT -> "중요 알림 보존으로 발송 생략"
    DeliveryStatus.ERROR -> "발송 요청 실패"
    DeliveryStatus.FILTERED -> "유형 설정으로 발송 생략"
    DeliveryStatus.PREFERENCES_UNAVAILABLE -> "설정 확인 전 발송 생략"
}

private fun PublicationStatus.label() = when (this) {
    PublicationStatus.NOT_REQUESTED -> null
    PublicationStatus.CHECKING -> "시스템 게시 확인 중"
    PublicationStatus.CONFIRMED -> "시스템 게시 확인 · 활성 목록"
    PublicationStatus.UNCONFIRMED -> "시스템 게시 미확인"
}
