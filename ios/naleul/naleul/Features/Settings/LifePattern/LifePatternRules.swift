// Features/Settings/LifePattern/LifePatternRules.swift
//
// 웹 원본:
//   features/life-pattern/presets.ts  (추천 · 종류 이름/색 · 요일 표기)
//   features/life-pattern/dayTimes.ts (요일별 시간 · 수면 "밤" 계산 — 백엔드 LifePattern.occurrenceOn 과 같은 규칙)
//
// 화면이 아니라 계산만 모아 둔 파일이에요. 이름이 겹치지 않게 전역 함수 대신 `LifePatternRules` 안에 넣었어요.

import SwiftUI

// MARK: - 추천 (웹 LifePatternPreset)

struct LifePatternPreset: Hashable, Identifiable {
    var id: String { key }
    let key: String
    /// 추천 칩에 보이는 이름
    let label: String
    let patternType: LifePatternType
    let title: String
    let emoji: String
    /// "HH:mm"
    let startTime: String
    let endTime: String
    let days: [JavaDayOfWeek]
    /// 제목을 입력할 때 이 단어가 들어가면 추천해요
    let keywords: [String]
    /// 사용자당 하나만 두는 종류 (수면·점심·저녁)
    let single: Bool
}

/// 하룻밤: bed = 그날 밤 취침, wake = 다음 날 아침 기상 ("HH:mm")
struct LifePatternSleepNight: Hashable {
    var bed: String
    var wake: String
}

/// 요일별 시간 계산에 필요한 값만 (LifePattern · 입력 중인 폼 둘 다에서 만들 수 있게)
struct LifePatternTimes {
    var startTime: String
    var endTime: String
    var days: [JavaDayOfWeek]
    var dayTimes: [LifePatternDayTime]?

    init(startTime: String, endTime: String, days: [JavaDayOfWeek], dayTimes: [LifePatternDayTime]? = nil) {
        self.startTime = startTime
        self.endTime = endTime
        self.days = days
        self.dayTimes = dayTimes
    }

    init(_ p: LifePattern) {
        self.init(startTime: p.startTime, endTime: p.endTime, days: p.days, dayTimes: p.dayTimes)
    }
}

enum LifePatternRules {
    // MARK: 요일

    static let allDays: [JavaDayOfWeek] = JavaDayOfWeek.allCases
    static let weekdays: [JavaDayOfWeek] = Array(JavaDayOfWeek.allCases.prefix(5))
    static let weekend: [JavaDayOfWeek] = Array(JavaDayOfWeek.allCases.suffix(2))

    /// 백엔드 LifePatternService.MAX_PATTERNS
    static let maxPatterns = 10

    // MARK: 종류

    static func typeLabel(_ t: LifePatternType) -> String {
        switch t {
        case .SLEEP: return "수면"
        case .LUNCH: return "점심"
        case .DINNER: return "저녁"
        case .CUSTOM: return "고정 일정"
        }
    }

    /// 종류별 색 (주간 미리보기 막대) — 데이터 색이라 고정값이에요
    static func typeColor(_ t: LifePatternType) -> Color {
        switch t {
        case .SLEEP: return GoalPalette.purple.tone.accent
        case .LUNCH: return GoalPalette.amber.tone.accent
        case .DINNER: return GoalPalette.coral.tone.accent
        case .CUSTOM: return GoalPalette.mint.tone.accent
        }
    }

    // MARK: 추천

    /// 제목 추천 (+이동시간, +수면시간, +외출준비, +쉬는시간 …)
    static let presets: [LifePatternPreset] = [
        LifePatternPreset(key: "sleep", label: "수면시간", patternType: .SLEEP, title: "수면", emoji: "🌙",
                          startTime: "00:00", endTime: "07:00", days: allDays,
                          keywords: ["수면", "잠", "취침", "기상"], single: true),
        LifePatternPreset(key: "getReady", label: "외출준비", patternType: .CUSTOM, title: "외출준비", emoji: "🪞",
                          startTime: "07:30", endTime: "08:10", days: weekdays,
                          keywords: ["외출", "준비", "씻", "샤워"], single: false),
        LifePatternPreset(key: "commute", label: "이동시간", patternType: .CUSTOM, title: "이동시간", emoji: "🚇",
                          startTime: "08:10", endTime: "09:00", days: weekdays,
                          keywords: ["이동", "출근", "퇴근", "통학", "등교", "하교"], single: false),
        LifePatternPreset(key: "break", label: "쉬는시간", patternType: .CUSTOM, title: "쉬는시간", emoji: "☕",
                          startTime: "15:00", endTime: "15:30", days: allDays,
                          keywords: ["쉬는", "휴식", "쉼", "브레이크", "낮잠"], single: false),
        LifePatternPreset(key: "lunch", label: "점심", patternType: .LUNCH, title: "점심", emoji: "🍚",
                          startTime: "12:00", endTime: "13:00", days: allDays,
                          keywords: ["점심"], single: true),
        LifePatternPreset(key: "dinner", label: "저녁", patternType: .DINNER, title: "저녁", emoji: "🍽️",
                          startTime: "18:00", endTime: "19:00", days: allDays,
                          keywords: ["저녁"], single: true),
    ]

    /// 수면·점심·저녁이 이미 있으면 그 패턴 (추천을 누르면 새로 만들지 않고 그걸 수정해요)
    static func existingOfType(_ patterns: [LifePattern], _ preset: LifePatternPreset) -> LifePattern? {
        guard preset.single else { return nil }
        return patterns.first { $0.patternType == preset.patternType }
    }

    /// 입력한 제목과 맞는 추천. 제목이 비어 있으면 전부
    static func matchPresets(_ title: String) -> [LifePatternPreset] {
        let q = title.trimmingCharacters(in: .whitespacesAndNewlines)
        if q.isEmpty { return presets }
        return presets.filter { p in
            p.title.contains(q) || p.label.contains(q) || p.keywords.contains { q.contains($0) }
        }
    }

    /// 추천 칩 문구: 이미 있으면 "🌙 수면시간 수정", 없으면 "+ 수면시간"
    static func presetChipTitle(_ preset: LifePatternPreset, existing: LifePattern?) -> String {
        existing != nil ? "\(preset.emoji) \(preset.label) 수정" : "+ \(preset.label)"
    }

    // MARK: 표기

    /// "HH:mm:ss" → "HH:mm"
    static func toHm(_ t: String) -> String { String(t.prefix(5)) }

    /// ["MONDAY",…,"FRIDAY"] → "평일", 7개 → "매일", 그 외 → "월·수·금"
    static func formatDays(_ days: [JavaDayOfWeek]) -> String {
        if days.isEmpty || days.count == 7 { return "매일" }
        if sameSet(days, weekdays) { return "평일" }
        if sameSet(days, weekend) { return "주말" }
        return allDays.filter { days.contains($0) }.map(\.label).joined(separator: "·")
    }

    static func sameSet(_ a: [JavaDayOfWeek], _ b: [JavaDayOfWeek]) -> Bool {
        a.count == b.count && b.allSatisfy { a.contains($0) }
    }

    /// 패턴 길이 ("1시간 30분") — 끝이 시작보다 이르면 다음 날까지
    static func duration(_ p: LifePattern) -> String {
        let s = KST.timeToMinutes(p.startTime)
        var e = KST.timeToMinutes(p.endTime)
        if e <= s { e += 1440 }
        return KST.formatDuration(e - s)
    }

    // MARK: 요일별 시간 (웹 dayTimes.ts)

    /// JS Math.floor(a / b) 와 같은 내림 나눗셈 (Swift 의 / 는 음수에서 0 쪽으로 잘라서 따로 만들어요)
    static func floorDiv(_ a: Int, _ b: Int) -> Int {
        let q = a / b
        return (a % b != 0 && (a < 0) != (b < 0)) ? q - 1 : q
    }

    static func applies(_ p: LifePatternTimes, _ d: JavaDayOfWeek) -> Bool {
        p.days.isEmpty || p.days.contains(d)
    }

    /// 소속 요일 day 의 블록 구간 — 그 요일 0시 기준 분 (start 는 음수·1440 이상일 수 있고, end > start)
    static func occurrenceOf(_ p: LifePatternTimes, _ day: JavaDayOfWeek) -> (start: Int, end: Int) {
        let dt = p.dayTimes?.first { $0.day == day }
        let start = KST.timeToMinutes(toHm(dt?.startTime ?? p.startTime)) + (dt?.startDayOffset ?? 0) * 1440
        var end = floorDiv(start, 1440) * 1440 + KST.timeToMinutes(toHm(dt?.endTime ?? p.endTime))
        if end <= start { end += 1440 }
        return (start, end)
    }

    private static let noon = 720

    /// 밤 W 가 몇 요일 블록인지: 기본 취침이 정오 전(00:00 등)이면 다음 날 소속(1), 정오 뒤(23:00 등)면 그날 소속(0)
    static func nightShift(_ defaultStart: String) -> Int {
        KST.timeToMinutes(toHm(defaultStart)) < noon ? 1 : 0
    }

    /// 패턴 → 요일별 밤 (월요일 밤 ~ 일요일 밤). 그 밤에 수면이 없으면 nil
    static func sleepNights(_ p: LifePatternTimes) -> [LifePatternSleepNight?] {
        let shift = nightShift(p.startTime)
        return (0..<7).map { w -> LifePatternSleepNight? in
            let key = allDays[(w + shift) % 7]
            guard applies(p, key) else { return nil }
            let occ = occurrenceOf(p, key)
            return LifePatternSleepNight(bed: KST.minutesToTime(occ.start), wake: KST.minutesToTime(occ.end))
        }
    }

    /// 밤 W 의 취침 시각을 밤 W 날짜 0시 기준 분으로 (정오 전이면 다음 날 새벽)
    private static func bedFromNight(_ bed: String) -> Int {
        let m = KST.timeToMinutes(bed)
        return m >= noon ? m : m + 1440
    }

    /// 요일별 밤 → 요일별 시간 (기본 시간과 같은 밤은 빼요)
    static func nightsToDayTimes(defaultStart: String, defaultEnd: String,
                                 nights: [LifePatternSleepNight?]) -> [LifePatternDayTime] {
        let shift = nightShift(defaultStart)
        var out: [LifePatternDayTime] = []
        for (w, night) in nights.enumerated() {
            guard let n = night else { continue }
            let relKey = bedFromNight(n.bed) - shift * 1440   // 소속 요일 0시 기준
            let offset = floorDiv(relKey, 1440)
            let startTime = KST.minutesToTime(relKey)
            if offset == 0 && startTime == toHm(defaultStart) && n.wake == toHm(defaultEnd) { continue }
            out.append(LifePatternDayTime(day: allDays[(w + shift) % 7], startTime: startTime,
                                          endTime: n.wake, startDayOffset: offset))
        }
        return out
    }

    /// 밤끼리 겹치는지 — W 밤에 일어나는 시각이 그다음 밤 취침보다 늦으면 안 돼요.
    /// - Returns: 문제가 있는 요일(기상하는 날) 인덱스, 없으면 -1
    static func sleepConflictDay(_ nights: [LifePatternSleepNight?]) -> Int {
        guard nights.count == 7 else { return -1 }
        for w in 0..<7 {
            guard let n = nights[w], let next = nights[(w + 1) % 7] else { continue }
            let bed = bedFromNight(n.bed)
            var wake = floorDiv(bed, 1440) * 1440 + KST.timeToMinutes(n.wake)
            if wake <= bed { wake += 1440 }
            let nextBed = 1440 + bedFromNight(next.bed)
            if wake >= nextBed { return (w + 1) % 7 }
        }
        return -1
    }
}

// MARK: - 요청 (웹 features/life-pattern/api.ts)

/// 추가 · 수정에 보내는 값 (수정할 때는 patternType 을 보내지 않아요)
struct LifePatternInput: Hashable {
    var patternType: LifePatternType
    var title: String
    var emoji: String?
    /// "HH:mm"
    var startTime: String
    var endTime: String
    /// 빈 배열이면 매일
    var days: [JavaDayOfWeek]
    /// 빈 배열이면 모든 요일 기본 시간
    var dayTimes: [LifePatternDayTime]
}

/// 백엔드 /v1/life-patterns (LifePatternController).
/// 같은 요일끼리 겹치면 409, 10개 넘으면 400 → 백엔드 메시지를 그대로 토스트로 보여줘요 (Mutation.run)
/// 고정 시간이 바뀌면 백엔드가 겹치는 자동 배치 블록을 다시 배치해서, 화면에서 .lifePatterns 와 .timetable 을 같이 다시 불러와요.
enum LifePatternAPI {
    static func create(_ v: LifePatternInput) async throws {
        var body = commonBody(v)
        body["patternType"] = .string(v.patternType.rawValue)
        try await API.send("POST", "/v1/life-patterns", body: .object(body))
    }

    /// 종류(patternType)는 바꿀 수 없어요 (수면은 하루 범위 계산에 쓰이므로)
    static func update(lifePatternId: Int, _ v: LifePatternInput) async throws {
        try await API.send("PUT", "/v1/life-patterns/\(lifePatternId)", body: .object(commonBody(v)))
    }

    static func delete(lifePatternId: Int) async throws {
        try await API.send("DELETE", "/v1/life-patterns/\(lifePatternId)")
    }

    private static func commonBody(_ v: LifePatternInput) -> [String: JSONValue] {
        [
            "title": .string(v.title),
            "emoji": .of(v.emoji),
            "startTime": .string(v.startTime),
            "endTime": .string(v.endTime),
            "days": .array(v.days.map { JSONValue.string($0.rawValue) }),
            "dayTimes": .array(v.dayTimes.map { t -> JSONValue in
                JSONValue.object([
                    "day": .string(t.day.rawValue),
                    "startTime": .string(LifePatternRules.toHm(t.startTime)),
                    "endTime": .string(LifePatternRules.toHm(t.endTime)),
                    "startDayOffset": .int(t.startDayOffset),
                ])
            }),
        ]
    }
}

// MARK: - 미리보기 샘플

enum LifePatternSamples {
    static let patterns: [LifePattern] = {
        let json = """
        [
          {"lifePatternId":1,"patternType":"SLEEP","title":"수면","emoji":"🌙","startTime":"00:00:00","endTime":"07:00:00",
           "days":[],"everyDay":true,"crossesMidnight":false,"sortOrder":0,
           "dayTimes":[{"day":"SATURDAY","startTime":"01:00:00","endTime":"09:30:00","startDayOffset":0}]},
          {"lifePatternId":2,"patternType":"LUNCH","title":"점심","emoji":"🍚","startTime":"12:00:00","endTime":"13:00:00",
           "days":[],"everyDay":true,"crossesMidnight":false,"sortOrder":1},
          {"lifePatternId":3,"patternType":"CUSTOM","title":"이동시간","emoji":"🚇","startTime":"08:10:00","endTime":"09:00:00",
           "days":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"],"everyDay":false,"crossesMidnight":false,"sortOrder":2},
          {"lifePatternId":4,"patternType":"CUSTOM","title":"회사","emoji":"💼","startTime":"09:30:00","endTime":"18:00:00",
           "days":["MONDAY","WEDNESDAY","FRIDAY"],"everyDay":false,"crossesMidnight":false,"sortOrder":3}
        ]
        """
        return (try? JSONDecoder().decode([LifePattern].self, from: Data(json.utf8))) ?? []
    }()
}
