// feature/goalcreation/InterviewChatModel.kt
//
// iOS Features/GoalCreation/InterviewChatModel.swift
// G-1 목표 대화 화면의 모든 상태와 동작.
// 웹 원본: features/goal-creation/hooks/useInterviewChat.ts
//
// 서버가 돌려주는 TurnResponse 를 그대로 화면 상태(meta)로 반영하고,
// 사용자가 보낸 말풍선은 응답을 기다리지 않고 먼저 그려요(낙관적 업데이트).

package com.naleul.naleul.feature.goalcreation

import com.naleul.naleul.app.*
import com.naleul.naleul.core.analytics.*
import com.naleul.naleul.core.network.*
import com.naleul.naleul.core.state.*
import com.naleul.naleul.core.util.*
import com.naleul.naleul.designsystem.*
import com.naleul.naleul.model.*
import com.naleul.naleul.feature.auth.*
import com.naleul.naleul.feature.calendar.*
import com.naleul.naleul.feature.calendarimport.*
import com.naleul.naleul.feature.goal.*
import com.naleul.naleul.feature.goalcreation.*
import com.naleul.naleul.feature.habit.*
import com.naleul.naleul.feature.home.*
import com.naleul.naleul.feature.notifications.*
import com.naleul.naleul.feature.pattern.*
import com.naleul.naleul.feature.record.*
import com.naleul.naleul.feature.settings.*
import com.naleul.naleul.feature.taskadd.*
import com.naleul.naleul.feature.work.*

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

enum class InterviewSendState {
    sending, failed;

    companion object {}
}

/** 화면에 그리는 말풍선 하나 */
data class InterviewChatItem(
    val key: String,
    val role: GoalCreationMessageRole,
    val content: String?,
    val skip: Boolean,
    val quickReplies: List<String>,
    val skippable: Boolean,
    /** ASSISTANT 질문이 묻는 칸 (마지막 "더 알려 줄 것" 질문은 extraNote) */
    val targetSlot: String? = null,
    /** USER 말풍선 전송 상태 */
    val sendState: InterviewSendState? = null,
    /** 실패 시 보여줄 이유 */
    val failedMessage: String? = null,
) {
    val id: String get() = key

    /** iOS init(_ m: GoalCreationMessage) */
    constructor(m: GoalCreationMessage) : this(
        key = "m-${m.messageId}",
        role = m.role,
        content = m.content,
        skip = m.skip ?: false,
        quickReplies = m.quickReplies ?: emptyList(),
        skippable = m.skippable ?: false,
        targetSlot = m.targetSlot,
    )

    companion object {}
}

data class InterviewMeta(
    val status: GoalCreationSessionStatus,
    val questionCount: Int,
    val maxQuestions: Int,
    val canFinishEarly: Boolean,
    val slots: GoalCreationSlots,
    val summary: GoalCreationSummary?,
) { companion object }

class InterviewChatModel(
    initial: GoalCreationSessionDetail,
    /** 409 등으로 서버 상태와 어긋났을 때 세션을 다시 불러오기 */
    var onResync: () -> Unit,
) : ScreenModel() {
    val sessionId: Int = initial.sessionId
    val items = mutableStateListOf<InterviewChatItem>().apply { addAll(initial.messages.map { InterviewChatItem(it) }.distinctBy { it.key }) }
    var meta by mutableStateOf(
        InterviewMeta(
            status = initial.status, questionCount = initial.questionCount,
            maxQuestions = initial.maxQuestions, canFinishEarly = initial.canFinishEarly,
            slots = initial.slots, summary = initial.summary,
        )
    )
    var input by mutableStateOf("")
    var pending by mutableStateOf(false)
    /** 부적절한 목표로 중단(422)됐을 때 안내 문구 */
    var rejectedMessage by mutableStateOf<String?>(null)

    /** 세션이 없어졌을 때(404) */
    var onNotFound: () -> Unit = {}

    // MARK: 화면에서 읽는 값

    /** 마지막 AI 질문 (빠른 답변 칩 / 건너뛰기 노출용) */
    val currentQuestion: InterviewChatItem?
        get() {
            val last = items.lastOrNull() ?: return null
            return if (last.role == GoalCreationMessageRole.ASSISTANT) last else null
        }
    val hasFailed: Boolean get() = items.any { it.sendState == InterviewSendState.failed }
    val interviewing: Boolean get() = meta.status == GoalCreationSessionStatus.INTERVIEWING && rejectedMessage == null
    /** 지금 입력/선택이 가능한지 */
    val canInteract: Boolean get() = interviewing && !pending && !hasFailed
    /** 인터뷰가 끝나서 정리 확인으로 넘어갈 수 있는지 */
    val done: Boolean get() = meta.status == GoalCreationSessionStatus.READY_TO_GENERATE && rejectedMessage == null

    // MARK: 내부

    private fun patchItem(key: String, change: (InterviewChatItem) -> InterviewChatItem) {
        val i = items.indexOfFirst { it.key == key }
        if (i < 0) return
        items[i] = change(items[i])
    }

    /** 방금 보낸 답의 종류 (text / quick_reply / skip / retry / finish_early) — 인터뷰가 끝난 이유에 써요 */
    private var lastAnswerType = "text"
    /** 마지막 실패를 어떻게 처리했는지 (answer_result 의 fail_handling) */
    private var lastFailHandling = "toast"

    private fun applyTurn(turn: GoalCreationTurnResponse) {
        if (meta.status != GoalCreationSessionStatus.READY_TO_GENERATE && turn.status == GoalCreationSessionStatus.READY_TO_GENERATE) {
            GCTracker.track(
                GCEvent.interviewComplete,
                "session_id" to sessionId,
                "complete_reason" to if (lastAnswerType == "finish_early") "finish_early" else "questions_done",
                "question_count" to turn.questionCount,
            )
        }
        meta = InterviewMeta(
            status = turn.status, questionCount = turn.questionCount, maxQuestions = turn.maxQuestions,
            canFinishEarly = turn.canFinishEarly, slots = turn.slots, summary = turn.summary,
        )
        // 502 뒤 다시 보내기 등으로 같은 메시지가 또 오면 건너뛰어요 (LazyColumn 키가 겹치면 앱이 죽어요)
        turn.message?.let { m ->
            val item = InterviewChatItem(m)
            if (items.none { it.key == item.key }) items.add(item)
        }
        // 목표 목록 · 시작 화면의 "이어서 만들기" 정보도 최신으로
        Invalidation.invalidate(DataKey.goalCreation)
    }

    private enum class ErrorResult { retry, drop }

    /** 공통 에러 처리 — 다시 보내기로 남겨둘지 여부를 돌려줘요 */
    private fun handleError(error: Throwable): ErrorResult {
        val e = error as? ApiError
        if (e == null) {
            Toast.error("문제가 생겼어요. 다시 시도해 주세요.")
            lastFailHandling = "retry"
            return ErrorResult.retry
        }
        val status = e.httpStatus
        val message = e.errorDescription
        if (isRetryable(status)) {
            lastFailHandling = "retry"
            return ErrorResult.retry
        }
        lastFailHandling = when (status) {
            422 -> "reject"
            404 -> "to_start"
            409 -> "resync"
            else -> "restore_input"
        }
        when (status) {
            422 -> {
                // 부적절한 목표 → 서버가 세션을 ABANDONED 로 바꿈
                GCTracker.track(GCEvent.goalRejected, "session_id" to sessionId, "question_count" to meta.questionCount)
                rejectedMessage = message
                meta = meta.copy(status = GoalCreationSessionStatus.ABANDONED)
                Invalidation.invalidate(DataKey.goalCreation)
            }
            404 -> {
                Toast.error("대화를 찾을 수 없어요.")
                onNotFound()
            }
            409 -> {
                // 다른 기기에서 진행됐거나 이미 끝난 대화 → 서버 상태로 다시 맞춤
                Toast.show(message)
                onResync()
            }
            // 400(건너뛸 수 없는 질문 등), 429(하루 한도) 등
            else -> Toast.error(message)
        }
        return ErrorResult.drop
    }

    // MARK: 동작

    /** answer_result — 요청이 끝난 직후 한 번 */
    private fun trackResult(type: String, error: Throwable?) {
        GCTracker.track(
            GCEvent.answerResult,
            "session_id" to sessionId,
            "answer_type" to type,
            "result" to if (error == null) "success" else "fail",
            "error_status" to error?.let { gcErrorStatus(it) },
            "fail_handling" to if (error == null) null else lastFailHandling,
            "question_count" to meta.questionCount,
        )
    }

    /** 답변 보내기 / 건너뛰기 / 다시 보내기. type: text / quick_reply / skip / retry */
    suspend fun send(content: String?, skip: Boolean, retryKey: String? = null, type: String = "text", chipIndex: Int? = null) {
        if (pending) return
        val text = content?.trim()
        if (!skip && text.isNullOrEmpty()) return

        pending = true
        try {
            val key: String
            if (retryKey != null) {
                key = retryKey
                patchItem(key) { it.copy(sendState = InterviewSendState.sending, failedMessage = null) }
            } else {
                tempSeq += 1
                key = "tmp-$tempSeq"
                items.add(
                    InterviewChatItem(
                        key = key, role = GoalCreationMessageRole.USER, content = text, skip = skip,
                        quickReplies = emptyList(), skippable = false, sendState = InterviewSendState.sending,
                    )
                )
                if (!skip) input = ""
            }

            lastAnswerType = type
            try {
                // 답 방식은 서버의 정리 정확도 측정용 (다시 보내기는 서버가 칩 글자와 비교해 추정)
                val source = when (type) {
                    "quick_reply" -> "CHIP"
                    "text" -> "TYPED"
                    else -> null
                }
                val turn = GoalCreationAPI.answer(
                    sessionId, content = if (skip) null else text, skip = skip,
                    answerSource = source, chipIndex = chipIndex,
                )
                patchItem(key) { it.copy(sendState = null) }
                applyTurn(turn)
                trackResult(type, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val handled = handleError(e)
                trackResult(type, e)
                if (handled == ErrorResult.retry) {
                    val msg = (e as? ApiError)?.errorDescription
                    patchItem(key) { it.copy(sendState = InterviewSendState.failed, failedMessage = msg) }
                } else {
                    // 서버가 받지 않은 말풍선은 지우고, 쓰던 글은 입력창에 돌려놓기
                    items.removeAll { it.key == key }
                    if (!skip && !text.isNullOrEmpty() && retryKey == null && input.isEmpty()) input = text
                }
            }
        } finally {
            pending = false
        }
    }

    suspend fun sendText() = send(input, skip = false, type = "text")
    suspend fun sendQuickReply(reply: String, index: Int? = null) =
        send(reply, skip = false, type = "quick_reply", chipIndex = index)
    suspend fun skipQuestion() = send(null, skip = true, type = "skip")

    /** 502 는 서버에 사용자 메시지가 이미 저장된 상태. 같은 내용으로 보내면 중복 저장되지 않아요. */
    suspend fun retry(key: String) {
        val item = items.firstOrNull { it.key == key } ?: return
        send(item.content, skip = item.skip, retryKey = key, type = "retry")
    }

    /** 실패한 말풍선을 지우고 입력창으로 되돌리기 */
    fun discardFailed(key: String) {
        val item = items.firstOrNull { it.key == key }
        items.removeAll { it.key == key }
        val c = item?.content
        if (item != null && !item.skip && c != null && input.isEmpty()) input = c
    }

    suspend fun finishEarly() {
        if (pending) return
        pending = true
        lastAnswerType = "finish_early"
        try {
            val turn = GoalCreationAPI.finishInterview(sessionId)
            applyTurn(turn)
            trackResult("finish_early", null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val handled = handleError(e)
            trackResult("finish_early", e)
            if (handled == ErrorResult.retry) Toast.error("계획 단계로 넘어가지 못했어요. 다시 시도해 주세요.")
        } finally {
            pending = false
        }
    }

    companion object {
        private var tempSeq = 0

        /** 다시 보내기가 의미 있는 에러: AI 실패(502), 네트워크/서버 연결 문제 */
        private fun isRetryable(status: Int): Boolean = status in listOf(0, 502, 503, 504)
    }
}
