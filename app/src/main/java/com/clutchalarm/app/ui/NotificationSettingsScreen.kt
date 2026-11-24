package com.clutchalarm.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.clutchalarm.app.domain.model.KboTeams
import com.clutchalarm.app.domain.notification.NotificationType
import com.clutchalarm.app.domain.notification.configurableNotificationTypes

@Composable
internal fun NotificationSettingsScreen(
    state: NotificationSettingsState,
    onToggle: (NotificationType, Boolean) -> Unit,
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit,
    onHideWhenLosing: (Boolean) -> Unit,
    onCategory: (NotificationCategory) -> Unit,
    onCategoryEnabled: (NotificationCategory, Boolean) -> Unit,
    onChangeTeam: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editor = state.editor ?: return
    val preferences = editor.draft
    Column(modifier) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp)) {
            item {
                if (editor.onboarding) Text("2 / 2 · 알림 선택", color = MaterialTheme.colorScheme.primary)
                Text("저장하기 전까지 기존 설정이 유지됩니다.", style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("응원팀 · ${KboTeams.find(preferences.preferredTeamId)?.name ?: "미선택"}", Modifier.weight(1f))
                    TextButton(onClick = onChangeTeam, enabled = !state.busy) { Text("변경") }
                }
                Text("선택한 알림 ${preferences.enabledCount}/${configurableNotificationTypes.size}")
            }
            if (editor.page == SettingsPage.DETAILS) {
                val category = editor.category ?: return@LazyColumn
                item {
                    Text(category.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    GroupSelection("이 카테고리 알림", category.types.count(preferences::isEnabled), category.types.size, !state.busy) {
                        onCategoryEnabled(category, it)
                    }
                }
                category.types.forEach { type -> item(key = type.name) {
                    val title = if (type == NotificationType.OFFENSE_STARTED) "이닝 시작·종료 (공수 교대)" else type.title
                    SettingSwitch(title, preferences.isEnabled(type), !state.busy) { onToggle(type, it) }
                } }
            } else {
                item {
                    GroupSelection("전체 알림", preferences.enabledCount, configurableNotificationTypes.size, !state.busy) {
                        if (it) onEnableAll() else onDisableAll()
                    }
                }
                NotificationCategory.entries.forEach { category -> item(key = category.name) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable(enabled = category.types.isNotEmpty() && !state.busy) { onCategory(category) }) {
                                Text(category.title, fontWeight = FontWeight.Bold)
                                Text(if (category.types.isEmpty()) "현재 제공되는 알림이 없습니다" else
                                    "${selectionLabel(category.types.count(preferences::isEnabled), category.types.size)} · 세부 항목 보기 ›",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            GroupCheckbox(category.title, category.types.count(preferences::isEnabled), category.types.size, !state.busy) {
                                onCategoryEnabled(category, it)
                            }
                        }
                    }
                } }
            }
            item {
                HorizontalDivider()
                SettingSwitch("지는 중 숨기기", preferences.hideWhenLosing, !state.busy, onHideWhenLosing)
                Text("뒤처지면 동점·경기 종료까지 숨기고 다시 앞서는 플레이부터 받습니다.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.error) Text("저장하지 못했습니다. 편집한 값은 유지됩니다. 다시 저장해주세요.", color = MaterialTheme.colorScheme.error)
        ClutchButton(onClick = onSave, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(if (state.busy) "저장 중…" else "선택한 설정 저장하기")
        }
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

private fun selectionLabel(selected: Int, total: Int): String = when {
    selected == 0 -> "모두 꺼짐"
    selected == total -> "모두 켜짐"
    else -> "일부 켜짐"
} + " ($selected/$total)"

@Composable
private fun GroupSelection(title: String, selected: Int, total: Int, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(selectionLabel(selected, total), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GroupCheckbox(title, selected, total, enabled, onChange)
    }
}

@Composable
private fun GroupCheckbox(title: String, selected: Int, total: Int, enabled: Boolean, onChange: (Boolean) -> Unit) {
    TriStateCheckbox(modifier = Modifier.semantics { contentDescription = "$title: ${selectionLabel(selected, total)}" }, state = when {
        selected == 0 -> ToggleableState.Off
        selected == total -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }, onClick = { onChange(selected != total) }, enabled = enabled && total > 0)
}
