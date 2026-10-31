// Features/Auth/GuestBanner.swift
//
// 둘러보기(로그인 전) 중임을 알려주는 띠. 누르면 로그인 시트가 떠요.
// 홈 맨 위 · 설정 맨 위에 보여요 (웹 GuestBanner 와 같은 문구).

import SwiftUI

struct GuestBanner: View {
    /// 띠가 있는 화면 — home · settings (guest_banner.login_click 의 surface)
    var surface: String = "home"

    var body: some View {
        Button {
            Analytics.track(AnalyticsEvent.guestBannerLoginClick, ["surface": surface])
            LoginPrompt.shared.show(reason: "guest_banner")
        } label: {
            HStack(spacing: 10) {
                NSymbol("eye", size: 15, weight: .semibold)
                VStack(alignment: .leading, spacing: 2) {
                    Text("둘러보는 중이에요")
                        .font(.pt(14, .bold))
                    Text("로그인하면 내 목표와 계획이 저장돼요")
                        .font(.pt(12))
                        .opacity(0.8)
                }
                Spacer(minLength: 8)
                Text("로그인")
                    .font(.pt(13, .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(Color.brand, in: Capsule())
            }
            .foregroundStyle(Color.textBrand)
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Color.brandSoft, in: RoundedRectangle(cornerRadius: Radius.xl))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("둘러보는 중이에요. 로그인하면 저장돼요. 로그인하기")
    }
}

#Preview {
    GuestBanner().padding()
}
