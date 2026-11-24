package com.clutchalarm.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clutchalarm.app.domain.model.GameState
import com.clutchalarm.app.domain.model.InningHalf

@Composable
internal fun GameSelectionScreen(
    state: GameListState,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("모의 경기 ${state.games.size}개", fontWeight = FontWeight.Bold)
                        Text("실시간 경기가 아닌 준비된 경기 장면입니다. 카드를 눌러 재생해보세요.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (state.games.isEmpty()) item { Text("표시할 모의 경기가 없습니다.") }
            itemsIndexed(state.games, key = { _, game -> game.gameId }) { index, game ->
                val scenario = state.scenarios[game.gameId]
                Card(
                    onClick = { onSelect(game.gameId) }, modifier = Modifier.fillMaxWidth(),
                    shape = ClutchStyle.card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("모의 경기 · ${(index + 1).toString().padStart(2, '0')}",
                                Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            ChevronMark()
                        }
                        Text(scenario?.name ?: "경기 시나리오", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TeamCrest(game.awayTeam.id, Modifier.size(28.dp))
                                    Text(game.awayTeam.name, style = MaterialTheme.typography.bodyMedium, color = teamPalette(game.awayTeam.id).primaryColor)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TeamCrest(game.homeTeam.id, Modifier.size(28.dp))
                                    Text(game.homeTeam.name, style = MaterialTheme.typography.bodyMedium, color = teamPalette(game.homeTeam.id).primaryColor)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${game.awayScore} : ${game.homeScore}", style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold)
                                Text("시작 점수", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(game.startLabel(), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        scenario?.let { Text(it.description, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }
}

private fun GameState.startLabel() = "${inning}회 ${if (half == InningHalf.TOP) "초" else "말"}부터 재생"
