// Features/Goal/GoalEditAPI.swift
//
// 웹 features/goal/edit/mutations.ts (목표 · 세부 목표 · 루틴 · 마일스톤 · 말로 고치기)
// + 웹 features/goal/api.ts 의 나머지 저장 요청 (수치 기록 · 오늘 기록 · 주간 점검 · 결과 · 마무리)
// + 웹 features/record/api.ts 의 useKeepAsRecord (임시 목표 → 업무형) · useRecordPattern 일부 (목록 카드의 연속 주)
//
// 여기는 "서버에 보내기"만 해요. 토스트 · 다시 불러오기(Invalidation)는 화면 쪽에서 Mutation.run 으로 해요.
// 웹 규칙 (무엇을 다시 불러오는지):
//   - 목표 편집(useGoalMutation)          → [.goals, .timetable]
//   - 목표 만들기 / 업무형으로 두기         → [.goals]
//   - 수치 기록 · 오늘 기록 · 기록 지우기    → 진행 데이터를 바로 바꾸고 [.goals]
//   - 주간 점검 적용                       → 진행 데이터를 바로 바꾸고 [.goals, .timetable] (실패하면 [.goals])
//   - 주간 점검 넘기기 · 결과 고르기         → 진행 데이터만 바로 바꿔요 (다시 불러오기 없음)
//   - 목표 마무리                          → 성공하면 [.goals, .timetable]

import Foundation

// MARK: - 요청 입력 모양

/// 직접 목표 만들기 (웹 GoalCreateInput + subGoals)
struct GoalCreateInput: Hashable {
    var goalCategoryName: String
    var goalCategoryStartDate: String
    var goalCategoryEndDate: String
    var colorId: Int
    var motive: String?
    var emoji: String?
    /// 수치 목표 — 넣을 때만 (이름이 있을 때)
    var metricName: String?
    var metricUnit: String?
    var startValue: Double?
    var targetValue: Double?
    var hasMetric = false
    var kind: GoalKindValue?
    var subGoals: [String] = []

    var json: JSONValue {
        var o: [String: JSONValue] = [
            "goalCategoryName": .string(goalCategoryName),
            "goalCategoryStartDate": .string(goalCategoryStartDate),
            "goalCategoryEndDate": .string(goalCategoryEndDate),
            "colorId": .int(colorId),
        ]
        // 웹은 undefined(보내지 않음)로 둬요
        if let motive { o["motive"] = .string(motive) }
        if let emoji { o["emoji"] = .string(emoji) }
        for (k, v) in GoalKind.body(kind) { o[k] = v }
        if hasMetric {
            o["metricName"] = .of(metricName)
            o["metricUnit"] = .of(metricUnit)
            o["startValue"] = .of(startValue)
            o["targetValue"] = .of(targetValue)
        }
        return .object(o)
    }
}

/// 루틴 추가 · 수정 (웹 RoutineInput)
struct GoalRoutineInput: Hashable {
    /// 영역 — nil 이면 서버가 목표의 "기타 할 일"에 넣어요
    var generalCategoryId: Int?
    var routineName: String
    var repeatStartDate: String
    var repeatEndDate: String
    var repeatDays: [JavaDayOfWeek]
    /// "HH:mm" — 없으면 nil (자동 배치가 시간을 정해요)
    var repeatStartTime: String?
    var repeatEndTime: String?
    var notificationEnabled: Bool
    /// 하는 방법 — 수정 때 "" 를 보내면 지워져요
    var description: String
    /// 기본 시간과 다른 요일만. [] 를 보내면 모든 요일이 기본 시간으로 돌아가요
    var dayTimes: [RoutineDayTime]

    var json: JSONValue {
        [
            "generalCategoryId": .of(generalCategoryId),
            "routineName": .string(routineName),
            "repeatStartDate": .string(repeatStartDate),
            "repeatEndDate": .string(repeatEndDate),
            "repeatDays": .array(repeatDays.map { JSONValue.string($0.rawValue) }),
            "repeatStartTime": .of(repeatStartTime),
            "repeatEndTime": .of(repeatEndTime),
            "notificationEnabled": .bool(notificationEnabled),
            "description": .string(description),
            "dayTimes": .array(dayTimes.map { t -> JSONValue in
                ["dayOfWeek": .string(t.dayOfWeek.rawValue), "startTime": .string(t.startTime), "endTime": .string(t.endTime)]
            }),
        ]
    }
}

// MARK: - 말로 고치기 (AI 변경안 미리보기 → 확인 → 적용)

/// 변경 하나. op 는 미리보기에서 받은 그대로 적용할 때 다시 보내야 해서 JSON 그대로 들고 있어요
struct GoalEditChange: Codable, Hashable {
    var op: JSONValue
    /// "추가" · "변경" · "삭제"
    var action: String
    var target: String
    var before: String?
    var after: String?
}

struct GoalEditPreview: Codable, Hashable {
    var summary: String
    var changes: [GoalEditChange]

    init(summary: String, changes: [GoalEditChange]) {
        self.summary = summary
        self.changes = changes
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        summary = try c.decodeIfPresent(String.self, forKey: .summary) ?? ""
        changes = try c.decodeIfPresent([GoalEditChange].self, forKey: .changes) ?? []
    }
}

private struct GoalEditApplyResult: Decodable {
    let applied: Int?
}

// MARK: - 업무형 목표 카드의 "연속 완료" (웹 record/api RecordPattern 중 목록 카드가 쓰는 부분만)

struct GoalWorkStreak: Decodable, Hashable {
    struct Week: Decodable, Hashable {
        var weekStart: String
        var count: Int
    }
    var weekStreak: Int
    var thisWeekDone: Bool
    /// 최근 12주, 오래된 것부터
    var weeks: [Week]

    private enum Root: String, CodingKey { case summary, rhythm }
    private enum Summary: String, CodingKey { case weekStreak, thisWeekDone }
    private enum Rhythm: String, CodingKey { case weeks }

    init(weekStreak: Int, thisWeekDone: Bool, weeks: [Week]) {
        self.weekStreak = weekStreak
        self.thisWeekDone = thisWeekDone
        self.weeks = weeks
    }

    init(from decoder: Decoder) throws {
        let root = try decoder.container(keyedBy: Root.self)
        let s = try root.nestedContainer(keyedBy: Summary.self, forKey: .summary)
        weekStreak = try s.decodeIfPresent(Int.self, forKey: .weekStreak) ?? 0
        thisWeekDone = try s.decodeIfPresent(Bool.self, forKey: .thisWeekDone) ?? false
        if root.contains(.rhythm) {
            let r = try root.nestedContainer(keyedBy: Rhythm.self, forKey: .rhythm)
            weeks = try r.decodeIfPresent([Week].self, forKey: .weeks) ?? []
        } else {
            weeks = []
        }
    }
}

// MARK: - 요청

enum GoalEditAPI {
    // ── 전체 목표 ──

    /// 직접 목표 만들기 — 목표를 만든 뒤 적어 둔 세부 목표들을 같은 기간·색으로 이어서 만들어요.
    /// 세부 목표 하나가 실패해도 목표는 이미 만들어졌으니 실패 개수만 돌려줘요.
    static func createGoal(_ input: GoalCreateInput) async throws -> (goal: GoalCategory, failed: Int) {
        guard let created: GoalCategory = try await API.post("/v1/goal-categories", body: input.json) else {
            throw APIError.http(status: 500, message: "문제가 생겼어요. 다시 시도해 주세요.", statusName: nil, data: nil)
        }
        var failed = 0
        for name in input.subGoals {
            do {
                try await API.send("POST", "/v1/general-categories", body: [
                    "generalCategoryName": .string(name),
                    "goalCategoryId": .int(created.goalCategoryId),
                    "generalCategoryStartDate": .string(input.goalCategoryStartDate),
                    "generalCategoryEndDate": .string(input.goalCategoryEndDate),
                    "colorId": .int(input.colorId),
                ])
            } catch {
                failed += 1
            }
        }
        return (created, failed)
    }

    /// 목표 수정 (웹 GoalUpdateInput — 넣은 필드만 바뀌어요. 문자열은 "" 를 보내면 지워져요)
    static func updateGoal(goalId: Int, _ body: JSONValue) async throws {
        try await API.send("PATCH", "/v1/goal-categories/\(goalId)", body: body)
    }

    /// 목표 삭제 — 세부 목표·루틴(과 루틴 Task)도 함께 지워져요
    static func deleteGoal(goalId: Int) async throws {
        try await API.send("DELETE", "/v1/goal-categories/\(goalId)")
    }

    /// 임시 목표를 업무형(기록형)으로 두기
    static func keepAsRecord(goalId: Int) async throws {
        try await API.send("PATCH", "/v1/goal-categories/\(goalId)/keep-as-record")
    }

    /// AI 없이 만든 목표형 목표를 업무형으로 바꾸기 (2026-10 · 목표 형태 정하기)
    static func convertToRecord(goalId: Int) async throws {
        try await API.send("PATCH", "/v1/goal-categories/\(goalId)/convert-to-record")
    }

    // ── 세부 목표 (general category) ──

    static func createSubGoal(goalId: Int, name: String, start: String, end: String, colorId: Int) async throws {
        try await API.send("POST", "/v1/general-categories", body: [
            "generalCategoryName": .string(name),
            "generalCategoryStartDate": .string(start),
            "generalCategoryEndDate": .string(end),
            "colorId": .int(colorId),
            "goalCategoryId": .int(goalId),
        ])
    }

    /// colorId 가 nil 이면 색은 그대로
    static func updateSubGoal(id: Int, name: String, start: String, end: String, colorId: Int?) async throws {
        try await API.send("PUT", "/v1/general-categories/\(id)", body: JSONValue.compact([
            "generalCategoryName": .string(name),
            "generalCategoryStartDate": .string(start),
            "generalCategoryEndDate": .string(end),
            "colorId": colorId.map { JSONValue.int($0) },
        ]))
    }

    static func deleteSubGoal(id: Int) async throws {
        try await API.send("DELETE", "/v1/general-categories/\(id)")
    }

    // ── 루틴 ──

    static func createRoutine(goalId: Int, _ input: GoalRoutineInput) async throws {
        var body = input.json.objectValue ?? [:]
        body["goalCategoryId"] = .int(goalId)
        try await API.send("POST", "/v1/routines", body: .object(body))
    }

    /// 루틴 수정 — 오늘 이후의 루틴 Task 가 새 설정으로 다시 만들어져요 (지난 기록은 그대로)
    static func updateRoutine(id: Int, _ input: GoalRoutineInput) async throws {
        try await API.send("PATCH", "/v1/routines/\(id)", body: input.json)
    }

    /// 루틴을 다른 날에도 했을 때 — 반복 요일은 그대로 두고 그날 하루만 루틴 블록을 만들어요.
    /// startTime · endTime 이 nil 이면 루틴 시간. done 이면 그 시간을 실제로 한 시간으로 남겨요 (앞날은 안 돼요)
    static func addRoutineExtraDay(routineId: Int, date: String, startTime: String?, endTime: String?, done: Bool) async throws {
        try await API.send("POST", "/v1/routines/\(routineId)/extra-days", body: [
            "date": .string(date),
            "startTime": .of(startTime),
            "endTime": .of(endTime),
            "done": .bool(done),
        ])
    }

    static func deleteRoutine(id: Int) async throws {
        try await API.send("DELETE", "/v1/routines/\(id)")
    }

    // ── 마일스톤 (점검 시점) ──

    /// targetValue 를 보내면 "직접 정한 값", nil 이면 시작값 → 목표값 직선에서 자동 계산
    static func createMilestone(goalId: Int, title: String, description: String, dueDate: String, targetValue: Double?) async throws {
        try await API.send("POST", "/v1/goal-categories/\(goalId)/milestones", body: [
            "title": .string(title),
            "description": .string(description),
            "dueDate": .string(dueDate),
            "targetValue": .of(targetValue),
        ])
    }

    /// 마일스톤 수정 · 달성 체크. body 에는 바꿀 필드만 (title, description, dueDate, targetValue, autoTargetValue, status)
    static func updateMilestone(id: Int, _ body: JSONValue) async throws {
        try await API.send("PATCH", "/v1/milestones/\(id)", body: body)
    }

    static func deleteMilestone(id: Int) async throws {
        try await API.send("DELETE", "/v1/milestones/\(id)")
    }

    // ── 말로 고치기 ──

    /// 미리보기 — 아직 아무것도 바뀌지 않아요
    static func editPreview(goalId: Int, instruction: String) async throws -> GoalEditPreview {
        let r: GoalEditPreview? = try await API.post("/v1/goal-categories/\(goalId)/ai-edit/preview",
                                                    body: ["instruction": .string(instruction)])
        return r ?? GoalEditPreview(summary: "", changes: [])
    }

    /// 고른 변경만 적용
    static func editApply(goalId: Int, ops: [JSONValue]) async throws {
        let _: GoalEditApplyResult? = try await API.post("/v1/goal-categories/\(goalId)/ai-edit/apply",
                                                         body: ["ops": .array(ops)])
    }

    // ── 진행 (수치 기록 · 오늘 기록 · 주간 점검 · 결과 · 마무리) — 응답은 진행 데이터 전체 ──

    /// 수치 기록 (같은 날짜는 덮어써요)
    static func recordMetric(goalId: Int, date: String?, value: Double, memo: String? = nil) async throws -> GoalProgress? {
        try await API.put("/v1/goal-categories/\(goalId)/metric-logs", body: JSONValue.compact([
            "date": date.map { JSONValue.string($0) },
            "value": .double(value),
            "memo": memo.map { JSONValue.string($0) },
        ]))
    }

    /// 오늘 기록 + 회고 한 번에 (mood·note 를 모두 비우면 그날 회고를 지워요)
    /// - date: 없으면 오늘 (홈에서 지난날 기록을 남길 때 넣어요. 앞날은 서버가 막아요)
    static func checkIn(goalId: Int, date: String? = nil, value: Double?, mood: ReflectionMood?, note: String?) async throws -> GoalProgress? {
        try await API.put("/v1/goal-categories/\(goalId)/check-in", body: [
            "date": .of(date),
            "value": .of(value),
            "mood": .of(mood?.rawValue),
            "note": .of(note),
        ])
    }

    static func deleteMetricLog(goalId: Int, date: String) async throws -> GoalProgress? {
        try await API.delete("/v1/goal-categories/\(goalId)/metric-logs/\(date)")
    }

    /// 주간 점검 제안 적용 — 내일부터 루틴 요일이 바뀌어요
    static func applyAdjustment(goalId: Int, key: String) async throws -> GoalProgress? {
        try await API.post("/v1/goal-categories/\(goalId)/adjustments/apply", body: ["key": .string(key)])
    }

    /// 주간 점검 제안 넘기기 — 같은 제안은 7일 동안 다시 안 보여요
    static func dismissAdjustment(goalId: Int, key: String) async throws -> GoalProgress? {
        try await API.post("/v1/goal-categories/\(goalId)/adjustments/dismiss", body: ["key": .string(key)])
    }

    /// 끝난 목표의 결과를 직접 고르기 (자동 판정을 덮어써요)
    static func updateOutcome(goalId: Int, result: OutcomeResult) async throws -> GoalProgress? {
        try await API.patch("/v1/goal-categories/\(goalId)/outcome", body: ["result": .string(result.rawValue)])
    }

    /// 목표 마무리 — 결과 · 소감과 함께 끝내요. 이후 예정된 Task 는 서버가 정리해요
    static func completeGoal(goalId: Int, endDate: String, achievement: String?, result: OutcomeResult) async throws {
        try await API.send("PATCH", "/v1/goal-categories/\(goalId)/complete", body: [
            "goalCategoryEndDate": .string(endDate),
            "achievement": .of(achievement),
            "result": .string(result.rawValue),
        ])
    }

    // ── 업무형 목표 카드 ──

    static func workStreak(goalId: Int) async throws -> GoalWorkStreak? {
        try await API.get("/v1/activities/goals/\(goalId)/pattern")
    }
}
