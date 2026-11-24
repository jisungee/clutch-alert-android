package com.clutchalarm.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.clutchalarm.app.notification.NotificationAvailability
import com.clutchalarm.app.domain.model.KboTeams

private val DetailColors = lightColorScheme(
    primary = Color(0xFFBE1536),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4EA),
    onPrimaryContainer = Color(0xFF65001D),
    background = Color(0xFFF7F8FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F1F5),
    onSurface = Color(0xFF172238),
    onSurfaceVariant = Color(0xFF555E70),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameApp(
    viewModel: GameViewModel,
    notificationAvailability: NotificationAvailability,
    onRequestPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onAppVisible: () -> Unit = {},
) {
    LifecycleResumeEffect(Unit) {
        onAppVisible()
        onPauseOrDispose { }
    }
    val games by viewModel.games.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val entry by viewModel.detailEntry.collectAsStateWithLifecycle()
    val mainScreen by viewModel.mainScreen.collectAsStateWithLifecycle()
    val showSettings by viewModel.showNotificationSettings.collectAsStateWithLifecycle()
    val settings by viewModel.notificationSettings.collectAsStateWithLifecycle()
    val editor = settings.editor
    val ready = settings.preferences != null
    val editorHasBack = editor != null && (!editor.onboarding || editor.page != SettingsPage.TEAM)
    val isDetail = detail != null
    val isHome = ready && !isDetail && !showSettings && mainScreen == MainScreen.HOME
    val isMyPage = ready && !isDetail && !showSettings && mainScreen == MainScreen.MY_PAGE
    val redHeader = isHome || isMyPage
    val returnLabel = when (mainScreen) {
        MainScreen.HOME -> "홈"
        MainScreen.GAMES -> "목록"
        MainScreen.MY_PAGE -> "마이페이지"
    }
    BackHandler(enabled = if (showSettings) editorHasBack || settings.busy else ready && (isDetail || mainScreen != MainScreen.HOME)) {
        viewModel.navigateBack()
    }
    val view = LocalView.current
    SideEffect {
        view.context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !redHeader
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(colorScheme = DetailColors) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(when {
                        !ready -> "클러치알림"
                        showSettings && editor?.page == SettingsPage.TEAM -> if (editor.onboarding) "응원팀 선택" else "응원팀 변경"
                        showSettings && editor?.page == SettingsPage.DETAILS -> "알림 세부 설정"
                        showSettings -> "알림 선택"
                        isDetail -> "모의 경기 상세"
                        mainScreen == MainScreen.GAMES -> "모의 경기 선택"
                        mainScreen == MainScreen.MY_PAGE -> "마이페이지"
                        else -> "클러치알림"
                    }) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (redHeader) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                        titleContentColor = if (redHeader) Color.White else MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = if (redHeader) Color.White else MaterialTheme.colorScheme.primary,
                    ),
                    navigationIcon = {
                        if (ready && (if (showSettings) editorHasBack else isDetail || mainScreen == MainScreen.GAMES)) {
                            TextButton(onClick = viewModel::navigateBack, enabled = !settings.busy) {
                                Text(when {
                                    showSettings -> "뒤로"
                                    isDetail -> returnLabel
                                    else -> "홈"
                                })
                            }
                        }
                    },
                    actions = {
                        if (ready && !showSettings && !isMyPage && (!isDetail || detail is GameDetailState.Loaded)) {
                            TextButton(onClick = viewModel::showTeamSelection) {
                                Text("응원팀", color = if (isHome) Color.White else MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = { viewModel.showNotificationSettings(true) }) {
                                Text("알림 유형", color = if (isHome) Color.White else MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (ready && !isDetail && !showSettings) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        val itemColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                        NavigationBarItem(
                            colors = itemColors,
                            selected = mainScreen == MainScreen.HOME,
                            onClick = viewModel::showHome,
                            icon = { NavigationMark(home = true) }, label = { Text("홈") },
                        )
                        NavigationBarItem(
                            colors = itemColors,
                            selected = mainScreen == MainScreen.GAMES,
                            onClick = viewModel::showGames,
                            icon = { NavigationMark(home = false) }, label = { Text("경기") },
                        )
                        NavigationBarItem(
                            colors = itemColors,
                            selected = mainScreen == MainScreen.MY_PAGE,
                            onClick = viewModel::showMyPage,
                            icon = { ProfileMark(Modifier.size(24.dp)) }, label = { Text("마이") },
                        )
                    }
                }
            },
        ) { padding ->
            val content = Modifier.fillMaxSize().padding(padding)
            val inset = content.padding(horizontal = 16.dp)
            if (!ready) {
                CenteredMessage(inset) {
                    if (settings.error) {
                        Text("저장된 설정을 불러오지 못했습니다.")
                        OutlinedButton(onClick = viewModel::loadNotificationPreferences, enabled = !settings.busy) { Text("다시 시도") }
                    } else CircularProgressIndicator()
                }
            } else if (showSettings) {
                if (editor?.page == SettingsPage.TEAM) {
                    TeamSelectionScreen(settings, viewModel::chooseTeam, viewModel::teamNext, viewModel::saveSettings,
                        inset.padding(top = 16.dp))
                } else NotificationSettingsScreen(
                    state = settings,
                    onToggle = viewModel::setNotificationEnabled,
                    onEnableAll = viewModel::enableAllNotifications,
                    onDisableAll = viewModel::disableAllNotifications,
                    onHideWhenLosing = viewModel::setHideWhenLosing,
                    onCategory = viewModel::openCategory,
                    onCategoryEnabled = viewModel::setCategoryEnabled,
                    onChangeTeam = viewModel::editTeamInSettings,
                    onSave = viewModel::saveSettings,
                    modifier = inset,
                )
            } else when (val selected = detail) {
                null -> when (mainScreen) {
                    MainScreen.HOME -> HomeScreen(games, viewModel::showGames, content, KboTeams.find(settings.preferences?.preferredTeamId)?.name)
                    MainScreen.GAMES -> GameSelectionScreen(games, viewModel::selectGame, viewModel::loadGames, inset)
                    MainScreen.MY_PAGE -> MyPageScreen(
                        preferences = requireNotNull(settings.preferences),
                        onChangeTeam = viewModel::showTeamSelection,
                        onNotificationSettings = { viewModel.showNotificationSettings(true) },
                        modifier = content,
                    )
                }
                GameDetailState.Loading -> CenteredMessage(inset) { CircularProgressIndicator() }
                GameDetailState.Error -> CenteredMessage(inset) {
                    Text("경기 데이터를 불러오지 못했습니다.")
                    OutlinedButton(onClick = viewModel::closeGame) {
                        Text(if (mainScreen == MainScreen.MY_PAGE) "마이페이지로" else "${returnLabel}으로")
                    }
                }
                is GameDetailState.Loaded -> {
                    LifecycleResumeEffect(selected.playback.gameState.gameId, entry?.requestId) {
                        entry?.let { viewModel.confirmDetailVisible(it.gameId, it.requestId) }
                        onPauseOrDispose { }
                    }
                    GameDetailScreen(
                        detail = selected,
                        savedTeamName = KboTeams.find(settings.preferences?.preferredTeamId)?.name,
                        onPlay = viewModel::play,
                        onPause = viewModel::pause,
                        onResume = viewModel::resume,
                        onReset = viewModel::reset,
                        onSpeedChange = viewModel::setPlaybackSpeed,
                        notificationAvailability = notificationAvailability,
                        onSelectTeam = viewModel::selectPreferredTeam,
                        onRequestPermission = onRequestPermission,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        modifier = inset,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CenteredMessage(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
