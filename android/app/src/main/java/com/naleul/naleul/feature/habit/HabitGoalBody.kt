// feature/habit/HabitGoalBody.kt
//
// iOS Features/Habit/HabitGoalBody.swift
// 웹 features/habit/ui/HabitView.tsx — 기본 생활 습관 (goalMode = HABIT) 목표 상세.
// GoalDetailScreen 이 머리 카드 + 탭 대신 이 화면을 보여줘요 (스크롤은 GoalDetailScreen 의 세로 스크롤).
// ⚠️ 바깥이 이미 세로 스크롤이라 여기서는 LazyColumn 을 안 써요.
//
//  - 체크 카드: 날짜(지난 날도 체크할 수 있어요) + 몇 개 했는지 + 그날 할 습관 · 안 해도 되는 습관 · 쉬는 습관
//  - 습관별 잔디 (한 달씩)
//  - 일회성 할 일 (병원 가기 · 친구 만나기) — 습관과 달리 한 번 하고 끝나요 (GoalTaskListSection 재사용)

package com.naleul.naleul.feature.habit

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

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 습관 시트: 새로 추가 · 수정 (iOS enum HabitFormTarget { case create, case edit(Habit) }) */
sealed class HabitFormTarget {
    object create : HabitFormTarget()
    data class edit(val habit: Habit) : HabitFormTarget()

    val id: String
        get() = when (this) {
            is create -> "new"
            is edit -> "habit:${habit.habitId}"
        }

    /** 수정이면 그 습관 */
    val habitOrNull: Habit?
        get() = (this as? edit)?.habit

    companion object {}
}

/** iOS HabitFormTarget.habit (edit 이면 습관, create 면 nil) — edit 의 프로퍼티 이름과 겹쳐서 확장으로 */
val HabitFormTarget.habit: Habit? get() = habitOrNull

/**
 * 기본 생활 습관 목표 상세 본문.
 * @param previewModel 미리보기용 (iOS init(goal:previewModel:))
 */
/** 기본 생활 습관 탭 (앰플리튜드 tab 값: today · progress · tasks) */
private enum class HabitTab { today, progress, tasks }

@Composable
fun HabitGoalBody(goal: GoalCategory, previewModel: HabitModel? = null, modifier: Modifier = Modifier) {
    val model = rememberScreenModel(goal.goalCategoryId) { previewModel ?: HabitModel() }
    var showOff by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<HabitFormTarget?>(null) }
    var tab by remember { mutableStateOf(HabitTab.today) }
    val detailVm = LocalGoalDetailViewModel.current

    val titleText = goal.emoji?.takeIf { it.isNotEmpty() }?.let { "$it ${goal.goalCategoryName}" } ?: goal.goalCategoryName

    ReloadOn(listOf(DataKey.habits), id = model.day) { model.load(day = model.day) }

    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            Modifier.padding(top = 8.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(titleText, style = NType.h2, color = NC.textPrimary)
            Text(
                "했는지만 체크하는 생활 습관과, 병원 가기처럼 한 번만 할 일을 여기서 챙겨요.",
                style = pt(14),
                color = NC.ink3,
            )
        }

        // 스펙 12.5: 오늘 · 할 일 · 진행 탭 (처음 = 오늘)
        UnderlineTabs(
            selection = tab,
            onSelectionChange = {
                detailVm?.track("goal_detail.tab_click", mapOf(
                    "goal_mode" to goal.analyticsMode, "tab" to it.name, "from_tab" to tab.name,
                ))
                tab = it
            },
            // 2026-10: "오늘" → "루틴" (값은 분석 연속성 때문에 today 그대로)
            items = listOf(HabitTab.today to "루틴", HabitTab.tasks to "할 일", HabitTab.progress to "진행"),
        )

        if (tab == HabitTab.today) {
            HabitCheckCard(model = model, showOff = showOff, onShowOffChange = { showOff = it }) { sheet = it }
        }

        if (tab == HabitTab.progress) {
            if (model.home != null) HabitGrassSection(model = model) else NotEnoughView()
        }

        if (tab == HabitTab.tasks) GoalTaskListSection(
            goal = goal,
            title = "할 일",
            hint = "병원 가기 · 친구 만나기처럼 한 번만 할 일이에요. 날짜만 정하면 캘린더 빈 시간에 넣어 드려요.",
            addLabel = "할 일 추가",
            emptyText = "아직 할 일이 없어요. 위에서 바로 추가해 보세요.",
            initialPeriod = GoalTaskPeriodFilter.all,
        )
    }

    sheet?.let { target ->
        key(target.id) {
            HabitFormSheet(habit = target.habitOrNull, existingNames = model.habits.map { it.name }, onDone = { sheet = null })
        }
    }
}

// MARK: - 체크 카드 (날짜 · 진행 · 습관 목록 · 추가)

@Composable
private fun HabitCheckCard(
    model: HabitModel,
    showOff: Boolean,
    onShowOffChange: (Boolean) -> Unit,
    open: (HabitFormTarget) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val today = model.today

    /** "오늘" · "어제" · "10월 11일 (토)" */
    fun dayTitle(ymd: String): String {
        if (ymd == today) return "오늘"
        if (ymd == KST.addDays(today, -1)) return "어제"
        return KST.formatMonthDay(ymd)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .card()
            .padding(16.dp),
    ) {
        // ── 날짜 + 진행 ──
        val isToday = model.isToday
        val due = model.due
        val done = model.doneCount
        val ratio: Double = if (due.isEmpty()) 0.0 else done.toDouble() / due.size.toDouble()
        val loaded = model.home != null
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.workNegativePadding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                NIconButton("chevron.left", size = 17.dp, color = NC.ink3, label = "전날") {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    model.day = KST.addDays(model.day, -1)
                    model.track("habit.date_change", mapOf("date_action" to "prev", "days_ago" to model.daysAgo))
                }
                Text(
                    dayTitle(model.day),
                    style = NType.h4,
                    color = NC.ink,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 64.dp),
                )
                NIconButton(
                    "chevron.right", size = 17.dp, color = if (isToday) NC.ink4 else NC.ink3, label = "다음 날",
                    enabled = !isToday,
                ) {
                    if (!isToday) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val next = KST.addDays(model.day, 1)
                        model.day = if (next > today) today else next
                        model.track("habit.date_change", mapOf("date_action" to "next", "days_ago" to model.daysAgo))
                    }
                }
                if (!isToday) {
                    Text(
                        "오늘로",
                        style = pt(13, PTWeight.semibold),
                        color = NC.textBrand,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clickable(role = Role.Button) {
                                model.day = today
                                model.track("habit.date_change", mapOf("date_action" to "today", "days_ago" to 0))
                            },
                    )
                }
                Spacer(Modifier.weight(1f))
                if (loaded) {
                    val ink = NC.ink
                    val ink3 = NC.ink3
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = ink)) { append("$done") }
                            withStyle(SpanStyle(color = ink3)) { append(" / ${due.size}") }
                        },
                        style = ptMono(15, PTWeight.bold),
                    )
                }
            }
            ProgressBar(value = ratio, height = 6.dp)
            if (loaded && due.isNotEmpty() && done == due.size) {
                Text(
                    if (isToday) "오늘 할 습관을 모두 챙겼어요 🎉" else "이날 할 습관을 모두 챙겼어요",
                    style = pt(13, PTWeight.semibold),
                    color = NC.success,
                )
            }
        }

        when (val phase = model.phase) {
            is LoadPhase.Loading -> {
                Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NC.ink3, strokeWidth = 2.5.dp)
                }
            }
            is LoadPhase.Failed -> {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("습관을 불러오지 못했어요", style = pt(16, PTWeight.bold), color = NC.ink)
                    Text(phase.message, style = pt(14), color = NC.ink3, textAlign = TextAlign.Center)
                    NButton("다시 시도", size = NButtonSize.sm, modifier = Modifier.padding(top = 10.dp)) {
                        model.track("habit.retry_click", mapOf("days_ago" to model.daysAgo))
                        val d = model.day
                        scope.launch { model.load(day = d) }
                    }
                }
            }
            is LoadPhase.Loaded -> {
                val home = phase.value
                HabitCheckList(
                    model = model,
                    home = home,
                    showOff = showOff,
                    onShowOffChange = onShowOffChange,
                    dayTitle = { dayTitle(it) },
                    onToggle = { h ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch { model.toggle(h) }
                    },
                    onEdit = { h ->
                        model.track("habit.edit_open", mapOf("habit_id" to h.habitId))
                        open(HabitFormTarget.edit(h))
                    },
                )
                HabitAddArea(
                    model = model,
                    existing = home.habits,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    model.track("habit.add_click", mapOf("habit_count" to home.habits.size))
                    open(HabitFormTarget.create)
                }
            }
        }
    }
}

// ── 습관 목록 ──

@Composable
private fun HabitCheckList(
    model: HabitModel,
    home: HabitHome,
    showOff: Boolean,
    onShowOffChange: (Boolean) -> Unit,
    dayTitle: (String) -> String,
    onToggle: (Habit) -> Unit,
    onEdit: (Habit) -> Unit,
) {
    if (home.habits.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("아직 습관이 없어요", style = pt(16, PTWeight.bold), color = NC.ink)
            Text(
                "영양제 · 물 마시기처럼 매일 챙길 것을 아래에서 골라 보세요.",
                style = pt(14),
                color = NC.ink3,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    val due = model.due
    val off = model.off
    val paused = model.paused
    if (due.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            Text("${dayTitle(model.day)}은 해야 하는 습관이 없어요.", style = pt(14), color = NC.ink3)
        }
    } else {
        HabitRows(habits = due, onToggle = onToggle, onEdit = onEdit, modifier = Modifier.padding(top = 6.dp))
    }

    if (off.isNotEmpty()) {
        Column(Modifier.fillMaxWidth()) {
            HabitListDivider()
            Row(
                Modifier
                    .height(36.dp)
                    .clickable(role = Role.Button) {
                        onShowOffChange(!showOff)
                        model.track("habit.off_list_toggle", mapOf(
                            "days_ago" to model.daysAgo, "expanded" to !showOff, "item_count" to off.size,
                        ))
                    }
                    .semantics { selected = showOff },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("${dayTitle(model.day)}은 안 해도 되는 습관 ${off.size}", style = pt(13, PTWeight.semibold), color = NC.ink3)
                SfImage("chevron.down", size = 11.dp, tint = NC.ink3, modifier = Modifier.rotate(if (showOff) 180f else 0f))
            }
            if (showOff) HabitRows(habits = off, onToggle = onToggle, onEdit = onEdit)
        }
    }

    if (paused.isNotEmpty()) {
        Column(Modifier.fillMaxWidth()) {
            HabitListDivider()
            Box(Modifier.height(36.dp), contentAlignment = Alignment.CenterStart) {
                Text("쉬는 중 ${paused.size}", style = pt(13, PTWeight.semibold), color = NC.ink3)
            }
            HabitRows(habits = paused, onToggle = onToggle, onEdit = onEdit)
        }
    }
}

@Composable
private fun HabitListDivider() {
    Box(
        Modifier
            .padding(top = 8.dp, bottom = 4.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(NC.line),
    )
}

@Composable
private fun HabitRows(
    habits: List<Habit>,
    onToggle: (Habit) -> Unit,
    onEdit: (Habit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        habits.forEachIndexed { i, h ->
            key(h.habitId) {
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(NC.line))
                HabitCheckRow(habit = h, onToggle = { onToggle(h) }, onEdit = { onEdit(h) })
            }
        }
    }
}

// MARK: - 습관 한 줄

@Composable
private fun HabitCheckRow(habit: Habit, onToggle: () -> Unit, onEdit: () -> Unit) {
    val checked = habit.checked
    val paused = habit.isPaused
    val meta: String = buildList {
        add(HabitFormat.daysLabel(habit))
        habit.timeRangeLabel?.let { add("🕘$it") }
        val r = habit.remindTime
        if (!r.isNullOrEmpty()) add("🔔" + r.take(5))
        habit.rate30?.let { add("30일 $it%") }
    }.joinToString(" · ")

    val fill by animateColorAsState(if (checked) NC.actionPrimary else Color.Transparent, label = "habitCheckFill")
    val stroke by animateColorAsState(if (checked) NC.actionPrimary else NC.borderControl, label = "habitCheckStroke")
    val checkLabel = "${habit.name} ${if (checked) "체크 풀기" else "했어요"}"
    val editLabel = "${habit.name} 수정"

    Row(
        Modifier.fillMaxWidth().alpha(if (paused) 0.6f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 체크 동그라미 (스펙 8.6: 보이는 원 22 · 아이콘 14, 누르는 영역은 40 그대로)
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(enabled = !paused, role = Role.Checkbox, onClick = onToggle)
                .semantics {
                    contentDescription = checkLabel
                    selected = checked
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(22.dp).background(fill, CircleShape).border(1.5.dp, stroke, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) SfImage("checkmark", size = 14.dp, tint = Color.White)
            }
        }

        // 누르면 수정 시트
        Row(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onEdit)
                .semantics { contentDescription = editLabel }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    habit.title,
                    style = pt(15, PTWeight.semibold).copy(textDecoration = if (checked) TextDecoration.LineThrough else null),
                    color = if (checked) NC.ink3 else NC.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        meta,
                        style = pt(12),
                        color = NC.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (paused) {
                        Text("쉬는 중", style = pt(12, PTWeight.semibold), color = NC.warningInk)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (habit.streak > 1) {
                    Text("🔥 ${habit.streak}일", style = pt(12, PTWeight.semibold), color = NC.statusWarningFg)
                }
                // 최근 7일
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (c in habit.recent7) {
                        HabitGrassCell(state = c.state, size = 8.dp, radius = 2.dp)
                    }
                }
            }
        }
    }
}

// MARK: - 추가 (점선 버튼 + 바로 추가 칩)

@Composable
private fun HabitAddArea(
    model: HabitModel,
    existing: List<Habit>,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val names = existing.map { it.name }.toSet()
    val suggestions = HabitSuggestion.all.filter { !names.contains(it.name) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GoalAddButton(title = "습관 추가", action = onAdd)
        if (suggestions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("눌러서 바로 추가", style = pt(12, PTWeight.medium), color = NC.ink3)
                FlowLayout(spacing = 6.dp) {
                    for ((i, s) in suggestions.take(10).withIndex()) {
                        NChip(
                            "${s.emoji} ${s.name}",
                            size = ChipSize.sm,
                            modifier = Modifier.alpha(if (model.addingSuggestion) 0.5f else 1f),
                        ) {
                            // 추가하는 중에는 눌러도 무시 (iOS .disabled)
                            if (!model.addingSuggestion) {
                                model.track("habit.suggestion_click", mapOf("suggestion_index" to i, "habit_count" to existing.size))
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch { model.add(s) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - 미리보기
// (아래 "할 일" 목록은 goal 기능의 GoalTaskListSection 이라, 그쪽 화면 모델 없이 그리면 비어 보일 수 있어요)

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun HabitGoalBodyPreview() {
    NaleulTheme(dark = false) {
        val m = remember { HabitModel.preview() }
        Column(Modifier.canvasBackground().verticalScroll(rememberScrollState())) {
            HabitGoalBody(goal = HabitPreviewData.goal, previewModel = m)
        }
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun HabitGoalBodyEmptyPreview() {
    NaleulTheme(dark = false) {
        val m = remember { HabitModel.preview(empty = true) }
        Column(Modifier.canvasBackground().verticalScroll(rememberScrollState())) {
            HabitGoalBody(goal = HabitPreviewData.goal, previewModel = m)
        }
    }
}
