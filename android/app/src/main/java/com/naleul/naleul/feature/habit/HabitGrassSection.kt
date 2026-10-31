// feature/habit/HabitGrassSection.kt
//
// iOS Features/Habit/HabitGrassSection.swift
// 웹 features/habit/ui/HabitHeatmap.tsx — 습관별 잔디 (한 달씩 ‹ › 로 넘겨 봐요).
// 세로는 요일(월~일), 가로는 그 달의 주. 습관을 추가한 달부터 볼 수 있어요 (최근 12달까지 — 서버 최대 400일).
// 전체를 합친 잔디는 없어요: 습관마다 시작한 날이 달라서 합치면 "언제부터 했는지"가 안 보여요.
//
// iOS 의 HabitGrassSection.cell / .gap / .firstOfMonth(…) 같은 static 값은
// 같은 이름의 object HabitGrassSection 에 두었어요 (컴포저블 함수 HabitGrassSection(...) 과 이름이 같아도 괜찮아요).

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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** iOS HabitGrassSection 의 static 값들 */
object HabitGrassSection {
    /** 칸 크기 · 간격 */
    val cell: Dp = 18.dp
    val gap: Dp = 3.dp
    /** 최근 12달 + 이번 달 */
    const val maxMonthsBack = 12

    fun firstOfMonth(ymd: String): String {
        val p = KST.parts(ymd)
        return KST.ymd(year = p.y, month = p.m, day = 1)
    }

    fun lastOfMonth(ymd: String): String {
        val p = KST.parts(ymd)
        return KST.ymd(year = p.y, month = p.m, day = KST.lastDayOfMonth(year = p.y, month = p.m))
    }
}

@Composable
fun HabitGrassSection(model: HabitModel, modifier: Modifier = Modifier) {
    var month by remember { mutableStateOf(HabitGrassSection.firstOfMonth(KST.today())) }

    val today = KST.today()
    val thisMonth = HabitGrassSection.firstOfMonth(today)

    /** 지운 습관은 빼고, 쉬는 습관은 아래로 (순서는 그대로) */
    val shown: List<Habit> = run {
        val live = model.habits.filter { it.status != HabitStatus.DELETED }
        live.filter { !it.isPaused } + live.filter { it.isPaused }
    }

    /** 가장 먼저 시작한 습관의 그 달 1일부터 (최근 12달까지) */
    val from: String = run {
        val limit = KST.addMonths(thisMonth, -HabitGrassSection.maxMonthsBack)
        var earliest = today
        for (h in shown) if (h.startDate < earliest) earliest = h.startDate
        val first = HabitGrassSection.firstOfMonth(earliest)
        if (first < limit) limit else first
    }

    /** 그 달을 월요일부터 시작하는 주(열)로 — 그 달이 아닌 날은 null */
    val weeks: List<List<String?>> = run {
        val p = KST.parts(month)
        val last = HabitGrassSection.lastOfMonth(month)
        KST.monthWeeks(year = p.y, month = p.m).map { week ->
            week.map { d -> if (d >= month && d <= last) d else null }
        }
    }

    val list = shown
    val start = from
    ReloadOn(listOf(DataKey.habits), id = start) {
        if (list.isNotEmpty()) model.loadHeatmap(from = start)
    }

    if (list.isEmpty()) return

    // 볼 수 있는 달 밖이면 맞춰요 (iOS .onAppear)
    LaunchedEffect(start, thisMonth) {
        if (month < start) month = start
        if (month > thisMonth) month = thisMonth
    }

    val canPrev = month > start
    val canNext = month < thisMonth
    fun trackMonth(action: String) {
        val a = KST.parts(month)
        val b = KST.parts(thisMonth)
        model.track("habit.heatmap_month_click", mapOf("date_action" to action, "months_ago" to (b.y - a.y) * 12 + (b.m - a.m)))
    }
    val sameYear = KST.yearOf(month) == KST.yearOf(today)
    val monthTitle = (if (sameYear) "" else "${KST.yearOf(month)}년 ") + "${KST.monthOf(month)}월"

    Column(
        modifier
            .fillMaxWidth()
            .card()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("습관별 잔디", style = NType.h4, color = NC.ink)
            Spacer(Modifier.weight(1f))
            NIconButton(
                "chevron.left", size = 15.dp, color = if (canPrev) NC.ink3 else NC.ink4, label = "지난달", enabled = canPrev,
            ) {
                if (canPrev) {
                    month = KST.addMonths(month, -1)
                    trackMonth("prev")
                }
            }
            Text(
                monthTitle,
                style = ptMono(15, PTWeight.bold),
                color = NC.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 44.dp),
            )
            NIconButton(
                "chevron.right", size = 15.dp, color = if (canNext) NC.ink3 else NC.ink4, label = "다음 달", enabled = canNext,
            ) {
                if (canNext) {
                    month = KST.addMonths(month, 1)
                    trackMonth("next")
                }
            }
            if (month != thisMonth) {
                Text(
                    "이번 달",
                    style = pt(13, PTWeight.semibold),
                    color = NC.textBrand,
                    modifier = Modifier.clickable(role = Role.Button) {
                        month = thisMonth
                        trackMonth("today")
                    },
                )
            }
        }

        val hm = model.heatmap
        if (hm != null) {
            Column(Modifier.fillMaxWidth()) {
                list.forEachIndexed { i, h ->
                    key(h.habitId) {
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(NC.line))
                        HabitGrassRow(
                            habit = h,
                            today = today,
                            month = month,
                            weeks = weeks,
                            states = hm.states(of = h.habitId),
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        } else if (model.heatmapFailed) {
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 100.dp), contentAlignment = Alignment.Center) {
                Text("잔디를 불러오지 못했어요.", style = pt(14), color = NC.ink3)
            }
        } else {
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NC.ink3, strokeWidth = 2.5.dp)
            }
        }

        // 범례
        val brand = NC.brand
        val lineStrong = NC.lineStrong
        val subtle = NC.subtle
        FlowLayout(spacing = 12.dp) {
            HabitGrassLegend("했어요") { Box(Modifier.fillMaxSize().background(brand, RoundedCornerShape(3.dp))) }
            HabitGrassLegend("놓쳤어요") { Box(Modifier.fillMaxSize().background(lineStrong, RoundedCornerShape(3.dp))) }
            HabitGrassLegend("안 하는 날") { Box(Modifier.fillMaxSize().background(subtle, RoundedCornerShape(3.dp))) }
            HabitGrassLegend("쉬는 중") { HabitPausedSwatch(radius = 3.dp) }
        }
    }
}

@Composable
private fun HabitGrassLegend(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(11.dp)) { swatch() }
        Text(label, style = pt(11), color = NC.ink3)
    }
}

// MARK: - 습관 한 줄 (왼쪽 이름 · 기록, 오른쪽 그 달 잔디)

@Composable
private fun HabitGrassRow(
    habit: Habit,
    today: String,
    month: String,
    weeks: List<List<String?>>,
    states: Map<String, HabitDayState>,
    modifier: Modifier = Modifier,
) {
    fun stateAt(d: String): HabitDayState {
        if (d > today) return HabitDayState.FUTURE
        if (d < habit.startDate) return HabitDayState.BEFORE_START
        return states[d] ?: HabitDayState.BEFORE_START
    }

    val days: List<String> = weeks.flatMap { w -> w.filterNotNull() }
    val cells: List<HabitDayState> = days.map { stateAt(it) }
    val doneDays = cells.count { it == HabitDayState.DONE }
    val dueDays = cells.count { it == HabitDayState.DONE || it == HabitDayState.MISSED || it == HabitDayState.TODO }
    val notYet = habit.startDate > HabitGrassSection.lastOfMonth(month)

    Row(
        modifier.fillMaxWidth().alpha(if (habit.isPaused) 0.6f else 1f),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(130.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(habit.title, style = pt(14, PTWeight.bold), color = NC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (notYet) "아직 시작 전" else (if (dueDays > 0) "이 달 $doneDays/${dueDays}일" else "이 달 할 날 없음"),
                style = ptMono(12),
                color = NC.ink3,
            )
            Text(
                "${KST.monthOf(habit.startDate)}월 ${KST.dayOfMonth(habit.startDate)}일부터",
                style = pt(11),
                color = NC.ink4,
            )
            if (habit.streak > 1) {
                Text(
                    "🔥 ${habit.streak}일 연속",
                    style = pt(12, PTWeight.semibold),
                    color = NC.statusWarningFg,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (habit.isPaused) {
                Text("쉬는 중", style = pt(12), color = NC.ink3)
            }
        }

        // 잔디
        val size = HabitGrassSection.cell
        val gap = HabitGrassSection.gap
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(gap)) {
            // 요일 눈금 (월 · 수 · 금 · 일)
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(gap)) {
                for (i in 0 until 7) {
                    Box(Modifier.width(12.dp).height(size), contentAlignment = Alignment.CenterEnd) {
                        Text(if (i % 2 == 0) KST.weekdayLabel[i] else "", style = pt(10), color = NC.ink4)
                    }
                }
            }
            for (week in weeks) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (di in 0 until 7) {
                        val d = week.getOrNull(di)
                        if (d != null) {
                            HabitGrassCell(state = stateAt(d), isToday = d == today)
                        } else {
                            Spacer(Modifier.size(size))
                        }
                    }
                }
            }
        }
    }
}

// MARK: - 잔디 한 칸

@Composable
fun HabitGrassCell(
    state: HabitDayState,
    isToday: Boolean = false,
    size: Dp = HabitGrassSection.cell,
    radius: Dp = 3.dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(radius)
    val todayRing = NC.ink2
    val base = modifier
        .size(size)
        .drawWithContent {
            drawContent()
            if (isToday) {
                // 칸 바깥 2 만큼 테두리 (iOS .overlay { … .padding(-2) })
                val o = 2.dp.toPx()
                val w = 1.dp.toPx()
                drawRoundRect(
                    color = todayRing,
                    topLeft = Offset(-o + w / 2, -o + w / 2),
                    size = Size(this.size.width + 2 * o - w, this.size.height + 2 * o - w),
                    cornerRadius = CornerRadius((radius + 1.5.dp).toPx()),
                    style = Stroke(width = w),
                )
            }
        }
    when (state) {
        HabitDayState.DONE -> Box(base.background(NC.brand, shape))
        HabitDayState.MISSED -> Box(base.background(NC.lineStrong, shape))
        HabitDayState.TODO -> Box(base.background(NC.surface, shape).border(1.5.dp, NC.brand, shape))
        HabitDayState.OFF -> Box(base.background(NC.subtle, shape))
        HabitDayState.PAUSED -> Box(base) { HabitPausedSwatch(radius = radius) }
        HabitDayState.BEFORE_START -> Box(base.background(NC.subtle.copy(alpha = 0.4f), shape))
        HabitDayState.FUTURE -> Box(base.border(1.dp, NC.borderSubtle, shape))
    }
}

/** 쉬는 중 칸 — 연한 바탕 + 빗금 + 테두리 (주어진 자리를 꽉 채워요) */
@Composable
fun HabitPausedSwatch(radius: Dp = 3.dp, modifier: Modifier = Modifier) {
    // Canvas 안에서는 NC 색을 못 읽어서 바깥에서 꺼내 둬요
    val hatch = NC.hatch
    val hatchLine = NC.hatchLine
    Canvas(modifier.fillMaxSize()) {
        val r = CornerRadius(radius.toPx())
        drawRoundRect(color = hatch, cornerRadius = r)
        val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) }
        clipPath(clip) {
            val step = 4.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(hatchLine, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.dp.toPx())
                x += step
            }
        }
        val w = 1.dp.toPx()
        drawRoundRect(
            color = hatchLine,
            topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w),
            cornerRadius = r,
            style = Stroke(width = w),
        )
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun HabitGrassSectionPreview() {
    NaleulTheme(dark = false) {
        val m = remember { HabitModel.preview() }
        Column(Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
            HabitGrassSection(model = m)
        }
    }
}
