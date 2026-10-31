// Features/Goal/GoalOutcomePrompt.swift
//
// 결과 확인 모달 — "OO 목표 기한이 지났어요. 목표는 만족했나요?" (웹 features/goal/ui/GoalOutcomePromptModal.tsx)
//
// 왜 필요한가: 기한이 지나면 서버가 수치(80% 이상) · 실천률로 자동 판정해요. 사용자가 직접 고른 결과와 얼마나 맞는지
// (판정 일치율) 알아야 대시보드의 목표 달성률을 믿을 수 있는데, 목표 상세에 들어가야만 고를 수 있으면 답이 거의 안 모여요.
//  - 고르면 자동 판정을 덮어써요 (PATCH /goal-categories/{id}/outcome)
//  - "나중에"는 자동 판정을 그대로 두고 다시 묻지 않아요 (목표 상세에서는 언제든 바꿀 수 있어요)
//  - 여러 개면 오래된 것부터 하나씩. MainTabView 가 앱을 열 때 · 앞으로 올 때 불러요

import SwiftUI
import Observation

/// GET /v1/goal-outcomes/prompts 한 건
struct OutcomePrompt: Codable, Hashable, Identifiable {
    var id: Int { goalCategoryId }
    var goalCategoryId: Int
    var goalName: String
    var emoji: String?
    var colorCode: String?
    var startDate: String?
    var endDate: String?
    var autoResult: OutcomeResult
    /// METRIC · ROUTINE_RATE · TASK_RATE · NO_RECORD
    var autoReason: String?
    /// 예) "마지막 기록이 목표 수치의 86%예요. 80% 이상이라 자동으로 달성 처리했어요."
    var autoMessage: String?
    var metricPercent: Double?
    var routineRate: Double?
    var taskRate: Double?
    var aiGenerated: Bool?
}

enum OutcomePromptAPI {
    static func list() async throws -> [OutcomePrompt] {
        (try await API.get("/v1/goal-outcomes/prompts")) ?? []
    }

    /// "나중에" — 자동 판정 그대로, 다시 묻지 않아요
    static func dismiss(goalId: Int) async throws {
        try await API.send("POST", "/v1/goal-outcomes/prompts/\(goalId)/dismiss")
    }
}

@MainActor
@Observable
final class OutcomePromptCenter {
    static let shared = OutcomePromptCenter()
    private init() {}

    /// 물어볼 목표 (오래된 순)
    private(set) var queue: [OutcomePrompt] = []
    /// 지금 띄운 목표 (sheet item)
    var current: OutcomePrompt?
    private var loading = false
    /// 이번 실행에서 답했거나 미룬 목표 — "나중에" 요청이 끝나기 전에 다시 불러와도 또 띄우지 않게
    private var handled: Set<Int> = []

    /// 앱을 열 때 · 앞으로 올 때. 실패해도 조용히 넘어가요 (부가 기능)
    func load() async {
        guard !loading, current == nil, UserStore.shared.isLoggedIn, !UserStore.shared.isGuest else { return }
        loading = true
        defer { loading = false }
        guard let list = try? await OutcomePromptAPI.list() else { return }
        queue = list.filter { !handled.contains($0.goalCategoryId) }
        showNext()
    }

    /// 답했거나 미룬 목표를 빼고 다음 것을 띄워요
    func finish(_ goalId: Int) {
        handled.insert(goalId)
        queue.removeAll { $0.goalCategoryId == goalId }
        current = nil
    }

    func showNext() {
        guard current == nil else { return }
        current = queue.first
    }
}

/// 이벤트 공통 속성 (노션 Tracking Plan 「목표 결과 확인 모달」)
private func outcomePromptProps(_ p: OutcomePrompt) -> EventProps {
    [
        "goal_category_id": p.goalCategoryId,
        "auto_result": p.autoResult.rawValue,
        "auto_reason": p.autoReason,
        "is_ai_generated": p.aiGenerated ?? false,
    ]
}

@MainActor
struct GoalOutcomePromptSheet: View {
    let prompt: OutcomePrompt
    let remaining: Int

    @State private var saving = false
    /// 시트가 닫힐 때 — answered · later 면 따로 이벤트를 보냈어요. nil 이면 쓸어내려 닫은 것 (= 나중에)
    @State private var outcome: String?

    var body: some View {
        let auto = GoalOutcomeOption.of(prompt.autoResult)
        SheetScaffold(title: "\(prompt.emoji.map { "\($0) " } ?? "")\(prompt.goalName) 목표 기한이 지났어요",
                      description: "목표는 만족했나요? 직접 골라 주면 다음 목표가 더 잘 맞춰져요.",
                      dismissible: !saving) {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("자동 판정 · \(auto.emoji) \(auto.label)").font(.pt(13, .semibold)).foregroundStyle(Color.ink2)
                    if let m = prompt.autoMessage {
                        Text(m).font(.pt(14)).foregroundStyle(Color.ink).fixedSize(horizontal: false, vertical: true)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xxl))

                VStack(alignment: .leading, spacing: 10) {
                    Text("내가 느끼기엔").font(.pt(14, .semibold)).foregroundStyle(Color.ink2)
                    GoalOutcomePicker(value: nil, disabled: saving) { choose($0) }
                }
                if remaining > 1 {
                    Text("물어볼 목표가 \(remaining - 1)개 더 있어요.").font(.pt(12)).foregroundStyle(Color.ink3)
                }
            }
        } footer: {
            NButton("나중에 (자동 판정 그대로 두기)", variant: .secondary, fullWidth: true) { later("later_button") }
                .disabled(saving)
        }
        .onAppear {
            var p = outcomePromptProps(prompt)
            p["metric_percent"] = prompt.metricPercent
            p["routine_rate"] = prompt.routineRate
            p["task_rate"] = prompt.taskRate
            p["remaining_count"] = remaining
            Analytics.track("goal_outcome_modal.modal_view", p)
        }
        .onDisappear {
            // 쓸어내려 · X 로 닫으면 "나중에"와 같아요 (자동 판정 그대로, 다시 묻지 않음)
            if outcome == nil {
                let hint = Analytics.sheetCloseHint
                Analytics.sheetCloseHint = nil
                later(hint ?? "swipe_down", closing: false)
            }
        }
    }

    private func choose(_ r: OutcomeResult) {
        guard !saving else { return }
        let id = prompt.goalCategoryId
        var props = outcomePromptProps(prompt)
        props["outcome_result"] = r.rawValue
        props["is_match"] = r == prompt.autoResult
        Analytics.track("goal_outcome_modal.outcome_select", props)
        saving = true
        let sent = props   // Task 안에서 var 를 잡지 않게 복사
        Task { @MainActor in
            let res = await Mutation.run(invalidate: [.goals], successMessage: "결과를 남겼어요.",
                                         errorFallback: "저장하지 못했어요. 다시 시도해 주세요.") {
                try await GoalEditAPI.updateOutcome(goalId: id, result: r)
            }
            saving = false
            Analytics.track("goal_outcome_modal.answer_result", sent.merging(GD.result(res != nil)) { $1 })
            if res != nil {
                outcome = "answered"
                OutcomePromptCenter.shared.finish(id)
            }
        }
    }

    /// "나중에" — 버튼이면 시트도 닫아요 (closing), 쓸어내려 닫았으면 이미 닫혔어요
    private func later(_ method: String, closing: Bool = true) {
        if closing && saving { return }
        let id = prompt.goalCategoryId
        var props = outcomePromptProps(prompt)
        props["close_method"] = method
        Analytics.track("goal_outcome_modal.later_click", props)
        outcome = "later"
        OutcomePromptCenter.shared.finish(id)   // 큐에서 빼고 시트를 닫아요 (이미 닫혔으면 큐에서만)
        let sent = props
        Task { @MainActor in
            let ok = (try? await OutcomePromptAPI.dismiss(goalId: id)) != nil
            Analytics.track("goal_outcome_modal.later_result", sent.merging(GD.result(ok)) { $1 })
        }
    }
}

extension View {
    /// 로그인 후 공통 틀(MainTabView)에 붙여요 — 앱을 열 때 · 앞으로 올 때 결과 확인 모달을 띄워요
    func goalOutcomePrompts() -> some View {
        modifier(GoalOutcomePromptsModifier())
    }
}

@MainActor
private struct GoalOutcomePromptsModifier: ViewModifier {
    @State private var center = OutcomePromptCenter.shared
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .sheet(item: $center.current, onDismiss: {
                // 다음 목표는 시트가 완전히 내려간 뒤에 (바로 띄우면 SwiftUI 가 무시해요)
                Task { @MainActor in
                    try? await Task.sleep(for: .milliseconds(350))
                    center.showNext()
                }
            }) { p in
                GoalOutcomePromptSheet(prompt: p, remaining: center.queue.count)
            }
            .task { await center.load() }
            .onChange(of: scenePhase) { _, phase in
                if phase == .active { Task { await center.load() } }
            }
    }
}
