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
import com.clutchalarm.app.auth.AccountFailure

@Composable
internal fun MyPageScreen(
    preferences: NotificationPreferences,
    email: String?,
    accountLoading: Boolean,
    accountError: AccountFailure?,
    deletionError: AccountFailure?,
    onChangeTeam: () -> Unit,
    onNotificationSettings: () -> Unit,
    onLogout: () -> Unit,
    onReloadAccount: () -> Unit,
    onDeleteAccount: () -> Unit,
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
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("로그인한 계정", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            when {
                                accountLoading -> {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text("계정 정보를 불러오는 중…", style = MaterialTheme.typography.bodySmall)
                                }
                                email != null -> Text(email, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                else -> {
                                    Text(if (accountError == AccountFailure.REAUTH_REQUIRED) "계정 확인을 위해 다시 로그인해주세요."
                                        else "계정 정보를 불러오지 못했어요.", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error)
                                    TextButton(onClick = onReloadAccount) { Text("계정 정보 다시 불러오기") }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MyPageSettingRow("응원팀 변경", KboTeams.find(preferences.preferredTeamId)?.name ?: "선택한 팀 없음",
                        MyPageIcon.TEAM, onChangeTeam)
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    MyPageSettingRow("알림 설정", "${preferences.enabledCount}/${configurableNotificationTypes.size}개 사용",
                        MyPageIcon.NOTIFICATION, onNotificationSettings)
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column {
                    MyPageSettingRow("회원 탈퇴", null, MyPageIcon.DELETE, onDeleteAccount,
                        enabled = email != null && !accountLoading)
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    MyPageSettingRow("로그아웃", null, MyPageIcon.LOGOUT, onLogout)
                }
            }
            if (deletionError != null) Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when (deletionError) {
                    AccountFailure.NETWORK -> "탈퇴 완료를 확인하지 못했어요. 연결을 확인한 뒤 다시 시도해주세요."
                    AccountFailure.NOT_CONFIGURED -> "회원 탈퇴 서비스를 준비하고 있어요. 잠시 후 다시 시도해주세요."
                    AccountFailure.REAUTH_REQUIRED -> "다시 로그인한 뒤 회원 탈퇴를 진행해주세요."
                    AccountFailure.STORAGE -> "탈퇴 처리 정보를 저장하지 못했어요. 다시 시도해주세요."
                    AccountFailure.UNKNOWN -> "회원 탈퇴를 완료하지 못했어요. 다시 시도해주세요."
                }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onDeleteAccount, enabled = email != null) { Text("회원 탈퇴 다시 시도") }
            }
        }
    }
}

private enum class MyPageIcon { TEAM, NOTIFICATION, DELETE, LOGOUT }

@Composable
private fun MyPageSettingRow(title: String, value: String?, icon: MyPageIcon, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
            if (icon == MyPageIcon.TEAM) BaseballMark(Modifier.size(24.dp), color)
            else Canvas(Modifier.size(24.dp)) {
                val stroke = Stroke(2.dp.toPx())
                when (icon) {
                    MyPageIcon.NOTIFICATION -> {
                        drawArc(color, 180f, 180f, false, Offset(size.width * .2f, size.height * .15f),
                            Size(size.width * .6f, size.height * .9f), style = stroke)
                        drawLine(color, Offset(size.width * .2f, size.height * .65f), Offset(size.width * .8f, size.height * .65f), stroke.width)
                        drawCircle(color, size.width * .07f, Offset(center.x, size.height * .82f))
                    }
                    MyPageIcon.DELETE -> {
                        drawRect(color, Offset(size.width * .25f, size.height * .3f), Size(size.width * .5f, size.height * .55f), style = stroke)
                        drawLine(color, Offset(size.width * .15f, size.height * .25f), Offset(size.width * .85f, size.height * .25f), stroke.width)
                        drawLine(color, Offset(size.width * .4f, size.height * .12f), Offset(size.width * .6f, size.height * .12f), stroke.width)
                    }
                    MyPageIcon.LOGOUT -> {
                        drawLine(color, Offset(size.width * .2f, size.height * .15f), Offset(size.width * .2f, size.height * .85f), stroke.width)
                        drawLine(color, Offset(size.width * .2f, center.y), Offset(size.width * .85f, center.y), stroke.width)
                        drawLine(color, Offset(size.width * .6f, size.height * .25f), Offset(size.width * .85f, center.y), stroke.width)
                        drawLine(color, Offset(size.width * .6f, size.height * .75f), Offset(size.width * .85f, center.y), stroke.width)
                    }
                    else -> Unit
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = if (icon == MyPageIcon.DELETE) color else MaterialTheme.colorScheme.onSurface)
                if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
