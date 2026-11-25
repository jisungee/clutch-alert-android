package com.clutchalarm.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clutchalarm.app.domain.model.GameState
import com.clutchalarm.app.domain.model.KboTeams
import com.clutchalarm.app.domain.repository.GameScenario

@Composable
internal fun GameSelectionScreen(
    state: GameListState,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    preferredTeamId: String? = null,
) {
    when (state) {
        GameListState.Loading -> CenteredMessage(modifier) {
            CircularProgressIndicator()
            Text("모의 경기를 불러오는 중입니다.")
        }
        GameListState.Error -> CenteredMessage(modifier) {
            Text("경기 목록을 불러오지 못했습니다.", color = MaterialTheme.colorScheme.error)
            ClutchOutlinedButton(onClick = onRetry) { Text("다시 시도") }
        }
        is GameListState.Loaded -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("실제 경기 기반 재현 ${state.replayGames.size}개", fontWeight = FontWeight.Bold)
                        Text("실시간 중계가 아닌 모의 재생입니다. 응원팀이 참여하는 경기는 바로 재생할 수 있어요.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val recommended = state.recommendedGames(preferredTeamId)
            if (recommended.isNotEmpty()) {
                item { SectionLabel("${KboTeams.find(preferredTeamId)?.name ?: "응원팀"} 추천 경기") }
                items(recommended, key = { "recommended:${it.gameId}" }) { game ->
                    ReplayCard(game, state.scenarios[game.gameId], onSelect, recommended = true)
                }
            }
            item { SectionLabel("전체 경기 · ${state.replayGames.size}개") }
            if (state.replayGames.isEmpty()) item { Text("표시할 모의 경기가 없습니다.") }
            items(state.replayGames, key = { "all:${it.gameId}" }) { game ->
                ReplayCard(game, state.scenarios[game.gameId], onSelect)
            }

        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold)
}

@Composable
private fun ReplayCard(game: GameState, scenario: GameScenario?, onSelect: (String) -> Unit, recommended: Boolean = false) {
    Card(onClick = { onSelect(game.gameId) }, modifier = Modifier.fillMaxWidth(), shape = ClutchStyle.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (recommended) "응원팀 추천 · 모의 재생" else "실제 경기 기반 · 모의 재생",
                    Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                ChevronMark()
            }
            scenario?.date?.let { Text(it.replace('-', '.'), style = MaterialTheme.typography.labelLarge) }
            TeamRow("원정", game.awayTeam.id, game.awayTeam.name)
            TeamRow("홈", game.homeTeam.id, game.homeTeam.name)
            Text("1배속 예상 재생 · ${replayDurationLabel(scenario?.durationMs ?: 0)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text("선별 플레이와 이닝 득점 합계 · 미상 정보는 미상으로 표시",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TeamRow(side: String, teamId: String, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(side, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TeamCrest(teamId, Modifier.size(32.dp))
        Text(name, style = MaterialTheme.typography.titleMedium, color = teamPalette(teamId).primaryColor)
    }
}

internal fun replayDurationLabel(durationMs: Long): String {
    val seconds = (durationMs + 999) / 1000
    return "약 ${seconds / 60}분 ${seconds % 60}초"
}
