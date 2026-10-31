// app/MainTabScreen.kt
//
// iOS App/MainTabView.swift — 로그인 후 화면의 공통 틀 (웹 AppShell + Sidebar).
// 웹 사이드바 메뉴(홈 · 캘린더 · 목표 · 나의 패턴 · 설정)를 아래 탭으로 옮겼어요.
//
// 탭마다 쌓인 화면 목록(AppRouter.homePath 등)이 있고, 맨 위 화면만 그려요.
// 목록이 비어 있으면 탭 첫 화면을 그려요. (iOS NavigationStack(path:) 와 같은 동작)

package com.naleul.naleul.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import com.naleul.naleul.designsystem.MobileHeader
import com.naleul.naleul.designsystem.NIconButton
import com.naleul.naleul.feature.home.HomeEvent
import com.naleul.naleul.feature.home.HomeTracker
import com.naleul.naleul.feature.notifications.InboxTracker
import com.naleul.naleul.feature.notifications.NudgeCenter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.naleul.naleul.core.analytics.AnalyticsEvent
import com.naleul.naleul.core.analytics.AnalyticsService
import com.naleul.naleul.designsystem.NC
import com.naleul.naleul.designsystem.NIcon
import com.naleul.naleul.designsystem.Icon
import com.naleul.naleul.designsystem.sfToNIcon
import com.naleul.naleul.designsystem.NType
import com.naleul.naleul.designsystem.Radius
import com.naleul.naleul.designsystem.PTWeight
import com.naleul.naleul.designsystem.canvasBackground
import com.naleul.naleul.designsystem.pt
import com.naleul.naleul.designsystem.sfIcon
import com.naleul.naleul.feature.goal.GoalAddScreen
import com.naleul.naleul.feature.goal.GoalDetailScreen
import com.naleul.naleul.feature.goal.GoalListScreen
import com.naleul.naleul.feature.goalcreation.GoalCreationFlowView
import com.naleul.naleul.feature.goal.GoalOutcomePromptHost
import com.naleul.naleul.feature.home.HomeScreen
import com.naleul.naleul.feature.home.ReflectionsScreen
import com.naleul.naleul.feature.calendar.CalendarScreen
import com.naleul.naleul.feature.notifications.NudgeInboxScreen
import com.naleul.naleul.feature.pattern.PatternScreen
import com.naleul.naleul.feature.settings.SettingsRouteView
import com.naleul.naleul.feature.settings.SettingsScreen

@Composable
fun MainTabScreen() {
    // 탭을 바꿔도 각 탭의 스크롤 위치 등을 기억해요
    val saveable = rememberSaveableStateHolder()
    val tab = AppRouter.tab
    val path = AppRouter.path(tab)
    val top = path.lastOrNull()

    // 시스템 뒤로 가기: 쌓인 화면이 있으면 닫고, 홈이 아니면 홈 탭으로
    BackHandler(enabled = top != null || tab != AppTab.home) {
        if (!AppRouter.pop()) AppRouter.tab = AppTab.home
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().canvasBackground()) {
            // 맨 위 공통 헤더 (Figma MobileHeader) — 탭 첫 화면에서만, 스크롤돼도 고정.
            // 쌓인 화면(목표 상세 등)은 자기 뒤로가기 줄(PushedScreen)이 있어서 숨겨요.
            val rootShown = top == null
            if (rootShown) {
                Column(Modifier.fillMaxWidth().background(NC.bgSurface).statusBarsPadding()) {
                    MobileHeader {
                        val unread = NudgeCenter.unreadCount
                        Box {
                            NIconButton(
                                "bell", size = 18.dp, color = NC.textSecondary,
                                label = if (unread > 0) "알림함, 읽지 않은 알림 ${unread}개" else "알림함",
                            ) {
                                // 예전 홈 상단 종과 같은 이벤트 · 이동
                                HomeTracker.track(HomeEvent.notificationBellClick, "unread_count" to NudgeCenter.unreadCount)
                                InboxTracker.pendingEntry = "home_bell"
                                AppRouter.openNotifications()
                            }
                            if (unread > 0) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-6).dp, y = 6.dp)
                                        .size(7.dp)
                                        .background(NC.actionDanger, CircleShape),
                                )
                            }
                        }
                        NIconButton("gearshape", size = 18.dp, color = NC.textSecondary, label = "설정") {
                            AppRouter.openSettings()
                        }
                    }
                }
            }
            // 헤더가 상태바 여백을 이미 줬으니, 아래 탭 화면들의 statusBarsPadding 은 0 이 되게 해요
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(if (rootShown) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
            ) {
                AnimatedContent(
                    // 화면 정보(route)를 상태에 같이 넣어야, 닫히며 미끄러져 나가는 화면이 끝까지 자기 내용을 그려요
                    targetState = Triple(tab, path.size, top),
                    transitionSpec = {
                        val (fromTab, fromSize, _) = initialState
                        val (toTab, toSize, _) = targetState
                        when {
                            fromTab != toTab -> fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                            toSize > fromSize -> slideInHorizontally(tween(220)) { it } togetherWith fadeOut(tween(220))
                            else -> fadeIn(tween(220)) togetherWith slideOutHorizontally(tween(220)) { it }
                        }
                    },
                    label = "tabContent",
                ) { (t, size, route) ->
                    val key = "${t.name}/$size/${routeKey(route)}"
                    saveable.SaveableStateProvider(key) {
                        Box(Modifier.fillMaxSize().canvasBackground()) {
                            if (route == null) TabRoot(t) else RouteScreen(route)
                        }
                    }
                }
            }
            HorizontalDivider(color = NC.borderSubtle)
            TabBar()
        }

        // AI 목표 만들기는 탭 없는 전체 화면 플로우 (웹 (flow) 레이아웃)
        AppRouter.goalCreation?.let { start ->
            Box(Modifier.fillMaxSize().background(NC.bgSurface)) {
                GoalCreationFlowView(start = start)
            }
        }

        // 기한이 지나 자동 판정된 목표 — "목표는 만족했나요?" (웹 AppShell 의 GoalOutcomePromptModal)
        GoalOutcomePromptHost()
    }
}

@Composable
private fun TabRoot(tab: AppTab) {
    when (tab) {
        AppTab.home -> HomeScreen()
        AppTab.calendar -> CalendarScreen()
        AppTab.goals -> GoalListScreen()
        AppTab.reflections -> ReflectionsScreen(asTab = true)
        AppTab.patterns -> PatternScreen()
        AppTab.settings -> SettingsScreen()
    }
}

/** 공통 경로 → 화면 (iOS AppRouteDestinations) */
@Composable
private fun RouteScreen(route: AppRoute) {
    when (route) {
        is AppRoute.GoalDetail -> GoalDetailScreen(goalId = route.goalId, justCreated = route.created)
        is AppRoute.GoalAdd -> GoalAddScreen(initialMode = route.mode)
        is AppRoute.Settings -> SettingsRouteView(route = route.route)
        is AppRoute.Reflections -> ReflectionsScreen(initialGoalId = route.goalId)
        is AppRoute.Notifications -> NudgeInboxScreen()
        is AppRoute.Screen -> route.content()
    }
}

private fun routeKey(route: AppRoute?): String = when (route) {
    null -> "root"
    is AppRoute.GoalDetail -> "goal-${route.goalId}"
    is AppRoute.GoalAdd -> "goal-add-${route.mode}"
    is AppRoute.Settings -> "settings-${route.route}"
    is AppRoute.Reflections -> "reflections-${route.goalId}"
    is AppRoute.Notifications -> "notifications"
    is AppRoute.Screen -> "screen-${route.key}"
}

/** Component_ver.2 TabBar (mobile): bg/surface · 위 1px border/subtle · padding 8/16 · 아이콘 상자 32×32 r8 (선택 action/ghost/bg/hover) + Label/SM */
@Composable
private fun TabBar() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(NC.bgSurface)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (t in AppTab.bar) {
            val selected = AppRouter.tab == t
            val fg = if (selected) NC.textBrand else NC.textTertiary
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.nav))
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            val from = AppRouter.tab
                            AnalyticsService.track(
                                AnalyticsEvent.tabBarClick,
                                "tab" to t.name, "from_tab" to from.name, "is_reselect" to selected,
                            )
                            // nav_depth 는 첫 화면으로 돌아가기 전 깊이
                            if (selected) AnalyticsService.track(AnalyticsEvent.tabBarReselect, "tab" to t.name, "nav_depth" to AppRouter.path(t).size)
                            // 이미 보고 있는 탭을 다시 누르면 첫 화면으로 (⚠ iOS 는 아직 아무 일도 안 일어나요)
                            if (selected) AppRouter.popToRoot() else AppRouter.tab = t
                        },
                    )
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(if (selected) NC.ghostHover else Color.Transparent, RoundedCornerShape(Radius.nav)),
                    contentAlignment = Alignment.Center,
                ) {
                    (sfToNIcon(t.icon) ?: NIcon.home).Icon(size = 20.dp, tint = fg)
                }
                Text(t.title, style = NType.labelSM, color = fg, maxLines = 1)
            }
        }
    }
}
