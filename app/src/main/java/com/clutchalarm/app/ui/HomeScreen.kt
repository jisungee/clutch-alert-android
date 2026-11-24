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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeScreen(state: GameListState, onShowGames: () -> Unit, modifier: Modifier = Modifier,
    teamName: String? = null, onQaReturnToLogin: (() -> Unit)? = null) {
    Column(
        modifier.background(Brush.verticalGradient(listOf(Color(0xFFBE1536), Color(0xFF800F2C))))
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("BASEBALL · CLUTCH ALARM", color = Color(0xFFFFDCE4), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BaseballMark(Modifier.size(76.dp), Color.White)
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (teamName == null) "야구의 중요한 순간,\n클러치알림과 함께" else "$teamName\n응원하고 있어요!", color = Color.White,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("오늘은 모의 경기로 만나보세요", color = Color(0xFFFFDCE4),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        Surface(color = Color(0x33FFFFFF), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("모의 경기", color = Color.White, fontWeight = FontWeight.Bold)
                Text("아래에서 경기를 고른 뒤, 상세에서 이번 경기의 알림 팀을 선택하고 재생하세요.",
                    color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Card(
            onClick = onShowGames, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("모의 경기 보러가기", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = Color(0xFF172238))
                    Text("›", color = Color(0xFFBE1536), style = MaterialTheme.typography.headlineMedium)
                }
                Text(when (state) {
                    is GameListState.Loaded -> "${state.games.size}개 시나리오 중 원하는 경기를 선택하세요."
                    GameListState.Loading -> "경기 목록을 준비하고 있어요."
                    GameListState.Error -> "경기 선택 화면에서 목록을 다시 불러올 수 있어요."
                }, color = Color(0xFF555E70), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("첫 투구부터 출루, 역전 홈런까지", color = Color.White,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("알림이 오면 화면 위에서 알림창을 내려 클러치알림의 알림을 눌러보세요. 해당 경기 상세로 돌아올 수 있어요. 알림이 꺼져 있어도 경기 재생은 가능합니다.",
            color = Color(0xFFFFDCE4), style = MaterialTheme.typography.bodyMedium)
        if (onQaReturnToLogin != null) TextButton(onClick = onQaReturnToLogin) {
            Text("QA 로그인 화면으로", color = Color.White)
        }
        Spacer(Modifier.height(12.dp))
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
