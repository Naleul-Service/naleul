// Features/TaskAdd/BrainDumpItemEditSheet.swift
//
// 웹 원본: features/brain-dump/ui/ItemEditModal.tsx
//
// 2단계 "변경" — 목표·세부 목표·마일스톤·날짜·시간·소요 시간·중요도를 고쳐요.
// 웹 Modal → 아래에서 올라오는 시트(SheetScaffold), 웹 <select> → Menu, <input type=date/time> → DatePicker.

import SwiftUI

/// 목표 선택 값: "g12" (기존 목표) / "t:t1" (이번에 만드는 임시 목표) / "__new__" (새 임시 목표) / "" (안 고름)
private let newTempValue = "__new__"

@MainActor
struct BrainDumpItemEditSheet: View {
    let goals: [GoalCategory]
    let tempGoals: [TempGoalInput]
    let today: String
    let nextTempKey: String
    let canAddTemp: Bool
    /// 새 임시 목표를 만들었으면 newTemp 도 같이
    let onSave: (BrainDumpMapItem, TempGoalInput?) -> Void

    @State private var f: BrainDumpMapItem
    @State private var goalSel: String
    @State private var newTempName = ""
    @State private var newTempEmoji = "📌"
    /// 처음 값 (changed_fields · is_dirty 비교용)
    private let original: BrainDumpMapItem
    private let initialGoalSel: String
    /// "saved" · "cancel_button" — 없으면 X(Analytics.sheetCloseHint) 또는 아래로 쓸어 닫기
    @State private var outcome: String?
    @Environment(\.dismiss) private var dismiss

    init(item: BrainDumpMapItem, goals: [GoalCategory], tempGoals: [TempGoalInput], today: String,
         nextTempKey: String, canAddTemp: Bool, onSave: @escaping (BrainDumpMapItem, TempGoalInput?) -> Void) {
        self.goals = goals
        self.tempGoals = tempGoals
        self.today = today
        self.nextTempKey = nextTempKey
        self.canAddTemp = canAddTemp
        self.onSave = onSave
        _f = State(initialValue: item)
        let sel: String
        if let gid = item.goalCategoryId { sel = "g\(gid)" }
        else if let tk = item.tempGoalKey { sel = "t:\(tk)" }
        else { sel = "" }
        _goalSel = State(initialValue: sel)
        original = item
        initialGoalSel = sel
    }

    // MARK: 앰플리튜드

    /// 선택 값 → goal_match (existing / temp / new_temp / none)
    private static func goalMatch(_ sel: String) -> String {
        sel == newTempValue ? "new_temp" : sel.hasPrefix("g") ? "existing" : sel.hasPrefix("t:") ? "temp" : "none"
    }

    /// 처음 값과 달라진 칸 (웹 changedFieldsOf 와 같은 이름)
    private var changedFields: [String] {
        let a = original, b = f
        var out: [String] = []
        if a.title.trimmingCharacters(in: .whitespaces) != b.title.trimmingCharacters(in: .whitespaces) { out.append("name") }
        if a.generalCategoryId != b.generalCategoryId { out.append("topic") }
        if a.workTopicId != b.workTopicId { out.append("work_topic") }
        if a.dateType != b.dateType || (b.dateType == .ON && a.scheduledDate != b.scheduledDate) { out.append("date") }
        if b.dateType == .DUE && a.dueDate != b.dueDate { out.append("due_date") }
        if goalSel != initialGoalSel || a.milestoneId != b.milestoneId { out.append("goal") }
        if a.startTime != b.startTime || a.endTime != b.endTime { out.append("time") }
        if (a.estimatedMinutes ?? 30) != (b.estimatedMinutes ?? 30) { out.append("duration") }
        if (a.priority ?? .C) != (b.priority ?? .C) { out.append("priority") }
        if (a.kind ?? .NORMAL) != (b.kind ?? .NORMAL) { out.append("appointment") }
        return out
    }

    private var isDirty: Bool {
        !changedFields.isEmpty || (goalSel == newTempValue && !newTempName.trimmingCharacters(in: .whitespaces).isEmpty)
    }

    /// 저장이 막힌 이유 (흐린 저장을 눌렀을 때)
    private var blockedReason: String? {
        if f.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return "no_title" }
        if goalSel.isEmpty { return "no_goal" }
        if goalSel == newTempValue && newTempName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return "blank_temp" }
        if f.dateType != .NONE && date == nil { return "no_date" }
        if timeInvalid { return "time_order" }
        return nil
    }

    // MARK: 계산 값

    /// 업무형 목표의 주제 (2026-10)
    @State private var workTopics: [WorkTopic] = []
    @State private var workTopicsLoaded = false

    private var goal: GoalCategory? {
        guard goalSel.hasPrefix("g") else { return nil }
        return goals.first { "g\($0.goalCategoryId)" == goalSel }
    }
    /// 세부 목표 (삭제된 것 · "기타 할 일" 그릇은 빼요 — 그릇은 "선택 안 함"과 같아요)
    private var subGoals: [SubGoalInfo] {
        (goal?.generalCategories ?? []).filter { $0.generalCategoryStatus != .DELETED && !($0.defaultBucket ?? false) }
    }
    private var milestones: [MilestoneInfo] {
        (goal?.milestones ?? []).filter { $0.status == .PENDING }
    }
    private var date: String? { f.dateType == .DUE ? f.dueDate : f.scheduledDate }

    private var timeInvalid: Bool {
        guard let s = f.startTime, let e = f.endTime else { return false }
        return e <= s
    }
    private var valid: Bool {
        !f.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !goalSel.isEmpty
            && (goalSel != newTempValue || !newTempName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            && (f.dateType == .NONE || date != nil)
            && !timeInvalid
    }

    private var goalText: String {
        if goalSel == newTempValue { return "+ 새 임시 목표 만들기" }
        if let goal { return (goal.emoji.map { "\($0) " } ?? "") + goal.goalCategoryName }
        if goalSel.hasPrefix("t:") {
            let tk = String(goalSel.dropFirst(2))
            if let t = tempGoals.first(where: { $0.tempKey == tk }) { return (t.emoji.map { "\($0) " } ?? "") + t.name }
        }
        return "목표를 골라 주세요"
    }

    var body: some View {
        SheetScaffold(title: "Task 변경", detents: [.large]) {
            VStack(alignment: .leading, spacing: 16) {
                titleField
                goalField
                if goalSel == newTempValue { newTempBox }
                if let goal, goal.isWorkGoal { workTopicField(goal.goalCategoryId) }
                if goal != nil && ((goal?.isWorkGoal != true && !subGoals.isEmpty) || !milestones.isEmpty) { subFields }
                dateField
                if f.dateType == .ON { timeField }
                minutesField
                priorityField
                appointmentField
            }
        } footer: {
            NButton("취소", variant: .secondary) {
                outcome = "cancel_button"
                dismiss()
            }
            NButton("저장") { save() }
                .disabled(!valid)
                .disabledTap(!valid) {
                    if let r = blockedReason { TA.track("task_add_item_edit.save_blocked", ["disabled_reason": r]) }
                }
        }
        .onAppear {
            // 임시 목표가 이미 5개라 "+ 새 임시 목표 만들기"가 빠진 채 열림
            if !canAddTemp { TA.track("task_add_item_edit.temp_limit_view", ["temp_count": tempGoals.count]) }
        }
        .onDisappear {
            guard let m = GD.closeMethod(outcome) else { return }
            TA.track("task_add_item_edit.close", ["close_method": m, "is_dirty": isDirty])
        }
    }

    // MARK: 칸들

    private func label(_ s: String) -> some View {
        Text(s).font(.pt(12, .medium)).foregroundStyle(Color.ink3)
    }

    private var titleField: some View {
        VStack(alignment: .leading, spacing: 4) {
            label("할 일")
            TextField("", text: Binding(get: { f.title }, set: { f.title = String($0.prefix(100)) }))
                .inputStyle()
                .accessibilityLabel("할 일 이름")
        }
    }

    private var goalField: some View {
        VStack(alignment: .leading, spacing: 4) {
            label("목표")
            BrainDumpMenuField(text: goalText, isPlaceholder: goalSel.isEmpty) {
                if !goals.isEmpty {
                    Section("내 목표") {
                        ForEach(goals) { g in
                            Button((g.emoji.map { "\($0) " } ?? "") + g.goalCategoryName) { selectGoal("g\(g.goalCategoryId)") }
                        }
                    }
                }
                if !tempGoals.isEmpty {
                    Section("이번에 만드는 임시 목표") {
                        ForEach(tempGoals, id: \.tempKey) { t in
                            Button((t.emoji.map { "\($0) " } ?? "") + t.name) { selectGoal("t:\(t.tempKey)") }
                        }
                    }
                }
                if canAddTemp {
                    Button("+ 새 임시 목표 만들기") { selectGoal(newTempValue) }
                }
            }
            .accessibilityLabel("목표")
        }
    }

    private var newTempBox: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                EmojiField(emoji: $newTempEmoji)
                    .accessibilityLabel("임시 목표 이모지")
                TextField("예) 이사 준비", text: Binding(get: { newTempName }, set: { newTempName = String($0.prefix(30)) }))
                    .inputStyle()
                    .accessibilityLabel("임시 목표 이름")
            }
            Text("임시 목표는 나중에 AI 목표 만들기로 구체화할 수 있어요. 무료 목표 개수에 포함되지 않아요.")
                .font(.pt(12))
                .foregroundStyle(Color.ink3)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(12)
        .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xl))
    }

    @ViewBuilder
    private var subFields: some View {
        if goal?.isWorkGoal != true && !subGoals.isEmpty {
            let current = subGoals.first { $0.generalCategoryId == f.generalCategoryId }
            VStack(alignment: .leading, spacing: 4) {
                label("세부 목표")
                BrainDumpMenuField(text: current?.generalCategoryName ?? "선택 안 함 (기타 할 일)", isPlaceholder: false) {
                    Button("선택 안 함 (기타 할 일)") { f.generalCategoryId = nil }
                    ForEach(subGoals) { c in
                        Button(c.generalCategoryName) { f.generalCategoryId = c.generalCategoryId }
                    }
                }
                .accessibilityLabel("세부 목표")
            }
        }
        if !milestones.isEmpty {
            let current = milestones.first { $0.milestoneId == f.milestoneId }
            VStack(alignment: .leading, spacing: 4) {
                label("마일스톤")
                BrainDumpMenuField(text: current.map { "🏁 \($0.title)" } ?? "선택 안 함", isPlaceholder: false) {
                    Button("선택 안 함") { f.milestoneId = nil }
                    ForEach(milestones) { m in
                        Button("🏁 \(m.title)") { f.milestoneId = m.milestoneId }
                    }
                }
                .accessibilityLabel("마일스톤")
            }
        }
    }

    private var dateField: some View {
        VStack(alignment: .leading, spacing: 8) {
            label("날짜")
            HStack(spacing: 6) {
                Chip("그날 하기", selected: f.dateType == .ON, size: .sm) { setDateType(.ON) }
                Chip("그날까지", selected: f.dateType == .DUE, size: .sm) { setDateType(.DUE) }
                Chip("날짜 없음", selected: f.dateType == .NONE, size: .sm) { setDateType(.NONE) }
            }
            if f.dateType != .NONE {
                let current = date ?? today
                DatePicker("날짜",
                           selection: Binding(get: { KST.date(from: current) }, set: { setDate(KST.ymd(from: $0)) }),
                           in: KST.date(from: min(current, today))...,
                           displayedComponents: .date)
                    .labelsHidden()
                    .environment(\.timeZone, KST.timeZone)
                    .tint(Color.brand)
                    .accessibilityLabel("날짜")
            } else {
                Text("빈 시간이 있는 가까운 날에 알아서 배치해요.")
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
            }
        }
    }

    private var timeField: some View {
        VStack(alignment: .leading, spacing: 4) {
            label("시간 (선택)")
            HStack(spacing: 8) {
                BrainDumpTimeField(value: $f.startTime, label: "시작 시각", defaultValue: "09:00", wide: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                BrainDumpTimeField(value: $f.endTime, label: "종료 시각", defaultValue: endDefault, wide: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            Text(timeInvalid
                 ? "종료 시각이 시작 시각보다 늦어야 해요."
                 : "시간을 정하면 그 시간에 고정돼요. 비워 두면 AI가 빈 시간에 놓아요.")
                .font(.pt(12))
                .foregroundStyle(timeInvalid ? Color.danger : Color.ink3)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var minutesField: some View {
        let current = f.estimatedMinutes ?? 30
        let options = Set(BrainDumpConst.editMinutes + [current]).sorted()
        return VStack(alignment: .leading, spacing: 4) {
            label("예상 소요 시간")
            BrainDumpMenuField(text: BrainDumpConst.minutesLabel(current), isPlaceholder: false) {
                ForEach(options, id: \.self) { m in
                    Button(BrainDumpConst.minutesLabel(m)) { f.estimatedMinutes = m }
                }
            }
            .accessibilityLabel("예상 소요 시간")
        }
    }

    private var priorityField: some View {
        VStack(alignment: .leading, spacing: 4) {
            label("중요도")
            HStack(spacing: 4) {
                ForEach(TaskPriority.allCases, id: \.self) { p in
                    Chip(p.rawValue, selected: (f.priority ?? .C) == p, size: .sm) { f.priority = p }
                        .accessibilityLabel("중요도 \(p.rawValue) \(BrainDumpConst.priorityHint(p))")
                }
            }
        }
    }

    private var appointmentField: some View {
        let on = f.kind == .APPOINTMENT
        return Button {
            f.kind = on ? .NORMAL : .APPOINTMENT
        } label: {
            HStack(spacing: 8) {
                NSymbol(on ? "checkmark.square.fill" : "square", size: 16)
                    .foregroundStyle(on ? Color.textBrand : Color.ink4)
                Text("다른 사람과 정한 약속이에요 (중요도가 올라가요)")
                    .font(.pt(14))
                    .foregroundStyle(Color.ink)
                    .multilineTextAlignment(.leading)
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    // MARK: 동작

    /// 종료 시각을 처음 채울 땐 시작 + 1시간
    private var endDefault: String {
        guard let s = f.startTime else { return "10:00" }
        return KST.minutesToTime(min(KST.timeToMinutes(s) + 60, 23 * 60 + 50))
    }

    private func selectGoal(_ value: String) {
        TA.track("task_add_item_edit.goal_select", ["goal_match": Self.goalMatch(value)])
        goalSel = value
        f.generalCategoryId = nil
        f.milestoneId = nil
        f.workTopicId = nil
    }

    /// 업무형 목표: 세부 목표 대신 주제 (끝내지 않은 주제 · 비우면 주제 없음) — 웹 WorkTopicField
    private func workTopicField(_ goalId: Int) -> some View {
        let choices = workTopics.filter { $0.topicId != nil && (!$0.completed || $0.topicId == f.workTopicId) }
        let current = choices.first { $0.topicId == f.workTopicId }
        let title = { (t: WorkTopic) in [t.emoji, t.name].compactMap { $0 }.joined(separator: " ") }
        return VStack(alignment: .leading, spacing: 4) {
            label("세부 목표")
            BrainDumpMenuField(text: current.map(title) ?? (workTopicsLoaded ? "선택 안 함 (기타 할 일)" : "불러오는 중…"), isPlaceholder: false) {
                Button("선택 안 함 (기타 할 일)") { f.workTopicId = nil }
                ForEach(choices, id: \.topicId) { t in
                    Button(title(t)) { f.workTopicId = t.topicId }
                }
            }
            .accessibilityLabel("세부 목표")
            if workTopicsLoaded && choices.isEmpty {
                Text("아직 세부 목표가 없어요. 목표 상세의 로드맵에서 세부 목표를 만들 수 있어요.")
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
            }
        }
        .task(id: goalId) {
            workTopicsLoaded = false
            workTopics = (try? await WorkAPI.board(goalId: goalId))?.topics ?? []
            workTopicsLoaded = true
        }
    }

    private func setDateType(_ t: DateType) {
        TA.track("task_add_item_edit.date_type_select", ["date_type": t.rawValue])
        let d = date
        f.dateType = t
        f.scheduledDate = t == .ON ? (d ?? today) : nil
        f.dueDate = t == .DUE ? (d ?? today) : nil
        if t != .ON {
            f.startTime = nil
            f.endTime = nil
        }
    }

    private func setDate(_ d: String) {
        if f.dateType == .DUE { f.dueDate = d } else { f.scheduledDate = d }
    }

    private func save() {
        guard valid else { return }
        TA.track("task_add_item_edit.save_click", [
            "changed_fields": changedFields.joined(separator: ","),
            "goal_match": Self.goalMatch(goalSel),
            "date_type": f.dateType.rawValue,
            "has_time": f.dateType == .ON && f.startTime != nil,
            "time_dropped": f.dateType == .ON && f.startTime == nil && f.endTime != nil,
        ])
        outcome = "saved"
        var base = f
        base.title = f.title.trimmingCharacters(in: .whitespacesAndNewlines)
        if goalSel == newTempValue {
            let emoji = newTempEmoji.isEmpty ? nil : newTempEmoji
            let temp = TempGoalInput(tempKey: nextTempKey,
                                     name: String(newTempName.trimmingCharacters(in: .whitespacesAndNewlines).prefix(30)),
                                     emoji: emoji, colorId: nil)
            base.goalCategoryId = nil
            base.generalCategoryId = nil
            base.milestoneId = nil
            base.workTopicId = nil
            base.tempGoalKey = temp.tempKey
            onSave(base, temp)
        } else if goalSel.hasPrefix("t:") {
            base.goalCategoryId = nil
            base.generalCategoryId = nil
            base.milestoneId = nil
            base.workTopicId = nil
            base.tempGoalKey = String(goalSel.dropFirst(2))
            onSave(base, nil)
        } else {
            base.goalCategoryId = Int(goalSel.dropFirst())
            base.tempGoalKey = nil
            onSave(base, nil)
        }
    }
}

// MARK: - 고르기 칸 (웹 <select>)

/// 입력칸 모양의 버튼 — 누르면 목록(Menu)이 떠요
struct BrainDumpMenuField<Content: View>: View {
    let text: String
    let isPlaceholder: Bool
    @ViewBuilder var content: () -> Content

    init(text: String, isPlaceholder: Bool, @ViewBuilder content: @escaping () -> Content) {
        self.text = text
        self.isPlaceholder = isPlaceholder
        self.content = content
    }

    var body: some View {
        Menu {
            content()
        } label: {
            HStack(spacing: 8) {
                Text(text)
                    .font(.pt(15))
                    .foregroundStyle(isPlaceholder ? Color.ink4 : Color.ink)
                    .lineLimit(1)
                Spacer(minLength: 0)
                Image(systemName: "chevron.up.chevron.down")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color.ink3)
            }
            .padding(.horizontal, 12)
            .frame(height: 44)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
            .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.borderSubtle, lineWidth: 1))
            .contentShape(Rectangle())
        }
    }
}

#Preview {
    let today = KST.today()
    return Color.canvas
        .sheet(isPresented: .constant(true)) {
            BrainDumpItemEditSheet(
                item: TaskAddSample.items(today: today)[0],
                goals: TaskAddSample.goals(),
                tempGoals: [TempGoalInput(tempKey: "t1", name: "이사 준비", emoji: "📦", colorId: nil)],
                today: today,
                nextTempKey: "t2",
                canAddTemp: true,
                onSave: { _, _ in }
            )
        }
}
