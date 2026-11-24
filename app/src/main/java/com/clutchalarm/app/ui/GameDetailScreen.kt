package com.clutchalarm.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import com.clutchalarm.app.notification.NotificationAvailability

@OptIn(ExperimentalLayoutApi::class)
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
    savedTeamName: String? = null,
) {
    val playback = detail.playback
    val game = playback.gameState

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = ClutchStyle.card, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (detail.notificationsEnabled) "모의 경기" else "PDF 기반 테스트 경기 · 알림 발송 보류", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TeamName("원정", game.awayTeam.name, game.awayTeam.id, Modifier.weight(1f))
                    Text("${game.awayScore} : ${game.homeScore}",
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp))
                    TeamName("홈", game.homeTeam.name, game.homeTeam.id, Modifier.weight(1f))
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${game.inning}회 ${if (game.half == InningHalf.TOP) "초" else "말"}",
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${game.outs} OUT", style = MaterialTheme.typography.labelLarge)
                        }
                        BaseballDiamond(game.bases, Modifier.weight(1f).height(56.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CountIndicator("B", "볼", game.balls, 3, Color(0xFF16845B))
                    CountIndicator("S", "스트라이크", game.strikes, 2, Color(0xFFC38A00))
                    CountIndicator("O", "아웃", game.outs, 3, MaterialTheme.colorScheme.primary)
                }
                Text(listOfNotNull(
                    game.bases.first?.let { "1루 ${it.name}" },
                    game.bases.second?.let { "2루 ${it.name}" },
                    game.bases.third?.let { "3루 ${it.name}" },
                ).joinToString(" · ").ifEmpty { "주자 없음" },
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        Surface(shape = ClutchStyle.card, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("최신 경기 상황", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp)
                Text("타자  ${game.batter?.name ?: "미제공"}   ·   투수  ${game.pitcher?.name ?: "미제공"}",
                    style = MaterialTheme.typography.bodyMedium)
                val event = detail.latestEvent
                if (event == null) {
                    Text("재생을 시작하면 최신 경기 상황이 표시됩니다.")
                } else {
                    Text(event.sourceLabel ?: event.eventType.label(), color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)
                    Text(event.sourceSummary ?: event.description())
                }
            }
        }

        val teams = listOf(game.awayTeam, game.homeTeam)
        val selectedTeam = teams.firstOrNull { it.id == detail.preferredTeamId }
        if (selectedTeam == null) {
            Text("이번 경기의 알림 팀을 선택하세요. 저장된 응원팀은 바뀌지 않아요.",
                style = MaterialTheme.typography.bodyMedium)
        }
        Text("저장한 응원팀 · ${savedTeamName ?: "미선택"}", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (team in teams) {
                FilterChip(selected = team.id == detail.preferredTeamId, onClick = { onSelectTeam(team.id) }, label = { Text(team.name) })
            }
        }
        when (notificationAvailability) {
            NotificationAvailability.AVAILABLE -> Text(
                "시스템 알림을 누르면 이 경기 상세로 돌아옵니다.",
                style = MaterialTheme.typography.bodySmall)
            NotificationAvailability.PERMISSION_REQUIRED -> {
                Text("알림 권한이 꺼져 있어요. 경기 재생은 가능하지만 시스템 알림은 오지 않아요. 알림을 받으려면 권한을 허용해주세요.")
                ClutchOutlinedButton(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) { Text("알림 권한 허용하기") }
                ClutchOutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) { Text("시스템 알림 설정 열기") }
            }
            NotificationAvailability.DISABLED -> {
                Text("시스템 알림이 꺼져 있어요. 경기 재생은 가능하지만 시스템 알림은 오지 않아요. 설정에서 앱 알림과 ‘모의 경기 알림 (팝업)’을 켜주세요.")
                ClutchOutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) { Text("시스템 알림 설정 열기") }
            }
        }
        Text(playback.status.label(), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary)
        val (buttonLabel, action) = when (playback.status) {
            PlaybackStatus.IDLE -> "모의 경기 재생하기" to onPlay
            PlaybackStatus.PLAYING -> "일시정지" to onPause
            PlaybackStatus.PAUSED -> "재개" to onResume
            PlaybackStatus.COMPLETED -> "처음부터 다시 재생" to onPlay
        }
        ClutchButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(buttonLabel) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (speed in PlaybackSpeed.entries) {
                FilterChip(
                    selected = playback.speed == speed,
                    onClick = { onSpeedChange(speed) },
                    label = { Text("${speed.multiplier}x") },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (playback.status == PlaybackStatus.PLAYING || playback.status == PlaybackStatus.PAUSED) {
                ClutchOutlinedButton(onClick = onPlay, modifier = Modifier.weight(1f)) { Text("처음부터 재생") }
            }
            if (playback.status != PlaybackStatus.IDLE) {
                ClutchOutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("초기화") }
            }
        }

        Text("이닝별 득점 · H/E · 라인업 · 선수 기록은 제공하지 않습니다.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        ReplayNotificationSections(detail)
    }
}

@Composable
private fun TeamName(side: String, name: String, teamId: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(side, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (com.clutchalarm.app.domain.model.KboTeams.find(teamId) != null) TeamCrest(teamId, Modifier.size(28.dp))
        Text(name, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = teamPalette(teamId).primaryColor)
    }
}

@Composable
private fun CountIndicator(label: String, description: String, count: Int?, total: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$description ${count ?: "미상"}" }) {
        Text("$label  ${count ?: "—"}", style = MaterialTheme.typography.labelLarge)
        if (count != null) Canvas(Modifier.height(10.dp).width(52.dp)) {
            val step = size.width / total
            repeat(total) { index ->
                drawCircle(if (index < count) color else Color(0xFFDDE0E6),
                    radius = minOf(step / 3, size.height / 2),
                    center = Offset(step * (index + 0.5f), size.height / 2))
            }
        }
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
        val chalk = Color(0xFF424B5C)
        drawPath(field, Color(0xFFF1F2F5))
        drawPath(field, chalk, style = Stroke(2.dp.toPx()))
        drawCircle(chalk, radius = 3.dp.toPx(), center = center)
        for ((base, occupied) in listOf(
            first to (bases.first != null), second to (bases.second != null),
            third to (bases.third != null), home to false,
        )) {
            val diamond = Path().apply {
                moveTo(base.x, base.y - 7.dp.toPx())
                lineTo(base.x + 7.dp.toPx(), base.y)
                lineTo(base.x, base.y + 7.dp.toPx())
                lineTo(base.x - 7.dp.toPx(), base.y)
                close()
            }
            drawPath(diamond, if (occupied) Color(0xFFFFCC32) else Color.White)
            drawPath(diamond, chalk, style = Stroke(2.dp.toPx()))
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
    EventType.PITCHER_CHANGE -> "투수 교체"
    EventType.PITCHER_STAT -> "투수 기록"
    EventType.DEFENSE_HIGHLIGHT -> "호수비"
    EventType.BENCH_CLEARING -> "벤치클리어링"
    EventType.SNAPSHOT -> "원본 상태 스냅샷"
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
