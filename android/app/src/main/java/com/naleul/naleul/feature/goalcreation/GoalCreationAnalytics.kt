// feature/goalcreation/GoalCreationAnalytics.kt
//
// iOS Features/GoalCreation/GoalCreationAnalytics.swift — AI 목표 만들기 이벤트 (Tracking Plan 분류 "목표 만들기").
// 한 전체 화면이 단계(GoalCreationRoute)만 바꿔 끼워서, 단계를 넘나드는 값(세션 · 생성 계기 · 고친 상태)을 여기에 잠깐 들고 있어요.

package com.naleul.naleul.feature.goalcreation

import com.naleul.naleul.core.analytics.AnalyticsService
import com.naleul.naleul.core.network.ApiError

object GCEvent {
    // 시작
    const val newPageview = "goal_new.pageview"
    const val newClose = "goal_new.close_click"
    const val newRecordMode = "goal_new.record_mode_click"
    const val kindTypeSelect = "goal_new.kind_type_select"
    const val kindSubSelect = "goal_new.kind_sub_select"
    const val exampleClick = "goal_new.example_click"
    const val startClick = "goal_new.start_click"
    const val startResult = "goal_new.start_result"
    const val startDisabledClick = "goal_new.disabled_click"
    const val resumeClick = "goal_new.resume_click"
    const val quotaBlockView = "goal_new.quota_block_view"
    const val mustDoToggle = "must_do_picker.toggle"
    const val mustDoAdd = "must_do_picker.add"
    // 대화
    const val chatPageview = "goal_chat.pageview"
    const val answerSend = "goal_chat.answer_send"
    const val quickReply = "goal_chat.quick_reply_click"
    const val skip = "goal_chat.skip_click"
    const val answerResult = "goal_chat.answer_result"
    const val chatRetry = "goal_chat.retry_click"
    const val chatDiscard = "goal_chat.discard_click"
    const val finishEarly = "goal_chat.finish_early_click"
    const val interviewComplete = "goal_chat.interview_complete"
    const val reviewClick = "goal_chat.review_click"
    const val goalRejected = "goal_chat.goal_rejected_view"
    const val restart = "goal_chat.restart_click"
    const val chatClose = "goal_chat.close_click"
    const val leaveConfirm = "goal_chat.leave_confirm_click"
    const val leaveCancel = "goal_chat.leave_cancel_click"
    // 정리 확인
    const val reviewPageview = "goal_review.pageview"
    const val slotEditOpen = "goal_review.slot_edit_open"
    const val slotEditSave = "goal_review.slot_edit_save_click"
    const val slotEditResult = "goal_review.slot_edit_result"
    const val slotEditCancel = "goal_review.slot_edit_cancel"
    const val styleSelect = "goal_review.style_select"
    const val intensitySelect = "goal_review.intensity_select"
    const val generateClick = "goal_review.generate_click"
    const val generateResult = "goal_review.generate_result"
    const val reviewClose = "goal_review.close_click"
    // 생성 중
    const val generatingView = "goal_generating.view"
    const val stepView = "goal_generating.step_view"
    const val draftReady = "goal_generating.draft_ready"
    const val failView = "goal_generating.fail_view"
    const val genRetry = "goal_generating.retry_click"
    const val genRetryResult = "goal_generating.retry_result"
    const val editInfo = "goal_generating.edit_info_click"
    const val generatingClose = "goal_generating.close_click"
    // 초안
    const val draftPageview = "goal_draft.pageview"
    const val itemEditOpen = "goal_draft.item_edit_open"
    const val itemEditSave = "goal_draft.item_edit_save"
    const val itemEditCancel = "goal_draft.item_edit_cancel"
    const val itemEditError = "goal_draft.item_edit_error"
    const val itemDeleteClick = "goal_draft.item_delete_click"
    const val itemDeleteConfirm = "goal_draft.item_delete_confirm"
    const val itemDeleteBlocked = "goal_draft.item_delete_blocked"
    const val colorSelect = "goal_draft.color_select"
    const val structureToggle = "goal_draft.structure_toggle"
    const val quickFixExample = "goal_draft.quick_fix_example_click"
    const val quickFixSubmit = "goal_draft.quick_fix_submit"
    const val regenOpen = "goal_draft.regen_open"
    const val regenSubmit = "goal_draft.regen_submit"
    const val regenCancel = "goal_draft.regen_cancel"
    const val regenDisabled = "goal_draft.regen_disabled_click"
    const val styleChip = "goal_draft.style_chip_click"
    const val styleChangeConfirm = "goal_draft.style_change_confirm"
    const val styleChangeCancel = "goal_draft.style_change_cancel"
    const val intensityChip = "goal_draft.intensity_chip_click"
    const val intensityChangeConfirm = "goal_draft.intensity_change_confirm"
    const val intensityChangeCancel = "goal_draft.intensity_change_cancel"
    const val regenResult = "goal_draft.regen_result"
    const val issueJump = "goal_draft.issue_jump_click"
    const val conditionEdit = "goal_draft.condition_edit_click"
    const val confirmClick = "goal_draft.confirm_click"
    const val confirmBlocked = "goal_draft.confirm_blocked"
    const val confirmResult = "goal_draft.confirm_result"
    const val cancelClick = "goal_draft.cancel_click"
    const val cancelConfirm = "goal_draft.cancel_confirm"
    const val cancelDismiss = "goal_draft.cancel_dismiss"
    const val draftClose = "goal_draft.close_click"
    // 세션 안내
    const val sessionRedirect = "goal_flow.session_redirect"
    const val sessionEnded = "goal_flow.session_ended_view"
    const val sessionError = "goal_flow.session_error_view"
    const val ctaClick = "goal_flow.cta_click"
    const val appBackground = "goal_flow.app_background"
}

object GCTracker {
    /** 지금 단계의 세션 (시트 · 공용 부품이 이벤트에 붙여요) */
    var sessionId: Int? = null
    /** 대화 단계의 질문 수 (앱이 뒤로 갈 때 붙여요) */
    var questionCount: Int? = null
    /** 초안을 직접 고쳤는지 알려주는 함수 (초안 단계만) */
    var dirtyProvider: (() -> Boolean)? = null
    /** 다음 생성 중 단계가 어떤 동작으로 열리는지 (review · regen · quick_fix · style_change · intensity_change · retry) */
    private var pendingGenerateSource: String? = null
    /** 시트가 어떻게 닫혔는지 (cancel_button · saved · deleted). 비어 있으면 아래로 쓸어 닫은 것 */
    var sheetClose: String? = null

    /** session_id 를 넘기지 않으면 지금 세션을 붙여요 */
    fun track(event: String, vararg props: Pair<String, Any?>) {
        val hasSession = props.any { it.first == "session_id" }
        val all = if (hasSession || sessionId == null) props else arrayOf(*props, "session_id" to sessionId)
        AnalyticsService.track(event, *all)
    }

    fun setGenerateSource(s: String) { pendingGenerateSource = s }

    /** 생성 중 단계가 열릴 때 한 번 꺼내요. 기록이 없으면 이어서 보기(resume) */
    fun takeGenerateSource(): String {
        val s = pendingGenerateSource ?: "resume"
        pendingGenerateSource = null
        return s
    }

    /** 시트가 닫힌 뒤 — 취소 버튼이면 cancel_button, 저장 · 삭제가 아니면 쓸어 닫기 */
    fun takeSheetClose(): String? {
        val m = sheetClose
        val hint = AnalyticsService.sheetCloseHint
        sheetClose = null
        AnalyticsService.sheetCloseHint = null
        return when (m) {
            "saved", "deleted" -> null
            null -> hint ?: "swipe_down"
            else -> m
        }
    }

    val isDirty: Boolean? get() = dirtyProvider?.invoke()
}

/** from_screen 값 */
val GoalCreationRoute.analyticsScreen: String
    get() = when (this) {
        is GoalCreationRoute.start -> "start"
        is GoalCreationRoute.resume -> "resume"
        is GoalCreationRoute.chat -> "chat"
        is GoalCreationRoute.review -> "review"
        is GoalCreationRoute.generating -> "generating"
        is GoalCreationRoute.draft -> "draft"
    }

val GoalCreationRoute.analyticsSessionId: Int?
    get() = when (this) {
        is GoalCreationRoute.start -> null
        is GoalCreationRoute.resume -> sessionId
        is GoalCreationRoute.chat -> sessionId
        is GoalCreationRoute.review -> sessionId
        is GoalCreationRoute.generating -> sessionId
        is GoalCreationRoute.draft -> sessionId
    }

/** item_kind 값 (goal / routine / one_time / sub_goal / milestone) */
val DraftEditTarget.analyticsKind: String
    get() = when (this) {
        is DraftEditTarget.goal -> "goal"
        is DraftEditTarget.subGoal -> "sub_goal"
        is DraftEditTarget.milestone -> "milestone"
        is DraftEditTarget.task -> if (type == DraftTaskType.ROUTINE) "routine" else "one_time"
    }

/** API 오류 → error_status (네트워크 · 알 수 없는 오류는 0) */
fun gcErrorStatus(e: Throwable?): Int = (e as? ApiError)?.httpStatus ?: 0
