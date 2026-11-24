package com.clutchalarm.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clutchalarm.app.auth.SessionProblem

@Composable
internal fun SessionScreen(state: LoginState, retry: () -> Unit, retryLogout: () -> Unit, retryDeletionCleanup: () -> Unit) {
    if (state.sessionPhase == SessionPhase.CHECKING) { BrandStartupScreen(); return }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
            when (state.sessionPhase) {
                SessionPhase.CHECKING -> {
                    CircularProgressIndicator()
                    Text("로그인 정보를 확인하고 있어요")
                }
                SessionPhase.LOGGING_OUT -> {
                    CircularProgressIndicator()
                    Text("로그아웃하고 있어요")
                }
                SessionPhase.DELETING -> {
                    CircularProgressIndicator()
                    Text("회원 탈퇴를 처리하고 있어요")
                }
                SessionPhase.DELETE_CLEANUP_FAILED -> {
                    Text("계정 삭제는 확인됐지만 기기의 로그인 정보를 정리하지 못했어요.", color = MaterialTheme.colorScheme.error)
                    ClutchButton(onClick = retryDeletionCleanup) { Text("로그인 정보 정리 다시 시도") }
                }
                SessionPhase.LOGOUT_FAILED -> {
                    Text("기기의 로그인 정보를 지우지 못했어요. 다시 시도해주세요.", color = MaterialTheme.colorScheme.error)
                    ClutchButton(onClick = retryLogout) { Text("로그아웃 다시 시도") }
                }
                SessionPhase.RETRYABLE -> {
                    Text(when (state.sessionProblem) {
                        SessionProblem.NETWORK -> "연결을 확인하지 못했어요. 저장된 로그인 정보는 유지됩니다."
                        SessionProblem.NOT_CONFIGURED -> "로그인 확인 서비스를 준비하고 있어요. 저장된 로그인 정보는 유지됩니다."
                        else -> "저장된 로그인 정보를 읽지 못했어요. 다시 시도해주세요."
                    }, color = MaterialTheme.colorScheme.error)
                    ClutchButton(onClick = retry) { Text("세션 확인 다시 시도") }
                }
                SessionPhase.IDLE -> Unit
            }
        }
    }
}
