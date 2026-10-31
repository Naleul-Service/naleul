// Features/Goal/GoalTaskListSection.kt
//
// iOS GoalTaskListSection.swift — 웹 features/goal/ui/GoalTaskList.tsx — 목표 상세 · 관련 Task 전체.
// AI 가 만든 Task · 직접 추가한 Task · 루틴 Task 를 한 번에 보고, 완료/미완료 · 할 일/루틴으로 거를 수 있어요.
// 정렬은 서버 순서 그대로: 미완료 위 · 오늘과 가까운 날짜일수록 위 · 완료는 아래.

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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class GoalTaskStatusFilter {
    all, todo, done;

    val analyticsName: String get() = when (this) { all -> "ALL"; todo -> "TODO"; done -> "DONE" }
}

private enum class GoalTaskKindFilter {
    all, oneTime, routine;

    val analyticsName: String get() = when (this) { all -> "ALL"; oneTime -> "ONE_TIME"; routine -> "ROUTINE" }
}

/** 언제 할 Task 를 볼지 — 기본은 이번 주 (전체는 몇 달치가 쌓여 지금 할 일이 묻혀요) */
enum class GoalTaskPeriodFilter {
    today, week, all;

    /** 앰플리튜드 period 값 */
    val analyticsName: String get() = when (this) { today -> "TODAY"; week -> "WEEK"; all -> "ALL" }

    companion object {}
}

private const val GOAL_TASK_PAGE = 20

/**
 * @param title · hint · addLabel · emptyText 제목 · 안내 · 추가 버튼 · 빈 목록 문구 (기본 생활 습관 화면은 "할 일"로 바꿔 써요)
 */
@Composable
fun GoalTaskListSection(
    goal: GoalCategory,
    title: String = "관련 Task",
    hint: String? = null,
    addLabel: String = "Task 추가",
    emptyText: String = "이 목표에 연결된 Task가 아직 없어요. 위에서 바로 추가해 보세요.",
    initialPeriod: GoalTaskPeriodFilter = GoalTaskPeriodFilter.week,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    modifier: Modifier = Modifier,
) {
    var period by remember { mutableStateOf(initialPeriod) }
    var status by remember { mutableStateOf(GoalTaskStatusFilter.all) }
    var kind by remember { mutableStateOf(GoalTaskKindFilter.all) }
    var limit by remember { mutableStateOf(GOAL_TASK_PAGE) }
    val scope = rememberCoroutineScope()
    val today = KST.today()

    fun inPeriod(t: TimeBlockTask, p: GoalTaskPeriodFilter): Boolean {
        if (p == GoalTaskPeriodFilter.all) return true
        val day = GoalDetailLogic.dayOf(t) ?: return false
        if (p == GoalTaskPeriodFilter.today) return day == today
        val ws = KST.startOfWeek(today)
        return day >= ws && day <= KST.addDays(ws, 6)
    }

    fun matchKind(t: TimeBlockTask, k: GoalTaskKindFilter): Boolean = when (k) {
        GoalTaskKindFilter.all -> true
        GoalTaskKindFilter.routine -> t.sourceType == TaskSourceType.ROUTINE
        GoalTaskKindFilter.oneTime -> t.sourceType != TaskSourceType.ROUTINE
    }

    fun matchStatus(t: TimeBlockTask, s: GoalTaskStatusFilter): Boolean = when (s) {
        GoalTaskStatusFilter.all -> true
        GoalTaskStatusFilter.done -> GoalDetailLogic.isDone(t)
        GoalTaskStatusFilter.todo -> !GoalDetailLogic.isDone(t)
    }

    fun emptyMessageFor(byKindEmpty: Boolean): String {
        if (byKindEmpty) {
            return when (period) {
                GoalTaskPeriodFilter.today -> "오늘 할 Task가 없어요."
                GoalTaskPeriodFilter.week -> "이번 주 Task가 없어요."
                GoalTaskPeriodFilter.all -> "조건에 맞는 Task가 없어요."
            }
        }
        return if (status == GoalTaskStatusFilter.done) "아직 완료한 Task가 없어요." else "남은 Task가 없어요. 모두 끝냈어요! 🎉"
    }

    fun countFor(all: List<TimeBlockTask>, p: GoalTaskPeriodFilter, k: GoalTaskKindFilter, s: GoalTaskStatusFilter): Int =
        all.count { inPeriod(it, p) && matchKind(it, k) && matchStatus(it, s) }

    fun pickStatus(s: GoalTaskStatusFilter, all: List<TimeBlockTask>) {
        if (s == status) return
        status = s
        limit = GOAL_TASK_PAGE
        vm.track("goal_task_list.status_filter_select", mapOf(
            "surface" to vm.taskSurface, "task_filter" to s.analyticsName, "result_count" to countFor(all, period, kind, s),
        ))
    }

    fun pickKind(k: GoalTaskKindFilter, all: List<TimeBlockTask>) {
        if (k == kind) return
        kind = k
        limit = GOAL_TASK_PAGE
        vm.track("goal_task_list.kind_filter_select", mapOf(
            "surface" to vm.taskSurface, "task_kind" to k.analyticsName, "result_count" to countFor(all, period, k, status),
        ))
    }

    Column(
        modifier.fillMaxWidth().card().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = NType.h4, color = NC.ink)
                Spacer(Modifier.weight(1f))
                vm.tasks?.let { d ->
                    Text("${d.completedCount} / ${d.totalCount} 완료", style = pt(13), color = NC.ink3)
                }
            }
            if (hint != null) Text(hint, style = pt(13), color = NC.ink3)
        }
        // 추가: 이름·날짜만 넣고 저장
        GoalAddButton(addLabel) {
            vm.track("goal_task_list.add_click", mapOf("surface" to vm.taskSurface, "task_count" to (vm.tasks?.totalCount ?: 0)))
            vm.sheet = GoalDetailSheet.task(null)
        }

        val data = vm.tasks
        if (data != null) {
            if (data.tasks.isEmpty()) {
                Text(
                    emptyText,
                    style = pt(14),
                    color = NC.ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
            } else {
                // ── listBody ──
                val all = data.tasks
                val todayCount = all.count { inPeriod(it, GoalTaskPeriodFilter.today) }
                val weekCount = all.count { inPeriod(it, GoalTaskPeriodFilter.week) }
                val byKind = all.filter { inPeriod(it, period) && matchKind(it, kind) }
                val shown = byKind.filter { matchStatus(it, status) }
                val visible = shown.take(limit)
                val doneCount = byKind.count { GoalDetailLogic.isDone(it) }
                val hasRoutine = all.any { it.sourceType == TaskSourceType.ROUTINE }
                val firstDoneId: Int? =
                    if (status == GoalTaskStatusFilter.all) visible.firstOrNull { GoalDetailLogic.isDone(it) }?.taskId else null

                // 기간: 오늘 · 이번 주(기본) · 전체
                SegmentPicker(
                    selection = period,
                    onSelectionChange = {
                        if (it != period) {
                            period = it
                            limit = GOAL_TASK_PAGE
                            vm.track("goal_task_list.period_select", mapOf(
                                "surface" to vm.taskSurface, "period" to it.analyticsName,
                                "result_count" to countFor(all, it, kind, status),
                            ))
                        }
                    },
                    options = listOf(
                        GoalTaskPeriodFilter.today to "오늘 $todayCount",
                        GoalTaskPeriodFilter.week to "이번 주 $weekCount",
                        GoalTaskPeriodFilter.all to "전체 기간 ${all.size}",
                    ),
                )

                // 필터
                FlowLayout(spacing = 6.dp) {
                    NChip("전체 ${byKind.size}", selected = status == GoalTaskStatusFilter.all, size = ChipSize.sm) { pickStatus(GoalTaskStatusFilter.all, all) }
                    NChip("미완료 ${byKind.size - doneCount}", selected = status == GoalTaskStatusFilter.todo, size = ChipSize.sm) { pickStatus(GoalTaskStatusFilter.todo, all) }
                    NChip("완료 $doneCount", selected = status == GoalTaskStatusFilter.done, size = ChipSize.sm) { pickStatus(GoalTaskStatusFilter.done, all) }
                    if (hasRoutine) {
                        Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.width(1.dp).height(16.dp).background(NC.line))
                        }
                        NChip("모두", selected = kind == GoalTaskKindFilter.all, size = ChipSize.sm) { pickKind(GoalTaskKindFilter.all, all) }
                        NChip("할 일", selected = kind == GoalTaskKindFilter.oneTime, size = ChipSize.sm) { pickKind(GoalTaskKindFilter.oneTime, all) }
                        NChip("루틴", selected = kind == GoalTaskKindFilter.routine, size = ChipSize.sm) { pickKind(GoalTaskKindFilter.routine, all) }
                    }
                }

                if (shown.isEmpty()) {
                    Text(
                        emptyMessageFor(byKindEmpty = byKind.isEmpty()),
                        style = pt(14),
                        color = NC.ink3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    )
                    LaunchedEffect(period, status, kind) {
                        if (vm.once("task_empty_filter:$period:$status:$kind")) {
                            vm.track("goal_task_list.empty_filter_view", mapOf(
                                "surface" to vm.taskSurface, "period" to period.analyticsName,
                                "task_filter" to status.analyticsName, "unfiltered_count" to all.size,
                            ))
                        }
                    }
                } else {
                    Column {
                        visible.forEachIndexed { i, t ->
                            key(t.taskId) {
                                // 전체 보기에서 미완료와 완료 사이 구분선
                                if (t.taskId == firstDoneId) {
                                    Text(
                                        "완료한 Task",
                                        style = pt(12, PTWeight.semibold),
                                        color = NC.ink3,
                                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
                                    )
                                } else if (i > 0) {
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(NC.line))
                                }
                                GoalTaskRow(
                                    task = t,
                                    today = today,
                                    toggling = vm.togglingTaskId == t.taskId,
                                    onToggle = { scope.launch { vm.toggleTask(t) } },
                                    onEdit = if (t.sourceType == TaskSourceType.MANUAL) ({
                                        vm.track("goal_task_list.edit_open", mapOf(
                                            "surface" to vm.taskSurface, "task_id" to t.taskId, "source_type" to t.sourceType.name,
                                        ))
                                        vm.sheet = GoalDetailSheet.task(t)
                                    }) else null,
                                    onNameTap = { day ->
                                        vm.track("goal_task_list.task_name_click", mapOf(
                                            "surface" to vm.taskSurface, "task_id" to t.taskId, "days_from_today" to GD.daysFromToday(day),
                                        ))
                                    },
                                    onDelete = if (t.sourceType != TaskSourceType.MISSION) ({ vm.ask(GoalDetailConfirm.deleteTask(t)) }) else null,
                                )
                            }
                        }
                    }
                    if (shown.size > visible.size) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clickable(role = Role.Button) {
                                    vm.track("goal_task_list.load_more_click", mapOf(
                                        "surface" to vm.taskSurface, "shown_count" to visible.size,
                                        "remaining_count" to shown.size - visible.size,
                                    ))
                                    limit += GOAL_TASK_PAGE
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        ) {
                            Text("더 보기 (${shown.size - visible.size}개 남음)", style = pt(14, PTWeight.semibold), color = NC.ink2)
                            SfImage("chevron.down", size = 12.dp, tint = NC.ink2)
                        }
                    }
                }
            }
        } else if (vm.tasksFailed) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            ) {
                Text("Task를 불러오지 못했어요.", style = pt(14), color = NC.ink3)
                Text(
                    "다시 시도",
                    style = pt(14).copy(textDecoration = TextDecoration.Underline),
                    color = NC.ink2,
                    modifier = Modifier.clickable(role = Role.Button) {
                        vm.track("goal_task_list.retry_click", mapOf("surface" to vm.taskSurface))
                        scope.launch { vm.reloadTasks() }
                    },
                )
            }
        } else {
            Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NC.ink3, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
        }
    }
}

// MARK: - 한 줄

@Composable
private fun GoalTaskRow(
    task: TimeBlockTask,
    today: String,
    toggling: Boolean,
    onToggle: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onNameTap: ((String) -> Unit)? = null,
) {
    val done = GoalDetailLogic.isDone(task)
    val day = GoalDetailLogic.dayOf(task)

    val timeText = run {
        val shown = TimetableLayout.shownTime(task) ?: return@run "시간 미정"
        val end = shown.endAt?.let { "–${KST.hm(it)}" } ?: ""
        (if (shown.actual) "실제 " else "") + KST.hm(shown.startAt) + end
    }

    val dayColor = when {
        day == today && !done -> NC.brand
        task.missed -> NC.danger
        done -> NC.ink4
        else -> NC.ink2
    }

    val title = task.emoji.let { e -> if (!e.isNullOrEmpty()) e + " " + task.taskName else task.taskName }
    val subLine = listOfNotNull(task.generalCategoryName, task.milestoneTitle).filter { it.isNotEmpty() }.joinToString(" · ")

    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 완료 체크 동그라미
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (done) NC.actionPrimary else Color.Transparent, CircleShape)
                .border(1.5.dp, if (done) NC.actionPrimary else NC.borderControl, CircleShape)
                .clickable(enabled = !toggling, role = Role.Checkbox, onClick = onToggle)
                .semantics { contentDescription = if (done) "${task.taskName} 완료 취소" else "${task.taskName} 완료" },
            contentAlignment = Alignment.Center,
        ) {
            if (toggling) {
                CircularProgressIndicator(color = NC.ink3, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
            } else if (done) {
                SfImage("checkmark", size = 11.dp, tint = Color.White)
            }
        }

        // 날짜
        Column(Modifier.width(80.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                day?.let { GoalDetailLogic.dayLabel(it, today = today) } ?: "날짜 미정",
                style = pt(13, PTWeight.semibold),
                color = dayColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(timeText, style = ptMono(11), color = NC.ink4, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Box(Modifier.size(8.dp).background(goalColor(task.goalColorCode), CircleShape))

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // 날짜가 있으면 누르면 그날 캘린더로
            Text(
                title,
                style = pt(15, PTWeight.semibold).copy(textDecoration = if (done) TextDecoration.LineThrough else null),
                color = if (done) NC.ink4 else NC.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (day != null) {
                    Modifier.clickable(role = Role.Button) {
                        onNameTap?.invoke(day)
                        AppRouter.openCalendar(view = CalendarViewMode.day, date = day)
                    }
                } else {
                    Modifier
                },
            )
            if (subLine.isNotEmpty()) {
                Text(subLine, style = pt(12), color = NC.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        GoalRowActions(label = task.taskName, onEdit = onEdit, onDelete = onDelete)
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun GoalTaskListSectionPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview() }
        CompositionLocalProvider(LocalGoalDetailViewModel provides vm) {
            Column(Modifier.fillMaxSize().canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
                GoalTaskListSection(goal = GoalPreviewData.goal)
            }
        }
    }
}
