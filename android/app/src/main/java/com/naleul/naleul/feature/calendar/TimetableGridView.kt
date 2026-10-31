// feature/calendar/TimetableGridView.kt
//
// iOS Features/Calendar/TimetableGridView.swift 를 옮긴 파일.
// TimeTable 격자 — 날짜 머리글 · 시간 미정 줄 · 시간 눈금 · 날짜 칸 · 블록 · 드래그.
// 웹 원본: features/timetable/ui/TimeGrid.tsx + useGridDrag.ts
//
// 배치
//  ┌ 날짜 머리글 (고정) ─────────────────┐
//  ├ 시간 미정 줄 (고정, 있을 때만) ──────┤   ← 여기로 끌어 놓으면 "시간 미정"
//  ├ 세로 스크롤 ──────────────────────────┤
//  │  시간 눈금 | 월 | 화 | … (시간당 56dp) │
//  └────────────────────────────────────┘
//
// 드래그 (웹: 마우스 4px · 터치 0.35초 길게 누르기 → 휴대폰은 "길게 눌러 끌기"만)
//  - 블록을 0.35초 길게 누르면(진동) 끌 수 있어요. 10분 단위로 맞춰져요. 주간에서는 옆 날짜 칸으로도 옮겨요.
//  - 블록 아래 끝(높이 26dp 이상) · 위 끝(44dp 이상)을 길게 눌러 끌면 끝 · 시작 시각만 바뀌어요.
//  - 수면 블록은 "☀️ 기상 / 🌙 취침" 띠를 끌어 기상 · 취침 시각을 바꿔요.
//  - 좌표는 머리글~격자 전체를 감싼 바깥 Column(root) 기준이에요 (iOS TimetableGeo.space).
//    블록 안 손가락 위치를 root 좌표로 바꿔서(localPositionOf) 계산하니 스크롤 · 블록 이동과 상관없이 정확해요.
//  - 끄는 동안에는 스크롤을 잠가요 (손가락 움직임이 스크롤이 아니라 블록 이동이 되게).
//  - 손가락이 위 · 아래 가장자리에 가까우면 자동으로 스크롤해요.
//
// ⚠️ 이 격자는 안에 세로 스크롤이 있어서, 부르는 쪽이 높이를 정해 줘야 해요
//    (예: Modifier.weight(1f) / .height(520.dp) / .fillMaxSize()). 바깥 verticalScroll 안에 높이 없이 넣으면 앱이 죽어요.

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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// MARK: - 격자 치수 · 좌표 → 시각

/** 모든 길이는 dp 값(Float) 이에요 */
data class TimetableGridMetrics(
    val width: Float,
    val dates: List<String>,
    /** 화면 시작 · 끝 (그날 0시 기준 분) */
    val from: Int,
    val to: Int,
    val headerHeight: Float,
    /** 시간 미정 줄 높이 (없으면 0) */
    val unscheduledHeight: Float,
) {
    val showUnscheduled: Boolean get() = unscheduledHeight > 0f
    val dayCount: Int get() = max(dates.size, 1)
    val colWidth: Float get() = max(0f, (width - TimetableGeo.labelWidth) / dayCount)
    val height: Float get() = (to - from) * TimetableGeo.ppm

    fun columnX(i: Int): Float = TimetableGeo.labelWidth + i * colWidth

    /** 그날 0시 기준 분 → 격자 안 y */
    fun y(minute: Int): Float = (minute - from) * TimetableGeo.ppm

    /** root x → 날짜 칸 번호 (칸 밖이면 가장 가까운 칸) */
    fun columnIndex(x: Float): Int {
        if (colWidth <= 0f || dates.isEmpty()) return 0
        val raw = floor(((x - TimetableGeo.labelWidth) / colWidth).toDouble()).toInt()
        return min(max(raw, 0), dates.size - 1)
    }

    /** root y → 그날 0시 기준 분 (소수) */
    fun minute(atY: Float, gridTop: Float): Double = from.toDouble() + ((atY - gridTop) / TimetableGeo.ppm).toDouble()

    /**
     * 손가락 위치 → 놓을 자리 (웹 useGridDrag compute)
     * - translationY: 손가락이 처음 누른 곳에서 세로로 움직인 거리 (격자 기준).
     *   이미 시간이 있는 블록은 "원래 시각 + 움직인 거리"로 계산해요 → 스크롤 위치와 상관없이 손가락을 그대로 따라와요.
     *   시간 미정 칩처럼 원래 시각이 없을 때만 손가락의 절대 위치(gridTop 기준)를 써요.
     */
    fun target(st: TimetableDragState, p: Offset, translationY: Float?, gridTop: Float): TimetableDropTarget? {
        if (dates.isEmpty()) return null
        val src = st.source
        // ① "시간 미정" 줄 위
        val unscheduledTop = headerHeight
        if (st.mode == TimetableDragMode.move && src.canUnschedule && showUnscheduled &&
            p.y >= unscheduledTop && p.y <= unscheduledTop + unscheduledHeight
        ) {
            return TimetableDropTarget.unscheduled(date = dates[columnIndex(p.x)])
        }
        // ② 날짜 칸 (고정 시간 · 크기 조절은 원래 날짜에서만)
        // 실제로 한 시간은 그 날 안에서만 옮겨요 (다른 날로 옮기면 "언제 했는지"가 바뀌어 버려요)
        val sameDay = src.isFixed || src.isActual || st.mode != TimetableDragMode.move
        var index = columnIndex(p.x)
        if (sameDay) {
            val i = dates.indexOf(src.date)
            if (i >= 0) index = i
        }
        val date = dates[index]
        val m = minute(atY = p.y, gridTop = gridTop)
        /** 움직인 거리 → 분 */
        val dm: Double? = translationY?.let { (it / TimetableGeo.ppm).toDouble() }

        return when (st.mode) {
            TimetableDragMode.resize -> {
                val start = src.start ?: from
                val raw = dm?.let { (src.end ?: start).toDouble() + it } ?: m
                val end = min(to, max(start + KST.snapMinutes, TimetableDrag.snap(raw)))
                TimetableDropTarget.grid(date = date, start = start, end = end)
            }
            TimetableDragMode.resizeTop -> {
                // 끝은 그대로 두고 시작 시각만 움직여요 (최소 10분은 남김)
                val end = src.end ?: to
                val raw = dm?.let { (src.start ?: end).toDouble() + it } ?: m
                val start = max(from, min(end - KST.snapMinutes, TimetableDrag.snap(raw)))
                TimetableDropTarget.grid(date = date, start = start, end = end)
            }
            TimetableDragMode.move -> {
                val dur = src.duration
                // 시간 미정 칩: 블록 위쪽 조금 아래를 잡은 것으로
                val grab = st.grab ?: min(dur.toDouble() / 2, 30.0)
                // 묶음이면 묶음 전체가 격자 안에 들어오게
                var before = 0
                var after = dur
                val s0 = src.start
                if (s0 != null) {
                    for (g in src.group) {
                        before = max(before, s0 - g.start)
                        after = max(after, g.end - s0)
                    }
                }
                val lo = from + before
                // 시간이 있는 블록: 원래 시작 + 움직인 거리 (잡은 지점이 손가락 아래에 그대로 남아요)
                val raw: Double = if (s0 != null && dm != null) s0.toDouble() + dm else m - grab
                val start = min(max(TimetableDrag.snap(raw), lo), max(lo, to - after))
                TimetableDropTarget.grid(date = date, start = start, end = start + dur)
            }
        }
    }

    companion object {}
}

/** 격자 전체에서 한 번만 계산해 칸들에 나눠 주는 표시 상태 */
data class TimetableGridFlags(
    val selectedTaskId: Int?,
    val selectedFixedKey: String?,
    val selectedActivityId: Int?,
    val focusIds: Set<Int>,
    val groupIds: Set<Int>,
    /** AI 배치 미리보기 중 — 제안 말고는 흐리게 */
    val fadeOthers: Boolean,
    val highlightIds: Set<Int>,
    val drag: TimetableDragState?,
    /** 길게 눌러 들어 올린 블록 (owner id) */
    val liftedOwner: String?,
) {
    /** 블록을 들어 올렸거나 끄는 중 → 나머지는 50% 로 흐리게 */
    val dimOthers: Boolean get() = drag != null || liftedOwner != null

    /** 들어 올렸거나 끄는 게 "실제로 한 시간" 블록인지 (그 블록이 있는 실제 칸 전체를 흐리게 하지 않으려고) */
    val actualActive: Boolean get() = drag?.source?.isActual == true || liftedOwner?.startsWith("a-") == true

    /** 이 블록(또는 그 손잡이)이 들어 올린 블록인지 */
    fun isLifted(owner: String): Boolean {
        val l = liftedOwner ?: return false
        return l == owner || l.startsWith("$owner-")
    }

    companion object {}
}

/** 드래그 중 손가락 위치 (root 기준 dp) — iOS DragGesture.Value 대신 */
data class TimetableDragValue(
    val location: Offset,
    /** 들어 올린 순간의 손가락 위치 */
    val startLocation: Offset,
) {
    val translation: Offset get() = location - startLocation

    companion object {}
}

/**
 * 스크롤해도 화면을 다시 그리지 않도록 일반 클래스에 담아 둬요 (드래그 계산 때만 읽어요)
 * 길이는 모두 dp 값이에요.
 */
class TimetableScrollTracker {
    /** 머리글~격자 전체를 감싼 바깥 Column 의 좌표 (드래그 좌표 기준) */
    var root: LayoutCoordinates? = null
    var scrollState: ScrollState? = null
    var density: Float = 1f
    /** 스크롤 영역(보이는 격자)의 높이 */
    var viewportHeight: Float = 0f
    /** 끌기 시작할 때의 "시간 미정" 줄 높이 — 끄는 동안 줄이 생기면 격자가 아래로 밀린 만큼 빼 줘요 */
    var dragStartUnscheduledHeight: Float = 0f
    /** 끌기 시작할 때 스크롤 위치 — 자동 스크롤로 내려간 만큼 블록도 같이 내려가요 */
    var dragStartOffset: Float = 0f
    /** 마지막 손가락 정보 (손가락이 멈춰 있어도 자동 스크롤 중에 자리를 다시 계산해요) */
    var lastValue: TimetableDragValue? = null
    var lastMetrics: TimetableGridMetrics? = null

    /** 지금 스크롤 위치 (맨 위 = 0, dp) */
    val currentOffset: Float get() = (scrollState?.value ?: 0) / density

    /** 지금 격자 맨 위가 root 의 어디인지 */
    fun gridTop(m: TimetableGridMetrics): Float = m.headerHeight + m.unscheduledHeight + 1f - currentOffset

    /** 블록 안 위치(px) → root 기준 dp */
    fun toRoot(block: LayoutCoordinates?, local: Offset): Offset? {
        val r = root ?: return null
        val b = block ?: return null
        if (!r.isAttached || !b.isAttached) return null
        val p = r.localPositionOf(b, local)
        return Offset(p.x / density, p.y / density)
    }

    fun stopAutoScroll() {
        lastValue = null
    }

    companion object {}
}

/** (끄는 대상, 방식, 블록 owner id, 손가락 위치) */
typealias TimetableDragHandler = (TimetableDragSource, TimetableDragMode, String, TimetableDragValue) -> Unit

/** 블록 좌표를 담아 두는 상자 (제스처 코루틴에서 읽어요) */
private class TimetableCoordsHolder {
    var value: LayoutCoordinates? = null
}

// MARK: - 격자

@Composable
fun TimetableGridView(
    board: TimetableBoardModel,
    days: List<TimetableDay>,
    activities: List<ActualActivity> = emptyList(),
    startHour: Int,
    endHour: Int,
    /** 일간: 칸을 "계획 | 실제"로 나눠요 */
    planVsActual: Boolean = false,
    /** "NEW" 표시할 Task (방금 만든 것) */
    highlightIds: Set<Int> = emptySet(),
    /** 주간에서 날짜 머리글 누름 → 일간 */
    onDayClick: ((String) -> Unit)? = null,
    /** 빈 칸(지난 시간)을 누르면 실제로 한 일 기록 */
    emptyClickEnabled: Boolean = false,
    /** 주면: 빈 칸을 누르면 (날짜, 30분 단위 시각) 으로 이걸 불러요 — 캘린더 + 메뉴 (미래 시간도) */
    onEmptyTap: ((String, Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    val tracker = remember { TimetableScrollTracker() }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var scrolledFor by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current.density
    val haptic = LocalHapticFeedback.current
    tracker.scrollState = scrollState
    tracker.density = density

    val rangeKey = "${days.firstOrNull()?.date ?: ""}_${days.size}"

    BoxWithConstraints(modifier) {
        val width = maxWidth.value
        val m = timetableGridMetrics(board, days, expanded, width, startHour, endHour, planVsActual)
        val flags = TimetableGridFlags(
            selectedTaskId = board.selectedTaskId,
            selectedFixedKey = board.selectedFixedKey,
            selectedActivityId = board.selectedActivityId,
            focusIds = board.focusIds,
            groupIds = board.groupIds,
            fadeOthers = board.proposing,
            highlightIds = highlightIds,
            drag = board.drag,
            liftedOwner = board.liftedOwnerId,
        )
        val drag: TimetableDragHandler = { s, mode, owner, v -> timetableDragChanged(board, tracker, haptic, s, mode, owner, v, m) }
        val dragEnd: (String) -> Unit = { owner -> board.endDrag(ownerId = owner) }

        Column(Modifier.fillMaxSize().onGloballyPositioned { tracker.root = it }) {
            TimetableHeaderRow(days = days, metrics = m, planVsActual = planVsActual, onDayClick = onDayClick)
            if (m.showUnscheduled) {
                TimetableUnscheduledRow(
                    board = board, days = days, metrics = m, flags = flags, tracker = tracker, haptic = haptic,
                    expanded = expanded, onExpand = { d -> expanded = expanded + d },
                    onDrag = drag, onDragEnd = dragEnd,
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(NC.line))
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { tracker.viewportHeight = it.height / density }
                    .verticalScroll(scrollState, enabled = board.drag == null),
            ) {
                Box(Modifier.size(m.width.dp, m.height.dp)) {
                    TimetableHourBackground(startHour = startHour, endHour = endHour, width = m.width)
                    TimetableNowLabel(dates = days.map { it.date }, from = m.from, to = m.to, modifier = Modifier.zIndex(5f))
                    days.forEachIndexed { i, d ->
                        key(d.date) {
                            TimetableDayColumn(
                                board = board, day = d, metrics = m, flags = flags, activities = activities,
                                single = days.size == 1, planVsActual = planVsActual,
                                emptyClickEnabled = emptyClickEnabled, onEmptyTap = onEmptyTap, tracker = tracker, haptic = haptic,
                                onDrag = drag, onDragEnd = dragEnd,
                                modifier = Modifier.offset(x = m.columnX(i).dp).size(m.colWidth.dp, m.height.dp),
                            )
                        }
                    }
                }
                footer()
            }
        }

        // 끄는 중 가장자리 자동 스크롤 (손가락이 격자 위 · 아래 가장자리 70dp 안에 있으면)
        val dragging = board.drag != null
        LaunchedEffect(dragging) {
            if (!dragging) {
                tracker.stopAutoScroll()
                return@LaunchedEffect
            }
            while (true) {
                withFrameNanos { }
                timetableAutoScrollStep(board, tracker, haptic)
            }
        }

        // 처음 보거나 기간이 바뀌면: 오늘이 보이면 현재 시각 근처로, 아니면 맨 위로
        LaunchedEffect(rangeKey) {
            if (days.isEmpty() || scrolledFor == rangeKey) return@LaunchedEffect
            scrolledFor = rangeKey
            val now = KST.now()
            val hasToday = days.any { it.date == now.date }
            // 스펙 9.6: 오늘이면 지금 시각이 위에서 1/3 쯤, 아니면 첫 블록 (없으면 08:00)
            val targetMin: Int = if (hasToday) {
                now.minutes
            } else {
                var first: Int? = null
                for (d in days) {
                    for (t in d.tasks) {
                        val r = TimetableLayout.shownMinutes(d.date, t) ?: continue
                        first = min(first ?: r.start, r.start)
                    }
                }
                first ?: (8 * 60)
            }
            // 내용이 아직 그려지기 전이면 스크롤 끝(maxValue)이 0 이라 잠깐 기다려요
            withTimeoutOrNull(1_000) { snapshotFlow { scrollState.maxValue }.first { it > 0 } }
            val yDp = (targetMin - startHour * 60) * TimetableGeo.ppm
            val offsetDp = if (hasToday) tracker.viewportHeight / 3f else 16f
            scrollState.scrollTo(max(0f, (yDp - offsetDp) * density).toInt())
        }

        // 확인을 기다리는 블록이 생기면 그 블록이 보이게 스크롤
        val focusKey = if (flags.focusIds.isEmpty()) "" else flags.focusIds.sorted().joinToString(",") + "|" + rangeKey
        LaunchedEffect(focusKey) {
            if (flags.focusIds.isEmpty()) return@LaunchedEffect
            var best: Int? = null
            for (d in days) {
                for (t in d.tasks) {
                    if (!flags.focusIds.contains(t.taskId)) continue
                    val r = TimetableLayout.shownMinutes(d.date, t) ?: continue
                    best = min(best ?: r.start, r.start)
                }
            }
            val b = best ?: return@LaunchedEffect
            val hour = min(max(floor(b.toDouble() / 60).toInt() - 1, startHour), endHour - 1)
            withTimeoutOrNull(1_000) { snapshotFlow { scrollState.maxValue }.first { it > 0 } }
            scrollState.animateScrollTo(((hour - startHour) * TimetableGeo.hourHeight * density).toInt())
        }
    }
}

/** 치수 계산 (iOS metrics) */
private fun timetableGridMetrics(
    board: TimetableBoardModel,
    days: List<TimetableDay>,
    expanded: Set<String>,
    width: Float,
    startHour: Int,
    endHour: Int,
    planVsActual: Boolean,
): TimetableGridMetrics {
    // Task 를 끌고 있으면 "시간 미정"으로 놓을 수 있게 빈 줄이라도 보여줘요
    val show = days.any { it.unscheduledTasks.isNotEmpty() } || (board.drag?.source?.canUnschedule ?: false)
    var uh = 0f
    if (show) {
        var maxShown = 0
        var anyMore = false
        for (d in days) {
            val n = d.unscheduledTasks.size
            val shown = if (expanded.contains(d.date)) n else min(n, TimetableGeo.unscheduledVisible)
            maxShown = max(maxShown, shown)
            if (shown < n) anyMore = true
        }
        val natural = 10f + maxShown * (TimetableGeo.chipHeight + 4f) + (if (anyMore) 18f else 0f)
        uh = min(TimetableGeo.maxUnscheduledHeight, max(40f, natural))
    }
    val header = if (days.size == 1) (if (planVsActual) TimetableGeo.dayHeaderHeight else 52f) else TimetableGeo.weekHeaderHeight
    return TimetableGridMetrics(
        width = width, dates = days.map { it.date }, from = startHour * 60, to = endHour * 60,
        headerHeight = header, unscheduledHeight = uh,
    )
}

// MARK: 드래그

private fun timetableDragChanged(
    board: TimetableBoardModel,
    tracker: TimetableScrollTracker,
    haptic: HapticFeedback,
    source: TimetableDragSource,
    mode: TimetableDragMode,
    owner: String,
    v: TimetableDragValue,
    m: TimetableGridMetrics,
) {
    val top = tracker.gridTop(m)
    if (board.drag?.ownerId != owner) {
        tracker.dragStartUnscheduledHeight = m.unscheduledHeight
        tracker.dragStartOffset = tracker.currentOffset
        // 잡은 지점: 블록 위쪽에서 몇 분 아래를 잡았는지 (시간 미정 칩을 격자로 끌 때 씀)
        var grab: Double? = null
        val s = source.start
        if (mode == TimetableDragMode.move && s != null) {
            grab = max(0.0, m.minute(atY = v.startLocation.y, gridTop = top) - s)
        }
        if (board.beginDrag(source = source, mode = mode, grab = grab, ownerId = owner)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    tracker.lastValue = v
    tracker.lastMetrics = m
    timetableUpdateTarget(board, tracker, haptic, v, m)
}

/** 손가락 위치 + 스크롤 위치로 놓을 자리 계산 */
private fun timetableUpdateTarget(
    board: TimetableBoardModel,
    tracker: TimetableScrollTracker,
    haptic: HapticFeedback,
    v: TimetableDragValue,
    m: TimetableGridMetrics,
) {
    val st = board.drag ?: return
    // 끌기 시작하면서 "시간 미정" 줄이 새로 생기면 격자가 그만큼 아래로 밀려요 → 손가락과 어긋나지 않게 빼 줘요
    val pushed = m.unscheduledHeight - tracker.dragStartUnscheduledHeight
    // 자동 스크롤로 내려간 만큼 블록도 같이 내려가요
    val scrolled = tracker.currentOffset - tracker.dragStartOffset
    val dy: Float? = if (st.source.start != null) v.translation.y - pushed + scrolled else null
    val moved = board.updateDrag(target = m.target(st, v.location, translationY = dy, gridTop = tracker.gridTop(m)))
    if (moved) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
}

/** 손가락이 격자 위 · 아래 가장자리(70dp)에 가까울수록 빠르게 스크롤해요 (한 프레임마다) */
private fun timetableAutoScrollStep(board: TimetableBoardModel, tracker: TimetableScrollTracker, haptic: HapticFeedback) {
    if (board.drag == null) return
    val sv = tracker.scrollState ?: return
    val v = tracker.lastValue ?: return
    val m = tracker.lastMetrics ?: return
    val visTop = m.headerHeight + m.unscheduledHeight + 1f
    val visBottom = visTop + tracker.viewportHeight
    val edge = 70f
    val maxSpeed = 14f
    val y = v.location.y
    var speed = 0f
    if (y > visBottom - edge) {
        speed = min((y - (visBottom - edge)) / edge, 1.4f) * maxSpeed
    } else if (y >= visTop && y < visTop + edge) {
        // "시간 미정" 줄 위에서는 스크롤하지 않아요 (거기에 놓으려는 거라서)
        speed = -min((visTop + edge - y) / edge, 1.4f) * maxSpeed
    }
    if (speed == 0f) return
    val before = sv.value
    sv.dispatchRawDelta(speed * tracker.density)
    if (abs(sv.value - before) < 1) return
    timetableUpdateTarget(board, tracker, haptic, v, m)
}

// MARK: - 길게 눌러 끌기 + 탭 (블록 공통 제스처)

/**
 * 길게 누르면 끌기, 짧게 누르면 탭. 끄는 중이 아니면 바깥 세로 스크롤은 평소처럼 스크롤돼요.
 *  - 누른 채 holdSeconds 안에 손가락이 움직이면(스크롤) 아무것도 안 해요
 *  - holdSeconds 동안 가만히 누르면 "들어 올림"(onLift) → 그 뒤 움직이면 onChanged, 떼면 onEnded
 *  - 들어 올린 뒤 손을 뗀 건 탭(상세 열기)으로 치지 않아요
 *  - canLift() 가 false 면 들어 올리지 않아요 (안쪽 손잡이가 먼저 들어 올렸을 때)
 * 좌표는 tracker.root 기준 dp 로 바꿔서 넘겨요.
 */
@Composable
private fun Modifier.timetableBlockGesture(
    enabled: Boolean,
    tracker: TimetableScrollTracker,
    holdSeconds: Double = TimetableGeo.holdSeconds,
    canLift: () -> Boolean = { true },
    onTap: () -> Unit,
    onChanged: (TimetableDragValue) -> Unit,
    onEnded: () -> Unit,
    onCancelled: () -> Unit,
    onLift: () -> Unit = {},
    /** 들어 올린 뒤에도 움직이지 않고 2초까지 누르고 있으면 (묶음 고르기 시작) — null 이면 없음 */
    onGroupHold: (() -> Unit)? = null,
): Modifier {
    val holder = remember { TimetableCoordsHolder() }
    val groupHold by rememberUpdatedState(onGroupHold)
    val tap by rememberUpdatedState(onTap)
    val changed by rememberUpdatedState(onChanged)
    val ended by rememberUpdatedState(onEnded)
    val cancelled by rememberUpdatedState(onCancelled)
    val lift by rememberUpdatedState(onLift)
    val mayLift by rememberUpdatedState(canLift)
    val holdMs = (holdSeconds * 1000).toLong()
    return this
        .onGloballyPositioned { holder.value = it }
        .pointerInput(enabled, holdMs) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!enabled) {
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        up.consume()
                        tap()
                    }
                    return@awaitEachGesture
                }
                var lastPos = down.position
                // 0 = 계속, 1 = 탭, 2 = 취소(스크롤 등) / null = 오래 누름
                val result: Int? = withTimeoutOrNull(holdMs) {
                    var r = 0
                    while (r == 0) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id }
                        r = when {
                            c == null -> 2
                            c.changedToUp() -> if (c.isConsumed) 2 else {
                                c.consume()
                                1
                            }
                            c.isConsumed -> 2
                            (c.position - down.position).getDistance() > viewConfiguration.touchSlop -> 2
                            else -> {
                                lastPos = c.position
                                0
                            }
                        }
                    }
                    r
                }
                if (result == 1) {
                    tap()
                    return@awaitEachGesture
                }
                if (result != null) return@awaitEachGesture
                if (!mayLift()) return@awaitEachGesture

                // 들어 올림
                lift()
                val start = tracker.toRoot(holder.value, lastPos) ?: Offset.Zero
                var finished = false
                try {
                    // 스펙 10.5: 움직이지 않고 모두 2초 누르면 → 끌기를 취소하고 묶음 고르기
                    val gh = groupHold
                    if (gh != null) {
                        // 0 = 계속, 1 = 손 뗌, 2 = 움직임 시작 / null = 2초 다 참
                        val r2: Int? = withTimeoutOrNull(maxOf(0L, 2_000L - holdMs)) {
                            var r = 0
                            while (r == 0) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == down.id }
                                r = when {
                                    c == null -> 3
                                    c.changedToUp() -> 1
                                    (c.position - down.position).getDistance() > viewConfiguration.touchSlop -> {
                                        c.consume()
                                        val p = tracker.toRoot(holder.value, c.position)
                                        if (p != null) changed(TimetableDragValue(location = p, startLocation = start))
                                        2
                                    }
                                    else -> 0
                                }
                            }
                            r
                        }
                        if (r2 == null) {
                            finished = true
                            cancelled()
                            gh()
                            // 손을 뗄 때까지 나머지 이벤트는 삼켜요
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                c.consume()
                                if (c.changedToUp()) break
                            }
                            return@awaitEachGesture
                        }
                        if (r2 == 1) {
                            finished = true
                            ended()
                            return@awaitEachGesture
                        }
                        if (r2 == 3) {
                            finished = true
                            cancelled()
                            return@awaitEachGesture
                        }
                    }
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id }
                        if (c == null) {
                            finished = true
                            cancelled()
                            break
                        }
                        if (c.changedToUp()) {
                            c.consume()
                            finished = true
                            ended()
                            break
                        }
                        if (c.positionChange() != Offset.Zero) {
                            c.consume()
                            val p = tracker.toRoot(holder.value, c.position)
                            if (p != null) changed(TimetableDragValue(location = p, startLocation = start))
                        }
                    }
                } finally {
                    // 제스처가 끝나지 못하고 취소됨 (화면이 바뀌는 등)
                    if (!finished) cancelled()
                }
            }
        }
}

// MARK: - 날짜 머리글

@Composable
private fun TimetableHeaderRow(
    days: List<TimetableDay>,
    metrics: TimetableGridMetrics,
    planVsActual: Boolean,
    onDayClick: ((String) -> Unit)?,
) {
    val today = KST.today()
    val single = days.size == 1
    val line = NC.line
    Row(
        Modifier
            .width(metrics.width.dp)
            .height(metrics.headerHeight.dp)
            .background(NC.surface),
    ) {
        Spacer(Modifier.width(TimetableGeo.labelWidth.dp))
        for (d in days) {
            Box(
                Modifier
                    .width(metrics.colWidth.dp)
                    .height(metrics.headerHeight.dp)
                    .drawBehind { drawLine(line, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 0.5.dp.toPx()) },
            ) {
                if (single) {
                    TimetableDayHeaderCell(day = d, today = today, colWidth = metrics.colWidth, planVsActual = planVsActual)
                } else {
                    TimetableWeekHeaderCell(day = d, today = today, onDayClick = onDayClick)
                }
            }
        }
    }
}

@Composable
private fun TimetableWeekHeaderCell(day: TimetableDay, today: String, onDayClick: ((String) -> Unit)?) {
    val wd = KST.weekdayIndex(day.date)
    val isToday = day.date == today
    val numberColor = if (isToday) NC.calendarTodayFg else (if (wd == 6) NC.calendarHoliday else (if (wd == 5) NC.calendarSaturday else NC.textPrimary))
    val date = day.date
    Column(
        Modifier
            .fillMaxSize()
            .then(
                if (onDayClick != null) {
                    Modifier
                        .clickable { onDayClick(date) }
                        .semantics { contentDescription = "${day.date} 일간 보기" }
                } else {
                    Modifier
                }
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(KST.weekdayLabel[wd], style = pt(11, PTWeight.semibold), color = if (isToday) NC.textBrand else NC.textTertiary)
        Box(
            Modifier.size(26.dp).background(if (isToday) NC.calendarToday else Color.Transparent, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text("${KST.dayOfMonth(day.date)}", style = pt(14, PTWeight.bold), color = numberColor)
        }
        Text(timetableWeekStatText(day, today), style = ptMono(9), color = NC.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 좁은 칸이라 짧게: 아직 오지 않은 날은 개수, 지난 날 · 오늘은 실행률 */
private fun timetableWeekStatText(day: TimetableDay, today: String): String {
    val s = day.stats
    if (s.total == 0) return "–"
    if (day.date > today) return "${s.total}개"
    return "${s.rate ?: 0}%"
}

@Composable
private fun TimetableDayHeaderCell(day: TimetableDay, today: String, colWidth: Float, planVsActual: Boolean) {
    val wd = KST.weekdayIndex(day.date)
    val isToday = day.date == today
    val numberColor = if (isToday) NC.calendarTodayFg else (if (wd == 6) NC.calendarHoliday else (if (wd == 5) NC.calendarSaturday else NC.textPrimary))
    val s = day.stats
    val statText = when {
        s.total == 0 -> "Task 없음"
        day.date > today -> "Task ${s.total}개"
        else -> "실행률 ${s.rate ?: 0}%"
    }
    val line = NC.line
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        Row(
            Modifier.padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).background(if (isToday) NC.calendarToday else Color.Transparent, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Text("${KST.dayOfMonth(day.date)}", style = pt(15, PTWeight.bold), color = numberColor)
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("${KST.weekdayLabel[wd]}요일", style = pt(12, PTWeight.semibold), color = if (isToday) NC.textBrand else NC.textTertiary)
                Text(statText, style = pt(11), color = NC.ink3, maxLines = 1)
            }
        }
        // 일간 "계획 | 실제" 칸 이름 — 아래 격자의 50% · 50% 와 맞춰요 (스펙 9.4)
        if (planVsActual) {
            Row {
                Text(
                    "📋 계획한 일",
                    style = pt(11, PTWeight.semibold), color = NC.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width((colWidth * 0.5f).dp).padding(start = 10.dp),
                )
                Text(
                    "✅ 실제로 한 일",
                    style = pt(11, PTWeight.semibold), color = NC.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .width((colWidth * 0.5f).dp)
                        .drawBehind { drawLine(line, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.dp.toPx()) }
                        .padding(start = 6.dp),
                )
            }
        }
    }
}

// MARK: - 시간 미정 줄

@Composable
private fun TimetableUnscheduledRow(
    board: TimetableBoardModel,
    days: List<TimetableDay>,
    metrics: TimetableGridMetrics,
    flags: TimetableGridFlags,
    tracker: TimetableScrollTracker,
    haptic: HapticFeedback,
    expanded: Set<String>,
    onExpand: (String) -> Unit,
    onDrag: TimetableDragHandler,
    onDragEnd: (String) -> Unit,
) {
    val line = NC.line
    Row(
        Modifier
            .width(metrics.width.dp)
            .height(metrics.unscheduledHeight.dp)
            .background(NC.surface)
            .drawBehind { drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 0.5.dp.toPx()) },
    ) {
        Box(Modifier.width(TimetableGeo.labelWidth.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text("시간\n미정", style = pt(10), color = NC.ink3, textAlign = TextAlign.Center)
        }
        for (d in days) {
            key(d.date) {
                TimetableUnscheduledCell(
                    board = board, day = d, flags = flags, tracker = tracker, haptic = haptic, compact = days.size > 1,
                    expanded = expanded.contains(d.date),
                    onExpand = { onExpand(d.date) },
                    onDrag = onDrag, onDragEnd = onDragEnd,
                    modifier = Modifier
                        .width(metrics.colWidth.dp)
                        .fillMaxHeight()
                        .drawBehind { drawLine(line, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 0.5.dp.toPx()) },
                )
            }
        }
    }
}

@Composable
private fun TimetableUnscheduledCell(
    board: TimetableBoardModel,
    day: TimetableDay,
    flags: TimetableGridFlags,
    tracker: TimetableScrollTracker,
    haptic: HapticFeedback,
    compact: Boolean,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDrag: TimetableDragHandler,
    onDragEnd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tasks = day.unscheduledTasks
    val shown = if (expanded) tasks else tasks.take(TimetableGeo.unscheduledVisible)
    val more = tasks.size - shown.size
    val target = (flags.drag?.target as? TimetableDropTarget.unscheduled)?.date == day.date
    val brand = NC.brand
    Box(
        modifier
            .background(if (target) NC.brandSoft else NC.surface)
            .then(
                if (target) {
                    Modifier.padding(1.dp).timetableDashedBorder(2.dp, brand, androidx.compose.ui.graphics.RectangleShape, 4.dp, 3.dp)
                } else {
                    Modifier
                }
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState(), enabled = flags.drag == null && expanded)
                .padding(if (compact) 3.dp else 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (target) {
                Text(
                    "여기에 놓으면 시간 미정",
                    style = pt(if (compact) 9 else 11, PTWeight.semibold),
                    color = NC.textBrand,
                    maxLines = 2,
                )
            }
            for (t in shown) {
                key(t.taskId) {
                    TimetableUnscheduledChip(board, day, t, flags, tracker, haptic, compact, onDrag, onDragEnd)
                }
            }
            if (more > 0) {
                Text(
                    "+${more}개 더보기",
                    style = pt(if (compact) 9 else 11),
                    color = NC.ink3,
                    modifier = Modifier.clickable {
                        board.gridTrack("timetable.unscheduled_more_click", mapOf("view_date" to day.date, "remaining_count" to more))
                        onExpand()
                    },
                )
            }
        }
    }
}

@Composable
private fun TimetableUnscheduledChip(
    board: TimetableBoardModel,
    day: TimetableDay,
    t: TimeBlockTask,
    flags: TimetableGridFlags,
    tracker: TimetableScrollTracker,
    haptic: HapticFeedback,
    compact: Boolean,
    onDrag: TimetableDragHandler,
    onDragEnd: (String) -> Unit,
) {
    val style = TimetableTaskStyle(t)
    val owner = "u-${t.taskId}"
    val source = TimetableDragSource.task(task = t, date = day.date, start = null, end = null, group = emptyList())
    val dragging = flags.drag?.draggingTaskIds?.contains(t.taskId) ?: false
    val selected = flags.selectedTaskId == t.taskId
    val lifted = flags.isLifted(owner) && !dragging
    val scale by animateFloatAsState(if (lifted) 1.06f else 1f, label = "chipLift")
    // 왼쪽은 각지게 — 왼쪽 목표 색 막대가 위아래 끝까지 곧게 (TimeBlock 과 같게, 2026-10)
    val shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 5.dp, bottomEnd = 5.dp)
    val opacity = if (dragging) 0.3f else if (flags.dimOthers && !flags.isLifted(owner)) 0.5f else if (style.done) 0.55f else 1f
    Box(
        Modifier
            .fillMaxWidth()
            .height(TimetableGeo.chipHeight.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (lifted) 8.dp else 0.dp, shape)
            .alpha(opacity)
            .clip(shape)
            .then(if (selected) Modifier.border(2.dp, NC.ink, shape) else Modifier)
            .timetableBlockGesture(
                enabled = TimetableRules.canDragTask(t),
                tracker = tracker,
                canLift = { board.liftedOwnerId == null || board.liftedOwnerId == owner },
                onTap = { board.tapTaskBlock(t) },
                onChanged = { v -> onDrag(source, TimetableDragMode.move, owner, v) },
                onEnded = { onDragEnd(owner) },
                onCancelled = { board.cancelDrag(ownerId = owner) },
                onLift = { if (board.lift(ownerId = owner)) haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        TimetableTaskBackground(style, Modifier.matchParentSize())
        Text(
            TimetableRules.title(t.emoji, t.taskName),
            style = (if (compact) timetableFont(9.5, PTWeight.semibold) else pt(11, PTWeight.semibold))
                .copy(textDecoration = if (style.done) TextDecoration.LineThrough else null),
            color = style.titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (compact) 6.dp else 10.dp, end = if (compact) 3.dp else 7.dp),
        )
    }
}

// MARK: - 시간 눈금 (가로줄 + 왼쪽 시각)

@Composable
private fun TimetableHourBackground(startHour: Int, endHour: Int, width: Float) {
    val line = NC.borderSoft
    Column(Modifier.width(width.dp)) {
        for (h in startHour until max(endHour, startHour + 1)) {
            Box(Modifier.width(width.dp).height(TimetableGeo.hourHeight.dp)) {
                Box(
                    Modifier
                        .padding(start = TimetableGeo.labelWidth.dp)
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(line)
                )
                // 24시 이후는 다음 날 새벽 (취침이 자정 뒤일 때)
                if (h != startHour) {
                    Text(
                        String.format("%02d:00", h % 24),
                        style = ptMono(10),
                        color = NC.textTertiary,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .width((TimetableGeo.labelWidth - 6f).dp)
                            .offset(y = (-7).dp),
                    )
                }
            }
        }
    }
}

// MARK: - 날짜 칸 하나

/** 실제 기록과 Task 를 같이 놓을 때 열을 다시 나누려고 한 타입으로 묶어요 */
private sealed class TimetableMixedItem {
    data class task(val t: TimeBlockTask) : TimetableMixedItem()
    data class activity(val a: ActualActivity) : TimetableMixedItem()
}

private fun <A, B> timetableConvertPlaced(p: TimetableLayout.Placed<A>, item: B): TimetableLayout.Placed<B> =
    TimetableLayout.Placed(
        item = item, top = p.top, bottom = p.bottom, start = p.start, end = p.end,
        clippedTop = p.clippedTop, clippedBottom = p.clippedBottom, col = p.col, cols = p.cols, part = p.part,
    )

/** Task 와 실제 기록을 같이 놓고 겹치는 것끼리만 나란히 (col / cols 를 다시 정해요) */
private fun timetableMixedLayout(
    tasks: List<TimetableLayout.Placed<TimeBlockTask>>,
    acts: List<TimetableLayout.Placed<ActualActivity>>,
): Pair<List<TimetableLayout.Placed<TimeBlockTask>>, List<TimetableLayout.Placed<ActualActivity>>> {
    val mixed = mutableListOf<TimetableLayout.Placed<TimetableMixedItem>>()
    for (p in tasks) mixed.add(timetableConvertPlaced(p, TimetableMixedItem.task(p.item)))
    for (p in acts) mixed.add(timetableConvertPlaced(p, TimetableMixedItem.activity(p.item)))
    val laid = TimetableLayout.layoutColumns(mixed)
    val outTasks = mutableListOf<TimetableLayout.Placed<TimeBlockTask>>()
    val outActs = mutableListOf<TimetableLayout.Placed<ActualActivity>>()
    for (p in laid) {
        when (val it = p.item) {
            is TimetableMixedItem.task -> outTasks.add(timetableConvertPlaced(p, it.t))
            is TimetableMixedItem.activity -> outActs.add(timetableConvertPlaced(p, it.a))
        }
    }
    return Pair(outTasks, outActs)
}

/** 칸 하나에 그릴 블록 위치 (웹 TimeGrid 날짜 칸의 계산 부분) */
private class TimetableColumnLayout(
    day: TimetableDay,
    activities: List<ActualActivity>,
    from: Int,
    to: Int,
    single: Boolean,
    planVsActual: Boolean,
) {
    val tasks: List<TimetableLayout.Placed<TimeBlockTask>>
    val doneTasks: List<TimetableLayout.Placed<TimeBlockTask>>
    val acts: List<TimetableLayout.Placed<ActualActivity>>
    /** 칸을 "계획 50% | 실제 50%"로 나누는지 */
    val split: Boolean
    /** 일간 계획 | 실제 비교 */
    val compare: Boolean = single && planVsActual

    init {
        val acts0 = TimetableLayout.placeActivities(day.date, activities, from = from, to = to)
        // 일간: 실제 기록이 있으면 나눠서 나란히 / 주간: 칸이 좁아서 한 열에 같이 그려요 (겹칠 때만 나란히)
        split = compare || (single && acts0.isNotEmpty())
        var t = TimetableLayout.placeTasks(
            day, from = from, to = to,
            basis = if (compare) TimetableLayout.TaskBasis.planned else TimetableLayout.TaskBasis.shown,
        )
        var done = if (compare) TimetableLayout.placeTasks(day, from = from, to = to, basis = TimetableLayout.TaskBasis.done) else emptyList()
        var a = acts0
        if (compare) {
            // 실제 칸에는 완료한 Task(실제 시각)와 실제로 한 일 기록을 같이, 겹치면 나란히
            val r = timetableMixedLayout(done, acts0)
            done = r.first
            a = r.second
        } else if (!split && acts0.isNotEmpty()) {
            val r = timetableMixedLayout(t, acts0)
            t = r.first
            a = r.second
        }
        tasks = t
        doneTasks = done
        acts = a
    }
}

private data class TimetableGhostInfo(
    val title: String,
    val style: TimetableTaskStyle?,
    val start: Int,
    val end: Int,
    val invalid: String?,
    /** 원래 블록이 그려진 x · 폭 (dp) — 없으면 칸 전체 폭 */
    val rendered: Pair<Float, Float>? = null,
)

/** 블록 위치 (dp) */
private data class TimetableBlockRect(val x: Float, val y: Float, val width: Float, val height: Float)

/** 블록이 마지막으로 그려진 칸 안 x · 폭 (dp) — 드래그 미리보기용 (화면 전용 값, 저장 안 해요) */
private object TimetableRenderedRects {
    val map = HashMap<String, Pair<Float, Float>>()
}

@Composable
private fun TimetableDayColumn(
    board: TimetableBoardModel,
    day: TimetableDay,
    metrics: TimetableGridMetrics,
    flags: TimetableGridFlags,
    activities: List<ActualActivity>,
    single: Boolean,
    planVsActual: Boolean,
    emptyClickEnabled: Boolean,
    onEmptyTap: ((String, Int) -> Unit)? = null,
    tracker: TimetableScrollTracker,
    haptic: HapticFeedback,
    onDrag: TimetableDragHandler,
    onDragEnd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = remember(day, activities, metrics.from, metrics.to, single, planVsActual) {
        TimetableColumnLayout(day, activities, from = metrics.from, to = metrics.to, single = single, planVsActual = planVsActual)
    }
    val w = metrics.colWidth
    val planW = if (layout.split) w * 0.5f else w
    val fade = flags.fadeOthers && flags.focusIds.isNotEmpty()

    fun rect(top: Int, bottom: Int, col: Int, cols: Int, areaX: Float, areaW: Float): TimetableBlockRect {
        val y = metrics.y(top)
        val h = max((bottom - top) * TimetableGeo.ppm - 2f, 16f)
        val cw = areaW / max(cols, 1)
        return TimetableBlockRect(x = areaX + col * cw + 1f, y = y, width = max(cw - 2f, 4f), height = h)
    }

    val ctx = TimetableColumnCtx(
        board = board, day = day, metrics = metrics, flags = flags, single = single,
        tracker = tracker, haptic = haptic, onDrag = onDrag, onDragEnd = onDragEnd,
    )

    Box(modifier) {
        // 배경 · 빈 칸 누르기
        TimetableColumnBackground(day = day, metrics = metrics, emptyClickEnabled = emptyClickEnabled, board = board, onEmptyTap = onEmptyTap)
        // 수면
        for (ps in TimetableLayout.placeSleep(day, from = metrics.from, to = metrics.to)) {
            key("s-${ps.placed.item.key}-${ps.edge.rawValue}") { TimetableSleepBlock(ctx, ps, fade) }
        }
        // 고정 시간
        for (p in TimetableLayout.placeFixed(day, from = metrics.from, to = metrics.to)) {
            key("f-${p.item.key}") {
                TimetableFixedBlock(ctx, p, rect(p.top, p.bottom, 0, 1, 0f, metrics.colWidth), fade)
            }
        }
        // 시간대 습관 (V56) — 연한 초록 점선 블록, 누르면 체크 · 체크 풀기 (Task 블록 아래 층)
        for (p in TimetableLayout.placeHabits(day, from = metrics.from, to = metrics.to)) {
            key("h-${p.item.habitId}") {
                val r = rect(p.top, p.bottom, 0, 1, 0f, planW)
                TimetableHabitBlockView(
                    habit = p.item, compact = !single, date = day.date,
                    modifier = Modifier.offset(x = r.x.dp, y = r.y.dp).size(r.width.dp, r.height.dp),
                    onTap = {
                        board.gridTrack(
                            "timetable.habit_block_toggle",
                            mapOf(
                                "habit_id" to p.item.habitId, "check_action" to if (p.item.checked) "uncomplete" else "complete",
                                "date_relation" to CAL.relation(day.date),
                            ),
                        )
                    },
                )
            }
        }
        // 못 해서 다른 날로 옮긴 흔적 (2026-10) — 원래 자리에 흐리게 "* 이름 → 옮긴 날". 끌 수 없어요
        for (c in day.carriedOut) {
            val cs = c.start ?: continue
            val top = KST.minutesFrom(day.date, cs).coerceIn(metrics.from, metrics.to)
            val bottom = (c.end?.let { KST.minutesFrom(day.date, it) } ?: (top + 30)).coerceIn(metrics.from, metrics.to)
            if (bottom <= top) continue
            key("c-${c.id}") {
                TimetableCarriedTrace(ctx, c, rect(top, bottom, 0, 1, 0f, planW), fade)
            }
        }
        // Task
        for (p in layout.tasks) {
            key("t-${p.uid}") {
                TimetableTaskBlock(
                    ctx, p, all = layout.tasks, r = rect(p.top, p.bottom, p.col, p.cols, 0f, planW),
                    planned = layout.compare, interactive = true,
                )
            }
        }
        // 실제로 한 일 (한 열에 같이)
        if (!layout.split && layout.acts.isNotEmpty()) {
            Box(Modifier.matchParentSize().alpha(if (flags.dimOthers) 0.5f else (if (fade) 0.55f else 1f))) {
                for (p in layout.acts) {
                    key("a-${p.item.activityId}-${p.top}") {
                        TimetableActivityBlock(ctx, p, rect(p.top, p.bottom, p.col, p.cols, 0f, w))
                    }
                }
            }
        }
        // 일간 오른쪽 50% "실제" 칸: 완료한 Task(실제 시각) + 실제로 한 일
        if (layout.split) {
            val x = planW
            val sw = w - planW
            val line = NC.line
            Box(Modifier.matchParentSize().alpha(if (flags.dimOthers && !flags.actualActive) 0.5f else (if (fade) 0.55f else 1f))) {
                Canvas(Modifier.matchParentSize()) {
                    val px = x.dp.toPx()
                    drawLine(
                        line, Offset(px, 0f), Offset(px, size.height), strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                    )
                }
                for (p in layout.doneTasks) {
                    key("d-${p.uid}") {
                        TimetableTaskBlock(
                            ctx, p, all = layout.doneTasks, r = rect(p.top, p.bottom, p.col, p.cols, x, sw),
                            planned = false, interactive = false,
                        )
                    }
                }
                for (p in layout.acts) {
                    key("a-${p.item.activityId}-${p.top}") {
                        TimetableActivityBlock(ctx, p, rect(p.top, p.bottom, p.col, p.cols, x, sw))
                    }
                }
            }
        }
        // 드래그 미리보기
        TimetableGhostLayer(day = day, metrics = metrics, flags = flags)
        TimetableNowLine(date = day.date, from = metrics.from, to = metrics.to, modifier = Modifier.matchParentSize())
    }
}

/** 다른 날로 옮긴 Task 의 흔적 — 점선 · 흐린 바탕. 누르면 메뉴 (2026-10): 그때 했어요 · 원래 시간으로 되돌리기 · 옮긴 곳 보기 */
@Composable
private fun TimetableCarriedTrace(ctx: TimetableColumnCtx, c: CarriedOutBlock, r: TimetableBlockRect, fade: Boolean) {
    val color = goalColor(c.goalColorCode)
    val shape = RoundedCornerShape(6.dp)
    val to = c.toDate
    val compact = !ctx.single
    val scope = rememberCoroutineScope()
    val action = { a: String ->
        ctx.board.gridTrack("timetable.carried_trace_action", mapOf("task_id" to c.taskId, "carry_action" to a))
    }
    val items = buildList {
        add(MenuItem(title = "그때 했어요", icon = "checkmark", enabled = ctx.board.carryBusy == null) {
            action("done_then")
            scope.launch { ctx.board.revertCarry(c.taskId, from = ctx.day.date, thenActual = true) }
        })
        add(MenuItem(title = "원래 시간으로 되돌리기", icon = "arrow.uturn.backward", enabled = ctx.board.carryBusy == null) {
            action("revert")
            scope.launch { ctx.board.revertCarry(c.taskId, from = ctx.day.date) }
        })
        if (to != null) {
            add(MenuItem(title = "옮긴 곳 보기 (${KST.monthOf(to)}/${KST.dayOfMonth(to)})", icon = "chevron.right") {
                action("go")
                AppRouter.openCalendar(date = to)
            })
        }
    }
    NMenu(
        items = items,
        modifier = Modifier
            .offset(x = r.x.dp, y = r.y.dp)
            .size(r.width.dp, r.height.dp)
            .semantics { contentDescription = "${c.taskName} — ${to?.let { KST.formatMonthDay(it) } ?: "다른 날"}로 옮김. 그때 했다고 남기거나 되돌릴 수 있어요" },
    ) {
        Box(
            Modifier
                .size(r.width.dp, r.height.dp)
                .alpha(if (fade) 0.3f else 0.75f)
                .clip(shape)
                .background(NC.surface.copy(alpha = 0.6f))
                .timetableDashedBorder(1.dp, color.copy(alpha = 0.6f), shape, 3.dp, 3.dp)
                .padding(horizontal = if (compact) 3.dp else 6.dp, vertical = 2.dp),
        ) {
            Column {
                Text(
                    "* " + (c.emoji?.let { if (it.isEmpty()) "" else "$it " } ?: "") + c.taskName,
                    style = pt(if (compact) 10 else 12, PTWeight.medium).copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
                    color = NC.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (to != null && r.height >= 30f) {
                    Text(
                        "→ ${KST.monthOf(to)}/${KST.dayOfMonth(to)}로 옮김",
                        style = pt(if (compact) 9 else 11),
                        color = NC.ink4,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    // 메뉴를 연 것 자체 (timetable.carried_trace_click) 는 NMenu 가 열릴 때 따로 남기지 않아요 — 고른 항목만 carried_trace_action
}

/** 칸 안 블록들이 같이 쓰는 값 묶음 */
private class TimetableColumnCtx(
    val board: TimetableBoardModel,
    val day: TimetableDay,
    val metrics: TimetableGridMetrics,
    val flags: TimetableGridFlags,
    val single: Boolean,
    val tracker: TimetableScrollTracker,
    val haptic: HapticFeedback,
    val onDrag: TimetableDragHandler,
    val onDragEnd: (String) -> Unit,
)

/** 블록 공통 제스처를 칸 정보로 묶어서 (iOS blockGesture) */
@Composable
private fun Modifier.timetableColumnGesture(
    ctx: TimetableColumnCtx,
    enabled: Boolean,
    owner: String,
    source: TimetableDragSource,
    mode: TimetableDragMode,
    hold: Double = TimetableGeo.holdSeconds,
    onGroupHold: (() -> Unit)? = null,
    onTap: () -> Unit,
): Modifier {
    val board = ctx.board
    val drag = ctx.onDrag
    val end = ctx.onDragEnd
    val haptic = ctx.haptic
    return this.timetableBlockGesture(
        enabled = enabled,
        tracker = ctx.tracker,
        holdSeconds = hold,
        canLift = { board.liftedOwnerId == null || board.liftedOwnerId == owner },
        onTap = onTap,
        onChanged = { v -> drag(source, mode, owner, v) },
        onEnded = { end(owner) },
        onCancelled = { board.cancelDrag(ownerId = owner) },
        onLift = { if (board.lift(ownerId = owner)) haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
        onGroupHold = onGroupHold,
    )
}

// MARK: 배경 · 빈 칸 누르기

@Composable
private fun BoxScope.TimetableColumnBackground(
    day: TimetableDay,
    metrics: TimetableGridMetrics,
    emptyClickEnabled: Boolean,
    board: TimetableBoardModel,
    onEmptyTap: ((String, Int) -> Unit)? = null,
) {
    val isToday = day.date == KST.today()
    val line = NC.line
    val groupMode = board.groupIds.isNotEmpty()
    val tappable = (emptyClickEnabled && (onEmptyTap != null || day.date <= KST.today())) || groupMode
    val menuTap by rememberUpdatedState(onEmptyTap)
    Box(
        Modifier
            .matchParentSize()
            .background(if (isToday) NC.aiTintSoft.copy(alpha = 0.35f) else Color.Transparent)
            .drawBehind { drawLine(line, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 0.5.dp.toPx()) }
            .then(
                if (tappable) {
                    Modifier.pointerInput(day.date, metrics.from, groupMode) {
                        detectTapGestures { pos ->
                            // 묶음 고르기 중 빈 곳을 누르면 고르기 끝
                            val open = menuTap
                            when {
                                groupMode -> board.clearGroupByEmptyTap()
                                open != null -> {
                                    // 스펙 11.4: + 메뉴 (그 날짜 · 시각을 넘겨요). 자정 뒤 칸은 다음 날로
                                    val raw = metrics.from.toDouble() + ((pos.y / density) / TimetableGeo.ppm).toDouble()
                                    var m = floor(raw / 30).toInt() * 30
                                    var d = day.date
                                    if (m >= 1440) { d = KST.addDays(d, 1); m -= 1440 }
                                    board.emptyTapForMenu(date = d, minutes = m)
                                    open(d, m)
                                }
                                else -> timetableTapEmpty(board, day, metrics, pos.y / density)
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
    )
}

/** 블록이 없는 빈 곳을 누르면 그 시각(30분 단위)으로 실제 기록 열기 — 지난 시간만 */
private fun timetableTapEmpty(board: TimetableBoardModel, day: TimetableDay, metrics: TimetableGridMetrics, y: Float) {
    val raw = metrics.from.toDouble() + (y / TimetableGeo.ppm).toDouble()
    val minutes = floor(raw / 30).toInt() * 30
    val now = KST.now()
    if (day.date > now.date) return
    // 자정 뒤 칸(24:00~)은 다음 날 시각
    var nowOnDay: Int? = null
    if (day.date == now.date) {
        nowOnDay = now.minutes
    } else if (KST.addDays(day.date, 1) == now.date) {
        nowOnDay = now.minutes + 1440
    }
    if (nowOnDay != null && minutes >= nowOnDay) return
    board.emptyClick(date = day.date, minutes = minutes)
}

// MARK: 수면

@Composable
private fun TimetableSleepBlock(ctx: TimetableColumnCtx, ps: TimetableLayout.PlacedSleep, fade: Boolean) {
    val p = ps.placed
    val b = p.item
    val key = b.key
    val wake = ps.edge == TimetableLayout.SleepEdge.wake
    val h = max((p.bottom - p.top) * TimetableGeo.ppm, 16f)
    val day = ctx.day
    val flags = ctx.flags
    val owner = "s-$key-${ps.edge.rawValue}-${day.date}"
    val source = TimetableDragSource.fixed(block = b, date = day.date, start = p.start, end = p.end)
    val dimmed = flags.drag?.draggingFixedKey == key || fade || (flags.dimOthers && !flags.isLifted(owner))
    val board = ctx.board
    Box(
        Modifier
            .offset(y = ctx.metrics.y(p.top).dp)
            .size(ctx.metrics.colWidth.dp, h.dp)
    ) {
        TimetableSleepBlockView(
            selected = flags.selectedFixedKey == key, dimmed = dimmed,
            modifier = Modifier.matchParentSize().timetableColumnGesture(
                ctx, enabled = false, owner = owner, source = source, mode = TimetableDragMode.move,
                onTap = { board.selectFixed(b) },
            ),
        )
        // 아침 블록은 아래 가장자리(= 기상), 밤 블록은 위 가장자리(= 취침)를 끌어서 시간을 바꿔요
        TimetableSleepEdgeLabel(
            edge = ps.edge, minutes = if (wake) p.end else p.start, compact = !ctx.single,
            modifier = Modifier
                .align(if (wake) Alignment.BottomStart else Alignment.TopStart)
                .fillMaxWidth()
                .height(min(22f, h).dp)
                .timetableColumnGesture(
                    ctx, enabled = true, owner = owner, source = source,
                    mode = if (wake) TimetableDragMode.resize else TimetableDragMode.resizeTop,
                    hold = 0.25, onTap = { board.selectFixed(b) },
                ),
        )
    }
}

// MARK: 고정 시간

@Composable
private fun TimetableFixedBlock(ctx: TimetableColumnCtx, p: TimetableLayout.Placed<FixedBlock>, r: TimetableBlockRect, fade: Boolean) {
    val b = p.item
    val day = ctx.day
    val flags = ctx.flags
    val canDrag = TimetableRules.canDragFixed(b, date = day.date)
    val owner = "f-${b.key}-${day.date}"
    val source = TimetableDragSource.fixed(block = b, date = day.date, start = p.start, end = p.end)
    val dragging = flags.drag?.draggingFixedKey == b.key
    val lifted = flags.isLifted(owner) && !dragging
    val dimmed = dragging || fade || (flags.dimOthers && !flags.isLifted(owner))
    val board = ctx.board
    val showHandle = canDrag && !p.clippedBottom && r.height >= 26f
    val scale by animateFloatAsState(if (lifted) 1.05f else 1f, label = "fixedLift")
    Box(
        Modifier
            .offset(x = r.x.dp, y = r.y.dp)
            .size(r.width.dp, r.height.dp)
            .zIndex(if (lifted) 10f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (lifted) 10.dp else 0.dp, RoundedCornerShape(7.dp))
            .timetableColumnGesture(ctx, enabled = canDrag, owner = owner, source = source, mode = TimetableDragMode.move, onTap = { board.selectFixed(b) })
    ) {
        TimetableFixedBlockView(
            placed = p, heightPx = r.height, compact = !ctx.single,
            selected = flags.selectedFixedKey == b.key, dimmed = dimmed,
            modifier = Modifier.matchParentSize(),
        )
        if (showHandle) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(12.dp)
                    .timetableColumnGesture(
                        ctx, enabled = true, owner = "$owner-r", source = source, mode = TimetableDragMode.resize,
                        hold = 0.25, onTap = { board.selectFixed(b) },
                    )
            )
        }
    }
}

// MARK: Task

@Composable
private fun TimetableTaskBlock(
    ctx: TimetableColumnCtx,
    p: TimetableLayout.Placed<TimeBlockTask>,
    all: List<TimetableLayout.Placed<TimeBlockTask>>,
    r: TimetableBlockRect,
    planned: Boolean,
    interactive: Boolean,
) {
    val t = p.item
    val day = ctx.day
    val flags = ctx.flags
    val board = ctx.board
    // 완료한 Task 가 실제 시각에 그려진 블록(일간 "실제" 칸 · 주간)은 실제로 한 시간을 끌어서 바꿔요
    val actualDrag = !planned && TimetableRules.canDragActual(t)
    val canDrag = (interactive && TimetableRules.canDragTask(t)) || actualDrag
    // 묶음 고르기 중이면 하나만 골라도 점선 (끌 때 같이 움직이는 건 2개 이상일 때)
    val grouped = interactive && flags.groupIds.contains(t.taskId)
    val groupMode = interactive && board.groupIds.isNotEmpty()
    // 묶음에 든 Task 를 옮기면(크기 조절 말고) 같은 날의 나머지 묶음도 같이 움직여요
    val members = mutableListOf<TimetableGroupMember>()
    if (grouped && flags.groupIds.size > 1) {
        for (q in all) {
            if (q.item.taskId != t.taskId && flags.groupIds.contains(q.item.taskId) && TimetableRules.canDragTask(q.item)) {
                members.add(TimetableGroupMember(task = q.item, date = day.date, start = q.start, end = q.end))
            }
        }
    }
    // 일간 비교에서는 같은 Task 가 계획 칸 · 실제 칸에 둘 다 있어서 owner 를 나눠요
    val owner = "${if (actualDrag) "a" else "t"}-${t.taskId}-${p.part}-${day.date}"
    // 끌 때 미리보기가 이 블록의 그려진 폭 · 칸 안 위치를 그대로 쓰게 기억해 둬요 (스펙 9.7)
    SideEffect { TimetableRenderedRects.map["${if (actualDrag) "a" else "t"}-${t.taskId}"] = r.x to r.width }
    val actualSource = TimetableDragSource.actual(task = t, date = day.date, start = p.start, end = p.end, part = p.part)
    // 점심 앞뒤로 나눠 넣은 Task 는 어느 조각을 끌어도 전체 길이 한 덩어리로 옮겨요
    val taskEnd = TimetableRules.splitLength(t)?.let { p.start + it } ?: p.end
    val moveSource: TimetableDragSource = if (actualDrag) actualSource else
        TimetableDragSource.task(task = t, date = day.date, start = p.start, end = taskEnd, group = members)
    val resizeSource: TimetableDragSource = if (actualDrag) actualSource else
        TimetableDragSource.task(task = t, date = day.date, start = p.start, end = p.end, group = emptyList())
    // 나눠 넣은 조각은 길이 조절 손잡이를 숨겨요 (한 조각 끝을 끌면 전체 길이가 어긋나요 — 옮기면 한 덩어리가 돼요)
    val splitPiece = !actualDrag && TimetableRules.splitLength(t) != null
    val dragging = if (actualDrag) {
        flags.drag?.draggingActualUid == p.uid
    } else {
        interactive && (flags.drag?.draggingTaskIds?.contains(t.taskId) ?: false)
    }
    val fadedOut = (interactive || actualDrag) && (
        (flags.fadeOthers && flags.focusIds.isNotEmpty() && !flags.focusIds.contains(t.taskId)) ||
            // 다른 블록을 들어 올렸거나 끄는 중이면 50%
            (flags.dimOthers && !flags.isLifted(owner))
        )
    // 길게 눌러 들어 올린 순간 (움직이기 전): 살짝 커지고 그림자
    val lifted = (interactive || actualDrag) && flags.isLifted(owner) && !dragging
    val showTop = canDrag && !splitPiece && !p.clippedTop && r.height >= 44f
    val showBottom = canDrag && !splitPiece && !p.clippedBottom && r.height >= 26f
    val scale by animateFloatAsState(if (lifted) 1.06f else 1f, label = "taskLift")
    Box(
        Modifier
            .offset(x = r.x.dp, y = r.y.dp)
            .size(r.width.dp, r.height.dp)
            .zIndex(if (lifted) 10f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (lifted) 12.dp else 0.dp, RoundedCornerShape(7.dp))
            .timetableColumnGesture(
                ctx, enabled = canDrag, owner = owner, source = moveSource, mode = TimetableDragMode.move,
                // 2초 꾹 누르면 묶음 고르기 시작 (계획 칸 Task 만 · 고정 시간은 안 돼요)
                onGroupHold = if (interactive && !actualDrag && TimetableRules.groupable(t)) ({
                    ctx.haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    board.toggleGroupMember(t.taskId, method = "long_press")
                }) else null,
                onTap = {
                    // 묶음 고르기 중: 누르면 묶음에 넣기 · 빼기만 (상세 시트는 안 열려요 — 스펙 13.1)
                    if (!actualDrag) board.tapTaskBlock(t)
                },
            )
    ) {
        TimetableTaskBlockView(
            placed = p, heightPx = r.height, compact = !ctx.single, planned = planned,
            selected = flags.selectedTaskId == t.taskId, dimmed = dragging, faded = fadedOut,
            focused = interactive && flags.focusIds.contains(t.taskId),
            isNew = interactive && flags.highlightIds.contains(t.taskId), grouped = grouped,
            modifier = Modifier.matchParentSize(),
        )
        if (showTop) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(12.dp)
                    .timetableColumnGesture(
                        ctx, enabled = true, owner = "$owner-rt", source = resizeSource, mode = TimetableDragMode.resizeTop,
                        hold = 0.25, onTap = { board.tapTaskBlock(t) },
                    )
            )
        }
        if (showBottom) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(12.dp)
                    .timetableColumnGesture(
                        ctx, enabled = true, owner = "$owner-rb", source = resizeSource, mode = TimetableDragMode.resize,
                        hold = 0.25, onTap = { board.tapTaskBlock(t) },
                    )
            )
        }
    }
}

// MARK: 실제로 한 일

@Composable
private fun TimetableActivityBlock(ctx: TimetableColumnCtx, p: TimetableLayout.Placed<ActualActivity>, r: TimetableBlockRect) {
    val board = ctx.board
    TimetableActivityBlockView(
        placed = p, heightPx = r.height, compact = !ctx.single,
        selected = ctx.flags.selectedActivityId == p.item.activityId,
        modifier = Modifier
            .offset(x = r.x.dp, y = r.y.dp)
            .size(r.width.dp, r.height.dp)
            .clickable { board.selectActivity(p.item) },
    )
}

// MARK: 드래그 미리보기

@Composable
private fun BoxScope.TimetableGhostLayer(day: TimetableDay, metrics: TimetableGridMetrics, flags: TimetableGridFlags) {
    val st = flags.drag
    val target = st?.target as? TimetableDropTarget.grid
    val ghost: TimetableGhostInfo? = if (st != null && target != null && target.date == day.date) {
        when (val src = st.source) {
            is TimetableDragSource.task -> TimetableGhostInfo(
                title = TimetableRules.title(src.task.emoji, src.task.taskName), style = TimetableTaskStyle(src.task),
                start = target.start, end = target.end, invalid = st.invalid,
                rendered = TimetableRenderedRects.map["t-${src.task.taskId}"],
            )
            is TimetableDragSource.fixed -> TimetableGhostInfo(
                title = src.block.title, style = null, start = target.start, end = target.end, invalid = st.invalid,
            )
            is TimetableDragSource.actual -> TimetableGhostInfo(
                title = TimetableRules.title(src.task.emoji, src.task.taskName) + " · 실제", style = TimetableTaskStyle(src.task),
                start = target.start, end = target.end, invalid = st.invalid,
                rendered = TimetableRenderedRects.map["a-${src.task.taskId}"],
            )
        }
    } else {
        null
    }
    if (ghost != null) {
        val h = max((ghost.end - ghost.start) * TimetableGeo.ppm - 2f, 18f)
        TimetableDragGhostView(
            title = ghost.title, taskStyle = ghost.style, start = ghost.start, end = ghost.end,
            invalid = ghost.invalid, heightPx = h,
            // 손가락으로 들고 있는 느낌 — 살짝 크게 + 진한 그림자
            // 그려져 있던 폭 · 칸 안 위치 그대로 (겹쳐서 반 폭이면 반 폭으로)
            modifier = Modifier
                .offset(x = (ghost.rendered?.first ?: 1f).dp, y = metrics.y(ghost.start).dp)
                .size((ghost.rendered?.second ?: max(metrics.colWidth - 2f, 4f)).dp, h.dp)
                .zIndex(20f)
                .graphicsLayer { scaleX = 1.05f; scaleY = 1.05f },
        )
    }
    // 묶음 드래그 중 같이 움직이는 나머지 Task 들의 새 자리
    val groupGhosts: List<TimetableGroupMember> =
        if (st != null && st.source.group.isNotEmpty() && target != null) {
            TimetableDrag.groupMoves(st.source, date = target.date, start = target.start, end = target.end)
                .drop(1)
                .filter { it.date == day.date }
        } else {
            emptyList()
        }
    val invalid = st?.invalid
    val danger = NC.danger
    for (m in groupGhosts) {
        key(m.task.taskId) {
            val h = max((m.end - m.start) * TimetableGeo.ppm - 2f, 14f)
            TimetableDragGhostView(
                title = TimetableRules.title(m.task.emoji, m.task.taskName),
                taskStyle = TimetableTaskStyle(m.task), start = m.start, end = m.end,
                invalid = null, heightPx = h,
                modifier = Modifier
                    .offset(x = (TimetableRenderedRects.map["t-${m.task.taskId}"]?.first ?: 1f).dp, y = metrics.y(m.start).dp)
                    .size((TimetableRenderedRects.map["t-${m.task.taskId}"]?.second ?: max(metrics.colWidth - 2f, 4f)).dp, h.dp)
                    .alpha(0.9f)
                    .then(if (invalid != null) Modifier.border(2.dp, danger, RoundedCornerShape(7.dp)) else Modifier),
            )
        }
    }
}

// MARK: - 지금 시각 빨간 선

/** 새벽 00:30 이고 어제 칸이 01:00 까지 이어지면 어제 칸 맨 아래(24:30)에 그려요 */
@Composable
private fun TimetableNowLine(date: String, from: Int, to: Int, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(KST.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            // 다음 분이 시작될 때까지 기다려요 (iOS TimelineView(.everyMinute))
            val ms = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(ms)
            now = KST.now()
        }
    }
    val m: Int? = when {
        date == now.date && now.minutes >= from && now.minutes <= to -> now.minutes
        KST.addDays(date, 1) == now.date && now.minutes + 1440 <= to -> now.minutes + 1440
        else -> null
    }
    if (m == null) return
    // 스펙 9.6: 1.5dp action/primary 선 + 왼쪽 끝 8dp 원
    val primary = NC.actionPrimary
    Canvas(modifier) {
        val y = ((m - from) * TimetableGeo.ppm).dp.toPx()
        val w = 1.5.dp.toPx()
        drawRect(primary, topLeft = Offset(0f, y - w / 2), size = androidx.compose.ui.geometry.Size(size.width, w))
        drawCircle(primary, radius = 4.dp.toPx(), center = Offset(0f, y))
    }
}

/** 지금 시각 글자 (시간 눈금 칸 안, Caption/SM · primary) — 오늘이 보일 때만 */
@Composable
private fun TimetableNowLabel(dates: List<String>, from: Int, to: Int, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(KST.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val ms = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(ms)
            now = KST.now()
        }
    }
    val m: Int? = when {
        dates.contains(now.date) && now.minutes >= from && now.minutes <= to -> now.minutes
        dates.any { KST.addDays(it, 1) == now.date } && now.minutes + 1440 <= to -> now.minutes + 1440
        else -> null
    }
    if (m == null) return
    Box(modifier) {
        Text(
            KST.formatMinutes(now.minutes),
            style = NType.captionSM.copy(fontFeatureSettings = "tnum"),
            color = NC.actionPrimary,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .width((TimetableGeo.labelWidth - 3f).dp)
                .offset(y = ((m - from) * TimetableGeo.ppm - 7f).dp)
                .background(NC.bgSurface),
        )
    }
}

// MARK: - 시간대 습관 블록

@Composable
fun TimetableHabitBlockView(
    habit: HabitBlock,
    compact: Boolean = false,
    date: String,
    modifier: Modifier = Modifier,
    /** 누를 때 (timetable.habit_block_toggle) */
    onTap: (() -> Unit)? = null,
) {
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val green = NC.statusSuccessFg
    val fg = NC.statusSuccessFg
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (habit.checked) NC.statusSuccessBg else NC.statusSuccessBg.copy(alpha = 0.5f), shape)
            .timetableDashedBorder(1.dp, green.copy(alpha = 0.4f), shape, 3.dp, 2.dp)
            .clickable(enabled = !busy) {
                onTap?.invoke()
                scope.launch {
                    busy = true
                    try {
                        Mutation.run(invalidate = listOf(DataKey.timetable, DataKey.habits, DataKey.home)) {
                            if (habit.checked) {
                                API.send("DELETE", "/v1/habits/${habit.habitId}/checks/$date")
                            } else {
                                API.send("PUT", "/v1/habits/${habit.habitId}/checks/$date")
                            }
                        }
                    } finally {
                        busy = false
                    }
                }
            }
            .semantics { contentDescription = "${habit.name} ${if (habit.checked) "체크 풀기" else "체크하기"}" }
            .padding(horizontal = 5.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SfImage(if (habit.checked) "checkmark.circle.fill" else "circle", size = if (compact) 9.dp else 11.dp, tint = fg)
        Text(
            (habit.emoji?.let { "$it " } ?: "") + habit.name,
            style = pt(if (compact) 10 else 11, PTWeight.semibold)
                .copy(textDecoration = if (habit.checked) TextDecoration.LineThrough else null),
            color = fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun TimetableGridViewWeekPreview() {
    NaleulTheme(dark = false) {
        val start = KST.startOfWeek(KST.today())
        val board = remember { TimetableBoardModel.sample(start = start, end = KST.addDays(start, 6)) }
        val days = board.days
        val hours = TimetableLayout.visibleHours(days, activities = board.activities)
        TimetableGridView(
            board = board, days = days, activities = board.activities,
            startHour = hours.startHour, endHour = hours.endHour,
            highlightIds = setOf(2001), emptyClickEnabled = true,
            modifier = Modifier.fillMaxSize().canvasBackground().padding(horizontal = 16.dp),
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun TimetableGridViewDayPreview() {
    NaleulTheme(dark = false) {
        val d = KST.today()
        val board = remember { TimetableBoardModel.sample(start = d, end = d) }
        val days = board.days
        val hours = TimetableLayout.visibleHours(days, activities = board.activities)
        TimetableGridView(
            board = board, days = days, activities = board.activities,
            startHour = hours.startHour, endHour = hours.endHour,
            planVsActual = true, emptyClickEnabled = true,
            modifier = Modifier.fillMaxSize().canvasBackground().padding(horizontal = 16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TimetableHabitBlockViewPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TimetableHabitBlockView(
                habit = HabitBlock(habitId = 1, name = "샤워하기", emoji = "🚿", start = "2026-10-13T20:00:00", end = "2026-10-13T20:30:00", checked = false),
                date = "2026-10-13",
                modifier = Modifier.size(200.dp, 40.dp),
            )
            TimetableHabitBlockView(
                habit = HabitBlock(habitId = 2, name = "물 2L", emoji = "💧", start = "2026-10-13T08:00:00", end = "2026-10-13T08:20:00", checked = true),
                compact = true, date = "2026-10-13",
                modifier = Modifier.size(60.dp, 30.dp),
            )
        }
    }
}
