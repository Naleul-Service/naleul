// Features/GoalCreation/GoalCreationGeneratingView.swift
//
// G-3 계획 만드는 중 (/goal/new/{sessionId}/generating?draftId=)
// 웹 원본: features/goal-creation/ui/generating/GeneratingScreen.tsx · GeneratingView.tsx
//
// 초안은 서버가 비동기로 만들어요(약 30초). 화면은 2초마다 상태를 물어봐요(폴링).
//  - READY   → 초안 검토 화면으로
//  - FAILED  → 에러 화면 (다시 시도 / 정보 수정하기)
//  - 90초 초과 → 에러 화면 (서버는 계속 만들 수도 있어요)
// 화면을 벗어나면 .task 가 취소되면서 폴링도 자동으로 멈춰요.

import SwiftUI
import Observation

/// draftId 가 있으면 바로 폴링 (계획 만들기 직후), 없으면 세션의 최신 초안을 찾아서 폴링 (이어서 만들기)
struct GoalCreationGeneratingScreen: View {
    let sessionId: Int
    let draftId: Int?

    var body: some View {
        if let draftId {
            GoalCreationGeneratingView(sessionId: sessionId, draftId: draftId)
                .id(draftId)
        } else {
            GoalCreationSessionGate(sessionId: sessionId, allow: [.GENERATING]) { session, _ in
                if let latest = session.latestDraft {
                    GoalCreationGeneratingView(sessionId: sessionId, draftId: latest.draftId)
                        .id(latest.draftId)
                }
            }
        }
    }
}

@MainActor
@Observable
final class GoalCreationGeneratingModel {
    let sessionId: Int
    let draftId: Int
    var draft: GoalCreationDraftResponse?
    var timedOut = false
    /// 첫 응답도 못 받고 실패했을 때
    var loadError: String?
    var retrying = false
    // 이벤트 값
    let openedAt = Date()
    var pollCount = 0
    var lastErrorStatus: Int?
    var secondsElapsed: Int { Int(Date().timeIntervalSince(openedAt)) }
    /// 실패 이유 (server_failed / timeout / poll_error)
    var failReason: String {
        if status == .FAILED { return "server_failed" }
        if loadError != nil && draft == nil { return "poll_error" }
        return "timeout"
    }

    init(sessionId: Int, draftId: Int, preview: GoalCreationDraftResponse? = nil) {
        self.sessionId = sessionId
        self.draftId = draftId
        self.draft = preview
    }

    var status: GoalCreationDraftStatus? { draft?.status }
    var failed: Bool { status == .FAILED || (timedOut && status != .READY) || (loadError != nil && draft == nil) }

    static let failMessage = "계획을 만드는 데 실패했어요. 다시 시도해 주세요."

    var failMessageText: String {
        if status == .FAILED { return draft?.message ?? Self.failMessage }
        if let loadError, draft == nil { return loadError }
        return "생각보다 오래 걸리고 있어요. 잠시 후 다시 시도해 주세요."
    }

    func poll() async {
        let startedAt = Date()
        var failures = 0
        while !Task.isCancelled {
            do {
                let d = try await GoalCreationAPI.getDraft(sessionId, draftId)
                pollCount += 1
                draft = d
                failures = 0
                if d.status != .GENERATING { return }
            } catch is CancellationError {
                return
            } catch {
                // 폴링 중 네트워크가 잠깐 끊겨도 바로 실패로 보지 않게 2번까지는 다시 물어봐요 (웹 retry: 2)
                failures += 1
                lastErrorStatus = gcErrorStatus(error)
                if failures > 2 {
                    if draft == nil {
                        loadError = errorMessage(error)
                        return
                    }
                    failures = 0
                }
            }
            if Date().timeIntervalSince(startedAt) * 1000 >= Double(GoalCreationConst.pollTimeoutMs) {
                timedOut = true
                return
            }
            let wait = draft?.pollAfterMs ?? GoalCreationConst.pollIntervalMs
            try? await Task.sleep(nanoseconds: UInt64(max(wait, 500)) * 1_000_000)
        }
    }

    /// 다시 시도 → 이동할 단계 (실패하면 nil)
    func retry() async -> GoalCreationRoute? {
        retrying = true
        defer { retrying = false }
        do {
            let style = draft?.planningStyle
            let body = GoalCreationDraftRequestBody(
                planningStyle: GoalCreationStyleMemory.wasSelectedByUser(sessionId) ? style : nil)
            let res = try await GoalCreationAPI.requestDraft(sessionId, body)
            GCTracker.shared.track(GCEvent.genRetryResult, ["session_id": sessionId, "result": "success", "draft_id": res.draftId])
            Invalidation.shared.invalidate(.usage)
            GCTracker.shared.setGenerateSource("retry")
            return .generating(sessionId, draftId: res.draftId)
        } catch {
            GCTracker.shared.track(GCEvent.genRetryResult, [
                "session_id": sessionId, "result": "fail", "error_status": gcErrorStatus(error),
            ])
            if let e = error as? APIError, e.httpStatus == 409 {
                GCTracker.shared.setGenerateSource("retry")
                // 아직 서버가 이전 요청을 만들고 있음 → 그 초안을 이어서 기다려요
                let session = try? await GoalCreationAPI.getSession(sessionId)
                if let latest = session?.latestDraft {
                    if latest.status == .GENERATING { return .generating(sessionId, draftId: latest.draftId) }
                    if latest.status == .READY { return .draft(sessionId) }
                }
            }
            Toast.error(errorMessage(error, fallback: Self.failMessage))
            return nil
        }
    }
}

struct GoalCreationGeneratingView: View {
    @Environment(GoalCreationNavigator.self) private var nav
    @State private var vm: GoalCreationGeneratingModel
    private let isPreview: Bool

    init(sessionId: Int, draftId: Int) {
        _vm = State(initialValue: GoalCreationGeneratingModel(sessionId: sessionId, draftId: draftId))
        isPreview = false
    }

    /// 미리보기용 (폴링 안 함)
    init(preview: GoalCreationDraftResponse, timedOut: Bool = false) {
        let m = GoalCreationGeneratingModel(sessionId: 101, draftId: preview.draftId, preview: preview)
        m.timedOut = timedOut
        _vm = State(initialValue: m)
        isPreview = true
    }

    var body: some View {
        GoalCreationFlowShell(onClose: { close() }) {
            if vm.failed { failedContent } else { progressContent }
        }
        .task { await startPolling() }
        .onChange(of: vm.status) { _, s in
            if s == .READY {
                gc.track(GCEvent.draftReady, [
                    "session_id": vm.sessionId, "draft_id": vm.draftId,
                    "seconds_elapsed": vm.secondsElapsed, "poll_count": vm.pollCount,
                ])
                goDraft()
            }
        }
        .onChange(of: vm.draft?.progressStep) { _, step in
            guard let step, vm.status == .GENERATING else { return }
            gc.track(GCEvent.stepView, [
                "session_id": vm.sessionId, "draft_id": vm.draftId, "progress_step": step,
                "seconds_elapsed": vm.secondsElapsed,
            ])
        }
        .onChange(of: vm.failed) { _, failed in
            guard failed else { return }
            gc.track(GCEvent.failView, [
                "session_id": vm.sessionId, "draft_id": vm.draftId, "fail_reason": vm.failReason,
                "seconds_elapsed": vm.secondsElapsed, "error_status": vm.failReason == "poll_error" ? vm.lastErrorStatus : nil,
            ])
        }
    }

    private var gc: GCTracker { .shared }

    // MARK: 실패 / 시간 초과

    private var failedContent: some View {
        VStack(spacing: 0) {
            Image(systemName: "exclamationmark.circle")
                .font(.system(size: 28))
                .foregroundStyle(Color.danger)
                .frame(width: 56, height: 56)
                .background(Color.dangerSoft, in: Circle())
            Text("계획을 만들지 못했어요").font(.pt(16, .semibold)).foregroundStyle(Color.ink).padding(.top, 20)
            Text(vm.failMessageText)
                .font(.pt(14)).foregroundStyle(Color.ink3).lineSpacing(3)
                .multilineTextAlignment(.center).padding(.top, 8)
            HStack(spacing: 8) {
                NButton("정보 수정하기", variant: .secondary) {
                    gc.track(GCEvent.editInfo, ["session_id": vm.sessionId, "fail_reason": vm.failReason])
                    nav.go(.review(vm.sessionId))
                }
                NButton("다시 시도", loading: vm.retrying) { Task { await retry() } }
            }
            .padding(.top, 24)
        }
        .frame(maxWidth: 360)
        .padding(.horizontal, 16)
    }

    // MARK: 생성 중

    private var progressContent: some View {
        let step = vm.draft?.progressStep ?? "ANALYZING"
        let currentIndex = max(GoalCreationConst.progressSteps.firstIndex { $0.step == step } ?? 0, 0)
        let done = vm.status == .READY
        return VStack(spacing: 0) {
            GoalCreationPulse()
            Text(done ? "계획이 완성됐어요" : GoalCreationConst.progressSteps[currentIndex].label)
                .font(.pt(20, .semibold)).foregroundStyle(Color.ink)
                .multilineTextAlignment(.center)
                .padding(.top, 32)
                .animation(.easeOut(duration: 0.2), value: currentIndex)
            Text("보통 30초 정도 걸려요. 화면을 나가도 계속 만들어요.")
                .font(.pt(14)).foregroundStyle(Color.ink3)
                .multilineTextAlignment(.center)
                .padding(.top, 8)

            VStack(alignment: .leading, spacing: 12) {
                ForEach(GoalCreationConst.progressSteps.indices, id: \.self) { i in
                    let state: StepState = (done || i < currentIndex) ? .done : (i == currentIndex ? .current : .todo)
                    stepRow(GoalCreationConst.progressSteps[i].label, state)
                }
            }
            .padding(.top, 40)
        }
        .frame(maxWidth: 360)
        .padding(.horizontal, 16)
    }

    private enum StepState { case done, current, todo }

    private func stepRow(_ label: String, _ state: StepState) -> some View {
        HStack(spacing: 12) {
            ZStack {
                switch state {
                case .done:
                    Circle().fill(Color.ink)
                    NSymbol("checkmark", size: 11, weight: .heavy).foregroundStyle(Color.onInk)
                case .current:
                    Circle().fill(Color.brandSoft)
                    ProgressView().controlSize(.mini).tint(Color.brand)
                case .todo:
                    Circle().fill(Color.subtle)
                }
            }
            .frame(width: 24, height: 24)
            Text(label)
                .font(.pt(14, state == .current ? .semibold : .regular))
                .foregroundStyle(state == .todo ? Color.ink4 : Color.ink)
            Spacer(minLength: 0)
        }
    }

    // MARK: 동작

    @MainActor
    private func startPolling() async {
        guard !isPreview else { return }
        gc.track(GCEvent.generatingView, [
            "session_id": vm.sessionId, "draft_id": vm.draftId, "generate_source": gc.takeGenerateSource(),
        ])
        await vm.poll()
    }

    @MainActor
    private func goDraft() {
        Invalidation.shared.invalidate(.goalCreation)
        nav.go(.draft(vm.sessionId))
    }

    @MainActor
    private func retry() async {
        gc.track(GCEvent.genRetry, ["session_id": vm.sessionId, "draft_id": vm.draftId, "fail_reason": vm.failReason])
        if let next = await vm.retry() { nav.go(next) }
    }

    @MainActor
    private func close() {
        gc.track(GCEvent.generatingClose, [
            "session_id": vm.sessionId, "draft_status": vm.status?.rawValue ?? "UNKNOWN",
            "seconds_elapsed": vm.secondsElapsed,
        ])
        if vm.status == .GENERATING || vm.status == nil {
            Toast.show("계획은 계속 만들어지고 있어요. 나중에 이어서 볼 수 있어요.")
        }
        nav.close()
    }
}

/// 가운데 반짝이 아이콘 + 퍼지는 원 (웹 animate-ping)
private struct GoalCreationPulse: View {
    @State private var ping = false

    var body: some View {
        ZStack {
            Circle()
                .fill(Color.aiGradientMid.opacity(0.18))
                .scaleEffect(ping ? 1.6 : 1)
                .opacity(ping ? 0 : 1)
                .animation(.easeOut(duration: 1).repeatForever(autoreverses: false), value: ping)
            Circle().fill(Color.aiGradient)
            NSymbol("sparkles", size: 30, weight: .semibold).foregroundStyle(.white)
        }
        .frame(width: 80, height: 80)
        .onAppear { ping = true }
        .accessibilityHidden(true)
    }
}

#Preview("만드는 중") {
    NavigationStack {
        GoalCreationGeneratingView(preview: GoalCreationDraftResponse(
            draftId: 7, version: 1, status: .GENERATING, planningStyle: .PLANNER, progressStep: "MILESTONES",
            pollAfterMs: 2000, plan: nil, errorCode: nil, message: nil))
    }
    .environment(GoalCreationNavigator(route: .generating(101, draftId: 7)))
}

#Preview("실패") {
    NavigationStack {
        GoalCreationGeneratingView(preview: GoalCreationDraftResponse(
            draftId: 7, version: 1, status: .FAILED, planningStyle: nil, progressStep: nil,
            pollAfterMs: nil, plan: nil, errorCode: "AI_TIMEOUT", message: "AI 응답이 늦어 계획을 만들지 못했어요."))
    }
    .environment(GoalCreationNavigator(route: .generating(101, draftId: 7)))
}
