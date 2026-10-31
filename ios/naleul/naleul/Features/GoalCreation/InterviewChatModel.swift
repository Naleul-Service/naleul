// Features/GoalCreation/InterviewChatModel.swift
//
// G-1 목표 대화 화면의 모든 상태와 동작.
// 웹 원본: features/goal-creation/hooks/useInterviewChat.ts
//
// 서버가 돌려주는 TurnResponse 를 그대로 화면 상태(meta)로 반영하고,
// 사용자가 보낸 말풍선은 응답을 기다리지 않고 먼저 그려요(낙관적 업데이트).

import Foundation
import Observation

enum InterviewSendState: Hashable {
    case sending, failed
}

/// 화면에 그리는 말풍선 하나
struct InterviewChatItem: Identifiable, Hashable {
    let key: String
    var id: String { key }
    var role: GoalCreationMessageRole
    var content: String?
    var skip: Bool
    var quickReplies: [String]
    var skippable: Bool
    /// 질문이 채우려는 칸 (ASSISTANT 만 · "extraNote" = 마지막 "더 알려줄 정보" 질문)
    var targetSlot: String? = nil
    /// USER 말풍선 전송 상태
    var sendState: InterviewSendState? = nil
    /// 실패 시 보여줄 이유
    var failedMessage: String? = nil

    init(key: String, role: GoalCreationMessageRole, content: String?, skip: Bool, quickReplies: [String],
         skippable: Bool, sendState: InterviewSendState? = nil, failedMessage: String? = nil) {
        self.key = key
        self.role = role
        self.content = content
        self.skip = skip
        self.quickReplies = quickReplies
        self.skippable = skippable
        self.sendState = sendState
        self.failedMessage = failedMessage
    }

    init(_ m: GoalCreationMessage) {
        self.init(key: "m-\(m.messageId)", role: m.role, content: m.content, skip: m.skip ?? false,
                  quickReplies: m.quickReplies ?? [], skippable: m.skippable ?? false)
        self.targetSlot = m.targetSlot
    }
}

struct InterviewMeta: Hashable {
    var status: GoalCreationSessionStatus
    var questionCount: Int
    var maxQuestions: Int
    var canFinishEarly: Bool
    var slots: GoalCreationSlots
    var summary: GoalCreationSummary?
}

@MainActor
@Observable
final class InterviewChatModel {
    let sessionId: Int
    var items: [InterviewChatItem]
    var meta: InterviewMeta
    var input = ""
    var pending = false
    /// 부적절한 목표로 중단(422)됐을 때 안내 문구
    var rejectedMessage: String?

    /// 409 등으로 서버 상태와 어긋났을 때 세션을 다시 불러오기
    @ObservationIgnored var onResync: () -> Void
    /// 세션이 없어졌을 때(404)
    @ObservationIgnored var onNotFound: () -> Void = {}

    private static var tempSeq = 0

    init(initial: GoalCreationSessionDetail, onResync: @escaping () -> Void) {
        sessionId = initial.sessionId
        items = initial.messages.map { InterviewChatItem($0) }
        meta = InterviewMeta(status: initial.status, questionCount: initial.questionCount,
                             maxQuestions: initial.maxQuestions, canFinishEarly: initial.canFinishEarly,
                             slots: initial.slots, summary: initial.summary)
        self.onResync = onResync
    }

    // MARK: 화면에서 읽는 값

    /// 마지막 AI 질문 (빠른 답변 칩 / 건너뛰기 노출용)
    var currentQuestion: InterviewChatItem? {
        guard let last = items.last, last.role == .ASSISTANT else { return nil }
        return last
    }
    var hasFailed: Bool { items.contains { $0.sendState == .failed } }
    var interviewing: Bool { meta.status == .INTERVIEWING && rejectedMessage == nil }
    /// 지금 입력/선택이 가능한지
    var canInteract: Bool { interviewing && !pending && !hasFailed }
    /// 인터뷰가 끝나서 정리 확인으로 넘어갈 수 있는지
    var done: Bool { meta.status == .READY_TO_GENERATE && rejectedMessage == nil }

    // MARK: 내부

    private func patchItem(_ key: String, _ change: (inout InterviewChatItem) -> Void) {
        guard let i = items.firstIndex(where: { $0.key == key }) else { return }
        change(&items[i])
    }

    /// 방금 보낸 답의 종류 (text / quick_reply / skip / retry / finish_early) — 인터뷰가 끝난 이유에 써요
    private var lastAnswerType = "text"

    private func applyTurn(_ turn: GoalCreationTurnResponse) {
        let wasReady = meta.status == .READY_TO_GENERATE
        if !wasReady && turn.status == .READY_TO_GENERATE {
            GCTracker.shared.track(GCEvent.interviewComplete, [
                "session_id": sessionId,
                "complete_reason": lastAnswerType == "finish_early" ? "finish_early" : "questions_done",
                "question_count": turn.questionCount,
            ])
        }
        meta = InterviewMeta(status: turn.status, questionCount: turn.questionCount, maxQuestions: turn.maxQuestions,
                             canFinishEarly: turn.canFinishEarly, slots: turn.slots, summary: turn.summary)
        if let m = turn.message { items.append(InterviewChatItem(m)) }
        // 목표 목록 · 시작 화면의 "이어서 만들기" 정보도 최신으로
        Invalidation.shared.invalidate(.goalCreation)
    }

    private enum ErrorResult { case retry, drop }
    /// 마지막 실패를 어떻게 처리했는지 (answer_result 의 fail_handling)
    private var lastFailHandling = "toast"

    /// 다시 보내기가 의미 있는 에러: AI 실패(502), 네트워크/서버 연결 문제
    private static func isRetryable(_ status: Int) -> Bool { [0, 502, 503, 504].contains(status) }

    /// 공통 에러 처리 — 다시 보내기로 남겨둘지 여부를 돌려줘요
    private func handleError(_ error: Error) -> ErrorResult {
        guard let e = error as? APIError else {
            Toast.error("문제가 생겼어요. 다시 시도해 주세요.")
            lastFailHandling = "retry"
            return .retry
        }
        let status = e.httpStatus
        let message = e.errorDescription ?? "문제가 생겼어요. 다시 시도해 주세요."
        if Self.isRetryable(status) { lastFailHandling = "retry"; return .retry }
        switch status {
        case 422:
            // 부적절한 목표 → 서버가 세션을 ABANDONED 로 바꿈
            lastFailHandling = "reject"
            rejectedMessage = message
            meta.status = .ABANDONED
            GCTracker.shared.track(GCEvent.goalRejected, ["session_id": sessionId, "question_count": meta.questionCount])
            Invalidation.shared.invalidate(.goalCreation)
        case 404:
            lastFailHandling = "to_start"
            Toast.error("대화를 찾을 수 없어요.")
            onNotFound()
        case 409:
            // 다른 기기에서 진행됐거나 이미 끝난 대화 → 서버 상태로 다시 맞춤
            lastFailHandling = "resync"
            Toast.show(message)
            onResync()
        default:
            // 400(건너뛸 수 없는 질문 등), 429(하루 한도) 등
            lastFailHandling = "restore_input"
            Toast.error(message)
        }
        return .drop
    }

    // MARK: 동작

    /// answer_result — 요청이 끝난 직후 한 번
    private func trackResult(_ type: String, error: Error?) {
        GCTracker.shared.track(GCEvent.answerResult, [
            "session_id": sessionId,
            "answer_type": type,
            "result": error == nil ? "success" : "fail",
            "error_status": error.map(gcErrorStatus),
            "fail_handling": error == nil ? nil : lastFailHandling,
            "question_count": meta.questionCount,
        ])
    }

    /// 답변 보내기 / 건너뛰기 / 다시 보내기. type: text / quick_reply / skip / retry
    func send(_ content: String?, skip: Bool, retryKey: String? = nil, type: String = "text", chipIndex: Int? = nil) async {
        if pending { return }
        let text = content?.trimmingCharacters(in: .whitespacesAndNewlines)
        if !skip && (text ?? "").isEmpty { return }

        pending = true
        defer { pending = false }

        let key: String
        if let retryKey {
            key = retryKey
            patchItem(key) { $0.sendState = .sending; $0.failedMessage = nil }
        } else {
            Self.tempSeq += 1
            key = "tmp-\(Self.tempSeq)"
            items.append(InterviewChatItem(key: key, role: .USER, content: text, skip: skip, quickReplies: [],
                                           skippable: false, sendState: .sending))
            if !skip { input = "" }
        }

        lastAnswerType = type
        do {
            // 답 방식은 서버의 정리 정확도 측정용 (다시 보내기는 서버가 칩 글자와 비교해 추정)
            let source: String? = type == "quick_reply" ? "CHIP" : (type == "text" ? "TYPED" : nil)
            let turn = try await GoalCreationAPI.answer(sessionId, content: skip ? nil : text, skip: skip,
                                                        answerSource: source, chipIndex: chipIndex)
            patchItem(key) { $0.sendState = nil }
            applyTurn(turn)
            trackResult(type, error: nil)
        } catch {
            let handled = handleError(error)
            trackResult(type, error: error)
            if handled == .retry {
                let msg = (error as? APIError)?.errorDescription
                patchItem(key) { $0.sendState = .failed; $0.failedMessage = msg }
            } else {
                // 서버가 받지 않은 말풍선은 지우고, 쓰던 글은 입력창에 돌려놓기
                items.removeAll { $0.key == key }
                if !skip, let text, !text.isEmpty, retryKey == nil, input.isEmpty { input = text }
            }
        }
    }

    func sendText() async { await send(input, skip: false, type: "text") }
    func sendQuickReply(_ reply: String, index: Int? = nil) async {
        await send(reply, skip: false, type: "quick_reply", chipIndex: index)
    }
    func skipQuestion() async { await send(nil, skip: true, type: "skip") }

    /// 502 는 서버에 사용자 메시지가 이미 저장된 상태. 같은 내용으로 보내면 중복 저장되지 않아요.
    func retry(_ key: String) async {
        guard let item = items.first(where: { $0.key == key }) else { return }
        await send(item.content, skip: item.skip, retryKey: key, type: "retry")
    }

    /// 실패한 말풍선을 지우고 입력창으로 되돌리기
    func discardFailed(_ key: String) {
        let item = items.first { $0.key == key }
        items.removeAll { $0.key == key }
        if let item, !item.skip, let c = item.content, input.isEmpty { input = c }
    }

    func finishEarly() async {
        if pending { return }
        pending = true
        defer { pending = false }
        lastAnswerType = "finish_early"
        do {
            let turn = try await GoalCreationAPI.finishInterview(sessionId)
            applyTurn(turn)
            trackResult("finish_early", error: nil)
        } catch {
            let handled = handleError(error)
            trackResult("finish_early", error: error)
            if handled == .retry { Toast.error("계획 단계로 넘어가지 못했어요. 다시 시도해 주세요.") }
        }
    }
}
