// feature/goal/GoalOutcomePrompt.kt
//
// 결과 확인 모달 — "OO 목표 기한이 지났어요. 목표는 만족했나요?"
// iOS Features/Goal/GoalOutcomePrompt.swift · 웹 features/goal/ui/GoalOutcomePromptModal.tsx
//
// 왜 필요한가: 기한이 지나면 서버가 수치(80% 이상) · 실천률로 자동 판정해요. 사용자가 직접 고른 결과와 얼마나 맞는지
// (판정 일치율) 알아야 대시보드의 목표 달성률을 믿을 수 있는데, 목표 상세에 들어가야만 고를 수 있으면 답이 거의 안 모여요.
//  - 고르면 자동 판정을 덮어써요 (PATCH /goal-categories/{id}/outcome)
//  - "나중에"는 자동 판정을 그대로 두고 다시 묻지 않아요 (목표 상세에서는 언제든 바꿀 수 있어요)
//  - 여러 개면 오래된 것부터 하나씩. MainTabScreen 이 앱을 열 때 · 앞으로 올 때 불러요

package com.naleul.naleul.feature.goal

import com.naleul.naleul.core.analytics.*
import com.naleul.naleul.core.network.*
import com.naleul.naleul.core.state.*
import com.naleul.naleul.designsystem.*
import com.naleul.naleul.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** GET /v1/goal-outcomes/prompts 한 건 */
@Serializable
data class OutcomePrompt(
    val goalCategoryId: Int,
    val goalName: String,
    val emoji: String? = null,
    val colorCode: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val autoResult: OutcomeResult,
    /** METRIC · ROUTINE_RATE · TASK_RATE · NO_RECORD */
    val autoReason: String? = null,
    /** 예) "마지막 기록이 목표 수치의 86%예요. 80% 이상이라 자동으로 달성 처리했어요." */
    val autoMessage: String? = null,
    val metricPercent: Double? = null,
    val routineRate: Double? = null,
    val taskRate: Double? = null,
    val aiGenerated: Boolean? = null,
)

object OutcomePromptAPI {
    suspend fun list(): List<OutcomePrompt> = API.get<List<OutcomePrompt>>("/v1/goal-outcomes/prompts") ?: emptyList()

    /** "나중에" — 자동 판정 그대로, 다시 묻지 않아요 */
    suspend fun dismiss(goalId: Int) = API.send("POST", "/v1/goal-outcomes/prompts/$goalId/dismiss")
}

/** 물어볼 목표 큐 (앱 전체에서 하나) */
object OutcomePromptCenter {
    /** 물어볼 목표 (오래된 순) */
    var queue by mutableStateOf<List<OutcomePrompt>>(emptyList())
        private set
    /** 지금 띄운 목표 */
    var current by mutableStateOf<OutcomePrompt?>(null)
        private set
    private var loading = false
    /** 이번 실행에서 답했거나 미룬 목표 — "나중에" 요청이 끝나기 전에 다시 불러와도 또 띄우지 않게 */
    private val handled = mutableSetOf<Int>()
    /** 시트가 사라져도 "나중에" 요청은 끝까지 보내요 */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 앱을 열 때 · 앞으로 올 때. 실패해도 조용히 넘어가요 (부가 기능) */
    suspend fun load() {
        if (loading || current != null || !UserStore.isLoggedIn || UserStore.isGuest) return
        loading = true
        try {
            val list = try {
                OutcomePromptAPI.list()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return
            }
            queue = list.filterNot { it.goalCategoryId in handled }
            showNext()
        } finally {
            loading = false
        }
    }

    /** 답했거나 미룬 목표를 빼요 (시트도 닫혀요) */
    fun finish(goalId: Int) {
        handled += goalId
        queue = queue.filterNot { it.goalCategoryId == goalId }
        current = null
    }

    fun showNext() {
        if (current == null) current = queue.firstOrNull()
    }
}

/** 이벤트 공통 속성 (노션 Tracking Plan 「목표 결과 확인 모달」) */
private fun OutcomePrompt.baseProps(): EventProps = mapOf(
    "goal_category_id" to goalCategoryId,
    "auto_result" to autoResult.rawValue,
    "auto_reason" to autoReason,
    "is_ai_generated" to (aiGenerated ?: false),
)

private fun trackPrompt(event: String, props: EventProps) =
    AnalyticsService.track(event, *props.toList().toTypedArray())

/** 로그인 후 공통 틀(MainTabScreen)에 한 번 — 앱을 열 때 · 앞으로 올 때 결과 확인 모달을 띄워요 */
@Composable
fun GoalOutcomePromptHost() {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { OutcomePromptCenter.load() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch { OutcomePromptCenter.load() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    OutcomePromptCenter.current?.let { p ->
        key(p.goalCategoryId) {
            GoalOutcomePromptSheet(prompt = p, remaining = OutcomePromptCenter.queue.size) {
                // 다음 목표는 시트가 내려간 뒤에
                scope.launch {
                    delay(350)
                    OutcomePromptCenter.showNext()
                }
            }
        }
    }
}

@Composable
private fun GoalOutcomePromptSheet(prompt: OutcomePrompt, remaining: Int, onClosed: () -> Unit) {
    var saving by remember { mutableStateOf(false) }
    // "answered" 면 결과를 골라 닫은 것. 그 밖의 닫기는 모두 "나중에"
    var outcome by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val auto = GoalOutcomeOption.of(prompt.autoResult)

    LaunchedEffect(Unit) {
        trackPrompt("goal_outcome_modal.modal_view", prompt.baseProps() + mapOf(
            "metric_percent" to prompt.metricPercent,
            "routine_rate" to prompt.routineRate,
            "task_rate" to prompt.taskRate,
            "remaining_count" to remaining,
        ))
    }

    /** 시트가 닫힌 뒤(onDismiss) — 답했으면 큐에서 빼고, 아니면 "나중에" (X · 쓸어내려 닫기 · 뒤로 가기 포함) */
    fun closed(method: String) {
        val id = prompt.goalCategoryId
        OutcomePromptCenter.finish(id)
        onClosed()
        if (outcome == "answered") return
        val props = prompt.baseProps() + mapOf("close_method" to method)
        trackPrompt("goal_outcome_modal.later_click", props)
        // 시트가 사라져도 요청은 끝까지 보내요 (화면 범위가 아니라 앱 범위 scope)
        OutcomePromptCenter.scope.launch {
            val ok = runCatching { OutcomePromptAPI.dismiss(id) }.isSuccess
            trackPrompt("goal_outcome_modal.later_result", props + GD.result(ok))
        }
    }

    fun choose(r: OutcomeResult, close: SheetClose) {
        if (saving) return
        val id = prompt.goalCategoryId
        val props = prompt.baseProps() + mapOf("outcome_result" to r.rawValue, "is_match" to (r == prompt.autoResult))
        trackPrompt("goal_outcome_modal.outcome_select", props)
        saving = true
        scope.launch {
            val res = Mutation.run(
                invalidate = listOf(DataKey.goals),
                successMessage = "결과를 남겼어요.",
                errorFallback = "저장하지 못했어요. 다시 시도해 주세요.",
            ) {
                Pair(GoalEditAPI.updateOutcome(goalId = id, result = r), true)
            }
            saving = false
            trackPrompt("goal_outcome_modal.answer_result", props + GD.result())
            if (res != null) {
                outcome = "answered"
                close()
            }
        }
    }

    SheetScaffold(
        onDismiss = {
            val hint = AnalyticsService.sheetCloseHint
            AnalyticsService.sheetCloseHint = null
            closed(hint ?: "swipe_down")
        },
        title = "${prompt.emoji?.let { "$it " } ?: ""}${prompt.goalName} 목표 기한이 지났어요",
        description = "목표는 만족했나요? 직접 골라 주면 다음 목표가 더 잘 맞춰져요.",
        dismissible = !saving,
        footer = { close ->
            NButton("나중에 (자동 판정 그대로 두기)", variant = NButtonVariant.secondary, enabled = !saving) {
                AnalyticsService.sheetCloseHint = "later_button"
                close()
            }
        },
    ) { close ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(NC.subtle, RoundedCornerShape(Radius.xxl))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("자동 판정 · ${auto.emoji} ${auto.label}", style = pt(13, PTWeight.semibold), color = NC.ink2)
                prompt.autoMessage?.let { Text(it, style = pt(14), color = NC.ink) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("내가 느끼기엔", style = pt(14, PTWeight.semibold), color = NC.ink2)
                GoalOutcomePicker(value = null, disabled = saving) { choose(it, close) }
            }
            if (remaining > 1) {
                Text("물어볼 목표가 ${remaining - 1}개 더 있어요.", style = pt(12), color = NC.ink3)
            }
        }
    }
}
