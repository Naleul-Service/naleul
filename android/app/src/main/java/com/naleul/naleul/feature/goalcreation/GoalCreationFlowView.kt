// feature/goalcreation/GoalCreationFlowView.kt
//
// iOS Features/GoalCreation/GoalCreationFlowView.swift
// AI 목표 만들기 전체 화면 (앱이 탭 위에 덮어 그려요 — AppRouter.goalCreation 이 있으면).
// 웹 원본: app/(flow)/goal/new/** 페이지들 + features/goal-creation/ui/SessionGate.tsx · FlowHeader.tsx
//
// 웹은 단계마다 URL 이 달라요 (/goal/new → /goal/new/12 → …/review → …/generating → …/draft).
// 여기서는 이 화면 하나가 "지금 단계(GoalCreationRoute)"를 들고 있다가 내용을 바꿔 끼워요.
// 웹에서 단계 이동은 거의 router.replace(뒤로 가기로 돌아가지 않음)라서, 쌓지 않고 바꿔 끼우는 방식이 같아요.
// 닫기(X) · 시스템 뒤로 가기는 어느 단계에서든 각 화면의 onClose → 보통 AppRouter.closeGoalCreation()
// (웹의 "/goal 로 이동" 과 같아요). 대화 중이면 "잠시 멈출까요?" 확인창을 먼저 띄워요.

package com.naleul.naleul.feature.goalcreation

import com.naleul.naleul.app.*
import com.naleul.naleul.core.analytics.*
import com.naleul.naleul.core.network.*
import com.naleul.naleul.core.state.*
import com.naleul.naleul.core.util.*
import com.naleul.naleul.designsystem.*
import com.naleul.naleul.model.*
import com.naleul.naleul.feature.auth.*
import com.naleul.naleul.feature.calendar.*
import com.naleul.naleul.feature.calendarimport.*
import com.naleul.naleul.feature.goal.*
import com.naleul.naleul.feature.goalcreation.*
import com.naleul.naleul.feature.habit.*
import com.naleul.naleul.feature.home.*
import com.naleul.naleul.feature.notifications.*
import com.naleul.naleul.feature.pattern.*
import com.naleul.naleul.feature.record.*
import com.naleul.naleul.feature.settings.*
import com.naleul.naleul.feature.taskadd.*
import com.naleul.naleul.feature.work.*

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// MARK: - 단계 이동

class GoalCreationNavigator(
    route: GoalCreationRoute,
    /** 임시 목표를 구체화할 때 (웹 ?sourceGoalId=) */
    val sourceGoalId: Int? = null,
) {
    var route: GoalCreationRoute by mutableStateOf(route)
        private set

    init { GCTracker.sessionId = route.analyticsSessionId }

    /** 웹 router.replace(goalFlowPath.xxx) — 화면 전환은 GoalCreationFlowView 의 Crossfade 가 부드럽게 해요 */
    fun go(route: GoalCreationRoute) {
        this.route = route
        GCTracker.sessionId = route.analyticsSessionId
    }

    /** 플로우 닫기 (웹 router.push('/goal')) */
    fun close() {
        AppRouter.closeGoalCreation()
    }

    /** 닫고 목표 목록으로 (웹 "목표로 가기" 링크) */
    fun closeToGoalList() {
        AppRouter.closeGoalCreation()
        AppRouter.openGoalList()
    }

    companion object {}
}

/**
 * iOS @Environment(GoalCreationNavigator.self).
 * 미리보기에서 따로 넣지 않아도 멈추지 않게 기본값(시작 화면)을 둬요.
 */
val LocalGoalCreationNavigator = staticCompositionLocalOf { GoalCreationNavigator(GoalCreationRoute.start) }

// MARK: - 전체 화면

/** 앱이 부르는 진입 화면 */
@Composable
fun GoalCreationFlowView(start: GoalCreationStart) {
    val nav = remember(start.id) {
        val route: GoalCreationRoute = start.resumeSessionId?.let { GoalCreationRoute.resume(it) } ?: GoalCreationRoute.start
        GoalCreationNavigator(route = route, sourceGoalId = start.sourceGoalId)
    }
    GoalCreationFlowContent(nav)
}

/** 미리보기용 — 원하는 단계에서 시작 */
@Composable
fun GoalCreationFlowView(previewRoute: GoalCreationRoute) {
    val nav = remember { GoalCreationNavigator(route = previewRoute) }
    GoalCreationFlowContent(nav)
}

@Composable
private fun GoalCreationFlowContent(nav: GoalCreationNavigator) {
    // 목표 만들기 중에 앱이 뒤로 가면 — 어느 단계에서 나갔는지
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                val screen = nav.route.analyticsScreen
                GCTracker.track(
                    GCEvent.appBackground,
                    "session_id" to nav.route.analyticsSessionId,
                    "from_screen" to screen,
                    "question_count" to if (screen == "chat") GCTracker.questionCount else null,
                    "is_dirty" to if (screen == "draft") GCTracker.isDirty else null,
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    CompositionLocalProvider(LocalGoalCreationNavigator provides nav) {
        Box(
            Modifier
                .fillMaxSize()
                .background(NC.surface)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // 단계가 바뀌면 화면 상태를 새로 시작해요 (웹은 페이지가 바뀌면 컴포넌트가 새로 만들어져요).
            // Crossfade 는 단계(값)마다 따로 화면을 만들어서 iOS .id(nav.route) + .transition(.opacity) 와 같아요.
            Crossfade(targetState = nav.route, animationSpec = tween(200), label = "goal-creation-route") { route ->
                GoalCreationRouteContent(route = route, sourceGoalId = nav.sourceGoalId)
            }
        }
    }
}

/** 단계 → 화면 (웹 app/(flow)/goal/new/... 의 page.tsx 들) */
@Composable
private fun GoalCreationRouteContent(route: GoalCreationRoute, sourceGoalId: Int?) {
    when (route) {
        is GoalCreationRoute.start -> GoalCreationStartView(sourceGoalId = sourceGoalId)
        is GoalCreationRoute.resume ->
            // 이어서 하기: 세션 상태에 맞는 단계로 바로 보내요 (allow 가 비어 있어서 항상 이동하거나 "끝난 대화" 안내)
            GoalCreationSessionGate(sessionId = route.sessionId, allow = emptyList()) { _, _ -> }
        is GoalCreationRoute.chat -> InterviewChatScreen(sessionId = route.sessionId)
        is GoalCreationRoute.review ->
            GoalCreationSessionGate(
                sessionId = route.sessionId,
                allow = listOf(GoalCreationSessionStatus.READY_TO_GENERATE, GoalCreationSessionStatus.DRAFT_READY),
            ) { session, reload -> GoalCreationReviewView(session = session, reload = reload) }
        is GoalCreationRoute.generating -> GoalCreationGeneratingScreen(sessionId = route.sessionId, draftId = route.draftId)
        is GoalCreationRoute.draft ->
            GoalCreationSessionGate(
                sessionId = route.sessionId,
                allow = listOf(GoalCreationSessionStatus.DRAFT_READY),
            ) { session, reload -> DraftLoaderView(session = session, reload = reload) }
    }
}

// MARK: - 상단 바 (웹 FlowHeader)

/**
 * 목표 만들기 공통 상단 바: 닫기 · 제목 · 가운데(진행 표시) · 오른쪽 액션.
 * (iOS .goalCreationHeader(onClose:center:action:)) — 시스템 뒤로 가기도 onClose 로 이어 줘요.
 */
@Composable
fun GoalCreationHeader(
    title: String = "새 목표",
    onClose: () -> Unit,
    center: @Composable () -> Unit = {},
    action: @Composable RowScope.() -> Unit = {},
) {
    BackHandler { onClose() }
    Column(Modifier.fillMaxWidth().background(NC.surface)) {
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
            Row(
                Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                NIconButton("xmark", size = 17.dp, color = NC.ink2, label = "닫기") { onClose() }
                Text(title, style = pt(15, PTWeight.bold), color = NC.ink)
            }
            Box(Modifier.align(Alignment.Center)) { center() }
            Row(
                Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) { action() }
        }
        HorizontalDivider(color = NC.borderSoft)
    }
}

// MARK: - 가운데 안내 (웹 CenterMessage)

@Composable
fun GoalCreationCenterMessage(
    title: String,
    description: String? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 360.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = NType.h4, color = NC.ink, textAlign = TextAlign.Center)
            if (description != null) {
                Text(
                    description,
                    style = pt(14),
                    color = NC.ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                Modifier.padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { actions() }
        }
    }
}

/** 상단 바 + 가운데 내용 (웹 FlowShell). onClose 가 null 이면 플로우를 닫아요 */
@Composable
fun GoalCreationFlowShell(
    onClose: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val nav = LocalGoalCreationNavigator.current
    Column(Modifier.fillMaxSize().background(NC.surface)) {
        GoalCreationHeader(onClose = { if (onClose != null) onClose() else nav.close() })
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center, content = content)
    }
}

// MARK: - 세션 불러오기 + 상태 확인 (웹 SessionGate)

private sealed class GoalCreationGatePhase {
    object loading : GoalCreationGatePhase()
    data class loaded(val session: GoalCreationSessionDetail) : GoalCreationGatePhase()
    /** 이어갈 화면이 없는 상태 (CONFIRMED / ABANDONED) */
    data class ended(val status: GoalCreationSessionStatus) : GoalCreationGatePhase()
    object notFound : GoalCreationGatePhase()
    data class failed(val message: String) : GoalCreationGatePhase()
}

/**
 * 세션을 불러와서
 *  - 로딩/에러 화면을 대신 보여주고
 *  - 이 화면에서 다룰 수 없는 상태면 맞는 단계로 보내요 (예: 다시 열었더니 이미 생성 완료)
 * content 에는 세션과 "다시 불러오기" 함수를 넘겨요.
 */
@Composable
fun GoalCreationSessionGate(
    sessionId: Int,
    allow: List<GoalCreationSessionStatus>,
    content: @Composable (GoalCreationSessionDetail, suspend () -> Unit) -> Unit,
) {
    val nav = LocalGoalCreationNavigator.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf<GoalCreationGatePhase>(GoalCreationGatePhase.loading) }

    suspend fun load() {
        val from = nav.route.analyticsScreen
        try {
            val session = GoalCreationAPI.getSession(sessionId)
            if (allow.contains(session.status)) {
                phase = GoalCreationGatePhase.loaded(session)
            } else {
                val next = GoalCreationRoute.forStatus(sessionId, session.status)
                if (next != null) {
                    GCTracker.track(
                        GCEvent.sessionRedirect,
                        "session_id" to sessionId, "from_screen" to from, "session_status" to session.status.name,
                    )
                    // 맞는 단계로 이동하는 동안은 로딩 표시
                    phase = GoalCreationGatePhase.loading
                    nav.go(next)
                } else {
                    GCTracker.track(
                        GCEvent.sessionEnded,
                        "session_id" to sessionId, "session_status" to session.status.name, "from_screen" to from,
                    )
                    phase = GoalCreationGatePhase.ended(session.status)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GCTracker.track(GCEvent.sessionError, "session_id" to sessionId, "error_status" to gcErrorStatus(e), "from_screen" to from)
            phase = if ((e as? ApiError)?.httpStatus == 404) GoalCreationGatePhase.notFound
            else GoalCreationGatePhase.failed(errorMessage(e))
        }
    }

    // 화면에 들어올 때마다 서버 최신 상태로 (웹 staleTime: 0)
    LaunchedEffect(sessionId) { load() }

    when (val p = phase) {
        is GoalCreationGatePhase.loading -> GoalCreationFlowShell {
            CircularProgressIndicator(color = NC.ink3, strokeWidth = 2.5.dp)
        }
        is GoalCreationGatePhase.loaded -> content(p.session) { load() }
        is GoalCreationGatePhase.ended -> GoalCreationFlowShell { GoalCreationEndedMessage(p.status, nav, sessionId) }
        is GoalCreationGatePhase.notFound -> GoalCreationFlowShell {
            GoalCreationCenterMessage(title = "대화를 찾을 수 없어요") {
                NButton("새로 시작하기") {
                    GCTracker.track(GCEvent.ctaClick, "session_id" to sessionId, "cta" to "restart", "session_status" to "NOT_FOUND")
                    nav.go(GoalCreationRoute.start)
                }
            }
        }
        is GoalCreationGatePhase.failed -> GoalCreationFlowShell {
            GoalCreationCenterMessage(title = "불러오지 못했어요", description = p.message) {
                NButton("다시 시도") {
                    GCTracker.track(GCEvent.ctaClick, "session_id" to sessionId, "cta" to "retry", "session_status" to "ERROR")
                    phase = GoalCreationGatePhase.loading
                    scope.launch { load() }
                }
            }
        }
    }
}

@Composable
private fun GoalCreationEndedMessage(status: GoalCreationSessionStatus, nav: GoalCreationNavigator, sessionId: Int? = null) {
    val cta = { name: String ->
        GCTracker.track(GCEvent.ctaClick, "session_id" to sessionId, "cta" to name, "session_status" to status.name)
    }
    when (status) {
        GoalCreationSessionStatus.CONFIRMED ->
            GoalCreationCenterMessage(title = "이미 만들어진 목표예요", description = "목표 화면에서 확인할 수 있어요.") {
                NButton("목표로 가기", variant = NButtonVariant.secondary) { cta("to_goals"); nav.closeToGoalList() }
            }
        GoalCreationSessionStatus.ABANDONED ->
            GoalCreationCenterMessage(title = "종료된 대화예요", description = "취소했거나 7일이 지나 더 이어갈 수 없어요.") {
                NButton("새로 시작하기") { cta("restart"); nav.go(GoalCreationRoute.start) }
            }
        else ->
            GoalCreationCenterMessage(title = "이어갈 수 없어요") {
                NButton("목표로 가기", variant = NButtonVariant.secondary) { cta("to_goals"); nav.closeToGoalList() }
            }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun GoalCreationFlowViewEndedPreview() {
    NaleulTheme(dark = false) {
        GoalCreationFlowShell {
            GoalCreationCenterMessage(title = "종료된 대화예요", description = "취소했거나 7일이 지나 더 이어갈 수 없어요.") {
                NButton("새로 시작하기") {}
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun GoalCreationFlowViewStartPreview() {
    NaleulTheme(dark = false) {
        GoalCreationFlowView(previewRoute = GoalCreationRoute.start)
    }
}
