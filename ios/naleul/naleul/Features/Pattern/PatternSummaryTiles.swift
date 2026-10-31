// Features/Pattern/PatternSummaryTiles.swift
//
// 웹 features/pattern/ui/SummaryTiles.tsx
// 요약 4칸 (2×2): 전체 실행률 · 최고 연속 실행 · 평균 시작 지연 · AI 배치 수정률

import SwiftUI

enum PatternTileTone { case up, down }

/// 요약 칸 하나 (기록형 목표의 요약 칸도 같은 모양이라 같이 써요)
struct PatternTile: View {
    let label: String
    let value: String
    var sub: String? = nil
    var tone: PatternTileTone? = nil
    /// 값 크기 — Heading/H2 20 SemiBold (DESIGN_SPEC 8.3)
    var valueSize: CGFloat = 20

    private var subColor: Color {
        switch tone {
        case .up?: return .success
        case .down?: return .danger
        case nil: return .ink3
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(label).font(.captionMD).foregroundStyle(Color.textSecondary)
            Spacer(minLength: 4)
            Text(value)
                .font(.pt(valueSize, .semibold))
                .tracking(-0.02 * valueSize)
                .foregroundStyle(Color.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            if let sub {
                Text(sub)
                    .font(.pt(13, .semibold))
                    .foregroundStyle(subColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 4)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, minHeight: 112, maxHeight: .infinity, alignment: .topLeading)
        .card()
    }
}

struct PatternSummaryTiles: View {
    let summary: PatternSummary?

    private static let dash = "–"

    private var rateTile: PatternTile {
        let s = summary
        let value = s?.executionRate.map { "\($0)%" } ?? Self.dash
        if let delta = s?.firstWeekDelta {
            return PatternTile(label: "전체 실행률", value: value,
                               sub: "\(delta >= 0 ? "▲" : "▼") \(abs(delta))%p 첫 주 대비",
                               tone: delta >= 0 ? PatternTileTone.up : PatternTileTone.down)
        }
        return PatternTile(label: "전체 실행률", value: value, sub: "기록이 쌓이면 보여드려요")
    }

    private var streakTile: PatternTile {
        let best = summary?.bestStreak ?? 0
        let current = summary?.currentStreak ?? 0
        return PatternTile(label: "최고 연속 실행",
                           value: (best > 0 ? "🔥 " : "") + "\(best)일",
                           sub: current > 0 ? "현재 \(current)일째" : "오늘부터 다시 시작해봐요")
    }

    private var delayTile: PatternTile {
        let s = summary
        let value = s?.avgStartDelayMinutes.map { PatternFormat.signedMinutes($0) } ?? Self.dash
        var parts: [String] = []
        if let m = s?.morningDelayMinutes { parts.append("아침 \(PatternFormat.signedMinutes(m))") }
        if let e = s?.eveningDelayMinutes { parts.append("저녁 \(PatternFormat.signedMinutes(e))") }
        let sub = parts.isEmpty ? "완료할 때 실제 시각을 남기면 더 정확해요" : parts.joined(separator: " · ")
        return PatternTile(label: "평균 시작 지연", value: value, sub: sub)
    }

    private var aiTile: PatternTile {
        let s = summary
        let value = s?.aiPlacementEditRate.map { "\($0)%" } ?? Self.dash
        let sub = s?.aiPlacementKeptPerTen.map { "10개 중 \($0)개는 그대로 사용" } ?? "AI 배치 기록이 쌓이면 보여드려요"
        return PatternTile(label: "AI 배치 수정률", value: value, sub: sub)
    }

    var body: some View {
        VStack(spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                rateTile.patternDeadTap("summary_tile")
                streakTile.patternDeadTap("summary_tile")
            }
            .fixedSize(horizontal: false, vertical: true)
            HStack(alignment: .top, spacing: 12) {
                delayTile.patternDeadTap("summary_tile")
                aiTile.patternDeadTap("summary_tile")
            }
            .fixedSize(horizontal: false, vertical: true)
        }
    }
}

#Preview {
    VStack(spacing: 16) {
        PatternSummaryTiles(summary: PatternSampleData.report.summary)
        PatternSummaryTiles(summary: nil)
    }
    .padding(16)
    .canvasBackground()
}
