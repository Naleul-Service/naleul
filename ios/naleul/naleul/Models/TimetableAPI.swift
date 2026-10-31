// Models/TimetableAPI.swift
//
// 웹 features/timetable/api.ts 의 요청 함수들. (토스트 · 다시 불러오기는 화면에서 Mutation.run 으로 감싸요)
//
//   let res = try await TimetableAPI.timetable(start: "2026-11-16", end: "2026-11-22")
//   await Mutation.run(invalidate: [.timetable], success: { _ in "완료했어요." }) {
//       try await TimetableAPI.toggleComplete(task)
//   }
//
// 웹 규칙: 캘린더에서 하는 변경은 끝나면 .timetable 을 다시 불러와요
// (밀려난 블록·덮인 고정 시간·실행률이 같이 바뀌므로 부분 수정보다 다시 조회가 안전)

import Foundation

enum TimetableAPI {
    // MARK: 조회

    /// GET /timetable?startDate&endDate (최대 7일)
    static func timetable(start: String, end: String) async throws -> TimetableResponse {
        let r: TimetableResponse? = try await API.get("/v1/timetable?startDate=\(start)&endDate=\(end)")
        return r ?? TimetableResponse(days: [])
    }

    /// GET /timetable/monthly?year&month
    static func monthly(year: Int, month: Int) async throws -> MonthlyTimetableResponse? {
        try await API.get("/v1/timetable/monthly?year=\(year)&month=\(month)")
    }

    /// GET /timetable/goal-time?startDate&endDate (최대 42일) — 캘린더의 "목표별 실천 시간"
    static func goalTime(start: String, end: String) async throws -> GoalTimeResponse? {
        try await API.get("/v1/timetable/goal-time?startDate=\(start)&endDate=\(end)")
    }

    /// 기본 생활 패턴 목록
    static func lifePatterns() async throws -> [LifePattern] {
        let r: [LifePattern]? = try await API.get("/v1/life-patterns")
        return r ?? []
    }

    /// 기간(시작일~종료일, 최대 42일)과 겹치는 실제 기록
    static func activities(start: String, end: String) async throws -> [ActualActivity] {
        let r: [ActualActivity]? = try await API.get("/v1/activities?startDate=\(start)&endDate=\(end)")
        return r ?? []
    }

    // MARK: Task 완료 · 삭제 · 고정

    /// 완료 ↔ 완료 취소 (지금 상태의 반대로)
    static func toggleComplete(taskId: Int, currentStatus: TaskStatus) async throws {
        let action = currentStatus == .COMPLETED ? "cancel-complete" : "complete"
        try await API.send("PATCH", "/v1/tasks/\(taskId)/\(action)")
    }

    /// 완료 + 실제 시각 기록 ("계획과 다른 시간에 했어요")
    static func completeWithActual(taskId: Int, actualStartAt: String, actualEndAt: String) async throws {
        try await API.send("PATCH", "/v1/task-actuals/\(taskId)/actual",
                           body: ["actualStartAt": .string(actualStartAt), "actualEndAt": .string(actualEndAt)])
    }

    /// 여러 번 나눠서 한 실제 시간 (2개 이상) — 서버가 첫 시작 ~ 마지막 끝 + 구간을 같이 저장해요
    static func completeWithSegments(taskId: Int, segments: [(start: String, end: String)]) async throws {
        try await API.send("PATCH", "/v1/task-actuals/\(taskId)/actual",
                           body: ["segments": .array(segments.map { .object(["start": .string($0.start), "end": .string($0.end)]) })])
    }

    static func deleteTask(taskId: Int) async throws {
        try await API.send("DELETE", "/v1/tasks/\(taskId)")
    }

    /// 고정(📌) 풀기 — 다음 자동 배치 때 옮겨질 수 있어요
    static func unlockTask(taskId: Int) async throws {
        try await API.send("PATCH", "/v1/tasks/\(taskId)/unlock")
    }

    /// 우선순위만 바꾸기 — 시간은 그대로, 루틴 · 미션은 그날 Task 만 (PATCH /tasks/{id}/priority)
    static func changePriority(taskId: Int, priority: TaskPriority) async throws {
        try await API.send("PATCH", "/v1/tasks/\(taskId)/priority", body: ["taskPriority": .string(priority.rawValue)])
    }

    /// 시간 미정으로 보내기
    static func unscheduleTask(taskId: Int) async throws {
        try await API.send("DELETE", "/v1/tasks/\(taskId)/schedule")
    }

    /// 다른 날로 옮기기 (내일 / 모레 / 날짜 지정 · 시간 미정끼리 드래그) — PATCH /tasks/{id}/date
    ///  - keepTime true: 그날 같은 시각에 놓아 보고, 자리가 없으면 그날 시간 미정으로 (루틴은 409)
    ///  - keepTime false: 그날 시간 미정으로
    @discardableResult
    static func moveTaskDate(taskId: Int, date: String, keepTime: Bool) async throws -> TimeBlockTask? {
        try await API.patch("/v1/tasks/\(taskId)/date", body: ["date": .string(date), "keepTime": .bool(keepTime)])
    }

    // MARK: 시간 바꾸기

    /// 시간 변경 (드래그·직접 입력 공통). 저장하면 그 시간에 고정(📌)돼요
    static func reschedule(taskId: Int, plannedStartAt: String, plannedEndAt: String) async throws -> TaskScheduleResponse? {
        try await API.patch("/v1/tasks/\(taskId)/schedule",
                            body: ["plannedStartAt": .string(plannedStartAt), "plannedEndAt": .string(plannedEndAt)])
    }

    struct ScheduleItem: Hashable {
        var taskId: Int
        var plannedStartAt: String
        var plannedEndAt: String
    }

    /// 여러 Task 를 한 번에 옮기기 (묶음 드래그) — 하나라도 자리가 없으면 아무것도 바뀌지 않아요
    static func rescheduleBatch(_ items: [ScheduleItem]) async throws -> TaskBatchScheduleResponse? {
        let arr: [JSONValue] = items.map {
            ["taskId": .int($0.taskId), "plannedStartAt": .string($0.plannedStartAt), "plannedEndAt": .string($0.plannedEndAt)]
        }
        return try await API.patch("/v1/tasks/schedule/batch", body: ["items": .array(arr)])
    }

    /// 루틴 블록 시간 변경 + 범위. DAY 는 기존 드래그와 같아요
    static func routineScopedReschedule(taskId: Int, plannedStartAt: String, plannedEndAt: String,
                                        scope: ChangeScope) async throws -> RoutineScheduleResponse? {
        try await API.patch("/v1/tasks/\(taskId)/routine-schedule", body: [
            "plannedStartAt": .string(plannedStartAt),
            "plannedEndAt": .string(plannedEndAt),
            "scope": .string(scope.rawValue),
        ])
    }

    // MARK: 22시 이월 제안

    /// "옮길까요?" → 옮기기
    static func acceptCarry(taskId: Int) async throws {
        try await API.send("PATCH", "/v1/tasks/\(taskId)/carry-over/accept")
    }

    /// "옮길까요?" → 안 옮기기 (그날 "시간 미정"으로)
    static func declineCarry(taskId: Int) async throws {
        try await API.send("PATCH", "/v1/tasks/\(taskId)/carry-over/decline")
    }

    /// 이월 되돌리기 — 원래 날짜 · 시각으로 (자리가 없으면 그날 시간 미정). 자동 이월이 다시 옮기지 않아요 (2026-10)
    @discardableResult
    /// from: 누른 이월 흔적이 있는 날 (없으면 마지막 이월의 원래 날). 이미 그날에 있으면 그대로 돌려줘요
    static func revertCarry(taskId: Int, from: String? = nil) async throws -> TimeBlockTask? {
        try await API.patch("/v1/tasks/\(taskId)/carry-over/revert" + (from.map { "?from=\($0)" } ?? ""))
    }

    /// 그날 이월 제안 모두 옮기기 → 옮긴 개수
    static func acceptAllCarry(date: String) async throws -> Int {
        let n: Int? = try await API.patch("/v1/tasks/carry-over/accept-all?date=\(date)")
        return n ?? 0
    }

    // MARK: 고정 시간 (생활 패턴)

    /// 그날만 변경 / 비우기
    static func patternOverride(lifePatternId: Int, targetDate: String, startTime: String? = nil,
                                endTime: String? = nil, skipped: Bool? = nil) async throws {
        try await API.send("PUT", "/v1/life-patterns/\(lifePatternId)/overrides/\(targetDate)", body: .compact([
            "startTime": startTime.map(JSONValue.string),
            "endTime": endTime.map(JSONValue.string),
            "skipped": skipped.map(JSONValue.bool),
        ]))
    }

    /// 기본 시간으로 되돌리기
    static func deletePatternOverride(lifePatternId: Int, targetDate: String) async throws {
        try await API.send("DELETE", "/v1/life-patterns/\(lifePatternId)/overrides/\(targetDate)")
    }

    /// 고정 시간 변경 + 범위. FOLLOWING · WEEKDAY 는 기본값이 바뀌니 .lifePatterns 도 다시 불러와요
    static func patternScopedChange(lifePatternId: Int, targetDate: String, startTime: String, endTime: String,
                                    scope: ChangeScope, startDayOffset: Int = 0) async throws {
        try await API.send("PUT", "/v1/life-patterns/\(lifePatternId)/scoped-change", body: [
            "targetDate": .string(targetDate),
            "startTime": .string(startTime),
            "endTime": .string(endTime),
            "scope": .string(scope.rawValue),
            "startDayOffset": .int(startDayOffset),
        ])
    }

    // MARK: 자동 배치

    /// "다시 배치" — 그날의 자동 배치 블록과 시간 미정 Task 를 다시 놓아요. 지난 날짜는 400 이라 오늘 이후만 보내요
    static func replan(dates: [String]) async throws -> [ReplanResponse] {
        var out: [ReplanResponse] = []
        for d in dates {
            if let r: ReplanResponse = try await API.post("/v1/daily-plans/\(d)/replan") { out.append(r) }
        }
        return out
    }

    /// AI 로 배치 미리보기 — 기간 전체
    static func fillPreview(startDate: String, endDate: String) async throws -> FillPreviewResponse {
        let r: FillPreviewResponse? = try await API.post("/v1/daily-plans/fill/preview?startDate=\(startDate)&endDate=\(endDate)")
        return r ?? FillPreviewResponse(proposals: [], unscheduled: [])
    }

    /// AI 로 배치 미리보기 — 정한 Task 들만 (방금 추가한 Task)
    static func fillPreview(taskIds: [Int]) async throws -> FillPreviewResponse {
        let r: FillPreviewResponse? = try await API.post("/v1/daily-plans/fill/preview-tasks", body: ["taskIds": .of(taskIds)])
        return r ?? FillPreviewResponse(proposals: [], unscheduled: [])
    }

    struct FillApplyItem: Hashable {
        var taskId: Int
        var proposedStart: String
        var proposedEnd: String
        var start: String
        var end: String
        var reason: String?
    }

    /// 점선 제안을 최종 자리로 확정 (그대로 둔 것 = AI 배치, 옮긴 것 = 📌 직접 정한 시간). .timetable · .goals 다시 불러오기
    static func fillApply(_ items: [FillApplyItem]) async throws -> FillApplyResponse? {
        let arr: [JSONValue] = items.map {
            [
                "taskId": .int($0.taskId),
                "proposedStart": .string($0.proposedStart),
                "proposedEnd": .string($0.proposedEnd),
                "start": .string($0.start),
                "end": .string($0.end),
                "reason": .of($0.reason),
            ]
        }
        return try await API.post("/v1/daily-plans/fill/apply", body: ["items": .array(arr)])
    }

    /// 바로 저장 배치 (Task 추가 화면용). 시간 미정 Task 만 남은 빈 시간에 넣어요. .timetable · .goals 다시 불러오기
    static func fill(startDate: String, endDate: String) async throws -> FillResponse? {
        try await API.post("/v1/daily-plans/fill?startDate=\(startDate)&endDate=\(endDate)")
    }

    /// 바로 저장 배치 결과 토스트 (웹 useFillTimetable onSuccess 와 같은 문구)
    @MainActor
    static func toastFillResult(_ r: FillResponse?) {
        guard let r else { return }
        if r.placedCount == 0 && r.unscheduledCount == 0 {
            Toast.show("배치할 시간 미정 Task가 없었어요.")
        } else if r.placedCount == 0 {
            Toast.error("빈 시간이 없어 \(r.unscheduledCount)개는 시간 미정으로 남았어요.")
        } else {
            Toast.success("\(r.placedCount)개 Task를 빈 시간에 배치했어요."
                + (r.unscheduledCount > 0 ? " \(r.unscheduledCount)개는 빈 시간이 없어 시간 미정으로 남았어요." : ""))
        }
    }

    // MARK: 실제로 한 일

    /// 추가(activityId 없음) · 수정
    static func saveActivity(_ input: ActivityInput, activityId: Int? = nil) async throws -> ActualActivity? {
        if let activityId {
            return try await API.put("/v1/activities/\(activityId)", body: input.json)
        }
        return try await API.post("/v1/activities", body: input.json)
    }

    static func deleteActivity(activityId: Int) async throws {
        try await API.send("DELETE", "/v1/activities/\(activityId)")
    }

    // MARK: 안내 문장

    /// 드래그 결과 안내 문장: 밀려난 블록 · 덮은 고정 시간
    static func scheduleResultMessage(_ r: TaskScheduleResponse?) -> String {
        guard let r else { return "시간을 바꿨어요." }
        var parts: [String] = []
        if let c = r.coveredFixedBlocks, !c.isEmpty { parts.append("\(c.map(\.title).joined(separator: "·")) 시간을 덮었어요") }
        if let m = r.moved, !m.isEmpty { parts.append("\(m.count)개 블록을 다른 시간으로 옮겼어요") }
        if let u = r.unscheduled, !u.isEmpty { parts.append("\(u.count)개는 빈 시간이 없어 시간 미정이 됐어요") }
        return parts.isEmpty ? "시간을 바꿨어요." : "시간을 바꿨어요 · \(parts.joined(separator: ", "))."
    }

    /// 묶음 드래그 결과 문장
    static func batchResultMessage(_ r: TaskBatchScheduleResponse?, count: Int) -> String {
        let single = r.map { TaskScheduleResponse(task: $0.tasks.first, moved: $0.moved, unscheduled: $0.unscheduled,
                                                  coveredFixedBlocks: $0.coveredFixedBlocks) }
        return scheduleResultMessage(single).replacingOccurrences(of: "시간을 바꿨어요", with: "\(count)개를 함께 옮겼어요")
    }

    /// 루틴 범위 변경 결과 문장
    static func routineScopeMessage(_ r: RoutineScheduleResponse?, scope: ChangeScope) -> String {
        guard let r, scope != .DAY else { return scheduleResultMessage(r?.result) }
        let n = r.changedDates.count + 1
        let head = scope == .WEEK ? "이번 주 루틴 \(n)일의 시간을 바꿨어요." : "앞으로의 루틴 시간을 바꿨어요."
        let skipped = r.skippedDates.isEmpty ? ""
            : " \(r.skippedDates.map { KST.slashDate($0) }.joined(separator: ", "))은 다른 일정과 겹쳐 그대로예요."
        return head + skipped
    }

    /// 고정 시간 범위 변경 결과 문장
    static func patternScopeMessage(_ scope: ChangeScope) -> String {
        switch scope {
        case .DAY: return "이날만 시간을 바꿨어요."
        case .WEEK: return "이번 주 시간을 바꿨어요."
        case .WEEKDAY: return "앞으로 이 요일의 시간을 바꿨어요. 지난 기록은 그대로예요."
        case .FOLLOWING: return "앞으로 매일의 시간을 바꿨어요. 지난 기록은 그대로예요."
        }
    }
}
