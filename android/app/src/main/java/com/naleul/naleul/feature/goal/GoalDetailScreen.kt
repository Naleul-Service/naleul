// feature/goal/GoalDetailScreen.kt
//
// iOS Features/Goal/GoalDetailScreen.swift
// 웹 app/(main)/goal/[goalId]/page.tsx + features/goal/ui/GoalDetailView.tsx — 목표 상세.
//
// 웹은 각 영역을 "그 자리에서" 고쳤어요 (EditingContext: 한 번에 입력창 하나).
// 앱은 입력창을 시트로 띄우고, 어떤 시트를 띄울지(sheet)·어떤 삭제를 확인할지(confirm)를
// 이 화면의 GoalDetailViewModel 하나가 들고 있어요. 그래서 시트는 늘 하나만 떠요.
//
// 데이터: 목표 · 관련 Task · 진행("지금 어디쯤?") · 루틴 히트맵을 한 번에 불러와서 섹션들에 나눠 줘요
// (웹은 섹션마다 useQuery 를 따로 썼지만 캐시를 공유했어요 — 여기선 ViewModel 이 그 캐시 역할).
// DataKey.goals 가 낡으면(Invalidation) 전부 다시 불러와요.
//
// iOS @Environment(GoalDetailViewModel.self) → Android LocalGoalDetailViewModel (CompositionLocal)
//   하위 섹션들은 val vm = LocalGoalDetailViewModel.current 로 같은 ViewModel 을 읽어요.
//   (iOS 처럼 "화면이 넣어 준 공용 객체"를 꺼내 쓰는 방식이에요. 매번 파라미터로 넘기지 않아도 돼요)

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.naleul.naleul.feature.goal

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

import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// MARK: - 하위 섹션이 같은 ViewModel 을 읽는 통로 (iOS .environment(vm))

/**
 * 목표 상세 화면이 넣어 줘요. 화면 밖(미리보기 등)에서는 null 이라
 * 미리보기에서는 CompositionLocalProvider(LocalGoalDetailViewModel provides GoalDetailViewModel.preview()) { … } 로 감싸요.
 */
val LocalGoalDetailViewModel = staticCompositionLocalOf<GoalDetailViewModel?> { null }

// MARK: - 어떤 시트를 띄울지

/** iOS enum GoalDetailSheet (연관값 있는 case → sealed class, case 이름 그대로) */
sealed class GoalDetailSheet {
    /** 목표 수정 (이름·기간·색·카테고리·수치) */
    object editGoal : GoalDetailSheet()

    /** 세부 목표 추가(null) · 수정 */
    data class subGoal(val sub: SubGoalInfo?) : GoalDetailSheet()

    /** 루틴 추가(null) · 수정 */
    data class routine(val routine: RoutineSummary?, val subGoalId: Int?) : GoalDetailSheet()

    /** 루틴을 다른 날에도 했어요 (반복 요일이 아닌 날 하루 추가) */
    data class routineExtraDay(val routine: RoutineSummary) : GoalDetailSheet()

    /** 마일스톤 추가(null) · 수정 */
    data class milestone(val milestone: MilestoneInfo?, val isLast: Boolean) : GoalDetailSheet()

    /** Task 추가(null) · 수정 */
    data class task(val task: TimeBlockTask?) : GoalDetailSheet()

    /** 수치 목표만 고치기 (이름 · 단위 · 처음/목표 값 · 수치 마감일) — 목표 수정과 따로 (2026-10) */
    data class editMetric(val entryPoint: String) : GoalDetailSheet()

    /** 지난 수치 기록 고치기 */
    data class metricLog(val date: String, val value: Double) : GoalDetailSheet()

    /** 목표 마무리 */
    object wrapUp : GoalDetailSheet()

    /** 오늘 기록 (수치 · 기분 · 한 줄 회고) */
    object checkIn : GoalDetailSheet()

    /** 업무형: 오늘 한 일 한 줄 기록 */
    object workLog : GoalDetailSheet()

    /** 업무형: 적기 (할 일 · 한 일 · 회고) */
    object workCompose : GoalDetailSheet()

    /** 말로 고치기 (문장 입력) */
    object aiEdit : GoalDetailSheet()

    /** 말로 고치기 미리보기 */
    data class aiPreview(val preview: GoalEditPreview) : GoalDetailSheet()

    /** 목표 형태 정하기 (AI 없이 만든 목표형 목표, 2026-10) — trigger: auto · button */
    data class legacySetup(val trigger: String) : GoalDetailSheet()

    /** 시트 구분용 이름 (같은 시트를 다시 그릴지 새로 띄울지 판단) */
    val id: String
        get() = when (this) {
            is editGoal -> "goal"
            is subGoal -> "sub:${this.sub?.generalCategoryId?.toString() ?: "new"}"
            is routine -> "routine:${this.routine?.routineId?.toString() ?: "new"}"
            is routineExtraDay -> "routine-extra:${this.routine.routineId}"
            is milestone -> "milestone:${this.milestone?.milestoneId?.toString() ?: "new"}"
            is task -> "task:${this.task?.taskId?.toString() ?: "new"}"
            is metricLog -> "metriclog:${this.date}"
            is editMetric -> "metric"
            is wrapUp -> "wrapup"
            is checkIn -> "checkin"
            is workLog -> "worklog"
            is workCompose -> "workcompose"
            is aiEdit -> "aiedit"
            is aiPreview -> "ai"
            is legacySetup -> "legacy"
        }

    companion object {}
}

/** 어떤 삭제를 확인할지 (웹 InlineConfirm → 앱 확인창) */
sealed class GoalDetailConfirm {
    data class deleteSubGoal(val sub: SubGoalInfo) : GoalDetailConfirm()
    data class deleteRoutine(val routine: RoutineSummary) : GoalDetailConfirm()
    data class deleteMilestone(val milestone: MilestoneInfo) : GoalDetailConfirm()
    data class deleteTask(val task: TimeBlockTask) : GoalDetailConfirm()
    data class deleteMetricLog(val date: String, val value: Double, val unit: String?) : GoalDetailConfirm()

    val title: String
        get() = when (this) {
            is deleteSubGoal -> "'${sub.generalCategoryName}'을 삭제할까요?"
            is deleteRoutine -> "'${routine.routineName}' 루틴을 삭제할까요?"
            is deleteMilestone -> "'${milestone.title}'을 삭제할까요?"
            is deleteTask -> "'${task.taskName}'을 삭제할까요?"
            is deleteMetricLog -> "${GoalDetailLogic.md(date)} 기록(${GoalDetailLogic.withUnit(value, unit)})을 지울까요?"
        }

    val message: String?
        get() = when (this) {
            is deleteSubGoal ->
                if (sub.routines.isEmpty()) "이 세부 목표에 연결된 일회성 Task는 남아요."
                else "루틴 ${sub.routines.size}개와 그 루틴의 Task도 함께 삭제돼요."
            is deleteRoutine -> "이 루틴으로 만들어진 Task도 함께 삭제돼요."
            is deleteMilestone -> "이 마일스톤에 연결된 Task는 남고 연결만 풀려요."
            is deleteTask -> if (task.sourceType == TaskSourceType.ROUTINE) "이 날짜의 루틴 Task 하나만 지워져요. 루틴은 그대로예요." else null
            is deleteMetricLog -> "그래프와 현재 값이 바로 바뀌어요."
        }

    companion object {}
}

// MARK: - ViewModel

class GoalDetailViewModel(val goalId: Int) : ScreenModel() {
    var phase by mutableStateOf<LoadPhase<GoalCategory>>(LoadPhase.Loading)
    var notFound by mutableStateOf(false)
    var tasks by mutableStateOf<GoalTasksResponse?>(null)
    var tasksFailed by mutableStateOf(false)
    var progress by mutableStateOf<GoalProgress?>(null)

    /** 진행 데이터를 한 번이라도 불러왔는지 (불러오는 중 스피너용) */
    var progressLoaded by mutableStateOf(false)
    var heatmap by mutableStateOf<List<RoutineHeatmap>?>(null)
    var colors by mutableStateOf<List<UserColor>>(emptyList())

    /** 지금 띄운 시트 (하나만) */
    var sheet by mutableStateOf<GoalDetailSheet?>(null)

    /** "모두 보기"(오늘 기록 · 업무형 회고)를 누르면 true → 탭이 "회고"로 바뀌고 다시 false */
    var reflectionsTabRequested by mutableStateOf(false)

    /** 지금 확인 중인 삭제 (확인창이 떠 있는 동안만 값이 있어요) */
    var confirm by mutableStateOf<GoalDetailConfirm?>(null)

    /** 마지막으로 물어본 삭제 — 확인창이 닫힌 뒤에도 [삭제] 동작에서 읽을 수 있게 */
    var confirmTarget by mutableStateOf<GoalDetailConfirm?>(null)

    /** 완료 체크 중인 Task */
    var togglingTaskId by mutableStateOf<Int?>(null)

    /** "말로 고치기" 입력 (오늘 기록 카드의 "최근 기록 보고 계획 조정받기"가 채워요 — 웹 AI_EDIT_PREFILL_EVENT) */
    var aiEditText by mutableStateOf("")

    /** 값이 바뀌면 화면이 말로 고치기 시트를 열어요 */
    var aiFocusTick by mutableIntStateOf(0)

    /** 미리보기용 (네트워크 안 씀) */
    var isPreview by mutableStateOf(false)

    val goal: GoalCategory? get() = phase.value

    // MARK: 앰플리튜드

    private var pageviewSent = false
    private val seen = mutableSetOf<String>()

    /** 목표를 못 불러왔을 때의 HTTP 상태 (실패 화면 버튼에 붙여요) */
    var loadErrorStatus: Int? = null

    /** AI 목표 확정 직후로 들어왔는지 (pageview 의 just_created) */
    var justCreated = false

    /** goal_category_id 만 (대부분 이벤트의 기본 속성) */
    val gid: EventProps get() = mapOf("goal_category_id" to goalId)

    /** 할 일 목록이 쓰인 화면 (goal_detail · habit) */
    val taskSurface: String get() = if (goal?.isHabitGoal == true) "habit" else "goal_detail"

    /** 화면당 처음 한 번만 true */
    fun once(key: String): Boolean = if (isPreview) false else seen.add(key)

    fun track(event: String, props: EventProps = emptyMap()) {
        if (isPreview) return
        GD.track(event, gid, props)
    }

    /** 그래프 만져보기 — 그래프마다 화면당 1회 */
    fun trackChartInteract(kind: String, metricId: Int? = null) {
        if (!once("chart:$kind:${metricId ?: 0}")) return
        track("goal_detail.chart_interact", buildMap {
            put("chart_kind", kind)
            if (metricId != null) put("metric_id", metricId)
        })
    }

    /** 목표 · 할 일을 처음 다 불러왔을 때 한 번 (습관 그릇은 habit.pageview 로 대신) */
    private fun trackPageviewIfNeeded() {
        val g = goal ?: return
        if (pageviewSent || isPreview) return
        pageviewSent = true
        if (g.isHabitGoal) return
        // 스펙 13.5: 목표형 첫 탭은 "progress"
        val tab = if (g.isRecordGoal) "board" else "progress"
        GD.track("goal_detail.pageview", g.analyticsBase, mapOf(
            "goal_status" to g.goalCategoryStatus.name, "is_temporary" to g.isTemporary, "just_created" to justCreated,
            "tab" to tab, "has_metric" to g.analyticsHasMetric, "routine_count" to g.analyticsRoutineCount, "d_day" to g.analyticsDDay,
        ))
    }

    /** 실패해도 화면 전체가 멈추지 않게 결과로 감싸요. 취소(CancellationException)는 그대로 던져요 */
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun load() {
        if (isPreview) return
        try {
            val g = GoalAPI.goal(goalId)
            if (g != null) {
                phase = LoadPhase.Loaded(g)
                notFound = false
            } else {
                notFound = true
                phase = LoadPhase.Failed("목표를 찾을 수 없어요")
                loadErrorStatus = 404
                track("goal_detail.load_fail", mapOf("error_status" to 404))
                return
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if ((e as? ApiError)?.httpStatus == 404) notFound = true
            // 이미 보여주던 목표가 있으면 그대로 둬요 (웹 TanStack Query 도 캐시를 계속 보여줘요)
            if (phase.value == null || notFound) {
                phase = LoadPhase.Failed(errorMessage(e))
                loadErrorStatus = (e as? ApiError)?.httpStatus ?: 0
                track("goal_detail.load_fail", mapOf("error_status" to loadErrorStatus))
            }
            return
        }

        // 나머지는 같이(동시에) 불러와요 (iOS async let)
        val id = goalId
        coroutineScope {
            val tasksReq = async { attempt { GoalAPI.tasks(goalId = id) } }
            val progressReq = async { attempt { GoalAPI.progress(goalId = id) } }
            val heatmapReq = async { attempt { GoalAPI.routineHeatmap(goalId = id) } }

            tasksReq.await()
                .onSuccess {
                    tasks = it
                    tasksFailed = false
                }
                .onFailure {
                    if (tasks == null) {
                        tasksFailed = true
                        track("goal_task_list.load_fail", mapOf("surface" to taskSurface, "error_status" to ((it as? ApiError)?.httpStatus ?: 0)))
                    }
                }
            trackPageviewIfNeeded()

            // 진행 데이터가 없어도 나머지 화면은 보여줘요
            progressReq.await().onSuccess { progress = it }
            progressLoaded = true

            heatmapReq.await()
                .onSuccess { heatmap = it }
                .onFailure { if (heatmap == null) heatmap = emptyList() }
        }
        if (colors.isEmpty()) {
            colors = attempt { ColorAPI.userColors() }.getOrNull() ?: emptyList()
        }
    }

    suspend fun reloadTasks() {
        try {
            tasks = GoalAPI.tasks(goalId = goalId)
            tasksFailed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            tasksFailed = true
            track("goal_task_list.load_fail", mapOf("surface" to taskSurface, "error_status" to ((e as? ApiError)?.httpStatus ?: 0)))
        }
    }

    /** 목표 상세에서 완료 체크 / 취소 (웹 useToggleGoalTask) */
    suspend fun toggleTask(t: TimeBlockTask) {
        if (togglingTaskId != null) return
        togglingTaskId = t.taskId
        val completing = t.taskStatus != TaskStatus.COMPLETED
        track("goal_task_list.toggle_complete", mapOf(
            "surface" to taskSurface, "task_id" to t.taskId, "check_action" to if (completing) "complete" else "uncomplete",
            "source_type" to t.sourceType.name, "is_missed" to t.missed,
            "days_from_today" to GD.daysFromToday(GoalDetailLogic.dayOf(t)),
        ))
        val msg = if (t.taskStatus == TaskStatus.COMPLETED) "완료를 취소했어요." else "완료했어요."
        Mutation.run(invalidate = listOf(DataKey.goals, DataKey.timetable, DataKey.record), successMessage = msg) {
            GoalAPI.toggleTask(taskId = t.taskId, currentStatus = t.taskStatus)
        }
        track("goal_task_list.result", GD.result() + mapOf("surface" to taskSurface, "task_action" to "toggle"))
        togglingTaskId = null
    }

    /** 삭제 확인창 띄우기 */
    fun ask(c: GoalDetailConfirm) {
        trackConfirm(c, "click")
        confirmTarget = c
        confirm = c
    }

    /** 확인창 '취소' · 바깥 누르기 · 뒤로 가기 */
    fun cancelConfirm() {
        confirmTarget?.let { trackConfirm(it, "cancel") }
    }

    /** 삭제 확인창 단계별 이벤트 (click · confirm · cancel) */
    private fun trackConfirm(c: GoalDetailConfirm, step: String) {
        when (c) {
            is GoalDetailConfirm.deleteSubGoal -> track("goal_detail.structure_delete_$step", mapOf(
                "item_kind" to "sub_goal", "routine_count" to if (step == "cancel") null else c.sub.routines.size,
            ))
            is GoalDetailConfirm.deleteMilestone -> track("goal_detail.structure_delete_$step", mapOf("item_kind" to "milestone"))
            is GoalDetailConfirm.deleteRoutine -> track("goal_detail.routine_delete_$step", mapOf("routine_id" to c.routine.routineId))
            is GoalDetailConfirm.deleteTask -> if (!isPreview) GD.track("goal_task_list.delete_$step", mapOf(
                "surface" to taskSurface, "task_id" to c.task.taskId, "source_type" to c.task.sourceType.name,
            ))
            is GoalDetailConfirm.deleteMetricLog -> track("goal_detail.metric_log_delete_$step",
                if (step == "cancel") emptyMap() else mapOf("days_ago" to GD.daysAgo(c.date)))
        }
    }

    /** 삭제 요청 결과 */
    private fun trackDeleteResult(c: GoalDetailConfirm) {
        val r = GD.result()
        when (c) {
            is GoalDetailConfirm.deleteSubGoal ->
                track("goal_detail.structure_result", r + mapOf("item_kind" to "sub_goal", "structure_action" to "delete"))
            is GoalDetailConfirm.deleteMilestone ->
                track("goal_detail.structure_result", r + mapOf("item_kind" to "milestone", "structure_action" to "delete"))
            is GoalDetailConfirm.deleteRoutine -> track("goal_detail.routine_result", r + mapOf("routine_action" to "delete"))
            is GoalDetailConfirm.deleteTask ->
                track("goal_task_list.result", r + mapOf("surface" to taskSurface, "task_action" to "delete"))
            is GoalDetailConfirm.deleteMetricLog -> track("goal_detail.metric_result", r + mapOf("metric_action" to "delete"))
        }
    }

    /** 확인창에서 [삭제]를 눌렀을 때 */
    suspend fun perform(c: GoalDetailConfirm) {
        trackConfirm(c, "confirm")
        try {
            performDelete(c)
        } finally {
            trackDeleteResult(c)
        }
    }

    private suspend fun performDelete(c: GoalDetailConfirm) {
        val edit = listOf(DataKey.goals, DataKey.timetable)
        when (c) {
            is GoalDetailConfirm.deleteSubGoal ->
                Mutation.run(invalidate = edit, successMessage = "세부 목표를 삭제했어요.") {
                    GoalEditAPI.deleteSubGoal(id = c.sub.generalCategoryId)
                }
            is GoalDetailConfirm.deleteRoutine ->
                Mutation.run(invalidate = edit, successMessage = "루틴을 삭제했어요.") {
                    GoalEditAPI.deleteRoutine(id = c.routine.routineId)
                }
            is GoalDetailConfirm.deleteMilestone ->
                Mutation.run(invalidate = edit, successMessage = "마일스톤을 삭제했어요.") {
                    GoalEditAPI.deleteMilestone(id = c.milestone.milestoneId)
                }
            is GoalDetailConfirm.deleteTask ->
                Mutation.run(invalidate = edit, successMessage = "Task를 삭제했어요.") {
                    GoalAPI.deleteTask(taskId = c.task.taskId)
                }
            is GoalDetailConfirm.deleteMetricLog -> {
                val r: GoalProgress? = Mutation.run(
                    invalidate = listOf(DataKey.goals),
                    successMessage = "기록을 지웠어요.",
                    errorFallback = "지우지 못했어요. 다시 시도해 주세요.",
                ) {
                    GoalEditAPI.deleteMetricLog(goalId = goalId, date = c.date)
                }
                if (r != null) progress = r
            }
        }
    }

    companion object {
        /** 미리보기용 ViewModel (샘플 데이터 · 네트워크 안 씀) */
        fun preview(
            goal: GoalCategory = GoalPreviewData.goal,
            progress: GoalProgress? = GoalPreviewData.progress,
        ): GoalDetailViewModel {
            val vm = GoalDetailViewModel(goal.goalCategoryId)
            vm.isPreview = true
            vm.phase = LoadPhase.Loaded(goal)
            vm.tasks = GoalPreviewData.tasks
            vm.progress = progress
            vm.progressLoaded = true
            vm.heatmap = GoalPreviewData.heatmap
            vm.colors = GoalPreviewData.colors
            return vm
        }
    }
}

// MARK: - 화면
//
// 모바일 배치
//  ┌ 그라데이션 머리 카드: 이름 · 상태 · 기간 · 진행 링 · 경과/남은 기간 ┐   (스크롤로 사라지면 위 제목줄에 이름)
//  ├ 탭 (위에 붙어 있어요): 오늘 · 진행 · 계획 · 회고  (업무형: 보드 · 통계 · 회고) ┤
//  │  오늘: 주간 점검 · 오늘 기록 · 할 일 · 시작한 이유                      │
//  │  진행: 지금 어디쯤?(그래프) · Task 실천률 · 루틴 실천 히트맵 · 결과       │
//  │  계획: 루틴 · 세부 구조 · 마무리                                       │
//  └ 오른쪽 아래 ✨ 말로 고치기 · ✏️ 오늘 기록 (시트)                         ┘
// 수정 · 마무리는 오른쪽 위 … 메뉴에서도 열 수 있어요.

/** 앱 골격(AppRouter)이 부르는 진입 화면 */
@Composable
fun GoalDetailScreen(goalId: Int, justCreated: Boolean = false) {
    val vm = rememberScreenModel(goalId) { GoalDetailViewModel(goalId).also { it.justCreated = justCreated } }
    GoalDetailScreenBody(vm = vm, justCreated = justCreated)
}

/** 화면 본문 (미리보기에서 샘플 ViewModel 을 넣으려고 나눠 뒀어요 — iOS init(previewVM:)) */
@Composable
private fun GoalDetailScreenBody(vm: GoalDetailViewModel, justCreated: Boolean) {
    // 웹 ?created=1 — 닫으면 주소에서 지웠어요. 여기선 이 화면 동안만 기억해요
    var showPlaceCard by remember { mutableStateOf(justCreated) }
    // 탭은 목록(LazyColumn) 밖에서 들고 있어요 — 목록 항목 안에 두면 스크롤로 사라질 때 잊혀요
    var tabOverride by remember { mutableStateOf<GoalDetailTab?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    ReloadOn(listOf(DataKey.goals, DataKey.userColors), id = vm.goalId) {
        vm.load()
        // AI 없이 만든 목표형 목표 → 처음 들어왔을 때 한 번 "목표 형태 정하기" (그 뒤로는 머리말 버튼)
        val g = vm.goal
        if (vm.sheet == null && !showPlaceCard && g != null && GoalLegacy.takeAutoOpen(g)) {
            vm.sheet = GoalDetailSheet.legacySetup("auto")
        }
    }

    // 오늘 기록 카드의 "최근 기록 보고 계획 조정받기" → 문장을 채우고 말로 고치기 시트를 열어요
    // (iOS .onChange 는 처음엔 안 불려서, 이전 값과 비교해요)
    var lastAiTick by remember { mutableIntStateOf(vm.aiFocusTick) }
    LaunchedEffect(vm.aiFocusTick) {
        if (vm.aiFocusTick != lastAiTick) {
            lastAiTick = vm.aiFocusTick
            vm.sheet = GoalDetailSheet.aiEdit
        }
    }
    // "모두 보기" → 회고 탭으로
    LaunchedEffect(vm.reflectionsTabRequested) {
        if (vm.reflectionsTabRequested) {
            tabOverride = GoalDetailTab.reflections
            vm.reflectionsTabRequested = false
        }
    }

    val goal = vm.goal
    val loadedNormal = vm.phase is LoadPhase.Loaded && goal != null && !goal.isHabitGoal
    // 스펙 12.4: 머리 카드가 스크롤로 사라지면 제목줄에 목표 이름이 서서히 · 아래 1dp 선
    // 스펙 13.3: 목록 순서 = 요약(0) → 시작한 이유(1, 목표형에 있을 때) → 탭
    val hasWhy = goal != null && !goal.isRecordGoal && (goal.motive != null || goal.aiNote != null)
    val heroIndex = 0
    val tabsIndex = if (hasWhy) 2 else 1
    // 스펙 13.4: LazyListState 를 직접 읽어서 (요약 카드가 위로 지나가면) 제목 표시
    // 줄 번호(firstVisibleItemIndex) + 화면에 보이는 칸(layoutInfo, key 로 찾기)을 같이 봐요 — 스크롤 위치값이 0 으로 돌아가도 맞게
    val showNavTitle by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val hero = info.visibleItemsInfo.firstOrNull { it.key == "hero" }
            if (hero != null) hero.offset + hero.size * 0.6f < info.viewportStartOffset
            else listState.firstVisibleItemIndex > heroIndex
        }
    }
    // 탭 바가 화면 위로 지나갔는지 → "보드 ▲" 같은 알약 버튼 (탭 칸이 안 보이고, 첫 줄이 탭보다 아래)
    val tabsScrolledOut by remember(tabsIndex) {
        derivedStateOf {
            listState.firstVisibleItemIndex > tabsIndex ||
                (listState.firstVisibleItemIndex >= tabsIndex && listState.layoutInfo.visibleItemsInfo.none { it.key == "tabs" })
        }
    }
    val navTitle = if (loadedNormal) (goal?.goalCategoryName ?: "목표") else ""
    // 업무형: 맨 위 요약 카드와 보드 탭이 같은 데이터를 써요 (스펙 12.1)
    val workModel: WorkBoardModel? = if (goal != null && goal.isRecordGoal) {
        rememberScreenModel(goal.goalCategoryId) { WorkBoardModel(goalId = goal.goalCategoryId, routineCount = goal.analyticsRoutineCount) }
    } else {
        null
    }

    CompositionLocalProvider(LocalGoalDetailViewModel provides vm) {
        PushedScreen(
            title = navTitle,
            background = NC.bgSurface,
            titleVisible = loadedNormal && showNavTitle,
            titleStyle = NType.labelLG,
            showDivider = loadedNormal && showNavTitle,
            actions = { if (goal != null) GoalDetailMoreMenu(goal = goal, vm = vm) },
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                when (val ph = vm.phase) {
                    is LoadPhase.Loading -> item {
                        LoadingView(minHeight = 400.dp, modifier = Modifier.padding(16.dp))
                    }
                    is LoadPhase.Failed -> item {
                        GoalDetailErrorCard(
                            notFound = vm.notFound,
                            message = ph.message,
                            errorStatus = vm.loadErrorStatus,
                            retry = {
                                vm.phase = LoadPhase.Loading
                                vm.load()
                            },
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    is LoadPhase.Loaded -> {
                        val g = ph.value
                        if (g.isHabitGoal) {
                            // 기본 생활 습관 (V52) — 습관 체크 · 잔디 · 일회성 할 일을 오늘 · 진행 · 할 일 탭으로 (스펙 12.5)
                            item {
                                Box(Modifier.padding(top = 8.dp, bottom = 32.dp)) {
                                    HabitGoalBody(goal = g)
                                }
                            }
                        } else {
                            val tab = tabOverride ?: goalDetailInitialTab(g)
                            val fab = goalDetailHasFab(g)
                            item(key = "hero") {
                                val wm = workModel
                                if (g.isRecordGoal && wm != null) {
                                    WorkGoalSummaryCard(
                                        goal = g, model = wm,
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                                    )
                                } else {
                                    GoalDetailHeroCard(
                                        goal = g,
                                        vm = vm,
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                                    )
                                }
                            }
                            // 스펙 13.3: 목표형 "시작한 이유"는 요약 카드 바로 아래
                            if (hasWhy) {
                                item(key = "why") {
                                    GoalNotesCard(goal = g, vm = vm, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
                                }
                            }
                            // 스펙 12.4: 탭은 위에 붙지 않고 내용과 같이 스크롤
                            item(key = "tabs") {
                                GoalDetailTabBar(
                                    selection = tab,
                                    onSelectionChange = {
                                        vm.track("goal_detail.tab_click", mapOf(
                                            "goal_mode" to g.analyticsMode, "tab" to it.analyticsName, "from_tab" to tab.analyticsName,
                                        ))
                                        tabOverride = it
                                    },
                                    items = goalDetailTabs(g),
                                )
                            }
                            item(key = "content") {
                                Crossfade(targetState = tab, animationSpec = tween(180), label = "goal-tab") { t ->
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, top = 14.dp)
                                            // 떠 있는 버튼(최대 2개)에 마지막 카드가 가리지 않게
                                            .padding(bottom = if (fab) 150.dp else 32.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        when (t) {
                                            // (예전 오늘 탭 — 이제 계획 탭 맨 위에 같이 그려요)
                                            GoalDetailTab.today, GoalDetailTab.plan -> GoalPlanTab(goal = g, vm = vm)
                                            // iOS 와 같게: 새 목표 안내 카드(배치 · AI 다듬기 · 주간 점검)는 기본 탭인 진행 맨 위
                                            GoalDetailTab.progress -> {
                                                GoalTodayTab(
                                                    goal = g,
                                                    vm = vm,
                                                    showPlaceCard = showPlaceCard,
                                                    onShowPlaceCardChange = { showPlaceCard = it },
                                                )
                                                GoalProgressTab(goal = g, vm = vm)
                                            }
                                            GoalDetailTab.board -> WorkBoardTab(goal = g, detail = vm, sharedModel = workModel)
                                            GoalDetailTab.stats -> GoalRecordStatsTab(goal = g, vm = vm)
                                            GoalDetailTab.reflections -> GoalReflectionsTab(goal = g)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 스펙 12.4: 탭이 위로 지나가면 "보드 ▲" 알약 — 누르면 탭 바가 보이게 스크롤
            val g0 = goal
            if (g0 != null && loadedNormal) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = tabsScrolledOut,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                ) {
                    val current = tabOverride ?: goalDetailInitialTab(g0)
                    val label = goalDetailTabs(g0).firstOrNull { it.first == current }?.second ?: ""
                    val pill = RoundedCornerShape(50)
                    Row(
                        Modifier
                            .height(32.dp)
                            .cardShadow(pill)
                            .clip(pill)
                            .background(NC.bgSurface, pill)
                            .border(1.dp, NC.borderSubtle, pill)
                            .clickable(role = Role.Button) { scope.launch { listState.animateScrollToItem(tabsIndex) } }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(label, style = NType.labelMD, color = NC.textPrimary)
                        SfImage("chevron.up", size = 14.dp, tint = NC.textSecondary)
                    }
                }
            }

            // 오른쪽 아래 떠 있는 버튼들
            if (goal != null && goalDetailHasFab(goal)) {
                Column(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // ✨ 말로 고치기 (스펙 12.9: 기록 버튼과 같은 56 크기)
                    if (goalDetailCanAiEdit(goal)) {
                        GoalAIFab {
                            vm.track("goal_detail.ai_edit_open", mapOf("entry_point" to "fab", "is_prefilled" to false))
                            vm.sheet = GoalDetailSheet.aiEdit
                        }
                    }
                    // ✏️ 오늘 기록 (주 버튼 — 매일 누르는 것). 업무형은 할 일 · 한 일 · 회고를 한곳에서 적기
                    if (goalDetailCanCheckIn(goal)) {
                        GoalCheckInFab(done = vm.checkedInToday) {
                            vm.track("goal_detail.checkin_open", mapOf("entry_point" to "fab", "is_edit" to vm.checkedInToday))
                            vm.sheet = GoalDetailSheet.checkIn
                        }
                    } else if (goalDetailCanWorkLog(goal)) {
                        GoalCheckInFab(done = false, label = "적기") {
                            vm.track("work_compose.open", mapOf("compose_tab" to "task", "entry_point" to "fab"))
                            vm.sheet = GoalDetailSheet.workCompose
                        }
                    }
                }
            }
        }

        // 시트 (한 번에 하나)
        val s = vm.sheet
        if (s != null && goal != null) {
            key(s.id) {
                GoalDetailSheetContent(sheet = s, goal = goal, vm = vm)
            }
        }

        ConfirmDialog(
            isPresented = vm.confirm != null,
            onDismiss = { vm.confirm = null },
            title = vm.confirmTarget?.title ?: "",
            message = vm.confirmTarget?.message,
            confirmLabel = "삭제",
            tone = ConfirmTone.danger,
            onCancel = { vm.cancelConfirm() },
        ) {
            // 확인창이 닫히며 confirm 이 먼저 비워질 수 있어서, 따로 기억해 둔 confirmTarget 을 써요
            val c = vm.confirmTarget
            if (c != null) scope.launch { vm.perform(c) }
        }
    }
}

/** 오늘 기록은 달성형 목표가 끝나기 전까지 (업무형은 업무 일지에서 한 줄 기록) */
private fun goalDetailCanCheckIn(goal: GoalCategory): Boolean =
    !goal.isRecordGoal && !goal.isHabitGoal && goal.goalCategoryStatus != GoalStatus.COMPLETED

/** 업무형: 끝나기 전까지 오늘 한 일 기록 */
private fun goalDetailCanWorkLog(goal: GoalCategory): Boolean =
    goal.isRecordGoal && goal.goalCategoryStatus != GoalStatus.COMPLETED

private fun goalDetailCanAiEdit(goal: GoalCategory): Boolean =
    !goal.isRecordGoal && !goal.isHabitGoal && !goal.isTemporary && goal.goalCategoryStatus != GoalStatus.COMPLETED

private fun goalDetailHasFab(goal: GoalCategory): Boolean =
    goalDetailCanCheckIn(goal) || goalDetailCanWorkLog(goal) || goalDetailCanAiEdit(goal)

/** 오른쪽 위 … 메뉴 */
@Composable
private fun GoalDetailMoreMenu(goal: GoalCategory, vm: GoalDetailViewModel) {
    // 기본 생활 습관 그릇은 모든 사용자에게 하나씩 있는 목표라 마무리하지 않아요 (습관 단위로 쉬거나 지워요)
    val ongoing = !goal.isTemporary && goal.goalCategoryStatus == GoalStatus.IN_PROGRESS && !goal.isRecordGoal && !goal.isHabitGoal
    val items = buildList {
        add(MenuItem("목표 수정", "pencil") {
            vm.track("goal_detail.edit_open", mapOf("goal_mode" to goal.analyticsMode, "entry_point" to "menu"))
            vm.sheet = GoalDetailSheet.editGoal
        })
        if (goalDetailCanAiEdit(goal)) {
            add(MenuItem("말로 고치기", "wand.and.stars") {
                vm.track("goal_detail.ai_edit_open", mapOf("entry_point" to "menu", "is_prefilled" to false))
                vm.sheet = GoalDetailSheet.aiEdit
            })
        }
        if (ongoing) {
            add(MenuItem("목표 마무리", "flag.checkered") {
                vm.track("goal_detail.wrapup_open", mapOf(
                    "wrapup_prompt" to goalWrapUpPrompt(goal, vm.progress?.metric?.status == ProgressStatus.ACHIEVED),
                    "entry_point" to "menu",
                ))
                vm.sheet = GoalDetailSheet.wrapUp
            })
        }
    }
    NMenu(items = items, modifier = Modifier.padding(end = 4.dp).semantics { contentDescription = "더보기" }) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            SfImage("ellipsis.circle", size = 20.dp, tint = NC.ink2)
        }
    }
}

/** 시트 내용 고르기 (iOS sheetContent) */
@Composable
private fun GoalDetailSheetContent(sheet: GoalDetailSheet, goal: GoalCategory, vm: GoalDetailViewModel) {
    val dismiss: () -> Unit = { vm.sheet = null }
    when (sheet) {
        is GoalDetailSheet.editGoal ->
            GoalEditFormSheet(goal = goal, colors = vm.colors, vm = vm, onDismiss = dismiss)
        is GoalDetailSheet.subGoal ->
            GoalSubGoalFormSheet(goal = goal, sub = sheet.sub, colors = vm.colors, vm = vm, onDismiss = dismiss)
        is GoalDetailSheet.routine ->
            GoalRoutineFormSheet(goal = goal, routine = sheet.routine, subGoalId = sheet.subGoalId, onDismiss = dismiss)
        is GoalDetailSheet.routineExtraDay ->
            GoalRoutineExtraDaySheet(routine = sheet.routine, onDismiss = dismiss)
        is GoalDetailSheet.milestone ->
            GoalMilestoneFormSheet(goal = goal, milestone = sheet.milestone, isLast = sheet.isLast, vm = vm, onDismiss = dismiss)
        is GoalDetailSheet.task -> {
            // 기본 생활 습관의 일회성 Task 는 "할 일"이라고 불러요
            val noun = if (goal.isHabitGoal) "할 일" else "Task"
            SheetScaffold(
                onDismiss = dismiss,
                title = if (sheet.task == null) "$noun 추가" else "$noun 수정",
                fullHeight = true,
            ) { close ->
                GoalSheetForm {
                    GoalTaskFormView(goal = goal, task = sheet.task, onDone = { close() }, analyticsSurface = vm.taskSurface)
                }
            }
        }
        is GoalDetailSheet.editMetric ->
            GoalMetricEditSheet(goal = goal, entryPoint = sheet.entryPoint, vm = vm, onDismiss = dismiss)
        is GoalDetailSheet.metricLog -> {
            val p = vm.progress
            val m = p?.metric
            if (p != null && m != null) {
                GoalMetricLogEditSheet(
                    goalId = goal.goalCategoryId,
                    metric = m,
                    today = p.today,
                    date = sheet.date,
                    value = sheet.value,
                    vm = vm,
                    onDismiss = dismiss,
                )
            } else {
                // 진행 데이터가 없으면 띄울 게 없어요 — 시트 상태만 비워요
                LaunchedEffect(Unit) { vm.sheet = null }
            }
        }
        is GoalDetailSheet.wrapUp ->
            GoalWrapUpSheet(
                goal = goal,
                metricDone = vm.progress?.metric?.status == ProgressStatus.ACHIEVED,
                vm = vm,
                onDismiss = dismiss,
            )
        is GoalDetailSheet.checkIn ->
            GoalCheckInSheet(goal = goal, vm = vm, onDismiss = dismiss)
        is GoalDetailSheet.workLog ->
            SheetScaffold(
                onDismiss = dismiss,
                title = "오늘 한 일 기록",
                description = "한 줄로 적으면 시각·걸린 시간을 알아서 읽어요.",
            ) { close ->
                RecordQuickLogView(goal = goal, sheetMode = true, onDone = { close() })
            }
        is GoalDetailSheet.workCompose ->
            WorkComposeSheet(goal = goal, detail = vm, onDismiss = dismiss)
        is GoalDetailSheet.aiEdit ->
            GoalAiEditSheet(goal = goal, onDismiss = dismiss)
        is GoalDetailSheet.aiPreview ->
            GoalAiEditPreviewSheet(goalId = goal.goalCategoryId, preview = sheet.preview, vm = vm, onDismiss = dismiss)
        is GoalDetailSheet.legacySetup ->
            GoalLegacySetupSheet(goal = goal, trigger = sheet.trigger, vm = vm, onDismiss = dismiss)
    }
}

/** iOS enum GoalDetailGeo (스크롤 좌표 이름) — Android 는 LazyListState 로 대신해서 이름만 남겨요 */
object GoalDetailGeo {
    const val space = "goal-detail-scroll"
}

// MARK: - 탭

private enum class GoalDetailTab {
    today, progress, plan,

    /** 업무형: 보드(로드맵 · 보드 · 업무 일지 · 루틴) · 통계(나의 패턴 · 루틴 실천) */
    board, stats,

    /** 이 목표에 남긴 기록 · 회고를 날짜별로 (두 형태 모두) */
    reflections;

    /** 앰플리튜드 tab / from_tab 값 */
    val analyticsName: String get() = name

    companion object {}
}

/** 처음 탭: 업무형은 보드, 끝난 목표는 결과 · 그래프가 먼저 궁금해요 */
private fun goalDetailInitialTab(goal: GoalCategory): GoalDetailTab = when {
    goal.isRecordGoal -> GoalDetailTab.board
    // 스펙 13.5: 목표형은 늘 진행이 처음 (끝난 목표도)
    else -> GoalDetailTab.progress
}

private fun goalDetailTabs(goal: GoalCategory): List<Pair<GoalDetailTab, String>> =
    if (goal.isRecordGoal) {
        // 스펙 13.9: 보드 → 업무관리 (앰플리튜드 값은 그대로 "board")
        listOf(GoalDetailTab.board to "업무관리", GoalDetailTab.stats to "통계", GoalDetailTab.reflections to "회고")
    } else {
        listOf(
            GoalDetailTab.progress to "진행",
            GoalDetailTab.plan to "계획",
            GoalDetailTab.reflections to "회고",
        )
    }

/** 탭 바 (밑줄 · 위에 붙음) */
@Composable
private fun GoalDetailTabBar(
    selection: GoalDetailTab,
    onSelectionChange: (GoalDetailTab) -> Unit,
    items: List<Pair<GoalDetailTab, String>>,
) {
    val haptic = LocalHapticFeedback.current
    val lineColor = NC.borderSoft
    Row(
        Modifier
            .fillMaxWidth()
            .background(NC.bgSurface)
            // 아래 1dp 선 (iOS .overlay(alignment: .bottom) { Rectangle().frame(height: 1) })
            .drawBehind {
                drawRect(lineColor, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
            },
    ) {
        for ((value, label) in items) {
            val on = value == selection
            Column(
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Tab) {
                        if (!on) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectionChange(value)
                        }
                    }
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    label,
                    style = pt(15, if (on) PTWeight.bold else PTWeight.medium),
                    color = if (on) NC.ink else NC.ink3,
                )
                val alpha by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "tab-line")
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .height(3.dp)
                        .background(NC.ink.copy(alpha = alpha), CircleShape),
                )
            }
        }
    }
}

// MARK: - 탭 내용

/** 오늘: 지금 해야 할 것 — 주간 점검 · 오늘 기록 · 할 일 · 시작한 이유 */
@Composable
private fun GoalTodayTab(
    goal: GoalCategory,
    vm: GoalDetailViewModel,
    showPlaceCard: Boolean,
    onShowPlaceCardChange: (Boolean) -> Unit,
) {
    val completed = goal.goalCategoryStatus == GoalStatus.COMPLETED
    val ongoing = !goal.isTemporary && goal.goalCategoryStatus == GoalStatus.IN_PROGRESS
    if (showPlaceCard) {
        GoalPlaceAfterCreateCard(goal = goal, onDismiss = { onShowPlaceCardChange(false) })
    }
    if (goal.isTemporary) {
        GoalRefineWithAiCard(goal = goal)
    }
    // 주간 점검: 2주 연속 너무 힘들거나 너무 쉬우면 루틴 하루 빼기/더하기 제안
    val adj = vm.progress?.adjustment
    if (ongoing && adj != null) {
        GoalAdjustmentBanner(goalId = goal.goalCategoryId, adjustment = adj, vm = vm)
    }
    // 스펙 12.7: "오늘 어땠나요?" 카드는 빼요 — 떠 있는 기록 버튼이 같은 일을 해요
    // (할 일 목록은 계획 탭 맨 아래로 — 스펙 13.5)
}

/** 진행: 얼마나 왔는지 — 그래프 모음 */
@Composable
private fun GoalProgressTab(goal: GoalCategory, vm: GoalDetailViewModel) {
    val completed = goal.goalCategoryStatus == GoalStatus.COMPLETED
    if (completed && !goal.isTemporary) {
        GoalOutcomeSection(goalId = goal.goalCategoryId, outcome = vm.progress?.outcome, vm = vm)
    }
    val achievement = goal.achievement
    if (!achievement.isNullOrEmpty()) {
        SectionCard(title = "달성 기록") {
            Text(achievement, style = pt(15).copy(lineHeight = 22.sp), color = NC.ink)
        }
    }
    // 스펙 13.7: 칩 한 줄 (전체 · 지금 어디쯤? · Task 실천률 · 루틴 실천) — 고른 것만 보여요
    val tasks = vm.tasks
    val views = buildList {
        add(GoalProgressView.all)
        if (!goal.isTemporary) add(GoalProgressView.position)
        if (!tasks?.tasks.isNullOrEmpty()) add(GoalProgressView.taskRate)
        if (!vm.heatmap.isNullOrEmpty()) add(GoalProgressView.routineRate)
    }
    var view by rememberSaveable(goal.goalCategoryId) { mutableStateOf(GoalProgressView.all) }
    val current = if (view in views) view else GoalProgressView.all
    GoalProgressViewChips(views = views, selected = current, goal = goal, vm = vm) { view = it }
    when (current) {
        GoalProgressView.all -> GoalCombinedProgressCard(goal = goal, vm = vm)
        GoalProgressView.position -> {
            GoalProgressSection(goal = goal, vm = vm)
            // 추가 그래프 (V52) — 대표 수치 말고 같이 볼 수치
            GoalExtraMetricsSection(goal = goal, vm = vm)
        }
        GoalProgressView.taskRate -> if (tasks != null) GoalTaskRateSection(tasks = tasks.tasks)
        GoalProgressView.routineRate -> GoalRoutineHeatmapSection(routines = vm.heatmap)
    }
}

/** 계획: 어떻게 할지 — 루틴 · 세부 구조 · 마무리 */
@Composable
private fun GoalPlanTab(goal: GoalCategory, vm: GoalDetailViewModel) {
    // 스펙 13.5: 마일스톤 → 루틴 → Task. "목표 마무리"는 ⋯ 메뉴로 옮겼어요
    GoalStructurePanel(goal = goal)
    GoalRoutinesSection(goal = goal)
    GoalTaskListSection(goal = goal)
}

/** 회고: 이 목표에 남긴 기록(수치 · 기분 · 한 줄 회고) · 회고 글을 날짜별로 (회고 모아보기와 같은 목록) */
@Composable
private fun GoalReflectionsTab(goal: GoalCategory) {
    val feed = rememberScreenModel(goal.goalCategoryId) {
        ReflectionsViewModel(goalId = goal.goalCategoryId).apply { surface = "goal_detail" }
    }
    val hint = if (goal.isRecordGoal) {
        "아래 '적기' 버튼의 회고나 홈의 '오늘 회고'에서 남기면 여기 모여요."
    } else {
        "아래 연필 버튼의 '오늘 기록'이나 홈의 '오늘 회고'에서 수치 · 기분 · 한 줄 회고를 남기면 여기 모여요."
    }
    ReloadOn(listOf(DataKey.home, DataKey.goals, DataKey.work), id = feed.reloadId) { feed.load() }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("이 목표의 회고", style = NType.h4, color = NC.ink)
            Text(hint, style = pt(12), color = NC.ink3)
        }
        ReflectionTimelineView(vm = feed, showGoal = false, emptyHint = hint)
    }
}

/** 업무형 통계: 이 목표에 대한 나의 패턴 + 루틴 실천 (보드 탭에 있던 걸 웹처럼 여기로) */
@Composable
private fun GoalRecordStatsTab(goal: GoalCategory, vm: GoalDetailViewModel) {
    RecordPatternSectionView(goalId = goal.goalCategoryId)
    GoalRoutineHeatmapSection(
        routines = vm.heatmap,
        modifier = if (vm.isPreview) Modifier else Modifier.onFirstHalfVisible {
            WK.track("record_stats.card_view", goalId = goal.goalCategoryId, props = mapOf("stats_card" to "routine_heatmap"))
        },
    )
}

// MARK: - 머리 카드 (이름 · 상태 · 기간 · 진행 링)

/** 링 옆 요약 (iOS 안쪽 struct Summary) */
private data class GoalHeroSummary(val ratio: Double, val caption: String, val headline: String)

@Composable
private fun GoalDetailHeroCard(goal: GoalCategory, vm: GoalDetailViewModel, modifier: Modifier = Modifier) {
    val period = GoalFormat.periodProgress(goal.goalCategoryStartDate, goal.goalCategoryEndDate)
    // 업무형 · 임시 목표는 진행률이 의미 없어서 링 없이 이름만
    val showsProgress = !goal.isTemporary && !goal.isRecordGoal
    val emoji = goal.emoji
    val titleText = if (!emoji.isNullOrEmpty()) "$emoji ${goal.goalCategoryName}" else goal.goalCategoryName
    val shape = RoundedCornerShape(Radius.card)
    // NowCard 스타일 (Figma 3022:14866): 목표 색 tint 바탕 · textPrimary/Secondary (예전 보라 그라데이션 대신)
    val heroTone = goalTone(goal.colorCode)

    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(heroTone.bg, shape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    titleText,
                    style = pt(22, PTWeight.bold),
                    color = NC.textPrimary,
                    modifier = Modifier.padding(end = 22.dp),
                )
                GoalHeroTags(goal)
            }

            if (showsProgress) {
                GoalHeroProgressRow(goal = goal, vm = vm, period = period)
                if (period != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val sv = goal.startValue
                        if (goal.targetValue != null && sv != null) {
                            GoalHeroStatBox(
                                "현재",
                                "${GoalDetailLogic.trim(goal.currentValue ?: sv, 6)}${goal.metricUnit ?: ""}",
                                Modifier.weight(1f),
                            )
                        }
                        GoalHeroStatBox("경과", "${period.elapsed} / ${period.total}일", Modifier.weight(1f))
                        GoalHeroStatBox("남은 기간", GoalFormat.dDayLabel(period.remain), Modifier.weight(1f))
                    }
                }
            }
        }
        // 목표 색 표시 (목록 · 캘린더와 같은 색)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(14.dp)
                .background(goalColor(goal.colorCode), CircleShape)
                .border(2.dp, NC.bgSurface, CircleShape),
        )
    }
}

/** 머리 카드 둘째 줄 태그 (진행 중 · 업무형 · 📅 기간) — 업무형 요약 카드도 같이 써요 */
@Composable
private fun GoalHeroTags(goal: GoalCategory) {
    FlowLayout(spacing = 6.dp) {
        GoalHeroPill(
            goal.goalCategoryStatus.label,
            icon = if (goal.goalCategoryStatus == GoalStatus.COMPLETED) "checkmark" else null,
        )
        if (goal.isTemporary) GoalHeroPill("임시 목표")
        // 업무형은 회사 업무 · 취업 준비 · 동아리처럼 종류가 다양해서 종류를 나누지 않아요
        if (goal.isRecordGoal) GoalHeroPill("업무형")
        val kind = goal.goalKindName
        if (kind != null && goal.goalType != GoalType.WORK) GoalHeroPill(kind)
        val start = goal.goalCategoryStartDate
        if (start != null) GoalHeroPill(goalHeroPeriodLine(goal, start), icon = "calendar")
    }
}

/** 수치 목표면 수치 진행률, 아니면 기간 경과율 */
private fun goalHeroSummary(goal: GoalCategory, period: GoalFormat.Period?): GoalHeroSummary {
    val unit = goal.metricUnit ?: ""
    val start = goal.startValue
    val target = goal.targetValue
    if (start != null && target != null) {
        val current = goal.currentValue ?: start
        val span = target - start
        val ratio = if (span == 0.0) 1.0 else (current - start) / span
        val caption = "목표까지 남은 ${goal.metricName ?: ""}".trim()
        return GoalHeroSummary(ratio, caption, "${GoalDetailLogic.trim(kotlin.math.abs(target - current), 1)}$unit")
    }
    return GoalHeroSummary(
        ratio = period?.ratio ?: 0.0,
        caption = "기간 경과",
        headline = period?.let { "${it.elapsed}일째" } ?: "-",
    )
}

@Composable
private fun GoalHeroProgressRow(goal: GoalCategory, vm: GoalDetailViewModel, period: GoalFormat.Period?) {
    val s = goalHeroSummary(goal, period)
    val clamped = s.ratio.coerceIn(0.0, 1.0)
    val animated by animateFloatAsState(clamped.toFloat(), tween(600), label = "hero-ring")
    // Canvas 안에서는 NC 를 못 읽어서 밖에서 꺼내 둬요
    val ringTrack = NC.bgSurface.copy(alpha = 0.7f)
    val ringFill = goalTone(goal.colorCode).accent
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 9.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = ringTrack,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color = ringFill,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Text("${Math.round(clamped * 100).toInt()}%", style = ptMono(20, PTWeight.bold), color = NC.textPrimary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(s.caption, style = pt(13), color = NC.textSecondary)
            Text(
                s.headline,
                style = pt(32, PTWeight.bold),
                color = NC.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val status = vm.progress?.metric?.status
            if (status != null) {
                Text(
                    goalHeroStatusText(status),
                    style = pt(12, PTWeight.semibold),
                    color = NC.textPrimary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .background(NC.bgSurface.copy(alpha = 0.7f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

private fun goalHeroStatusText(s: ProgressStatus): String = when (s) {
    ProgressStatus.ACHIEVED -> "목표 달성 🎉"
    ProgressStatus.AHEAD -> "계획보다 앞서요"
    ProgressStatus.ON_TRACK -> "계획대로 가는 중"
    ProgressStatus.BEHIND -> "계획보다 조금 늦어요"
    ProgressStatus.NOT_STARTED -> "시작 전"
    ProgressStatus.NO_PLAN -> "종료일 없음"
}

@Composable
private fun GoalHeroPill(text: String, icon: String? = null) {
    Row(
        Modifier
            .height(24.dp)
            .background(NC.bgSurface.copy(alpha = 0.7f), CircleShape)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) SfImage(icon, size = 10.dp, tint = NC.textSecondary)
        Text(text, style = pt(12, PTWeight.semibold), color = NC.textSecondary, maxLines = 1)
    }
}

@Composable
private fun GoalHeroStatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(NC.bgSurface.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = pt(11), color = NC.textSecondary)
        Text(value, style = ptMono(15, PTWeight.bold), color = NC.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun goalHeroPeriodLine(goal: GoalCategory, start: String): String {
    // 업무형은 전체 마감이 없어요 (V53 — 마감은 주제마다)
    if (goal.isRecordGoal) return "${GoalFormat.formatDot(start)} 시작 · 마감은 세부 목표마다"
    val end = goal.goalCategoryEndDate?.let { GoalFormat.formatDot(it) } ?: "종료일 미정"
    return "${GoalFormat.formatDot(start)} – $end"
}

// MARK: - 떠 있는 버튼 (누르면 살짝 작아져요 — iOS TimetableFabPressStyle)


/** ✨ 말로 고치기 떠 있는 버튼 (스펙 13.6: 공용 NFab) */
@Composable
private fun GoalAIFab(action: () -> Unit) {
    NFab(icon = "wand.and.stars", label = "말로 고치기", style = NFabStyle.ai, onClick = action)
}

/** ✏️ 오늘 기록 떠 있는 버튼. done 이면 오늘 이미 기록했다는 작은 체크 표시 */
@Composable
private fun GoalCheckInFab(done: Boolean, label: String? = null, action: () -> Unit) {
    val a11y = label ?: (if (done) "오늘 기록 수정" else "오늘 기록하기")
    val doneBadge: @Composable BoxScope.() -> Unit = {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 2.dp, y = (-2).dp)
                .size(20.dp)
                .background(NC.actionPrimary, CircleShape)
                .border(2.dp, NC.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SfImage("checkmark", size = 10.dp, tint = Color.White)
        }
    }
    NFab(icon = "square.and.pencil", label = a11y, onClick = action, badge = if (done) doneBadge else null)
}

// MARK: - 불러오지 못했을 때

@Composable
private fun GoalDetailErrorCard(
    notFound: Boolean,
    message: String,
    errorStatus: Int?,
    retry: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier
            .fillMaxWidth()
            .card()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (notFound) "목표를 찾을 수 없어요" else "목표를 불러오지 못했어요",
            style = NType.h4,
            color = NC.ink,
        )
        if (!notFound) {
            Text(message, style = pt(14), color = NC.ink3, textAlign = TextAlign.Center)
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NButton("목표 목록", variant = NButtonVariant.ghost, size = NButtonSize.sm) {
                GD.track("goal_detail.load_fail_cta_click", mapOf("cta" to "goal_list", "error_status" to errorStatus))
                AppRouter.openGoalList()
            }
            if (!notFound) {
                NButton("다시 시도", size = NButtonSize.sm) {
                    GD.track("goal_detail.load_fail_cta_click", mapOf("cta" to "retry", "error_status" to errorStatus))
                    scope.launch { retry() }
                }
            }
        }
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, heightDp = 1200, name = "달성형")
@Composable
private fun GoalDetailScreenPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview() }
        GoalDetailScreenBody(vm = vm, justCreated = true)
    }
}

@Preview(showBackground = true, heightDp = 1200, name = "업무형")
@Composable
private fun GoalDetailScreenRecordPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview(goal = GoalPreviewData.recordGoal) }
        GoalDetailScreenBody(vm = vm, justCreated = false)
    }
}

@Preview(showBackground = true, heightDp = 1200, name = "임시 목표")
@Composable
private fun GoalDetailScreenTempPreview() {
    NaleulTheme(dark = false) {
        val vm = remember {
            GoalDetailViewModel.preview(goal = GoalPreviewData.tempGoal, progress = GoalPreviewData.taskOnlyProgress)
        }
        GoalDetailScreenBody(vm = vm, justCreated = false)
    }
}
