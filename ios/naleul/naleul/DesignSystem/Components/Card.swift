// DesignSystem/Components/Card.swift
//
// Component_ver.2 — Card · CardHeader · Chip(선택 칩) · GoalChip(목표 색 칩) · Badge/Tag(상태 알약) · EventBlock(일정 블록)
//
//   VStack { ... }.padding(20).card()
//   CardHeader(title: "루틴", aside: "3개")
//   Chip("아침", selected: picked) { toggle() }
//   GoalChip("운동", colorCode: goal.colorCode, selected: true) { pick() }
//   Badge("AI가 설계한 목표", tone: .brand) · Tag("진행 중", tone: .success, dot: true)
//   EventBlock(title: "러닝", time: "07:00 – 07:30", colorCode: "#4BC47C", state: .done)

import SwiftUI

// MARK: - Card (bg/surface · 1px border/subtle · radius 16 · shadow-card)

struct CardModifier: ViewModifier {
    var radius: CGFloat = Radius.card
    var background: Color = .bgSurface

    func body(content: Content) -> some View {
        content
            .background(background, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).strokeBorder(Color.borderSubtle, lineWidth: 1))
            .cardShadow()
    }
}

extension View {
    /// rounded-card border-subtle bg-surface shadow-card
    func card(radius: CGFloat = Radius.card, background: Color = .bgSurface) -> some View {
        modifier(CardModifier(radius: radius, background: background))
    }
}

struct CardHeader<Aside: View>: View {
    let title: String
    @ViewBuilder var aside: () -> Aside

    init(_ title: String, @ViewBuilder aside: @escaping () -> Aside) {
        self.title = title
        self.aside = aside
    }

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            Text(title).textStyle(.h4).foregroundStyle(Color.textPrimary)
            Spacer(minLength: 0)
            aside().font(.captionMD).foregroundStyle(Color.textSecondary)
        }
    }
}

extension CardHeader where Aside == EmptyView {
    init(_ title: String) {
        self.init(title) { EmptyView() }
    }
}

extension CardHeader where Aside == Text {
    init(_ title: String, aside: String) {
        self.init(title) { Text(aside) }
    }
}

// MARK: - AI 표시 (DESIGN_SPEC §6 — 모든 AI 기능은 그라데이션)

/// AI 결과를 담는 면: 1.5pt 그라데이션 안쪽 테두리 + glass(또는 surface) 바탕 + 그림자 (0 12 36 8% · 0 2 8 4%)
///   VStack { ... }.padding(16).aiBorder()                 // 카드 (bg/surface)
///   bar.aiBorder(cornerRadius: 16, glass: true)           // 떠 있는 제안 바 (glass/bg + blur)
struct AIBorderModifier: ViewModifier {
    var cornerRadius: CGFloat = Radius.card
    var glass = false
    /// true = surface 위에 ai/tint-soft 를 깔아요 (AI 분석 히어로 카드)
    var tinted = false

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        return content
            .background {
                ZStack {
                    if glass {
                        shape.fill(.ultraThinMaterial)
                        shape.fill(Color.glassBg)
                    } else {
                        shape.fill(Color.bgSurface)
                    }
                    if tinted {
                        shape.fill(Color.aiTintSoft)
                    }
                }
                .shadow(color: Color.black.opacity(0.08), radius: 18, x: 0, y: 12)
                .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 2)
            }
            .overlay(shape.strokeBorder(Color.aiGradient, lineWidth: 1.5))
    }
}

extension View {
    /// AI 그라데이션 테두리 면 (ProposalBar · AI 요청 바 · AI 제안 카드/패널)
    func aiBorder(cornerRadius: CGFloat = Radius.card, glass: Bool = false, tinted: Bool = false) -> some View {
        modifier(AIBorderModifier(cornerRadius: cornerRadius, glass: glass, tinted: tinted))
    }
}

/// AI 배지: 28×28 · radius 8 · 그라데이션 + 흰 sparkles 16 (작게: 20×20 · r6 · 아이콘 12)
struct AIBadge: View {
    enum Size { case md, sm }
    var size: Size = .md
    var icon = "sparkles"

    init(size: Size = .md, icon: String = "sparkles") {
        self.size = size
        self.icon = icon
    }

    var body: some View {
        let md = size == .md
        NSymbol(icon, size: md ? 13.3 : 10, weight: .semibold)
            .foregroundStyle(Color.white)
            .frame(width: md ? 28 : 20, height: md ? 28 : 20)
            .background(Color.aiGradient, in: RoundedRectangle(cornerRadius: md ? 8 : 6, style: .continuous))
            .accessibilityHidden(true)
    }
}

// MARK: - Chip (선택 가능한 칩 · 필터/옵션)

enum ChipSize { case sm, md }

/// 선택 칩. 기본: bg/surface + 1px border/subtle + text/secondary · 선택: action/secondary/bg + 1.5px text/brand 테두리 + text/brand
struct Chip: View {
    let title: String
    var selected = false
    var size: ChipSize = .md
    var icon: String? = nil
    let action: () -> Void

    init(_ title: String, selected: Bool = false, size: ChipSize = .md, icon: String? = nil, action: @escaping () -> Void) {
        self.title = title
        self.selected = selected
        self.size = size
        self.icon = icon
        self.action = action
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: size == .sm ? Radius.chipSm : Radius.chip, style: .continuous)
        Button(action: action) {
            HStack(spacing: 4) {
                if let icon { NSymbol(icon, size: size == .sm ? 11 : 13, weight: .medium) }
                Text(title)
            }
            .font(.pt(size == .sm ? 12 : 13, size == .sm ? .semibold : .medium))
            .tracking(size == .sm ? -0.24 : -0.26)
            .foregroundStyle(selected ? Color.textBrand : Color.textSecondary)
            .padding(.horizontal, size == .sm ? 10 : 12)
            .frame(height: size == .sm ? 28 : 32)
            .background(selected ? Color.actionSecondaryBg : Color.bgSurface, in: shape)
            .overlay(shape.strokeBorder(selected ? Color.textBrand : Color.borderSubtle, lineWidth: selected ? 1.5 : 1))
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

// MARK: - QuickReply (AI 대화 빠른 답변 · radius 20 · padding 9/16)

struct QuickReply: View {
    let title: String
    var selected = false
    let action: () -> Void

    init(_ title: String, selected: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.selected = selected
        self.action = action
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: Radius.quickReply, style: .continuous)
        Button(action: action) {
            Text(title)
                .font(.bodyMD)
                .tracking(-0.28)
                .foregroundStyle(selected ? Color.textBrand : Color.textPrimary)
                .padding(.horizontal, 16)
                .padding(.vertical, 9)
                .background(selected ? Color.aiTintSoft : Color.bgSurface, in: shape)
                .overlay(shape.strokeBorder(selected ? Color.textBrand : Color.borderSubtle, lineWidth: selected ? 1.5 : 1))
                .contentShape(shape)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

// MARK: - GoalChip (목표 색 칩 — tint + 색 막대)

/// MD h32 · 막대 4×16 · Label/MD   /   SM h24 · 막대 3×12 · Label/SM
/// 선택: 1.5px accent 테두리 · 비활성: disabled/bg + disabled/fg
struct GoalChip: View {
    let title: String
    var colorCode: String?
    var size: ChipSize = .md
    var selected = false
    var disabled = false
    var onClose: (() -> Void)? = nil
    var action: (() -> Void)? = nil

    init(_ title: String, colorCode: String?, size: ChipSize = .md, selected: Bool = false, disabled: Bool = false,
         onClose: (() -> Void)? = nil, action: (() -> Void)? = nil) {
        self.title = title
        self.colorCode = colorCode
        self.size = size
        self.selected = selected
        self.disabled = disabled
        self.onClose = onClose
        self.action = action
    }

    var body: some View {
        if let action {
            Button(action: action) { chip }
                .buttonStyle(.plain)
                .disabled(disabled)
                .accessibilityAddTraits(selected ? .isSelected : [])
        } else {
            chip
        }
    }

    private var chip: some View {
        let tone = Color.goalTone(colorCode)
        let md = size == .md
        let shape = RoundedRectangle(cornerRadius: md ? Radius.chip : Radius.chipSm, style: .continuous)
        return HStack(spacing: md ? 4 : 3) {
            RoundedRectangle(cornerRadius: md ? 2 : 1.5)
                .fill(disabled ? Color.disabledFg : tone.accent)
                .frame(width: md ? 4 : 3, height: md ? 16 : 12)
            Text(title)
                .font(.pt(md ? 13 : 12, .semibold))
                .tracking(md ? -0.26 : -0.24)
                .foregroundStyle(disabled ? Color.disabledFg : tone.fg)
                .lineLimit(1)
            if let onClose {
                Button(action: onClose) {
                    NSymbol("xmark", size: md ? 11 : 9, weight: .semibold)
                        .foregroundStyle(disabled ? Color.disabledFg : tone.fg)
                        .frame(width: md ? 16 : 14, height: md ? 16 : 14)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(title) 빼기")
            }
        }
        .padding(.leading, md ? 10 : 8)
        .padding(.trailing, md ? 8 : 6)
        .frame(height: md ? 32 : 24)
        .background(disabled ? Color.disabledBg : tone.bg, in: shape)
        .overlay {
            if selected && !disabled {
                shape.strokeBorder(tone.accent, lineWidth: 1.5)
            }
        }
        .contentShape(shape)
    }
}

// MARK: - Badge / Tag (클릭 안 되는 상태 알약 · h24 px10 · radius full · Label/SM)

enum BadgeTone {
    case brand, success, warning, danger, neutral, mint, info
    /// AI 표시 (ai/tint 바탕 · text/brand 글자 · 아이콘은 AI 그라데이션)
    case ai

    var background: Color {
        switch self {
        case .brand: return .actionSecondaryBg
        case .success: return .statusSuccessBg
        case .warning: return .statusWarningBg
        case .danger: return .statusErrorBg
        case .neutral: return .bgSubtle
        case .mint: return .moodMint
        case .info: return .statusInfoBg
        case .ai: return .aiTint
        }
    }
    var foreground: Color {
        switch self {
        case .brand: return .textBrand
        case .success: return .statusSuccessFg
        case .warning: return .statusWarningFg
        case .danger: return .statusErrorFg
        case .neutral: return .textSecondary
        case .mint: return GoalPalette.mint.tone.fg
        case .info: return .statusInfoFg
        case .ai: return .textBrand
        }
    }
    /// 점(dot) 색
    var dot: Color {
        switch self {
        case .mint, .ai: return .accentMint
        case .neutral: return .textTertiary
        default: return foreground
        }
    }
}

typealias TagTone = BadgeTone

struct Badge: View {
    let title: String
    var tone: BadgeTone = .neutral
    var icon: String? = nil
    /// 앞에 6pt 점
    var dot = false

    init(_ title: String, tone: BadgeTone = .neutral, icon: String? = nil, dot: Bool = false) {
        self.title = title
        self.tone = tone
        self.icon = icon
        self.dot = dot
    }

    var body: some View {
        HStack(spacing: 6) {
            if dot { Circle().fill(tone.dot).frame(width: 6, height: 6) }
            if let icon {
                if tone == .ai {
                    NSymbol(icon, size: 10, weight: .semibold).foregroundStyle(Color.aiGradient)
                } else {
                    NSymbol(icon, size: 10, weight: .semibold)
                }
            }
            Text(title).lineLimit(1)
        }
        .font(.labelSM)
        .tracking(-0.24)
        .foregroundStyle(tone.foreground)
        .padding(.horizontal, 10)
        .frame(height: 24)
        .background(tone.background, in: Capsule())
    }
}

/// Figma "Tag" — Badge 와 같아요. 경고(warning)는 bg/subtle + 노란 점 모양
struct Tag: View {
    let title: String
    var tone: TagTone = .neutral
    var dot = false

    init(_ title: String, tone: TagTone = .neutral, dot: Bool = false) {
        self.title = title
        self.tone = tone
        self.dot = dot
    }

    var body: some View {
        if tone == .warning {
            HStack(spacing: 6) {
                Circle().fill(Color.warning).frame(width: 6, height: 6)
                Text(title).lineLimit(1)
            }
            .font(.labelSM)
            .tracking(-0.24)
            .foregroundStyle(Color.textSecondary)
            .padding(.horizontal, 10)
            .frame(height: 24)
            .background(Color.bgSubtle, in: Capsule())
        } else {
            Badge(title, tone: tone, dot: dot)
        }
    }
}

// MARK: - EventBlock (캘린더 일정 블록 — tint + 왼쪽 색 막대)

enum EventBlockState {
    case scheduled, done, incomplete, proposal, proposalActive
}

/// radius 8 · padding 6/8/6/12 · 세로 간격 2 · 바탕 goal/X/bg · 왼쪽 4pt accent 막대
struct EventBlock: View {
    let title: String
    var time: String? = nil
    var colorCode: String?
    var state: EventBlockState = .scheduled

    var body: some View {
        let tone = Color.goalTone(colorCode)
        let shape = RoundedRectangle(cornerRadius: Radius.event, style: .continuous)
        let soft = state == .incomplete || state == .proposal
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 4) {
                if state == .done {
                    NSymbol("checkmark", size: 8, weight: .heavy)
                        .foregroundStyle(Color.white)
                        .frame(width: 14, height: 14)
                        .background(tone.accent, in: Circle())
                }
                if state == .proposal || state == .proposalActive {
                    NSymbol("sparkles", size: 12).foregroundStyle(tone.accent)
                }
                Text(title)
                    .font(.labelSM)
                    .tracking(-0.24)
                    .foregroundStyle(tone.fg)
                    .lineLimit(1)
            }
            if let time {
                Text(time).font(.captionSM).foregroundStyle(Color.textSecondary).lineLimit(1)
            }
        }
        .padding(EdgeInsets(top: 6, leading: 12, bottom: 6, trailing: 8))
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .background {
            ZStack(alignment: .leading) {
                Color.bgSurface
                tone.bg.opacity(soft ? 0.55 : 1)
                Rectangle().fill(tone.accent).frame(width: 4)
            }
        }
        .clipShape(shape)
        .overlay {
            switch state {
            case .incomplete: shape.strokeBorder(tone.accent, lineWidth: 1)
            case .proposal, .proposalActive: shape.strokeBorder(tone.accent, lineWidth: 1.5)
            default: EmptyView()
            }
        }
        .shadow(color: state == .proposalActive ? Color.shadowPrimary : .clear, radius: 6, y: 4)
    }
}

/// 고정 일정 (점심 · 수면 등): bg/subtle · 1px border/subtle · radius 10 · 시계 아이콘 + Caption/MD text/secondary
struct FixedEventBlock: View {
    let title: String
    var time: String? = nil
    var icon = "clock"

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: Radius.eventFixed, style: .continuous)
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 4) {
                NSymbol(icon, size: 12)
                Text(title).font(.captionMD).lineLimit(1)
            }
            if let time { Text(time).font(.captionSM).lineLimit(1) }
        }
        .foregroundStyle(Color.textSecondary)
        .padding(EdgeInsets(top: 6, leading: 8, bottom: 6, trailing: 8))
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .background(Color.bgSubtle, in: shape)
        .overlay(shape.strokeBorder(Color.borderSubtle, lineWidth: 1))
    }
}

#Preview {
    ScrollView {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader("루틴", aside: "3개")
                Text("카드 내용").font(.bodyMD).foregroundStyle(Color.textSecondary)
            }
            .padding(20)
            .card()

            HStack {
                Chip("아침", selected: true) {}
                Chip("저녁") {}
                Chip("작게", size: .sm) {}
            }
            HStack {
                GoalChip("운동", colorCode: "#4BC47C", selected: true) {}
                GoalChip("공부", colorCode: "#5586F0", onClose: {})
                GoalChip("독서", colorCode: "#123456", size: .sm)
            }
            HStack {
                Badge("AI가 설계한 목표", tone: .brand, icon: "sparkles")
                Badge("루틴", tone: .success)
                Tag("마감", tone: .warning)
                Tag("Task", dot: true)
            }
            EventBlock(title: "러닝", time: "07:00 – 07:30", colorCode: "#4BC47C")
            EventBlock(title: "러닝", time: "07:00 – 07:30", colorCode: "#4BC47C", state: .done)
            EventBlock(title: "러닝", time: "07:00 – 07:30", colorCode: "#4BC47C", state: .incomplete)
            EventBlock(title: "러닝", time: "07:00 – 07:30", colorCode: "#4BC47C", state: .proposal)
            EventBlock(title: "러닝", time: "07:00 – 07:30", colorCode: "#4BC47C", state: .proposalActive)
            FixedEventBlock(title: "점심", time: "12:00 – 13:00")
        }
        .padding()
    }
    .background(Color.canvas)
}
