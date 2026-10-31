// feature/taskadd/TaskAddManualView.kt
//
// iOS Features/TaskAdd/TaskAddManualView.swift
// 웹 원본: features/brain-dump/ui/ManualTaskAdd.tsx
//
// Task 직접 추가 — 목표를 고르고, 이름·날짜·시간(선택)·영역(선택)을 정해 저장.
// 시간을 비워 두면 그날 "시간 미정"으로 들어가고, 바로 "AI로 빈 시간에 배치하기"를 누를 수 있어요.
// 입력 폼은 Goal 작업자의 GoalTaskFormView (웹 goal/ui/GoalTaskForm) 를 그대로 써요.
// 시트(웹 panel) 안이라 "캘린더에서 보기" 버튼은 숨겨요 (웹 inPanel).

package com.naleul.naleul.feature.taskadd

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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun TaskAddManualView(
    /** 진행 중 · 시작 전 목표 (목록이 아직이면 loading) */
    goals: List<GoalCategory>,
    loading: Boolean,
    /** 새로 만든 Task — 캘린더가 그 날짜로 옮기고 NEW 를 붙여요 */
    onCreated: (TimeBlockTask) -> Unit,
    /** "목표 만들기" 전에 시트를 닫아요 */
    closeSheet: () -> Unit,
    modifier: Modifier = Modifier,
    /** 목표 목록을 못 불러왔는지 (no_goal_view 의 goal_list_state) */
    goalsFailed: Boolean = false,
    /** 폼에 적던 내용이 있는지 (Task 추가 시트의 has_input) */
    onInputChange: (Boolean) -> Unit = {},
    /** 캘린더 빈 칸에서 연 날짜 · 시각 (미리 채움) */
    prefillDate: String? = null,
    prefillStartMinutes: Int? = null,
) {
    when {
        loading -> LoadingView(minHeight = 200.dp, modifier = modifier.fillMaxWidth().card())
        goals.isEmpty() -> ManualEmptyCard(closeSheet = closeSheet, goalsFailed = goalsFailed, modifier = modifier)
        else -> ManualFormCard(goals = goals, onCreated = onCreated, onInputChange = onInputChange, modifier = modifier, prefillDate = prefillDate, prefillStartMinutes = prefillStartMinutes)
    }
}

// MARK: 목표가 없을 때

@Composable
private fun ManualEmptyCard(closeSheet: () -> Unit, goalsFailed: Boolean, modifier: Modifier) {
    LaunchedEffect(Unit) {
        TA.track("task_add_manual.no_goal_view", mapOf("goal_list_state" to if (goalsFailed) "error" else "empty"))
    }
    Column(
        modifier
            .fillMaxWidth()
            .card()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Task를 연결할 목표가 없어요", style = pt(15, PTWeight.bold), color = NC.ink)
        Text(
            "목표를 먼저 만들거나, AI로 정리하기를 쓰면 임시 목표가 자동으로 만들어져요.",
            style = pt(14),
            color = NC.ink3,
            textAlign = TextAlign.Center,
        )
        NButton("목표 만들기", modifier = Modifier.padding(top = 16.dp)) {
            TA.track("task_add_manual.goal_create_click")
            closeSheet()
            AppRouter.openGoalAdd()
        }
    }
}

// MARK: 폼

@Composable
private fun ManualFormCard(
    goals: List<GoalCategory>,
    onCreated: (TimeBlockTask) -> Unit,
    onInputChange: (Boolean) -> Unit,
    modifier: Modifier,
    prefillDate: String? = null,
    prefillStartMinutes: Int? = null,
) {
    var hasInput by remember { mutableStateOf(false) }
    fun setHasInput(v: Boolean) {
        hasInput = v
        onInputChange(v)
    }
    var goalId by remember { mutableStateOf<Int?>(null) }
    var goalDetail by remember { mutableStateOf<GoalCategory?>(null) }
    var formKey by remember { mutableStateOf(0) }
    var added by remember { mutableStateOf(listOf<TimeBlockTask>()) }
    /** AI 배치를 요청한 Task (시간은 캘린더에서 확인) */
    var placed by remember { mutableStateOf(setOf<Int>()) }
    var filling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val selectedId: Int? = goalId ?: goals.firstOrNull()?.goalCategoryId
    val selectedGoal: GoalCategory? = goals.firstOrNull { it.goalCategoryId == selectedId }

    val unplaced: List<TimeBlockTask> = added.filter { it.plannedStartAt == null && !placed.contains(it.taskId) }
    val unplacedDates: List<String> = unplaced.mapNotNull { it.date ?: it.scheduledDate }.toSet().sorted()

    // MARK: 도움 함수

    fun goalTitle(g: GoalCategory): String =
        (g.emoji?.let { "$it " } ?: "") + g.goalCategoryName + (if (g.isTemporary) " (임시)" else "")

    fun whenText(t: TimeBlockTask): String {
        val day = (t.plannedStartAt ?: t.date ?: "").take(10)
        val d = if (day.isEmpty()) (t.scheduledDate ?: "") else day
        val dayLabel = if (KST.isYmd(d)) KST.formatMonthDay(d) else ""
        val time = when {
            t.plannedStartAt != null -> "${KST.hm(t.plannedStartAt)}–${KST.hm(t.plannedEndAt)}"
            placed.contains(t.taskId) -> "AI가 빈 시간에 배치했어요"
            else -> "시간 미정"
        }
        return "$dayLabel · $time"
    }

    /** 목표를 바꾸면 그 목표의 상세(영역 · 마일스톤 포함)를 불러와요 (웹 useGoalCategory) */
    suspend fun loadDetail() {
        val id = selectedId ?: return
        try {
            val g = GoalAPI.goal(id = id)
            if (g != null) goalDetail = g
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 상세를 못 읽으면 목록에 있는 정보로라도 폼을 보여 줘요
            if (goalDetail?.goalCategoryId != id) goalDetail = selectedGoal
        }
    }

    /** 웹 useFillTimetable — 시간 미정 Task 를 그 날짜들의 빈 시간에 바로 배치 */
    fun fill() {
        val sorted = unplacedDates
        val first = sorted.firstOrNull() ?: return
        val last = sorted.lastOrNull() ?: return
        val ids = unplaced.map { it.taskId }
        TA.track("task_add_manual.ai_fill_click", mapOf("unscheduled_count" to ids.size, "day_count" to KST.diffDays(first, last) + 1))
        scope.launch {
            filling = true
            try {
                val r = TimetableAPI.fill(startDate = first, endDate = last)
                TA.track("task_add_manual.ai_fill_result", mapOf("result" to "success"))
                TimetableAPI.toastFillResult(r)
                placed = placed + ids
            } catch (e: CancellationException) {
                // 화면을 닫았어요
                throw e
            } catch (e: Exception) {
                TA.track("task_add_manual.ai_fill_result", mapOf("result" to "fail", "error_status" to TA.status(e)))
                Toast.error(errorMessage(e))
            } finally {
                filling = false
                Invalidation.invalidate(DataKey.timetable, DataKey.goals)
            }
        }
    }

    ReloadOn(listOf(DataKey.goals), id = selectedId) { loadDetail() }

    Column(
        modifier
            .fillMaxWidth()
            .card()
            .padding(20.dp),
    ) {
        Text("직접 추가하기", style = NType.h4, color = NC.ink)
        Text(
            "이름과 날짜만 넣고 Enter. 시간을 비워 두면 그날 빈 시간에 AI가 배치할 수 있어요.",
            style = pt(14),
            color = NC.ink3,
            modifier = Modifier.padding(top = 4.dp),
        )

        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("목표", style = pt(12, PTWeight.medium), color = NC.ink3)
            BrainDumpMenuField(text = selectedGoal?.let { goalTitle(it) } ?: "", isPlaceholder = false) {
                for (g in goals) {
                    Button(goalTitle(g)) {
                        TA.track("task_add_manual.goal_select", mapOf(
                            "goal_category_id" to g.goalCategoryId, "is_temporary" to g.isTemporary, "has_input" to hasInput,
                            "is_habit_goal" to g.isHabitGoal,
                        ))
                        goalId = g.goalCategoryId
                        formKey += 1
                        setHasInput(false)
                    }
                }
            }
        }

        Box(Modifier.padding(top = 12.dp)) {
            val detail = goalDetail
            if (detail != null && detail.goalCategoryId == selectedId) {
                // 목표 · 저장 횟수가 바뀌면 폼을 새로 만들어요 (iOS .id(...))
                key("${detail.goalCategoryId}-$formKey") {
                    GoalTaskFormView(
                        goal = detail,
                        task = null,
                        prefillDate = prefillDate,
                        prefillStartMinutes = prefillStartMinutes,
                        // 저장하면 폼을 비우고 다음 Task 를 바로 적을 수 있게
                        onDone = {
                            formKey += 1
                            setHasInput(false)
                        },
                        onCreated = { t ->
                            added = listOf(t) + added
                            // 분석: 직접 추가한 Task
                            AnalyticsService.logTaskAdded(mode = TaskAddMode.manual.rawValue, count = 1)
                            onCreated(t)
                        },
                        // 폼 안 동작은 goal_task_list.* (surface=task_add_manual). 폼이 다시 만들어질 때(목표 변경 · 저장)
                        // 생기는 '취소'는 사용자가 닫은 게 아니라서 edit_cancel 은 안 보내요
                        analyticsSurface = "task_add_manual",
                        tracksCancel = false,
                        onDirtyChange = { setHasInput(it) },
                    )
                }
            } else {
                LoadingView(minHeight = 160.dp)
            }
        }

        if (added.isNotEmpty()) {
            Column(Modifier.padding(top = 20.dp)) {
                HorizontalDivider(color = NC.borderSoft)
                Text(
                    "방금 추가한 Task ${added.size}",
                    style = pt(12, PTWeight.semibold),
                    color = NC.ink3,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (t in added) {
                        key(t.taskId) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                SfImage("checkmark", size = 12.dp, tint = NC.success)
                                Text(
                                    (t.emoji?.let { "$it " } ?: "") + t.taskName,
                                    style = pt(14, PTWeight.medium),
                                    color = NC.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(whenText(t), style = pt(12), color = NC.ink3, maxLines = 1)
                            }
                        }
                    }
                }
                if (unplacedDates.isNotEmpty()) {
                    NButton(
                        "시간 미정 ${unplaced.size}개 AI로 빈 시간에 배치하기",
                        variant = NButtonVariant.ai,
                        size = NButtonSize.sm,
                        icon = "sparkles",
                        loading = filling,
                        modifier = Modifier.padding(top = 12.dp),
                    ) { fill() }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TaskAddManualViewPreview() {
    NaleulTheme(dark = false) {
        Column(
            Modifier
                .canvasBackground()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            TaskAddManualView(goals = emptyList(), loading = false, onCreated = {}, closeSheet = {})
        }
    }
}
