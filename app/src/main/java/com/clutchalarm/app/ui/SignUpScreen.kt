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
import com.clutchalarm.app.auth.RegistrationFailure

@Composable
internal fun SignUpScreen(
    state: SignUpState,
    qa: Boolean,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConfirmation: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val focus = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmationVisible by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()
        .navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFBE1536), Color(0xFF800F2C))))
            .padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("클러치알림", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("회원가입", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("가입을 마치면 로그인 화면으로 돌아갑니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (qa) Text("QA 전용 가입 체험 · 가입한 계정은 앱 프로세스가 종료되면 초기화돼요.",
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = state.email, onValueChange = onEmail, enabled = !state.busy,
                label = { Text("이메일") }, singleLine = true, isError = state.emailInvalid,
                supportingText = { if (state.emailInvalid) Text("올바른 이메일을 입력해주세요.") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }))
            OutlinedTextField(value = state.password, onValueChange = onPassword, enabled = !state.busy,
                label = { Text("비밀번호") }, singleLine = true, isError = state.passwordInvalid,
                supportingText = { Text("공백만으로 된 비밀번호는 사용할 수 없으며 8자 이상 입력해주세요.") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { passwordVisible = !passwordVisible }, enabled = !state.busy) {
                    Text(if (passwordVisible) "숨기기" else "보기")
                } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }))
            OutlinedTextField(value = state.confirmation, onValueChange = onConfirmation, enabled = !state.busy,
                label = { Text("비밀번호 확인") }, singleLine = true, isError = state.confirmationInvalid,
                supportingText = { if (state.confirmationInvalid) Text("비밀번호가 일치하지 않습니다.") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                visualTransformation = if (confirmationVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { confirmationVisible = !confirmationVisible }, enabled = !state.busy) {
                    Text(if (confirmationVisible) "숨기기" else "보기")
                } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (state.canSubmit) { focus.clearFocus(); onSubmit() } }))
            state.error?.let { error ->
                Text(when (error) {
                    RegistrationFailure.EMAIL_IN_USE -> "이미 가입된 이메일입니다. 로그인하거나 다른 이메일로 다시 시도해주세요."
                    RegistrationFailure.NETWORK -> "연결을 확인한 뒤 다시 시도해주세요."
                    RegistrationFailure.NOT_CONFIGURED -> "회원가입 서비스를 준비하고 있습니다. 잠시 후 다시 시도해주세요."
                    RegistrationFailure.UNKNOWN -> "가입하지 못했습니다. 다시 시도해주세요."
                }, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = { focus.clearFocus(); onSubmit() }, enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text(if (state.busy) "가입 중…" else "가입하기")
            }
            OutlinedButton(onClick = onBack, enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)) { Text("로그인으로 돌아가기") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
