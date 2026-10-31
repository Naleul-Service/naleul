// Features/Habit/HabitSummaryCard.swift
//
// 웹 features/habit/ui/HabitSummaryCard.tsx — 목표 목록 맨 위 기본 생활 습관 요약.
// 오늘 몇 개 챙겼는지 + 습관 칩. 누르면 습관 화면(기본 생활 습관 목표 상세)으로 가요.
// 처음 부를 때 서버가 그릇(목표)을 만들어 줘요.

import SwiftUI

@MainActor
struct HabitSummaryCard: View {
    @State private var home: HabitHome?
    @State private var loaded = false
    private let preview: HabitHome?
    @Environment(AppRouter.self) private var router

    init() { preview = nil }

    /// 미리보기용
    init(preview: HabitHome) { self.preview = preview }

    var body: some View {
        Group {
            if let data = preview ?? home {
                card(data)
            } else if !loaded {
                RoundedRectangle(cornerRadius: Radius.card)
                    .fill(Color.surface)
                    .overlay(RoundedRectangle(cornerRadius: Radius.card).stroke(Color.line, lineWidth: 1))
                    .frame(height: 96)
                    .redacted(reason: .placeholder)
            }
        }
        .reloadOn([.habits]) {
            guard preview == nil else { return }
            if let h = try? await HabitAPI.home() { home = h }
            loaded = true
        }
    }

    private func card(_ data: HabitHome) -> some View {
        let due = data.habits.filter { $0.isActive && $0.scheduled }
        let done = due.filter(\.checked).count
        let ratio = due.isEmpty ? 0 : Double(done) / Double(due.count)

        return Button {
            if preview == nil {
                Analytics.track("goal_list.habit_card_click", [
                    "habit_count": data.habits.count, "due_today_count": due.count, "done_count": done,
                ])
            }
            router.openGoal(data.goalCategoryId)
        } label: {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 12) {
                    Text(data.emoji ?? "🌱")
                        .font(.system(size: 20))
                        .frame(width: 40, height: 40)
                        .background(GoalPalette.mint.tone.bg, in: RoundedRectangle(cornerRadius: Radius.xl))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(data.goalName).font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                        Text(subtitle(data, due: due.count, done: done))
                            .font(.pt(13))
                            .foregroundStyle(Color.ink3)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 0)
                    if !due.isEmpty {
                        (Text("\(done)").font(.ptMono(17, .bold)).foregroundColor(Color.ink)
                            + Text(" / \(due.count)").font(.ptMono(14, .semibold)).foregroundColor(Color.ink3))
                    }
                    NSymbol("chevron.right", size: 13, weight: .semibold)
                        .foregroundStyle(Color.ink4)
                }
                if !due.isEmpty {
                    ProgressBar(value: ratio, color: .progressFill, height: 6)
                    FlowLayout(spacing: 6) {
                        ForEach(due.prefix(8)) { h in
                            HStack(spacing: 4) {
                                if h.checked {
                                    NSymbol("checkmark", size: 10, weight: .heavy)
                                }
                                Text(h.title).lineLimit(1)
                            }
                            .font(.pt(12, .medium))
                            // DESIGN_SPEC 8.6: 연한 칩 — bg/sunken + 1px border/soft · text/secondary (한 것은 민트 tint)
                            .foregroundStyle(h.checked ? GoalPalette.mint.tone.fg : Color.textSecondary)
                            .padding(.horizontal, 10)
                            .frame(height: 26)
                            .background(h.checked ? GoalPalette.mint.tone.bg : Color.bgSunken, in: Capsule())
                            .overlay(Capsule().strokeBorder(h.checked ? Color.clear : Color.borderSoft, lineWidth: 1))
                        }
                        if due.count > 8 {
                            Text("+\(due.count - 8)").font(.pt(12)).foregroundStyle(Color.ink3)
                        }
                    }
                }
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.card).stroke(Color.line, lineWidth: 1))
            .contentShape(RoundedRectangle(cornerRadius: Radius.card))
        }
        .buttonStyle(.plain)
    }

    private func subtitle(_ data: HabitHome, due: Int, done: Int) -> String {
        if data.habits.isEmpty { return "영양제 · 물 마시기처럼 매일 챙길 것을 추가해 보세요" }
        if due == 0 { return "오늘은 해야 하는 습관이 없어요" }
        if done == due { return "오늘 할 습관을 모두 챙겼어요 🎉" }
        return "오늘 \(due - done)개 남았어요"
    }
}

#Preview {
    VStack(spacing: 16) {
        HabitSummaryCard(preview: HabitPreviewData.home)
    }
    .padding(16)
    .canvasBackground()
    .environment(AppRouter.shared)
}
