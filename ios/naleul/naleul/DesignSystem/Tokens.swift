// DesignSystem/Tokens.swift
//
// Naleul Component_ver.2 (Figma "Semantic Color", Light / Dark) 디자인 토큰.
// 라이트/다크 값이 자동으로 바뀌어요. 웹 · iOS · Android 가 같은 표(DESIGN_SPEC §5)를 써요.
//
//   새 이름 (권장)                 옛 이름 (계속 동작 · 새 값으로 바뀜)
//   Color.textPrimary            Color.ink
//   Color.textSecondary          Color.ink2
//   Color.bgSurface              Color.surface
//   Color.borderSubtle           Color.line
//   Color.actionPrimary          Color.brand        (채우기 색)
//   Color.textBrand              —                  (브랜드색 글자/아이콘은 이걸 써요)
//   Color.bgInverse              Color.inverse      ⚠️ 다크 모드에서 밝아져요 → 위 글자는 Color.onInk / textInverse
//
//   rounded-card → Radius.card (16) · shadow-card → .cardShadow() · shadow-pop → .popShadow()
//   ⚠️ 항상 Color. 을 붙여 써요 (.ink 만 쓰면 컴파일 안 될 때가 있어요)

import SwiftUI
import UIKit

extension Color {
    /// 라이트/다크 두 값을 받아 시스템 모드에 따라 바뀌는 색
    init(light: String, dark: String) {
        self.init(uiColor: UIColor { trait in
            UIColor(hexString: trait.userInterfaceStyle == .dark ? dark : light)
        })
    }

    /// "#3D5AFE" / "3D5AFE" / "#3D5AFE80"
    init(hex: String) {
        self.init(uiColor: UIColor(hexString: hex))
    }

    /// 목표 색 코드("#3D5AFE" | "3D5AFE" | nil) → 목표 팔레트의 accent 색. 없으면 neutral
    static func goal(_ code: String?) -> Color {
        goalTone(code).accent
    }

    // MARK: - Semantic · 면 (bg/*)
    static let bgCanvas = Color(light: "#FFFFFF", dark: "#121820")
    static let bgSurface = Color(light: "#FFFFFF", dark: "#1C232C")
    static let bgElevated = Color(light: "#FFFFFF", dark: "#252D38")
    static let bgSubtle = Color(light: "#F1F4F8", dark: "#252D38")
    static let bgSunken = Color(light: "#F6F8FB", dark: "#121820")
    static let bgInverse = Color(light: "#121820", dark: "#F2F5F9")
    static let bgOverlay = Color(light: "#00000066", dark: "#00000099")

    // MARK: - Semantic · 글자 (text/*)
    static let textPrimary = Color(light: "#121820", dark: "#F2F5F9")
    static let textSecondary = Color(light: "#6B7685", dark: "#A3AEBD")
    static let textTertiary = Color(light: "#98A2B0", dark: "#7D8898")
    static let textPlaceholder = Color(light: "#A3ACB9", dark: "#7D8898")
    static let textBrand = Color(light: "#0D6B89", dark: "#A3D2E8")
    static let textInverse = Color(light: "#FFFFFF", dark: "#121820")
    /// bg/inverse 위 강조 글자 — 반대 모드의 text/brand · status fg 값 (라이트 → 다크 값 · 다크 → 라이트 값)
    static let onInverseBrand = Color(light: "#A3D2E8", dark: "#0D6B89")
    static let onInverseSuccess = Color(light: "#8ED6AA", dark: "#246B42")
    static let onInverseWarning = Color(light: "#E8C878", dark: "#805A12")
    static let onInverseError = Color(light: "#E2888E", dark: "#BC3942")

    // MARK: - Semantic · 선 (border/*)
    static let borderSubtle = Color(light: "#E7ECF2", dark: "#2F3845")
    static let borderSoft = Color(light: "#EEF2F6", dark: "#2F3845B2")
    static let borderControl = Color(light: "#B9C2CD", dark: "#7D8898")
    static let borderFocus = Color(light: "#0F7A9C", dark: "#6FB1CF")

    // MARK: - Semantic · 액션 (action/*)
    static let actionPrimary = Color(light: "#0F7A9C", dark: "#167A9C")
    static let actionPrimaryHover = Color(light: "#0D6B89", dark: "#126B88")
    static let actionPrimaryPressed = Color(light: "#0B5D77", dark: "#0E5B75")
    static let actionPrimaryFg = Color(light: "#FFFFFF", dark: "#FFFFFF")
    static let actionSecondaryBg = Color(light: "#E8F4F5", dark: "#2B3646")
    static let actionSecondaryBgHover = Color(light: "#DCEBEE", dark: "#354258")
    static let actionSecondaryBgPressed = Color(light: "#CCE1E5", dark: "#3F4D63")
    static let actionSecondaryFg = Color(light: "#0D6B89", dark: "#F2F5F9")
    static let actionSecondaryBorder = Color(light: "#D3E6EA", dark: "#3F4A5A")
    static let actionOutlineBorder = Color(light: "#0F7A9C", dark: "#6FB1CF")
    static let ghostHover = Color(light: "#E8F4F5", dark: "#252D38")
    static let ghostPressed = Color(light: "#DCEBEE", dark: "#2F3845")
    static let actionDanger = Color(light: "#D13F49", dark: "#A33139")
    static let actionDangerHover = Color(light: "#B83740", dark: "#AC464D")
    static let actionDangerPressed = Color(light: "#9F3037", dark: "#8B2A30")
    static let actionDangerFg = Color(light: "#FFFFFF", dark: "#FFFFFF")
    static let disabledBg = Color(light: "#F1F4F8", dark: "#252D38")
    static let disabledFg = Color(light: "#A3ACB9", dark: "#566273")
    static let selectionBg = Color(light: "#F1F4F8", dark: "#2F3845")
    static let selectionFg = Color(light: "#121820", dark: "#F2F5F9")
    /// 수치 · 기분 배지 바탕 (bg/subtle 보다 조금 진하게 — 회고 카드)
    static let badgeBg = Color(light: "#EAEEF3", dark: "#2F3845")
    /// 목록 칸 사이 구분선 (border/soft 보다 조금 진하게 — 회고 카드)
    static let listDivider = Color(light: "#E1E6ED", dark: "#2F3845")

    // MARK: - Semantic · 상태 (status/*)
    static let statusSuccessBg = Color(light: "#EDF7F0", dark: "#334745")
    static let statusSuccessFg = Color(light: "#246B42", dark: "#8ED6AA")
    static let statusWarningBg = Color(light: "#FFF6DF", dark: "#45443B")
    static let statusWarningFg = Color(light: "#805A12", dark: "#E8C878")
    static let statusErrorBg = Color(light: "#FBF0F0", dark: "#443740")
    static let statusErrorFg = Color(light: "#BC3942", dark: "#E2888E")
    static let statusInfoBg = Color(light: "#F1F4F8", dark: "#252D38")
    static let statusInfoFg = Color(light: "#3F4A5A", dark: "#A3AEBD")

    // MARK: - Accent · 진행 · 캘린더
    static let accentMint = Color(light: "#00CBA8", dark: "#00CBA8")
    static let onAccentMint = Color(light: "#121820", dark: "#121820")
    static let progressTrack = Color(light: "#E3F1F4", dark: "#22343D")
    static let progressFill = Color(light: "#00CBA8", dark: "#00CBA8")
    static let calendarToday = Color(light: "#0F7A9C", dark: "#167A9C")
    static let calendarTodayFg = Color(light: "#FFFFFF", dark: "#FFFFFF")
    static let calendarHoliday = Color(light: "#BC3942", dark: "#E2888E")
    static let calendarSaturday = Color(light: "#3C5EA8", dark: "#6F98F2")

    // MARK: - AI
    static let aiTint = Color(light: "#D9F7F2", dark: "#1C2B3D")
    static let aiTintSoft = Color(light: "#D9F7F299", dark: "#1C2B3D99")
    static let aiAccentStart = Color(hex: "#00CBA8")
    static let aiAccentEnd = Color(hex: "#2AA7DF")
    static let aiAccentViolet = Color(hex: "#A18BFA")
    static let aiGradientStart = Color(light: "#1488AB", dark: "#2AA0C6")
    static let aiGradientMid = Color(light: "#3487C3", dark: "#4D92DC")
    static let aiGradientEnd = Color(light: "#6F86F0", dark: "#8EA3FF")
    static let aiGradientStartHover = Color(light: "#127896", dark: "#3BB0D4")
    static let aiGradientMidHover = Color(light: "#2E77AB", dark: "#62A2E6")
    static let aiGradientEndHover = Color(light: "#6276D3", dark: "#A2B4FF")
    static let aiGradientStartPressed = Color(light: "#0F6782", dark: "#1F86AA")
    static let aiGradientMidPressed = Color(light: "#276794", dark: "#3D7FC4")
    static let aiGradientEndPressed = Color(light: "#5466B6", dark: "#7A8FE8")

    /// AI 그라데이션 방향 (≈115°: 왼쪽 위 → 오른쪽 아래, 거의 가로)
    static let aiGradientStartPoint = UnitPoint(x: 0, y: 0.2)
    static let aiGradientEndPoint = UnitPoint(x: 1, y: 0.8)

    /// 3색 · stops 0 / 0.65 / 1 · 115° 그라데이션
    static func aiLinearGradient(_ start: Color, _ mid: Color, _ end: Color) -> LinearGradient {
        LinearGradient(stops: [.init(color: start, location: 0), .init(color: mid, location: 0.65), .init(color: end, location: 1)],
                       startPoint: aiGradientStartPoint, endPoint: aiGradientEndPoint)
    }
    /// AI 버튼 · 배지 · 테두리 그라데이션 (DESIGN_SPEC §6)
    static var aiGradient: LinearGradient { aiLinearGradient(.aiGradientStart, .aiGradientMid, .aiGradientEnd) }
    static var aiGradientHover: LinearGradient { aiLinearGradient(.aiGradientStartHover, .aiGradientMidHover, .aiGradientEndHover) }
    static var aiGradientPressed: LinearGradient {
        aiLinearGradient(.aiGradientStartPressed, .aiGradientMidPressed, .aiGradientEndPressed)
    }

    // MARK: - Mood · Glass · Shadow · Brand logo
    static let moodMint = Color(light: "#DDF7F1", dark: "#16302D")
    static let moodSky = Color(light: "#E2F0FC", dark: "#172A3B")
    static let moodLavender = Color(light: "#EFEAFD", dark: "#25213D")
    static let glassBg = Color(light: "#FFFFFFE0", dark: "#141D29E5")
    static let glassBorder = Color(light: "#1218200D", dark: "#FFFFFF24")
    static let glassShadow = Color(light: "#1218201F", dark: "#00000080")
    static let glassShadowSoft = Color(light: "#1218200F", dark: "#0000004D")
    static let cardShadowTint = Color(light: "#1218200A", dark: "#00000040")
    static let shadowPrimary = Color(light: "#087F8E40", dark: "#00000059")
    static let shadowDanger = Color(light: "#D13F4938", dark: "#00000059")
    static let brandLogoBase = Color(light: "#0B4F65", dark: "#7FCBE6")
    static let brandLogoAccent = Color(light: "#4A90E2", dark: "#5F9CF5")

    // MARK: - Legacy 이름 (DESIGN_SPEC §5 매핑 — 새 코드는 위 Semantic 이름을 써요)
    // 글자
    static let ink = Color(light: "#121820", dark: "#F2F5F9")
    static let ink2 = Color(light: "#6B7685", dark: "#A3AEBD")
    static let ink3 = Color(light: "#98A2B0", dark: "#7D8898")
    static let ink4 = Color(light: "#C3CAD3", dark: "#566273")

    // 면 / 선
    static let canvas = Color(light: "#FFFFFF", dark: "#121820")
    static let surface = Color(light: "#FFFFFF", dark: "#1C232C")
    static let subtle = Color(light: "#F1F4F8", dark: "#252D38")
    static let line = Color(light: "#E7ECF2", dark: "#2F3845")
    static let lineStrong = Color(light: "#D5DCE4", dark: "#3F4A5A")

    // 포인트 (채우기 색. 글자/아이콘은 Color.textBrand)
    static let brand = Color(light: "#0F7A9C", dark: "#167A9C")
    static let brandStrong = Color(light: "#0D6B89", dark: "#126B88")
    static let brandSoft = Color(light: "#E8F4F5", dark: "#2B3646")

    // 상태
    static let success = Color(light: "#246B42", dark: "#8ED6AA")
    static let successSoft = Color(light: "#EDF7F0", dark: "#334745")
    /// 경고 채우기/점 (goal amber accent)
    static let warning = Color(light: "#F5B63A", dark: "#F6C158")
    static let warningSoft = Color(light: "#FFF6DF", dark: "#45443B")
    /// 글자용 빨강 (status/error/fg). 위험 버튼 채우기는 Color.actionDanger
    static let danger = Color(light: "#BC3942", dark: "#E2888E")
    static let dangerSoft = Color(light: "#FBF0F0", dark: "#443740")

    /// inverse 위 글자 (= text/inverse · 라이트: 흰색 · 다크: 진한 남색)
    static let onInk = Color(light: "#FFFFFF", dark: "#121820")
    /// 반전 면 (= bg/inverse). ⚠️ 다크 모드에선 밝아져요 → 위 글자/아이콘은 Color.onInk
    static let inverse = Color(light: "#121820", dark: "#F2F5F9")
    /// 연한 노랑 바탕 위 경고 글자
    static let warningInk = Color(light: "#805A12", dark: "#E8C878")
    static let warningInkStrong = Color(light: "#6B4A0E", dark: "#F0D89A")

    // 타임테이블 빗금 (고정 시간 · 수면 — 모두 회색 빗금, DESIGN_SPEC 9.5)
    static let hatch = Color(light: "#F6F8FB", dark: "#1A2129")
    static let hatchLine = Color(light: "#E3E8EE", dark: "#2A323D")
    static let sleep = Color(light: "#F6F8FB", dark: "#1A2129")
    static let sleepLine = Color(light: "#E3E8EE", dark: "#2A323D")
    static let sleepInk = Color(light: "#6B7685", dark: "#A3AEBD")

    // 히트맵 단계 (1 연함 ~ 5 진함, teal)
    static let heat1 = Color(light: "#DCEBEE", dark: "#1A3540")
    static let heat2 = Color(light: "#A9D1DC", dark: "#1F4F60")
    static let heat3 = Color(light: "#5FB0C6", dark: "#22708A")
    static let heat4 = Color(light: "#1F8FAE", dark: "#2A93B5")
    static let heat5 = Color(light: "#0B5D77", dark: "#6FB1CF")

    /// 히트맵 빈 칸 · 안 한 칸 (DESIGN_SPEC 8.1)
    static let heatEmpty = Color(light: "#F1F4F8", dark: "#252D38")

    /// 단계(0~5) → 히트맵 색. 0 은 heatEmpty
    static func heat(_ level: Int) -> Color {
        switch level {
        case ...0: return .heatEmpty
        case 1: return .heat1
        case 2: return .heat2
        case 3: return .heat3
        case 4: return .heat4
        default: return .heat5
        }
    }

    /// 카카오 버튼 노랑 (서드파티 브랜드 색 — 그대로)
    static let kakao = Color(hex: "#FEE500")
}

/// 웹 hexOf: "#3D5AFE" / "3D5AFE" / null → "#3D5AFE" (없으면 text/tertiary 회색)
func hexOf(_ code: String?) -> String {
    guard let code, !code.isEmpty else { return "#7D8898" }
    return code.hasPrefix("#") ? code : "#\(code)"
}

extension UIColor {
    convenience init(hexString: String) {
        var h = hexString.trimmingCharacters(in: .whitespacesAndNewlines)
        if h.hasPrefix("#") { h.removeFirst() }
        if h.count == 3 { h = h.map { "\($0)\($0)" }.joined() }
        var n: UInt64 = 0
        guard Scanner(string: h).scanHexInt64(&n), h.count == 6 || h.count == 8 else {
            self.init(red: 125 / 255, green: 136 / 255, blue: 152 / 255, alpha: 1)
            return
        }
        if h.count == 8 {
            self.init(red: CGFloat((n >> 24) & 0xFF) / 255, green: CGFloat((n >> 16) & 0xFF) / 255,
                      blue: CGFloat((n >> 8) & 0xFF) / 255, alpha: CGFloat(n & 0xFF) / 255)
        } else {
            self.init(red: CGFloat((n >> 16) & 0xFF) / 255, green: CGFloat((n >> 8) & 0xFF) / 255,
                      blue: CGFloat(n & 0xFF) / 255, alpha: 1)
        }
    }
}

// MARK: - 목표 색 팔레트 (21색 · bg / fg / accent)

/// 목표 한 개의 색 묶음. 칩 · 일정 블록 · 점 등에 써요
///   let tone = Color.goalTone(goal.colorCode)
///   .background(tone.bg) · .foregroundStyle(tone.fg) · 막대/점 = tone.accent
struct GoalTone {
    /// 연한 바탕 (goal/X/bg)
    let bg: Color
    /// 바탕 위 글자 (goal/X/fg)
    let fg: Color
    /// 막대 · 점 · 강조 (goal/X/accent)
    let accent: Color
}

enum GoalPalette: String, CaseIterable {
    case coral, orange, amber, yellow, lime, green, mint, sky, blue, purple
    case violet, pink, rose, teal, slate, brown, sand, sage, navy, plum

    /// (light bg, light fg, light accent, dark bg, dark fg, dark accent)
    var hexes: (String, String, String, String, String, String) {
        switch self {
        case .coral: return ("#FDECEC", "#9D4B4B", "#F27474", "#47373F", "#FACACA", "#F48989")
        case .orange: return ("#FEF0E7", "#935730", "#F59150", "#483C38", "#FBD5BC", "#F6A16A")
        case .amber: return ("#FEF5E3", "#876420", "#F5B63A", "#484335", "#FBE3B4", "#F6C158")
        case .yellow: return ("#FEFAE8", "#7B6E2F", "#F5DC5E", "#48493B", "#FBF2C1", "#F6E176")
        case .lime: return ("#F2FAE8", "#526D2F", "#A3D95E", "#3A493B", "#DCF0C1", "#B1DF76")
        case .green: return ("#E6F7ED", "#2D764A", "#4BC47C", "#2B4540", "#BAE8CD", "#66CD90")
        case .mint: return ("#E0F7F3", "#137664", "#1FC4A6", "#234547", "#A9E8DD", "#41CDB3")
        case .sky: return ("#E4F6FC", "#25738A", "#3DBFE6", "#284452", "#B5E7F5", "#5AC9EA")
        case .blue: return ("#E7EEFD", "#3C5EA8", "#5586F0", "#2D3A54", "#BED1F9", "#6F98F2")
        case .purple: return ("#EEEAFD", "#5C49A8", "#8468F0", "#343554", "#D0C5F9", "#967FF2")
        case .violet: return ("#F5EBFC", "#7F51A2", "#B573E8", "#3D3752", "#E3C9F6", "#C088EB")
        case .pink: return ("#FDEFFA", "#915383", "#F28BDB", "#473B50", "#FAD3F1", "#F49CE0")
        case .rose: return ("#FDEBF1", "#9C4967", "#F0709E", "#473746", "#F9C8DA", "#F285AD")
        case .teal: return ("#E2F5F7", "#1B7078", "#2DBBC8", "#26434D", "#AFE5EA", "#4DC5D0")
        case .slate: return ("#EAEEF1", "#4A5C6B", "#6A8499", "#303A45", "#C6D0D8", "#8096A8")
        case .brown: return ("#F4EFEA", "#7B6147", "#B08A66", "#3C3B3C", "#E1D2C4", "#BC9C7D")
        case .sand: return ("#F8F5EF", "#71654C", "#CDB88B", "#414342", "#ECE4D3", "#D4C39C")
        case .sage: return ("#EFF4F0", "#56685A", "#8FAE96", "#364144", "#D4E0D7", "#A0BAA6")
        case .navy: return ("#E6E9F3", "#374576", "#4F63A8", "#2B3447", "#BCC3DE", "#697AB5")
        case .plum: return ("#F2EAF1", "#744C6D", "#A56C9C", "#3A3645", "#DDC7D9", "#B282AB")
        }
    }

    var tone: GoalTone { GoalPalette.tones[self] ?? GoalPalette.neutral }

    /// 라이트 accent 의 RGB (0~255) — 가장 가까운 팔레트 찾기용
    var lightAccentRGB: (Double, Double, Double) { GoalPalette.rgb(hexes.2) ?? (0, 0, 0) }

    /// 모든 팔레트의 라이트 accent RGB (한 번만 계산)
    static let accentRGBs: [(GoalPalette, (Double, Double, Double))] = allCases.map { ($0, $0.lightAccentRGB) }

    /// 색이 없을 때 (bg/subtle · text/secondary · text/tertiary)
    static let neutral = GoalTone(bg: .bgSubtle, fg: .textSecondary, accent: .textTertiary)

    private static let tones: [GoalPalette: GoalTone] = Dictionary(uniqueKeysWithValues: allCases.map { p -> (GoalPalette, GoalTone) in
        let h = p.hexes
        return (p, GoalTone(bg: Color(light: h.0, dark: h.3), fg: Color(light: h.1, dark: h.4),
                            accent: Color(light: h.2, dark: h.5)))
    })

    /// "#RRGGBB" / "RRGGBB" / "#RGB" → (r, g, b) 0~255. 잘못된 값이면 nil
    static func rgb(_ hex: String) -> (Double, Double, Double)? {
        var h = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if h.hasPrefix("#") { h.removeFirst() }
        if h.count == 3 { h = h.map { "\($0)\($0)" }.joined() }
        if h.count == 8 { h = String(h.prefix(6)) }
        guard h.count == 6 else { return nil }
        var n: UInt64 = 0
        guard Scanner(string: h).scanHexInt64(&n) else { return nil }
        return (Double((n >> 16) & 0xFF), Double((n >> 8) & 0xFF), Double(n & 0xFF))
    }

    /// 팔레트와 너무 먼 색 → 직접 만들어요
    /// accent = 원래 색 · bg = surface 위에 12%(라이트) / 22%(다크) · fg = 45% 어둡게(라이트) / 60% 밝게(다크)
    static func derived(_ c: (Double, Double, Double)) -> GoalTone {
        let r = CGFloat(c.0 / 255), g = CGFloat(c.1 / 255), b = CGFloat(c.2 / 255)
        let accent = UIColor(red: r, green: g, blue: b, alpha: 1)
        func mix(_ t: CGFloat, over s: (CGFloat, CGFloat, CGFloat)) -> UIColor {
            UIColor(red: s.0 + (r - s.0) * t, green: s.1 + (g - s.1) * t, blue: s.2 + (b - s.2) * t, alpha: 1)
        }
        let lightSurface: (CGFloat, CGFloat, CGFloat) = (1, 1, 1)
        let darkSurface: (CGFloat, CGFloat, CGFloat) = (28.0 / 255, 35.0 / 255, 44.0 / 255)   // #1C232C
        let bgLight = mix(0.12, over: lightSurface)
        let bgDark = mix(0.22, over: darkSurface)
        let fgLight = UIColor(red: r * 0.55, green: g * 0.55, blue: b * 0.55, alpha: 1)
        let fgDark = UIColor(red: r + (1 - r) * 0.6, green: g + (1 - g) * 0.6, blue: b + (1 - b) * 0.6, alpha: 1)
        return GoalTone(
            bg: Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? bgDark : bgLight }),
            fg: Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? fgDark : fgLight }),
            accent: Color(uiColor: accent)
        )
    }
}

extension Color {
    /// 백엔드 목표 색(임의 hex) → 팔레트 색 묶음.
    /// 라이트 accent 와 RGB 거리가 가장 가까운 팔레트를 쓰고, 60 보다 멀면 hex 로 직접 만들어요. 비어 있으면 neutral
    static func goalTone(_ hex: String?) -> GoalTone {
        guard let hex, let c = GoalPalette.rgb(hex) else { return GoalPalette.neutral }
        var best = GoalPalette.coral
        var bestDistance = Double.greatestFiniteMagnitude
        for (p, a) in GoalPalette.accentRGBs {
            let d = ((c.0 - a.0) * (c.0 - a.0) + (c.1 - a.1) * (c.1 - a.1) + (c.2 - a.2) * (c.2 - a.2)).squareRoot()
            if d < bestDistance {
                bestDistance = d
                best = p
            }
        }
        return bestDistance <= 60 ? best.tone : GoalPalette.derived(c)
    }
}

// MARK: - 모서리 · 간격 · 그림자

enum Radius {
    /// 카드 · 목표 요약 · 다이얼로그 (rounded-card)
    static let card: CGFloat = 16
    /// rounded-xl
    static let xl: CGFloat = 12
    /// rounded-2xl
    static let xxl: CGFloat = 16
    /// rounded-lg
    static let lg: CGFloat = 8
    /// 시트 위쪽 모서리 (rounded-3xl)
    static let sheet: CGFloat = 24
    /// 버튼 LG / MD
    static let btn: CGFloat = 12
    /// 버튼 SM
    static let btnSm: CGFloat = 10
    /// 입력칸 · 시간 칸 · 목표 선택
    static let input: CGFloat = 10
    /// 칩 MD / SM
    static let chip: CGFloat = 8
    static let chipSm: CGFloat = 6
    /// 일정 블록 / 고정 일정 블록
    static let event: CGFloat = 8
    static let eventFixed: CGFloat = 10
    /// 내비 항목 · 목표 항목 · 탭바 아이콘 상자
    static let nav: CGFloat = 8
    /// 아이콘 버튼
    static let iconBtn: CGFloat = 6
    static let toast: CGFloat = 12
    static let dialog: CGFloat = 16
    static let checkbox: CGFloat = 6
    /// 세그먼트 바깥 / 안쪽 선택 항목
    static let segment: CGFloat = 12
    static let segmentItem: CGFloat = 10
    static let quickReply: CGFloat = 20
    static let composer: CGFloat = 26
    static let full: CGFloat = 9999
}

extension View {
    /// shadow-card (glass/shadow-soft · 0 4 16)
    /// 0 2 8 rgba(18,24,32,.04) · 다크 rgba(0,0,0,.25) — 카드 그림자는 아주 옅게 (DESIGN_SPEC 12.3)
    func cardShadow() -> some View { shadow(color: Color.cardShadowTint, radius: 4, x: 0, y: 2) }
    /// shadow-pop (glass/shadow · 0 12 40)
    func popShadow() -> some View { shadow(color: Color.glassShadow, radius: 20, x: 0, y: 12) }
    /// 기본(primary) 버튼 그림자 (0 4 12)
    func primaryShadow() -> some View { shadow(color: Color.shadowPrimary, radius: 6, x: 0, y: 4) }
    /// 세그먼트 선택 항목 그림자 (0 1 3 · 8%)
    func segShadow() -> some View { shadow(color: Color.black.opacity(0.08), radius: 1.5, x: 0, y: 1) }
}

// MARK: - 화면 모드 (설정 > 화면 모드: 시스템 / 라이트 / 다크)

enum ThemePref: String, CaseIterable, Identifiable {
    case system, light, dark
    var id: String { rawValue }
    var label: String {
        switch self {
        case .system: return "시스템"
        case .light: return "라이트"
        case .dark: return "다크"
        }
    }
    var icon: String {
        switch self {
        case .system: return "iphone"
        case .light: return "sun.max"
        case .dark: return "moon"
        }
    }
    /// .preferredColorScheme 에 넣을 값 (시스템이면 nil)
    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
    /// @AppStorage 키 (웹 localStorage 'naleul-theme' 와 같은 이름)
    static let storageKey = "naleul-theme"
}
