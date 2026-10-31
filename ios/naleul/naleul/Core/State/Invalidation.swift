// Core/State/Invalidation.swift
//
// 웹의 TanStack Query "invalidateQueries" 를 대신하는 아주 작은 도구.
//
// 웹은 Task 를 완료하면 qc.invalidateQueries({ queryKey: timetableKeys.all }) 로
// 캘린더 · 목표 화면이 알아서 다시 불러와요. iOS 에서는 이렇게 해요.
//
//   // 1) 화면: 데이터 종류(DataKey)를 구독하면서 불러오기 — 처음 뜰 때 + 그 종류가 바뀔 때마다 다시 실행
//   .reloadOn([.timetable]) { await vm.load() }
//   .reloadOn([.goals], id: goalId) { await vm.load(goalId) }   // 값(id)이 바뀌어도 다시
//
//   // 2) 바꾸는 쪽: 저장이 끝나면 관련 데이터를 "낡았다"고 표시
//   Invalidation.shared.invalidate(.timetable, .goals)
//
// 왜 이렇게? → 한 화면에서 바꾼 데이터(예: Task 완료)가 다른 탭/화면(목표 상세, 나의 패턴)에도
// 영향을 주는데, 화면끼리 서로를 몰라도 다시 불러오게 하려고요.

import SwiftUI
import Observation

/// 다시 불러올 데이터 종류 (웹 queryKey 의 첫 단어와 맞춰요)
enum DataKey: String, CaseIterable, Hashable {
    /// 캘린더(TimeTable) — 주간/일간/월간 모두
    case timetable
    /// 목표 목록 · 상세 · 목표의 Task · 진행 · 히트맵 (['goals'])
    case goals
    /// 실제로 한 일 (['activities'])
    case activities
    /// 기본 생활 패턴 (['life-patterns'])
    case lifePatterns
    /// 기록형 목표의 나의 패턴 · 업무 일지 (['record'])
    case record
    /// 나의 패턴 (['pattern'])
    case pattern
    /// 오늘 AI 사용량 (['ai-usage'])
    case usage
    /// 목표 만들기 대화 세션 (['goal-creation'])
    case goalCreation
    /// 사용자 색상 (['user-colors'])
    case userColors
    /// 알림 설정
    case notificationSettings
    /// 멤버십 · 사용자 정보
    case account
    /// 기본 생활 습관 (['habits'], V52)
    case habits
    /// 업무형 보드 (['work'], V52)
    case work
    /// 홈 "오늘" — 다가오는 마감 · 목표별 회고 (['home'])
    case home
}

@MainActor
@Observable
final class Invalidation {
    static let shared = Invalidation()
    private init() {}

    private(set) var versions: [DataKey: Int] = [:]

    func version(_ key: DataKey) -> Int { versions[key, default: 0] }

    func invalidate(_ keys: DataKey...) { invalidate(keys) }

    func invalidate(_ keys: [DataKey]) {
        for k in keys { versions[k, default: 0] += 1 }
    }

    func invalidateAll() { invalidate(DataKey.allCases) }
}

// MARK: - 화면에서 구독하기

private struct ReloadKey<ID: Equatable>: Equatable {
    let id: ID
    let versions: [Int]
}

extension View {
    /// 처음 나타날 때 + keys 중 하나가 invalidate 될 때마다 perform 을 다시 실행해요.
    /// (이전 실행 중이면 취소하고 새로 시작 — SwiftUI .task(id:) 와 같아요)
    func reloadOn(_ keys: [DataKey], perform: @escaping @MainActor () async -> Void) -> some View {
        reloadOn(keys, id: 0, perform: perform)
    }

    /// id 가 바뀌어도 다시 실행 (예: 다른 목표로 이동, 주 이동)
    func reloadOn<ID: Equatable>(_ keys: [DataKey], id: ID, perform: @escaping @MainActor () async -> Void) -> some View {
        modifier(ReloadModifier(keys: keys, id: id, perform: perform))
    }
}

private struct ReloadModifier<ID: Equatable>: ViewModifier {
    let keys: [DataKey]
    let id: ID
    let perform: @MainActor () async -> Void

    func body(content: Content) -> some View {
        let inv = Invalidation.shared
        let key = ReloadKey(id: id, versions: keys.map { inv.version($0) })
        content.task(id: key) { await perform() }
    }
}
