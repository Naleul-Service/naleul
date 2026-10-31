// feature/goalcreation/GoalCreationGeneratingView.kt
//
// iOS Features/GoalCreation/GoalCreationGeneratingView.swift
// G-3 계획 만드는 중 (/goal/new/{sessionId}/generating?draftId=)
// 웹 원본: features/goal-creation/ui/generating/GeneratingScreen.tsx · GeneratingView.tsx
//
// 초안은 서버가 비동기로 만들어요(약 30초). 화면은 2초마다 상태를 물어봐요(폴링).
//  - READY   → 초안 검토 화면으로
//  - FAILED  → 에러 화면 (다시 시도 / 정보 수정하기)
//  - 90초 초과 → 에러 화면 (서버는 계속 만들 수도 있어요)
// 화면을 벗어나면 LaunchedEffect 가 취소되면서 폴링도 자동으로 멈춰요.

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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** draftId 가 있으면 바로 폴링 (계획 만들기 직후), 없으면 세션의 최신 초안을 찾아서 폴링 (이어서 만들기) */
@Composable
fun GoalCreationGeneratingScreen(sessionId: Int, draftId: Int?) {
    if (draftId != null) {
        key(draftId) { GoalCreationGeneratingView(sessionId = sessionId, draftId = draftId) }
    } else {
        GoalCreationSessionGate(sessionId = sessionId, allow = listOf(GoalCreationSessionStatus.GENERATING)) { session, _ ->
            val latest = session.latestDraft
            if (latest != null) {
                key(latest.draftId) { GoalCreationGeneratingView(sessionId = sessionId, draftId = latest.draftId) }
            }
        }
    }
}

class GoalCreationGeneratingModel(
    val sessionId: Int,
    val draftId: Int,
    preview: GoalCreationDraftResponse? = null,
) : ScreenModel() {
    var draft by mutableStateOf(preview)
    var timedOut by mutableStateOf(false)
    /** 첫 응답도 못 받고 실패했을 때 */
    var loadError by mutableStateOf<String?>(null)
    var retrying by mutableStateOf(false)

    // 이벤트 값
    val openedAt = System.currentTimeMillis()
    var pollCount = 0
    var lastErrorStatus: Int? = null
    val secondsElapsed: Int get() = ((System.currentTimeMillis() - openedAt) / 1000).toInt()

    /** 실패 이유 (server_failed / timeout / poll_error) */
    val failReason: String
        get() = when {
            status == GoalCreationDraftStatus.FAILED -> "server_failed"
            loadError != null && draft == null -> "poll_error"
            else -> "timeout"
        }

    val status: GoalCreationDraftStatus? get() = draft?.status
    val failed: Boolean
        get() = status == GoalCreationDraftStatus.FAILED ||
            (timedOut && status != GoalCreationDraftStatus.READY) ||
            (loadError != null && draft == null)

    val failMessageText: String
        get() {
            if (status == GoalCreationDraftStatus.FAILED) return draft?.message ?: failMessage
            val le = loadError
            if (le != null && draft == null) return le
            return "생각보다 오래 걸리고 있어요. 잠시 후 다시 시도해 주세요."
        }

    suspend fun poll() {
        val startedAt = System.currentTimeMillis()
        var failures = 0
        while (currentCoroutineContext().isActive) {
            try {
                val d = GoalCreationAPI.getDraft(sessionId, draftId)
                pollCount += 1
                draft = d
                failures = 0
                if (d.status != GoalCreationDraftStatus.GENERATING) return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 폴링 중 네트워크가 잠깐 끊겨도 바로 실패로 보지 않게 2번까지는 다시 물어봐요 (웹 retry: 2)
                failures += 1
                lastErrorStatus = gcErrorStatus(e)
                if (failures > 2) {
                    if (draft == null) {
                        loadError = errorMessage(e)
                        return
                    }
                    failures = 0
                }
            }
            if (System.currentTimeMillis() - startedAt >= GoalCreationConst.pollTimeoutMs) {
                timedOut = true
                return
            }
            val wait = draft?.pollAfterMs ?: GoalCreationConst.pollIntervalMs
            delay(maxOf(wait, 500).toLong())
        }
    }

    /** 다시 시도 → 이동할 단계 (실패하면 null) */
    suspend fun retry(): GoalCreationRoute? {
        retrying = true
        try {
            val style = draft?.planningStyle
            val body = GoalCreationDraftRequestBody(
                planningStyle = if (GoalCreationStyleMemory.wasSelectedByUser(sessionId)) style else null,
            )
            val res = GoalCreationAPI.requestDraft(sessionId, body)
            GCTracker.track(GCEvent.genRetryResult, "session_id" to sessionId, "result" to "success", "draft_id" to res.draftId)
            Invalidation.invalidate(DataKey.usage)
            GCTracker.setGenerateSource("retry")
            return GoalCreationRoute.generating(sessionId, draftId = res.draftId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GCTracker.track(GCEvent.genRetryResult, "session_id" to sessionId, "result" to "fail", "error_status" to gcErrorStatus(e))
            if ((e as? ApiError)?.httpStatus == 409) {
                GCTracker.setGenerateSource("retry")
                // 아직 서버가 이전 요청을 만들고 있음 → 그 초안을 이어서 기다려요
                val session = try {
                    GoalCreationAPI.getSession(sessionId)
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: Exception) {
                    null
                }
                val latest = session?.latestDraft
                if (latest != null) {
                    if (latest.status == GoalCreationDraftStatus.GENERATING) return GoalCreationRoute.generating(sessionId, draftId = latest.draftId)
                    if (latest.status == GoalCreationDraftStatus.READY) return GoalCreationRoute.draft(sessionId)
                }
            }
            Toast.error(errorMessage(e, failMessage))
            return null
        } finally {
            retrying = false
        }
    }

    companion object {
        const val failMessage = "계획을 만드는 데 실패했어요. 다시 시도해 주세요."
    }
}

@Composable
fun GoalCreationGeneratingView(sessionId: Int, draftId: Int) {
    val vm = rememberScreenModel(sessionId, draftId) { GoalCreationGeneratingModel(sessionId = sessionId, draftId = draftId) }
    GoalCreationGeneratingContent(vm = vm, isPreview = false)
}

/** 미리보기용 (폴링 안 함) */
@Composable
fun GoalCreationGeneratingView(preview: GoalCreationDraftResponse, timedOut: Boolean = false) {
    val vm = remember {
        GoalCreationGeneratingModel(sessionId = 101, draftId = preview.draftId, preview = preview).also { it.timedOut = timedOut }
    }
    GoalCreationGeneratingContent(vm = vm, isPreview = true)
}

private enum class GoalCreationStepState { done, current, todo }

@Composable
private fun GoalCreationGeneratingContent(vm: GoalCreationGeneratingModel, isPreview: Boolean) {
    val nav = LocalGoalCreationNavigator.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (isPreview) return@LaunchedEffect
        GCTracker.track(
            GCEvent.generatingView,
            "session_id" to vm.sessionId, "draft_id" to vm.draftId, "generate_source" to GCTracker.takeGenerateSource(),
        )
        vm.poll()
    }
    LaunchedEffect(vm.draft?.progressStep) {
        val step = vm.draft?.progressStep ?: return@LaunchedEffect
        if (vm.status != GoalCreationDraftStatus.GENERATING) return@LaunchedEffect
        GCTracker.track(
            GCEvent.stepView,
            "session_id" to vm.sessionId, "draft_id" to vm.draftId, "progress_step" to step, "seconds_elapsed" to vm.secondsElapsed,
        )
    }
    LaunchedEffect(vm.failed) {
        if (!vm.failed || isPreview) return@LaunchedEffect
        GCTracker.track(
            GCEvent.failView,
            "session_id" to vm.sessionId, "draft_id" to vm.draftId, "fail_reason" to vm.failReason,
            "seconds_elapsed" to vm.secondsElapsed, "error_status" to if (vm.failReason == "poll_error") vm.lastErrorStatus else null,
        )
    }
    LaunchedEffect(vm.status) {
        if (vm.status == GoalCreationDraftStatus.READY) {
            GCTracker.track(
                GCEvent.draftReady,
                "session_id" to vm.sessionId, "draft_id" to vm.draftId,
                "seconds_elapsed" to vm.secondsElapsed, "poll_count" to vm.pollCount,
            )
            Invalidation.invalidate(DataKey.goalCreation)
            nav.go(GoalCreationRoute.draft(vm.sessionId))
        }
    }

    fun close() {
        GCTracker.track(
            GCEvent.generatingClose,
            "session_id" to vm.sessionId, "draft_status" to (vm.status?.name ?: "UNKNOWN"), "seconds_elapsed" to vm.secondsElapsed,
        )
        if (vm.status == GoalCreationDraftStatus.GENERATING || vm.status == null) {
            Toast.show("계획은 계속 만들어지고 있어요. 나중에 이어서 볼 수 있어요.")
        }
        nav.close()
    }

    GoalCreationFlowShell(onClose = { close() }) {
        Column(
            Modifier.widthIn(max = 360.dp).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (vm.failed) {
                // MARK: 실패 / 시간 초과
                Box(Modifier.size(56.dp).background(NC.dangerSoft, CircleShape), contentAlignment = Alignment.Center) {
                    SfImage("exclamationmark.circle", size = 28.dp, tint = NC.danger)
                }
                Text("계획을 만들지 못했어요", style = NType.h4, color = NC.ink, modifier = Modifier.padding(top = 20.dp))
                Text(
                    vm.failMessageText,
                    style = pt(14),
                    color = NC.ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NButton("정보 수정하기", variant = NButtonVariant.secondary) {
                        GCTracker.track(GCEvent.editInfo, "session_id" to vm.sessionId, "fail_reason" to vm.failReason)
                        nav.go(GoalCreationRoute.review(vm.sessionId))
                    }
                    NButton("다시 시도", variant = NButtonVariant.ai, icon = "sparkles", loading = vm.retrying) {
                        GCTracker.track(GCEvent.genRetry, "session_id" to vm.sessionId, "draft_id" to vm.draftId, "fail_reason" to vm.failReason)
                        scope.launch { vm.retry()?.let { nav.go(it) } }
                    }
                }
            } else {
                // MARK: 생성 중
                val step = vm.draft?.progressStep ?: "ANALYZING"
                val currentIndex = maxOf(GoalCreationConst.progressSteps.indexOfFirst { it.step == step }, 0)
                val done = vm.status == GoalCreationDraftStatus.READY
                GoalCreationPulse()
                AnimatedContent(
                    targetState = if (done) "계획이 완성됐어요" else GoalCreationConst.progressSteps[currentIndex].label,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                    label = "step-label",
                    modifier = Modifier.padding(top = 32.dp),
                ) { label ->
                    Text(label, style = pt(20, PTWeight.bold), color = NC.ink, textAlign = TextAlign.Center)
                }
                Text(
                    "보통 30초 정도 걸려요. 화면을 나가도 계속 만들어요.",
                    style = pt(14),
                    color = NC.ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Column(Modifier.padding(top = 40.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GoalCreationConst.progressSteps.forEachIndexed { i, s ->
                        val state = when {
                            done || i < currentIndex -> GoalCreationStepState.done
                            i == currentIndex -> GoalCreationStepState.current
                            else -> GoalCreationStepState.todo
                        }
                        GoalCreationStepRow(s.label, state)
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalCreationStepRow(label: String, state: GoalCreationStepState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(24.dp).background(
                when (state) {
                    GoalCreationStepState.done -> NC.ink
                    GoalCreationStepState.current -> NC.brandSoft
                    GoalCreationStepState.todo -> NC.subtle
                },
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                GoalCreationStepState.done -> SfImage("checkmark", size = 11.dp, tint = NC.onInk)
                GoalCreationStepState.current -> CircularProgressIndicator(color = NC.textBrand, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
                GoalCreationStepState.todo -> Unit
            }
        }
        Text(
            label,
            style = pt(14, if (state == GoalCreationStepState.current) PTWeight.semibold else PTWeight.regular),
            color = if (state == GoalCreationStepState.todo) NC.ink4 else NC.ink,
        )
    }
}

/** 가운데 반짝이 아이콘 + 퍼지는 원 (웹 animate-ping) */
@Composable
private fun GoalCreationPulse() {
    val transition = rememberInfiniteTransition(label = "ping")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "ping-progress",
    )
    val brand = NC.aiGradientMid
    val gradient = NC.aiGradient()
    Box(Modifier.size(80.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .scale(1f + 0.6f * progress)
                .alpha(1f - progress)
                .background(brand.copy(alpha = 0.15f), CircleShape)
        )
        Box(Modifier.fillMaxSize().background(gradient, CircleShape))
        SfImage("sparkles", size = 30.dp, tint = Color.White)
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun GoalCreationGeneratingViewPreview() {
    NaleulTheme(dark = false) {
        GoalCreationGeneratingView(
            preview = GoalCreationDraftResponse(
                draftId = 7, version = 1, status = GoalCreationDraftStatus.GENERATING, planningStyle = PlanningStyle.PLANNER,
                progressStep = "MILESTONES", pollAfterMs = 2000, plan = null, errorCode = null, message = null,
            )
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun GoalCreationGeneratingViewFailedPreview() {
    NaleulTheme(dark = false) {
        GoalCreationGeneratingView(
            preview = GoalCreationDraftResponse(
                draftId = 7, version = 1, status = GoalCreationDraftStatus.FAILED, planningStyle = null,
                progressStep = null, pollAfterMs = null, plan = null, errorCode = "AI_TIMEOUT",
                message = "AI 응답이 늦어 계획을 만들지 못했어요.",
            )
        )
    }
}
