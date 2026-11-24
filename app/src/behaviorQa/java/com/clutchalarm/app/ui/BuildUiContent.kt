package com.clutchalarm.app.ui

/** Test copy is compiled only into the opt-in QA debug source set. */
internal object BuildUiContent {
    const val loginLabel = "QA 검증용 로그인"
    const val loginHint = "QA 전용 인증 체험\n이메일: qa@example.com\n비밀번호: clutch-qa\n실제 계정 인증이 아닌 화면 검증용입니다."
    const val registrationHint = "QA 전용 가입 체험 · 가입한 계정은 앱 프로세스가 종료되면 초기화돼요."
    const val deletionMessage = "QA 검증용 계정의 탈퇴를 진행합니다. 실제 서버 계정은 삭제되지 않습니다."
    const val homeLoginLabel = "QA 로그인 화면으로"
}
