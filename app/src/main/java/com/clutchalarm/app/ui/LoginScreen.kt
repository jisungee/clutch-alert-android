package com.clutchalarm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.clutchalarm.app.auth.AuthenticationFailure
import com.clutchalarm.app.auth.SessionInvalidReason

@Composable
internal fun LoginScreen(
    state: LoginState,
    qaLoginAvailable: Boolean,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onLogin: () -> Unit,
    onSignUp: () -> Unit,
) {
    var showPassword by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
        AuthBrandHeader()
        Column(Modifier.fillMaxWidth().widthIn(max = 520.dp).align(Alignment.CenterHorizontally).padding(ClutchStyle.page),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("로그인", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("이메일과 비밀번호를 입력해주세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.invalidSession != null) Text(
                if (state.invalidSession == SessionInvalidReason.LOCAL_DATA_INVALID) "기기의 로그인 정보를 확인할 수 없어요. 다시 로그인해주세요."
                else "로그인 세션이 만료되거나 폐기됐어요. 다시 로그인해주세요.", color = MaterialTheme.colorScheme.error)
            if (state.registrationSucceeded) Text("회원가입이 완료됐어요. 비밀번호를 입력해 로그인해주세요.",
                color = MaterialTheme.colorScheme.primary)
            if (state.accountDeleted) Text("회원 탈퇴가 완료됐어요.", color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(value = state.email, onValueChange = onEmail, enabled = !state.busy,
                label = { Text("이메일") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }))
            OutlinedTextField(value = state.password, onValueChange = onPassword, enabled = !state.busy,
                label = { Text("비밀번호") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showPassword = !showPassword }, enabled = !state.busy) {
                    Text(if (showPassword) "숨기기" else "보기")
                } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (state.canSubmit) { focus.clearFocus(); onLogin() } }))
            state.error?.let { error ->
                Text(when (error) {
                    AuthenticationFailure.INVALID_CREDENTIALS -> "이메일 또는 비밀번호를 확인해주세요."
                    AuthenticationFailure.NETWORK -> "연결을 확인한 뒤 다시 시도해주세요."
                    AuthenticationFailure.NOT_CONFIGURED -> "로그인 서비스를 준비하고 있습니다. 잠시 후 다시 시도해주세요."
                    AuthenticationFailure.UNKNOWN -> "로그인하지 못했습니다. 다시 시도해주세요."
                    AuthenticationFailure.STORAGE -> "로그인 정보를 저장하지 못했습니다. 다시 시도해주세요."
                }, color = MaterialTheme.colorScheme.error)
            }
            ClutchButton(onClick = { focus.clearFocus(); onLogin() }, enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text(if (state.busy) "로그인 중…" else if (qaLoginAvailable) "QA 검증용 로그인" else "로그인")
            }
            ClutchOutlinedButton(onClick = onSignUp, enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)) { Text("회원가입") }
            if (qaLoginAvailable) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("QA 전용 인증 체험\n이메일: qa@example.com\n비밀번호: clutch-qa\n실제 계정 인증이 아닌 화면 검증용입니다.",
                        Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
