// Features/GoalCreation/GoalCreationResumeCard.swift
//
// 만들던 목표가 있으면 "이어서 만들기" 카드 (없으면 아무것도 안 그려요).
// 웹 원본: features/goal-creation/ui/ResumeCard.tsx
//
// 목표 목록 등 플로우 밖에서는 router.resumeGoalCreation(sessionId:) 로 전체 화면을 띄워요.
// 플로우 안(시작 화면)에서는 onOpen 으로 받아서 같은 화면 안에서 단계만 바꿔요.

import SwiftUI

struct GoalCreationResumeCard: View {
    @Environment(AppRouter.self) private var router
    @State private var session: GoalCreationSessionDetail?
    /// nil 이면 router.resumeGoalCreation 을 불러요
    private let onOpen: ((Int) -> Void)?
    /// 미리보기용 (네트워크 없이)
    private let previewSession: GoalCreationSessionDetail?
    /// 이어갈 세션이 있는지 다 불러왔을 때 (목표 만들기 시작 화면 pageview 의 has_resume)
    private var onLoaded: ((Bool) -> Void)? = nil

    /// 목표 목록에서 쓸 때 (전체 화면을 router 로 띄워요)
    init(onLoaded: ((Bool) -> Void)? = nil) {
        self.onOpen = nil
        self.previewSession = nil
        self.onLoaded = onLoaded
    }

    /// 목표 만들기 플로우 안에서 쓸 때
    init(onOpen: @escaping (Int) -> Void, onLoaded: ((Bool) -> Void)? = nil) {
        self.onOpen = onOpen
        self.previewSession = nil
        self.onLoaded = onLoaded
    }

    init(preview: GoalCreationSessionDetail) {
        self.onOpen = { _ in }
        self.previewSession = preview
        self._session = State(initialValue: preview)
    }

    /// 웹 STEP_LABEL
    private static func stepLabel(_ s: GoalCreationSessionStatus) -> String {
        switch s {
        case .INTERVIEWING: return "AI와 대화하는 중"
        case .READY_TO_GENERATE: return "정리 확인 단계"
        case .GENERATING: return "계획을 만드는 중"
        case .DRAFT_READY: return "초안이 준비됐어요"
        default: return ""
        }
    }

    var body: some View {
        content
            .reloadOn([.goalCreation]) {
                guard previewSession == nil else { return }
                // 실패하면 카드만 안 보여요 (웹 useQuery 실패 시 data 없음)
                do {
                    session = try await GoalCreationAPI.getActive()
                } catch is CancellationError {
                    // 다시 불러오는 중에 취소됨 — 지금 값을 그대로 둬요
                    return
                } catch {
                    session = nil
                }
                if let s = session { onLoaded?(GoalCreationRoute.forStatus(s.sessionId, s.status) != nil) } else { onLoaded?(false) }
            }
    }

    @ViewBuilder
    private var content: some View {
        if let s = session, GoalCreationRoute.forStatus(s.sessionId, s.status) != nil {
            Button {
                if let onOpen {
                    // 목표 만들기 시작 화면 안에서만 (목표 목록의 같은 카드는 목표 목록 분류에서)
                    if previewSession == nil {
                        GCTracker.shared.track(GCEvent.resumeClick, ["session_id": s.sessionId, "resume_status": s.status.rawValue])
                    }
                    onOpen(s.sessionId)
                } else {
                    if previewSession == nil {
                        Analytics.track("goal_list.resume_click", ["session_id": s.sessionId, "resume_status": s.status.rawValue])
                    }
                    router.resumeGoalCreation(sessionId: s.sessionId)
                }
            } label: {
                HStack(spacing: 16) {
                    AIBadge()
                    VStack(alignment: .leading, spacing: 2) {
                        Text("만들던 목표가 있어요 · \(Self.stepLabel(s.status))")
                            .font(.pt(13)).foregroundStyle(Color.ink3)
                        Text(s.slots.goalStatement.value ?? "이름 없는 목표")
                            .font(.pt(15, .bold)).foregroundStyle(Color.ink)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    HStack(spacing: 4) {
                        Text("이어서 만들기")
                        NSymbol("arrow.right", size: 13, weight: .semibold)
                    }
                    .font(.pt(14, .semibold))
                    .foregroundStyle(Color.ink)
                    .fixedSize()
                }
                .padding(16)
                .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xxl))
                .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.line, lineWidth: 1))
                .contentShape(RoundedRectangle(cornerRadius: Radius.xxl))
            }
            .buttonStyle(.plain)
        } else {
            // 높이 0 — 목록 사이에 빈 칸을 남기지 않아요
            Color.clear.frame(height: 0)
        }
    }
}

#Preview {
    VStack(spacing: 16) {
        GoalCreationResumeCard(preview: GoalCreationSamples.session)
    }
    .padding(16)
    .canvasBackground()
    .environment(AppRouter.shared)
}
