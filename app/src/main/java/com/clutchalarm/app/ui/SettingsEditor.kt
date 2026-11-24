package com.clutchalarm.app.ui

import com.clutchalarm.app.domain.notification.NotificationPreferences
import com.clutchalarm.app.domain.notification.NotificationType
import com.clutchalarm.app.domain.notification.NotificationType.*

enum class SettingsPage { TEAM, CATEGORIES, DETAILS }

data class SettingsEditor(
    val draft: NotificationPreferences,
    val page: SettingsPage = SettingsPage.CATEGORIES,
    val category: NotificationCategory? = null,
    val onboarding: Boolean = false,
    val teamOnly: Boolean = false,
    val flowId: String = java.util.UUID.randomUUID().toString(),
    val notificationTouched: Boolean = false,
)

enum class NotificationCategory(val title: String, val types: List<NotificationType>) {
    FLOW("경기 상황 및 득점 흐름", listOf(GAME_STARTED, OFFENSE_STARTED, GAME_ENDED, REACHED_BASE,
        OPPONENT_ON_BASE, RUN_SCORED, HOME_RUN, COMEBACK, RUN_ALLOWED, BIG_INNING)),
    INCIDENT("돌발 변수 및 위기, 판정", listOf(VIDEO_REVIEW, INJURY, BENCH_CLEARING)),
    TACTICS("선수 기용 및 경기 데이터 분석", listOf(PITCHER_CHANGE, PITCHER_STAT)),
    PERFORMANCE("주요 기록 달성 및 선수 퍼포먼스", listOf(DEFENSE_HIGHLIGHT)),
    FAN("구단 엔트리 및 팬 개인화 활동", emptyList()),
    STADIUM("경기장 직관 편의", emptyList()),
    RECAP("경기 종료 후 리그 정보 및 복기", emptyList()),
}
