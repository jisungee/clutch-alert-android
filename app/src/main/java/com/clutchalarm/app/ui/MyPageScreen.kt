package com.clutchalarm.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clutchalarm.app.domain.model.KboTeams
import com.clutchalarm.app.domain.notification.NotificationPreferences
import com.clutchalarm.app.domain.notification.configurableNotificationTypes

@Composable
internal fun MyPageScreen(
    preferences: NotificationPreferences,
    onChangeTeam: () -> Unit,
    onNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().height(104.dp).background(MaterialTheme.colorScheme.primary))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                            ProfileMark(Modifier.padding(14.dp).size(32.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("클러치알림 체험 중", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Text("계정 없이 모의 경기를 즐겨보세요", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MyPageSettingRow("응원팀 변경", KboTeams.find(preferences.preferredTeamId)?.name ?: "선택한 팀 없음",
                        true, onChangeTeam)
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    MyPageSettingRow("알림 설정", "${preferences.enabledCount}/${configurableNotificationTypes.size}개 사용",
                        false, onNotificationSettings)
                }
            }
            Text("저장한 응원팀과 알림 설정으로\n모의 경기의 중요한 순간을 함께하세요.",
                Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MyPageSettingRow(title: String, value: String, teamMark: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val color = MaterialTheme.colorScheme.primary
            if (teamMark) BaseballMark(Modifier.size(24.dp), color)
            else Canvas(Modifier.size(24.dp)) {
                val stroke = Stroke(2.dp.toPx())
                drawArc(color, 180f, 180f, false, Offset(size.width * .2f, size.height * .15f),
                    Size(size.width * .6f, size.height * .9f), style = stroke)
                drawLine(color, Offset(size.width * .2f, size.height * .65f),
                    Offset(size.width * .8f, size.height * .65f), stroke.width)
                drawCircle(color, size.width * .07f, Offset(center.x, size.height * .82f))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
internal fun ProfileMark(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier) {
        val stroke = Stroke(2.dp.toPx())
        drawCircle(color, size.minDimension * .17f, Offset(center.x, size.height * .27f), style = stroke)
        drawArc(color, 180f, 180f, false, Offset(size.width * .15f, size.height * .52f),
            Size(size.width * .7f, size.height * .6f), style = stroke)
        drawLine(color, Offset(size.width * .15f, size.height * .82f),
            Offset(size.width * .85f, size.height * .82f), stroke.width)
    }
}
