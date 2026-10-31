// Features/Settings/LifePattern/LifePatternWeeklyOverview.swift
//
// 웹 원본: features/life-pattern/ui/WeeklyOverview.tsx
//
// 요일별로 고정 시간이 어떻게 깔리는지 한눈에 보는 막대.
// 자정을 넘기는 패턴(수면 23:00~07:00)은 시작한 요일에 23:00~24:00, 다음 요일에 00:00~07:00 으로 나눠 그려요.
// 요일별 시간(예: 주말 수면 01:00~09:30)과 "전날/다음 날 시작"도 반영해요.
// (백엔드 FixedBlockCalculator 와 같은 규칙: 요일 판정은 블록 "소속 요일" 기준)

import SwiftUI

/// 한 요일 줄에 그릴 막대 하나
private struct OverviewSegment: Identifiable {
    let id: String
    let pattern: LifePattern
    /// 0 ~ 1440
    let start: Int
    let end: Int
}

struct LifePatternWeeklyOverview: View {
    let patterns: [LifePattern]
    let onSelect: (LifePattern) -> Void

    private static let hours = [0, 6, 12, 18, 24]

    /// 요일(월~일)별 막대 목록
    private var rows: [[OverviewSegment]] {
        var rows: [[OverviewSegment]] = Array(repeating: [], count: 7)
        let all = LifePatternRules.allDays
        for p in patterns {
            let days = p.days.isEmpty ? all : p.days
            let times = LifePatternTimes(p)
            for d in days {
                let i = d.index
                let occ = LifePatternRules.occurrenceOf(times, d)   // 소속 요일 0시 기준 분 (전날·다음 날로 넘어갈 수 있음)
                // 하루(1440분)씩 잘라서 해당 요일 줄에 그려요
                var dayStart = LifePatternRules.floorDiv(occ.start, 1440) * 1440
                while dayStart < occ.end {
                    let s = max(occ.start, dayStart) - dayStart
                    let e = min(occ.end, dayStart + 1440) - dayStart
                    if e > s {
                        let row = (((i + dayStart / 1440) % 7) + 7) % 7
                        let seg = OverviewSegment(id: "\(p.lifePatternId)-\(d.rawValue)-\(dayStart)",
                                                  pattern: p, start: s, end: e)
                        rows[row].append(seg)
                    }
                    dayStart += 1440
                }
            }
        }
        return rows
    }

    var body: some View {
        let rows = self.rows
        VStack(alignment: .leading, spacing: 16) {
            CardHeader("한 주 미리보기")
            VStack(alignment: .leading, spacing: 4) {
                hourScale
                VStack(spacing: 6) {
                    ForEach(0..<7, id: \.self) { i in
                        HStack(spacing: 8) {
                            Text(LifePatternRules.allDays[i].label)
                                .font(.pt(12, i >= 5 ? .regular : .medium))
                                .foregroundStyle(i >= 5 ? Color.ink3 : Color.ink2)
                                .frame(width: 20, alignment: .leading)
                            OverviewBar(segments: rows[i], onSelect: onSelect)
                        }
                    }
                }
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    /// 시간 눈금 (0 · 6 · 12 · 18 · 24)
    private var hourScale: some View {
        GeometryReader { geo in
            ForEach(Self.hours, id: \.self) { h in
                Text("\(h)")
                    .font(.pt(10))
                    .foregroundStyle(Color.ink4)
                    .fixedSize()
                    .position(x: geo.size.width * CGFloat(h) / 24, y: 8)
            }
        }
        .frame(height: 16)
        .padding(.leading, 28)   // 요일 글자(20) + 간격(8)
    }
}

/// 한 요일 막대 (가로 폭 = 24시간)
private struct OverviewBar: View {
    let segments: [OverviewSegment]
    let onSelect: (LifePattern) -> Void

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 6).fill(Color.subtle)
                ForEach([6, 12, 18], id: \.self) { h in
                    Rectangle()
                        .fill(Color.line)
                        .frame(width: 1, height: 24)
                        .offset(x: w * CGFloat(h) / 24)
                }
                ForEach(segments) { seg in
                    segmentView(seg, totalWidth: w)
                }
            }
        }
        .frame(height: 24)
        .clipShape(RoundedRectangle(cornerRadius: 6))
    }

    private func segmentView(_ seg: OverviewSegment, totalWidth w: CGFloat) -> some View {
        let x = w * CGFloat(seg.start) / 1440
        let width = max(w * CGFloat(seg.end - seg.start) / 1440, 2)
        let showTitle = seg.end - seg.start >= 90
        return Button { onSelect(seg.pattern) } label: {
            Text(showTitle ? seg.pattern.title : "")
                .font(.pt(10, .semibold))
                .foregroundStyle(.white)
                .lineLimit(1)
                .padding(.horizontal, 4)
                .frame(width: width, height: 20, alignment: .leading)
                .background(LifePatternRules.typeColor(seg.pattern.patternType), in: RoundedRectangle(cornerRadius: 4))
                .clipped()
        }
        .buttonStyle(.plain)
        .offset(x: x, y: 2)
        .accessibilityLabel(accessibilityText(seg))
    }

    private func accessibilityText(_ seg: OverviewSegment) -> String {
        let end = seg.end == 1440 ? "24:00" : KST.minutesToTime(seg.end)
        return "\(seg.pattern.title) \(KST.minutesToTime(seg.start))–\(end)"
    }
}

#Preview {
    ScrollView {
        LifePatternWeeklyOverview(patterns: LifePatternSamples.patterns) { _ in }
            .padding(16)
    }
    .canvasBackground()
}
