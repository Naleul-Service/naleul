// Models/BrainDumpModels.swift
//
// 웹 features/brain-dump/types.ts · api.ts · parseLine.ts
// "할 일을 적으면 AI 가 정리해서 캘린더에 넣어 주는" Task 추가 기능의 모델 (백엔드 BE-5 DTO 와 1:1).
// 시간은 "HH:mm" 또는 "HH:mm:ss" (LocalTime), 날짜는 "YYYY-MM-DD"

import Foundation

/// ON: 그날 하는 일 · DUE: 그날까지 · NONE: 날짜 없음
enum DateType: String, Codable, FallbackDecodable, Hashable, CaseIterable {
    case ON, DUE, NONE
    static let fallback: DateType = .NONE
}

enum GoalMatchType: String, Codable, FallbackDecodable, Hashable {
    case EXISTING, TEMP, NONE
    static let fallback: GoalMatchType = .NONE
}

/// 오늘 남은 Task 추가 개수 (Free 하루 2개)
struct TaskQuota: Codable, Hashable {
    var premium: Bool
    /// Pro 는 nil (무제한)
    var dailyLimit: Int?
    var usedToday: Int
    var remaining: Int?
}

// MARK: 1단계 → 2단계 (AI 해석)

struct ParseItemRequest: Encodable, Hashable {
    var clientKey: String
    var text: String
    var date: String?
    var dateType: DateType?
    var startTime: String?
    var endTime: String?
    /// 사용자가 정한 걸리는 시간(분) — AI 추정보다 우선
    var estimatedMinutes: Int?
}

struct GoalLink: Codable, Hashable {
    var type: GoalMatchType
    var goalCategoryId: Int?
    var goalName: String?
    var goalEmoji: String?
    var goalColorCode: String?
    var temporaryGoal: Bool
    var generalCategoryId: Int?
    var generalCategoryName: String?
    var milestoneId: Int?
    var milestoneTitle: String?
    var tempKey: String?
    var reason: String?

    // init(from:) 을 extension 에 직접 썼으니 키 목록도 직접 적어 둬요 (encode 자동 생성에도 쓰여요)
    enum CodingKeys: String, CodingKey {
        case type, goalCategoryId, goalName, goalEmoji, goalColorCode, temporaryGoal
        case generalCategoryId, generalCategoryName, milestoneId, milestoneTitle, tempKey, reason
    }
}

extension GoalLink {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        type = try c.decodeIfPresent(GoalMatchType.self, forKey: .type) ?? .NONE
        goalCategoryId = try c.decodeIfPresent(Int.self, forKey: .goalCategoryId)
        goalName = try c.decodeIfPresent(String.self, forKey: .goalName)
        goalEmoji = try c.decodeIfPresent(String.self, forKey: .goalEmoji)
        goalColorCode = try c.decodeIfPresent(String.self, forKey: .goalColorCode)
        temporaryGoal = try c.decodeIfPresent(Bool.self, forKey: .temporaryGoal) ?? false
        generalCategoryId = try c.decodeIfPresent(Int.self, forKey: .generalCategoryId)
        generalCategoryName = try c.decodeIfPresent(String.self, forKey: .generalCategoryName)
        milestoneId = try c.decodeIfPresent(Int.self, forKey: .milestoneId)
        milestoneTitle = try c.decodeIfPresent(String.self, forKey: .milestoneTitle)
        tempKey = try c.decodeIfPresent(String.self, forKey: .tempKey)
        reason = try c.decodeIfPresent(String.self, forKey: .reason)
    }
}

struct ParsedItem: Codable, Hashable {
    var clientKey: String
    var title: String
    var dateType: DateType
    var scheduledDate: String?
    var dueDate: String?
    var startTime: String?
    var endTime: String?
    var estimatedMinutes: Int
    var priority: TaskPriority?
    var kind: TaskKind?
    var goal: GoalLink
}

struct TempGoalInfo: Codable, Hashable {
    var tempKey: String
    var name: String
    var emoji: String?
}

struct ParseResponse: Codable, Hashable {
    var items: [ParsedItem]
    var tempGoals: [TempGoalInfo]
    /// true 면 AI 없이 규칙으로만 해석 (목표 연결이 비어 있을 수 있음)
    var aiFailed: Bool
    /// true 면 오늘 AI 정리 횟수를 다 써서 기본 정리로만 나눴어요 (aiFailed 도 true)
    var aiLimited: Bool?
}

// MARK: 2단계 → 3단계 (저장 + 배치)

struct ConfirmItem: Encodable, Hashable {
    var clientKey: String
    var title: String
    var dateType: DateType
    var scheduledDate: String?
    var dueDate: String?
    var startTime: String?
    var endTime: String?
    var estimatedMinutes: Int?
    var priority: TaskPriority?
    var kind: TaskKind?
    var goalCategoryId: Int?
    var generalCategoryId: Int?
    var milestoneId: Int?
    var tempGoalKey: String?
    /// 업무형 목표의 주제 (WorkTopic) — 2단계 줄에서 바로 골라요 (2026-10)
    var workTopicId: Int? = nil
}

struct TempGoalInput: Codable, Hashable {
    var tempKey: String
    var name: String
    var emoji: String?
    var colorId: Int?
}

struct BrainDumpConfirmRequest: Encodable, Hashable {
    var items: [ConfirmItem]
    var tempGoals: [TempGoalInput]
    /// true: 시간을 정하지 않은 Task 는 저장만 하고(pending) 배치는 점선 미리보기 → 확인 후에
    var placeLater: Bool?
}

struct Placement: Codable, Hashable {
    var taskId: Int
    var clientKey: String
    var title: String
    var date: String
    var start: String
    var end: String
    var reason: String?
    var locked: Bool
}

struct BrainDumpTaskRef: Codable, Hashable {
    var taskId: Int
    var clientKey: String
    var title: String
    var date: String?
}

struct BrainDumpConflict: Codable, Hashable {
    var clientKey: String
    var title: String
    var start: String
    var end: String
    var conflictTaskId: Int?
    var conflictTitle: String?
}

struct CreatedTempGoal: Codable, Hashable {
    var tempKey: String
    var goalCategoryId: Int
    var name: String
    var emoji: String?
    var colorCode: String?
}

struct ConfirmResponse: Codable, Hashable {
    /// false 면 아무것도 저장하지 않음 → conflicts 를 보여주고 시간을 고쳐 다시 요청
    var created: Bool
    var placements: [Placement]
    var movedToOtherDays: [Placement]
    var unscheduled: [BrainDumpTaskRef]
    /// placeLater 로 저장만 한 Task — 이 id 들로 배치 미리보기를 받아 "이대로 진행할까요?"를 물어요
    var pending: [BrainDumpTaskRef]?
    var conflicts: [BrainDumpConflict]
    var tempGoals: [CreatedTempGoal]

    enum CodingKeys: String, CodingKey {
        case created, placements, movedToOtherDays, unscheduled, pending, conflicts, tempGoals
    }
}

extension ConfirmResponse {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        created = try c.decodeIfPresent(Bool.self, forKey: .created) ?? false
        placements = try c.decodeIfPresent([Placement].self, forKey: .placements) ?? []
        movedToOtherDays = try c.decodeIfPresent([Placement].self, forKey: .movedToOtherDays) ?? []
        unscheduled = try c.decodeIfPresent([BrainDumpTaskRef].self, forKey: .unscheduled) ?? []
        pending = try c.decodeIfPresent([BrainDumpTaskRef].self, forKey: .pending)
        conflicts = try c.decodeIfPresent([BrainDumpConflict].self, forKey: .conflicts) ?? []
        tempGoals = try c.decodeIfPresent([CreatedTempGoal].self, forKey: .tempGoals) ?? []
    }
}

// MARK: - API

enum BrainDumpAPI {
    /// 오늘 남은 Task 추가 개수
    static func quota() async throws -> TaskQuota? {
        try await API.get("/v1/tasks/quota")
    }

    /// 1단계 → 2단계: AI 해석 (최대 15초 정도 걸릴 수 있어요)
    static func parse(_ items: [ParseItemRequest]) async throws -> ParseResponse? {
        try await API.post("/v1/brain-dump/parse", body: ["items": JSONValue.from(items)])
    }

    /// 2단계 → 3단계: 저장 + 배치. created 면 .timetable · .usage (임시 목표가 있으면 .goals) 다시 불러오기
    static func confirm(_ body: BrainDumpConfirmRequest) async throws -> ConfirmResponse? {
        try await API.request("POST", "/v1/brain-dump/confirm", encodable: body)
    }

    /// 화면에서 쓰는 고유 키 (clientKey)
    static func newClientKey() -> String { UUID().uuidString.lowercased() }
}

// MARK: - parseLine (한 줄을 바로 읽어서 칩 채우기)

/// Brain dump 한 줄을 Enter 하는 순간 바로 읽어서 칩을 채워요 (서버 KoreanTaskTextParser 와 같은 규칙의 일부).
///  - 시각: "저녁 8시", "오후 3시 반", "15:00", "3시~4시", "14:00-15:30"
///  - 소요 시간: "3시간", "1시간 반", "90분"
///  - 날짜: "오늘", "내일", "모레"
struct ParsedLine: Hashable {
    var date: String?
    /// "HH:mm"
    var startTime: String?
    var endTime: String?
    var minutes: Int?
}

enum LineParser {
    // "1시간"의 "1시"를 시각으로 읽지 않도록 '시' 뒤에 '간'이 오면 제외
    private static let hm = #"(오전|오후|저녁|밤|아침)?\s*(\d{1,2})(?::(\d{2})|\s*시(?!간)(?:\s*(\d{1,2})\s*분|\s*(반))?)"#
    private static let timeRange = try! NSRegularExpression(pattern: "\(hm)\\s*[~\\-]\\s*\(hm)")
    private static let timeOne = try! NSRegularExpression(pattern: hm)
    // "7시 30분"의 "30분"은 시각이라 소요 시간으로 보지 않아요 ('시' 바로 뒤의 분은 제외)
    // (iOS 정규식은 길이가 정해지지 않은 lookbehind 를 못 써서 공백을 최대 3개까지로 정했어요)
    private static let duration = try! NSRegularExpression(pattern: #"(\d{1,2})\s*시간(\s*반)?|(?<!시\s{0,3})(\d{2,3})\s*분"#)
    private static let relative = try! NSRegularExpression(pattern: "(오늘|내일|모레)")
    private static let eveningRe = try! NSRegularExpression(pattern: "저녁|밤")

    private static func pad(_ n: Int) -> String { n < 10 ? "0\(n)" : "\(n)" }

    /// 매치의 i 번째 그룹 (없으면 nil)
    private static func group(_ m: NSTextCheckingResult, _ i: Int, in s: String) -> String? {
        guard i < m.numberOfRanges, let r = Range(m.range(at: i), in: s) else { return nil }
        return String(s[r])
    }

    private static func toTime(_ ampm: String?, _ h: String?, _ colonMin: String?, _ min: String?, _ half: String?,
                               after: Int?, evening: Bool) -> Int? {
        guard let h, var hour = Int(h) else { return nil }
        let minute = colonMin.flatMap(Int.init) ?? min.flatMap(Int.init) ?? (half != nil ? 30 : 0)
        if hour > 24 || minute > 59 { return nil }
        if let ampm, ["오후", "저녁", "밤"].contains(ampm), hour < 12 { hour += 12 }
        else if ampm == nil, colonMin == nil, hour >= 1, hour <= 6 { hour += 12 } // "3시 미팅" → 15시
        else if ampm == nil, evening, hour >= 7, hour < 12 { hour += 12 }          // "저녁 약속 7시" → 19시
        if let after, hour * 60 < after, hour + 12 < 24 { hour += 12 }            // "3시~4시" 뒤 시간 보정
        if hour == 24 { hour = 0 }
        return hour * 60 + minute
    }

    private static func fmt(_ m: Int?) -> String? {
        guard let m else { return nil }
        return "\(pad(m / 60)):\(pad(m % 60))"
    }

    static func parse(_ text: String, today: String) -> ParsedLine {
        var rest = text
        var out = ParsedLine()
        let ns = { (s: String) in NSRange(s.startIndex..., in: s) }

        if let rel = relative.firstMatch(in: rest, range: ns(rest)), let word = group(rel, 1, in: rest) {
            out.date = word == "내일" ? KST.addDays(today, 1) : (word == "모레" ? KST.addDays(today, 2) : today)
        }

        // 소요 시간 먼저 (그래야 "2시간"을 2시로 읽지 않아요)
        if let d = duration.firstMatch(in: rest, range: ns(rest)) {
            if let hours = group(d, 1, in: rest), let h = Int(hours) {
                out.minutes = h * 60 + (group(d, 2, in: rest) != nil ? 30 : 0)
            } else if let mins = group(d, 3, in: rest) {
                out.minutes = Int(mins)
            }
            if let r = Range(d.range, in: rest) { rest.replaceSubrange(r, with: " ") }
        }
        if let m = out.minutes, m < 5 || m > 480 { out.minutes = nil }

        let evening = eveningRe.firstMatch(in: text, range: ns(text)) != nil
        if let r = timeRange.firstMatch(in: rest, range: ns(rest)) {
            let g = { (i: Int) in group(r, i, in: rest) }
            let s = toTime(g(1), g(2), g(3), g(4), g(5), after: nil, evening: evening)
            let e = s == nil ? nil : toTime(g(6) ?? g(1), g(7), g(8), g(9), g(10), after: s, evening: evening)
            out.startTime = fmt(s)
            if let s, let e, e > s { out.endTime = fmt(e) }
        } else if let one = timeOne.firstMatch(in: rest, range: ns(rest)) {
            let g = { (i: Int) in group(one, i, in: rest) }
            // 숫자만 있는 건 시각이 아니에요 ("3곳") — '시'나 ':' 가 있어야 해요
            if g(3) != nil || (g(0) ?? "").contains("시") {
                out.startTime = fmt(toTime(g(1), g(2), g(3), g(4), g(5), after: nil, evening: evening))
            }
        }
        return out
    }
}
