//
//  WidgetDataStore.swift
//  Shared (앱 + 위젯)
//
//  앱이 App Group(group.naleul.naleul) 에 위젯용 데이터를 저장하고, 위젯은 읽기만 해요.
//  토큰은 Keychain 에만 있어서 위젯이 직접 서버를 부르지 않아요 — 앱이 켜질 때 · 일정이 바뀔 때 앱이 채워줘요.

import Foundation

enum WidgetDataStore {
    static let appGroupID = "group.naleul.naleul"
    static let widgetKind = "NaleulWidget"

    private static var sharedDefaults: UserDefaults? {
        UserDefaults(suiteName: appGroupID)
    }

    private enum Key {
        static let displayType = "widgetDisplayType"
        static let daily = "widgetDailyTasks"
        static let weekly = "widgetWeeklyTasks"
        static let monthly = "widgetMonthlyTasks"
        static let updatedAt = "widgetUpdatedAt"
    }

    // MARK: - 위젯 스타일

    static func saveDisplayType(_ type: WidgetDisplayType) {
        sharedDefaults?.set(type.rawValue, forKey: Key.displayType)
    }

    /// 예전 버전에서 저장된 "mission" 같은 값은 일간으로 돌려요
    static func loadDisplayType() -> WidgetDisplayType {
        guard let raw = sharedDefaults?.string(forKey: Key.displayType),
              let type = WidgetDisplayType(rawValue: raw) else { return .daily }
        return type
    }

    // MARK: - 데이터

    static func saveDailyTasks(_ tasks: [WidgetTask]) { save(tasks, Key.daily) }
    static func loadDailyTasks() -> [WidgetTask] { load(Key.daily) ?? [] }

    /// key: "MONDAY" ~ "SUNDAY"
    static func saveWeeklyTasks(_ tasks: [String: [WidgetTask]]) { save(tasks, Key.weekly) }
    static func loadWeeklyTasks() -> [String: [WidgetTask]] { load(Key.weekly) ?? [:] }

    /// key: "yyyy-MM-dd"
    static func saveMonthlyTasks(_ tasks: [String: [WidgetTask]]) { save(tasks, Key.monthly) }
    static func loadMonthlyTasks() -> [String: [WidgetTask]] { load(Key.monthly) ?? [:] }

    static func markUpdated() { sharedDefaults?.set(Date(), forKey: Key.updatedAt) }

    /// 로그아웃 · 탈퇴 시 다른 계정 일정이 위젯에 남지 않게
    static func clearAll() {
        [Key.daily, Key.weekly, Key.monthly, Key.updatedAt].forEach { sharedDefaults?.removeObject(forKey: $0) }
    }

    // MARK: - 내부

    private static func save<T: Encodable>(_ value: T, _ key: String) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        sharedDefaults?.set(data, forKey: key)
    }

    private static func load<T: Decodable>(_ key: String) -> T? {
        guard let data = sharedDefaults?.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }
}
