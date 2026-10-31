// Features/Goal/GoalRoutinesSection.kt
//
// iOS GoalRoutinesSection.swift — 웹 features/goal/ui/RoutinesSection.tsx — 루틴 목록 · 추가 · 수정 · 삭제.
// 웹은 줄을 누르면 그 자리에서 수정 → 휴대폰은 줄을 누르면 수정 시트가 떠요.
//
// iOS 는 @Environment(GoalDetailViewModel.self) 로 목표 상세 ViewModel 을 받았어요.
// 여기선 GoalDetailScreen 이 CompositionLocalProvider(LocalGoalDetailViewModel provides vm) 로 넣어 주고,
// 각 컴포저블의 vm 인자 기본값이 그걸 읽어요 (필요하면 vm = … 로 직접 넘겨도 돼요).

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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle

// MARK: - 섹션

private data class GoalRoutinesSectionRowItem(
    val routine: RoutineSummary,
    /** 영역 이름 ("기타 할 일" 그릇이면 "") */
    val subGoal: String,
    val subGoalId: Int,
) {
    val id: Int get() = routine.routineId
}

@Composable
fun GoalRoutinesSection(
    goal: GoalCategory,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    modifier: Modifier = Modifier,
) {
    val items = GoalDetailLogic.activeSubGoals(goal).flatMap { sg ->
        sg.routines.map { r ->
            GoalRoutinesSectionRowItem(
                routine = r,
                subGoal = if (sg.defaultBucket ?: false) "" else sg.generalCategoryName,
                subGoalId = sg.generalCategoryId,
            )
        }
    }
    SectionCard("루틴", modifier = modifier, aside = { Text("${items.size}개") }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (items.isNotEmpty()) {
                Column {
                    items.forEachIndexed { i, item ->
                        key(item.id) {
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(NC.line))
                            GoalRoutineRow(
                                routine = item.routine,
                                subGoal = item.subGoal,
                                practice = vm.heatmap?.firstOrNull { it.routineId == item.routine.routineId }?.timeStats?.practiceSentence,
                                onEdit = { entry ->
                                    vm.track("goal_detail.routine_edit_open", mapOf("routine_id" to item.routine.routineId, "entry_point" to entry))
                                    vm.sheet = GoalDetailSheet.routine(routine = item.routine, subGoalId = item.subGoalId)
                                },
                                onDelete = { vm.ask(GoalDetailConfirm.deleteRoutine(item.routine)) },
                                onExtraDay = {
                                    vm.track("goal_detail.routine_extra_day_click", mapOf("routine_id" to item.routine.routineId))
                                    vm.sheet = GoalDetailSheet.routineExtraDay(item.routine)
                                },
                            )
                        }
                    }
                }
            }
            GoalAddButton("루틴 추가") {
                vm.track("goal_detail.routine_add_click", mapOf("routine_count" to items.size))
                vm.sheet = GoalDetailSheet.routine(routine = null, subGoalId = null)
            }
        }
    }
}

// MARK: - 한 줄

/** @param onExtraDay 반복 요일이 아닌 날에도 했을 때 — 그날을 추가해요 */
@Composable
private fun GoalRoutineRow(
    routine: RoutineSummary,
    subGoal: String,
    /** "화 19:00~19:30 · 목 17:10~17:40에 가장 많이 실천했어요" (기록이 없으면 null) */
    practice: String?,
    /** entry_point — 줄을 누르면 "row", 연필 아이콘이면 "icon" */
    onEdit: (String) -> Unit,
    onDelete: () -> Unit,
    onExtraDay: () -> Unit,
) {
    val active = routine.routineStatus != "STOPPED" && routine.routineStatus != "DELETED"
    val timeLine = run {
        var t = GoalDetailLogic.routineTimeText(routine)
        val hasStart = GoalFormat.hhmm(routine.repeatStartTime) != null
        val d = routine.durationMinutes
        if (!hasStart && (routine.dayTimes ?: emptyList()).isEmpty() && d != null && d > 0) {
            t += " · ${d}분"
        }
        t
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier.weight(1f).clickable(role = Role.Button) { onEdit("row") },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(routine.routineName, style = pt(15, PTWeight.semibold), color = NC.ink)
                    if (routine.notificationEnabled) {
                        SfImage("bell", size = 12.dp, tint = NC.ink3, contentDescription = "알림 켜짐")
                    }
                }
                // 영역은 부모가 아니라 라벨 — "기타 할 일"이면 안 보여줘요 (2026-10: 시간은 카드 맨 아래로)
                if (subGoal.isNotEmpty()) {
                    Text(
                        subGoal,
                        style = pt(12, PTWeight.medium),
                        color = NC.ink2,
                        modifier = Modifier.background(NC.subtle, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                val desc = routine.description
                if (!desc.isNullOrEmpty()) {
                    Text(
                        desc,
                        style = pt(13).copy(lineHeight = 1.5.em),
                        color = NC.ink2,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth()
                            .background(NC.subtle, RoundedCornerShape(Radius.lg))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            GoalRowActions(label = routine.routineName, onEdit = { onEdit("icon") }, onDelete = onDelete)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalRoutineDayChips(days = routine.repeatDays)
            Spacer(Modifier.weight(1f))
            if (active) {
                Row(
                    Modifier
                        .height(28.dp)
                        .border(1.dp, NC.borderSubtle, CircleShape)
                        .clickable(role = Role.Button, onClick = onExtraDay)
                        .semantics { contentDescription = "다른 날 추가 — 반복 요일이 아닌 날에도 했다면 그날을 추가해요" }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SfImage("calendar.badge.plus", size = 12.dp, tint = NC.ink2)
                    Text("다른 날 추가", style = pt(12, PTWeight.medium), color = NC.ink2)
                }
            }
        }
        // 계획한 시간 · 실제로 가장 많이 한 시간 — 무슨 시간인지 이름을 붙여 따로 보여줘요
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (timeLine.isNotEmpty()) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = NC.ink2)) { append("계획 · ") }
                        withStyle(SpanStyle(color = NC.ink3)) { append(timeLine) }
                    },
                    style = pt(12),
                )
            }
            if (practice != null) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = NC.brand)) { append("실천 · ") }
                        withStyle(SpanStyle(color = NC.ink2)) { append(practice) }
                    },
                    style = pt(12),
                )
            }
        }
    }
}

/** 월~일 칸 (반복하는 요일만 파랗게) */
@Composable
private fun GoalRoutineDayChips(days: List<JavaDayOfWeek>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (d in JavaDayOfWeek.entries) {
            val on = days.contains(d)
            Box(
                Modifier.size(24.dp).background(if (on) NC.brand else NC.subtle, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(d.label, style = pt(11, PTWeight.semibold), color = if (on) Color.White else NC.ink4)
            }
        }
    }
}

// MARK: - 다른 날 추가 시트 (웹 ExtraDayForm)

/** 그 날의 루틴 시간 — 요일별 시간이 있으면 그 시간, 없으면 기본 시간 (iOS GoalRoutineExtraDaySheet.timeOf) */
fun goalRoutineTimeOn(r: RoutineSummary, ymd: String): GoalRoutineDraft.Times {
    val day = JavaDayOfWeek.from(KST.weekdayIndex(ymd))
    val own = (r.dayTimes ?: emptyList()).firstOrNull { it.dayOfWeek == day }
    return GoalRoutineDraft.Times(
        start = GoalFormat.hhmm(own?.startTime ?: r.repeatStartTime) ?: "",
        end = GoalFormat.hhmm(own?.endTime ?: r.repeatEndTime) ?: "",
    )
}

/**
 * 평일 루틴을 주말에도 했을 때처럼, 반복 요일이 아닌 날에 그날 하루만 루틴 블록을 만들어요.
 * "이미 했어요"면 그 시간을 실제로 한 시간으로 바로 남겨요 (앞날은 예정으로만).
 */
@Composable
fun GoalRoutineExtraDaySheet(
    routine: RoutineSummary,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    onDismiss: () -> Unit = { vm.sheet = null },
) {
    val today = KST.today()
    val initial = remember(routine.routineId) { goalRoutineTimeOn(routine, today) }
    var date by remember(routine.routineId) { mutableStateOf(today) }
    var startTime by remember(routine.routineId) { mutableStateOf(initial.start) }
    var endTime by remember(routine.routineId) { mutableStateOf(initial.end) }
    var done by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        onDispose {
            GD.closeMethod(outcome)?.let { m ->
                vm.track("goal_detail.routine_extra_day_cancel", mapOf("routine_id" to routine.routineId, "close_method" to m))
            }
        }
    }

    val maxDate = KST.addDays(today, 60)
    val future = date > today
    val day = JavaDayOfWeek.from(KST.weekdayIndex(date))
    val isRepeatDay = routine.repeatDays.contains(day)

    val error: String? = run {
        if (date > maxDate) return@run "60일 뒤까지만 추가할 수 있어요."
        if (startTime.isEmpty() != endTime.isEmpty()) return@run "시작·종료 시간을 함께 입력하거나 둘 다 비워 주세요."
        if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
            val s = KST.timeToMinutes(startTime)
            val e = KST.timeToMinutes(endTime)
            // 끝이 시작보다 이르면 자정을 넘기는 것 (12시간까지)
            if (s == e || (e < s && 1440 - (s - e) > 12 * 60)) return@run "시작과 종료 시간을 확인해 주세요."
        }
        null
    }

    fun submit() {
        if (error != null || saving) return
        val id = routine.routineId
        val d = date
        val s = if (startTime.isEmpty()) null else startTime
        val e = if (endTime.isEmpty()) null else endTime
        val didIt = done && !future
        vm.track("goal_detail.routine_extra_day_save", mapOf(
            "routine_id" to id, "days_from_today" to GD.daysFromToday(d), "marked_done" to didIt,
            "is_repeat_day" to isRepeatDay, "has_time" to (s != null),
        ))
        saving = true
        scope.launch {
            Mutation.run(
                invalidate = listOf(DataKey.goals, DataKey.timetable, DataKey.home, DataKey.pattern),
                successMessage = if (didIt) "그날 한 걸로 남겼어요." else "그날 캘린더에 넣었어요.",
            ) {
                GoalEditAPI.addRoutineExtraDay(routineId = id, date = d, startTime = s, endTime = e, done = didIt)
            }
            saving = false
            val ok = Mutation.lastErrorStatus == null
            vm.track("goal_detail.routine_result", GD.result() + mapOf("routine_action" to "extra_day"))
            if (ok) {
                outcome = "saved"
                onDismiss()
            }
        }
    }

    SheetScaffold(
        onDismiss = onDismiss,
        title = "‘${routine.routineName}’를 다른 날에도 했어요",
        description = "반복 요일은 그대로 두고, 고른 날 하루만 루틴 블록이 생겨요. 실천 기록에도 함께 들어가요.",
        dismissible = !saving,
    ) { close ->
        GoalSheetForm {
            GoalInlineForm(
                submitLabel = if (done && !future) "한 걸로 남기기" else "그날 추가",
                saving = saving,
                valid = error == null,
                error = error,
                onCancel = {
                    outcome = "cancel_button"
                    close()
                },
                onSubmit = { submit() },
            ) {
                GoalFormField("날짜") {
                    GoalDateInput(
                        value = date,
                        onValueChange = { v ->
                            // iOS .onChange(of: date) — 날짜가 바뀌면 그날 요일의 루틴 시간으로 다시 채워요
                            date = v
                            val t = goalRoutineTimeOn(routine, v)
                            startTime = t.start
                            endTime = t.end
                        },
                        maxDate = maxDate,
                    )
                }
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalFormField("시작", modifier = Modifier.weight(1f)) {
                        GoalTimeInput(value = startTime, onValueChange = { startTime = it })
                    }
                    GoalFormField("종료", modifier = Modifier.weight(1f)) {
                        GoalTimeInput(
                            value = endTime,
                            onValueChange = { endTime = it },
                            suggested = if (startTime.isEmpty()) "09:30" else KST.minutesToTime(KST.timeToMinutes(startTime) + 30),
                        )
                    }
                }
                if (isRepeatDay) {
                    Text(
                        "${day.label}요일은 원래 하는 날이에요. 이미 블록이 있으면 캘린더에서 그 블록을 완료해 주세요.",
                        style = pt(12),
                        color = NC.warning,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("이미 했어요", style = pt(14, PTWeight.semibold), color = NC.ink)
                        Text(
                            if (future) {
                                "앞날은 예정으로만 넣어요"
                            } else if (startTime.isEmpty()) {
                                "루틴 시간에 한 걸로 남겨요"
                            } else {
                                "$startTime~$endTime 에 한 걸로 남겨요"
                            },
                            style = pt(12),
                            color = NC.ink3,
                        )
                    }
                    NSwitch(isOn = done && !future, onIsOnChange = { done = it }, label = "이미 했어요", disabled = future)
                }
            }
        }
    }
}

// MARK: - 추가 · 수정 시트 (웹 RoutineForm)

private data class GoalRoutineFormDayTime(val start: String, val end: String)

/**
 * @param routine null 이면 새로 추가
 * @param subGoalId 처음 고를 세부 목표 (영역)
 */
@Composable
fun GoalRoutineFormSheet(
    goal: GoalCategory,
    routine: RoutineSummary?,
    subGoalId: Int?,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    onDismiss: () -> Unit = { vm.sheet = null },
) {
    val areas = GoalDetailLogic.areas(goal)
    val rkey = routine?.routineId
    var name by remember(rkey) { mutableStateOf(routine?.routineName ?: "") }
    var sub by remember(rkey) {
        mutableStateOf(if (subGoalId != null && areas.any { it.generalCategoryId == subGoalId }) subGoalId else null)
    }
    /** 반복 기간은 보통 목표 기간 그대로라 접어 둬요 */
    var showPeriod by remember(rkey) { mutableStateOf(false) }
    var days by remember(rkey) {
        val rd = routine?.repeatDays ?: emptyList()
        mutableStateOf(if (rd.isEmpty()) JavaDayOfWeek.entries.toList() else rd)
    }
    var startTime by remember(rkey) { mutableStateOf(GoalDetailLogic.hhmmOrEmpty(routine?.repeatStartTime)) }
    var endTime by remember(rkey) { mutableStateOf(GoalDetailLogic.hhmmOrEmpty(routine?.repeatEndTime)) }
    var start by remember(rkey) {
        val period = GoalDetailLogic.defaultPeriod(goal)
        val today = KST.today()
        mutableStateOf(routine?.repeatStartDate ?: (if (period.start < today) today else period.start))
    }
    var end by remember(rkey) {
        val period = GoalDetailLogic.defaultPeriod(goal)
        val today = KST.today()
        mutableStateOf(routine?.repeatEndDate ?: (if (period.end < today) today else period.end))
    }
    var notify by remember(rkey) { mutableStateOf(routine?.notificationEnabled ?: true) }
    var howTo by remember(rkey) { mutableStateOf(routine?.description ?: "") }
    /** 요일별 시간: 기본은 모든 요일 같은 시간, 원하면 요일마다 다르게 (기본과 다른 요일만 저장돼요) */
    var perDay by remember(rkey) { mutableStateOf(!(routine?.dayTimes ?: emptyList()).isEmpty()) }
    var dayTimes by remember(rkey) {
        mutableStateOf(
            (routine?.dayTimes ?: emptyList()).associate { t ->
                t.dayOfWeek to GoalRoutineFormDayTime(GoalDetailLogic.hhmmOrEmpty(t.startTime), GoalDetailLogic.hhmmOrEmpty(t.endTime))
            },
        )
    }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var outcome by remember { mutableStateOf<String?>(null) }
    /** 지금 다른 루틴과 겹치는 수 (주간표가 알려줘요) */
    var conflictCount by remember { mutableIntStateOf(0) }

    val subOptions: List<Pair<Int?, String>> = listOf<Pair<Int?, String>>(null to "세부 목표 없음") +
        areas.map { it.generalCategoryId to it.generalCategoryName }
    val trimmedName = name.trim()
    val shownDays = JavaDayOfWeek.entries.filter { days.contains(it) }

    /** 요일별 칸에 보여줄 값 — 따로 정한 게 없으면 기본 시간 */
    fun timeOf(d: JavaDayOfWeek): GoalRoutineFormDayTime = dayTimes[d] ?: GoalRoutineFormDayTime(startTime, endTime)

    fun setDayTime(d: JavaDayOfWeek, isStart: Boolean, v: String) {
        val t = timeOf(d)
        dayTimes = dayTimes + (d to (if (isStart) t.copy(start = v) else t.copy(end = v)))
    }

    /** 주간표의 빈 곳을 눌렀을 때 — 그 요일을 넣고, 지금 길이(없으면 1시간) 그대로 시작 시각만 옮겨요 */
    fun pickFromMap(d: JavaDayOfWeek, m: Int) {
        var len = 60
        if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
            val l = (KST.timeToMinutes(endTime) - KST.timeToMinutes(startTime) + 1440) % 1440
            if (l > 0) len = l
        }
        if (!days.contains(d)) days = JavaDayOfWeek.entries.filter { it == d || days.contains(it) }
        val st = KST.minutesToTime(m)
        val et = KST.minutesToTime((m + len) % 1440)
        if (perDay && days.size > 1) {
            dayTimes = dayTimes + (d to GoalRoutineFormDayTime(st, et))
        } else {
            startTime = st
            endTime = et
        }
    }

    /** 기본 시간과 실제로 다른 요일만 보내요 */
    val overrides: List<RoutineDayTime> = if (!perDay) {
        emptyList()
    } else {
        shownDays.mapNotNull { d ->
            val t = timeOf(d)
            if (t.start.isEmpty() || t.end.isEmpty() || (t.start == startTime && t.end == endTime)) {
                null
            } else {
                RoutineDayTime(dayOfWeek = d, startTime = t.start, endTime = t.end)
            }
        }
    }

    val badDay: JavaDayOfWeek? = if (!perDay) {
        null
    } else {
        shownDays.firstOrNull { d ->
            val t = timeOf(d)
            t.start.isEmpty() != t.end.isEmpty() ||
                (t.start.isNotEmpty() && t.end.isNotEmpty() && !KST.isValidRoutineRange(t.start, t.end))
        }
    }

    val error: String? = when {
        trimmedName.isEmpty() -> null
        days.isEmpty() -> "요일을 하나 이상 골라 주세요."
        startTime.isEmpty() != endTime.isEmpty() -> "시작·종료 시간을 함께 입력하거나 둘 다 비워 주세요."
        startTime.isNotEmpty() && endTime.isNotEmpty() && !KST.isValidRoutineRange(startTime, endTime) ->
            "시작과 종료 시간을 확인해 주세요. 자정을 넘기는 루틴(예: 23:00 ~ 00:00)은 12시간까지 돼요."
        routine?.repeatStartTime != null && startTime.isEmpty() ->
            "이미 정한 시간은 비울 수 없어요. 시간 없이 하려면 루틴을 다시 만들어 주세요."
        start.isEmpty() || end.isEmpty() || start > end -> "반복 기간을 확인해 주세요."
        badDay != null -> "${badDay.label}요일 시간을 확인해 주세요. (시작·종료를 함께, 자정을 넘기면 12시간까지)"
        else -> null
    }

    // MARK: 앰플리튜드
    fun trackR(event: String, props: EventProps = emptyMap()) = vm.track(event, props + mapOf("is_new" to (routine == null)))
    val formKey = listOf(
        trimmedName, days.joinToString("") { it.name }, startTime, endTime, start, end, notify.toString(), howTo, perDay.toString(),
        dayTimes.map { "${it.key.name}${it.value.start}${it.value.end}" }.sorted().joinToString(""), (sub ?: 0).toString(),
    ).joinToString("|")
    val initialKey = remember { formKey }
    val initialPeriod = remember { start + end }
    val latestKey by rememberUpdatedState(formKey)
    LaunchedEffect(error) { error?.let { trackR("goal_detail.routine_edit_error", mapOf("validation_error" to it)) } }
    DisposableEffect(Unit) {
        onDispose {
            GD.closeMethod(outcome)?.let { m ->
                trackR("goal_detail.routine_edit_cancel", mapOf("close_method" to m, "is_dirty" to (latestKey != initialKey)))
            }
        }
    }

    fun submit() {
        if (trimmedName.isEmpty() || error != null || saving) return
        // 영역 없음 → 새로 만들 땐 null(서버가 "기타 할 일"로), 수정할 땐 "기타 할 일"로 옮기기 (그릇이 없으면 null)
        val areaId: Int? = sub ?: (if (routine != null) GoalDetailLogic.bucket(goal)?.generalCategoryId else null)
        val input = GoalRoutineInput(
            generalCategoryId = areaId,
            routineName = trimmedName,
            repeatStartDate = start,
            repeatEndDate = end,
            repeatDays = days,
            repeatStartTime = if (startTime.isEmpty()) null else startTime,
            repeatEndTime = if (endTime.isEmpty()) null else endTime,
            notificationEnabled = notify,
            description = howTo.trim(),
            dayTimes = overrides,
        )
        val goalId = goal.goalCategoryId
        val routineId = routine?.routineId
        trackR("goal_detail.routine_save_click", mapOf(
            "repeat_day_count" to days.size, "has_time" to startTime.isNotEmpty(), "per_day_time" to overrides.isNotEmpty(),
            "notification_enabled" to notify, "has_description" to input.description.isNotEmpty(), "has_sub_goal" to (sub != null),
            "period_changed" to (initialPeriod != start + end), "has_conflict" to (conflictCount > 0),
        ))
        saving = true
        scope.launch {
            val ok: Unit? = if (routineId != null) {
                Mutation.run(invalidate = listOf(DataKey.goals, DataKey.timetable), successMessage = "루틴을 수정했어요.") {
                    GoalEditAPI.updateRoutine(id = routineId, input = input)
                }
            } else {
                Mutation.run(invalidate = listOf(DataKey.goals, DataKey.timetable), successMessage = "루틴을 추가했어요.") {
                    GoalEditAPI.createRoutine(goalId = goalId, input = input)
                }
            }
            saving = false
            vm.track("goal_detail.routine_result", GD.result() + mapOf("routine_action" to if (routineId == null) "create" else "update"))
            if (ok != null) {
                outcome = "saved"
                onDismiss()
            }
        }
    }

    SheetScaffold(
        onDismiss = onDismiss,
        title = if (routine == null) "루틴 추가" else "루틴 수정",
        dismissible = !saving,
        fullHeight = true,
    ) { close ->
        GoalSheetForm {
            GoalInlineForm(
                submitLabel = if (routine == null) "추가" else "저장",
                saving = saving,
                valid = trimmedName.isNotEmpty() && error == null,
                error = error,
                onCancel = {
                    outcome = "cancel_button"
                    close()
                },
                onSubmit = { submit() },
                extra = {
                    Row(
                        Modifier
                            .clickable(role = Role.Button) {
                                trackR("goal_detail.routine_notify_toggle", mapOf("notification_enabled" to !notify))
                                notify = !notify
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val c = if (notify) NC.brand else NC.ink3
                        SfImage(if (notify) "bell" else "bell.slash", size = 12.dp, tint = c)
                        Text("알림 ${if (notify) "켜짐" else "꺼짐"}", style = pt(13, PTWeight.semibold), color = c)
                    }
                },
            ) {
                GoalFormField("루틴 이름") {
                    NTextField(value = name, onValueChange = { name = it }, placeholder = "예: 아침 스트레칭", maxLength = 50)
                }
                // 영역이 있을 때만 (선택) — 없으면 아예 안 보여요
                if (areas.isNotEmpty()) {
                    GoalFormField("세부 목표 (선택)") {
                        GoalSelectMenu(selection = sub, onSelectionChange = { sub = it }, options = subOptions)
                    }
                }
                GoalFormField("반복 요일") { GoalDaysPicker(value = days, onValueChange = { days = it }) }
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalFormField(if (perDay) "기본 시작 시간" else "시작 시간 (선택)", modifier = Modifier.weight(1f)) {
                        GoalTimeInput(value = startTime, onValueChange = { startTime = it })
                    }
                    GoalFormField(if (perDay) "기본 종료 시간" else "종료 시간 (선택)", modifier = Modifier.weight(1f)) {
                        GoalTimeInput(
                            value = endTime,
                            onValueChange = { endTime = it },
                            suggested = if (startTime.isEmpty()) "09:30" else KST.minutesToTime(KST.timeToMinutes(startTime) + 30),
                        )
                    }
                }
                if (showPeriod) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoalFormField("반복 시작일", modifier = Modifier.weight(1f)) {
                            GoalDateInput(value = start, onValueChange = { start = it })
                        }
                        GoalFormField("반복 종료일", modifier = Modifier.weight(1f)) {
                            GoalDateInput(value = end, onValueChange = { end = it })
                        }
                    }
                }
                // 다른 루틴과 겹치는지 · 내 루틴 주간표 (캘린더에 다녀오지 않아도 돼요)
                GoalRoutineWeekMap(
                    draft = GoalRoutineDraft(
                        routineId = routine?.routineId,
                        name = trimmedName,
                        days = days,
                        timeOf = { d -> val t = timeOf(d); GoalRoutineDraft.Times(t.start, t.end) },
                    ),
                    onPick = { d, m ->
                        trackR("goal_detail.routine_weekmap_pick", mapOf(
                            "day_of_week" to d.name, "start_minute" to m, "per_day_time" to (perDay && days.size > 1),
                        ))
                        pickFromMap(d, m)
                    },
                    onDismiss = {
                        // 캘린더 AI 로 넘어가요 — 취소 이벤트 대신 conflict_ai_click 이 남아요
                        outcome = "next"
                        close()
                    },
                    isNew = routine == null,
                    onConflictCount = { conflictCount = it },
                )
                // 요일별 시간 — 기본은 같은 시간, 필요할 때만 펼쳐요
                if (days.size > 1) {
                    if (perDay) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(NC.subtle.copy(alpha = 0.6f), RoundedCornerShape(Radius.xl))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("요일별 시간", style = pt(13, PTWeight.semibold), color = NC.ink)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "모든 요일 같은 시간으로",
                                    style = pt(12, PTWeight.semibold).copy(textDecoration = TextDecoration.Underline),
                                    color = NC.ink3,
                                    modifier = Modifier.clickable(role = Role.Button) {
                                        trackR("goal_detail.routine_per_day_toggle", mapOf("per_day_time" to false))
                                        perDay = false
                                        dayTimes = emptyMap()
                                    },
                                )
                            }
                            for (d in shownDays) {
                                val t = timeOf(d)
                                val custom = dayTimes[d] != null && (t.start != startTime || t.end != endTime)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        Modifier
                                            .size(32.dp)
                                            .background(if (custom) NC.brand else NC.surface, RoundedCornerShape(Radius.lg)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(d.label, style = pt(13, PTWeight.bold), color = if (custom) Color.White else NC.ink2)
                                    }
                                    GoalTimeInput(
                                        value = t.start,
                                        onValueChange = { v -> setDayTime(d, true, v) },
                                        placeholder = "시작",
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("~", style = pt(15), color = NC.ink4)
                                    GoalTimeInput(
                                        value = t.end,
                                        onValueChange = { v -> setDayTime(d, false, v) },
                                        placeholder = "종료",
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "기본으로",
                                        style = pt(12, PTWeight.semibold),
                                        color = NC.ink3,
                                        modifier = Modifier
                                            .alpha(if (custom) 1f else 0f)
                                            .clickable(enabled = custom, role = Role.Button) { dayTimes = dayTimes - d },
                                    )
                                }
                            }
                            Text(
                                "파란 요일만 기본 시간과 달라요. 비워 둔 요일은 " +
                                    (if (startTime.isNotEmpty() && endTime.isNotEmpty()) "기본 시간($startTime~$endTime)" else "자동 배치") +
                                    "을 따라요.",
                                style = pt(12),
                                color = NC.ink3,
                            )
                        }
                    } else {
                        Text(
                            "+ 요일마다 시간을 다르게 하기",
                            style = pt(13, PTWeight.semibold),
                            color = NC.textBrand,
                            modifier = Modifier.clickable(role = Role.Button) {
                                trackR("goal_detail.routine_per_day_toggle", mapOf("per_day_time" to true))
                                perDay = true
                            },
                        )
                    }
                }
                if (!showPeriod) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${GoalFormat.formatDot(start)} – ${GoalFormat.formatDot(end)} 동안 반복해요.",
                            style = pt(12),
                            color = NC.ink3,
                        )
                        Text(
                            "기간 바꾸기",
                            style = pt(12).copy(textDecoration = TextDecoration.Underline),
                            color = NC.ink2,
                            modifier = Modifier.clickable(role = Role.Button) {
                                trackR("goal_detail.routine_period_open")
                                showPeriod = true
                            },
                        )
                    }
                }
                GoalFormField("하는 방법 (선택)") {
                    NTextField(
                        value = howTo,
                        onValueChange = { howTo = it },
                        placeholder = "예) 스쿼트 4x10 → 런지 3x12(각), 세트 사이 60초 휴식",
                        minHeight = 72.dp,
                        singleLine = false,
                        maxLength = 300,
                    )
                }
                Text(
                    (if (startTime.isEmpty()) "시간을 비워 두면 자동 배치가 빈 시간에 넣어 줘요." else "정한 시간에 매번 고정돼요.") +
                        (if (routine != null) " 저장하면 오늘 이후의 루틴 Task가 새 설정으로 바뀌어요 (지난 기록은 그대로)." else ""),
                    style = pt(12),
                    color = NC.ink3,
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun GoalRoutinesSectionPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview() }
        CompositionLocalProvider(LocalGoalDetailViewModel provides vm) {
            Column(Modifier.fillMaxSize().canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
                GoalRoutinesSection(goal = GoalPreviewData.goal)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun GoalRoutineFormSheetPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview() }
        val goal = GoalPreviewData.goal
        GoalRoutineFormSheet(goal = goal, routine = goal.generalCategories[1].routines[0], subGoalId = 22, vm = vm, onDismiss = {})
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun GoalRoutineExtraDaySheetPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview() }
        val goal = GoalPreviewData.goal
        GoalRoutineExtraDaySheet(routine = goal.generalCategories[1].routines[0], vm = vm, onDismiss = {})
    }
}
