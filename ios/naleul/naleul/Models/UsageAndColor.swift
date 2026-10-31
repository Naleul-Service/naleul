// Models/UsageAndColor.swift
//
// 웹 features/usage (오늘 AI 사용량) · features/color (사용자 색상)

import SwiftUI

// MARK: - 오늘 AI 사용량 (GET /v1/ai-usage)

/// 하루 횟수가 정해진 기능의 오늘 사용량
struct UsageItem: Codable, Hashable, Identifiable {
    var id: String { key }
    /// GOAL_SESSION · GOAL_SESSION_WEEK · GOAL_DRAFT · GOAL_EDIT · BRAIN_DUMP · TASK_PER_DAY · SESSION_REGENERATE
    var key: String
    var label: String
    var description: String
    var used: Int
    /// nil 이면 제한 없음
    var limit: Int?
    var remaining: Int?
}

struct AiUsage: Codable, Hashable {
    var pro: Bool
    /// 다시 채워지는 시각 (내일 0시, 한국 시간)
    var resetsAt: String
    var items: [UsageItem]
    /// sessionId 를 넘겼을 때: 그 목표 대화에서 계획을 만들 수 있는 횟수
    var session: UsageItem?

    func item(_ key: String) -> UsageItem? { items.first { $0.key == key } }

    /// 하루 · 주 · 월 중 가장 먼저 바닥나는 한도 (2026-10 — 예: "GOAL_DRAFT" → GOAL_DRAFT · _WEEK · _MONTH)
    func tightest(_ baseKey: String) -> UsageItem? {
        [baseKey, "\(baseKey)_WEEK", "\(baseKey)_MONTH"]
            .compactMap { item($0) }
            .filter { $0.remaining != nil }
            .min { ($0.remaining ?? .max) < ($1.remaining ?? .max) }
    }
}

enum UsageAPI {
    /// 횟수를 쓰는 동작(목표 대화 시작·초안 생성·Task 추가) 뒤에는 Invalidation.shared.invalidate(.usage)
    static func usage(sessionId: Int? = nil) async throws -> AiUsage? {
        try await API.get("/v1/ai-usage" + (sessionId.map { "?sessionId=\($0)" } ?? ""))
    }
}

/// "오늘 AI 목표 만들기 3/5회 남음" — 한도가 없으면 아무것도 안 그려요 (웹 UsageLine)
struct UsageLine: View {
    let items: [UsageItem?]
    /// true = 어두운 바탕 위
    var onDark = false

    private var limited: [UsageItem] { items.compactMap { $0 }.filter { $0.limit != nil } }
    private var baseColor: Color {
        if onDark { return Color.onInk.opacity(0.7) }
        return limited.contains { $0.remaining == 0 } ? Color.danger : Color.ink3
    }

    private var line: Text {
        var t = Text("")
        for (n, i) in limited.enumerated() {
            if n > 0 { t = t + Text("  ·  ").foregroundColor(baseColor.opacity(0.5)) }
            t = t + Text(i.label + " ")
            t = t + Text(String(i.remaining ?? 0)).bold().foregroundColor(onDark ? Color.onInk : Color.ink2)
            t = t + Text("/" + String(i.limit ?? 0) + "회 남음")
        }
        let weekly = items.contains { $0?.key.hasSuffix("_WEEK") == true }
        return t + Text(weekly ? "  (하루 횟수는 매일, 주간 횟수는 월요일 0시에 다시 채워져요)" : "  (매일 0시에 다시 채워져요)")
            .foregroundColor(baseColor.opacity(0.7))
    }

    var body: some View {
        if !limited.isEmpty {
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Image(systemName: "gauge.with.dots.needle.33percent").font(.system(size: 11))
                line
            }
            .font(.pt(12))
            .foregroundStyle(baseColor)
        }
    }
}

// MARK: - 사용자 색상 (GET /v1/user-colors)

/// ⚠️ Lombok `boolean isDefault` → Jackson 이 "default" 로 내보내요. 두 이름 모두 대비
struct UserColor: Hashable, Identifiable {
    var id: Int { userColorId }
    var userColorId: Int
    /// 항상 "#RRGGBB"
    var hex: String
    var isDefault: Bool

    var color: Color { Color.goal(hex) }
}

private struct UserColorRaw: Decodable {
    let userColorId: Int
    let colorCode: String
    let isDefault: Bool?
    let `default`: Bool?
}

enum ColorAPI {
    static func userColors() async throws -> [UserColor] {
        let list: [UserColorRaw]? = try await API.get("/v1/user-colors")
        return (list ?? []).map {
            UserColor(userColorId: $0.userColorId, hex: hexOf($0.colorCode), isDefault: $0.isDefault ?? $0.default ?? false)
        }
    }
}

/// 목표 색 고르기 (원 버튼 줄)
struct ColorPickerRow: View {
    let colors: [UserColor]
    @Binding var selectedId: Int?

    var body: some View {
        FlowLayout(spacing: 10) {
            ForEach(colors) { c in
                Button { selectedId = c.userColorId } label: {
                    Circle()
                        .fill(c.color)
                        .frame(width: 30, height: 30)
                        .overlay {
                            if selectedId == c.userColorId {
                                NSymbol("checkmark", size: 12, weight: .bold).foregroundStyle(.white)
                            }
                        }
                        .overlay(Circle().stroke(Color.ink.opacity(selectedId == c.userColorId ? 0.25 : 0), lineWidth: 3).padding(-3))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("색 \(c.hex)")
            }
        }
    }
}

#Preview {
    VStack(alignment: .leading, spacing: 16) {
        UsageLine(items: [
            UsageItem(key: "GOAL_SESSION", label: "AI 목표 만들기", description: "", used: 2, limit: 5, remaining: 3),
            UsageItem(key: "TASK_PER_DAY", label: "Task 추가", description: "", used: 2, limit: 2, remaining: 0),
        ])
        ColorPickerRow(colors: [
            UserColor(userColorId: 1, hex: "#3D5AFE", isDefault: true),
            UserColor(userColorId: 2, hex: "#12B76A", isDefault: false),
            UserColor(userColorId: 3, hex: "#F59E0B", isDefault: false),
        ], selectedId: .constant(1))
    }
    .padding()
}
