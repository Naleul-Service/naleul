// Features/Pattern/PatternStyleCard.swift
//
// 웹 features/pattern/ui/StyleCard.tsx
// 상단 AI 카드 (AI 그라데이션 테두리): AI가 분석한 나의 실행 스타일.
// 기록이 부족하면 빈 화면 대신 "얼마나 더 하면 열리는지" 진행 막대를 보여줘요 (Locked).

import SwiftUI

struct PatternStyleCard: View {
    let style: PatternStyle?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                AIBadge(size: .sm)
                Text("AI가 분석한 나의 실행 스타일")
                    .font(.pt(13))
                    .foregroundStyle(Color.textSecondary)
            }
            if let style, let title = style.title, !title.isEmpty {
                titleText(title, highlight: style.highlight)
                    .font(.pt(22, .bold))   // 22 Bold · 자간 −2%
                    .foregroundStyle(Color.textPrimary)
                    .tracking(-0.44)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 8)
                    .patternDeadTap("style_sentence")
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(style.insights ?? [], id: \.key) { it in
                        PatternInsightRow(insight: it)
                    }
                }
                .padding(.top, 20)
                .patternDeadTap("style_sentence")
            } else {
                PatternStyleLocked(style: style)
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .aiBorder(tinted: true)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("나의 실행 스타일")
    }

    /// "저녁에 강한 꾸준형" 에서 "저녁에 강한" 부분만 파란색
    private func titleText(_ title: String, highlight: String?) -> Text {
        guard let highlight, !highlight.isEmpty, title.hasPrefix(highlight) else { return Text(title) }
        return Text(highlight).foregroundColor(PatternFormat.styleAccent) + Text(String(title.dropFirst(highlight.count)))
    }
}

private struct PatternInsightRow: View {
    let insight: PatternInsight

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text(PatternFormat.insightIcon[insight.icon] ?? "•")
                .font(.system(size: 14))
                .frame(width: 28, height: 28)
                .background(Color.bgSurface, in: RoundedRectangle(cornerRadius: Radius.lg))
                .accessibilityHidden(true)
            PatternEmphasis.text(insight.text, color: Color.textBrand)
                .font(.pt(14))
                .foregroundStyle(Color.textPrimary)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 3)
        }
    }
}

/// 아직 기록이 부족할 때 — 예) 실행 스타일까지 Task 15개 · 4일 더
struct PatternStyleLocked: View {
    let style: PatternStyle?

    private var remain: String? {
        guard let p = style?.progress else { return nil }
        var parts: [String] = []
        if p.remainingTasks > 0 { parts.append("Task \(p.remainingTasks)개") }
        if p.remainingDays > 0 { parts.append("\(p.remainingDays)일") }
        return parts.joined(separator: " · ")
    }

    private var message: String {
        if let p = style?.progress {
            return "Task \(p.requiredTasks)개를 \(p.requiredDays)일 이상에 걸쳐 실행하면 아침형·저녁형, 꾸준형·몰아치기형 같은 나만의 스타일을 알려드려요. 오늘 완료한 Task도 바로 반영돼요."
        }
        if let n = style?.remainingForStyle, n > 0 {
            return "Task를 \(n)개 더 실행하면 나의 실행 스타일을 알려드릴게요."
        }
        return "Task를 꾸준히 실행하면 나의 실행 스타일을 알려드릴게요."
    }

    private var headline: Text {
        if let remain, !remain.isEmpty {
            return Text("실행 스타일까지 ") + Text(remain).foregroundColor(PatternFormat.styleAccent) + Text(" 더")
        }
        return Text("아직 스타일을 알기엔 기록이 적어요")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            headline
                .font(.pt(22, .bold))
                .tracking(-0.44)
                .foregroundStyle(Color.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            Text(message)
                .font(.pt(14))
                .foregroundStyle(Color.textSecondary)
                .lineSpacing(2)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 8)
            if let p = style?.progress {
                VStack(spacing: 14) {
                    PatternLockedBar(label: "실행한 Task", value: p.evaluated, max: p.requiredTasks, unit: "개")
                    PatternLockedBar(label: "실행한 날", value: p.activeDays, max: p.requiredDays, unit: "일")
                }
                .padding(.top, 20)
            }
        }
        .padding(.top, 12)
    }
}

private struct PatternLockedBar: View {
    let label: String
    let value: Int
    let max: Int
    let unit: String

    var body: some View {
        let ratio = Swift.min(Double(value) / Double(Swift.max(max, 1)), 1)
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(label).foregroundStyle(Color.textSecondary)
                Spacer(minLength: 0)
                (Text("\(Swift.min(value, max))").foregroundColor(Color.textPrimary).fontWeight(.semibold)
                 + Text(" / \(max)\(unit)").foregroundColor(Color.textTertiary))
                    .monospacedDigit()
            }
            .font(.pt(13))
            ProgressBar(value: ratio, color: Color.progressFill, track: Color.progressTrack, height: 6)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityValue("\(value) / \(max)\(unit)")
    }
}

#Preview {
    ScrollView {
        VStack(spacing: 16) {
            PatternStyleCard(style: PatternSampleData.report.style)
            PatternStyleCard(style: PatternSampleData.emptyReport.style)
            PatternStyleCard(style: nil)
        }
        .padding(16)
    }
    .canvasBackground()
}
