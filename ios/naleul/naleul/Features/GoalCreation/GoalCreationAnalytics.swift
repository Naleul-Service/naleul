// Features/GoalCreation/GoalCreationAnalytics.swift
//
// AI 목표 만들기 이벤트 (Tracking Plan 분류 "목표 만들기" · 웹 goalCreationEvents 와 같은 이름).
// 모바일은 한 전체 화면이 단계(GoalCreationRoute)만 바꿔 끼워서, 단계를 넘나드는 값(세션 · 생성 계기 · 고친 상태)을 여기에 잠깐 들고 있어요.
//
//   GCTracker.shared.track(GCEvent.startClick, ["start_mode": "typed"])

import Foundation

enum GCEvent {
    // 시작
    static let newPageview = "goal_new.pageview"
    static let newClose = "goal_new.close_click"
    static let newRecordMode = "goal_new.record_mode_click"
    static let kindTypeSelect = "goal_new.kind_type_select"
    static let kindSubSelect = "goal_new.kind_sub_select"
    static let exampleClick = "goal_new.example_click"
    static let startClick = "goal_new.start_click"
    static let startResult = "goal_new.start_result"
    static let startDisabledClick = "goal_new.disabled_click"
    static let resumeClick = "goal_new.resume_click"
    static let quotaBlockView = "goal_new.quota_block_view"
    static let mustDoToggle = "must_do_picker.toggle"
    static let mustDoAdd = "must_do_picker.add"
    // 대화
    static let chatPageview = "goal_chat.pageview"
    static let answerSend = "goal_chat.answer_send"
    static let quickReply = "goal_chat.quick_reply_click"
    static let skip = "goal_chat.skip_click"
    static let answerResult = "goal_chat.answer_result"
    static let chatRetry = "goal_chat.retry_click"
    static let chatDiscard = "goal_chat.discard_click"
    static let finishEarly = "goal_chat.finish_early_click"
    static let interviewComplete = "goal_chat.interview_complete"
    static let reviewClick = "goal_chat.review_click"
    static let goalRejected = "goal_chat.goal_rejected_view"
    static let restart = "goal_chat.restart_click"
    static let chatClose = "goal_chat.close_click"
    static let leaveConfirm = "goal_chat.leave_confirm_click"
    static let leaveCancel = "goal_chat.leave_cancel_click"
    // 정리 확인
    static let reviewPageview = "goal_review.pageview"
    static let slotEditOpen = "goal_review.slot_edit_open"
    static let slotEditSave = "goal_review.slot_edit_save_click"
    static let slotEditResult = "goal_review.slot_edit_result"
    static let slotEditCancel = "goal_review.slot_edit_cancel"
    static let styleSelect = "goal_review.style_select"
    static let intensitySelect = "goal_review.intensity_select"
    static let generateClick = "goal_review.generate_click"
    static let generateResult = "goal_review.generate_result"
    static let reviewClose = "goal_review.close_click"
    // 생성 중
    static let generatingView = "goal_generating.view"
    static let stepView = "goal_generating.step_view"
    static let draftReady = "goal_generating.draft_ready"
    static let failView = "goal_generating.fail_view"
    static let genRetry = "goal_generating.retry_click"
    static let genRetryResult = "goal_generating.retry_result"
    static let editInfo = "goal_generating.edit_info_click"
    static let generatingClose = "goal_generating.close_click"
    // 초안
    static let draftPageview = "goal_draft.pageview"
    static let itemEditOpen = "goal_draft.item_edit_open"
    static let itemEditSave = "goal_draft.item_edit_save"
    static let itemEditCancel = "goal_draft.item_edit_cancel"
    static let itemEditError = "goal_draft.item_edit_error"
    static let itemDeleteClick = "goal_draft.item_delete_click"
    static let itemDeleteConfirm = "goal_draft.item_delete_confirm"
    static let itemDeleteBlocked = "goal_draft.item_delete_blocked"
    static let colorSelect = "goal_draft.color_select"
    static let structureToggle = "goal_draft.structure_toggle"
    static let quickFixExample = "goal_draft.quick_fix_example_click"
    static let quickFixSubmit = "goal_draft.quick_fix_submit"
    static let regenOpen = "goal_draft.regen_open"
    static let regenSubmit = "goal_draft.regen_submit"
    static let regenCancel = "goal_draft.regen_cancel"
    static let regenDisabled = "goal_draft.regen_disabled_click"
    static let styleChip = "goal_draft.style_chip_click"
    static let styleChangeConfirm = "goal_draft.style_change_confirm"
    static let styleChangeCancel = "goal_draft.style_change_cancel"
    static let intensityChip = "goal_draft.intensity_chip_click"
    static let intensityChangeConfirm = "goal_draft.intensity_change_confirm"
    static let intensityChangeCancel = "goal_draft.intensity_change_cancel"
    static let regenResult = "goal_draft.regen_result"
    static let issueJump = "goal_draft.issue_jump_click"
    static let conditionEdit = "goal_draft.condition_edit_click"
    static let confirmClick = "goal_draft.confirm_click"
    static let confirmBlocked = "goal_draft.confirm_blocked"
    static let confirmResult = "goal_draft.confirm_result"
    static let cancelClick = "goal_draft.cancel_click"
    static let cancelConfirm = "goal_draft.cancel_confirm"
    static let cancelDismiss = "goal_draft.cancel_dismiss"
    static let draftClose = "goal_draft.close_click"
    // 세션 안내
    static let sessionRedirect = "goal_flow.session_redirect"
    static let sessionEnded = "goal_flow.session_ended_view"
    static let sessionError = "goal_flow.session_error_view"
    static let ctaClick = "goal_flow.cta_click"
    static let appBackground = "goal_flow.app_background"
}

@MainActor
final class GCTracker {
    static let shared = GCTracker()
    private init() {}

    /// 지금 단계의 세션 (시트 · 공용 부품이 이벤트에 붙여요)
    var sessionId: Int?
    /// 대화 단계의 질문 수 (앱이 뒤로 갈 때 붙여요)
    var questionCount: Int?
    /// 초안을 직접 고쳤는지 알려주는 함수 (초안 단계만)
    var dirtyProvider: (() -> Bool)?
    /// 다음 생성 중 단계가 어떤 동작으로 열리는지 (review · regen · quick_fix · style_change · intensity_change · retry)
    private var pendingGenerateSource: String?
    /// 시트가 어떻게 닫혔는지 (cancel_button · saved · deleted). 비어 있으면 아래로 쓸어 닫은 것
    var sheetClose: String?

    func track(_ event: String, _ props: EventProps = [:]) {
        var p = props
        if p["session_id"] == nil, let sessionId { p["session_id"] = sessionId }
        Analytics.track(event, p)
    }

    func setGenerateSource(_ s: String) { pendingGenerateSource = s }
    /// 생성 중 단계가 열릴 때 한 번 꺼내요. 기록이 없으면 이어서 보기(resume)
    func takeGenerateSource() -> String {
        defer { pendingGenerateSource = nil }
        return pendingGenerateSource ?? "resume"
    }

    /// 시트가 닫힌 뒤 — 취소 버튼이면 cancel_button, 머리의 X 면 x_button, 저장 · 삭제가 아니면 쓸어 닫기(swipe_down)
    func takeSheetClose() -> String? {
        let hint = Analytics.sheetCloseHint
        defer { sheetClose = nil; Analytics.sheetCloseHint = nil }
        switch sheetClose {
        case "saved", "deleted": return nil
        case let m?: return m
        case nil: return hint ?? "swipe_down"
        }
    }

    var isDirty: Bool? { dirtyProvider?() }
}

extension GoalCreationRoute {
    /// from_screen 값
    var analyticsScreen: String {
        switch self {
        case .start: return "start"
        case .resume: return "resume"
        case .chat: return "chat"
        case .review: return "review"
        case .generating: return "generating"
        case .draft: return "draft"
        }
    }

    var sessionId: Int? {
        switch self {
        case .start: return nil
        case .resume(let id), .chat(let id), .review(let id), .draft(let id): return id
        case .generating(let id, _): return id
        }
    }
}

extension DraftEditTarget {
    /// item_kind 값 (goal / routine / one_time / sub_goal / milestone)
    var analyticsKind: String {
        switch self {
        case .goal: return "goal"
        case .subGoal: return "sub_goal"
        case .milestone: return "milestone"
        case .task(_, let type): return type == .ROUTINE ? "routine" : "one_time"
        }
    }
}

/// API 오류 → error_status (네트워크 · 알 수 없는 오류는 0)
func gcErrorStatus(_ error: Error) -> Int {
    (error as? APIError)?.httpStatus ?? 0
}
