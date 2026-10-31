// Features/GoalCreation/InterviewSummaryCard.swift
//
// 인터뷰 결과 요약 (목표 · 수치 · 기한 · 계획 · 실천 시간 · 성향). 예전 세션은 이유도.
// 웹 원본: features/goal-creation/ui/InterviewSummaryCard.tsx
// (웹에서도 아직 쓰는 곳은 없고 "재사용 예정"이에요. 정리 확인 화면은 고칠 수 있는 줄을 따로 그려요)

import SwiftUI

struct InterviewSummaryCard: View {
    let summary: GoalCreationSummary

    var body: some View {
        VStack(spacing: 0) {
            row("목표") { valueText(summary.goalStatement) }
            Divider().overlay(Color.borderSoft)
            row("수치") { valueText(summary.metricText) }
            Divider().overlay(Color.borderSoft)
            row("기한") { valueText(summary.deadlineText) }
            Divider().overlay(Color.borderSoft)
            row("계획") { valueText(summary.approach) }
            Divider().overlay(Color.borderSoft)
            // 예전 세션(이유를 묻던 때)에 답한 이유가 있으면 보여줘요
            if let motivation = summary.motivation, !motivation.isEmpty {
                row("이유") { valueText(motivation) }
                Divider().overlay(Color.borderSoft)
            }
            row("실천 시간") { valueText(summary.preferenceText) }
            Divider().overlay(Color.borderSoft)
            row("성향") {
                if let style = summary.planningStyle {
                    Badge(style.goalCreationLabel, tone: .brand)
                } else {
                    emptyText
                }
            }
        }
    }

    private var emptyText: some View {
        Text("AI가 알맞게 정해줄게요").font(.pt(15, .medium)).foregroundStyle(Color.ink4)
    }

    @ViewBuilder
    private func valueText(_ v: String?) -> some View {
        if let v {
            Text(v).font(.pt(15, .medium)).foregroundStyle(Color.ink)
        } else {
            emptyText
        }
    }

    private func row<V: View>(_ label: String, @ViewBuilder _ value: () -> V) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 16) {
            Text(label).font(.pt(13)).foregroundStyle(Color.ink3).frame(width: 64, alignment: .leading)
            value().frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 10)
    }
}

#Preview {
    InterviewSummaryCard(summary: GoalCreationSamples.session.summary!)
        .padding(16)
        .background(Color.surface)
}
