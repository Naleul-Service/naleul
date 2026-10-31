// Features/Habit/HabitModels.swift
//
// 웹 features/habit/api.ts — 기본 생활 습관 (백엔드 V52 · /api/v1/habits)
//
// 사용자마다 "기본 생활 습관" 목표(goalMode = HABIT)가 하나씩 자동으로 생기고, 그 안에
// 영양제 먹기 · 샤워하기 같은 "했는지만 체크하는" 습관을 담아요. 마감 · 수치가 없고, 쉬었다 다시 시작할 수 있어요.
// 병원 가기처럼 한 번만 할 일은 같은 목표에 일회성 Task 로 넣어요 (GoalAPI.createGoalTask).
//
// 바꾸면 [.habits] (+ 홈 · 캘린더) 를 다시 불러와요.

import Foundation

enum HabitStatus: String, Codable, FallbackDecodable, Hashable {
    case ACTIVE, PAUSED, DELETED
    static let fallback: HabitStatus = .ACTIVE
}

/// 잔디 한 칸 — DONE 함 · MISSED 놓침 · TODO 오늘 아직 · OFF 반복 요일 아님 · PAUSED 쉬는 중 · BEFORE_START · FUTURE
enum HabitDayState: String, Codable, FallbackDecodable, Hashable {
    case DONE, MISSED, TODO, OFF, PAUSED, BEFORE_START, FUTURE
    static let fallback: HabitDayState = .OFF

    var label: String {
        switch self {
        case .DONE: return "했어요"
        case .MISSED: return "놓쳤어요"
        case .TODO: return "오늘 아직"
        case .OFF: return "안 하는 날"
        case .PAUSED: return "쉬는 중"
        case .BEFORE_START: return "시작 전"
        case .FUTURE: return ""
        }
    }
}

struct HabitRecentDay: Codable, Hashable {
    var date: String
    var state: HabitDayState
}

struct Habit: Codable, Hashable, Identifiable {
    var id: Int { habitId }

    var habitId: Int
    var name: String
    var emoji: String?
    /// 비어 있으면 매일 (daily = true)
    var repeatDays: [JavaDayOfWeek]
    var daily: Bool
    /// "08:30"
    var remindTime: String?
    var status: HabitStatus
    var startDate: String
    var sortOrder: Int
    /// 기준 날짜(date)에 해야 하는 날인지
    var scheduled: Bool
    /// 기준 날짜에 체크했는지
    var checked: Bool
    var state: HabitDayState
    /// 연속으로 지킨 "해야 하는 날" 수 (오늘 아직 안 했으면 어제까지)
    var streak: Int
    /// 최근 30일 실천률(%) — 해야 하는 날이 없었으면 nil
    var rate30: Int?
    /// 최근 7일 (오래된 날 → 기준 날짜)
    var recent7: [HabitRecentDay]
    /// 하는 시간대 "20:00" ~ "20:30" (선택) — 있으면 캘린더에 블록으로 보여요
    var startTime: String? = nil
    var endTime: String? = nil

    var hasTimeRange: Bool { !(startTime ?? "").isEmpty && !(endTime ?? "").isEmpty }
    var timeRangeLabel: String? {
        guard hasTimeRange, let s = startTime, let e = endTime else { return nil }
        return "\(s.prefix(5))–\(e.prefix(5))"
    }

    var isActive: Bool { status == .ACTIVE }
    var isPaused: Bool { status == .PAUSED }
    var title: String { (emoji.map { $0.isEmpty ? "" : "\($0) " } ?? "") + name }

    enum CodingKeys: String, CodingKey {
        case habitId, name, emoji, repeatDays, daily, remindTime, status, startDate, sortOrder
        case scheduled, checked, state, streak, rate30, recent7, startTime, endTime
    }
}

extension Habit {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        habitId = try c.decode(Int.self, forKey: .habitId)
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        emoji = try c.decodeIfPresent(String.self, forKey: .emoji)
        repeatDays = try c.decodeIfPresent([JavaDayOfWeek].self, forKey: .repeatDays) ?? []
        daily = try c.decodeIfPresent(Bool.self, forKey: .daily) ?? repeatDays.isEmpty
        remindTime = try c.decodeIfPresent(String.self, forKey: .remindTime)
        status = try c.decodeIfPresent(HabitStatus.self, forKey: .status) ?? .ACTIVE
        startDate = try c.decodeIfPresent(String.self, forKey: .startDate) ?? KST.today()
        sortOrder = try c.decodeIfPresent(Int.self, forKey: .sortOrder) ?? 0
        scheduled = try c.decodeIfPresent(Bool.self, forKey: .scheduled) ?? false
        checked = try c.decodeIfPresent(Bool.self, forKey: .checked) ?? false
        state = try c.decodeIfPresent(HabitDayState.self, forKey: .state) ?? .OFF
        streak = try c.decodeIfPresent(Int.self, forKey: .streak) ?? 0
        rate30 = try c.decodeIfPresent(Int.self, forKey: .rate30)
        recent7 = try c.decodeIfPresent([HabitRecentDay].self, forKey: .recent7) ?? []
        startTime = try c.decodeIfPresent(String.self, forKey: .startTime)
        endTime = try c.decodeIfPresent(String.self, forKey: .endTime)
    }
}

/// GET /habits?date= — 그날 습관 목록
struct HabitHome: Codable, Hashable {
    var goalCategoryId: Int
    var goalName: String
    var emoji: String?
    var date: String
    var today: String
    var scheduledCount: Int
    var doneCount: Int
    var habits: [Habit]
}

/// GET /habits/heatmap?from= — 습관별 잔디 (cells[i] = from + i 일)
struct HabitHeatmap: Codable, Hashable {
    struct Day: Codable, Hashable {
        var date: String
        var done: Int
        var scheduled: Int
        var level: Int
    }

    struct Row: Codable, Hashable, Identifiable {
        var id: Int { habitId }
        var habitId: Int
        var name: String
        var emoji: String?
        var status: HabitStatus
        var streak: Int
        var rate: Int?
        var doneDays: Int
        var cells: [HabitDayState]
    }

    var from: String
    var to: String
    var today: String
    var days: [Day]
    var habits: [Row]

    /// 습관별 날짜 → 상태
    func states(of habitId: Int) -> [String: HabitDayState] {
        guard let row = habits.first(where: { $0.habitId == habitId }) else { return [:] }
        var out: [String: HabitDayState] = [:]
        for (i, s) in row.cells.enumerated() { out[KST.addDays(from, i)] = s }
        return out
    }
}

/// 습관 추가 · 수정 입력. repeatDays 가 비어 있으면 매일
struct HabitInput: Hashable {
    var name: String
    var emoji: String
    var repeatDays: [JavaDayOfWeek]
    /// "HH:mm" — nil 이면 알림 없음
    var remindTime: String?
    /// 수정할 때 원래 알림이 있었는지 (지우려면 clearRemindTime 을 보내요)
    var hadRemind = false
    /// 하는 시간대 "HH:mm" (둘 다 있거나 둘 다 nil)
    var startTime: String? = nil
    var endTime: String? = nil
    /// 수정할 때 원래 시간대가 있었는지 (지우려면 clearTimeRange)
    var hadTimeRange = false

    var json: JSONValue {
        var o: [String: JSONValue] = [
            "name": .string(name.trimmingCharacters(in: .whitespaces)),
            "emoji": .string(emoji.trimmingCharacters(in: .whitespaces)),
            "repeatDays": .array(repeatDays.map { .string($0.rawValue) }),
        ]
        if let remindTime { o["remindTime"] = .string(remindTime) } else if hadRemind { o["clearRemindTime"] = .bool(true) }
        if let startTime, let endTime {
            o["startTime"] = .string(startTime)
            o["endTime"] = .string(endTime)
        } else if hadTimeRange {
            o["clearTimeRange"] = .bool(true)
        }
        return .object(o)
    }
}

/// 빠르게 고를 수 있는 기본 습관 — 누르면 바로 추가돼요 (웹 HABIT_SUGGESTIONS)
struct HabitSuggestion: Hashable, Identifiable {
    var id: String { name }
    let emoji: String
    let name: String
    var repeatDays: [JavaDayOfWeek] = []

    static let all: [HabitSuggestion] = [
        HabitSuggestion(emoji: "💊", name: "영양제 먹기"),
        HabitSuggestion(emoji: "💧", name: "물 2L 마시기"),
        HabitSuggestion(emoji: "🚿", name: "샤워하기"),
        HabitSuggestion(emoji: "🧹", name: "집안일 하기"),
        HabitSuggestion(emoji: "🦷", name: "치실하기"),
        HabitSuggestion(emoji: "📓", name: "일기 쓰기"),
        HabitSuggestion(emoji: "🧘", name: "스트레칭 5분"),
        HabitSuggestion(emoji: "🚭", name: "금연 지키기"),
        HabitSuggestion(emoji: "🍺", name: "금주 지키기"),
        HabitSuggestion(emoji: "🥗", name: "식단 지키기"),
        HabitSuggestion(emoji: "🌙", name: "자정 전에 자기"),
        HabitSuggestion(emoji: "🗑️", name: "분리수거", repeatDays: [.WEDNESDAY, .SUNDAY]),
    ]
}

enum HabitAPI {
    /// 그날 습관 목록 — date 가 nil 이면 오늘. 처음 부르면 서버가 기본 생활 습관 그릇을 만들어요
    static func home(date: String? = nil) async throws -> HabitHome? {
        try await API.get("/v1/habits\(date.map { "?date=\($0)" } ?? "")")
    }

    /// 잔디 — from(포함) ~ 오늘. 서버는 최대 400일까지 받아요
    static func heatmap(from: String? = nil) async throws -> HabitHeatmap? {
        try await API.get("/v1/habits/heatmap\(from.map { "?from=\($0)" } ?? "")")
    }

    static func create(_ input: HabitInput) async throws -> Habit? {
        try await API.post("/v1/habits", body: input.json)
    }

    static func update(habitId: Int, _ input: HabitInput) async throws -> Habit? {
        try await API.patch("/v1/habits/\(habitId)", body: input.json)
    }

    static func pause(habitId: Int) async throws {
        try await API.send("PATCH", "/v1/habits/\(habitId)/pause")
    }

    static func resume(habitId: Int) async throws {
        try await API.send("PATCH", "/v1/habits/\(habitId)/resume")
    }

    static func delete(habitId: Int) async throws {
        try await API.send("DELETE", "/v1/habits/\(habitId)")
    }

    /// 체크 · 체크 풀기 (그날)
    static func setChecked(habitId: Int, day: String, checked: Bool) async throws {
        try await API.send(checked ? "PUT" : "DELETE", "/v1/habits/\(habitId)/checks/\(day)")
    }
}

/// "매일" · "평일" · "주말" · "월·수·금" (웹 daysLabel)
enum HabitFormat {
    static func daysLabel(_ h: Habit) -> String { daysLabel(daily: h.daily, days: h.repeatDays) }

    static func daysLabel(daily: Bool, days: [JavaDayOfWeek]) -> String {
        let set = Set(days)
        if daily || set.isEmpty || set.count == 7 { return "매일" }
        let weekdays: Set<JavaDayOfWeek> = [.MONDAY, .TUESDAY, .WEDNESDAY, .THURSDAY, .FRIDAY]
        if set == weekdays { return "평일" }
        if set == [.SATURDAY, .SUNDAY] { return "주말" }
        return JavaDayOfWeek.allCases.filter { set.contains($0) }.map(\.label).joined(separator: "·")
    }
}
