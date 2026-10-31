// feature/calendar/CalendarMonthView.kt
//
// iOS Features/Calendar/CalendarMonthView.swift 를 옮긴 파일.
// 월간 보기 (명세 3-5, 8)
//  - 날짜 칸: 실행률 막대 · Task 점(좁은 화면용) · 마일스톤 🏁
//  - 날짜를 누르면 아래 패널에 그날 실행률 · 이번 주 마감 · 시간순 Task (체크 가능)
//  - "일간 보기"를 누르면 그날 일간 보기 (웹의 두 번 누르기는 한 번 누르기와 헷갈려서 버튼만 남겼어요)
// 웹 원본: features/timetable/ui/MonthView.tsx

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** GET /timetable/monthly 상태 */
class CalendarMonthModel : ScreenModel() {
    var data by mutableStateOf<MonthlyTimetableResponse?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    /** 실패의 HTTP 상태 (calendar_month.load_fail · 0 = 네트워크) */
    var errorStatus by mutableStateOf<Int?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    private var offline = false

    suspend fun load(year: Int, month: Int) {
        if (offline) return
        loading = true
        try {
            val r = TimetableAPI.monthly(year = year, month = month)
            data = r
            failed = false
        } catch (c: CancellationException) {
            // 다음 달로 넘어갔어요
            loading = false
            throw c
        } catch (e: Exception) {
            failed = true
            errorStatus = CAL.status(e)
        }
        loading = false
    }

    companion object {
        /** 미리보기용 */
        fun sample(date: String): CalendarMonthModel {
            val m = CalendarMonthModel()
            m.offline = true
            m.data = TimetableSampleData.monthly(year = KST.yearOf(date), month = KST.monthOf(date))
            return m
        }
    }
}

/** 실행률 막대 색: 80% 이상 초록 · 50% 이상 파랑 · 그 아래 주황 */
@Composable
private fun calendarRateColor(rate: Int): Color =
    if (rate >= 80) NC.success else (if (rate >= 50) NC.brand else NC.warning)

@Composable
fun CalendarMonthView(
    model: CalendarMonthModel,
    board: TimetableBoardModel,
    /** 선택한 날짜 (이 날짜가 속한 달을 보여줘요) */
    date: String,
    onSelectDate: (String) -> Unit,
    onOpenDay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val year = KST.yearOf(date)
    val month = KST.monthOf(date)
    val today = KST.today()
    val scope = rememberCoroutineScope()
    // 이전 달 데이터가 잠깐 남아 있을 수 있어 해당 달인지 확인
    val d0 = model.data
    val current: MonthlyTimetableResponse? = if (d0 != null && d0.year == year && d0.month == month) d0 else null
    val byDate: Map<String, MonthlyDay> = (current?.days ?: emptyList()).associateBy { it.date }

    ReloadOn(listOf(DataKey.timetable), id = "$year-$month") { model.load(year = year, month = month) }
    /** 아래 시트로 연 날짜 (null = 닫힘) */
    var sheetDate by remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // 좌우 여백은 캘린더 화면(pageGutter)이 이미 줘요
            // 스펙 10.1: 제목 줄과 달력 카드 사이 12
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CalendarMonthGrid(
            year = year, month = month, date = date, today = today, byDate = byDate,
            loading = current == null && !model.failed,
            failed = model.failed && current == null,
            onRetry = {
                if (!board.isOffline) CAL.track("calendar_month.retry_click", mapOf("view_date" to date))
                scope.launch { model.load(year = year, month = month) }
            },
            onSelect = { d ->
                if (!board.isOffline) {
                    CAL.track(
                        "calendar_month.day_select",
                        mapOf("view_date" to d, "date_relation" to CAL.relation(d), "task_count" to (byDate[d]?.taskCount ?: 0)),
                    )
                }
                onSelectDate(d)
                // 스펙 9.9: 날짜를 누르면 그날 실행률 · 할 일을 아래 시트로
                sheetDate = d
            },
            onFailView = {
                if (!board.isOffline) CAL.track("calendar_month.load_fail", mapOf("view_date" to date, "error_status" to model.errorStatus))
            },
        )
        // 이 달 목표별 실천 시간 — 참고용 작은 막대 (2026-10). 미리보기(샘플 보드)는 불러오지 않아요
        if (!board.isOffline) {
            GoalTimeStats(
                start = KST.ymd(year, month, 1),
                end = KST.ymd(year, month, KST.lastDayOfMonth(year, month)),
                viewMode = "month",
                periodLabel = "${month}월",
            )
        }
    }

    // 그날 시트 (예전엔 달력 아래에 바로 보였어요)
    if (sheetDate != null) {
        NSheet(onDismiss = { sheetDate = null }) { _ ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    // 스펙 13.2: 시트 내용 좌우 24
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            ) {
                CalendarMonthDayPanel(
                    board = board, date = date, today = today, summary = byDate[date],
                    detail = board.days.firstOrNull { it.date == date },
                    loading = board.isFetching,
                    deadlines = current?.deadlinesThisWeek ?: emptyList(),
                    onOpenDay = { d -> sheetDate = null; onOpenDay(d) },
                )
            }
        }
    }
}

// MARK: - 달력

@Composable
private fun CalendarMonthGrid(
    year: Int,
    month: Int,
    date: String,
    today: String,
    byDate: Map<String, MonthlyDay>,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onSelect: (String) -> Unit,
    /** 실패 문구가 보일 때 (calendar_month.load_fail) */
    onFailView: () -> Unit = {},
) {
    val weeks = KST.monthWeeks(year, month)
    Column(Modifier.fillMaxWidth().card()) {
        // 스펙 8.5 MonthGrid: 요일 머리 h28 · Caption/MD · 아래 1dp border/soft
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until 7) {
                Text(
                    KST.weekdayLabel[i],
                    style = NType.captionMD,
                    color = if (i == 6) NC.calendarHoliday else (if (i == 5) NC.calendarSaturday else NC.textSecondary),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(NC.borderSoft))
        if (failed) {
            Column(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 280.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("월간 TimeTable을 불러오지 못했어요", style = pt(15, PTWeight.bold), color = NC.ink)
                NButton("다시 시도", variant = NButtonVariant.secondary, action = onRetry)
            }
            LaunchedEffect(Unit) { onFailView() }
        } else {
            Box {
                Column {
                    weeks.forEachIndexed { w, week ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).defaultMinSize(minHeight = 56.dp)) {
                            week.forEachIndexed { col, d ->
                                CalendarMonthDayCell(
                                    date = d, inMonth = KST.monthOf(d) == month, day = byDate[d],
                                    today = today, selected = d == date,
                                    lastColumn = col == week.size - 1, lastRow = w == weeks.size - 1,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                ) { onSelect(d) }
                            }
                        }
                    }
                }
                if (loading) {
                    Box(Modifier.matchParentSize().background(NC.surface.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NC.ink3, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthDayCell(
    date: String,
    inMonth: Boolean,
    day: MonthlyDay?,
    today: String,
    selected: Boolean,
    lastColumn: Boolean = false,
    lastRow: Boolean = false,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val wd = KST.weekdayIndex(date)
    val isToday = date == today
    val past = date <= today
    val numberColor = if (isToday) NC.calendarTodayFg else (if (!inMonth) NC.textPlaceholder else (if (wd == 6) NC.calendarHoliday else (if (wd == 5) NC.calendarSaturday else NC.textPrimary)))
    val info = if (inMonth) day else null
    // 그리기 블록 안에서는 NC 를 못 읽어서 밖에서 꺼내 둬요
    val line = NC.borderSoft
    val todayStroke = NC.textBrand
    val selectedStroke = NC.borderFocus
    val accessibility = run {
        var s = KST.formatMonthDay(date)
        if (day != null && day.taskCount > 0) s += " Task ${day.taskCount}개"
        val r = day?.rate
        if (r != null && date <= today) s += " 실행률 $r%"
        s
    }
    Column(
        modifier
            // 칸마다 둥근 모서리 · 배경 없음. 오늘만 ai/tint-soft 60% + 안쪽 2dp text/brand 테두리
            .background(if (isToday) NC.aiTintSoft.copy(alpha = 0.6f) else Color.Transparent)
            .drawBehind {
                val px = 1.dp.toPx()
                // 오른쪽 · 아래 1dp border/soft (마지막 열 · 마지막 줄은 없음)
                if (!lastColumn) drawRect(line, topLeft = Offset(size.width - px, 0f), size = Size(px, size.height))
                if (!lastRow) drawRect(line, topLeft = Offset(0f, size.height - px), size = Size(size.width, px))
                if (isToday || selected) {
                    val w = (if (isToday) 2f else 1.5f).dp.toPx()
                    drawRect(
                        if (isToday) todayStroke else selectedStroke,
                        topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w),
                    )
                }
            }
            .clickable(role = Role.Button, onClick = onTap)
            .semantics { contentDescription = accessibility }
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(22.dp).background(if (isToday) NC.calendarToday else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("${KST.dayOfMonth(date)}", style = NType.bodySMMedium, color = numberColor)
        }
        // 실행률: 지난 날 · 오늘만, Task 가 없으면 막대 없음 (h3 r2 · 트랙 teal tint · 채움 mint)
        val rate = info?.rate
        if (info != null && past && rate != null && info.taskCount > 0) {
            val barShape = RoundedCornerShape(2.dp)
            Box(Modifier.fillMaxWidth().height(3.dp).background(NC.progressTrack, barShape)) {
                Box(
                    Modifier
                        .fillMaxWidth((maxOf(rate, 4) / 100f).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(NC.progressFill, barShape)
                )
            }
        }
        // 스펙 9.9: Task 칩 최대 3개 (h14 · r3 · 목표 tint + 왼쪽 2dp 막대) + 나머지 "+N"
        if (info != null) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (c in info.chips.take(3)) {
                    CalendarMonthChip(c)
                }
                val more = maxOf(info.chips.size - 3, 0) + info.moreCount
                if (more > 0 || info.milestones.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (info.milestones.isNotEmpty()) Text("🏁", style = pt(8).copy(fontSize = 8.sp))
                        if (more > 0) Text("+$more", style = NType.captionSM, color = NC.textTertiary, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** 월간 칸 안 Task 칩 (h14 · r3 · 목표 tint + 왼쪽 2dp accent 막대 · 9sp 제목) */
@Composable
private fun CalendarMonthChip(c: MonthlyChip) {
    val tone = goalTone(c.colorCode)
    val shape = RoundedCornerShape(3.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .alpha(if (c.completed) 0.55f else 1f)
            .clip(shape)
            .background(tone.bg, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(tone.accent))
        Text(
            c.title,
            style = pt(9, PTWeight.medium).copy(lineHeight = 12.sp),
            color = tone.fg,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, end = 1.dp),
        )
    }
}

// MARK: - 선택한 날짜 패널

@Composable
private fun CalendarMonthDayPanel(
    board: TimetableBoardModel,
    date: String,
    today: String,
    summary: MonthlyDay?,
    /** 선택한 날짜의 상세 (GET /timetable?startDate=date&endDate=date) */
    detail: TimetableDay?,
    loading: Boolean,
    deadlines: List<MonthlyDeadline>,
    onOpenDay: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxWidth()
            // 스펙 10.2: 시트 안에서는 카드 테두리 · 그림자 없이 평평하게
            .padding(vertical = 4.dp)
            .semantics { contentDescription = "선택한 날짜" },
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(KST.formatMonthDay(date), style = NType.h4, color = NC.ink)
            if (date == today) Text("오늘", style = pt(13, PTWeight.bold), color = NC.textBrand)
            Spacer(Modifier.weight(1f))
            NButton("일간 보기", variant = NButtonVariant.secondary, size = NButtonSize.sm, icon = "calendar") {
                if (!board.isOffline) CAL.track("calendar_month.open_day_click", mapOf("view_date" to date))
                onOpenDay(date)
            }
        }

        // 실행률 상자
        val stats = detail?.stats
        val rate = stats?.rate
        val future = date > today
        Column(
            Modifier
                .fillMaxWidth()
                .background(NC.bgSunken, RoundedCornerShape(Radius.xl))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (future) "계획된 Task" else "실행률", style = NType.labelSM, color = NC.textSecondary)
                    Text(
                        if (future) "${stats?.total ?: summary?.taskCount ?: 0}개" else (rate?.let { "$it%" } ?: "–"),
                        style = ptMono(26, PTWeight.bold),
                        color = NC.ink,
                    )
                }
                if (stats != null && stats.total > 0) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("완료 ${stats.completed} / 전체 ${stats.total}", style = ptMono(12), color = NC.ink3)
                        if (stats.missed > 0) Text("놓침 ${stats.missed}", style = ptMono(12), color = NC.danger)
                        val c = stats.carriedOver
                        if (c != null && c > 0) Text("(다음 날로 넘김 $c 포함)", style = pt(11), color = NC.ink4)
                    }
                }
            }
            if (!future && rate != null) {
                ProgressBar(value = maxOf(rate, 3).toDouble() / 100, height = 6.dp)
            }
        }

        if (summary != null && summary.milestones.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("마일스톤", style = pt(12, PTWeight.semibold), color = NC.ink3)
                for (m in summary.milestones) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SfImage("flag.fill", size = 12.dp, tint = goalColor(m.colorCode))
                        Text(m.title, style = pt(14), color = NC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (deadlines.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("이번 주 마감", style = pt(12, PTWeight.semibold), color = NC.ink3)
                for (d in deadlines) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (d.type == "MILESTONE") {
                            SfImage("flag.fill", size = 12.dp, tint = goalColor(d.colorCode))
                        } else {
                            Box(Modifier.padding(horizontal = 2.dp).size(8.dp).background(goalColor(d.colorCode), CircleShape))
                        }
                        Text(d.title, style = pt(14), color = NC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(
                            KST.dDay(d.date, today = today),
                            style = ptMono(12, PTWeight.semibold),
                            color = if (d.date <= today) NC.danger else NC.ink3,
                        )
                    }
                }
            }
        }

        // 할 일 — 시간순 (시간 미정은 맨 뒤)
        val tasks = (detail?.tasks ?: emptyList()).sortedBy { TimetableLayout.shownTime(it)?.startAt ?: "" }
        val all = tasks + (detail?.unscheduledTasks ?: emptyList())
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("할 일", style = pt(12, PTWeight.semibold), color = NC.ink3)
            if (loading && detail == null) {
                Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 96.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NC.ink4, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
            } else if (all.isEmpty()) {
                Text(
                    "이날은 Task가 없어요.",
                    style = pt(14),
                    color = NC.ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NC.bgSunken, RoundedCornerShape(Radius.xl))
                        .padding(vertical = 24.dp),
                )
            } else {
                Column {
                    for (t in all) {
                        key(t.taskId) {
                            CalendarTaskCheckRow(
                                task = t, busy = board.togglingId == t.taskId, timeLeading = true,
                                onToggle = {
                                    if (!board.isOffline) {
                                        CAL.track(
                                            "calendar_month.task_check",
                                            mapOf(
                                                "task_id" to t.taskId,
                                                "check_action" to if (t.taskStatus == TaskStatus.COMPLETED) "uncomplete" else "complete",
                                                "date_relation" to CAL.relation(date),
                                            ),
                                        )
                                    }
                                    scope.launch { board.toggleComplete(t, closeOnSuccess = false) }
                                },
                                onSelect = {
                                    if (!board.isOffline) {
                                        CAL.track("calendar_month.task_click", mapOf("task_id" to t.taskId, "task_status" to t.taskStatus.rawValue))
                                    }
                                    board.selectTask(t, trackGrid = false)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun CalendarMonthViewPreview() {
    NaleulTheme(dark = false) {
        val d = KST.today()
        val board = remember { TimetableBoardModel.sample(start = d, end = d) }
        val model = remember { CalendarMonthModel.sample(d) }
        CalendarMonthView(
            model = model, board = board, date = d,
            onSelectDate = { }, onOpenDay = { },
            modifier = Modifier.canvasBackground(),
        )
    }
}
