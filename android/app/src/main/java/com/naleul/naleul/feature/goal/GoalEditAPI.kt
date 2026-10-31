// Features/Goal/GoalEditAPI.kt
//
// iOS GoalEditAPI.swift — 웹 features/goal/edit/mutations.ts (목표 · 세부 목표 · 루틴 · 마일스톤 · 말로 고치기)
// + 웹 features/goal/api.ts 의 나머지 저장 요청 (수치 기록 · 오늘 기록 · 주간 점검 · 결과 · 마무리)
// + 웹 features/record/api.ts 의 useKeepAsRecord (임시 목표 → 업무형) · useRecordPattern 일부 (목록 카드의 연속 주)
//
// 여기는 "서버에 보내기"만 해요. 토스트 · 다시 불러오기(Invalidation)는 화면 쪽에서 Mutation.run 으로 해요.
// 웹 규칙 (무엇을 다시 불러오는지):
//   - 목표 편집(useGoalMutation)          → [goals, timetable]
//   - 목표 만들기 / 업무형으로 두기         → [goals]
//   - 수치 기록 · 오늘 기록 · 기록 지우기    → 진행 데이터를 바로 바꾸고 [goals]
//   - 주간 점검 적용                       → 진행 데이터를 바로 바꾸고 [goals, timetable] (실패하면 [goals])
//   - 주간 점검 넘기기 · 결과 고르기         → 진행 데이터만 바로 바꿔요 (다시 불러오기 없음)
//   - 목표 마무리                          → 성공하면 [goals, timetable]

package com.naleul.naleul.feature.goal

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

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// MARK: - 요청 입력 모양

/** 직접 목표 만들기 (웹 GoalCreateInput + subGoals) */
data class GoalCreateInput(
    var goalCategoryName: String,
    var goalCategoryStartDate: String,
    var goalCategoryEndDate: String,
    var colorId: Int,
    var motive: String? = null,
    var emoji: String? = null,
    /** 수치 목표 — 넣을 때만 (이름이 있을 때) */
    var metricName: String? = null,
    var metricUnit: String? = null,
    var startValue: Double? = null,
    var targetValue: Double? = null,
    var hasMetric: Boolean = false,
    var kind: GoalKindValue? = null,
    var subGoals: List<String> = emptyList(),
) {
    val json: JsonObject
        get() {
            val o = mutableMapOf<String, JsonElement>(
                "goalCategoryName" to JsonPrimitive(goalCategoryName),
                "goalCategoryStartDate" to JsonPrimitive(goalCategoryStartDate),
                "goalCategoryEndDate" to JsonPrimitive(goalCategoryEndDate),
                "colorId" to JsonPrimitive(colorId),
            )
            // 웹은 undefined(보내지 않음)로 둬요
            motive?.let { o["motive"] = JsonPrimitive(it) }
            emoji?.let { o["emoji"] = JsonPrimitive(it) }
            for ((k, v) in GoalKind.body(kind)) o[k] = v
            if (hasMetric) {
                o["metricName"] = toJsonValue(metricName)
                o["metricUnit"] = toJsonValue(metricUnit)
                o["startValue"] = toJsonValue(startValue)
                o["targetValue"] = toJsonValue(targetValue)
            }
            return JsonObject(o)
        }

    companion object {}
}

/** 루틴 추가 · 수정 (웹 RoutineInput) */
data class GoalRoutineInput(
    /** 영역 — null 이면 서버가 목표의 "기타 할 일"에 넣어요 */
    var generalCategoryId: Int?,
    var routineName: String,
    var repeatStartDate: String,
    var repeatEndDate: String,
    var repeatDays: List<JavaDayOfWeek>,
    /** "HH:mm" — 없으면 null (자동 배치가 시간을 정해요) */
    var repeatStartTime: String?,
    var repeatEndTime: String?,
    var notificationEnabled: Boolean,
    /** 하는 방법 — 수정 때 "" 를 보내면 지워져요 */
    var description: String,
    /** 기본 시간과 다른 요일만. [] 를 보내면 모든 요일이 기본 시간으로 돌아가요 */
    var dayTimes: List<RoutineDayTime>,
) {
    val json: JsonObject
        // 속성 이름(json)과 함수 이름이 같아서 함수는 패키지 이름까지 붙여 불러요
        get() = com.naleul.naleul.core.network.json(
            "generalCategoryId" to generalCategoryId,
            "routineName" to routineName,
            "repeatStartDate" to repeatStartDate,
            "repeatEndDate" to repeatEndDate,
            "repeatDays" to repeatDays.map { it.name },
            "repeatStartTime" to repeatStartTime,
            "repeatEndTime" to repeatEndTime,
            "notificationEnabled" to notificationEnabled,
            "description" to description,
            "dayTimes" to dayTimes.map { t ->
                com.naleul.naleul.core.network.json("dayOfWeek" to t.dayOfWeek.name, "startTime" to t.startTime, "endTime" to t.endTime)
            },
        )

    companion object {}
}

// MARK: - 말로 고치기 (AI 변경안 미리보기 → 확인 → 적용)

/** 변경 하나. op 는 미리보기에서 받은 그대로 적용할 때 다시 보내야 해서 JSON 그대로 들고 있어요 */
@Serializable
data class GoalEditChange(
    val op: JsonElement,
    /** "추가" · "변경" · "삭제" */
    val action: String,
    val target: String,
    val before: String? = null,
    val after: String? = null,
) {
    companion object {}
}

/** summary · changes 가 없으면 빈 값으로 (iOS 커스텀 디코딩과 같아요) */
@Serializable
data class GoalEditPreview(
    val summary: String = "",
    val changes: List<GoalEditChange> = emptyList(),
) {
    companion object {}
}

@Serializable
private data class GoalEditAPIApplyResult(val applied: Int? = null)

// MARK: - 업무형 목표 카드의 "연속 완료" (웹 record/api RecordPattern 중 목록 카드가 쓰는 부분만)

data class GoalWorkStreak(
    val weekStreak: Int,
    val thisWeekDone: Boolean,
    /** 최근 12주, 오래된 것부터 */
    val weeks: List<Week>,
) {
    @Serializable
    data class Week(val weekStart: String, val count: Int) {
        companion object {}
    }

    companion object {}
}

// 응답은 { summary: { weekStreak, thisWeekDone }, rhythm: { weeks } } 처럼 한 겹 더 감싸져 있어서
// 이 모양으로 먼저 받고 GoalWorkStreak 으로 펴요 (iOS 의 nestedContainer 디코딩과 같아요)
@Serializable
private data class GoalEditAPIStreakSummary(val weekStreak: Int = 0, val thisWeekDone: Boolean = false)

@Serializable
private data class GoalEditAPIStreakRhythm(val weeks: List<GoalWorkStreak.Week> = emptyList())

@Serializable
private data class GoalEditAPIStreakRaw(
    val summary: GoalEditAPIStreakSummary = GoalEditAPIStreakSummary(),
    val rhythm: GoalEditAPIStreakRhythm? = null,
)

// MARK: - 요청

object GoalEditAPI {
    /** createGoal 결과 (iOS 튜플 (goal:, failed:)) */
    data class CreatedGoal(val goal: GoalCategory, val failed: Int) {
        companion object {}
    }

    // ── 전체 목표 ──

    /**
     * 직접 목표 만들기 — 목표를 만든 뒤 적어 둔 세부 목표들을 같은 기간·색으로 이어서 만들어요.
     * 세부 목표 하나가 실패해도 목표는 이미 만들어졌으니 실패 개수만 돌려줘요.
     */
    suspend fun createGoal(input: GoalCreateInput): CreatedGoal {
        val created = API.post<GoalCategory>("/v1/goal-categories", input.json)
            ?: throw ApiError.Http(500, "문제가 생겼어요. 다시 시도해 주세요.", null, null)
        var failed = 0
        for (name in input.subGoals) {
            try {
                API.send(
                    "POST", "/v1/general-categories",
                    json(
                        "generalCategoryName" to name,
                        "goalCategoryId" to created.goalCategoryId,
                        "generalCategoryStartDate" to input.goalCategoryStartDate,
                        "generalCategoryEndDate" to input.goalCategoryEndDate,
                        "colorId" to input.colorId,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += 1
            }
        }
        return CreatedGoal(created, failed)
    }

    /** 목표 수정 (웹 GoalUpdateInput — 넣은 필드만 바뀌어요. 문자열은 "" 를 보내면 지워져요) */
    suspend fun updateGoal(goalId: Int, body: JsonElement) {
        API.send("PATCH", "/v1/goal-categories/$goalId", body)
    }

    /** 목표 삭제 — 세부 목표·루틴(과 루틴 Task)도 함께 지워져요 */
    suspend fun deleteGoal(goalId: Int) {
        API.send("DELETE", "/v1/goal-categories/$goalId")
    }

    /** 임시 목표를 업무형(기록형)으로 두기 */
    suspend fun keepAsRecord(goalId: Int) {
        API.send("PATCH", "/v1/goal-categories/$goalId/keep-as-record")
    }

    /** AI 없이 만든 목표형 목표를 업무형으로 바꾸기 (2026-10 · 목표 형태 정하기) */
    suspend fun convertToRecord(goalId: Int) {
        API.send("PATCH", "/v1/goal-categories/$goalId/convert-to-record")
    }

    // ── 세부 목표 (general category) ──

    suspend fun createSubGoal(goalId: Int, name: String, start: String, end: String, colorId: Int) {
        API.send(
            "POST", "/v1/general-categories",
            json(
                "generalCategoryName" to name,
                "generalCategoryStartDate" to start,
                "generalCategoryEndDate" to end,
                "colorId" to colorId,
                "goalCategoryId" to goalId,
            ),
        )
    }

    /** colorId 가 null 이면 색은 그대로 */
    suspend fun updateSubGoal(id: Int, name: String, start: String, end: String, colorId: Int?) {
        API.send(
            "PUT", "/v1/general-categories/$id",
            jsonCompact(
                "generalCategoryName" to name,
                "generalCategoryStartDate" to start,
                "generalCategoryEndDate" to end,
                "colorId" to colorId,
            ),
        )
    }

    suspend fun deleteSubGoal(id: Int) {
        API.send("DELETE", "/v1/general-categories/$id")
    }

    // ── 루틴 ──

    suspend fun createRoutine(goalId: Int, input: GoalRoutineInput) {
        val body = input.json.toMutableMap()
        body["goalCategoryId"] = JsonPrimitive(goalId)
        API.send("POST", "/v1/routines", JsonObject(body))
    }

    /** 루틴 수정 — 오늘 이후의 루틴 Task 가 새 설정으로 다시 만들어져요 (지난 기록은 그대로) */
    suspend fun updateRoutine(id: Int, input: GoalRoutineInput) {
        API.send("PATCH", "/v1/routines/$id", input.json)
    }

    /**
     * 루틴을 다른 날에도 했을 때 — 반복 요일은 그대로 두고 그날 하루만 루틴 블록을 만들어요.
     * startTime · endTime 이 null 이면 루틴 시간. done 이면 그 시간을 실제로 한 시간으로 남겨요 (앞날은 안 돼요)
     */
    suspend fun addRoutineExtraDay(routineId: Int, date: String, startTime: String?, endTime: String?, done: Boolean) {
        API.send(
            "POST", "/v1/routines/$routineId/extra-days",
            json("date" to date, "startTime" to startTime, "endTime" to endTime, "done" to done),
        )
    }

    suspend fun deleteRoutine(id: Int) {
        API.send("DELETE", "/v1/routines/$id")
    }

    // ── 마일스톤 (점검 시점) ──

    /** targetValue 를 보내면 "직접 정한 값", null 이면 시작값 → 목표값 직선에서 자동 계산 */
    suspend fun createMilestone(goalId: Int, title: String, description: String, dueDate: String, targetValue: Double?) {
        API.send(
            "POST", "/v1/goal-categories/$goalId/milestones",
            json("title" to title, "description" to description, "dueDate" to dueDate, "targetValue" to targetValue),
        )
    }

    /** 마일스톤 수정 · 달성 체크. body 에는 바꿀 필드만 (title, description, dueDate, targetValue, autoTargetValue, status) */
    suspend fun updateMilestone(id: Int, body: JsonElement) {
        API.send("PATCH", "/v1/milestones/$id", body)
    }

    suspend fun deleteMilestone(id: Int) {
        API.send("DELETE", "/v1/milestones/$id")
    }

    // ── 말로 고치기 ──

    /** 미리보기 — 아직 아무것도 바뀌지 않아요 */
    suspend fun editPreview(goalId: Int, instruction: String): GoalEditPreview {
        val r = API.post<GoalEditPreview>("/v1/goal-categories/$goalId/ai-edit/preview", json("instruction" to instruction))
        return r ?: GoalEditPreview(summary = "", changes = emptyList())
    }

    /** 고른 변경만 적용 */
    suspend fun editApply(goalId: Int, ops: List<JsonElement>) {
        API.post<GoalEditAPIApplyResult>("/v1/goal-categories/$goalId/ai-edit/apply", json("ops" to ops))
    }

    // ── 진행 (수치 기록 · 오늘 기록 · 주간 점검 · 결과 · 마무리) — 응답은 진행 데이터 전체 ──

    /** 수치 기록 (같은 날짜는 덮어써요) */
    suspend fun recordMetric(goalId: Int, date: String?, value: Double, memo: String? = null): GoalProgress? =
        API.put<GoalProgress>(
            "/v1/goal-categories/$goalId/metric-logs",
            jsonCompact("date" to date, "value" to value, "memo" to memo),
        )

    /**
     * 오늘 기록 + 회고 한 번에 (mood·note 를 모두 비우면 그날 회고를 지워요)
     * @param date 없으면 오늘 (홈에서 지난날 기록을 남길 때 넣어요. 앞날은 서버가 막아요)
     */
    suspend fun checkIn(goalId: Int, date: String? = null, value: Double?, mood: ReflectionMood?, note: String?): GoalProgress? =
        API.put<GoalProgress>(
            "/v1/goal-categories/$goalId/check-in",
            json("date" to date, "value" to value, "mood" to mood?.rawValue, "note" to note),
        )

    suspend fun deleteMetricLog(goalId: Int, date: String): GoalProgress? =
        API.delete<GoalProgress>("/v1/goal-categories/$goalId/metric-logs/$date")

    /** 주간 점검 제안 적용 — 내일부터 루틴 요일이 바뀌어요 */
    suspend fun applyAdjustment(goalId: Int, key: String): GoalProgress? =
        API.post<GoalProgress>("/v1/goal-categories/$goalId/adjustments/apply", json("key" to key))

    /** 주간 점검 제안 넘기기 — 같은 제안은 7일 동안 다시 안 보여요 */
    suspend fun dismissAdjustment(goalId: Int, key: String): GoalProgress? =
        API.post<GoalProgress>("/v1/goal-categories/$goalId/adjustments/dismiss", json("key" to key))

    /** 끝난 목표의 결과를 직접 고르기 (자동 판정을 덮어써요) */
    suspend fun updateOutcome(goalId: Int, result: OutcomeResult): GoalProgress? =
        API.patch<GoalProgress>("/v1/goal-categories/$goalId/outcome", json("result" to result.rawValue))

    /** 목표 마무리 — 결과 · 소감과 함께 끝내요. 이후 예정된 Task 는 서버가 정리해요 */
    suspend fun completeGoal(goalId: Int, endDate: String, achievement: String?, result: OutcomeResult) {
        API.send(
            "PATCH", "/v1/goal-categories/$goalId/complete",
            json("goalCategoryEndDate" to endDate, "achievement" to achievement, "result" to result.rawValue),
        )
    }

    // ── 업무형 목표 카드 ──

    suspend fun workStreak(goalId: Int): GoalWorkStreak? {
        val raw = API.get<GoalEditAPIStreakRaw>("/v1/activities/goals/$goalId/pattern") ?: return null
        return GoalWorkStreak(
            weekStreak = raw.summary.weekStreak,
            thisWeekDone = raw.summary.thisWeekDone,
            weeks = raw.rhythm?.weeks ?: emptyList(),
        )
    }
}
