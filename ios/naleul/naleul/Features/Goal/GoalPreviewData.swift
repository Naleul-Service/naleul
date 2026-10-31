// Features/Goal/GoalPreviewData.swift
//
// #Preview 전용 샘플 데이터 (네트워크 없이 화면을 그려 보려고). 앱 동작에는 쓰지 않아요.
// 백엔드 응답 JSON 을 그대로 디코딩해서 만들어요 (모델 디코딩 규칙도 같이 확인돼요).

import Foundation

enum GoalPreviewData {
    private static func decode<T: Decodable>(_ type: T.Type, _ json: String) -> T {
        // 미리보기 전용이라 실패하면 바로 알 수 있게 강제로 풀어요
        try! JSONDecoder().decode(T.self, from: Data(json.utf8))
    }

    private static var today: String { KST.today() }

    /// 수치 목표가 있는 달성형 목표 (체중 80 → 72kg)
    static var goal: GoalCategory {
        let start = KST.addDays(today, -30)
        let end = KST.addDays(today, 60)
        return decode(GoalCategory.self, """
        {
          "goalCategoryId": 1, "goalCategoryName": "3개월에 8kg 빼기", "goalCategoryStatus": "IN_PROGRESS",
          "goalCategoryStartDate": "\(start)", "goalCategoryEndDate": "\(end)",
          "motive": "건강검진 결과가 안 좋아서 시작했어요", "colorCode": "3D5AFE", "emoji": "🥗",
          "aiGenerated": true, "goalMode": "ACHIEVEMENT", "goalType": "HEALTH", "goalSubType": "DIET",
          "goalKindName": "건강 · 다이어트",
          "aiNote": "주 3회 운동과 저녁 식단 관리가 핵심이에요.",
          "metricName": "체중", "metricUnit": "kg", "startValue": 80, "currentValue": 77.4, "targetValue": 72,
          "milestones": [
            {"milestoneId": 11, "title": "1개월 차 점검", "dueDate": "\(KST.addDays(today, -2))", "targetValue": 77.3, "status": "ACHIEVED"},
            {"milestoneId": 12, "title": "2개월 차 점검", "dueDate": "\(KST.addDays(today, 28))", "targetValue": 74.7, "status": "PENDING"},
            {"milestoneId": 13, "title": "최종 목표", "dueDate": "\(end)", "targetValue": 72, "status": "PENDING"}
          ],
          "generalCategories": [
            {"generalCategoryId": 21, "generalCategoryName": "식단 관리", "colorCode": "12B76A",
             "generalCategoryStartDate": "\(start)", "generalCategoryEndDate": "\(end)", "generalCategoryStatus": "IN_PROGRESS",
             "description": "하루 1,600kcal, 단백질 90g", "emoji": "🥗",
             "routines": [
               {"routineId": 31, "routineName": "저녁 샐러드", "routineStatus": "ACTIVE", "repeatStartDate": "\(start)",
                "repeatEndDate": "\(end)", "repeatStartTime": "19:00:00", "repeatEndTime": "19:30:00",
                "repeatDays": ["MONDAY","WEDNESDAY","FRIDAY"], "notificationEnabled": true}
             ]},
            {"generalCategoryId": 22, "generalCategoryName": "기타 할 일", "generalCategoryStatus": "IN_PROGRESS", "defaultBucket": true,
             "routines": [
               {"routineId": 32, "routineName": "하체 근력 30분", "routineStatus": "ACTIVE", "repeatStartDate": "\(start)",
                "repeatEndDate": "\(end)", "repeatDays": ["TUESDAY","THURSDAY","SATURDAY"], "notificationEnabled": false,
                "description": "스쿼트 4x10 → 런지 3x12", "durationMinutes": 30,
                "dayTimes": [{"dayOfWeek": "SATURDAY", "startTime": "10:00", "endTime": "10:30"}]}
             ]}
          ]
        }
        """)
    }

    /// 업무형(기록형) 목표
    static var recordGoal: GoalCategory {
        decode(GoalCategory.self, """
        {
          "goalCategoryId": 2, "goalCategoryName": "나를 업무", "goalCategoryStatus": "IN_PROGRESS",
          "goalCategoryStartDate": "\(KST.addDays(today, -60))", "colorCode": "F59E0B", "emoji": "💼",
          "goalMode": "RECORD", "goalType": "WORK", "goalSubType": "COMPANY_WORK",
          "motive": "iOS 앱 개발과 백엔드 운영", "generalCategories": [], "pausedDays": 15
        }
        """)
    }

    /// Brain dump 에서 만들어진 임시 목표
    static var tempGoal: GoalCategory {
        decode(GoalCategory.self, """
        {
          "goalCategoryId": 3, "goalCategoryName": "영어 공부", "goalCategoryStatus": "IN_PROGRESS",
          "colorCode": "12B76A", "temporary": true, "generalCategories": []
        }
        """)
    }

    /// 끝난 목표
    static var doneGoal: GoalCategory {
        decode(GoalCategory.self, """
        {
          "goalCategoryId": 4, "goalCategoryName": "정보처리기사 필기 합격", "goalCategoryStatus": "COMPLETED",
          "goalCategoryStartDate": "2026-03-01", "goalCategoryEndDate": "2026-05-20", "colorCode": "8B5CF6",
          "emoji": "📜", "achievement": "기출 5회독으로 합격했어요!", "generalCategories": []
        }
        """)
    }

    static var tasks: GoalTasksResponse {
        decode(GoalTasksResponse.self, """
        {
          "totalCount": 5, "todoCount": 3, "completedCount": 2,
          "tasks": [
            {"taskId": 101, "taskName": "식단 기록하기", "emoji": "📝", "taskStatus": "TODO", "sourceType": "MANUAL",
             "goalCategoryId": 1, "goalColorCode": "3D5AFE", "generalCategoryName": "식단 관리",
             "date": "\(today)", "plannedStartAt": "\(today)T21:00:00", "plannedEndAt": "\(today)T21:20:00"},
            {"taskId": 102, "taskName": "하체 근력 30분", "taskStatus": "TODO", "sourceType": "ROUTINE", "routineId": 32,
             "goalCategoryId": 1, "goalColorCode": "3D5AFE", "date": "\(KST.addDays(today, 1))"},
            {"taskId": 103, "taskName": "장보기", "taskStatus": "TODO", "sourceType": "MANUAL", "source": "AI_GOAL",
             "goalCategoryId": 1, "goalColorCode": "3D5AFE", "date": "\(KST.addDays(today, -1))", "missed": true,
             "milestoneId": 12, "milestoneTitle": "2개월 차 점검"},
            {"taskId": 104, "taskName": "저녁 샐러드", "taskStatus": "COMPLETED", "sourceType": "ROUTINE",
             "goalCategoryId": 1, "goalColorCode": "3D5AFE", "date": "\(KST.addDays(today, -2))",
             "plannedStartAt": "\(KST.addDays(today, -2))T19:00:00", "plannedEndAt": "\(KST.addDays(today, -2))T19:30:00"},
            {"taskId": 105, "taskName": "인바디 측정", "taskStatus": "COMPLETED", "sourceType": "MANUAL",
             "goalCategoryId": 1, "goalColorCode": "3D5AFE", "date": "\(KST.addDays(today, -9))"}
          ]
        }
        """)
    }

    static var progress: GoalProgress {
        let start = KST.addDays(today, -30)
        let end = KST.addDays(today, 60)
        return decode(GoalProgress.self, """
        {
          "goalCategoryId": 1, "today": "\(today)", "startDate": "\(start)", "endDate": "\(end)",
          "elapsedDays": 30, "totalDays": 90,
          "metric": {
            "name": "체중", "unit": "kg", "startValue": 80, "targetValue": 72, "currentValue": 77.4,
            "expectedValue": 77.3, "progressPercent": 32.5, "status": "ON_TRACK",
            "projectedDate": "\(KST.addDays(today, 65))", "lastLoggedDate": "\(KST.addDays(today, -1))", "loggedToday": false,
            "planLine": [
              {"date": "\(start)", "value": 80, "label": "시작"},
              {"date": "\(KST.addDays(today, -2))", "value": 77.3, "label": "1개월 차 점검"},
              {"date": "\(end)", "value": 72, "label": "목표"}
            ],
            "logs": [
              {"date": "\(KST.addDays(today, -20))", "value": 79.1},
              {"date": "\(KST.addDays(today, -10))", "value": 78.2, "memo": "회식 다음 날"},
              {"date": "\(KST.addDays(today, -1))", "value": 77.4}
            ]
          },
          "tasks": {"total": 5, "completed": 2, "dueByToday": 4, "completionPercent": 50,
                    "series": [{"date": "\(start)", "planned": 0, "completed": 0}, {"date": "\(today)", "planned": 4, "completed": 2}]},
          "reflections": [
            {"date": "\(KST.addDays(today, -1))", "mood": "GOOD", "note": "점심 샐러드, 저녁 30분 걸음"},
            {"date": "\(KST.addDays(today, -3))", "mood": "BAD", "note": "야근이라 운동 못 함"}
          ],
          "adjustment": {"key": "r32-down", "direction": "DOWN", "routineId": 32, "routineName": "하체 근력 30분",
                         "fromDays": 3, "toDays": 2, "day": "SATURDAY", "previousWeekRate": 33, "lastWeekRate": 0,
                         "message": "2주 연속 실천률이 낮아요. 토요일을 빼고 주 2회로 해 볼까요?"}
        }
        """)
    }

    /// 수치 없는 목표의 진행 (Task 누적)
    static var taskOnlyProgress: GoalProgress {
        let start = KST.addDays(today, -14)
        return decode(GoalProgress.self, """
        {
          "goalCategoryId": 5, "today": "\(today)", "startDate": "\(start)", "endDate": "\(KST.addDays(today, 14))",
          "elapsedDays": 14, "totalDays": 28,
          "tasks": {"total": 10, "completed": 4, "dueByToday": 6, "completionPercent": 66.7,
                    "series": [
                      {"date": "\(start)", "planned": 0, "completed": 0},
                      {"date": "\(KST.addDays(today, -7))", "planned": 3, "completed": 2},
                      {"date": "\(today)", "planned": 6, "completed": 4},
                      {"date": "\(KST.addDays(today, 14))", "planned": 10}
                    ]},
          "outcome": {"result": "PARTIAL", "source": "AUTO", "routineRate": 64, "taskRate": 70}
        }
        """)
    }

    static var heatmap: [RoutineHeatmap] {
        let start = KST.startOfWeek(KST.addDays(today, -35))
        var done: [String] = []
        var d = start
        var i = 0
        while d < today {
            if [0, 2, 4].contains(KST.weekdayIndex(d)) && i % 3 != 2 { done.append(d) }
            d = KST.addDays(d, 1)
            i += 1
        }
        let list = done.map { "\"\($0)\"" }.joined(separator: ",")
        return decode([RoutineHeatmap].self, """
        [
          {"routineId": 31, "routineName": "저녁 샐러드", "repeatStartDate": "\(start)", "repeatEndDate": "\(KST.addDays(today, 60))",
           "repeatDays": ["MONDAY","WEDNESDAY","FRIDAY"], "completedDates": [\(list)], "currentStreak": 3, "longestStreak": 5}
        ]
        """)
    }

    static var workStreak: GoalWorkStreak {
        let w0 = KST.startOfWeek(today)
        let weeks = (0..<8).map { i in
            GoalWorkStreak.Week(weekStart: KST.addDays(w0, (i - 7) * 7), count: [0, 2, 0, 3, 1, 4, 2, 0][i])
        }
        return GoalWorkStreak(weekStreak: 3, thisWeekDone: false, weeks: weeks)
    }

    static var colors: [UserColor] {
        [
            UserColor(userColorId: 1, hex: "#3D5AFE", isDefault: true),
            UserColor(userColorId: 2, hex: "#12B76A", isDefault: false),
            UserColor(userColorId: 3, hex: "#F59E0B", isDefault: false),
            UserColor(userColorId: 4, hex: "#8B5CF6", isDefault: false),
        ]
    }
}
