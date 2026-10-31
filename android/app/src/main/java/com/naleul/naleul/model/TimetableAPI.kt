// model/TimetableAPI.kt
//
// iOS Models/TimetableAPI.swift 를 옮긴 파일.
// 웹 features/timetable/api.ts 의 요청 함수들. (토스트 · 다시 불러오기는 화면에서 Mutation.run 으로 감싸요)
//
//   val res = TimetableAPI.timetable(start = "2026-11-16", end = "2026-11-22")
//   Mutation.run(invalidate = listOf(DataKey.timetable), success = { "완료했어요." }) {
//       TimetableAPI.toggleComplete(taskId = t.taskId, currentStatus = t.taskStatus)
//   }
//
// 웹 규칙: 캘린더에서 하는 변경은 끝나면 DataKey.timetable 을 다시 불러와요
// (밀려난 블록·덮인 고정 시간·실행률이 같이 바뀌므로 부분 수정보다 다시 조회가 안전)

package com.naleul.naleul.model

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

object TimetableAPI {
    // MARK: 조회

    /** GET /timetable?startDate&endDate (최대 7일) */
    suspend fun timetable(start: String, end: String): TimetableResponse {
        val r = API.get<TimetableResponse>("/v1/timetable?startDate=$start&endDate=$end")
        return r ?: TimetableResponse(days = emptyList())
    }

    /** GET /timetable/monthly?year&month */
    suspend fun monthly(year: Int, month: Int): MonthlyTimetableResponse? =
        API.get<MonthlyTimetableResponse>("/v1/timetable/monthly?year=$year&month=$month")

    /** GET /timetable/goal-time?startDate&endDate (최대 42일) — 캘린더의 "목표별 실천 시간" */
    suspend fun goalTime(start: String, end: String): GoalTimeResponse? =
        API.get<GoalTimeResponse>("/v1/timetable/goal-time?startDate=$start&endDate=$end")

    /** 기본 생활 패턴 목록 */
    suspend fun lifePatterns(): List<LifePattern> {
        val r = API.get<List<LifePattern>>("/v1/life-patterns")
        return r ?: emptyList()
    }

    /** 기간(시작일~종료일, 최대 42일)과 겹치는 실제 기록 */
    suspend fun activities(start: String, end: String): List<ActualActivity> {
        val r = API.get<List<ActualActivity>>("/v1/activities?startDate=$start&endDate=$end")
        return r ?: emptyList()
    }

    // MARK: Task 완료 · 삭제 · 고정

    /** 완료 ↔ 완료 취소 (지금 상태의 반대로) */
    suspend fun toggleComplete(taskId: Int, currentStatus: TaskStatus) {
        val action = if (currentStatus == TaskStatus.COMPLETED) "cancel-complete" else "complete"
        API.send("PATCH", "/v1/tasks/$taskId/$action")
    }

    /** 완료 + 실제 시각 기록 ("계획과 다른 시간에 했어요") */
    suspend fun completeWithActual(taskId: Int, actualStartAt: String, actualEndAt: String) {
        API.send(
            "PATCH", "/v1/task-actuals/$taskId/actual",
            json("actualStartAt" to actualStartAt, "actualEndAt" to actualEndAt),
        )
    }

    /**
     * 여러 번 나눠서 한 실제 시간 (2개 이상) — 서버가 첫 시작 ~ 마지막 끝 + 구간을 같이 저장해요.
     * iOS 는 튜플 [(start:, end:)] 였는데, Kotlin 은 같은 필드 이름의 ActualSegment(start, end) 목록을 받아요.
     */
    suspend fun completeWithSegments(taskId: Int, segments: List<ActualSegment>) {
        val arr = segments.map { json("start" to it.start, "end" to it.end) }
        API.send("PATCH", "/v1/task-actuals/$taskId/actual", json("segments" to arr))
    }

    suspend fun deleteTask(taskId: Int) {
        API.send("DELETE", "/v1/tasks/$taskId")
    }

    /** 고정(📌) 풀기 — 다음 자동 배치 때 옮겨질 수 있어요 */
    suspend fun unlockTask(taskId: Int) {
        API.send("PATCH", "/v1/tasks/$taskId/unlock")
    }

    /** 우선순위만 바꾸기 — 시간은 그대로, 루틴 · 미션은 그날 Task 만 (PATCH /tasks/{id}/priority) */
    suspend fun changePriority(taskId: Int, priority: TaskPriority): TimeBlockTask? =
        API.patch<TimeBlockTask>("/v1/tasks/$taskId/priority", json("taskPriority" to priority.rawValue))

    /** 시간 미정으로 보내기 */
    suspend fun unscheduleTask(taskId: Int) {
        API.send("DELETE", "/v1/tasks/$taskId/schedule")
    }

    /**
     * 다른 날로 옮기기 (2026-10) — PATCH /tasks/{id}/date
     * keepTime = true: 같은 시각으로 그날에 놓아 봐요 (자리가 없으면 그날 시간 미정) · false: 그날 시간 미정으로
     */
    suspend fun moveTaskDate(taskId: Int, date: String, keepTime: Boolean): TimeBlockTask? =
        API.patch<TimeBlockTask>("/v1/tasks/$taskId/date", json("date" to date, "keepTime" to keepTime))

    // MARK: 시간 바꾸기

    /** 시간 변경 (드래그·직접 입력 공통). 저장하면 그 시간에 고정(📌)돼요 */
    suspend fun reschedule(taskId: Int, plannedStartAt: String, plannedEndAt: String): TaskScheduleResponse? =
        API.patch<TaskScheduleResponse>(
            "/v1/tasks/$taskId/schedule",
            json("plannedStartAt" to plannedStartAt, "plannedEndAt" to plannedEndAt),
        )

    /** 묶음 이동의 한 항목 (iOS TimetableAPI.ScheduleItem) */
    data class ScheduleItem(
        val taskId: Int,
        val plannedStartAt: String,
        val plannedEndAt: String,
    ) {
        companion object {}
    }

    /** 여러 Task 를 한 번에 옮기기 (묶음 드래그) — 하나라도 자리가 없으면 아무것도 바뀌지 않아요 */
    suspend fun rescheduleBatch(items: List<ScheduleItem>): TaskBatchScheduleResponse? {
        val arr = items.map {
            json("taskId" to it.taskId, "plannedStartAt" to it.plannedStartAt, "plannedEndAt" to it.plannedEndAt)
        }
        return API.patch<TaskBatchScheduleResponse>("/v1/tasks/schedule/batch", json("items" to arr))
    }

    /** 루틴 블록 시간 변경 + 범위. DAY 는 기존 드래그와 같아요 */
    suspend fun routineScopedReschedule(
        taskId: Int,
        plannedStartAt: String,
        plannedEndAt: String,
        scope: ChangeScope,
    ): RoutineScheduleResponse? =
        API.patch<RoutineScheduleResponse>(
            "/v1/tasks/$taskId/routine-schedule",
            json(
                "plannedStartAt" to plannedStartAt,
                "plannedEndAt" to plannedEndAt,
                "scope" to scope.rawValue,
            ),
        )

    // MARK: 22시 이월 제안

    /** "옮길까요?" → 옮기기 */
    suspend fun acceptCarry(taskId: Int) {
        API.send("PATCH", "/v1/tasks/$taskId/carry-over/accept")
    }

    /** "옮길까요?" → 안 옮기기 (그날 "시간 미정"으로) */
    suspend fun declineCarry(taskId: Int) {
        API.send("PATCH", "/v1/tasks/$taskId/carry-over/decline")
    }

    /** 이월 되돌리기 — 원래 날짜 · 시각으로 (자리가 없으면 그날 시간 미정). 자동 이월이 다시 옮기지 않아요 (2026-10) */
    suspend fun revertCarry(taskId: Int, from: String? = null): TimeBlockTask? =
        API.patch<TimeBlockTask>("/v1/tasks/$taskId/carry-over/revert" + (from?.let { "?from=$it" } ?: ""))

    /** 그날 이월 제안 모두 옮기기 → 옮긴 개수 */
    suspend fun acceptAllCarry(date: String): Int {
        val n = API.patch<Int>("/v1/tasks/carry-over/accept-all?date=$date")
        return n ?: 0
    }

    // MARK: 고정 시간 (생활 패턴)

    /** 그날만 변경 / 비우기 (null 인 값은 아예 안 보내요 — iOS JSONValue.compact) */
    suspend fun patternOverride(
        lifePatternId: Int,
        targetDate: String,
        startTime: String? = null,
        endTime: String? = null,
        skipped: Boolean? = null,
    ) {
        API.send(
            "PUT", "/v1/life-patterns/$lifePatternId/overrides/$targetDate",
            jsonCompact("startTime" to startTime, "endTime" to endTime, "skipped" to skipped),
        )
    }

    /** 기본 시간으로 되돌리기 */
    suspend fun deletePatternOverride(lifePatternId: Int, targetDate: String) {
        API.send("DELETE", "/v1/life-patterns/$lifePatternId/overrides/$targetDate")
    }

    /** 고정 시간 변경 + 범위. FOLLOWING · WEEKDAY 는 기본값이 바뀌니 DataKey.lifePatterns 도 다시 불러와요 */
    suspend fun patternScopedChange(
        lifePatternId: Int,
        targetDate: String,
        startTime: String,
        endTime: String,
        scope: ChangeScope,
        startDayOffset: Int = 0,
    ) {
        API.send(
            "PUT", "/v1/life-patterns/$lifePatternId/scoped-change",
            json(
                "targetDate" to targetDate,
                "startTime" to startTime,
                "endTime" to endTime,
                "scope" to scope.rawValue,
                "startDayOffset" to startDayOffset,
            ),
        )
    }

    // MARK: 자동 배치

    /** "다시 배치" — 그날의 자동 배치 블록과 시간 미정 Task 를 다시 놓아요. 지난 날짜는 400 이라 오늘 이후만 보내요 */
    suspend fun replan(dates: List<String>): List<ReplanResponse> {
        val out = mutableListOf<ReplanResponse>()
        for (d in dates) {
            val r = API.post<ReplanResponse>("/v1/daily-plans/$d/replan")
            if (r != null) out.add(r)
        }
        return out
    }

    /** AI 로 배치 미리보기 — 기간 전체 */
    suspend fun fillPreview(startDate: String, endDate: String): FillPreviewResponse {
        val r = API.post<FillPreviewResponse>("/v1/daily-plans/fill/preview?startDate=$startDate&endDate=$endDate")
        return r ?: FillPreviewResponse(proposals = emptyList(), unscheduled = emptyList())
    }

    /** AI 로 배치 미리보기 — 정한 Task 들만 (방금 추가한 Task) */
    suspend fun fillPreview(taskIds: List<Int>): FillPreviewResponse {
        val r = API.post<FillPreviewResponse>("/v1/daily-plans/fill/preview-tasks", json("taskIds" to taskIds))
        return r ?: FillPreviewResponse(proposals = emptyList(), unscheduled = emptyList())
    }

    /** 확정할 제안 하나 (iOS TimetableAPI.FillApplyItem) */
    data class FillApplyItem(
        val taskId: Int,
        val proposedStart: String,
        val proposedEnd: String,
        val start: String,
        val end: String,
        val reason: String? = null,
    ) {
        companion object {}
    }

    /** 점선 제안을 최종 자리로 확정 (그대로 둔 것 = AI 배치, 옮긴 것 = 📌 직접 정한 시간). timetable · goals 다시 불러오기 */
    suspend fun fillApply(items: List<FillApplyItem>): FillApplyResponse? {
        val arr = items.map {
            json(
                "taskId" to it.taskId,
                "proposedStart" to it.proposedStart,
                "proposedEnd" to it.proposedEnd,
                "start" to it.start,
                "end" to it.end,
                "reason" to it.reason,
            )
        }
        return API.post<FillApplyResponse>("/v1/daily-plans/fill/apply", json("items" to arr))
    }

    /** 바로 저장 배치 (Task 추가 화면용). 시간 미정 Task 만 남은 빈 시간에 넣어요. timetable · goals 다시 불러오기 */
    suspend fun fill(startDate: String, endDate: String): FillResponse? =
        API.post<FillResponse>("/v1/daily-plans/fill?startDate=$startDate&endDate=$endDate")

    /** 바로 저장 배치 결과 토스트 (웹 useFillTimetable onSuccess 와 같은 문구). 메인 스레드에서 불러요 */
    fun toastFillResult(r: FillResponse?) {
        if (r == null) return
        if (r.placedCount == 0 && r.unscheduledCount == 0) {
            Toast.show("배치할 시간 미정 Task가 없었어요.")
        } else if (r.placedCount == 0) {
            Toast.error("빈 시간이 없어 ${r.unscheduledCount}개는 시간 미정으로 남았어요.")
        } else {
            Toast.success(
                "${r.placedCount}개 Task를 빈 시간에 배치했어요." +
                    (if (r.unscheduledCount > 0) " ${r.unscheduledCount}개는 빈 시간이 없어 시간 미정으로 남았어요." else "")
            )
        }
    }

    // MARK: 실제로 한 일

    /** 추가(activityId 없음) · 수정 */
    suspend fun saveActivity(input: ActivityInput, activityId: Int? = null): ActualActivity? {
        if (activityId != null) {
            return API.put<ActualActivity>("/v1/activities/$activityId", input.json)
        }
        return API.post<ActualActivity>("/v1/activities", input.json)
    }

    suspend fun deleteActivity(activityId: Int) {
        API.send("DELETE", "/v1/activities/$activityId")
    }

    // MARK: 안내 문장

    /** 드래그 결과 안내 문장: 밀려난 블록 · 덮은 고정 시간 */
    fun scheduleResultMessage(r: TaskScheduleResponse?): String {
        if (r == null) return "시간을 바꿨어요."
        val parts = mutableListOf<String>()
        val c = r.coveredFixedBlocks
        if (!c.isNullOrEmpty()) parts.add("${c.joinToString("·") { it.title }} 시간을 덮었어요")
        val m = r.moved
        if (!m.isNullOrEmpty()) parts.add("${m.size}개 블록을 다른 시간으로 옮겼어요")
        val u = r.unscheduled
        if (!u.isNullOrEmpty()) parts.add("${u.size}개는 빈 시간이 없어 시간 미정이 됐어요")
        return if (parts.isEmpty()) "시간을 바꿨어요." else "시간을 바꿨어요 · ${parts.joinToString(", ")}."
    }

    /** 묶음 드래그 결과 문장 */
    fun batchResultMessage(r: TaskBatchScheduleResponse?, count: Int): String {
        val single = r?.let {
            TaskScheduleResponse(
                task = it.tasks.firstOrNull(),
                moved = it.moved,
                unscheduled = it.unscheduled,
                coveredFixedBlocks = it.coveredFixedBlocks,
            )
        }
        return scheduleResultMessage(single).replace("시간을 바꿨어요", "${count}개를 함께 옮겼어요")
    }

    /** 루틴 범위 변경 결과 문장 */
    fun routineScopeMessage(r: RoutineScheduleResponse?, scope: ChangeScope): String {
        if (r == null || scope == ChangeScope.DAY) return scheduleResultMessage(r?.result)
        val n = r.changedDates.size + 1
        val head = if (scope == ChangeScope.WEEK) "이번 주 루틴 ${n}일의 시간을 바꿨어요." else "앞으로의 루틴 시간을 바꿨어요."
        val skipped = if (r.skippedDates.isEmpty()) ""
        else " ${r.skippedDates.joinToString(", ") { KST.slashDate(it) }}은 다른 일정과 겹쳐 그대로예요."
        return head + skipped
    }

    /** 고정 시간 범위 변경 결과 문장 */
    fun patternScopeMessage(scope: ChangeScope): String = when (scope) {
        ChangeScope.DAY -> "이날만 시간을 바꿨어요."
        ChangeScope.WEEK -> "이번 주 시간을 바꿨어요."
        ChangeScope.WEEKDAY -> "앞으로 이 요일의 시간을 바꿨어요. 지난 기록은 그대로예요."
        ChangeScope.FOLLOWING -> "앞으로 매일의 시간을 바꿨어요. 지난 기록은 그대로예요."
    }
}
