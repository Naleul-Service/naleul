// Features/Pattern/PatternModels.swift
//
// 웹 features/pattern/types.ts · api.ts · format.ts · ui/Emphasis.tsx 를 옮겼어요.
// 백엔드 PatternReportResponse (domain/pattern/dto) 와 1:1 이에요.
//
// 카드 하나가 계산에 실패하면 그 필드가 null 이라 카드 필드는 전부 Optional 이에요.
// 목록(배열)도 혹시 null 이 와서 화면 전체가 디코딩 실패로 안 뜨는 일이 없게 Optional 로 받고,
// 화면에서는 `?? []` 로 써요.

import SwiftUI

// MARK: - enum

/// 분석 기간 (최근 4주 / 전체)
enum PatternPeriod: String, Codable, FallbackDecodable, Hashable, CaseIterable {
    case RECENT_4W, ALL
    static let fallback: PatternPeriod = .RECENT_4W

    var label: String {
        switch self {
        case .RECENT_4W: return "최근 4주"
        case .ALL: return "전체"
        }
    }
}

/// 평일 / 주말 / 매일
enum PatternDayGroup: String, Codable, FallbackDecodable, Hashable {
    case WEEKDAY, WEEKEND, ALL
    static let fallback: PatternDayGroup = .ALL

    var label: String {
        switch self {
        case .WEEKDAY: return "평일"
        case .WEEKEND: return "주말"
        case .ALL: return "매일"
        }
    }
}

/// 히트맵 칸: 값 있음 / 고정 시간(빗금) / 블록 없음
enum PatternHeatCellState: String, Codable, FallbackDecodable, Hashable {
    case VALUE, FIXED, EMPTY
    static let fallback: PatternHeatCellState = .EMPTY
}

enum PatternSuggestionType: String, Codable, FallbackDecodable, Hashable {
    case ROUTINE_TIME, ROUTINE_DAY, unknown
    static let fallback: PatternSuggestionType = .unknown
}

enum PatternAiStatus: String, Codable, FallbackDecodable, Hashable {
    case AI, TEMPLATE
    static let fallback: PatternAiStatus = .TEMPLATE
}

// MARK: - 리포트

struct PatternReport: Codable, Hashable {
    var period: PatternPeriod
    var range: PatternRange
    var timeBlockCount: Int
    var evaluatedCount: Int
    /// 이 리포트를 계산한 시각 (한국 벽시계 "2026-10-09T13:42:00")
    var generatedAt: String
    var aiStatus: PatternAiStatus
    /// 계산에 쓴 기록의 버전 (서버 캐시 확인용)
    var dataVersion: String?
    var style: PatternStyle?
    var summary: PatternSummary?
    var heatmap: PatternHeatmap?
    var weekday: PatternWeekday?
    var weekly: PatternWeekly?
    var timeUsage: PatternTimeUsage?
    var blockKeeping: PatternBlockKeeping?
    var routines: PatternRoutines?
    var taskHabits: PatternTaskHabits?
    var suggestion: PatternSuggestionBar?
}

struct PatternRange: Codable, Hashable {
    var start: String
    var end: String
    var days: Int
}

/// text 안의 **…** 는 강조
struct PatternInsight: Codable, Hashable {
    var key: String
    var icon: String
    var text: String
}

struct PatternStyleProgress: Codable, Hashable {
    var evaluated: Int
    var activeDays: Int
    var requiredTasks: Int
    var requiredDays: Int
    var remainingTasks: Int
    var remainingDays: Int
}

struct PatternStyle: Codable, Hashable {
    /// MORNING · DAYTIME · EVENING · EVEN (화면에선 안 써서 문자열 그대로)
    var timeStrength: String?
    /// WEEKDAY_FOCUSED · WEEKEND_RECHARGE · STEADY · BURST
    var rhythm: String?
    var title: String?
    var highlight: String?
    var insights: [PatternInsight]?
    /// 스타일을 보여주려면 더 필요한 Task 수 (충분하면 nil)
    var remainingForStyle: Int?
    /// 스타일을 열기까지의 진행 — "Task 15개 · 4일 더"
    var progress: PatternStyleProgress?
}

struct PatternSummary: Codable, Hashable {
    var executionRate: Int?
    var firstWeekDelta: Int?
    var bestStreak: Int
    var currentStreak: Int
    var avgStartDelayMinutes: Int?
    var morningDelayMinutes: Int?
    var eveningDelayMinutes: Int?
    var aiPlacementEditRate: Int?
    var aiPlacementKeptPerTen: Int?
    var engineBlockCount: Int
    var insufficient: [String]?
}

struct PatternHeatCell: Codable, Hashable {
    var hour: Int
    var state: PatternHeatCellState
    var rate: Int?
    var samples: Int?
    /// 1~5 — 가장 많이 한 칸(maxCount)에 견준 진하기
    var level: Int?
    /// 그 요일 · 시간에 실제로 한 Task · 루틴 수 (계획한 시간과 상관없이)
    var count: Int?
}

struct PatternHeatRow: Codable, Hashable {
    var dayOfWeek: JavaDayOfWeek
    var cells: [PatternHeatCell]?
}

struct PatternTimeWindow: Codable, Hashable {
    var dayGroup: PatternDayGroup
    var startHour: Int
    /// 포함하지 않음 (19~22 = 19, 20, 21시)
    var endHour: Int
    var rate: Int
    var samples: Int
}

struct PatternHeatmap: Codable, Hashable {
    var hours: [Int]?
    var rows: [PatternHeatRow]?
    /// 가장 많이 한 칸의 횟수
    var maxCount: Int?
    var fixedLabel: String?
    var goldenTime: PatternTimeWindow?
    var weakTime: PatternTimeWindow?
}

struct PatternWeekdayDay: Codable, Hashable {
    var dayOfWeek: JavaDayOfWeek
    var rate: Int?
    var samples: Int
}

struct PatternWeekday: Codable, Hashable {
    var average: Int?
    var days: [PatternWeekdayDay]?
    var lowest: JavaDayOfWeek?
    var lowestMissed: String?
    var insight: String?
}

struct PatternWeeklyPoint: Codable, Hashable {
    var index: Int
    var weekStart: String
    var rate: Int?
    var samples: Int
}

struct PatternWeekly: Codable, Hashable {
    var points: [PatternWeeklyPoint]?
    var maxIndex: Int?
}

struct PatternGoalUsage: Codable, Hashable {
    var goalCategoryId: Int
    var name: String
    var emoji: String?
    var colorCode: String?
    var actualHoursPerWeek: Double
    var plannedHoursPerWeek: Double
    var planRatio: Int?
    var temporary: Bool
    var newTemporary: Bool
}

struct PatternTimeUsage: Codable, Hashable {
    var weeklyTotalHours: Double
    var goals: [PatternGoalUsage]?
}

struct PatternFrequentMove: Codable, Hashable {
    var routineId: Int?
    var title: String
    var emoji: String?
    var fromLabel: String
    var toLabel: String
    var dayChange: Bool
    var description: String
    var count: Int
}

struct PatternBlockKeeping: Codable, Hashable {
    var total: Int
    var onTime: Int?
    var late: Int?
    var missed: Int?
    var frequentMoves: [PatternFrequentMove]?
    /// 블록(루틴 · Task)별 제시간 / 늦게 / 놓침 횟수 — 많은 순
    var items: [PatternBlockKeepItem]?
}

struct PatternBlockKeepItem: Codable, Hashable, Identifiable {
    var key: String
    var title: String
    var emoji: String?
    var goalName: String?
    var goalColorCode: String?
    var routine: Bool
    var onTime: Int
    var late: Int
    var missed: Int

    var id: String { key }
    var total: Int { onTime + late + missed }
}

struct PatternKeptRoutine: Codable, Hashable {
    var routineId: Int
    var title: String
    var emoji: String?
    var rate: Int
    var samples: Int
}

struct PatternMissedRoutine: Codable, Hashable {
    var routineId: Int
    var routineName: String
    var emoji: String?
    var label: String
    var description: String
    var rate: Int
    var samples: Int
}

struct PatternRoutines: Codable, Hashable {
    var kept: [PatternKeptRoutine]?
    var missed: [PatternMissedRoutine]?
}

struct PatternTaskHabits: Codable, Hashable {
    var taskCount: Int
    /// 백엔드 Double (소수 가능)
    var deadlineLeadDays: Double?
    var deadlineLabel: String?
    var postponedRate: Int?
    var postponedTopGoal: String?
    var brainDumpPerWeek: Double?
    var brainDumpAvgItems: Double?
    var brainDumpUsualDay: JavaDayOfWeek?
    var brainDumpUsualLabel: String?
    var brainDumpUsualRange: String?
}

struct PatternSuggestionChange: Codable, Hashable {
    /// "HH:mm"
    var startTime: String?
    var endTime: String?
    var days: [JavaDayOfWeek]?
}

struct PatternSuggestionItem: Codable, Hashable, Identifiable {
    var id: String { key }
    var key: String
    var type: PatternSuggestionType
    var routineId: Int
    var routineName: String
    var before: PatternSuggestionChange
    var after: PatternSuggestionChange
}

struct PatternSuggestionBar: Codable, Hashable {
    var text: String
    var expectedGain: Int?
    var items: [PatternSuggestionItem]?
}

// MARK: - API (웹 pattern/api.ts)

enum PatternAPI {
    /// GET /patterns?period= — 서버가 캐시해 두지만 기록이 바뀌면 바로 다시 계산해요
    /// refresh: "지금 다시 계산" — 캐시를 무시하고 새로 계산
    static func report(_ period: PatternPeriod, refresh: Bool = false) async throws -> PatternReport? {
        try await API.get("/v1/patterns?period=\(period.rawValue)" + (refresh ? "&refresh=true" : ""))
    }

    /// key 에 ":" 가 들어가요 (ROUTINE_TIME:31:12:40) → 경로에 넣을 때 인코딩 (웹 encodeURIComponent)
    static func suggestionPath(_ key: String, _ action: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-_.!~*'()")
        let encoded = key.addingPercentEncoding(withAllowedCharacters: allowed) ?? key
        return "/v1/patterns/suggestions/\(encoded)/\(action)"
    }

    /// 제안 하나 반영 (응답은 화면에서 쓰지 않아요)
    static func applySuggestion(key: String) async throws {
        try await API.send("POST", suggestionPath(key, "apply"))
    }

    /// 제안 하나 숨기기 (일주일)
    static func dismissSuggestion(key: String) async throws {
        try await API.send("POST", suggestionPath(key, "dismiss"))
    }
}

// MARK: - format (웹 pattern/format.ts)

enum PatternFormat {
    /// "월"
    static func dayShort(_ d: JavaDayOfWeek) -> String { d.label }

    private static func pad(_ n: Int) -> String { n < 10 ? "0\(n)" : "\(n)" }

    /// "2026-10-01" → "10.01"
    static func dotDate(_ ymd: String) -> String {
        let chars = Array(ymd)
        guard chars.count >= 10 else { return ymd }
        return String(chars[5...6]) + "." + String(chars[8...9])
    }

    /// 1234 → "1,234" (웹 toLocaleString)
    static func number(_ n: Int) -> String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        return f.string(from: NSNumber(value: n)) ?? "\(n)"
    }

    /// 헤더 문구: "10.01 – 11.18 · 49일, TimeBlock 412개 기록 기반"
    static func rangeLabel(_ r: PatternReport) -> String {
        "\(dotDate(r.range.start)) – \(dotDate(r.range.end)) · \(r.range.days)일, TimeBlock \(number(r.timeBlockCount))개 기록 기반"
    }

    /// "10월 9일 13:42" — 서버가 준 한국 시간 문자열 그대로
    static func updatedLabel(_ iso: String) -> String {
        let parts = iso.split(separator: "T", maxSplits: 1).map(String.init)
        guard parts.count == 2, parts[0].count >= 10 else { return iso }
        let d = parts[0]
        return "\(KST.monthOf(d))월 \(KST.dayOfMonth(d))일 \(parts[1].prefix(5))"
    }

    /// 골든/취약 시간: "평일 19–22시"
    static func windowLabel(_ w: PatternTimeWindow) -> String {
        "\(w.dayGroup.label) \(pad(w.startHour))–\(pad(w.endHour))시"
    }

    /// +12분 / −3분 / 0분 (− 는 웹과 같은 유니코드 빼기 기호)
    static func signedMinutes(_ m: Int) -> String {
        if m > 0 { return "+\(m)분" }
        if m < 0 { return "−\(abs(m))분" }
        return "0분"
    }

    /// 0.0 → "0", 7.5 → "7.5"
    static func hours1(_ h: Double) -> String {
        if h == h.rounded() { return "\(Int(h))" }
        return String(format: "%.1f", h)
    }

    /// 인사이트 아이콘 키 → 이모지
    static let insightIcon: [String: String] = [
        "moon": "🌙", "sun": "☀️", "sunrise": "🌅", "calendar": "📅",
        "alarm": "⏰", "chart-down": "📉", "fire": "🔥", "sparkle": "✨",
    ]

    /// 실행 스타일 카드의 연한 파랑 (웹도 늘 어두운 카드 위에 #8c9bff 고정값을 써요)
    static let styleAccent = Color.textBrand   // 실행 스타일 카드(AI 테두리) 강조색
}

// MARK: - 강조 (웹 ui/Emphasis.tsx)

/// 백엔드 문장의 **…** 부분을 굵게(+색) 바꾼 Text 를 만들어요.
/// (사용자가 입력한 루틴 이름이 들어가므로 마크다운으로 해석하지 않고 ** 만 직접 찾아요)
enum PatternEmphasis {
    private static let re = try! NSRegularExpression(pattern: #"\*\*(.+?)\*\*"#)

    static func text(_ s: String, color: Color? = nil) -> Text {
        let ns = s as NSString
        var out = Text("")
        var last = 0
        for m in re.matches(in: s, range: NSRange(location: 0, length: ns.length)) {
            if m.range.location > last {
                out = out + Text(ns.substring(with: NSRange(location: last, length: m.range.location - last)))
            }
            var strong = Text(ns.substring(with: m.range(at: 1))).bold()
            if let color { strong = strong.foregroundColor(color) }
            out = out + strong
            last = m.range.location + m.range.length
        }
        if last < ns.length {
            out = out + Text(ns.substring(from: last))
        }
        return out
    }
}

// MARK: - 고정 시간 빗금 (웹 HATCH: 135° line / subtle 줄무늬)

struct PatternHatchFill: View {
    var body: some View {
        Canvas { ctx, size in
            ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color.subtle))
            var p = Path()
            var x: CGFloat = -size.height
            while x < size.width + size.height {
                p.move(to: CGPoint(x: x, y: size.height))
                p.addLine(to: CGPoint(x: x + size.height, y: 0))
                x += 7
            }
            ctx.stroke(p, with: .color(Color.line), lineWidth: 3)
        }
    }
}

// MARK: - 미리보기 샘플

enum PatternSampleData {
    static let reportJSON = """
    {
      "period": "RECENT_4W",
      "range": {"start": "2026-09-11", "end": "2026-10-08", "days": 28},
      "timeBlockCount": 1412, "evaluatedCount": 186,
      "generatedAt": "2026-10-09T13:42:10", "aiStatus": "AI", "dataVersion": "v1",
      "style": {
        "timeStrength": "EVENING", "rhythm": "STEADY",
        "title": "저녁에 강한 꾸준형", "highlight": "저녁에 강한",
        "insights": [
          {"key": "a", "icon": "moon", "text": "저녁 7시 이후 실행률이 **87%**로 가장 높아요"},
          {"key": "b", "icon": "calendar", "text": "주말에도 평일만큼 **꾸준히** 해요"},
          {"key": "c", "icon": "alarm", "text": "아침 블록은 평균 **12분** 늦게 시작해요"}
        ],
        "remainingForStyle": null, "progress": null
      },
      "summary": {
        "executionRate": 78, "firstWeekDelta": 12, "bestStreak": 9, "currentStreak": 3,
        "avgStartDelayMinutes": 8, "morningDelayMinutes": 12, "eveningDelayMinutes": -3,
        "aiPlacementEditRate": 24, "aiPlacementKeptPerTen": 8, "engineBlockCount": 40, "insufficient": []
      },
      "heatmap": {
        "hours": [6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23],
        "rows": \(heatRows),
        "maxCount": 6,
        "fixedLabel": "회사 업무",
        "goldenTime": {"dayGroup": "WEEKDAY", "startHour": 19, "endHour": 22, "rate": 91, "samples": 40},
        "weakTime": {"dayGroup": "WEEKEND", "startHour": 7, "endHour": 9, "rate": 42, "samples": 12}
      },
      "weekday": {
        "average": 76,
        "days": [
          {"dayOfWeek": "MONDAY", "rate": 82, "samples": 30}, {"dayOfWeek": "TUESDAY", "rate": 79, "samples": 28},
          {"dayOfWeek": "WEDNESDAY", "rate": 80, "samples": 31}, {"dayOfWeek": "THURSDAY", "rate": 74, "samples": 26},
          {"dayOfWeek": "FRIDAY", "rate": 58, "samples": 25}, {"dayOfWeek": "SATURDAY", "rate": 77, "samples": 22},
          {"dayOfWeek": "SUNDAY", "rate": null, "samples": 2}
        ],
        "lowest": "FRIDAY", "lowestMissed": "운동",
        "insight": "금요일에는 **운동**을 자주 놓쳐요"
      },
      "weekly": {
        "points": [
          {"index": 1, "weekStart": "2026-09-07", "rate": 66, "samples": 30},
          {"index": 2, "weekStart": "2026-09-14", "rate": 72, "samples": 40},
          {"index": 3, "weekStart": "2026-09-21", "rate": null, "samples": 2},
          {"index": 4, "weekStart": "2026-09-28", "rate": 85, "samples": 44},
          {"index": 5, "weekStart": "2026-10-05", "rate": 78, "samples": 20}
        ],
        "maxIndex": 4
      },
      "timeUsage": {
        "weeklyTotalHours": 14.5,
        "goals": [
          {"goalCategoryId": 1, "name": "토익 900", "emoji": "📚", "colorCode": "3D5AFE",
           "actualHoursPerWeek": 6.5, "plannedHoursPerWeek": 8, "planRatio": 81, "temporary": false, "newTemporary": false},
          {"goalCategoryId": 2, "name": "건강", "emoji": "💪", "colorCode": "12B76A",
           "actualHoursPerWeek": 5, "plannedHoursPerWeek": 4, "planRatio": 125, "temporary": false, "newTemporary": false},
          {"goalCategoryId": 3, "name": "블로그", "emoji": null, "colorCode": null,
           "actualHoursPerWeek": 3, "plannedHoursPerWeek": 0, "planRatio": null, "temporary": true, "newTemporary": true}
        ]
      },
      "blockKeeping": {
        "total": 1240, "onTime": 61, "late": 24, "missed": 15,
        "frequentMoves": [
          {"routineId": 11, "title": "아침 러닝", "emoji": "🏃", "fromLabel": "07:00", "toLabel": "19:00",
           "dayChange": false, "description": "평일 아침 → 저녁으로 자주 옮겨요", "count": 6}
        ],
        "items": [
          {"key": "R12", "title": "영어 단어", "emoji": "📖", "goalName": "토익 900", "goalColorCode": "3D5AFE",
           "routine": true, "onTime": 14, "late": 4, "missed": 2},
          {"key": "R11", "title": "아침 러닝", "emoji": "🏃", "goalName": "건강", "goalColorCode": "12B76A",
           "routine": true, "onTime": 3, "late": 3, "missed": 6},
          {"key": "T보고서 정리", "title": "보고서 정리", "emoji": null, "goalName": null, "goalColorCode": null,
           "routine": false, "onTime": 1, "late": 2, "missed": 1}
        ]
      },
      "routines": {
        "kept": [{"routineId": 12, "title": "영어 단어", "emoji": "📖", "rate": 92, "samples": 20}],
        "missed": [{"routineId": 11, "routineName": "아침 러닝", "emoji": "🏃", "label": "금요일 아침 러닝",
                    "description": "금 07시 · 10번 중 4번", "rate": 40, "samples": 10}]
      },
      "taskHabits": {
        "taskCount": 64, "deadlineLeadDays": 1.5, "deadlineLabel": "마감 하루 반 전에 끝내요",
        "postponedRate": 18, "postponedTopGoal": "토익 900",
        "brainDumpPerWeek": 3.2, "brainDumpAvgItems": 4.5,
        "brainDumpUsualDay": "SUNDAY", "brainDumpUsualLabel": "일요일 밤", "brainDumpUsualRange": "21–23시"
      },
      "suggestion": {
        "text": "아침 러닝을 **저녁 7시**로 옮기면 실행률이 **+18%p** 오를 것 같아요",
        "expectedGain": 18,
        "items": [
          {"key": "ROUTINE_TIME:11:19:00", "type": "ROUTINE_TIME", "routineId": 11, "routineName": "아침 러닝",
           "before": {"startTime": "07:00", "endTime": "07:40"}, "after": {"startTime": "19:00", "endTime": "19:40"}},
          {"key": "ROUTINE_DAY:12", "type": "ROUTINE_DAY", "routineId": 12, "routineName": "영어 단어",
           "before": {"days": ["MONDAY", "FRIDAY"]}, "after": {"days": ["MONDAY", "THURSDAY"]}}
        ]
      }
    }
    """

    /// 7요일 × 6~23시 칸
    private static var heatRows: String {
        let days = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
        let rows = days.enumerated().map { di, d -> String in
            let cells = (6...23).map { h -> String in
                if di < 5, (9...17).contains(h) { return #"{"hour": \#(h), "state": "FIXED"}"# }
                if (h + di) % 4 == 0 { return #"{"hour": \#(h), "state": "EMPTY", "count": 0}"# }
                let count = 1 + ((h * 7 + di * 11) % 6)   // 1~6 (maxCount 6)
                let level = max(1, min(5, Int((5.0 * Double(count) / 6.0).rounded(.up))))
                return #"{"hour": \#(h), "state": "VALUE", "count": \#(count), "level": \#(level)}"#
            }
            return #"{"dayOfWeek": "\#(d)", "cells": [\#(cells.joined(separator: ","))]}"#
        }
        return "[" + rows.joined(separator: ",") + "]"
    }

    static var report: PatternReport {
        try! JSONDecoder().decode(PatternReport.self, from: Data(reportJSON.utf8))
    }

    /// 기록이 하나도 없을 때 (스타일 잠김)
    static var emptyReport: PatternReport {
        var r = report
        r.evaluatedCount = 0
        r.style = PatternStyle(insights: [],
                               progress: PatternStyleProgress(evaluated: 3, activeDays: 2, requiredTasks: 18,
                                                              requiredDays: 6, remainingTasks: 15, remainingDays: 4))
        return r
    }
}

#Preview("빗금 · 강조") {
    VStack(alignment: .leading, spacing: 12) {
        PatternHatchFill().frame(width: 120, height: 28).clipShape(RoundedRectangle(cornerRadius: 4))
        PatternEmphasis.text("금요일에는 **운동**을 자주 놓쳐요", color: Color.brand).font(.pt(14))
        Text(PatternFormat.rangeLabel(PatternSampleData.report)).font(.pt(13))
    }
    .padding(16)
}
