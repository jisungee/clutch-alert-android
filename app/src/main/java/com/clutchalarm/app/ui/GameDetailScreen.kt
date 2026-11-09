package com.clutchalarm.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.clutchalarm.app.domain.model.Bases
import com.clutchalarm.app.domain.model.EventType
import com.clutchalarm.app.domain.model.GameEvent
import com.clutchalarm.app.domain.model.InningHalf
import com.clutchalarm.app.domain.simulation.PlaybackStatus
import com.clutchalarm.app.domain.simulation.PlaybackSpeed
import com.clutchalarm.app.notification.DeliveryStatus
import com.clutchalarm.app.notification.NotificationAvailability
import com.clutchalarm.app.notification.PublicationStatus

@Composable
fun GameDetailScreen(
    detail: GameDetailState.Loaded,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onSpeedChange: (PlaybackSpeed) -> Unit,
    notificationAvailability: NotificationAvailability,
    onSelectTeam: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = detail.playback
    val game = playback.gameState

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${game.inning}회 ${if (game.half == InningHalf.TOP) "초" else "말"}",
                    color = MaterialTheme.colorScheme.primary)
                ScoreRow("원정", game.awayTeam.name, game.awayScore)
                ScoreRow("홈", game.homeTeam.name, game.homeScore)
                Text("볼 ${game.balls}   스트라이크 ${game.strikes}   아웃 ${game.outs}")
            }
        }

        Text(playback.status.label(), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary)
        val (buttonLabel, action) = when (playback.status) {
            PlaybackStatus.IDLE -> "재생" to onPlay
            PlaybackStatus.PLAYING -> "일시정지" to onPause
            PlaybackStatus.PAUSED -> "재개" to onResume
            PlaybackStatus.COMPLETED -> "처음부터 다시 재생" to onPlay
        }
        Button(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(buttonLabel) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (speed in PlaybackSpeed.entries) {
                FilterChip(
                    selected = playback.speed == speed,
                    onClick = { onSpeedChange(speed) },
                    label = { Text("${speed.multiplier}x") },
                )
            }
        }
        if (playback.status == PlaybackStatus.PLAYING || playback.status == PlaybackStatus.PAUSED) {
            OutlinedButton(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
                Text("처음부터 다시 재생")
            }
        }
        if (playback.status != PlaybackStatus.IDLE) {
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("초기화") }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("응원 팀", style = MaterialTheme.typography.titleMedium)
                for (team in listOf(game.awayTeam, game.homeTeam)) {
                    FilterChip(
                        selected = detail.preferredTeamId == team.id,
                        onClick = { onSelectTeam(team.id) },
                        label = { Text(team.name) },
                    )
                }
                if (detail.preferredTeamId == null) {
                    Text("팀을 선택하면 이후 경기 이벤트의 알림을 받을 수 있습니다.")
                } else {
                    when (notificationAvailability) {
                        NotificationAvailability.AVAILABLE -> Text("경기 알림 사용 가능")
                        NotificationAvailability.PERMISSION_REQUIRED -> {
                            Text("알림 권한이 허용되지 않았습니다. 재생은 계속할 수 있습니다.")
                            OutlinedButton(onClick = onRequestPermission) { Text("알림 권한 요청") }
                            OutlinedButton(onClick = onOpenNotificationSettings) { Text("알림 설정 열기") }
                        }
                        NotificationAvailability.DISABLED -> {
                            Text("시스템 설정에서 앱 또는 모의 경기 알림이 꺼져 있습니다.")
                            OutlinedButton(onClick = onOpenNotificationSettings) { Text("알림 설정 열기") }
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("주자 상황", style = MaterialTheme.typography.titleMedium)
                BaseballDiamond(game.bases, Modifier.fillMaxWidth().height(148.dp))
                Text("1루  ${game.bases.first?.name ?: "비어 있음"}")
                Text("2루  ${game.bases.second?.name ?: "비어 있음"}")
                Text("3루  ${game.bases.third?.name ?: "비어 있음"}")
                Text("타자  ${game.batter?.name ?: "—"}")
                Text("투수  ${game.pitcher?.name ?: "—"}")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("최근 플레이", style = MaterialTheme.typography.titleMedium)
                val event = detail.latestEvent
                if (event == null) {
                    Text("아직 발생한 플레이가 없습니다.")
                } else {
                    Text(event.eventType.label(), color = MaterialTheme.colorScheme.primary)
                    Text(event.description())
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("알림 기록", style = MaterialTheme.typography.titleMedium)
                if (detail.notificationHistory.isEmpty()) Text("생성된 알림 후보가 없습니다.")
                detail.notificationHistory.asReversed().forEach { record ->
                    val candidate = record.candidate
                    Text(candidate.title, color = MaterialTheme.colorScheme.primary)
                    Text(candidate.body)
                    Text("${candidate.type.title} · 이벤트 ${candidate.eventId}")
                    Text("후보 생성 · ${record.status.label()}")
                    record.publication.label()?.let { Text(it) }
                    if (record.cleaned) Text("기존 알림 정리됨")
                    if (record.cleanupCount > 0) Text("기존 알림 ${record.cleanupCount}개 정리됨")
                }
            }
        }
    }
}

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

@Composable
private fun ScoreRow(side: String, team: String, score: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("$side  $team", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(score.toString(), style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun BaseballDiamond(bases: Bases, modifier: Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension * 0.36f
        val first = center + Offset(radius, 0f)
        val second = center + Offset(0f, -radius)
        val third = center + Offset(-radius, 0f)
        val home = center + Offset(0f, radius)
        val field = Path().apply {
            moveTo(home.x, home.y)
            lineTo(first.x, first.y)
            lineTo(second.x, second.y)
            lineTo(third.x, third.y)
            close()
        }
        val chalk = Color(0xFFDDECE0)
        drawPath(field, Color(0xFF276044))
        drawPath(field, chalk, style = Stroke(2.dp.toPx()))
        drawCircle(chalk, radius = 3.dp.toPx(), center = center)
        for ((base, occupied) in listOf(
            first to (bases.first != null), second to (bases.second != null),
            third to (bases.third != null), home to false,
        )) {
            drawCircle(if (occupied) Color(0xFFFFCC64) else chalk, 7.dp.toPx(), base)
        }
    }
}

private fun PlaybackStatus.label() = when (this) {
    PlaybackStatus.IDLE -> "재생 대기"
    PlaybackStatus.PLAYING -> "재생 중"
    PlaybackStatus.PAUSED -> "일시정지"
    PlaybackStatus.COMPLETED -> "재생 완료"
}

private fun EventType.label() = when (this) {
    EventType.GAME_STARTED -> "경기 시작"
    EventType.SINGLE -> "단타"
    EventType.DOUBLE -> "2루타"
    EventType.TRIPLE -> "3루타"
    EventType.HOME_RUN -> "홈런"
    EventType.WALK -> "볼넷"
    EventType.STRIKEOUT -> "삼진"
    EventType.FOUL -> "파울"
    EventType.BATTED_OUT -> "타구 아웃"
    EventType.RUN_SCORED -> "득점"
    EventType.HALF_INNING_CHANGED -> "공수교대"
    EventType.GAME_ENDED -> "경기 종료"
    EventType.FIRST_PITCH -> "첫 투구"
    EventType.HALF_INNING_STARTED -> "공격 이닝 시작"
    EventType.HIT_BY_PITCH -> "사구"
    EventType.REACHED_ON_ERROR -> "실책 출루"
    EventType.DROPPED_THIRD_STRIKE_REACHED -> "낫아웃 출루"
    EventType.FIELDERS_CHOICE_REACHED -> "야수선택 출루"
    EventType.STOLEN_BASE -> "도루"
    EventType.WILD_PITCH -> "폭투"
    EventType.OFFICIAL_REVIEW_STARTED -> "비디오 판독 시작"
    EventType.OFFICIAL_REVIEW_UPHELD -> "비디오 판독 원심 유지"
    EventType.OFFICIAL_REVIEW_OVERTURNED -> "비디오 판독 판정 번복"
    EventType.BROADCAST_REPLAY -> "중계 리플레이"
    EventType.INJURY_SUSPECTED_STOPPAGE -> "부상 의심으로 경기 중단"
    EventType.INJURY_MEDICAL_CHECK -> "의료진 확인"
    EventType.INJURY_SUBSTITUTION -> "부상 교체"
}

private fun GameEvent.description(): String {
    val team = listOf(after.awayTeam, after.homeTeam).firstOrNull { it.id == teamId }
    val runs = (after.homeScore - before.homeScore) + (after.awayScore - before.awayScore)
    return listOfNotNull(
        team?.name,
        players.joinToString(", ") { it.name }.ifEmpty { null },
        if (runs > 0) "${runs}점 득점" else null,
        "원정 ${after.awayScore} · 홈 ${after.homeScore}",
    ).joinToString(" / ")
}
