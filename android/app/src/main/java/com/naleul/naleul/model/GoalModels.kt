// model/GoalModels.kt
//
// iOS Models/GoalModels.swift 를 옮긴 목표 모델.
// 웹 features/goal/api.ts 의 타입 · goal/format.ts · goal/kind.ts 를 옮긴 것이에요.
// 백엔드 GoalCategoryResponse (목표 = goal_category).
// AI 목표 생성 필드(emoji, aiNote, metric*, milestones 등)는 사용자가 직접 만든 목표에선 null 이에요.
//
// Swift → Kotlin 메모
//  - Swift Optional(String?) → Kotlin nullable + 기본값 null. 서버가 필드를 빼고 보내도 죽지 않아요.
//  - Swift 의 커스텀 init(from:) (빠진 필드는 기본값으로) → Kotlin 은 "기본값이 있는 파라미터"로 같은 효과를 내요.
//    NJson 설정(coerceInputValues)이 null 이 온 경우에도 기본값을 넣어 줘요.
//  - 서버 응답 모델은 모두 val (바꿀 땐 copy(...)). Kotlin 객체는 Swift struct 와 달리 "복사"가 아니라
//    "같은 객체를 가리키는 것"이라, var 로 두면 목록 안의 원본까지 바뀌는 실수가 생기기 쉬워요.
//  - FallbackDecodable enum → FallbackEnumSerializer (모르는 값이 와도 fallback 으로).

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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil

// MARK: - 목표 상태

@Serializable(with = GoalStatus.Serializer::class)
enum class GoalStatus {
    NOT_STARTED, IN_PROGRESS, COMPLETED, DELETED;

    val rawValue: String get() = name

    /** 웹 STATUS_LABEL */
    val label: String
        get() = when (this) {
            NOT_STARTED -> "시작 전"
            IN_PROGRESS -> "진행 중"
            COMPLETED -> "완료"
            DELETED -> "삭제됨"
        }

    /** 아직 끝나지 않은 목표 (진행 중 목록) */
    val isOngoing: Boolean get() = this == IN_PROGRESS || this == NOT_STARTED

    object Serializer : FallbackEnumSerializer<GoalStatus>("GoalStatus", GoalStatus.entries.toTypedArray(), GoalStatus.IN_PROGRESS)

    companion object {
        val fallback: GoalStatus get() = IN_PROGRESS
        fun fromRaw(raw: String?): GoalStatus? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable
data class RoutineDayTime(
    val dayOfWeek: JavaDayOfWeek,
    /** "HH:mm" 또는 "HH:mm:ss" */
    val startTime: String,
    val endTime: String,
) {
    companion object {}
}

/**
 * 루틴 요약. iOS 는 커스텀 init(from:) 으로 빠진 필드를 기본값으로 채웠어요 → 여기선 파라미터 기본값으로 같은 효과.
 * (routineId 만 꼭 있어야 해요)
 */
@Serializable
data class RoutineSummary(
    val routineId: Int,
    val routineName: String = "",
    val routineStatus: String = "",
    val repeatStartDate: String? = null,
    val repeatEndDate: String? = null,
    /** "19:30:00" (LocalTime) */
    val repeatStartTime: String? = null,
    val repeatEndTime: String? = null,
    val repeatDays: List<JavaDayOfWeek> = emptyList(),
    val notificationEnabled: Boolean = false,
    /** 하는 방법 (AI 루틴: "스쿼트 4x10, 런지 3x12…") */
    val description: String? = null,
    val durationMinutes: Int? = null,
    /** 기본 시간과 다른 요일만. 비어 있으면 모든 요일이 repeatStartTime~repeatEndTime */
    val dayTimes: List<RoutineDayTime>? = null,
) {
    val id: Int get() = routineId

    companion object {}
}

/** 세부 목표 (= 영역, general_category) */
@Serializable
data class SubGoalInfo(
    val generalCategoryId: Int,
    val generalCategoryName: String = "",
    val colorCode: String? = null,
    val generalCategoryStartDate: String? = null,
    val generalCategoryEndDate: String? = null,
    val generalCategoryStatus: GoalStatus = GoalStatus.IN_PROGRESS,
    val routines: List<RoutineSummary> = emptyList(),
    /** 세부 목표의 실천 원칙 (AI: "하루 1,600kcal, 단백질 90g…") */
    val description: String? = null,
    val emoji: String? = null,
    /** 목표의 "기타 할 일" 그릇 — 영역을 안 고른 루틴·Task 가 들어가요. 화면에선 영역으로 보여주지 않아요 */
    val defaultBucket: Boolean? = null,
) {
    val id: Int get() = generalCategoryId

    companion object {}
}

@Serializable(with = MilestoneStatus.Serializer::class)
enum class MilestoneStatus {
    PENDING, ACHIEVED, MISSED;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<MilestoneStatus>("MilestoneStatus", MilestoneStatus.entries.toTypedArray(), MilestoneStatus.PENDING)

    companion object {
        val fallback: MilestoneStatus get() = PENDING
        fun fromRaw(raw: String?): MilestoneStatus? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable
data class MilestoneInfo(
    val milestoneId: Int,
    val title: String,
    val description: String? = null,
    val dueDate: String,
    val targetValue: Double? = null,
    /** 사용자가 이 시점 수치를 직접 정했는지 (false = 시작값 → 목표값 직선에서 자동 계산) */
    val targetValueManual: Boolean? = null,
    val status: MilestoneStatus,
) {
    val id: Int get() = milestoneId

    companion object {}
}

/** 목표 형태: ACHIEVEMENT 목표형(기본) / RECORD 업무형 / HABIT 기본 생활 습관 (V52, 사용자당 1개) */
@Serializable(with = GoalMode.Serializer::class)
enum class GoalMode {
    ACHIEVEMENT, RECORD, HABIT;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<GoalMode>("GoalMode", GoalMode.entries.toTypedArray(), GoalMode.ACHIEVEMENT)

    companion object {
        val fallback: GoalMode get() = ACHIEVEMENT
        fun fromRaw(raw: String?): GoalMode? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable(with = PlanningStyle.Serializer::class)
enum class PlanningStyle {
    PLANNER, SPONTANEOUS;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<PlanningStyle>("PlanningStyle", PlanningStyle.entries.toTypedArray(), PlanningStyle.PLANNER)

    companion object {
        val fallback: PlanningStyle get() = PLANNER
        fun fromRaw(raw: String?): PlanningStyle? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** 목표 하나 (iOS 커스텀 init(from:) 의 기본값을 파라미터 기본값으로 옮겼어요. goalCategoryId 만 필수) */
@Serializable
data class GoalCategory(
    val goalCategoryId: Int,
    val goalCategoryName: String = "",
    val goalCategoryStatus: GoalStatus = GoalStatus.IN_PROGRESS,
    val goalCategoryStartDate: String? = null,
    val goalCategoryEndDate: String? = null,
    val achievement: String? = null,
    val motive: String? = null,
    val colorCode: String? = null,
    val generalCategories: List<SubGoalInfo> = emptyList(),
    // ── AI 목표 생성 필드 ──
    val emoji: String? = null,
    val goalTopic: String? = null,
    val description: String? = null,
    val aiGenerated: Boolean? = null,
    /** Brain dump 에서 자동으로 만든 임시 목표 (종료일·마일스톤 없음) → "AI로 구체화하기"를 권해요 */
    val temporary: Boolean? = null,
    val goalMode: GoalMode? = null,
    /** 목표 카테고리 2단계. 예전 목표는 null */
    val goalType: GoalType? = null,
    val goalSubType: GoalSubType? = null,
    val goalKindLabel: String? = null,
    /** "건강 · 다이어트" */
    val goalKindName: String? = null,
    val planningStyle: PlanningStyle? = null,
    val aiNote: String? = null,
    val metricName: String? = null,
    val metricUnit: String? = null,
    val startValue: Double? = null,
    val currentValue: Double? = null,
    val targetValue: Double? = null,
    /** 수치 목표 마감일 (V58). null 이면 목표 종료일을 따라요 */
    val metricTargetDate: String? = null,
    val milestones: List<MilestoneInfo>? = null,
    /** 목록 조회에서만 — 마지막 활동일 (Task 완료 · 수치 기록) */
    val lastActivityDate: String? = null,
    /** 목록 조회에서만 — 진행 중인데 오래 활동이 없으면 멈춘 지 며칠째, 아니면 null */
    val pausedDays: Int? = null,
) {
    val id: Int get() = goalCategoryId

    /** 기록형(업무형) 목표인지 (예전 응답엔 goalMode 가 없어서 null = 달성형) */
    val isRecordGoal: Boolean get() = goalMode == GoalMode.RECORD

    /** 기본 생활 습관 그릇 (V52) — 목표 상세 대신 습관 화면을 보여줘요 */
    val isHabitGoal: Boolean get() = goalMode == GoalMode.HABIT
    /** 업무형 목표인지 — 업무형은 기록형(RECORD)으로 만들어져요. 세부 목표 = 로드맵의 세부 목표(WorkTopic) (웹 isWorkGoal) */
    val isWorkGoal: Boolean get() = goalMode == GoalMode.RECORD || goalType == GoalType.WORK

    val isTemporary: Boolean get() = temporary ?: false

    /** 화면에 보여줄 세부 목표 (defaultBucket "기타 할 일" 그릇은 빼요) — 웹 areasOf */
    val areas: List<SubGoalInfo> get() = generalCategories.filter { !(it.defaultBucket ?: false) }

    companion object {}
}

/** GET /goal-categories/{id}/tasks — 목표에 속한 Task 전체 */
@Serializable
data class GoalTasksResponse(
    val totalCount: Int,
    val todoCount: Int,
    val completedCount: Int,
    val tasks: List<TimeBlockTask>,
) {
    companion object {}
}

// MARK: - 루틴 실천 히트맵 (GET /routines/{goalId}/heatmap)

@Serializable
data class RoutineHeatmap(
    val routineId: Int,
    val routineName: String,
    val repeatStartDate: String,
    val repeatEndDate: String? = null,
    val repeatDays: List<JavaDayOfWeek>,
    /** 루틴 Task 를 완료한 날짜 "YYYY-MM-DD" */
    val completedDates: List<String>,
    val currentStreak: Int,
    val longestStreak: Int,
    /** 주로 몇 시부터 몇 시에 했는지 (2026-10). 시각을 알 수 있는 기록이 없으면 null */
    val timeStats: RoutineTimeStats? = null,
) {
    val id: Int get() = routineId

    companion object {}
}

/** 한 요일에서 가장 많이 한 시간대 */
@Serializable
data class RoutineDayTimeStat(
    /** "MONDAY" ~ "SUNDAY" */
    val day: String = "",
    val start: String = "",
    val end: String = "",
    val count: Int = 0,
    val total: Int = 0,
) {
    val dayLabel: String?
        get() = mapOf(
            "MONDAY" to "월", "TUESDAY" to "화", "WEDNESDAY" to "수", "THURSDAY" to "목",
            "FRIDAY" to "금", "SATURDAY" to "토", "SUNDAY" to "일",
        )[day]
}

/** 루틴을 주로 실천한 시간대 — bestStart~bestEnd 에 practiceCount 회 중 inWindowCount 회 */
@Serializable
data class RoutineTimeStats(
    val bestStart: String? = null,
    val bestEnd: String? = null,
    val practiceCount: Int = 0,
    val inWindowCount: Int = 0,
    /** 0~23시 각 시간에 걸친 실천 횟수 */
    val hourly: List<Int> = emptyList(),
    /** 요일별로 가장 많이 한 시간대 (많이 한 요일 순, 최대 3개 · 2026-10) */
    val byDay: List<RoutineDayTimeStat> = emptyList(),
) {
    val hasData: Boolean get() = practiceCount > 0 && bestStart != null && bestEnd != null

    /** "화 19:00~19:30 · 목 17:10~17:40에 가장 많이 실천했어요" — 요일 정보가 없으면 "주로 …에 실천했어요", 기록이 없으면 null */
    val practiceSentence: String?
        get() {
            val days = byDay.mapNotNull { d -> d.dayLabel?.let { "$it ${d.start}~${d.end}" } }
            if (days.isNotEmpty()) return days.joinToString(" · ") + "에 가장 많이 실천했어요"
            if (hasData) return "주로 ${bestStart}~${bestEnd}에 실천했어요"
            return null
        }

    companion object {}
}

// MARK: - 진행 도표 ("지금 어디쯤?")

@Serializable(with = ProgressStatus.Serializer::class)
enum class ProgressStatus {
    NOT_STARTED, AHEAD, ON_TRACK, BEHIND, ACHIEVED, NO_PLAN;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<ProgressStatus>("ProgressStatus", ProgressStatus.entries.toTypedArray(), ProgressStatus.NO_PLAN)

    companion object {
        val fallback: ProgressStatus get() = NO_PLAN
        fun fromRaw(raw: String?): ProgressStatus? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable
data class ProgressPoint(
    val date: String,
    val value: Double,
    /** "시작" · 마일스톤 제목 · "목표" */
    val label: String? = null,
) {
    companion object {}
}

@Serializable
data class MetricLog(
    val date: String,
    val value: Double,
    val memo: String? = null,
) {
    companion object {}
}

/** 수치 목표(체중 72 → 69kg 등)의 계획선 · 기록 · 오늘 위치 */
@Serializable
data class MetricProgress(
    val name: String? = null,
    val unit: String? = null,
    val startValue: Double,
    val targetValue: Double,
    val currentValue: Double,
    /** 계획대로라면 오늘 값 (종료일 없으면 null) */
    val expectedValue: Double? = null,
    /** (현재-시작)/(목표-시작)×100 — 음수·100 초과 가능 */
    val progressPercent: Double? = null,
    val status: ProgressStatus,
    /** 최근 기록 추세대로면 목표 도달 예상일 */
    val projectedDate: String? = null,
    val lastLoggedDate: String? = null,
    val loggedToday: Boolean,
    val planLine: List<ProgressPoint>,
    val logs: List<MetricLog>,
    /** 수치 목표 마감일 (V58 · 따로 정했으면 그 날, 아니면 목표 종료일) */
    val targetDate: String? = null,
) {
    companion object {}
}

@Serializable
data class TaskProgressPoint(
    val date: String,
    /** 그날까지 하기로 한 Task 누적 */
    val planned: Int,
    /** 그날까지 완료한 Task 누적 (오늘 이후는 null) */
    val completed: Int? = null,
) {
    companion object {}
}

@Serializable
data class TaskProgress(
    val total: Int,
    val completed: Int,
    /** 오늘까지 하기로 한 Task (이미 끝낸 Task 포함) */
    val dueByToday: Int,
    val completionPercent: Double? = null,
    val series: List<TaskProgressPoint>,
) {
    companion object {}
}

/** 목표 결과: 달성 · 일부 달성 · 못 함 */
@Serializable(with = OutcomeResult.Serializer::class)
enum class OutcomeResult {
    ACHIEVED, PARTIAL, NOT_ACHIEVED;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<OutcomeResult>("OutcomeResult", OutcomeResult.entries.toTypedArray(), OutcomeResult.PARTIAL)

    companion object {
        val fallback: OutcomeResult get() = PARTIAL
        fun fromRaw(raw: String?): OutcomeResult? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable
data class GoalOutcome(
    val result: OutcomeResult,
    /** "USER" 직접 고름 · "AUTO" 기간이 끝나 자동 판정 */
    val source: String,
    val metricPercent: Double? = null,
    val routineRate: Double? = null,
    val taskRate: Double? = null,
    /** 수치 목표인데 수치 기록이 없어 실천률로 판정했어요 */
    val metricUnrecorded: Boolean? = null,
    /** 서버 자동 판정 (직접 골라도 남아요 — 판정 일치율 비교용) */
    val autoResult: OutcomeResult? = null,
    /** METRIC · ROUTINE_RATE · TASK_RATE · NO_RECORD */
    val autoReason: String? = null,
    /** 예) "마지막 기록이 목표 수치의 86%예요. 80% 이상이라 자동으로 달성 처리했어요." */
    val autoMessage: String? = null,
) {
    companion object {}
}

@Serializable
data class GoalAdjustment(
    val key: String,
    /** "DOWN" 하루 줄이기 · "UP" 하루 늘리기 */
    val direction: String,
    val routineId: Int,
    val routineName: String,
    val fromDays: Int,
    val toDays: Int,
    val day: JavaDayOfWeek,
    val previousWeekRate: Double,
    val lastWeekRate: Double,
    val message: String,
) {
    companion object {}
}

/** 그날 목표를 돌아본 느낌 */
@Serializable(with = ReflectionMood.Serializer::class)
enum class ReflectionMood {
    GOOD, OK, BAD;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<ReflectionMood>("ReflectionMood", ReflectionMood.entries.toTypedArray(), ReflectionMood.OK)

    companion object {
        val fallback: ReflectionMood get() = OK
        fun fromRaw(raw: String?): ReflectionMood? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** 하루 회고: 무엇을 했는지 · 어땠는지 */
@Serializable
data class GoalReflection(
    val date: String,
    val mood: ReflectionMood? = null,
    val note: String? = null,
) {
    companion object {}
}

@Serializable
data class GoalProgress(
    val goalCategoryId: Int,
    val today: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val elapsedDays: Int,
    val totalDays: Int? = null,
    val metric: MetricProgress? = null,
    val tasks: TaskProgress,
    /** 최근 하루 회고 (최신순) */
    val reflections: List<GoalReflection>? = null,
    /** 끝난 목표의 결과 (진행 중이면 null) */
    val outcome: GoalOutcome? = null,
    /** 주간 점검 제안 — 2주 연속 실천률이 낮거나 높을 때만 내려와요 */
    val adjustment: GoalAdjustment? = null,
    /** 추가 그래프 (V52) — 대표 수치 말고 같이 볼 수치 (기출 회독 수 · 모의고사 점수 …) */
    val extraMetrics: List<ExtraMetric>? = null,
) {
    companion object {}
}

/** 목표형의 추가 그래프 하나 (GET /goal-categories/{id}/progress 의 extraMetrics) */
@Serializable
data class ExtraMetric(
    val metricId: Int,
    val sortOrder: Int,
    val progress: MetricProgress,
) {
    val id: Int get() = metricId

    companion object {}
}

// MARK: - goal/format.ts

object GoalFormat {
    /** "19:30:00" → "19:30" */
    fun hhmm(t: String?): String? {
        if (t == null) return null
        return t.take(5)
    }

    /** 목표 색 (없으면 연한 회색 #B8BCC4) */
    fun goalColorHex(code: String?): String {
        if (code.isNullOrEmpty()) return "#B8BCC4"
        return if (code.startsWith("#")) code else "#$code"
    }

    /** "2026-11-18" → "2026.11.18" */
    fun formatDot(v: String?): String = (v ?: "").replace("-", ".")

    /** 기간 진행 정보 (iOS GoalFormat.Period) */
    data class Period(
        /** 전체 일수 */
        val total: Int,
        /** 지난 일수 (0~total) */
        val elapsed: Int,
        /** 남은 일수 (음수면 종료) */
        val remain: Int,
        /** 0~1 */
        val ratio: Double,
        val weeks: Int,
    ) {
        companion object {}
    }

    /** 기간 진행 정보 (오늘 기준) */
    fun periodProgress(start: String?, end: String?, today: String = KST.today()): Period? {
        if (start == null || end == null) return null
        val total = maxOf(KST.diffDays(start, end), 1)
        val elapsed = minOf(maxOf(KST.diffDays(start, today), 0), total)
        val remain = KST.diffDays(today, end)
        return Period(
            total = total,
            elapsed = elapsed,
            remain = remain,
            ratio = elapsed.toDouble() / total.toDouble(),
            weeks = ceil(total.toDouble() / 7).toInt(),
        )
    }

    /** D-3 / D-DAY / 종료 */
    fun dDayLabel(remain: Int): String =
        if (remain > 0) "D-$remain" else (if (remain == 0) "D-DAY" else "종료")

    /** 숫자 표시: 72.0 → "72", 72.5 → "72.5", 1234 → "1,234" (iOS NumberFormatter .decimal 과 같아요) */
    fun number(v: Double?, maxFraction: Int = 1): String {
        if (v == null) return "-"
        val f = NumberFormat.getNumberInstance(Locale.KOREA)
        f.maximumFractionDigits = maxFraction
        f.minimumFractionDigits = 0
        return f.format(v)
    }
}

// MARK: - goal/kind.ts — 목표 카테고리 2단계

@Serializable(with = GoalType.Serializer::class)
enum class GoalType {
    HEALTH, LEARNING, HABIT, ETC, WORK;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<GoalType>("GoalType", GoalType.entries.toTypedArray(), GoalType.ETC)

    companion object {
        val fallback: GoalType get() = ETC
        fun fromRaw(raw: String?): GoalType? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable(with = GoalSubType.Serializer::class)
enum class GoalSubType {
    EXERCISE, DIET, WATER, NO_ALCOHOL, NO_SMOKING, HEALTH_OTHER,
    LANGUAGE, CERTIFICATE, READING, CODING, LEARNING_OTHER,
    WAKE_UP, SLEEP, MEDITATION, CLEANING, JOURNAL,
    SAVING, OTHER,
    COMPANY_WORK, SIDE_PROJECT;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<GoalSubType>("GoalSubType", GoalSubType.entries.toTypedArray(), GoalSubType.OTHER)

    companion object {
        val fallback: GoalSubType get() = OTHER
        fun fromRaw(raw: String?): GoalSubType? = entries.firstOrNull { it.rawValue == raw }
    }
}

/**
 * 목표 종류 입력값 (화면 상태). 입력 중에 goalKindLabel 을 직접 바꾸는 코드가 있어서 var 로 뒀어요.
 * ⚠️ Kotlin 은 참조라서, 바꾸기 전에 copy() 로 복사본을 만들어 바꾸고 상태에 다시 넣어 주세요.
 */
data class GoalKindValue(
    var goalType: GoalType,
    var goalSubType: GoalSubType,
    /** "기타"일 때만 (1~10자) */
    var goalKindLabel: String? = null,
) {
    companion object {}
}

data class GoalSubOption(
    val value: GoalSubType,
    val label: String,
    val emoji: String,
    val custom: Boolean = false,
    /** 시작 화면 예시 문장 */
    val examples: List<String> = emptyList(),
) {
    val id: GoalSubType get() = value

    companion object {}
}

data class GoalTypeOption(
    val value: GoalType,
    val label: String,
    val emoji: String,
    val hint: String,
    val subs: List<GoalSubOption>,
) {
    val id: GoalType get() = value

    companion object {}
}

object GoalKind {
    const val labelMax = 10

    /** 생활형 목표 카테고리 (업무형 WORK 는 따로) */
    val types: List<GoalTypeOption> = listOf(
        GoalTypeOption(
            value = GoalType.HEALTH, label = "건강", emoji = "💪", hint = "운동 · 다이어트 · 금연", subs = listOf(
                GoalSubOption(value = GoalSubType.EXERCISE, label = "운동", emoji = "🏋️", examples = listOf("주 3회 헬스로 근력 키우기", "3개월 안에 5km 30분 안에 뛰기")),
                GoalSubOption(value = GoalSubType.DIET, label = "다이어트", emoji = "🥗", examples = listOf("2주 동안 3kg 감량하기", "3개월에 8kg 빼기")),
                GoalSubOption(value = GoalSubType.WATER, label = "물 마시기", emoji = "💧", examples = listOf("하루 물 2L 마시기")),
                GoalSubOption(value = GoalSubType.NO_ALCOHOL, label = "금주", emoji = "🚱", examples = listOf("한 달 동안 술 끊기", "술은 주 1회 이하로")),
                GoalSubOption(value = GoalSubType.NO_SMOKING, label = "금연", emoji = "🚭", examples = listOf("다음 달부터 완전히 금연하기", "하루 10개비에서 0개비로")),
                GoalSubOption(value = GoalSubType.HEALTH_OTHER, label = "기타", emoji = "✏️", custom = true),
            )
        ),
        GoalTypeOption(
            value = GoalType.LEARNING, label = "학습·자기개발", emoji = "📚", hint = "언어 · 자격증 · 독서 · 코딩", subs = listOf(
                GoalSubOption(value = GoalSubType.LANGUAGE, label = "언어", emoji = "🗣️", examples = listOf("토익 900점 받기", "6개월 안에 일본어로 일상 회화하기")),
                GoalSubOption(value = GoalSubType.CERTIFICATE, label = "자격증", emoji = "📜", examples = listOf("정보처리기사 필기 합격하기", "컴활 1급 따기")),
                GoalSubOption(value = GoalSubType.READING, label = "독서", emoji = "📖", examples = listOf("올해 책 12권 읽기", "한 달에 2권 읽기")),
                GoalSubOption(value = GoalSubType.CODING, label = "코딩", emoji = "💻", examples = listOf("백준 골드 달성하기", "3개월 안에 포트폴리오 앱 완성하기")),
                GoalSubOption(value = GoalSubType.LEARNING_OTHER, label = "기타", emoji = "✏️", custom = true),
            )
        ),
        GoalTypeOption(
            value = GoalType.HABIT, label = "생활습관", emoji = "🌱", hint = "기상 · 취침 · 명상 · 청소 · 일기", subs = listOf(
                GoalSubOption(value = GoalSubType.WAKE_UP, label = "기상", emoji = "🌅", examples = listOf("매일 아침 6시 30분에 일어나기")),
                GoalSubOption(value = GoalSubType.SLEEP, label = "취침", emoji = "🌙", examples = listOf("자정 전에 잠들기")),
                GoalSubOption(value = GoalSubType.MEDITATION, label = "명상", emoji = "🧘", examples = listOf("매일 10분 명상하기")),
                GoalSubOption(value = GoalSubType.CLEANING, label = "청소", emoji = "🧹", examples = listOf("방을 매일 깔끔하게 유지하기")),
                GoalSubOption(value = GoalSubType.JOURNAL, label = "일기", emoji = "📝", examples = listOf("매일 자기 전 일기 쓰기")),
            )
        ),
        GoalTypeOption(
            value = GoalType.ETC, label = "기타", emoji = "✨", hint = "돈 모으기 · 직접 적기", subs = listOf(
                GoalSubOption(value = GoalSubType.SAVING, label = "돈 모으기", emoji = "💰", examples = listOf("6개월 안에 300만 원 모으기", "한 달 식비 40만 원 이하로")),
                GoalSubOption(value = GoalSubType.OTHER, label = "기타", emoji = "✏️", custom = true),
            )
        ),
    )

    /**
     * AI 로 만드는 목표형 목표 카테고리 (2026-10): 기본 생활 패턴(물 마시기 · 기상 · 취침 · 명상 · 청소 · 일기)은 빼고
     * 이뤄야 하고 수치가 있는 것만. 남는 게 적어서 화면에서는 칩으로만 보여요.
     * 2026-10-24: 금주 · 금연 · 코딩 빼고 돈 모으기 추가 (직접 만드는 목표에는 그대로 있어요)
     */
    private val aiExcludedSubs = setOf(GoalSubType.WATER, GoalSubType.NO_ALCOHOL, GoalSubType.NO_SMOKING, GoalSubType.CODING)
    private val aiHints = mapOf(
        GoalType.HEALTH to "운동 · 다이어트",
        GoalType.LEARNING to "언어 · 자격증 · 독서",
    )
    val aiTypes: List<GoalTypeOption> = types
        .filter { it.value != GoalType.HABIT }
        .map { t -> t.copy(hint = aiHints[t.value] ?: t.hint, subs = t.subs.filter { it.value !in aiExcludedSubs }) }

    /** 업무형(기록형) 목표 전용 종류 */
    val workType: GoalTypeOption = GoalTypeOption(
        value = GoalType.WORK, label = "업무", emoji = "💼", hint = "회사 업무 · 사이드 프로젝트", subs = listOf(
            GoalSubOption(value = GoalSubType.COMPANY_WORK, label = "회사 업무", emoji = "💼", examples = listOf("OO팀 업무", "나를 업무")),
            GoalSubOption(value = GoalSubType.SIDE_PROJECT, label = "사이드 프로젝트", emoji = "🚀", examples = listOf("포트폴리오 앱", "블로그 운영")),
        )
    )

    val allTypes: List<GoalTypeOption> get() = types + listOf(workType)

    /** "이 목표를 위해 꼭 하고 싶은 일" 추천 칩 */
    val mustDoSuggestions: Map<GoalSubType, List<String>> = mapOf(
        GoalSubType.EXERCISE to listOf("헬스", "러닝", "홈트레이닝", "스트레칭", "수영"),
        GoalSubType.DIET to listOf("헬스", "식단 기록", "유산소", "체중 기록", "간헐적 단식"),
        GoalSubType.WATER to listOf("물 마시기 체크", "텀블러 들고 다니기"),
        GoalSubType.NO_ALCOHOL to listOf("음주 기록", "술자리 대신 운동"),
        GoalSubType.NO_SMOKING to listOf("흡연 욕구 기록", "금연 클리닉"),
        GoalSubType.LANGUAGE to listOf("단어 암기", "듣기 연습", "회화 연습", "모의고사"),
        GoalSubType.CERTIFICATE to listOf("이론 공부", "기출 풀이", "오답 정리", "모의고사"),
        GoalSubType.READING to listOf("매일 읽기", "독서 기록"),
        GoalSubType.CODING to listOf("알고리즘 문제 풀이", "강의 듣기", "프로젝트 개발"),
        GoalSubType.SAVING to listOf("가계부 쓰기", "자동 이체", "무지출 데이", "지출 점검"),
        GoalSubType.WAKE_UP to listOf("기상 인증", "아침 스트레칭"),
        GoalSubType.SLEEP to listOf("취침 준비 루틴", "자기 전 휴대폰 끄기"),
        GoalSubType.MEDITATION to listOf("아침 명상", "호흡 연습"),
        GoalSubType.CLEANING to listOf("정리 정돈", "설거지 바로 하기"),
        GoalSubType.JOURNAL to listOf("일기 쓰기", "감사 일기"),
    )

    fun typeOption(t: GoalType): GoalTypeOption =
        allTypes.firstOrNull { it.value == t } ?: types[types.size - 1]

    fun subOption(s: GoalSubType): GoalSubOption =
        allTypes.flatMap { it.subs }.firstOrNull { it.value == s }
            ?: GoalSubOption(value = GoalSubType.OTHER, label = "기타", emoji = "✏️", custom = true)

    /** Swift trimmingCharacters(in: .whitespaces) — 줄바꿈은 남기고 공백·탭만 앞뒤에서 지워요 */
    private fun trimSpaces(s: String): String = s.trim { it == ' ' || it == '\t' || it == ' ' || it == '　' }

    /** 입력이 끝났는지 (기타면 이름 1~10자) */
    fun isComplete(v: GoalKindValue?): Boolean {
        if (v == null) return false
        if (!subOption(v.goalSubType).custom) return true
        val label = trimSpaces(v.goalKindLabel ?: "")
        // Swift 의 count 는 "보이는 글자 수"라서, 이모지가 2칸으로 세지지 않게 코드포인트로 세요
        return label.isNotEmpty() && label.codePointCount(0, label.length) <= labelMax
    }

    /** "건강 · 다이어트", "기타 · 악기 연습" */
    fun name(type: GoalType?, subType: GoalSubType?, label: String?): String? {
        if (type == null) return null
        val t = typeOption(type)
        if (subType == null) return t.label
        val sub = subOption(subType)
        val trimmed = trimSpaces(label ?: "")
        val subLabel = if (sub.custom) (if (trimmed.isEmpty()) sub.label else trimmed) else sub.label
        return "${t.label} · $subLabel"
    }

    fun name(v: GoalKindValue?): String? =
        name(type = v?.goalType, subType = v?.goalSubType, label = v?.goalKindLabel)

    /**
     * API 요청 바디용 (기타가 아니면 이름은 보내지 않아요). 다른 필드와 합쳐 쓰세요.
     *   val body = JsonObject(mapOf("title" to JsonPrimitive(t)) + GoalKind.body(kind))
     */
    fun body(v: GoalKindValue?): Map<String, JSONValue> {
        if (v == null) return emptyMap()
        val out = mutableMapOf<String, JsonElement>(
            "goalType" to JsonPrimitive(v.goalType.rawValue),
            "goalSubType" to JsonPrimitive(v.goalSubType.rawValue),
        )
        if (subOption(v.goalSubType).custom) {
            out["goalKindLabel"] = JsonPrimitive(trimSpaces(v.goalKindLabel ?: ""))
        }
        return out
    }
}

// MARK: - 목표 이름으로 카테고리 짐작 (웹 features/goal/kind.ts guessKind · kindOfGoal, iOS GoalKind.guess · of — 2026-10)

private val KIND_KEYWORDS: List<Pair<GoalSubType, Regex>> = listOf(
    GoalSubType.LANGUAGE to "토익|토플|오픽|opic|toeic|ielts|jlpt|hsk|영어|일본어|중국어|회화|스피킹|speaking|어학",
    GoalSubType.CERTIFICATE to "자격증|기사|산업기사|컴활|한국사|시험|합격|면허|필기|실기",
    GoalSubType.CODING to "코딩|개발|프로그래밍|알고리즘|코테|코딩테스트|백준|프로젝트|앱 만들기",
    GoalSubType.READING to "독서|책|읽기|완독",
    GoalSubType.DIET to "다이어트|감량|체중|몸무게|kg|살 ?빼|식단",
    GoalSubType.EXERCISE to "운동|헬스|러닝|달리기|조깅|마라톤|요가|필라테스|근력|수영|등산|자전거|홈트|크로스핏|스트레칭",
    GoalSubType.WATER to "물 ?마시|수분",
    GoalSubType.NO_ALCOHOL to "금주|술",
    GoalSubType.NO_SMOKING to "금연|담배",
    GoalSubType.WAKE_UP to "기상|일찍 일어|미라클 ?모닝|아침형",
    GoalSubType.SLEEP to "수면|취침|일찍 자|잠",
    GoalSubType.MEDITATION to "명상|마음챙김",
    GoalSubType.CLEANING to "청소|정리정돈|방 정리",
    GoalSubType.JOURNAL to "일기|다이어리|감사 ?일기",
    GoalSubType.SAVING to "저축|적금|저금|돈 ?모으|재테크|절약",
).map { (sub, p) -> sub to Regex(p, RegexOption.IGNORE_CASE) }

/** AI 를 부르지 않는 키워드 규칙 — 예전 · 임시 목표를 AI 로 설계할 때 시작 화면에 미리 골라 둬요 (틀려도 바로 바꿀 수 있어요) */
fun GoalKind.guess(name: String): GoalKindValue {
    val text = name.trim()
    for ((sub, re) in KIND_KEYWORDS) {
        if (re.containsMatchIn(text)) {
            val type = types.firstOrNull { t -> t.subs.any { it.value == sub } }?.value
            if (type != null) return GoalKindValue(goalType = type, goalSubType = sub)
        }
    }
    val label = text.take(labelMax)
    return GoalKindValue(goalType = GoalType.ETC, goalSubType = GoalSubType.OTHER, goalKindLabel = label.ifEmpty { "기타" })
}

/** 목표에 저장된 카테고리 → 없으면 이름으로 짐작 */
fun GoalKind.of(goal: GoalCategory): GoalKindValue {
    val t = goal.goalType
    val s = goal.goalSubType
    if (t != null && s != null && t != GoalType.WORK && types.any { it.value == t }) {
        val v = GoalKindValue(goalType = t, goalSubType = s, goalKindLabel = goal.goalKindLabel)
        if (isComplete(v)) return v
    }
    return guess(goal.goalCategoryName)
}
