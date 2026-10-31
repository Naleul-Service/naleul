// Features/TaskAdd/TaskAddAnalytics.swift
//
// Task 추가 이벤트 (Tracking Plan 분류 "Task 추가" · 웹 task_add / task_add_input / task_add_goals /
// task_add_item_edit / task_add_result / task_add_manual / task_add_log 와 같은 이름).
// 모바일은 웹의 /tasks/new 페이지 · 캘린더 옆 패널 대신 캘린더 위 시트라서 surface 가 "sheet" 예요.
// 시간 안 정한 Task 의 점선 제안은 시트가 닫힌 뒤 캘린더에서 물어서 그 이벤트만 surface = "calendar".
//
//   TA.track("task_add_input.line_remove", ["list_position": 0, "line_count": 2])

import Foundation

@MainActor
enum TA {
    /// 미리보기에서는 보내지 않아요
    static var isPreview: Bool { ProcessInfo.processInfo.environment["XCODE_RUNNING_FOR_PREVIEWS"] == "1" }

    /// surface = "sheet" 를 붙여서 (props 에 surface 가 있으면 그 값)
    static func track(_ event: String, _ props: EventProps = [:]) {
        if isPreview { return }
        Analytics.track(event, ["surface": "sheet"].merging(props) { _, new in new })
    }

    /// surface 없이 (task_add_log.* 처럼 Tracking Plan 에 surface 가 없는 이벤트)
    static func plain(_ event: String, _ props: EventProps = [:]) {
        if isPreview { return }
        Analytics.track(event, props)
    }

    /// 요청 실패의 HTTP 상태 (0 = 네트워크)
    static func status(_ error: Error) -> Int { (error as? APIError)?.httpStatus ?? 0 }

    /// ai_step 값 (input · goals · result)
    static func aiStep(_ step: Int) -> String { ["input", "goals", "result"][min(max(step, 0), 2)] }

    /// goal_match — existing: 내 목표 / temp: 이번 임시 목표 / none: 없음
    static func goalMatch(goalId: Int?, tempKey: String?) -> String {
        goalId != nil ? "existing" : (tempKey != nil ? "temp" : "none")
    }

    /// 확정 결과 공통 개수
    static func resultCounts(_ r: ConfirmResponse) -> EventProps {
        ["placed_count": r.placements.count, "moved_count": r.movedToOtherDays.count,
         "unscheduled_count": r.unscheduled.count, "pending_count": (r.pending ?? []).count,
         "temp_count": r.tempGoals.count]
    }

    /// 시트가 닫힐 때 close_method — outcome(코드가 닫음 = "next") > 머리 X(Analytics.sheetCloseHint) > 아래로 쓸어 닫기
    static func closeMethod(_ outcome: String?) -> String {
        let hint = Analytics.sheetCloseHint
        Analytics.sheetCloseHint = nil
        return outcome ?? hint ?? "swipe_down"
    }
}

