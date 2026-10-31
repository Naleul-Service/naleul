// feature/goalcreation/DraftReviewView.kt
//
// iOS Features/GoalCreation/DraftReviewView.swift
// G-4 초안 검토 · 수정 · 확정 (/goal/new/{sessionId}/draft).
// 웹 원본: features/goal-creation/ui/draft/DraftScreen.tsx · DraftReviewView.tsx
//
// 휴대폰 배치: 웹의 넓은 2단(목표+수치 / 루틴+일회성)을 세로로 쌓고, 확정 바는 화면 아래에 붙여요.

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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

// MARK: - 초안 불러오기 (웹 DraftLoader)

@Composable
fun DraftLoaderView(session: GoalCreationSessionDetail, reload: suspend () -> Unit) {
    var draft by remember { mutableStateOf<GoalCreationDraftResponse?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        // 초안 내용은 바뀌지 않아요 (새 초안은 새 draftId) — 한 번만 받아요
        val draftId = session.latestDraft?.draftId ?: return
        loadError = null
        try {
            draft = GoalCreationAPI.getDraft(session.sessionId, draftId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GCTracker.track(GCEvent.sessionError, "session_id" to session.sessionId, "error_status" to gcErrorStatus(e), "from_screen" to "draft")
            loadError = errorMessage(e)
        }
    }

    val d = draft
    if (d != null && d.plan != null) {
        // 새 초안이 오면 편집 상태를 새로 시작
        key(d.draftId) { DraftReviewView(session = session, draft = d, reload = reload) }
    } else {
        LaunchedEffect(session.latestDraft?.draftId ?: 0) { load() }
        GoalCreationFlowShell {
            val err = loadError
            if (err != null) {
                GoalCreationCenterMessage(title = "초안을 불러오지 못했어요", description = err) {
                    NButton("다시 시도") {
                        GCTracker.track(GCEvent.ctaClick, "session_id" to session.sessionId, "cta" to "retry", "session_status" to "DRAFT_ERROR")
                        scope.launch { load() }
                    }
                }
            } else {
                CircularProgressIndicator(color = NC.ink3, strokeWidth = 2.5.dp)
            }
        }
    }
}

// MARK: - 상태

/** 문제 항목으로 스크롤 요청 (같은 키라도 다시 스크롤되게 UUID) */
data class DraftScrollRequest(val key: String, val token: String = UUID.randomUUID().toString()) { companion object }

class DraftReviewModel(
    val session: GoalCreationSessionDetail,
    val draft: GoalCreationDraftResponse,
) : ScreenModel() {
    private val originalPlan: DraftGoalPlan = draft.plan ?: GoalCreationSamples.plan

    var plan by mutableStateOf(originalPlan)
    var colorId by mutableStateOf<Int?>(null)
    var serverIssues by mutableStateOf<List<DraftPlanIssue>?>(null)
    var showClientIssues by mutableStateOf(false)
    var editTarget by mutableStateOf<DraftEditTarget?>(null)

    var confirming by mutableStateOf(false)
    var regenOpen by mutableStateOf(false)
    var feedback by mutableStateOf("")
    var regenerating by mutableStateOf(false)
    var styleOpen by mutableStateOf(false)
    var pendingStyle by mutableStateOf<PlanningStyle?>(null)
    var styleConfirmOpen by mutableStateOf(false)
    var intensityOpen by mutableStateOf(false)
    var pendingIntensity by mutableStateOf<GoalCreationPlanIntensity?>(null)
    var intensityConfirmOpen by mutableStateOf(false)
    var cancelOpen by mutableStateOf(false)
    var cancelling by mutableStateOf(false)
    var quickFix by mutableStateOf("")
    /** 세부 목표·마일스톤은 기본으로 접어 둬요 (트리 구조를 몰라도 루틴만 보고 고칠 수 있게) */
    var structureOpen by mutableStateOf(false)
    var scrollRequest by mutableStateOf<DraftScrollRequest?>(null)

    var usage by mutableStateOf<AiUsage?>(null)
    var colors by mutableStateOf<List<UserColor>>(emptyList())

    val sessionId: Int get() = session.sessionId
    val dirty: Boolean get() = plan != originalPlan
    val clientIssues: List<DraftPlanIssue> get() = DraftPlanUtils.validatePlan(plan)
    val shownIssues: List<DraftPlanIssue> get() = (serverIssues ?: emptyList()) + (if (showClientIssues) clientIssues else emptyList())
    val issueMap: DraftIssueMap get() = DraftPlanUtils.groupIssues(shownIssues)
    val routineCount: Int get() = plan.tasks.count { it.isRoutine }
    val oneTimeCount: Int get() = plan.tasks.size - routineCount
    val busy: Boolean get() = confirming || regenerating || cancelling
    val currentIntensity: GoalCreationPlanIntensity get() = plan.fit?.intensity ?: GoalCreationPlanIntensity.STEADY

    /** 세부 구조 안에 고칠 곳이 있으면 접혀 있지 않게 */
    val structureHasIssue: Boolean
        get() = issueMap.keys.any { it.startsWith("subGoals") || it.startsWith("milestones") }

    // 계획 다시 만들기 남은 횟수: 이 대화 한도와 오늘 한도 중 작은 쪽
    val regenLeft: UsageItem? get() = usage?.session
    /** 하루 · 주 · 월 중 가장 먼저 바닥나는 초안 한도 (2026-10) */
    val draftLeft: UsageItem? get() = usage?.tightest("GOAL_DRAFT")
    val regenRemaining: Int? get() = listOfNotNull(regenLeft?.remaining, draftLeft?.remaining).minOrNull()
    val noRegen: Boolean get() = regenRemaining == 0

    suspend fun loadExtras() {
        try {
            UsageAPI.usage(sessionId = sessionId)?.let { usage = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
        try {
            colors = ColorAPI.userColors()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
    }

    // MARK: 편집

    private fun update(next: DraftGoalPlan) {
        // 마일스톤 날짜·수치, 할 일 마감을 자동으로 맞춰요 → 하나 고쳤다고 다른 곳에 오류가 생기지 않게
        plan = DraftPlanUtils.normalizePlan(next)
        // 서버 결과는 이전 내용 기준이라 지워요 (index 가 바뀌었을 수 있음)
        serverIssues = null
    }

    fun applyEdit(r: DraftEditResult) {
        var p = plan
        when (r) {
            is DraftEditResult.goal -> {
                var g = p.goal.copy(title = r.title, emoji = r.emoji, startDate = r.startDate, endDate = r.endDate)
                val m = g.metric
                val v = r.metricValues
                if (m != null && v != null) g = g.copy(metric = m.copy(startValue = v.start, targetValue = v.target))
                p = p.copy(goal = g)
            }
            is DraftEditResult.subGoal -> {
                val list = p.subGoals.toMutableList()
                val i = r.index
                if (i != null && i in list.indices) list[i] = r.item else list.add(r.item)
                p = p.copy(subGoals = list)
            }
            is DraftEditResult.milestone -> {
                val list = p.milestones.toMutableList()
                val i = r.index
                if (i != null && i in list.indices) list[i] = r.item else list.add(r.item)
                // 마일스톤은 항상 날짜 순서로
                p = p.copy(milestones = list.sortedBy { it.dueDate })
            }
            is DraftEditResult.task -> {
                val list = p.tasks.toMutableList()
                val i = r.index
                if (i != null && i in list.indices) list[i] = r.item else list.add(r.item)
                p = p.copy(tasks = list)
            }
        }
        update(p)
        editTarget?.let {
            GCTracker.track(GCEvent.itemEditSave, "session_id" to sessionId, "item_kind" to it.analyticsKind, "is_new" to it.isNew)
        }
        GCTracker.sheetClose = "saved"
        editTarget = null
    }

    /** 지울 수 없는 이유 (최소 개수) */
    fun deleteBlockReason(t: DraftEditTarget?): String? {
        if (t == null) return null
        val L = DraftPlanUtils.Limits
        return when (t) {
            is DraftEditTarget.goal -> null
            is DraftEditTarget.subGoal ->
                if (plan.subGoals.size <= L.subGoals.min) "세부 목표는 최소 ${L.subGoals.min}개가 필요해요." else null
            is DraftEditTarget.milestone ->
                if (plan.milestones.size <= L.milestones.min) "마일스톤은 최소 ${L.milestones.min}개가 필요해요." else null
            is DraftEditTarget.task -> when {
                routineCount + oneTimeCount <= 1 -> "할 일은 최소 1개가 필요해요."
                t.type == DraftTaskType.ROUTINE && routineCount <= L.routines.min -> "루틴은 최소 ${L.routines.min}개가 필요해요."
                t.type == DraftTaskType.ONE_TIME && oneTimeCount <= L.oneTimes.min -> "할 일은 최소 ${L.oneTimes.min}개가 필요해요."
                else -> null
            }
        }
    }

    fun deleteTarget() {
        val t = editTarget ?: return
        val index = t.index ?: return
        var p = plan
        when (t) {
            is DraftEditTarget.goal -> return
            is DraftEditTarget.subGoal -> {
                if (index !in p.subGoals.indices) return
                val list = p.subGoals.toMutableList()
                val removed = list.removeAt(index)
                // 지운 세부 목표에 연결된 Task 는 첫 번째 세부 목표로 옮겨요
                val firstId = list.firstOrNull()?.tempId
                p = p.copy(
                    subGoals = list,
                    tasks = p.tasks.map { if (it.subGoalTempId == removed.tempId) it.copy(subGoalTempId = firstId) else it },
                )
            }
            is DraftEditTarget.milestone -> {
                if (index !in p.milestones.indices) return
                val list = p.milestones.toMutableList()
                val removed = list.removeAt(index)
                p = p.copy(
                    milestones = list,
                    tasks = p.tasks.map { if (it.milestoneTempId == removed.tempId) it.copy(milestoneTempId = null) else it },
                )
            }
            is DraftEditTarget.task -> {
                if (index !in p.tasks.indices) return
                p = p.copy(tasks = p.tasks.filterIndexed { i, _ -> i != index })
            }
        }
        update(p)
        GCTracker.sheetClose = "deleted"
        editTarget = null
        Toast.show("삭제했어요.")
    }

    // MARK: 스크롤

    fun scrollToIssue(issues: List<DraftPlanIssue>) {
        val first = issues.firstOrNull() ?: return
        scrollRequest = DraftScrollRequest(key = DraftPlanUtils.itemKeyOf(first.path))
    }

    // MARK: 다시 생성 / 성향 · 강도 변경

    /** 성공하면 이동할 단계 */
    /** source: regen / quick_fix / style_change / intensity_change (regen_result · 다음 생성 중 단계의 generate_source) */
    suspend fun regenerate(body: GoalCreationDraftRequestBody, source: String): GoalCreationRoute? {
        regenerating = true
        try {
            val res = GoalCreationAPI.requestDraft(sessionId, body)
            GCTracker.track(
                GCEvent.regenResult,
                "session_id" to sessionId, "generate_source" to source, "result" to "success", "draft_id" to res.draftId,
            )
            Invalidation.invalidate(DataKey.usage)
            GCTracker.setGenerateSource(source)
            return GoalCreationRoute.generating(sessionId, draftId = res.draftId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            regenerating = false
            GCTracker.track(
                GCEvent.regenResult,
                "session_id" to sessionId, "generate_source" to source, "result" to "fail", "error_status" to gcErrorStatus(e),
            )
            if ((e as? ApiError)?.httpStatus == 409) {
                GCTracker.setGenerateSource(source)
                return GoalCreationRoute.generating(sessionId, draftId = null)
            }
            Toast.error(errorMessage(e, "다시 만들지 못했어요."))
            return null
        }
    }

    private val userStyle: PlanningStyle?
        get() = if (GoalCreationStyleMemory.wasSelectedByUser(sessionId)) plan.goal.planningStyle else null

    suspend fun submitFeedback(): GoalCreationRoute? {
        val text = GoalCreationForm.trim(feedback)
        return regenerate(GoalCreationDraftRequestBody(feedback = text.ifEmpty { null }, planningStyle = userStyle), source = "regen")
    }

    /**
     * 말로 고치기 — 화면에서 고친 지금 초안(basePlan)을 기준으로 이 문장에 해당하는 부분만 AI 가 바꿔요.
     * "다시 생성"과 달리 직접 고친 내용이 사라지지 않아요.
     */
    suspend fun submitQuickFix(): GoalCreationRoute? {
        val text = GoalCreationForm.trim(quickFix)
        if (text.isEmpty() || busy) return null
        return regenerate(GoalCreationDraftRequestBody(feedback = text, planningStyle = userStyle, basePlan = plan), source = "quick_fix")
    }

    suspend fun changeStyle(): GoalCreationRoute? {
        val next = pendingStyle ?: return null
        GoalCreationStyleMemory.markSelectedByUser(sessionId)
        pendingStyle = null
        return regenerate(GoalCreationDraftRequestBody(planningStyle = next), source = "style_change")
    }

    /** 강도 변경 — 서버가 이 사용자 기준 상한을 다시 계산해서 처음부터 다시 만들어요 */
    suspend fun changeIntensity(): GoalCreationRoute? {
        val next = pendingIntensity ?: return null
        val style = userStyle
        pendingIntensity = null
        return regenerate(GoalCreationDraftRequestBody(planningStyle = style, intensity = next), source = "intensity_change")
    }

    // MARK: 확정

    sealed class ConfirmOutcome {
        data class created(val goalId: Int) : ConfirmOutcome()
        /** 이미 확정된 세션 (다른 기기 / 두 번 누름) */
        object alreadyConfirmed : ConfirmOutcome()
        /** 서버 상태가 바뀜 → 세션 다시 불러오기 */
        object resync : ConfirmOutcome()
        object stay : ConfirmOutcome()
    }

    suspend fun confirm(): ConfirmOutcome {
        if (busy) return ConfirmOutcome.stay
        val ci = clientIssues
        if (ci.isNotEmpty()) {
            GCTracker.track(GCEvent.confirmBlocked, "session_id" to sessionId, "issue_count" to ci.size)
            showClientIssues = true
            serverIssues = null
            Toast.error("수정이 필요한 항목이 있어요.")
            scrollToIssue(ci)
            return ConfirmOutcome.stay
        }

        confirming = true
        try {
            val r = GoalCreationAPI.confirm(
                sessionId,
                GoalCreationConfirmRequest(
                    draftId = draft.draftId,
                    plan = plan,
                    planningStyleSelectedByUser = GoalCreationStyleMemory.wasSelectedByUser(sessionId),
                    colorId = colorId,
                ),
            )
            // 목표 목록 · 캘린더(루틴이 TimeBlock 에 배치돼요) · 이어서 만들기 카드 갱신
            GCTracker.track(
                GCEvent.confirmResult,
                "session_id" to sessionId, "draft_id" to draft.draftId, "result" to "success", "goal_category_id" to r.goalId,
            )
            Invalidation.invalidate(DataKey.goals, DataKey.timetable, DataKey.goalCreation)
            Toast.success("목표가 만들어졌어요.")
            return ConfirmOutcome.created(r.goalId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            confirming = false
            val api = e as? ApiError
            val issueCount = if (api?.httpStatus == 422) api.violations.size else 0
            GCTracker.track(
                GCEvent.confirmResult,
                "session_id" to sessionId, "draft_id" to draft.draftId, "result" to "fail",
                "error_status" to gcErrorStatus(e), "issue_count" to if (issueCount > 0) issueCount else null,
            )
            if (api == null) {
                Toast.error("목표를 만들지 못했어요. 다시 시도해 주세요.")
                return ConfirmOutcome.stay
            }
            val message = api.errorDescription
            val violations = api.violations
            if (api.httpStatus == 422 && violations.isNotEmpty()) {
                val list = violations.map { DraftPlanIssue(ruleId = it.ruleId, path = it.path, message = it.message) }
                serverIssues = list
                Toast.error(message)
                scrollToIssue(list)
                return ConfirmOutcome.stay
            }
            if (api.httpStatus == 409) {
                // 이미 확정됐거나 더 새로운 초안이 있음 → 서버 상태 다시 확인
                Toast.show(message)
                val latest = try {
                    GoalCreationAPI.getSession(sessionId)
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: Exception) {
                    null
                }
                if (latest?.status == GoalCreationSessionStatus.CONFIRMED) return ConfirmOutcome.alreadyConfirmed
                return ConfirmOutcome.resync
            }
            // 403(무료 플랜 목표 개수 초과) / 기타
            Toast.error(message)
            return ConfirmOutcome.stay
        }
    }

    suspend fun cancelSession(): Boolean {
        cancelling = true
        try {
            GoalCreationAPI.cancel(sessionId)
            Invalidation.invalidate(DataKey.goalCreation)
            Toast.show("목표 만들기를 그만뒀어요.")
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cancelling = false
            Toast.error(errorMessage(e, "취소하지 못했어요."))
            return false
        }
    }

    companion object {
        const val feedbackMax = 200
        /** 말로 고치기 예시 */
        val quickFixExamples = listOf("운동은 주 2회로 줄여줘", "루틴을 저녁 시간으로 옮겨줘", "하나만 남기고 더 가볍게")
    }
}

// MARK: - 화면

@Composable
fun DraftReviewView(session: GoalCreationSessionDetail, draft: GoalCreationDraftResponse, reload: suspend () -> Unit) {
    val vm = rememberScreenModel(draft.draftId) { DraftReviewModel(session = session, draft = draft) }
    DraftReviewContent(vm = vm, reload = reload, isPreview = false)
}

/** 미리보기용 (사용량 · 색상을 불러오지 않아요) */
@Composable
fun DraftReviewView(preview: DraftReviewModel) {
    DraftReviewContent(vm = preview, reload = {}, isPreview = true)
}

@Composable
private fun DraftReviewContent(vm: DraftReviewModel, reload: suspend () -> Unit, isPreview: Boolean) {
    val nav = LocalGoalCreationNavigator.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val anchors = remember { DraftScrollAnchors() }
    var viewportHeight by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (isPreview) return@LaunchedEffect
        vm.loadExtras()
        val p = vm.plan
        GCTracker.track(
            GCEvent.draftPageview,
            "session_id" to vm.sessionId, "draft_id" to vm.draft.draftId,
            "plan_routine_count" to vm.routineCount, "plan_one_time_count" to vm.oneTimeCount,
            "plan_subgoal_count" to p.subGoals.size, "plan_milestone_count" to p.milestones.size,
            "warning_count" to p.warnings.size, "regen_remaining" to vm.regenRemaining,
            "planning_style" to p.goal.planningStyle.name, "plan_intensity" to p.fit?.intensity?.name,
            "has_fit" to (p.fit != null),
        )
    }
    // 앱이 뒤로 갈 때 붙일 "고친 게 있는지"
    DisposableEffect(vm) {
        GCTracker.dirtyProvider = { vm.dirty }
        onDispose { GCTracker.dirtyProvider = null }
    }
    // 편집 시트가 열릴 때 · 닫힐 때 (닫힐 때는 저장 · 삭제가 아니면 취소)
    var editingKind by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm.editTarget) {
        val t = vm.editTarget ?: run {
            // 저장 · 삭제로 시트가 화면에서 빠진 경우 (onDismiss 가 안 와요)
            editingKind = null
            return@LaunchedEffect
        }
        editingKind = t.analyticsKind
        GCTracker.sheetClose = null
        GCTracker.track(
            GCEvent.itemEditOpen,
            "session_id" to vm.sessionId, "item_kind" to t.analyticsKind, "is_new" to t.isNew,
            "list_position" to t.index?.let { it + 1 },
        )
    }
    fun trackEditClosed() {
        val kind = editingKind ?: return
        editingKind = null
        val m = GCTracker.takeSheetClose() ?: return
        GCTracker.track(GCEvent.itemEditCancel, "session_id" to vm.sessionId, "item_kind" to kind, "close_method" to m)
    }

    // 문제 항목으로 스크롤 — 다음 화면 갱신(빨간 테두리 · 세부 구조 펼치기) 뒤에 이동
    LaunchedEffect(vm.scrollRequest) {
        val req = vm.scrollRequest ?: return@LaunchedEffect
        delay(150)
        val (y, h) = anchors.offsetOf(req.key) ?: return@LaunchedEffect
        // iOS anchor: .center — 항목 가운데가 화면 가운데에 오게
        val target = (y - viewportHeight / 2f + h / 2f).toInt().coerceIn(0, scrollState.maxValue)
        scrollState.animateScrollTo(target, tween(300))
    }

    /** 다시 만들기 성공 → 생성 중 화면으로 */
    fun go(route: GoalCreationRoute?) {
        if (route == null) return
        vm.regenOpen = false
        nav.go(route)
    }

    fun openStyle() {
        GCTracker.track(GCEvent.styleChip, "session_id" to vm.sessionId, "planning_style" to vm.plan.goal.planningStyle.name)
        vm.pendingStyle = null
        vm.styleOpen = true
    }

    fun openIntensity(entry: String) {
        GCTracker.track(
            GCEvent.intensityChip,
            "session_id" to vm.sessionId, "plan_intensity" to vm.currentIntensity.name, "entry_point" to entry,
        )
        vm.pendingIntensity = null
        vm.intensityOpen = true
    }

    fun submitQuickFix(method: String) {
        val text = GoalCreationForm.trim(vm.quickFix)
        if (text.isEmpty() || vm.busy) return
        GCTracker.track(
            GCEvent.quickFixSubmit,
            "session_id" to vm.sessionId, "feedback_length" to GoalCreationForm.count(text), "is_dirty" to vm.dirty,
            "submit_method" to method,
        )
        scope.launch { go(vm.submitQuickFix()) }
    }

    fun confirm() {
        scope.launch {
            when (val outcome = vm.confirm()) {
                // 상세 화면에서 "이어서 TimeTable 에 배치할까요?"를 물어봐요 (이 호출이 전체 화면도 닫아요)
                is DraftReviewModel.ConfirmOutcome.created -> AppRouter.replaceWithGoal(outcome.goalId, created = true)
                is DraftReviewModel.ConfirmOutcome.alreadyConfirmed -> nav.closeToGoalList()
                is DraftReviewModel.ConfirmOutcome.resync -> reload()
                is DraftReviewModel.ConfirmOutcome.stay -> Unit
            }
        }
    }

    val issues = vm.issueMap

    CompositionLocalProvider(LocalDraftScrollAnchors provides anchors) {
        Column(Modifier.fillMaxSize().background(NC.canvas)) {
            GoalCreationHeader(onClose = {
                GCTracker.track(GCEvent.draftClose, "session_id" to vm.sessionId, "is_dirty" to vm.dirty)
                nav.close()
            })
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { viewportHeight = it.height },
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .onGloballyPositioned { anchors.content = it }
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DraftReviewHeader(vm)
                    DraftConditionsCard(vm) {
                        GCTracker.track(GCEvent.conditionEdit, "session_id" to vm.sessionId, "is_dirty" to vm.dirty)
                        nav.go(GoalCreationRoute.review(vm.sessionId))
                    }
                    vm.plan.fit?.let { DraftFitCard(vm, it) { openIntensity("fit_card") } }
                    if (vm.plan.warnings.isNotEmpty()) DraftWarningsCard(vm.plan.warnings)
                    if (vm.shownIssues.isNotEmpty()) DraftIssuesCard(issues.size) {
                        GCTracker.track(GCEvent.issueJump, "session_id" to vm.sessionId, "issue_count" to issues.size)
                        vm.scrollToIssue(vm.shownIssues)
                    }
                    DraftQuickFixCard(vm) { method -> submitQuickFix(method) }

                    // 목표 · 수치 · 루틴 · 일회성
                    DraftGoalHeroCard(
                        plan = vm.plan,
                        issues = issues,
                        colors = vm.colors,
                        colorId = vm.colorId,
                        onColorIdChange = {
                            vm.colorId = it
                            GCTracker.track(GCEvent.colorSelect, "session_id" to vm.sessionId, "color_id" to it)
                        },
                        onEdit = { vm.editTarget = it },
                    ) {
                        // 성향 · 강도 칩 (목표 카드 위)
                        DraftChipButton(vm.plan.goal.planningStyle.goalCreationLabel) { openStyle() }
                        if (vm.plan.fit != null) DraftChipButton(vm.currentIntensity.label) { openIntensity("chip") }
                    }
                    DraftMetricCard(plan = vm.plan, onEditGoal = { vm.editTarget = DraftEditTarget.goal })
                    // 실제로 할 일 — 루틴이 계획의 중심, 일회성 Task 는 꼭 필요한 것만
                    DraftRoutinesCard(plan = vm.plan, issues = issues, onEdit = { vm.editTarget = it })
                    DraftOneTimeTasksCard(plan = vm.plan, issues = issues, onEdit = { vm.editTarget = it })
                    // V52: AI 초안은 마일스톤 없이 세부 목표 1개로 와서, 그럴 땐 이 칸을 아예 숨겨요 (예전 초안 · 문제가 있을 때만)
                    if (vm.plan.milestones.isNotEmpty() || vm.plan.subGoals.size > 1 || vm.structureHasIssue) {
                        DraftStructureSection(vm, issues)
                    }
                }
            }
            DraftConfirmBar(vm) {
                GCTracker.track(
                    GCEvent.confirmClick,
                    "session_id" to vm.sessionId, "draft_id" to vm.draft.draftId, "is_dirty" to vm.dirty,
                    "color_id" to vm.colorId, "plan_routine_count" to vm.routineCount, "plan_one_time_count" to vm.oneTimeCount,
                )
                confirm()
            }
        }
    }

    // MARK: 시트 · 확인창

    vm.editTarget?.let { target ->
        DraftItemEditorSheet(
            target = target,
            plan = vm.plan,
            onSave = { vm.applyEdit(it) },
            onDelete = { vm.deleteTarget() },
            deleteDisabledReason = vm.deleteBlockReason(target),
            onDismiss = { vm.editTarget = null; trackEditClosed() },
        )
    }

    if (vm.regenOpen) {
        SheetScaffold(
            onDismiss = {
                vm.regenOpen = false
                // 다시 생성으로 넘어간 게 아니면 취소 (취소 버튼 / X / 쓸어 닫기)
                GCTracker.takeSheetClose()?.let { m ->
                    GCTracker.track(GCEvent.regenCancel, "session_id" to vm.sessionId, "close_method" to m)
                }
            },
            title = "계획을 다시 만들까요?",
            description = if (vm.dirty) "직접 수정한 내용은 사라지고 새 초안이 만들어져요." else "바꾸고 싶은 점을 알려주면 반영해서 다시 만들어요.",
            dismissible = !vm.regenerating,
            footer = { close ->
                NButton("취소", variant = NButtonVariant.secondary, enabled = !vm.regenerating) {
                    GCTracker.sheetClose = "cancel_button"
                    close()
                }
                NButton("다시 생성", variant = NButtonVariant.ai, icon = "sparkles", loading = vm.regenerating) {
                    GCTracker.track(
                        GCEvent.regenSubmit,
                        "session_id" to vm.sessionId, "feedback_length" to GoalCreationForm.count(GoalCreationForm.trim(vm.feedback)),
                        "is_dirty" to vm.dirty,
                    )
                    GCTracker.sheetClose = "saved"
                    scope.launch { go(vm.submitFeedback()) }
                }
            },
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NTextField(
                    vm.feedback,
                    { vm.feedback = GoalCreationForm.prefix(it, DraftReviewModel.feedbackMax) },
                    placeholder = "예) 운동을 조금 더 가볍게 해주세요 (선택)",
                    minHeight = 112.dp,
                    singleLine = false,
                )
                Text("${GoalCreationForm.count(vm.feedback)}/${DraftReviewModel.feedbackMax}", style = ptMono(12), color = NC.ink3)
            }
        }
    }

    if (vm.styleOpen) {
        SheetScaffold(
            onDismiss = {
                vm.styleOpen = false
                if (vm.pendingStyle != null) vm.styleConfirmOpen = true
                else GCTracker.track(GCEvent.styleChangeCancel, "session_id" to vm.sessionId)
            },
            title = "실행 성향",
        ) { close ->
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (value in listOf(PlanningStyle.PLANNER, PlanningStyle.SPONTANEOUS)) {
                    val current = vm.plan.goal.planningStyle == value
                    DraftOptionButton(title = value.goalCreationLabel, description = null, current = current) {
                        if (!current) vm.pendingStyle = value
                        close()
                    }
                }
            }
        }
    }

    if (vm.intensityOpen) {
        SheetScaffold(
            onDismiss = {
                vm.intensityOpen = false
                if (vm.pendingIntensity != null) vm.intensityConfirmOpen = true
                else GCTracker.track(GCEvent.intensityChangeCancel, "session_id" to vm.sessionId)
            },
            title = "계획 강도",
        ) { close ->
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (value in GoalCreationPlanIntensity.entries) {
                    val current = vm.currentIntensity == value
                    DraftOptionButton(title = value.label, description = value.descriptionText, current = current) {
                        if (!current) vm.pendingIntensity = value
                        close()
                    }
                }
            }
        }
    }

    ConfirmDialog(
        isPresented = vm.styleConfirmOpen,
        onDismiss = { vm.styleConfirmOpen = false },
        title = "성향을 바꿀까요?",
        message = "성향에 맞게 Task 배치가 달라져서, 계획을 처음부터 다시 만들어요.",
        confirmLabel = "바꾸고 다시 만들기",
        onCancel = {
            vm.pendingStyle = null
            GCTracker.track(GCEvent.styleChangeCancel, "session_id" to vm.sessionId)
        },
    ) {
        GCTracker.track(GCEvent.styleChangeConfirm, "session_id" to vm.sessionId, "planning_style" to vm.pendingStyle?.name)
        scope.launch { go(vm.changeStyle()) }
    }

    ConfirmDialog(
        isPresented = vm.intensityConfirmOpen,
        onDismiss = { vm.intensityConfirmOpen = false },
        title = "${vm.pendingIntensity?.label ?: ""}으로 바꿀까요?",
        message = "루틴 양이 달라져서 계획을 처음부터 다시 만들어요. 직접 고친 내용은 사라져요.",
        confirmLabel = "바꾸고 다시 만들기",
        onCancel = {
            vm.pendingIntensity = null
            GCTracker.track(GCEvent.intensityChangeCancel, "session_id" to vm.sessionId)
        },
    ) {
        GCTracker.track(
            GCEvent.intensityChangeConfirm,
            "session_id" to vm.sessionId, "plan_intensity" to vm.pendingIntensity?.name, "is_dirty" to vm.dirty,
        )
        scope.launch { go(vm.changeIntensity()) }
    }

    ConfirmDialog(
        isPresented = vm.cancelOpen,
        onDismiss = { vm.cancelOpen = false },
        title = "목표 만들기를 그만둘까요?",
        message = "지금까지 나눈 대화와 초안이 모두 사라져요.",
        confirmLabel = "그만 만들기",
        cancelLabel = "계속 만들기",
        tone = ConfirmTone.danger,
        onCancel = { GCTracker.track(GCEvent.cancelDismiss, "session_id" to vm.sessionId) },
    ) {
        GCTracker.track(GCEvent.cancelConfirm, "session_id" to vm.sessionId)
        scope.launch { if (vm.cancelSession()) nav.close() }
    }
}

// MARK: 머리말 · 조건

@Composable
private fun DraftReviewHeader(vm: DraftReviewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AIBadge()
                Text("AI 목표 설계", style = NType.h2, color = NC.textPrimary)
            }
            Text("AI가 만든 초안이에요. 항목을 누르면 바로 고칠 수 있어요.", style = pt(14), color = NC.ink3)
        }
        NButton("그만 만들기", variant = NButtonVariant.ghost, size = NButtonSize.sm, enabled = !vm.busy) {
            GCTracker.track(GCEvent.cancelClick, "session_id" to vm.sessionId, "is_dirty" to vm.dirty)
            vm.cancelOpen = true
        }
    }
}

@Composable
private fun DraftConditionsCard(vm: DraftReviewModel, onEditConditions: () -> Unit) {
    val s = vm.session.summary
    val must = vm.session.mustDoItems ?: emptyList()
    val chips = listOfNotNull(
        s?.deadlineText, s?.metricText, s?.preferenceText,
        if (must.isEmpty()) null else "꼭 할 일: ${must.joinToString(" · ")}",
    )
    val shape = RoundedCornerShape(Radius.card)
    Column(
        Modifier
            .fillMaxWidth()
            .background(NC.surface, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("이루고 싶은 목표", style = pt(12), color = NC.ink3)
            Text(
                s?.goalStatement ?: vm.plan.goal.title,
                style = NType.h4,
                color = NC.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FlowLayout(spacing = 8.dp) {
            for (chip in chips) {
                Text(
                    chip,
                    style = pt(13, PTWeight.medium),
                    color = NC.ink2,
                    modifier = Modifier.background(NC.subtle, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Text(
                "조건 수정",
                style = pt(13, PTWeight.semibold).copy(textDecoration = TextDecoration.Underline),
                color = NC.ink2,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onEditConditions)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/** "나에게 맞춘 점" — 서버가 실제 계획과 대조해 확인한 근거만 보여줘요 */
@Composable
private fun DraftFitCard(vm: DraftReviewModel, fit: DraftPlanFit, onChangeIntensity: () -> Unit) {
    // 직접 고친 내용까지 반영한 지금 계획의 루틴 주간 합계
    val weekly = DraftPlanUtils.weeklyRoutineMinutes(vm.plan.tasks)
    val over = weekly > fit.weeklyCapMinutes
    val warnInk = NC.warningInk
    val ink = NC.ink
    val ink3 = NC.ink3
    val shape = RoundedCornerShape(Radius.xxl)
    Column(
        Modifier
            .fillMaxWidth()
            .background(NC.surface, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SfImage("person.crop.circle.badge.checkmark", size = 14.dp, tint = NC.textBrand)
                Text("나에게 맞춘 점", style = pt(14, PTWeight.bold), color = NC.ink)
            }
            Spacer(Modifier.weight(1f).widthIn(min = 8.dp))
            Text(
                "${fit.intensity.label} · 바꾸기",
                style = pt(13, PTWeight.semibold),
                color = NC.ink2,
                modifier = Modifier.clickable(role = Role.Button, onClick = onChangeIntensity),
            )
        }
        Text(
            buildAnnotatedString {
                append("루틴은 일주일에 ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (over) warnInk else ink)) {
                    append(DraftPlanUtils.hoursText(weekly))
                }
                withStyle(SpanStyle(color = ink3)) {
                    append(" · 내 기준 ${DraftPlanUtils.hoursText(fit.weeklyCapMinutes)} 이내 추천")
                }
            },
            style = pt(13),
            color = NC.ink2,
        )
        if (over) {
            Text("지금 실천량보다 많아요. 처음엔 조금 줄여서 시작하면 끝까지 가기 쉬워요.", style = pt(13), color = NC.warningInk)
        }
        if (fit.notes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (note in fit.notes) Text("· $note", style = pt(13), color = NC.ink2)
            }
        }
    }
}

/** SOFT 경고 */
@Composable
private fun DraftWarningsCard(warnings: List<DraftPlanIssue>) {
    val shape = RoundedCornerShape(Radius.xxl)
    Column(
        Modifier
            .fillMaxWidth()
            .background(NC.warningSoft, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SfImage("exclamationmark.triangle", size = 13.dp, tint = NC.warningInk)
            Text("확인해 보면 좋은 점", style = pt(14, PTWeight.bold), color = NC.warningInk)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (w in warnings) Text("· ${w.message}", style = pt(13), color = NC.warningInkStrong)
        }
    }
}

/** 검증 실패 요약 */
@Composable
private fun DraftIssuesCard(count: Int, onJump: () -> Unit) {
    val shape = RoundedCornerShape(Radius.xxl)
    Column(
        Modifier
            .fillMaxWidth()
            .background(NC.dangerSoft, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("수정이 필요한 항목이 ${count}곳 있어요", style = pt(14, PTWeight.bold), color = NC.danger)
        Text(
            "첫 번째 항목으로 이동",
            style = pt(13, PTWeight.semibold).copy(textDecoration = TextDecoration.Underline),
            color = NC.danger,
            modifier = Modifier.clickable(role = Role.Button, onClick = onJump),
        )
    }
}

/** 말로 고치기: 트리를 몰라도 한 문장으로 */
@Composable
private fun DraftQuickFixCard(vm: DraftReviewModel, onSubmit: (method: String) -> Unit) {
    val shape = RoundedCornerShape(Radius.card)
    val ink = NC.ink
    val ink4 = NC.ink4
    Column(
        Modifier
            .fillMaxWidth()
            .aiBorder(shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AIBadge(AIBadgeSize.sm)
            BasicTextField(
                value = vm.quickFix,
                onValueChange = { vm.quickFix = GoalCreationForm.prefix(it, DraftReviewModel.feedbackMax) },
                enabled = !vm.busy,
                textStyle = pt(15).copy(color = ink),
                cursorBrush = SolidColor(NC.brand),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit("keyboard") }),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp)
                    .semantics { contentDescription = "말로 고치기" },
                decorationBox = { inner ->
                    Box(Modifier.defaultMinSize(minHeight = 40.dp), contentAlignment = Alignment.CenterStart) {
                        if (vm.quickFix.isEmpty()) {
                            Text("바꾸고 싶은 걸 말로 적어 보세요. 예) 운동은 주 2회로 줄여줘", style = pt(15), color = ink4)
                        }
                        inner()
                    }
                },
            )
            NButton(
                "고치기",
                variant = NButtonVariant.ai,
                size = NButtonSize.sm,
                icon = "arrow.turn.down.left",
                loading = vm.regenerating,
                enabled = GoalCreationForm.trim(vm.quickFix).isNotEmpty() && !vm.busy,
            ) { onSubmit("button") }
        }
        if (vm.quickFix.isEmpty()) {
            FlowLayout(spacing = 6.dp, modifier = Modifier.padding(start = 26.dp)) {
                DraftReviewModel.quickFixExamples.forEachIndexed { i, ex ->
                    Box(
                        Modifier
                            .height(24.dp)
                            .clip(CircleShape)
                            .border(1.dp, NC.borderSubtle, CircleShape)
                            .clickable(role = Role.Button) {
                                GCTracker.track(GCEvent.quickFixExample, "session_id" to vm.sessionId, "example_index" to i)
                                vm.quickFix = ex
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(ex, style = pt(12), color = NC.ink3, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** 성향 · 강도 칩 */
@Composable
private fun DraftChipButton(title: String, action: () -> Unit) {
    Row(
        Modifier
            .height(24.dp)
            .clip(CircleShape)
            .border(1.dp, NC.borderSubtle, CircleShape)
            .clickable(role = Role.Button, onClick = action)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = pt(12, PTWeight.semibold), color = NC.ink2, maxLines = 1)
        SfImage("chevron.down", size = 9.dp, tint = NC.ink2)
    }
}

// MARK: 세부 구조 (접힘)

@Composable
private fun DraftStructureSection(vm: DraftReviewModel, issues: DraftIssueMap) {
    /** 세부 목표 · 마일스톤을 편집 중이면 펼쳐 둬요 */
    val editingStructure = vm.editTarget.let { it is DraftEditTarget.subGoal || it is DraftEditTarget.milestone }
    val open = vm.structureOpen || vm.structureHasIssue || editingStructure
    val rotation by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "structure-chevron")
    val shape = RoundedCornerShape(Radius.card)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NC.surface, shape)
            .border(1.dp, NC.borderSubtle, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) {
                    vm.structureOpen = !vm.structureOpen
                    // 고칠 곳이 있거나 그 안을 편집 중이면 눌러도 접히지 않아요 → 실제로 보이는 상태를 보내요
                    val editingStructure = vm.editTarget is DraftEditTarget.subGoal || vm.editTarget is DraftEditTarget.milestone
                    GCTracker.track(
                        GCEvent.structureToggle,
                        "session_id" to vm.sessionId, "expanded" to (vm.structureOpen || vm.structureHasIssue || editingStructure),
                    )
                }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("세부 구조", style = pt(15, PTWeight.bold), color = NC.ink)
                Text(
                    "세부 목표 ${vm.plan.subGoals.size}개 · 마일스톤 ${vm.plan.milestones.size}단계 — 날짜${if (vm.plan.goal.metric != null) "·수치" else ""}는 자동으로 맞춰져요. 꼭 바꿀 때만 열어 보세요.",
                    style = pt(13),
                    color = NC.ink3,
                )
            }
            SfImage("chevron.down", size = 15.dp, tint = NC.ink3, modifier = Modifier.rotate(rotation))
        }

        AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                HorizontalDivider(color = NC.borderSoft)
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    DraftMilestoneTimeline(plan = vm.plan, issues = issues, onEdit = { vm.editTarget = it })
                    DraftSubGoalsCard(plan = vm.plan, issues = issues, onEdit = { vm.editTarget = it })
                }
            }
        }
    }
}

// MARK: 하단 확정 바

@Composable
private fun DraftConfirmBar(vm: DraftReviewModel, onConfirm: () -> Unit) {
    val remaining = vm.regenRemaining
    val accent = NC.textBrand
    val regenGradient = NC.aiGradient()
    val dark = NC.actionPrimaryFg
    val regenDisabled = vm.busy || vm.noRegen
    Box(Modifier.fillMaxWidth().background(NC.canvas).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .aiBorder()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    buildAnnotatedString {
                        append("이 목표로 시작하면 ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) { append("Task ${vm.oneTimeCount}개") }
                        append("와 ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) { append("루틴 ${vm.routineCount}개") }
                        append("가 내일부터 TimeBlock에 자동 배치돼요.")
                    },
                    style = pt(14),
                    color = NC.textSecondary,
                )
                // 다시 생성은 한 대화 최대 횟수 + 하루 횟수가 있어요 — 누르기 전에 남은 횟수를 보여줘요
                UsageLine(items = listOf(vm.regenLeft, vm.draftLeft))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val xl = RoundedCornerShape(Radius.xl)
                Row(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .alpha(if (regenDisabled) 0.4f else 1f)
                        .clip(xl)
                        .background(regenGradient, xl)
                        .disabledTap(vm.noRegen && !vm.busy) {
                            GCTracker.track(GCEvent.regenDisabled, "session_id" to vm.sessionId, "regen_remaining" to vm.regenRemaining)
                        }
                        .clickable(enabled = !regenDisabled, role = Role.Button) {
                            GCTracker.track(
                                GCEvent.regenOpen,
                                "session_id" to vm.sessionId, "is_dirty" to vm.dirty, "regen_remaining" to vm.regenRemaining,
                            )
                            GCTracker.sheetClose = null
                            vm.regenOpen = true
                        }
                        .semantics {
                            if (vm.noRegen) contentDescription = "다시 생성 — 오늘(또는 이 목표에서) 계획을 더 만들 수 없어요. 초안을 직접 고쳐 주세요."
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    SfImage("sparkles", size = 16.dp, tint = Color.White)
                    Text("다시 생성", style = NType.bodySMMedium, color = Color.White, maxLines = 1)
                    if (remaining != null) {
                        Text("(${remaining}회 남음)", style = pt(14, PTWeight.semibold), color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                    }
                }
                Row(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .alpha(if (vm.busy && !vm.confirming) 0.4f else 1f)
                        .clip(xl)
                        .background(NC.actionPrimary, xl)
                        .clickable(enabled = !vm.busy, role = Role.Button, onClick = onConfirm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    if (vm.confirming) CircularProgressIndicator(color = dark, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Text("이 목표로 시작하기", style = NType.bodySMMedium, color = dark, maxLines = 1)
                }
            }
        }
    }
}

// MARK: 시트 안 선택 버튼

@Composable
private fun DraftOptionButton(title: String, description: String?, current: Boolean, action: () -> Unit) {
    val shape = RoundedCornerShape(Radius.xxl)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (current) NC.brandSoft else NC.surface, shape)
            .border(if (current) 1.5.dp else 1.dp, if (current) NC.borderFocus else NC.borderSubtle, shape)
            .clickable(role = Role.RadioButton, onClick = action)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = pt(15, PTWeight.semibold), color = if (current) NC.textBrand else NC.ink)
            if (current) Text("현재", style = pt(12, PTWeight.medium), color = NC.textBrand)
        }
        if (description != null) Text(description, style = pt(13), color = NC.ink3)
    }
}

@Preview(showBackground = true, heightDp = 2600)
@Composable
private fun DraftReviewViewPreview() {
    NaleulTheme(dark = false) {
        val vm = remember {
            DraftReviewModel(session = GoalCreationSamples.session, draft = GoalCreationSamples.draft).apply {
                colors = listOf(
                    UserColor(userColorId = 1, hex = "#3D5AFE", isDefault = true),
                    UserColor(userColorId = 2, hex = "#12B76A", isDefault = false),
                    UserColor(userColorId = 3, hex = "#F59E0B", isDefault = false),
                )
                usage = AiUsage(
                    pro = false, resetsAt = "2026-11-19T00:00:00",
                    items = listOf(UsageItem(key = "GOAL_DRAFT", label = "계획 초안", description = "", used = 2, limit = 5, remaining = 3)),
                    session = UsageItem(key = "SESSION_REGENERATE", label = "이 목표 다시 만들기", description = "", used = 1, limit = 3, remaining = 2),
                )
            }
        }
        CompositionLocalProvider(LocalGoalCreationNavigator provides remember { GoalCreationNavigator(GoalCreationRoute.draft(101)) }) {
            DraftReviewView(preview = vm)
        }
    }
}
