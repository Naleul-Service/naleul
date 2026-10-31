// Features/Calendar/AutoCarrySetting.swift
//
// 못 한 일 자동 이월 켜기/끄기 (2026-10, 웹 AutoCarryToggle)
//  - 켜짐: 못 한 일을 다음 날 아침(기상 시간)에 오늘로 옮겨요. 새벽까지 하던 일은 그대로 둬요
//  - 꺼짐: 못 한 일은 그날에 '놓침'으로 남아요 → 직접 '다른 날로' 옮겨요
// 캘린더 + 메뉴 · 설정 "일정" 두 곳에서 같은 값을 써요 (GET/PATCH /planner-settings)

import SwiftUI
import Observation

struct PlannerSettingDTO: Codable, Hashable {
    var autoGenerateEnabled: Bool
    var nightlyPushEnabled: Bool
    var taskGapMinutes: Int?
    var autoCarryEnabled: Bool?
}

@MainActor
@Observable
final class AutoCarrySetting {
    static let shared = AutoCarrySetting()

    private(set) var enabled = true
    private(set) var loaded = false
    private(set) var saving = false

    var help: String {
        enabled
            ? "못 한 일을 다음 날 아침(기상 시간)에 오늘로 옮겨요. 새벽까지 하던 일은 그대로 둬요."
            : "못 한 일은 그날에 '놓침'으로 남아요. 필요하면 직접 '다른 날로' 옮겨요."
    }

    func load() async {
        guard let s: PlannerSettingDTO = try? await API.get("/v1/planner-settings") else { return }
        enabled = s.autoCarryEnabled ?? true
        loaded = true
    }

    /// surface: calendar · settings (calendar.auto_carry_toggle / settings.auto_carry_toggle)
    func set(_ on: Bool, surface: String) async {
        Analytics.track("\(surface).auto_carry_toggle", ["enabled": on])
        let before = enabled
        enabled = on
        saving = true
        defer { saving = false }
        do {
            let s: PlannerSettingDTO? = try await API.patch("/v1/planner-settings", body: ["autoCarryEnabled": .bool(on)])
            if let s { enabled = s.autoCarryEnabled ?? on }
        } catch {
            enabled = before
            Toast.error(errorMessage(error))
        }
    }

    func binding(surface: String) -> Binding<Bool> {
        Binding(get: { self.enabled }, set: { v in Task { await self.set(v, surface: surface) } })
    }
}
