// DesignSystem/Components/Sheets.swift
//
// 웹 Modal · ConfirmDialog · Popover 를 iOS 방식으로.
//  - Modal / Popover   → .sheet 안에 SheetScaffold (아래에서 올라오는 시트)
//  - ConfirmDialog     → .confirmDialog(...) (iOS 기본 Alert)
//
//   .sheet(item: $editing) { item in
//       SheetScaffold(title: "시간 바꾸기", description: "…") {
//           내용
//       } footer: {
//           NButton("취소", variant: .secondary) { editing = nil }
//           NButton("저장") { save() }
//       }
//   }

import SwiftUI

/// 시트 기본 틀 (DESIGN_SPEC 13.2 — 모든 추가 · 수정 · 상세 시트가 같은 모양)
///  - 위 radius 24 · bg/surface · 손잡이 36×4 (위 16 · 아래 12)
///  - 머리: 제목 H3 18 SemiBold 왼쪽 · 설명 Body/SM text/secondary (4 아래) · 오른쪽 닫기(X) MD · 좌우 24
///  - 내용: 좌우 24 · 머리 아래 20
///  - 아래 버튼: 바닥에 붙어 있고 16/24 + 안전 영역 · 내용이 스크롤될 때만 위 1px border/soft
///    버튼은 LG 꽉 차게 (두 개면 Secondary + Primary 나란히 · 8 간격)
struct SheetScaffold<Content: View, Footer: View>: View {
    var title: String?
    var description: String?
    /// 닫기 버튼 · 아래로 끌어 닫기 허용 (저장 중엔 false)
    var dismissible = true
    /// 시트 높이 (기본: 내용 크기에 맞춰 중간/큰)
    var detents: Set<PresentationDetent> = [.medium, .large]
    @ViewBuilder var content: () -> Content
    @ViewBuilder var footer: () -> Footer

    @Environment(\.dismiss) private var dismiss
    @State private var contentHeight: CGFloat = 0
    @State private var viewportHeight: CGFloat = 0
    /// 내용 안 폼이 올려 보낸 버튼 줄 (SheetActions) — 있으면 바닥 버튼 줄에 그려요
    @State private var slot: SheetFooterSlot?

    init(title: String? = nil, description: String? = nil, dismissible: Bool = true,
         detents: Set<PresentationDetent> = [.medium, .large],
         @ViewBuilder content: @escaping () -> Content,
         @ViewBuilder footer: @escaping () -> Footer) {
        self.title = title
        self.description = description
        self.dismissible = dismissible
        self.detents = detents
        self.content = content
        self.footer = footer
    }

    private var hasHeader: Bool { title != nil || description != nil || dismissible }

    var body: some View {
        VStack(spacing: 0) {
            SheetGrabber()
            if hasHeader {
                SheetHeader(title: title, subtitle: description, onClose: dismissible ? { dismiss() } : nil)
            }

            ScrollView {
                content()
                    .padding(.horizontal, 24)
                    .padding(.top, hasHeader ? 20 : 0)
                    .padding(.bottom, 24)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(GeometryReader { g in
                        Color.clear
                            .onAppear { contentHeight = g.size.height }
                            .onChange(of: g.size.height) { _, v in contentHeight = v }
                    })
                    .environment(\.sheetFooterHost, true)
            }
            .onPreferenceChange(SheetFooterSlotKey.self) { v in slot = v }
            .scrollBounceBehavior(.basedOnSize)
            .background(GeometryReader { g in
                Color.clear
                    .onAppear { viewportHeight = g.size.height }
                    .onChange(of: g.size.height) { _, v in viewportHeight = v }
            })

            let f = footer()
            if let slot {
                SheetFooter(showsDivider: contentHeight > viewportHeight + 1) { slot.content }
            } else if !(f is EmptyView) {
                SheetFooter(showsDivider: contentHeight > viewportHeight + 1) { f }
            }
        }
        .background(Color.bgSurface)
        .sheetChrome(detents: detents)
        .interactiveDismissDisabled(!dismissible)
    }
}

extension SheetScaffold where Footer == EmptyView {
    init(title: String? = nil, description: String? = nil, dismissible: Bool = true,
         detents: Set<PresentationDetent> = [.medium, .large],
         @ViewBuilder content: @escaping () -> Content) {
        self.init(title: title, description: description, dismissible: dismissible, detents: detents,
                  content: content, footer: { EmptyView() })
    }
}

/// 시트 손잡이 — 36×4 · radius 2 · border/subtle · 위 16 · 아래 12 (위 끝에 붙지 않게)
struct SheetGrabber: View {
    var body: some View {
        RoundedRectangle(cornerRadius: 2, style: .continuous)
            .fill(Color.borderSubtle)
            .frame(width: 36, height: 4)
            .frame(maxWidth: .infinity)
            .padding(.top, 16)
            .padding(.bottom, 12)
            .accessibilityHidden(true)
    }
}

/// 시트 머리: 제목 H3 · 설명 Body/SM · 닫기(X) IconButton MD — 좌우 24 · 위아래 0
struct SheetHeader: View {
    var title: String?
    var subtitle: String? = nil
    /// nil 이면 닫기 버튼 없음
    var onClose: (() -> Void)? = nil
    var closeLabel = "닫기"
    /// X 로 닫힌 걸 시트 닫기 이벤트(close_method)에 알려요 — 따로 기록하는 시트(로그인)는 false
    var setsCloseHint = true

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                if let title {
                    Text(title).textStyle(.h3).foregroundStyle(Color.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if let subtitle {
                    Text(subtitle).textStyle(.bodySM).foregroundStyle(Color.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let onClose {
                IconButton(systemName: "xmark", size: 18, label: closeLabel) {
                    if setsCloseHint { Analytics.sheetCloseHint = "x_button" }
                    onClose()
                }
                .padding(.top, -4)
                .padding(.trailing, -6)
            }
        }
        .padding(.horizontal, 24)
    }
}

/// 시트 아래 버튼 줄 — 16/24 (+ 안전 영역은 시트가) · 스크롤될 때만 위 1px 선
/// 안의 NButton · .n 버튼은 LG 꽉 차게 (두 개면 반씩 · 8 간격)
struct SheetFooter<Content: View>: View {
    var showsDivider = false
    @ViewBuilder var content: () -> Content

    init(showsDivider: Bool = false, @ViewBuilder content: @escaping () -> Content) {
        self.showsDivider = showsDivider
        self.content = content
    }

    var body: some View {
        HStack(spacing: 8) {
            content()
        }
        .environment(\.nSheetFooter, true)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 24)
        .padding(.vertical, 16)
        .background(Color.bgSurface)
        .overlay(alignment: .top) {
            if showsDivider { Rectangle().fill(Color.borderSoft).frame(height: 1) }
        }
    }
}

/// 시트 안 입력 하나: 라벨 Label/MD text/secondary · 8 아래 입력칸 (입력칸끼리 16 · 묶음끼리 24)
struct SheetField<Content: View>: View {
    let label: String
    @ViewBuilder var content: () -> Content

    init(_ label: String, @ViewBuilder content: @escaping () -> Content) {
        self.label = label
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label).font(.labelMD).foregroundStyle(Color.textSecondary)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// 폼이 바닥 버튼 줄로 올려 보내는 버튼들 — key 가 같으면 같은 버튼으로 봐요
struct SheetFooterSlot: Equatable {
    let key: String
    let content: AnyView

    static func == (a: SheetFooterSlot, b: SheetFooterSlot) -> Bool { a.key == b.key }
}

struct SheetFooterSlotKey: PreferenceKey {
    static let defaultValue: SheetFooterSlot? = nil
    static func reduce(value: inout SheetFooterSlot?, nextValue: () -> SheetFooterSlot?) {
        if let n = nextValue() { value = n }
    }
}

/// 폼 안 버튼 줄 (취소 · 저장 …). SheetScaffold 안이면 바닥에 붙은 버튼 줄로 올라가고 (LG · 꽉 차게 ·
/// 스크롤될 때만 위 선), 시트 밖(화면 안 인라인 폼)이면 그 자리에 그대로 그려요 (DESIGN_SPEC 13.2)
///  - key: 버튼 모양을 바꾸는 값(저장 중 · 저장 가능 · 글자 …)을 이어 붙인 것 — 바뀔 때만 다시 올려요
struct SheetActions<Content: View>: View {
    let key: String
    @ViewBuilder var content: () -> Content
    @Environment(\.sheetFooterHost) private var hosted

    init(key: String, @ViewBuilder content: @escaping () -> Content) {
        self.key = key
        self.content = content
    }

    var body: some View {
        if hosted {
            Color.clear
                .frame(height: 0)
                .preference(key: SheetFooterSlotKey.self,
                            value: SheetFooterSlot(key: key, content: AnyView(HStack(spacing: 8) { content() })))
        } else {
            HStack(spacing: 8) { content() }
        }
    }
}

private struct SheetFooterHostKey: EnvironmentKey {
    static let defaultValue = false
}

/// 시트 버튼 줄 안인지 — NButton 이 LG · 꽉 차게 바뀌어요
private struct NSheetFooterKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    /// SheetScaffold 내용 안인지 (SheetActions 가 바닥 버튼 줄로 올라가요)
    var sheetFooterHost: Bool {
        get { self[SheetFooterHostKey.self] }
        set { self[SheetFooterHostKey.self] = newValue }
    }

    var nSheetFooter: Bool {
        get { self[NSheetFooterKey.self] }
        set { self[NSheetFooterKey.self] = newValue }
    }
}

extension View {
    /// 시트 겉모양 (SheetScaffold 를 못 쓰는 시트도 같이): 위 radius 24 · bg/surface · 시스템 손잡이 대신 SheetGrabber
    func sheetChrome(detents: Set<PresentationDetent> = [.medium, .large]) -> some View {
        self
            .presentationDetents(detents)
            .presentationDragIndicator(.hidden)
            .presentationCornerRadius(Radius.sheet)
            .presentationBackground(Color.bgSurface)
    }
}

// MARK: - 확인창 ("정말 ~할까요?")
// 시트 · 화면 전환과 겹쳐도 안전하도록 시스템 Alert 를 그대로 써요.
// 화면 안에 직접 띄우는 디자인 시스템 다이얼로그는 아래 NDialog 를 써요.

enum ConfirmTone { case primary, danger }

struct ConfirmDialogModifier: ViewModifier {
    @Binding var isPresented: Bool
    let title: String
    var message: String?
    var confirmLabel = "확인"
    var cancelLabel = "취소"
    var tone: ConfirmTone = .primary
    /// 취소를 눌렀을 때 (이벤트 기록용 · 없어도 돼요)
    var onCancel: (() -> Void)? = nil
    let onConfirm: () -> Void

    func body(content: Content) -> some View {
        content.alert(title, isPresented: $isPresented) {
            Button(cancelLabel, role: .cancel) { onCancel?() }
            Button(confirmLabel, role: tone == .danger ? .destructive : nil) { onConfirm() }
        } message: {
            if let message { Text(message) }
        }
    }
}

extension View {
    /// 웹 ConfirmDialog
    func confirmDialog(isPresented: Binding<Bool>, title: String, message: String? = nil,
                       confirmLabel: String = "확인", cancelLabel: String = "취소",
                       tone: ConfirmTone = .primary, onCancel: (() -> Void)? = nil,
                       onConfirm: @escaping () -> Void) -> some View {
        modifier(ConfirmDialogModifier(isPresented: isPresented, title: title, message: message,
                                       confirmLabel: confirmLabel, cancelLabel: cancelLabel,
                                       tone: tone, onCancel: onCancel, onConfirm: onConfirm))
    }
}

// MARK: - Dialog (glass · 폭 340 · padding 20 · 간격 16 · radius 16)

/// 화면 위에 직접 얹는 다이얼로그 카드. 바깥 어둡게(bg/overlay)는 NDialogOverlay 가 해 줘요
///   .overlay { if showing { NDialogOverlay { NDialog(title: "삭제할까요?", ...) } } }
struct NDialog: View {
    let title: String
    var message: String? = nil
    var confirmLabel = "확인"
    var cancelLabel: String? = "취소"
    var tone: ConfirmTone = .primary
    var onCancel: (() -> Void)? = nil
    let onConfirm: () -> Void

    init(title: String, message: String? = nil, confirmLabel: String = "확인", cancelLabel: String? = "취소",
         tone: ConfirmTone = .primary, onCancel: (() -> Void)? = nil, onConfirm: @escaping () -> Void) {
        self.title = title
        self.message = message
        self.confirmLabel = confirmLabel
        self.cancelLabel = cancelLabel
        self.tone = tone
        self.onCancel = onCancel
        self.onConfirm = onConfirm
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: Radius.dialog, style: .continuous)
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 6) {
                Text(title).textStyle(.h3).foregroundStyle(Color.textPrimary)
                if let message {
                    Text(message).textStyle(.bodyMD).foregroundStyle(Color.textSecondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            HStack(spacing: 8) {
                if let cancelLabel {
                    NButton(cancelLabel, variant: .outline, fullWidth: true) { onCancel?() }
                }
                NButton(confirmLabel, variant: tone == .danger ? .danger : .primary, fullWidth: true) { onConfirm() }
            }
        }
        .padding(20)
        .frame(maxWidth: 340)
        .background(Color.glassBg, in: shape)
        .background(.ultraThinMaterial, in: shape)
        .overlay(shape.strokeBorder(Color.glassBorder, lineWidth: 1))
        .shadow(color: Color.glassShadowSoft, radius: 8, x: 0, y: 4)
    }
}

/// 다이얼로그 뒤 어두운 막 (bg/overlay) + 가운데 정렬
struct NDialogOverlay<Content: View>: View {
    var onTapOutside: (() -> Void)? = nil
    @ViewBuilder var content: () -> Content

    init(onTapOutside: (() -> Void)? = nil, @ViewBuilder content: @escaping () -> Content) {
        self.onTapOutside = onTapOutside
        self.content = content
    }

    var body: some View {
        ZStack {
            Color.bgOverlay
                .ignoresSafeArea()
                .onTapGesture { onTapOutside?() }
            content().padding(.horizontal, 24)
        }
        .transition(.opacity)
    }
}

// MARK: - Toast (bg/inverse · radius 12 · padding 12/16 · 간격 10 · 아이콘 16 + Body/SM text/inverse)

enum NToastKind { case info, success, warning, error }

struct NToastView: View {
    let message: String
    var kind: NToastKind = .info
    var actionLabel: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        HStack(spacing: 10) {
            if let icon {
                NSymbol(icon, size: 15, weight: .medium)
                    .foregroundStyle(accent)
            }
            Text(message)
                .font(.bodySM)
                .tracking(-0.26)
                .foregroundStyle(Color.textInverse)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let actionLabel, let action {
                Button(action: action) {
                    Text(actionLabel).font(.bodySMMedium).foregroundStyle(accent)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.bgInverse, in: RoundedRectangle(cornerRadius: Radius.toast, style: .continuous))
        .popShadow()
    }

    private var icon: String? {
        switch kind {
        case .info: return nil
        case .success: return "checkmark.circle.fill"
        case .warning: return "exclamationmark.triangle.fill"
        case .error: return "exclamationmark.circle.fill"
        }
    }

    /// 반전 면 위라서 상태 색도 반대 모드 값을 써요 (라이트 → 다크 값 · 다크 → 라이트 값)
    private var accent: Color {
        switch kind {
        case .info: return .textInverse
        case .success: return .onInverseSuccess
        case .warning: return .onInverseWarning
        case .error: return .onInverseError
        }
    }
}

#Preview {
    struct Demo: View {
        @State private var open = true
        @State private var confirm = false
        var body: some View {
            VStack(spacing: 12) {
                NButton("시트 열기") { open = true }
                NButton("삭제", variant: .danger) { confirm = true }
            }
            .sheet(isPresented: $open) {
                SheetScaffold(title: "시간 바꾸기", description: "이날만 바꿀지, 앞으로 계속 바꿀지 골라요.") {
                    Text("내용").font(.pt(15))
                } footer: {
                    NButton("취소", variant: .secondary) { open = false }
                    NButton("저장") { open = false }
                }
            }
            .confirmDialog(isPresented: $confirm, title: "이 Task를 삭제할까요?", message: "되돌릴 수 없어요.",
                           confirmLabel: "삭제", tone: .danger) {}
        }
    }
    return Demo()
}
