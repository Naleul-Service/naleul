// model/TimetableModels.kt
//
// iOS Models/TimetableModels.swift 를 옮긴 파일.
// 웹 features/timetable/types.ts 를 그대로 옮긴 TimeTable 응답 모델 (백엔드 DTO 와 1:1).
//
// ⚠️ 날짜/시간 문자열은 모두 "한국 시간 벽시계" 값이에요. (Z, +09:00 없음)
//   LocalDate → "2026-11-18" · LocalDateTime → "2026-11-18T09:30:00" · LocalTime → "09:30:00"
//   Date 로 바꾸지 말고 문자열 그대로 KST.* 함수로 계산해요.
//
// 웹에서 `?` (optional) 인 필드는 전부 Kotlin nullable(+ 기본값 null) 이에요. 백엔드가 null 을 생략해서 내려주기 때문이에요.
//
// 서버 응답 모델은 대부분 val 이에요. 바꾼 값을 만들 땐 copy(...):
//   val done = t.copy(taskStatus = TaskStatus.COMPLETED)
// (Swift struct 는 대입하면 복사되지만 Kotlin 객체는 참조라, var 로 두면 목록 안의 원본까지 바뀌기 쉬워요)
// 예외: 화면에서 필드를 직접 바꾸는 TimeBlockTask · TimetableDay · ActivityInput 은 var (copy() 한 뒤에 바꾸세요)

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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// MARK: - enum (모르는 값이 와도 fallback 으로 — iOS FallbackDecodable)

@Serializable(with = LifePatternType.Serializer::class)
enum class LifePatternType {
    SLEEP, LUNCH, DINNER, CUSTOM;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<LifePatternType>("LifePatternType", LifePatternType.entries.toTypedArray(), LifePatternType.CUSTOM)

    companion object {
        val fallback: LifePatternType get() = CUSTOM
        fun fromRaw(raw: String?): LifePatternType? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable(with = TaskStatus.Serializer::class)
enum class TaskStatus {
    TODO, COMPLETED, SKIPPED, DELETED;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<TaskStatus>("TaskStatus", TaskStatus.entries.toTypedArray(), TaskStatus.TODO)

    companion object {
        val fallback: TaskStatus get() = TODO
        fun fromRaw(raw: String?): TaskStatus? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable(with = TaskPriority.Serializer::class)
enum class TaskPriority {
    A, B, C, D, E;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<TaskPriority>("TaskPriority", TaskPriority.entries.toTypedArray(), TaskPriority.C)

    /** 웹 features/timetable/ui/Priority.tsx PRIORITY_LABEL */
    val label: String
        get() = when (this) {
            A -> "가장 중요"
            B -> "중요"
            C -> "보통"
            D -> "가벼움"
            E -> "여유"
        }

    /** TimeBlock 에서 배지 + 테두리로 강조하는 우선순위 */
    val isTop: Boolean get() = this == A

    companion object {
        /** 백엔드 기본값도 C (루틴 · 목표 · 미션에서 자동으로 생긴 Task 포함, 2026-10) */
        val fallback: TaskPriority get() = C
        fun fromRaw(raw: String?): TaskPriority? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable(with = TaskKind.Serializer::class)
enum class TaskKind {
    NORMAL, APPOINTMENT;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<TaskKind>("TaskKind", TaskKind.entries.toTypedArray(), TaskKind.NORMAL)

    companion object {
        val fallback: TaskKind get() = NORMAL
        fun fromRaw(raw: String?): TaskKind? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable(with = TaskSourceType.Serializer::class)
enum class TaskSourceType {
    MANUAL, ROUTINE, MISSION;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<TaskSourceType>("TaskSourceType", TaskSourceType.entries.toTypedArray(), TaskSourceType.MANUAL)

    companion object {
        val fallback: TaskSourceType get() = MANUAL
        fun fromRaw(raw: String?): TaskSourceType? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable(with = PlacedBy.Serializer::class)
enum class PlacedBy {
    USER, ENGINE;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<PlacedBy>("PlacedBy", PlacedBy.entries.toTypedArray(), PlacedBy.USER)

    companion object {
        val fallback: PlacedBy get() = USER
        fun fromRaw(raw: String?): PlacedBy? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable(with = DailyPlanStatus.Serializer::class)
enum class DailyPlanStatus {
    GENERATING, GENERATED, REVIEWED, FAILED;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<DailyPlanStatus>("DailyPlanStatus", DailyPlanStatus.entries.toTypedArray(), DailyPlanStatus.GENERATED)

    companion object {
        val fallback: DailyPlanStatus get() = GENERATED
        fun fromRaw(raw: String?): DailyPlanStatus? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable(with = DailyPlanSource.Serializer::class)
enum class DailyPlanSource {
    NIGHTLY, BRAIN_DUMP, REPLAN, GOAL_CREATED, unknown;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<DailyPlanSource>("DailyPlanSource", DailyPlanSource.entries.toTypedArray(), DailyPlanSource.unknown)

    companion object {
        val fallback: DailyPlanSource get() = unknown
        fun fromRaw(raw: String?): DailyPlanSource? = entries.firstOrNull { it.rawValue == raw }
    }
}

// MARK: - Task 블록

/**
 * TimeTable 의 Task 블록 하나 (목표 상세의 Task 목록에서도 같은 모양을 써요).
 * iOS 커스텀 init(from:) 처럼, 백엔드가 일부 필드를 생략해도 죽지 않게 기본값을 채워요 (taskId 만 필수).
 *
 * 화면에서 `t.taskStatus = TaskStatus.COMPLETED` 처럼 필드를 직접 바꾸는 곳(홈 낙관적 표시 · 미리보기 샘플)이 있어서 var 예요.
 * ⚠️ Swift struct 는 대입하면 복사되지만 Kotlin 객체는 참조예요. 목록/상태 안의 값을 바꿀 땐 꼭 복사본에서:
 *   val t = task.copy(); t.taskStatus = TaskStatus.COMPLETED     // 또는 task.copy(taskStatus = TaskStatus.COMPLETED)
 */
@Serializable
data class TimeBlockTask(
    var taskId: Int,
    var taskName: String = "",
    var emoji: String? = null,
    var taskStatus: TaskStatus = TaskStatus.TODO,
    var taskPriority: TaskPriority? = null,
    var taskKind: TaskKind? = null,
    var sourceType: TaskSourceType = TaskSourceType.MANUAL,
    var source: String? = null,
    var goalCategoryId: Int? = null,
    var goalCategoryName: String? = null,
    var goalEmoji: String? = null,
    var goalColorCode: String? = null,
    var goalTemporary: Boolean = false,
    var generalCategoryId: Int? = null,
    var generalCategoryName: String? = null,
    var milestoneId: Int? = null,
    var milestoneTitle: String? = null,
    /** 업무형 목표의 세부 목표 (로드맵 · WorkTopic) — 없으면 "세부 목표 없음" */
    var workTopicId: Int? = null,
    var workTopicName: String? = null,
    var routineId: Int? = null,
    var date: String? = null,
    var plannedStartAt: String? = null,
    var plannedEndAt: String? = null,
    var plannedDurationMinutes: Int? = null,
    var scheduledDate: String? = null,
    var dueDate: String? = null,
    var locked: Boolean = false,
    var placedBy: PlacedBy? = null,
    var placementReason: String? = null,
    var placementReasonCodes: List<String>? = null,
    var carryOverCount: Int = 0,
    var completedAt: String? = null,
    /** 완료한 Task 의 실제 수행 시각 (완료 전이거나 실제 시각 기록이 없으면 null) */
    var actualStartAt: String? = null,
    var actualEndAt: String? = null,
    var missed: Boolean = false,
    /** 22시 이월로 놓였지만 아직 확인 안 함 → 점선 블록 + "옮길까요?" */
    var carryPending: Boolean? = null,
    /** 이월되기 전 날짜 */
    var carriedFromDate: String? = null,
    /** (화면 전용) "AI로 배치하기" 미리보기 제안 — 아직 저장 안 된 점선 블록 */
    var proposed: Boolean? = null,
    /** 여러 번 나눠서 했을 때만 각 구간 (예: 13~15시, 16~18시). 한 번에 했으면 null/빈 목록 */
    var actualSegments: List<ActualSegment>? = null,
    /**
     * 자동 배치가 연속된 빈 시간이 없어 나눠 넣었을 때만 계획 구간 (예: 09~12시, 13~14시 — 점심 앞뒤).
     * plannedStartAt ~ plannedEndAt 은 첫 시작 ~ 마지막 끝, plannedDurationMinutes 는 구간 합계예요
     */
    var plannedSegments: List<ActualSegment>? = null,
) {
    val id: Int get() = taskId

    val isCompleted: Boolean get() = taskStatus == TaskStatus.COMPLETED

    companion object {}
}

/** 실제로 한 구간 하나 (나눠서 한 경우) */
@Serializable
data class ActualSegment(
    val start: String,
    val end: String,
) {
    companion object {}
}

/** 시간대를 정한 기본 생활 습관 (예: 샤워 20:00~20:30) — 캘린더에 체크할 수 있는 블록 */
@Serializable
data class HabitBlock(
    val habitId: Int,
    val name: String,
    val emoji: String? = null,
    val start: String,
    val end: String,
    val checked: Boolean,
) {
    companion object {}
}

@Serializable
data class Covered(
    val taskId: Int,
    val start: String,
    val end: String,
) {
    companion object {}
}

/**
 * 고정 시간 블록 (수면 · 점심 · 저녁 · 직접 만든 생활 패턴).
 * iOS 커스텀 init(from:) 처럼 빠진 필드는 기본값으로 (lifePatternId · targetDate · start · end 는 필수).
 */
@Serializable
data class FixedBlock(
    val lifePatternId: Int,
    val patternType: LifePatternType = LifePatternType.CUSTOM,
    val title: String = "",
    val emoji: String? = null,
    /** 그날만 변경 API 에 넘길 날짜 (자정을 넘긴 수면의 다음 날 부분이어도 전날) */
    val targetDate: String,
    val start: String,
    val end: String,
    val overridden: Boolean = false,
    val clippedFromPreviousDay: Boolean = false,
    val coveredBy: List<Covered>? = null,
) {
    /** 화면에서 블록을 구별하는 키 (웹 fixedKey 와 같은 규칙: 패턴 id + 그날 + 시작) */
    val key: String get() = "$lifePatternId-$targetDate-$start"

    companion object {}
}

@Serializable
data class DayPlan(
    val status: DailyPlanStatus,
    val source: DailyPlanSource,
    val summary: String? = null,
    val needsReview: Boolean,
    val generatedAt: String? = null,
) {
    companion object {}
}

@Serializable
data class DayStats(
    val total: Int,
    val completed: Int,
    val missed: Int,
    /** 완료/전체 × 100. Task 가 없으면 null */
    val rate: Int? = null,
    /** 이날 못 해서 다음 날로 넘어간 Task 수 */
    val carriedOver: Int? = null,
) {
    companion object {}
}

@Serializable
data class DayRange(
    val start: String,
    val end: String,
) {
    companion object {}
}

@Serializable
data class SleepEdges(
    /** 아침에 끝나는 수면 (끝 = 기상) */
    val wake: FixedBlock? = null,
    /** 밤에 시작하는 수면 (시작 = 취침) */
    val bed: FixedBlock? = null,
) {
    companion object {}
}

/**
 * 하루치 TimeTable.
 * iOS 커스텀 init(from:) 처럼 빠진 필드는 기본값으로 채워요 (date 만 필수).
 * dayRange 가 없으면 그날 0시 ~ 다음 날 0시 (기본값 식에서 앞의 date 를 쓸 수 있어요).
 * 홈 화면이 tasks 를 고쳐 끼우는 곳이 있어서 var 예요 (바꿀 땐 copy() 한 복사본에서 — TimeBlockTask 주석 참고).
 */
@Serializable
data class TimetableDay(
    var date: String,
    var dayRange: DayRange = DayRange(start = "${date}T00:00:00", end = "${KST.addDays(date, 1)}T00:00:00"),
    var fixedBlocks: List<FixedBlock> = emptyList(),
    var tasks: List<TimeBlockTask> = emptyList(),
    var unscheduledTasks: List<TimeBlockTask> = emptyList(),
    var plan: DayPlan? = null,
    var stats: DayStats = DayStats(total = 0, completed = 0, missed = 0, rate = null, carriedOver = null),
    /** 하루 범위를 정한 수면 블록 (자르지 않은 원본 구간). 수면 패턴이 없으면 null */
    var sleep: SleepEdges? = null,
    /** 시간대를 정한 기본 생활 습관 */
    var habits: List<HabitBlock>? = null,
    /** 이날 못 해서 다른 날로 옮긴 Task 의 흔적 (2026-10) — 원래 자리에 흐리게 "* 이름 → 옮긴 날" */
    var carriedOut: List<CarriedOutBlock> = emptyList(),
) {
    val id: String get() = date

    companion object {}
}

/** 못 한 Task 를 다른 날로 옮긴 흔적 (원래 시각 · 옮긴 날). 끌 수 없고 누르면 옮긴 날로 가요 */
@Serializable
data class CarriedOutBlock(
    val taskId: Int,
    val taskName: String,
    val emoji: String? = null,
    val goalColorCode: String? = null,
    /** 원래 시작 · 끝 (LocalDateTime) — 시간 미정이었으면 null */
    val start: String? = null,
    val end: String? = null,
    /** 옮긴 날 YYYY-MM-DD */
    val toDate: String? = null,
) {
    val id: String get() = "carried-$taskId-${start ?: ""}"

    companion object {}
}

@Serializable
data class TimetableResponse(
    val days: List<TimetableDay>,
) {
    companion object {}
}

@Serializable
data class MovedBlock(
    val taskId: Int,
    val fromStart: String,
    val toStart: String,
    val toEnd: String,
    val reason: String? = null,
) {
    companion object {}
}

@Serializable
data class CoveredFixedBlock(
    val lifePatternId: Int,
    val title: String,
) {
    companion object {}
}

@Serializable
data class TaskScheduleResponse(
    val task: TimeBlockTask? = null,
    val moved: List<MovedBlock>? = null,
    val unscheduled: List<Int>? = null,
    val coveredFixedBlocks: List<CoveredFixedBlock>? = null,
) {
    companion object {}
}

/** 여러 Task 를 한 번에 옮긴 결과 (묶음 드래그) */
@Serializable
data class TaskBatchScheduleResponse(
    val tasks: List<TimeBlockTask>,
    val moved: List<MovedBlock>? = null,
    val unscheduled: List<Int>? = null,
    val coveredFixedBlocks: List<CoveredFixedBlock>? = null,
) {
    companion object {}
}

/** 고정 시간 요일별 시간. startDayOffset: 블록이 그 요일 하루 앞(-1)·뒤(+1)에 시작 */
@Serializable
data class LifePatternDayTime(
    val day: JavaDayOfWeek,
    val startTime: String,
    val endTime: String,
    val startDayOffset: Int,
) {
    companion object {}
}

/** 기본 생활 패턴 (설정 > 생활 패턴) */
@Serializable
data class LifePattern(
    val lifePatternId: Int,
    val patternType: LifePatternType,
    val title: String,
    val emoji: String? = null,
    val startTime: String,
    val endTime: String,
    val days: List<JavaDayOfWeek>,
    val everyDay: Boolean,
    val crossesMidnight: Boolean,
    val sortOrder: Int,
    /** 요일별로 다른 시간 (기본 시간과 다른 요일만) */
    val dayTimes: List<LifePatternDayTime>? = null,
) {
    val id: Int get() = lifePatternId

    companion object {}
}

// MARK: - 월간 (GET /timetable/monthly)

@Serializable
data class MonthlyChip(
    val taskId: Int,
    val title: String,
    val colorCode: String? = null,
    val completed: Boolean,
    val temporaryGoal: Boolean,
) {
    companion object {}
}

@Serializable
data class MonthlyMilestoneChip(
    val milestoneId: Int,
    val title: String,
    val colorCode: String? = null,
    val goalCategoryId: Int,
) {
    companion object {}
}

@Serializable
data class MonthlyDay(
    val date: String,
    /** 실행률 (Task 없으면 null) */
    val rate: Int? = null,
    val taskCount: Int,
    val completedCount: Int,
    /** 루틴이 아닌 Task 최대 3개 */
    val chips: List<MonthlyChip>,
    val moreCount: Int,
    val milestones: List<MonthlyMilestoneChip>,
) {
    val id: String get() = date

    companion object {}
}

@Serializable
data class MonthlyDeadline(
    /** "TASK" | "MILESTONE" */
    val type: String,
    val id: Int,
    val title: String,
    val date: String,
    val colorCode: String? = null,
) {
    companion object {}
}

/** GET /timetable/goal-time — 기간 동안 완료한 Task 의 실천 시간, 목표별 (많이 쓴 순 · 2026-10) */
@Serializable
data class GoalTimeResponse(
    val startDate: String,
    val endDate: String,
    val totalMinutes: Int,
    val goals: List<GoalTimeEntry>,
)

/** 목표 없이 완료한 Task 는 goalCategoryId == null 한 줄 */
@Serializable
data class GoalTimeEntry(
    val goalCategoryId: Int? = null,
    val goalCategoryName: String? = null,
    val goalEmoji: String? = null,
    val goalColorCode: String? = null,
    val minutes: Int,
    val taskCount: Int,
)

@Serializable
data class MonthlyTimetableResponse(
    val year: Int,
    val month: Int,
    val days: List<MonthlyDay>,
    /** 이번 주(월~일) 마감인 미완료 Task + 마일스톤 */
    val deadlinesThisWeek: List<MonthlyDeadline>,
) {
    companion object {}
}

// MARK: - 하루 계획 (GET /daily-plans/{date})

@Serializable
data class DailyPlanResponse(
    val date: String,
    val exists: Boolean,
    val status: DailyPlanStatus? = null,
    val source: DailyPlanSource? = null,
    val summary: String? = null,
    val needsReview: Boolean,
    val placedCount: Int,
    val carriedCount: Int,
    val unplacedCount: Int,
    val generatedAt: String? = null,
    val reviewedAt: String? = null,
) {
    companion object {}
}

@Serializable
data class PlacedItem(
    val taskId: Int,
    val date: String,
    val start: String,
    val end: String,
    val reason: String? = null,
) {
    companion object {}
}

/** POST /daily-plans/{date}/replan */
@Serializable
data class ReplanResponse(
    val plan: DailyPlanResponse? = null,
    val placed: List<PlacedItem>,
    val movedToOtherDays: List<PlacedItem>,
    val unscheduled: List<Int>,
) {
    companion object {}
}

// MARK: - 실제로 한 일 (GET /activities)

/** 계획(Task)과 상관없이 그 시간에 실제로 한 활동. 시각은 한국 시간 "YYYY-MM-DDTHH:mm:ss" */
@Serializable
data class ActualActivity(
    val activityId: Int,
    val title: String,
    val emoji: String? = null,
    val startAt: String,
    val endAt: String,
    val durationMinutes: Int,
    val goalCategoryId: Int? = null,
    val goalCategoryName: String? = null,
    val goalColorCode: String? = null,
    /** 원래 이 시간에 계획했던 Task (선택) */
    val replacedTaskId: Int? = null,
    val replacedTaskName: String? = null,
    val memo: String? = null,
) {
    val id: Int get() = activityId

    companion object {}
}

/**
 * 실제 기록 입력값 (폼 상태 — iOS 처럼 필드를 직접 바꿀 수 있게 var).
 * ⚠️ 상태에 들어 있는 값을 바꿀 땐 copy() 로 복사본을 만들어 바꾸세요.
 */
data class ActivityInput(
    var title: String,
    var emoji: String? = null,
    var startAt: String,
    var endAt: String,
    var goalCategoryId: Int? = null,
    var replacedTaskId: Int? = null,
    var memo: String? = null,
) {
    /** 웹은 { emoji: null } 처럼 null 을 그대로 보내요 (지우기) */
    val json: JsonObject
        get() = com.naleul.naleul.core.network.json(
            "title" to title,
            "emoji" to emoji,
            "startAt" to startAt,
            "endAt" to endAt,
            "goalCategoryId" to goalCategoryId,
            "replacedTaskId" to replacedTaskId,
            "memo" to memo,
        )

    companion object {}
}

// MARK: - AI 로 배치하기 (미리보기 → 확정)

@Serializable
data class FillProposal(
    val taskId: Int,
    val date: String,
    val start: String,
    val end: String,
    val reason: String? = null,
) {
    companion object {}
}

@Serializable
data class FillPreviewResponse(
    val proposals: List<FillProposal>,
    /** 빈 시간이 없어 제안하지 못한 Task */
    val unscheduled: List<Int>,
) {
    companion object {}
}

@Serializable
data class FillApplyResponse(
    val placed: Int,
    val skipped: List<Int>,
) {
    companion object {}
}

@Serializable
data class FillDayResult(
    val date: String,
    val placed: Int,
    val unscheduled: Int,
) {
    companion object {}
}

/** POST /daily-plans/fill (바로 저장) */
@Serializable
data class FillResponse(
    val days: List<FillDayResult>,
    val placedCount: Int,
    val unscheduledCount: Int,
) {
    companion object {}
}

/** 범위 골라 바꾸기: DAY 이날만 · WEEK 이번 주 · FOLLOWING 앞으로 계속 · WEEKDAY 앞으로 이 요일마다 (고정 시간 전용) */
@Serializable
enum class ChangeScope {
    DAY, WEEK, FOLLOWING, WEEKDAY;

    val rawValue: String get() = name

    companion object {
        fun fromRaw(raw: String?): ChangeScope? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable
data class RoutineScheduleResponse(
    val scope: ChangeScope,
    val result: TaskScheduleResponse? = null,
    /** 같이 옮긴 다른 날짜 */
    val changedDates: List<String>,
    /** 잠긴 일정과 겹쳐 건너뛴 날짜 (그날은 원래 시간 그대로) */
    val skippedDates: List<String>,
) {
    companion object {}
}
