// Features/Pattern/PatternHeatmapCard.swift
//
// 웹 features/pattern/ui/Heatmap.tsx
// 언제 가장 잘 실행하나요? — 요일 × 시간(6~23시)에 실제로 한 횟수 표.
// 계획한 시간과 상관없이 그 시간대에 Task · 루틴을 했으면 +1. 가장 많이 한 칸이 가장 진하고, 한 게 없으면 색이 없어요.
// 웹은 가로(시간 18칸) × 세로(요일 7줄)지만, 아이폰 세로 화면에서는 18칸이 너무 좁아 숫자가 안 읽혀요.
// 그래서 표를 돌렸어요: 가로 = 요일 7칸, 세로 = 시간 줄. 칸이 넓어져서 숫자를 그대로 읽을 수 있어요.
// 골든 타임 · 취약 시간 요약을 표보다 먼저 보여줘요 (한눈에 결론 → 자세한 표).
// 칸을 누르면 그 칸이 테두리로 표시되고, 위에 자세한 값이 떠요.

import SwiftUI
import UIKit

struct PatternHeatmapCard: View {
    let heatmap: PatternHeatmap?
    @State private var picked: String?
    @State private var pickedKey: String?
    /// 말풍선 크기 (가로로 카드 안에 가두려고 재요)
    @State private var bubbleSize: CGSize = .zero
    /// 3초 뒤 자동으로 닫을 때 "그 사이 다른 칸을 눌렀는지" 확인용
    @State private var dismissToken = UUID()

    // private @State 가 있으면 자동 생성 init 이 private 이 될 수 있어서, 다른 파일(PatternScreen)에서 부를 수 있게 직접 만들어요
    init(heatmap: PatternHeatmap?) {
        self.heatmap = heatmap
    }

    private var hasValue: Bool {
        (heatmap?.rows ?? []).contains { row in (row.cells ?? []).contains { $0.state == .VALUE } }
    }

    var body: some View {
        SectionCard("언제 가장 잘 실행하나요?", aside: { Text("한 횟수") }) {
            if let heatmap, hasValue {
                VStack(alignment: .leading, spacing: 0) {
                    windows(heatmap)
                    hintLine
                    table(heatmap)
                        // 고른 칸 위(위쪽 끝이면 아래)에 말풍선
                        .overlayPreferenceValue(PatternHeatPickedAnchorKey.self) { anchor in
                            GeometryReader { proxy in
                                if let anchor, let picked {
                                    tooltip(picked, cell: proxy[anchor], width: proxy.size.width)
                                }
                            }
                        }
                    legend(heatmap).padding(.top, 12)
                }
                // 칸 밖을 누르면 닫혀요
                .background(Color.clear.contentShape(Rectangle()).onTapGesture { clearPick() })
            } else {
                NotEnoughView(text: "Task나 루틴을 완료하면 언제 많이 했는지 보여요")
            }
        }
    }

    // MARK: 표 (가로 = 요일, 세로 = 시간)

    /// 안내 한 줄 (자세한 값은 칸 위 말풍선으로 보여줘요)
    private var hintLine: some View {
        HStack(spacing: 6) {
            NSymbol("hand.tap", size: 11)
            Text("칸을 누르면 자세히 보여드려요")
        }
        .font(.captionMD)
        .foregroundStyle(Color.textTertiary)
        .frame(maxWidth: .infinity, minHeight: 20, alignment: .leading)
        .padding(.bottom, 8)
    }

    /// 칸 위 말풍선 — bg/inverse · text/inverse · Caption/MD · padding 6/10 · radius 8 · 꼬리
    private func tooltip(_ text: String, cell: CGRect, width: CGFloat) -> some View {
        let w = bubbleSize.width
        let h = bubbleSize.height
        let gap: CGFloat = 6
        let tail: CGFloat = 5
        // 위쪽 끝이라 공간이 없으면 칸 아래로
        let below = cell.minY < h + gap + tail
        let half = w / 2
        let x = width > w ? min(max(cell.midX, half), width - half) : width / 2
        let y = below ? cell.maxY + gap + tail + h / 2 : cell.minY - gap - tail - h / 2
        return ZStack(alignment: .topLeading) {
            Text(text)
                .font(.captionMD)
                .foregroundStyle(Color.textInverse)
                .lineLimit(1)
                .padding(.vertical, 6)
                .padding(.horizontal, 10)
                .fixedSize()
                .background(Color.bgInverse, in: RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
                .onGeometryChange(for: CGSize.self) { $0.size } action: { bubbleSize = $0 }
                .position(x: x, y: y)
            PatternTooltipTail()
                .fill(Color.bgInverse)
                .frame(width: 10, height: tail)
                .rotationEffect(.degrees(below ? 180 : 0))
                .position(x: cell.midX, y: below ? cell.maxY + gap + tail / 2 : cell.minY - gap - tail / 2)
        }
        .opacity(w > 0 ? 1 : 0)
        .allowsHitTesting(false)
        .transition(.opacity)
        .accessibilityHidden(true)
    }

    private func table(_ h: PatternHeatmap) -> some View {
        let hours = h.hours ?? []
        let rows = h.rows ?? []
        // [요일][시] → 칸
        var lookup: [JavaDayOfWeek: [Int: PatternHeatCell]] = [:]
        for r in rows {
            var byHour: [Int: PatternHeatCell] = [:]
            for c in r.cells ?? [] { byHour[c.hour] = c }
            lookup[r.dayOfWeek] = byHour
        }
        let days = rows.map(\.dayOfWeek)
        return VStack(spacing: 3) {
            HStack(spacing: 3) {
                Color.clear.frame(width: 30, height: 16)
                ForEach(days, id: \.self) { d in
                    Text(PatternFormat.dayShort(d))
                        .font(.pt(12, .semibold))
                        .foregroundStyle(d == .SATURDAY ? Color.calendarSaturday : (d == .SUNDAY ? Color.calendarHoliday : Color.ink2))
                        .frame(maxWidth: .infinity)
                }
            }
            ForEach(hours, id: \.self) { hour in
                HStack(spacing: 3) {
                    Text("\(hour)시")
                        .font(.ptMono(10))
                        .foregroundStyle(Color.ink3)
                        .frame(width: 30, alignment: .leading)
                    ForEach(days, id: \.self) { d in
                        cell(lookup[d]?[hour] ?? PatternHeatCell(hour: hour, state: .EMPTY), day: d, fixedLabel: h.fixedLabel)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func cell(_ c: PatternHeatCell, day: JavaDayOfWeek, fixedLabel: String?) -> some View {
        let label = "\(PatternFormat.dayShort(day)) \(c.hour)시"
        let shape = RoundedRectangle(cornerRadius: 5)
        let key = "\(day.rawValue)-\(c.hour)"
        let on = pickedKey == key
        Group {
            if c.state == .VALUE, let level = c.level, level > 0 {
                Text("\(c.count ?? 0)")
                    .font(.ptMono(11, .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                    .foregroundStyle(level >= 3 ? Color.white : Color.ink2)
                    .frame(maxWidth: .infinity)
                    .frame(height: 24)
                    .background(Color.heat(level), in: shape)
                    .onTapGesture { pick(key, "\(label) · \(c.count ?? 0)번 했어요" + (c.rate.map { " · 계획 대비 \($0)%" } ?? "")) }
                    .accessibilityLabel("\(label) \(c.count ?? 0)번 했어요")
            } else if c.state == .FIXED {
                PatternHatchFill()
                    .frame(maxWidth: .infinity)
                    .frame(height: 24)
                    .clipShape(shape)
                    .onTapGesture { pick(key, "\(label) · \(fixedLabel ?? "고정 시간")") }
                    .accessibilityLabel("\(label) \(fixedLabel ?? "고정 시간")")
            } else {
                // 한 게 없는 칸은 색 없이 점선 테두리만
                shape.strokeBorder(Color.line, style: StrokeStyle(lineWidth: 1, dash: [3, 2]))
                    .frame(maxWidth: .infinity)
                    .frame(height: 24)
                    .contentShape(shape)
                    .onTapGesture { pick(key, "\(label) · 한 게 없어요") }
                    .accessibilityLabel("\(label) 한 게 없음")
            }
        }
        .overlay {
            if on { shape.strokeBorder(Color.borderFocus, lineWidth: 1.5) }
        }
        // 말풍선 자리 (고른 칸만)
        .anchorPreference(key: PatternHeatPickedAnchorKey.self, value: .bounds) { on ? $0 : nil }
    }

    private func pick(_ key: String, _ text: String) {
        UISelectionFeedbackGenerator().selectionChanged()
        PT.chart("heatmap")
        if pickedKey == key {
            clearPick()
        } else {
            withAnimation(.easeOut(duration: 0.12)) {
                pickedKey = key
                picked = text
            }
            // 3초 뒤 자동으로 닫아요 (그 사이 다른 칸을 누르면 그 칸 기준으로 다시)
            let token = UUID()
            dismissToken = token
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 3_000_000_000)
                if dismissToken == token { clearPick() }
            }
        }
    }

    private func clearPick() {
        guard pickedKey != nil else { return }
        withAnimation(.easeOut(duration: 0.12)) {
            pickedKey = nil
            picked = nil
        }
    }

    // MARK: 범례

    private func legend(_ h: PatternHeatmap) -> some View {
        FlowLayout(spacing: 16, lineSpacing: 8) {
            HStack(spacing: 6) {
                Text("적게")
                ForEach(1...5, id: \.self) { l in
                    RoundedRectangle(cornerRadius: 3).fill(Color.heat(l)).frame(width: 16, height: 12)
                }
                Text(h.maxCount.map { "많이 (최대 \($0)번)" } ?? "많이")
            }
            HStack(spacing: 6) {
                PatternHatchFill().frame(width: 16, height: 12).clipShape(RoundedRectangle(cornerRadius: 3))
                Text(h.fixedLabel ?? "고정 시간")
            }
            HStack(spacing: 6) {
                RoundedRectangle(cornerRadius: 3)
                    .strokeBorder(Color.line, style: StrokeStyle(lineWidth: 1, dash: [3, 2]))
                    .frame(width: 16, height: 12)
                Text("한 게 없음")
            }
        }
        .font(.pt(12))
        .foregroundStyle(Color.ink3)
    }

    // MARK: 골든 타임 · 취약 시간

    @ViewBuilder
    private func windows(_ h: PatternHeatmap) -> some View {
        if h.goldenTime != nil || h.weakTime != nil {
            HStack(alignment: .top, spacing: 10) {
                if let g = h.goldenTime {
                    PatternWindowBox(badge: "골든 타임", icon: .trendingUp, iconBg: .statusSuccessBg, iconFg: .statusSuccessFg, window: g)
                        .patternDeadTap("golden_time")
                }
                if let w = h.weakTime {
                    PatternWindowBox(badge: "취약 시간", icon: .trendingDown, iconBg: .statusWarningBg, iconFg: .statusWarningFg, window: w)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
            .padding(.bottom, 16)
        }
    }
}

private struct PatternWindowBox: View {
    let badge: String
    /// 라벨 앞 아이콘 (28×28 · radius 8 타일)
    let icon: NIcon
    let iconBg: Color
    let iconFg: Color
    let window: PatternTimeWindow

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                icon.image(size: 16)
                    .foregroundStyle(iconFg)
                    .frame(width: 28, height: 28)
                    .background(iconBg, in: RoundedRectangle(cornerRadius: Radius.nav, style: .continuous))
                Text(badge).font(.labelMD).foregroundStyle(Color.textSecondary)
            }
            .padding(.bottom, 2)
            Text(PatternFormat.windowLabel(window))
                .font(.pt(16, .semibold))
                .foregroundStyle(Color.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
            Text("실행률 \(window.rate)%").font(.captionMD).foregroundStyle(Color.textSecondary)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color.bgSunken, in: RoundedRectangle(cornerRadius: Radius.xl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xl).strokeBorder(Color.borderSubtle, lineWidth: 1))
    }
}

/// 히트맵에서 고른 칸의 자리 (말풍선 위치용)
private struct PatternHeatPickedAnchorKey: PreferenceKey {
    static let defaultValue: Anchor<CGRect>? = nil
    static func reduce(value: inout Anchor<CGRect>?, nextValue: () -> Anchor<CGRect>?) {
        value = value ?? nextValue()
    }
}

/// 말풍선 꼬리 (아래를 가리키는 삼각형)
private struct PatternTooltipTail: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: rect.minX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        p.closeSubpath()
        return p
    }
}

#Preview {
    ScrollView {
        VStack(spacing: 16) {
            PatternHeatmapCard(heatmap: PatternSampleData.report.heatmap)
            PatternHeatmapCard(heatmap: nil)
        }
        .padding(16)
    }
    .canvasBackground()
}
