// Features/Pattern/PatternAnalytics.swift
//
// 나의 패턴 이벤트 (Tracking Plan 분류 "나의 패턴" · 웹 pattern.* 과 같은 이름).
// 카드들은 기간을 몰라서 PT.period 를 같이 붙여요 (PatternScreen 이 기간을 바꿀 때 맞춰 둬요).
// 그래프 만지기는 "카드마다 방문당 한 번" — 탭이 보일 때(pageview) PT.resetVisit() 로 새로 세요.
//
//   PT.dead("summary_tile")      // 눌러도 반응 없는 곳
//   PT.chart("heatmap")          // 그래프를 처음 누름

import Foundation
import SwiftUI

@MainActor
enum PT {
    /// 미리보기에서는 보내지 않아요
    static var isPreview: Bool { ProcessInfo.processInfo.environment["XCODE_RUNNING_FOR_PREVIEWS"] == "1" }

    static func track(_ event: String, _ props: EventProps = [:]) {
        if isPreview { return }
        Analytics.track(event, props)
    }

    /// 요청 실패의 HTTP 상태 (0 = 네트워크)
    static func status(_ error: Error) -> Int { (error as? APIError)?.httpStatus ?? 0 }

    /// 지금 보고 있는 기간 (RECENT_4W · ALL)
    static var period: PatternPeriod = .RECENT_4W

    /// 나의 패턴을 열기 직전에 정해 둬요 (nudge) → pageview 의 entry_source (없으면 tab_bar)
    static var pendingEntry: String?

    private static var seen: Set<String> = []
    static func resetVisit() { seen = [] }

    /// 방문당 한 번만
    static func once(_ key: String, _ fn: () -> Void) {
        guard !seen.contains(key) else { return }
        seen.insert(key)
        fn()
    }

    /// 그래프를 처음 누름 (heatmap · weekday · weekly · time_usage) — 카드마다 방문당 한 번
    static func chart(_ kind: String) {
        once("chart:\(kind)") {
            track("pattern.chart_interact", ["chart_kind": kind, "period": period.rawValue])
        }
    }

    /// 눌렀지만 아무 일도 없는 곳 (summary_tile · style_sentence · time_usage_row · moved_routine · kept_routine · missed_block · golden_time)
    static func dead(_ target: String) {
        track("pattern.dead_click", ["dead_target": target, "period": period.rawValue])
    }

    /// 마지막으로 계산한 뒤 지난 분 (generatedAt 은 시간대 없는 한국 시간일 수 있어요)
    static func minutesSince(_ iso: String) -> Int? {
        guard let date = parse(iso) else { return nil }
        return max(0, Int(Date().timeIntervalSince(date) / 60))
    }

    private static func parse(_ s: String) -> Date? {
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = iso.date(from: s) { return d }
        iso.formatOptions = [.withInternetDateTime]
        if let d = iso.date(from: s) { return d }
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "Asia/Seoul")
        // 소수 초는 떼고 읽어요
        let base = String(s.prefix(19))
        f.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        return f.date(from: base)
    }

    /// 제안 항목 종류, 쉼표로 (ROUTINE_TIME,ROUTINE_DAY)
    static func suggestionTypes(_ items: [PatternSuggestionItem]) -> String {
        var out: [String] = []
        for i in items where !out.contains(i.type.rawValue) { out.append(i.type.rawValue) }
        return out.joined(separator: ",")
    }

    private static let allSections = ["summary", "heatmap", "weekday", "weekly", "time_usage", "block_keeping", "routines", "task_habits"]

    /// '기록이 쌓이면 보여드려요'로 비어 보인 카드 — 웹 emptySectionsOf 와 같은 조건
    static func emptySections(_ r: PatternReport) -> String {
        if r.evaluatedCount == 0 { return allSections.joined(separator: ",") }
        var empty: [String] = []
        if r.summary?.executionRate == nil { empty.append("summary") }
        if !(r.heatmap?.rows ?? []).contains(where: { ($0.cells ?? []).contains { $0.state == .VALUE } }) { empty.append("heatmap") }
        if !(r.weekday?.days ?? []).contains(where: { $0.rate != nil }) { empty.append("weekday") }
        if (r.weekly?.points ?? []).filter({ $0.rate != nil }).count < 2 { empty.append("weekly") }
        if (r.timeUsage?.goals ?? []).isEmpty { empty.append("time_usage") }
        if !((r.blockKeeping?.total ?? 0) > 0 && r.blockKeeping?.onTime != nil) { empty.append("block_keeping") }
        if (r.routines?.kept ?? []).isEmpty && (r.routines?.missed ?? []).isEmpty { empty.append("routines") }
        if r.taskHabits?.postponedRate == nil && r.taskHabits?.brainDumpPerWeek == nil { empty.append("task_habits") }
        return empty.joined(separator: ",")
    }
}

extension PatternSection {
    /// section 값 — 웹 empty_sections 와 같은 이름
    var analyticsKey: String {
        switch self {
        case .usage: return "time_usage"
        case .block: return "block_keeping"
        case .habits: return "task_habits"
        default: return rawValue
        }
    }
}

extension PatternKeepKind {
    /// keep_kind 값 (on_time · late · missed)
    var analyticsKey: String { self == .onTime ? "on_time" : rawValue }
}

extension View {
    /// 눌러도 반응 없는 곳 → pattern.dead_click
    func patternDeadTap(_ target: String) -> some View {
        contentShape(Rectangle()).onTapGesture { PT.dead(target) }
    }

    /// 그래프를 누름 → pattern.chart_interact (방문당 한 번). 다른 탭 동작은 그대로 받아요
    func patternChartTap(_ kind: String) -> some View {
        simultaneousGesture(TapGesture().onEnded { PT.chart(kind) })
    }
}
