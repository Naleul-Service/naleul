// Features/Record/RecordModels.swift
//
// 웹 features/record/api.ts · journalText.ts · ui/pattern/format.ts
//
// 기록형(업무형) 목표 (백엔드 goal_mode = RECORD)
// 회사 업무처럼 "무엇을 이룰지"보다 "오늘 무엇을 했는지"를 쌓는 목표예요.
// 종료일·수치·마일스톤 없이 이름만으로 만들고, 기록은 "실제로 한 일"(ActualActivity)로 모아요.
//
// 웹 queryKey → iOS DataKey
//   ['record'] → .record · ['activities'] → .activities · ['goals'] → .goals

import SwiftUI

// MARK: - 한 일 빠르게 기록하기 (POST /activities/logs)

struct RecordActivityLogItem: Encodable, Hashable {
    var clientKey: String
    /// 사용자가 적은 문장 그대로 — 서버가 시각·걸린 시간을 읽어요
    var text: String
    /// 아래는 AI 해석 결과를 쓸 때만 (보낸 값이 문장보다 우선)
    var title: String?
    var date: String?
    /// "HH:mm"
    var startTime: String?
    var endTime: String?
    var minutes: Int?
    var goalCategoryId: Int?
    var tempGoalKey: String?
}

struct RecordActivityLogRequest: Encodable, Hashable {
    var items: [RecordActivityLogItem]
    /// 이번에 새로 만들 임시 목표 (Brain dump 의 TempGoalInput 과 같은 모양)
    var tempGoals: [TempGoalInput]?
}

struct RecordActivityLogResponse: Codable, Hashable {
    /// 저장된 기록 (요청 순서)
    var activities: [ActualActivity]
    /// 이번에 새로 만든 임시 목표 (Brain dump 의 CreatedTempGoal 과 같은 모양)
    var tempGoals: [CreatedTempGoal]
}

extension RecordActivityLogResponse {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        activities = try c.decodeIfPresent([ActualActivity].self, forKey: .activities) ?? []
        tempGoals = try c.decodeIfPresent([CreatedTempGoal].self, forKey: .tempGoals) ?? []
    }
}

// MARK: - 이 목표에 대한 나의 패턴 (GET /activities/goals/{id}/pattern)

enum RecordTimeBand: String, Codable, FallbackDecodable, Hashable, CaseIterable {
    case DAWN, MORNING, AFTERNOON, EVENING
    static let fallback: RecordTimeBand = .MORNING

    var label: String {
        switch self {
        case .DAWN: return "새벽"
        case .MORNING: return "오전"
        case .AFTERNOON: return "오후"
        case .EVENING: return "저녁"
        }
    }

    var range: String {
        switch self {
        case .DAWN: return "0–6시"
        case .MORNING: return "6–12시"
        case .AFTERNOON: return "12–18시"
        case .EVENING: return "18–24시"
        }
    }
}

struct RecordPeriodStat: Codable, Hashable {
    var start: String
    var end: String
    /// 완료한 Task 수
    var count: Int
    /// 시간을 알 수 있는 Task 의 시간 합
    var minutes: Int
    var activeDays: Int
}

struct RecordPatternSummary: Codable, Hashable {
    var totalCount: Int
    var totalMinutes: Int
    var activeDays: Int
    var firstDoneDate: String?
    /// 최근 4주 (오늘 포함 28일)
    var recent: RecordPeriodStat
    /// 그 전 4주
    var previous: RecordPeriodStat
    /// 완료한 Task 가 있는 주가 몇 주째 이어지는지 (이번 주가 비었으면 지난주까지)
    var weekStreak: Int
    var thisWeekDone: Bool
}

struct RecordDayStat: Codable, Hashable {
    var date: String
    var count: Int
    var minutes: Int
}

struct RecordWeekStat: Codable, Hashable {
    var weekStart: String
    var count: Int
    var minutes: Int
    var cumulativeCount: Int
    var cumulativeMinutes: Int
}

struct RecordRhythm: Codable, Hashable {
    /// 잔디 시작일 (월요일)
    var heatmapStart: String
    /// 완료가 있는 날만
    var days: [RecordDayStat]
    /// 최근 12주, 오래된 것부터 (빈 주 포함)
    var weeks: [RecordWeekStat]
}

struct RecordWhenCell: Codable, Hashable {
    var dayOfWeek: JavaDayOfWeek
    var band: RecordTimeBand
    var count: Int
    var minutes: Int
}

struct RecordBandStat: Codable, Hashable {
    var band: RecordTimeBand
    var count: Int
    var minutes: Int
}

struct RecordWhen: Codable, Hashable {
    var cells: [RecordWhenCell]
    var bands: [RecordBandStat]
    var peakDay: JavaDayOfWeek?
    var peakBand: RecordTimeBand?
    /// 시각을 몰라서 표에서 뺀 Task 수 (시간 미정으로 완료)
    var untimedCount: Int
}

/// 완료한 루틴 Task 기준. 시간은 실제 시각 → 계획 시간 → 루틴 기본 시간 순
struct RecordRoutineStat: Codable, Hashable {
    var routineId: Int
    var routineName: String
    var emoji: String?
    var minutes: Int
    var completedCount: Int
    /// 최근 4주
    var recentMinutes: Int
    var recentCount: Int
    /// 그 전 4주
    var previousMinutes: Int
    var previousCount: Int
}

struct RecordRoutines: Codable, Hashable {
    /// 쌓인 시간이 많은 순 (완료가 없는 루틴도 포함). 목표에 루틴이 없으면 비어 있어요 → 카드를 숨겨요
    var items: [RecordRoutineStat]
    /// 최근 4주에 그 전 4주보다 눈에 띄게 더 한 루틴
    var risingRoutineId: Int?
}

/// 이 목표의 완료한 Task(일반 + 루틴) 기준 패턴. 직접 남긴 "한 일" 기록은 섞지 않아요 (업무 일지에서 함께 보여줘요)
struct RecordPattern: Codable, Hashable {
    var summary: RecordPatternSummary
    var rhythm: RecordRhythm
    var when: RecordWhen
    var routines: RecordRoutines
}

// MARK: - 업무 일지 (GET /activities/goals/{id}/journal)

/// TASK: 완료한 Task · RECORD: 직접 남긴 기록
enum RecordJournalItemType: String, Codable, FallbackDecodable, Hashable {
    case TASK, RECORD
    static let fallback: RecordJournalItemType = .TASK
}

struct RecordJournalItem: Codable, Hashable {
    var type: RecordJournalItemType
    var id: Int
    var title: String
    var emoji: String?
    /// 모르면 nil (시간 미정으로 완료한 Task)
    var startAt: String?
    var endAt: String?
    var minutes: Int?
    /// 루틴 Task 인지
    var routine: Bool
    var memo: String?

    /// 목록 키 (Task 와 기록의 id 가 겹칠 수 있어서 종류를 붙여요)
    var key: String { "\(type.rawValue)-\(id)" }
}

struct RecordJournalDay: Codable, Hashable, Identifiable {
    var id: String { date }
    var date: String
    var taskCount: Int
    var recordCount: Int
    /// 시간을 알 수 있는 항목의 합
    var minutes: Int
    /// 시각순 (시각 없는 Task 는 뒤로)
    var items: [RecordJournalItem]
}

struct RecordJournal: Codable, Hashable {
    /// 최근 날짜부터
    var days: [RecordJournalDay]
    /// 더 오래된 일지가 있으면 다음 쪽을 부를 날짜 (없으면 nil)
    var nextBefore: String?
}

// MARK: - API

enum RecordAPI {
    /// 업무형 목표 만들기 → 성공하면 .goals 다시 불러오기
    /// 업무형은 회사 업무 · 취업 준비 · 동아리처럼 종류가 다양해서 종류(goalType · goalSubType)를 보내지 않아요 (서버에서 선택)
    static func createRecordGoal(name: String, emoji: String?, colorId: Int?,
                                 description: String?) async throws -> GoalCategory? {
        let body: JSONValue = [
            "name": .string(name),
            "emoji": .of(emoji),
            "colorId": .of(colorId),
            "description": .of(description),
        ]
        return try await API.post("/v1/goal-categories/record", body: body)
    }

    /// 임시 목표 → 기록형 목표로 그대로 두기 (구체화 없이). 웹 useKeepAsRecord
    /// 성공 문구: "업무형 목표로 바꿨어요." · 끝나면 .goals 다시 불러오기
    static func keepAsRecord(goalId: Int) async throws -> GoalCategory? {
        try await API.patch("/v1/goal-categories/\(goalId)/keep-as-record")
    }

    /// 한 일 기록. 성공하면 .activities · .record (임시 목표가 생겼으면 .goals) 다시 불러오기
    static func logActivities(_ body: RecordActivityLogRequest) async throws -> RecordActivityLogResponse? {
        try await API.request("POST", "/v1/activities/logs", encodable: body)
    }

    /// 직접 남긴 기록 지우기
    static func deleteRecord(activityId: Int) async throws {
        try await API.send("DELETE", "/v1/activities/\(activityId)")
    }

    static func pattern(goalId: Int) async throws -> RecordPattern? {
        try await API.get("/v1/activities/goals/\(goalId)/pattern")
    }

    /// 업무 일지 — 2주씩, before 로 과거를 이어 불러요
    static func journal(goalId: Int, before: String?) async throws -> RecordJournal? {
        try await API.get("/v1/activities/goals/\(goalId)/journal?days=14" + (before.map { "&before=\($0)" } ?? ""))
    }

    private static var seq = 0

    /// 화면에서 쓰는 고유 키 (웹 `r${Date.now().toString(36)}${seq.toString(36)}`)
    @MainActor
    static func newClientKey() -> String {
        defer { seq += 1 }
        let now = String(Int(Date().timeIntervalSince1970 * 1000), radix: 36)
        return "r\(now)\(String(seq, radix: 36))"
    }
}

// MARK: - 업무 일지 → 글 (웹 journalText.ts)

enum RecordJournalText {
    /// "· 09:00–09:30 코드 리뷰 (30분) — 메모" / "· 메일 정리"
    private static func itemLine(_ i: RecordJournalItem) -> String {
        var time = ""
        if let s = i.startAt, let e = i.endAt { time = "\(KST.hm(s))–\(KST.hm(e)) " }
        let dur = (i.minutes ?? 0) > 0 ? " (\(KST.formatDuration(i.minutes ?? 0)))" : ""
        let memo = (i.memo?.isEmpty == false) ? " — \(i.memo ?? "")" : ""
        return "· \(time)\(i.title)\(dur)\(memo)"
    }

    private static func dayBlock(_ d: RecordJournalDay) -> String {
        let total = d.minutes > 0 ? " · \(KST.formatDuration(d.minutes))" : ""
        return (["■ \(KST.formatMonthDay(d.date))\(total)"] + d.items.map(itemLine)).joined(separator: "\n")
    }

    /// 업무 일지를 메신저·문서에 바로 붙여 넣을 수 있는 글로 바꿔요.
    /// 날짜는 오래된 것부터 (보고서처럼 위에서 아래로 읽히게).
    static func text(_ title: String, _ days: [RecordJournalDay]) -> String {
        let ordered = days.sorted { $0.date < $1.date }
        return ["[\(title)]", "", ordered.map(dayBlock).joined(separator: "\n\n")].joined(separator: "\n")
    }
}

// MARK: - 패턴 카드 도우미 (웹 ui/pattern/format.ts)

enum RecordPatternFormat {
    /// 칸 안에 들어갈 짧은 표기: 45 → "45분", 150 → "2.5시간"
    static func shortDuration(_ minutes: Int) -> String {
        if minutes < 60 { return "\(minutes)분" }
        let h = (Double(minutes) / 60 * 10).rounded() / 10
        return "\(PatternFormat.hours1(h))시간"
    }

    /// 1~4 단계 (0 은 기록 없음). 가장 많이 한 날을 기준으로 나눠요 (기록형은 목표 시간이 없어서 내 기록끼리 비교)
    static func levelOf(_ value: Int, max: Int) -> Int {
        if value <= 0 || max <= 0 { return 0 }
        let r = Double(value) / Double(max)
        return r > 0.75 ? 4 : r > 0.5 ? 3 : r > 0.25 ? 2 : 1
    }

    /// 단계별 색 (나의 패턴 히트맵과 같은 파란 계열: 1·2·4·5 단계)
    static func levelColor(_ level: Int) -> Color {
        switch level {
        case 1: return .heat1
        case 2: return .heat2
        case 3: return .heat4
        case 4: return .heat5
        default: return .subtle
        }
    }
}

// MARK: - 미리보기 샘플

enum RecordSampleData {
    static var goal: GoalCategory {
        let json = """
        {"goalCategoryId": 7, "goalCategoryName": "나를 업무", "goalCategoryStatus": "IN_PROGRESS",
         "colorCode": "3D5AFE", "emoji": "💼", "goalMode": "RECORD", "goalType": "WORK", "goalSubType": "COMPANY_WORK",
         "generalCategories": []}
        """
        return try! JSONDecoder().decode(GoalCategory.self, from: Data(json.utf8))
    }

    static var goals: [GoalCategory] {
        let json = """
        [{"goalCategoryId": 7, "goalCategoryName": "나를 업무", "goalCategoryStatus": "IN_PROGRESS", "emoji": "💼",
          "goalMode": "RECORD", "generalCategories": []},
         {"goalCategoryId": 3, "goalCategoryName": "토익 900", "goalCategoryStatus": "IN_PROGRESS", "emoji": "📚",
          "generalCategories": []}]
        """
        return try! JSONDecoder().decode([GoalCategory].self, from: Data(json.utf8))
    }

    static var journal: RecordJournal {
        let today = KST.today()
        let y = KST.addDays(today, -1)
        let d3 = KST.addDays(today, -3)
        let json = """
        {"days": [
          {"date": "\(today)", "taskCount": 2, "recordCount": 1, "minutes": 150, "items": [
            {"type": "TASK", "id": 11, "title": "주간 회의", "emoji": "👥", "startAt": "\(today)T10:00:00",
             "endAt": "\(today)T11:00:00", "minutes": 60, "routine": true, "memo": null},
            {"type": "RECORD", "id": 11, "title": "기획서 초안 작성", "emoji": null, "startAt": "\(today)T13:00:00",
             "endAt": "\(today)T14:30:00", "minutes": 90, "routine": false, "memo": "목차까지"},
            {"type": "TASK", "id": 12, "title": "메일 정리", "emoji": null, "startAt": null, "endAt": null,
             "minutes": null, "routine": false, "memo": null}
          ]},
          {"date": "\(y)", "taskCount": 1, "recordCount": 0, "minutes": 30, "items": [
            {"type": "TASK", "id": 13, "title": "코드 리뷰", "emoji": "🧑‍💻", "startAt": "\(y)T16:00:00",
             "endAt": "\(y)T16:30:00", "minutes": 30, "routine": false, "memo": null}
          ]},
          {"date": "\(d3)", "taskCount": 0, "recordCount": 1, "minutes": 0, "items": [
            {"type": "RECORD", "id": 14, "title": "고객 문의 답변", "emoji": null, "startAt": null, "endAt": null,
             "minutes": null, "routine": false, "memo": null}
          ]}
        ], "nextBefore": "\(KST.addDays(today, -14))"}
        """
        return try! JSONDecoder().decode(RecordJournal.self, from: Data(json.utf8))
    }

    static var pattern: RecordPattern {
        let today = KST.today()
        let thisWeek = KST.startOfWeek(today)
        let heatStart = KST.addDays(thisWeek, -15 * 7)
        // 잔디: 최근 16주 중 띄엄띄엄
        var days: [String] = []
        var d = heatStart
        var i = 0
        while d <= today {
            if i % 3 != 0 && i % 7 < 5 {
                days.append(#"{"date": "\#(d)", "count": \#(1 + i % 4), "minutes": \#(30 * (1 + i % 3))}"#)
            }
            d = KST.addDays(d, 1)
            i += 1
        }
        // 주차: 최근 12주
        var weeks: [String] = []
        var cum = 0
        var cumMin = 0
        for w in 0..<12 {
            let count = [2, 3, 0, 4, 5, 3, 6, 4, 7, 5, 8, 3][w]
            cum += count
            cumMin += count * 45
            weeks.append(#"{"weekStart": "\#(KST.addDays(thisWeek, (w - 11) * 7))", "count": \#(count), "minutes": \#(count * 45), "cumulativeCount": \#(cum), "cumulativeMinutes": \#(cumMin)}"#)
        }
        // 언제: 요일 × 시간대
        let dayNames = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
        let bands = ["DAWN", "MORNING", "AFTERNOON", "EVENING"]
        var cells: [String] = []
        for (di, dn) in dayNames.enumerated() {
            for (bi, b) in bands.enumerated() {
                let c = di < 5 ? [0, 3, 5, 1][bi] + di % 2 : [0, 1, 0, 2][bi]
                cells.append(#"{"dayOfWeek": "\#(dn)", "band": "\#(b)", "count": \#(c), "minutes": \#(c * 40)}"#)
            }
        }
        let json = """
        {
          "summary": {"totalCount": \(cum), "totalMinutes": \(cumMin), "activeDays": 38, "firstDoneDate": "\(heatStart)",
            "recent": {"start": "\(KST.addDays(today, -27))", "end": "\(today)", "count": 23, "minutes": 1035, "activeDays": 15},
            "previous": {"start": "\(KST.addDays(today, -55))", "end": "\(KST.addDays(today, -28))", "count": 18, "minutes": 810, "activeDays": 12},
            "weekStreak": 4, "thisWeekDone": true},
          "rhythm": {"heatmapStart": "\(heatStart)", "days": [\(days.joined(separator: ","))], "weeks": [\(weeks.joined(separator: ","))]},
          "when": {"cells": [\(cells.joined(separator: ","))],
            "bands": [{"band": "DAWN", "count": 0, "minutes": 0}, {"band": "MORNING", "count": 17, "minutes": 680},
                      {"band": "AFTERNOON", "count": 27, "minutes": 1080}, {"band": "EVENING", "count": 9, "minutes": 360}],
            "peakDay": "TUESDAY", "peakBand": "AFTERNOON", "untimedCount": 3},
          "routines": {"items": [
            {"routineId": 1, "routineName": "주간 회의", "emoji": "👥", "minutes": 600, "completedCount": 10,
             "recentMinutes": 240, "recentCount": 4, "previousMinutes": 180, "previousCount": 3},
            {"routineId": 2, "routineName": "코드 리뷰", "emoji": "🧑‍💻", "minutes": 300, "completedCount": 10,
             "recentMinutes": 150, "recentCount": 5, "previousMinutes": 60, "previousCount": 2},
            {"routineId": 3, "routineName": "회고 쓰기", "emoji": null, "minutes": 0, "completedCount": 0,
             "recentMinutes": 0, "recentCount": 0, "previousMinutes": 0, "previousCount": 0}
          ], "risingRoutineId": 2}
        }
        """
        return try! JSONDecoder().decode(RecordPattern.self, from: Data(json.utf8))
    }

    /// 완료한 Task 가 아직 없을 때
    static var emptyPattern: RecordPattern {
        var p = pattern
        p.summary.totalCount = 0
        p.summary.totalMinutes = 0
        p.rhythm.days = []
        return p
    }
}
