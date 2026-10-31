// Features/Goal/GoalCards.swift
//
// 웹 features/goal/ui/RefineWithAiCard.tsx · PlaceAfterCreateCard.tsx
//  - GoalRefineWithAiCard    : 임시 목표 → "AI로 구체화하기" / "업무형으로 두기"
//  - GoalPlaceAfterCreateCard: AI 목표를 막 만든 직후 "이어서 Task를 TimeTable에 배치할까요?"

import SwiftUI

// MARK: - 임시 목표 → AI 로 구체화하기

/// 임시 목표는 Brain dump 가 대충 만든 그릇이라 기간·마일스톤·루틴이 없어요.
/// 구체화를 확정하면 지금 있는 Task 와 기록은 새 목표로 그대로 옮겨져요.
@MainActor
struct GoalRefineWithAiCard: View {
    let goal: GoalCategory

    @Environment(AppRouter.self) private var router
    @Environment(GoalDetailViewModel.self) private var vm
    @State private var keeping = false

    private var name: String {
        if let e = goal.emoji, !e.isEmpty { return e + " " + goal.goalCategoryName }
        return goal.goalCategoryName
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top, spacing: 14) {
                AIBadge()
                VStack(alignment: .leading, spacing: 4) {
                    Text("AI로 ‘\(name)’ 목표를 구체화해 볼까요?")
                        .font(.pt(16, .semibold))
                        .foregroundStyle(Color.ink)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("아직 임시 목표예요. 몇 가지 질문에 답하면 기간·마일스톤·루틴까지 설계해 드려요. 지금 있는 Task와 기록은 그대로 옮겨져요. 회사 업무 · 취업 준비 · 동아리처럼 기한 없이 쌓아 갈 일이라면 업무형으로 두면 돼요.")
                        .font(.pt(14))
                        .foregroundStyle(Color.ink3)
                        .lineSpacing(3)
                }
            }
            FlowLayout(spacing: 8) {
                // 회사 업무처럼 "이룰 것"이 없는 일은 구체화할 필요 없이 기록형으로 두는 게 맞아요
                NButton("업무형으로 두기", variant: .secondary, icon: "square.and.pencil", loading: keeping) {
                    vm.track("goal_detail.keep_record_click")
                    keep()
                }
                NButton("AI로 구체화하기", variant: .ai, icon: "sparkles") {
                    vm.track("goal_detail.refine_click")
                    router.startGoalCreation(sourceGoalId: goal.goalCategoryId)
                }
            }
        }
        .onAppear {
            if vm.once("refine_card") {
                vm.track("goal_detail.refine_card_view", ["task_count": vm.tasks?.totalCount])
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .aiBorder()
    }

    private func keep() {
        guard !keeping else { return }
        let id = goal.goalCategoryId
        keeping = true
        Task { @MainActor in
            let ok = await Mutation.run(invalidate: [.goals], successMessage: "업무형 목표로 바꿨어요.") {
                try await GoalEditAPI.keepAsRecord(goalId: id)
            }
            vm.track("goal_detail.keep_record_result", GD.result(ok != nil))
            keeping = false
        }
    }
}

// MARK: - 목표를 만든 직후: 이번 주 TimeTable 에 배치할까요?

/// 백엔드는 확정 직후 오늘만 자동으로 채우고 나머지 날은 밤 10시 작업이 하루씩 채워요.
/// 여기서 동의하면 오늘~6일 뒤까지 한 번에 채워서, 목표를 만들자마자 한 주 계획을 볼 수 있어요.
/// (이미 시간이 정해진 블록은 건드리지 않아요 — FILL)
@MainActor
struct GoalPlaceAfterCreateCard: View {
    let goal: GoalCategory
    /// "나중에" · "닫기" (웹: 주소에서 ?created=1 지우기)
    let onDismiss: () -> Void

    @Environment(AppRouter.self) private var router
    @Environment(GoalDetailViewModel.self) private var vm
    @State private var filling = false
    @State private var result: FillResponse? = nil

    /// 목표를 만든 직후 채울 기간: 오늘부터 7일
    private static let days = 7

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top, spacing: 14) {
                AIBadge(icon: result != nil ? "calendar.badge.checkmark" : "sparkles")
                if let result {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("이번 주 TimeTable에 배치했어요").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                        Text(resultText(result)).font(.pt(14)).foregroundStyle(Color.ink3)
                    }
                } else {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("목표가 만들어졌어요 🎉 이어서 Task를 TimeTable에 배치할까요?")
                            .font(.pt(16, .semibold))
                            .foregroundStyle(Color.ink)
                            .fixedSize(horizontal: false, vertical: true)
                        Text("오늘부터 \(Self.days)일 동안 시간이 정해지지 않은 Task와 루틴을 빈 시간에 넣어요. 직접 정한 일정과 고정 시간은 그대로예요. 나중에 해도 밤 10시마다 다음 날이 자동으로 채워져요.")
                            .font(.pt(14))
                            .foregroundStyle(Color.ink3)
                            .lineSpacing(3)
                    }
                }
            }
            HStack(spacing: 8) {
                Spacer(minLength: 0)
                if result != nil {
                    NButton("닫기", variant: .ghost) {
                        vm.track("goal_detail.place_dismiss_click", ["card_state": "after_fill"])
                        onDismiss()
                    }
                    NButton("캘린더에서 보기") {
                        vm.track("goal_detail.place_calendar_click", ["placed_count": result?.placedCount])
                        router.openCalendar(view: .week, date: KST.today())
                    }
                } else {
                    NButton("나중에", variant: .ghost) {
                        vm.track("goal_detail.place_dismiss_click", ["card_state": "before_fill"])
                        onDismiss()
                    }
                        .disabled(filling)
                    NButton("AI로 이번 주 배치하기", variant: .ai, icon: "sparkles", loading: filling) {
                        vm.track("goal_detail.place_fill_click")
                        fill()
                    }
                }
            }
        }
        .onAppear {
            if vm.once("place_card") { vm.track("goal_detail.place_card_view") }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .aiBorder()
    }

    private func resultText(_ r: FillResponse) -> String {
        let tail = r.unscheduledCount > 0
            ? " · \(r.unscheduledCount)개는 빈 시간이 없어 시간 미정으로 남았어요 (밤 10시에 다시 시도해요)"
            : ""
        return "\(r.placedCount)개 Task를 빈 시간에 넣었어요\(tail)."
    }

    private func fill() {
        guard !filling else { return }
        let today = KST.today()
        let end = KST.addDays(today, Self.days - 1)
        filling = true
        Task { @MainActor in
            // 웹 useFillTimetable: 끝나면 캘린더 · 목표(관련 Task 시간)를 다시 불러와요
            let r = await Mutation.run(invalidate: [.timetable, .goals], successMessage: nil) {
                try await TimetableAPI.fill(startDate: today, endDate: end)
            }
            filling = false
            vm.track("goal_detail.place_fill_result", GD.result(r != nil).merging([
                "placed_count": (r ?? nil)?.placedCount, "unscheduled_count": (r ?? nil)?.unscheduledCount,
            ]) { $1 })
            if let r, let res = r {
                TimetableAPI.toastFillResult(res)
                result = res
            }
        }
    }
}

#Preview {
    ScrollView {
        VStack(spacing: 16) {
            GoalPlaceAfterCreateCard(goal: GoalPreviewData.goal) {}
            GoalRefineWithAiCard(goal: GoalPreviewData.tempGoal)
        }
        .padding(16)
    }
    .canvasBackground()
    .environment(GoalDetailViewModel.preview())
    .environment(AppRouter.shared)
}
