// Features/Goal/GoalMetricEditSheet.swift
//
// 목표 상세 "지금 어디쯤?"의 수치만 고치는 시트 (2026-10) — 웹 progress/MetricEditPanel 과 같아요.
// 목표 전체 수정(이름 · 기간 · 색)과 따로, 수치 목표(무엇을 · 단위 · 처음 값 · 목표 값)와 수치 마감일을 고쳐요.
//  - 수치 마감일은 목표 종료일과 따로 움직여요 (V58 metricTargetDate). "목표 종료일과 같게"면 clearMetricTargetDate
//  - "수치 없이 진행하기" = clearMetric

import SwiftUI

@MainActor
struct GoalMetricEditSheet: View {
    let goal: GoalCategory
    /// progress_section · progress_cta · goal_edit
    var entryPoint: String = "progress_section"

    @Environment(GoalDetailViewModel.self) private var vm
    @State private var metricName: String
    @State private var metricUnit: String
    @State private var startValue: String
    @State private var targetValue: String
    /// 수치 마감일을 목표 종료일과 따로 정하는지
    @State private var ownDate: Bool
    @State private var targetDate: String
    @State private var clearMetric = false
    @State private var saving = false
    @State private var outcome: String?

    init(goal: GoalCategory, entryPoint: String = "progress_section") {
        self.goal = goal
        self.entryPoint = entryPoint
        func numStr(_ v: Double?) -> String { v.map { GoalDetailLogic.trim($0, 6) } ?? "" }
        _metricName = State(initialValue: goal.metricName ?? "")
        _metricUnit = State(initialValue: goal.metricUnit ?? "")
        _startValue = State(initialValue: numStr(goal.startValue))
        _targetValue = State(initialValue: numStr(goal.targetValue))
        _ownDate = State(initialValue: goal.metricTargetDate != nil)
        _targetDate = State(initialValue: goal.metricTargetDate ?? goal.goalCategoryEndDate ?? "")
    }

    private var hadMetric: Bool { goal.targetValue != nil || !(goal.metricName ?? "").isEmpty }
    private var nums: [Double?] { [GoalDetailLogic.toNum(startValue), GoalDetailLogic.toNum(targetValue)] }
    private var goalEnd: String? { goal.goalCategoryEndDate }
    private var effectiveDate: String? { ownDate && !targetDate.isEmpty ? targetDate : goalEnd }

    private var error: String? {
        if clearMetric { return nil }
        if metricName.trimmingCharacters(in: .whitespaces).isEmpty { return "무엇을 재는지 적어 주세요." }
        if nums.contains(where: { $0?.isNaN == true }) { return "수치는 숫자로 입력해 주세요." }
        if nums[1] == nil { return "목표 값을 입력해 주세요." }
        if ownDate, !targetDate.isEmpty, let s = goal.goalCategoryStartDate, targetDate < s {
            return "수치 마감일은 목표 시작일(\(GoalDetailLogic.md(s))) 뒤여야 해요."
        }
        return nil
    }

    private var changedFields: [String] {
        var f: [String] = []
        if clearMetric { return ["clear"] }
        if metricName != (goal.metricName ?? "") { f.append("name") }
        if metricUnit != (goal.metricUnit ?? "") { f.append("unit") }
        if nums[0] != goal.startValue { f.append("start_value") }
        if nums[1] != goal.targetValue { f.append("target_value") }
        if (ownDate ? targetDate : nil) != goal.metricTargetDate { f.append("target_date") }
        return f
    }

    var body: some View {
        SheetScaffold(title: hadMetric ? "수치 목표 수정" : "수치 목표 정하기",
                      description: "목표 기간과 따로, 수치만 고쳐요", dismissible: !saving, detents: [.large]) {
            GoalInlineForm(saving: saving, valid: error == nil, error: error, onCancel: {
                outcome = "cancel_button"
                vm.sheet = nil
            }, onSubmit: submit) {
                if clearMetric {
                    HStack(spacing: 8) {
                        Text("저장하면 수치 목표와 마일스톤의 수치가 지워져요. 기록한 값은 남아요.")
                            .font(.pt(13))
                            .foregroundStyle(Color.warningInkStrong)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Button("되돌리기") {
                            vm.track("goal_detail.metric_toggle_click", ["metric_action": "undo", "entry_point": "metric_edit"])
                            clearMetric = false
                        }
                        .font(.pt(13, .semibold))
                        .foregroundStyle(Color.warningInkStrong)
                        .underline()
                        .buttonStyle(.plain)
                    }
                    .padding(12)
                    .background(Color.warningSoft, in: RoundedRectangle(cornerRadius: Radius.xl))
                } else {
                    fields
                }
            }
            .goalSheetForm()
        }
        .onAppear {
            vm.track("goal_detail.metric_edit_open", [
                "goal_category_id": goal.goalCategoryId, "entry_point": entryPoint, "had_metric": hadMetric,
            ])
        }
        .onDisappear {
            if let m = GD.closeMethod(outcome) {
                vm.track("goal_detail.metric_edit_cancel", ["close_method": m, "is_dirty": !changedFields.isEmpty])
            }
        }
    }

    @ViewBuilder private var fields: some View {
        HStack(alignment: .top, spacing: 8) {
            GoalFormField("무엇을 재나요?") {
                TextField("체중", text: $metricName).inputStyle().goalMaxLength($metricName, 30)
            }
            GoalFormField("단위") {
                TextField("kg", text: $metricUnit).inputStyle().goalMaxLength($metricUnit, 10)
            }
            .frame(width: 96)
        }
        HStack(alignment: .top, spacing: 8) {
            GoalFormField("처음 값") {
                TextField("80", text: $startValue).keyboardType(.decimalPad).inputStyle()
            }
            GoalFormField("목표 값") {
                TextField("72", text: $targetValue).keyboardType(.decimalPad).inputStyle()
            }
        }
        GoalFormField("수치 마감일") {
            VStack(alignment: .leading, spacing: 8) {
                Toggle(isOn: $ownDate.animation(.easeOut(duration: 0.15))) {
                    Text(ownDate ? "따로 정하기" : "목표 종료일과 같게" + (goalEnd.map { " (\(GoalDetailLogic.md($0)))" } ?? ""))
                        .font(.pt(14))
                        .foregroundStyle(Color.ink)
                }
                .tint(Color.actionPrimary)
                if ownDate {
                    GoalDateInput(value: $targetDate)
                }
            }
        }
        Text(GoalDetailLogic.metricSentence(name: metricName, unit: metricUnit, start: nums[0], target: nums[1],
                                            startDate: goal.goalCategoryStartDate, endDate: effectiveDate)
             ?? "처음 값과 목표 값을 넣으면 어떤 계획인지 문장으로 보여드려요.")
            .font(.pt(13, .medium))
            .foregroundStyle(Color.ink2)
        Text("목표 기간을 바꿔도 따로 정한 수치 마감일은 그대로예요. 지금 값은 ‘오늘 기록’에서 남기면 바뀌어요.")
            .font(.pt(12))
            .foregroundStyle(Color.ink3)
        if hadMetric {
            Button("수치 없이 진행하기") {
                vm.track("goal_detail.metric_toggle_click", ["metric_action": "clear", "entry_point": "metric_edit"])
                clearMetric = true
            }
            .font(.pt(13, .semibold))
            .foregroundStyle(Color.ink3)
            .buttonStyle(.plain)
        }
    }

    private func submit() {
        guard error == nil, !saving else { return }
        let fields = changedFields
        vm.track("goal_detail.metric_edit_save", [
            "entry_point": entryPoint,
            "changed_fields": fields.joined(separator: ","),
            "metric_action": clearMetric ? "clear" : (hadMetric ? "update" : "add"),
            "own_target_date": ownDate,
        ])
        var o: [String: JSONValue] = [:]
        if clearMetric {
            o["clearMetric"] = .bool(true)
        } else {
            o["metricName"] = .string(metricName.trimmingCharacters(in: .whitespaces))
            o["metricUnit"] = .string(metricUnit.trimmingCharacters(in: .whitespaces))
            o["startValue"] = .of(nums[0])
            o["targetValue"] = .of(nums[1])
            if ownDate && !targetDate.isEmpty {
                o["metricTargetDate"] = .string(targetDate)
            } else if goal.metricTargetDate != nil {
                o["clearMetricTargetDate"] = .bool(true)
            }
        }
        let body = JSONValue.object(o)
        let goalId = goal.goalCategoryId
        saving = true
        Task { @MainActor in
            let ok = await Mutation.run(invalidate: [.goals, .timetable], successMessage: "수치 목표를 고쳤어요.") {
                try await GoalEditAPI.updateGoal(goalId: goalId, body)
            }
            saving = false
            vm.track("goal_detail.metric_edit_result", GD.result(ok != nil))
            if ok != nil {
                outcome = "saved"
                vm.sheet = nil
            }
        }
    }
}
