// Features/TaskAdd/BrainDumpStepGoals.swift
//
// 웹 원본: features/brain-dump/ui/StepGoals.tsx
//
// 2단계 · 목표 연결
//  - AI 결과를 목표별로 묶어서 보여줘요. 관련 목표가 없으면 임시 목표.
//  - "변경"으로 목표·날짜·시간·소요 시간·중요도를 고쳐요 (BrainDumpItemEditSheet).
//  - 모든 Task 가 목표에 연결돼야 "TimeBlock 만들기"를 누를 수 있어요 (백엔드 BRAIN_DUMP_GOAL_REQUIRED).

import SwiftUI

@MainActor
struct BrainDumpStepGoals: View {
    let items: [BrainDumpMapItem]
    let tempGoals: [TempGoalInput]
    let goals: [GoalCategory]
    let aiFailed: Bool
    /// 오늘 AI 정리 횟수를 다 씀 (Free 하루 3번)
    var aiLimited: Bool = false
    /// clientKey → 겹친 일정 안내 (확정 시 conflicts)
    let conflicts: [String: String]
    let today: String
    let loading: Bool
    let onChange: ([BrainDumpMapItem], [TempGoalInput]) -> Void
    let onBack: () -> Void
    let onConfirm: () -> Void

    @State private var editing: BrainDumpMapItem? = nil
    /// 이름을 고치고 있는 임시 목표 (벗어날 때 temp_rename)
    @FocusState private var renaming: String?
    @State private var renameStart = ""

    // MARK: 목표별 묶음

    private var groups: [BrainDumpGoalGroup] {
        var out: [BrainDumpGoalGroup] = []
        var index: [String: Int] = [:]
        for it in items {
            var g = BrainDumpGoalGroup(key: "none", title: "목표를 골라 주세요", emoji: nil, color: "#B8BCC4", tempKey: nil)
            if let gid = it.goalCategoryId {
                let goal = goals.first { $0.goalCategoryId == gid }
                g = BrainDumpGoalGroup(key: "g\(gid)", title: goal?.goalCategoryName ?? "목표", emoji: goal?.emoji,
                                       color: hexOf(goal?.colorCode), tempKey: nil)
            } else if let tk = it.tempGoalKey {
                let t = tempGoals.first { $0.tempKey == tk }
                g = BrainDumpGoalGroup(key: "t:\(tk)", title: t?.name ?? "임시 목표", emoji: t?.emoji,
                                       color: "#F59E0B", tempKey: tk)
            }
            if let i = index[g.key] {
                out[i].items.append(it)
            } else {
                g.items = [it]
                index[g.key] = out.count
                out.append(g)
            }
        }
        // 목표가 없는 묶음은 맨 위에 (먼저 고르도록)
        return out.filter { $0.key == "none" } + out.filter { $0.key != "none" }
    }

    private var missing: Int { items.filter { $0.goalCategoryId == nil && $0.tempGoalKey == nil }.count }
    private var blankTemp: Bool {
        tempGoals.contains { $0.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }

    private var nextTempKey: String {
        let maxN = tempGoals.reduce(0) { m, t in max(m, Int(t.tempKey.dropFirst()) ?? 0) }
        return "t\(maxN + 1)"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("목표에 연결했어요")
                .font(.pt(16, .semibold))
                .foregroundStyle(Color.ink)
            Text("잘못 연결됐거나 날짜·시간이 다르면 \"변경\"을 눌러 고쳐 주세요. 관련 목표가 없는 일은 임시 목표로 묶었어요.")
                .font(.pt(14))
                .foregroundStyle(Color.ink3)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 4)

            if aiFailed {
                BrainDumpNotice(text: aiLimited
                                ? "오늘 AI 정리 횟수를 다 써서 기본 정리로 나눠 두었어요. 목표는 \"변경\"에서 직접 골라 주세요. (내일 0시에 다시 채워져요 · Pro는 하루 30번)"
                                : "AI 정리가 잠시 안 돼서, 적은 내용만 나눠 두었어요. 목표는 \"변경\"에서 직접 골라 주세요.",
                                danger: false)
                    .padding(.top, 16)
            }
            if !conflicts.isEmpty {
                BrainDumpNotice(text: "직접 정한 시간이 다른 일정과 겹쳐서 저장하지 않았어요. 빨갛게 표시된 Task의 시간을 바꿔 주세요.",
                                danger: true)
                    .padding(.top, 16)
            }

            VStack(alignment: .leading, spacing: 20) {
                ForEach(groups) { g in
                    groupSection(g)
                }
            }
            .padding(.top, 20)

            footer.padding(.top, 24)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .aiBorder()   // AI 정리 결과
        .onAppear {
            TA.track("task_add_goals.view", [
                "item_count": items.count, "missing_goal_count": missing,
                "temp_count": Set(items.compactMap(\.tempGoalKey)).count,
                "ai_failed": aiFailed, "ai_limited": aiLimited,
            ])
        }
        .onChange(of: renaming) { old, new in
            if let old, let t = tempGoals.first(where: { $0.tempKey == old }), t.name != renameStart {
                TA.track("task_add_goals.temp_rename", ["text_length": t.name.trimmingCharacters(in: .whitespaces).count])
            }
            if let new { renameStart = tempGoals.first { $0.tempKey == new }?.name ?? "" }
        }
        .sheet(item: $editing) { it in
            BrainDumpItemEditSheet(
                item: it,
                goals: goals,
                tempGoals: tempGoals,
                today: today,
                nextTempKey: nextTempKey,
                canAddTemp: tempGoals.count < BrainDumpConst.maxTempGoals,
                onSave: { next, newTemp in save(next, newTemp) }
            )
        }
    }

    // MARK: 묶음 하나

    @ViewBuilder
    private func groupSection(_ g: BrainDumpGoalGroup) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Circle().fill(Color.goal(g.color)).frame(width: 10, height: 10)
                if let tk = g.tempKey {
                    HStack(spacing: 6) {
                        if let e = g.emoji, !e.isEmpty { Text(e).font(.pt(15)) }
                        TextField("임시 목표 이름", text: Binding(
                            get: { tempGoals.first { $0.tempKey == tk }?.name ?? "" },
                            set: { v in renameTemp(tk, v) }
                        ))
                        .font(.pt(15, .bold))
                        .foregroundStyle(Color.ink)
                        .fixedSize(horizontal: true, vertical: false)
                        .focused($renaming, equals: tk)
                        .accessibilityLabel("임시 목표 이름")
                        NSymbol("pencil", size: 12)
                            .foregroundStyle(Color.ink4)
                        Badge("임시 목표", tone: .warning)
                    }
                } else {
                    Text((g.emoji.map { "\($0) " } ?? "") + g.title)
                        .font(.pt(15, .bold))
                        .foregroundStyle(g.key == "none" ? Color.danger : Color.ink)
                        .lineLimit(1)
                }
                Text("\(g.items.count)개")
                    .font(.pt(12))
                    .foregroundStyle(Color.ink4)
                Spacer(minLength: 0)
            }
            VStack(spacing: 8) {
                ForEach(g.items) { it in
                    BrainDumpItemRow(item: it, goals: goals, conflict: conflicts[it.clientKey],
                                     onPriority: { p in selectPriority(it, p) },
                                     onTopic: { id, count in selectTopic(it, id, count) }) {
                        TA.track("task_add_goals.edit_open", [
                            "list_position": items.firstIndex { $0.clientKey == it.clientKey } ?? 0,
                            "goal_match": TA.goalMatch(goalId: it.goalCategoryId, tempKey: it.tempGoalKey),
                            "has_conflict": conflicts[it.clientKey] != nil,
                        ])
                        editing = it
                    }
                }
            }
        }
    }

    // MARK: 아래 버튼

    private var footer: some View {
        VStack(alignment: .trailing, spacing: 10) {
            if missing > 0 {
                Text("\(missing)개 Task의 목표를 골라 주세요")
                    .font(.pt(14))
                    .foregroundStyle(Color.danger)
            }
            if blankTemp {
                Text("임시 목표 이름을 입력해 주세요")
                    .font(.pt(14))
                    .foregroundStyle(Color.danger)
            }
            HStack(spacing: 8) {
                NButton("이전", variant: .secondary, action: onBack)
                    .disabled(loading)
                NButton("TimeBlock 만들기", loading: loading, action: onConfirm)
                    .disabled(missing > 0 || blankTemp || loading)
                    // 흐린 버튼을 누름 (목표 안 고른 Task · 빈 임시 목표 이름)
                    .disabledTap(!loading && (missing > 0 || blankTemp)) {
                        TA.track("task_add_goals.confirm_blocked", [
                            "disabled_reason": missing > 0 ? "no_goal" : "blank_temp", "missing_goal_count": missing,
                        ])
                    }
            }
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
    }

    // MARK: 동작

    private func renameTemp(_ tempKey: String, _ name: String) {
        let next = tempGoals.map { t -> TempGoalInput in
            guard t.tempKey == tempKey else { return t }
            var renamed = t
            renamed.name = String(name.prefix(30))
            return renamed
        }
        onChange(items, next)
    }

    /// 행에서 바로 중요도 고르기 — "변경" 시트를 열지 않아도 돼요
    private func selectPriority(_ it: BrainDumpMapItem, _ p: TaskPriority) {
        TA.track("task_add_goals.priority_select", [
            "list_position": items.firstIndex { $0.clientKey == it.clientKey } ?? 0,
            "priority": p.rawValue,
            "prev_priority": (it.priority ?? .fallback).rawValue,
        ])
        let list = items.map { i -> BrainDumpMapItem in
            guard i.clientKey == it.clientKey else { return i }
            var next = i
            next.priority = p
            return next
        }
        onChange(list, tempGoals)
    }

    /// 업무형 목표의 주제를 줄에서 바로 고르기 — 고른 주제는 업무 보드에서 그 주제 아래에 보여요
    private func selectTopic(_ it: BrainDumpMapItem, _ topicId: Int?, _ topicCount: Int) {
        TA.track("task_add_goals.topic_select", [
            "list_position": items.firstIndex { $0.clientKey == it.clientKey } ?? 0,
            "has_topic": topicId != nil,
            "topic_count": topicCount,
        ])
        let list = items.map { i -> BrainDumpMapItem in
            guard i.clientKey == it.clientKey else { return i }
            var next = i
            next.workTopicId = topicId
            return next
        }
        onChange(list, tempGoals)
    }

    private func save(_ next: BrainDumpMapItem, _ newTemp: TempGoalInput?) {
        var next = next
        // 목표가 바뀌면 주제는 그 목표의 것이 아니라 비워요
        if let prev = items.first(where: { $0.clientKey == next.clientKey }), prev.goalCategoryId != next.goalCategoryId {
            next.workTopicId = nil
        }
        let list = items.map { $0.clientKey == next.clientKey ? next : $0 }
        let temps = newTemp.map { tempGoals + [$0] } ?? tempGoals
        // 아무 Task 도 가리키지 않는 임시 목표는 만들지 않아요
        let used = Set(list.compactMap(\.tempGoalKey))
        onChange(list, temps.filter { used.contains($0.tempKey) })
        editing = nil
    }
}

/// 목표별 묶음 (파일 안에서만 써요)
private struct BrainDumpGoalGroup: Identifiable {
    var id: String { key }
    var key: String
    var title: String
    var emoji: String?
    /// "#RRGGBB"
    var color: String
    /// 임시 목표 묶음이면 그 tempKey
    var tempKey: String?
    var items: [BrainDumpMapItem] = []
}

/// 노란 · 빨간 안내 상자
private struct BrainDumpNotice: View {
    let text: String
    let danger: Bool

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "exclamationmark.circle")
                .font(.system(size: 14))
                .padding(.top, 1)
            Text(text)
                .font(.pt(14))
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .foregroundStyle(danger ? Color.danger : Color.warningInk)
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(danger ? Color.dangerSoft : Color.warningSoft, in: RoundedRectangle(cornerRadius: Radius.xl))
    }
}

// MARK: - Task 한 줄

private struct BrainDumpItemRow: View {
    let item: BrainDumpMapItem
    let goals: [GoalCategory]
    let conflict: String?
    /// 행에서 바로 중요도 고르기 (2026-10, 웹 StepGoals onPriority)
    let onPriority: (TaskPriority) -> Void
    /// 업무형 목표면 주제를 바로 고르기 (topicId, 고를 수 있는 주제 수)
    let onTopic: (Int?, Int) -> Void
    let onEdit: () -> Void

    @State private var pickPriority = false
    private var priority: TaskPriority { item.priority ?? .fallback }

    private var goal: GoalCategory? {
        guard let gid = item.goalCategoryId else { return nil }
        return goals.first { $0.goalCategoryId == gid }
    }
    private var sub: SubGoalInfo? {
        goal?.generalCategories.first { $0.generalCategoryId == item.generalCategoryId }
    }
    private var milestone: MilestoneInfo? {
        goal?.milestones?.first { $0.milestoneId == item.milestoneId }
    }
    private var dateLabel: String {
        if item.dateType == .DUE, let d = item.dueDate { return "\(KST.formatMonthDay(d))까지" }
        if item.dateType == .ON, let d = item.scheduledDate { return KST.formatMonthDay(d) }
        return "날짜 없음"
    }
    private var timeLabel: String? {
        guard let s = item.startTime else { return nil }
        let end = item.endTime.map { "–\($0.prefix(5))" } ?? ""
        return "\(s.prefix(5))\(end)"
    }

    private var priorityButton: some View {
        Button { pickPriority.toggle() } label: {
            HStack(spacing: 3) {
                Text(priority.rawValue)
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(priority.isTop ? Color.white : Color.ink3)
                    .frame(width: 14, height: 14)
                    .background(priority.isTop ? Color.textBrand : Color.subtle, in: RoundedRectangle(cornerRadius: 4))
                Text(BrainDumpConst.priorityLabel(item.priority))
                    .fontWeight(priority.isTop ? .semibold : .regular)
                    .foregroundStyle(priority.isTop ? Color.textBrand : Color.ink3)
                NSymbol("chevron.down", size: 9)
                    .rotationEffect(.degrees(pickPriority ? 180 : 0))
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("중요도 \(priority.rawValue) \(priority.label) 바꾸기")
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 0) {
                Text(item.title)
                    .font(.pt(15, .semibold))
                    .foregroundStyle(Color.ink)
                FlowLayout(spacing: 10, lineSpacing: 4) {
                    meta("calendar.badge.clock", dateLabel)
                    if let t = timeLabel { meta("clock", t) }
                    Text("예상 \(KST.formatDuration(item.estimatedMinutes ?? 30))")
                    priorityButton
                    if item.kind == .APPOINTMENT { Badge("약속", tone: .warning) }
                    if let sub { Text("› \(sub.generalCategoryName)") }
                    if let milestone { Text("🏁 \(milestone.title)") }
                }
                .font(.pt(12))
                .foregroundStyle(Color.ink3)
                .padding(.top, 4)

                if let goal, goal.isWorkGoal {
                    BrainDumpWorkTopicPicker(goalId: goal.goalCategoryId, value: item.workTopicId, onChange: onTopic)
                        .padding(.top, 8)
                }

                if pickPriority {
                    HStack(spacing: 4) {
                        ForEach(TaskPriority.allCases, id: \.self) { p in
                            Chip(p.rawValue, selected: priority == p, size: .sm) {
                                pickPriority = false
                                if p != priority { onPriority(p) }
                            }
                            .accessibilityLabel("중요도 \(p.rawValue) \(p.label)")
                        }
                    }
                    .padding(.top, 8)
                }

                if let reason = item.reason, !reason.isEmpty, conflict == nil {
                    HStack(alignment: .top, spacing: 4) {
                        NSymbol("sparkles", size: 10)
                            .foregroundStyle(Color.aiGradient)
                            .padding(.top, 2)
                        Text(reason)
                            .font(.pt(12))
                            .foregroundStyle(Color.ink3)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(.top, 6)
                }
                if let conflict {
                    Text(conflict)
                        .font(.pt(12, .semibold))
                        .foregroundStyle(Color.danger)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 6)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            NButton("변경", variant: .secondary, size: .sm, action: onEdit)
                .accessibilityLabel("\(item.title) 변경")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(conflict != nil ? Color.dangerSoft.opacity(0.4) : Color.surface,
                    in: RoundedRectangle(cornerRadius: Radius.xxl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xxl)
            .stroke(conflict != nil ? Color.danger : Color.line, lineWidth: 1))
    }

    private func meta(_ icon: String, _ text: String) -> some View {
        HStack(spacing: 4) {
            NSymbol(icon, size: 11)
            Text(text)
        }
    }
}

#Preview {
    let today = KST.today()
    return ScrollView {
        BrainDumpStepGoals(
            items: TaskAddSample.items(today: today),
            tempGoals: [TempGoalInput(tempKey: "t1", name: "이사 준비", emoji: "📦", colorId: nil)],
            goals: TaskAddSample.goals(),
            aiFailed: false,
            conflicts: ["a": "이 시간엔 이미 \"팀 회의\"이(가) 겹쳐 있어요 (19:00–20:00). 시간을 조금 옮겨 주세요."],
            today: today,
            loading: false,
            onChange: { _, _ in },
            onBack: {},
            onConfirm: {}
        )
        .padding(16)
    }
    .canvasBackground()
}

// MARK: - 업무형 목표의 주제 고르기 (웹 StepGoals WorkTopicPicker)

private struct BrainDumpWorkTopicPicker: View {
    let goalId: Int
    let value: Int?
    let onChange: (Int?, Int) -> Void

    @State private var topics: [WorkTopic] = []
    @State private var loaded = false

    /// 끝내지 않은 주제만 (지금 고른 주제는 끝냈어도 남겨요)
    private var choices: [WorkTopic] {
        topics.filter { $0.topicId != nil && (!$0.completed || $0.topicId == value) }
    }
    private var selectedLabel: String {
        guard let v = value, let t = choices.first(where: { $0.topicId == v }) else { return "선택 안 함 (기타 할 일)" }
        return [t.emoji, t.name].compactMap { $0 }.joined(separator: " ")
    }

    var body: some View {
        Group {
            if loaded && choices.isEmpty {
                HStack(spacing: 4) {
                    NSymbol("briefcase", size: 11)
                    Text("세부 목표 없음 · 목표 상세의 로드맵에서 세부 목표를 만들면 여기서 고를 수 있어요")
                }
                .font(.pt(12))
                .foregroundStyle(Color.ink3)
            } else if loaded {
                Menu {
                    Button { onChange(nil, choices.count) } label: {
                        if value == nil { Label("선택 안 함 (기타 할 일)", systemImage: "checkmark") } else { Text("선택 안 함 (기타 할 일)") }
                    }
                    ForEach(choices, id: \.topicId) { t in
                        let title = [t.emoji, t.name].compactMap { $0 }.joined(separator: " ")
                        Button { onChange(t.topicId, choices.count) } label: {
                            if t.topicId == value { Label(title, systemImage: "checkmark") } else { Text(title) }
                        }
                    }
                } label: {
                    HStack(spacing: 4) {
                        NSymbol("briefcase", size: 11)
                        Text("세부 목표")
                        Text(selectedLabel)
                            .foregroundStyle(value != nil ? Color.ink : Color.ink3)
                            .lineLimit(1)
                        NSymbol("chevron.down", size: 9)
                    }
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.surface, in: RoundedRectangle(cornerRadius: 8))
                    .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(Color.borderSubtle, lineWidth: 1))
                }
                .accessibilityLabel("세부 목표 \(selectedLabel)")
            }
        }
        .task(id: goalId) {
            topics = (try? await WorkAPI.board(goalId: goalId))?.topics ?? []
            loaded = true
        }
    }
}
