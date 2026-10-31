// Features/GoalCreation/GoalCreationAPI.swift
//
// AI 목표 만들기 API · "성향을 직접 골랐는지" 기억.
// 웹 원본: features/goal-creation/api.ts · planningStyleMemory.ts
//
// 웹 queryKey ['goal-creation', …] → iOS DataKey .goalCreation
// (세션 · 초안 조회는 화면이 직접 불러오고, "이어서 만들기" 카드만 .goalCreation 을 구독해요)

import Foundation

enum GoalCreationAPI {
    private static let base = "/v1/goal-creation/sessions"

    /// 응답 data 가 비어 있으면(있어야 하는데) 에러로
    private static func need<T>(_ v: T?) throws -> T {
        guard let v else { throw APIError.decoding("empty data") }
        return v
    }

    /// 세션 시작 + 첫 질문 (201)
    /// - sourceGoalId: 임시 목표를 구체화할 때. 확정하면 그 임시 목표의 Task·기록이 새 목표로 옮겨져요.
    /// - initialMessage 를 비우면 서버가 (임시 목표 이름으로 / AI 가 먼저 물어보며) 시작해요.
    static func start(initialMessage: String?, sourceGoalId: Int?, kind: GoalKindValue?,
                      mustDoItems: [String]) async throws -> GoalCreationTurnResponse {
        // 고른 카테고리 → AI 가 카테고리에 맞는 질문 · 계획 가이드를 써요
        var body = GoalKind.body(kind)
        if let initialMessage, !initialMessage.isEmpty { body["initialMessage"] = .string(initialMessage) }
        if let sourceGoalId { body["sourceGoalId"] = .int(sourceGoalId) }
        // 꼭 하고 싶은 일 → 인터뷰가 다시 묻지 않고, 계획에 반드시 루틴으로 넣어요
        if !mustDoItems.isEmpty { body["mustDoItems"] = .of(mustDoItems) }
        let r: GoalCreationTurnResponse? = try await API.post(base, body: .object(body))
        return try need(r)
    }

    /// 진행 중인 최신 세션. 없으면 204 → nil
    static func getActive() async throws -> GoalCreationSessionDetail? {
        try await API.get("\(base)/active")
    }

    static func getSession(_ sessionId: Int) async throws -> GoalCreationSessionDetail {
        let r: GoalCreationSessionDetail? = try await API.get("\(base)/\(sessionId)")
        return try need(r)
    }

    /// 답변 / 건너뛰기
    /// answerSource("CHIP" · "TYPED") · chipIndex: 칩을 눌러 답했는지 직접 썼는지 — 운영 대시보드 "AI 채팅 정리 정확도" 전용 (화면 변화 없음)
    static func answer(_ sessionId: Int, content: String?, skip: Bool,
                       answerSource: String? = nil, chipIndex: Int? = nil) async throws -> GoalCreationTurnResponse {
        var body: [String: JSONValue] = ["content": .of(content), "skip": .bool(skip)]
        if let answerSource { body["answerSource"] = .string(answerSource) }
        if answerSource == "CHIP", let chipIndex { body["chipIndex"] = .int(chipIndex) }
        let r: GoalCreationTurnResponse? = try await API.post("\(base)/\(sessionId)/answers", body: .object(body))
        return try need(r)
    }

    /// "바로 계획 만들기"
    static func finishInterview(_ sessionId: Int) async throws -> GoalCreationTurnResponse {
        let r: GoalCreationTurnResponse? = try await API.post("\(base)/\(sessionId)/finish-interview")
        return try need(r)
    }

    /// 정리 확인 화면에서 슬롯 수정 (바꾼 것만)
    static func updateSlots(_ sessionId: Int, _ patch: GoalCreationSlotsPatch) async throws -> GoalCreationTurnResponse {
        let r: GoalCreationTurnResponse? = try await API.request("PATCH", "\(base)/\(sessionId)/slots", encodable: patch)
        return try need(r)
    }

    /// 취소 (ABANDONED)
    static func cancel(_ sessionId: Int) async throws {
        try await API.send("DELETE", "\(base)/\(sessionId)")
    }

    /// 초안 생성 / 다시 생성 (202, 비동기).
    /// basePlan 을 같이 보내면 "말로 고치기": 화면에서 직접 고친 지금 초안을 기준으로 feedback 부분만 바꿔요.
    static func requestDraft(_ sessionId: Int, _ body: GoalCreationDraftRequestBody) async throws -> GoalCreationDraftRequested {
        let r: GoalCreationDraftRequested? = try await API.request("POST", "\(base)/\(sessionId)/drafts", encodable: body)
        return try need(r)
    }

    /// 초안 폴링
    static func getDraft(_ sessionId: Int, _ draftId: Int) async throws -> GoalCreationDraftResponse {
        let r: GoalCreationDraftResponse? = try await API.get("\(base)/\(sessionId)/drafts/\(draftId)")
        return try need(r)
    }

    /// "이 목표로 시작하기" → 201 { goalId }, 검증 실패 422 + data.violations
    static func confirm(_ sessionId: Int, _ body: GoalCreationConfirmRequest) async throws -> GoalCreationConfirmResponse {
        let r: GoalCreationConfirmResponse? = try await API.request("POST", "\(base)/\(sessionId)/confirm", encodable: body)
        return try need(r)
    }
}

/// "사용자가 성향을 직접 골랐는지"를 기억해요 (확정 요청의 planningStyleSelectedByUser 에 필요).
/// 웹은 탭을 닫으면 사라지는 sessionStorage 를 써요 → iOS 는 앱이 살아 있는 동안만 기억하는 메모리 값.
/// (화면 코드에서만 부르니 메인 스레드에서만 써요)
enum GoalCreationStyleMemory {
    private static var selected: Set<Int> = []

    static func markSelectedByUser(_ sessionId: Int) { selected.insert(sessionId) }
    static func wasSelectedByUser(_ sessionId: Int) -> Bool { selected.contains(sessionId) }
}
