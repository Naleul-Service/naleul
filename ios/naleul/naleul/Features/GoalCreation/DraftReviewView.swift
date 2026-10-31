// Features/GoalCreation/DraftReviewView.swift
//
// G-4 초안 검토 · 수정 · 확정 (/goal/new/{sessionId}/draft).
// 웹 원본: features/goal-creation/ui/draft/DraftScreen.tsx · DraftReviewView.tsx
//
// 아이폰 배치: 웹의 넓은 2단(목표+수치 / 루틴+일회성)을 세로로 쌓고, 확정 바는 화면 아래에 붙여요.

import SwiftUI
import Observation

// MARK: - 초안 불러오기 (웹 DraftLoader)

struct DraftLoaderView: View {
    let session: GoalCreationSessionDetail
    let reload: () async -> Void

    @State private var draft: GoalCreationDraftResponse?
    @State private var loadError: String?

    /// private @State 때문에 자동 init 이 private 이 돼요 → GoalCreationFlowView(다른 파일)에서 부르려고 직접 둬요
    init(session: GoalCreationSessionDetail, reload: @escaping () async -> Void) {
        self.session = session
        self.reload = reload
    }

    var body: some View {
        if let d = draft, d.plan != nil {
            // 새 초안이 오면 편집 상태를 새로 시작
            DraftReviewView(session: session, draft: d, reload: reload)
                .id(d.draftId)
        } else {
            GoalCreationFlowShell {
                if let loadError {
                    GoalCreationCenterMessage(title: "초안을 불러오지 못했어요", description: loadError) {
                        NButton("다시 시도") {
                            GCTracker.shared.track(GCEvent.ctaClick, ["session_id": session.sessionId, "cta": "retry", "session_status": "DRAFT_ERROR"])
                            Task { await load() }
                        }
                    }
                } else {
                    ProgressView().tint(Color.ink3)
                }
            }
            .task(id: session.latestDraft?.draftId ?? 0) { await load() }
        }
    }

    @MainActor
    private func load() async {
        // 초안 내용은 바뀌지 않아요 (새 초안은 새 draftId) — 한 번만 받아요
        guard let draftId = session.latestDraft?.draftId else { return }
        loadError = nil
        do {
            draft = try await GoalCreationAPI.getDraft(session.sessionId, draftId)
        } catch is CancellationError {
            return
        } catch {
            GCTracker.shared.track(GCEvent.sessionError, [
                "session_id": session.sessionId, "error_status": gcErrorStatus(error), "from_screen": "draft",
            ])
            loadError = errorMessage(error)
        }
    }
}

// MARK: - 상태

/// 문제 항목으로 스크롤 요청 (같은 키라도 다시 스크롤되게 UUID)
struct DraftScrollRequest: Equatable {
    let key: String
    let token = UUID()
}

@MainActor
@Observable
final class DraftReviewModel {
    let session: GoalCreationSessionDetail
    let draft: GoalCreationDraftResponse
    private let originalPlan: DraftGoalPlan

    var plan: DraftGoalPlan
    var colorId: Int?
    var serverIssues: [DraftPlanIssue]?
    var showClientIssues = false
    var editTarget: DraftEditTarget?

    var confirming = false
    var regenOpen = false
    var feedback = ""
    var regenerating = false
    var styleOpen = false
    var pendingStyle: PlanningStyle?
    var styleConfirmOpen = false
    var intensityOpen = false
    var pendingIntensity: GoalCreationPlanIntensity?
    var intensityConfirmOpen = false
    var cancelOpen = false
    var cancelling = false
    var quickFix = ""
    /// 세부 목표·마일스톤은 기본으로 접어 둬요 (트리 구조를 몰라도 루틴만 보고 고칠 수 있게)
    var structureOpen = false
    var scrollRequest: DraftScrollRequest?

    var usage: AiUsage?
    var colors: [UserColor] = []

    static let feedbackMax = 200
    /// 말로 고치기 예시
    static let quickFixExamples = ["운동은 주 2회로 줄여줘", "루틴을 저녁 시간으로 옮겨줘", "하나만 남기고 더 가볍게"]

    init(session: GoalCreationSessionDetail, draft: GoalCreationDraftResponse) {
        self.session = session
        self.draft = draft
        let p = draft.plan ?? GoalCreationSamples.plan
        self.originalPlan = p
        self.plan = p
    }

    var sessionId: Int { session.sessionId }
    var dirty: Bool { plan != originalPlan }
    var clientIssues: [DraftPlanIssue] { DraftPlanUtils.validatePlan(plan) }
    var shownIssues: [DraftPlanIssue] { (serverIssues ?? []) + (showClientIssues ? clientIssues : []) }
    var issueMap: DraftIssueMap { DraftPlanUtils.groupIssues(shownIssues) }
    var routineCount: Int { plan.tasks.filter(\.isRoutine).count }
    var oneTimeCount: Int { plan.tasks.count - routineCount }
    var busy: Bool { confirming || regenerating || cancelling }
    var currentIntensity: GoalCreationPlanIntensity { plan.fit?.intensity ?? .STEADY }

    /// 세부 구조 안에 고칠 곳이 있으면 접혀 있지 않게
    var structureHasIssue: Bool {
        issueMap.keys.contains { $0.hasPrefix("subGoals") || $0.hasPrefix("milestones") }
    }

    // 계획 다시 만들기 남은 횟수: 이 대화 한도와 오늘 한도 중 작은 쪽
    var regenLeft: UsageItem? { usage?.session }
    /// 하루 · 주 · 월 중 가장 먼저 바닥나는 초안 한도 (2026-10)
    var draftLeft: UsageItem? { usage?.tightest("GOAL_DRAFT") }
    var regenRemaining: Int? {
        let values = [regenLeft?.remaining, draftLeft?.remaining].compactMap { $0 }
        return values.min()
    }
    var noRegen: Bool { regenRemaining == 0 }

    func loadExtras() async {
        if let u = try? await UsageAPI.usage(sessionId: sessionId) { usage = u }
        if let c = try? await ColorAPI.userColors() { colors = c }
    }

    // MARK: 편집

    private func update(_ next: DraftGoalPlan) {
        // 마일스톤 날짜·수치, 할 일 마감을 자동으로 맞춰요 → 하나 고쳤다고 다른 곳에 오류가 생기지 않게
        plan = DraftPlanUtils.normalizePlan(next)
        // 서버 결과는 이전 내용 기준이라 지워요 (index 가 바뀌었을 수 있음)
        serverIssues = nil
    }

    func applyEdit(_ r: DraftEditResult) {
        var p = plan
        switch r {
        case let .goal(title, emoji, startDate, endDate, metricValues):
            p.goal.title = title
            p.goal.emoji = emoji
            p.goal.startDate = startDate
            p.goal.endDate = endDate
            if var m = p.goal.metric, let v = metricValues {
                m.startValue = v.start
                m.targetValue = v.target
                p.goal.metric = m
            }
        case let .subGoal(index, item):
            if let index, p.subGoals.indices.contains(index) { p.subGoals[index] = item } else { p.subGoals.append(item) }
        case let .milestone(index, item):
            if let index, p.milestones.indices.contains(index) { p.milestones[index] = item } else { p.milestones.append(item) }
            // 마일스톤은 항상 날짜 순서로
            p.milestones.sort { $0.dueDate < $1.dueDate }
        case let .task(index, item):
            if let index, p.tasks.indices.contains(index) { p.tasks[index] = item } else { p.tasks.append(item) }
        }
        update(p)
        if let t = editTarget {
            GCTracker.shared.track(GCEvent.itemEditSave, ["session_id": sessionId, "item_kind": t.analyticsKind, "is_new": t.isNew])
        }
        GCTracker.shared.sheetClose = "saved"
        editTarget = nil
    }

    /// 지울 수 없는 이유 (최소 개수)
    func deleteBlockReason(_ t: DraftEditTarget?) -> String? {
        guard let t else { return nil }
        let L = DraftPlanUtils.Limits.self
        switch t {
        case .goal:
            return nil
        case .subGoal:
            return plan.subGoals.count <= L.subGoals.min ? "세부 목표는 최소 \(L.subGoals.min)개가 필요해요." : nil
        case .milestone:
            return plan.milestones.count <= L.milestones.min ? "마일스톤은 최소 \(L.milestones.min)개가 필요해요." : nil
        case .task(_, let type):
            if routineCount + oneTimeCount <= 1 { return "할 일은 최소 1개가 필요해요." }
            if type == .ROUTINE && routineCount <= L.routines.min { return "루틴은 최소 \(L.routines.min)개가 필요해요." }
            if type == .ONE_TIME && oneTimeCount <= L.oneTimes.min { return "할 일은 최소 \(L.oneTimes.min)개가 필요해요." }
            return nil
        }
    }

    func deleteTarget() {
        guard let t = editTarget, let index = t.index else { return }
        var p = plan
        switch t {
        case .goal:
            return
        case .subGoal:
            guard p.subGoals.indices.contains(index) else { return }
            let removed = p.subGoals.remove(at: index)
            // 지운 세부 목표에 연결된 Task 는 첫 번째 세부 목표로 옮겨요
            let firstId = p.subGoals.first?.tempId
            for i in p.tasks.indices where p.tasks[i].subGoalTempId == removed.tempId {
                p.tasks[i].subGoalTempId = firstId
            }
        case .milestone:
            guard p.milestones.indices.contains(index) else { return }
            let removed = p.milestones.remove(at: index)
            for i in p.tasks.indices where p.tasks[i].milestoneTempId == removed.tempId {
                p.tasks[i].milestoneTempId = nil
            }
        case .task:
            guard p.tasks.indices.contains(index) else { return }
            p.tasks.remove(at: index)
        }
        update(p)
        GCTracker.shared.sheetClose = "deleted"
        editTarget = nil
        Toast.show("삭제했어요.")
    }

    // MARK: 스크롤

    func scrollToIssue(_ issues: [DraftPlanIssue]) {
        guard let first = issues.first else { return }
        scrollRequest = DraftScrollRequest(key: DraftPlanUtils.itemKeyOf(first.path))
    }

    // MARK: 다시 생성 / 성향 · 강도 변경

    /// 성공하면 이동할 단계
    /// source: regen / quick_fix / style_change / intensity_change (regen_result · 다음 생성 중 단계의 generate_source)
    func regenerate(_ body: GoalCreationDraftRequestBody, source: String) async -> GoalCreationRoute? {
        regenerating = true
        do {
            let res = try await GoalCreationAPI.requestDraft(sessionId, body)
            GCTracker.shared.track(GCEvent.regenResult, [
                "session_id": sessionId, "generate_source": source, "result": "success", "draft_id": res.draftId,
            ])
            Invalidation.shared.invalidate(.usage)
            GCTracker.shared.setGenerateSource(source)
            return .generating(sessionId, draftId: res.draftId)
        } catch {
            regenerating = false
            GCTracker.shared.track(GCEvent.regenResult, [
                "session_id": sessionId, "generate_source": source, "result": "fail", "error_status": gcErrorStatus(error),
            ])
            if let e = error as? APIError, e.httpStatus == 409 {
                GCTracker.shared.setGenerateSource(source)
                return .generating(sessionId, draftId: nil)
            }
            Toast.error(errorMessage(error, fallback: "다시 만들지 못했어요."))
            return nil
        }
    }

    private var userStyle: PlanningStyle? {
        GoalCreationStyleMemory.wasSelectedByUser(sessionId) ? plan.goal.planningStyle : nil
    }

    func submitFeedback() async -> GoalCreationRoute? {
        let text = GoalCreationForm.trim(feedback)
        return await regenerate(GoalCreationDraftRequestBody(feedback: text.isEmpty ? nil : text, planningStyle: userStyle), source: "regen")
    }

    /// 말로 고치기 — 화면에서 고친 지금 초안(basePlan)을 기준으로 이 문장에 해당하는 부분만 AI 가 바꿔요.
    /// "다시 생성"과 달리 직접 고친 내용이 사라지지 않아요.
    func submitQuickFix() async -> GoalCreationRoute? {
        let text = GoalCreationForm.trim(quickFix)
        if text.isEmpty || busy { return nil }
        return await regenerate(GoalCreationDraftRequestBody(feedback: text, planningStyle: userStyle, basePlan: plan), source: "quick_fix")
    }

    func changeStyle() async -> GoalCreationRoute? {
        guard let next = pendingStyle else { return nil }
        GoalCreationStyleMemory.markSelectedByUser(sessionId)
        pendingStyle = nil
        return await regenerate(GoalCreationDraftRequestBody(planningStyle: next), source: "style_change")
    }

    /// 강도 변경 — 서버가 이 사용자 기준 상한을 다시 계산해서 처음부터 다시 만들어요
    func changeIntensity() async -> GoalCreationRoute? {
        guard let next = pendingIntensity else { return nil }
        let style = userStyle
        pendingIntensity = nil
        return await regenerate(GoalCreationDraftRequestBody(planningStyle: style, intensity: next), source: "intensity_change")
    }

    // MARK: 확정

    enum ConfirmOutcome {
        case created(Int)
        /// 이미 확정된 세션 (다른 기기 / 두 번 누름)
        case alreadyConfirmed
        /// 서버 상태가 바뀜 → 세션 다시 불러오기
        case resync
        case stay
    }

    func confirm() async -> ConfirmOutcome {
        if busy { return .stay }
        let ci = clientIssues
        if !ci.isEmpty {
            GCTracker.shared.track(GCEvent.confirmBlocked, ["session_id": sessionId, "issue_count": ci.count])
            showClientIssues = true
            serverIssues = nil
            Toast.error("수정이 필요한 항목이 있어요.")
            scrollToIssue(ci)
            return .stay
        }

        confirming = true
        do {
            let r = try await GoalCreationAPI.confirm(sessionId, GoalCreationConfirmRequest(
                draftId: draft.draftId, plan: plan,
                planningStyleSelectedByUser: GoalCreationStyleMemory.wasSelectedByUser(sessionId),
                colorId: colorId))
            // 목표 목록 · 캘린더(루틴이 TimeBlock 에 배치돼요) · 이어서 만들기 카드 갱신
            GCTracker.shared.track(GCEvent.confirmResult, [
                "session_id": sessionId, "draft_id": draft.draftId, "result": "success", "goal_category_id": r.goalId,
            ])
            Invalidation.shared.invalidate(.goals, .timetable, .goalCreation)
            Toast.success("목표가 만들어졌어요.")
            return .created(r.goalId)
        } catch {
            confirming = false
            let violations422 = (error as? APIError).map { $0.httpStatus == 422 ? $0.violations.count : 0 } ?? 0
            GCTracker.shared.track(GCEvent.confirmResult, [
                "session_id": sessionId, "draft_id": draft.draftId, "result": "fail",
                "error_status": gcErrorStatus(error), "issue_count": violations422 > 0 ? violations422 : nil,
            ])
            guard let e = error as? APIError else {
                Toast.error("목표를 만들지 못했어요. 다시 시도해 주세요.")
                return .stay
            }
            let message = e.errorDescription ?? "목표를 만들지 못했어요. 다시 시도해 주세요."
            let violations = e.violations
            if e.httpStatus == 422 && !violations.isEmpty {
                let list = violations.map { DraftPlanIssue(ruleId: $0.ruleId, path: $0.path, message: $0.message) }
                serverIssues = list
                Toast.error(message)
                scrollToIssue(list)
                return .stay
            }
            if e.httpStatus == 409 {
                // 이미 확정됐거나 더 새로운 초안이 있음 → 서버 상태 다시 확인
                Toast.show(message)
                let latest = try? await GoalCreationAPI.getSession(sessionId)
                if latest?.status == .CONFIRMED { return .alreadyConfirmed }
                return .resync
            }
            // 403(무료 플랜 목표 개수 초과) / 기타
            Toast.error(message)
            return .stay
        }
    }

    func cancelSession() async -> Bool {
        cancelling = true
        do {
            try await GoalCreationAPI.cancel(sessionId)
            Invalidation.shared.invalidate(.goalCreation)
            Toast.show("목표 만들기를 그만뒀어요.")
            return true
        } catch {
            cancelling = false
            Toast.error(errorMessage(error, fallback: "취소하지 못했어요."))
            return false
        }
    }
}

// MARK: - 화면

struct DraftReviewView: View {
    @Environment(GoalCreationNavigator.self) private var nav
    @State private var vm: DraftReviewModel
    private let reload: () async -> Void
    private let isPreview: Bool

    init(session: GoalCreationSessionDetail, draft: GoalCreationDraftResponse, reload: @escaping () async -> Void) {
        _vm = State(initialValue: DraftReviewModel(session: session, draft: draft))
        self.reload = reload
        self.isPreview = false
    }

    /// 미리보기용 (사용량 · 색상을 불러오지 않아요)
    init(preview vm: DraftReviewModel) {
        _vm = State(initialValue: vm)
        self.reload = {}
        self.isPreview = true
    }

    var body: some View {
        mainContent
            .background(Color.canvas.ignoresSafeArea())
            .safeAreaInset(edge: .bottom) { confirmBar }
            .goalCreationHeader(onClose: {
                gc.track(GCEvent.draftClose, ["session_id": vm.sessionId, "is_dirty": vm.dirty])
                nav.close()
            })
            .task {
                await loadExtras()
                trackPageview()
            }
            .sheet(item: Binding(get: { vm.editTarget }, set: { vm.editTarget = $0 }),
                   onDismiss: { trackEditClosed() }) { target in
                editorSheet(target)
            }
            .onChange(of: vm.editTarget) { _, t in
                guard let t else { return }
                editingKind = t.analyticsKind
                gc.sheetClose = nil
                gc.track(GCEvent.itemEditOpen, [
                    "session_id": vm.sessionId, "item_kind": t.analyticsKind, "is_new": t.isNew,
                    "list_position": t.index.map { $0 + 1 },
                ])
            }
            .onAppear {
                let m = vm
                gc.dirtyProvider = { m.dirty }
            }
            .onDisappear { gc.dirtyProvider = nil }
            // 시트 · 확인창이 서로 부딪히지 않게 각각 다른 (보이지 않는) 뷰에 붙여요
            .background { regenHost }
            .background { styleHost }
            .background { intensityHost }
            .background { cancelHost }
    }

    private var mainContent: some View {
        let issues = vm.issueMap
        return ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    conditionsCard
                    if let fit = vm.plan.fit { fitCard(fit) }
                    if !vm.plan.warnings.isEmpty { warningsCard }
                    if !vm.shownIssues.isEmpty { issuesCard(issues) }
                    quickFixCard
                    planCards(issues)
                    // V52: AI 초안은 마일스톤 없이 세부 목표 1개로 와서, 그럴 땐 이 칸을 아예 숨겨요 (예전 초안 · 문제가 있을 때만)
                    if !vm.plan.milestones.isEmpty || vm.plan.subGoals.count > 1 || vm.structureHasIssue {
                        structureSection(issues)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 24)
                .padding(.bottom, 24)
            }
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: vm.scrollRequest) { _, req in
                guard let req else { return }
                // 다음 화면 갱신(빨간 테두리 · 세부 구조 펼치기) 뒤에 이동
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 150_000_000)
                    withAnimation(.easeOut(duration: 0.3)) { proxy.scrollTo(req.key, anchor: .center) }
                }
            }
        }
    }

    @ViewBuilder
    private func planCards(_ issues: DraftIssueMap) -> some View {
        DraftGoalHeroCard(plan: vm.plan, issues: issues, colors: vm.colors,
                          colorId: Binding(get: { vm.colorId }, set: {
                              vm.colorId = $0
                              gc.track(GCEvent.colorSelect, ["session_id": vm.sessionId, "color_id": $0])
                          }),
                          onEdit: { vm.editTarget = $0 }) { styleChips }
        DraftMetricCard(plan: vm.plan, onEditGoal: { vm.editTarget = .goal })
        // 실제로 할 일 — 루틴이 계획의 중심, 일회성 Task 는 꼭 필요한 것만
        DraftRoutinesCard(plan: vm.plan, issues: issues, onEdit: { vm.editTarget = $0 })
        DraftOneTimeTasksCard(plan: vm.plan, issues: issues, onEdit: { vm.editTarget = $0 })
    }

    private func editorSheet(_ target: DraftEditTarget) -> some View {
        DraftItemEditorSheet(target: target, plan: vm.plan,
                             onSave: { vm.applyEdit($0) },
                             onDelete: { vm.deleteTarget() },
                             deleteDisabledReason: vm.deleteBlockReason(target))
    }

    private var regenHost: some View {
        Color.clear
            .sheet(isPresented: Binding(get: { vm.regenOpen }, set: { vm.regenOpen = $0 }),
                   onDismiss: {
                       // 다시 생성으로 넘어간 게 아니면 취소 (취소 버튼 / 쓸어 닫기)
                       if let m = gc.takeSheetClose() {
                           gc.track(GCEvent.regenCancel, ["session_id": vm.sessionId, "close_method": m])
                       }
                   }) { regenSheet }
    }

    private var styleHost: some View {
        Color.clear
            .sheet(isPresented: Binding(get: { vm.styleOpen }, set: { vm.styleOpen = $0 }),
                   onDismiss: {
                       if vm.pendingStyle != nil { vm.styleConfirmOpen = true }
                       else { gc.track(GCEvent.styleChangeCancel, ["session_id": vm.sessionId]) }
                   }) { styleSheet }
            .confirmDialog(isPresented: Binding(get: { vm.styleConfirmOpen }, set: { vm.styleConfirmOpen = $0 }),
                           title: "성향을 바꿀까요?",
                           message: "성향에 맞게 Task 배치가 달라져서, 계획을 처음부터 다시 만들어요.",
                           confirmLabel: "바꾸고 다시 만들기",
                           onCancel: {
                               vm.pendingStyle = nil
                               gc.track(GCEvent.styleChangeCancel, ["session_id": vm.sessionId])
                           }) {
                gc.track(GCEvent.styleChangeConfirm, ["session_id": vm.sessionId, "planning_style": vm.pendingStyle?.rawValue])
                Task { await go(vm.changeStyle()) }
            }
    }

    private var intensityHost: some View {
        let label = vm.pendingIntensity?.label ?? ""
        return Color.clear
            .sheet(isPresented: Binding(get: { vm.intensityOpen }, set: { vm.intensityOpen = $0 }),
                   onDismiss: {
                       if vm.pendingIntensity != nil { vm.intensityConfirmOpen = true }
                       else { gc.track(GCEvent.intensityChangeCancel, ["session_id": vm.sessionId]) }
                   }) { intensitySheet }
            .confirmDialog(isPresented: Binding(get: { vm.intensityConfirmOpen }, set: { vm.intensityConfirmOpen = $0 }),
                           title: "\(label)으로 바꿀까요?",
                           message: "루틴 양이 달라져서 계획을 처음부터 다시 만들어요. 직접 고친 내용은 사라져요.",
                           confirmLabel: "바꾸고 다시 만들기",
                           onCancel: {
                               vm.pendingIntensity = nil
                               gc.track(GCEvent.intensityChangeCancel, ["session_id": vm.sessionId])
                           }) {
                gc.track(GCEvent.intensityChangeConfirm, [
                    "session_id": vm.sessionId, "plan_intensity": vm.pendingIntensity?.rawValue, "is_dirty": vm.dirty,
                ])
                Task { await go(vm.changeIntensity()) }
            }
    }

    private var cancelHost: some View {
        Color.clear
            .confirmDialog(isPresented: Binding(get: { vm.cancelOpen }, set: { vm.cancelOpen = $0 }),
                           title: "목표 만들기를 그만둘까요?", message: "지금까지 나눈 대화와 초안이 모두 사라져요.",
                           confirmLabel: "그만 만들기", cancelLabel: "계속 만들기", tone: .danger,
                           onCancel: { gc.track(GCEvent.cancelDismiss, ["session_id": vm.sessionId]) }) {
                gc.track(GCEvent.cancelConfirm, ["session_id": vm.sessionId])
                Task { await cancelSession() }
            }
    }

    // MARK: 머리말 · 조건

    private var header: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    AIBadge()
                    Text("AI 목표 설계").font(.pt(20, .semibold))
                }
                .foregroundStyle(Color.ink)
                Text("AI가 만든 초안이에요. 항목을 누르면 바로 고칠 수 있어요.")
                    .font(.pt(14)).foregroundStyle(Color.ink3)
            }
            Spacer(minLength: 0)
            NButton("그만 만들기", variant: .ghost, size: .sm) {
                gc.track(GCEvent.cancelClick, ["session_id": vm.sessionId, "is_dirty": vm.dirty])
                vm.cancelOpen = true
            }
                .disabled(vm.busy)
        }
    }

    private var summaryChips: [String] {
        let s = vm.session.summary
        let must = vm.session.mustDoItems ?? []
        return [s?.deadlineText, s?.metricText, s?.preferenceText,
                must.isEmpty ? nil : "꼭 할 일: \(must.joined(separator: " · "))"].compactMap { $0 }
    }

    private var conditionsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("이루고 싶은 목표").font(.pt(12)).foregroundStyle(Color.ink3)
                Text(vm.session.summary?.goalStatement ?? vm.plan.goal.title)
                    .font(.pt(16, .semibold)).foregroundStyle(Color.ink).lineLimit(1)
            }
            FlowLayout(spacing: 8) {
                ForEach(summaryChips, id: \.self) { chip in
                    Text(chip)
                        .font(.pt(13, .medium)).foregroundStyle(Color.ink2)
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background(Color.subtle, in: Capsule())
                }
                Button {
                    gc.track(GCEvent.conditionEdit, ["session_id": vm.sessionId, "is_dirty": vm.dirty])
                    nav.go(.review(vm.sessionId))
                } label: {
                    Text("조건 수정").font(.pt(13, .semibold)).foregroundStyle(Color.ink2)
                        .underline()
                        .padding(.horizontal, 8).padding(.vertical, 6)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card))
        .overlay(RoundedRectangle(cornerRadius: Radius.card).stroke(Color.line, lineWidth: 1))
    }

    /// "나에게 맞춘 점" — 서버가 실제 계획과 대조해 확인한 근거만 보여줘요
    private func fitCard(_ fit: DraftPlanFit) -> some View {
        // 직접 고친 내용까지 반영한 지금 계획의 루틴 주간 합계
        let weekly = DraftPlanUtils.weeklyRoutineMinutes(vm.plan.tasks)
        let over = weekly > fit.weeklyCapMinutes
        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "person.crop.circle.badge.checkmark").font(.system(size: 14)).foregroundStyle(Color.textBrand)
                    Text("나에게 맞춘 점").font(.pt(14, .bold)).foregroundStyle(Color.ink)
                }
                Spacer(minLength: 8)
                Button { openIntensity(entry: "fit_card") } label: {
                    Text("\(fit.intensity.label) · 바꾸기").font(.pt(13, .semibold)).foregroundStyle(Color.ink2)
                }
                .buttonStyle(.plain)
            }
            (Text("루틴은 일주일에 ")
                + Text(DraftPlanUtils.hoursText(weekly)).bold().foregroundColor(over ? Color.warningInk : Color.ink)
                + Text(" · 내 기준 \(DraftPlanUtils.hoursText(fit.weeklyCapMinutes)) 이내 추천").foregroundColor(Color.ink3))
                .font(.pt(13)).foregroundStyle(Color.ink2)
            if over {
                Text("지금 실천량보다 많아요. 처음엔 조금 줄여서 시작하면 끝까지 가기 쉬워요.")
                    .font(.pt(13)).foregroundStyle(Color.warningInk)
            }
            if !fit.notes.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(fit.notes, id: \.self) { note in Text("· \(note)") }
                }
                .font(.pt(13)).foregroundStyle(Color.ink2)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xxl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.line, lineWidth: 1))
    }

    /// SOFT 경고
    private var warningsCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: "exclamationmark.triangle").font(.system(size: 13, weight: .semibold))
                Text("확인해 보면 좋은 점").font(.pt(14, .bold))
            }
            .foregroundStyle(Color.warningInk)
            VStack(alignment: .leading, spacing: 2) {
                ForEach(vm.plan.warnings.indices, id: \.self) { i in Text("· \(vm.plan.warnings[i].message)") }
            }
            .font(.pt(13)).foregroundStyle(Color.warningInkStrong)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.warningSoft, in: RoundedRectangle(cornerRadius: Radius.xxl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.borderSubtle, lineWidth: 1))
    }

    /// 검증 실패 요약
    private func issuesCard(_ issues: DraftIssueMap) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("수정이 필요한 항목이 \(issues.count)곳 있어요").font(.pt(14, .bold)).foregroundStyle(Color.danger)
            Button {
                gc.track(GCEvent.issueJump, ["session_id": vm.sessionId, "issue_count": issues.count])
                vm.scrollToIssue(vm.shownIssues)
            } label: {
                Text("첫 번째 항목으로 이동").font(.pt(13, .semibold)).foregroundStyle(Color.danger).underline()
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.dangerSoft, in: RoundedRectangle(cornerRadius: Radius.xxl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.borderSubtle, lineWidth: 1))
    }

    /// 말로 고치기: 트리를 몰라도 한 문장으로
    private var quickFixCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Image(systemName: "text.bubble").font(.system(size: 17)).foregroundStyle(Color.textBrand)
                TextField("바꾸고 싶은 걸 말로 적어 보세요. 예) 운동은 주 2회로 줄여줘", text: Binding(
                    get: { vm.quickFix },
                    set: { vm.quickFix = String($0.prefix(DraftReviewModel.feedbackMax)) }
                ))
                .font(.pt(15))
                .foregroundStyle(Color.ink)
                .submitLabel(.send)
                .onSubmit { submitQuickFix(method: "keyboard") }
                .disabled(vm.busy)
                .frame(minHeight: 40)
                .accessibilityLabel("말로 고치기")
                NButton("고치기", variant: .brand, size: .sm, icon: "arrow.turn.down.left", loading: vm.regenerating) {
                    submitQuickFix(method: "button")
                }
                .disabled(GoalCreationForm.trim(vm.quickFix).isEmpty || vm.busy)
            }
            if vm.quickFix.isEmpty {
                FlowLayout(spacing: 6) {
                    ForEach(Array(DraftReviewModel.quickFixExamples.enumerated()), id: \.element) { i, ex in
                        Button {
                            gc.track(GCEvent.quickFixExample, ["session_id": vm.sessionId, "example_index": i])
                            vm.quickFix = ex
                        } label: {
                            Text(ex).font(.pt(12)).foregroundStyle(Color.ink3)
                                .padding(.horizontal, 8).frame(height: 24)
                                .overlay(Capsule().stroke(Color.line, lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.leading, 26)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card))
        .overlay(RoundedRectangle(cornerRadius: Radius.card).stroke(Color.line, lineWidth: 1))
    }

    /// 성향 · 강도 칩 (목표 카드 위)
    @ViewBuilder
    private var styleChips: some View {
        chipButton(vm.plan.goal.planningStyle.goalCreationLabel) {
            gc.track(GCEvent.styleChip, ["session_id": vm.sessionId, "planning_style": vm.plan.goal.planningStyle.rawValue])
            openStyle()
        }
        if vm.plan.fit != nil {
            chipButton(vm.currentIntensity.label) { openIntensity(entry: "chip") }
        }
    }

    private func chipButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Text(title)
                NSymbol("chevron.down", size: 9, weight: .bold)
            }
            .font(.pt(12, .semibold))
            .foregroundStyle(Color.ink2)
            .padding(.horizontal, 10)
            .frame(height: 24)
            .overlay(Capsule().stroke(Color.borderSubtle, lineWidth: 1))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    // MARK: 세부 구조 (접힘)

    /// 세부 목표 · 마일스톤을 편집 중이면 펼쳐 둬요
    private var editingStructure: Bool {
        switch vm.editTarget {
        case .subGoal?, .milestone?: return true
        default: return false
        }
    }

    private func structureSection(_ issues: DraftIssueMap) -> some View {
        let open = vm.structureOpen || vm.structureHasIssue || editingStructure
        return VStack(alignment: .leading, spacing: 0) {
            Button {
                withAnimation(.easeOut(duration: 0.2)) { vm.structureOpen.toggle() }
                // 고칠 곳이 있거나 그 안을 편집 중이면 눌러도 접히지 않아요 → 실제로 보이는 상태를 보내요
                gc.track(GCEvent.structureToggle, [
                    "session_id": vm.sessionId,
                    "expanded": vm.structureOpen || vm.structureHasIssue || editingStructure,
                ])
            } label: {
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("세부 구조").font(.pt(15, .bold)).foregroundStyle(Color.ink)
                        Text("세부 목표 \(vm.plan.subGoals.count)개 · 마일스톤 \(vm.plan.milestones.count)단계 — 날짜\(vm.plan.goal.metric != nil ? "·수치" : "")는 자동으로 맞춰져요. 꼭 바꿀 때만 열어 보세요.")
                            .font(.pt(13)).foregroundStyle(Color.ink3)
                            .multilineTextAlignment(.leading)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    NSymbol("chevron.down", size: 15, weight: .semibold)
                        .foregroundStyle(Color.ink3)
                        .rotationEffect(.degrees(open ? 180 : 0))
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if open {
                Divider().overlay(Color.borderSoft)
                VStack(alignment: .leading, spacing: 20) {
                    DraftMilestoneTimeline(plan: vm.plan, issues: issues, onEdit: { vm.editTarget = $0 })
                    DraftSubGoalsCard(plan: vm.plan, issues: issues, onEdit: { vm.editTarget = $0 })
                }
                .padding(.horizontal, 16)
                .padding(.top, 20)
                .padding(.bottom, 20)
            }
        }
        .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card))
        .overlay(RoundedRectangle(cornerRadius: Radius.card).stroke(Color.line, lineWidth: 1))
    }

    // MARK: 하단 확정 바

    private var confirmBar: some View {
        let remaining = vm.regenRemaining
        return VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                (Text("이 목표로 시작하면 ")
                    + Text("Task \(vm.oneTimeCount)개").bold().foregroundColor(Color.textBrand)
                    + Text("와 ")
                    + Text("루틴 \(vm.routineCount)개").bold().foregroundColor(Color.textBrand)
                    + Text("가 내일부터 TimeBlock에 자동 배치돼요."))
                    .font(.pt(14))
                    .foregroundStyle(Color.textSecondary)
                // 다시 생성은 한 대화 최대 횟수 + 하루 횟수가 있어요 — 누르기 전에 남은 횟수를 보여줘요
                UsageLine(items: [vm.regenLeft, vm.draftLeft])
            }
            HStack(spacing: 8) {
                Button {
                    gc.track(GCEvent.regenOpen, ["session_id": vm.sessionId, "is_dirty": vm.dirty, "regen_remaining": vm.regenRemaining])
                    gc.sheetClose = nil
                    vm.regenOpen = true
                } label: {
                    HStack(spacing: 6) {
                        NSymbol("sparkles", size: 14, weight: .semibold)
                        Text("다시 생성")
                        if let remaining { Text("(\(remaining)회 남음)").foregroundStyle(Color.white.opacity(0.8)) }
                    }
                    .font(.pt(13, .medium))
                    .foregroundStyle(Color.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 40)
                    // AI 버튼 (DESIGN_SPEC §6A)
                    .background(Color.aiGradient, in: RoundedRectangle(cornerRadius: Radius.btn))
                    .primaryShadow()
                    .contentShape(RoundedRectangle(cornerRadius: Radius.btn))
                }
                .buttonStyle(.plain)
                .disabled(vm.busy || vm.noRegen)
                .opacity(vm.busy || vm.noRegen ? 0.4 : 1)
                .accessibilityHint(vm.noRegen ? "오늘(또는 이 목표에서) 계획을 더 만들 수 없어요. 초안을 직접 고쳐 주세요." : "")
                .disabledTap(vm.noRegen && !vm.busy) {
                    gc.track(GCEvent.regenDisabled, ["session_id": vm.sessionId, "regen_remaining": vm.regenRemaining])
                }

                Button {
                    gc.track(GCEvent.confirmClick, [
                        "session_id": vm.sessionId, "draft_id": vm.draft.draftId, "is_dirty": vm.dirty,
                        "color_id": vm.colorId, "plan_routine_count": vm.routineCount, "plan_one_time_count": vm.oneTimeCount,
                    ])
                    Task { await confirm() }
                } label: {
                    HStack(spacing: 6) {
                        if vm.confirming { ProgressView().controlSize(.small).tint(Color.actionPrimaryFg) }
                        Text("이 목표로 시작하기")
                    }
                    .font(.pt(14, .semibold))
                    .foregroundStyle(Color.actionPrimaryFg)
                    .frame(maxWidth: .infinity)
                    .frame(height: 40)
                    .background(Color.actionPrimary, in: RoundedRectangle(cornerRadius: Radius.btn))
                    .contentShape(RoundedRectangle(cornerRadius: Radius.xl))
                }
                .buttonStyle(.plain)
                .disabled(vm.busy)
                .opacity(vm.busy && !vm.confirming ? 0.4 : 1)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .background(Color.bgSurface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).strokeBorder(Color.borderSubtle, lineWidth: 1))
        .popShadow()
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 8)
        .background(Color.canvas)
    }

    // MARK: 시트

    private var regenSheet: some View {
        SheetScaffold(title: "계획을 다시 만들까요?",
                      description: vm.dirty ? "직접 수정한 내용은 사라지고 새 초안이 만들어져요."
                                            : "바꾸고 싶은 점을 알려주면 반영해서 다시 만들어요.",
                      dismissible: !vm.regenerating) {
            VStack(alignment: .trailing, spacing: 4) {
                TextField("예) 운동을 조금 더 가볍게 해주세요 (선택)", text: Binding(
                    get: { vm.feedback },
                    set: { vm.feedback = String($0.prefix(DraftReviewModel.feedbackMax)) }
                ), axis: .vertical)
                .lineLimit(4...8)
                .inputStyle(minHeight: 112)
                Text("\(vm.feedback.count)/\(DraftReviewModel.feedbackMax)")
                    .font(.ptMono(12)).foregroundStyle(Color.ink3)
            }
        } footer: {
            NButton("취소", variant: .secondary) {
                gc.sheetClose = "cancel_button"
                vm.regenOpen = false
            }
                .disabled(vm.regenerating)
            NButton("다시 생성", variant: .ai, icon: "sparkles", loading: vm.regenerating) {
                gc.track(GCEvent.regenSubmit, [
                    "session_id": vm.sessionId, "feedback_length": GoalCreationForm.trim(vm.feedback).count, "is_dirty": vm.dirty,
                ])
                gc.sheetClose = "saved"
                Task { await go(vm.submitFeedback()) }
            }
        }
    }

    private var styleSheet: some View {
        SheetScaffold(title: "실행 성향", detents: [.height(280)]) {
            VStack(spacing: 8) {
                ForEach([PlanningStyle.PLANNER, .SPONTANEOUS], id: \.self) { value in
                    let current = vm.plan.goal.planningStyle == value
                    optionButton(title: value.goalCreationLabel, description: nil, current: current) {
                        if !current { vm.pendingStyle = value }
                        vm.styleOpen = false
                    }
                }
            }
            .padding(.bottom, 8)
        }
    }

    private var intensitySheet: some View {
        SheetScaffold(title: "계획 강도", detents: [.height(320)]) {
            VStack(spacing: 8) {
                ForEach(GoalCreationPlanIntensity.allCases, id: \.self) { value in
                    let current = vm.currentIntensity == value
                    optionButton(title: value.label, description: value.descriptionText, current: current) {
                        if !current { vm.pendingIntensity = value }
                        vm.intensityOpen = false
                    }
                }
            }
            .padding(.bottom, 8)
        }
    }

    private func optionButton(title: String, description: String?, current: Bool,
                              action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 8) {
                    Text(title).font(.pt(15, .semibold)).foregroundStyle(current ? Color.textBrand : Color.ink)
                    if current { Text("현재").font(.pt(12, .medium)).foregroundStyle(Color.textBrand) }
                }
                if let description {
                    Text(description).font(.pt(13)).foregroundStyle(Color.ink3).multilineTextAlignment(.leading)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(current ? Color.brandSoft : Color.surface, in: RoundedRectangle(cornerRadius: Radius.xxl))
            .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(current ? Color.brand : Color.borderSubtle, lineWidth: 1))
            .contentShape(RoundedRectangle(cornerRadius: Radius.xxl))
        }
        .buttonStyle(.plain)
    }

    // MARK: 동작

    @MainActor
    private func loadExtras() async {
        guard !isPreview else { return }
        await vm.loadExtras()
    }

    @MainActor
    private func openStyle() {
        vm.pendingStyle = nil
        vm.styleOpen = true
    }

    @MainActor
    private func openIntensity(entry: String) {
        gc.track(GCEvent.intensityChip, [
            "session_id": vm.sessionId, "plan_intensity": vm.currentIntensity.rawValue, "entry_point": entry,
        ])
        vm.pendingIntensity = nil
        vm.intensityOpen = true
    }

    // MARK: 이벤트

    private var gc: GCTracker { .shared }
    /// 지금 열린 편집 시트의 항목 종류 (닫힐 때 item_edit_cancel 에 붙여요)
    @State private var editingKind: String?
    @State private var pageviewSent = false

    @MainActor
    private func trackPageview() {
        guard !isPreview, !pageviewSent else { return }
        pageviewSent = true
        let p = vm.plan
        gc.track(GCEvent.draftPageview, [
            "session_id": vm.sessionId, "draft_id": vm.draft.draftId,
            "plan_routine_count": vm.routineCount, "plan_one_time_count": vm.oneTimeCount,
            "plan_subgoal_count": p.subGoals.count, "plan_milestone_count": p.milestones.count,
            "warning_count": p.warnings.count, "regen_remaining": vm.regenRemaining,
            "planning_style": p.goal.planningStyle.rawValue, "plan_intensity": p.fit?.intensity.rawValue,
            "has_fit": p.fit != nil,
        ])
    }

    /// 편집 시트가 닫힌 뒤 — 저장 · 삭제가 아니면 취소 (취소 버튼 / 쓸어 닫기)
    @MainActor
    private func trackEditClosed() {
        guard let kind = editingKind else { return }
        editingKind = nil
        if let m = gc.takeSheetClose() {
            gc.track(GCEvent.itemEditCancel, ["session_id": vm.sessionId, "item_kind": kind, "close_method": m])
        }
    }

    @MainActor
    private func submitQuickFix(method: String) {
        let text = GoalCreationForm.trim(vm.quickFix)
        guard !text.isEmpty, !vm.busy else { return }
        gc.track(GCEvent.quickFixSubmit, [
            "session_id": vm.sessionId, "feedback_length": text.count, "is_dirty": vm.dirty, "submit_method": method,
        ])
        Task { await go(vm.submitQuickFix()) }
    }

    /// 다시 만들기 성공 → 생성 중 화면으로
    @MainActor
    private func go(_ route: GoalCreationRoute?) async {
        guard let route else { return }
        vm.regenOpen = false
        nav.go(route)
    }

    @MainActor
    private func confirm() async {
        switch await vm.confirm() {
        case .created(let goalId):
            // 상세 화면에서 "이어서 TimeTable 에 배치할까요?"를 물어봐요 (이 호출이 전체 화면도 닫아요)
            AppRouter.shared.replaceWithGoal(goalId, created: true)
        case .alreadyConfirmed:
            nav.closeToGoalList()
        case .resync:
            await reload()
        case .stay:
            break
        }
    }

    @MainActor
    private func cancelSession() async {
        if await vm.cancelSession() { nav.close() }
    }
}

#Preview {
    let vm = DraftReviewModel(session: GoalCreationSamples.session, draft: GoalCreationSamples.draft)
    vm.colors = [
        UserColor(userColorId: 1, hex: "#3D5AFE", isDefault: true),
        UserColor(userColorId: 2, hex: "#12B76A", isDefault: false),
        UserColor(userColorId: 3, hex: "#F59E0B", isDefault: false),
    ]
    vm.usage = AiUsage(pro: false, resetsAt: "2026-11-19T00:00:00", items: [
        UsageItem(key: "GOAL_DRAFT", label: "계획 초안", description: "", used: 2, limit: 5, remaining: 3),
    ], session: UsageItem(key: "SESSION_REGENERATE", label: "이 목표 다시 만들기", description: "", used: 1, limit: 3, remaining: 2))
    return NavigationStack {
        DraftReviewView(preview: vm)
    }
    .environment(GoalCreationNavigator(route: .draft(101)))
}
