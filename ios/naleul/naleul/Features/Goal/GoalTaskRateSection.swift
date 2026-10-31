// Features/Goal/GoalTaskRateSection.swift
//
// 웹 features/goal/ui/TaskRateSection.tsx — Task 실천률.
// 실제로 만든 Task 중 날짜가 지난 것을 얼마나 해냈는지 주별 막대로 (Swift Charts).
// 루틴은 위 히트맵에서 따로 보니까, 기본은 일회성 Task 만 (원하면 루틴 포함).

import SwiftUI
import Charts

@MainActor
struct GoalTaskRateSection: View {
    let tasks: [TimeBlockTask]

    private enum Kind: Hashable { case oneTime, all }

    @State private var kind: Kind = .oneTime

    private struct WeekBar: Identifiable {
        var id: String { week }
        let week: String
        let due: Int
        let done: Int
        /// 이번 주처럼 아직 진행 중인 주
        let current: Bool

        var rate: Double { due > 0 ? Double(done) / Double(due) : 0 }
        var label: String { current ? "이번 주" : "\(KST.monthOf(week))/\(KST.dayOfMonth(week))" }
    }

    /// 주(월요일 시작)마다: 그 주에 하기로 했던 Task 중 오늘까지 날짜가 온 것 → 몇 개 실천했나
    private func weekly(_ list: [TimeBlockTask], today: String) -> [WeekBar] {
        var byWeek: [String: (due: Int, done: Int)] = [:]
        for t in list {
            // 아직 안 온 날의 Task 는 실천률에 넣지 않아요
            guard let day = GoalDetailLogic.dayOf(t), day <= today else { continue }
            let w = KST.startOfWeek(day)
            var cur = byWeek[w] ?? (0, 0)
            cur.due += 1
            if GoalDetailLogic.isDone(t) { cur.done += 1 }
            byWeek[w] = cur
        }
        guard let first = byWeek.keys.sorted().first else { return [] }
        let thisWeek = KST.startOfWeek(today)
        var bars: [WeekBar] = []
        var w = first
        while w <= thisWeek {
            let v = byWeek[w] ?? (0, 0)
            bars.append(WeekBar(week: w, due: v.due, done: v.done, current: w == thisWeek))
            w = KST.addDays(w, 7)
        }
        return bars
    }

    var body: some View {
        if !tasks.isEmpty {
            content
        }
    }

    private var content: some View {
        let today = KST.today()
        let hasRoutine = tasks.contains { $0.sourceType == .ROUTINE }
        let list = kind == .all ? tasks : tasks.filter { $0.sourceType != .ROUTINE }
        let bars = weekly(list, today: today)
        let due = bars.reduce(0) { $0 + $1.due }
        let done = bars.reduce(0) { $0 + $1.done }
        let rate: Int? = due > 0 ? Int((Double(done) / Double(due) * 100).rounded()) : nil
        let recent = Array(bars.suffix(12))

        return SectionCard("Task 실천률", aside: { Text(rate != nil ? "\(done) / \(due)개" : "") }) {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .bottom, spacing: 12) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("지금까지 하기로 한 Task 중").font(.pt(13)).foregroundStyle(Color.ink3)
                        HStack(alignment: .firstTextBaseline, spacing: 2) {
                            Text(rate.map { String($0) } ?? "-").font(.ptMono(32, .bold)).foregroundStyle(Color.ink)
                            Text("%").font(.pt(16, .semibold)).foregroundStyle(Color.ink3)
                            Text("실천했어요").font(.pt(14, .medium)).foregroundStyle(Color.ink2).padding(.leading, 6)
                        }
                    }
                    Spacer(minLength: 0)
                    if hasRoutine {
                        HStack(spacing: 6) {
                            Chip("할 일만", selected: kind == .oneTime, size: .sm) { kind = .oneTime }
                            Chip("루틴 포함", selected: kind == .all, size: .sm) { kind = .all }
                        }
                    }
                }

                if recent.isEmpty {
                    Text("아직 날짜가 지난 \(kind == .all ? "Task" : "할 일")이 없어요. 날짜가 지나면 여기서 실천률을 보여드려요.")
                        .font(.pt(14))
                        .foregroundStyle(Color.ink3)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 24)
                        .frame(maxWidth: .infinity)
                        .background(Color.bgSunken, in: RoundedRectangle(cornerRadius: Radius.xxl))
                } else {
                    chart(recent)
                    Text("주마다 그 주에 하기로 한 Task 중 몇 개를 실천했는지예요. 오늘 이후 날짜의 Task는 빼고 계산해요."
                         + (recent.count > 6 ? " 좌우로 밀면 지난 주도 볼 수 있어요." : "")
                         + (bars.count > recent.count ? " 최근 12주까지 보여줘요." : ""))
                        .font(.pt(12))
                        .foregroundStyle(Color.ink3)
                }
            }
        }
    }

    private func barColor(_ r: Double) -> Color {
        if r >= 0.8 { return Color.brand }
        if r >= 0.5 { return Color.brand.opacity(0.6) }
        return Color.brand.opacity(0.35)
    }

    /// 한 화면에 보이는 주 수 — 넘으면 좌우로 밀어서 지난 주를 봐요
    private static let visibleWeeks = 6

    /// 주별 막대: 높이 = 그 주 실천률, 위 숫자 = 실천률(%) · 실천/해야 할 개수
    /// 80% 점선 = 목표를 해냈다고 보는 기준 (목표 결과 계산과 같은 기준)
    private func chart(_ recent: [WeekBar]) -> some View {
        let scrolls = recent.count > Self.visibleWeeks
        return Chart {
            ForEach(recent) { b in
                // 바탕 칸
                BarMark(x: .value("주", b.label), yStart: .value("시작", 0.0), yEnd: .value("끝", 100.0), width: .ratio(0.62))
                    .foregroundStyle(Color.subtle)
                    .cornerRadius(8)
                if b.due > 0 {
                    BarMark(x: .value("주", b.label), yStart: .value("시작", 0.0), yEnd: .value("실천률", max(b.rate * 100, 4)), width: .ratio(0.62))
                        .foregroundStyle(barColor(b.rate))
                        .cornerRadius(8)
                        .annotation(position: .top, spacing: 4) {
                            VStack(spacing: 0) {
                                Text("\(Int((b.rate * 100).rounded()))%")
                                    .font(.ptMono(12, .bold))
                                    .foregroundStyle(b.rate >= 0.8 ? Color.textBrand : Color.ink2)
                                Text("\(b.done)/\(b.due)").font(.ptMono(10)).foregroundStyle(Color.ink4)
                            }
                        }
                }
            }
            RuleMark(y: .value("기준", 80.0))
                .foregroundStyle(Color.success.opacity(0.7))
                .lineStyle(StrokeStyle(lineWidth: 1, dash: [3, 4]))
                .annotation(position: .top, alignment: .leading, spacing: 2) {
                    Text("80%").font(.pt(10, .semibold)).foregroundStyle(Color.success)
                }
        }
        .chartYScale(domain: 0.0...125.0)
        .chartYAxis(.hidden)
        .chartXAxis {
            AxisMarks { value in
                AxisValueLabel {
                    Text(value.as(String.self) ?? "")
                        .font(.ptMono(11, value.as(String.self) == "이번 주" ? .bold : .regular))
                        .foregroundStyle(value.as(String.self) == "이번 주" ? Color.textBrand : Color.ink3)
                }
            }
        }
        .chartScrollableAxes(scrolls ? .horizontal : [])
        .chartXVisibleDomain(length: scrolls ? Self.visibleWeeks : max(recent.count, 1))
        .chartScrollPosition(initialX: recent.last?.label ?? "")
        .frame(height: 210)
        .accessibilityLabel("주별 Task 실천률")
    }
}

#Preview {
    ScrollView {
        GoalTaskRateSection(tasks: GoalPreviewData.tasks.tasks).padding(16)
    }
    .canvasBackground()
}
