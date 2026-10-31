// Models/TimetableModels.swift
//
// 웹 features/timetable/types.ts 를 그대로 옮긴 TimeTable 응답 모델 (백엔드 DTO 와 1:1).
//
// ⚠️ 날짜/시간 문자열은 모두 "한국 시간 벽시계" 값이에요. (Z, +09:00 없음)
//   LocalDate → "2026-11-18" · LocalDateTime → "2026-11-18T09:30:00" · LocalTime → "09:30:00"
//   Date 로 바꾸지 말고 문자열 그대로 KST.* 함수로 계산해요.
//
// 웹에서 `?` (optional) 인 필드는 전부 Swift Optional 이에요. 백엔드가 null 을 생략해서 내려주기 때문이에요.

import Foundation

enum LifePatternType: String, Codable, FallbackDecodable, Hashable {
    case SLEEP, LUNCH, DINNER, CUSTOM
    static let fallback: LifePatternType = .CUSTOM
}

enum TaskStatus: String, Codable, FallbackDecodable, Hashable {
    case TODO, COMPLETED, SKIPPED, DELETED
    static let fallback: TaskStatus = .TODO
}

enum TaskPriority: String, Codable, FallbackDecodable, Hashable, CaseIterable {
    case A, B, C, D, E
    /// 백엔드 기본값도 C (루틴 · 목표 · 미션에서 자동으로 생긴 Task 포함, 2026-10)
    static let fallback: TaskPriority = .C

    /// 웹 features/timetable/ui/Priority.tsx PRIORITY_LABEL
    var label: String {
        switch self {
        case .A: return "가장 중요"
        case .B: return "중요"
        case .C: return "보통"
        case .D: return "가벼움"
        case .E: return "여유"
        }
    }

    /// TimeBlock 에서 배지 + 테두리로 강조하는 우선순위
    var isTop: Bool { self == .A }
}

enum TaskKind: String, Codable, FallbackDecodable, Hashable {
    case NORMAL, APPOINTMENT
    static let fallback: TaskKind = .NORMAL
}

enum TaskSourceType: String, Codable, FallbackDecodable, Hashable {
    case MANUAL, ROUTINE, MISSION
    static let fallback: TaskSourceType = .MANUAL
}

enum PlacedBy: String, Codable, FallbackDecodable, Hashable {
    case USER, ENGINE
    static let fallback: PlacedBy = .USER
}

enum DailyPlanStatus: String, Codable, FallbackDecodable, Hashable {
    case GENERATING, GENERATED, REVIEWED, FAILED
    static let fallback: DailyPlanStatus = .GENERATED
}

enum DailyPlanSource: String, Codable, FallbackDecodable, Hashable {
    case NIGHTLY, BRAIN_DUMP, REPLAN, GOAL_CREATED, unknown
    static let fallback: DailyPlanSource = .unknown
}

/// TimeTable 의 Task 블록 하나 (목표 상세의 Task 목록에서도 같은 모양을 써요)
struct TimeBlockTask: Codable, Hashable, Identifiable {
    var id: Int { taskId }

    var taskId: Int
    var taskName: String
    var emoji: String?
    var taskStatus: TaskStatus
    var taskPriority: TaskPriority?
    var taskKind: TaskKind?
    var sourceType: TaskSourceType
    var source: String?
    var goalCategoryId: Int?
    var goalCategoryName: String?
    var goalEmoji: String?
    var goalColorCode: String?
    var goalTemporary: Bool
    var generalCategoryId: Int?
    var generalCategoryName: String?
    var milestoneId: Int?
    var milestoneTitle: String?
    /// 업무형 목표의 세부 목표 (로드맵 · WorkTopic) — 없으면 "세부 목표 없음"
    var workTopicId: Int? = nil
    var workTopicName: String? = nil
    var routineId: Int?
    var date: String?
    var plannedStartAt: String?
    var plannedEndAt: String?
    var plannedDurationMinutes: Int?
    var scheduledDate: String?
    var dueDate: String?
    var locked: Bool
    var placedBy: PlacedBy?
    var placementReason: String?
    var placementReasonCodes: [String]?
    var carryOverCount: Int
    var completedAt: String?
    /// 완료한 Task 의 실제 수행 시각 (완료 전이거나 실제 시각 기록이 없으면 nil)
    var actualStartAt: String?
    var actualEndAt: String?
    var missed: Bool
    /// 22시 이월로 놓였지만 아직 확인 안 함 → 점선 블록 + "옮길까요?"
    var carryPending: Bool?
    /// 이월되기 전 날짜
    var carriedFromDate: String?
    /// (화면 전용) "AI로 배치하기" 미리보기 제안 — 아직 저장 안 된 점선 블록
    var proposed: Bool?
    /// 여러 번 나눠서 했을 때만 각 구간 (예: 13~15시, 16~18시). 한 번에 했으면 nil/빈 배열
    var actualSegments: [ActualSegment]? = nil
    /// 자동 배치가 연속된 빈 시간이 없어 나눠 넣었을 때만 계획 구간 (예: 09~12시, 13~14시 — 점심 앞뒤).
    /// plannedStartAt ~ plannedEndAt 은 첫 시작 ~ 마지막 끝, plannedDurationMinutes 는 구간 합계예요
    var plannedSegments: [ActualSegment]? = nil

    var isCompleted: Bool { taskStatus == .COMPLETED }

    enum CodingKeys: String, CodingKey {
        case taskId, taskName, emoji, taskStatus, taskPriority, taskKind, sourceType, source
        case goalCategoryId, goalCategoryName, goalEmoji, goalColorCode, goalTemporary
        case generalCategoryId, generalCategoryName, milestoneId, milestoneTitle, routineId, workTopicId, workTopicName
        case date, plannedStartAt, plannedEndAt, plannedDurationMinutes, scheduledDate, dueDate
        case locked, placedBy, placementReason, placementReasonCodes, carryOverCount, completedAt
        case actualStartAt, actualEndAt, missed, carryPending, carriedFromDate, proposed, actualSegments, plannedSegments
    }

}

/// 실제로 한 구간 하나 (나눠서 한 경우)
struct ActualSegment: Codable, Hashable {
    var start: String
    var end: String
}

/// 시간대를 정한 기본 생활 습관 (예: 샤워 20:00~20:30) — 캘린더에 체크할 수 있는 블록
struct HabitBlock: Codable, Hashable {
    var habitId: Int
    var name: String
    var emoji: String?
    var start: String
    var end: String
    var checked: Bool
}

struct Covered: Codable, Hashable {
    var taskId: Int
    var start: String
    var end: String
}

extension TimeBlockTask {
    // 백엔드가 일부 필드를 생략해도 죽지 않게 기본값을 채워요 (extension 에 둬야 memberwise init 이 남아요)
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        taskId = try c.decode(Int.self, forKey: .taskId)
        taskName = try c.decodeIfPresent(String.self, forKey: .taskName) ?? ""
        emoji = try c.decodeIfPresent(String.self, forKey: .emoji)
        taskStatus = try c.decodeIfPresent(TaskStatus.self, forKey: .taskStatus) ?? .TODO
        taskPriority = try c.decodeIfPresent(TaskPriority.self, forKey: .taskPriority)
        taskKind = try c.decodeIfPresent(TaskKind.self, forKey: .taskKind)
        sourceType = try c.decodeIfPresent(TaskSourceType.self, forKey: .sourceType) ?? .MANUAL
        source = try c.decodeIfPresent(String.self, forKey: .source)
        goalCategoryId = try c.decodeIfPresent(Int.self, forKey: .goalCategoryId)
        goalCategoryName = try c.decodeIfPresent(String.self, forKey: .goalCategoryName)
        goalEmoji = try c.decodeIfPresent(String.self, forKey: .goalEmoji)
        goalColorCode = try c.decodeIfPresent(String.self, forKey: .goalColorCode)
        goalTemporary = try c.decodeIfPresent(Bool.self, forKey: .goalTemporary) ?? false
        generalCategoryId = try c.decodeIfPresent(Int.self, forKey: .generalCategoryId)
        generalCategoryName = try c.decodeIfPresent(String.self, forKey: .generalCategoryName)
        milestoneId = try c.decodeIfPresent(Int.self, forKey: .milestoneId)
        milestoneTitle = try c.decodeIfPresent(String.self, forKey: .milestoneTitle)
        workTopicId = try c.decodeIfPresent(Int.self, forKey: .workTopicId)
        workTopicName = try c.decodeIfPresent(String.self, forKey: .workTopicName)
        routineId = try c.decodeIfPresent(Int.self, forKey: .routineId)
        date = try c.decodeIfPresent(String.self, forKey: .date)
        plannedStartAt = try c.decodeIfPresent(String.self, forKey: .plannedStartAt)
        plannedEndAt = try c.decodeIfPresent(String.self, forKey: .plannedEndAt)
        plannedDurationMinutes = try c.decodeIfPresent(Int.self, forKey: .plannedDurationMinutes)
        scheduledDate = try c.decodeIfPresent(String.self, forKey: .scheduledDate)
        dueDate = try c.decodeIfPresent(String.self, forKey: .dueDate)
        locked = try c.decodeIfPresent(Bool.self, forKey: .locked) ?? false
        placedBy = try c.decodeIfPresent(PlacedBy.self, forKey: .placedBy)
        placementReason = try c.decodeIfPresent(String.self, forKey: .placementReason)
        placementReasonCodes = try c.decodeIfPresent([String].self, forKey: .placementReasonCodes)
        carryOverCount = try c.decodeIfPresent(Int.self, forKey: .carryOverCount) ?? 0
        completedAt = try c.decodeIfPresent(String.self, forKey: .completedAt)
        actualStartAt = try c.decodeIfPresent(String.self, forKey: .actualStartAt)
        actualEndAt = try c.decodeIfPresent(String.self, forKey: .actualEndAt)
        missed = try c.decodeIfPresent(Bool.self, forKey: .missed) ?? false
        carryPending = try c.decodeIfPresent(Bool.self, forKey: .carryPending)
        carriedFromDate = try c.decodeIfPresent(String.self, forKey: .carriedFromDate)
        proposed = try c.decodeIfPresent(Bool.self, forKey: .proposed)
        actualSegments = try c.decodeIfPresent([ActualSegment].self, forKey: .actualSegments)
        plannedSegments = try c.decodeIfPresent([ActualSegment].self, forKey: .plannedSegments)
    }
}

/// 고정 시간 블록 (수면 · 점심 · 저녁 · 직접 만든 생활 패턴)
struct FixedBlock: Codable, Hashable {
    var lifePatternId: Int
    var patternType: LifePatternType
    var title: String
    var emoji: String?
    /// 그날만 변경 API 에 넘길 날짜 (자정을 넘긴 수면의 다음 날 부분이어도 전날)
    var targetDate: String
    var start: String
    var end: String
    var overridden: Bool
    var clippedFromPreviousDay: Bool
    var coveredBy: [Covered]?

    enum CodingKeys: String, CodingKey {
        case lifePatternId, patternType, title, emoji, targetDate, start, end, overridden, clippedFromPreviousDay, coveredBy
    }


    /// 화면에서 블록을 구별하는 키 (웹 fixedKey 와 같은 규칙: 패턴 id + 그날 + 시작)
    var key: String { "\(lifePatternId)-\(targetDate)-\(start)" }
}

extension FixedBlock {
    // 백엔드가 일부 필드를 생략해도 죽지 않게 기본값을 채워요 (extension 에 둬야 memberwise init 이 남아요)
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        lifePatternId = try c.decode(Int.self, forKey: .lifePatternId)
        patternType = try c.decodeIfPresent(LifePatternType.self, forKey: .patternType) ?? .CUSTOM
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        emoji = try c.decodeIfPresent(String.self, forKey: .emoji)
        targetDate = try c.decode(String.self, forKey: .targetDate)
        start = try c.decode(String.self, forKey: .start)
        end = try c.decode(String.self, forKey: .end)
        overridden = try c.decodeIfPresent(Bool.self, forKey: .overridden) ?? false
        clippedFromPreviousDay = try c.decodeIfPresent(Bool.self, forKey: .clippedFromPreviousDay) ?? false
        coveredBy = try c.decodeIfPresent([Covered].self, forKey: .coveredBy)
    }
}

struct DayPlan: Codable, Hashable {
    var status: DailyPlanStatus
    var source: DailyPlanSource
    var summary: String?
    var needsReview: Bool
    var generatedAt: String?
}

struct DayStats: Codable, Hashable {
    var total: Int
    var completed: Int
    var missed: Int
    /// 완료/전체 × 100. Task 가 없으면 nil
    var rate: Int?
    /// 이날 못 해서 다음 날로 넘어간 Task 수
    var carriedOver: Int?
}

struct DayRange: Codable, Hashable {
    var start: String
    var end: String
}

struct SleepEdges: Codable, Hashable {
    /// 아침에 끝나는 수면 (끝 = 기상)
    var wake: FixedBlock?
    /// 밤에 시작하는 수면 (시작 = 취침)
    var bed: FixedBlock?
}

struct TimetableDay: Codable, Hashable, Identifiable {
    var id: String { date }

    var date: String
    var dayRange: DayRange
    var fixedBlocks: [FixedBlock]
    var tasks: [TimeBlockTask]
    var unscheduledTasks: [TimeBlockTask]
    var plan: DayPlan?
    var stats: DayStats
    /// 하루 범위를 정한 수면 블록 (자르지 않은 원본 구간). 수면 패턴이 없으면 nil
    var sleep: SleepEdges?
    /// 시간대를 정한 기본 생활 습관
    var habits: [HabitBlock]? = nil
    /// 이날 못 해서 다른 날로 옮겨진 Task 의 흔적 (2026-10) — 원래 자리에 흐린 점선 블록 "* 이름"으로 남겨요
    var carriedOut: [CarriedOutBlock] = []

    enum CodingKeys: String, CodingKey {
        case date, dayRange, fixedBlocks, tasks, unscheduledTasks, plan, stats, sleep, habits, carriedOut
    }

}

struct TimetableResponse: Codable, Hashable {
    var days: [TimetableDay]
}

extension TimetableDay {
    // 백엔드가 일부 필드를 생략해도 죽지 않게 기본값을 채워요 (extension 에 둬야 memberwise init 이 남아요)
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        // ?? 오른쪽은 클로저(autoclosure)라서, 아직 다 초기화되지 않은 self.date 를 쓰면 컴파일 에러예요 → 지역 상수로
        let d = try c.decode(String.self, forKey: .date)
        date = d
        dayRange = try c.decodeIfPresent(DayRange.self, forKey: .dayRange)
            ?? DayRange(start: "\(d)T00:00:00", end: "\(KST.addDays(d, 1))T00:00:00")
        fixedBlocks = try c.decodeIfPresent([FixedBlock].self, forKey: .fixedBlocks) ?? []
        tasks = try c.decodeIfPresent([TimeBlockTask].self, forKey: .tasks) ?? []
        unscheduledTasks = try c.decodeIfPresent([TimeBlockTask].self, forKey: .unscheduledTasks) ?? []
        plan = try c.decodeIfPresent(DayPlan.self, forKey: .plan)
        stats = try c.decodeIfPresent(DayStats.self, forKey: .stats) ?? DayStats(total: 0, completed: 0, missed: 0, rate: nil, carriedOver: nil)
        sleep = try c.decodeIfPresent(SleepEdges.self, forKey: .sleep)
        habits = try c.decodeIfPresent([HabitBlock].self, forKey: .habits)
        carriedOut = try c.decodeIfPresent([CarriedOutBlock].self, forKey: .carriedOut) ?? []
    }
}

/// 다른 날로 옮겨진 Task 의 흔적 — 옮겨진 Task 는 toDate 에 진짜 블록으로 있어요
struct CarriedOutBlock: Codable, Hashable, Identifiable {
    var id: String { "\(taskId)-\(start)" }

    var taskId: Int
    var taskName: String
    var emoji: String?
    var goalColorCode: String?
    /// 원래 있던 자리 "yyyy-MM-ddTHH:mm:ss"
    var start: String
    var end: String
    /// 옮겨 간 날 "yyyy-MM-dd"
    var toDate: String
}

struct MovedBlock: Codable, Hashable {
    var taskId: Int
    var fromStart: String
    var toStart: String
    var toEnd: String
    var reason: String?
}

struct CoveredFixedBlock: Codable, Hashable {
    var lifePatternId: Int
    var title: String
}

struct TaskScheduleResponse: Codable, Hashable {
    var task: TimeBlockTask?
    var moved: [MovedBlock]?
    var unscheduled: [Int]?
    var coveredFixedBlocks: [CoveredFixedBlock]?
}

/// 여러 Task 를 한 번에 옮긴 결과 (묶음 드래그)
struct TaskBatchScheduleResponse: Codable, Hashable {
    var tasks: [TimeBlockTask]
    var moved: [MovedBlock]?
    var unscheduled: [Int]?
    var coveredFixedBlocks: [CoveredFixedBlock]?
}

/// 고정 시간 요일별 시간. startDayOffset: 블록이 그 요일 하루 앞(-1)·뒤(+1)에 시작
struct LifePatternDayTime: Codable, Hashable {
    var day: JavaDayOfWeek
    var startTime: String
    var endTime: String
    var startDayOffset: Int
}

/// 기본 생활 패턴 (설정 > 생활 패턴)
struct LifePattern: Codable, Hashable, Identifiable {
    var id: Int { lifePatternId }

    var lifePatternId: Int
    var patternType: LifePatternType
    var title: String
    var emoji: String?
    var startTime: String
    var endTime: String
    var days: [JavaDayOfWeek]
    var everyDay: Bool
    var crossesMidnight: Bool
    var sortOrder: Int
    /// 요일별로 다른 시간 (기본 시간과 다른 요일만)
    var dayTimes: [LifePatternDayTime]?
}

// MARK: - 월간 (GET /timetable/monthly)

struct MonthlyChip: Codable, Hashable {
    var taskId: Int
    var title: String
    var colorCode: String?
    var completed: Bool
    var temporaryGoal: Bool
}

struct MonthlyMilestoneChip: Codable, Hashable {
    var milestoneId: Int
    var title: String
    var colorCode: String?
    var goalCategoryId: Int
}

struct MonthlyDay: Codable, Hashable, Identifiable {
    var id: String { date }
    var date: String
    /// 실행률 (Task 없으면 nil)
    var rate: Int?
    var taskCount: Int
    var completedCount: Int
    /// 루틴이 아닌 Task 최대 3개
    var chips: [MonthlyChip]
    var moreCount: Int
    var milestones: [MonthlyMilestoneChip]
}

struct MonthlyDeadline: Codable, Hashable {
    /// "TASK" | "MILESTONE"
    var type: String
    var id: Int
    var title: String
    var date: String
    var colorCode: String?
}

struct MonthlyTimetableResponse: Codable, Hashable {
    var year: Int
    var month: Int
    var days: [MonthlyDay]
    /// 이번 주(월~일) 마감인 미완료 Task + 마일스톤
    var deadlinesThisWeek: [MonthlyDeadline]
}

// MARK: - 목표별 실천 시간 (GET /timetable/goal-time · 2026-10)

/// 기간 동안 완료한 Task 의 실천 시간, 목표별 (많이 쓴 순). 목표 없이 완료한 Task 는 goalCategoryId == nil 한 줄
struct GoalTimeResponse: Codable, Hashable {
    var startDate: String
    var endDate: String
    var totalMinutes: Int
    var goals: [GoalTimeEntry]
}

struct GoalTimeEntry: Codable, Hashable {
    var goalCategoryId: Int?
    var goalCategoryName: String?
    var goalEmoji: String?
    var goalColorCode: String?
    var minutes: Int
    var taskCount: Int
}

// MARK: - 하루 계획 (GET /daily-plans/{date})

struct DailyPlanResponse: Codable, Hashable {
    var date: String
    var exists: Bool
    var status: DailyPlanStatus?
    var source: DailyPlanSource?
    var summary: String?
    var needsReview: Bool
    var placedCount: Int
    var carriedCount: Int
    var unplacedCount: Int
    var generatedAt: String?
    var reviewedAt: String?
}

struct PlacedItem: Codable, Hashable {
    var taskId: Int
    var date: String
    var start: String
    var end: String
    var reason: String?
}

/// POST /daily-plans/{date}/replan
struct ReplanResponse: Codable, Hashable {
    var plan: DailyPlanResponse?
    var placed: [PlacedItem]
    var movedToOtherDays: [PlacedItem]
    var unscheduled: [Int]
}

// MARK: - 실제로 한 일 (GET /activities)

/// 계획(Task)과 상관없이 그 시간에 실제로 한 활동. 시각은 한국 시간 "YYYY-MM-DDTHH:mm:ss"
struct ActualActivity: Codable, Hashable, Identifiable {
    var id: Int { activityId }

    var activityId: Int
    var title: String
    var emoji: String?
    var startAt: String
    var endAt: String
    var durationMinutes: Int
    var goalCategoryId: Int?
    var goalCategoryName: String?
    var goalColorCode: String?
    /// 원래 이 시간에 계획했던 Task (선택)
    var replacedTaskId: Int?
    var replacedTaskName: String?
    var memo: String?
}

struct ActivityInput: Hashable {
    var title: String
    var emoji: String?
    var startAt: String
    var endAt: String
    var goalCategoryId: Int?
    var replacedTaskId: Int?
    var memo: String?

    /// 웹은 { emoji: null } 처럼 null 을 그대로 보내요 (지우기)
    var json: JSONValue {
        [
            "title": .string(title),
            "emoji": .of(emoji),
            "startAt": .string(startAt),
            "endAt": .string(endAt),
            "goalCategoryId": .of(goalCategoryId),
            "replacedTaskId": .of(replacedTaskId),
            "memo": .of(memo),
        ]
    }
}

// MARK: - AI 로 배치하기 (미리보기 → 확정)

struct FillProposal: Codable, Hashable {
    var taskId: Int
    var date: String
    var start: String
    var end: String
    var reason: String?
}

struct FillPreviewResponse: Codable, Hashable {
    var proposals: [FillProposal]
    /// 빈 시간이 없어 제안하지 못한 Task
    var unscheduled: [Int]
}

struct FillApplyResponse: Codable, Hashable {
    var placed: Int
    var skipped: [Int]
}

struct FillDayResult: Codable, Hashable {
    var date: String
    var placed: Int
    var unscheduled: Int
}

/// POST /daily-plans/fill (바로 저장)
struct FillResponse: Codable, Hashable {
    var days: [FillDayResult]
    var placedCount: Int
    var unscheduledCount: Int
}

/// 범위 골라 바꾸기: DAY 이날만 · WEEK 이번 주 · FOLLOWING 앞으로 계속 · WEEKDAY 앞으로 이 요일마다 (고정 시간 전용)
enum ChangeScope: String, Codable, Hashable {
    case DAY, WEEK, FOLLOWING, WEEKDAY
}

struct RoutineScheduleResponse: Codable, Hashable {
    var scope: ChangeScope
    var result: TaskScheduleResponse?
    /// 같이 옮긴 다른 날짜
    var changedDates: [String]
    /// 잠긴 일정과 겹쳐 건너뛴 날짜 (그날은 원래 시간 그대로)
    var skippedDates: [String]
}
