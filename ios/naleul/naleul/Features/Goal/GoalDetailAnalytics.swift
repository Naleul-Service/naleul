// Features/Goal/GoalDetailAnalytics.swift
//
// 목표 목록 · 추가 · 상세 · 습관 이벤트 (Tracking Plan 분류 "목표 목록/상세" · 웹 goal_list / goal_add / goal_detail / goal_task_list / habit 과 같은 이름).
// 모바일은 웹의 "그 자리 입력창" 대신 시트를 써서, 시트가 어떻게 닫혔는지(close_method)를 각 시트가 onDisappear 에서 보내요.
//
//   GD.track("goal_detail.edit_open", goal.analyticsBase.merging(["entry_point": "menu"]) { $1 })

import Foundation

@MainActor
enum GD {
    static func track(_ event: String, _ props: EventProps = [:]) { Analytics.track(event, props) }

    /// 기본 속성 + 추가 속성
    static func track(_ event: String, _ base: EventProps, _ extra: EventProps) {
        Analytics.track(event, base.merging(extra) { _, new in new })
    }

    /// 요청 결과 공통 속성 — 실패면 Mutation 이 마지막으로 받은 HTTP 상태(0 = 네트워크)
    static func result(_ ok: Bool) -> EventProps {
        ok ? ["result": "success"] : ["result": "fail", "error_status": Mutation.lastErrorStatus ?? 0]
    }

    /// 시트가 닫힐 때(onDisappear) — outcome 이 저장 · 삭제 · 다음 단계면 nil(취소 이벤트 안 보냄).
    /// '취소' 버튼은 outcome = "cancel_button", 머리의 X 는 Analytics.sheetCloseHint, 둘 다 없으면 아래로 쓸어 닫기
    static func closeMethod(_ outcome: String?) -> String? {
        let hint = Analytics.sheetCloseHint
        Analytics.sheetCloseHint = nil
        switch outcome {
        case "saved", "deleted", "next": return nil
        case let m?: return m
        case nil: return hint == "next" ? nil : (hint ?? "swipe_down")
        }
    }

    /// 오늘부터 며칠 뒤(+) · 며칠 전(-)
    static func daysFromToday(_ ymd: String?) -> Int? {
        guard let ymd, !ymd.isEmpty else { return nil }
        return KST.diffDays(KST.today(), String(ymd.prefix(10)))
    }

    /// 오늘부터 며칠 전 (오늘 0)
    static func daysAgo(_ ymd: String?) -> Int? { daysFromToday(ymd).map { -$0 } }
}

extension GoalCategory {
    /// goal_mode 값 (ACHIEVEMENT · RECORD · HABIT)
    var analyticsMode: String { (goalMode ?? .ACHIEVEMENT).rawValue }
    /// 모든 목표 이벤트에 붙는 기본 속성
    var analyticsBase: EventProps { ["goal_category_id": goalCategoryId, "goal_mode": analyticsMode] }
    var analyticsRoutineCount: Int { generalCategories.reduce(0) { $0 + $1.routines.count } }
    var analyticsDDay: Int? {
        guard goalCategoryStatus.isOngoing else { return nil }
        return GoalFormat.periodProgress(goalCategoryStartDate, goalCategoryEndDate)?.remain
    }
    var analyticsHasMetric: Bool { targetValue != nil || !(metricName ?? "").isEmpty }
    /// 목록 카드에 보이는 % (수치 목표면 수치 진행률, 아니면 기간 경과율)
    var analyticsProgressPercent: Int? {
        if let s = startValue, let t = targetValue, s != t {
            let c = currentValue ?? s
            return Int((min(max((c - s) / (t - s), 0), 1) * 100).rounded())
        }
        return GoalFormat.periodProgress(goalCategoryStartDate, goalCategoryEndDate).map { Int(($0.ratio * 100).rounded()) }
    }
}
