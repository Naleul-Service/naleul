// feature/calendar/CalendarDayTaskList.kt
//
// iOS Features/Calendar/CalendarDayTaskList.swift 를 옮긴 파일.
// 일간 캘린더 아래 "오늘 할 일" 목록 (웹은 오른쪽, 휴대폰은 격자 아래).
// TimeTable 은 "언제" 하는지를, 이 목록은 "무엇을 남겼는지"를 한눈에 보여줘요.
//  - 남은 일(시간순, 시간 미정은 아래) / 완료한 일
//  - 동그라미를 누르면 바로 완료 · 완료 취소, 줄을 누르면 TimeTable 과 같은 상세 시트
// 웹 원본: features/timetable/ui/DayTaskList.tsx

package com.naleul.naleul.feature.calendar

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

@Composable
fun CalendarDayTaskList(
    board: TimetableBoardModel,
    day: TimetableDay?,
    today: String,
    modifier: Modifier = Modifier,
) {
    if (day == null) return
    val scope = rememberCoroutineScope()
    val all = CalendarDayTaskList.items(day)
    val todo = all.filter { it.taskStatus != TaskStatus.COMPLETED }
    val done = all.filter { it.taskStatus == TaskStatus.COMPLETED }
    val title = if (day.date == today) "오늘 할 일" else "${KST.formatMonthDay(day.date)} 할 일"
    val shape = RoundedCornerShape(Radius.xxl)

    @Composable
    fun row(t: TimeBlockTask) {
        CalendarTaskCheckRow(
            task = t, busy = board.togglingId == t.taskId, roundCheck = true,
            onToggle = {
                if (!board.isOffline) {
                    CAL.track(
                        "calendar_day_list.check_click",
                        mapOf(
                            "task_id" to t.taskId,
                            "check_action" to if (t.taskStatus == TaskStatus.COMPLETED) "uncomplete" else "complete",
                            "is_scheduled" to (t.plannedStartAt != null), "date_relation" to CAL.relation(day.date),
                        ),
                    )
                }
                scope.launch { board.toggleComplete(t, closeOnSuccess = false) }
            },
            onSelect = {
                if (!board.isOffline) {
                    CAL.track(
                        "calendar_day_list.row_click",
                        mapOf("task_id" to t.taskId, "task_status" to t.taskStatus.rawValue, "is_scheduled" to (t.plannedStartAt != null)),
                    )
                }
                board.selectTask(t, trackGrid = false)
            },
        )
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(NC.surface, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .semantics { contentDescription = title },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = pt(15, PTWeight.bold), color = NC.ink, modifier = Modifier.weight(1f))
            Text("${done.size} / ${all.size} 완료", style = ptMono(13), color = NC.textSecondary)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(NC.borderSoft))
        if (all.isNotEmpty()) {
            ProgressBar(
                value = done.size.toDouble() / all.size.toDouble(), height = 6.dp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
        }
        Column(Modifier.padding(8.dp)) {
            if (all.isEmpty()) {
                Text(
                    "이날 할 일이 없어요.",
                    style = pt(13),
                    color = NC.ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                )
            } else {
                for (t in todo) key(t.taskId) { row(t) }
                if (todo.isEmpty()) {
                    Text(
                        "모두 끝냈어요 🎉",
                        style = pt(13, PTWeight.semibold),
                        color = NC.success,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
                if (done.isNotEmpty()) {
                    Text(
                        "완료 ${done.size}",
                        style = pt(12, PTWeight.semibold),
                        color = NC.ink3,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                    )
                    for (t in done) key(t.taskId) { row(t) }
                }
            }
            // 못 해서 다른 날로 옮긴 일 (2026-10) — 이날 남는 흔적 "*"
            if (day.carriedOut.isNotEmpty()) {
                Text(
                    "* 다른 날로 옮긴 일 ${day.carriedOut.size}",
                    style = pt(12, PTWeight.semibold),
                    color = NC.ink3,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                )
                for (c in day.carriedOut) key("c-${c.id}") {
                    val to = c.toDate
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, enabled = to != null) {
                                if (!board.isOffline) {
                                    CAL.track("timetable.carried_trace_click", mapOf(
                                        "surface" to "day_list", "view_mode" to CalendarViewMode.day.rawValue, "task_id" to c.taskId,
                                        "is_scheduled" to (c.start != null), "day_offset" to (to?.let { KST.diffDays(day.date, it) }),
                                    ))
                                }
                                if (to != null) AppRouter.openCalendar(date = to)
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "* " + (c.emoji?.let { if (it.isEmpty()) "" else "$it " } ?: "") + c.taskName,
                            style = pt(14).copy(textDecoration = TextDecoration.LineThrough),
                            color = NC.ink3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (to != null) {
                            Text("→ ${KST.formatMonthDay(to)}", style = pt(12, PTWeight.medium), color = NC.textBrand)
                        }
                    }
                }
            }
        }
    }
}

/** iOS CalendarDayTaskList 의 static 함수 */
object CalendarDayTaskList {
    /** 전날 밤부터 이어진 블록은 그날 칸에도 보이지만, 목록은 "이날 하기로 한 일"만 (계획 시작일 또는 시간 미정 날짜) */
    fun items(day: TimetableDay): List<TimeBlockTask> {
        val seen = mutableSetOf<Int>()
        val all = mutableListOf<TimeBlockTask>()
        for (t in day.tasks + day.unscheduledTasks) {
            if (seen.contains(t.taskId) || t.taskStatus == TaskStatus.SKIPPED || t.taskStatus == TaskStatus.DELETED) continue
            seen.add(t.taskId)
            val planDay = TimetableRules.blockDay(t) ?: t.date ?: t.scheduledDate
            val completedDay = t.completedAt?.take(10)
            if (planDay == null || planDay == day.date || completedDay == day.date) all.add(t)
        }
        // 그날 정렬용 시작 분 (시간 미정은 맨 뒤)
        fun startOf(t: TimeBlockTask): Int = t.plannedStartAt?.let { KST.minutesFrom(day.date, it) } ?: Int.MAX_VALUE
        return all.sortedBy { startOf(it) }
    }
}

/** 체크 + 이름 한 줄 (일간 할 일 목록 · 월간 그날 할 일 공통) */
@Composable
fun CalendarTaskCheckRow(
    task: TimeBlockTask,
    busy: Boolean,
    /** 일간 목록: 동그라미 / 월간: 네모 */
    roundCheck: Boolean = false,
    /** 월간은 시간을 앞에, 일간은 뒤에 */
    timeLeading: Boolean = false,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
) {
    val t = task
    val done = t.taskStatus == TaskStatus.COMPLETED
    val mission = t.sourceType == TaskSourceType.MISSION
    val toggleEnabled = !(busy || (roundCheck && mission))
    Row(
        Modifier.padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp, 36.dp)
                .alpha(if (busy) 0.5f else 1f)
                .clickable(enabled = toggleEnabled, role = Role.Checkbox, onClick = onToggle)
                .semantics { contentDescription = if (done) "${t.taskName} 완료 취소" else "${t.taskName} 완료" },
            contentAlignment = Alignment.Center,
        ) {
            val shape = if (roundCheck) CircleShape else RoundedCornerShape(5.dp)
            Box(
                Modifier
                    .size(20.dp)
                    .background(if (done) NC.actionPrimary else (if (roundCheck) Color.Transparent else NC.surface), shape)
                    .border(1.5.dp, if (done) NC.actionPrimary else NC.borderControl, shape),
                contentAlignment = Alignment.Center,
            ) {
                if (done) SfImage("checkmark", size = 12.dp, tint = Color.White)
            }
        }
        Row(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onSelect)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (timeLeading) {
                val st = TimetableLayout.shownTime(t)
                val s = st?.let { KST.hm(it.startAt) }?.takeIf { it.isNotEmpty() }
                Text(s ?: "시간 미정", style = ptMono(12), color = NC.ink3, maxLines = 1, modifier = Modifier.width(54.dp))
            }
            Box(Modifier.size(8.dp).background(goalColor(t.goalColorCode), CircleShape))
            if ((t.taskPriority ?: TaskPriority.fallback).isTop) {
                PriorityMark(priority = TaskPriority.A, color = goalColor(t.goalColorCode), size = 14.dp)
            }
            Text(
                TimetableRules.title(t.emoji, t.taskName),
                style = pt(14).copy(textDecoration = if (done) TextDecoration.LineThrough else null),
                color = if (done) NC.ink3 else NC.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!timeLeading && t.sourceType == TaskSourceType.ROUTINE) {
                SfImage("repeat", size = 12.dp, tint = NC.ink4, contentDescription = "루틴")
            }
            if (t.missed && (timeLeading || !done)) {
                Text(
                    "놓침",
                    style = pt(10, PTWeight.bold),
                    color = NC.danger,
                    modifier = Modifier
                        .background(if (timeLeading) Color.Transparent else NC.dangerSoft, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp),
                )
            }
            if (!timeLeading) {
                Text(
                    if (t.plannedStartAt != null) KST.hm(t.plannedStartAt) else "미정",
                    style = ptMono(12),
                    color = NC.ink3,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(42.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendarDayTaskListPreview() {
    NaleulTheme(dark = false) {
        val d = KST.today()
        val board = remember { TimetableBoardModel.sample(start = d, end = d) }
        Column(Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
            CalendarDayTaskList(board = board, day = board.days.firstOrNull(), today = d)
        }
    }
}
