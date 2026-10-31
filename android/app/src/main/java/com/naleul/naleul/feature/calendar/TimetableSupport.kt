// feature/calendar/TimetableSupport.kt
//
// iOS Features/Calendar/TimetableSupport.swift 를 옮긴 파일.
// 캘린더(TimeTable) 화면들이 같이 쓰는 작은 규칙 · 타입 모음 (화면 없음).
// 웹 원본:
//   features/timetable/ui/TimeGrid.tsx        — canDragTask, canDragFixed, Selection
//   features/timetable/ui/useGridDrag.ts      — DragSource, DropTarget, groupMoves, canUnschedule
//   features/timetable/ui/useTimetableInteractions.tsx — chainOf, fullFixedEnd, Opened/Editing/ScopeAsk
//   features/timetable/ui/ScopeChooser.tsx    — scopeOptions
//   features/timetable/ui/TaskDetail.tsx      — taskPermissions, currentInput
//   features/timetable/ui/CarryProposalBar.tsx — relDay, eulReul
//   features/timetable/ui/AutoPlaceBar.tsx    — unplacedOf
//   features/timetable/ui/ActivityForm.tsx    — ActivityDraft, draftOf
//
// 왜 따로 두나요? → 격자 · 상세 시트 · 아래 확인 바가 모두 같은 규칙을 써야 해서 한 곳에 모았어요.
//
// Swift 와 다른 점
//  - 연관값이 있는 Swift enum(TimetableDragSource, TimetableSheet …) → Kotlin sealed class + 소문자 data class case
//    예) TimetableDragSource.task(task = t, date = d, start = s, end = e, group = emptyList())
//  - UUID → String (UUID.randomUUID().toString())
//  - CGFloat(pt) 길이 → Float(dp 값). 화면에 쓸 때 .dp 를 붙여요.

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

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import java.util.UUID
import kotlin.math.floor
import kotlin.math.roundToInt

// MARK: - 격자 크기

object TimetableGeo {
    /** 1시간 높이 (dp) (웹은 60px — 휴대폰은 조금 줄였어요) */
    const val hourHeight: Float = 56f
    /** 1분당 dp */
    const val ppm: Float = 56f / 60f
    /** 왼쪽 시간 눈금 폭 (dp) */
    const val labelWidth: Float = 36f
    /** 주간 날짜 머리글 높이 (dp) */
    const val weekHeaderHeight: Float = 58f
    /** 일간 날짜 머리글 높이 ("계획 | 실제" 칸 이름 포함) */
    const val dayHeaderHeight: Float = 70f
    /** 시간 미정 칸에 처음 보여줄 개수 (웹 UNSCHEDULED_VISIBLE) */
    const val unscheduledVisible = 3
    const val chipHeight: Float = 22f
    /** 시간 미정 줄 최대 높이 (넘으면 칸 안에서 스크롤 — 웹 max-h-32) */
    const val maxUnscheduledHeight: Float = 132f
    /** 길게 눌러야 끌기 시작 (웹 터치 TOUCH_HOLD_MS = 500) — 짧으면 "대고 잠깐 멈췄다 내리는" 스크롤이 끌기로 잡혀요 (2026-10: 0.35 → 0.5) */
    const val holdSeconds: Double = 0.5
}

/** 9.5 처럼 소수 글자 크기가 필요할 때 (pt() 는 Int 만 받아요) */
fun timetableFont(size: Double, weight: PTWeight = PTWeight.regular): TextStyle =
    pt(size.toInt(), weight).copy(fontSize = size.sp)

// MARK: - 규칙

data class TimetableTaskPermissions(
    /** 시간 변경 (완료 · 미션은 백엔드 409) */
    val editTime: Boolean,
    /** 이름 · 세부 목표 · 마일스톤 · 마감일: 목표에 연결된 일회성 Task 만 */
    val editContent: Boolean,
    val unlock: Boolean,
    /** 시간 미정으로 (루틴 · 미션은 400) */
    val unschedule: Boolean,
    val delete: Boolean,
    /** 중요도: 루틴 · 미션도 그날 Task 만 바꿀 수 있어요 (완료한 Task 는 보기만) */
    val editPriority: Boolean = false,
) {
    companion object {}
}

data class TimetableUnplaced(
    val date: String,
    val count: Int,
) {
    companion object {}
}

object TimetableRules {
    /** 기상은 취침보다, 취침은 기상보다 30분 이상 떨어져야 해요 (같은 날 칸 기준) */
    const val minAwakeMinutes = 30

    /** 드래그로 옮길 수 있는 Task (완료 · 미션은 백엔드에서 409) */
    fun canDragTask(t: TimeBlockTask): Boolean =
        t.taskStatus != TaskStatus.COMPLETED && t.sourceType != TaskSourceType.MISSION

    /** 자동 배치가 점심 앞뒤로 나눠 넣은 Task 의 전체 길이(분) — 나눠 넣지 않았으면 null (웹 splitLength) */
    fun splitLength(t: TimeBlockTask): Int? {
        if (t.taskStatus == TaskStatus.COMPLETED || (t.plannedSegments?.size ?: 0) < 2) return null
        return t.plannedDurationMinutes?.toInt()
    }

    /** 완료한 Task 의 "실제로 한 시간" 블록을 끌어서 옮기거나 늘리고 줄일 수 있는지 (웹 canDragActual) */
    fun canDragActual(t: TimeBlockTask): Boolean =
        t.taskStatus == TaskStatus.COMPLETED && t.sourceType != TaskSourceType.MISSION && t.plannedStartAt != null

    /**
     * 드래그로 옮길 수 있는 고정 블록: 그날 안에서 시작하고 끝나는 것만
     * (자정을 넘기는 수면은 날짜별 조각만 보여서 끌어도 실제 길이를 알 수 없어요 → 상세의 시간 변경으로)
     */
    fun canDragFixed(b: FixedBlock, date: String): Boolean =
        !b.clippedFromPreviousDay && b.targetDate == date && KST.minutesFrom(date, b.end) < 1440

    /** 묶음에 넣을 수 있는 Task: 시간이 정해져 있고 드래그로 옮길 수 있는 것 */
    fun groupable(t: TimeBlockTask): Boolean =
        t.plannedStartAt != null && canDragTask(t) && t.proposed != true

    /** 블록 날짜 (계획 시작일) */
    fun blockDay(t: TimeBlockTask): String? = t.plannedStartAt?.take(10)

    /** 루틴 Task 가 속한 날짜 (루틴 인스턴스 날짜) */
    fun routineDay(t: TimeBlockTask): String? = t.date ?: blockDay(t)

    private data class ChainItem(val x: TimeBlockTask, val s: Int, val e: Int)

    /** t 와 앞뒤로 바로 이어진 Task 들 (사이 간격 10분 이하). 쪼개 둔 연속 작업을 한 번에 고르려고 써요 */
    fun chainOf(t: TimeBlockTask, days: List<TimetableDay>): List<TimeBlockTask> {
        val date = blockDay(t) ?: return listOf(t)
        if (!groupable(t)) return listOf(t)
        val day = days.firstOrNull { it.date == date } ?: return listOf(t)
        val list = mutableListOf<ChainItem>()
        for (x in day.tasks) {
            if (!groupable(x) || blockDay(x) != date) continue
            val s = KST.minutesFrom(date, x.plannedStartAt ?: "${date}T00:00:00")
            val e = x.plannedEndAt?.let { KST.minutesFrom(date, it) } ?: (s + (x.plannedDurationMinutes ?: 30))
            list.add(ChainItem(x, s, e))
        }
        list.sortBy { it.s }
        val i = list.indexOfFirst { it.x.taskId == t.taskId }
        if (i < 0) return listOf(t)
        var lo = i
        var hi = i
        var end = list[i].e
        while (hi + 1 < list.size && list[hi + 1].s - end <= KST.snapMinutes) {
            hi += 1
            end = maxOf(end, list[hi].e)
        }
        var start = list[i].s
        while (lo - 1 >= 0 && start - list[lo - 1].e <= KST.snapMinutes) {
            lo -= 1
            start = minOf(start, list[lo].s)
        }
        return list.subList(lo, hi + 1).map { it.x }
    }

    /** 고정 블록이 자정을 넘기면(화면엔 24:00까지만) 다음 날 조각이나 패턴에서 실제 종료 시각을 찾아요 */
    fun fullFixedEnd(block: FixedBlock, days: List<TimetableDay>, patternEnd: String?): Int {
        val end = KST.minutesFrom(block.targetDate, block.end)
        if (end < 1440) return end
        val pieces = days.flatMap { it.fixedBlocks }
        val next = pieces.firstOrNull {
            it.clippedFromPreviousDay && it.lifePatternId == block.lifePatternId && it.targetDate == block.targetDate
        }
        if (next != null) return KST.minutesFrom(block.targetDate, next.end)
        if (patternEnd != null && !block.overridden) return 1440 + KST.timeToMinutes(patternEnd)
        return end
    }

    /** 어떤 작업을 할 수 있는지 (백엔드 규칙과 같게) */
    fun permissions(t: TimeBlockTask): TimetableTaskPermissions {
        val done = t.taskStatus == TaskStatus.COMPLETED
        val mission = t.sourceType == TaskSourceType.MISSION
        return TimetableTaskPermissions(
            editTime = !done && !mission,
            editContent = t.sourceType == TaskSourceType.MANUAL && t.goalCategoryId != null && t.generalCategoryId != null,
            unlock = t.locked && !done && !mission,
            unschedule = t.sourceType == TaskSourceType.MANUAL && t.plannedStartAt != null && !done,
            delete = !mission,
            editPriority = !done,
        )
    }

    /** 목표 상세 Task 수정 API 에 보낼 "지금 값 그대로"의 폼 (바꿀 항목만 덮어써서 보내요) */
    fun currentInput(t: TimeBlockTask): GoalTaskInput {
        val day = blockDay(t) ?: t.date ?: t.scheduledDate ?: KST.today()
        var startTime: String? = null
        var endTime: String? = null
        val ps = t.plannedStartAt
        if (ps != null) {
            val s = KST.minutesFrom(day, ps)
            val e = t.plannedEndAt?.let { KST.minutesFrom(day, it) } ?: (s + (t.plannedDurationMinutes ?: 30))
            startTime = KST.minutesToTime(s)
            endTime = KST.minutesToTime(e)
        }
        var duration: Int? = null
        val d = t.plannedDurationMinutes
        if (t.plannedStartAt == null && d != null && d >= 5 && d <= 720) duration = d
        return GoalTaskInput(
            taskName = t.taskName,
            emoji = t.emoji,
            generalCategoryId = t.generalCategoryId,
            milestoneId = t.milestoneId,
            date = day,
            startTime = startTime,
            endTime = endTime,
            durationMinutes = duration,
            dueDate = t.dueDate,
        )
    }

    /** 기준 날짜(base)에서 본 상대 날짜: 오늘 · 어제 · 그제 · 내일 · 10월 8일 (수) */
    fun relDay(date: String, base: String): String {
        if (date == base) return "오늘"
        if (date == KST.addDays(base, -1)) return "어제"
        if (date == KST.addDays(base, -2)) return "그제"
        if (date == KST.addDays(base, 1)) return "내일"
        return KST.formatMonthDay(date)
    }

    /** "을/를" — 받침에 맞게 (웹과 같이 마지막 글자가 한글이 아니면 "을(를)") */
    fun eulReul(word: String): String {
        if (word.isEmpty()) return "${word}을(를)"
        val c = word.codePointBefore(word.length)
        if (c < 0xAC00 || c > 0xD7A3) return "${word}을(를)"
        return word + (if ((c - 0xAC00) % 28 != 0) "을" else "를")
    }

    /** 배치 대상: 오늘 이후 날짜의 "시간 미정" 미완료 Task (미션은 방장이 시간을 정하므로 제외) */
    fun unplacedOf(days: List<TimetableDay>, today: String): List<TimetableUnplaced> =
        days.filter { it.date >= today }
            .map { d ->
                TimetableUnplaced(
                    date = d.date,
                    count = d.unscheduledTasks.count { it.taskStatus == TaskStatus.TODO && it.sourceType != TaskSourceType.MISSION },
                )
            }
            .filter { it.count > 0 }

    /** 블록 이름 앞에 이모지 */
    fun title(emoji: String?, name: String): String {
        if (!emoji.isNullOrEmpty()) return "$emoji $name"
        return name
    }
}

// MARK: - 적용 범위 (이날만 / 이번 주 / 앞으로)

enum class TimetableScopeKind {
    routine, fixed;

    companion object {}
}

data class TimetableScopeOption(
    val scope: ChangeScope,
    val label: String,
    val hint: String,
    /** 고를 수 없는 이유 (있으면 비활성) */
    val disabledReason: String? = null,
) {
    val id: String get() = scope.rawValue

    companion object {}
}

object TimetableScope {
    /**
     * 고를 수 있는 범위 (웹 scopeOptions)
     * - sameDay: 원래 날짜 그대로인지 (루틴을 다른 날로 옮기면 이날만)
     * - crossesMidnight: 새 시간이 자정을 넘기는지 (지금은 막지 않아요 — 12시간 넘게는 서버가 막아요)
     * - weekdayLabel: 고정 시간 "앞으로 ○마다"에 넣을 요일 이름 (있으면 요일별 옵션이 생겨요)
     */
    fun options(
        kind: TimetableScopeKind,
        isToday: Boolean,
        sameDay: Boolean = true,
        crossesMidnight: Boolean = false,
        weekdayLabel: String? = null,
    ): List<TimetableScopeOption> {
        val first = TimetableScopeOption(scope = ChangeScope.DAY, label = if (isToday) "오늘만" else "이날만", hint = "이 하루만 바뀌어요")
        if (kind == TimetableScopeKind.fixed && weekdayLabel != null) {
            return listOf(
                first,
                TimetableScopeOption(scope = ChangeScope.WEEK, label = "이번 주", hint = "이날부터 이번 주 일요일까지"),
                TimetableScopeOption(
                    scope = ChangeScope.WEEKDAY, label = "앞으로 ${weekdayLabel}마다",
                    hint = "이 요일만 기본 시간이 바뀌어요 (지난 기록은 그대로)",
                ),
                TimetableScopeOption(
                    scope = ChangeScope.FOLLOWING, label = "앞으로 매일",
                    hint = "모든 요일이 이 시간으로 (요일별로 다르게 정한 시간도 맞춰요)",
                ),
            )
        }
        val otherDay: String? =
            if (kind == TimetableScopeKind.routine && !sameDay) "다른 날로 옮길 때는 이날만 바꿀 수 있어요" else null
        return listOf(
            first,
            TimetableScopeOption(scope = ChangeScope.WEEK, label = "이번 주", hint = "이날부터 이번 주 일요일까지", disabledReason = otherDay),
            TimetableScopeOption(
                scope = ChangeScope.FOLLOWING, label = "앞으로 계속",
                hint = if (kind == TimetableScopeKind.routine) "남은 루틴 전부 + 루틴 기본 시간" else "기본 시간이 바뀌어요 (지난 기록은 그대로)",
                disabledReason = otherDay,
            ),
        )
    }

    /** 범위 아이콘 (웹 CalendarDays · CalendarRange · Repeat · Infinity) */
    fun icon(s: ChangeScope): String = when (s) {
        ChangeScope.DAY -> "calendar"
        ChangeScope.WEEK -> "calendar.badge.clock"
        ChangeScope.WEEKDAY -> "repeat"
        ChangeScope.FOLLOWING -> "infinity"
    }

    /** 고를 수 없게 된 범위를 골라 둔 상태면 이날만으로 */
    fun effective(chosen: ChangeScope, options: List<TimetableScopeOption>?): ChangeScope {
        if (options == null) return ChangeScope.DAY
        return if (options.any { it.scope == chosen && it.disabledReason == null }) chosen else ChangeScope.DAY
    }
}

// MARK: - 드래그 (웹 useGridDrag)

/** 묶음으로 같이 옮길 Task (끄는 Task 와 같은 날, 시간이 있는 것) */
data class TimetableGroupMember(
    val task: TimeBlockTask,
    val date: String,
    val start: Int,
    val end: Int,
) {
    companion object {}
}

/**
 * 끄는 대상. start/end 가 null 이면 "시간 미정" 칸에서 끌어온 Task
 * actual = 완료한 Task 의 실제로 한 시간 (part = 나눠서 했을 때 몇 번째 구간인지)
 */
sealed class TimetableDragSource {
    abstract val date: String
    abstract val start: Int?
    abstract val end: Int?

    data class task(
        val task: TimeBlockTask,
        override val date: String,
        override val start: Int?,
        override val end: Int?,
        override val group: List<TimetableGroupMember>,
    ) : TimetableDragSource() {
        companion object {}
    }

    data class fixed(
        val block: FixedBlock,
        override val date: String,
        override val start: Int,
        override val end: Int,
    ) : TimetableDragSource() {
        companion object {}
    }

    data class actual(
        val task: TimeBlockTask,
        override val date: String,
        override val start: Int,
        override val end: Int,
        val part: Int,
    ) : TimetableDragSource() {
        companion object {}
    }

    val isFixed: Boolean get() = this is fixed

    val isActual: Boolean get() = this is actual

    /** 계획 Task (실제 시간 블록은 null — 계획 블록은 그대로 두고 실제 시간만 바뀌어요) */
    val taskItem: TimeBlockTask? get() = (this as? TimetableDragSource.task)?.task

    open val group: List<TimetableGroupMember> get() = emptyList()

    /** 끄는 동안 유지할 길이 (분) */
    val duration: Int
        get() {
            val s = start
            val e = end
            if (s != null && e != null) return e - s
            val t = taskItem
            if (t != null) return t.plannedDurationMinutes ?: 60
            return 60
        }

    /** 시간 미정으로 보낼 수 있는 Task (백엔드: 루틴 · 미션은 400) */
    val canUnschedule: Boolean
        get() {
            val src = this as? task ?: return false
            return src.start != null && src.group.isEmpty() && src.task.sourceType == TaskSourceType.MANUAL &&
                src.task.taskStatus != TaskStatus.COMPLETED
        }

    companion object {}
}

/** move = 옮기기, resize = 아래 끝(끝 시각), resizeTop = 위 끝(시작 시각) */
enum class TimetableDragMode {
    move, resize, resizeTop;

    companion object {}
}

sealed class TimetableDropTarget {
    data class grid(val date: String, val start: Int, val end: Int) : TimetableDropTarget() {
        companion object {}
    }

    data class unscheduled(val date: String) : TimetableDropTarget() {
        companion object {}
    }

    companion object {}
}

data class TimetableDragState(
    val source: TimetableDragSource,
    val mode: TimetableDragMode,
    val target: TimetableDropTarget?,
    /** 놓을 수 없는 이유 (있으면 빨간 미리보기, 놓아도 저장 안 함) */
    val invalid: String?,
    /** 블록 위쪽에서 잡은 지점까지의 분 (놓을 때 같은 지점이 손가락 아래에 오도록) */
    val grab: Double?,
    /** 어느 블록의 제스처가 시작했는지 (제스처가 취소됐을 때 정리용) */
    val ownerId: String,
) {
    val draggingTaskIds: Set<Int>
        get() {
            val t = source.taskItem ?: return emptySet()
            return (listOf(t.taskId) + source.group.map { it.task.taskId }).toSet()
        }

    val draggingFixedKey: String?
        get() = (source as? TimetableDragSource.fixed)?.block?.key

    /** 끄는 실제 시간 블록 (Placed.uid 와 같은 "taskId-part") */
    val draggingActualUid: String?
        get() {
            val a = source as? TimetableDragSource.actual ?: return null
            return "${a.task.taskId}-${a.part}"
        }

    companion object {}
}

object TimetableDrag {
    /** 묶음 드래그 결과: 끈 Task 를 포함해 묶음 전체가 놓일 자리 (끈 만큼 같이 움직여요) */
    fun groupMoves(source: TimetableDragSource, date: String, start: Int, end: Int): List<TimetableGroupMember> {
        val src = source as? TimetableDragSource.task ?: return emptyList()
        val me = TimetableGroupMember(task = src.task, date = date, start = start, end = end)
        val s = src.start
        if (s == null || src.group.isEmpty()) return listOf(me)
        val delta = start - s
        return listOf(me) + src.group.map {
            TimetableGroupMember(task = it.task, date = date, start = it.start + delta, end = it.end + delta)
        }
    }

    /** 원래 자리와 같으면 저장하지 않아요 */
    fun sameAsSource(source: TimetableDragSource, target: TimetableDropTarget): Boolean = when (target) {
        // 같은 날 시간 미정 칸으로 다시 놓을 때만 그대로 (다른 날 시간 미정 칸이면 날짜를 옮겨요, 2026-10)
        is TimetableDropTarget.unscheduled -> source.start == null && source.date == target.date
        is TimetableDropTarget.grid -> target.date == source.date && target.start == source.start && target.end == source.end
    }

    /** 10분 단위로 맞추기 (소수 분 → 정수 분) */
    fun snap(minutes: Double): Int =
        (minutes / KST.snapMinutes.toDouble()).roundToInt() * KST.snapMinutes
}

// MARK: - 시트 · 확인 바 상태 (웹 Opened / Editing / ScopeAsk)

data class TimetableReplacedTask(
    val id: Int,
    val name: String,
) {
    companion object {}
}

/** 실제로 한 일 폼을 열 때의 초기값 */
data class TimetableActivityDraft(
    /** 있으면 수정, 없으면 새로 기록 */
    val activity: ActualActivity? = null,
    val date: String,
    /** 그날 0시 기준 분 (끝이 1440 이상이면 다음 날) */
    val start: Int,
    val end: Int,
    /** "이 시간에 다른 일을 했어요"로 열었을 때 원래 계획했던 Task */
    val replaced: TimetableReplacedTask? = null,
) {
    companion object {
        /** 수정할 기록 → 폼 초기값 (웹 draftOf) */
        fun of(a: ActualActivity): TimetableActivityDraft {
            val date = a.startAt.take(10)
            return TimetableActivityDraft(
                activity = a,
                date = date,
                start = KST.minutesFrom(date, a.startAt),
                end = KST.minutesFrom(date, a.endAt),
                replaced = a.replacedTaskId?.let { TimetableReplacedTask(id = it, name = a.replacedTaskName ?: "계획했던 일") },
            )
        }
    }
}

data class TimetableTimeValue(
    val date: String,
    /** 그 날짜 0시 기준 분. end 가 1440 이상이면 다음 날 */
    val start: Int,
    val end: Int,
) {
    companion object {}
}

sealed class TimetableTimeEditTarget {
    data class fixed(val block: FixedBlock) : TimetableTimeEditTarget() {
        companion object {}
    }

    /** 실제로 한 시간 입력 후 완료 */
    data class actual(val task: TimeBlockTask) : TimetableTimeEditTarget() {
        companion object {}
    }

    companion object {}
}

data class TimetableTimeEditing(
    val target: TimetableTimeEditTarget,
    val initial: TimetableTimeValue,
) {
    val id: String
        get() = when (val t = target) {
            is TimetableTimeEditTarget.fixed -> "f${t.block.key}"
            is TimetableTimeEditTarget.actual -> "a${t.task.taskId}"
        }

    companion object {}
}

/** 블록을 눌러 여는 시트 (웹 Popover · Modal → 아래에서 올라오는 시트 하나로) */
sealed class TimetableSheet {
    data class task(val task: TimeBlockTask) : TimetableSheet() {
        companion object {}
    }

    data class fixed(val block: FixedBlock) : TimetableSheet() {
        companion object {}
    }

    data class activity(val draft: TimetableActivityDraft) : TimetableSheet() {
        companion object {}
    }

    data class timeEdit(val editing: TimetableTimeEditing) : TimetableSheet() {
        companion object {}
    }

    val id: String
        get() = when (val s = this) {
            is task -> "task-${s.task.taskId}"
            is fixed -> "fixed-${s.block.key}"
            is activity -> {
                val a = s.draft.activity?.activityId?.toString() ?: "new"
                "activity-$a-${s.draft.date}-${s.draft.start}"
            }
            is timeEdit -> "edit-${s.editing.id}"
        }

    companion object {}
}

sealed class TimetableScopeAskKind {
    data class routine(val task: TimeBlockTask) : TimetableScopeAskKind() {
        companion object {}
    }

    /** edge: 수면이면 기상(아침 블록의 끝)을 바꿨는지 취침(밤 블록의 시작)을 바꿨는지 */
    data class fixed(val block: FixedBlock, val edge: TimetableLayout.SleepEdge?) : TimetableScopeAskKind() {
        companion object {}
    }

    companion object {}
}

/** 드래그 직후 "어디까지 바꿀까요?"를 묻는 중인 변경 (그동안 새 자리에 임시로 그려 둬요) */
data class TimetableScopeAsk(
    val kind: TimetableScopeAskKind,
    /** 끌어놓은 칸의 날짜 (start/end 는 이 날 0시 기준 분 — 수면은 음수 · 1440 이상일 수 있어요) */
    val date: String,
    val start: Int,
    val end: Int,
    val pendingId: String,
) {
    /** 확인 바 제목 (웹 ScopeChooser title) */
    val title: String
        get() = when (val k = kind) {
            is TimetableScopeAskKind.routine ->
                "'${k.task.taskName}' → ${KST.formatMinutes(start)}–${KST.formatMinutes(end)}"
            is TimetableScopeAskKind.fixed -> {
                val b = k.block
                when (k.edge) {
                    TimetableLayout.SleepEdge.wake ->
                        "☀️ 기상 ${KST.minutesToTime(KST.minutesFrom(date, b.end))} → ${KST.minutesToTime(end)}"
                    TimetableLayout.SleepEdge.bed ->
                        "🌙 취침 ${KST.minutesToTime(KST.minutesFrom(date, b.start))} → ${KST.minutesToTime(start)}"
                    null -> "${TimetableRules.title(b.emoji, b.title)} → ${KST.formatMinutes(start)}–${KST.formatMinutes(end)}"
                }
            }
        }

    val options: List<TimetableScopeOption>
        get() {
            val isToday = date == KST.today()
            return when (val k = kind) {
                is TimetableScopeAskKind.routine ->
                    TimetableScope.options(kind = TimetableScopeKind.routine, isToday = isToday, crossesMidnight = end >= 1440)
                is TimetableScopeAskKind.fixed -> {
                    val wd = KST.weekdayLabel[KST.weekdayIndex(date)]
                    val suffix = when (k.edge) {
                        TimetableLayout.SleepEdge.wake -> " 아침"
                        TimetableLayout.SleepEdge.bed -> " 밤"
                        null -> ""
                    }
                    TimetableScope.options(
                        kind = TimetableScopeKind.fixed, isToday = isToday, crossesMidnight = end >= 1440,
                        weekdayLabel = "${wd}요일$suffix",
                    )
                }
            }
        }

    companion object {}
}

/** 드래그 직후 서버 응답 전까지 새 자리에 그려 둘 블록 */
data class TimetablePendingEntry(
    val value: TimetableLayout.Pending,
    val id: String = UUID.randomUUID().toString(),
) {
    companion object {}
}

/** AI 배치 미리보기 대상 (웹 FillPreviewTarget) */
sealed class TimetableFillTarget {
    data class range(val start: String, val end: String) : TimetableFillTarget() {
        companion object {}
    }

    data class tasks(val ids: List<Int>) : TimetableFillTarget() {
        companion object {}
    }

    companion object {}
}

/** fill = "AI로 배치하기" · added = 방금 추가한 Task (확인 바 문구가 달라요) */
enum class TimetableProposalKind {
    fill, added;

    companion object {}
}

/** 22시 이월 제안 버튼 (어느 버튼이 돌고 있는지) */
enum class TimetableCarryAction {
    accept, decline, acceptAll, revert;

    companion object {}
}

/** 그날 0시 기준 분을 0~1439 로 (화면의 시계 값) */
fun timetableWrapMinutes(m: Int): Int = ((m % 1440) + 1440) % 1440
