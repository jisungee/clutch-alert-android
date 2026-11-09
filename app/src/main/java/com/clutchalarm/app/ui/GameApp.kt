package com.clutchalarm.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clutchalarm.app.R
import com.clutchalarm.app.domain.model.GameStatus

private val BroadcastColors = darkColorScheme(
    primary = Color(0xFF9FE0B3),
    background = Color(0xFF101713),
    surface = Color(0xFF19221D),
    surfaceVariant = Color(0xFF26352D),
    onSurface = Color(0xFFF0F5F0),
    onSurfaceVariant = Color(0xFFC2D1C5),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameApp(viewModel: GameViewModel) {
    val games by viewModel.games.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val isDetail = detail != null
    BackHandler(enabled = isDetail, onBack = viewModel::closeGame)

    MaterialTheme(colorScheme = BroadcastColors) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(if (isDetail) "경기 중계" else stringResource(R.string.app_name)) },
                navigationIcon = {
                    if (isDetail) TextButton(onClick = viewModel::closeGame) { Text("목록") }
                },
            )
        }) { padding ->
            val content = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
            when (val selected = detail) {
                null -> GameList(games, viewModel::selectGame, viewModel::loadGames, content)
                GameDetailState.Loading -> CenteredMessage(content) { CircularProgressIndicator() }
                GameDetailState.Error -> CenteredMessage(content) {
                    Text("경기 데이터를 불러오지 못했습니다.")
                    OutlinedButton(onClick = viewModel::closeGame) { Text("목록으로") }
                }
                is GameDetailState.Loaded -> GameDetailScreen(
                    detail = selected,
                    onPlay = viewModel::play,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onReset = viewModel::reset,
                    modifier = content,
                )
            }
        }
    }
}

@Composable
private fun GameList(
    state: GameListState,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    when (state) {
        GameListState.Loading -> CenteredMessage(modifier) { CircularProgressIndicator() }
        GameListState.Error -> CenteredMessage(modifier) {
            Text("경기 목록을 불러오지 못했습니다.")
            OutlinedButton(onClick = onRetry) { Text("다시 시도") }
        }
        is GameListState.Loaded -> LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("모의 경기", style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 12.dp))
            }
            if (state.games.isEmpty()) item { Text("표시할 경기가 없습니다.") }
            items(state.games, key = { it.gameId }) { game ->
                Card(onClick = { onSelect(game.gameId) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(game.status.label(), color = MaterialTheme.colorScheme.primary)
                        Text("원정  ${game.awayTeam.name}", style = MaterialTheme.typography.titleMedium)
                        Text("홈  ${game.homeTeam.name}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

private fun GameStatus.label() = when (this) {
    GameStatus.NOT_STARTED -> "경기 시작 전"
    GameStatus.IN_PROGRESS -> "경기 진행 중"
    GameStatus.FINISHED -> "경기 종료"
}
