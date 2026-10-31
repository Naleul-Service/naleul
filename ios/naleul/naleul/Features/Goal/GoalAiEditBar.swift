// Features/Goal/GoalAiEditBar.swift
//
// 웹 features/goal/ui/GoalAiEditBar.tsx — 목표 상세 "말로 고치기".
// 트리 구조(영역 → 루틴 → 점검 시점)나 수치 계산 방식을 몰라도 한 문장으로 고칠 수 있게 해요.
//  1) 문장을 보내면 AI 가 "바뀔 항목 목록"을 만들어요 — 이때는 아무것도 안 바뀌어요
//  2) 바뀌기 전/후를 보고 원하는 것만 골라 [적용]
// 실행 중인 목표라 바로 바꾸지 않고 꼭 확인을 거쳐요.

import SwiftUI

@MainActor
struct GoalAiEditBar: View {
    let goal: GoalCategory
    /// 시트로 열 때 바로 키보드를 올려요
    var autoFocus = false

    @Environment(GoalDetailViewModel.self) private var vm
    @State private var asking = false
    /// 미리보기 시트로 넘어갔는지 (넘어가면 입력 시트 닫힘 이벤트를 안 보내요)
    @State private var outcome: String?
    @FocusState private var focused: Bool

    /// '최근 기록 보고 계획 조정받기'가 채우는 문장
    static let prefill = "최근 기록과 회고를 보고 계획을 조정해줘"

    private static let maxLength = 200
    private static let examples = [
        "최근 기록 보고 계획 조정해줘",
        "운동은 주 2회로 줄여줘",
        "종료일 2주 늦춰줘",
        "아침 7시에 스트레칭 10분 추가해줘",
    ]

    var body: some View {
        @Bindable var vm = vm

        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                AIBadge(size: .sm)
                TextField("바꾸고 싶은 걸 말로 적어 보세요. 예) 운동은 주 2회로 줄여줘", text: $vm.aiEditText)
                    .font(.pt(15))
                    .foregroundStyle(Color.ink)
                    .focused($focused)
                    .submitLabel(.send)
                    .onSubmit { submit(method: "keyboard_send") }
                    .disabled(asking)
                    .goalMaxLength($vm.aiEditText, Self.maxLength)
                    .accessibilityLabel("말로 고치기")
                NButton("고치기", variant: .ai, size: .sm, icon: "sparkles", loading: asking) { submit(method: "button") }
                    .disabled(vm.aiEditText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .frame(minHeight: 40)

            if vm.aiEditText.isEmpty {
                FlowLayout(spacing: 6) {
                    ForEach(Self.examples, id: \.self) { ex in
                        Button {
                            vm.track("goal_detail.ai_edit_example_click", ["example_index": Self.examples.firstIndex(of: ex)])
                            vm.aiEditText = ex
                        } label: {
                            Text(ex)
                                .font(.pt(12))
                                .foregroundStyle(Color.ink3)
                                .padding(.horizontal, 8)
                                .frame(height: 24)
                                .overlay(Capsule().stroke(Color.line, lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.leading, 28)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .aiBorder()
        // "오늘 기록" 카드의 "최근 기록 보고 계획 조정받기" → 문장을 채우고 이 칸으로 커서 이동
        .onChange(of: vm.aiFocusTick) { focused = true }
        .onAppear {
            guard autoFocus else { return }
            // 시트가 다 올라온 뒤에 키보드를 올려야 애니메이션이 겹치지 않아요
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 350_000_000)
                focused = true
            }
        }
        .onDisappear {
            // 시트로 열었을 때만 — 보내지 않고 X · 쓸어 닫기
            guard autoFocus, let m = GD.closeMethod(outcome) else { return }
            vm.track("goal_detail.ai_edit_sheet_close", ["close_method": m, "text_length": vm.aiEditText.count])
        }
    }

    private func submit(method: String) {
        let value = vm.aiEditText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, !asking else { return }
        let id = goal.goalCategoryId
        vm.track("goal_detail.ai_edit_submit", [
            "text_length": value.count, "is_prefilled": value == Self.prefill, "submit_method": method,
        ])
        asking = true
        focused = false
        Task { @MainActor in
            let res = await Mutation.run(successMessage: nil) {
                try await GoalEditAPI.editPreview(goalId: id, instruction: value)
            }
            asking = false
            vm.track("goal_detail.ai_edit_preview_result", GD.result(res != nil).merging(["change_count": res?.changes.count]) { $1 })
            if let res {
                outcome = "next"
                vm.sheet = .aiPreview(res)
            }
        }
    }
}

// MARK: - 말로 고치기 시트 (✨ 버튼 · … 메뉴에서 열어요)

@MainActor
struct GoalAiEditSheet: View {
    let goal: GoalCategory

    var body: some View {
        SheetScaffold(title: "말로 고치기",
                      description: "바꾸고 싶은 걸 한 문장으로 적어 주세요. 바로 바꾸지 않고, 바뀔 내용을 먼저 보여드려요.",
                      detents: [.medium, .large]) {
            GoalAiEditBar(goal: goal, autoFocus: true)
        }
    }
}

// MARK: - 미리보기 시트 ("이렇게 바꿀까요?")

@MainActor
struct GoalAiEditPreviewSheet: View {
    let goalId: Int
    let preview: GoalEditPreview

    @Environment(GoalDetailViewModel.self) private var vm
    @State private var picked: [Bool]
    @State private var applying = false
    @State private var outcome: String?

    init(goalId: Int, preview: GoalEditPreview) {
        self.goalId = goalId
        self.preview = preview
        _picked = State(initialValue: preview.changes.map { _ in true })
    }

    private var chosen: [JSONValue] {
        preview.changes.indices.filter { $0 < picked.count && picked[$0] }.map { preview.changes[$0].op }
    }

    private func tone(_ action: String) -> BadgeTone {
        switch action {
        case "추가": return .brand
        case "삭제": return .danger
        default: return .neutral
        }
    }

    var body: some View {
        SheetScaffold(title: "이렇게 바꿀까요?", description: preview.summary.isEmpty ? nil : preview.summary,
                      dismissible: !applying, detents: [.large]) {
            if !preview.changes.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("아직 아무것도 바뀌지 않았어요. 적용할 항목만 체크해 주세요.")
                        .font(.pt(13))
                        .foregroundStyle(Color.ink3)
                        .padding(.bottom, 4)
                    ForEach(preview.changes.indices, id: \.self) { i in
                        changeRow(i)
                    }
                }
            }
        } footer: {
            if preview.changes.isEmpty {
                NButton("다시 적기", variant: .secondary) {
                    outcome = "retry_button"
                    vm.sheet = nil
                }
            } else {
                NButton("취소", variant: .secondary) {
                    outcome = "cancel_button"
                    vm.sheet = nil
                }
                    .disabled(applying)
                NButton("\(chosen.count)개 적용", loading: applying) { apply() }
                    .disabled(chosen.isEmpty)
            }
        }
        .onDisappear {
            if let m = GD.closeMethod(outcome) {
                vm.track("goal_detail.ai_edit_cancel", ["close_method": m, "change_count": preview.changes.count])
            }
        }
    }

    private func changeRow(_ i: Int) -> some View {
        let c = preview.changes[i]
        let on = i < picked.count && picked[i]
        return Button {
            if i < picked.count {
                vm.track("goal_detail.ai_edit_item_toggle", ["change_action": c.action, "checked": !picked[i]])
                picked[i].toggle()
            }
        } label: {
            HStack(alignment: .top, spacing: 12) {
                NSymbol(on ? "checkmark.square.fill" : "square", size: 18)
                    .foregroundStyle(on ? Color.ink : Color.ink4)
                    .padding(.top, 1)
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Badge(c.action, tone: tone(c.action))
                        Text(c.target).font(.pt(14, .semibold)).foregroundStyle(Color.ink)
                    }
                    if let b = c.before, !b.isEmpty {
                        Text(b).font(.pt(13)).foregroundStyle(Color.ink3).strikethrough()
                    }
                    if let a = c.after, !a.isEmpty {
                        Text(a).font(.pt(14)).foregroundStyle(Color.ink)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .multilineTextAlignment(.leading)
            }
            .padding(12)
            .background(on ? Color.surface : Color.bgSunken, in: RoundedRectangle(cornerRadius: Radius.xxl))
            .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(on ? Color.actionPrimary : Color.borderSubtle, lineWidth: 1))
            .opacity(on ? 1 : 0.6)
            .contentShape(RoundedRectangle(cornerRadius: Radius.xxl))
        }
        .buttonStyle(.plain)
        .disabled(applying)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func apply() {
        let ops = chosen
        guard !ops.isEmpty, !applying else { return }
        let id = goalId
        vm.track("goal_detail.ai_edit_apply_click", ["selected_count": ops.count, "change_count": preview.changes.count])
        applying = true
        Task { @MainActor in
            let ok = await Mutation.run(invalidate: [.goals, .timetable], successMessage: "\(ops.count)개 변경을 적용했어요.") {
                try await GoalEditAPI.editApply(goalId: id, ops: ops)
            }
            applying = false
            vm.track("goal_detail.ai_edit_apply_result", GD.result(ok != nil).merging(["selected_count": ops.count]) { $1 })
            if ok != nil {
                outcome = "saved"
                vm.sheet = nil
                vm.aiEditText = ""
            }
        }
    }
}

#Preview("말로 고치기") {
    ScrollView {
        GoalAiEditBar(goal: GoalPreviewData.goal).padding(16)
    }
    .canvasBackground()
    .environment(GoalDetailViewModel.preview())
}

#Preview("말로 고치기 시트") {
    Color.canvas
        .sheet(isPresented: .constant(true)) {
            GoalAiEditSheet(goal: GoalPreviewData.goal)
                .environment(GoalDetailViewModel.preview())
        }
}

#Preview("변경 미리보기") {
    let preview = GoalEditPreview(summary: "운동 루틴을 주 2회로 줄이고 종료일을 2주 늦출게요.", changes: [
        GoalEditChange(op: ["type": "UPDATE_ROUTINE", "targetId": 32], action: "변경", target: "루틴 '하체 근력 30분'",
                       before: "화·목·토", after: "화·목"),
        GoalEditChange(op: ["type": "UPDATE_GOAL"], action: "변경", target: "목표", before: "12월 7일까지", after: "12월 21일까지"),
        GoalEditChange(op: ["type": "ADD_ROUTINE"], action: "추가", target: "루틴 '아침 스트레칭 10분'", before: nil, after: "매일 07:00"),
    ])
    return Color.canvas
        .sheet(isPresented: .constant(true)) {
            GoalAiEditPreviewSheet(goalId: 1, preview: preview)
                .environment(GoalDetailViewModel.preview())
        }
}
