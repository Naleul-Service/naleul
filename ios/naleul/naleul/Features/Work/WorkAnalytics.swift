// Features/Work/WorkAnalytics.swift
//
// 업무형 목표 이벤트 (Tracking Plan 분류 "업무형 목표" · 웹 work_board / work_roadmap / work_task / work_task_modal /
// work_compose / work_share / work_journal / record_stats 와 같은 이름).
// 모바일은 웹의 오른쪽 "적기" 패널 대신 시트, 보드 세 칸 대신 칸 세그먼트 + 카드 … 메뉴를 써요.
//
//   WK.track("work_board.share_click", goalId: gid, ["open_task_count": n])

import SwiftUI
import UIKit

@MainActor
enum WK {
    /// 미리보기에서는 보내지 않아요
    static var isPreview: Bool { ProcessInfo.processInfo.environment["XCODE_RUNNING_FOR_PREVIEWS"] == "1" }

    static func track(_ event: String, _ props: EventProps = [:]) {
        if isPreview { return }
        Analytics.track(event, props)
    }

    /// goal_category_id + 추가 속성
    static func track(_ event: String, goalId: Int, _ props: EventProps = [:]) {
        track(event, ["goal_category_id": goalId].merging(props) { _, new in new })
    }

    /// Task 공통 속성 (칸 옮기기 · 열기)
    static func taskProps(_ t: WorkTask, surface: String) -> EventProps {
        ["surface": surface, "task_id": t.taskId, "topic_id": t.topicId, "is_overdue": t.overdue, "is_routine": t.routine]
    }
}

extension WorkTopicFilter {
    /// topic_filter 값 (ALL · NONE · TOPIC)
    var analyticsName: String {
        switch self {
        case .all: return "ALL"
        case .noTopic: return "NONE"
        case .topic: return "TOPIC"
        }
    }

    var analyticsTopicId: Int? {
        if case .topic(let id) = self { return id }
        return nil
    }
}

// MARK: - 화면에 절반 이상 처음 보일 때 (통계 카드 노출)
//
// iOS 17 에는 onScrollVisibilityChange 가 없어서, 스크롤할 때마다 바뀌는 전역 좌표로 직접 계산해요.

private struct FirstHalfVisibleModifier: ViewModifier {
    let action: () -> Void
    @State private var done = false

    func body(content: Content) -> some View {
        content.background(
            GeometryReader { g in
                let frame = g.frame(in: .global)
                Color.clear
                    .onAppear { check(frame) }
                    .onChange(of: frame.minY) { check(g.frame(in: .global)) }
            }
        )
    }

    private func check(_ frame: CGRect) {
        guard !done, frame.height > 0 else { return }
        let screen = UIScreen.main.bounds
        let visible = frame.intersection(screen).height
        if visible >= min(frame.height, screen.height) * 0.5 {
            done = true
            action()
        }
    }
}

extension View {
    /// 화면에 절반 이상 처음 보였을 때 한 번 (record_stats.card_view)
    func onFirstHalfVisible(_ action: @escaping () -> Void) -> some View {
        modifier(FirstHalfVisibleModifier(action: action))
    }
}

// MARK: - 통계 탭: 칸 누르기 이벤트가 어느 목표인지 (카드가 목표 id 를 몰라서 환경으로 내려요)

private struct RecordStatsGoalIdKey: EnvironmentKey {
    static let defaultValue: Int? = nil
}

extension EnvironmentValues {
    var recordStatsGoalId: Int? {
        get { self[RecordStatsGoalIdKey.self] }
        set { self[RecordStatsGoalIdKey.self] = newValue }
    }
}
