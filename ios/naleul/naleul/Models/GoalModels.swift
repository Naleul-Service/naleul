// Models/GoalModels.swift
//
// 웹 features/goal/api.ts 의 타입 · goal/format.ts · goal/kind.ts 를 옮긴 목표 모델.
// 백엔드 GoalCategoryResponse (목표 = goal_category).
// AI 목표 생성 필드(emoji, aiNote, metric*, milestones 등)는 사용자가 직접 만든 목표에선 nil 이에요.

import Foundation

enum GoalStatus: String, Codable, FallbackDecodable, Hashable {
    case NOT_STARTED, IN_PROGRESS, COMPLETED, DELETED
    static let fallback: GoalStatus = .IN_PROGRESS

    /// 웹 STATUS_LABEL
    var label: String {
        switch self {
        case .NOT_STARTED: return "시작 전"
        case .IN_PROGRESS: return "진행 중"
        case .COMPLETED: return "완료"
        case .DELETED: return "삭제됨"
        }
    }

    /// 아직 끝나지 않은 목표 (진행 중 목록)
    var isOngoing: Bool { self == .IN_PROGRESS || self == .NOT_STARTED }
}

struct RoutineDayTime: Codable, Hashable {
    var dayOfWeek: JavaDayOfWeek
    /// "HH:mm" 또는 "HH:mm:ss"
    var startTime: String
    var endTime: String
}

struct RoutineSummary: Codable, Hashable, Identifiable {
    var id: Int { routineId }

    var routineId: Int
    var routineName: String
    var routineStatus: String
    var repeatStartDate: String?
    var repeatEndDate: String?
    /// "19:30:00" (LocalTime)
    var repeatStartTime: String?
    var repeatEndTime: String?
    var repeatDays: [JavaDayOfWeek]
    var notificationEnabled: Bool
    /// 하는 방법 (AI 루틴: "스쿼트 4x10, 런지 3x12…")
    var description: String?
    var durationMinutes: Int?
    /// 기본 시간과 다른 요일만. 비어 있으면 모든 요일이 repeatStartTime~repeatEndTime
    var dayTimes: [RoutineDayTime]?

    // init(from:) 을 extension 에 직접 썼으니 키 목록도 직접 적어 둬요 (encode 자동 생성에도 쓰여요)
    enum CodingKeys: String, CodingKey {
        case routineId, routineName, routineStatus, repeatStartDate, repeatEndDate, repeatStartTime, repeatEndTime
        case repeatDays, notificationEnabled, description, durationMinutes, dayTimes
    }
}

extension RoutineSummary {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        routineId = try c.decode(Int.self, forKey: .routineId)
        routineName = try c.decodeIfPresent(String.self, forKey: .routineName) ?? ""
        routineStatus = try c.decodeIfPresent(String.self, forKey: .routineStatus) ?? ""
        repeatStartDate = try c.decodeIfPresent(String.self, forKey: .repeatStartDate)
        repeatEndDate = try c.decodeIfPresent(String.self, forKey: .repeatEndDate)
        repeatStartTime = try c.decodeIfPresent(String.self, forKey: .repeatStartTime)
        repeatEndTime = try c.decodeIfPresent(String.self, forKey: .repeatEndTime)
        repeatDays = try c.decodeIfPresent([JavaDayOfWeek].self, forKey: .repeatDays) ?? []
        notificationEnabled = try c.decodeIfPresent(Bool.self, forKey: .notificationEnabled) ?? false
        description = try c.decodeIfPresent(String.self, forKey: .description)
        durationMinutes = try c.decodeIfPresent(Int.self, forKey: .durationMinutes)
        dayTimes = try c.decodeIfPresent([RoutineDayTime].self, forKey: .dayTimes)
    }
}

/// 세부 목표 (= 영역, general_category)
struct SubGoalInfo: Codable, Hashable, Identifiable {
    var id: Int { generalCategoryId }

    var generalCategoryId: Int
    var generalCategoryName: String
    var colorCode: String?
    var generalCategoryStartDate: String?
    var generalCategoryEndDate: String?
    var generalCategoryStatus: GoalStatus
    var routines: [RoutineSummary]
    /// 세부 목표의 실천 원칙 (AI: "하루 1,600kcal, 단백질 90g…")
    var description: String?
    var emoji: String?
    /// 목표의 "기타 할 일" 그릇 — 영역을 안 고른 루틴·Task 가 들어가요. 화면에선 영역으로 보여주지 않아요
    var defaultBucket: Bool?

    enum CodingKeys: String, CodingKey {
        case generalCategoryId, generalCategoryName, colorCode, generalCategoryStartDate, generalCategoryEndDate
        case generalCategoryStatus, routines, description, emoji, defaultBucket
    }
}

extension SubGoalInfo {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        generalCategoryId = try c.decode(Int.self, forKey: .generalCategoryId)
        generalCategoryName = try c.decodeIfPresent(String.self, forKey: .generalCategoryName) ?? ""
        colorCode = try c.decodeIfPresent(String.self, forKey: .colorCode)
        generalCategoryStartDate = try c.decodeIfPresent(String.self, forKey: .generalCategoryStartDate)
        generalCategoryEndDate = try c.decodeIfPresent(String.self, forKey: .generalCategoryEndDate)
        generalCategoryStatus = try c.decodeIfPresent(GoalStatus.self, forKey: .generalCategoryStatus) ?? .IN_PROGRESS
        routines = try c.decodeIfPresent([RoutineSummary].self, forKey: .routines) ?? []
        description = try c.decodeIfPresent(String.self, forKey: .description)
        emoji = try c.decodeIfPresent(String.self, forKey: .emoji)
        defaultBucket = try c.decodeIfPresent(Bool.self, forKey: .defaultBucket)
    }
}

enum MilestoneStatus: String, Codable, FallbackDecodable, Hashable {
    case PENDING, ACHIEVED, MISSED
    static let fallback: MilestoneStatus = .PENDING
}

struct MilestoneInfo: Codable, Hashable, Identifiable {
    var id: Int { milestoneId }

    var milestoneId: Int
    var title: String
    var description: String?
    var dueDate: String
    var targetValue: Double?
    /// 사용자가 이 시점 수치를 직접 정했는지 (false = 시작값 → 목표값 직선에서 자동 계산)
    var targetValueManual: Bool?
    var status: MilestoneStatus
}

/// 목표 형태: ACHIEVEMENT 목표형(기본) / RECORD 업무형 / HABIT 기본 생활 습관 (V52, 사용자당 1개)
enum GoalMode: String, Codable, FallbackDecodable, Hashable {
    case ACHIEVEMENT, RECORD, HABIT
    static let fallback: GoalMode = .ACHIEVEMENT
}

enum PlanningStyle: String, Codable, FallbackDecodable, Hashable {
    case PLANNER, SPONTANEOUS
    static let fallback: PlanningStyle = .PLANNER
}

struct GoalCategory: Codable, Hashable, Identifiable {
    var id: Int { goalCategoryId }

    var goalCategoryId: Int
    var goalCategoryName: String
    var goalCategoryStatus: GoalStatus
    var goalCategoryStartDate: String?
    var goalCategoryEndDate: String?
    var achievement: String?
    var motive: String?
    var colorCode: String?
    var generalCategories: [SubGoalInfo]
    // ── AI 목표 생성 필드 ──
    var emoji: String?
    var goalTopic: String?
    var description: String?
    var aiGenerated: Bool?
    /// Brain dump 에서 자동으로 만든 임시 목표 (종료일·마일스톤 없음) → "AI로 구체화하기"를 권해요
    var temporary: Bool?
    var goalMode: GoalMode?
    /// 목표 카테고리 2단계. 예전 목표는 nil
    var goalType: GoalType?
    var goalSubType: GoalSubType?
    var goalKindLabel: String?
    /// "건강 · 다이어트"
    var goalKindName: String?
    var planningStyle: PlanningStyle?
    var aiNote: String?
    var metricName: String?
    var metricUnit: String?
    var startValue: Double?
    var currentValue: Double?
    var targetValue: Double?
    /// 수치 목표 마감일 (V58) — 목표 종료일과 따로. nil 이면 목표 종료일을 따라요
    var metricTargetDate: String? = nil
    var milestones: [MilestoneInfo]?
    /// 목록 조회에서만 — 마지막 활동일 (Task 완료 · 수치 기록)
    var lastActivityDate: String?
    /// 목록 조회에서만 — 진행 중인데 오래 활동이 없으면 멈춘 지 며칠째, 아니면 nil
    var pausedDays: Int?

    /// 기록형(업무형) 목표인지 (예전 응답엔 goalMode 가 없어서 nil = 달성형)
    var isRecordGoal: Bool { goalMode == .RECORD }
    /// 기본 생활 습관 그릇 (V52) — 목표 상세 대신 습관 화면을 보여줘요
    var isHabitGoal: Bool { goalMode == .HABIT }
    /// 업무형 목표인지 — 업무형은 기록형(RECORD)으로 만들어져요. 세부 목표 = 로드맵의 세부 목표(WorkTopic) (웹 isWorkGoal)
    var isWorkGoal: Bool { goalMode == .RECORD || goalType == .WORK }
    var isTemporary: Bool { temporary ?? false }
    /// 화면에 보여줄 세부 목표 (defaultBucket "기타 할 일" 그릇은 빼요) — 웹 areasOf
    var areas: [SubGoalInfo] { generalCategories.filter { !($0.defaultBucket ?? false) } }

    enum CodingKeys: String, CodingKey {
        case goalCategoryId, goalCategoryName, goalCategoryStatus, goalCategoryStartDate, goalCategoryEndDate
        case achievement, motive, colorCode, generalCategories
        case emoji, goalTopic, description, aiGenerated, temporary, goalMode, goalType, goalSubType
        case goalKindLabel, goalKindName, planningStyle, aiNote, metricName, metricUnit
        case startValue, currentValue, targetValue, metricTargetDate, milestones, lastActivityDate, pausedDays
    }
}

extension GoalCategory {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        goalCategoryId = try c.decode(Int.self, forKey: .goalCategoryId)
        goalCategoryName = try c.decodeIfPresent(String.self, forKey: .goalCategoryName) ?? ""
        goalCategoryStatus = try c.decodeIfPresent(GoalStatus.self, forKey: .goalCategoryStatus) ?? .IN_PROGRESS
        goalCategoryStartDate = try c.decodeIfPresent(String.self, forKey: .goalCategoryStartDate)
        goalCategoryEndDate = try c.decodeIfPresent(String.self, forKey: .goalCategoryEndDate)
        achievement = try c.decodeIfPresent(String.self, forKey: .achievement)
        motive = try c.decodeIfPresent(String.self, forKey: .motive)
        colorCode = try c.decodeIfPresent(String.self, forKey: .colorCode)
        generalCategories = try c.decodeIfPresent([SubGoalInfo].self, forKey: .generalCategories) ?? []
        emoji = try c.decodeIfPresent(String.self, forKey: .emoji)
        goalTopic = try c.decodeIfPresent(String.self, forKey: .goalTopic)
        description = try c.decodeIfPresent(String.self, forKey: .description)
        aiGenerated = try c.decodeIfPresent(Bool.self, forKey: .aiGenerated)
        temporary = try c.decodeIfPresent(Bool.self, forKey: .temporary)
        goalMode = try c.decodeIfPresent(GoalMode.self, forKey: .goalMode)
        goalType = try c.decodeIfPresent(GoalType.self, forKey: .goalType)
        goalSubType = try c.decodeIfPresent(GoalSubType.self, forKey: .goalSubType)
        goalKindLabel = try c.decodeIfPresent(String.self, forKey: .goalKindLabel)
        goalKindName = try c.decodeIfPresent(String.self, forKey: .goalKindName)
        planningStyle = try c.decodeIfPresent(PlanningStyle.self, forKey: .planningStyle)
        aiNote = try c.decodeIfPresent(String.self, forKey: .aiNote)
        metricName = try c.decodeIfPresent(String.self, forKey: .metricName)
        metricUnit = try c.decodeIfPresent(String.self, forKey: .metricUnit)
        startValue = try c.decodeIfPresent(Double.self, forKey: .startValue)
        currentValue = try c.decodeIfPresent(Double.self, forKey: .currentValue)
        targetValue = try c.decodeIfPresent(Double.self, forKey: .targetValue)
        metricTargetDate = try c.decodeIfPresent(String.self, forKey: .metricTargetDate)
        milestones = try c.decodeIfPresent([MilestoneInfo].self, forKey: .milestones)
        lastActivityDate = try c.decodeIfPresent(String.self, forKey: .lastActivityDate)
        pausedDays = try c.decodeIfPresent(Int.self, forKey: .pausedDays)
    }
}

/// GET /goal-categories/{id}/tasks — 목표에 속한 Task 전체
struct GoalTasksResponse: Codable, Hashable {
    var totalCount: Int
    var todoCount: Int
    var completedCount: Int
    var tasks: [TimeBlockTask]
}

// MARK: - 루틴 실천 히트맵 (GET /routines/{goalId}/heatmap)

struct RoutineHeatmap: Codable, Hashable, Identifiable {
    var id: Int { routineId }

    var routineId: Int
    var routineName: String
    var repeatStartDate: String
    var repeatEndDate: String?
    var repeatDays: [JavaDayOfWeek]
    /// 루틴 Task 를 완료한 날짜 "YYYY-MM-DD"
    var completedDates: [String]
    var currentStreak: Int
    var longestStreak: Int
    /// 주로 몇 시부터 몇 시에 했는지 (2026-10). 시각을 알 수 있는 기록이 없으면 nil
    var timeStats: RoutineTimeStats?
}

/// 루틴을 주로 실천한 시간대
struct RoutineTimeStats: Codable, Hashable {
    /// "HH:mm"
    var bestStart: String?
    var bestEnd: String?
    /// 시각을 알 수 있는 완료 횟수
    var practiceCount: Int
    /// 그중 bestStart~bestEnd 에 걸친 횟수
    var inWindowCount: Int
    /// 0~23시 각 시간에 걸친 실천 횟수
    var hourly: [Int]?
    /// 요일별로 가장 많이 한 시간대 (많이 한 요일 순, 최대 3개 · 2026-10)
    var byDay: [RoutineDayTimeStat]?

    /// "화 19:00~19:30 · 목 17:10~17:40에 가장 많이 실천했어요" — 요일 정보가 없으면 "주로 …에 실천했어요", 기록이 없으면 nil
    var practiceSentence: String? {
        let days = (byDay ?? []).compactMap { d -> String? in
            guard let label = d.dayLabel else { return nil }
            return "\(label) \(d.start)~\(d.end)"
        }
        if !days.isEmpty { return days.joined(separator: " · ") + "에 가장 많이 실천했어요" }
        if let s = bestStart, let e = bestEnd, practiceCount > 0 { return "주로 \(s)~\(e)에 실천했어요" }
        return nil
    }
}

/// 한 요일에서 가장 많이 한 시간대
struct RoutineDayTimeStat: Codable, Hashable {
    /// "MONDAY" ~ "SUNDAY"
    var day: String
    var start: String
    var end: String
    var count: Int
    var total: Int

    var dayLabel: String? {
        ["MONDAY": "월", "TUESDAY": "화", "WEDNESDAY": "수", "THURSDAY": "목",
         "FRIDAY": "금", "SATURDAY": "토", "SUNDAY": "일"][day]
    }
}

// MARK: - 진행 도표 ("지금 어디쯤?")

enum ProgressStatus: String, Codable, FallbackDecodable, Hashable {
    case NOT_STARTED, AHEAD, ON_TRACK, BEHIND, ACHIEVED, NO_PLAN
    static let fallback: ProgressStatus = .NO_PLAN
}

struct ProgressPoint: Codable, Hashable {
    var date: String
    var value: Double
    /// "시작" · 마일스톤 제목 · "목표"
    var label: String?
}

struct MetricLog: Codable, Hashable {
    var date: String
    var value: Double
    var memo: String?
}

/// 수치 목표(체중 72 → 69kg 등)의 계획선 · 기록 · 오늘 위치
struct MetricProgress: Codable, Hashable {
    var name: String?
    var unit: String?
    var startValue: Double
    var targetValue: Double
    var currentValue: Double
    /// 계획대로라면 오늘 값 (종료일 없으면 nil)
    var expectedValue: Double?
    /// (현재-시작)/(목표-시작)×100 — 음수·100 초과 가능
    var progressPercent: Double?
    var status: ProgressStatus
    /// 최근 기록 추세대로면 목표 도달 예상일
    var projectedDate: String?
    var lastLoggedDate: String?
    var loggedToday: Bool
    var planLine: [ProgressPoint]
    var logs: [MetricLog]
    /// 수치 목표 마감일 (V58 — 따로 정하지 않았으면 목표 종료일). 예전 응답엔 없어요
    var targetDate: String? = nil
}

struct TaskProgressPoint: Codable, Hashable {
    var date: String
    /// 그날까지 하기로 한 Task 누적
    var planned: Int
    /// 그날까지 완료한 Task 누적 (오늘 이후는 nil)
    var completed: Int?
}

struct TaskProgress: Codable, Hashable {
    var total: Int
    var completed: Int
    /// 오늘까지 하기로 한 Task (이미 끝낸 Task 포함)
    var dueByToday: Int
    var completionPercent: Double?
    var series: [TaskProgressPoint]
}

/// 목표 결과: 달성 · 일부 달성 · 못 함
enum OutcomeResult: String, Codable, FallbackDecodable, Hashable, CaseIterable {
    case ACHIEVED, PARTIAL, NOT_ACHIEVED
    static let fallback: OutcomeResult = .PARTIAL
}

struct GoalOutcome: Codable, Hashable {
    var result: OutcomeResult
    /// "USER" 직접 고름 · "AUTO" 기간이 끝나 자동 판정
    var source: String
    var metricPercent: Double?
    var routineRate: Double?
    var taskRate: Double?
    /// 수치 목표인데 수치 기록이 없어 실천률로 판정했어요
    var metricUnrecorded: Bool?
    /// 서버 자동 판정 (직접 골라도 남아요 — 판정 일치율 비교용)
    var autoResult: OutcomeResult?
    /// METRIC · ROUTINE_RATE · TASK_RATE · NO_RECORD
    var autoReason: String?
    /// 예) "마지막 기록이 목표 수치의 86%예요. 80% 이상이라 자동으로 달성 처리했어요."
    var autoMessage: String?
}

struct GoalAdjustment: Codable, Hashable {
    var key: String
    /// "DOWN" 하루 줄이기 · "UP" 하루 늘리기
    var direction: String
    var routineId: Int
    var routineName: String
    var fromDays: Int
    var toDays: Int
    var day: JavaDayOfWeek
    var previousWeekRate: Double
    var lastWeekRate: Double
    var message: String
}

/// 그날 목표를 돌아본 느낌
enum ReflectionMood: String, Codable, FallbackDecodable, Hashable, CaseIterable {
    case GOOD, OK, BAD
    static let fallback: ReflectionMood = .OK
}

/// 하루 회고: 무엇을 했는지 · 어땠는지
struct GoalReflection: Codable, Hashable {
    var date: String
    var mood: ReflectionMood?
    var note: String?
}

struct GoalProgress: Codable, Hashable {
    var goalCategoryId: Int
    var today: String
    var startDate: String?
    var endDate: String?
    var elapsedDays: Int
    var totalDays: Int?
    var metric: MetricProgress?
    var tasks: TaskProgress
    /// 최근 하루 회고 (최신순)
    var reflections: [GoalReflection]?
    /// 끝난 목표의 결과 (진행 중이면 nil)
    var outcome: GoalOutcome?
    /// 주간 점검 제안 — 2주 연속 실천률이 낮거나 높을 때만 내려와요
    var adjustment: GoalAdjustment?
    /// 추가 그래프 (V52) — 대표 수치 말고 같이 볼 수치 (기출 회독 수 · 모의고사 점수 …)
    var extraMetrics: [ExtraMetric]?
}

/// 목표형의 추가 그래프 하나 (GET /goal-categories/{id}/progress 의 extraMetrics)
struct ExtraMetric: Codable, Hashable, Identifiable {
    var id: Int { metricId }
    var metricId: Int
    var sortOrder: Int
    var progress: MetricProgress
}

// MARK: - goal/format.ts

enum GoalFormat {
    /// "19:30:00" → "19:30"
    static func hhmm(_ t: String?) -> String? {
        guard let t else { return nil }
        return String(t.prefix(5))
    }

    /// 목표 색 (없으면 연한 회색 #B8BCC4)
    static func goalColorHex(_ code: String?) -> String {
        guard let code, !code.isEmpty else { return "#B8BCC4" }
        return code.hasPrefix("#") ? code : "#\(code)"
    }

    /// "2026-11-18" → "2026.11.18"
    static func formatDot(_ v: String?) -> String { (v ?? "").replacingOccurrences(of: "-", with: ".") }

    struct Period {
        /// 전체 일수
        let total: Int
        /// 지난 일수 (0~total)
        let elapsed: Int
        /// 남은 일수 (음수면 종료)
        let remain: Int
        /// 0~1
        let ratio: Double
        let weeks: Int
    }

    /// 기간 진행 정보 (오늘 기준)
    static func periodProgress(_ start: String?, _ end: String?, today: String = KST.today()) -> Period? {
        guard let start, let end else { return nil }
        let total = max(KST.diffDays(start, end), 1)
        let elapsed = min(max(KST.diffDays(start, today), 0), total)
        let remain = KST.diffDays(today, end)
        return Period(total: total, elapsed: elapsed, remain: remain, ratio: Double(elapsed) / Double(total),
                      weeks: Int(ceil(Double(total) / 7)))
    }

    /// D-3 / D-DAY / 종료
    static func dDayLabel(_ remain: Int) -> String {
        remain > 0 ? "D-\(remain)" : (remain == 0 ? "D-DAY" : "종료")
    }

    /// 숫자 표시: 72.0 → "72", 72.5 → "72.5"
    static func number(_ v: Double?, maxFraction: Int = 1) -> String {
        guard let v else { return "-" }
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.maximumFractionDigits = maxFraction
        f.minimumFractionDigits = 0
        return f.string(from: NSNumber(value: v)) ?? "\(v)"
    }
}

// MARK: - goal/kind.ts — 목표 카테고리 2단계

enum GoalType: String, Codable, FallbackDecodable, Hashable, CaseIterable {
    case HEALTH, LEARNING, HABIT, ETC, WORK
    static let fallback: GoalType = .ETC
}

enum GoalSubType: String, Codable, FallbackDecodable, Hashable, CaseIterable {
    case EXERCISE, DIET, WATER, NO_ALCOHOL, NO_SMOKING, HEALTH_OTHER
    case LANGUAGE, CERTIFICATE, READING, CODING, LEARNING_OTHER
    case WAKE_UP, SLEEP, MEDITATION, CLEANING, JOURNAL
    case SAVING, OTHER
    case COMPANY_WORK, SIDE_PROJECT
    static let fallback: GoalSubType = .OTHER
}

struct GoalKindValue: Hashable {
    var goalType: GoalType
    var goalSubType: GoalSubType
    /// "기타"일 때만 (1~10자)
    var goalKindLabel: String?
}

struct GoalSubOption: Hashable, Identifiable {
    var id: GoalSubType { value }
    let value: GoalSubType
    let label: String
    let emoji: String
    var custom = false
    /// 시작 화면 예시 문장
    var examples: [String] = []
}

struct GoalTypeOption: Hashable, Identifiable {
    var id: GoalType { value }
    let value: GoalType
    let label: String
    let emoji: String
    let hint: String
    let subs: [GoalSubOption]
}

enum GoalKind {
    static let labelMax = 10

    /// 생활형 목표 카테고리 (업무형 WORK 는 따로)
    static let types: [GoalTypeOption] = [
        GoalTypeOption(value: .HEALTH, label: "건강", emoji: "💪", hint: "운동 · 다이어트 · 금연", subs: [
            GoalSubOption(value: .EXERCISE, label: "운동", emoji: "🏋️", examples: ["주 3회 헬스로 근력 키우기", "3개월 안에 5km 30분 안에 뛰기"]),
            GoalSubOption(value: .DIET, label: "다이어트", emoji: "🥗", examples: ["2주 동안 3kg 감량하기", "3개월에 8kg 빼기"]),
            GoalSubOption(value: .WATER, label: "물 마시기", emoji: "💧", examples: ["하루 물 2L 마시기"]),
            GoalSubOption(value: .NO_ALCOHOL, label: "금주", emoji: "🚱", examples: ["한 달 동안 술 끊기", "술은 주 1회 이하로"]),
            GoalSubOption(value: .NO_SMOKING, label: "금연", emoji: "🚭", examples: ["다음 달부터 완전히 금연하기", "하루 10개비에서 0개비로"]),
            GoalSubOption(value: .HEALTH_OTHER, label: "기타", emoji: "✏️", custom: true),
        ]),
        GoalTypeOption(value: .LEARNING, label: "학습·자기개발", emoji: "📚", hint: "언어 · 자격증 · 독서 · 코딩", subs: [
            GoalSubOption(value: .LANGUAGE, label: "언어", emoji: "🗣️", examples: ["토익 900점 받기", "6개월 안에 일본어로 일상 회화하기"]),
            GoalSubOption(value: .CERTIFICATE, label: "자격증", emoji: "📜", examples: ["정보처리기사 필기 합격하기", "컴활 1급 따기"]),
            GoalSubOption(value: .READING, label: "독서", emoji: "📖", examples: ["올해 책 12권 읽기", "한 달에 2권 읽기"]),
            GoalSubOption(value: .CODING, label: "코딩", emoji: "💻", examples: ["백준 골드 달성하기", "3개월 안에 포트폴리오 앱 완성하기"]),
            GoalSubOption(value: .LEARNING_OTHER, label: "기타", emoji: "✏️", custom: true),
        ]),
        GoalTypeOption(value: .HABIT, label: "생활습관", emoji: "🌱", hint: "기상 · 취침 · 명상 · 청소 · 일기", subs: [
            GoalSubOption(value: .WAKE_UP, label: "기상", emoji: "🌅", examples: ["매일 아침 6시 30분에 일어나기"]),
            GoalSubOption(value: .SLEEP, label: "취침", emoji: "🌙", examples: ["자정 전에 잠들기"]),
            GoalSubOption(value: .MEDITATION, label: "명상", emoji: "🧘", examples: ["매일 10분 명상하기"]),
            GoalSubOption(value: .CLEANING, label: "청소", emoji: "🧹", examples: ["방을 매일 깔끔하게 유지하기"]),
            GoalSubOption(value: .JOURNAL, label: "일기", emoji: "📝", examples: ["매일 자기 전 일기 쓰기"]),
        ]),
        GoalTypeOption(value: .ETC, label: "기타", emoji: "✨", hint: "돈 모으기 · 직접 적기", subs: [
            GoalSubOption(value: .SAVING, label: "돈 모으기", emoji: "💰", examples: ["6개월 안에 300만 원 모으기", "한 달 식비 40만 원 이하로"]),
            GoalSubOption(value: .OTHER, label: "기타", emoji: "✏️", custom: true),
        ]),
    ]

    /// 업무형(기록형) 목표 전용 종류
    static let workType = GoalTypeOption(value: .WORK, label: "업무", emoji: "💼", hint: "회사 업무 · 사이드 프로젝트", subs: [
        GoalSubOption(value: .COMPANY_WORK, label: "회사 업무", emoji: "💼", examples: ["OO팀 업무", "나를 업무"]),
        GoalSubOption(value: .SIDE_PROJECT, label: "사이드 프로젝트", emoji: "🚀", examples: ["포트폴리오 앱", "블로그 운영"]),
    ])

    static var allTypes: [GoalTypeOption] { types + [workType] }

    /// AI로 만드는 목표형에서 고를 수 있는 카테고리 (2026-10) — 기본 생활 패턴에 들어갈 것(생활습관 · 물 마시기)은 빼고,
    /// 이뤄야 하고 수치로 잴 수 있는 것만. 몇 개 안 남아서 1단계 없이 칩 한 줄로 보여줘요 (각 묶음의 "기타"는 맨 끝 "기타" 하나로)
    /// 2026-10-24: 금주 · 금연 · 코딩 빼고 돈 모으기 추가 (직접 만드는 목표에는 그대로 있어요)
    static let aiExcludedSubs: Set<GoalSubType> = [.WATER, .HEALTH_OTHER, .LEARNING_OTHER, .NO_ALCOHOL, .NO_SMOKING, .CODING]
    static var aiFlatOptions: [(type: GoalType, sub: GoalSubOption)] {
        var out: [(type: GoalType, sub: GoalSubOption)] = []
        for t in types where t.value != .HABIT {
            for s in t.subs where !aiExcludedSubs.contains(s.value) { out.append((t.value, s)) }
        }
        return out   // 기타 묶음(돈 모으기 · 기타)이 맨 끝
    }

    /// "이 목표를 위해 꼭 하고 싶은 일" 추천 칩
    static let mustDoSuggestions: [GoalSubType: [String]] = [
        .EXERCISE: ["헬스", "러닝", "홈트레이닝", "스트레칭", "수영"],
        .DIET: ["헬스", "식단 기록", "유산소", "체중 기록", "간헐적 단식"],
        .WATER: ["물 마시기 체크", "텀블러 들고 다니기"],
        .NO_ALCOHOL: ["음주 기록", "술자리 대신 운동"],
        .NO_SMOKING: ["흡연 욕구 기록", "금연 클리닉"],
        .LANGUAGE: ["단어 암기", "듣기 연습", "회화 연습", "모의고사"],
        .CERTIFICATE: ["이론 공부", "기출 풀이", "오답 정리", "모의고사"],
        .READING: ["매일 읽기", "독서 기록"],
        .CODING: ["알고리즘 문제 풀이", "강의 듣기", "프로젝트 개발"],
        .SAVING: ["가계부 쓰기", "자동 이체", "무지출 데이", "지출 점검"],
        .WAKE_UP: ["기상 인증", "아침 스트레칭"],
        .SLEEP: ["취침 준비 루틴", "자기 전 휴대폰 끄기"],
        .MEDITATION: ["아침 명상", "호흡 연습"],
        .CLEANING: ["정리 정돈", "설거지 바로 하기"],
        .JOURNAL: ["일기 쓰기", "감사 일기"],
    ]

    static func typeOption(_ t: GoalType) -> GoalTypeOption {
        allTypes.first { $0.value == t } ?? types[types.count - 1]
    }

    static func subOption(_ s: GoalSubType) -> GoalSubOption {
        allTypes.flatMap(\.subs).first { $0.value == s } ?? GoalSubOption(value: .OTHER, label: "기타", emoji: "✏️", custom: true)
    }

    /// 입력이 끝났는지 (기타면 이름 1~10자)
    static func isComplete(_ v: GoalKindValue?) -> Bool {
        guard let v else { return false }
        if !subOption(v.goalSubType).custom { return true }
        let label = (v.goalKindLabel ?? "").trimmingCharacters(in: .whitespaces)
        return !label.isEmpty && label.count <= labelMax
    }

    /// "건강 · 다이어트", "기타 · 악기 연습"
    static func name(type: GoalType?, subType: GoalSubType?, label: String?) -> String? {
        guard let type else { return nil }
        let t = typeOption(type)
        guard let subType else { return t.label }
        let sub = subOption(subType)
        let trimmed = (label ?? "").trimmingCharacters(in: .whitespaces)
        let subLabel = sub.custom ? (trimmed.isEmpty ? sub.label : trimmed) : sub.label
        return "\(t.label) · \(subLabel)"
    }

    static func name(_ v: GoalKindValue?) -> String? {
        name(type: v?.goalType, subType: v?.goalSubType, label: v?.goalKindLabel)
    }

    /// API 요청 바디용 (기타가 아니면 이름은 보내지 않아요). 다른 필드와 합쳐 쓰세요
    static func body(_ v: GoalKindValue?) -> [String: JSONValue] {
        guard let v else { return [:] }
        var out: [String: JSONValue] = [
            "goalType": .string(v.goalType.rawValue),
            "goalSubType": .string(v.goalSubType.rawValue),
        ]
        if subOption(v.goalSubType).custom {
            out["goalKindLabel"] = .string((v.goalKindLabel ?? "").trimmingCharacters(in: .whitespaces))
        }
        return out
    }
}

// MARK: - 목표 이름으로 카테고리 짐작 (웹 features/goal/kind.ts guessKind · kindOfGoal, 2026-10)

extension GoalKind {
    /// AI 를 부르지 않는 키워드 규칙 — 예전 · 임시 목표를 AI 로 설계할 때 시작 화면에 미리 골라 둬요 (틀려도 바로 바꿀 수 있어요)
    private static let keywords: [(GoalSubType, String)] = [
        (.LANGUAGE, "토익|토플|오픽|opic|toeic|ielts|jlpt|hsk|영어|일본어|중국어|회화|스피킹|speaking|어학"),
        (.CERTIFICATE, "자격증|기사|산업기사|컴활|한국사|시험|합격|면허|필기|실기"),
        (.CODING, "코딩|개발|프로그래밍|알고리즘|코테|코딩테스트|백준|프로젝트|앱 만들기"),
        (.READING, "독서|책|읽기|완독"),
        (.DIET, "다이어트|감량|체중|몸무게|kg|살 ?빼|식단"),
        (.EXERCISE, "운동|헬스|러닝|달리기|조깅|마라톤|요가|필라테스|근력|수영|등산|자전거|홈트|크로스핏|스트레칭"),
        (.WATER, "물 ?마시|수분"),
        (.NO_ALCOHOL, "금주|술"),
        (.NO_SMOKING, "금연|담배"),
        (.WAKE_UP, "기상|일찍 일어|미라클 ?모닝|아침형"),
        (.SLEEP, "수면|취침|일찍 자|잠"),
        (.MEDITATION, "명상|마음챙김"),
        (.CLEANING, "청소|정리정돈|방 정리"),
        (.JOURNAL, "일기|다이어리|감사 ?일기"),
        (.SAVING, "저축|적금|저금|돈 ?모으|재테크|절약"),
    ]

    static func guess(_ name: String) -> GoalKindValue {
        let text = name.trimmingCharacters(in: .whitespacesAndNewlines)
        for (sub, pattern) in keywords where text.range(of: pattern, options: [.regularExpression, .caseInsensitive]) != nil {
            if let type = types.first(where: { $0.subs.contains { $0.value == sub } })?.value {
                return GoalKindValue(goalType: type, goalSubType: sub)
            }
        }
        let label = String(text.prefix(labelMax))
        return GoalKindValue(goalType: .ETC, goalSubType: .OTHER, goalKindLabel: label.isEmpty ? "기타" : label)
    }

    /// 목표에 저장된 카테고리 → 없으면 이름으로 짐작
    static func of(_ goal: GoalCategory) -> GoalKindValue {
        if let t = goal.goalType, let s = goal.goalSubType, t != .WORK, types.contains(where: { $0.value == t }) {
            let v = GoalKindValue(goalType: t, goalSubType: s, goalKindLabel: goal.goalKindLabel)
            if isComplete(v) { return v }
        }
        return guess(goal.goalCategoryName)
    }
}
