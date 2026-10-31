// Features/Goal/GoalLegacySetupSheet.swift
//
// 웹 features/goal/ui/LegacyGoalSetup.tsx — AI 없이 만든 목표형 목표 → "목표 형태 정하기" (2026-10).
//  - 목표마다 처음 들어오면 한 번 자동으로 떠요 (이 기기에서). 그 뒤로는 머리말의 "목표 형태 정하기" 버튼으로
//  - 고르고(라디오) "확인"을 눌러야 진행돼요. "건너뛰기"는 왼쪽
//     · AI 와 설계 → 목표 만들기(sourceGoalId). 확정하면 이 목표에 계획이 채워지고 지금 하는 루틴 · 세부 목표는 이어서 써요
//     · 업무형 → 한 번 더 확인 → PATCH /goal-categories/{id}/convert-to-record
//     · 수치만 직접 → 수치 수정 시트 (entry_point=legacy_setup)

import SwiftUI

enum GoalLegacy {
    /// AI 없이 만든 목표형 목표인지 (예전 앱 · 직접 만들기). 수치를 정하거나 AI 로 설계하면 해당하지 않아요
    static func isLegacy(_ g: GoalCategory) -> Bool {
        !g.isTemporary && !(g.aiGenerated ?? false) && !g.isRecordGoal && !g.isHabitGoal
            && (g.metricName ?? "").isEmpty && g.targetValue == nil
            && g.goalCategoryStatus != .COMPLETED && g.goalCategoryStatus != .DELETED
    }

    private static func seenKey(_ id: Int) -> String { "naleul.legacyGoalSetup.seen.\(id)" }

    /// 처음 들어왔을 때 한 번만 자동으로 (보면 기록)
    static func takeAutoOpen(_ g: GoalCategory) -> Bool {
        guard isLegacy(g) else { return false }
        let key = seenKey(g.goalCategoryId)
        if UserDefaults.standard.bool(forKey: key) { return false }
        UserDefaults.standard.set(true, forKey: key)
        return true
    }
}

@MainActor
struct GoalLegacySetupSheet: View {
    let goal: GoalCategory
    /// auto(처음 들어왔을 때) · button(머리말 버튼)
    let trigger: String

    private enum Step { case choose, record }
    private enum Choice: String { case achievement_ai, record, achievement_manual }

    @Environment(GoalDetailViewModel.self) private var vm
    @Environment(AppRouter.self) private var router
    @State private var step: Step = .choose
    @State private var choice: Choice?
    @State private var converting = false
    /// later · saved · next (닫힌 이유 — 이벤트용)
    @State private var outcome: String?

    var body: some View {
        SheetScaffold(
            title: step == .record ? "업무형 목표로 바꿀까요?" : "이 목표, 어떤 형태로 이어갈까요?",
            description: step == .choose
                ? "아직 목표 수치와 계획이 없어요. 목표 성격에 맞게 골라 주시면 화면을 그에 맞게 바꿔 드려요. 지금까지의 Task · 루틴 · 기록은 어느 쪽이든 그대로 남아요."
                : nil,
            dismissible: !converting
        ) {
            if step == .choose {
                VStack(spacing: 12) {
                    option(.achievement_ai, title: "목표형으로 AI와 설계할게요", ai: true,
                           body: "몸무게 · 점수 · 권수처럼 이룰 수치가 있어요. 몇 가지 질문에 답하면 수치 목표와 루틴을 설계해 이 목표에 채워 드려요. 지금 하는 루틴 · 세부 목표는 그대로 이어서 써요.")
                    option(.record, title: "업무형으로 바꿀게요", icon: "briefcase",
                           body: "회사 업무 · 사이드 프로젝트처럼 정해진 수치 없이 한 일을 쌓아 가요. 세부 목표별 보드로 관리해요.")
                    option(.achievement_manual, title: "AI 없이 목표 수치만 직접 정할게요", icon: "target", body: nil)
                }
            } else {
                Text("세부 목표별 보드로 보여 드려요. Task · 루틴 · 기록은 그대로 남고, 목표 종료일은 비워져요 (업무형은 세부 목표마다 마감을 정해요).")
                    .font(.pt(14))
                    .foregroundStyle(Color.ink2)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        } footer: {
            if step == .choose {
                // 건너뛰기는 왼쪽 · 확인은 오른쪽 (고른 뒤에만 눌러져요)
                NButton("건너뛰기", variant: .secondary) {
                    outcome = "later"
                    vm.sheet = nil
                }
                NButton("확인") { confirm() }
                    .disabled(choice == nil)
            } else {
                NButton("돌아가기", variant: .secondary) {
                    vm.track("goal_detail.legacy_setup_record_cancel", ["close_method": "back"])
                    step = .choose
                }
                .disabled(converting)
                NButton("업무형으로 바꾸기", loading: converting) { toRecord() }
            }
        }
        .onAppear {
            vm.track("goal_detail.legacy_setup_view", ["goal_status": goal.goalCategoryStatus.rawValue, "trigger": trigger])
        }
        .onDisappear {
            guard let m = GD.closeMethod(outcome) else { return }
            if step == .record { vm.track("goal_detail.legacy_setup_record_cancel", ["close_method": m]) }
            vm.track("goal_detail.legacy_setup_dismiss", ["close_method": m, "step": step == .record ? "record" : "choose"])
        }
    }

    private func option(_ c: Choice, title: String, icon: String? = nil, ai: Bool = false, body: String?) -> some View {
        let on = choice == c
        return Button { choice = c } label: {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 8) {
                        if ai { AIBadge(size: .sm) } else if let icon {
                            NSymbol(icon, size: 16, weight: .semibold).foregroundStyle(Color.textBrand)
                        }
                        Text(title).font(.pt(body == nil ? 14 : 15, .semibold)).foregroundStyle(Color.ink)
                    }
                    if let body {
                        Text(body).font(.pt(13)).foregroundStyle(Color.ink3)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                ZStack {
                    Circle().stroke(on ? Color.brand : Color.lineStrong, lineWidth: 1.5)
                    if on {
                        Circle().fill(Color.brand)
                        NSymbol("checkmark", size: 10, weight: .bold).foregroundStyle(.white)
                    }
                }
                .frame(width: 20, height: 20)
            }
            .padding(16)
            .background(on ? Color.brandSoft.opacity(0.4) : Color.surface, in: RoundedRectangle(cornerRadius: Radius.xxl))
            .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(on ? Color.brand : Color.line, lineWidth: on ? 1.5 : 1))
            .contentShape(RoundedRectangle(cornerRadius: Radius.xxl))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    /// 고른 뒤 "확인" — 그때 진행해요
    private func confirm() {
        guard let choice else { return }
        vm.track("goal_detail.legacy_setup_select", ["choice": choice.rawValue])
        switch choice {
        case .achievement_ai:
            outcome = "next"
            vm.sheet = nil
            let id = goal.goalCategoryId
            // 시트가 내려간 뒤 목표 만들기를 띄워요 (겹쳐 띄우면 화면이 안 뜰 수 있어요)
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 350_000_000)
                router.startGoalCreation(sourceGoalId: id)
            }
        case .record:
            step = .record
        case .achievement_manual:
            outcome = "next"
            vm.sheet = .editMetric(entryPoint: "legacy_setup")
        }
    }

    private func toRecord() {
        guard !converting else { return }
        let id = goal.goalCategoryId
        vm.track("goal_detail.legacy_setup_record_confirm")
        converting = true
        Task { @MainActor in
            let ok = await Mutation.run(invalidate: [.goals], successMessage: "업무형 목표로 바꿨어요.",
                                        errorFallback: "바꾸지 못했어요. 다시 시도해 주세요.") {
                try await GoalEditAPI.convertToRecord(goalId: id)
            }
            converting = false
            vm.track("goal_detail.legacy_setup_record_result", GD.result(ok != nil))
            if ok != nil {
                outcome = "saved"
                vm.sheet = nil
            }
        }
    }
}
