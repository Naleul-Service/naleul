// model/TimetableLayout.kt
//
// iOS Models/TimetableLayout.swift 를 옮긴 파일.
// 웹 features/timetable/layout.ts — TimeTable 격자에 블록을 놓기 위한 순수 계산 (화면과 분리).
// 모든 위치는 "그날 0시부터 몇 분" (음수 · 1440 이상 가능) 이에요.
//
// Swift 와 다른 점
//  - Swift 의 튜플 반환 (startHour:, endHour:) → data class VisibleHours(startHour, endHour)
//    `val (startHour, endHour) = TimetableLayout.visibleHours(days)` 처럼 풀어 쓸 수 있어요.
//  - Swift enum Pending(연관값 있는 case) → sealed class Pending { task(...), fixed(...) }
//  - Placed 같은 값은 val + copy(...) 로 바꿔요 (Swift struct 의 "복사해서 고치기"와 같은 효과)
//  - Proposal 은 화면에서 start/end 를 직접 고쳐서 var (copy() 한 복사본에서 바꾸세요)

package com.naleul.naleul.model

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

import kotlin.math.ceil
import kotlin.math.floor

object TimetableLayout {
    const val defaultStartHour = 7
    const val defaultEndHour = 24

    /** 취침이 자정을 넘기면(예: 01:00) 격자를 다음 날 새벽까지 늘려요. 최대 다음 날 06:00 */
    const val maxEndHour = 30

    /** 너무 짧은 블록도 글자가 보이도록 */
    const val minTaskMinutes = 15

    /** 기상 전 · 취침 후로 더 보여줄 시간 — 기상·취침을 끌어서 바꿀 수 있게 (분) */
    const val sleepMarginMinutes = 120

    /** 한 시각에 같이 둘 수 있는 (밀어낼 수 없는) Task 수 — 백엔드 Concurrency.MAX_TASKS_AT_ONCE 와 같아야 해요 */
    const val maxTasksAtOnce = 2

    data class ShownTime(
        val startAt: String,
        val endAt: String? = null,
        /** true 면 실제로 한 시각, false 면 계획 시각 */
        val actual: Boolean,
    ) {
        companion object {}
    }

    data class MinuteRange(
        val start: Int,
        val end: Int,
    ) {
        companion object {}
    }

    /** visibleHours 결과 (iOS 튜플 (startHour:, endHour:)) */
    data class VisibleHours(
        val startHour: Int,
        val endHour: Int,
    ) {
        companion object {}
    }

    /**
     * 블록을 그릴 시각 (백엔드 TimetableQueryService.blockRange 와 같은 규칙)
     *  - 완료 전: 계획 시각
     *  - 완료 후: 실제 시각이 기록돼 있으면 실제로 한 시각, 없으면 계획 시각
     * 시간이 없으면 null (시간 미정)
     */
    fun shownTime(t: TimeBlockTask): ShownTime? {
        val aS = t.actualStartAt
        val aE = t.actualEndAt
        if (t.taskStatus == TaskStatus.COMPLETED && aS != null && aE != null) {
            return ShownTime(startAt = aS, endAt = aE, actual = true)
        }
        val s = t.plannedStartAt ?: return null
        return ShownTime(startAt = s, endAt = t.plannedEndAt, actual = false)
    }

    /** 계획 시각 기준 구간 (완료했어도 원래 계획한 자리) */
    fun plannedMinutes(day: String, t: TimeBlockTask): MinuteRange? {
        val s = t.plannedStartAt ?: return null
        val start = KST.minutesFrom(day, s)
        val end = t.plannedEndAt?.let { KST.minutesFrom(day, it) } ?: (start + (t.plannedDurationMinutes ?: 30))
        return MinuteRange(start = start, end = end)
    }

    /** 그날 0시 기준 분으로 바꾼 블록 구간. 끝이 없으면 소요 시간(없으면 30분)으로 */
    fun shownMinutes(day: String, t: TimeBlockTask, fallbackMinutes: Int? = null): MinuteRange? {
        val st = shownTime(t) ?: return null
        val start = KST.minutesFrom(day, st.startAt)
        val end = st.endAt?.let { KST.minutesFrom(day, it) }
            ?: (start + (fallbackMinutes ?: t.plannedDurationMinutes ?: 30))
        return MinuteRange(start = start, end = end)
    }

    /** 격자에 보여줄 시간 범위 (시 단위). 기본은 하루 범위(기상~취침) + 앞뒤 2시간, 그 밖에 Task 가 있으면 넓혀요 */
    fun visibleHours(days: List<TimetableDay>, activities: List<ActualActivity> = emptyList()): VisibleHours {
        if (days.isEmpty()) return VisibleHours(startHour = defaultStartHour, endHour = defaultEndHour)
        var start = Int.MAX_VALUE
        var end = Int.MIN_VALUE
        for (d in days) {
            start = minOf(start, KST.minutesFrom(d.date, d.dayRange.start) - (if (d.sleep?.wake != null) sleepMarginMinutes else 0))
            end = maxOf(end, KST.minutesFrom(d.date, d.dayRange.end) + (if (d.sleep?.bed != null) sleepMarginMinutes else 0))
            for (t in d.tasks) {
                val m = shownMinutes(d.date, t, fallbackMinutes = 30) ?: continue
                start = minOf(start, m.start)
                end = maxOf(end, m.end)
            }
            for (a in activities) {
                val s = KST.minutesFrom(d.date, a.startAt)
                val e = KST.minutesFrom(d.date, a.endAt)
                if (e <= 0 || s >= maxEndHour * 60) continue
                start = minOf(start, maxOf(s, 0))
                end = maxOf(end, minOf(e, maxEndHour * 60))
            }
        }
        val startHour = maxOf(0, minOf(defaultEndHour - 1, floor(start.toDouble() / 60).toInt()))
        // 끝은 최소 24시 — 취침 직전·늦은 밤으로도 끌어다 놓을 수 있게
        val endHour = minOf(maxEndHour, maxOf(startHour + 1, ceil(end.toDouble() / 60).toInt(), defaultEndHour))
        return VisibleHours(startHour = startHour, endHour = endHour)
    }

    // MARK: 배치

    data class Placed<Item>(
        val item: Item,
        /** 그날 0시 기준 분 (화면 범위로 잘림) */
        val top: Int,
        val bottom: Int,
        /** 잘리기 전 원래 시각(분) — 글자로 보여줄 때 */
        val start: Int,
        val end: Int,
        /** 원래 시간이 화면 범위 밖까지 이어지는지 */
        val clippedTop: Boolean,
        val clippedBottom: Boolean,
        /** 겹칠 때 나란히 놓기 위한 열 정보 */
        val col: Int = 0,
        val cols: Int = 1,
        /** 한 Task 를 여러 구간으로 나눠 그릴 때 몇 번째 조각인지 (나눠서 한 실제 시간) */
        val part: Int = 0,
    ) {
        companion object {}
    }

    private data class Clip(
        val top: Int,
        val bottom: Int,
        val start: Int,
        val end: Int,
        val clippedTop: Boolean,
        val clippedBottom: Boolean,
    )

    private fun clip(start: Int, end: Int, from: Int, to: Int): Clip? {
        val top = maxOf(start, from)
        val bottom = minOf(end, to)
        if (bottom <= top) return null
        return Clip(top = top, bottom = bottom, start = start, end = end, clippedTop = start < from, clippedBottom = end > to)
    }

    fun placeFixed(day: TimetableDay, from: Int, to: Int): List<Placed<FixedBlock>> {
        // 기상 · 취침 수면은 placeSleep 이 자르지 않은 원본으로 따로 그려요 (기상·취침 가장자리를 끌 수 있게).
        // 그 둘이 아닌 수면(예전 서버가 늦잠 기상을 못 알아본 경우 등)은 여기서 그대로 그려서 사라지지 않게 해요
        fun same(a: FixedBlock, b: FixedBlock?): Boolean {
            if (b == null) return false
            return a.lifePatternId == b.lifePatternId && a.targetDate == b.targetDate
        }
        val out = mutableListOf<Placed<FixedBlock>>()
        for (b in day.fixedBlocks) {
            if (b.patternType == LifePatternType.SLEEP && (same(b, day.sleep?.wake) || same(b, day.sleep?.bed))) continue
            val c = clip(KST.minutesFrom(day.date, b.start), KST.minutesFrom(day.date, b.end), from, to) ?: continue
            out.add(
                Placed(
                    item = b, top = c.top, bottom = c.bottom, start = c.start, end = c.end,
                    clippedTop = c.clippedTop, clippedBottom = c.clippedBottom,
                )
            )
        }
        return out
    }

    enum class SleepEdge {
        wake, bed;

        val rawValue: String get() = name

        companion object {
            fun fromRaw(raw: String?): SleepEdge? = SleepEdge.entries.firstOrNull { it.rawValue == raw }
        }
    }

    data class PlacedSleep(
        val placed: Placed<FixedBlock>,
        /** wake = 아침에 끝나는 수면 (아래 가장자리 = 기상), bed = 밤에 시작하는 수면 (위 가장자리 = 취침) */
        val edge: SleepEdge,
    ) {
        companion object {}
    }

    /** 그날 칸에 그릴 수면 블록 (기상 전 · 취침 후) */
    fun placeSleep(day: TimetableDay, from: Int, to: Int): List<PlacedSleep> {
        val out = mutableListOf<PlacedSleep>()
        for (edge in listOf(SleepEdge.wake, SleepEdge.bed)) {
            val b = (if (edge == SleepEdge.wake) day.sleep?.wake else day.sleep?.bed) ?: continue
            val c = clip(KST.minutesFrom(day.date, b.start), KST.minutesFrom(day.date, b.end), from, to) ?: continue
            out.add(
                PlacedSleep(
                    placed = Placed(
                        item = b, top = c.top, bottom = c.bottom, start = c.start, end = c.end,
                        clippedTop = c.clippedTop, clippedBottom = c.clippedBottom,
                    ),
                    edge = edge,
                )
            )
        }
        return out
    }

    enum class TaskBasis {
        /** 화면 기본 (완료 전엔 계획 시각, 완료 후엔 실제 시각) */
        shown,

        /** 모두 계획 시각 (일간 "계획" 칸) */
        planned,

        /** 완료한 Task 만, 실제 시각(없으면 계획 시각) (일간 "실제" 칸) */
        done;

        companion object {}
    }

    /** Task 블록 배치. 겹치면 나란히 그려요 */
    fun placeTasks(day: TimetableDay, from: Int, to: Int, basis: TaskBasis = TaskBasis.shown): List<Placed<TimeBlockTask>> {
        val items = mutableListOf<Placed<TimeBlockTask>>()
        for (t in day.tasks) {
            if (basis == TaskBasis.done && t.taskStatus != TaskStatus.COMPLETED) continue
            // 여러 번 나눠서 한 완료 Task 는 실제로 한 구간마다 블록 하나씩 (계획 기준일 땐 계획 자리 하나)
            val actualSegs: List<ActualSegment> =
                if (basis != TaskBasis.planned && t.taskStatus == TaskStatus.COMPLETED) (t.actualSegments ?: emptyList()) else emptyList()
            // 자동 배치가 점심 앞뒤로 나눠 넣은 Task 는 계획 구간마다 블록 하나씩 (실제 시각으로 그릴 땐 제외)
            val drawnAtActual = basis != TaskBasis.planned && t.taskStatus == TaskStatus.COMPLETED && t.actualStartAt != null
            val segs: List<ActualSegment> =
                if (actualSegs.size > 1) actualSegs else if (drawnAtActual) emptyList() else (t.plannedSegments ?: emptyList())
            val ranges: List<MinuteRange?> =
                if (segs.size > 1) {
                    segs.map { MinuteRange(start = KST.minutesFrom(day.date, it.start), end = KST.minutesFrom(day.date, it.end)) }
                } else {
                    listOf(if (basis == TaskBasis.planned) plannedMinutes(day.date, t) else shownMinutes(day.date, t))
                }
            for ((part, r) in ranges.withIndex()) {
                val m = r ?: continue
                val c = clip(m.start, maxOf(m.end, m.start + minTaskMinutes), from, to) ?: continue
                items.add(
                    Placed(
                        item = t, top = c.top, bottom = c.bottom, start = c.start, end = m.end,
                        clippedTop = c.clippedTop, clippedBottom = c.clippedBottom, part = part,
                    )
                )
            }
        }
        return layoutColumns(items)
    }

    /** 시간대 습관 블록 (V56) — 그날 칸의 위치 */
    fun placeHabits(day: TimetableDay, from: Int, to: Int): List<Placed<HabitBlock>> =
        (day.habits ?: emptyList()).mapNotNull { h ->
            val c = clip(KST.minutesFrom(day.date, h.start), KST.minutesFrom(day.date, h.end), from, to)
                ?: return@mapNotNull null
            Placed(
                item = h, top = c.top, bottom = c.bottom, start = c.start, end = c.end,
                clippedTop = c.clippedTop, clippedBottom = c.clippedBottom,
            )
        }

    /** 겹치는 블록을 나란히 놓도록 열(col / cols)을 정해요 (위→아래 순으로 돌려줘요) */
    fun <T> layoutColumns(list: List<Placed<T>>): List<Placed<T>> {
        val sorted = list.sortedWith { a, b ->
            if (a.top != b.top) a.top.compareTo(b.top) else b.bottom.compareTo(a.bottom)
        }
        val out = mutableListOf<Placed<T>>()
        var cluster = mutableListOf<Placed<T>>()
        var clusterEnd = Int.MIN_VALUE
        var colEnds = mutableListOf<Int>()

        fun flush() {
            val cols = maxOf(1, colEnds.size)
            out.addAll(cluster.map { it.copy(cols = cols) })
            cluster = mutableListOf()
            colEnds = mutableListOf()
        }

        for (p in sorted) {
            if (p.top >= clusterEnd) flush()
            var col = colEnds.indexOfFirst { it <= p.top }
            if (col == -1) {
                col = colEnds.size
                colEnds.add(p.bottom)
            } else {
                colEnds[col] = p.bottom
            }
            cluster.add(p.copy(col = col, cols = 1))
            clusterEnd = maxOf(clusterEnd, p.bottom)
        }
        flush()
        return out
    }

    /** 그날 칸에 그릴 "실제로 한 일" 블록. 자정을 넘긴 기록은 날짜마다 잘린 조각으로 그려요 */
    fun placeActivities(day: String, activities: List<ActualActivity>, from: Int, to: Int): List<Placed<ActualActivity>> {
        val out = mutableListOf<Placed<ActualActivity>>()
        for (a in activities) {
            val start = KST.minutesFrom(day, a.startAt)
            val end = KST.minutesFrom(day, a.endAt)
            if (end <= 0 || start >= to) continue
            val top = maxOf(start, 0, from)
            val bottom = minOf(maxOf(end, start + minTaskMinutes), to)
            if (bottom <= top) continue
            out.add(
                Placed(
                    item = a, top = top, bottom = bottom, start = start, end = end,
                    clippedTop = start < top, clippedBottom = end > bottom,
                )
            )
        }
        return out
    }

    // MARK: 놓을 수 있는지

    /** 자동 배치가 밀어낼 수 없는 블록 — 백엔드 isMovableEngineBlock 의 반대 (건너뛴 Task 는 자리를 차지하지 않아요) */
    fun isBlockingTask(t: TimeBlockTask): Boolean =
        t.taskStatus != TaskStatus.SKIPPED &&
            (t.locked || t.placedBy != PlacedBy.ENGINE || t.taskStatus != TaskStatus.TODO || t.sourceType == TaskSourceType.MISSION)

    /** window 안에서 ranges 가 가장 많이 겹치는 순간의 개수 (끝과 시작이 같으면 겹치지 않음) */
    fun maxOverlap(ranges: List<MinuteRange>, window: MinuteRange): Int {
        val events = mutableListOf<Pair<Int, Int>>()
        for (r in ranges) {
            if (!(r.start < window.end && r.end > window.start)) continue
            events.add(Pair(maxOf(r.start, window.start), 1))
            events.add(Pair(minOf(r.end, window.end), -1))
        }
        // 같은 시각이면 끝(-1)을 먼저 → 10~11 과 11~12 는 겹치지 않음
        val sorted = events.sortedWith(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
        var cur = 0
        var best = 0
        for ((_, d) in sorted) {
            cur += d
            best = maxOf(best, cur)
        }
        return best
    }

    /**
     * start~end 에 놓으면 한 시각에 밀어낼 수 없는 Task 가 3개 이상이 되는지.
     * 놓을 수 없으면 그 자리에 이미 있는 Task 들, 놓을 수 있으면 null
     *  - exclude: 같이 옮기는 Task 들 (원래 자리는 빼고 봐요)
     *  - extra: 같이 옮기는 다른 Task 들의 새 자리
     */
    fun crowdedTasks(
        day: TimetableDay,
        start: Int,
        end: Int,
        exclude: Set<Int>,
        extra: List<MinuteRange> = emptyList(),
    ): List<TimeBlockTask>? {
        val hits = mutableListOf<Pair<TimeBlockTask, MinuteRange>>()
        for (t in day.tasks) {
            if (exclude.contains(t.taskId) || !isBlockingTask(t)) continue
            val r = shownMinutes(day.date, t, fallbackMinutes = 30)
            if (r != null && r.start < end && r.end > start) {
                hits.add(Pair(t, r))
            }
        }
        val ranges = hits.map { it.second } + extra
        if (maxOverlap(ranges, window = MinuteRange(start = start, end = end)) < maxTasksAtOnce) return null
        return hits.map { it.first }
    }

    // MARK: 드래그 결과를 서버 응답 전에 먼저 보여주기 (낙관적 표시)

    /** iOS enum Pending { case task(...), case fixed(...) } */
    sealed class Pending {
        /** Task 를 date 의 start~end 로 옮김. date 가 null 이면 "시간 미정"으로 보냄 */
        data class task(val taskId: Int, val date: String?, val start: Int, val end: Int) : Pending() {
            companion object {}
        }

        /** 고정 블록(key)을 그날 start~end 로 바꿈 */
        data class fixed(val key: String, val date: String, val start: Int, val end: Int) : Pending() {
            companion object {}
        }

        companion object {}
    }

    /** 서버 응답이 오기 전까지 옮긴 위치에 블록을 그려요 */
    fun applyPending(days: List<TimetableDay>, pending: List<Pending>): List<TimetableDay> {
        if (pending.isEmpty()) return days
        // 같은 대상이 여러 번 있으면 나중 것이 이겨요 (Swift 딕셔너리 대입과 같아요)
        val taskMoves = linkedMapOf<Int, Pending.task>()
        val fixedMoves = linkedMapOf<String, Pending.fixed>()
        for (p in pending) {
            when (p) {
                is Pending.task -> taskMoves[p.taskId] = p
                is Pending.fixed -> fixedMoves[p.key] = p
            }
        }
        val all = days.flatMap { it.tasks + it.unscheduledTasks }

        return days.map { d0 ->
            // 클로저 안에서 바꾸는 중인 값을 읽지 않도록 날짜를 상수로
            val dayDate = d0.date
            val tasks = d0.tasks.filter { taskMoves[it.taskId] == null }.toMutableList()
            val unscheduled = d0.unscheduledTasks.filter { taskMoves[it.taskId] == null }.toMutableList()
            for ((id, p) in taskMoves) {
                val t = all.firstOrNull { it.taskId == id } ?: continue
                if (p.date == dayDate) {
                    tasks.add(
                        t.copy(
                            date = dayDate,
                            plannedStartAt = KST.toDateTime(dayDate, p.start),
                            plannedEndAt = KST.toDateTime(dayDate, p.end),
                            plannedSegments = null, // 옮기면 한 덩어리 (서버 응답 전까지 옛 조각 자리에 그리지 않게)
                            locked = true,
                            placedBy = PlacedBy.USER,
                            missed = false,
                        )
                    )
                } else if (p.date == null && (t.date ?: t.plannedStartAt?.take(10)) == dayDate) {
                    unscheduled.add(t.copy(plannedStartAt = null, plannedEndAt = null, plannedSegments = null, locked = false))
                }
            }
            val fixedBlocks = d0.fixedBlocks.map { b ->
                val p = fixedMoves[b.key]
                if (p == null || p.date != dayDate) {
                    b
                } else {
                    b.copy(
                        start = KST.toDateTime(dayDate, p.start),
                        end = KST.toDateTime(dayDate, p.end),
                        overridden = true,
                    )
                }
            }
            // 수면 블록은 이틀 칸에 걸쳐 보여서, 끌어놓은 칸의 날짜 기준으로 바꾼 시각을 양쪽에 반영해요
            fun moveSleep(b: FixedBlock?): FixedBlock? {
                if (b == null) return null
                val p = fixedMoves[b.key] ?: return b
                return b.copy(
                    start = KST.toDateTime(p.date, p.start),
                    end = KST.toDateTime(p.date, p.end),
                    overridden = true,
                )
            }
            val s = d0.sleep
            val sleep = if (s != null) SleepEdges(wake = moveSleep(s.wake), bed = moveSleep(s.bed)) else null
            d0.copy(tasks = tasks, unscheduledTasks = unscheduled, fixedBlocks = fixedBlocks, sleep = sleep)
        }
    }

    // MARK: "AI로 배치하기" 미리보기

    /** 미리보기 제안 하나 (화면 상태). 끌어서 자리를 고치는 곳(`q.start = …`)이 있어서 var — 복사본에서 바꾸세요: val q = p.copy(); q.start = … */
    data class Proposal(
        var taskId: Int,
        var reason: String? = null,
        /** AI 가 처음 제안한 자리 — 확정할 때 "고쳤는지" 비교용 */
        var proposedStart: String,
        var proposedEnd: String,
        /** 지금 자리 (끌어서 고치면 바뀜) */
        var start: String,
        var end: String,
    ) {
        val id: Int get() = taskId

        companion object {}
    }

    /** 제안을 점선 블록으로 덧입혀요: 시간 미정 칸에서 빼서 제안 자리에 그려요 (저장 전) */
    fun applyProposals(days: List<TimetableDay>, proposals: List<Proposal>?): List<TimetableDay> {
        if (proposals.isNullOrEmpty()) return days
        val ids = proposals.map { it.taskId }.toSet()
        val all = days.flatMap { it.tasks + it.unscheduledTasks }
        return days.map { d0 ->
            val tasks = d0.tasks.filter { !ids.contains(it.taskId) }.toMutableList()
            val unscheduled = d0.unscheduledTasks.filter { !ids.contains(it.taskId) }
            for (p in proposals) {
                if (p.start.take(10) != d0.date) continue
                val t = all.firstOrNull { it.taskId == p.taskId } ?: continue
                tasks.add(
                    t.copy(
                        date = d0.date,
                        plannedStartAt = p.start,
                        plannedEndAt = p.end,
                        plannedSegments = null,
                        placedBy = PlacedBy.ENGINE,
                        locked = false,
                        missed = false,
                        placementReason = p.reason ?: t.placementReason,
                        proposed = true,
                    )
                )
            }
            d0.copy(tasks = tasks, unscheduledTasks = unscheduled)
        }
    }
}

/** ForEach(LazyColumn key) 용 — 나눠서 한 Task 는 조각마다 따로 (iOS extension Placed where Item == TimeBlockTask) */
val TimetableLayout.Placed<TimeBlockTask>.uid: String
    get() = "${item.taskId}-$part"
