package com.clutchalarm.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.clutchalarm.app.domain.notification.NotificationType

@Composable
internal fun NotificationSettingsScreen(
    state: NotificationSettingsState,
    onToggle: (NotificationType, Boolean) -> Unit,
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preferences = state.preferences
    LazyColumn(modifier) {
        if (preferences == null) {
            item {
                if (state.error) {
                    Text("알림 설정을 불러오지 못했습니다.")
                    OutlinedButton(onClick = onRetry, enabled = !state.busy) { Text("다시 시도") }
                } else {
                    Text("알림 설정을 불러오는 중입니다.")
                }
            }
        } else {
            item {
                Text("활성 알림 ${preferences.enabledCount}/${NotificationType.entries.size}")
                OutlinedButton(onClick = onEnableAll, enabled = !state.busy) {
                    Text("전체 알림 활성화")
                }
                OutlinedButton(onClick = onDisableAll, enabled = !state.busy) {
                    Text("전체 알림 비활성화")
                }
                if (state.busy) Text("저장 중입니다.")
                if (state.error) Text("저장하지 못했습니다. 다시 변경해 주세요.")
            }
            for ((group, types) in notificationGroups) {
                item {
                    Text(group, Modifier.padding(top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleMedium)
                }
                items(types, key = { it.name }) { type ->
                    val enabled = preferences.isEnabled(type)
                    Row(
                        modifier = Modifier.fillMaxWidth().toggleable(
                            value = enabled,
                            enabled = !state.busy,
                            role = Role.Switch,
                            onValueChange = { onToggle(type, it) },
                        ).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(type.title, Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = null, enabled = !state.busy)
                    }
                }
            }
        }
    }
}

private val notificationGroups = NotificationType.entries.groupBy { type ->
    when (type) {
        NotificationType.GAME_STARTED, NotificationType.OFFENSE_STARTED, NotificationType.OFFENSE_ENDED,
        NotificationType.DEFENSE_STARTED, NotificationType.DEFENSE_ENDED -> "경기 진행"
        NotificationType.REACHED_BASE, NotificationType.HOME_RUN -> "타격·출루"
        NotificationType.RUN_SCORED, NotificationType.TIED, NotificationType.COMEBACK,
        NotificationType.RUN_ALLOWED -> "점수 변화"
        NotificationType.VIDEO_REVIEW, NotificationType.INJURY, NotificationType.BIG_INNING -> "주요 상황"
    }
}
