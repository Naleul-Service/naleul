// feature/goalcreation/GoalCreationModels.kt
//
// iOS Features/GoalCreation/GoalCreationModels.swift
// AI 목표 만들기 — 백엔드 응답/요청 모델 · 상수 · 화면 경로.
// 웹 원본: features/goal-creation/types.ts · constants.ts · routes.ts
// 백엔드 DTO: domain/goalCreation/dto/** (필드 타입은 여기서 확인했어요)
//
// 주의
//  - GoalSessionTurnResponse 는 @JsonInclude(ALWAYS) 라 message/summary 가 null 로 와요 → nullable 로 받아요.
//  - 초안(GoalPlan)은 "받은 그대로 고쳐서 다시 보내는" 구조라(확정 요청 바디), 모르는 값이 와도
//    바꾸지 않고 그대로 돌려보내도록 category · subType 같은 값은 String 으로, 세부 목표 수치는 JsonElement 로 둬요.
//  - 날짜는 "YYYY-MM-DD", 시각은 "HH:mm" 문자열 그대로예요.
//  - iOS 의 커스텀 init(from decoder:) (없으면 기본값) 는 Kotlin 에서 "파라미터 기본값" 으로 옮겼어요.
//    (NJson 설정 coerceInputValues 덕분에 null 이 와도 기본값이 들어가요)
//  - iOS 모델은 var 라 그 자리에서 고쳤지만, Kotlin 은 val + copy() 로 새 값을 만들어 바꿔요.

package com.naleul.naleul.feature.goalcreation

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

// MARK: - 상태 enum

@Serializable(with = GoalCreationSessionStatus.Serializer::class)
enum class GoalCreationSessionStatus {
    INTERVIEWING, READY_TO_GENERATE, GENERATING, DRAFT_READY, CONFIRMED, ABANDONED;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<GoalCreationSessionStatus>(
        "GoalCreationSessionStatus", GoalCreationSessionStatus.entries.toTypedArray(), GoalCreationSessionStatus.ABANDONED,
    )

    companion object {
        val fallback: GoalCreationSessionStatus get() = ABANDONED
        fun fromRaw(raw: String?): GoalCreationSessionStatus? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable(with = GoalCreationSlotStatus.Serializer::class)
enum class GoalCreationSlotStatus {
    EMPTY, FILLED, SKIPPED;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<GoalCreationSlotStatus>(
        "GoalCreationSlotStatus", GoalCreationSlotStatus.entries.toTypedArray(), GoalCreationSlotStatus.EMPTY,
    )

    companion object {
        val fallback: GoalCreationSlotStatus get() = EMPTY
        fun fromRaw(raw: String?): GoalCreationSlotStatus? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable(with = GoalCreationMessageRole.Serializer::class)
enum class GoalCreationMessageRole {
    USER, ASSISTANT;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<GoalCreationMessageRole>(
        "GoalCreationMessageRole", GoalCreationMessageRole.entries.toTypedArray(), GoalCreationMessageRole.ASSISTANT,
    )

    companion object {
        val fallback: GoalCreationMessageRole get() = ASSISTANT
        fun fromRaw(raw: String?): GoalCreationMessageRole? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable(with = GoalCreationDraftStatus.Serializer::class)
enum class GoalCreationDraftStatus {
    GENERATING, READY, FAILED;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<GoalCreationDraftStatus>(
        "GoalCreationDraftStatus", GoalCreationDraftStatus.entries.toTypedArray(), GoalCreationDraftStatus.FAILED,
    )

    companion object {
        val fallback: GoalCreationDraftStatus get() = FAILED
        fun fromRaw(raw: String?): GoalCreationDraftStatus? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** 계획 강도 — 안정형(기본)은 지금 실력 기준, 도전형은 조금 더 높게 */
@Serializable(with = GoalCreationPlanIntensity.Serializer::class)
enum class GoalCreationPlanIntensity {
    STEADY, CHALLENGE;

    val rawValue: String get() = name

    /** 웹 PLAN_INTENSITY_LABEL */
    val label: String
        get() = when (this) {
            STEADY -> "안정형"
            CHALLENGE -> "도전형"
        }

    /** 웹 PLAN_INTENSITY_DESCRIPTION */
    val descriptionText: String
        get() = when (this) {
            STEADY -> "지금 실천량에 맞춰 작게 시작해 꾸준히 이어가요"
            CHALLENGE -> "조금 더 빡빡하게, 평소보다 한 단계 높여 잡아요"
        }

    object Serializer : FallbackEnumSerializer<GoalCreationPlanIntensity>(
        "GoalCreationPlanIntensity", GoalCreationPlanIntensity.entries.toTypedArray(), GoalCreationPlanIntensity.STEADY,
    )

    companion object {
        val fallback: GoalCreationPlanIntensity get() = STEADY
        fun fromRaw(raw: String?): GoalCreationPlanIntensity? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** 요일 "MON" … "SUN" (백엔드 Weekday) */
@Serializable(with = GoalCreationWeekday.Serializer::class)
enum class GoalCreationWeekday {
    MON, TUE, WED, THU, FRI, SAT, SUN;

    val rawValue: String get() = name

    /** 웹 DAY_LABEL */
    val label: String
        get() = when (this) {
            MON -> "월"
            TUE -> "화"
            WED -> "수"
            THU -> "목"
            FRI -> "금"
            SAT -> "토"
            SUN -> "일"
        }

    object Serializer : FallbackEnumSerializer<GoalCreationWeekday>(
        "GoalCreationWeekday", GoalCreationWeekday.entries.toTypedArray(), GoalCreationWeekday.MON,
    )

    companion object {
        val fallback: GoalCreationWeekday get() = MON
        fun fromRaw(raw: String?): GoalCreationWeekday? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** 초안 Task 종류 */
@Serializable(with = DraftTaskType.Serializer::class)
enum class DraftTaskType {
    ROUTINE, ONE_TIME;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<DraftTaskType>(
        "DraftTaskType", DraftTaskType.entries.toTypedArray(), DraftTaskType.ONE_TIME,
    )

    companion object {
        val fallback: DraftTaskType get() = ONE_TIME
        fun fromRaw(raw: String?): DraftTaskType? = entries.firstOrNull { it.rawValue == raw }
    }
}

/**
 * 인터뷰 슬롯 이름 (웹 SlotKey)
 * V52: 이유(motivation)는 더 묻지 않고, 어떻게 이룰지(approach)를 물어요. motivation 은 예전 세션 호환용
 */
enum class GoalCreationSlotKey {
    goalStatement, metric, motivation, deadline, approach, practicePreference;

    val rawValue: String get() = name
    val id: String get() = rawValue

    /** 웹 SlotEditModal TITLES */
    val editTitle: String
        get() = when (this) {
            goalStatement -> "목표 수정"
            metric -> "수치 수정"
            motivation -> "이유 수정"
            deadline -> "기한 수정"
            approach -> "계획 수정"
            practicePreference -> "실천 시간 수정"
        }

    companion object {
        fun fromRaw(raw: String?): GoalCreationSlotKey? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** 웹 PLANNING_STYLE_LABEL */
val PlanningStyle.goalCreationLabel: String
    get() = when (this) {
        PlanningStyle.PLANNER -> "차근차근 계획형"
        PlanningStyle.SPONTANEOUS -> "몰아서 하는 즉흥형"
    }

// MARK: - 슬롯

/** DB 에 status 가 비어 있으면 EMPTY 로 봐요 (백엔드 Slot.isEmpty 와 같게) */
@Serializable
data class GoalCreationSlot<T>(
    val status: GoalCreationSlotStatus = GoalCreationSlotStatus.EMPTY,
    val value: T? = null,
) {
    companion object {
        fun <T> empty(): GoalCreationSlot<T> = GoalCreationSlot(GoalCreationSlotStatus.EMPTY, null)
    }
}

/** { "name": "체중", "currentValue": 78, "targetValue": 68, "unit": "kg" } */
@Serializable
data class GoalCreationMetricValue(
    val name: String? = null,
    val currentValue: Double? = null,
    val targetValue: Double? = null,
    val unit: String? = null,
) { companion object }

@Serializable
data class GoalCreationDeadlineValue(
    /** yyyy-MM-dd */
    val endDate: String? = null,
    val isFlexible: Boolean? = null,
    /** 사용자가 말한 시작일 (없으면 오늘부터) — 루틴 · Task 가 이 날부터 생겨요 */
    val startDate: String? = null,
) { companion object }

@Serializable
data class GoalCreationTimeRange(
    /** "HH:mm" */
    val start: String = "",
    val end: String = "",
) { companion object }

@Serializable
data class GoalCreationPracticePreference(
    val preferredDays: List<GoalCreationWeekday>? = null,
    val preferredTimeRanges: List<GoalCreationTimeRange>? = null,
    val unavailableNote: String? = null,
    val workStyleAnswer: String? = null,
) { companion object }

@Serializable
data class GoalCreationSlots(
    val goalStatement: GoalCreationSlot<String> = GoalCreationSlot(),
    val metric: GoalCreationSlot<GoalCreationMetricValue> = GoalCreationSlot(),
    val motivation: GoalCreationSlot<String> = GoalCreationSlot(),
    val deadline: GoalCreationSlot<GoalCreationDeadlineValue> = GoalCreationSlot(),
    val practicePreference: GoalCreationSlot<GoalCreationPracticePreference> = GoalCreationSlot(),
    /** 어떻게 이루고 싶은지 · 정해 둔 계획 (V52, 예전 세션엔 없어요) */
    val approach: GoalCreationSlot<String> = GoalCreationSlot(),
) {
    companion object {
        val empty: GoalCreationSlots get() = GoalCreationSlots()
    }
}

/** 정리 확인 화면용 사람이 읽기 좋은 요약 (값이 없으면 null) */
@Serializable
data class GoalCreationSummary(
    val goalStatement: String? = null,
    val metricText: String? = null,
    val motivation: String? = null,
    val deadlineText: String? = null,
    val preferenceText: String? = null,
    val planningStyle: PlanningStyle? = null,
    /** 어떻게 이루고 싶은지 · 정해 둔 계획 (V52) */
    val approach: String? = null,
) { companion object }

// MARK: - 대화

@Serializable
data class GoalCreationMessage(
    val messageId: Int,
    val role: GoalCreationMessageRole = GoalCreationMessageRole.ASSISTANT,
    val content: String? = null,
    val targetSlot: String? = null,
    val quickReplies: List<String>? = null,
    val skippable: Boolean? = null,
    val skip: Boolean? = null,
) { companion object }

/** 세션 시작 / 답변 / 조기 종료 / 슬롯 수정 응답 */
@Serializable
data class GoalCreationTurnResponse(
    val sessionId: Int,
    val status: GoalCreationSessionStatus,
    val questionCount: Int = 0,
    val maxQuestions: Int = 0,
    val canFinishEarly: Boolean = false,
    val message: GoalCreationMessage? = null,
    val slots: GoalCreationSlots = GoalCreationSlots(),
    val summary: GoalCreationSummary? = null,
    /** 이 목표를 위해 꼭 하고 싶은 일 — 계획에 반드시 들어가요 */
    val mustDoItems: List<String>? = null,
) { companion object }

@Serializable
data class GoalCreationDraftRef(
    val draftId: Int,
    val version: Int = 0,
    val status: GoalCreationDraftStatus = GoalCreationDraftStatus.FAILED,
) { companion object }

/** 세션 조회 (이어하기) */
@Serializable
data class GoalCreationSessionDetail(
    val sessionId: Int,
    val status: GoalCreationSessionStatus,
    val questionCount: Int = 0,
    val maxQuestions: Int = 0,
    val canFinishEarly: Boolean = false,
    val messages: List<GoalCreationMessage> = emptyList(),
    val slots: GoalCreationSlots = GoalCreationSlots(),
    val summary: GoalCreationSummary? = null,
    val latestDraft: GoalCreationDraftRef? = null,
    val mustDoItems: List<String>? = null,
) { companion object }

/** PATCH /slots — 바꾼 것만 보내요 (null 은 JSON 에서 빠져요 = 안 바꿈. NJson explicitNulls = false) */
@Serializable
data class GoalCreationSlotsPatch(
    val goalStatement: String? = null,
    val metric: GoalCreationMetricValue? = null,
    val motivation: String? = null,
    val approach: String? = null,
    val deadline: GoalCreationDeadlineValue? = null,
    val practicePreference: GoalCreationPracticePreference? = null,
    /** 빈 목록이면 지워요 */
    val mustDoItems: List<String>? = null,
) { companion object }

// MARK: - 초안 (GoalPlan) — 초안 응답 = 확정 요청 공통 구조

@Serializable
data class DraftPlanGoalMetric(
    val name: String = "",
    val unit: String? = null,
    val startValue: Double = 0.0,
    val targetValue: Double = 0.0,
) { companion object }

@Serializable
data class DraftPlanGoal(
    val title: String = "",
    val emoji: String? = null,
    /** HEALTH · STUDY … (GoalTopic) — 받은 값 그대로 돌려보내요 */
    val category: String? = null,
    val description: String? = null,
    val metric: DraftPlanGoalMetric? = null,
    val startDate: String = KST.today(),
    val endDate: String = KST.today(),
    val planningStyle: PlanningStyle = PlanningStyle.PLANNER,
    val aiNote: String? = null,
    /** 목표 카테고리 2단계 (GoalSubType 이름) */
    val subType: String? = null,
) { companion object }

@Serializable
data class DraftPlanSubGoal(
    val tempId: String,
    val title: String = "",
    val emoji: String? = null,
    val description: String? = null,
    /** 화면에선 안 쓰고 그대로 돌려보내요 ({ name, unit, comparator, targetValue, period }) */
    val metric: JsonElement? = null,
) { companion object }

@Serializable
data class DraftPlanMilestone(
    val tempId: String,
    val title: String = "",
    val description: String? = null,
    val dueDate: String = "",
    val targetValue: Double? = null,
) { companion object }

@Serializable
data class DraftPlanTask(
    val tempId: String,
    val type: DraftTaskType = DraftTaskType.ONE_TIME,
    val subGoalTempId: String? = null,
    val milestoneTempId: String? = null,
    val title: String = "",
    val emoji: String? = null,
    val durationMinutes: Int = 0,
    val routineDays: List<GoalCreationWeekday>? = null,
    /** "HH:mm" (받을 때 "07:00:00" 이면 GoalCreationAPI.getDraft 가 "07:00" 으로 맞춰요) */
    val preferredStartTime: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val scheduledDate: String? = null,
    val dueDate: String? = null,
    /** 루틴의 구체적인 방법 (예: "스쿼트 4x10, 런지 3x12…") */
    val description: String? = null,
    /** AI 가 이렇게 정한 이유 — 검토 화면에서만 보여요 */
    val reason: String? = null,
) {
    val isRoutine: Boolean get() = type == DraftTaskType.ROUTINE

    companion object {}
}

/** 검증 규칙 위반 1건 (서버 violations / warnings 와 같은 모양) */
@Serializable
data class DraftPlanIssue(
    val ruleId: String = "",
    val path: String = "",
    val message: String = "",
) { companion object }

/** "나에게 맞춘 점" */
@Serializable
data class DraftPlanFit(
    val intensity: GoalCreationPlanIntensity = GoalCreationPlanIntensity.STEADY,
    /** 이 사용자 기준 루틴 주간 상한 (분) */
    val weeklyCapMinutes: Int = 0,
    /** 이 계획의 루틴 주간 합계 (분) */
    val planWeeklyMinutes: Int = 0,
    /** 최근 4주 실행 기록을 바탕으로 했는지 */
    val basedOnHistory: Boolean = false,
    val notes: List<String> = emptyList(),
) { companion object }

@Serializable
data class DraftGoalPlan(
    val goal: DraftPlanGoal,
    val subGoals: List<DraftPlanSubGoal> = emptyList(),
    val milestones: List<DraftPlanMilestone> = emptyList(),
    val tasks: List<DraftPlanTask> = emptyList(),
    val warnings: List<DraftPlanIssue> = emptyList(),
    /** 이 계획이 나에게 어떻게 맞춰졌는지 (기존 초안엔 없을 수 있어요) */
    val fit: DraftPlanFit? = null,
) { companion object }

// MARK: - 초안 생성 / 폴링

/** POST /drafts → 202 */
@Serializable
data class GoalCreationDraftRequested(
    val draftId: Int,
    val version: Int = 0,
    val status: GoalCreationDraftStatus = GoalCreationDraftStatus.GENERATING,
    val pollAfterMs: Int? = null,
) { companion object }

/** GET /drafts/{draftId} */
@Serializable
data class GoalCreationDraftResponse(
    val draftId: Int,
    val version: Int = 0,
    val status: GoalCreationDraftStatus,
    val planningStyle: PlanningStyle? = null,
    /** ANALYZING · SUB_GOALS · MILESTONES · TASKS · VALIDATING */
    val progressStep: String? = null,
    val pollAfterMs: Int? = null,
    val plan: DraftGoalPlan? = null,
    val errorCode: String? = null,
    val message: String? = null,
) { companion object }

/** POST /drafts 바디 (모두 선택) */
@Serializable
data class GoalCreationDraftRequestBody(
    val feedback: String? = null,
    val planningStyle: PlanningStyle? = null,
    val intensity: GoalCreationPlanIntensity? = null,
    /** "말로 고치기" — 화면에서 직접 고친 지금 초안 */
    val basePlan: DraftGoalPlan? = null,
) { companion object }

/** POST /confirm 바디 */
@Serializable
data class GoalCreationConfirmRequest(
    val draftId: Int,
    val plan: DraftGoalPlan,
    val planningStyleSelectedByUser: Boolean,
    /** 선택한 UserColor id. null 이면 서버가 자동 배정 */
    val colorId: Int? = null,
) { companion object }

@Serializable
data class GoalCreationConfirmResponse(
    val goalId: Int,
) { companion object }

// MARK: - 상수 (웹 constants.ts)

/** 생성 단계 하나 (iOS 튜플 (step:, label:)) */
data class GoalCreationProgressStep(val step: String, val label: String) { companion object }

object GoalCreationConst {
    /** 시작 화면의 예시 목표 칩 */
    val exampleGoals: List<String> = listOf(
        "몸무게 10kg 감량하기",
        "토익 900점 받기",
        "올해 책 12권 읽기",
        "매일 아침 운동하는 습관 만들기",
    )

    /** 닫기 확인창 문구 */
    const val leaveNotice = "진행 중인 목표는 7일 동안 이어서 만들 수 있어요."

    /** 생성 단계 문구 */
    val progressSteps: List<GoalCreationProgressStep> = listOf(
        GoalCreationProgressStep("ANALYZING", "목표를 분석하고 있어요"),
        GoalCreationProgressStep("SUB_GOALS", "세부 목표를 나누고 있어요"),
        GoalCreationProgressStep("MILESTONES", "마일스톤을 정하고 있어요"),
        GoalCreationProgressStep("TASKS", "꼭 필요한 루틴만 짜고 있어요"),
        GoalCreationProgressStep("VALIDATING", "계획을 점검하고 있어요"),
    )

    /** 폴링 간격 / 최대 대기 (명세: 2초, 90초) */
    const val pollIntervalMs = 2000
    const val pollTimeoutMs = 90_000
}

// MARK: - 화면 경로 (웹 routes.ts)

/**
 * 목표 만들기 플로우 안의 화면. 웹 URL 대신 이 값으로 화면을 바꿔요.
 * (iOS 연관값 enum → sealed class. data class 라 같은 단계면 equals 가 같아요)
 */
sealed class GoalCreationRoute {
    /** /goal/new */
    object start : GoalCreationRoute()

    /** 이어서 하기 — 세션을 불러와 상태에 맞는 화면으로 */
    data class resume(val sessionId: Int) : GoalCreationRoute()

    /** /goal/new/{id} */
    data class chat(val sessionId: Int) : GoalCreationRoute()

    /** /goal/new/{id}/review */
    data class review(val sessionId: Int) : GoalCreationRoute()

    /** /goal/new/{id}/generating?draftId= */
    data class generating(val sessionId: Int, val draftId: Int? = null) : GoalCreationRoute()

    /** /goal/new/{id}/draft */
    data class draft(val sessionId: Int) : GoalCreationRoute()

    companion object {
        /** 웹 pathForStatus — CONFIRMED / ABANDONED 는 이어갈 화면이 없어요 */
        fun forStatus(id: Int, status: GoalCreationSessionStatus): GoalCreationRoute? = when (status) {
            GoalCreationSessionStatus.INTERVIEWING -> chat(id)
            GoalCreationSessionStatus.READY_TO_GENERATE -> review(id)
            GoalCreationSessionStatus.GENERATING -> generating(id, draftId = null)
            GoalCreationSessionStatus.DRAFT_READY -> draft(id)
            GoalCreationSessionStatus.CONFIRMED, GoalCreationSessionStatus.ABANDONED -> null
        }
    }
}

// MARK: - 미리보기용 샘플

object GoalCreationSamples {
    private inline fun <reified T> decode(json: String): T =
        NJson.decodeFromString(kotlinx.serialization.serializer<T>(), json)

    val slotsJSON: String = """
    {
      "goalStatement": { "status": "FILLED", "value": "3개월에 8kg 빼기" },
      "metric": { "status": "FILLED", "value": { "name": "체중", "currentValue": 78, "targetValue": 70, "unit": "kg" } },
      "motivation": { "status": "FILLED", "value": "건강검진에서 지방간이 나와서" },
      "deadline": { "status": "FILLED", "value": { "endDate": "2027-01-31", "isFlexible": false } },
      "practicePreference": { "status": "FILLED", "value": { "preferredDays": ["MON","WED","FRI"], "preferredTimeRanges": [{"start":"19:00","end":"21:00"}], "unavailableNote": "평일 9~18시 회사", "workStyleAnswer": null } }
    }
    """

    val slots: GoalCreationSlots get() = decode(slotsJSON)

    val session: GoalCreationSessionDetail
        get() = decode(
            """
            {
              "sessionId": 101, "status": "INTERVIEWING", "questionCount": 2, "maxQuestions": 5, "canFinishEarly": true,
              "messages": [
                { "messageId": 1, "role": "ASSISTANT", "content": "좋아요! 지금 몸무게와 목표 몸무게를 알려주세요.", "targetSlot": "metric", "quickReplies": [], "skippable": true, "skip": false },
                { "messageId": 2, "role": "USER", "content": "78kg인데 70kg까지 빼고 싶어요", "skippable": false, "skip": false },
                { "messageId": 3, "role": "ASSISTANT", "content": "왜 이 목표를 이루고 싶으세요?", "targetSlot": "motivation", "quickReplies": ["건강 때문에", "자신감을 위해", "옷 핏이 좋아지게"], "skippable": true, "skip": false }
              ],
              "slots": $slotsJSON,
              "summary": { "goalStatement": "3개월에 8kg 빼기", "metricText": "체중 78kg → 70kg", "motivation": "건강검진에서 지방간이 나와서", "deadlineText": "2027년 1월 31일까지", "preferenceText": "월·수·금 19:00~21:00", "planningStyle": "PLANNER" },
              "latestDraft": { "draftId": 7, "version": 1, "status": "READY" },
              "mustDoItems": ["헬스", "식단 기록"]
            }
            """
        )

    val plan: DraftGoalPlan
        get() {
            val start = KST.addDays(KST.today(), 1)
            val m1 = KST.addDays(start, 30)
            val m2 = KST.addDays(start, 60)
            val end = KST.addDays(start, 90)
            val t3s = KST.addDays(start, 3)
            val t3d = KST.addDays(start, 7)
            return decode(
                """
                {
                  "goal": { "title": "3개월에 8kg 감량", "emoji": "🏃", "category": "HEALTH", "description": null,
                            "metric": { "name": "체중", "unit": "kg", "startValue": 78, "targetValue": 70 },
                            "startDate": "$start", "endDate": "$end", "planningStyle": "PLANNER",
                            "aiNote": "처음 2주는 습관을 만드는 데 집중해요.", "subType": "DIET" },
                  "subGoals": [
                    { "tempId": "sg-1", "title": "운동 습관", "emoji": "💪", "description": "주 3회 근력 + 유산소", "metric": null },
                    { "tempId": "sg-2", "title": "식단 관리", "emoji": "🥗", "description": "하루 1,800kcal 이하", "metric": null }
                  ],
                  "milestones": [
                    { "tempId": "ms-1", "title": "적응기", "description": "운동 습관 세팅", "dueDate": "$m1", "targetValue": 75.3 },
                    { "tempId": "ms-2", "title": "가속기", "description": null, "dueDate": "$m2", "targetValue": 72.7 },
                    { "tempId": "ms-3", "title": "목표 달성", "description": null, "dueDate": "$end", "targetValue": 70 }
                  ],
                  "tasks": [
                    { "tempId": "t-1", "type": "ROUTINE", "subGoalTempId": "sg-1", "milestoneTempId": null, "title": "하체 근력 40분", "emoji": "🏋️",
                      "durationMinutes": 40, "routineDays": ["MON","WED","FRI"], "preferredStartTime": "19:00", "startDate": "$start", "endDate": null,
                      "scheduledDate": null, "dueDate": null, "description": "스쿼트 4x10 → 런지 3x12", "reason": "초보라 주 3회로 시작해요" },
                    { "tempId": "t-2", "type": "ROUTINE", "subGoalTempId": "sg-2", "milestoneTempId": null, "title": "식단 기록", "emoji": "📝",
                      "durationMinutes": 10, "routineDays": ["MON","TUE","WED","THU","FRI","SAT","SUN"], "preferredStartTime": null, "startDate": null, "endDate": null,
                      "scheduledDate": null, "dueDate": null },
                    { "tempId": "t-3", "type": "ONE_TIME", "subGoalTempId": "sg-1", "milestoneTempId": "ms-1", "title": "인바디 측정", "emoji": "📏",
                      "durationMinutes": 30, "routineDays": null, "preferredStartTime": null, "startDate": null, "endDate": null,
                      "scheduledDate": "$t3s", "dueDate": "$t3d" }
                  ],
                  "warnings": [ { "ruleId": "S1", "path": "tasks", "message": "루틴 주간 합계가 조금 많아요." } ],
                  "fit": { "intensity": "STEADY", "weeklyCapMinutes": 180, "planWeeklyMinutes": 190, "basedOnHistory": true, "notes": ["최근 4주 동안 주 3회 운동했어요"] }
                }
                """
            )
        }

    val draft: GoalCreationDraftResponse
        get() = GoalCreationDraftResponse(
            draftId = 7, version = 1, status = GoalCreationDraftStatus.READY, planningStyle = PlanningStyle.PLANNER,
            progressStep = null, pollAfterMs = null, plan = plan, errorCode = null, message = null,
        )
}
