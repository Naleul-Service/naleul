// Features/Calendar/GoalTimeStatsView.swift
//
// 목표별 실천 시간 (2026-10) — 기간 동안 완료한 Task 시간을 목표별로 나눈 얇은 막대. 참고용이라 작게.
// 웹 원본: features/timetable/ui/GoalTimeStats.tsx
//  - 칸을 누르면 위에 말풍선으로 어떤 목표인지 (다시 누르면 닫혀요)
//  - 일간 · 3일 · 주간: 격자 아래(오늘 할 일 아래) · 월간: 달력 아래
//  - Task 를 완료 · 수정하면(.timetable) 다시 불러와요

import SwiftUI

struct GoalTimeStatsView: View {
    let start: String
    let end: String
    /// 분석 view_mode (day · three_day · week · month)
    let viewMode: String
    /// "오늘" · "이번 주" · "10월" … 합계 앞에 붙여요
    let periodLabel: String

    @State private var data: GoalTimeResponse?
    @State private var failed = false
    @State private var selected: Int?
    @State private var grown = false
    /// 이벤트 중복 막기 (기간마다)
    @State private var seen: Set<String> = []

    private var current: GoalTimeResponse? {
        guard let data, data.startDate == start, data.endDate == end else { return nil }
        return data
    }
    private var goals: [GoalTimeEntry] { current?.goals ?? [] }
    private var total: Int { current?.totalMinutes ?? 0 }
    private var rangeDays: Int { KST.diffDays(start, end) + 1 }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("목표별 실천 시간").font(.pt(13, .semibold)).foregroundStyle(Color.ink2)
                Spacer(minLength: 0)
                Text(summary).font(.pt(12)).monospacedDigit().foregroundStyle(Color.ink3)
            }
            bar
            if current != nil && total == 0 {
                Text("완료한 Task가 생기면 목표별로 나눠 보여드려요").font(.pt(12)).foregroundStyle(Color.ink4)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        // 말풍선이 카드 밖으로 조금 나와도 잘리지 않게 clip 하지 않아요
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.surface))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.line, lineWidth: 1))
        .reloadOn([.timetable], id: "\(start)_\(end)") { await load() }
    }

    private var summary: String {
        if current == nil { return failed ? "불러오지 못했어요" : "…" }
        return "\(periodLabel) \(Self.duration(total))"
    }

    private var bar: some View {
        GeometryReader { geo in
            let width = geo.size.width
            ZStack(alignment: .topLeading) {
                Capsule().fill(Color.progressTrack).frame(height: 8)
                if total > 0 {
                    HStack(spacing: 2) {
                        ForEach(Array(goals.enumerated()), id: \.offset) { i, g in
                            Rectangle()
                                .fill(color(g))
                                .frame(width: max(segmentWidth(i, width: width), 3), height: 8)
                                .opacity(selected == nil || selected == i ? 1 : 0.4)
                        }
                    }
                    .frame(width: width, height: 8, alignment: .leading)
                    .clipShape(Capsule())
                    // 처음 보일 때 왼쪽에서 쭉 차올라요
                    .scaleEffect(x: grown ? 1 : 0.001, y: 1, anchor: .leading)
                    .animation(.spring(response: 0.6, dampingFraction: 0.85), value: grown)
                    // 막대가 얇아서 위아래로 넉넉히 누를 수 있게 — 누른 x 로 어느 칸인지 찾아요
                    Color.clear
                        .frame(width: width, height: 32)
                        .offset(y: -12)
                        .contentShape(Rectangle())
                        .onTapGesture(coordinateSpace: .local) { p in tap(index(at: p.x, width: width)) }
                        .accessibilityElement()
                        .accessibilityLabel(goals.map { "\(label($0)) \(Self.duration($0.minutes))" }.joined(separator: ", "))
                }
                if let i = selected, i < goals.count {
                    bubble(goals[i])
                        .fixedSize()
                        // 칸 가운데 위 — 가장자리면 안쪽으로 붙여요
                        .alignmentGuide(.leading) { d in -min(max(center(i, width: width) - d.width / 2, 0), max(width - d.width, 0)) }
                        .alignmentGuide(.top) { d in d.height + 8 }
                        .transition(.scale(scale: 0.9, anchor: .bottom).combined(with: .opacity))
                        .zIndex(2)
                        .allowsHitTesting(false)
                }
            }
            .animation(.spring(response: 0.28, dampingFraction: 0.8), value: selected)
        }
        .frame(height: 8)
        .zIndex(1)
        .onAppear { grown = true }
        .onChange(of: start) { _, _ in selected = nil }
    }

    private func segmentWidth(_ i: Int, width: CGFloat) -> CGFloat {
        let gaps = CGFloat(max(goals.count - 1, 0)) * 2
        return max(width - gaps, 0) * CGFloat(goals[i].minutes) / CGFloat(max(total, 1))
    }

    private func index(at x: CGFloat, width: CGFloat) -> Int {
        var acc: CGFloat = 0
        for i in goals.indices {
            acc += segmentWidth(i, width: width) + 2
            if x < acc { return i }
        }
        return max(goals.count - 1, 0)
    }

    private func bubble(_ g: GoalTimeEntry) -> some View {
        let pct = total > 0 ? Int((Double(g.minutes) / Double(total) * 100).rounded()) : 0
        return VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 6) {
                Circle().fill(color(g)).frame(width: 8, height: 8)
                Text(label(g)).font(.pt(12, .semibold)).lineLimit(1)
            }
            Text("\(Self.duration(g.minutes)) · \(pct)% · Task \(g.taskCount)개").font(.pt(12)).monospacedDigit().opacity(0.8)
        }
        .foregroundStyle(Color.textInverse)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(Color.bgInverse, in: RoundedRectangle(cornerRadius: 8))
        .frame(maxWidth: 220, alignment: .leading)
    }

    private func center(_ i: Int, width: CGFloat) -> CGFloat {
        guard total > 0 else { return 0 }
        let before = goals.prefix(i).reduce(0) { $0 + $1.minutes }
        return width * (CGFloat(before) + CGFloat(goals[i].minutes) / 2) / CGFloat(total)
    }

    private func color(_ g: GoalTimeEntry) -> Color { g.goalCategoryId == nil ? Color.borderSubtle : Color.goal(g.goalColorCode) }

    private func label(_ g: GoalTimeEntry) -> String {
        guard g.goalCategoryId != nil else { return "목표 없음" }
        let name = g.goalCategoryName ?? "이름 없는 목표"
        if let e = g.goalEmoji, !e.isEmpty { return "\(e) \(name)" }
        return name
    }

    private func tap(_ i: Int) {
        if selected == i {
            selected = nil
            return
        }
        selected = i
        let g = goals[i]
        let key = "seg:\(start)_\(end):\(g.goalCategoryId.map(String.init) ?? "none")"
        guard !seen.contains(key) else { return }
        seen.insert(key)
        CAL.track("calendar.goal_time_segment_view", [
            "view_mode": viewMode, "range_days": rangeDays,
            "goal_category_id": g.goalCategoryId, "has_goal": g.goalCategoryId != nil,
            "minutes": g.minutes,
            "share_percent": total > 0 ? Int((Double(g.minutes) / Double(total) * 100).rounded()) : 0,
            "rank": i + 1, "trigger": "tap",
        ])
    }

    @MainActor
    private func load() async {
        if CAL.isPreview {
            data = GoalTimeResponse(startDate: start, endDate: end, totalMinutes: 200, goals: [
                GoalTimeEntry(goalCategoryId: 1, goalCategoryName: "운동", goalEmoji: "🏃", goalColorCode: "#4BC47C", minutes: 120, taskCount: 3),
                GoalTimeEntry(goalCategoryId: 2, goalCategoryName: "영어", goalEmoji: "📚", goalColorCode: "#5586F0", minutes: 60, taskCount: 2),
                GoalTimeEntry(goalCategoryId: nil, goalCategoryName: nil, goalEmoji: nil, goalColorCode: nil, minutes: 20, taskCount: 1),
            ])
            return
        }
        do {
            let r = try await TimetableAPI.goalTime(start: start, end: end)
            data = r
            failed = r == nil
            if let r {
                let key = "view:\(start)_\(end)"
                if !seen.contains(key) {
                    seen.insert(key)
                    CAL.track("calendar.goal_time_view", [
                        "view_mode": viewMode, "range_days": rangeDays,
                        "goal_count": r.goals.filter { $0.goalCategoryId != nil }.count,
                        "total_minutes": r.totalMinutes, "has_data": r.totalMinutes > 0,
                    ])
                }
            }
        } catch {
            failed = true
        }
    }

    /// 80 → "1시간 20분", 45 → "45분", 120 → "2시간"
    static func duration(_ minutes: Int) -> String {
        let h = minutes / 60
        let m = minutes % 60
        if h == 0 { return "\(m)분" }
        return m > 0 ? "\(h)시간 \(m)분" : "\(h)시간"
    }
}

#Preview {
    GoalTimeStatsView(start: KST.today(), end: KST.today(), viewMode: "day", periodLabel: "오늘")
        .padding()
}
