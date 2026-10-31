// feature/home/HomeSections.kt
//
// iOS Features/Home/HomeSections.swift
// 홈 카드들. 웹 원본: features/home/ui/HomeView.tsx(Summary) · TodayChecklist.tsx · Upcoming.tsx · TaskCheck.tsx
//  - HomeSummaryCard   : 오늘 요약 (날짜 · 인사 · 진행률 · 지금/다음 할 일 · 마감 · 오늘 회고)
//  - HomeTasksCard     : 할 일 (루틴 아닌 Task, 끝낸 일은 접어 둬요)
//  - HomeRoutinesCard  : 루틴 · 습관 (목표 루틴 Task + 기본 생활 습관)
//  - HomeUpcomingCard  : 다가오는 일정 (앞으로 6일)
//  - HomeRoadmapCard   : 마감 로드맵 (Task · 업무형 주제 · 목표 종료일을 날짜순 세로 선으로)

package com.naleul.naleul.feature.home

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val homeWeekdayFull = listOf("월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일")

/** "📝 이름" (이모지가 없으면 이름만) */
fun homeTitle(emoji: String?, name: String): String {
    if (emoji.isNullOrEmpty()) return name
    return "$emoji $name"
}

// MARK: - 공통 부품

/** 동그란 완료 체크 — 홈의 Task · 루틴 · 습관 공통 (웹 TaskCheck) */
@Composable
fun HomeCheckCircle(
    done: Boolean,
    label: String,
    disabled: Boolean = false,
    /** 꺼진 체크를 눌렀을 때 (home.disabled_click) */
    onDisabledTap: (() -> Unit)? = null,
    onToggle: () -> Unit,
) {
    val success = NC.actionPrimary
    val stroke = NC.borderControl
    Box(
        Modifier
            .size(36.dp)
            .alpha(if (disabled) 0.5f else 1f)
            .clip(CircleShape)
            .clickable(enabled = !disabled || onDisabledTap != null, role = Role.Checkbox) {
                if (disabled) onDisabledTap?.invoke() else onToggle()
            }
            .semantics { contentDescription = "$label ${if (done) "완료 취소" else "완료"}" },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Box(Modifier.size(22.dp).background(success, CircleShape), contentAlignment = Alignment.Center) {
                SfImage("checkmark", size = 14.dp, tint = Color.White)
            }
        } else {
            Box(Modifier.size(22.dp).border(1.5.dp, stroke, CircleShape))
        }
    }
}

/** 카드 머리 — 제목 · 끝낸 수/전체 · 오른쪽 버튼 · 진행 막대 */
@Composable
fun HomeCardHeader(title: String, done: Int, total: Int, trailing: @Composable RowScope.() -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = NType.h4, color = NC.ink)
            if (total > 0) {
                Text("$done / $total", style = ptMono(13), color = NC.ink3)
            }
            Spacer(Modifier.weight(1f))
            trailing()
        }
        if (total > 0) {
            ProgressBar(value = done.toDouble() / total.toDouble(), height = 6.dp)
        }
    }
}

/** 작은 네모 칩 (마감 · 놓침 · D-day) */
@Composable
private fun HomeTinyChip(text: String, background: Color, foreground: Color) {
    Text(
        text,
        style = pt(10, PTWeight.bold),
        color = foreground,
        maxLines = 1,
        modifier = Modifier
            .background(background, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** 카드 공통 겉모양 (iOS .padding(16).frame(maxWidth: .infinity).card()) */
@Composable
private fun Modifier.homeCard(): Modifier = this.fillMaxWidth().card().padding(16.dp)

// MARK: - 오늘 요약

/** 홈 날짜 ‹ › — 지난날 · 앞날의 홈을 볼 수 있어요 */
data class HomeDayNav(
    val isToday: Boolean,
    val isPast: Boolean,
    val onPrev: () -> Unit,
    val onNext: () -> Unit,
    val onToday: () -> Unit,
) {
    companion object {}
}

/** 지금 할 일 / 다음 일정 계산용 (iOS HomeSummaryCard.Agenda) */
private data class HomeAgenda(val start: Int, val end: Int, val task: TimeBlockTask)

@Composable
fun HomeSummaryCard(
    today: String,
    nowMinutes: Int,
    userName: String,
    day: TimetableDay?,
    tasks: List<TimeBlockTask>,
    routines: List<TimeBlockTask>,
    habitDone: Int,
    habitTotal: Int,
    dueToday: Int,
    overdue: Int,
    retroLeft: Int,
    /** /home/today 를 못 받으면 회고 버튼을 숨겨요 */
    showRetro: Boolean,
    onRetro: () -> Unit,
    /** 날짜 ‹ › (지난날 · 앞날 보기). null 이면 날짜만 글자로 */
    nav: HomeDayNav? = null,
) {
    val isToday = nav?.isToday ?: true
    val taskDone = tasks.count { it.isCompleted }
    val routineDone = routines.count { it.isCompleted }
    val total = tasks.size + routines.size + habitTotal
    val done = taskDone + routineDone + habitDone
    val percent = if (total > 0) (done.toDouble() / total.toDouble() * 100).roundToInt() else 0
    val left = total - done

    fun greeting(): String {
        if (!isToday) {
            if (total == 0) return "이날은 비어 있어요"
            if (nav?.isPast == true) {
                return if (left <= 0) "이날 할 일을 모두 끝냈어요 🎉" else "이날 ${total}개 중 ${total - left}개를 했어요"
            }
            return "이날 할 일 ${total}개가 잡혀 있어요"
        }
        if (total == 0) return "${userName}님,\n오늘은 비어 있어요"
        if (left <= 0) return "${userName}님,\n오늘 할 일을 모두 끝냈어요 🎉"
        return "${userName}님,\n오늘 ${left}개 남았어요"
    }

    fun countsLine(): String {
        var s = "할 일 $taskDone/${tasks.size}"
        if (routines.isNotEmpty()) s += " · 루틴 $routineDone/${routines.size}"
        if (habitTotal > 0) s += " · 습관 $habitDone/$habitTotal"
        return s
    }

    // NowCard 스타일 (Figma 3022:14866): 검정 대신 연한 tint 바탕 — 홈은 특정 목표가 없어 sky 톤
    val heroTone = GoalPalette.sky.tone(LocalIsDark.current)
    Column(
        Modifier
            .fillMaxWidth()
            .background(heroTone.bg, RoundedCornerShape(Radius.card))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HomeSummaryDateRow(today = today, nav = nav)
                Text(greeting(), style = pt(19, PTWeight.bold), color = NC.textPrimary)
            }
            if (showRetro) HomeSummaryRetroButton(isToday = isToday, retroLeft = retroLeft, onRetro = onRetro)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "$percent%", style = ptMono(26, PTWeight.bold), color = NC.textPrimary,
                // 눌릴 것 같지만 반응 없는 곳 (home.dead_click)
                modifier = Modifier.clickable(indication = null, interactionSource = null) {
                    HomeTracker.system(HomeEvent.deadClick, "dead_target" to "progress_percent", "section" to "summary", "view_date" to today)
                },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                ProgressBar(
                    value = if (total > 0) done.toDouble() / total.toDouble() else 0.0,
                    color = heroTone.accent,
                    track = NC.bgSurface.copy(alpha = 0.7f),
                    height = 6.dp,
                )
                Text(
                    countsLine(),
                    style = ptMono(11),
                    color = NC.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (dueToday > 0 || overdue > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (dueToday > 0) {
                    HomeSummaryPill("오늘 마감 $dueToday", bg = NC.statusWarningBg, fg = NC.statusWarningFg)
                }
                if (overdue > 0) {
                    HomeSummaryPill("마감 지남 $overdue", bg = NC.statusErrorBg, fg = NC.statusErrorFg)
                }
            }
        }

        if (isToday) HomeSummaryFocusBox(day = day, nowMinutes = nowMinutes, viewDate = today)
    }
}

/** 날짜 + ‹ › + (오늘이 아니면) "오늘" */
@Composable
private fun HomeSummaryDateRow(today: String, nav: HomeDayNav?) {
    val label = "${KST.monthOf(today)}월 ${KST.dayOfMonth(today)}일 ${homeWeekdayFull[KST.weekdayIndex(today)]}"
    if (nav != null) {
        Row(
            Modifier.offset(x = (-6).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            HomeSummaryNavButton("chevron.left", label = "이전 날", action = nav.onPrev)
            Text(label, style = pt(12, PTWeight.semibold), color = NC.textSecondary)
            HomeSummaryNavButton("chevron.right", label = "다음 날", action = nav.onNext)
            if (!nav.isToday) {
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .height(24.dp)
                        .clip(CircleShape)
                        .background(NC.bgSurface.copy(alpha = 0.7f), CircleShape)
                        .clickable(role = Role.Button, onClick = nav.onToday)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("오늘", style = pt(12, PTWeight.semibold), color = NC.textPrimary)
                }
            }
        }
    } else {
        Text(label, style = pt(12, PTWeight.semibold), color = NC.textSecondary)
    }
}

@Composable
private fun HomeSummaryNavButton(icon: String, label: String, action: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = action)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        SfImage(icon, size = 12.dp, tint = NC.textSecondary)
    }
}

@Composable
private fun HomeSummaryPill(text: String, bg: Color, fg: Color) {
    Text(
        text,
        style = ptMono(12, PTWeight.semibold),
        color = fg,
        modifier = Modifier
            .background(bg, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun HomeSummaryRetroButton(isToday: Boolean, retroLeft: Int, onRetro: () -> Unit) {
    Row(
        Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(NC.bgSurface.copy(alpha = 0.7f), CircleShape)
            .clickable(role = Role.Button, onClick = onRetro)
            .semantics { contentDescription = if (retroLeft > 0) "오늘 회고, 남은 목표 ${retroLeft}개" else "오늘 회고" }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        SfImage("square.and.pencil", size = 13.dp, tint = NC.textPrimary)
        Text(if (isToday) "오늘 회고" else "이날 회고", style = pt(13, PTWeight.semibold), color = NC.textPrimary)
        if (retroLeft > 0) {
            Box(
                Modifier
                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                    .background(NC.actionPrimary, CircleShape)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("$retroLeft", style = pt(10, PTWeight.bold), color = NC.actionPrimaryFg)
            }
        }
    }
}

/** 지금 할 일 / 다음 일정 — 오늘 시간이 정해진 Task 중 아직 안 끝낸 것 */
@Composable
private fun HomeSummaryFocusBox(day: TimetableDay?, nowMinutes: Int, viewDate: String) {
    val open = homeAgenda(day).filter { !it.task.isCompleted }
    val current = open.firstOrNull { it.start <= nowMinutes && nowMinutes < it.end }
    val next = open.firstOrNull { it.start > nowMinutes }
    val focus = current ?: next
    Column(
        Modifier
            .fillMaxWidth()
            .background(NC.bgSurface.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            // 눌러도 아무 일이 없어요 (home.dead_click)
            .clickable(indication = null, interactionSource = null) {
                HomeTracker.system(HomeEvent.deadClick, "dead_target" to "focus_box", "section" to "summary", "view_date" to viewDate)
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            if (current != null) "지금 할 일" else "다음 일정",
            style = pt(11, PTWeight.semibold),
            color = NC.textSecondary,
        )
        if (focus != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${KST.formatMinutes(focus.start)} ${homeTitle(focus.task.emoji, focus.task.taskName)}",
                    style = pt(14, PTWeight.bold),
                    color = NC.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (current == null) {
                    Text(
                        " · ${HomeDay.inLabel(focus.start - nowMinutes)}",
                        style = pt(13, PTWeight.medium),
                        color = NC.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        } else {
            Text("남은 일정이 없어요", style = pt(13, PTWeight.semibold), color = NC.textSecondary)
        }
    }
}

/** 웹 agendaOf 의 Task 부분 — 오늘 시작하는 시간이 정해진 Task */
private fun homeAgenda(day: TimetableDay?): List<HomeAgenda> {
    if (day == null) return emptyList()
    val out = mutableListOf<HomeAgenda>()
    for (t in HomeDay.tasksOfDay(day)) {
        val s = t.plannedStartAt ?: continue
        if (s.take(10) != day.date) continue
        out.add(HomeAgenda(start = HomeDay.startMinutes(day.date, t), end = HomeDay.endMinutes(day.date, t), task = t))
    }
    return out.sortedBy { it.start }
}

// MARK: - 할 일 · 루틴 줄

@Composable
private fun HomeTaskRow(task: TimeBlockTask, today: String, busy: Boolean, onToggle: () -> Unit, onSelect: () -> Unit) {
    val t = task
    val done = t.isCompleted
    Row(
        Modifier.padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeCheckCircle(
            done = done, label = t.taskName, disabled = busy || t.sourceType == TaskSourceType.MISSION,
            onDisabledTap = {
                HomeTracker.system(
                    HomeEvent.disabledClick,
                    "disabled_reason" to if (t.sourceType == TaskSourceType.MISSION) "mission_task" else "saving",
                    "task_id" to t.taskId, "source_type" to t.sourceType.rawValue,
                )
            },
            onToggle = onToggle,
        )
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSelect)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).background(goalColor(t.goalColorCode), CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    homeTitle(t.emoji, t.taskName),
                    style = pt(14, if (done) PTWeight.regular else PTWeight.medium)
                        .copy(textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None),
                    color = if (done) NC.ink3 else NC.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val g = t.goalCategoryName
                if (!g.isNullOrEmpty() && !t.goalTemporary) {
                    Text(g, style = pt(11), color = NC.ink4, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (t.sourceType == TaskSourceType.ROUTINE) {
                SfImage("repeat", size = 11.dp, tint = NC.ink4, contentDescription = "루틴")
            }
            val due = t.dueDate
            if (due != null && !done && due <= KST.addDays(today, 2)) {
                if (due < today) {
                    HomeTinyChip(text = KST.dDay(due, today), background = NC.dangerSoft, foreground = NC.danger)
                } else {
                    HomeTinyChip(text = "${KST.dDay(due, today)} 마감", background = NC.warningSoft, foreground = NC.warningInk)
                }
            }
            if (t.missed && !done) {
                HomeTinyChip(text = "놓침", background = NC.dangerSoft, foreground = NC.danger)
            }
            Text(
                if (t.plannedStartAt != null) KST.hm(t.plannedStartAt) else "미정",
                style = ptMono(12),
                color = NC.ink3,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.width(42.dp),
            )
        }
    }
}

// MARK: - 할 일

/** 오늘 할 일 — 루틴이 아닌 Task. 남은 일(시간순, 시간 미정은 아래) → 끝낸 일(접힘) */
@Composable
fun HomeTasksCard(
    today: String,
    tasks: List<TimeBlockTask>,
    togglingId: Int?,
    onToggle: (TimeBlockTask) -> Unit,
    onSelect: (TimeBlockTask) -> Unit,
    onAdd: () -> Unit,
    /** 홈에서 다른 날을 보고 있으면 "이날" */
    dayLabel: String = "오늘",
) {
    var showDone by remember { mutableStateOf(false) }
    val todo = tasks.filter { !it.isCompleted }
    val done = tasks.filter { it.isCompleted }

    Column(Modifier.homeCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeCardHeader("할 일", done = done.size, total = tasks.size) {
            Row(
                Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button, onClick = onAdd)
                    .semantics { contentDescription = "Task 추가" }
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SfImage("plus", size = 14.dp, tint = NC.textBrand)
                Text("추가", style = NType.bodySMMedium, color = NC.textBrand)
            }
        }

        if (tasks.isEmpty()) {
            Text("${dayLabel}로 정한 할 일이 없어요.", style = pt(14), color = NC.ink3, modifier = Modifier.padding(vertical = 8.dp))
            LaunchedEffect(today) { HomeTracker.emptyOnce("tasks", today) }
        } else {
            if (todo.isEmpty()) {
                Text(
                    "오늘 할 일을 모두 끝냈어요 🎉",
                    style = pt(14, PTWeight.semibold),
                    color = NC.success,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                Column {
                    for (t in todo) key(t.taskId) {
                        HomeTaskRow(
                            task = t, today = today, busy = togglingId == t.taskId,
                            onToggle = { onToggle(t) }, onSelect = { onSelect(t) },
                        )
                    }
                }
            }
            if (done.isNotEmpty()) {
                Column {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(NC.line))
                    val rotation by animateFloatAsState(if (showDone) 180f else 0f, tween(180), label = "doneChevron")
                    Row(
                        Modifier
                            .height(34.dp)
                            .clickable(role = Role.Button) {
                                HomeTracker.track(HomeEvent.taskDoneToggleClick, "expanded" to !showDone, "done_count" to done.size)
                                showDone = !showDone
                            }
                            .semantics { contentDescription = "끝낸 일 ${done.size}, ${if (showDone) "펼침" else "접힘"}" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("끝낸 일 ${done.size}", style = pt(13, PTWeight.semibold), color = NC.success)
                        SfImage("chevron.down", size = 11.dp, tint = NC.success, modifier = Modifier.rotate(rotation))
                    }
                    AnimatedVisibility(
                        visible = showDone,
                        enter = expandVertically(tween(180)) + fadeIn(tween(180)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(180)),
                    ) {
                        Column {
                    for (t in done) key(t.taskId) {
                        HomeTaskRow(
                            task = t, today = today, busy = togglingId == t.taskId,
                            onToggle = { onToggle(t) }, onSelect = { onSelect(t) },
                        )
                    }
                }
                    }
                }
            }
        }
    }
}

// MARK: - 루틴 · 습관

/** 오늘 루틴 — 목표에서 반복으로 만든 루틴 Task + 기본 생활 습관 체크 */
@Composable
fun HomeRoutinesCard(
    today: String,
    routines: List<TimeBlockTask>,
    /** ACTIVE 이고 오늘 하는 날인 습관 */
    habits: List<Habit>,
    habitHome: HabitHome?,
    togglingId: Int?,
    onToggleTask: (TimeBlockTask) -> Unit,
    onSelectTask: (TimeBlockTask) -> Unit,
    onToggleHabit: (Habit) -> Unit,
    onOpenHabits: () -> Unit,
    /** 홈에서 다른 날을 보고 있으면 "이날" */
    dayLabel: String = "오늘",
) {
    val total = routines.size + habits.size
    val done = routines.count { it.isCompleted } + habits.count { it.checked }
    // 안 한 것 먼저
    val sortedRoutines = routines.filter { !it.isCompleted } + routines.filter { it.isCompleted }
    val sortedHabits = habits.filter { !it.checked } + habits.filter { it.checked }

    Column(Modifier.homeCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeCardHeader("루틴 · 습관", done = done, total = total)

        if (total == 0) {
            Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$dayLabel 할 루틴이 없어요.", style = pt(14), color = NC.ink3)
                LaunchedEffect(today) { HomeTracker.emptyOnce("routines", today) }
                Text(
                    "생활 습관 추가하기",
                    style = pt(13, PTWeight.semibold),
                    color = NC.textBrand,
                    modifier = Modifier.clickable(role = Role.Button) {
                        HomeTracker.track(HomeEvent.habitAddClick, "view_date" to today)
                        onOpenHabits()
                    },
                )
            }
        } else {
            if (sortedRoutines.isNotEmpty()) {
                Column {
                    Box(Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
                        Text("목표 루틴", style = pt(12, PTWeight.semibold), color = NC.ink3)
                    }
                    for (t in sortedRoutines) key(t.taskId) {
                        HomeTaskRow(
                            task = t, today = today, busy = togglingId == t.taskId,
                            onToggle = { onToggleTask(t) }, onSelect = { onSelectTask(t) },
                        )
                    }
                }
            }
            if (sortedHabits.isNotEmpty()) {
                Column {
                    Row(
                        Modifier
                            .height(24.dp)
                            .clickable(role = Role.Button) {
                                HomeTracker.track(HomeEvent.habitGroupLinkClick, "goal_category_id" to habitHome?.goalCategoryId)
                                onOpenHabits()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("${habitHome?.emoji ?: "🌱"} 생활 습관", style = pt(12, PTWeight.semibold), color = NC.ink3)
                        SfImage("chevron.right", size = 9.dp, tint = NC.ink3)
                    }
                    for (h in sortedHabits) key(h.habitId) { HomeHabitRow(h = h, onToggle = { onToggleHabit(h) }) }
                }
            }
        }
    }
}

@Composable
private fun HomeHabitRow(h: Habit, onToggle: () -> Unit) {
    Row(
        Modifier.padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeCheckCircle(done = h.checked, label = h.name, onToggle = onToggle)
        Text(
            homeTitle(h.emoji, h.name),
            style = pt(14).copy(textDecoration = if (h.checked) TextDecoration.LineThrough else TextDecoration.None),
            color = if (h.checked) NC.ink3 else NC.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (h.streak > 1) {
            val fire = NC.statusWarningFg
            Row(
                Modifier.semantics { contentDescription = "🔥 ${h.streak}일 연속" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                SfImage("flame.fill", size = 10.dp, tint = fire)
                Text("${h.streak}", style = ptMono(11, PTWeight.semibold), color = fire)
            }
        }
    }
}

// MARK: - 다가오는 일정

/** 날짜별 묶음 (iOS HomeUpcomingCard.DayGroup) */
private data class HomeUpcomingGroup(val date: String, val items: List<TimeBlockTask>)

/** 다가오는 일정 — 내일부터 6일 동안 할 일 (루틴은 매일 반복이라 빼요). 약속은 강조해요 */
@Composable
fun HomeUpcomingCard(days: List<TimetableDay>, today: String, onOpenDay: (String) -> Unit) {
    val gs = days.map { d ->
        HomeUpcomingGroup(
            date = d.date,
            items = HomeDay.tasksOfDay(d)
                .filter { !HomeDay.isRoutine(it) && it.taskStatus == TaskStatus.TODO }
                .sortedBy { HomeDay.startMinutes(d.date, it) },
        )
    }.filter { it.items.isNotEmpty() }

    Column(Modifier.homeCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CardHeader("다가오는 일정", aside = "앞으로 6일")
        if (gs.isEmpty()) {
            Text("이번 주에 잡힌 일정이 없어요.", style = pt(14), color = NC.ink3, modifier = Modifier.padding(vertical = 8.dp))
            LaunchedEffect(today) { HomeTracker.emptyOnce("upcoming", today) }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (g in gs) key(g.date) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            Modifier
                                .height(24.dp)
                                .clickable(role = Role.Button) {
                                    HomeTracker.track(
                                        HomeEvent.upcomingDayClick,
                                        "target_date" to g.date, "days_from_today" to KST.diffDays(KST.today(), g.date),
                                        "item_count" to g.items.size,
                                    )
                                    onOpenDay(g.date)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(WorkFormat.dayLabel(g.date, today), style = pt(13, PTWeight.bold), color = NC.ink2)
                            Text("${g.items.size}개", style = pt(13, PTWeight.semibold), color = NC.ink4)
                        }
                        for (t in g.items) key(t.taskId) { HomeScheduleRow(t, today) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScheduleRow(t: TimeBlockTask, today: String) {
    val appointment = t.taskKind == TaskKind.APPOINTMENT
    Row(
        Modifier
            .defaultMinSize(minHeight = 28.dp)
            // 다가오는 일정의 Task 줄은 눌러도 아무 일이 없어요 (home.dead_click)
            .clickable(indication = null, interactionSource = null) {
                HomeTracker.system(HomeEvent.deadClick, "dead_target" to "upcoming_item", "section" to "upcoming", "view_date" to today)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (t.plannedStartAt != null) KST.hm(t.plannedStartAt) else "미정",
            style = ptMono(12),
            color = NC.ink3,
            modifier = Modifier.width(44.dp),
        )
        Box(Modifier.size(8.dp).background(goalColor(t.goalColorCode), CircleShape))
        Text(
            homeTitle(t.emoji, t.taskName),
            style = pt(14, if (appointment) PTWeight.semibold else PTWeight.regular),
            color = NC.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (appointment) NBadge("약속", tone = BadgeTone.brand)
    }
}

// MARK: - 마감 로드맵

/** 로드맵 한 줄: "오늘" 선 또는 마감 하나 (iOS HomeRoadmapCard.Row) */
private sealed class HomeRoadmapRow {
    abstract val id: String

    object today : HomeRoadmapRow() {
        override val id: String get() = "today"
    }

    data class item(val d: HomeDeadline, val showDate: Boolean) : HomeRoadmapRow() {
        override val id: String get() = d.id
    }
}

/** 날짜 칸 52 + 2 + 점 칸 28 의 가운데 */
private val roadmapDateWidth = 52.dp
private val roadmapNodeWidth = 28.dp
private val roadmapLineX = roadmapDateWidth + 2.dp + roadmapNodeWidth / 2 - 1.dp

private fun roadmapRows(list: List<HomeDeadline>): List<HomeRoadmapRow> {
    val todayAt = list.indexOfFirst { !it.overdue }.let { if (it < 0) list.size else it }
    val out = mutableListOf<HomeRoadmapRow>()
    list.forEachIndexed { i, d ->
        if (i == todayAt) out.add(HomeRoadmapRow.today)
        val newDate = i == 0 || list[i - 1].date != d.date
        out.add(HomeRoadmapRow.item(d, showDate = newDate))
    }
    if (todayAt == list.size) out.add(HomeRoadmapRow.today)
    return out
}

/**
 * 마감 로드맵 — 모든 목표의 마감을 날짜 순으로 세로 선 위에 (업무형 보드의 로드맵과 같은 모양)
 *  - Task 마감(앞으로 2주) · 업무형 주제 마감 · 목표형 종료일(앞으로 1년)
 *  - 마감이 지난 것(최근 7일)은 맨 위에 빨갛게, 그다음 "오늘" 줄
 */
@Composable
fun HomeRoadmapCard(
    /** null = 아직 불러오는 중 */
    deadlines: List<HomeDeadline>?,
    failed: Boolean,
    today: String,
    onOpenGoal: (Int) -> Unit,
    onOpenDay: (String) -> Unit,
) {
    Column(Modifier.homeCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("마감 로드맵", style = NType.h4, color = NC.ink)
            Spacer(Modifier.weight(1f))
            if (deadlines != null && deadlines.isNotEmpty() && !failed) {
                Text("${deadlines.count { !it.overdue }}개 남음", style = pt(13), color = NC.ink3)
            }
        }

        if (failed) {
            Text("마감을 불러오지 못했어요.", style = pt(14), color = NC.ink3, modifier = Modifier.padding(vertical = 8.dp))
        } else if (deadlines != null) {
            if (deadlines.isEmpty()) {
                Text(
                    "다가오는 마감이 없어요. 목표형 종료일 · 업무형 세부 목표 마감 · Task 마감이 여기 이어져요.",
                    style = pt(14), color = NC.ink3, modifier = Modifier.padding(vertical = 8.dp),
                )
                LaunchedEffect(today) { HomeTracker.emptyOnce("deadlines", today) }
            } else {
                HomeRoadmapTimeline(list = deadlines, today = today, onOpenGoal = onOpenGoal, onOpenDay = onOpenDay)
                HomeRoadmapLegend()
            }
        } else {
            Box(Modifier.fillMaxWidth().height(120.dp).background(NC.subtle, RoundedCornerShape(Radius.xl)))
        }
    }
}

@Composable
private fun HomeRoadmapTimeline(list: List<HomeDeadline>, today: String, onOpenGoal: (Int) -> Unit, onOpenDay: (String) -> Unit) {
    // 색은 그리기 블록(drawBehind) 밖에서 꺼내 둬요
    val lineColor = NC.line
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                // 세로 선
                val x = roadmapLineX.toPx()
                val top = 10.dp.toPx()
                drawRect(
                    color = lineColor,
                    topLeft = Offset(x, top),
                    size = Size(2.dp.toPx(), (size.height - top * 2).coerceAtLeast(0f)),
                )
            },
    ) {
        for (r in roadmapRows(list)) key(r.id) {
            when (r) {
                is HomeRoadmapRow.today -> HomeRoadmapTodayLine()
                is HomeRoadmapRow.item -> HomeRoadmapItemRow(
                    d = r.d, showDate = r.showDate, today = today,
                    onClick = {
                        val gid = r.d.goalCategoryId
                        HomeTracker.track(
                            HomeEvent.deadlineItemClick,
                            "deadline_type" to r.d.type.name, "d_day" to r.d.dDay, "is_overdue" to r.d.overdue,
                            "goal_category_id" to gid, "destination" to if (gid != null) "goal" else "calendar",
                        )
                        if (gid != null) onOpenGoal(gid) else onOpenDay(r.d.date)
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeRoadmapTodayLine() {
    Row(
        Modifier
            .padding(vertical = 6.dp)
            .semantics { contentDescription = "오늘" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "오늘",
            style = pt(12, PTWeight.bold),
            color = NC.ink,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(roadmapDateWidth),
        )
        Box(Modifier.padding(start = 2.dp).width(roadmapNodeWidth), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).background(NC.ink, CircleShape))
        }
        Box(
            Modifier
                .padding(start = 10.dp)
                .weight(1f)
                .height(1.dp)
                .background(NC.ink.copy(alpha = 0.4f)),
        )
    }
}

@Composable
private fun HomeRoadmapItemRow(d: HomeDeadline, showDate: Boolean, today: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            Modifier.width(roadmapDateWidth).padding(top = 1.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            if (showDate) {
                Text(
                    "${KST.monthOf(d.date)}/${KST.dayOfMonth(d.date)}",
                    style = ptMono(13, PTWeight.bold),
                    color = if (d.overdue) NC.danger else NC.ink,
                )
                Text(roadmapWeekdayText(d.date, today), style = pt(11), color = NC.ink4)
            }
        }

        Box(
            Modifier.padding(start = 2.dp).width(roadmapNodeWidth).height(22.dp),
            contentAlignment = Alignment.Center,
        ) {
            HomeRoadmapNode(d)
        }

        Column(
            Modifier.padding(start = 10.dp).weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                homeTitle(d.emoji, d.title),
                style = pt(14, if (d.type == HomeDeadlineType.GOAL) PTWeight.bold else PTWeight.semibold),
                color = NC.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(roadmapSubtitle(d), style = pt(12), color = NC.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Box(Modifier.padding(start = 8.dp, top = 1.dp)) { HomeRoadmapDDayChip(d) }
    }
}

/** 해가 바뀌는 마감은 "27년 · 금"처럼 해를 같이 적어요 */
private fun roadmapWeekdayText(ymd: String, today: String): String {
    val w = KST.weekdayLabel[KST.weekdayIndex(ymd)]
    if (KST.yearOf(ymd) != KST.yearOf(today)) {
        return "${ymd.take(4).takeLast(2)}년 · $w"
    }
    return w
}

private fun roadmapSubtitle(d: HomeDeadline): String {
    var s = d.type.label
    val g = d.goalName
    if (d.type != HomeDeadlineType.GOAL && !g.isNullOrEmpty()) s += " · $g"
    val r = d.remaining
    if (d.type == HomeDeadlineType.TOPIC && r != null && r > 0) s += " · 남은 ${r}개"
    return s
}

/** 점 — Task 는 작은 점, 업무형 주제는 테두리 점, 목표 종료는 깃발 */
@Composable
private fun HomeRoadmapNode(d: HomeDeadline) {
    val color = goalColor(d.colorCode)
    when (d.type) {
        HomeDeadlineType.GOAL -> Box(
            Modifier.offset(y = (-2).dp).size(26.dp).background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SfImage("flag.fill", size = 12.dp, tint = Color.White)
        }
        HomeDeadlineType.TOPIC -> Box(
            Modifier
                .size(18.dp)
                .background(NC.surface, CircleShape)
                .border(3.dp, if (d.overdue) NC.danger else color, CircleShape),
        )
        HomeDeadlineType.TASK -> Box(
            Modifier.size(20.dp).background(NC.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(12.dp).background(if (d.overdue) NC.danger else color, CircleShape))
        }
    }
}

@Composable
private fun HomeRoadmapDDayChip(d: HomeDeadline) {
    val bg: Color
    val fg: Color
    if (d.overdue) {
        bg = NC.dangerSoft
        fg = NC.danger
    } else if (d.dDay <= 2) {
        bg = NC.warningSoft
        fg = NC.warningInk
    } else {
        bg = NC.subtle
        fg = NC.ink2
    }
    Text(
        d.dDayText,
        style = ptMono(11, PTWeight.bold),
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun HomeRoadmapLegend() {
    val c = NC.ink4
    Row(
        Modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(8.dp).background(NC.ink3, CircleShape))
            Text("Task", style = pt(11), color = c)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(10.dp).border(2.dp, NC.ink3, CircleShape))
            Text("업무형 세부 목표", style = pt(11), color = c)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SfImage("flag.fill", size = 10.dp, tint = c)
            Text("목표 종료일", style = pt(11), color = c)
        }
    }
}

// MARK: - 미리보기 샘플

/** 미리보기 전용 홈 샘플 (백엔드와 같은 JSON 을 디코딩해요) */
object HomeSampleData {
    fun homeToday(today: String): HomeToday {
        val d = { n: Int -> KST.addDays(today, n) }
        val json = """
        {
          "today": "$today",
          "deadlines": [
            {"type": "TASK", "id": 11, "title": "견적서 보내기", "emoji": "📨", "date": "${d(-2)}", "dDay": -2, "overdue": true,
             "goalCategoryId": 2, "goalName": "커리어", "colorCode": "F59E0B", "goalMode": "RECORD"},
            {"type": "TOPIC", "id": 5, "title": "2분기 리포트", "date": "$today", "dDay": 0, "overdue": false,
             "goalCategoryId": 2, "goalName": "커리어", "colorCode": "F59E0B", "goalMode": "RECORD", "remaining": 3},
            {"type": "TASK", "id": 12, "title": "모의고사 신청", "date": "${d(3)}", "dDay": 3, "overdue": false,
             "goalCategoryId": 3, "goalName": "토익 900", "colorCode": "3D5AFE", "goalMode": "ACHIEVEMENT"},
            {"type": "TASK", "id": 13, "title": "단어장 2권 끝내기", "date": "${d(3)}", "dDay": 3, "overdue": false,
             "goalCategoryId": 3, "goalName": "토익 900", "colorCode": "3D5AFE"},
            {"type": "GOAL", "id": 3, "title": "토익 900", "emoji": "📚", "date": "${d(40)}", "dDay": 40, "overdue": false,
             "goalCategoryId": 3, "goalName": "토익 900", "colorCode": "3D5AFE", "goalMode": "ACHIEVEMENT"}
          ],
          "goals": [
            {"goalCategoryId": 3, "goalName": "토익 900", "emoji": "📚", "colorCode": "3D5AFE", "goalMode": "ACHIEVEMENT",
             "activeToday": true, "planned": 2, "done": 1, "doneTasks": ["LC 파트 2 풀기"], "openTasks": ["영어 단어 외우기"],
             "retros": [], "checkIn": {"hasMetric": true, "metricName": "모의고사 점수", "unit": "점", "currentValue": 820}},
            {"goalCategoryId": 2, "goalName": "커리어", "emoji": "💼", "colorCode": "F59E0B", "goalMode": "RECORD",
             "activeToday": true, "planned": 1, "done": 0, "doneTasks": [], "openTasks": ["보고서 초안 쓰기"],
             "retros": [{"retrospectiveId": 1, "reviewType": "DAILY", "content": "회의가 길어져서 초안을 못 썼다. 내일 오전에 먼저 하기."}]},
            {"goalCategoryId": 1, "goalName": "건강", "emoji": "🏃", "colorCode": "12B76A", "goalMode": "ACHIEVEMENT",
             "activeToday": true, "planned": 1, "done": 1, "doneTasks": ["아침 스트레칭"], "openTasks": [], "retros": [],
             "checkIn": {"hasMetric": true, "metricName": "체중", "unit": "kg", "currentValue": 64.2, "value": 64.0, "mood": "OK", "note": "점심 마라탕, 저녁은 샐러드"}},
            {"goalCategoryId": 4, "goalName": "독서", "emoji": "📖", "colorCode": "8B5CF6", "goalMode": "ACHIEVEMENT",
             "activeToday": false, "planned": 0, "done": 0, "doneTasks": [], "openTasks": [], "retros": [],
             "checkIn": {"hasMetric": false}}
          ]
        }
        """
        return NJson.decodeFromString(HomeToday.serializer(), json)
    }

    /** 습관 샘플 — Habit 모델(습관 기능)이 바뀌어 디코딩이 안 되면 null (미리보기에서 습관만 빠져요) */
    fun habitHome(today: String): HabitHome? {
        val json = """
        {
          "goalCategoryId": 10, "goalName": "생활 습관", "emoji": "🌱", "date": "$today", "today": "$today",
          "scheduledCount": 3, "doneCount": 1,
          "habits": [
            {"habitId": 1, "name": "영양제 먹기", "emoji": "💊", "repeatDays": [], "daily": true, "status": "ACTIVE", "startDate": "$today",
             "sortOrder": 0, "scheduled": true, "checked": true, "state": "DONE", "streak": 6, "recent7": []},
            {"habitId": 2, "name": "물 2L 마시기", "emoji": "💧", "repeatDays": [], "daily": true, "status": "ACTIVE", "startDate": "$today",
             "sortOrder": 1, "scheduled": true, "checked": false, "state": "TODO", "streak": 0, "recent7": []},
            {"habitId": 3, "name": "일기 쓰기", "emoji": "📓", "repeatDays": [], "daily": true, "status": "ACTIVE", "startDate": "$today",
             "sortOrder": 2, "scheduled": true, "checked": false, "state": "TODO", "streak": 2, "recent7": []},
            {"habitId": 4, "name": "분리수거", "emoji": "🗑️", "repeatDays": ["WEDNESDAY"], "daily": false, "status": "ACTIVE", "startDate": "$today",
             "sortOrder": 3, "scheduled": false, "checked": false, "state": "OFF", "streak": 0, "recent7": []}
          ]
        }
        """
        return runCatching { NJson.decodeFromString(kotlinx.serialization.serializer<HabitHome>(), json) }.getOrNull()
    }

    fun days(today: String): List<TimetableDay> = TimetableSampleData.days(today, KST.addDays(today, 6))
}

// MARK: - 미리보기

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun HomeSummaryCardPreview() {
    NaleulTheme(dark = false) {
        val today = KST.today()
        val days = HomeSampleData.days(today)
        val all = HomeDay.tasksOfDay(days.firstOrNull())
        Column(
            Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HomeSummaryCard(
                today = today, nowMinutes = 8 * 60 + 20, userName = "유림", day = days.firstOrNull(),
                tasks = all.filter { !HomeDay.isRoutine(it) }, routines = all.filter { HomeDay.isRoutine(it) },
                habitDone = 1, habitTotal = 3, dueToday = 1, overdue = 1, retroLeft = 2,
                showRetro = true, onRetro = {},
                nav = HomeDayNav(isToday = true, isPast = false, onPrev = {}, onNext = {}, onToday = {}),
            )
            HomeSummaryCard(
                today = today, nowMinutes = 600, userName = "나를 사용자", day = null,
                tasks = emptyList(), routines = emptyList(), habitDone = 0, habitTotal = 0, dueToday = 0, overdue = 0,
                retroLeft = 0, showRetro = false, onRetro = {},
            )
            // 지난날을 보고 있을 때 (날짜 ‹ › + "오늘")
            HomeSummaryCard(
                today = KST.addDays(today, -1), nowMinutes = 600, userName = "유림", day = days.firstOrNull(),
                tasks = all.filter { !HomeDay.isRoutine(it) }, routines = all.filter { HomeDay.isRoutine(it) },
                habitDone = 1, habitTotal = 3, dueToday = 0, overdue = 0, retroLeft = 1,
                showRetro = true, onRetro = {},
                nav = HomeDayNav(isToday = false, isPast = true, onPrev = {}, onNext = {}, onToday = {}),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun HomeTasksRoutinesPreview() {
    NaleulTheme(dark = false) {
        val today = KST.today()
        val days = HomeSampleData.days(today)
        val all = HomeDay.tasksOfDay(days.firstOrNull())
        val hh = HomeSampleData.habitHome(today)
        Column(
            Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HomeTasksCard(
                today = today, tasks = all.filter { !HomeDay.isRoutine(it) }, togglingId = null,
                onToggle = {}, onSelect = {}, onAdd = {},
            )
            HomeRoutinesCard(
                today = today, routines = all.filter { HomeDay.isRoutine(it) },
                habits = hh?.habits?.filter { it.scheduled } ?: emptyList(), habitHome = hh, togglingId = null,
                onToggleTask = {}, onSelectTask = {}, onToggleHabit = {}, onOpenHabits = {},
            )
            HomeRoutinesCard(
                today = today, routines = emptyList(), habits = emptyList(), habitHome = null, togglingId = null,
                onToggleTask = {}, onSelectTask = {}, onToggleHabit = {}, onOpenHabits = {},
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun HomeUpcomingRoadmapPreview() {
    NaleulTheme(dark = false) {
        val today = KST.today()
        val days = HomeSampleData.days(today)
        Column(
            Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HomeUpcomingCard(days = days.drop(1), today = today, onOpenDay = {})
            HomeRoadmapCard(
                deadlines = HomeSampleData.homeToday(today).deadlines, failed = false, today = today,
                onOpenGoal = {}, onOpenDay = {},
            )
            HomeRoadmapCard(deadlines = emptyList(), failed = false, today = today, onOpenGoal = {}, onOpenDay = {})
            HomeRoadmapCard(deadlines = null, failed = true, today = today, onOpenGoal = {}, onOpenDay = {})
        }
    }
}
