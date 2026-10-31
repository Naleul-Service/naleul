// Features/Pattern/PatternWeekdayBars.swift
//
// 웹 features/pattern/ui/WeekdayBars.tsx
// 요일별 실행률 막대 — 가장 낮은 요일만 파란색

import SwiftUI

struct PatternWeekdayBars: View {
    let weekday: PatternWeekday?

    private static let barMax: CGFloat = 128

    private var hasData: Bool { (weekday?.days ?? []).contains { $0.rate != nil } }

    var body: some View {
        SectionCard("요일별 실행률", aside: {
            if let avg = weekday?.average { Text("평균 \(avg)%") }
        }) {
            if let weekday, hasData {
                VStack(alignment: .leading, spacing: 20) {
                    HStack(alignment: .bottom, spacing: 8) {
                        ForEach(weekday.days ?? [], id: \.dayOfWeek) { d in
                            PatternWeekdayBar(day: d, low: d.dayOfWeek == weekday.lowest, barMax: Self.barMax)
                        }
                    }
                    .padding(.top, 24)
                    .contentShape(Rectangle())
                    .patternChartTap("weekday")
                    if let insight = weekday.insight, !insight.isEmpty {
                        PatternEmphasis.text(insight, color: Color.brand)
                            .font(.pt(14))
                            .foregroundStyle(Color.ink2)
                            .lineSpacing(3)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xxl))
                    }
                }
            } else {
                NotEnoughView()
            }
        }
    }
}

private struct PatternWeekdayBar: View {
    let day: PatternWeekdayDay
    let low: Bool
    let barMax: CGFloat

    var body: some View {
        let height: CGFloat = day.rate.map { max(6, CGFloat($0) / 100 * barMax) } ?? 4
        VStack(spacing: 8) {
            Text(day.rate.map { "\($0)%" } ?? "–")
                .font(.pt(12, .semibold))
                .foregroundStyle(low ? Color.ink : Color.ink3)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            RoundedRectangle(cornerRadius: 6)
                .fill(low ? Color.brand : Color.lineStrong)
                .frame(maxWidth: 36)
                .frame(height: height)
            Text(PatternFormat.dayShort(day.dayOfWeek))
                .font(.pt(13, low ? .bold : .regular))
                .foregroundStyle(low ? Color.ink : Color.ink3)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(PatternFormat.dayShort(day.dayOfWeek)) \(day.rate.map { "\($0)" } ?? "-")%")
    }
}

#Preview {
    VStack(spacing: 16) {
        PatternWeekdayBars(weekday: PatternSampleData.report.weekday)
        PatternWeekdayBars(weekday: nil)
    }
    .padding(16)
    .canvasBackground()
}
