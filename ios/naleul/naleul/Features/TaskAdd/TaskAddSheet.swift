// Features/TaskAdd/TaskAddSheet.swift
//
// 웹 원본: features/brain-dump/ui/TaskAddPanel.tsx (머리 · 닫기) + TaskAddView.tsx (variant="panel")
//         app/(main)/tasks/new/page.tsx (?mode=manual|log → initialMode)
//
// 캘린더 위에 시트로 여는 "Task 추가".
//  - 추가 방식: AI로 정리하기 / 직접 추가하기 / 한 일 기록
//  - AI로 정리하기 = 3단계: Task 입력 → 목표 연결 → 확인
//  - 저장하면 onAdded 로 캘린더에 알려요 → 캘린더가 그 날짜로 옮기고 새 블록에 NEW 를 붙여요.
//    시간을 정하지 않은 Task(pending)가 있으면 캘린더가 시트를 닫고 점선 미리보기로 "이대로 진행할까요?"를 물어요.
//
// iOS 에서 바꾼 점
//  - 웹 /tasks/new 페이지는 따로 두지 않았어요. 앱에서는 모든 "Task 추가"가 이 시트예요
//    (router.openTaskAdd(mode:) → 캘린더가 이 시트를 띄워요).
//  - 웹 패널은 결과를 옆 캘린더에서 보지만, 아이폰은 시트가 캘린더를 가리므로
//    요약 아래 "TimeBlock 확인"을 누르면 웹 StepResult(격자 미리보기)를 시트 안에서 볼 수 있게 했어요.

import SwiftUI

@MainActor
struct TaskAddSheet: View {
    private let onAdded: (TaskAddedInfo) -> Void
    @State private var vm: TaskAddViewModel
    @State private var showDetail = false
    /// 코드가 시트를 닫으면 "next" (점선 제안 · 다른 화면으로 이동) — 그 밖엔 X / 아래로 쓸어 닫기
    @State private var closeOutcome: String?
    @Environment(\.dismiss) private var dismiss
    /// true = 그 방식만 (방식 고르기 탭 없음 · 제목 = 메뉴 이름 · DESIGN_SPEC 11.3)
    private var single = false
    /// 캘린더 빈 칸에서 열었을 때 미리 채울 날짜 · 시각 (직접 추가)
    private var prefill: TaskAddPrefill? = nil

    init(initialMode: TaskAddMode, single: Bool = false, prefill: TaskAddPrefill? = nil,
         onAdded: @escaping (TaskAddedInfo) -> Void) {
        self.onAdded = onAdded
        self.single = single
        self.prefill = prefill
        _vm = State(initialValue: TaskAddViewModel(mode: initialMode))
    }

    /// 캘린더 + 메뉴 이름 (그 방식만 열 때 시트 제목)
    static func singleTitle(_ mode: TaskAddMode) -> String {
        switch mode {
        case .ai: return "AI로 자동 Task 추가"
        case .manual: return "계획 할 일 추가"
        case .log: return "실제 한 일 추가"
        }
    }

    /// 미리보기 전용 — 서버를 부르지 않는 ViewModel 로
    init(previewModel: TaskAddViewModel) {
        self.onAdded = { _ in }
        _vm = State(initialValue: previewModel)
    }

    var body: some View {
        NavigationStack {
            // 공통 시트 모양: 손잡이 · 머리 · 좌우 24 (DESIGN_SPEC 13.2)
            VStack(spacing: 0) {
                SheetGrabber()
                header
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        if !single {
                            TaskAddModeSwitch(vm: vm)
                        }
                        content
                    }
                    .padding(.horizontal, 24)
                    .padding(.top, 20)
                    .padding(.bottom, 24)
                }
                .scrollDismissesKeyboard(.interactively)
            }
            .background(Color.bgSurface)
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(isPresented: $showDetail) {
                if let result = vm.result {
                    BrainDumpStepResult(result: result, today: vm.today, onAddMore: {
                        TA.track("task_add_result.add_more_click", ["placed_count": result.placements.count])
                        showDetail = false
                        vm.reset()
                    }, closeSheet: closeSheet)
                }
            }
        }
        .sheetChrome(detents: [.large])
        .reloadOn([.usage]) { await vm.loadQuota() }
        .reloadOn([.goals]) { await vm.loadGoals() }
        .onDisappear {
            if vm.isPreview { return }
            // 캘린더가 점선 제안으로 넘어가며 닫으면 Analytics.sheetCloseHint = "next"
            TA.plain("task_add.panel_close", [
                "add_mode": vm.mode.rawValue,
                "ai_step": vm.mode == .ai ? TA.aiStep(vm.step) : nil,
                "has_input": vm.hasInput,
                "close_method": TA.closeMethod(closeOutcome),
            ])
        }
        .onChange(of: vm.mode) { vm.childHasInput = false }
    }

    // MARK: 머리 (웹 TaskAddPanel 의 h-14 줄)

    private var header: some View {
        SheetHeader(title: single ? Self.singleTitle(vm.mode) : "Task 추가",
                    subtitle: single ? nil : "캘린더를 보면서 추가해요",
                    onClose: { dismiss() }, closeLabel: "Task 추가 닫기")
    }

    // MARK: 방식별 내용

    @ViewBuilder
    private var content: some View {
        switch vm.mode {
        case .log:
            LogDoneView(goals: vm.ongoingGoals, onInputChange: { vm.childHasInput = $0 })
                .padding(.top, 16)
        case .manual:
            TaskAddManualView(
                goals: vm.ongoingGoals,
                loading: vm.goalsLoading,
                goalsFailed: vm.goalsFailed,
                onCreated: { t in
                    let day = String((t.plannedStartAt ?? t.date ?? t.scheduledDate ?? "").prefix(10))
                    onAdded(TaskAddedInfo(taskIds: [t.taskId], date: day.isEmpty ? nil : day, pendingTaskIds: nil))
                },
                closeSheet: closeSheet,
                onInputChange: { vm.childHasInput = $0 },
                prefill: prefill
            )
            .padding(.top, 16)
        case .ai:
            TaskAddStepper(step: vm.step)
                .padding(.top, 16)
                .padding(.bottom, 16)
            aiStep
        }
    }

    @ViewBuilder
    private var aiStep: some View {
        if vm.step == 0 {
            BrainDumpStepInput(
                lines: $vm.lines,
                quota: vm.quota,
                quotaLoading: vm.quotaLoading,
                today: vm.today,
                loading: vm.parsing,
                onNext: { Task { await vm.goParse() } },
                onPro: openMembership
            )
        } else if vm.step == 1 {
            BrainDumpStepGoals(
                items: vm.items,
                tempGoals: vm.tempGoals,
                goals: vm.ongoingGoals,
                aiFailed: vm.aiFailed,
                aiLimited: vm.aiLimited,
                conflicts: vm.conflicts,
                today: vm.today,
                loading: vm.confirming,
                onChange: { items, temps in vm.applyChange(items: items, tempGoals: temps) },
                onBack: {
                    TA.track("task_add_goals.back_click", ["item_count": vm.items.count, "edited_count": vm.editedCount])
                    vm.step = 0
                },
                onConfirm: {
                    Task {
                        if let info = await vm.goConfirm() { onAdded(info) }
                    }
                }
            )
        } else if let result = vm.result {
            TaskAddPanelResult(
                result: result,
                onAddMore: {
                    TA.track("task_add_result.add_more_click", ["placed_count": result.placements.count])
                    vm.reset()
                },
                onShowDetail: detailAction(for: result)
            )
        }
    }

    /// 점선 확인(pending)이 남아 있으면 캘린더가 맡으므로 자세한 결과 버튼을 숨겨요
    private func detailAction(for result: ConfirmResponse) -> (() -> Void)? {
        if !(result.pending ?? []).isEmpty { return nil }
        return {
            let days = ((result.placements + result.movedToOtherDays).map(\.date) + result.unscheduled.compactMap(\.date)).sorted()
            let dayCount = days.first.map { KST.diffDays($0, days.last ?? $0) + 1 } ?? 1
            TA.track("task_add_result.detail_click", ["placed_count": result.placements.count, "day_count": dayCount])
            showDetail = true
        }
    }

    // MARK: 시트 닫기

    /// 하위 화면(push)에서는 dismiss 가 "뒤로 가기"가 되므로, 캘린더가 띄운 시트 상태(router.taskAdd)를 직접 비워요
    private func closeSheet() {
        closeOutcome = "next"
        if AppRouter.shared.taskAdd != nil {
            AppRouter.shared.taskAdd = nil
        } else {
            dismiss()
        }
    }

    /// 무료 플랜 한도 안내의 "Pro 알아보기" → 설정 > 멤버십 (웹은 "곧 만나볼 수 있어요" 토스트)
    private func openMembership() {
        closeSheet()
        ST.membershipEntry = "task_add"
        AppRouter.shared.openSettings(.membership)
    }
}

// MARK: - 추가 방식 고르기 (웹 role="tablist", panel 모양: 3칸)

private struct TaskAddModeOption {
    let mode: TaskAddMode
    let icon: String
    let title: String
    let desc: String
}

private let taskAddModeOptions: [TaskAddModeOption] = [
    TaskAddModeOption(mode: .ai, icon: "sparkles", title: "AI로 정리하기",
                      desc: "생각나는 일을 쏟아내면 목표에 연결하고 빈 시간에 배치해요."),
    TaskAddModeOption(mode: .manual, icon: "pencil.line", title: "직접 추가하기",
                      desc: "목표·날짜·시간을 정해서 하나씩 바로 추가해요."),
    TaskAddModeOption(mode: .log, icon: "note.text", title: "한 일 기록",
                      desc: "이미 한 일을 한 줄씩 적으면 목표에 연결해 기록으로 쌓아요."),
]

@MainActor
private struct TaskAddModeSwitch: View {
    let vm: TaskAddViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                ForEach(taskAddModeOptions, id: \.mode) { opt in
                    modeButton(opt)
                }
            }
            // 웹 패널은 설명을 마우스를 올렸을 때(title)만 보여요 — 아이폰은 고른 방식의 설명을 아래에 적어요
            if let current = taskAddModeOptions.first(where: { $0.mode == vm.mode }) {
                Text(current.desc)
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func modeButton(_ opt: TaskAddModeOption) -> some View {
        let on = vm.mode == opt.mode
        let locked = vm.modeLocked(opt.mode)
        return Button {
            guard !locked else { return }
            if opt.mode != vm.mode {
                TA.track("task_add.mode_click", [
                    "add_mode": opt.mode.rawValue, "from_mode": vm.mode.rawValue,
                    "ai_step": TA.aiStep(vm.step), "has_input": vm.hasInput,
                ])
            }
            vm.mode = opt.mode
        } label: {
            VStack(spacing: 4) {
                NSymbol(opt.icon, size: 14, weight: .semibold)
                    .foregroundStyle(on || opt.mode == .ai ? Color.actionPrimaryFg : Color.ink3)
                    .frame(width: 28, height: 28)
                    .background {
                        // AI 방식은 늘 AI 그라데이션 (AIBadge 와 같은 모양)
                        if opt.mode == .ai {
                            RoundedRectangle(cornerRadius: Radius.lg).fill(Color.aiGradient)
                        } else {
                            RoundedRectangle(cornerRadius: Radius.lg).fill(on ? Color.actionPrimary : Color.subtle)
                        }
                    }
                Text(opt.title)
                    .font(.pt(12, .bold))
                    .foregroundStyle(Color.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity)
            .padding(8)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.xl)
                    .stroke(on ? Color.ink : Color.line, lineWidth: on ? 1.5 : 1)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(locked)
        // 흐리게 잠긴 방식을 누름 — 이유 안내가 없어서 [UX] 이벤트로 봐요
        .disabledTap(locked) {
            TA.track("task_add.mode_locked_click", ["add_mode": opt.mode.rawValue, "ai_step": TA.aiStep(vm.step)])
        }
        .opacity(locked ? 0.4 : 1)
        .accessibilityAddTraits(on ? .isSelected : [])
        .accessibilityHint(locked ? "AI 정리를 마치거나 처음으로 돌아가면 바꿀 수 있어요" : opt.desc)
    }
}

// MARK: - 진행 단계 (1 Task 입력 · 2 목표 연결 · 3 확인)

private struct TaskAddStepper: View {
    let step: Int
    /// 웹 panel 은 3번째를 "확인"으로 짧게
    private let labels = ["Task 입력", "목표 연결", "확인"]

    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<3, id: \.self) { i in
                HStack(spacing: 6) {
                    ZStack {
                        Circle().fill(i < step ? Color.accentMint : (i == step ? Color.actionPrimary : Color.subtle))
                        if i < step {
                            NSymbol("checkmark", size: 10, weight: .heavy)
                                .foregroundStyle(Color.onAccentMint)
                        } else {
                            Text("\(i + 1)")
                                .font(.pt(12, .bold))
                                .foregroundStyle(i == step ? Color.actionPrimaryFg : Color.ink3)
                        }
                    }
                    .frame(width: 24, height: 24)
                    Text(labels[i])
                        .font(.pt(12, i == step ? .bold : .regular))
                        .foregroundStyle(i == step ? Color.ink : Color.ink3)
                }
                .accessibilityAddTraits(i == step ? .isSelected : [])
                if i < 2 {
                    Rectangle().fill(Color.lineStrong).frame(width: 12, height: 1)
                }
            }
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("진행 단계")
    }
}

#Preview("1단계 입력") {
    TaskAddSheet(previewModel: TaskAddSample.viewModel(step: 0))
}

#Preview("2단계 목표 연결") {
    TaskAddSheet(previewModel: TaskAddSample.viewModel(step: 1))
}

#Preview("3단계 결과") {
    TaskAddSheet(previewModel: TaskAddSample.viewModel(step: 2))
}
