// Features/Auth/LoginView.swift
//
// 웹 (auth)/login/page.tsx 와 같은 로그인 화면. 카카오 · 애플 로그인.

import SwiftUI
import KakaoSDKAuth
import KakaoSDKUser
import AuthenticationServices
import KakaoSDKCommon

struct LoginView: View {
    /// true: 둘러보기 중 저장하려다 뜬 로그인 시트 (둘러보기 버튼 대신 "계속 둘러보기")
    var asSheet: Bool = false
    @State private var isLoading = false
    @State private var errorMessage: String?
    @Environment(\.dismiss) private var dismiss

    init(asSheet: Bool = false) {
        self.asSheet = asSheet
    }

    var body: some View {
        if asSheet {
            // 공통 시트 모양: 손잡이(위 16 · 아래 12) · 머리(닫기) · 좌우 24 (DESIGN_SPEC 13.2)
            VStack(spacing: 0) {
                SheetGrabber()
                SheetHeader(title: nil, onClose: {
                    LoginPrompt.shared.closeMethod = "x_button"
                    dismiss()
                }, setsCloseHint: false)
                content
                    .padding(.top, 20)
            }
            .background(Color.bgSurface)
            .sheetChrome(detents: [.large])
            .trackScreen("LoginView")
            .onAppear { LoginTracking.appear(asSheet: asSheet) }
        } else {
            content
                .canvasBackground()
                .trackScreen("LoginView")
                .onAppear { LoginTracking.appear(asSheet: asSheet) }
        }
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 0) {
            Spacer()

            BrandLogo(size: .lg)

            (Text("목표를 세우고,\n") + Text("나를").foregroundColor(Color.textBrand) + Text(" 알아가요"))
                .font(.pt(28, .bold))
                .foregroundStyle(Color.ink)
                .lineSpacing(4)
                .padding(.top, 40)

            Text(asSheet
                 ? "지금은 둘러보는 중이에요.\n로그인하면 만든 목표와 Task가 저장되고, 웹과 앱 어디서든 이어서 쓸 수 있어요."
                 : "AI와 대화하며 목표를 설계하고\n매일의 실천을 기록해요.")
                .font(.pt(15))
                .foregroundStyle(Color.ink3)
                .lineSpacing(4)
                .padding(.top, 12)

            if let errorMessage {
                Text(errorMessage)
                    .font(.pt(14, .medium))
                    .foregroundStyle(Color.danger)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.dangerSoft, in: RoundedRectangle(cornerRadius: Radius.xl))
                    .padding(.top, 32)
            }

            Spacer()

            VStack(spacing: 12) {
                Button {
                    LoginTracking.click(provider: "kakao", asSheet: asSheet)
                    Task { await handleKakaoLogin() }
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "message.fill").font(.system(size: 17))
                        Text("카카오로 시작하기").font(.pt(15, .semibold))
                    }
                    .foregroundStyle(Color(hex: "#191600"))
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color.kakao, in: RoundedRectangle(cornerRadius: Radius.xxl))
                }
                .buttonStyle(.plain)

                AppleLoginButton(isLoading: $isLoading, errorMessage: $errorMessage, asSheet: asSheet)
                    .frame(height: 56)

                Button {
                    if asSheet {
                        LoginPrompt.shared.closeMethod = "continue_browsing"
                        dismiss()
                    } else {
                        Analytics.track(AnalyticsEvent.loginGuestClick)
                        Task { await handleGuest() }
                    }
                } label: {
                    Text(asSheet ? "계속 둘러보기" : "로그인 없이 둘러보기")
                        .font(.pt(15, .semibold))
                        .foregroundStyle(Color.ink2)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                }
                .buttonStyle(.plain)
            }
            .disabled(isLoading)
            .overlay { if isLoading { ProgressView().tint(Color.ink3) } }

            Text("© 2026 나를(Naleul)")
                .font(.pt(12))
                .foregroundStyle(Color.ink4)
                .frame(maxWidth: .infinity)
                .padding(.top, 32)
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 16)
        .frame(maxWidth: 440)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: 둘러보기

    private func handleGuest() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            try await AuthService.shared.startGuest()
            Analytics.track(AnalyticsEvent.guestSessionStart, ["is_new_guest": true])
        } catch {
            errorMessage = "지금은 둘러보기를 시작할 수 없어요. 잠시 후 다시 시도해 주세요."
            Analytics.track(AnalyticsEvent.guestStartFail, [
                "error_status": (error as? APIError)?.httpStatus, "fail_reason": LoginTracking.errorCode(error),
            ])
        }
    }

    // MARK: 카카오 로그인

    private func handleKakaoLogin() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        let wasGuest = UserStore.shared.isGuest
        do {
            let token = try await loginWithKakao()
            _ = try await AuthService.shared.kakaoLogin(accessToken: token.accessToken)
            LoginTracking.complete(provider: "kakao", asSheet: asSheet, wasGuest: wasGuest)
        } catch {
            // ⚠ 카카오 창을 닫기만 해도 여기로 와서 실패 문구가 떠요 (login.cancel 로 따로 세요)
            if LoginTracking.isKakaoCancel(error) {
                LoginTracking.cancel(provider: "kakao", asSheet: asSheet)
            } else {
                LoginTracking.fail(provider: "kakao", asSheet: asSheet, code: LoginTracking.errorCode(error))
            }
            errorMessage = "로그인에 실패했어요. 잠시 후 다시 시도해 주세요."
        }
    }

    /// 카카오 계정 로그인 (예전 앱과 같은 방식).
    /// 카카오톡 앱으로 로그인(loginWithKakaoTalk)은 카카오톡에서 우리 앱으로 "돌아오는" URL 스킴이
    /// Info.plist 에 제대로 등록돼 있어야 해요. 등록이 틀리면 카카오톡에서 돌아오지 못해 로딩이 끝나지 않아요.
    /// 계정 로그인은 앱 안의 웹 로그인 창(ASWebAuthenticationSession)이라 그런 문제가 없어요.
    @MainActor
    private func loginWithKakao() async throws -> OAuthToken {
        try await withCheckedThrowingContinuation { continuation in
            UserApi.shared.loginWithKakaoAccount { token, error in
                if let error { continuation.resume(throwing: error) }
                else if let token { continuation.resume(returning: token) }
                else { continuation.resume(throwing: APIError.unknown(0)) }
            }
        }
    }
}

// MARK: - 애플 로그인 버튼

private struct AppleLoginButton: View {
    @Binding var isLoading: Bool
    @Binding var errorMessage: String?
    var asSheet: Bool = false
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        SignInWithAppleButton(.signIn) { request in
            LoginTracking.click(provider: "apple", asSheet: asSheet)
            request.requestedScopes = [.fullName, .email]
        } onCompletion: { result in
            switch result {
            case .success(let auth):
                guard let credential = auth.credential as? ASAuthorizationAppleIDCredential,
                      let tokenData = credential.identityToken,
                      let identityToken = String(data: tokenData, encoding: .utf8) else {
                    errorMessage = "애플 로그인 정보를 받지 못했어요. 다시 시도해 주세요."
                    LoginTracking.fail(provider: "apple", asSheet: asSheet, code: "apple_token_missing")
                    return
                }
                // 이름은 처음 로그인할 때만 와요
                let fullName: String? = credential.fullName.flatMap {
                    let name = PersonNameComponentsFormatter().string(from: $0)
                    return name.isEmpty ? nil : name
                }
                Task { @MainActor in
                    isLoading = true
                    defer { isLoading = false }
                    let wasGuest = UserStore.shared.isGuest
                    do {
                        _ = try await AuthService.shared.appleLogin(identityToken: identityToken, fullName: fullName)
                        LoginTracking.complete(provider: "apple", asSheet: asSheet, wasGuest: wasGuest)
                    } catch {
                        errorMessage = "로그인에 실패했어요. 잠시 후 다시 시도해 주세요."
                        LoginTracking.fail(provider: "apple", asSheet: asSheet, code: LoginTracking.errorCode(error))
                    }
                }
            case .failure(let error):
                if let e = error as? ASAuthorizationError, e.code == .canceled {
                    LoginTracking.cancel(provider: "apple", asSheet: asSheet)
                    return
                }
                errorMessage = "애플 로그인에 실패했어요."
                LoginTracking.fail(provider: "apple", asSheet: asSheet, code: "apple_auth")
            }
        }
        .signInWithAppleButtonStyle(colorScheme == .dark ? .white : .black)
        .clipShape(RoundedRectangle(cornerRadius: Radius.xxl))
    }
}

// MARK: - 이벤트 (Tracking Plan · 로그인)

@MainActor
private enum LoginTracking {
    static func surface(_ asSheet: Bool) -> String { asSheet ? "sheet" : "screen" }

    /// 전체 화면이면 login.pageview, 시트면 login_modal.view
    static func appear(asSheet: Bool) {
        if asSheet {
            let prompt = LoginPrompt.shared
            Analytics.track(AnalyticsEvent.loginModalView, [
                "modal_reason": prompt.reason ?? "other",
                "blocked_api_path": prompt.blockedPath,
                "prompt_count_session": Analytics.nextLoginPromptCount(),
            ])
        } else {
            Analytics.track(AnalyticsEvent.loginPageview, [
                "entry_source": AuthService.shared.takeLoginEntry()?.rawValue ?? "app_launch",
            ])
        }
    }

    static func click(provider: String, asSheet: Bool) {
        Analytics.markLoginClick()
        let event: String
        switch (provider, asSheet) {
        case ("apple", true): event = AnalyticsEvent.loginModalAppleClick
        case ("apple", false): event = AnalyticsEvent.loginAppleClick
        case (_, true): event = AnalyticsEvent.loginModalKakaoClick
        default: event = AnalyticsEvent.loginKakaoClick
        }
        Analytics.track(event, [
            "login_surface": surface(asSheet),
            "modal_reason": asSheet ? (LoginPrompt.shared.reason ?? "other") : nil,
        ])
    }

    static func complete(provider: String, asSheet: Bool, wasGuest: Bool) {
        Analytics.track(AnalyticsEvent.loginComplete, [
            "login_provider": provider, "login_surface": surface(asSheet),
            "was_guest": wasGuest, "seconds_since_click": Analytics.secondsSinceLoginClick(),
        ])
    }

    static func fail(provider: String, asSheet: Bool, code: String) {
        Analytics.track(AnalyticsEvent.loginFail, [
            "login_provider": provider, "login_surface": surface(asSheet),
            "login_error_code": code, "seconds_since_click": Analytics.secondsSinceLoginClick(),
        ])
    }

    static func cancel(provider: String, asSheet: Bool) {
        Analytics.track(AnalyticsEvent.loginCancel, [
            "login_provider": provider, "login_surface": surface(asSheet),
            "seconds_since_click": Analytics.secondsSinceLoginClick(),
        ])
    }

    /// 카카오 로그인 창을 사용자가 닫았는지
    static func isKakaoCancel(_ error: Error) -> Bool {
        guard let e = error as? SdkError, e.isClientFailed else { return false }
        return e.getClientError().reason == .Cancelled
    }

    /// login_error_code · fail_reason — network · server · kakao_sdk · unknown
    static func errorCode(_ error: Error) -> String {
        if let e = error as? APIError {
            if case .network = e { return "network" }
            return "server"
        }
        if error is URLError { return "network" }
        if error is SdkError { return "kakao_sdk" }
        return "unknown"
    }
}

// MARK: - 로고 (웹 components/brand/Logo.tsx)

struct BrandLogo: View {
    enum Size { case md, lg }
    var size: Size = .md

    var body: some View {
        // Figma 워드마크 (Assets.xcassets/Logo/Wordmark · brand/logo/base 색)
        Wordmark(height: size == .lg ? 40 : 28)
    }
}

#Preview("로그인 시트") {
    LoginView(asSheet: true)
}

#Preview {
    LoginView()
        .environment(UserStore.shared)
}
