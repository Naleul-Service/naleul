// Core/State/Toast.swift
//
// 웹 stores/toastStore.ts 의 toast.success('...') / toast.error('...') 와 같아요.
// 화면 아래에 잠깐 떴다 사라지는 알림. 루트(RootView)에 .toastHost() 가 한 번 붙어 있어요.
//
//   Toast.success("완료했어요.")
//   Toast.error(errorMessage(error))
//   Toast.show("배치할 Task가 없었어요.")

import SwiftUI
import UIKit
import Observation

enum ToastTone {
    case `default`, success, error

    /// toast_tone 값 (웹과 같은 이름)
    var analyticsName: String {
        switch self {
        case .default: return "default"
        case .success: return "success"
        case .error: return "error"
        }
    }
}

struct ToastItem: Identifiable, Equatable {
    let id: Int
    let message: String
    let tone: ToastTone
}

@MainActor
@Observable
final class ToastCenter {
    static let shared = ToastCenter()
    private init() {}

    private(set) var toasts: [ToastItem] = []
    private var seq = 0

    func show(_ message: String, tone: ToastTone = .default) {
        Analytics.track(AnalyticsEvent.toastView, [
            "toast_tone": tone.analyticsName, "toast_message": message, "current_path": Analytics.currentScreen,
        ])
        seq += 1
        let id = seq
        // 최대 3개
        withAnimation(.easeOut(duration: 0.18)) {
            toasts = Array((toasts + [ToastItem(id: id, message: message, tone: tone)]).suffix(3))
        }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_800_000_000)
            self.dismiss(id)
        }
    }

    func dismiss(_ id: Int) {
        withAnimation(.easeIn(duration: 0.15)) { toasts.removeAll { $0.id == id } }
    }
}

/// 어디서든 부르는 단축 함수
enum Toast {
    @MainActor static func show(_ message: String) { ToastCenter.shared.show(message) }
    @MainActor static func success(_ message: String) { ToastCenter.shared.show(message, tone: .success) }
    /// 둘러보기 중 저장 시도는 로그인 안내 시트가 대신 알려줘서 토스트는 겹치지 않게 생략해요
    @MainActor static func error(_ message: String) {
        if message == APIError.guestMessage { return }
        ToastCenter.shared.show(message, tone: .error)
    }
}

// MARK: - 화면
//
// 시트(.sheet)나 전체 화면(.fullScreenCover) 위에서도 보이도록, 토스트는 앱 화면 위에 따로 띄운
// 투명한 창(UIWindow)에 그려요. 그 창은 토스트가 아닌 곳의 터치를 아래로 그대로 흘려보내요.

private struct ToastOverlayView: View {
    private var center = ToastCenter.shared

    var body: some View {
        VStack(spacing: 8) {
            Spacer(minLength: 0)
            ForEach(center.toasts) { t in
                Button {
                    Analytics.track(AnalyticsEvent.toastDismissClick, ["toast_tone": t.tone.analyticsName])
                    center.dismiss(t.id)
                } label: {
                    NToastView(message: t.message, kind: toastKind(t.tone))
                        .fixedSize(horizontal: false, vertical: true)
                }
                .buttonStyle(.plain)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .frame(maxWidth: 480)
        .padding(.horizontal, 16)
        .padding(.bottom, 90)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func toastKind(_ tone: ToastTone) -> NToastKind {
        switch tone {
        case .default: return .info
        case .success: return .success
        case .error: return .error
        }
    }
}

/// 토스트가 없는 곳의 터치는 통과시키는 창
private final class PassthroughWindow: UIWindow {
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        guard let hit = super.hitTest(point, with: event) else { return nil }
        // 루트 뷰(투명 배경) 자체가 잡히면 아래 창으로 넘겨요
        return hit === rootViewController?.view ? nil : hit
    }
}

@MainActor
private enum ToastWindowInstaller {
    static var window: UIWindow?

    static func install() {
        guard window == nil,
              let scene = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .first(where: { $0.activationState == .foregroundActive }) ??
                UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first
        else { return }
        let w = PassthroughWindow(windowScene: scene)
        let host = UIHostingController(rootView: ToastOverlayView())
        host.view.backgroundColor = .clear
        w.rootViewController = host
        w.windowLevel = .alert + 1
        w.backgroundColor = .clear
        w.isHidden = false
        window = w
    }
}

private struct ToastHost: ViewModifier {
    func body(content: Content) -> some View {
        content.onAppear { ToastWindowInstaller.install() }
    }
}

extension View {
    /// 루트 화면에 한 번만 붙여요
    func toastHost() -> some View { modifier(ToastHost()) }
}
