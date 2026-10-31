// Features/Goal/GoalDetailLogic.swift
//
// 목표 화면들이 같이 쓰는 계산 (화면과 분리한 순수 함수).
// 웹 원본:
//   goal/ui/SubGoalsSection.tsx (activeSubGoals · areasOf · bucketOf · defaultPeriod)
//   goal/edit/inline.tsx (colorIdOf · toNum)
//   goal/ui/metricSentence.ts
//   goal/ui/GoalHeader.tsx (motiveLabel)
//   goal/ui/RoutinesSection.tsx (routineTimeText)
//   goal/ui/GoalTaskList.tsx (dayOf · dayLabel · isDone)
//   goal/ui/progress/ProgressSection.tsx (fmtNum · md)

import Foundation

enum GoalDetailLogic {
    // MARK: 세부 목표(영역)

    /// 화면에 보여줄 세부 목표 (삭제된 것 제외 — 백엔드가 소프트 삭제된 것도 내려줘요)
    static func activeSubGoals(_ goal: GoalCategory) -> [SubGoalInfo] {
        goal.generalCategories.filter { $0.generalCategoryStatus != .DELETED }
    }

    /// 사용자가 만든 영역만 ("기타 할 일" 그릇 제외)
    static func areas(_ goal: GoalCategory) -> [SubGoalInfo] {
        activeSubGoals(goal).filter { !($0.defaultBucket ?? false) }
    }

    /// 목표의 "기타 할 일" 그릇 (영역을 안 고른 루틴·Task 가 들어가요)
    static func bucket(_ goal: GoalCategory) -> SubGoalInfo? {
        activeSubGoals(goal).first { $0.defaultBucket ?? false }
    }

    /// 새로 만들 때 기본 기간: 목표 기간 (기록형은 종료일이 없어서 오늘부터 3개월)
    static func defaultPeriod(_ goal: GoalCategory, today: String = KST.today()) -> (start: String, end: String) {
        let start = goal.goalCategoryStartDate ?? today
        let end = goal.goalCategoryEndDate ?? (goal.isRecordGoal ? KST.addDays(today, 90) : start)
        return (start, end < start ? start : end)
    }

    /// 마일스톤을 날짜순으로
    static func sortedMilestones(_ goal: GoalCategory) -> [MilestoneInfo] {
        (goal.milestones ?? []).sorted { $0.dueDate < $1.dueDate }
    }

    // MARK: 색 · 숫자

    /// colorCode("3D5AFE" | "#3d5afe") → 내 색상 목록의 id (없으면 기본 색 → 첫 색)
    static func colorIdOf(_ colors: [UserColor], _ code: String?) -> Int? {
        guard !colors.isEmpty else { return nil }
        func norm(_ v: String) -> String { v.replacingOccurrences(of: "#", with: "").uppercased() }
        if let code, let hit = colors.first(where: { norm($0.hex) == norm(code) }) { return hit.userColorId }
        return (colors.first { $0.isDefault } ?? colors[0]).userColorId
    }

    /// 웹 toNum: "" → nil, "1.5" → 1.5, 숫자가 아니면 .nan
    static func toNum(_ v: String) -> Double? {
        let t = v.trimmingCharacters(in: .whitespaces)
        if t.isEmpty { return nil }
        return Double(t) ?? .nan
    }

    /// 소수 digits 자리까지 쓰고 뒤의 0 은 지워요 (웹 String(Number(v.toFixed(2))))
    static func trim(_ v: Double, _ digits: Int = 2) -> String {
        guard v.isFinite else { return "-" }
        var s = String(format: "%.\(digits)f", v)
        if s.contains(".") {
            while s.hasSuffix("0") { s.removeLast() }
            if s.hasSuffix(".") { s.removeLast() }
        }
        if s == "-0" { s = "0" }
        return s
    }

    /// 72 → "72", 72.456 → "72.46"
    static func fmtNum(_ v: Double) -> String { trim(v, 2) }

    /// 값 + 단위 (소수 2자리)
    static func withUnit(_ v: Double, _ unit: String?) -> String { "\(trim(v, 2))\(unit ?? "")" }

    /// 계산으로 나온 값(계획값 · 차이)은 소수 1자리까지만
    static func withUnit1(_ v: Double, _ unit: String?) -> String { "\(trim(v, 1))\(unit ?? "")" }

    /// "2026-10-08" → "10월 8일"
    static func md(_ ymd: String) -> String { "\(KST.monthOf(ymd))월 \(KST.dayOfMonth(ymd))일" }

    // MARK: 수치 목표 한 문장

    /// "체중 80kg → 72kg, 1월 8일까지 · 주 평균 -0.6kg"
    static func metricSentence(name: String, unit: String, start: Double?, target: Double?,
                               startDate: String?, endDate: String?) -> String? {
        guard let start, let target, !start.isNaN, !target.isNaN else { return nil }
        let u = unit.trimmingCharacters(in: .whitespaces)
        let trimmedName = name.trimmingCharacters(in: .whitespaces)
        let n = trimmedName.isEmpty ? "수치" : trimmedName
        var text = "\(n) \(trim(start, 2))\(u) → \(trim(target, 2))\(u)"
        if let endDate, !endDate.isEmpty {
            text += ", \(KST.monthOf(endDate))월 \(KST.dayOfMonth(endDate))일까지"
        }
        if let startDate, let endDate, !startDate.isEmpty, !endDate.isEmpty, endDate > startDate {
            let days = Double(KST.diffDays(startDate, endDate))
            let perWeek = (target - start) / max(days / 7, 1)
            if perWeek.isFinite && perWeek != 0 {
                let sign = perWeek > 0 ? "+" : ""
                text += " · 주 평균 \(sign)\(trim(perWeek, 1))\(u)"
            }
        }
        return text
    }

    // MARK: 머리말

    /// motive 칸의 제목 — 업무형은 "시작한 이유" 대신 만들 때 받은 한 줄 설명
    static func motiveLabel(_ goal: GoalCategory) -> String {
        guard goal.isRecordGoal else { return "이 목표를 시작한 이유" }
        // 업무형은 회사 업무 · 취업 준비 · 동아리처럼 종류를 나누지 않아요
        return "무엇을 하나요"
    }

    // MARK: 루틴

    /// "19:30:00" → "19:30" (없으면 "")
    static func hhmmOrEmpty(_ t: String?) -> String { GoalFormat.hhmm(t) ?? "" }

    /// 루틴 시간 한 줄 — 요일별 시간이 있으면 시간이 같은 요일끼리 묶어서 "월·금 20:00~20:25 · 수 07:00~07:30"
    static func routineTimeText(_ r: RoutineSummary) -> String {
        func fmt(_ s: String?, _ e: String?) -> String {
            guard let s, !s.isEmpty else { return "시간 자동" }
            if let e, !e.isEmpty { return "\(s)~\(e)" }
            return s
        }
        let base = GoalFormat.hhmm(r.repeatStartTime)
        let baseEnd = GoalFormat.hhmm(r.repeatEndTime)
        let dayTimes = r.dayTimes ?? []
        if dayTimes.isEmpty { return fmt(base, baseEnd) }
        // 순서를 지키는 묶음 (Dictionary 는 순서가 없어서 배열로)
        var keys: [String] = []
        var groups: [String: [JavaDayOfWeek]] = [:]
        for d in JavaDayOfWeek.allCases where r.repeatDays.contains(d) {
            let own = dayTimes.first { $0.dayOfWeek == d }
            let key = own.map { fmt(GoalFormat.hhmm($0.startTime), GoalFormat.hhmm($0.endTime)) } ?? fmt(base, baseEnd)
            if groups[key] == nil { keys.append(key) }
            groups[key, default: []].append(d)
        }
        return keys.map { k in "\((groups[k] ?? []).map(\.label).joined(separator: "·")) \(k)" }.joined(separator: " · ")
    }

    // MARK: Task

    static func isDone(_ t: TimeBlockTask) -> Bool { t.taskStatus == .COMPLETED }

    /// 이 Task 가 화면에 놓이는 날짜 (완료 후 실제 → 계획 → 시간 미정 날짜 → 권장일 → 마감일)
    static func dayOf(_ t: TimeBlockTask) -> String? {
        if let shown = TimetableLayout.shownTime(t) { return String(shown.startAt.prefix(10)) }
        return t.date ?? t.scheduledDate ?? t.dueDate
    }

    /// "오늘" · "내일" · "어제" · "10.08 (목)" (해가 다르면 "2027.01.03 (일)")
    static func dayLabel(_ ymd: String, today: String) -> String {
        if ymd == today { return "오늘" }
        if ymd == KST.addDays(today, 1) { return "내일" }
        if ymd == KST.addDays(today, -1) { return "어제" }
        let p = KST.parts(ymd)
        let md = String(format: "%02d.%02d", p.m, p.d)
        let date = p.y == KST.yearOf(today) ? md : "\(p.y).\(md)"
        return "\(date) (\(KST.weekdayLabel[KST.weekdayIndex(ymd)]))"
    }
}
