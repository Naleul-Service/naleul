// Core/Util/KST.swift
//
// 웹 features/timetable/time.ts 를 그대로 옮긴 날짜·시간 도우미.
//
// 백엔드는 "한국 시간 벽시계" 문자열을 주고받아요. (Z, +09:00 없음)
//   LocalDate      → "2026-11-18"
//   LocalDateTime  → "2026-11-18T09:30:00"
//   LocalTime      → "09:30:00"
// 기기 시간대가 한국이 아니어도 어긋나지 않게, 날짜는 "YYYY-MM-DD" 문자열, 시간은 "그날 0시부터 몇 분" 숫자로만 다뤄요.
//
//   KST.today()                       // "2026-11-18"
//   KST.addDays("2026-11-18", 1)      // "2026-11-19"
//   KST.minutesFrom(day, "2026-11-19T01:00:00")   // 1500 (다음 날 01:00)
//   KST.formatMonthDay("2026-11-18")  // "11월 18일 (수)"

import Foundation

enum KST {
    static let timeZone = TimeZone(identifier: "Asia/Seoul")!
    /// 드래그 · 시간 선택 단위 (분)
    static let snapMinutes = 10

    /// 날짜 계산용 달력 (UTC 고정 → 서머타임 영향 없음)
    private static let utcCalendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }()

    private static let kstCalendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = timeZone
        return c
    }()

    private static func pad(_ n: Int) -> String { n < 10 ? "0\(n)" : "\(n)" }

    /// "YYYY-MM-DD" → (y, m, d)
    static func parts(_ ymd: String) -> (y: Int, m: Int, d: Int) {
        let p = ymd.prefix(10).split(separator: "-").compactMap { Int($0) }
        guard p.count == 3 else { return (1970, 1, 1) }
        return (p[0], p[1], p[2])
    }

    private static func toUtc(_ ymd: String) -> Date {
        let p = parts(ymd)
        return utcCalendar.date(from: DateComponents(year: p.y, month: p.m, day: p.d)) ?? Date(timeIntervalSince1970: 0)
    }

    private static func fromUtc(_ date: Date) -> String {
        let c = utcCalendar.dateComponents([.year, .month, .day], from: date)
        return "\(c.year ?? 1970)-\(pad(c.month ?? 1))-\(pad(c.day ?? 1))"
    }

    static func ymd(year: Int, month: Int, day: Int) -> String { "\(year)-\(pad(month))-\(pad(day))" }

    /// 올바른 "YYYY-MM-DD" 인지
    static func isYmd(_ v: String?) -> Bool {
        guard let v, v.count == 10, v.range(of: #"^\d{4}-\d{2}-\d{2}$"#, options: .regularExpression) != nil else { return false }
        return fromUtc(toUtc(v)) == v
    }

    static func addDays(_ ymd: String, _ n: Int) -> String {
        fromUtc(utcCalendar.date(byAdding: .day, value: n, to: toUtc(ymd)) ?? toUtc(ymd))
    }

    /// 두 날짜 차이 (b - a, 일)
    static func diffDays(_ a: String, _ b: String) -> Int {
        Int(((toUtc(b).timeIntervalSince1970 - toUtc(a).timeIntervalSince1970) / 86_400).rounded())
    }

    /// 0=월 … 6=일
    static func weekdayIndex(_ ymd: String) -> Int {
        let w = utcCalendar.component(.weekday, from: toUtc(ymd)) // 1=일 … 7=토
        return (w + 5) % 7
    }

    /// 그 주 월요일
    static func startOfWeek(_ ymd: String) -> String { addDays(ymd, -weekdayIndex(ymd)) }

    static let weekdayLabel = ["월", "화", "수", "목", "금", "토", "일"]

    static func dayOfMonth(_ ymd: String) -> Int { parts(ymd).d }
    static func monthOf(_ ymd: String) -> Int { parts(ymd).m }
    static func yearOf(_ ymd: String) -> Int { parts(ymd).y }

    /// 지금 한국 시간 (date: "YYYY-MM-DD", minutes: 0~1439)
    static func now(_ at: Date = Date()) -> (date: String, minutes: Int) {
        let c = kstCalendar.dateComponents([.year, .month, .day, .hour, .minute], from: at)
        let date = ymd(year: c.year ?? 1970, month: c.month ?? 1, day: c.day ?? 1)
        return (date, (c.hour ?? 0) * 60 + (c.minute ?? 0))
    }

    static func today() -> String { now().date }

    /// "2026-11-18T09:30:00" → (date: "2026-11-18", minutes: 570)
    static func splitDateTime(_ v: String) -> (date: String, minutes: Int) {
        let comps = v.split(separator: "T", maxSplits: 1).map(String.init)
        let date = comps.first ?? v
        let time = comps.count > 1 ? comps[1] : "00:00"
        let hm = time.split(separator: ":").compactMap { Int($0.prefix(2)) }
        let h = hm.first ?? 0
        let m = hm.count > 1 ? hm[1] : 0
        return (date, h * 60 + m)
    }

    /// 어떤 날짜(dayYmd) 기준 0시부터 몇 분인지. 다음 날 01:00 이면 1500
    static func minutesFrom(_ dayYmd: String, _ v: String) -> Int {
        let s = splitDateTime(v)
        return diffDays(dayYmd, s.date) * 1440 + s.minutes
    }

    /// 날짜 + 분 → "YYYY-MM-DDTHH:mm:00" (분이 1440 이상이면 다음 날로)
    static func toDateTime(_ dayYmd: String, _ minutes: Int) -> String {
        let dayShift = Int(floor(Double(minutes) / 1440))
        let m = minutes - dayShift * 1440
        return "\(addDays(dayYmd, dayShift))T\(pad(m / 60)):\(pad(m % 60)):00"
    }

    /// 570 → "09:30", 1440 → "24:00"
    static func formatMinutes(_ minutes: Int) -> String {
        let m = ((minutes % 1440) + 1440) % 1440
        let h = (minutes > 0 && m == 0 && minutes % 1440 == 0) ? 24 : m / 60
        return "\(pad(h)):\(pad(m % 60))"
    }

    /// "2026-11-18T09:30:00" → "09:30"
    static func hm(_ v: String?) -> String {
        guard let v, v.count >= 16 else { return "" }
        let s = v.index(v.startIndex, offsetBy: 11)
        let e = v.index(v.startIndex, offsetBy: 16)
        return String(v[s..<e])
    }

    /// "09:30:00" | "09:30" → 570
    static func timeToMinutes(_ t: String) -> Int {
        let p = t.split(separator: ":").compactMap { Int($0) }
        return (p.first ?? 0) * 60 + (p.count > 1 ? p[1] : 0)
    }

    /// 루틴 시간 범위가 맞는지 (서버 RoutineValidator 와 같아요)
    /// 끝이 시작보다 이르면 자정을 넘기는 루틴 — 23:00 ~ 00:00(오후 11시 ~ 오전 12시)처럼 12시간까지 돼요
    static func isValidRoutineRange(_ start: String, _ end: String) -> Bool {
        let s = timeToMinutes(start), e = timeToMinutes(end)
        if s == e { return false }
        if e > s { return true }
        return 1440 - (s - e) <= 12 * 60
    }

    /// 570 → "09:30" (LocalTime 요청용, 24:00 은 00:00)
    static func minutesToTime(_ minutes: Int) -> String {
        let m = ((minutes % 1440) + 1440) % 1440
        return "\(pad(m / 60)):\(pad(m % 60))"
    }

    static func snap(_ minutes: Int, step: Int = snapMinutes) -> Int {
        Int((Double(minutes) / Double(step)).rounded()) * step
    }

    /// "11월 18일 (수)"
    static func formatMonthDay(_ ymd: String) -> String {
        "\(monthOf(ymd))월 \(dayOfMonth(ymd))일 (\(weekdayLabel[weekdayIndex(ymd)]))"
    }

    /// 주간 제목: "2026년 11월 16일 – 22일" / 달이 바뀌면 "2026년 11월 30일 – 12월 6일"
    static func formatRange(_ start: String, _ end: String) -> String {
        if start == end { return "\(yearOf(start))년 \(formatMonthDay(start))" }
        let head = "\(yearOf(start))년 \(monthOf(start))월 \(dayOfMonth(start))일"
        if yearOf(start) != yearOf(end) { return "\(head) – \(yearOf(end))년 \(monthOf(end))월 \(dayOfMonth(end))일" }
        if monthOf(start) != monthOf(end) { return "\(head) – \(monthOf(end))월 \(dayOfMonth(end))일" }
        return "\(head) – \(dayOfMonth(end))일"
    }

    /// 75 → "1시간 15분"
    static func formatDuration(_ minutes: Int) -> String {
        let h = minutes / 60
        let m = minutes % 60
        if h > 0 && m > 0 { return "\(h)시간 \(m)분" }
        if h > 0 { return "\(h)시간" }
        return "\(m)분"
    }

    /// 그 달의 마지막 날
    static func lastDayOfMonth(year: Int, month: Int) -> Int {
        let first = utcCalendar.date(from: DateComponents(year: year, month: month, day: 1)) ?? Date()
        return utcCalendar.range(of: .day, in: .month, for: first)?.count ?? 30
    }

    /// 달 이동. 31일 → 다음 달에 31일이 없으면 그 달 마지막 날
    static func addMonths(_ ymd: String, _ n: Int) -> String {
        let p = parts(ymd)
        let m0 = p.m - 1 + n
        let ny = p.y + Int(floor(Double(m0) / 12))
        let nm = ((m0 % 12) + 12) % 12 + 1
        return self.ymd(year: ny, month: nm, day: min(p.d, lastDayOfMonth(year: ny, month: nm)))
    }

    /// 월간 달력 칸: 그 달 1일이 있는 주 월요일부터, 마지막 날이 있는 주 일요일까지 (주 단위 배열)
    static func monthWeeks(year: Int, month: Int) -> [[String]] {
        let first = ymd(year: year, month: month, day: 1)
        let last = ymd(year: year, month: month, day: lastDayOfMonth(year: year, month: month))
        var weeks: [[String]] = []
        var d = startOfWeek(first)
        while d <= last {
            weeks.append((0..<7).map { addDays(d, $0) })
            d = addDays(d, 7)
        }
        return weeks
    }

    /// 오늘 기준 D-day 문구: 오늘 / 내일 / D-3 / 3일 지남
    static func dDay(_ ymd: String, today: String) -> String {
        let diff = diffDays(today, ymd)
        if diff == 0 { return "오늘" }
        if diff == 1 { return "내일" }
        if diff < 0 { return "\(-diff)일 지남" }
        return "D-\(diff)"
    }

    /// "YYYY-MM-DD" → 한국 자정 Date (DatePicker 등과 주고받을 때)
    static func date(from ymd: String) -> Date {
        let p = parts(ymd)
        return kstCalendar.date(from: DateComponents(year: p.y, month: p.m, day: p.d)) ?? Date()
    }

    /// Date → 한국 시간 기준 "YYYY-MM-DD"
    static func ymd(from date: Date) -> String { now(date).date }

    /// "YYYY-MM-DDTHH:mm:ss" (한국 벽시계) → Date
    static func date(fromDateTime v: String) -> Date {
        let s = splitDateTime(v)
        return date(from: s.date).addingTimeInterval(TimeInterval(s.minutes * 60))
    }

    /// Date → "YYYY-MM-DDTHH:mm:ss" (한국 벽시계)
    static func dateTime(from date: Date) -> String {
        let c = kstCalendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
        return "\(ymd(year: c.year ?? 1970, month: c.month ?? 1, day: c.day ?? 1))T\(pad(c.hour ?? 0)):\(pad(c.minute ?? 0)):\(pad(c.second ?? 0))"
    }

    /// "2026-11-18" → "11/18"
    static func slashDate(_ ymd: String) -> String { "\(monthOf(ymd))/\(dayOfMonth(ymd))" }

    /// "2026-11-18" → "2026.11.18"
    static func dotDate(_ ymd: String) -> String {
        let p = parts(ymd)
        return "\(p.y).\(pad(p.m)).\(pad(p.d))"
    }
}

// MARK: - 요일 (백엔드 java.time.DayOfWeek)

enum JavaDayOfWeek: String, Codable, CaseIterable, Hashable, Identifiable {
    case MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY

    var id: String { rawValue }

    /// 0=월 … 6=일
    var index: Int { JavaDayOfWeek.allCases.firstIndex(of: self) ?? 0 }

    /// "월"
    var label: String { KST.weekdayLabel[index] }

    static func from(index: Int) -> JavaDayOfWeek { allCases[((index % 7) + 7) % 7] }

    /// 그 날짜의 요일
    static func of(_ ymd: String) -> JavaDayOfWeek { from(index: KST.weekdayIndex(ymd)) }
}
