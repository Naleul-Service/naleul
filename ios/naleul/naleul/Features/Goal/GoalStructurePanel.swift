// Features/Goal/GoalStructurePanel.swift
//
// 웹 features/goal/ui/StructurePanel.tsx · MilestonesSection.tsx · SubGoalsSection.tsx
//
// 세부 구조 (세부 목표 · 마일스톤) — 기본은 접어 둬요.
// 왜: 매일 보는 건 "할 것(루틴·Task)"이고, 세부 목표는 라벨, 마일스톤은 자동으로 맞춰지니 꼭 바꿀 때만 열면 돼요.
// 접혀 있어도 다음 마일스톤은 크게 보여줘요.
// (웹은 이 안을 편집 중이면 접히지 않게 했어요 — 아이폰은 편집을 시트로 해서 그럴 필요가 없어요)

import SwiftUI

// MARK: - 세부 구조 패널

@MainActor
struct GoalStructurePanel: View {
    let goal: GoalCategory
    @State private var open = false
    @Environment(GoalDetailViewModel.self) private var vm

    var body: some View {
        let today = KST.today()
        let milestones = GoalDetailLogic.sortedMilestones(goal)
        let next = milestones.first { $0.status != .ACHIEVED && $0.dueDate >= today }
        let achieved = milestones.filter { $0.status == .ACHIEVED }.count
        let areas = GoalDetailLogic.areas(goal)

        VStack(spacing: 0) {
            if let next {
                nextBlock(next, today: today)
            }
            Button {
                vm.track("goal_detail.structure_toggle", [
                    "expanded": !open, "plan_milestone_count": milestones.count, "plan_subgoal_count": areas.count,
                ])
                withAnimation(.easeOut(duration: 0.2)) { open.toggle() }
            } label: {
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("세부 구조").font(.pt(15, .bold)).foregroundStyle(Color.ink)
                        Text("세부 목표 \(areas.count)개 · 마일스톤 \(milestones.isEmpty ? "없음" : "\(achieved)/\(milestones.count) 달성")")
                            .font(.pt(13))
                            .foregroundStyle(Color.ink3)
                    }
                    Spacer(minLength: 0)
                    Text(open ? "접기" : "펼쳐서 수정").font(.pt(13)).foregroundStyle(Color.ink3)
                    NSymbol("chevron.down", size: 14, weight: .semibold)
                        .foregroundStyle(Color.ink3)
                        .rotationEffect(.degrees(open ? 180 : 0))
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(open ? .isSelected : [])

            if open {
                VStack(spacing: 16) {
                    GoalMilestonesSection(goal: goal)
                    GoalSubGoalsSection(goal: goal)
                }
                .padding(12)
                .overlay(alignment: .top) { Rectangle().fill(Color.borderSoft).frame(height: 1) }
            }
        }
        .background(Color.surface)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card))
        .overlay(RoundedRectangle(cornerRadius: Radius.card).stroke(Color.line, lineWidth: 1))
    }

    /// 다음 마일스톤 — 접혀 있어도 한눈에 보이게 크게
    private func nextBlock(_ next: MilestoneInfo, today: String) -> some View {
        let dLeft = max(KST.diffDays(today, next.dueDate), 0)
        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("다음 마일스톤").font(.pt(13, .semibold)).foregroundStyle(Color.textBrand)
                Spacer(minLength: 0)
                Text(dLeft == 0 ? "D-DAY" : "D-\(dLeft)")
                    .font(.ptMono(13, .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 2)
                    .background(Color.brand, in: Capsule())
            }
            Text(next.title).font(.pt(16, .semibold)).foregroundStyle(Color.ink)
            HStack(spacing: 8) {
                Text("\(KST.monthOf(next.dueDate))월 \(KST.dayOfMonth(next.dueDate))일까지")
                    .foregroundStyle(Color.ink2)
                if let t = next.targetValue {
                    Text("목표 \(GoalDetailLogic.trim(t, 6))\(goal.metricUnit ?? "")")
                        .fontWeight(.bold)
                        .foregroundStyle(Color.textBrand)
                }
            }
            .font(.pt(14))
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.brandSoft)
    }
}

// MARK: - 마일스톤 (동그라미로 달성 체크, 글자를 누르면 수정)

@MainActor
struct GoalMilestonesSection: View {
    let goal: GoalCategory
    @Environment(GoalDetailViewModel.self) private var vm
    @State private var pendingId: Int? = nil

    /// 수치 목표가 있는 목표인지 (점검 시점 수치를 자동 계산할 수 있는지)
    private var hasMetric: Bool { goal.startValue != nil && goal.targetValue != nil }

    var body: some View {
        let milestones = GoalDetailLogic.sortedMilestones(goal)
        let today = KST.today()
        let achieved = milestones.filter { $0.status == .ACHIEVED }.count
        // 지금 향하고 있는 마일스톤: 아직 달성 안 했고 기한이 남은 것 중 가장 가까운 것
        let currentId = milestones.first { $0.status != .ACHIEVED && $0.dueDate >= today }?.milestoneId

        SectionCard("마일스톤", aside: { Text(milestones.isEmpty ? "" : "\(achieved) / \(milestones.count) 달성") }) {
            VStack(alignment: .leading, spacing: 16) {
                Text("목표까지 가는 길의 중간 점검 지점이에요. 그날까지 잘 가고 있는지 확인하고, 동그라미를 눌러 달성을 체크해요."
                     + (hasMetric ? " 수치는 처음 값 → 목표 값 사이에서 날짜에 맞춰 자동으로 정해져요." : ""))
                    .font(.pt(13))
                    .foregroundStyle(Color.ink3)

                if !milestones.isEmpty {
                    VStack(alignment: .leading, spacing: 20) {
                        ForEach(Array(milestones.enumerated()), id: \.element.milestoneId) { i, m in
                            milestoneRow(m, current: m.milestoneId == currentId, isLast: i == milestones.count - 1)
                        }
                    }
                    .background(alignment: .leading) {
                        // 세로 줄
                        Rectangle().fill(Color.lineStrong).frame(width: 2).padding(.vertical, 8).padding(.leading, 9)
                    }
                }
                GoalAddButton("마일스톤 추가") {
                    vm.track("goal_detail.structure_add_click", ["item_kind": "milestone"])
                    vm.sheet = .milestone(nil, isLast: false)
                }
            }
        }
    }

    private func milestoneRow(_ m: MilestoneInfo, current: Bool, isLast: Bool) -> some View {
        let done = m.status == .ACHIEVED
        let unit = goal.metricUnit ?? ""
        let ringColor: Color = done ? .actionPrimary : (current ? .brand : .lineStrong)
        return HStack(alignment: .top, spacing: 8) {
            // 동그라미를 누르면 달성 / 달성 취소
            Button { toggle(m) } label: {
                ZStack {
                    Circle().fill(done ? Color.actionPrimary : Color.bgSurface)
                    Circle().stroke(ringColor, lineWidth: 3)
                    if done {
                        NSymbol("checkmark", size: 9, weight: .black).foregroundStyle(.white)
                    }
                }
                .frame(width: 20, height: 20)
                .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .disabled(pendingId == m.milestoneId)
            .accessibilityLabel(done ? "\(m.title) 달성 취소" : "\(m.title) 달성")
            .padding(.top, 2)

            Button {
                vm.track("goal_detail.structure_edit_open", ["item_kind": "milestone", "entry_point": "row"])
                vm.sheet = .milestone(m, isLast: isLast)
            } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text("~\(GoalFormat.formatDot(m.dueDate))" + (m.status == .MISSED ? " · 기한 지남" : ""))
                        .font(.pt(12))
                        .foregroundStyle(m.status == .MISSED ? Color.danger : Color.ink3)
                    FlowLayout(spacing: 6) {
                        Text(m.title).font(.pt(15, .bold)).foregroundStyle(Color.ink)
                        if let t = m.targetValue {
                            Badge("\(GoalDetailLogic.trim(t, 6))\(unit)", tone: done ? .success : .brand)
                        }
                        if m.targetValueManual == true {
                            Text("직접 정함").font(.pt(11, .medium)).foregroundStyle(Color.ink3)
                        }
                    }
                    if let d = m.description, !d.isEmpty {
                        Text(d).font(.pt(13)).foregroundStyle(Color.ink3)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .multilineTextAlignment(.leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            GoalRowActions(label: m.title, onEdit: {
                vm.track("goal_detail.structure_edit_open", ["item_kind": "milestone", "entry_point": "icon"])
                vm.sheet = .milestone(m, isLast: isLast)
            },
                           onDelete: { vm.ask(.deleteMilestone(m)) })
        }
    }

    private func toggle(_ m: MilestoneInfo) {
        let done = m.status == .ACHIEVED
        let next = done ? "PENDING" : "ACHIEVED"
        let id = m.milestoneId
        vm.track("goal_detail.milestone_check_toggle", [
            "check_action": done ? "uncomplete" : "complete", "is_overdue": m.dueDate < KST.today(),
        ])
        pendingId = id
        Task { @MainActor in
            let ok = await Mutation.run(invalidate: [.goals, .timetable],
                               successMessage: done ? "달성을 취소했어요." : "마일스톤을 달성했어요. 🎉") {
                try await GoalEditAPI.updateMilestone(id: id, ["status": .string(next)])
            }
            vm.track("goal_detail.structure_result", GD.result(ok != nil).merging([
                "item_kind": "milestone", "structure_action": done ? "uncheck" : "check",
            ]) { $1 })
            pendingId = nil
        }
    }
}

// MARK: - 마일스톤 추가 · 수정 시트 (웹 MilestoneForm)

/// 수치는 기본으로 "처음 값 → 목표 값" 직선에서 날짜에 맞춰 서버가 계산해요.
/// 꼭 필요할 때만 "이 시점만 직접 정하기"를 펼쳐서 값을 넣고, 언제든 자동으로 되돌릴 수 있어요.
@MainActor
struct GoalMilestoneFormSheet: View {
    let goal: GoalCategory
    let milestone: MilestoneInfo?
    /// 수치 목표의 마지막 시점은 항상 목표 종료일 · 목표 값 (자동)
    let isLast: Bool

    @Environment(GoalDetailViewModel.self) private var vm

    @State private var title: String
    @State private var dueDate: String
    @State private var description: String
    /// 직접 정하기: 이미 직접 정한 값이면 펼친 채로
    @State private var manualOpen: Bool
    @State private var target: String
    @State private var revertToAuto = false
    @State private var saving = false
    @State private var outcome: String?

    init(goal: GoalCategory, milestone: MilestoneInfo?, isLast: Bool) {
        self.goal = goal
        self.milestone = milestone
        self.isLast = isLast
        // 새 점검 시점 기본 날짜: 마지막 시점 + 4주 (없으면 오늘 + 4주), 목표 종료일을 넘지 않게
        let last = (goal.milestones ?? []).sorted { $0.dueDate > $1.dueDate }.first
        var suggested = KST.addDays(last?.dueDate ?? KST.today(), 28)
        if let end = goal.goalCategoryEndDate, suggested > end { suggested = end }
        _title = State(initialValue: milestone?.title ?? "")
        _dueDate = State(initialValue: milestone?.dueDate ?? suggested)
        _description = State(initialValue: milestone?.description ?? "")
        let manual = milestone?.targetValueManual == true
        _manualOpen = State(initialValue: manual)
        _target = State(initialValue: manual ? (milestone?.targetValue.map { GoalDetailLogic.trim($0, 6) } ?? "") : "")
    }

    private var unit: String { goal.metricUnit ?? "" }
    private var metric: Bool { goal.startValue != nil && goal.targetValue != nil }
    private var lockedLast: Bool { metric && isLast && goal.goalCategoryEndDate != nil }
    private var targetNum: Double? { manualOpen ? GoalDetailLogic.toNum(target) : nil }
    private var trimmedTitle: String { title.trimmingCharacters(in: .whitespacesAndNewlines) }

    private var error: String? {
        if trimmedTitle.isEmpty { return nil }
        if dueDate.isEmpty { return "날짜를 입력해 주세요." }
        if let n = targetNum, n.isNaN { return "수치는 숫자로 입력해 주세요." }
        return nil
    }

    var body: some View {
        SheetScaffold(title: milestone == nil ? "마일스톤 추가" : "마일스톤 수정", dismissible: !saving) {
            GoalInlineForm(submitLabel: milestone == nil ? "추가" : "저장", saving: saving,
                           valid: !trimmedTitle.isEmpty && error == nil, error: error,
                           onCancel: {
                               outcome = "cancel_button"
                               vm.sheet = nil
                           }, onSubmit: submit) {
                fields
            }
            .goalSheetForm()
        }
        .onDisappear {
            if let m = GD.closeMethod(outcome) {
                vm.track("goal_detail.structure_edit_cancel", ["item_kind": "milestone", "is_new": milestone == nil, "close_method": m])
            }
        }
    }

    private func trackResult(_ ok: Bool) {
        vm.track("goal_detail.structure_result", GD.result(ok).merging([
            "item_kind": "milestone", "structure_action": milestone == nil ? "create" : "update",
        ]) { $1 })
        if ok { outcome = "saved" }
    }

    @ViewBuilder private var fields: some View {
        GoalFormField("이름") {
            TextField("예: 1개월 차 점검", text: $title)
                .inputStyle()
                .goalMaxLength($title, 40)
        }
        GoalFormField(lockedLast ? "날짜 (목표 종료일)" : "날짜") {
            if lockedLast {
                Text(GoalFormat.formatDot(goal.goalCategoryEndDate))
                    .font(.pt(15))
                    .foregroundStyle(Color.ink3)
                    .padding(.horizontal, 14)
                    .frame(maxWidth: .infinity, minHeight: 40, alignment: .leading)
                    .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xl))
            } else {
                GoalDateInput(value: $dueDate)
            }
        }

        // 수치: 기본은 자동, 필요할 때만 직접
        if metric {
            metricBox
                .font(.pt(13))
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.subtle.opacity(0.6), in: RoundedRectangle(cornerRadius: Radius.xl))
        }

        GoalFormField("설명 (선택)") {
            TextField("", text: $description)
                .inputStyle()
                .goalMaxLength($description, 300)
        }
    }

    @ViewBuilder private var metricBox: some View {
        if lockedLast {
            (Text("마지막 마일스톤은 항상 ")
                + Text("목표 값 \(GoalDetailLogic.trim(goal.targetValue ?? 0, 6))\(unit)").bold()
                + Text("이에요. 바꾸려면 목표 수정에서 목표 값을 고쳐 주세요."))
                .foregroundStyle(Color.ink2)
        } else if manualOpen {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .bottom, spacing: 8) {
                    GoalFormField("이 시점 목표 (\(unit.isEmpty ? "값" : unit))") {
                        TextField(milestone?.targetValue.map { GoalDetailLogic.trim($0, 6) } ?? "예: 76", text: $target)
                            .keyboardType(.decimalPad)
                            .inputStyle()
                    }
                    .frame(width: 150)
                    Button("자동으로 되돌리기") {
                        manualOpen = false
                        target = ""
                        revertToAuto = milestone?.targetValueManual == true
                    }
                    .font(.pt(13, .semibold))
                    .foregroundStyle(Color.ink2)
                    .underline()
                    .buttonStyle(.plain)
                    .frame(height: 44)
                }
                Text("직접 정한 값은 자동 계산에서 빠져요. 목표의 처음 값·목표 값·종료일을 바꾸면 다시 자동으로 돌아가요. 처음 값과 목표 값 사이를 벗어난 값은 자동으로 바뀌어요.")
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
            }
        } else {
            VStack(alignment: .leading, spacing: 4) {
                (Text("이 시점 수치는 날짜에 맞춰 ") + Text("자동으로 계산").bold() + Text("돼요" + autoNow + "."))
                    .foregroundStyle(Color.ink2)
                Button("이 시점만 직접 정하기") { manualOpen = true }
                    .font(.pt(12, .semibold))
                    .foregroundStyle(Color.ink3)
                    .underline()
                    .buttonStyle(.plain)
            }
        }
    }

    private var autoNow: String {
        guard let v = milestone?.targetValue, !revertToAuto else { return "" }
        return " (지금 \(GoalDetailLogic.trim(v, 6))\(unit))"
    }

    private func submit() {
        guard !trimmedTitle.isEmpty, error == nil, !saving else { return }
        let t = trimmedTitle
        let desc = description.trimmingCharacters(in: .whitespacesAndNewlines)
        let goalId = goal.goalCategoryId
        let due = dueDate
        let tn = targetNum
        vm.track("goal_detail.structure_save_click", [
            "item_kind": "milestone", "is_new": milestone == nil, "is_target_manual": manualOpen,
        ])
        saving = true
        if let milestone {
            var o: [String: JSONValue] = ["title": .string(t), "dueDate": .string(due), "description": .string(desc)]
            if manualOpen, let tn { o["targetValue"] = .double(tn) }
            let autoTarget = revertToAuto || (milestone.targetValueManual == true && !manualOpen)
            if autoTarget { o["autoTargetValue"] = .bool(true) }
            let body = JSONValue.object(o)
            let id = milestone.milestoneId
            Task { @MainActor in
                let ok = await Mutation.run(invalidate: [.goals, .timetable],
                                            successMessage: autoTarget ? "자동 계산으로 되돌렸어요." : "마일스톤을 수정했어요.") {
                    try await GoalEditAPI.updateMilestone(id: id, body)
                }
                saving = false
                trackResult(ok != nil)
                if ok != nil { vm.sheet = nil }
            }
        } else {
            Task { @MainActor in
                let ok = await Mutation.run(invalidate: [.goals, .timetable], successMessage: "마일스톤을 추가했어요.") {
                    try await GoalEditAPI.createMilestone(goalId: goalId, title: t, description: desc, dueDate: due,
                                                          targetValue: manualOpen ? tn : nil)
                }
                saving = false
                trackResult(ok != nil)
                if ok != nil { vm.sheet = nil }
            }
        }
    }
}

// MARK: - 세부 목표 (영역)

/// 루틴·할 일을 묶어 보는 라벨. 없어도 돼요. 안 고른 루틴·할 일은 "기타 할 일" 그릇에 들어가고, 그 그릇은 여기 보여주지 않아요.
@MainActor
struct GoalSubGoalsSection: View {
    let goal: GoalCategory
    @Environment(GoalDetailViewModel.self) private var vm

    var body: some View {
        let subs = GoalDetailLogic.areas(goal)
        SectionCard("세부 목표", aside: { Text("\(subs.count)개") }) {
            VStack(alignment: .leading, spacing: 12) {
                Text("목표를 이루려고 나눈 갈래예요 (예: 필기 대비 · 실기 대비). 루틴·Task에 붙는 이름표 역할이라, 없어도 괜찮아요.")
                    .font(.pt(13))
                    .foregroundStyle(Color.ink3)
                // 세로로 쌓아요 — 좁은 칸에서 가로로 나누면 글자가 끊겨 읽기 어려워요
                ForEach(subs) { sg in
                    subCard(sg)
                }
                GoalAddButton("세부 목표 추가") {
                    vm.track("goal_detail.structure_add_click", ["item_kind": "sub_goal"])
                    vm.sheet = .subGoal(nil)
                }
            }
        }
    }

    private func subCard(_ sub: SubGoalInfo) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                Capsule().fill(Color.goal(sub.colorCode ?? goal.colorCode)).frame(width: 32, height: 4).padding(.top, 6)
                Spacer(minLength: 0)
                GoalRowActions(label: sub.generalCategoryName, onEdit: {
                    vm.track("goal_detail.structure_edit_open", ["item_kind": "sub_goal", "entry_point": "icon"])
                    vm.sheet = .subGoal(sub)
                },
                               onDelete: { vm.ask(.deleteSubGoal(sub)) })
            }
            Button {
                vm.track("goal_detail.structure_edit_open", ["item_kind": "sub_goal", "entry_point": "row"])
                vm.sheet = .subGoal(sub)
            } label: {
                VStack(alignment: .leading, spacing: 4) {
                    Text((sub.emoji.map { $0 + " " } ?? "") + sub.generalCategoryName)
                        .font(.pt(15, .bold))
                        .foregroundStyle(Color.ink)
                    // AI 가 정한 실천 원칙 (예: 식단 — 하루 1,600kcal, 단백질 90g …)
                    if let d = sub.description, !d.isEmpty {
                        Text(d).font(.pt(13)).foregroundStyle(Color.ink2).lineSpacing(3)
                    }
                    subMeta(sub)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .multilineTextAlignment(.leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .background(Color.bgSunken, in: RoundedRectangle(cornerRadius: Radius.xxl))
    }

    private func subMeta(_ sub: SubGoalInfo) -> Text {
        var t = Text("루틴 \(sub.routines.count)개").foregroundColor(Color.ink3)
        if let s = sub.generalCategoryStartDate {
            let from = String(GoalFormat.formatDot(s).dropFirst(5))
            let to = String(GoalFormat.formatDot(sub.generalCategoryEndDate).dropFirst(5))
            t = t + Text(" · \(from)–\(to)").foregroundColor(Color.ink4)
        }
        return t.font(.pt(13))
    }
}

// MARK: - 세부 목표 추가 · 수정 시트 (웹 SubGoalForm)

@MainActor
struct GoalSubGoalFormSheet: View {
    let goal: GoalCategory
    let sub: SubGoalInfo?
    let colors: [UserColor]

    @Environment(GoalDetailViewModel.self) private var vm

    @State private var name: String
    @State private var start: String
    @State private var end: String
    @State private var colorId: Int? = nil
    @State private var saving = false
    @State private var outcome: String?

    init(goal: GoalCategory, sub: SubGoalInfo?, colors: [UserColor]) {
        self.goal = goal
        self.sub = sub
        self.colors = colors
        let period = GoalDetailLogic.defaultPeriod(goal)
        _name = State(initialValue: sub?.generalCategoryName ?? "")
        _start = State(initialValue: sub?.generalCategoryStartDate ?? period.start)
        _end = State(initialValue: sub?.generalCategoryEndDate ?? period.end)
    }

    private var effectiveColor: Int? { colorId ?? GoalDetailLogic.colorIdOf(colors, sub?.colorCode ?? goal.colorCode) }
    private var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }

    private var error: String? {
        if trimmedName.isEmpty { return nil }
        if start.isEmpty || end.isEmpty { return "기간을 입력해 주세요." }
        if start > end { return "종료일이 시작일보다 빨라요." }
        return nil
    }

    var body: some View {
        SheetScaffold(title: sub == nil ? "세부 목표 추가" : "세부 목표 수정", dismissible: !saving) {
            GoalInlineForm(submitLabel: sub == nil ? "추가" : "저장", saving: saving,
                           valid: !trimmedName.isEmpty && error == nil && effectiveColor != nil, error: error,
                           onCancel: {
                               outcome = "cancel_button"
                               vm.sheet = nil
                           }, onSubmit: submit) {
                GoalFormField("세부 목표 이름") {
                    TextField("예: 식단 관리, 단어 외우기", text: $name)
                        .inputStyle()
                        .goalMaxLength($name, 30)
                }
                HStack(alignment: .top, spacing: 8) {
                    GoalFormField("시작일") { GoalDateInput(value: $start) }
                    GoalFormField("종료일") { GoalDateInput(value: $end) }
                }
                GoalFormField("색상") {
                    ColorPickerRow(colors: colors, selectedId: Binding(get: { effectiveColor }, set: { colorId = $0 }))
                }
            }
            .goalSheetForm()
        }
        .onDisappear {
            if let m = GD.closeMethod(outcome) {
                vm.track("goal_detail.structure_edit_cancel", ["item_kind": "sub_goal", "is_new": sub == nil, "close_method": m])
            }
        }
    }

    private func submit() {
        guard !trimmedName.isEmpty, error == nil, !saving, let color = effectiveColor else { return }
        let n = trimmedName
        let s = start
        let e = end
        let goalId = goal.goalCategoryId
        let picked = colorId
        vm.track("goal_detail.structure_save_click", ["item_kind": "sub_goal", "is_new": sub == nil])
        saving = true
        Task { @MainActor in
            let ok: Void?
            if let sub {
                ok = await Mutation.run(invalidate: [.goals, .timetable], successMessage: "세부 목표를 수정했어요.") {
                    try await GoalEditAPI.updateSubGoal(id: sub.generalCategoryId, name: n, start: s, end: e, colorId: picked)
                }
            } else {
                ok = await Mutation.run(invalidate: [.goals, .timetable], successMessage: "세부 목표를 추가했어요.") {
                    try await GoalEditAPI.createSubGoal(goalId: goalId, name: n, start: s, end: e, colorId: color)
                }
            }
            saving = false
            vm.track("goal_detail.structure_result", GD.result(ok != nil).merging([
                "item_kind": "sub_goal", "structure_action": sub == nil ? "create" : "update",
            ]) { $1 })
            if ok != nil {
                outcome = "saved"
                vm.sheet = nil
            }
        }
    }
}

#Preview("세부 구조") {
    ScrollView {
        GoalStructurePanel(goal: GoalPreviewData.goal).padding(16)
    }
    .canvasBackground()
    .environment(GoalDetailViewModel.preview())
}

#Preview("마일스톤 수정") {
    let goal = GoalPreviewData.goal
    return Color.canvas
        .sheet(isPresented: .constant(true)) {
            GoalMilestoneFormSheet(goal: goal, milestone: goal.milestones?[1], isLast: false)
                .environment(GoalDetailViewModel.preview())
        }
}
