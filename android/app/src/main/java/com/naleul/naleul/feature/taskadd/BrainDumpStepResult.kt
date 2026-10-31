// feature/taskadd/BrainDumpStepResult.kt
//
// iOS Features/TaskAdd/BrainDumpStepResult.swift
// 웹 원본: features/brain-dump/ui/StepResult.tsx (+ TaskAddView.tsx 의 PanelResult)
//
// 3단계 · TimeBlock 확인
//  - TaskAddPanelResult : 시트 안에서 저장 직후 보여주는 요약 (웹 panel 변형의 PanelResult 와 같아요)
//  - BrainDumpStepResult: "TimeBlock 확인" 을 누르면 나오는 자세한 결과 (웹 /tasks/new 페이지의 StepResult)
//      · 그 기간 TimeTable 격자 (새 블록은 NEW) — Calendar 작업자의 TimetableRangePreview 를 써요
//      · 블록마다 배치 이유 · 다른 날로 배치된 Task · 시간 미정 Task
//      · 임시 목표 안내 → AI 목표 만들기로 구체화
//
// iOS 에서 바꾼 점
//  - 점선 제안("이대로 진행할까요?")은 캘린더 화면이 맡아요. 시간을 정하지 않은 Task(pending)가 있으면
//    캘린더가 시트를 닫고 캘린더 위에서 점선을 보여 줘요 (CalendarScreen.handleAdded).
//    그래서 여기 자세한 결과는 pending 이 없을 때만 열 수 있어요.
// Android 에서 바꾼 점
//  - iOS 는 시트 안 NavigationStack 으로 이 화면을 쌓았어요. Android 는 TaskAddSheet 가 시트 안에서
//    화면을 바꿔 그려요 (위쪽 "‹ TimeBlock 확인" 줄은 TaskAddSheet 가 그려요).

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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// MARK: - 시트 안 요약 (웹 PanelResult)

@Composable
fun TaskAddPanelResult(
    result: ConfirmResponse,
    onAddMore: () -> Unit,
    /** 자세한 결과(격자) 화면으로 */
    onShowDetail: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val pending = result.pending ?: emptyList()
    val list: List<PanelRow> = buildList {
        for (p in result.placements) {
            add(PanelRow(id = p.taskId, title = p.title, time = "${KST.formatMonthDay(p.date)} ${KST.hm(p.start)}–${KST.hm(p.end)}"))
        }
        for (p in result.movedToOtherDays) {
            add(PanelRow(id = p.taskId, title = p.title, time = "${KST.formatMonthDay(p.date)} ${KST.hm(p.start)}–${KST.hm(p.end)} (다른 날로)"))
        }
        for (u in result.unscheduled) {
            add(PanelRow(id = u.taskId, title = u.title, time = u.date?.let { "${KST.formatMonthDay(it)} 시간 미정" } ?: "시간 미정"))
        }
        for (u in pending) {
            add(PanelRow(id = u.taskId, title = u.title, time = u.date?.let { "${KST.formatMonthDay(it)} · 점선 확인" } ?: "점선 확인"))
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .card(radius = Radius.xxl)
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SfImage("calendar.badge.checkmark", size = 14.dp, tint = NC.success)
            Text("${list.size}개를 추가했어요", style = pt(15, PTWeight.bold), color = NC.ink)
        }
        // 웹은 "왼쪽 캘린더" — 앱은 캘린더가 시트 뒤에 있어서 "캘린더"로만 적어요
        Text(
            if (pending.isEmpty()) "캘린더에 NEW로 표시돼요. 끌어서 바로 옮길 수 있어요."
            else "시간을 정하지 않은 ${pending.size}개는 캘린더에 점선으로 먼저 보여 드려요. 끌어서 고친 뒤 아래에서 \"이대로 진행\"을 누르면 그 자리에 놓이고, \"아니요\"면 시간 미정으로 남아요.",
            style = pt(13),
            color = NC.ink3,
            modifier = Modifier.padding(top = 4.dp),
        )

        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in list) {
                key(r.id) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SfImage("checkmark", size = 11.dp, tint = NC.success)
                        Text(
                            r.title,
                            style = pt(13, PTWeight.medium),
                            color = NC.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(r.time, style = ptMono(13), color = NC.ink3, maxLines = 1)
                    }
                }
            }
        }

        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NButton("더 추가하기", variant = NButtonVariant.secondary, size = NButtonSize.sm, icon = "arrow.counterclockwise", action = onAddMore)
            if (onShowDetail != null) {
                NButton("TimeBlock 확인", variant = NButtonVariant.ghost, size = NButtonSize.sm, icon = "calendar", action = onShowDetail)
            }
        }
    }
}

/** 요약 한 줄 (iOS TaskAddPanelResult.Row) */
private data class PanelRow(val id: Int, val title: String, val time: String) {
    companion object {}
}

// MARK: - 자세한 결과 (웹 StepResult)

/** "AI로 구체화하기" 는 시트를 닫은 뒤(화면이 사라진 뒤)에 이어서 실행돼야 해서 화면과 상관없는 범위에서 기다려요 */
private val refineScope = MainScope()

@Composable
fun BrainDumpStepResult(
    result: ConfirmResponse,
    today: String,
    onAddMore: () -> Unit,
    /** "캘린더에서 보기" · "AI로 구체화하기" 전에 Task 추가 시트를 닫아요 */
    closeSheet: () -> Unit,
    /** 미리보기(@Preview)에서는 격자(서버 조회) 대신 빈 칸을 그려요 */
    showsGrid: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var replanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pending = result.pending ?: emptyList()
    val newIds: Set<Int> = (result.placements.map { it.taskId } + result.movedToOtherDays.map { it.taskId } +
        result.unscheduled.map { it.taskId } + pending.map { it.taskId }).toSet()

    /** 보여줄 기간: 결과가 있는 첫날부터 마지막 날까지 */
    val dates: List<String> = run {
        val placed = (result.placements + result.movedToOtherDays).map { it.date }
        val others = (result.unscheduled + pending).mapNotNull { it.date }
        val all = (placed + others).sorted()
        if (all.isEmpty()) listOf(today) else all
    }
    val start = dates.firstOrNull() ?: today
    val lastWanted = dates.lastOrNull() ?: today

    val placedNow = result.placements.filter { !it.locked }
    val lockedNow = result.placements.filter { it.locked }
    /** 점선 확인은 캘린더가 맡으므로, 여기서는 아직 자리가 없는 Task 로 보여 줘요 */
    val leftUnscheduled = result.unscheduled + pending
    val replanDates: List<String> = dates.filter { it >= today }.distinct()

    fun whenText(p: Placement): String = "${KST.formatMonthDay(p.date)} ${KST.hm(p.start)}–${KST.hm(p.end)}"

    /** 웹 useReplan — 결과 날짜(오늘 이후)를 다시 배치 */
    fun replan() {
        val ds = replanDates
        TA.track("task_add_result.replan_click", mapOf("day_count" to ds.size))
        scope.launch {
            replanning = true
            try {
                Mutation.run(
                    invalidate = listOf(DataKey.timetable, DataKey.goals),
                    success = { res: List<ReplanResponse> ->
                        val n = res.sumOf { it.placed.size + it.movedToOtherDays.size }
                        if (n > 0) "${n}개 블록을 다시 배치했어요." else "다시 배치할 블록이 없었어요."
                    },
                ) {
                    TimetableAPI.replan(dates = ds)
                }
                TA.track("task_add_result.replan_result", GD.result())
            } finally {
                replanning = false
            }
        }
    }

    /** 임시 목표 → AI 목표 만들기 (전체 화면). 시트가 떠 있으면 전체 화면이 가려져서 먼저 닫아요 */
    fun refine(goalId: Int) {
        TA.track("task_add_result.temp_refine_click", mapOf("goal_category_id" to goalId))
        closeSheet()
        refineScope.launch {
            delay(450)
            AppRouter.startGoalCreation(sourceGoalId = goalId)
        }
    }

    val summaryText: String = run {
        var s = "${result.placements.size}개를 배치했어요"
        if (result.movedToOtherDays.isNotEmpty()) s += " · 다른 날로 ${result.movedToOtherDays.size}개"
        if (leftUnscheduled.isNotEmpty()) s += " · 시간 미정 ${leftUnscheduled.size}개"
        "$s. 블록을 끌어서 옮기면 그 시간에 고정돼요."
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(NC.bgSurface)
            .verticalScroll(rememberScrollState())
            // 스펙 13.2: 시트 내용 좌우 24 · 위 20
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // MARK: 위 요약
        Column(
            Modifier
                .fillMaxWidth()
                .card()
                .padding(20.dp),
        ) {
            Text("TimeBlock을 만들었어요", style = NType.h4, color = NC.ink)
            Text(summaryText, style = pt(14), color = NC.ink3, modifier = Modifier.padding(top = 4.dp))
            if (replanDates.isNotEmpty()) {
                NButton(
                    "다시 배치",
                    variant = NButtonVariant.ai,
                    size = NButtonSize.sm,
                    icon = "arrow.counterclockwise",
                    loading = replanning,
                    modifier = Modifier.padding(top = 12.dp),
                ) { replan() }
            }

            // 임시 목표 안내
            for (t in result.tempGoals) {
                key(t.goalCategoryId) {
                    val warnInk = NC.warningInkStrong
                    Column(
                        Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth()
                            .background(NC.warningSoft, RoundedCornerShape(Radius.xxl))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append((t.emoji?.let { "$it " } ?: "") + t.name)
                                }
                                append("은(는) 임시 목표로 만들어졌어요. 목표를 구체화해볼까요?")
                            },
                            style = pt(14),
                            color = warnInk,
                        )
                        NButton("AI로 구체화하기", variant = NButtonVariant.ai, size = NButtonSize.sm, icon = "sparkles") {
                            refine(t.goalCategoryId)
                        }
                    }
                }
            }
        }

        // MARK: 격자
        if (showsGrid) {
            TimetableRangePreview(startDate = start, endDate = lastWanted, highlightTaskIds = newIds)
        } else {
            LoadingView(minHeight = 320.dp, modifier = Modifier.fillMaxWidth().card(radius = Radius.xxl))
        }

        // MARK: 배치 이유
        Column(
            Modifier
                .fillMaxWidth()
                .card()
                .padding(20.dp),
        ) {
            Text("이렇게 배치했어요", style = pt(15, PTWeight.bold), color = NC.ink)
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (p in placedNow) {
                    key(p.taskId) { BrainDumpReasonRow(title = p.title, time = whenText(p), reason = p.reason) }
                }
                for (p in lockedNow) {
                    key(p.taskId) { BrainDumpReasonRow(title = p.title, time = whenText(p), reason = "직접 정한 시간이에요") }
                }
                if (result.placements.isEmpty()) {
                    Text("그날 안에 놓인 Task가 없어요.", style = pt(14), color = NC.ink3)
                }
            }

            if (result.movedToOtherDays.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SfImage("calendar.badge.clock", size = 14.dp, tint = NC.ink)
                    Text("다른 날로 배치된 Task", style = pt(15, PTWeight.bold), color = NC.ink)
                }
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (p in result.movedToOtherDays) {
                        key(p.taskId) { BrainDumpReasonRow(title = p.title, time = whenText(p), reason = p.reason) }
                    }
                }
            }

            if (leftUnscheduled.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SfImage("calendar.badge.exclamationmark", size = 14.dp, tint = NC.ink)
                    Text("시간 미정", style = pt(15, PTWeight.bold), color = NC.ink)
                }
                Text(
                    "빈 시간이 없어서 날짜만 정했어요. 격자의 \"시간 미정\"에서 끌어다 놓을 수 있어요.",
                    style = pt(12),
                    color = NC.ink3,
                    modifier = Modifier.padding(top = 4.dp),
                )
                val ink = NC.ink
                val ink3 = NC.ink3
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (u in leftUnscheduled) {
                        key(u.taskId) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = ink)) { append(u.title) }
                                    withStyle(SpanStyle(color = ink3)) { append(u.date?.let { " · ${KST.formatMonthDay(it)}" } ?: "") }
                                },
                                style = pt(14),
                            )
                        }
                    }
                }
            }
        }

        // MARK: 아래 버튼
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NButton("Task 더 추가하기", variant = NButtonVariant.secondary, action = onAddMore)
            NButton("캘린더에서 보기", icon = "arrow.right") {
                val d = start
                TA.plain("task_add_result.calendar_click", mapOf("days_from_today" to KST.diffDays(today, d)))
                closeSheet()
                AppRouter.openCalendar(view = CalendarViewMode.week, date = d)
            }
        }
    }
}

/** 배치 이유 한 줄 (NEW 표시 + 제목 + 시각 + 이유) */
@Composable
private fun BrainDumpReasonRow(title: String, time: String, reason: String?) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "NEW",
            style = pt(9, PTWeight.bold),
            color = Color.White,
            modifier = Modifier
                .padding(top = 3.dp)
                .background(NC.actionDanger, RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val ink = NC.ink
            val ink3 = NC.ink3
            val titleStyle = pt(14, PTWeight.semibold)
            val whenStyle = ptMono(12)
            Text(
                buildAnnotatedString {
                    withStyle(titleStyle.toSpanStyle().copy(color = ink)) { append(title) }
                    withStyle(whenStyle.toSpanStyle().copy(color = ink3)) { append("  $time") }
                },
            )
            if (!reason.isNullOrEmpty()) {
                Text(reason, style = pt(12), color = NC.ink3)
            }
        }
    }
}

@Preview(showBackground = true, name = "시트 안 요약")
@Composable
private fun TaskAddPanelResultPreview() {
    NaleulTheme(dark = false) {
        Column(
            Modifier
                .canvasBackground()
                .padding(16.dp),
        ) {
            TaskAddPanelResult(result = TaskAddSample.result(KST.today()), onAddMore = {}, onShowDetail = {})
        }
    }
}

@Preview(showBackground = true, name = "자세한 결과", heightDp = 1200)
@Composable
private fun BrainDumpStepResultPreview() {
    NaleulTheme(dark = false) {
        BrainDumpStepResult(
            result = TaskAddSample.result(KST.today()),
            today = KST.today(),
            onAddMore = {},
            closeSheet = {},
            showsGrid = false,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
