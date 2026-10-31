// feature/goalcreation/GoalCreationAPI.kt
//
// iOS Features/GoalCreation/GoalCreationAPI.swift
// AI 목표 만들기 API · "성향을 직접 골랐는지" 기억.
// 웹 원본: features/goal-creation/api.ts · planningStyleMemory.ts
//
// 웹 queryKey ['goal-creation', …] → DataKey.goalCreation
// (세션 · 초안 조회는 화면이 직접 불러오고, "이어서 만들기" 카드만 DataKey.goalCreation 을 구독해요)

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

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object GoalCreationAPI {
    private const val base = "/v1/goal-creation/sessions"

    /** 응답 data 가 비어 있으면(있어야 하는데) 에러로 */
    private fun <T> need(v: T?): T = v ?: throw ApiError.Decoding("empty data")

    /**
     * 세션 시작 + 첫 질문 (201)
     * - sourceGoalId: 임시 목표를 구체화할 때. 확정하면 그 임시 목표의 Task·기록이 새 목표로 옮겨져요.
     * - initialMessage 를 비우면 서버가 (임시 목표 이름으로 / AI 가 먼저 물어보며) 시작해요.
     */
    suspend fun start(
        initialMessage: String?,
        sourceGoalId: Int?,
        kind: GoalKindValue?,
        mustDoItems: List<String>,
    ): GoalCreationTurnResponse {
        // 고른 카테고리 → AI 가 카테고리에 맞는 질문 · 계획 가이드를 써요
        val body = GoalKind.body(kind).toMutableMap<String, JsonElement>()
        if (!initialMessage.isNullOrEmpty()) body["initialMessage"] = JsonPrimitive(initialMessage)
        if (sourceGoalId != null) body["sourceGoalId"] = JsonPrimitive(sourceGoalId)
        // 꼭 하고 싶은 일 → 인터뷰가 다시 묻지 않고, 계획에 반드시 루틴으로 넣어요
        if (mustDoItems.isNotEmpty()) body["mustDoItems"] = jsonArray(mustDoItems)
        val r = API.post<GoalCreationTurnResponse>(base, JsonObject(body))
        return need(r)
    }

    /** 진행 중인 최신 세션. 없으면 204 → null */
    suspend fun getActive(): GoalCreationSessionDetail? =
        API.get<GoalCreationSessionDetail>("$base/active")

    suspend fun getSession(sessionId: Int): GoalCreationSessionDetail =
        need(API.get<GoalCreationSessionDetail>("$base/$sessionId"))

    /**
     * 답변 / 건너뛰기 (content 가 null 이면 JSON null 로 보내요).
     * answerSource("CHIP" · "TYPED") · chipIndex: 칩을 눌러 답했는지 직접 썼는지 — 운영 대시보드 "AI 채팅 정리 정확도" 전용 (화면 변화 없음)
     */
    suspend fun answer(
        sessionId: Int,
        content: String?,
        skip: Boolean,
        answerSource: String? = null,
        chipIndex: Int? = null,
    ): GoalCreationTurnResponse {
        val meta = jsonCompact("answerSource" to answerSource, "chipIndex" to if (answerSource == "CHIP") chipIndex else null)
        val body = JsonObject(json("content" to content, "skip" to skip) + meta)
        return need(API.post<GoalCreationTurnResponse>("$base/$sessionId/answers", body))
    }

    /** "바로 계획 만들기" */
    suspend fun finishInterview(sessionId: Int): GoalCreationTurnResponse =
        need(API.post<GoalCreationTurnResponse>("$base/$sessionId/finish-interview"))

    /** 정리 확인 화면에서 슬롯 수정 (바꾼 것만) */
    suspend fun updateSlots(sessionId: Int, patch: GoalCreationSlotsPatch): GoalCreationTurnResponse =
        need(API.requestEncodable<GoalCreationTurnResponse, GoalCreationSlotsPatch>("PATCH", "$base/$sessionId/slots", patch))

    /** 취소 (ABANDONED) */
    suspend fun cancel(sessionId: Int) {
        API.send("DELETE", "$base/$sessionId")
    }

    /**
     * 초안 생성 / 다시 생성 (202, 비동기).
     * basePlan 을 같이 보내면 "말로 고치기": 화면에서 직접 고친 지금 초안을 기준으로 feedback 부분만 바꿔요.
     */
    suspend fun requestDraft(sessionId: Int, body: GoalCreationDraftRequestBody): GoalCreationDraftRequested =
        need(API.requestEncodable<GoalCreationDraftRequested, GoalCreationDraftRequestBody>("POST", "$base/$sessionId/drafts", body))

    /** 초안 폴링 */
    suspend fun getDraft(sessionId: Int, draftId: Int): GoalCreationDraftResponse {
        val r = need(API.get<GoalCreationDraftResponse>("$base/$sessionId/drafts/$draftId"))
        // 혹시 "07:00:00" 처럼 오더라도 "HH:mm" 으로 맞춰요 (iOS 는 디코딩할 때 prefix(5))
        val plan = r.plan ?: return r
        return r.copy(plan = plan.copy(tasks = plan.tasks.map { t -> t.copy(preferredStartTime = t.preferredStartTime?.take(5)) }))
    }

    /** "이 목표로 시작하기" → 201 { goalId }, 검증 실패 422 + data.violations */
    suspend fun confirm(sessionId: Int, body: GoalCreationConfirmRequest): GoalCreationConfirmResponse =
        need(API.requestEncodable<GoalCreationConfirmResponse, GoalCreationConfirmRequest>("POST", "$base/$sessionId/confirm", body))
}

/**
 * "사용자가 성향을 직접 골랐는지"를 기억해요 (확정 요청의 planningStyleSelectedByUser 에 필요).
 * 웹은 탭을 닫으면 사라지는 sessionStorage 를 써요 → 앱이 살아 있는 동안만 기억하는 메모리 값.
 * (화면 코드에서만 부르니 메인 스레드에서만 써요)
 */
object GoalCreationStyleMemory {
    private val selected = mutableSetOf<Int>()

    fun markSelectedByUser(sessionId: Int) {
        selected.add(sessionId)
    }

    fun wasSelectedByUser(sessionId: Int): Boolean = selected.contains(sessionId)
}
