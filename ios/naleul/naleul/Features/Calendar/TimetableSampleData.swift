// Features/Calendar/TimetableSampleData.swift
//
// #Preview 에서 네트워크 없이 캘린더를 그려 보기 위한 샘플 데이터 (앱 실행 중에는 쓰지 않아요).
// 백엔드 응답과 같은 JSON 을 만들어 디코딩해요 → 모델의 기본값 처리까지 같이 확인돼요.

import Foundation

enum TimetableSampleData {
    /// start ~ end 날짜의 샘플 TimeTable
    static func days(from start: String, to end: String) -> [TimetableDay] {
        var out: [TimetableDay] = []
        var d = start
        var i = 0
        while d <= end && i < 42 {
            out.append(day(d, index: i))
            d = KST.addDays(d, 1)
            i += 1
        }
        return out
    }

    static func day(_ d: String, index i: Int) -> TimetableDay {
        let prev = KST.addDays(d, -1)
        let next = KST.addDays(d, 1)
        let today = KST.today()
        let done = d < today
        let unscheduled = i % 3 == 0
            ? """
              [{"taskId": \(9000 + i), "taskName": "보고서 초안 쓰기", "emoji": "📝", "taskStatus": "TODO",
                "sourceType": "MANUAL", "goalColorCode": "F59E0B", "goalCategoryName": "커리어", "goalCategoryId": 2,
                "generalCategoryId": 21, "date": "\(d)", "plannedDurationMinutes": 60}]
              """
            : "[]"
        let json = """
        {
          "date": "\(d)",
          "dayRange": {"start": "\(d)T07:00:00", "end": "\(d)T23:30:00"},
          "fixedBlocks": [
            {"lifePatternId": 2, "patternType": "LUNCH", "title": "점심", "emoji": "🍚",
             "targetDate": "\(d)", "start": "\(d)T12:00:00", "end": "\(d)T13:00:00", "overridden": \(i == 2)}
          ],
          "tasks": [
            {"taskId": \(1000 + i), "taskName": "아침 스트레칭", "emoji": "🧘", "taskStatus": "\(done ? "COMPLETED" : "TODO")",
             "sourceType": "ROUTINE", "goalColorCode": "12B76A", "goalCategoryName": "건강", "goalCategoryId": 1,
             "date": "\(d)", "plannedStartAt": "\(d)T07:30:00", "plannedEndAt": "\(d)T08:00:00", "placedBy": "USER"},
            {"taskId": \(2000 + i), "taskName": "영어 단어 외우기", "taskStatus": "TODO", "sourceType": "MANUAL",
             "goalColorCode": "3D5AFE", "goalCategoryName": "토익 900", "goalCategoryId": 3, "generalCategoryId": 31,
             "generalCategoryName": "단어", "date": "\(d)", "plannedStartAt": "\(d)T09:00:00", "plannedEndAt": "\(d)T10:30:00",
             "placedBy": "ENGINE", "placementReason": "오전에 집중이 잘 돼요. 최근 4주 오전 Task 12개 중 10개를 해냈어요.",
             "placementReasonCodes": ["HIGH_EFFICIENCY"], "missed": \(done)},
            {"taskId": \(3000 + i), "taskName": "팀 회의", "emoji": "👥", "taskStatus": "TODO", "sourceType": "MANUAL",
             "taskKind": "APPOINTMENT", "goalColorCode": "8B5CF6", "date": "\(d)", "locked": true,
             "plannedStartAt": "\(d)T14:00:00", "plannedEndAt": "\(d)T15:00:00", "placedBy": "USER"}
          ],
          "unscheduledTasks": \(unscheduled),
          "stats": {"total": 3, "completed": \(done ? 1 : 0), "missed": \(done ? 1 : 0), "rate": \(done ? 33 : 0)},
          "sleep": {
            "wake": {"lifePatternId": 1, "patternType": "SLEEP", "title": "수면", "emoji": "😴",
                     "targetDate": "\(d)", "start": "\(prev)T23:30:00", "end": "\(d)T07:00:00"},
            "bed": {"lifePatternId": 1, "patternType": "SLEEP", "title": "수면", "emoji": "😴",
                    "targetDate": "\(next)", "start": "\(d)T23:30:00", "end": "\(next)T07:00:00"}
          }
        }
        """
        // 샘플이 잘못되면 미리보기에서 바로 알 수 있게 try! (앱 코드에서는 쓰지 않아요)
        return try! JSONDecoder().decode(TimetableDay.self, from: Data(json.utf8))
    }

    static func activities(on d: String) -> [ActualActivity] {
        let json = """
        [{"activityId": 1, "title": "친구 만남", "emoji": "☕️", "startAt": "\(d)T10:40:00", "endAt": "\(d)T11:40:00",
          "durationMinutes": 60, "replacedTaskName": "영어 단어 외우기", "replacedTaskId": 2000}]
        """
        return try! JSONDecoder().decode([ActualActivity].self, from: Data(json.utf8))
    }

    static var task: TimeBlockTask {
        day(KST.today(), index: 1).tasks[1]
    }

    static func monthly(year: Int, month: Int) -> MonthlyTimetableResponse {
        let last = KST.lastDayOfMonth(year: year, month: month)
        var items: [String] = []
        for dd in 1...last {
            let d = KST.ymd(year: year, month: month, day: dd)
            let milestone = dd == 15
                ? #"[{"milestoneId": 1, "title": "모의고사 800점", "colorCode": "3D5AFE", "goalCategoryId": 3}]"#
                : "[]"
            items.append("""
            {"date": "\(d)", "rate": \(dd * 7 % 100), "taskCount": 3, "completedCount": 1,
             "chips": [{"taskId": \(dd), "title": "영어 단어", "colorCode": "3D5AFE", "completed": \(dd % 2 == 0), "temporaryGoal": false},
                       {"taskId": \(100 + dd), "title": "운동", "colorCode": "12B76A", "completed": false, "temporaryGoal": false}],
             "moreCount": \(dd % 4), "milestones": \(milestone)}
            """)
        }
        let json = """
        {"year": \(year), "month": \(month), "days": [\(items.joined(separator: ","))],
         "deadlinesThisWeek": [{"type": "TASK", "id": 5, "title": "보고서 제출", "date": "\(KST.today())", "colorCode": "F59E0B"},
                               {"type": "MILESTONE", "id": 1, "title": "모의고사 800점", "date": "\(KST.addDays(KST.today(), 3))", "colorCode": "3D5AFE"}]}
        """
        return try! JSONDecoder().decode(MonthlyTimetableResponse.self, from: Data(json.utf8))
    }
}
