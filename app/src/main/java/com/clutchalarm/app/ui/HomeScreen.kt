package com.clutchalarm.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeScreen(state: GameListState, onShowGames: () -> Unit, modifier: Modifier = Modifier,
    teamName: String? = null, onQaReturnToLogin: (() -> Unit)? = null) {
    val palette = LocalTeamPalette.current
    val team = com.clutchalarm.app.domain.model.KboTeams.all.firstOrNull { it.name == teamName }
    val gradient = com.clutchalarm.app.domain.model.HomeGradients.find(team?.id)
    val ink = if (gradient != null) Color.White else palette.onPrimaryContainer
    fun opaque(rgb: Int) = Color(0xFF000000L or rgb.toLong())
    val backdrop = if (gradient != null) Modifier.background(Brush.verticalGradient(listOf(opaque(gradient.top), opaque(gradient.bottom))))
        else Modifier.background(MaterialTheme.colorScheme.background)
    Column(modifier.then(backdrop)
        .verticalScroll(rememberScrollState()).padding(ClutchStyle.page),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("내 팀의 중요한 순간", color = ink, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TeamCrest(team?.id, Modifier.size(72.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(teamName ?: "클러치알림", color = ink, style = MaterialTheme.typography.headlineSmall)
                Text(if (teamName != null) "응원하고 있어요!" else "야구의 중요한 순간을 함께해요",
                    color = ink, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Card(onClick = onShowGames, modifier = Modifier.fillMaxWidth(), shape = ClutchStyle.card,
            colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("모의 경기 보러가기", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    ChevronMark()
                }
                Text(when (state) {
                    is GameListState.Loaded -> "${state.games.size}개 시나리오 · 출루부터 역전 홈런까지"
                    GameListState.Loading -> "경기 목록을 준비하고 있어요."
                    GameListState.Error -> "경기 선택 화면에서 다시 불러와주세요."
                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(color = if (gradient != null) Color.White.copy(alpha = com.clutchalarm.app.domain.model.HomeGradient.GUIDE_ALPHA.toFloat()) else palette.primaryContainer, shape = ClutchStyle.card) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("모의 경기, 이렇게 시작해요", color = ink, style = MaterialTheme.typography.titleMedium)
                Text("경기 선택 → 이번 경기 알림 팀 선택 → 재생", color = ink, style = MaterialTheme.typography.bodyMedium)
                Text("알림창에서 클러치알림을 누르면 해당 상세로 돌아옵니다. 알림이 꺼져 있어도 경기 재생은 가능해요.",
                    color = ink, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (onQaReturnToLogin != null) TextButton(onClick = onQaReturnToLogin) {
            Text("QA 로그인 화면으로", color = ink)
        }
    }
}

@Composable
internal fun BaseballMark(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val radius = size.minDimension * 0.44f
        drawCircle(color, radius, style = Stroke(2.dp.toPx()))
        for (side in listOf(-1f, 1f)) {
            val path = Path().apply {
                moveTo(center.x + side * radius * 0.52f, center.y - radius * 0.82f)
                quadraticTo(center.x - side * radius * 0.12f, center.y,
                    center.x + side * radius * 0.52f, center.y + radius * 0.82f)
            }
            drawPath(path, color, style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
internal fun NavigationMark(home: Boolean) {
    val color = LocalContentColor.current
    if (!home) BaseballMark(Modifier.size(24.dp), color)
    else Canvas(Modifier.size(24.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.46f)
            lineTo(center.x, size.height * 0.14f)
            lineTo(size.width * 0.85f, size.height * 0.46f)
            lineTo(size.width * 0.85f, size.height * 0.86f)
            lineTo(size.width * 0.6f, size.height * 0.86f)
            lineTo(size.width * 0.6f, size.height * 0.6f)
            lineTo(size.width * 0.4f, size.height * 0.6f)
            lineTo(size.width * 0.4f, size.height * 0.86f)
            lineTo(size.width * 0.15f, size.height * 0.86f)
            close()
        }
        drawPath(path, color, style = Stroke(2.dp.toPx()))
    }
}
