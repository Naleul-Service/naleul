// Models/GoalAPI.swift
//
// 웹 features/goal/api.ts 의 조회 · 공통 요청 (여러 화면이 같이 쓰는 것만 여기 있어요).
// 목표 상세의 추가·수정·삭제(웹 goal/edit/mutations.ts)는 Features/Goal 안에 있어요.
//
// 웹 규칙: 목표를 바꾸면 [.goals, .timetable] 을 다시 불러와요.

import Foundation

enum GoalAPI {
    /// 목표 목록 (삭제된 목표는 혹시 내려와도 제외)
    /// - includeHabit: 기본 생활 습관 그릇(HABIT)도 같이 (맨 뒤) — 실제로 한 일 · 할 일의 목표를 직접 고르는 곳에서
    ///   "기본 생활 습관"으로도 분류할 수 있게 (2026-10, 웹 useGoalCategories({ includeHabit }))
    static func goals(includeHabit: Bool = false) async throws -> [GoalCategory] {
        let list: [GoalCategory]? = try await API.get("/v1/goal-categories" + (includeHabit ? "?includeHabit=true" : ""))
        let alive = (list ?? []).filter { $0.goalCategoryStatus != .DELETED }
        // 서버는 습관 그릇을 맨 앞에 줘요 → 고르는 목록에선 맨 뒤로 (첫 목표가 기본 선택이라)
        return alive.filter { !$0.isHabitGoal } + alive.filter { $0.isHabitGoal }
    }

    static func goal(_ id: Int) async throws -> GoalCategory? {
        try await API.get("/v1/goal-categories/\(id)")
    }

    /// 목표에 속한 Task 전체 (서버 정렬: 미완료 먼저 · 오늘과 가까운 날짜 먼저 · 완료는 아래)
    static func tasks(goalId: Int) async throws -> GoalTasksResponse? {
        try await API.get("/v1/goal-categories/\(goalId)/tasks")
    }

    /// 목표 상세에서 완료 체크 / 취소.
    /// 끝나면 [.goals, .timetable, .record] 다시 불러오기 (기록형 목표의 패턴·업무 일지도 완료한 Task 로 계산해요)
    static func toggleTask(taskId: Int, currentStatus: TaskStatus) async throws {
        try await TimetableAPI.toggleComplete(taskId: taskId, currentStatus: currentStatus)
    }

    /// 루틴 실천 히트맵
    static func routineHeatmap(goalId: Int) async throws -> [RoutineHeatmap] {
        let r: [RoutineHeatmap]? = try await API.get("/v1/routines/\(goalId)/heatmap")
        return r ?? []
    }

    /// 진행 도표 ("지금 어디쯤?")
    static func progress(goalId: Int) async throws -> GoalProgress? {
        try await API.get("/v1/goal-categories/\(goalId)/progress")
    }
}

// MARK: - 목표의 Task 추가 · 수정 (웹 goal/edit/mutations.ts — 캘린더 Task 상세에서도 써요)
// 끝나면 [.goals, .timetable] 다시 불러오기

struct GoalTaskInput: Hashable {
    var taskName: String
    var emoji: String?
    /// 영역 — nil 이면 서버가 목표의 "기타 할 일"에 넣어요
    var generalCategoryId: Int?
    var milestoneId: Int?
    /// "YYYY-MM-DD"
    var date: String
    /// "HH:mm" — 둘 다 있거나 둘 다 nil. 없으면 그날 "시간 미정"으로 두고 자동 배치가 시간을 정해요
    var startTime: String?
    var endTime: String?
    var durationMinutes: Int?
    var dueDate: String?

    /// 웹은 null 을 그대로 보내요 (지우기)
    var json: JSONValue {
        [
            "taskName": .string(taskName),
            "emoji": .of(emoji),
            "generalCategoryId": .of(generalCategoryId),
            "milestoneId": .of(milestoneId),
            "date": .string(date),
            "startTime": .of(startTime),
            "endTime": .of(endTime),
            "durationMinutes": .of(durationMinutes),
            "dueDate": .of(dueDate),
        ]
    }
}

extension GoalAPI {
    static func createGoalTask(goalId: Int, _ input: GoalTaskInput) async throws -> TimeBlockTask? {
        try await API.post("/v1/goal-categories/\(goalId)/tasks", body: input.json)
    }

    static func updateGoalTask(goalId: Int, taskId: Int, _ input: GoalTaskInput) async throws -> TimeBlockTask? {
        try await API.put("/v1/goal-categories/\(goalId)/tasks/\(taskId)", body: input.json)
    }

    static func deleteTask(taskId: Int) async throws {
        try await API.send("DELETE", "/v1/tasks/\(taskId)")
    }
}
