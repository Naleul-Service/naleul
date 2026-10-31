// Features/GoalCreation/GoalCreationFlowView.swift
//
// AI 목표 만들기 전체 화면 (MainTabView 가 fullScreenCover 로 띄워요).
// 웹 원본: app/(flow)/goal/new/** 페이지들 + features/goal-creation/ui/SessionGate.tsx · FlowHeader.tsx
//
// 웹은 단계마다 URL 이 달라요 (/goal/new → /goal/new/12 → …/review → …/generating → …/draft).
// iOS 는 이 화면 하나가 "지금 단계(GoalCreationRoute)"를 들고 있다가 내용을 바꿔 끼워요.
// 웹에서 단계 이동은 거의 router.replace(뒤로 가기로 돌아가지 않음)라서, push 대신 바꿔 끼우는 방식이 같아요.
// 닫기(X)는 어느 단계에서든 AppRouter.closeGoalCreation() — 웹의 "/goal 로 이동" 과 같아요.

import SwiftUI
import Observation

// MARK: - 단계 이동

@MainActor
@Observable
final class GoalCreationNavigator {
    var route: GoalCreationRoute
    /// 임시 목표를 구체화할 때 (웹 ?sourceGoalId=)
    let sourceGoalId: Int?

    init(route: GoalCreationRoute, sourceGoalId: Int? = nil) {
        self.route = route
        self.sourceGoalId = sourceGoalId
    }

    /// 웹 router.replace(goalFlowPath.xxx)
    func go(_ route: GoalCreationRoute) {
        withAnimation(.easeOut(duration: 0.2)) { self.route = route }
    }

    /// 플로우 닫기 (웹 router.push('/goal'))
    func close() {
        AppRouter.shared.closeGoalCreation()
    }

    /// 닫고 목표 목록으로 (웹 "목표로 가기" 링크)
    func closeToGoalList() {
        AppRouter.shared.closeGoalCreation()
        AppRouter.shared.openGoalList()
    }
}

// MARK: - 전체 화면

struct GoalCreationFlowView: View {
    @State private var nav: GoalCreationNavigator
    @Environment(\.scenePhase) private var scenePhase

    init(start: GoalCreationStart) {
        let route: GoalCreationRoute = start.resumeSessionId.map { GoalCreationRoute.resume($0) } ?? .start
        _nav = State(initialValue: GoalCreationNavigator(route: route, sourceGoalId: start.sourceGoalId))
    }

    /// 미리보기용 — 원하는 단계에서 시작
    init(previewRoute: GoalCreationRoute) {
        _nav = State(initialValue: GoalCreationNavigator(route: previewRoute))
    }

    var body: some View {
        NavigationStack {
            GoalCreationRouteContent(route: nav.route, sourceGoalId: nav.sourceGoalId)
                // 단계가 바뀌면 화면 상태를 새로 시작해요 (웹은 페이지가 바뀌면 컴포넌트가 새로 만들어져요)
                .id(nav.route)
                .transition(.opacity)
        }
        .environment(nav)
        .tint(Color.ink)
        // 목표 만들기 중에 앱이 뒤로 가면 — 어느 단계에서 나갔는지
        .onChange(of: scenePhase) { _, phase in
            guard phase == .background else { return }
            let t = GCTracker.shared
            t.track(GCEvent.appBackground, [
                "session_id": nav.route.sessionId,
                "from_screen": nav.route.analyticsScreen,
                "question_count": nav.route.analyticsScreen == "chat" ? t.questionCount : nil,
                "is_dirty": nav.route.analyticsScreen == "draft" ? t.isDirty : nil,
            ])
        }
        .onChange(of: nav.route) { _, r in GCTracker.shared.sessionId = r.sessionId }
        .onAppear { GCTracker.shared.sessionId = nav.route.sessionId }
    }
}

/// 단계 → 화면 (웹 app/(flow)/goal/new/** 의 page.tsx 들)
private struct GoalCreationRouteContent: View {
    let route: GoalCreationRoute
    let sourceGoalId: Int?

    var body: some View {
        switch route {
        case .start:
            GoalCreationStartView(sourceGoalId: sourceGoalId)
        case .resume(let id):
            // 이어서 하기: 세션 상태에 맞는 단계로 바로 보내요 (allow 가 비어 있어서 항상 이동하거나 "끝난 대화" 안내)
            GoalCreationSessionGate(sessionId: id, allow: []) { _, _ in EmptyView() }
        case .chat(let id):
            InterviewChatScreen(sessionId: id)
        case .review(let id):
            GoalCreationSessionGate(sessionId: id, allow: [.READY_TO_GENERATE, .DRAFT_READY]) { session, reload in
                GoalCreationReviewView(session: session, reload: reload)
            }
        case let .generating(id, draftId):
            GoalCreationGeneratingScreen(sessionId: id, draftId: draftId)
        case .draft(let id):
            GoalCreationSessionGate(sessionId: id, allow: [.DRAFT_READY]) { session, reload in
                DraftLoaderView(session: session, reload: reload)
            }
        }
    }
}

// MARK: - 상단 바 (웹 FlowHeader)

private struct GoalCreationHeaderModifier<Center: View, Action: View>: ViewModifier {
    let title: String
    let onClose: () -> Void
    let center: () -> Center
    let action: () -> Action

    func body(content: Content) -> some View {
        content
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarBackButtonHidden(true)
            .toolbarBackground(Color.surface, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    HStack(spacing: 2) {
                        IconButton(systemName: "xmark", size: 17, color: .ink2, label: "닫기", action: onClose)
                        Text(title).font(.pt(15, .bold)).foregroundStyle(Color.ink)
                    }
                }
                ToolbarItem(placement: .principal) { center() }
                ToolbarItem(placement: .topBarTrailing) { action() }
            }
    }
}

extension View {
    /// 목표 만들기 공통 상단 바: 닫기 · 제목 · 가운데(진행 표시) · 오른쪽 액션
    func goalCreationHeader<C: View, A: View>(title: String = "새 목표", onClose: @escaping () -> Void,
                                              @ViewBuilder center: @escaping () -> C,
                                              @ViewBuilder action: @escaping () -> A) -> some View {
        modifier(GoalCreationHeaderModifier(title: title, onClose: onClose, center: center, action: action))
    }

    func goalCreationHeader(title: String = "새 목표", onClose: @escaping () -> Void) -> some View {
        goalCreationHeader(title: title, onClose: onClose, center: { EmptyView() }, action: { EmptyView() })
    }
}

// MARK: - 가운데 안내 (웹 CenterMessage)

struct GoalCreationCenterMessage<Actions: View>: View {
    let title: String
    var description: String? = nil
    @ViewBuilder var actions: () -> Actions

    init(title: String, description: String? = nil, @ViewBuilder actions: @escaping () -> Actions) {
        self.title = title
        self.description = description
        self.actions = actions
    }

    var body: some View {
        VStack(spacing: 0) {
            Text(title).font(.pt(16, .semibold)).foregroundStyle(Color.ink).multilineTextAlignment(.center)
            if let description {
                Text(description).font(.pt(14)).foregroundStyle(Color.ink3)
                    .multilineTextAlignment(.center).lineSpacing(3).padding(.top, 8)
            }
            HStack(spacing: 8) { actions() }.padding(.top, 24)
        }
        .frame(maxWidth: 360)
        .padding(.horizontal, 16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// 상단 바 + 가운데 내용 (웹 FlowShell)
struct GoalCreationFlowShell<Content: View>: View {
    var onClose: (() -> Void)? = nil
    @ViewBuilder var content: () -> Content
    @Environment(GoalCreationNavigator.self) private var nav

    init(onClose: (() -> Void)? = nil, @ViewBuilder content: @escaping () -> Content) {
        self.onClose = onClose
        self.content = content
    }

    var body: some View {
        content()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.surface.ignoresSafeArea())
            .goalCreationHeader(onClose: {
                if let onClose { onClose() } else { nav.close() }
            })
    }
}

// MARK: - 세션 불러오기 + 상태 확인 (웹 SessionGate)

/// 세션을 불러와서
///  - 로딩/에러 화면을 대신 보여주고
///  - 이 화면에서 다룰 수 없는 상태면 맞는 단계로 보내요 (예: 다시 열었더니 이미 생성 완료)
/// content 에는 세션과 "다시 불러오기" 함수를 넘겨요.
struct GoalCreationSessionGate<Content: View>: View {
    let sessionId: Int
    let allow: [GoalCreationSessionStatus]
    let content: (GoalCreationSessionDetail, @escaping () async -> Void) -> Content

    @Environment(GoalCreationNavigator.self) private var nav
    @State private var phase: GatePhase = .loading

    private enum GatePhase {
        case loading
        case loaded(GoalCreationSessionDetail)
        /// 이어갈 화면이 없는 상태 (CONFIRMED / ABANDONED)
        case ended(GoalCreationSessionStatus)
        case notFound
        case failed(String)
    }

    init(sessionId: Int, allow: [GoalCreationSessionStatus],
         @ViewBuilder content: @escaping (GoalCreationSessionDetail, @escaping () async -> Void) -> Content) {
        self.sessionId = sessionId
        self.allow = allow
        self.content = content
    }

    var body: some View {
        Group {
            switch phase {
            case .loading:
                GoalCreationFlowShell {
                    ProgressView().tint(Color.ink3)
                }
            case .loaded(let session):
                content(session, { await load() })
            case .ended(let status):
                GoalCreationFlowShell { endedMessage(status) }
            case .notFound:
                GoalCreationFlowShell {
                    GoalCreationCenterMessage(title: "대화를 찾을 수 없어요") {
                        NButton("새로 시작하기") {
                            GCTracker.shared.track(GCEvent.ctaClick, ["session_id": sessionId, "cta": "restart", "session_status": "NOT_FOUND"])
                            nav.go(.start)
                        }
                    }
                }
            case .failed(let message):
                GoalCreationFlowShell {
                    GoalCreationCenterMessage(title: "불러오지 못했어요", description: message) {
                        NButton("다시 시도") {
                            GCTracker.shared.track(GCEvent.ctaClick, ["session_id": sessionId, "cta": "retry", "session_status": "ERROR"])
                            phase = .loading
                            Task { await load() }
                        }
                    }
                }
            }
        }
        // 화면에 들어올 때마다 서버 최신 상태로 (웹 staleTime: 0)
        .task(id: sessionId) { await load() }
    }

    @ViewBuilder
    private func endedMessage(_ status: GoalCreationSessionStatus) -> some View {
        switch status {
        case .CONFIRMED:
            GoalCreationCenterMessage(title: "이미 만들어진 목표예요", description: "목표 화면에서 확인할 수 있어요.") {
                NButton("목표로 가기", variant: .secondary) { cta("to_goals", status); nav.closeToGoalList() }
            }
        case .ABANDONED:
            GoalCreationCenterMessage(title: "종료된 대화예요", description: "취소했거나 7일이 지나 더 이어갈 수 없어요.") {
                NButton("새로 시작하기") { cta("restart", status); nav.go(.start) }
            }
        default:
            GoalCreationCenterMessage(title: "이어갈 수 없어요") {
                NButton("목표로 가기", variant: .secondary) { cta("to_goals", status); nav.closeToGoalList() }
            }
        }
    }

    private func cta(_ name: String, _ status: GoalCreationSessionStatus) {
        GCTracker.shared.track(GCEvent.ctaClick, ["session_id": sessionId, "cta": name, "session_status": status.rawValue])
    }

    @MainActor
    private func load() async {
        let from = nav.route.analyticsScreen
        do {
            let session = try await GoalCreationAPI.getSession(sessionId)
            if allow.contains(session.status) {
                phase = .loaded(session)
            } else if let next = GoalCreationRoute.forStatus(sessionId, session.status) {
                GCTracker.shared.track(GCEvent.sessionRedirect, [
                    "session_id": sessionId, "from_screen": from, "session_status": session.status.rawValue,
                ])
                // 맞는 단계로 이동하는 동안은 로딩 표시
                phase = .loading
                nav.go(next)
            } else {
                GCTracker.shared.track(GCEvent.sessionEnded, [
                    "session_id": sessionId, "session_status": session.status.rawValue, "from_screen": from,
                ])
                phase = .ended(session.status)
            }
        } catch is CancellationError {
            return
        } catch {
            GCTracker.shared.track(GCEvent.sessionError, [
                "session_id": sessionId, "error_status": gcErrorStatus(error), "from_screen": from,
            ])
            if let e = error as? APIError, e.httpStatus == 404 {
                phase = .notFound
            } else {
                phase = .failed(errorMessage(error))
            }
        }
    }
}

#Preview("세션 없음 안내") {
    NavigationStack {
        GoalCreationFlowShell {
            GoalCreationCenterMessage(title: "종료된 대화예요", description: "취소했거나 7일이 지나 더 이어갈 수 없어요.") {
                NButton("새로 시작하기") {}
            }
        }
    }
    .environment(GoalCreationNavigator(route: .start))
}

#Preview("시작 화면") {
    GoalCreationFlowView(previewRoute: .start)
        // 시작 화면이 @Environment(AppRouter.self) 를 읽어요 — 없으면 미리보기가 멈춰요
        .environment(AppRouter.shared)
}
