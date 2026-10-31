// feature/calendar/TimetableSampleData.kt
//
// iOS Features/Calendar/TimetableSampleData.swift 를 옮긴 파일.
// @Preview 에서 네트워크 없이 캘린더를 그려 보기 위한 샘플 데이터 (앱 실행 중에는 쓰지 않아요).
// 백엔드 응답과 같은 JSON 을 만들어 디코딩해요 → 모델의 기본값 처리까지 같이 확인돼요.

package com.naleul.naleul.feature.calendar

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

import kotlinx.serialization.builtins.ListSerializer

object TimetableSampleData {
    /** start ~ end 날짜의 샘플 TimeTable */
    fun days(from: String, to: String): List<TimetableDay> {
        val out = mutableListOf<TimetableDay>()
        var d = from
        var i = 0
        while (d <= to && i < 42) {
            out.add(day(d, index = i))
            d = KST.addDays(d, 1)
            i += 1
        }
        return out
    }

    fun day(d: String, index: Int): TimetableDay {
        val i = index
        val prev = KST.addDays(d, -1)
        val next = KST.addDays(d, 1)
        val today = KST.today()
        val done = d < today
        val unscheduled = if (i % 3 == 0) {
            """
            [{"taskId": ${9000 + i}, "taskName": "보고서 초안 쓰기", "emoji": "📝", "taskStatus": "TODO",
              "sourceType": "MANUAL", "goalColorCode": "F59E0B", "goalCategoryName": "커리어", "goalCategoryId": 2,
              "generalCategoryId": 21, "date": "$d", "plannedDurationMinutes": 60}]
            """
        } else {
            "[]"
        }
        val json = """
        {
          "date": "$d",
          "dayRange": {"start": "${d}T07:00:00", "end": "${d}T23:30:00"},
          "fixedBlocks": [
            {"lifePatternId": 2, "patternType": "LUNCH", "title": "점심", "emoji": "🍚",
             "targetDate": "$d", "start": "${d}T12:00:00", "end": "${d}T13:00:00", "overridden": ${i == 2}}
          ],
          "tasks": [
            {"taskId": ${1000 + i}, "taskName": "아침 스트레칭", "emoji": "🧘", "taskStatus": "${if (done) "COMPLETED" else "TODO"}",
             "sourceType": "ROUTINE", "goalColorCode": "12B76A", "goalCategoryName": "건강", "goalCategoryId": 1,
             "date": "$d", "plannedStartAt": "${d}T07:30:00", "plannedEndAt": "${d}T08:00:00", "placedBy": "USER"},
            {"taskId": ${2000 + i}, "taskName": "영어 단어 외우기", "taskStatus": "TODO", "sourceType": "MANUAL",
             "goalColorCode": "3D5AFE", "goalCategoryName": "토익 900", "goalCategoryId": 3, "generalCategoryId": 31,
             "generalCategoryName": "단어", "date": "$d", "plannedStartAt": "${d}T09:00:00", "plannedEndAt": "${d}T10:30:00",
             "placedBy": "ENGINE", "placementReason": "오전에 집중이 잘 돼요. 최근 4주 오전 Task 12개 중 10개를 해냈어요.",
             "placementReasonCodes": ["HIGH_EFFICIENCY"], "missed": $done},
            {"taskId": ${3000 + i}, "taskName": "팀 회의", "emoji": "👥", "taskStatus": "TODO", "sourceType": "MANUAL",
             "taskKind": "APPOINTMENT", "goalColorCode": "8B5CF6", "date": "$d", "locked": true,
             "plannedStartAt": "${d}T14:00:00", "plannedEndAt": "${d}T15:00:00", "placedBy": "USER"}
          ],
          "unscheduledTasks": $unscheduled,
          "stats": {"total": 3, "completed": ${if (done) 1 else 0}, "missed": ${if (done) 1 else 0}, "rate": ${if (done) 33 else 0}},
          "sleep": {
            "wake": {"lifePatternId": 1, "patternType": "SLEEP", "title": "수면", "emoji": "😴",
                     "targetDate": "$d", "start": "${prev}T23:30:00", "end": "${d}T07:00:00"},
            "bed": {"lifePatternId": 1, "patternType": "SLEEP", "title": "수면", "emoji": "😴",
                    "targetDate": "$next", "start": "${d}T23:30:00", "end": "${next}T07:00:00"}
          }
        }
        """
        // 샘플이 잘못되면 미리보기에서 바로 알 수 있게 그대로 던져요 (앱 코드에서는 쓰지 않아요)
        return NJson.decodeFromString(TimetableDay.serializer(), json)
    }

    fun activities(on: String): List<ActualActivity> {
        val d = on
        val json = """
        [{"activityId": 1, "title": "친구 만남", "emoji": "☕️", "startAt": "${d}T10:40:00", "endAt": "${d}T11:40:00",
          "durationMinutes": 60, "replacedTaskName": "영어 단어 외우기", "replacedTaskId": 2000}]
        """
        return NJson.decodeFromString(ListSerializer(ActualActivity.serializer()), json)
    }

    val task: TimeBlockTask
        get() = day(KST.today(), index = 1).tasks[1]

    fun monthly(year: Int, month: Int): MonthlyTimetableResponse {
        val last = KST.lastDayOfMonth(year, month)
        val items = mutableListOf<String>()
        for (dd in 1..last) {
            val d = KST.ymd(year, month, dd)
            val milestone = if (dd == 15) {
                """[{"milestoneId": 1, "title": "모의고사 800점", "colorCode": "3D5AFE", "goalCategoryId": 3}]"""
            } else {
                "[]"
            }
            items.add(
                """
                {"date": "$d", "rate": ${dd * 7 % 100}, "taskCount": 3, "completedCount": 1,
                 "chips": [{"taskId": $dd, "title": "영어 단어", "colorCode": "3D5AFE", "completed": ${dd % 2 == 0}, "temporaryGoal": false},
                           {"taskId": ${100 + dd}, "title": "운동", "colorCode": "12B76A", "completed": false, "temporaryGoal": false}],
                 "moreCount": ${dd % 4}, "milestones": $milestone}
                """
            )
        }
        val json = """
        {"year": $year, "month": $month, "days": [${items.joinToString(",")}],
         "deadlinesThisWeek": [{"type": "TASK", "id": 5, "title": "보고서 제출", "date": "${KST.today()}", "colorCode": "F59E0B"},
                               {"type": "MILESTONE", "id": 1, "title": "모의고사 800점", "date": "${KST.addDays(KST.today(), 3)}", "colorCode": "3D5AFE"}]}
        """
        return NJson.decodeFromString(MonthlyTimetableResponse.serializer(), json)
    }
}
