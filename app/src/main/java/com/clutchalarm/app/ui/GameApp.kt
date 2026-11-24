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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameApp(
    viewModel: GameViewModel,
    loginViewModel: LoginViewModel,
    notificationAvailability: NotificationAvailability,
    onRequestPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onAppVisible: () -> Unit = {},
) {
    LifecycleResumeEffect(Unit) {
        onAppVisible()
        onPauseOrDispose { }
    }
    val login by loginViewModel.state.collectAsStateWithLifecycle()
    if (login.sessionPhase != SessionPhase.IDLE) {
        BackHandler(enabled = login.sessionPhase in setOf(SessionPhase.LOGGING_OUT, SessionPhase.LOGOUT_FAILED,
            SessionPhase.DELETING, SessionPhase.DELETE_CLEANUP_FAILED)) { }
        ClutchTheme() {
            SessionScreen(login, { loginViewModel.restoreSession() }, loginViewModel::confirmLogout, loginViewModel::retryDeletionCleanup)
        }
        return
    }
    if (!login.authenticated) {
        val view = LocalView.current
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                }
            }
        }
        val showSignUp by loginViewModel.showSignUp.collectAsStateWithLifecycle()
        val signUp by loginViewModel.signUpState.collectAsStateWithLifecycle()
        BackHandler(enabled = showSignUp) { loginViewModel.closeSignUp() }
        ClutchTheme() {
            if (showSignUp) SignUpScreen(signUp, loginViewModel.qaLoginAvailable, loginViewModel::setSignUpEmail,
                loginViewModel::setSignUpPassword, loginViewModel::setSignUpConfirmation, loginViewModel::signUp,
                loginViewModel::closeSignUp)
            else LoginScreen(login, loginViewModel.qaLoginAvailable, loginViewModel::setEmail,
                loginViewModel::setPassword, loginViewModel::login, loginViewModel::openSignUp)
        }
        return
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
    val homeGradient = if (isHome) com.clutchalarm.app.domain.model.HomeGradients.find(settings.preferences?.preferredTeamId) else null
    val homeHeaderInk = if (homeGradient != null) Color.White else teamPalette(settings.preferences?.preferredTeamId).onPrimaryContainer
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
                isAppearanceLightStatusBars = homeGradient == null
                isAppearanceLightNavigationBars = true
            }
        }
    }

    ClutchTheme(settings.preferences?.preferredTeamId) {
        if (login.logoutConfirmation) AlertDialog(
            onDismissRequest = loginViewModel::cancelLogout,
            title = { Text("로그아웃하시겠어요?") },
            text = { Text("저장한 응원팀과 알림 설정은 유지됩니다.") },
            confirmButton = { TextButton(onClick = loginViewModel::confirmLogout) { Text("로그아웃하기") } },
            dismissButton = { TextButton(onClick = loginViewModel::cancelLogout) { Text("취소") } },
        )
        if (login.deletionConfirmation) AlertDialog(
            onDismissRequest = loginViewModel::cancelDeletion,
            title = { Text("회원 탈퇴를 진행할까요?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(login.accountEmail.orEmpty())
                Text(if (loginViewModel.qaLoginAvailable) "QA 검증용 계정의 탈퇴를 진행합니다. 실제 서버 계정은 삭제되지 않습니다."
                    else "현재 로그인한 계정의 탈퇴를 요청합니다. 완료 후 로그인 화면으로 이동합니다.")
                Text("이 기기에 저장한 응원팀과 알림 설정은 유지됩니다.")
            } },
            confirmButton = { TextButton(onClick = loginViewModel::confirmDeletion) { Text("탈퇴하기", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = loginViewModel::cancelDeletion) { Text("취소") } },
        )
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(when {
                        !ready -> "클러치알림"
                        showSettings && editor?.page == SettingsPage.TEAM -> if (editor.onboarding) "응원팀 선택" else "응원팀 변경"
                        showSettings && editor?.page == SettingsPage.DETAILS -> "알림 설정"
                        showSettings -> if (editor?.onboarding == true) "알림 선택" else "알림 설정"
                        isDetail -> "모의 경기 상세"
                        mainScreen == MainScreen.GAMES -> "모의 경기 선택"
                        mainScreen == MainScreen.MY_PAGE -> "마이페이지"
                        else -> "클러치알림"
                    }) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (homeGradient != null) Color(0xFF000000L or homeGradient.top.toLong()) else if (redHeader) LocalTeamPalette.current.primaryContainer else MaterialTheme.colorScheme.background,
                        titleContentColor = if (redHeader) homeHeaderInk else MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = if (redHeader) homeHeaderInk else MaterialTheme.colorScheme.primary,
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
                                Text("응원팀", color = if (isHome) homeHeaderInk else MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = { viewModel.showNotificationSettings(true) }) {
                                Text("알림 설정", color = if (isHome) homeHeaderInk else MaterialTheme.colorScheme.primary)
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
                        Text("저장된 설정을 불러오지 못했습니다.", color = MaterialTheme.colorScheme.error)
                        ClutchOutlinedButton(onClick = viewModel::loadNotificationPreferences, enabled = !settings.busy) { Text("다시 시도") }
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
                    MainScreen.HOME -> HomeScreen(games, viewModel::showGames, content,
                        KboTeams.find(settings.preferences?.preferredTeamId)?.name,
                        if (loginViewModel.qaLoginAvailable) loginViewModel::returnToLoginForQa else null)
                    MainScreen.GAMES -> GameSelectionScreen(games, viewModel::selectGame, viewModel::loadGames, inset)
                    MainScreen.MY_PAGE -> MyPageScreen(
                        preferences = requireNotNull(settings.preferences),
                        email = login.accountEmail,
                        accountLoading = login.accountLoading,
                        accountError = login.accountError,
                        deletionError = login.deletionError,
                        onChangeTeam = viewModel::showTeamSelection,
                        onNotificationSettings = { viewModel.showNotificationSettings(true) },
                        onLogout = loginViewModel::requestLogout,
                        onReloadAccount = loginViewModel::loadAccount,
                        onDeleteAccount = loginViewModel::requestDeletion,
                        modifier = content,
                    )
                }
                GameDetailState.Loading -> CenteredMessage(inset) { CircularProgressIndicator() }
                GameDetailState.Error -> CenteredMessage(inset) {
                    Text("경기 데이터를 불러오지 못했습니다.", color = MaterialTheme.colorScheme.error)
                    ClutchOutlinedButton(onClick = viewModel::closeGame) {
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
