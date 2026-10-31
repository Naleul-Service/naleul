// feature/goalcreation/GoalCreationStartView.kt
//
// iOS Features/GoalCreation/GoalCreationStartView.swift
// /goal/new — 카테고리 · 첫 문장 · 꼭 할 일 입력 + 이어서 만들기.
// 웹 원본: features/goal-creation/ui/StartGoalView.tsx · app/(flow)/goal/new/page.tsx

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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class GoalCreationStartViewModel : ScreenModel() {
    var text by mutableStateOf("")
    var kind by mutableStateOf<GoalKindValue?>(null)
    /** 이 목표를 위해 꼭 하고 싶은 일 (예: 다이어트 → 헬스 · 식단 기록) */
    var mustDo by mutableStateOf<List<String>>(emptyList())
    /** 입력창에 적어 두기만 한 일도 함께 보내요 (하나만 할 거면 "+ 추가"를 안 눌러도 돼요) */
    var mustDoDraft by mutableStateOf("")
    var usage by mutableStateOf<AiUsage?>(null)
    /** 구체화할 임시 목표 */
    var source by mutableStateOf<GoalCategory?>(null)
    var starting by mutableStateOf(false)
    /** 이동 중에도 버튼이 다시 눌리지 않게 성공 후에도 막아둬요 */
    var started by mutableStateOf(false)

    val kindReady: Boolean get() = GoalKind.isComplete(kind)
    val goalText: String get() = GoalCreationForm.trim(text)
    val sessionLeft: UsageItem? get() = usage?.item("GOAL_SESSION")
    /** 하루 · 주 · 월 중 가장 먼저 바닥나는 초안 한도 (2026-10) */
    val draftLeft: UsageItem? get() = usage?.tightest("GOAL_DRAFT")
    /** 주간 한도 (Free 주 2개 · Pro 주 10개, 2026-10) */
    val weekLeft: UsageItem? get() = usage?.item("GOAL_SESSION_WEEK")
    /** 월간 한도 (Free 월 8개 · Pro 월 20개, 2026-10) */
    val monthLeft: UsageItem? get() = usage?.item("GOAL_SESSION_MONTH")
    val outOfMonth: Boolean get() = monthLeft?.remaining == 0
    val outOfWeek: Boolean get() = !outOfMonth && weekLeft?.remaining == 0
    /** 남은 횟수를 다 쓰면 시작 버튼을 막고 미리 알려줘요 */
    val outOfQuota: Boolean get() = sessionLeft?.remaining == 0 || draftLeft?.remaining == 0 || outOfWeek || outOfMonth

    /** 예전에 AI 없이 만든 목표를 AI 로 다시 설계하는지 (임시 목표가 아님, 2026-10) */
    val isLegacySource: Boolean get() = source?.let { !it.isTemporary } ?: false
    val refineSource: String? get() = if (source == null) null else if (isLegacySource) "legacy" else "temporary"
    private var prefilled = false
    val busy: Boolean get() = starting || started

    // MARK: 이벤트 값

    /** 시작 화면을 다 불러왔는지 (pageview 는 남은 횟수 · 만들던 목표를 다 알고 나서 한 번) */
    var usageLoaded by mutableStateOf(false)
    var hasResume by mutableStateOf<Boolean?>(null)
    var pageviewSent = false

    /** AI 한도 상태 (ok / out_daily / out_weekly / out_draft) */
    val quotaState: String
        get() = when {
            outOfMonth -> "out_monthly"
            outOfWeek -> "out_weekly"
            sessionLeft?.remaining == 0 -> "out_daily"
            draftLeft?.remaining == 0 -> "out_draft"
            else -> "ok"
        }

    /** 고른 · 적은 게 하나라도 있는지 (닫으면 사라지는 입력) */
    val hasInput: Boolean
        get() = kind != null || goalText.isNotEmpty() || mustDo.isNotEmpty() || GoalCreationForm.trim(mustDoDraft).isNotEmpty()

    /** 시작 버튼이 막힌 이유 (no_kind / no_label / no_text / quota) — 막히지 않았거나 요청 중이면 null */
    fun disabledReason(needsText: Boolean): String? = when {
        busy -> null
        kind == null -> "no_kind"
        !kindReady -> "no_label"
        needsText && goalText.isEmpty() -> "no_text"
        outOfQuota -> "quota"
        else -> null
    }

    /** 고른 2단계에 맞는 예시 (미선택이면 기본 예시, "기타"는 사용자가 적은 이름으로) */
    val examples: List<String>
        get() {
            val k = kind ?: return GoalCreationConst.exampleGoals
            val sub = GoalKind.subOption(k.goalSubType)
            return if (sub.custom) emptyList() else sub.examples
        }

    val placeholder: String
        get() {
            if (!kindReady) return "먼저 카테고리를 골라 주세요"
            val k = kind
            if (k != null && GoalKind.subOption(k.goalSubType).custom) {
                return "예) ${GoalCreationForm.trim(k.goalKindLabel ?: "")} 주 3회 꾸준히 하기"
            }
            return "예) ${examples.firstOrNull() ?: ""}"
        }

    val suggestions: List<String>
        get() {
            val k = kind ?: return emptyList()
            return GoalKind.mustDoSuggestions[k.goalSubType] ?: emptyList()
        }

    companion object {}

    suspend fun loadUsage() {
        try {
            UsageAPI.usage()?.let { usage = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // iOS try? — 못 불러오면 그대로 둬요
        }
        usageLoaded = true
    }

    suspend fun loadSource(id: Int?) {
        if (id == null) return
        try {
            val goals = GoalAPI.goals()
            source = goals.firstOrNull { it.goalCategoryId == id }
            // 이름 · 카테고리를 미리 채워 둬요 (꼭 해야 할 일만 비움) — 바로 시작하거나 고쳐서 시작 (웹 · iOS 와 같아요)
            //  카테고리: 목표에 저장된 값 → 없으면 이름 키워드로 짐작 (AI 를 부르지 않아요)
            val src = source
            if (src != null && !prefilled) {
                prefilled = true
                if (goalText.isEmpty()) text = src.goalCategoryName.take(100)
                if (kind == null) kind = GoalKind.of(src)
                GCTracker.track("goal_new.prefill", "refine_source" to refineSource, "has_saved_kind" to (src.goalSubType != null))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // iOS try?
        }
    }

    /** 시작 성공 → 세션 id. mode: typed / ask_ai / refine_name */
    suspend fun start(initialMessage: String?, sourceGoalId: Int?, mode: String): Int? {
        if (busy) return null
        val mustDoItems = GoalCreationMustDo.withDraft(mustDo, mustDoDraft)
        GCTracker.track(
            GCEvent.startClick,
            "start_mode" to mode,
            "goal_type" to kind?.goalType?.name,
            "goal_sub_type" to kind?.goalSubType?.name,
            "goal_text_length" to (initialMessage?.let { GoalCreationForm.count(it) } ?: 0),
            "must_do_count" to mustDoItems.size,
            "has_unadded_draft" to GoalCreationForm.trim(mustDoDraft).isNotEmpty(),
            "is_refine" to (sourceGoalId != null),
            "refine_source" to refineSource,
        )
        starting = true
        try {
            val turn = GoalCreationAPI.start(
                initialMessage = initialMessage,
                sourceGoalId = sourceGoalId,
                kind = kind,
                mustDoItems = mustDoItems,
            )
            started = true
            GCTracker.track(GCEvent.startResult, "result" to "success", "session_id" to turn.sessionId, "start_mode" to mode)
            // 오늘 남은 횟수 · 이어서 만들기 카드 갱신 (웹 refreshUsage)
            Invalidation.invalidate(DataKey.usage, DataKey.goalCreation)
            return turn.sessionId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GCTracker.track(GCEvent.startResult, "result" to "fail", "error_status" to gcErrorStatus(e), "start_mode" to mode)
            Toast.error(errorMessage(e, "시작하지 못했어요. 다시 시도해 주세요."))
            return null
        } finally {
            starting = false
        }
    }
}

@Composable
fun GoalCreationStartView(sourceGoalId: Int?) {
    val vm = rememberScreenModel { GoalCreationStartViewModel() }
    GoalCreationStartContent(vm = vm, sourceGoalId = sourceGoalId, isPreview = false)
}

/** 미리보기용 */
@Composable
fun GoalCreationStartView(preview: GoalCreationStartViewModel, sourceGoalId: Int? = null) {
    GoalCreationStartContent(vm = preview, sourceGoalId = sourceGoalId, isPreview = true)
}

@Composable
private fun GoalCreationStartContent(vm: GoalCreationStartViewModel, sourceGoalId: Int?, isPreview: Boolean) {
    val nav = LocalGoalCreationNavigator.current
    val scope = rememberCoroutineScope()
    val mustDoFocus = remember { FocusRequester() }

    if (!isPreview) {
        ReloadOn(listOf(DataKey.usage)) { vm.loadUsage() }
        // 남은 횟수와 만들던 목표를 둘 다 안 뒤 한 번
        LaunchedEffect(vm.usageLoaded, vm.hasResume) {
            if (vm.pageviewSent || !vm.usageLoaded || vm.hasResume == null) return@LaunchedEffect
            vm.pageviewSent = true
            GCTracker.track(
                GCEvent.newPageview,
                "is_refine" to (sourceGoalId != null),
                "refine_source" to vm.refineSource,
                "has_resume" to vm.hasResume,
                "quota_state" to vm.quotaState,
                "remaining_sessions" to vm.sessionLeft?.remaining,
            )
            if (vm.outOfQuota) GCTracker.track(GCEvent.quotaBlockView, "quota_state" to vm.quotaState)
        }
        ReloadOn(listOf(DataKey.goals), id = sourceGoalId ?: 0) { vm.loadSource(sourceGoalId) }
    }

    fun submit() {
        val goal = vm.goalText
        if (goal.isEmpty() || !vm.kindReady || vm.busy || vm.outOfQuota) return
        scope.launch {
            val id = vm.start(initialMessage = goal, sourceGoalId = sourceGoalId, mode = "typed")
            if (id != null) nav.go(GoalCreationRoute.chat(id))
        }
    }

    /** 첫 문장 없이 시작 (임시 목표 이름 그대로 refine_name / AI 가 먼저 물어보기 ask_ai) */
    fun startWithoutText(mode: String) {
        scope.launch {
            val id = vm.start(initialMessage = null, sourceGoalId = sourceGoalId, mode = mode)
            if (id != null) nav.go(GoalCreationRoute.chat(id))
        }
    }

    fun trackDisabled(needsText: Boolean, mode: String) {
        val reason = vm.disabledReason(needsText) ?: return
        GCTracker.track(GCEvent.startDisabledClick, "disabled_reason" to reason, "start_mode" to mode)
    }

    Column(Modifier.fillMaxSize().background(NC.surface)) {
        GoalCreationHeader(onClose = {
            GCTracker.track(GCEvent.newClose, "has_input" to vm.hasInput)
            nav.close()
        })
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 40.dp),
        ) {
            StartIntro()
            if (sourceGoalId == null) StartRecordGoalLink(Modifier.padding(top = 24.dp)) {
                GCTracker.track(GCEvent.newRecordMode, "has_input" to vm.hasInput)
            }
            if (sourceGoalId != null) StartSourceNotice(
                vm, Modifier.padding(top = 24.dp),
                onDisabledTap = { trackDisabled(needsText = false, mode = "refine_name") },
            ) { startWithoutText("refine_name") }

            // 1 · 카테고리
            Column(Modifier.padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StartStepTitle(1, "카테고리")
                GoalKindPicker(
                    value = vm.kind, onValueChange = { vm.kind = it }, disabled = vm.busy,
                    onTypePick = { t -> GCTracker.track(GCEvent.kindTypeSelect, "goal_type" to t.name) },
                    onSubPick = { t, s -> GCTracker.track(GCEvent.kindSubSelect, "goal_type" to t.name, "goal_sub_type" to s.name) },
                    types = GoalKind.aiTypes,
                )
            }

            // 2 · 목표 문장
            Column(
                Modifier.padding(top = 32.dp).alpha(if (vm.kindReady) 1f else 0.4f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StartStepTitle(2, "이루고 싶은 목표를 작성해 주세요")
                NTextField(
                    value = vm.text,
                    onValueChange = { vm.text = GoalCreationForm.prefix(it, 100) },   // 웹 maxLength 100
                    placeholder = vm.placeholder,
                    minHeight = 48.dp,
                    enabled = !vm.busy && vm.kindReady,
                    imeAction = ImeAction.Next,
                    // 완료(엔터)는 바로 시작하지 않고 다음 질문(꼭 해야 할 일)으로 넘어가요
                    onSubmit = {
                        try {
                            mustDoFocus.requestFocus()
                        } catch (e: IllegalStateException) {
                        }
                    },
                )
                if (vm.examples.isNotEmpty()) {
                    FlowLayout(spacing = 8.dp) {
                        vm.examples.forEachIndexed { i, goal ->
                            NChip(goal, size = ChipSize.sm) {
                                if (!vm.busy && vm.kindReady) {
                                    GCTracker.track(GCEvent.exampleClick, "goal_sub_type" to vm.kind?.goalSubType?.name, "example_index" to i)
                                    vm.text = goal
                                }
                            }
                        }
                    }
                }
            }

            // 3 · 꼭 할 일
            Column(Modifier.padding(top = 32.dp).alpha(if (vm.kindReady) 1f else 0.4f)) {
                Text(mustDoTitle(vm.goalText, NC.brand, NC.ink3), style = pt(14, PTWeight.semibold), color = NC.ink)
                Text(
                    "하나면 적기만 하면 되고, 여러 개면 ‘+ 추가’로 더 적어 주세요. 고른 일은 계획에 꼭 넣어 드려요.",
                    style = pt(13),
                    color = NC.ink3,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                GoalCreationMustDoPicker(
                    value = vm.mustDo,
                    onValueChange = { vm.mustDo = it },
                    draft = vm.mustDoDraft,
                    onDraftChange = { vm.mustDoDraft = it },
                    suggestions = vm.suggestions,
                    disabled = vm.busy || !vm.kindReady,
                    focus = mustDoFocus,
                    surface = "goal_new",
                )
            }

            // 시작 버튼
            val disabled = vm.busy || !vm.kindReady || vm.goalText.isEmpty() || vm.outOfQuota
            Column(
                Modifier.padding(top = 32.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NButton(
                    "AI와 목표 설계 시작하기",
                    variant = NButtonVariant.ai,
                    size = NButtonSize.lg,
                    icon = "sparkles",
                    loading = vm.starting,
                    fullWidth = true,
                    enabled = !disabled,
                    modifier = Modifier.disabledTap(disabled) { trackDisabled(needsText = true, mode = "typed") },
                ) { submit() }
                UsageLine(items = listOf(vm.usage?.tightest("GOAL_SESSION") ?: vm.sessionLeft, vm.draftLeft))
                if (vm.outOfQuota) {
                    Text(
                        if (vm.outOfMonth)
                            "이번 달 AI 목표 만들기를 모두 썼어요. 다음 달 1일 0시에 다시 채워져요 — 지금은 직접 만들기를 쓸 수 있어요. Pro는 주 10개 · 월 20개까지 만들 수 있어요."
                        else if (vm.outOfWeek)
                            "이번 주 AI 목표 만들기를 모두 썼어요. 월요일 0시에 다시 채워져요 — 지금은 직접 만들기를 쓸 수 있어요. Pro는 주 10개 · 월 20개까지 만들 수 있어요."
                        else
                            "오늘 AI 목표 만들기 횟수를 모두 썼어요. 내일 0시에 다시 채워져요 — 지금은 직접 만들기를 쓸 수 있어요. Pro는 주 10개 · 월 20개까지 만들 수 있어요.",
                        style = pt(13, PTWeight.semibold),
                        color = NC.danger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // AI 가 먼저 물어보기
            val askDisabled = vm.busy || !vm.kindReady || vm.outOfQuota
            Row(
                Modifier
                    .padding(top = 24.dp)
                    .alpha(if (askDisabled) 0.4f else 1f)
                    .disabledTap(askDisabled) { trackDisabled(needsText = false, mode = "ask_ai") }
                    .clickable(enabled = !askDisabled, role = Role.Button) { startWithoutText("ask_ai") },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SfImage("bubble.left", size = 14.dp, tint = NC.ink3)
                Text("아직 정하지 못했어요. AI가 먼저 물어봐 주세요", style = pt(14, PTWeight.medium), color = NC.ink3)
            }

            GoalCreationResumeCard(
                onOpen = { id -> nav.go(GoalCreationRoute.resume(id)) },
                onLoaded = { has -> vm.hasResume = has },
                modifier = Modifier.padding(top = 40.dp),
            )
        }
    }
}

// MARK: 머리말

@Composable
private fun StartIntro() {
    Column {
        NBadge("AI 목표 설계", tone = BadgeTone.mint, icon = "sparkles")
        Text(
            "어떤 목표를 이루고 싶으세요?",
            style = NType.h2,
            color = NC.ink,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "카테고리를 고르면 AI가 그 분야에 맞는 루틴 위주로, 꼭 필요한 것만 담아 계획을 설계해 드려요.",
            style = pt(15).copy(lineHeight = 22.sp),
            color = NC.ink3,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** 회사 업무처럼 수치·마감이 없는 일은 AI 설계가 오히려 뜬구름이 돼요 → 업무형으로 안내 */
@Composable
private fun StartRecordGoalLink(modifier: Modifier = Modifier, onTrack: () -> Unit = {}) {
    val shape = RoundedCornerShape(Radius.xxl)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NC.surface, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .clickable(role = Role.Button) {
                onTrack()
                AppRouter.closeGoalCreation()
                AppRouter.openGoalAdd(mode = GoalAddMode.record)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).background(NC.actionSecondaryBg, RoundedCornerShape(Radius.xl)),
            contentAlignment = Alignment.Center,
        ) { SfImage("book.closed", size = 16.dp, tint = NC.textBrand) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("회사 업무 · 취업 준비 · 동아리 활동인가요?", style = pt(14, PTWeight.semibold), color = NC.ink)
            Text(
                "AI 설계 없이 바로 만들고, 완료한 Task로 업무 일지를 쌓는 업무형 목표가 더 잘 맞아요.",
                style = pt(13),
                color = NC.ink3,
            )
        }
        SfImage("arrow.right", size = 14.dp, tint = NC.ink3)
    }
}

@Composable
private fun StartSourceNotice(
    vm: GoalCreationStartViewModel,
    modifier: Modifier = Modifier,
    onDisabledTap: () -> Unit = {},
    onStart: () -> Unit,
) {
    val name = (vm.source?.emoji?.let { "$it " } ?: "") + (vm.source?.goalCategoryName ?: "")
    val disabled = vm.busy || !vm.kindReady
    Column(
        modifier
            .fillMaxWidth()
            .background(NC.warningSoft, RoundedCornerShape(Radius.xxl))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            buildAnnotatedString {
                if (vm.isLegacySource) {
                    append("예전에 만든 목표 ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name) }
                    append("을(를) AI와 다시 설계해요. 확정하면 이 목표에 수치 목표 · 루틴이 더해지고, 지금까지의 Task · 루틴 · 기록은 그대로 남아요.")
                } else {
                    append("임시 목표 ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name) }
                    append("을(를) 구체화해요. 목표가 확정되면 거기에 있던 Task와 기록이 새 목표로 옮겨져요.")
                }
            },
            style = pt(14),
            color = NC.warningInkStrong,
        )
        // 이름 · 카테고리는 아래에 미리 채워 뒀어요 — 확인하고 시작만 누르면 돼요 (고쳐도 돼요)
        Text("이름과 카테고리를 미리 채워 뒀어요. 확인하고 시작만 누르세요.", style = pt(13), color = NC.warningInkStrong.copy(alpha = 0.8f))
    }
}

// MARK: 1 · 2 · 3

@Composable
private fun StartStepTitle(n: Int, title: String) {
    val brand = NC.brand
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = brand)) { append("$n  ") }
            append(title)
        },
        style = pt(14, PTWeight.semibold),
        color = NC.ink,
    )
}

/** "3  “목표” 목표를 이루기 위해 꼭 해야 할 일들이 있나요? (선택)" */
private fun mustDoTitle(goal: String, brand: androidx.compose.ui.graphics.Color, ink3: androidx.compose.ui.graphics.Color) =
    buildAnnotatedString {
        withStyle(SpanStyle(color = brand)) { append("3  ") }
        if (goal.isEmpty()) {
            append("이 목표를 이루기 위해 꼭 해야 할 일들이 있나요?")
        } else {
            val short = if (GoalCreationForm.count(goal) > 24) GoalCreationForm.prefix(goal, 24) + "…" else goal
            withStyle(SpanStyle(color = brand)) { append("“$short”") }
            append(" 목표를 이루기 위해 꼭 해야 할 일들이 있나요?")
        }
        withStyle(SpanStyle(color = ink3, fontWeight = FontWeight.Normal)) { append(" (선택)") }
    }

@Preview(showBackground = true, heightDp = 1500)
@Composable
private fun GoalCreationStartViewPreview() {
    NaleulTheme(dark = false) {
        val vm = remember {
            GoalCreationStartViewModel().apply {
                kind = GoalKindValue(goalType = GoalType.HEALTH, goalSubType = GoalSubType.DIET, goalKindLabel = null)
                text = "3개월에 8kg 빼기"
                mustDo = listOf("헬스")
                usage = AiUsage(
                    pro = false, resetsAt = "2026-11-19T00:00:00", items = listOf(
                        UsageItem(key = "GOAL_SESSION", label = "AI 목표 만들기", description = "", used = 1, limit = 3, remaining = 2),
                        UsageItem(key = "GOAL_DRAFT", label = "계획 초안", description = "", used = 1, limit = 5, remaining = 4),
                    ), session = null,
                )
            }
        }
        GoalCreationStartView(preview = vm)
    }
}

@Preview(showBackground = true, heightDp = 1500)
@Composable
private fun GoalCreationStartViewSourcePreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalCreationStartViewModel() }
        GoalCreationStartView(preview = vm, sourceGoalId = 3)
    }
}
