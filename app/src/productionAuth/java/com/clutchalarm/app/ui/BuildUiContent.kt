package com.clutchalarm.app.ui

/** Ordinary debug and every release contain only production copy. */
internal object BuildUiContent {
    const val loginLabel = "로그인"
    val loginHint: String? = null
    val registrationHint: String? = null
    const val deletionMessage = "현재 로그인한 계정의 탈퇴를 요청합니다. 완료 후 로그인 화면으로 이동합니다."
    val homeLoginLabel: String? = null
}
