// model/GoalAPI.kt
//
// iOS Models/GoalAPI.swift 를 옮긴 파일.
// 웹 features/goal/api.ts 의 조회 · 공통 요청 (여러 화면이 같이 쓰는 것만 여기 있어요).
// 목표 상세의 추가·수정·삭제(웹 goal/edit/mutations.ts)는 feature/goal 안에 있어요.
//
// 웹 규칙: 목표를 바꾸면 [DataKey.goals, DataKey.timetable] 을 다시 불러와요.
//
// 모든 함수는 suspend (iOS async throws 와 같아요). 실패하면 ApiError 를 던져요.

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

import kotlinx.serialization.json.JsonObject

object GoalAPI {
    /**
     * 목표 목록 (삭제된 목표는 혹시 내려와도 제외)
     * @param includeHabit 기본 생활 습관 그릇(HABIT)도 같이 (맨 뒤) — 실제로 한 일 · 할 일의 목표를 직접 고르는 곳에서
     *   "기본 생활 습관"으로도 분류할 수 있게 (2026-10, 웹 useGoalCategories({ includeHabit }))
     */
    suspend fun goals(includeHabit: Boolean = false): List<GoalCategory> {
        val list = API.get<List<GoalCategory>>("/v1/goal-categories" + if (includeHabit) "?includeHabit=true" else "")
        val alive = (list ?: emptyList()).filter { it.goalCategoryStatus != GoalStatus.DELETED }
        // 서버는 습관 그릇을 맨 앞에 줘요 → 고르는 목록에선 맨 뒤로 (첫 목표가 기본 선택이라)
        return alive.filter { !it.isHabitGoal } + alive.filter { it.isHabitGoal }
    }

    suspend fun goal(id: Int): GoalCategory? =
        API.get<GoalCategory>("/v1/goal-categories/$id")

    /** 목표에 속한 Task 전체 (서버 정렬: 미완료 먼저 · 오늘과 가까운 날짜 먼저 · 완료는 아래) */
    suspend fun tasks(goalId: Int): GoalTasksResponse? =
        API.get<GoalTasksResponse>("/v1/goal-categories/$goalId/tasks")

    /**
     * 목표 상세에서 완료 체크 / 취소.
     * 끝나면 [goals, timetable, record] 다시 불러오기 (기록형 목표의 패턴·업무 일지도 완료한 Task 로 계산해요)
     */
    suspend fun toggleTask(taskId: Int, currentStatus: TaskStatus) {
        TimetableAPI.toggleComplete(taskId = taskId, currentStatus = currentStatus)
    }

    /** 루틴 실천 히트맵 */
    suspend fun routineHeatmap(goalId: Int): List<RoutineHeatmap> {
        val r = API.get<List<RoutineHeatmap>>("/v1/routines/$goalId/heatmap")
        return r ?: emptyList()
    }

    /** 진행 도표 ("지금 어디쯤?") */
    suspend fun progress(goalId: Int): GoalProgress? =
        API.get<GoalProgress>("/v1/goal-categories/$goalId/progress")

    // MARK: - 목표의 Task 추가 · 수정 (웹 goal/edit/mutations.ts — 캘린더 Task 상세에서도 써요)
    // 끝나면 [goals, timetable] 다시 불러오기
    // (iOS 는 extension GoalAPI 에 있었어요. 두 번째 인자는 Swift 에서 이름표가 없어서(_ input) 여기선 input 이에요)

    suspend fun createGoalTask(goalId: Int, input: GoalTaskInput): TimeBlockTask? =
        API.post<TimeBlockTask>("/v1/goal-categories/$goalId/tasks", input.json)

    suspend fun updateGoalTask(goalId: Int, taskId: Int, input: GoalTaskInput): TimeBlockTask? =
        API.put<TimeBlockTask>("/v1/goal-categories/$goalId/tasks/$taskId", input.json)

    suspend fun deleteTask(taskId: Int) {
        API.send("DELETE", "/v1/tasks/$taskId")
    }
}

/**
 * 목표 Task 입력값 (폼 상태).
 * iOS 처럼 `input.taskName = name` 으로 고쳐 쓰는 곳이 있어서 var 로 뒀어요.
 * ⚠️ Kotlin 객체는 참조라서, 상태에 들어 있는 값을 바꿀 땐 copy() 로 복사본을 만들어 바꾸세요.
 */
data class GoalTaskInput(
    var taskName: String,
    var emoji: String? = null,
    /** 영역 — null 이면 서버가 목표의 "기타 할 일"에 넣어요 */
    var generalCategoryId: Int? = null,
    var milestoneId: Int? = null,
    /** "YYYY-MM-DD" */
    var date: String,
    /** "HH:mm" — 둘 다 있거나 둘 다 null. 없으면 그날 "시간 미정"으로 두고 자동 배치가 시간을 정해요 */
    var startTime: String? = null,
    var endTime: String? = null,
    var durationMinutes: Int? = null,
    var dueDate: String? = null,
) {
    /** 웹은 null 을 그대로 보내요 (지우기) */
    val json: JsonObject
        get() = com.naleul.naleul.core.network.json(
            "taskName" to taskName,
            "emoji" to emoji,
            "generalCategoryId" to generalCategoryId,
            "milestoneId" to milestoneId,
            "date" to date,
            "startTime" to startTime,
            "endTime" to endTime,
            "durationMinutes" to durationMinutes,
            "dueDate" to dueDate,
        )

    companion object {}
}
