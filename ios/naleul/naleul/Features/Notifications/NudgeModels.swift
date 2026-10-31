// Features/Notifications/NudgeModels.swift
//
// "실천 도우미" 알림 — 계획을 실행으로 옮기도록 돕는 이 기기의 로컬 알림.
//
// 왜 이런 알림인가요? (Fogg 행동 모델 B = M · A · P)
//  행동은 동기(Motivation) · 능력(Ability, 지금 바로 할 만큼 쉬운가) · 계기(Prompt, "지금 해" 신호)가
//  같은 순간에 맞아야 일어나요. 실천을 못 하는 건 대부분 셋 중 하나가 빠져서예요.
//   - P 계기   : 하루 마무리 · 아침 첫걸음
//   - A 능력   : 아침에는 "하나만" 보여주기
//   (2026-10: "할 일이 많아 보여요" · "내일 할 일 n개" 는 실천에 도움이 안 돼서 뺐어요 — 알림은 적을수록 좋아요)
//   - M 동기   : 이루고 난 나(시작한 이유) · 끝이 보여요(해방까지 D-n) · 주간/월간 돌아보기
//   - 다시 시작 : 며칠 안 들어오면 "완벽하지 않아도 괜찮아요"
//
// 서버 푸시(일정 알림 · 미션 알림)도 알림함에 같이 모아요.

import Foundation

// MARK: - 알림 종류

enum NudgeKind: String, Codable, CaseIterable, Hashable {
    /// 아침 — 오늘 할 일 중 "하나만" 먼저 보여주기 (A)
    case firstStep
    /// [더 이상 만들지 않음 2026-10] 할 일이 많은 날 — 예전에 받은 알림을 읽을 수 있게 값만 남겨요
    case overwhelm
    /// 오늘 일정이 끝나면 — 돌아보기 · 못 한 이유 남기기 (P)
    case dayWrapUp
    /// [더 이상 만들지 않음 2026-10] 자기 전 내일 미리 보기 — 예전에 받은 알림을 읽을 수 있게 값만 남겨요
    case tomorrowCheck
    /// 시작한 이유 · 과거의 내가 보내는 말 (M)
    case why
    /// 끝이 있다 — 해방까지 D-n, 긴 목표는 "남은 주말 n번" (M)
    case countdown
    /// 주간 돌아보기 보고서 (M · 지표)
    case weeklyReport
    /// 월간 돌아보기 (M · 지표)
    case monthlyReport
    /// 며칠 안 들어오면 — 다시 시작하면 돼요
    case comeback
    /// 서버 푸시 ROUTINE_NUDGE — "어제 10:00에 독서를 했어요 · 연속 7일" · "실천률 85% 루틴, 시간을 바꿔 볼까요?"
    case routineNudge
    /// 서버에서 온 푸시 (일정 · 미션 알림)
    case server

    var group: NudgeGroup? {
        switch self {
        case .firstStep, .overwhelm: return .morning
        case .dayWrapUp, .tomorrowCheck: return .evening
        case .why, .countdown: return .motivation
        case .weeklyReport, .monthlyReport: return .report
        case .comeback: return .comeback
        case .routineNudge, .server: return nil
        }
    }

    /// 알림함 아이콘 (SF Symbol)
    var icon: String {
        switch self {
        case .firstStep: return "figure.walk"
        case .overwhelm: return "leaf"
        case .dayWrapUp: return "moon.stars"
        case .tomorrowCheck: return "sunrise"
        case .why: return "envelope.open"
        case .countdown: return "flag.checkered"
        case .weeklyReport, .monthlyReport: return "chart.bar.doc.horizontal"
        case .comeback: return "arrow.counterclockwise"
        case .routineNudge: return "repeat"
        case .server: return "bell"
        }
    }

    /// 알림함에서 작게 붙는 이름
    var label: String {
        switch self {
        case .firstStep: return "아침 첫걸음"
        case .overwhelm: return "가볍게 가기"
        case .dayWrapUp: return "하루 마무리"
        case .tomorrowCheck: return "내일 미리 보기"
        case .why: return "시작한 이유"
        case .countdown: return "끝이 보여요"
        case .weeklyReport: return "주간 돌아보기"
        case .monthlyReport: return "월간 돌아보기"
        case .comeback: return "다시 시작"
        case .routineNudge: return "루틴 실천"
        case .server: return "알림"
        }
    }
}

/// 설정에서 켜고 끄는 묶음
enum NudgeGroup: String, CaseIterable, Identifiable, Hashable {
    case morning, evening, motivation, report, comeback

    var id: String { rawValue }

    var title: String {
        switch self {
        case .morning: return "아침 첫걸음"
        case .evening: return "하루 마무리"
        case .motivation: return "목표를 떠올리게"
        case .report: return "주간 · 월간 돌아보기"
        case .comeback: return "다시 시작 응원"
        }
    }

    var description: String {
        switch self {
        case .morning: return "일어나면 오늘 할 일 중 하나만 먼저 알려드려요."
        case .evening: return "오늘 일정이 끝나면 3분 돌아보기를 권해요."
        case .motivation: return "목표를 시작한 이유와 끝까지 남은 날을 알려드려요."
        case .report: return "일요일 저녁과 월말에 한 일 · 못 한 일을 돌아보게 해 드려요."
        case .comeback: return "며칠 쉬었다면 부담 없이 다시 시작할 수 있게 말을 걸어요."
        }
    }

    private var storageKey: String { "nudge.enabled.\(rawValue)" }

    /// 기본은 켜짐
    var isEnabled: Bool {
        get { UserDefaults.standard.object(forKey: storageKey) as? Bool ?? true }
        nonmutating set { UserDefaults.standard.set(newValue, forKey: storageKey) }
    }
}

// MARK: - 누르면 어디로 갈지

/// userInfo["route"] 에 문자열로 넣어요 — "calendar:2026-10-15", "goal:12", "ai:문장" …
enum NudgeRoute: Hashable {
    case home
    /// 홈 + 오늘 회고 시트
    case homeRetro
    case calendar(date: String)
    /// 그 날짜 캘린더 + 그 Task 상세 시트 (루틴 실천 알림 "시간을 바꿔 볼까요?")
    case calendarTask(date: String, taskId: Int)
    case taskAdd
    case goal(Int)
    case reflections
    case patterns
    /// 캘린더 "AI로 일정 조정"에 문장을 채워서
    case calendarAi(String)

    var raw: String {
        switch self {
        case .home: return "home"
        case .homeRetro: return "home-retro"
        case .calendar(let d): return "calendar:\(d)"
        case .calendarTask(let d, let id): return "calendar-task:\(d)|\(id)"
        case .taskAdd: return "task-add"
        case .goal(let id): return "goal:\(id)"
        case .reflections: return "reflections"
        case .patterns: return "patterns"
        case .calendarAi(let p): return "ai:\(p)"
        }
    }

    init?(raw: String?) {
        guard let raw, !raw.isEmpty else { return nil }
        let parts = raw.split(separator: ":", maxSplits: 1).map(String.init)
        let head = parts[0]
        let rest = parts.count > 1 ? parts[1] : ""
        switch head {
        case "home": self = .home
        case "home-retro": self = .homeRetro
        case "calendar": self = .calendar(date: KST.isYmd(rest) ? rest : KST.today())
        case "calendar-task":
            let p = rest.split(separator: "|").map(String.init)
            guard p.count == 2, KST.isYmd(p[0]), let id = Int(p[1]) else { return nil }
            self = .calendarTask(date: p[0], taskId: id)
        case "task-add": self = .taskAdd
        case "goal":
            guard let id = Int(rest) else { return nil }
            self = .goal(id)
        case "reflections": self = .reflections
        case "patterns": self = .patterns
        case "ai": self = .calendarAi(rest)
        default: return nil
        }
    }

    /// 버튼 글자 (알림함에서)
    var actionLabel: String {
        switch self {
        case .home: return "홈으로"
        case .homeRetro: return "돌아보기"
        case .calendar: return "일정 보기"
        case .calendarTask: return "시간 바꾸기"
        case .taskAdd: return "할 일 정하기"
        case .goal: return "목표 보기"
        case .reflections: return "회고 모아보기"
        case .patterns: return "나의 패턴"
        case .calendarAi: return "AI로 다시 짜기"
        }
    }
}

// MARK: - 알림 한 건

struct NudgeEntry: Codable, Hashable, Identifiable {
    /// 로컬 알림이면 "nudge.<kind>.<날짜>…", 서버 푸시면 iOS 알림 identifier
    var id: String
    var kind: NudgeKind
    var title: String
    var body: String
    /// 울린 시각 (예정 목록에서는 울릴 시각)
    var date: Date
    var route: String?
    var read: Bool = false
    /// 서버 푸시의 data.type (TASK_START · HABIT_REMIND …). 로컬 알림이나 예전에 저장된 알림이면 nil
    var serverType: String? = nil
    /// ROUTINE_NUDGE 의 kind (YESTERDAY_TIME · RATE_RESCHEDULE). 다른 알림이면 nil
    var routineKind: String? = nil

    var nudgeRoute: NudgeRoute? { NudgeRoute(raw: route) }

    /// 알림함에 넣지 않는 알림 — "'○○' 할 시간이에요"(Task 시작 알림)는 너무 많아서 핵심 알림이 묻혀요.
    /// 푸시 배너로는 그대로 울리고, 목록에만 안 보여요. (serverType 이 없던 예전 알림은 제목으로 걸러요)
    var isHiddenFromInbox: Bool {
        guard kind == .server else { return false }
        if serverType == "TASK_START" { return true }
        return serverType == nil && title.contains("Task 시작 시간")
    }
}
