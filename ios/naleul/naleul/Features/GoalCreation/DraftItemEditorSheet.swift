// Features/GoalCreation/DraftItemEditorSheet.swift
//
// 초안 항목 고치기 (목표 · 세부 목표 · 마일스톤 · 루틴 / 할 일).
// 웹 원본: features/goal-creation/ui/draft/ItemEditModal.tsx (그 자리 편집기 → 아이폰은 시트)
//
// 삭제는 같은 시트 아래에서 한 번 더 확인해요 (웹과 같아요).

import SwiftUI

/// 지금 편집 중인 대상. index 가 nil 이면 새로 추가
enum DraftEditTarget: Hashable, Identifiable {
    case goal
    case subGoal(Int?)
    case milestone(Int?)
    case task(Int?, DraftTaskType)

    var id: String {
        switch self {
        case .goal: return "goal"
        case .subGoal(let i): return "subGoal-\(i.map(String.init) ?? "new")"
        case .milestone(let i): return "milestone-\(i.map(String.init) ?? "new")"
        case let .task(i, type): return "task-\(type.rawValue)-\(i.map(String.init) ?? "new")"
        }
    }

    var index: Int? {
        switch self {
        case .goal: return nil
        case .subGoal(let i), .milestone(let i): return i
        case .task(let i, _): return i
        }
    }

    var isNew: Bool {
        if case .goal = self { return false }
        return index == nil
    }

    /// "루틴 추가", "목표 수정" …
    var title: String {
        let name: String
        switch self {
        case .goal: name = "목표"
        case .subGoal: name = "세부 목표"
        case .milestone: name = "마일스톤"
        case .task(_, let type): name = type == .ROUTINE ? "루틴" : "할 일"
        }
        return "\(name) \(isNew ? "추가" : "수정")"
    }
}

enum DraftEditResult {
    /// metricValues: 수치 목표일 때만 (시작값, 목표값)
    case goal(title: String, emoji: String?, startDate: String, endDate: String, metricValues: (start: Double, target: Double)?)
    case subGoal(Int?, DraftPlanSubGoal)
    case milestone(Int?, DraftPlanMilestone)
    case task(Int?, DraftPlanTask)
}

struct DraftItemEditorSheet: View {
    let target: DraftEditTarget
    let plan: DraftGoalPlan
    let onSave: (DraftEditResult) -> Void
    let onDelete: () -> Void
    /// 최소 개수 등으로 지울 수 없을 때 이유
    let deleteDisabledReason: String?

    var body: some View {
        switch target {
        case .goal:
            DraftGoalForm(plan: plan, onSave: onSave)
        case .subGoal(let i):
            DraftSubGoalForm(index: i, plan: plan, onSave: onSave, onDelete: onDelete, deleteBlock: deleteDisabledReason)
        case .milestone(let i):
            DraftMilestoneForm(index: i, plan: plan, onSave: onSave, onDelete: onDelete, deleteBlock: deleteDisabledReason)
        case let .task(i, type):
            DraftTaskForm(index: i, type: type, plan: plan, onSave: onSave, onDelete: onDelete, deleteBlock: deleteDisabledReason)
        }
    }
}

// MARK: - 공통 부품

/// 이모지 + 제목 한 줄
private struct DraftEmojiTitleRow: View {
    @Binding var emoji: String
    @Binding var title: String
    var placeholder = ""

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            GoalCreationField("이모지") {
                TextField("🙂", text: Binding(get: { emoji }, set: { emoji = String($0.prefix(4)) }))
                    .multilineTextAlignment(.center)
                    .inputStyle()
            }
            .frame(width: 64)
            GoalCreationField("제목") {
                TextField(placeholder, text: Binding(get: { title },
                                                     set: { title = String($0.prefix(DraftPlanUtils.Limits.title)) }))
                    .inputStyle()
            }
        }
    }
}

/// 시트 아래 버튼: [삭제] … [취소] [저장] · 삭제 확인 줄
private struct DraftEditorFooter: View {
    /// 이벤트의 item_kind
    let itemKind: String
    let canDelete: Bool
    let deleteBlock: String?
    @Binding var confirmDelete: Bool
    @Binding var error: String?
    let onDelete: () -> Void
    let onSave: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        if confirmDelete {
            HStack(spacing: 8) {
                Text("이 항목을 초안에서 뺄까요?").font(.pt(14, .semibold)).foregroundStyle(Color.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                NButton("취소", variant: .secondary) { confirmDelete = false }
                NButton("삭제", variant: .danger) {
                    GCTracker.shared.track(GCEvent.itemDeleteConfirm, ["item_kind": itemKind])
                    onDelete()
                }
            }
            // 시트 버튼 줄 안이라 따로 테두리 · 바탕 없이 (DESIGN_SPEC 10.2 · 13.2)
        } else {
            HStack(spacing: 8) {
                if canDelete {
                    Button {
                        GCTracker.shared.track(GCEvent.itemDeleteClick, ["item_kind": itemKind])
                        if let deleteBlock {
                            // [UX] 최소 개수라 지울 수 없어요
                            GCTracker.shared.track(GCEvent.itemDeleteBlocked, ["item_kind": itemKind, "validation_error": deleteBlock])
                            error = deleteBlock
                        } else {
                            error = nil
                            confirmDelete = true
                        }
                    } label: {
                        HStack(spacing: 6) {
                            NSymbol("trash", size: 14, weight: .semibold)
                            Text("삭제")
                        }
                        .foregroundStyle(Color.danger)
                    }
                    .buttonStyle(.n(.ghost, .lg))
                }
                // 왼쪽 삭제(Ghost · 빨간 글자) · 오른쪽 취소 + 저장 LG (DESIGN_SPEC 13.2)
                Spacer(minLength: 0)
                NButton("취소", variant: .secondary, size: .lg) {
                    GCTracker.shared.sheetClose = "cancel_button"
                    dismiss()
                }
                NButton("저장", size: .lg) { onSave() }
            }
        }
    }
}

/// 폼 공통 틀: 제목 · 내용 · 에러 · 아래 버튼
private struct DraftEditorScaffold<Content: View>: View {
    let target: DraftEditTarget
    let canDelete: Bool
    let deleteBlock: String?
    @Binding var error: String?
    let onDelete: () -> Void
    let onSave: () -> Void
    @ViewBuilder var content: () -> Content
    @State private var confirmDelete = false

    init(target: DraftEditTarget, canDelete: Bool, deleteBlock: String?, error: Binding<String?>,
         onDelete: @escaping () -> Void, onSave: @escaping () -> Void, @ViewBuilder content: @escaping () -> Content) {
        self.target = target
        self.canDelete = canDelete
        self.deleteBlock = deleteBlock
        self._error = error
        self.onDelete = onDelete
        self.onSave = onSave
        self.content = content
    }

    var body: some View {
        SheetScaffold(title: target.title, detents: [.large]) {
            VStack(alignment: .leading, spacing: 16) {
                content()
                if let error {
                    Text(error).font(.pt(14, .medium)).foregroundStyle(Color.danger)
                }
            }
        } footer: {
            DraftEditorFooter(itemKind: target.analyticsKind, canDelete: canDelete, deleteBlock: deleteBlock,
                              confirmDelete: $confirmDelete, error: $error, onDelete: onDelete, onSave: onSave)
                // 왼쪽 삭제 · 오른쪽 취소 + 저장 배치라 꽉 차게 늘리지 않아요 (크기는 LG)
                .environment(\.nSheetFooter, false)
        }
        // [UX] 시트 안 검사 오류 (최소 개수 삭제 막힘은 위에서 따로 보내요)
        .onChange(of: error) { _, e in
            guard let e, e != deleteBlock else { return }
            GCTracker.shared.track(GCEvent.itemEditError, ["item_kind": target.analyticsKind, "validation_error": e])
        }
    }
}

// MARK: - 목표
// 목표 이름 + "그래프를 정하는 값" (시작일 · 종료일 · 시작값 · 목표값).
// 마일스톤 날짜·수치는 이 값에서 자동으로 계산돼서, 마일스톤을 하나하나 맞출 필요가 없어요.

private struct DraftGoalForm: View {
    let plan: DraftGoalPlan
    let onSave: (DraftEditResult) -> Void

    @State private var title: String
    @State private var emoji: String
    @State private var startDate: String
    @State private var endDate: String
    @State private var startValue: String
    @State private var targetValue: String
    @State private var error: String?

    init(plan: DraftGoalPlan, onSave: @escaping (DraftEditResult) -> Void) {
        self.plan = plan
        self.onSave = onSave
        let g = plan.goal
        _title = State(initialValue: g.title)
        _emoji = State(initialValue: g.emoji ?? "")
        _startDate = State(initialValue: g.startDate)
        _endDate = State(initialValue: g.endDate)
        _startValue = State(initialValue: g.metric.map { DraftPlanUtils.num($0.startValue) } ?? "")
        _targetValue = State(initialValue: g.metric.map { DraftPlanUtils.num($0.targetValue) } ?? "")
    }

    var body: some View {
        let metric = plan.goal.metric
        let today = KST.today()
        DraftEditorScaffold(target: .goal, canDelete: false, deleteBlock: nil, error: $error,
                            onDelete: {}, onSave: { submit() }) {
            DraftEmojiTitleRow(emoji: $emoji, title: $title)
            HStack(alignment: .top, spacing: 12) {
                GoalCreationField("시작일") { GoalCreationDateField(ymd: $startDate, min: today) }
                GoalCreationField("종료일") { GoalCreationDateField(ymd: $endDate, min: startDate.isEmpty ? today : startDate) }
            }
            if let metric {
                HStack(alignment: .top, spacing: 12) {
                    GoalCreationField("지금 \(metric.name) (\(metric.unit ?? ""))") {
                        TextField("", text: $startValue).keyboardType(.decimalPad).inputStyle()
                    }
                    GoalCreationField("목표 \(metric.name) (\(metric.unit ?? ""))") {
                        TextField("", text: $targetValue).keyboardType(.decimalPad).inputStyle()
                    }
                }
            }
            Text("마일스톤 날짜\(metric != nil ? "와 단계별 수치" : "")는 이 값에 맞춰 자동으로 다시 계산돼요.")
                .font(.pt(12)).foregroundStyle(Color.ink3)
        }
    }

    private func submit() {
        let t = GoalCreationForm.trim(title)
        let today = KST.today()
        if t.isEmpty { error = "목표 이름을 입력해 주세요."; return }
        if startDate.isEmpty || startDate < today { error = "시작일은 오늘 이후여야 해요."; return }
        if endDate.isEmpty || endDate <= startDate { error = "종료일은 시작일보다 뒤여야 해요."; return }
        var values: (start: Double, target: Double)? = nil
        if plan.goal.metric != nil {
            guard let sv = GoalCreationForm.toNumber(startValue), let tv = GoalCreationForm.toNumber(targetValue),
                  !sv.isNaN, !tv.isNaN else {
                error = "시작값과 목표값을 숫자로 입력해 주세요."
                return
            }
            if sv == tv { error = "목표값이 시작값과 같아요."; return }
            values = (start: sv, target: tv)
        }
        let e = GoalCreationForm.trim(emoji)
        error = nil
        onSave(.goal(title: t, emoji: e.isEmpty ? nil : e, startDate: startDate, endDate: endDate, metricValues: values))
    }
}

// MARK: - 세부 목표

private struct DraftSubGoalForm: View {
    let index: Int?
    let plan: DraftGoalPlan
    let onSave: (DraftEditResult) -> Void
    let onDelete: () -> Void
    let deleteBlock: String?

    @State private var title: String
    @State private var emoji: String
    @State private var description: String
    @State private var error: String?

    init(index: Int?, plan: DraftGoalPlan, onSave: @escaping (DraftEditResult) -> Void,
         onDelete: @escaping () -> Void, deleteBlock: String?) {
        self.index = index
        self.plan = plan
        self.onSave = onSave
        self.onDelete = onDelete
        self.deleteBlock = deleteBlock
        let current = index.flatMap { plan.subGoals.indices.contains($0) ? plan.subGoals[$0] : nil }
        _title = State(initialValue: current?.title ?? "")
        _emoji = State(initialValue: current?.emoji ?? "")
        _description = State(initialValue: current?.description ?? "")
    }

    private var current: DraftPlanSubGoal? {
        index.flatMap { plan.subGoals.indices.contains($0) ? plan.subGoals[$0] : nil }
    }

    var body: some View {
        DraftEditorScaffold(target: .subGoal(index), canDelete: index != nil, deleteBlock: deleteBlock, error: $error,
                            onDelete: onDelete, onSave: { submit() }) {
            DraftEmojiTitleRow(emoji: $emoji, title: $title, placeholder: "예) 식단 관리")
            GoalCreationField("설명 (선택)") {
                TextField("예) 하루 섭취량 1,800kcal 이하 유지",
                          text: Binding(get: { description }, set: { description = String($0.prefix(200)) }),
                          axis: .vertical)
                    .lineLimit(3...6)
                    .inputStyle(minHeight: 88)
            }
        }
    }

    private func submit() {
        let t = GoalCreationForm.trim(title)
        if t.isEmpty { error = "제목을 입력해 주세요."; return }
        let e = GoalCreationForm.trim(emoji)
        let d = GoalCreationForm.trim(description)
        error = nil
        onSave(.subGoal(index, DraftPlanSubGoal(
            tempId: current?.tempId ?? DraftPlanUtils.newTempId("sg"),
            title: t,
            emoji: e.isEmpty ? nil : e,
            description: d.isEmpty ? nil : d,
            metric: current?.metric)))
    }
}

// MARK: - 마일스톤

private struct DraftMilestoneForm: View {
    let index: Int?
    let plan: DraftGoalPlan
    let onSave: (DraftEditResult) -> Void
    let onDelete: () -> Void
    let deleteBlock: String?

    @State private var title: String
    @State private var description: String
    @State private var dueDate: String
    @State private var error: String?

    init(index: Int?, plan: DraftGoalPlan, onSave: @escaping (DraftEditResult) -> Void,
         onDelete: @escaping () -> Void, deleteBlock: String?) {
        self.index = index
        self.plan = plan
        self.onSave = onSave
        self.onDelete = onDelete
        self.deleteBlock = deleteBlock
        let current = index.flatMap { plan.milestones.indices.contains($0) ? plan.milestones[$0] : nil }
        _title = State(initialValue: current?.title ?? "")
        _description = State(initialValue: current?.description ?? "")
        _dueDate = State(initialValue: current?.dueDate ?? "")
    }

    private var current: DraftPlanMilestone? {
        index.flatMap { plan.milestones.indices.contains($0) ? plan.milestones[$0] : nil }
    }

    /// 마지막 단계는 항상 목표 종료일 (자동) — 날짜를 바꾸려면 목표의 종료일을 고쳐요
    private var isLast: Bool { index != nil && index == plan.milestones.count - 1 }

    var body: some View {
        let metric = plan.goal.metric
        DraftEditorScaffold(target: .milestone(index), canDelete: index != nil, deleteBlock: deleteBlock, error: $error,
                            onDelete: onDelete, onSave: { submit() }) {
            GoalCreationField("제목") {
                TextField("예) 적응기: 습관 세팅",
                          text: Binding(get: { title }, set: { title = String($0.prefix(DraftPlanUtils.Limits.title)) }))
                    .inputStyle()
            }
            HStack(alignment: .top, spacing: 12) {
                GoalCreationField(isLast ? "날짜 (목표 종료일)" : "날짜") {
                    GoalCreationDateField(ymd: $dueDate, min: plan.goal.startDate, max: plan.goal.endDate, disabled: isLast)
                }
                if let metric {
                    Text("이 시점 \(metric.name)은(는) 날짜에 맞춰\n자동으로 계산돼요")
                        .font(.pt(12)).foregroundStyle(Color.ink3).lineSpacing(2)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 24)
                }
            }
            GoalCreationField("설명 (선택)") {
                TextField("", text: Binding(get: { description }, set: { description = String($0.prefix(200)) }),
                          axis: .vertical)
                    .lineLimit(2...5)
                    .inputStyle(minHeight: 72)
            }
        }
    }

    private func submit() {
        let t = GoalCreationForm.trim(title)
        if t.isEmpty { error = "제목을 입력해 주세요."; return }
        if dueDate.isEmpty { error = "날짜를 정해 주세요."; return }
        if dueDate < plan.goal.startDate || dueDate > plan.goal.endDate { error = "목표 기간 안의 날짜를 골라 주세요."; return }
        let d = GoalCreationForm.trim(description)
        error = nil
        onSave(.milestone(index, DraftPlanMilestone(
            tempId: current?.tempId ?? DraftPlanUtils.newTempId("ms"),
            title: t,
            description: d.isEmpty ? nil : d,
            dueDate: dueDate,
            // 수치는 저장할 때 시작값→목표값 직선에서 자동 계산돼요 (normalizePlan)
            targetValue: current?.targetValue)))
    }
}

// MARK: - Task (루틴 / 일회성)

private struct DraftTaskForm: View {
    let index: Int?
    let type: DraftTaskType
    let plan: DraftGoalPlan
    let onSave: (DraftEditResult) -> Void
    let onDelete: () -> Void
    let deleteBlock: String?

    @State private var title: String
    @State private var emoji: String
    @State private var minutes: String
    @State private var subGoalId: String
    // 루틴
    @State private var days: [GoalCreationWeekday]
    @State private var startTime: String
    @State private var howTo: String
    // 일회성
    @State private var milestoneId: String
    @State private var scheduledDate: String
    @State private var dueDate: String
    @State private var error: String?

    init(index: Int?, type: DraftTaskType, plan: DraftGoalPlan, onSave: @escaping (DraftEditResult) -> Void,
         onDelete: @escaping () -> Void, deleteBlock: String?) {
        self.index = index
        self.type = type
        self.plan = plan
        self.onSave = onSave
        self.onDelete = onDelete
        self.deleteBlock = deleteBlock
        let current = index.flatMap { plan.tasks.indices.contains($0) ? plan.tasks[$0] : nil }
        let routine = type == .ROUTINE
        _title = State(initialValue: current?.title ?? "")
        _emoji = State(initialValue: current?.emoji ?? "")
        _minutes = State(initialValue: String(current?.durationMinutes ?? (routine ? 20 : 30)))
        _subGoalId = State(initialValue: current?.subGoalTempId ?? plan.subGoals.first?.tempId ?? "")
        _days = State(initialValue: current?.routineDays ?? [])
        _startTime = State(initialValue: current?.preferredStartTime ?? "")
        _howTo = State(initialValue: current?.description ?? "")
        _milestoneId = State(initialValue: current?.milestoneTempId ?? "")
        _scheduledDate = State(initialValue: current?.scheduledDate ?? "")
        _dueDate = State(initialValue: current?.dueDate ?? "")
    }

    private var current: DraftPlanTask? { index.flatMap { plan.tasks.indices.contains($0) ? plan.tasks[$0] : nil } }
    private var routine: Bool { type == .ROUTINE }
    private var limit: (min: Int, max: Int) { routine ? DraftPlanUtils.Limits.routineMinutes : DraftPlanUtils.Limits.oneTimeMinutes }
    private var milestone: DraftPlanMilestone? { plan.milestones.first { $0.tempId == milestoneId } }
    private var maxDue: String { milestone?.dueDate ?? plan.goal.endDate }

    var body: some View {
        DraftEditorScaffold(target: .task(index, type), canDelete: index != nil, deleteBlock: deleteBlock, error: $error,
                            onDelete: onDelete, onSave: { submit() }) {
            DraftEmojiTitleRow(emoji: $emoji, title: $title, placeholder: routine ? "예) 하체 근력 50분" : "예) 5km 기록 측정")
            HStack(alignment: .top, spacing: 12) {
                GoalCreationField("걸리는 시간 (분)", hint: "\(limit.min)~\(limit.max)분") {
                    TextField("", text: $minutes).keyboardType(.numberPad).inputStyle()
                }
                GoalCreationField("세부 목표") { subGoalPicker }
            }
            if routine { routineFields } else { oneTimeFields }
        }
    }

    private var subGoalPicker: some View {
        Picker("세부 목표", selection: $subGoalId) {
            ForEach(plan.subGoals, id: \.tempId) { sg in
                Text("\(sg.emoji ?? "") \(sg.title)").tag(sg.tempId)
            }
        }
        .pickerStyle(.menu)
        .tint(Color.ink)
        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
        .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.borderSubtle, lineWidth: 1))
    }

    @ViewBuilder
    private var routineFields: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("반복 요일").font(.pt(13, .semibold)).foregroundStyle(Color.ink2)
            GoalCreationDayChips(days: $days)
        }
        GoalCreationField("시작 시각 (선택)") {
            GoalCreationTimeField(hm: $startTime, placeholder: "시간 자동")
        }
        GoalCreationField("하는 방법 (선택)", hint: "\(howTo.count)/\(DraftPlanUtils.Limits.routineDescription)") {
            TextField("예) 스쿼트 4x10 → 런지 3x12(각) → 레그프레스 3x12, 세트 사이 60~90초 휴식",
                      text: Binding(get: { howTo },
                                    set: { howTo = String($0.prefix(DraftPlanUtils.Limits.routineDescription)) }),
                      axis: .vertical)
                .lineLimit(3...8)
                .inputStyle(minHeight: 88)
        }
    }

    @ViewBuilder
    private var oneTimeFields: some View {
        GoalCreationField("마일스톤 (선택)") {
            Picker("마일스톤", selection: $milestoneId) {
                Text("연결 안 함").tag("")
                ForEach(plan.milestones, id: \.tempId) { ms in
                    Text("\(ms.title) (~\(DraftPlanUtils.formatMd(ms.dueDate, withWeekday: false)))").tag(ms.tempId)
                }
            }
            .pickerStyle(.menu)
            .tint(Color.ink)
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
            .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.borderSubtle, lineWidth: 1))
        }
        HStack(alignment: .top, spacing: 12) {
            GoalCreationField("실행일 (선택)") {
                GoalCreationDateField(ymd: $scheduledDate, min: plan.goal.startDate,
                                      max: dueDate.isEmpty ? maxDue : dueDate, clearable: true)
            }
            GoalCreationField("마감일") {
                GoalCreationDateField(ymd: $dueDate, min: scheduledDate.isEmpty ? plan.goal.startDate : scheduledDate,
                                      max: maxDue)
            }
        }
    }

    private func submit() {
        let t = GoalCreationForm.trim(title)
        if t.isEmpty { error = "제목을 입력해 주세요."; return }
        guard let m = Int(GoalCreationForm.trim(minutes)), m >= limit.min, m <= limit.max else {
            error = "\(routine ? "루틴" : "할 일")은 \(limit.min)~\(limit.max)분으로 설정해 주세요."
            return
        }

        // 제목에 쓴 시간("유산소 25분")은 걸리는 시간을 바꾸면 같이 바꿔요 (제목과 시간이 어긋나지 않게)
        var synced = title
        if let old = current?.durationMinutes, old != m, title.contains("\(old)분") {
            if let r = title.range(of: "\(old)분") { synced = title.replacingCharacters(in: r, with: "\(m)분") }
        }
        let e = GoalCreationForm.trim(emoji)
        var item = DraftPlanTask(
            tempId: current?.tempId ?? DraftPlanUtils.newTempId("t"),
            type: type,
            subGoalTempId: subGoalId.isEmpty ? nil : subGoalId,
            milestoneTempId: nil,
            title: GoalCreationForm.trim(synced),
            emoji: e.isEmpty ? nil : e,
            durationMinutes: m)

        if routine {
            if days.isEmpty { error = "요일을 하나 이상 골라 주세요."; return }
            let how = GoalCreationForm.trim(howTo)
            item.milestoneTempId = current?.milestoneTempId
            item.routineDays = GoalCreationWeekday.allCases.filter { days.contains($0) }
            item.preferredStartTime = startTime.isEmpty ? nil : startTime
            item.startDate = current?.startDate ?? plan.goal.startDate
            item.endDate = current?.endDate
            item.description = how.isEmpty ? nil : how
            item.reason = current?.reason
            error = nil
            onSave(.task(index, item))
            return
        }

        if dueDate.isEmpty { error = "마감일을 정해 주세요."; return }
        if !scheduledDate.isEmpty && scheduledDate > dueDate { error = "실행일은 마감일보다 늦을 수 없어요."; return }
        if dueDate > maxDue {
            if let milestone {
                error = "마감일은 연결된 마일스톤(\(DraftPlanUtils.formatMd(milestone.dueDate, withWeekday: false)))보다 늦을 수 없어요."
            } else {
                error = "마감일은 목표 종료일보다 늦을 수 없어요."
            }
            return
        }
        item.milestoneTempId = milestoneId.isEmpty ? nil : milestoneId
        item.scheduledDate = scheduledDate.isEmpty ? nil : scheduledDate
        item.dueDate = dueDate
        error = nil
        onSave(.task(index, item))
    }
}

#Preview("루틴 수정") {
    Color.canvas
        .sheet(isPresented: .constant(true)) {
            DraftItemEditorSheet(target: .task(0, .ROUTINE), plan: GoalCreationSamples.plan,
                                 onSave: { _ in }, onDelete: {}, deleteDisabledReason: nil)
        }
}

#Preview("목표 수정") {
    Color.canvas
        .sheet(isPresented: .constant(true)) {
            DraftItemEditorSheet(target: .goal, plan: GoalCreationSamples.plan,
                                 onSave: { _ in }, onDelete: {}, deleteDisabledReason: nil)
        }
}
