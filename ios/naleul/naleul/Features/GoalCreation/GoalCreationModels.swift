// Features/GoalCreation/GoalCreationModels.swift
//
// AI 목표 만들기 — 백엔드 응답/요청 모델 · 상수 · 화면 경로.
// 웹 원본: features/goal-creation/types.ts · constants.ts · routes.ts
// 백엔드 DTO: domain/goalCreation/dto/** (필드 타입은 여기서 확인했어요)
//
// 주의
//  - GoalSessionTurnResponse 는 @JsonInclude(ALWAYS) 라 message/summary 가 null 로 와요 → Optional 로 받아요.
//  - 초안(GoalPlan)은 "받은 그대로 고쳐서 다시 보내는" 구조라(확정 요청 바디), 모르는 값이 와도
//    바꾸지 않고 그대로 돌려보내도록 category · subType 같은 값은 String 으로, 세부 목표 수치는 JSONValue 로 둬요.
//  - 날짜는 "YYYY-MM-DD", 시각은 "HH:mm" 문자열 그대로예요.

import Foundation

// MARK: - 상태 enum

enum GoalCreationSessionStatus: String, Codable, FallbackDecodable, Hashable {
    case INTERVIEWING, READY_TO_GENERATE, GENERATING, DRAFT_READY, CONFIRMED, ABANDONED
    static let fallback: GoalCreationSessionStatus = .ABANDONED
}

enum GoalCreationSlotStatus: String, Codable, FallbackDecodable, Hashable {
    case EMPTY, FILLED, SKIPPED
    static let fallback: GoalCreationSlotStatus = .EMPTY
}

enum GoalCreationMessageRole: String, Codable, FallbackDecodable, Hashable {
    case USER, ASSISTANT
    static let fallback: GoalCreationMessageRole = .ASSISTANT
}

enum GoalCreationDraftStatus: String, Codable, FallbackDecodable, Hashable {
    case GENERATING, READY, FAILED
    static let fallback: GoalCreationDraftStatus = .FAILED
}

/// 계획 강도 — 안정형(기본)은 지금 실력 기준, 도전형은 조금 더 높게
enum GoalCreationPlanIntensity: String, Codable, FallbackDecodable, Hashable, CaseIterable {
    case STEADY, CHALLENGE
    static let fallback: GoalCreationPlanIntensity = .STEADY

    /// 웹 PLAN_INTENSITY_LABEL
    var label: String {
        switch self {
        case .STEADY: return "안정형"
        case .CHALLENGE: return "도전형"
        }
    }

    /// 웹 PLAN_INTENSITY_DESCRIPTION
    var descriptionText: String {
        switch self {
        case .STEADY: return "지금 실천량에 맞춰 작게 시작해 꾸준히 이어가요"
        case .CHALLENGE: return "조금 더 빡빡하게, 평소보다 한 단계 높여 잡아요"
        }
    }
}

/// 요일 "MON" … "SUN" (백엔드 Weekday)
enum GoalCreationWeekday: String, Codable, FallbackDecodable, Hashable, CaseIterable {
    case MON, TUE, WED, THU, FRI, SAT, SUN
    static let fallback: GoalCreationWeekday = .MON

    /// 웹 DAY_LABEL
    var label: String {
        switch self {
        case .MON: return "월"
        case .TUE: return "화"
        case .WED: return "수"
        case .THU: return "목"
        case .FRI: return "금"
        case .SAT: return "토"
        case .SUN: return "일"
        }
    }
}

/// 초안 Task 종류
enum DraftTaskType: String, Codable, FallbackDecodable, Hashable {
    case ROUTINE, ONE_TIME
    static let fallback: DraftTaskType = .ONE_TIME
}

/// 인터뷰 슬롯 이름 (웹 SlotKey)
/// V52: 이유(motivation)는 더 묻지 않고, 어떻게 이룰지(approach)를 물어요. motivation 은 예전 세션 호환용
enum GoalCreationSlotKey: String, Hashable, Identifiable, CaseIterable {
    case goalStatement, metric, motivation, deadline, approach, practicePreference
    var id: String { rawValue }

    /// 웹 SlotEditModal TITLES
    var editTitle: String {
        switch self {
        case .goalStatement: return "목표 수정"
        case .metric: return "수치 수정"
        case .motivation: return "이유 수정"
        case .deadline: return "기한 수정"
        case .approach: return "계획 수정"
        case .practicePreference: return "실천 시간 수정"
        }
    }
}

extension PlanningStyle {
    /// 웹 PLANNING_STYLE_LABEL
    var goalCreationLabel: String {
        switch self {
        case .PLANNER: return "차근차근 계획형"
        case .SPONTANEOUS: return "몰아서 하는 즉흥형"
        }
    }
}

// MARK: - 슬롯

struct GoalCreationSlot<T: Codable & Hashable>: Codable, Hashable {
    var status: GoalCreationSlotStatus
    var value: T?

    static var empty: GoalCreationSlot<T> { GoalCreationSlot(status: .EMPTY, value: nil) }
}

extension GoalCreationSlot {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        // DB 에 status 가 비어 있으면 EMPTY 로 봐요 (백엔드 Slot.isEmpty 와 같게)
        status = try c.decodeIfPresent(GoalCreationSlotStatus.self, forKey: .status) ?? .EMPTY
        value = try c.decodeIfPresent(T.self, forKey: .value)
    }
}

/// { "name": "체중", "currentValue": 78, "targetValue": 68, "unit": "kg" }
struct GoalCreationMetricValue: Codable, Hashable {
    var name: String?
    var currentValue: Double?
    var targetValue: Double?
    var unit: String?
}

struct GoalCreationDeadlineValue: Codable, Hashable {
    /// yyyy-MM-dd
    var endDate: String?
    var isFlexible: Bool?
    /// 사용자가 말한 시작일 (없으면 오늘부터) — 루틴 · Task 가 이 날부터 생겨요
    var startDate: String? = nil
}

struct GoalCreationTimeRange: Codable, Hashable {
    /// "HH:mm"
    var start: String
    var end: String
}

struct GoalCreationPracticePreference: Codable, Hashable {
    var preferredDays: [GoalCreationWeekday]?
    var preferredTimeRanges: [GoalCreationTimeRange]?
    var unavailableNote: String?
    var workStyleAnswer: String?
}

struct GoalCreationSlots: Codable, Hashable {
    var goalStatement: GoalCreationSlot<String>
    var metric: GoalCreationSlot<GoalCreationMetricValue>
    var motivation: GoalCreationSlot<String>
    var deadline: GoalCreationSlot<GoalCreationDeadlineValue>
    var practicePreference: GoalCreationSlot<GoalCreationPracticePreference>
    /// 어떻게 이루고 싶은지 · 정해 둔 계획 (V52, 예전 세션엔 없어요)
    var approach: GoalCreationSlot<String>

    static let empty = GoalCreationSlots(goalStatement: .empty, metric: .empty, motivation: .empty,
                                         deadline: .empty, practicePreference: .empty, approach: .empty)
}

extension GoalCreationSlots {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        goalStatement = try c.decodeIfPresent(GoalCreationSlot<String>.self, forKey: .goalStatement) ?? .empty
        metric = try c.decodeIfPresent(GoalCreationSlot<GoalCreationMetricValue>.self, forKey: .metric) ?? .empty
        motivation = try c.decodeIfPresent(GoalCreationSlot<String>.self, forKey: .motivation) ?? .empty
        deadline = try c.decodeIfPresent(GoalCreationSlot<GoalCreationDeadlineValue>.self, forKey: .deadline) ?? .empty
        practicePreference = try c.decodeIfPresent(GoalCreationSlot<GoalCreationPracticePreference>.self,
                                                   forKey: .practicePreference) ?? .empty
        approach = try c.decodeIfPresent(GoalCreationSlot<String>.self, forKey: .approach) ?? .empty
    }
}

/// 정리 확인 화면용 사람이 읽기 좋은 요약 (값이 없으면 nil)
struct GoalCreationSummary: Codable, Hashable {
    var goalStatement: String?
    var metricText: String?
    var motivation: String?
    var deadlineText: String?
    var preferenceText: String?
    var planningStyle: PlanningStyle?
    /// 어떻게 이루고 싶은지 · 정해 둔 계획 (V52)
    var approach: String?
}

// MARK: - 대화

struct GoalCreationMessage: Codable, Hashable {
    var messageId: Int
    var role: GoalCreationMessageRole
    var content: String?
    var targetSlot: String?
    var quickReplies: [String]?
    var skippable: Bool?
    var skip: Bool?
}

/// 세션 시작 / 답변 / 조기 종료 / 슬롯 수정 응답
struct GoalCreationTurnResponse: Codable, Hashable {
    var sessionId: Int
    var status: GoalCreationSessionStatus
    var questionCount: Int
    var maxQuestions: Int
    var canFinishEarly: Bool
    var message: GoalCreationMessage?
    var slots: GoalCreationSlots
    var summary: GoalCreationSummary?
    /// 이 목표를 위해 꼭 하고 싶은 일 — 계획에 반드시 들어가요
    var mustDoItems: [String]?
}

extension GoalCreationTurnResponse {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        sessionId = try c.decode(Int.self, forKey: .sessionId)
        status = try c.decode(GoalCreationSessionStatus.self, forKey: .status)
        questionCount = try c.decodeIfPresent(Int.self, forKey: .questionCount) ?? 0
        maxQuestions = try c.decodeIfPresent(Int.self, forKey: .maxQuestions) ?? 0
        canFinishEarly = try c.decodeIfPresent(Bool.self, forKey: .canFinishEarly) ?? false
        message = try c.decodeIfPresent(GoalCreationMessage.self, forKey: .message)
        slots = try c.decodeIfPresent(GoalCreationSlots.self, forKey: .slots) ?? .empty
        summary = try c.decodeIfPresent(GoalCreationSummary.self, forKey: .summary)
        mustDoItems = try c.decodeIfPresent([String].self, forKey: .mustDoItems)
    }
}

struct GoalCreationDraftRef: Codable, Hashable {
    var draftId: Int
    var version: Int
    var status: GoalCreationDraftStatus
}

/// 세션 조회 (이어하기)
struct GoalCreationSessionDetail: Codable, Hashable {
    var sessionId: Int
    var status: GoalCreationSessionStatus
    var questionCount: Int
    var maxQuestions: Int
    var canFinishEarly: Bool
    var messages: [GoalCreationMessage]
    var slots: GoalCreationSlots
    var summary: GoalCreationSummary?
    var latestDraft: GoalCreationDraftRef?
    var mustDoItems: [String]?
}

extension GoalCreationSessionDetail {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        sessionId = try c.decode(Int.self, forKey: .sessionId)
        status = try c.decode(GoalCreationSessionStatus.self, forKey: .status)
        questionCount = try c.decodeIfPresent(Int.self, forKey: .questionCount) ?? 0
        maxQuestions = try c.decodeIfPresent(Int.self, forKey: .maxQuestions) ?? 0
        canFinishEarly = try c.decodeIfPresent(Bool.self, forKey: .canFinishEarly) ?? false
        messages = try c.decodeIfPresent([GoalCreationMessage].self, forKey: .messages) ?? []
        slots = try c.decodeIfPresent(GoalCreationSlots.self, forKey: .slots) ?? .empty
        summary = try c.decodeIfPresent(GoalCreationSummary.self, forKey: .summary)
        latestDraft = try c.decodeIfPresent(GoalCreationDraftRef.self, forKey: .latestDraft)
        mustDoItems = try c.decodeIfPresent([String].self, forKey: .mustDoItems)
    }
}

/// PATCH /slots — 바꾼 것만 보내요 (nil 은 JSON 에서 빠져요 = 안 바꿈)
struct GoalCreationSlotsPatch: Encodable {
    var goalStatement: String?
    var metric: GoalCreationMetricValue?
    var motivation: String?
    var approach: String?
    var deadline: GoalCreationDeadlineValue?
    var practicePreference: GoalCreationPracticePreference?
    /// 빈 목록이면 지워요
    var mustDoItems: [String]?
}

// MARK: - 초안 (GoalPlan) — 초안 응답 = 확정 요청 공통 구조

struct DraftPlanGoalMetric: Codable, Hashable {
    var name: String
    var unit: String?
    var startValue: Double
    var targetValue: Double
}

extension DraftPlanGoalMetric {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        unit = try c.decodeIfPresent(String.self, forKey: .unit)
        startValue = try c.decodeIfPresent(Double.self, forKey: .startValue) ?? 0
        targetValue = try c.decodeIfPresent(Double.self, forKey: .targetValue) ?? 0
    }
}

struct DraftPlanGoal: Codable, Hashable {
    var title: String
    var emoji: String?
    /// HEALTH · STUDY … (GoalTopic) — 받은 값 그대로 돌려보내요
    var category: String?
    var description: String?
    var metric: DraftPlanGoalMetric?
    var startDate: String
    var endDate: String
    var planningStyle: PlanningStyle
    var aiNote: String?
    /// 목표 카테고리 2단계 (GoalSubType 이름)
    var subType: String?
}

extension DraftPlanGoal {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        emoji = try c.decodeIfPresent(String.self, forKey: .emoji)
        category = try c.decodeIfPresent(String.self, forKey: .category)
        description = try c.decodeIfPresent(String.self, forKey: .description)
        metric = try c.decodeIfPresent(DraftPlanGoalMetric.self, forKey: .metric)
        startDate = try c.decodeIfPresent(String.self, forKey: .startDate) ?? KST.today()
        endDate = try c.decodeIfPresent(String.self, forKey: .endDate) ?? KST.today()
        planningStyle = try c.decodeIfPresent(PlanningStyle.self, forKey: .planningStyle) ?? .PLANNER
        aiNote = try c.decodeIfPresent(String.self, forKey: .aiNote)
        subType = try c.decodeIfPresent(String.self, forKey: .subType)
    }
}

struct DraftPlanSubGoal: Codable, Hashable {
    var tempId: String
    var title: String
    var emoji: String?
    var description: String?
    /// 화면에선 안 쓰고 그대로 돌려보내요 ({ name, unit, comparator, targetValue, period })
    var metric: JSONValue?
}

struct DraftPlanMilestone: Codable, Hashable {
    var tempId: String
    var title: String
    var description: String?
    var dueDate: String
    var targetValue: Double?
}

struct DraftPlanTask: Codable, Hashable {
    var tempId: String
    var type: DraftTaskType
    var subGoalTempId: String?
    var milestoneTempId: String?
    var title: String
    var emoji: String?
    var durationMinutes: Int
    var routineDays: [GoalCreationWeekday]?
    /// "HH:mm"
    var preferredStartTime: String?
    var startDate: String?
    var endDate: String?
    var scheduledDate: String?
    var dueDate: String?
    /// 루틴의 구체적인 방법 (예: "스쿼트 4x10, 런지 3x12…")
    var description: String?
    /// AI 가 이렇게 정한 이유 — 검토 화면에서만 보여요
    var reason: String?

    var isRoutine: Bool { type == .ROUTINE }
}

extension DraftPlanTask {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        tempId = try c.decode(String.self, forKey: .tempId)
        type = try c.decodeIfPresent(DraftTaskType.self, forKey: .type) ?? .ONE_TIME
        subGoalTempId = try c.decodeIfPresent(String.self, forKey: .subGoalTempId)
        milestoneTempId = try c.decodeIfPresent(String.self, forKey: .milestoneTempId)
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        emoji = try c.decodeIfPresent(String.self, forKey: .emoji)
        durationMinutes = try c.decodeIfPresent(Int.self, forKey: .durationMinutes) ?? 0
        routineDays = try c.decodeIfPresent([GoalCreationWeekday].self, forKey: .routineDays)
        // 혹시 "07:00:00" 처럼 오더라도 "HH:mm" 으로 맞춰요
        preferredStartTime = try c.decodeIfPresent(String.self, forKey: .preferredStartTime).map { String($0.prefix(5)) }
        startDate = try c.decodeIfPresent(String.self, forKey: .startDate)
        endDate = try c.decodeIfPresent(String.self, forKey: .endDate)
        scheduledDate = try c.decodeIfPresent(String.self, forKey: .scheduledDate)
        dueDate = try c.decodeIfPresent(String.self, forKey: .dueDate)
        description = try c.decodeIfPresent(String.self, forKey: .description)
        reason = try c.decodeIfPresent(String.self, forKey: .reason)
    }
}

/// 검증 규칙 위반 1건 (서버 violations / warnings 와 같은 모양)
struct DraftPlanIssue: Codable, Hashable {
    var ruleId: String
    var path: String
    var message: String
}

/// "나에게 맞춘 점"
struct DraftPlanFit: Codable, Hashable {
    var intensity: GoalCreationPlanIntensity
    /// 이 사용자 기준 루틴 주간 상한 (분)
    var weeklyCapMinutes: Int
    /// 이 계획의 루틴 주간 합계 (분)
    var planWeeklyMinutes: Int
    /// 최근 4주 실행 기록을 바탕으로 했는지
    var basedOnHistory: Bool
    var notes: [String]
}

extension DraftPlanFit {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        intensity = try c.decodeIfPresent(GoalCreationPlanIntensity.self, forKey: .intensity) ?? .STEADY
        weeklyCapMinutes = try c.decodeIfPresent(Int.self, forKey: .weeklyCapMinutes) ?? 0
        planWeeklyMinutes = try c.decodeIfPresent(Int.self, forKey: .planWeeklyMinutes) ?? 0
        basedOnHistory = try c.decodeIfPresent(Bool.self, forKey: .basedOnHistory) ?? false
        notes = try c.decodeIfPresent([String].self, forKey: .notes) ?? []
    }
}

struct DraftGoalPlan: Codable, Hashable {
    var goal: DraftPlanGoal
    var subGoals: [DraftPlanSubGoal]
    var milestones: [DraftPlanMilestone]
    var tasks: [DraftPlanTask]
    var warnings: [DraftPlanIssue]
    /// 이 계획이 나에게 어떻게 맞춰졌는지 (기존 초안엔 없을 수 있어요)
    var fit: DraftPlanFit?
}

extension DraftGoalPlan {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        goal = try c.decode(DraftPlanGoal.self, forKey: .goal)
        subGoals = try c.decodeIfPresent([DraftPlanSubGoal].self, forKey: .subGoals) ?? []
        milestones = try c.decodeIfPresent([DraftPlanMilestone].self, forKey: .milestones) ?? []
        tasks = try c.decodeIfPresent([DraftPlanTask].self, forKey: .tasks) ?? []
        warnings = try c.decodeIfPresent([DraftPlanIssue].self, forKey: .warnings) ?? []
        fit = try c.decodeIfPresent(DraftPlanFit.self, forKey: .fit)
    }
}

// MARK: - 초안 생성 / 폴링

/// POST /drafts → 202
struct GoalCreationDraftRequested: Codable, Hashable {
    var draftId: Int
    var version: Int
    var status: GoalCreationDraftStatus
    var pollAfterMs: Int?
}

/// GET /drafts/{draftId}
struct GoalCreationDraftResponse: Codable, Hashable {
    var draftId: Int
    var version: Int
    var status: GoalCreationDraftStatus
    var planningStyle: PlanningStyle?
    /// ANALYZING · SUB_GOALS · MILESTONES · TASKS · VALIDATING
    var progressStep: String?
    var pollAfterMs: Int?
    var plan: DraftGoalPlan?
    var errorCode: String?
    var message: String?
}

/// POST /drafts 바디 (모두 선택)
struct GoalCreationDraftRequestBody: Encodable {
    var feedback: String?
    var planningStyle: PlanningStyle?
    var intensity: GoalCreationPlanIntensity?
    /// "말로 고치기" — 화면에서 직접 고친 지금 초안
    var basePlan: DraftGoalPlan?
}

/// POST /confirm 바디
struct GoalCreationConfirmRequest: Encodable {
    var draftId: Int
    var plan: DraftGoalPlan
    var planningStyleSelectedByUser: Bool
    /// 선택한 UserColor id. nil 이면 서버가 자동 배정
    var colorId: Int?
}

struct GoalCreationConfirmResponse: Codable, Hashable {
    var goalId: Int
}

// MARK: - 상수 (웹 constants.ts)

enum GoalCreationConst {
    /// 시작 화면의 예시 목표 칩
    static let exampleGoals = [
        "몸무게 10kg 감량하기",
        "토익 900점 받기",
        "올해 책 12권 읽기",
        "매일 아침 운동하는 습관 만들기",
    ]

    /// 닫기 확인창 문구
    static let leaveNotice = "진행 중인 목표는 7일 동안 이어서 만들 수 있어요."

    /// 생성 단계 문구
    static let progressSteps: [(step: String, label: String)] = [
        ("ANALYZING", "목표를 분석하고 있어요"),
        ("SUB_GOALS", "세부 목표를 나누고 있어요"),
        ("MILESTONES", "마일스톤을 정하고 있어요"),
        ("TASKS", "꼭 필요한 루틴만 짜고 있어요"),
        ("VALIDATING", "계획을 점검하고 있어요"),
    ]

    /// 폴링 간격 / 최대 대기 (명세: 2초, 90초)
    static let pollIntervalMs = 2000
    static let pollTimeoutMs = 90_000
}

// MARK: - 화면 경로 (웹 routes.ts)

/// 목표 만들기 플로우 안의 화면. 웹 URL 대신 이 값으로 화면을 바꿔요
enum GoalCreationRoute: Hashable {
    /// /goal/new
    case start
    /// 이어서 하기 — 세션을 불러와 상태에 맞는 화면으로
    case resume(Int)
    /// /goal/new/{id}
    case chat(Int)
    /// /goal/new/{id}/review
    case review(Int)
    /// /goal/new/{id}/generating?draftId=
    case generating(Int, draftId: Int?)
    /// /goal/new/{id}/draft
    case draft(Int)

    /// 웹 pathForStatus — CONFIRMED / ABANDONED 는 이어갈 화면이 없어요
    static func forStatus(_ id: Int, _ status: GoalCreationSessionStatus) -> GoalCreationRoute? {
        switch status {
        case .INTERVIEWING: return .chat(id)
        case .READY_TO_GENERATE: return .review(id)
        case .GENERATING: return .generating(id, draftId: nil)
        case .DRAFT_READY: return .draft(id)
        case .CONFIRMED, .ABANDONED: return nil
        }
    }
}

// MARK: - 미리보기용 샘플

enum GoalCreationSamples {
    private static func decode<T: Decodable>(_ json: String) -> T {
        try! JSONDecoder().decode(T.self, from: Data(json.utf8))
    }

    static let slotsJSON = """
    {
      "goalStatement": { "status": "FILLED", "value": "3개월에 8kg 빼기" },
      "metric": { "status": "FILLED", "value": { "name": "체중", "currentValue": 78, "targetValue": 70, "unit": "kg" } },
      "motivation": { "status": "FILLED", "value": "건강검진에서 지방간이 나와서" },
      "deadline": { "status": "FILLED", "value": { "endDate": "2027-01-31", "isFlexible": false } },
      "practicePreference": { "status": "FILLED", "value": { "preferredDays": ["MON","WED","FRI"], "preferredTimeRanges": [{"start":"19:00","end":"21:00"}], "unavailableNote": "평일 9~18시 회사", "workStyleAnswer": null } }
    }
    """

    static var session: GoalCreationSessionDetail {
        decode("""
        {
          "sessionId": 101, "status": "INTERVIEWING", "questionCount": 2, "maxQuestions": 5, "canFinishEarly": true,
          "messages": [
            { "messageId": 1, "role": "ASSISTANT", "content": "좋아요! 지금 몸무게와 목표 몸무게를 알려주세요.", "targetSlot": "metric", "quickReplies": [], "skippable": true, "skip": false },
            { "messageId": 2, "role": "USER", "content": "78kg인데 70kg까지 빼고 싶어요", "skippable": false, "skip": false },
            { "messageId": 3, "role": "ASSISTANT", "content": "왜 이 목표를 이루고 싶으세요?", "targetSlot": "motivation", "quickReplies": ["건강 때문에", "자신감을 위해", "옷 핏이 좋아지게"], "skippable": true, "skip": false }
          ],
          "slots": \(slotsJSON),
          "summary": { "goalStatement": "3개월에 8kg 빼기", "metricText": "체중 78kg → 70kg", "motivation": "건강검진에서 지방간이 나와서", "deadlineText": "2027년 1월 31일까지", "preferenceText": "월·수·금 19:00~21:00", "planningStyle": "PLANNER" },
          "latestDraft": { "draftId": 7, "version": 1, "status": "READY" },
          "mustDoItems": ["헬스", "식단 기록"]
        }
        """)
    }

    static var plan: DraftGoalPlan {
        let start = KST.addDays(KST.today(), 1)
        let m1 = KST.addDays(start, 30)
        let m2 = KST.addDays(start, 60)
        let end = KST.addDays(start, 90)
        return decode("""
        {
          "goal": { "title": "3개월에 8kg 감량", "emoji": "🏃", "category": "HEALTH", "description": null,
                    "metric": { "name": "체중", "unit": "kg", "startValue": 78, "targetValue": 70 },
                    "startDate": "\(start)", "endDate": "\(end)", "planningStyle": "PLANNER",
                    "aiNote": "처음 2주는 습관을 만드는 데 집중해요.", "subType": "DIET" },
          "subGoals": [
            { "tempId": "sg-1", "title": "운동 습관", "emoji": "💪", "description": "주 3회 근력 + 유산소", "metric": null },
            { "tempId": "sg-2", "title": "식단 관리", "emoji": "🥗", "description": "하루 1,800kcal 이하", "metric": null }
          ],
          "milestones": [
            { "tempId": "ms-1", "title": "적응기", "description": "운동 습관 세팅", "dueDate": "\(m1)", "targetValue": 75.3 },
            { "tempId": "ms-2", "title": "가속기", "description": null, "dueDate": "\(m2)", "targetValue": 72.7 },
            { "tempId": "ms-3", "title": "목표 달성", "description": null, "dueDate": "\(end)", "targetValue": 70 }
          ],
          "tasks": [
            { "tempId": "t-1", "type": "ROUTINE", "subGoalTempId": "sg-1", "milestoneTempId": null, "title": "하체 근력 40분", "emoji": "🏋️",
              "durationMinutes": 40, "routineDays": ["MON","WED","FRI"], "preferredStartTime": "19:00", "startDate": "\(start)", "endDate": null,
              "scheduledDate": null, "dueDate": null, "description": "스쿼트 4x10 → 런지 3x12", "reason": "초보라 주 3회로 시작해요" },
            { "tempId": "t-2", "type": "ROUTINE", "subGoalTempId": "sg-2", "milestoneTempId": null, "title": "식단 기록", "emoji": "📝",
              "durationMinutes": 10, "routineDays": ["MON","TUE","WED","THU","FRI","SAT","SUN"], "preferredStartTime": null, "startDate": null, "endDate": null,
              "scheduledDate": null, "dueDate": null },
            { "tempId": "t-3", "type": "ONE_TIME", "subGoalTempId": "sg-1", "milestoneTempId": "ms-1", "title": "인바디 측정", "emoji": "📏",
              "durationMinutes": 30, "routineDays": null, "preferredStartTime": null, "startDate": null, "endDate": null,
              "scheduledDate": "\(KST.addDays(start, 3))", "dueDate": "\(KST.addDays(start, 7))" }
          ],
          "warnings": [ { "ruleId": "S1", "path": "tasks", "message": "루틴 주간 합계가 조금 많아요." } ],
          "fit": { "intensity": "STEADY", "weeklyCapMinutes": 180, "planWeeklyMinutes": 190, "basedOnHistory": true, "notes": ["최근 4주 동안 주 3회 운동했어요"] }
        }
        """)
    }

    static var draft: GoalCreationDraftResponse {
        GoalCreationDraftResponse(draftId: 7, version: 1, status: .READY, planningStyle: .PLANNER, progressStep: nil,
                                  pollAfterMs: nil, plan: plan, errorCode: nil, message: nil)
    }
}
