// DesignSystem/Icons.swift
//
// Figma 디자인 시스템 아이콘 (Lucide 기반 59개 · Assets.xcassets/Icons) 과 로고 (Assets.xcassets/Logo).
//
//   NIcon.calendar.image(size: 20)                    // 18~24pt: 선 1.5 · 14~16pt: "-sm" 에셋(선 1.25pt 로 보임)
//   NSymbol("xmark", size: 13, weight: .semibold)     // SF 이름 → Figma 아이콘 (없으면 SF Symbol 그대로)
//   Wordmark(height: 24) · BrandSymbol(height: 28)    // 로고 (brand/logo/base 색)
//
// 아이콘은 template 이라 .foregroundStyle(...) 로 색을 바꿔요 (주변 글자색을 따라가요).

import SwiftUI

enum NIcon: String, CaseIterable {
    case home, calendar, notebook
    case squarePen = "square-pen"
    case plus, pencil, trash, x, check
    case ellipsisVertical = "ellipsis-vertical"
    case menu, sliders
    case sortAscending = "sort-ascending"
    case chevronLeft = "chevron-left"
    case chevronRight = "chevron-right"
    case chevronUp = "chevron-up"
    case chevronDown = "chevron-down"
    case arrowLeft = "arrow-left"
    case arrowRight = "arrow-right"
    case arrowUp = "arrow-up"
    case search, settings, clock, user
    case logOut = "log-out"
    case eye, trophy
    case gripVertical = "grip-vertical"
    case panelLeftOpen = "panel-left-open"
    case panelLeftClose = "panel-left-close"
    case panelRightOpen = "panel-right-open"
    case target, download, upload
    case listChecks = "list-checks"
    case calendarClock = "calendar-clock"
    case repeatIcon = "repeat"
    case chartBar = "chart-bar"
    case users, palette, bell
    case bellDot = "bell-dot"
    case compass
    case missionPlay = "mission-play"
    case lock, camera, heart
    case heartFilled = "heart-filled"
    case userFilled = "user-filled"
    case crown
    case badgeCheckFilled = "badge-check-filled"
    case trendingUp = "trending-up"
    case trendingDown = "trending-down"
    case sparkles
    case photo = "image"
    case tag, minus, pin, info

    /// 에셋 이름 (14~16pt 는 선을 1.875 로 바꾼 "-sm" 에셋 → 16pt 에서 1.25pt 로 보여요)
    func assetName(size: CGFloat) -> String {
        size <= 16 ? rawValue + "-sm" : rawValue
    }

    /// 아이콘 이미지 (template · size×size)
    func image(size: CGFloat) -> some View {
        Image(assetName(size: size))
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .overlay {
                // bell-dot 의 빨간 점은 색이 고정이라 따로 그려요 (Figma: cx 18.42 · cy 5 · r 3 / 24)
                if self == .bellDot {
                    Circle()
                        .fill(Color.statusErrorFg)
                        .frame(width: size * 0.25, height: size * 0.25)
                        .position(x: size * 0.7675, y: size * 0.2083)
                }
            }
            .accessibilityHidden(true)
    }

    /// SF Symbol 이름 → 같은 뜻의 Figma 아이콘 (없으면 nil)
    static func fromSF(_ name: String) -> NIcon? {
        switch name {
        case "house", "house.fill": return .home
        case "calendar": return .calendar
        case "plus": return .plus
        case "xmark": return .x
        case "checkmark": return .check
        case "chevron.left": return .chevronLeft
        case "chevron.right": return .chevronRight
        case "chevron.up": return .chevronUp
        case "chevron.down": return .chevronDown
        case "arrow.left": return .arrowLeft
        case "arrow.right": return .arrowRight
        case "arrow.up": return .arrowUp
        case "magnifyingglass": return .search
        case "gearshape", "gearshape.fill": return .settings
        case "clock": return .clock
        case "person": return .user
        case "person.fill": return .userFilled
        case "person.2", "person.2.fill": return .users
        case "rectangle.portrait.and.arrow.right": return .logOut
        case "eye": return .eye
        case "trophy", "trophy.fill": return .trophy
        case "line.3.horizontal": return .menu
        case "ellipsis", "ellipsis.vertical": return .ellipsisVertical
        case "slider.horizontal.3": return .sliders
        case "trash": return .trash
        case "pencil", "pencil.line": return .pencil
        case "square.and.pencil": return .squarePen
        case "bell": return .bell
        case "bell.badge": return .bellDot
        case "sparkles", "wand.and.stars": return .sparkles
        case "info.circle": return .info
        case "lock", "lock.fill": return .lock
        case "camera": return .camera
        case "heart": return .heart
        case "heart.fill": return .heartFilled
        case "crown", "crown.fill": return .crown
        case "checkmark.seal.fill": return .badgeCheckFilled
        case "chart.bar", "chart.bar.xaxis": return .chartBar
        case "paintpalette": return .palette
        case "safari": return .compass
        case "target", "scope": return .target
        case "tray.and.arrow.down": return .download
        case "tray.and.arrow.up", "square.and.arrow.up": return .upload
        case "list.bullet.clipboard", "checklist": return .listChecks
        case "calendar.badge.clock": return .calendarClock
        case "repeat": return .repeatIcon
        case "photo": return .photo
        case "tag": return .tag
        case "minus": return .minus
        case "pin", "pin.fill": return .pin
        case "book", "book.closed", "notebook": return .notebook
        case "chart.line.uptrend.xyaxis", "arrow.up.right": return .trendingUp
        case "arrow.down.right": return .trendingDown
        default: return nil
        }
    }
}

/// SF Symbol 이름으로 부르는 아이콘. Figma 에 같은 아이콘이 있으면 그걸, 없으면 SF Symbol 을 그려요.
///   size = 예전 .font(.system(size:)) 값 — Figma 아이콘은 그보다 조금 큰 상자(× 1.2)로 그려요 (SF 글리프 크기와 비슷하게)
struct NSymbol: View {
    let name: String
    var size: CGFloat = 14
    var weight: Font.Weight = .regular

    init(_ name: String, size: CGFloat = 14, weight: Font.Weight = .regular) {
        self.name = name
        self.size = size
        self.weight = weight
    }

    var body: some View {
        if let icon = NIcon.fromSF(name) {
            icon.image(size: (size * 1.2).rounded())
        } else {
            Image(systemName: name).font(.system(size: size, weight: weight))
        }
    }
}

// MARK: - 로고

/// 워드마크 "나를" (118×32) — brand/logo/base 색
struct Wordmark: View {
    var height: CGFloat = 24
    var color: Color = .brandLogoBase

    var body: some View {
        Image("Wordmark")
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(width: height * 118 / 32, height: height)
            .foregroundStyle(color)
            .accessibilityLabel("나를")
    }
}

/// 심볼 (42×28) — brand/logo/base 색
struct BrandSymbol: View {
    var height: CGFloat = 28
    var color: Color = .brandLogoBase

    var body: some View {
        Image("Symbol")
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(width: height * 42 / 28, height: height)
            .foregroundStyle(color)
            .accessibilityHidden(true)
    }
}
