// Features/Goal/GoalProgressChart.swift
//
// 웹 features/goal/ui/progress/ProgressChart.tsx — 목표 진행 꺾은선 (웹은 SVG 직접, 여기선 Swift Charts).
//
// - plan  : 계획선 (점선) — 날짜 사이 값은 이어서(보간) 읽어요
// - actual: 실제 기록 (실선 + 점) — 기록한 날만 값이 있어요
// - 오늘 세로선 + 지금 위치 큰 점 + 목표값 가로선
// - 손가락으로 누르고 끌면 그날의 계획 · 기록 값을 그래프 위에 보여줘요 (웹 호버 말풍선)
//
// x 축은 "시작일로부터 며칠째"(Int) 로 그려요 — 날짜를 Date 로 바꾸면 기기 시간대에 따라 하루씩 밀릴 수 있어서요.

import SwiftUI
import Charts

struct GoalChartPoint: Hashable {
    var date: String
    var value: Double
    var label: String? = nil
}

struct GoalChartSeries: Hashable, Identifiable {
    enum Kind: Hashable { case plan, actual }
    var id: String { key }
    var key: String
    var name: String
    var kind: Kind
    var points: [GoalChartPoint]
}

struct GoalChartMarker: Hashable {
    var date: String? = nil
    var value: Double
    var label: String
}

@MainActor
struct GoalProgressChart: View {
    let series: [GoalChartSeries]
    let from: String
    let to: String
    let today: String
    /// 목표값 가로선
    var target: GoalChartMarker? = nil
    /// 지금 위치 (오늘 또는 마지막 기록 날) — date 필요
    var current: GoalChartMarker? = nil
    let format: (Double) -> String
    var height: CGFloat = 260
    /// y축을 0부터 (Task 개수처럼 누적값)
    var zeroBased = false
    /// 그래프를 눌러 값 말풍선을 처음 볼 때 (앰플리튜드 chart_interact — 화면당 1회는 부르는 쪽에서 걸러요)
    var onInteract: (() -> Void)? = nil

    @State private var selected: Int? = nil

    private struct XY: Hashable {
        var x: Int
        var y: Double
        var label: String?
    }

    /// 계열 하나를 x(며칠째)·y 로 바꾼 것
    private struct Prepared: Identifiable {
        let s: GoalChartSeries
        let xy: [XY]
        var id: String { s.key }

        /// 그날 값 — 계획선은 이어서(보간), 기록은 그날 기록만
        func value(at x: Int) -> Double? {
            if s.kind == .plan { return GoalProgressChart.interpolate(xy, x) }
            return xy.first(where: { $0.x == x })?.y
        }
    }

    private var span: Int { max(KST.diffDays(from, to), 1) }
    private func xOf(_ date: String) -> Int { KST.diffDays(from, date) }

    private func prepared(_ s: GoalChartSeries) -> [XY] {
        s.points.map { XY(x: xOf($0.date), y: $0.value, label: $0.label) }.sorted { $0.x < $1.x }
    }

    /// 보기 좋은 눈금 (1, 2, 2.5, 5 × 10^n)
    nonisolated private static func niceTicks(_ minV: Double, _ maxV: Double, count: Double = 4) -> (lo: Double, hi: Double, ticks: [Double]) {
        var mn = minV
        var mx = maxV
        if mn == mx {
            let pad = abs(mn) * 0.1 == 0 ? 1 : abs(mn) * 0.1
            mn -= pad
            mx += pad
        }
        let raw = (mx - mn) / count
        let mag = pow(10, floor(log10(raw)))
        let step = [1, 2, 2.5, 5, 10].map { $0 * mag }.first { $0 >= raw } ?? raw
        let lo = floor(mn / step) * step
        let hi = ceil(mx / step) * step
        var ticks: [Double] = []
        var v = lo
        while v <= hi + step / 2 && ticks.count < 20 {
            ticks.append((v * 1e6).rounded() / 1e6)
            v += step
        }
        return (lo, hi, ticks)
    }

    /// 계획선 위 x 날의 값 (양 끝 밖이면 nil)
    // private: 매개변수 타입 XY 가 private 이라 이 함수도 private 이어야 해요 (안쪽 Prepared 에서는 그대로 불러요)
    nonisolated private static func interpolate(_ pts: [XY], _ x: Int) -> Double? {
        guard let first = pts.first, let last = pts.last, x >= first.x, x <= last.x else { return nil }
        for i in 1..<max(pts.count, 1) where i < pts.count {
            let a = pts[i - 1]
            let b = pts[i]
            if x <= b.x {
                if b.x == a.x { return b.y }
                return a.y + (b.y - a.y) * Double(x - a.x) / Double(b.x - a.x)
            }
        }
        return first.x == x ? first.y : nil
    }

    private func mdLabel(_ x: Int) -> String {
        let d = KST.addDays(from, x)
        return "\(KST.monthOf(d)).\(KST.dayOfMonth(d))"
    }

    var body: some View {
        let all: [Prepared] = series.map { Prepared(s: $0, xy: prepared($0)) }
        var ys = all.flatMap { $0.xy.map(\.y) }
        if let target { ys.append(target.value) }
        if let current { ys.append(current.value) }
        if zeroBased { ys.append(0) }
        let scale = Self.niceTicks(ys.min() ?? 0, ys.max() ?? 1)
        let todayX = xOf(today)
        let showToday = todayX >= 0 && todayX <= span
        let xTicks = Array(Set((0..<4).map { Int((Double(span) * Double($0) / 3).rounded()) })).sorted()
        let currentX = current?.date.map { xOf($0) }

        return VStack(alignment: .leading, spacing: 6) {
            hoverInfo()
                .frame(minHeight: 18, alignment: .leading)

            Chart {
                // 목표값
                if let target {
                    RuleMark(y: .value("목표", target.value))
                        .foregroundStyle(Color.success.opacity(0.6))
                        .lineStyle(StrokeStyle(lineWidth: 1, dash: [2, 4]))
                        .annotation(position: .top, alignment: .trailing, spacing: 2) {
                            Text(target.label).font(.pt(11, .semibold)).foregroundStyle(Color.success)
                        }
                }
                // 오늘
                if showToday {
                    RuleMark(x: .value("오늘", todayX))
                        .foregroundStyle(Color.ink3)
                        .lineStyle(StrokeStyle(lineWidth: 1, dash: [3, 3]))
                        .annotation(position: .top, spacing: 2) {
                            Text("오늘").font(.pt(11, .semibold)).foregroundStyle(Color.ink3)
                        }
                }
                // 기록 선 아래 옅은 면 (어디까지 왔는지 한눈에)
                ForEach(all.filter { $0.s.kind == .actual }) { item in
                    ForEach(item.xy, id: \.x) { p in
                        AreaMark(x: .value("날짜", p.x), yStart: .value("바닥", scale.lo), yEnd: .value("값", p.y),
                                 series: .value("계열", item.s.key + "-area"))
                            .foregroundStyle(areaGradient)
                            .interpolationMethod(.monotone)
                    }
                }
                // 선
                ForEach(all) { item in
                    ForEach(item.xy, id: \.x) { p in
                        LineMark(x: .value("날짜", p.x), y: .value("값", p.y), series: .value("계열", item.s.key))
                            .foregroundStyle(seriesColor(item.s.kind))
                            .lineStyle(seriesStroke(item.s.kind))
                            .interpolationMethod(interp(item.s.kind))
                    }
                }
                // 점: 계획선의 꺾이는 점(마일스톤) · 기록한 날
                ForEach(all) { item in
                    ForEach(pointsToMark(item.s, item.xy), id: \.x) { p in
                        PointMark(x: .value("날짜", p.x), y: .value("값", p.y))
                            .symbol {
                                Circle()
                                    .fill(seriesColor(item.s.kind))
                                    .overlay(Circle().stroke(Color.surface, lineWidth: 1.5))
                                    .frame(width: pointDiameter(item.s.kind), height: pointDiameter(item.s.kind))
                            }
                    }
                }
                // 지금 위치
                if let current, let cx = currentX, cx >= 0, cx <= span {
                    PointMark(x: .value("날짜", cx), y: .value("값", current.value))
                        .symbolSize(380)
                        .foregroundStyle(Color.brand.opacity(0.18))
                    PointMark(x: .value("날짜", cx), y: .value("값", current.value))
                        .symbolSize(110)
                        .foregroundStyle(Color.textBrand)
                        .annotation(position: .top, spacing: 6) {
                            if selected == nil {
                                Text(current.label)
                                    .font(.pt(11, .bold))
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 2)
                                    .background(Color.brand, in: Capsule())
                            }
                        }
                }
                // 누르고 있는 날
                if let selected {
                    RuleMark(x: .value("선택", selected))
                        .foregroundStyle(Color.ink2.opacity(0.35))
                        .lineStyle(StrokeStyle(lineWidth: 1))
                        .annotation(position: .top, spacing: 0,
                                    overflowResolution: .init(x: .fit(to: .chart), y: .disabled)) {
                            selectionBubble(all, x: selected)
                        }
                }
            }
            .chartXScale(domain: 0...span)
            .chartYScale(domain: scale.lo...max(scale.hi, scale.lo + 0.0001))
            .chartXAxis {
                AxisMarks(values: xTicks) { v in
                    AxisGridLine().foregroundStyle(Color.line.opacity(0.6))
                    AxisValueLabel {
                        Text(mdLabel(v.as(Int.self) ?? 0)).font(.pt(11)).foregroundStyle(Color.ink4)
                    }
                }
            }
            .chartYAxis {
                AxisMarks(position: .trailing, values: scale.ticks) { v in
                    AxisGridLine().foregroundStyle(Color.line)
                    AxisValueLabel {
                        Text(format(v.as(Double.self) ?? 0)).font(.pt(11)).foregroundStyle(Color.ink4)
                    }
                }
            }
            .chartXSelection(value: $selected)
            .onChange(of: selected) { old, new in
                if old == nil, new != nil { onInteract?() }
            }
            .frame(height: height)
            .accessibilityLabel("목표 진행 그래프")
        }
    }

    // 차트 안 삼항식을 줄여서 타입 검사가 빨리 끝나게 작은 함수로 빼 뒀어요
    private func seriesColor(_ k: GoalChartSeries.Kind) -> Color { k == .plan ? Color.ink4 : Color.brand }

    private func seriesStroke(_ k: GoalChartSeries.Kind) -> StrokeStyle {
        k == .plan
            ? StrokeStyle(lineWidth: 2, lineCap: .round, dash: [6, 5])
            : StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round)
    }

    private func interp(_ k: GoalChartSeries.Kind) -> InterpolationMethod { k == .actual ? .monotone : .linear }

    private func pointDiameter(_ k: GoalChartSeries.Kind) -> CGFloat { k == .plan ? 7 : 8 }

    private var areaGradient: LinearGradient {
        LinearGradient(colors: [Color.brand.opacity(0.28), Color.brand.opacity(0.02)], startPoint: .top, endPoint: .bottom)
    }

    /// 누르고 있는 날의 계획 · 기록 값 말풍선
    private func selectionBubble(_ all: [Prepared], x: Int) -> some View {
        let date = KST.addDays(from, x)
        return VStack(alignment: .leading, spacing: 4) {
            Text("\(KST.monthOf(date)).\(KST.dayOfMonth(date)) (\(KST.weekdayLabel[KST.weekdayIndex(date)]))" + (date == today ? " · 오늘" : ""))
                .font(.pt(11, .semibold))
                .foregroundStyle(Color.onInk.opacity(0.75))
            ForEach(all) { item in
                if let y = item.value(at: x) {
                    HStack(spacing: 6) {
                        RoundedRectangle(cornerRadius: 1)
                            .fill(item.s.kind == .plan ? Color.onInk.opacity(0.6) : Color.onInverseBrand)
                            .frame(width: 10, height: 3)
                        Text(item.s.name).font(.pt(12)).foregroundStyle(Color.onInk.opacity(0.8))
                        Text(format(y)).font(.ptMono(12, .bold)).foregroundStyle(Color.onInk)
                    }
                }
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(Color.inverse, in: RoundedRectangle(cornerRadius: 10))
        .popShadow()
    }

    private func pointsToMark(_ s: GoalChartSeries, _ xy: [XY]) -> [XY] {
        switch s.kind {
        case .plan:
            guard xy.count > 2 else { return [] }
            return Array(xy.dropFirst().dropLast()).filter { $0.label != nil }
        case .actual:
            return xy.count <= 60 ? xy : []
        }
    }

    /// 그래프 위 안내 (값은 누르고 있는 자리의 말풍선으로 보여줘요)
    @ViewBuilder private func hoverInfo() -> some View {
        HStack(spacing: 4) {
            Image(systemName: "hand.point.up.left").font(.system(size: 10))
            Text(selected == nil ? "그래프를 누르고 좌우로 움직이면 그날 값을 보여드려요" : "손을 떼면 닫혀요")
        }
        .font(.pt(11))
        .foregroundStyle(Color.ink4)
    }
}

/// 범례 (점선 = 계획, 실선 = 기록, 점 = 지금 위치)
@MainActor
struct GoalChartLegend: View {
    let items: [(name: String, kind: GoalChartSeries.Kind)]

    var body: some View {
        FlowLayout(spacing: 16) {
            ForEach(items.indices, id: \.self) { i in
                HStack(spacing: 6) {
                    Path { p in
                        p.move(to: CGPoint(x: 0, y: 2))
                        p.addLine(to: CGPoint(x: 18, y: 2))
                    }
                    .stroke(items[i].kind == .plan ? Color.ink4 : Color.brand,
                            style: StrokeStyle(lineWidth: 2.5, dash: items[i].kind == .plan ? [4, 3] : []))
                    .frame(width: 18, height: 4)
                    Text(items[i].name)
                }
            }
            HStack(spacing: 6) {
                Circle().fill(Color.brand).frame(width: 10, height: 10)
                Text("지금 위치")
            }
        }
        .font(.pt(12))
        .foregroundStyle(Color.ink3)
    }
}

#Preview {
    let p = GoalPreviewData.progress
    let m = p.metric!
    return VStack(alignment: .leading, spacing: 8) {
        GoalProgressChart(
            series: [
                GoalChartSeries(key: "plan", name: "계획", kind: .plan,
                                points: m.planLine.map { GoalChartPoint(date: $0.date, value: $0.value, label: $0.label) }),
                GoalChartSeries(key: "actual", name: "기록", kind: .actual,
                                points: m.logs.map { GoalChartPoint(date: $0.date, value: $0.value) }),
            ],
            from: p.startDate!, to: p.endDate!, today: p.today,
            target: GoalChartMarker(value: m.targetValue, label: "목표 72kg"),
            current: GoalChartMarker(date: m.lastLoggedDate, value: m.currentValue, label: "지금 77.4kg"),
            format: { GoalDetailLogic.withUnit($0, "kg") }
        )
        GoalChartLegend(items: [("계획", .plan), ("내 기록", .actual)])
    }
    .padding(16)
}
