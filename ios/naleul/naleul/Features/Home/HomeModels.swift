// Features/Home/HomeModels.swift
//
// 웹 features/home/api.ts · today.ts — 홈 "오늘" (백엔드 GET /api/v1/home/today)
//  - 다가오는 마감: Task 마감(지난 7일 ~ 앞으로 14일) · 업무형 주제 마감 · 목표형 종료일(앞으로 1년)
//  - 진행 중인 목표별 오늘: 끝낸 일 · 못 한 일 · 오늘 쓴 회고
// 오늘 일정 · 루틴 Task 는 /timetable, 생활 습관은 /habits 를 그대로 써요.

import Foundation

enum HomeDeadlineType: String, Codable, FallbackDecodable, Hashable {
    case TASK, TOPIC, GOAL
    static let fallback: HomeDeadlineType = .TASK

    var label: String {
        switch self {
        case .TASK: return "Task 마감"
        case .TOPIC: return "세부 목표 마감"
        case .GOAL: return "목표 종료"
        }
    }
}

struct HomeDeadline: Codable, Hashable, Identifiable {
    var id: String { "\(type.rawValue)-\(itemId)" }

    var type: HomeDeadlineType
    /// taskId · workTopicId · goalCategoryId
    var itemId: Int
    var title: String
    var emoji: String?
    var date: String
    /// 오늘 0, 지났으면 음수
    var dDay: Int
    var overdue: Bool
    var goalCategoryId: Int?
    var goalName: String?
    var goalEmoji: String?
    var colorCode: String?
    var goalMode: GoalMode?
    /// TOPIC: 그 주제의 남은 Task 수
    var remaining: Int?

    enum CodingKeys: String, CodingKey {
        case type, itemId = "id", title, emoji, date, dDay, overdue, goalCategoryId, goalName, goalEmoji
        case colorCode, goalMode, remaining
    }

    /// "오늘" · "내일" · "D-3" · "2일 지남"
    var dDayText: String {
        if dDay == 0 { return "오늘" }
        if dDay == 1 { return "내일" }
        return dDay < 0 ? "\(-dDay)일 지남" : "D-\(dDay)"
    }
}

struct HomeRetro: Codable, Hashable, Identifiable {
    var id: Int { retrospectiveId }
    var retrospectiveId: Int
    var reviewType: String?
    var content: String
}

/// 목표형(ACHIEVEMENT) 그날 기록 — 목표 상세 "오늘 기록"과 같은 데이터 (PUT /goal-categories/{id}/check-in)
///  - hasMetric: 수치 목표인지 (아니면 기분 · 한 줄 회고만)
///  - value: 그날 기록한 수치
struct HomeCheckIn: Codable, Hashable {
    var hasMetric: Bool
    var metricName: String?
    var unit: String?
    var currentValue: Double?
    var value: Double?
    var mood: ReflectionMood?
    var note: String?

    var written: Bool { value != nil || mood != nil || !(note ?? "").isEmpty }
}

struct HomeGoalToday: Codable, Hashable, Identifiable {
    var id: Int { goalCategoryId }

    var goalCategoryId: Int
    var goalName: String
    var emoji: String?
    var colorCode: String?
    var goalMode: GoalMode?
    /// 오늘 할 일 · 끝낸 일 · 회고가 하나라도 있으면 true
    var activeToday: Bool
    var planned: Int
    var done: Int
    var doneTasks: [String]
    var openTasks: [String]
    var retros: [HomeRetro]
    /// 목표형만 (업무형은 nil — 회고 글로 남겨요). 아직 배포 전 서버면 없어요
    var checkIn: HomeCheckIn? = nil

    /// 그날 남긴 게 있는지 — 목표형은 "오늘 기록"(수치 · 기분 · 회고), 업무형은 회고 글
    var hasRecord: Bool { !retros.isEmpty || (checkIn?.written ?? false) }

    var title: String { (emoji.map { $0.isEmpty ? "" : "\($0) " } ?? "") + goalName }

    /// 목표 형태별 회고 질문 — 빈 칸 앞에서 막막하지 않게
    var prompt: String {
        if goalMode == .RECORD { return "오늘 일하며 막힌 점 · 배운 점 · 내일 바꿀 점" }
        if planned > 0 && done == 0 { return "오늘 못 한 이유는 뭐였나요? 내일은 어떻게 해 볼까요?" }
        return "오늘 목표에 얼마나 다가갔나요? 잘 된 점 · 아쉬운 점"
    }
}

struct HomeToday: Codable, Hashable {
    var today: String
    var deadlines: [HomeDeadline]
    var goals: [HomeGoalToday]
}

enum HomeAPI {
    /// date 를 주면 그날 (홈에서 날짜 ‹ › 로 지난날 · 앞날 보기). 안 주면 오늘
    static func today(date: String? = nil) async throws -> HomeToday? {
        try await API.get("/v1/home/today" + (date.map { "?date=\($0)" } ?? ""))
    }

    /// 오늘 회고 남기기 (POST /retrospectives · DAILY) — 업무형
    static func createRetro(goalId: Int, content: String, date: String) async throws {
        try await WorkAPI.createRetro(goalId: goalId, content: content, date: date)
    }

    /// 목표형 그날 기록 (수치 + 기분 + 한 줄 회고) — 목표 상세 "오늘 기록"과 같은 API
    static func checkIn(goalId: Int, date: String, value: Double?, mood: ReflectionMood?, note: String?) async throws {
        let _: GoalProgress? = try await GoalEditAPI.checkIn(goalId: goalId, date: date, value: value, mood: mood, note: note)
    }

    /// 회고 모아보기 (GET /home/reflections) — before 의 전날까지 days 일치
    static func reflections(before: String? = nil, days: Int = 30, goalId: Int? = nil) async throws -> ReflectionFeedPage? {
        var q = ["days=\(days)"]
        if let before { q.append("before=\(before)") }
        if let goalId { q.append("goalCategoryId=\(goalId)") }
        return try await API.get("/v1/home/reflections?" + q.joined(separator: "&"))
    }
}

// MARK: - 회고 모아보기

enum ReflectionKind: String, Codable, FallbackDecodable, Hashable {
    /// 목표형 그날 기록 (수치 · 기분 · 한 줄 회고)
    case CHECK_IN
    /// 회고 글 (업무형 등)
    case RETRO
    static let fallback: ReflectionKind = .RETRO
}

struct ReflectionEntry: Codable, Hashable, Identifiable {
    var id: String { "\(kind.rawValue)-\(goalCategoryId)-\(retrospectiveId.map(String.init) ?? date)" }

    var kind: ReflectionKind
    var date: String
    var goalCategoryId: Int
    var goalName: String
    var goalEmoji: String?
    var colorCode: String?
    var goalMode: GoalMode?
    /// RETRO 만
    var retrospectiveId: Int?
    var reviewType: String?
    /// CHECK_IN 만
    var mood: ReflectionMood?
    /// CHECK_IN: 한 줄 회고 · RETRO: 회고 내용
    var note: String?
    var value: Double?
    var metricName: String?
    var unit: String?

    var goalTitle: String { (goalEmoji.map { $0.isEmpty ? "" : "\($0) " } ?? "") + goalName }
}

struct ReflectionFeedPage: Codable, Hashable {
    var from: String
    var to: String
    var entries: [ReflectionEntry]
    var hasMore: Bool
}

/// 웹 home/today.ts — TimeTable 하루에서 "그날 하기로 한" Task 고르기
enum HomeDay {
    /// 계획 시작일 또는 시간 미정 날짜가 그날이거나, 그날 끝낸 것 (전날 밤부터 이어진 블록은 빼요)
    static func tasksOfDay(_ day: TimetableDay?) -> [TimeBlockTask] {
        guard let day else { return [] }
        var seen = Set<Int>()
        return (day.tasks + day.unscheduledTasks).filter { t in
            if seen.contains(t.taskId) || t.taskStatus == .SKIPPED || t.taskStatus == .DELETED { return false }
            seen.insert(t.taskId)
            let planDay = t.plannedStartAt.map { String($0.prefix(10)) } ?? t.date ?? t.scheduledDate
            guard let planDay else { return true }
            return planDay == day.date || t.completedAt.map { String($0.prefix(10)) } == day.date
        }
    }

    /// 그날 0시부터 시작 분 (시간 미정은 맨 뒤)
    static func startMinutes(_ day: String, _ t: TimeBlockTask) -> Int {
        t.plannedStartAt.map { KST.minutesFrom(day, $0) } ?? Int.max
    }

    static func endMinutes(_ day: String, _ t: TimeBlockTask) -> Int {
        guard let s = t.plannedStartAt else { return Int.max }
        if let e = t.plannedEndAt { return KST.minutesFrom(day, e) }
        return KST.minutesFrom(day, s) + (t.plannedDurationMinutes ?? 30)
    }

    static func isRoutine(_ t: TimeBlockTask) -> Bool { t.sourceType == .ROUTINE }

    /// "40분 뒤" · "1시간 20분 뒤"
    static func inLabel(_ minutes: Int) -> String {
        if minutes <= 0 { return "지금" }
        let h = minutes / 60, m = minutes % 60
        if h > 0 && m > 0 { return "\(h)시간 \(m)분 뒤" }
        if h > 0 { return "\(h)시간 뒤" }
        return "\(m)분 뒤"
    }
}
