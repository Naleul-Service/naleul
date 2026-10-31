// DesignSystem/Components/NButton.swift
//
// Component_ver.2 버튼 — "Style A Soft Flat". Type × Size × State × Icon(None / Left / Right)
//
//   NButton("저장") { save() }                                       // primary (브랜드 채우기)
//   NButton("다시 배치", variant: .secondary, size: .sm, icon: "arrow.clockwise", loading: isSaving) { ... }
//   NButton("다음", icon: "chevron.right", iconPosition: .trailing) { ... }
//   NButton("AI로 배치하기", variant: .ai, icon: "sparkles") { ... }
//   Button { ... } label: { 직접 만든 라벨 }.buttonStyle(.n(.primary, .lg, fullWidth: true))
//
//   type        bg                              fg                  border
//   primary     action/primary (+pressed)       white               — (primary shadow)
//   secondary   action/secondary/bg (+pressed)  action/secondary/fg 1px action/secondary/border
//   outline     bg/surface (+ghost pressed)     text/brand          1px action/outline/border
//   ghost       transparent (+ghost pressed)    text/brand          —
//   danger      action/danger (+pressed)        white               —
//   ai          ai/gradient 115° (+pressed)     white               — (primary shadow, 누르면 없음)
//   disabled    disabled/bg                     disabled/fg         —

import SwiftUI

enum NButtonVariant {
    /// 브랜드 채우기 (기본)
    case primary
    /// 예전 파란 버튼 — 지금은 primary 와 같아요
    case brand
    /// 연한 브랜드 바탕 + 테두리
    case secondary
    /// 흰 바탕 + 브랜드 테두리
    case outline
    /// 투명 · 브랜드 글자
    case ghost
    /// 빨간 채우기
    case danger
    /// AI 그라데이션
    case ai
}

enum NButtonIconPosition { case leading, trailing }

enum NButtonSize {
    case sm, md, lg

    var height: CGFloat {
        switch self {
        case .sm: return 32
        case .md: return 40
        case .lg: return 48
        }
    }
    var hPadding: CGFloat {
        switch self {
        case .sm: return 12
        case .md: return 16
        case .lg: return 20
        }
    }
    /// 아이콘 쪽 안쪽 여백 (기본 − 4)
    var iconSidePadding: CGFloat { hPadding - 4 }
    var fontSize: CGFloat {
        switch self {
        case .sm, .md: return 13
        case .lg: return 14
        }
    }
    var iconSize: CGFloat {
        switch self {
        case .sm: return 14
        case .md: return 16
        case .lg: return 18
        }
    }
    var radius: CGFloat { self == .sm ? Radius.btnSm : Radius.btn }
    var spacing: CGFloat { self == .sm ? 4 : 6 }
}

struct NButtonStyle: ButtonStyle {
    var variant: NButtonVariant = .primary
    var size: NButtonSize = .md
    var fullWidth = false
    /// 아이콘이 있는 쪽 (안쪽 여백을 4 줄여요). nil = 아이콘 없음
    var iconPosition: NButtonIconPosition? = nil
    @Environment(\.isEnabled) private var isEnabled
    /// 시트 아래 버튼 줄 안이면 LG · 꽉 차게 (DESIGN_SPEC 13.2)
    @Environment(\.nSheetFooter) private var inSheetFooter

    func makeBody(configuration: Configuration) -> some View {
        let pressed = configuration.isPressed
        let size = inSheetFooter ? NButtonSize.lg : self.size
        let fullWidth = inSheetFooter || self.fullWidth
        let shape = RoundedRectangle(cornerRadius: size.radius, style: .continuous)
        return configuration.label
            .font(.pt(size.fontSize, .medium))
            .tracking(-0.02 * size.fontSize)
            .lineLimit(1)
            .foregroundStyle(foreground)
            .padding(.leading, iconPosition == .leading ? size.iconSidePadding : size.hPadding)
            .padding(.trailing, iconPosition == .trailing ? size.iconSidePadding : size.hPadding)
            .frame(height: size.height)
            .frame(maxWidth: fullWidth ? .infinity : nil)
            .background { background(pressed: pressed).clipShape(shape) }
            .overlay {
                if let border, isEnabled {
                    shape.strokeBorder(border, lineWidth: 1)
                }
            }
            .shadow(color: hasShadow && !pressed ? Color.shadowPrimary : Color.clear, radius: 6, x: 0, y: 4)
            .contentShape(shape)
            .animation(.easeOut(duration: 0.12), value: pressed)
    }

    private var hasShadow: Bool {
        guard isEnabled else { return false }
        switch variant {
        case .primary, .brand, .ai: return true
        default: return false
        }
    }

    private var foreground: Color {
        guard isEnabled else { return .disabledFg }
        switch variant {
        case .primary, .brand: return .actionPrimaryFg
        case .danger: return .actionDangerFg
        case .ai: return .white
        case .secondary: return .actionSecondaryFg
        case .outline, .ghost: return .textBrand
        }
    }

    private var border: Color? {
        switch variant {
        case .secondary: return .actionSecondaryBorder
        case .outline: return .actionOutlineBorder
        default: return nil
        }
    }

    @ViewBuilder
    private func background(pressed: Bool) -> some View {
        if !isEnabled {
            variant == .ghost ? Color.clear : Color.disabledBg
        } else {
            switch variant {
            case .primary, .brand: pressed ? Color.actionPrimaryPressed : Color.actionPrimary
            case .secondary: pressed ? Color.actionSecondaryBgPressed : Color.actionSecondaryBg
            case .outline: pressed ? Color.ghostPressed : Color.bgSurface
            case .ghost: pressed ? Color.ghostPressed : Color.clear
            case .danger: pressed ? Color.actionDangerPressed : Color.actionDanger
            case .ai:
                if pressed {
                    Color.aiGradientPressed
                } else {
                    Color.aiGradient
                }
            }
        }
    }
}

extension ButtonStyle where Self == NButtonStyle {
    static func n(_ variant: NButtonVariant = .primary, _ size: NButtonSize = .md, fullWidth: Bool = false,
                  iconPosition: NButtonIconPosition? = nil) -> NButtonStyle {
        NButtonStyle(variant: variant, size: size, fullWidth: fullWidth, iconPosition: iconPosition)
    }
}

/// 글자(+아이콘) 버튼. loading 이면 스피너가 돌고 눌리지 않아요
struct NButton: View {
    let title: String
    var variant: NButtonVariant = .primary
    var size: NButtonSize = .md
    var icon: String? = nil
    var iconPosition: NButtonIconPosition = .leading
    var loading = false
    var fullWidth = false
    let action: () -> Void

    init(_ title: String, variant: NButtonVariant = .primary, size: NButtonSize = .md, icon: String? = nil,
         iconPosition: NButtonIconPosition = .leading,
         loading: Bool = false, fullWidth: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.variant = variant
        self.size = size
        self.icon = icon
        self.iconPosition = iconPosition
        self.loading = loading
        self.fullWidth = fullWidth
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: size.spacing) {
                if loading {
                    ProgressView().controlSize(.small).tint(spinnerTint)
                } else if let icon, iconPosition == .leading {
                    iconView(icon)
                }
                Text(title)
                if !loading, let icon, iconPosition == .trailing {
                    iconView(icon)
                }
            }
        }
        .buttonStyle(.n(variant, size, fullWidth: fullWidth, iconPosition: styleIconPosition))
        .disabled(loading)
    }

    /// 스타일에 넘길 아이콘 위치 (아이콘 · 스피너가 없으면 nil)
    private var styleIconPosition: NButtonIconPosition? {
        if loading { return .leading }
        return icon == nil ? nil : iconPosition
    }

    private func iconView(_ name: String) -> some View {
        NSymbol(name, size: size.iconSize - 2, weight: .medium)
    }

    private var spinnerTint: Color {
        switch variant {
        case .primary, .brand, .danger, .ai: return .white
        case .secondary: return .actionSecondaryFg
        case .outline, .ghost: return .textBrand
        }
    }
}

/// 아이콘만 있는 작은 버튼 (닫기 · 더보기 등).
/// MD 32×32 (아이콘 18) · SM 28×28 (아이콘 16) · radius 6 · 누르면 bg/subtle · 아이콘 text/secondary
struct IconButton: View {
    let systemName: String
    var size: CGFloat = 16
    var color: Color = .textSecondary
    var label: String = ""
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            NSymbol(systemName, size: size, weight: .medium)
                .foregroundStyle(color)
                .frame(width: max(28, size + 14), height: max(28, size + 14))
                .contentShape(Rectangle())
        }
        .buttonStyle(IconButtonStyle())
        .accessibilityLabel(label)
    }
}

/// IconButton 누름 효과 (bg/subtle · radius 6)
struct IconButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(configuration.isPressed ? Color.bgSubtle : Color.clear,
                        in: RoundedRectangle(cornerRadius: Radius.iconBtn, style: .continuous))
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

// MARK: - 떠 있는 동그란 버튼 (FAB · DESIGN_SPEC 13.6)

enum NFabStyle {
    /// 브랜드 채우기 (+ · 적기)
    case primary
    /// AI 그라데이션 (✨ · 말로 고치기)
    case ai
    /// 꺼진 모양 (흰 바탕 + 테두리)
    case idle
}

/// 앱 전체 FAB 모양 하나: 56×56 원 · 아이콘 22 (캘린더 + 버튼 값) · 같은 그림자 · 버튼끼리 12 간격
/// Button / Menu 의 label 로 써요:  Button { … } label: { NFabFace(icon: "plus") }.buttonStyle(NFabPressStyle())
struct NFabFace: View {
    static let size: CGFloat = 56
    static let iconSize: CGFloat = 22
    /// FAB 여러 개를 세로로 쌓을 때 간격
    static let gap: CGFloat = 12

    let icon: String
    var style: NFabStyle = .primary
    var loading = false
    /// 오른쪽 위 숫자 배지 (nil · 0 이면 없음)
    var badge: Int? = nil

    var body: some View {
        ZStack {
            switch style {
            case .primary:
                Circle().fill(Color.actionPrimary)
            case .ai:
                Circle().fill(Color.aiGradient)
            case .idle:
                Circle().fill(Color.bgSurface)
                Circle().strokeBorder(Color.borderSubtle, lineWidth: 1)
            }
            if loading {
                ProgressView().tint(style == .idle ? Color.textTertiary : Color.white)
            } else {
                NSymbol(icon, size: Self.iconSize, weight: .semibold)
                    .foregroundStyle(foreground)
            }
        }
        .frame(width: Self.size, height: Self.size)
        .primaryShadow()
        .overlay(alignment: .topTrailing) {
            if let badge, badge > 0, !loading {
                Text("\(badge)")
                    .font(.ptMono(11, .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 5)
                    .frame(minWidth: 20, minHeight: 20)
                    .background(Color.actionDanger, in: Capsule())
                    .overlay(Capsule().stroke(Color.bgSurface, lineWidth: 2))
                    .offset(x: 4, y: -4)
                    .transition(.scale)
            }
        }
    }

    private var foreground: Color {
        switch style {
        case .primary: return .actionPrimaryFg
        case .ai: return .white
        case .idle: return .textTertiary
        }
    }
}

/// FAB 누르는 동안 살짝 작아져요
struct NFabPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.92 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

#Preview {
    VStack(spacing: 12) {
        NButton("저장", variant: .primary) {}
        NButton("AI로 배치하기", variant: .ai, icon: "sparkles") {}
        NButton("취소", variant: .secondary) {}
        NButton("다음", variant: .outline, icon: "chevron.right", iconPosition: .trailing) {}
        NButton("더보기", variant: .ghost, size: .sm) {}
        NButton("삭제", variant: .danger, loading: true) {}
        NButton("Task 추가", variant: .primary, size: .lg, icon: "plus", fullWidth: true) {}
        NButton("비활성", variant: .primary) {}.disabled(true)
        IconButton(systemName: "xmark") {}
    }
    .padding()
    .background(Color.canvas)
}
