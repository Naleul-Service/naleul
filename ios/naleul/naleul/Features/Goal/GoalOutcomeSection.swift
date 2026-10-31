// Features/Goal/GoalOutcomeSection.swift
//
// 웹 features/goal/ui/GoalOutcomeSection.tsx
//  - GoalWrapUpSection : 진행 중인 목표를 결과와 함께 마무리 (모달 → 시트)
//  - GoalOutcomeSection: 끝난 목표의 결과 (자동 판정이면 직접 골라 달라고 해요)
//  - GoalAdjustmentBanner: 주간 점검 제안 (2주 연속 실천률이 낮으면 하루 줄이기, 높으면 하루 늘리기)

import SwiftUI

/// 결과 선택지 (웹 OUTCOME_OPTIONS)
struct GoalOutcomeOption: Hashable, Identifiable {
    var id: OutcomeResult { value }
    let value: OutcomeResult
    let emoji: String
    let label: String
    let hint: String

    static let all: [GoalOutcomeOption] = [
        GoalOutcomeOption(value: .ACHIEVED, emoji: "🎉", label: "달성했어요", hint: "목표한 만큼 해냈어요"),
        GoalOutcomeOption(value: .PARTIAL, emoji: "🙂", label: "일부 달성", hint: "절반 이상은 해냈어요"),
        GoalOutcomeOption(value: .NOT_ACHIEVED, emoji: "😣", label: "못 했어요", hint: "이번엔 어려웠어요"),
    ]

    static func of(_ r: OutcomeResult) -> GoalOutcomeOption { all.first { $0.value == r } ?? all[1] }
}

@MainActor
struct GoalOutcomePicker: View {
    let value: OutcomeResult?
    var disabled = false
    let onChange: (OutcomeResult) -> Void

    var body: some View {
        HStack(spacing: 8) {
            ForEach(GoalOutcomeOption.all) { o in
                let selected = value == o.value
                Button { onChange(o.value) } label: {
                    VStack(spacing: 4) {
                        Text(o.emoji).font(.system(size: 22))
                        Text(o.label).font(.pt(13, .bold)).foregroundStyle(selected ? Color.textBrand : Color.ink)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(selected ? Color.brandSoft : Color.clear, in: RoundedRectangle(cornerRadius: Radius.xxl))
                    .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(selected ? Color.brand : Color.borderSubtle, lineWidth: 1))
                    .contentShape(RoundedRectangle(cornerRadius: Radius.xxl))
                }
                .buttonStyle(.plain)
                .disabled(disabled)
                .opacity(disabled ? 0.6 : 1)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
    }
}

// MARK: - 진행 중: 목표 마무리

/// 진행 중인 달성형 목표를 결과와 함께 끝내요. 고른 결과는 다음 목표 추천의 근거가 돼요
@MainActor
struct GoalWrapUpSection: View {
    let goal: GoalCategory
    let metricDone: Bool
    @Environment(GoalDetailViewModel.self) private var vm

    /// wrapup_prompt 값 (metric_done · period_over · normal)
    static func prompt(_ goal: GoalCategory, metricDone: Bool) -> String {
        if metricDone { return "metric_done" }
        if let end = goal.goalCategoryEndDate, end < KST.today() { return "period_over" }
        return "normal"
    }

    var body: some View {
        let periodOver = (goal.goalCategoryEndDate.map { $0 < KST.today() }) ?? false
        let strong = metricDone || periodOver
        let lead = metricDone
            ? "목표값에 도달했어요! 결과를 남기고 마무리해 볼까요?"
            : (periodOver ? "기간이 끝났어요. 어땠는지 남기고 마무리해요." : "목표를 끝냈다면 결과를 남겨 주세요. 다음 목표를 짤 때 참고해요.")

        SectionCard("목표 마무리") {
            VStack(alignment: .leading, spacing: 12) {
                Text(lead)
                    .font(.pt(14, strong ? .semibold : .regular))
                    .foregroundStyle(strong ? Color.ink : Color.ink2)
                NButton("마무리하기", variant: strong ? .primary : .secondary, icon: "flag", fullWidth: true) {
                    vm.track("goal_detail.wrapup_open", ["wrapup_prompt": Self.prompt(goal, metricDone: metricDone), "entry_point": "plan_card"])
                    vm.sheet = .wrapUp
                }
            }
        }
    }
}

/// "이 목표, 어땠나요?" 시트
@MainActor
struct GoalWrapUpSheet: View {
    let goal: GoalCategory
    let metricDone: Bool

    @Environment(GoalDetailViewModel.self) private var vm
    @State private var result: OutcomeResult?
    @State private var achievement = ""
    @State private var saving = false
    @State private var outcome: String?

    init(goal: GoalCategory, metricDone: Bool) {
        self.goal = goal
        self.metricDone = metricDone
        _result = State(initialValue: metricDone ? .ACHIEVED : nil)
    }

    var body: some View {
        SheetScaffold(title: "이 목표, 어땠나요?", description: "오늘 날짜로 마무리해요. 남은 기간의 루틴과 예정된 할 일은 캘린더에서 빠지고, 지난 기록과 완료한 일은 그대로 남아요.",
                      dismissible: !saving) {
            VStack(alignment: .leading, spacing: 16) {
                GoalOutcomePicker(value: result, disabled: saving) {
                    vm.track("goal_detail.wrapup_outcome_select", ["outcome_result": $0.rawValue])
                    result = $0
                }
                VStack(alignment: .leading, spacing: 6) {
                    Text("한 줄 소감 (선택)").font(.pt(13, .semibold)).foregroundStyle(Color.ink2)
                    TextField("예) 주 3회 운동은 지켰는데 식단은 어려웠어요", text: $achievement, axis: .vertical)
                        .lineLimit(3...6)
                        .inputStyle(minHeight: 80)
                        .goalMaxLength($achievement, 500)
                        .disabled(saving)
                }
            }
        } footer: {
            NButton("취소", variant: .secondary) {
                outcome = "cancel_button"
                vm.sheet = nil
            }
                .disabled(saving)
            NButton("마무리하기", loading: saving) { submit() }
                .disabled(result == nil)
        }
        .onDisappear {
            if let m = GD.closeMethod(outcome) {
                vm.track("goal_detail.wrapup_cancel", ["close_method": m, "outcome_result": result?.rawValue])
            }
        }
    }

    private func submit() {
        guard let r = result, !saving else { return }
        let trimmed = achievement.trimmingCharacters(in: .whitespacesAndNewlines)
        let a: String? = trimmed.isEmpty ? nil : trimmed
        let id = goal.goalCategoryId
        let prompt = GoalWrapUpSection.prompt(goal, metricDone: metricDone)
        vm.track("goal_detail.wrapup_submit", [
            "outcome_result": r.rawValue, "achievement_length": trimmed.count,
            "was_preselected": metricDone && r == .ACHIEVED, "wrapup_prompt": prompt,
        ])
        saving = true
        Task { @MainActor in
            let ok = await Mutation.run(successMessage: "목표를 마무리했어요. 수고했어요!",
                                        errorFallback: "마무리하지 못했어요. 다시 시도해 주세요.") {
                try await GoalEditAPI.completeGoal(goalId: id, endDate: KST.today(), achievement: a, result: r)
            }
            saving = false
            vm.track("goal_detail.wrapup_result", GD.result(ok != nil).merging(["outcome_result": r.rawValue]) { $1 })
            if ok != nil {
                outcome = "saved"
                // 웹: 성공했을 때만 진행 · 목록 · Task · 캘린더를 다시 불러와요
                // 서버가 남은 루틴을 끝내고 앞날의 일정을 지워서, 캘린더 · 홈도 다시 불러와요
                Invalidation.shared.invalidate(.goals, .timetable, .home)
                vm.sheet = nil
            }
        }
    }
}

// MARK: - 끝난 목표: 결과

@MainActor
struct GoalOutcomeSection: View {
    let goalId: Int
    let outcome: GoalOutcome?

    @Environment(GoalDetailViewModel.self) private var vm
    @State private var editing = false
    @State private var saving = false

    var body: some View {
        let ask = outcome == nil || outcome?.source == "AUTO" || editing
        SectionCard("목표 결과", aside: {
            if outcome?.source == "USER" && !editing {
                Button("바꾸기") {
                    vm.track("goal_detail.outcome_change_click", ["outcome_action": "open"])
                    editing = true
                }
                    .font(.pt(13, .semibold))
                    .foregroundStyle(Color.ink3)
                    .buttonStyle(.plain)
            }
        }) {
            VStack(alignment: .leading, spacing: 0) {
                if let outcome, !editing {
                    let o = GoalOutcomeOption.of(outcome.result)
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text("\(o.emoji) \(o.label)").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                        if outcome.source == "AUTO" {
                            Text("기록으로 자동 판정").font(.pt(12, .medium)).foregroundStyle(Color.ink3)
                        }
                    }
                }
                if let outcome {
                    GoalOutcomeStats(outcome: outcome)
                }
                if ask {
                    VStack(alignment: .leading, spacing: 10) {
                        if outcome != nil && !editing {
                            Rectangle().fill(Color.borderSoft).frame(height: 1).padding(.bottom, 6)
                        }
                        Text(editing ? "결과를 다시 골라 주세요" : "이 목표 어땠나요? 직접 골라 주면 다음 목표가 더 잘 맞춰져요")
                            .font(.pt(14, .semibold))
                            .foregroundStyle(Color.ink2)
                        GoalOutcomePicker(value: editing ? outcome?.result : nil, disabled: saving) { choose($0) }
                        if editing {
                            Button("취소") {
                                vm.track("goal_detail.outcome_change_click", ["outcome_action": "cancel"])
                                editing = false
                            }
                                .font(.pt(13, .semibold))
                                .foregroundStyle(Color.ink3)
                                .buttonStyle(.plain)
                        }
                    }
                    .padding(.top, outcome != nil ? 16 : 0)
                    .onAppear {
                        if !editing, vm.once("outcome_ask") {
                            vm.track("goal_detail.outcome_ask_view", [
                                "outcome_source": outcome?.source ?? "none",
                                "auto_result": outcome?.autoResult?.rawValue,
                                "auto_reason": outcome?.autoReason,
                            ])
                        }
                    }
                }
            }
        }
    }

    private func choose(_ r: OutcomeResult) {
        guard !saving else { return }
        let id = goalId
        vm.track("goal_detail.outcome_select", [
            "outcome_result": r.rawValue, "outcome_source": outcome?.source ?? "none", "is_change": editing,
            "auto_result": outcome?.autoResult?.rawValue,
            "is_match": outcome?.autoResult.map { $0 == r },
        ])
        saving = true
        Task { @MainActor in
            let res = await Mutation.run(successMessage: "결과를 남겼어요.", errorFallback: "저장하지 못했어요. 다시 시도해 주세요.") {
                try await GoalEditAPI.updateOutcome(goalId: id, result: r)
            }
            saving = false
            vm.track("goal_detail.outcome_result", GD.result(res != nil).merging(["outcome_result": r.rawValue]) { $1 })
            if let res {
                editing = false
                if let p = res { vm.progress = p }
            }
        }
    }
}

@MainActor
private struct GoalOutcomeStats: View {
    let outcome: GoalOutcome

    private var stats: [String] {
        var out: [String] = []
        if let v = outcome.metricPercent { out.append("수치 \(GoalDetailLogic.trim(v, 1))%") }
        if let v = outcome.routineRate { out.append("루틴 실천 \(GoalDetailLogic.trim(v, 1))%") }
        if let v = outcome.taskRate { out.append("할 일 완료 \(GoalDetailLogic.trim(v, 1))%") }
        return out
    }

    var body: some View {
        let list = stats
        if !list.isEmpty || outcome.metricUnrecorded == true || (outcome.source == "AUTO" && outcome.autoMessage != nil) {
            VStack(alignment: .leading, spacing: 8) {
                if !list.isEmpty {
                    FlowLayout(spacing: 8) {
                        ForEach(list, id: \.self) { s in
                            Text(s)
                                .font(.ptMono(13, .medium))
                                .foregroundStyle(Color.ink2)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 4)
                                .background(Color.subtle, in: Capsule())
                        }
                    }
                }
                if outcome.metricUnrecorded == true {
                    Text("수치 기록이 없어서 실천률로 판정했어요.").font(.pt(13)).foregroundStyle(Color.ink3)
                }
                if outcome.source == "AUTO", let m = outcome.autoMessage {
                    Text(m).font(.pt(13)).foregroundStyle(Color.ink3).fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(.top, 12)
        }
    }
}

// MARK: - 진행 중: 주간 점검 제안

@MainActor
struct GoalAdjustmentBanner: View {
    let goalId: Int
    let adjustment: GoalAdjustment

    @Environment(GoalDetailViewModel.self) private var vm
    @State private var applying = false
    @State private var dismissing = false

    private var down: Bool { adjustment.direction == "DOWN" }
    private var busy: Bool { applying || dismissing }

    var body: some View {
        let day = adjustment.day.label
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top, spacing: 6) {
                NSymbol(down ? "calendar.badge.minus" : "calendar.badge.plus", size: 14, weight: .semibold)
                Text("주간 점검 · \(adjustment.routineName)").font(.pt(14, .bold))
                Spacer(minLength: 0)
                IconButton(systemName: "xmark", size: 14, color: .ink3, label: "이번엔 넘기기") { dismiss(method: "x_button") }
                    .disabled(busy)
                    .padding(.top, -6)
                    .padding(.trailing, -8)
            }
            .foregroundStyle(down ? Color.warningInk : Color.textBrand)

            Text(adjustment.message).font(.pt(14)).foregroundStyle(Color.ink2)
            Text("지난 2주 실천률 \(GoalDetailLogic.trim(adjustment.previousWeekRate, 1))% → \(GoalDetailLogic.trim(adjustment.lastWeekRate, 1))% · 주 \(adjustment.fromDays)회 → \(adjustment.toDays)회")
                .font(.ptMono(13))
                .foregroundStyle(Color.ink3)
            HStack(spacing: 8) {
                NButton(down ? "\(day)요일 빼기" : "\(day)요일 더하기", size: .sm, loading: applying) {
                    if !busy { vm.track("goal_detail.adjust_apply_click", ["adjust_direction": adjustment.direction]) }
                    apply()
                }
                    .disabled(busy)
                NButton("지금 그대로 할게요", variant: .ghost, size: .sm) { dismiss(method: "keep_button") }
                    .disabled(busy)
            }
            .padding(.top, 8)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(down ? Color.warningSoft : Color.brandSoft, in: RoundedRectangle(cornerRadius: Radius.xxl))
        .overlay(
            RoundedRectangle(cornerRadius: Radius.xxl)
                .stroke(down ? Color.warning.opacity(0.4) : Color.brand.opacity(0.3), lineWidth: 1)
        )
        .onAppear {
            if vm.once("adjust:\(adjustment.key)") {
                vm.track("goal_detail.adjust_banner_view", [
                    "adjust_direction": adjustment.direction,
                    "previous_rate": adjustment.previousWeekRate, "last_rate": adjustment.lastWeekRate,
                ])
            }
        }
    }

    private func apply() {
        guard !busy else { return }
        let id = goalId
        let key = adjustment.key
        applying = true
        Task { @MainActor in
            // 실패해도(이미 루틴이 바뀐 STALE 등) 새 진행 정보로 제안을 다시 받아요 → 항상 .goals
            let r = await Mutation.run(invalidate: [.goals], successMessage: "내일부터 바뀐 요일로 진행해요.",
                                       errorFallback: "바꾸지 못했어요. 다시 시도해 주세요.") {
                try await GoalEditAPI.applyAdjustment(goalId: id, key: key)
            }
            applying = false
            vm.track("goal_detail.adjust_result", GD.result(r != nil).merging(["adjust_action": "apply"]) { $1 })
            if let r {
                if let p = r { vm.progress = p }
                // 루틴 요일이 바뀌면 캘린더도 달라져요
                Invalidation.shared.invalidate(.timetable)
            }
        }
    }

    private func dismiss(method: String) {
        guard !busy else { return }
        vm.track("goal_detail.adjust_dismiss_click", ["adjust_direction": adjustment.direction, "close_method": method])
        let id = goalId
        let key = adjustment.key
        dismissing = true
        Task { @MainActor in
            let r = await Mutation.run(successMessage: nil, errorFallback: "처리하지 못했어요. 다시 시도해 주세요.") {
                try await GoalEditAPI.dismissAdjustment(goalId: id, key: key)
            }
            dismissing = false
            vm.track("goal_detail.adjust_result", GD.result(r != nil).merging(["adjust_action": "dismiss"]) { $1 })
            if let r, let p = r { vm.progress = p }
        }
    }
}

#Preview("결과 · 점검") {
    let p = GoalPreviewData.progress
    return ScrollView {
        VStack(spacing: 16) {
            GoalAdjustmentBanner(goalId: 1, adjustment: p.adjustment!)
            GoalOutcomeSection(goalId: 1, outcome: GoalPreviewData.taskOnlyProgress.outcome)
            GoalOutcomeSection(goalId: 1, outcome: nil)
            GoalWrapUpSection(goal: GoalPreviewData.goal, metricDone: false)
        }
        .padding(16)
    }
    .canvasBackground()
    .environment(GoalDetailViewModel.preview())
}
