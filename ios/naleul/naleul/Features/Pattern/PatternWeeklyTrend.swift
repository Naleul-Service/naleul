// Features/Pattern/PatternWeeklyTrend.swift
//
// 웹 features/pattern/ui/WeeklyTrend.tsx
// 주차별 실행률 선 그래프. 웹은 SVG 로 직접 그려서, 여기서도 Canvas 로 같은 계산을 그대로 옮겼어요.
// (Swift Charts 는 표본이 부족한 주에서 선을 끊고, 최고점 · 첫 점에만 라벨을 다는 걸 맞추기 번거로워서)

import SwiftUI

struct PatternWeeklyTrend: View {
    let weekly: PatternWeekly?

    var body: some View {
        let points = weekly?.points ?? []
        let valuedCount = points.filter { $0.rate != nil }.count
        SectionCard("주차별 흐름", aside: { Text("실행률 %") }) {
            if valuedCount < 2 {
                NotEnoughView(text: "2주 이상 기록이 쌓이면 흐름을 보여드려요")
            } else {
                PatternTrendCanvas(points: points, maxIndex: weekly?.maxIndex)
                    .frame(height: 220)
                    .contentShape(Rectangle())
                    .patternChartTap("weekly")
                    .accessibilityLabel("주차별 실행률 그래프")
            }
        }
    }
}

private struct PatternTrendCanvas: View {
    let points: [PatternWeeklyPoint]
    let maxIndex: Int?

    var body: some View {
        Canvas { ctx, size in
            draw(&ctx, size: size)
        }
    }

    private func draw(_ ctx: inout GraphicsContext, size: CGSize) {
        let padL: CGFloat = 32, padR: CGFloat = 12, padT: CGFloat = 24, padB: CGFloat = 28
        let w = size.width, h = size.height
        let innerW = w - padL - padR
        let innerH = h - padT - padB
        let rates = points.compactMap { $0.rate }
        guard let minRate = rates.min() else { return }

        let yMin = max(0, Int(floor(Double(minRate - 10) / 10)) * 10)
        let yMax = 100
        let span = Double(max(yMax - yMin, 1))
        let tickCount = Int((Double(yMax - yMin) / 10).rounded()) + 1
        let allTicks = (0..<tickCount).map { yMin + $0 * 10 }
        let ticks = allTicks.enumerated().filter { i, _ in
            allTicks.count <= 6 || i % 2 == 0 || i == allTicks.count - 1
        }.map { $0.element }

        func x(_ i: Int) -> CGFloat {
            points.count == 1 ? padL + innerW / 2 : padL + CGFloat(i) / CGFloat(points.count - 1) * innerW
        }
        func y(_ r: Int) -> CGFloat {
            padT + CGFloat(1 - Double(r - yMin) / span) * innerH
        }

        // 가로 눈금
        for t in ticks {
            var line = Path()
            line.move(to: CGPoint(x: padL, y: y(t)))
            line.addLine(to: CGPoint(x: w - padR, y: y(t)))
            ctx.stroke(line, with: .color(Color.line), lineWidth: 1)
            ctx.draw(Text("\(t)").font(.pt(11)).foregroundColor(Color.ink3),
                     at: CGPoint(x: padL - 8, y: y(t)), anchor: .trailing)
        }

        // 표본이 부족한 주(nil)에서 선을 끊어요
        var segments: [[(i: Int, r: Int)]] = []
        var cur: [(i: Int, r: Int)] = []
        for (i, p) in points.enumerated() {
            if let r = p.rate {
                cur.append((i, r))
            } else {
                if !cur.isEmpty { segments.append(cur) }
                cur = []
            }
        }
        if !cur.isEmpty { segments.append(cur) }

        let bottom = padT + innerH
        let gradient = Gradient(colors: [Color.brand.opacity(0.16), Color.brand.opacity(0)])
        for seg in segments {
            var line = Path()
            for (k, p) in seg.enumerated() {
                let pt = CGPoint(x: x(p.i), y: y(p.r))
                if k == 0 { line.move(to: pt) } else { line.addLine(to: pt) }
            }
            if seg.count > 1, let first = seg.first, let last = seg.last {
                var area = line
                area.addLine(to: CGPoint(x: x(last.i), y: bottom))
                area.addLine(to: CGPoint(x: x(first.i), y: bottom))
                area.closeSubpath()
                ctx.fill(area, with: .linearGradient(gradient, startPoint: CGPoint(x: 0, y: padT),
                                                     endPoint: CGPoint(x: 0, y: bottom)))
            }
            ctx.stroke(line, with: .color(Color.brand), style: StrokeStyle(lineWidth: 2.5, lineJoin: .round))
            for p in seg {
                let dot = Path(ellipseIn: CGRect(x: x(p.i) - 4.5, y: y(p.r) - 4.5, width: 9, height: 9))
                ctx.fill(dot, with: .color(Color.brand))
                ctx.stroke(dot, with: .color(Color.surface), lineWidth: 2)
            }
        }

        // 최고점 · 첫 점 라벨
        let firstIdx = points.firstIndex { $0.rate != nil }
        let maxIdx = maxIndex.flatMap { mi in points.firstIndex { $0.index == mi } }
        if let mi = maxIdx, let r = points[mi].rate {
            ctx.draw(Text("\(r)%").font(.pt(12, .bold)).foregroundColor(Color.ink),
                     at: CGPoint(x: x(mi), y: y(r) - 12), anchor: .bottom)
        }
        if let fi = firstIdx, fi != maxIdx, let r = points[fi].rate {
            ctx.draw(Text("\(r)%").font(.pt(11)).foregroundColor(Color.ink3),
                     at: CGPoint(x: x(fi) + 8, y: y(r) + 14), anchor: .topLeading)
        }

        // 아래 "n주" 라벨 (주가 많으면 건너뛰며)
        let lastIdx = points.count - 1
        let labelEvery = points.count > 12 ? Int(ceil(Double(points.count) / 8)) : 1
        for (i, p) in points.enumerated() where i % labelEvery == 0 || i == lastIdx {
            let isLast = i == lastIdx
            ctx.draw(Text("\(p.index)주").font(.pt(11, isLast ? .bold : .regular))
                        .foregroundColor(isLast ? Color.ink : Color.ink3),
                     at: CGPoint(x: x(i), y: h - 4), anchor: .bottom)
        }
    }
}

#Preview {
    VStack(spacing: 16) {
        PatternWeeklyTrend(weekly: PatternSampleData.report.weekly)
        PatternWeeklyTrend(weekly: nil)
    }
    .padding(16)
    .canvasBackground()
}
