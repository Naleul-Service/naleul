// Features/GoalCreation/GoalCreationStartView.swift
//
// /goal/new — 카테고리 · 첫 문장 · 꼭 할 일 입력 + 이어서 만들기.
// 웹 원본: features/goal-creation/ui/StartGoalView.tsx · app/(flow)/goal/new/page.tsx

import SwiftUI
import Observation

@MainActor
@Observable
final class GoalCreationStartViewModel {
    var text = ""
    var kind: GoalKindValue?
    /// 이 목표를 위해 꼭 하고 싶은 일 (예: 다이어트 → 헬스 · 식단 기록)
    var mustDo: [String] = []
    /// 입력창에 적어 두기만 한 일도 함께 보내요 (하나만 할 거면 "+ 추가"를 안 눌러도 돼요)
    var mustDoDraft = ""
    var usage: AiUsage?
    /// 구체화할 임시 목표
    var source: GoalCategory?
    var starting = false
    /// 이동 중에도 버튼이 다시 눌리지 않게 성공 후에도 막아둬요
    var started = false

    var kindReady: Bool { GoalKind.isComplete(kind) }
    var goalText: String { GoalCreationForm.trim(text) }
    var sessionLeft: UsageItem? { usage?.item("GOAL_SESSION") }
    /// 하루 · 주 · 월 중 가장 먼저 바닥나는 초안 한도 (2026-10)
    var draftLeft: UsageItem? { usage?.tightest("GOAL_DRAFT") }
    /// 주간 한도 (Free 주 2개 · Pro 주 10개, 2026-10)
    var weekLeft: UsageItem? { usage?.item("GOAL_SESSION_WEEK") }
    /// 월간 한도 (Free 월 8개 · Pro 월 20개, 2026-10)
    var monthLeft: UsageItem? { usage?.item("GOAL_SESSION_MONTH") }
    var outOfMonth: Bool { monthLeft?.remaining == 0 }
    var outOfWeek: Bool { !outOfMonth && weekLeft?.remaining == 0 }
    /// 남은 횟수를 다 쓰면 시작 버튼을 막고 미리 알려줘요
    var outOfQuota: Bool { sessionLeft?.remaining == 0 || draftLeft?.remaining == 0 || outOfWeek || outOfMonth }
    var busy: Bool { starting || started }

    // MARK: 이벤트 값

    /// 시작 화면을 다 불러왔는지 (pageview 는 남은 횟수 · 만들던 목표를 다 알고 나서 한 번)
    var usageLoaded = false
    var hasResume: Bool?
    var pageviewSent = false

    /// AI 한도 상태 (ok / out_daily / out_weekly / out_draft)
    var quotaState: String {
        if outOfMonth { return "out_monthly" }
        if outOfWeek { return "out_weekly" }
        if sessionLeft?.remaining == 0 { return "out_daily" }
        if draftLeft?.remaining == 0 { return "out_draft" }
        return "ok"
    }
    /// 고른 · 적은 게 하나라도 있는지 (닫으면 사라지는 입력)
    var hasInput: Bool { kind != nil || !goalText.isEmpty || !mustDo.isEmpty || !GoalCreationForm.trim(mustDoDraft).isEmpty }
    /// 시작 버튼이 막힌 이유 (no_kind / no_label / no_text / quota) — 막히지 않았거나 요청 중이면 nil
    func disabledReason(needsText: Bool) -> String? {
        if busy { return nil }
        if kind == nil { return "no_kind" }
        if !kindReady { return "no_label" }
        if needsText && goalText.isEmpty { return "no_text" }
        if outOfQuota { return "quota" }
        return nil
    }

    /// 고른 2단계에 맞는 예시 (미선택이면 기본 예시, "기타"는 사용자가 적은 이름으로)
    var examples: [String] {
        guard let kind else { return GoalCreationConst.exampleGoals }
        let sub = GoalKind.subOption(kind.goalSubType)
        return sub.custom ? [] : sub.examples
    }

    var placeholder: String {
        if !kindReady { return "먼저 카테고리를 골라 주세요" }
        if let kind, GoalKind.subOption(kind.goalSubType).custom {
            return "예) \(GoalCreationForm.trim(kind.goalKindLabel ?? "")) 주 3회 꾸준히 하기"
        }
        return "예) \(examples.first ?? "")"
    }

    var suggestions: [String] {
        guard let kind else { return [] }
        return GoalKind.mustDoSuggestions[kind.goalSubType] ?? []
    }

    func loadUsage() async {
        if let u = try? await UsageAPI.usage() { usage = u }
        usageLoaded = true
    }

    /// 예전에 AI 없이 만든 목표를 AI 로 다시 설계하는지 (임시 목표가 아님, 2026-10)
    var isLegacySource: Bool { source.map { !$0.isTemporary } ?? false }
    var refineSource: String? { source == nil ? nil : (isLegacySource ? "legacy" : "temporary") }
    private var prefilled = false

    func loadSource(_ id: Int?) async {
        guard let id else { return }
        if let goals = try? await GoalAPI.goals() {
            source = goals.first { $0.goalCategoryId == id }
        }
        // 이름 · 카테고리를 미리 채워 둬요 (꼭 해야 할 일만 비움) — 바로 시작하거나 고쳐서 시작 (웹 StartGoalView 와 같아요)
        //  카테고리: 목표에 저장된 값 → 없으면 이름 키워드로 짐작 (AI 를 부르지 않아요)
        if let source, !prefilled {
            prefilled = true
            if goalText.isEmpty { text = String(source.goalCategoryName.prefix(100)) }
            if kind == nil { kind = GoalKind.of(source) }
            GCTracker.shared.track("goal_new.prefill", ["refine_source": refineSource, "has_saved_kind": source.goalSubType != nil])
        }
    }

    /// 시작 성공 → 세션 id. mode: typed / ask_ai / refine_name
    func start(initialMessage: String?, sourceGoalId: Int?, mode: String) async -> Int? {
        guard !busy else { return nil }
        let mustDoItems = GoalCreationMustDo.withDraft(mustDo, mustDoDraft)
        GCTracker.shared.track(GCEvent.startClick, [
            "start_mode": mode,
            "goal_type": kind?.goalType.rawValue,
            "goal_sub_type": kind?.goalSubType.rawValue,
            "goal_text_length": initialMessage?.count ?? 0,
            "must_do_count": mustDoItems.count,
            "has_unadded_draft": !GoalCreationForm.trim(mustDoDraft).isEmpty,
            "is_refine": sourceGoalId != nil,
            "refine_source": refineSource,
        ])
        starting = true
        defer { starting = false }
        do {
            let turn = try await GoalCreationAPI.start(
                initialMessage: initialMessage, sourceGoalId: sourceGoalId, kind: kind,
                mustDoItems: mustDoItems)
            started = true
            GCTracker.shared.track(GCEvent.startResult, ["result": "success", "session_id": turn.sessionId, "start_mode": mode])
            // 오늘 남은 횟수 · 이어서 만들기 카드 갱신 (웹 refreshUsage)
            Invalidation.shared.invalidate(.usage, .goalCreation)
            return turn.sessionId
        } catch is CancellationError {
            return nil
        } catch {
            GCTracker.shared.track(GCEvent.startResult, [
                "result": "fail", "error_status": gcErrorStatus(error), "start_mode": mode,
            ])
            Toast.error(errorMessage(error, fallback: "시작하지 못했어요. 다시 시도해 주세요."))
            return nil
        }
    }
}

struct GoalCreationStartView: View {
    let sourceGoalId: Int?
    @Environment(GoalCreationNavigator.self) private var nav
    @Environment(AppRouter.self) private var router
    @State private var vm: GoalCreationStartViewModel
    @FocusState private var mustDoFocused: Bool

    init(sourceGoalId: Int?) {
        self.sourceGoalId = sourceGoalId
        _vm = State(initialValue: GoalCreationStartViewModel())
    }

    /// 미리보기용
    init(preview vm: GoalCreationStartViewModel, sourceGoalId: Int? = nil) {
        self.sourceGoalId = sourceGoalId
        _vm = State(initialValue: vm)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                intro
                if sourceGoalId == nil { recordGoalLink.padding(.top, 24) }
                if sourceGoalId != nil { sourceNotice.padding(.top, 24) }
                kindSection.padding(.top, 32)
                goalSection.padding(.top, 32)
                mustDoSection.padding(.top, 32)
                startButton.padding(.top, 32)
                askFirstButton.padding(.top, 24)
                GoalCreationResumeCard(onOpen: { id in nav.go(.resume(id)) },
                                       onLoaded: { has in vm.hasResume = has; trackPageviewIfReady() })
                    .padding(.top, 40)
            }
            .padding(.horizontal, 16)
            .padding(.top, 32)
            .padding(.bottom, 40)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Color.surface.ignoresSafeArea())
        .goalCreationHeader(onClose: {
            GCTracker.shared.track(GCEvent.newClose, ["has_input": vm.hasInput])
            nav.close()
        })
        .reloadOn([.usage]) {
            await vm.loadUsage()
            trackPageviewIfReady()
        }
        .reloadOn([.goals], id: sourceGoalId ?? 0) { await vm.loadSource(sourceGoalId) }
    }

    // MARK: 머리말

    private var intro: some View {
        VStack(alignment: .leading, spacing: 0) {
            Badge("AI 목표 설계", tone: .ai, icon: "sparkles")
            Text("어떤 목표를 이루고 싶으세요?")
                .font(.pt(20, .semibold)).foregroundStyle(Color.ink)
                .padding(.top, 16)
            Text("카테고리를 고르면 AI가 그 분야에 맞는 루틴 위주로, 꼭 필요한 것만 담아 계획을 설계해 드려요.")
                .font(.pt(15)).foregroundStyle(Color.ink3).lineSpacing(3)
                .padding(.top, 8)
        }
    }

    /// 회사 업무처럼 수치·마감이 없는 일은 AI 설계가 오히려 뜬구름이 돼요 → 업무형으로 안내
    private var recordGoalLink: some View {
        Button {
            GCTracker.shared.track(GCEvent.newRecordMode, ["has_input": vm.hasInput])
            router.closeGoalCreation()
            router.openGoalAdd(mode: .record)
        } label: {
            HStack(spacing: 12) {
                NSymbol("book.closed", size: 15, weight: .semibold)
                    .foregroundStyle(Color.textBrand)
                    .frame(width: 36, height: 36)
                    .background(Color.actionSecondaryBg, in: RoundedRectangle(cornerRadius: Radius.xl))
                VStack(alignment: .leading, spacing: 2) {
                    Text("회사 업무 · 취업 준비 · 동아리 활동인가요?")
                        .font(.pt(14, .semibold)).foregroundStyle(Color.ink)
                    Text("AI 설계 없이 바로 만들고, 완료한 Task로 업무 일지를 쌓는 업무형 목표가 더 잘 맞아요.")
                        .font(.pt(13)).foregroundStyle(Color.ink3)
                        .multilineTextAlignment(.leading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                NSymbol("arrow.right", size: 14).foregroundStyle(Color.ink3)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xxl))
            .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.line, lineWidth: 1))
            .contentShape(RoundedRectangle(cornerRadius: Radius.xxl))
        }
        .buttonStyle(.plain)
    }

    private var sourceNotice: some View {
        let name = (vm.source?.emoji.map { "\($0) " } ?? "") + (vm.source?.goalCategoryName ?? "")
        let lead = vm.isLegacySource
            ? (Text("예전에 만든 목표 ") + Text(name).bold()
                + Text("을(를) AI와 다시 설계해요. 확정하면 이 목표에 수치 목표 · 루틴이 더해지고, 지금까지의 Task · 루틴 · 기록은 그대로 남아요."))
            : (Text("임시 목표 ") + Text(name).bold()
                + Text("을(를) 구체화해요. 목표가 확정되면 거기에 있던 Task와 기록이 새 목표로 옮겨져요."))
        return VStack(alignment: .leading, spacing: 6) {
            lead
                .font(.pt(14))
                .foregroundStyle(Color.warningInkStrong)
            // 이름 · 카테고리는 아래에 미리 채워 뒀어요 — 확인하고 시작만 누르면 돼요 (고쳐도 돼요)
            Text("이름과 카테고리를 미리 채워 뒀어요. 확인하고 시작만 누르세요.")
                .font(.pt(13))
                .foregroundStyle(Color.warningInkStrong.opacity(0.8))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.warningSoft, in: RoundedRectangle(cornerRadius: Radius.xxl))
    }

    // MARK: 1 · 2 · 3

    private func stepTitle(_ n: Int, _ title: String) -> some View {
        (Text("\(n)  ").foregroundColor(Color.textBrand) + Text(title))
            .font(.pt(14, .semibold))
            .foregroundStyle(Color.ink)
    }

    private var kindSection: some View {
        @Bindable var vm = vm
        return VStack(alignment: .leading, spacing: 12) {
            stepTitle(1, "카테고리")
            GoalKindPicker(value: $vm.kind, disabled: vm.busy, flat: true,
                           onTypePick: { t in GCTracker.shared.track(GCEvent.kindTypeSelect, ["goal_type": t.rawValue]) },
                           onSubPick: { t, s in
                               GCTracker.shared.track(GCEvent.kindSubSelect, ["goal_type": t.rawValue, "goal_sub_type": s.rawValue])
                           })
        }
    }

    private var goalSection: some View {
        // $vm 바인딩은 안 써요 — 아래 Binding/버튼 클로저가 지역 var(@Bindable) 대신 self.vm 을 잡게 해요
        VStack(alignment: .leading, spacing: 12) {
            stepTitle(2, "이루고 싶은 목표를 작성해 주세요")
            TextField(vm.placeholder, text: Binding(
                get: { vm.text },
                set: { vm.text = String($0.prefix(100)) }   // 웹 maxLength 100
            ))
            .submitLabel(.next)
            // 완료(엔터)는 바로 시작하지 않고 다음 질문(꼭 해야 할 일)으로 넘어가요
            .onSubmit { mustDoFocused = true }
            .disabled(vm.busy || !vm.kindReady)
            .accessibilityLabel("이루고 싶은 목표")
            .inputStyle(minHeight: 48)
            if !vm.examples.isEmpty {
                FlowLayout(spacing: 8) {
                    ForEach(Array(vm.examples.enumerated()), id: \.element) { i, goal in
                        Chip(goal, size: .sm) {
                            GCTracker.shared.track(GCEvent.exampleClick, [
                                "goal_sub_type": vm.kind?.goalSubType.rawValue, "example_index": i,
                            ])
                            vm.text = goal
                        }
                    }
                }
                .disabled(vm.busy || !vm.kindReady)
            }
        }
        .opacity(vm.kindReady ? 1 : 0.4)
        .allowsHitTesting(vm.kindReady)
    }

    private var mustDoTitle: Text {
        let goal = vm.goalText
        let head = Text("3  ").foregroundColor(Color.textBrand)
        let optional = Text(" (선택)").font(.pt(14)).foregroundColor(Color.ink3)
        if goal.isEmpty {
            return head + Text("이 목표를 이루기 위해 꼭 해야 할 일들이 있나요?") + optional
        }
        let short = goal.count > 24 ? "\(goal.prefix(24))…" : goal
        return head + Text("“\(short)”").foregroundColor(Color.textBrand) + Text(" 목표를 이루기 위해 꼭 해야 할 일들이 있나요?") + optional
    }

    private var mustDoSection: some View {
        @Bindable var vm = vm
        return VStack(alignment: .leading, spacing: 0) {
            mustDoTitle.font(.pt(14, .semibold)).foregroundStyle(Color.ink)
            Text("하나면 적기만 하면 되고, 여러 개면 ‘+ 추가’로 더 적어 주세요. 고른 일은 계획에 꼭 넣어 드려요.")
                .font(.pt(13)).foregroundStyle(Color.ink3)
                .padding(.top, 4).padding(.bottom, 12)
            GoalCreationMustDoPicker(value: $vm.mustDo, draft: $vm.mustDoDraft, suggestions: vm.suggestions,
                                     disabled: vm.busy || !vm.kindReady, focus: $mustDoFocused, surface: "goal_new")
        }
        .opacity(vm.kindReady ? 1 : 0.4)
        .allowsHitTesting(vm.kindReady)
    }

    // MARK: 버튼

    private var startButton: some View {
        let disabled = vm.busy || !vm.kindReady || vm.goalText.isEmpty || vm.outOfQuota
        return VStack(spacing: 8) {
            NButton("AI와 목표 설계 시작하기", variant: .ai, size: .lg, icon: "sparkles",
                    loading: vm.starting, fullWidth: true) { submit() }
                .disabled(disabled)
                .disabledTap(disabled) { trackDisabled(needsText: true, mode: "typed") }
            UsageLine(items: [vm.usage?.tightest("GOAL_SESSION") ?? vm.sessionLeft, vm.draftLeft])
                .frame(maxWidth: .infinity)
                .multilineTextAlignment(.center)
            if vm.outOfQuota {
                Text(vm.outOfMonth
                     ? "이번 달 AI 목표 만들기를 모두 썼어요. 다음 달 1일 0시에 다시 채워져요 — 지금은 직접 만들기를 쓸 수 있어요. Pro는 주 10개 · 월 20개까지 만들 수 있어요."
                     : vm.outOfWeek
                     ? "이번 주 AI 목표 만들기를 모두 썼어요. 월요일 0시에 다시 채워져요 — 지금은 직접 만들기를 쓸 수 있어요. Pro는 주 10개 · 월 20개까지 만들 수 있어요."
                     : "오늘 AI 목표 만들기 횟수를 모두 썼어요. 내일 0시에 다시 채워져요 — 지금은 직접 만들기를 쓸 수 있어요. Pro는 주 10개 · 월 20개까지 만들 수 있어요.")
                    .font(.pt(13, .semibold)).foregroundStyle(Color.danger)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private var askFirstButton: some View {
        Button { startWithoutText(mode: "ask_ai") } label: {
            HStack(spacing: 6) {
                Image(systemName: "bubble.left").font(.system(size: 14))
                Text("아직 정하지 못했어요. AI가 먼저 물어봐 주세요")
            }
            .font(.pt(14, .medium))
            .foregroundStyle(Color.ink3)
        }
        .buttonStyle(.plain)
        .disabled(vm.busy || !vm.kindReady || vm.outOfQuota)
        .opacity(vm.busy || !vm.kindReady || vm.outOfQuota ? 0.4 : 1)
        .disabledTap(vm.busy || !vm.kindReady || vm.outOfQuota) { trackDisabled(needsText: false, mode: "ask_ai") }
    }

    // MARK: 동작

    @MainActor
    private func submit() {
        let goal = vm.goalText
        guard !goal.isEmpty, vm.kindReady, !vm.busy, !vm.outOfQuota else { return }
        Task {
            if let id = await vm.start(initialMessage: goal, sourceGoalId: sourceGoalId, mode: "typed") { nav.go(.chat(id)) }
        }
    }

    /// 첫 문장 없이 시작 (임시 목표 이름 그대로 refine_name / AI 가 먼저 물어보기 ask_ai)
    @MainActor
    private func startWithoutText(mode: String) {
        Task {
            if let id = await vm.start(initialMessage: nil, sourceGoalId: sourceGoalId, mode: mode) { nav.go(.chat(id)) }
        }
    }

    // MARK: 이벤트

    /// 남은 횟수와 만들던 목표를 둘 다 안 뒤 한 번
    @MainActor
    private func trackPageviewIfReady() {
        guard !vm.pageviewSent, vm.usageLoaded, vm.hasResume != nil else { return }
        vm.pageviewSent = true
        GCTracker.shared.track(GCEvent.newPageview, [
            "is_refine": sourceGoalId != nil,
            "refine_source": vm.refineSource,
            "has_resume": vm.hasResume,
            "quota_state": vm.quotaState,
            "remaining_sessions": vm.sessionLeft?.remaining,
        ])
        if vm.outOfQuota { GCTracker.shared.track(GCEvent.quotaBlockView, ["quota_state": vm.quotaState]) }
    }

    @MainActor
    private func trackDisabled(needsText: Bool, mode: String) {
        guard let reason = vm.disabledReason(needsText: needsText) else { return }
        GCTracker.shared.track(GCEvent.startDisabledClick, ["disabled_reason": reason, "start_mode": mode])
    }
}

#Preview("시작") {
    let vm = GoalCreationStartViewModel()
    vm.kind = GoalKindValue(goalType: .HEALTH, goalSubType: .DIET, goalKindLabel: nil)
    vm.text = "3개월에 8kg 빼기"
    vm.mustDo = ["헬스"]
    vm.usage = AiUsage(pro: false, resetsAt: "2026-11-19T00:00:00", items: [
        UsageItem(key: "GOAL_SESSION", label: "AI 목표 만들기", description: "", used: 1, limit: 3, remaining: 2),
        UsageItem(key: "GOAL_DRAFT", label: "계획 초안", description: "", used: 1, limit: 5, remaining: 4),
    ], session: nil)
    return NavigationStack {
        GoalCreationStartView(preview: vm)
    }
    .environment(GoalCreationNavigator(route: .start))
    .environment(AppRouter.shared)
}

#Preview("임시 목표 구체화") {
    NavigationStack {
        GoalCreationStartView(preview: GoalCreationStartViewModel(), sourceGoalId: 3)
    }
    .environment(GoalCreationNavigator(route: .start))
    .environment(AppRouter.shared)
}
