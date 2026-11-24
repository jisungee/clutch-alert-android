package com.clutchalarm.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clutchalarm.app.domain.model.KboTeams

@Composable
internal fun TeamSelectionScreen(
    state: NotificationSettingsState,
    onSelect: (String) -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editor = state.editor ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (editor.onboarding) "응원하는 팀을 선택해주세요!" else "응원팀을 변경해주세요",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("KBO 10개 구단 중 한 팀을 선택하세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (editor.onboarding) Text("1 / 2 · 응원팀 선택", color = MaterialTheme.colorScheme.primary)
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp)) {
            items(KboTeams.all, key = { it.id }) { team ->
                val selected = editor.draft.preferredTeamId == team.id
                Card(onClick = { onSelect(team.id) }, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 108.dp), shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (selected) "✓" else team.id, color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(team.name, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (state.error) Text("저장하지 못했습니다. 선택은 유지되어 있습니다. 다시 저장해주세요.", color = MaterialTheme.colorScheme.error)
        Button(onClick = if (editor.teamOnly) onSave else onNext,
            enabled = !state.busy && editor.draft.preferredTeamId != null,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(if (state.busy) "저장 중…" else if (editor.teamOnly) "변경한 응원팀 저장하기" else "다음")
        }
    }
}
