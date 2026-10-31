// Features/Goal/GoalDetailHeader.swift
//
// 웹 features/goal/ui/GoalHeader.tsx — 목표 상세 머리말(이름·상태·기간) · 목표 수정 폼 · 시작한 이유/AI 노트 카드.
// 웹은 연필을 누르면 그 자리가 수정 폼으로 바뀌었어요 → 아이폰은 "수정" 버튼으로 시트를 열어요.
// 삭제는 폼 안의 "목표 삭제" → 확인창.

import SwiftUI

// MARK: - 머리말

@MainActor
struct GoalDetailHeader: View {
    let goal: GoalCategory
    @Environment(GoalDetailViewModel.self) private var vm

    private var period: GoalFormat.Period? {
        GoalFormat.periodProgress(goal.goalCategoryStartDate, goal.goalCategoryEndDate)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Circle().fill(Color.goal(goal.colorCode)).frame(width: 12, height: 12)
                Text(titleText)
                    .font(.pt(20, .semibold))
                    .foregroundStyle(Color.ink)
                    .fixedSize(horizontal: false, vertical: true)
            }
            FlowLayout(spacing: 6) {
                Badge(goal.goalCategoryStatus.label, tone: goal.goalCategoryStatus == .COMPLETED ? .success : .neutral)
                if goal.isTemporary { Badge("임시 목표", tone: .warning) }
                if goal.isRecordGoal { Badge(recordBadge, tone: .neutral) }
                if let kind = goal.goalKindName, goal.goalType != .WORK { Badge(kind, tone: .brand) }
                Button { vm.sheet = .editGoal } label: {
                    HStack(spacing: 4) {
                        NSymbol("pencil", size: 12, weight: .semibold)
                        Text("수정")
                    }
                    .font(.pt(13, .semibold))
                    .foregroundStyle(Color.ink3)
                    .padding(.horizontal, 8)
                    .frame(height: 24)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                // AI 없이 만든 목표형 목표 → 처음엔 시트로 한 번, 그 뒤로는 이 버튼으로 (2026-10)
                if GoalLegacy.isLegacy(goal) {
                    Button {
                        vm.track("goal_detail.legacy_setup_button_click")
                        vm.sheet = .legacySetup(trigger: "button")
                    } label: {
                        HStack(spacing: 4) {
                            NSymbol("sparkles", size: 12, weight: .semibold)
                            Text("목표 형태 정하기")
                        }
                        .font(.pt(13, .semibold))
                        .foregroundStyle(Color.textBrand)
                        .padding(.horizontal, 8)
                        .frame(height: 24)
                        .background(Color.aiTintSoft, in: RoundedRectangle(cornerRadius: Radius.lg))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            if let start = goal.goalCategoryStartDate {
                Text(periodLine(start))
                    .font(.pt(14))
                    .foregroundStyle(Color.ink3)
            }
        }
    }

    private var titleText: String {
        if let e = goal.emoji, !e.isEmpty { return e + " " + goal.goalCategoryName }
        return goal.goalCategoryName
    }

    private var recordBadge: String {
        if goal.goalType == .WORK, let sub = goal.goalSubType { return "업무형 · \(GoalKind.subOption(sub).label)" }
        return "업무형"
    }

    private func periodLine(_ start: String) -> String {
        let end = goal.goalCategoryEndDate.map { GoalFormat.formatDot($0) } ?? "종료일 미정"
        let weeks = period.map { " · \($0.weeks)주" } ?? ""
        return "\(GoalFormat.formatDot(start)) – \(end)\(weeks)"
    }
}

// MARK: - 시작한 이유 · AI 노트 카드 (웹 GoalNotes)

@MainActor
struct GoalNotesCard: View {
    let goal: GoalCategory
    @Environment(GoalDetailViewModel.self) private var vm

    var body: some View {
        if goal.motive != nil || goal.aiNote != nil {
            VStack(alignment: .leading, spacing: 12) {
                if let motive = goal.motive {
                    // 누르면 목표 수정을 열어요 (머리말 수정에서 함께 바뀌어요)
                    Button {
                        vm.track("goal_detail.edit_open", ["goal_mode": goal.analyticsMode, "entry_point": "notes"])
                        vm.sheet = .editGoal
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(GoalDetailLogic.motiveLabel(goal)).font(.pt(13, .medium)).foregroundStyle(Color.ink3)
                            Text(motive)
                                .font(.pt(15))
                                .foregroundStyle(Color.ink)
                                .lineSpacing(4)
                                .multilineTextAlignment(.leading)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                if let note = goal.aiNote {
                    HStack(alignment: .top, spacing: 8) {
                        AIBadge(size: .sm)
                        Text(note).font(.pt(14)).foregroundStyle(Color.ink).lineSpacing(3)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .aiBorder(cornerRadius: Radius.xl)
                }
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .card()
        }
    }
}

// MARK: - 목표 수정 시트 (웹 GoalEditForm)

/// 이름·이모지·기간·색·시작한 이유·수치 목표.
/// 수치는 "시작값 · 목표값 · 종료일" 세 가지만 정해요. 마일스톤 수치는 이 값으로 자동 계산되고,
/// "현재" 값은 "지금 어디쯤?"에서 남긴 기록으로만 바뀌어요.
@MainActor
struct GoalEditFormSheet: View {
    let goal: GoalCategory
    let colors: [UserColor]

    @Environment(GoalDetailViewModel.self) private var vm
    @Environment(AppRouter.self) private var router

    @State private var emoji: String
    @State private var name: String
    @State private var start: String
    @State private var end: String
    @State private var colorId: Int? = nil
    @State private var motive: String
    @State private var metricName: String
    @State private var metricUnit: String
    @State private var startValue: String
    @State private var targetValue: String
    @State private var kind: GoalKindValue?
    @State private var showMetric: Bool
    /// 수치를 지우려고 하는지 ("수치 없이 진행하기")
    @State private var clearMetric = false
    @State private var saving = false
    @State private var confirmDelete = false
    @State private var deleting = false
    /// 시트가 어떻게 닫혔는지 (cancel_button · saved · deleted — 없으면 X · 쓸어 닫기)
    @State private var outcome: String?

    init(goal: GoalCategory, colors: [UserColor]) {
        self.goal = goal
        self.colors = colors
        func numStr(_ v: Double?) -> String { v.map { GoalDetailLogic.trim($0, 6) } ?? "" }
        _emoji = State(initialValue: goal.emoji ?? "")
        _name = State(initialValue: goal.goalCategoryName)
        _start = State(initialValue: goal.goalCategoryStartDate ?? "")
        _end = State(initialValue: goal.goalCategoryEndDate ?? "")
        _motive = State(initialValue: goal.motive ?? "")
        _metricName = State(initialValue: goal.metricName ?? "")
        _metricUnit = State(initialValue: goal.metricUnit ?? "")
        _startValue = State(initialValue: numStr(goal.startValue))
        _targetValue = State(initialValue: numStr(goal.targetValue))
        if let t = goal.goalType, let s = goal.goalSubType {
            _kind = State(initialValue: GoalKindValue(goalType: t, goalSubType: s, goalKindLabel: goal.goalKindLabel ?? ""))
        } else {
            _kind = State(initialValue: nil)
        }
        _showMetric = State(initialValue: goal.targetValue != nil || !(goal.metricName ?? "").isEmpty)
    }

    /// 업무형은 기한 · 수치 · 카테고리가 없어요 (마감은 주제마다, V53) → 기간 · 수치 · 카테고리 칸을 숨겨요
    private var isWork: Bool { goal.isRecordGoal }
    /// 기본 생활 습관 그릇(V52)은 이름 · 이모지 · 색만 바꿀 수 있어요 (지울 수도 없어요)
    private var isHabit: Bool { goal.isHabitGoal }
    private var hadMetric: Bool { goal.targetValue != nil || !(goal.metricName ?? "").isEmpty }
    private var effectiveColorId: Int? { colorId ?? GoalDetailLogic.colorIdOf(colors, goal.colorCode) }
    private var nums: [Double?] { [GoalDetailLogic.toNum(startValue), GoalDetailLogic.toNum(targetValue)] }
    private var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }

    private var error: String? {
        if trimmedName.isEmpty { return "목표 이름을 입력해 주세요." }
        if !start.isEmpty && !end.isEmpty && start > end { return "종료일이 시작일보다 빨라요." }
        if nums.contains(where: { $0?.isNaN == true }) { return "수치는 숫자로 입력해 주세요." }
        if !isWork && !isHabit, let kind, !GoalKind.isComplete(kind) { return "기타 카테고리 이름을 1~10자로 적어 주세요." }
        return nil
    }

    /// 화면에 보이는 오류 문구 (이름이 비면 문구 없이 막혀요)
    private var shownError: String? { trimmedName.isEmpty ? nil : error }

    /// 수치 칸 상태 (add · clear · none)
    private var metricAction: String {
        if clearMetric { return "clear" }
        if showMetric && !hadMetric { return "add" }
        if showMetric { return "update" }
        return "none"
    }

    /// 바뀐 칸들 (name, emoji, period, kind, color, motive, metric)
    private var changedFields: [String] {
        var f: [String] = []
        if trimmedName != goal.goalCategoryName { f.append("name") }
        if emoji.trimmingCharacters(in: .whitespacesAndNewlines) != (goal.emoji ?? "") { f.append("emoji") }
        if !isWork && !isHabit && (start != (goal.goalCategoryStartDate ?? "") || end != (goal.goalCategoryEndDate ?? "")) { f.append("period") }
        if !isWork && !isHabit && GoalKind.isComplete(kind)
            && (kind?.goalType != goal.goalType || kind?.goalSubType != goal.goalSubType || (kind?.goalKindLabel ?? "") != (goal.goalKindLabel ?? "")) { f.append("kind") }
        if colorId != nil { f.append("color") }
        if motive.trimmingCharacters(in: .whitespacesAndNewlines) != (goal.motive ?? "") { f.append("motive") }
        if !isWork && !isHabit {
            if clearMetric { f.append("metric") }
            else if showMetric && (metricName != (goal.metricName ?? "") || metricUnit != (goal.metricUnit ?? "")
                                   || nums[0] != goal.startValue || nums[1] != goal.targetValue) { f.append("metric") }
        }
        return f
    }

    var body: some View {
        SheetScaffold(title: "목표 수정", dismissible: !saving && !deleting, detents: [.large]) {
            GoalInlineForm(saving: saving, valid: error == nil, error: trimmedName.isEmpty ? nil : error,
                           onCancel: {
                               outcome = "cancel_button"
                               vm.sheet = nil
                           }, onSubmit: { submit(method: "button") }) {
                fields
            } extra: {
                if !isHabit {
                Button {
                    vm.track("goal_detail.delete_click", ["goal_mode": goal.analyticsMode, "goal_status": goal.goalCategoryStatus.rawValue])
                    confirmDelete = true
                } label: {
                    HStack(spacing: 4) {
                        NSymbol("trash", size: 12, weight: .semibold)
                        Text("목표 삭제")
                    }
                    .font(.pt(13, .semibold))
                    .foregroundStyle(Color.danger)
                }
                .buttonStyle(.plain)
                .disabled(saving || deleting)
                }
            }
            .goalSheetForm()
        }
        .confirmDialog(
            isPresented: $confirmDelete,
            title: "'\(goal.goalCategoryName)' 목표를 삭제할까요?",
            message: "지금 이후로 예정된 Task와 루틴은 모두 지워지고, 지난 Task·완료한 기록은 남아요. 되돌릴 수 없어요.",
            confirmLabel: "삭제",
            tone: .danger,
            onCancel: { vm.track("goal_detail.delete_cancel") }
        ) {
            vm.track("goal_detail.delete_confirm", [
                "goal_mode": goal.analyticsMode, "goal_status": goal.goalCategoryStatus.rawValue,
                "routine_count": goal.analyticsRoutineCount,
            ])
            Task { @MainActor in await deleteGoal() }
        }
        .onChange(of: shownError) { _, e in
            if let e { vm.track("goal_detail.edit_error", ["validation_error": e]) }
        }
        .onDisappear {
            if let m = GD.closeMethod(outcome) {
                vm.track("goal_detail.edit_cancel", ["close_method": m, "is_dirty": !changedFields.isEmpty])
            }
        }
    }

    @ViewBuilder private var fields: some View {
        HStack(alignment: .top, spacing: 8) {
            GoalFormField("이모지") {
                TextField("🎯", text: $emoji)
                    .multilineTextAlignment(.center)
                    .inputStyle()
                    .goalMaxLength($emoji, 16)
            }
            .frame(width: 72)
            GoalFormField("목표 이름") {
                TextField("", text: $name)
                    .inputStyle()
                    .goalMaxLength($name, 50)
                    .submitLabel(.done)
                    .onSubmit { if error == nil { submit(method: "keyboard_done") } }
            }
        }
        if !isWork && !isHabit {
            HStack(alignment: .top, spacing: 8) {
                GoalFormField("시작일") { GoalDateInput(value: $start, optional: true) }
                GoalFormField("종료일") { GoalDateInput(value: $end, optional: true) }
            }
            // 업무형은 회사 업무 · 취업 준비 · 동아리처럼 종류가 다양해서 카테고리를 고르지 않아요
            GoalFormField("카테고리") {
                GoalKindPicker(value: $kind, compact: true)
            }
        }
        GoalFormField("색상") {
            ColorPickerRow(colors: colors, selectedId: Binding(get: { effectiveColorId }, set: { colorId = $0 }))
        }
        if !isHabit {
            GoalFormField(isWork ? "\(GoalDetailLogic.motiveLabel(goal)) (선택)" : "이 목표를 시작한 이유") {
                TextField("", text: $motive, axis: .vertical)
                    .lineLimit(2...6)
                    .inputStyle()
                    .goalMaxLength($motive, 500)
            }
        }
        if !isWork && !isHabit {
            // 수치 목표는 "지금 어디쯤?"의 "수치 수정"에서 따로 고쳐요 (2026-10 · 목표 기간과 수치 마감일을 따로)
            Button {
                outcome = "next"
                vm.sheet = .editMetric(entryPoint: "goal_edit")
            } label: {
                HStack(spacing: 4) {
                    Text(hadMetric ? "수치 목표 수정하기" : "+ 수치 목표 추가 (예: 체중 80 → 72kg)")
                    Image(systemName: "chevron.right").font(.system(size: 11, weight: .semibold))
                }
                .font(.pt(13, .semibold))
                .foregroundStyle(Color.textBrand)
            }
            .buttonStyle(.plain)
        }
    }

    @ViewBuilder private var metricBlock: some View {
        if showMetric && !clearMetric {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .top, spacing: 8) {
                    GoalFormField("무엇을 재나요?") {
                        TextField("체중", text: $metricName).inputStyle().goalMaxLength($metricName, 30)
                    }
                    GoalFormField("단위") {
                        TextField("kg", text: $metricUnit).inputStyle().goalMaxLength($metricUnit, 10)
                    }
                }
                HStack(alignment: .top, spacing: 8) {
                    GoalFormField("처음 값") {
                        TextField("80", text: $startValue).keyboardType(.decimalPad).inputStyle()
                    }
                    GoalFormField("목표 값") {
                        TextField("72", text: $targetValue).keyboardType(.decimalPad).inputStyle()
                    }
                }
                // 숫자의 의미를 문장으로 — "체중 80kg → 72kg, 1월 8일까지 · 주 평균 -0.6kg"
                Text(GoalDetailLogic.metricSentence(name: metricName, unit: metricUnit, start: nums[0], target: nums[1],
                                                    startDate: start, endDate: end)
                     ?? "처음 값과 목표 값을 넣으면 어떤 계획인지 문장으로 보여드려요.")
                    .font(.pt(13, .medium))
                    .foregroundStyle(Color.ink2)
                Text("마일스톤 수치는 이 값과 기간으로 자동 계산돼요. 지금 값은 ‘지금 어디쯤?’에서 기록하면 바뀌어요.")
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
                if hadMetric {
                    Button("수치 없이 진행하기") {
                        vm.track("goal_detail.metric_toggle_click", ["metric_action": "clear"])
                        clearMetric = true
                    }
                        .font(.pt(13, .semibold))
                        .foregroundStyle(Color.ink3)
                        .buttonStyle(.plain)
                }
            }
            .padding(12)
            .background(Color.subtle.opacity(0.6), in: RoundedRectangle(cornerRadius: Radius.xl))
        } else if clearMetric {
            HStack(spacing: 8) {
                Text("저장하면 수치 목표와 마일스톤의 수치가 지워져요. 기록한 값은 남아요.")
                    .font(.pt(13))
                    .foregroundStyle(Color.warningInkStrong)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button("되돌리기") {
                    vm.track("goal_detail.metric_toggle_click", ["metric_action": "undo"])
                    clearMetric = false
                }
                    .font(.pt(13, .semibold))
                    .foregroundStyle(Color.warningInkStrong)
                    .underline()
                    .buttonStyle(.plain)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(Color.warningSoft, in: RoundedRectangle(cornerRadius: Radius.xl))
        } else {
            Button("+ 수치 목표 추가 (예: 체중 80 → 72kg)") {
                vm.track("goal_detail.metric_toggle_click", ["metric_action": "add"])
                showMetric = true
            }
                .font(.pt(13, .semibold))
                .foregroundStyle(Color.textBrand)
                .buttonStyle(.plain)
        }
    }

    private func submit(method: String) {
        guard error == nil, !saving else { return }
        vm.track("goal_detail.edit_save", [
            "goal_mode": goal.analyticsMode, "changed_fields": changedFields.joined(separator: ","),
            "metric_action": "none", "submit_method": method,
        ])
        var o: [String: JSONValue] = [
            "goalCategoryName": .string(trimmedName),
            "emoji": .string(emoji.trimmingCharacters(in: .whitespacesAndNewlines)),
            "motive": .string(motive.trimmingCharacters(in: .whitespacesAndNewlines)),
        ]
        if !start.isEmpty && !isHabit { o["goalCategoryStartDate"] = .string(start) }
        // 업무형은 전체 마감이 없어요 (V53 — 마감은 주제마다)
        if !end.isEmpty && !isWork && !isHabit { o["goalCategoryEndDate"] = .string(end) }
        if let colorId { o["colorId"] = .int(colorId) }
        // 카테고리는 2단계까지 고른 경우에만 (고르다 만 건 기존 값 유지) · 업무형 · 습관은 카테고리 없음
        if !isWork && !isHabit && GoalKind.isComplete(kind) {
            for (k, v) in GoalKind.body(kind) { o[k] = v }
        }
        // 수치는 GoalMetricEditSheet 에서 따로 저장해요 (여기서는 보내지 않아요)
        let body = JSONValue.object(o)
        let goalId = goal.goalCategoryId
        saving = true
        Task { @MainActor in
            let ok = await Mutation.run(invalidate: [.goals, .timetable], successMessage: "목표를 수정했어요.") {
                try await GoalEditAPI.updateGoal(goalId: goalId, body)
            }
            saving = false
            vm.track("goal_detail.edit_result", GD.result(ok != nil))
            if ok != nil {
                outcome = "saved"
                vm.sheet = nil
            }
        }
    }

    private func deleteGoal() async {
        deleting = true
        let goalId = goal.goalCategoryId
        let ok = await Mutation.run(invalidate: [.goals, .timetable], successMessage: "목표를 삭제했어요.") {
            try await GoalEditAPI.deleteGoal(goalId: goalId)
        }
        deleting = false
        vm.track("goal_detail.delete_result", GD.result(ok != nil))
        if ok != nil {
            outcome = "deleted"
            vm.sheet = nil
            router.openGoalList()
        }
    }
}

#Preview("머리말") {
    let vm = GoalDetailViewModel.preview()
    return ScrollView {
        VStack(alignment: .leading, spacing: 16) {
            GoalDetailHeader(goal: GoalPreviewData.goal)
            GoalDetailHeader(goal: GoalPreviewData.recordGoal)
            GoalNotesCard(goal: GoalPreviewData.goal)
        }
        .padding(16)
    }
    .canvasBackground()
    .environment(vm)
    .environment(AppRouter.shared)
}

#Preview("목표 수정") {
    Color.canvas
        .sheet(isPresented: .constant(true)) {
            GoalEditFormSheet(goal: GoalPreviewData.goal, colors: GoalPreviewData.colors)
                .environment(GoalDetailViewModel.preview())
                .environment(AppRouter.shared)
        }
}
