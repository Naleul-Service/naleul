// DesignSystem/Components/AdBannerSlot.swift
//
// 배너 광고 (Google AdMob) — Free · 둘러보기 사용자에게만. Pro 는 광고 없음.
//
// 광고 ID
//  - 앱 ID: Info.plist 의 GADApplicationIdentifier (지금은 구글 테스트 앱 ID)
//  - 배너 단위 ID: 아래 AdUnit.productionBanner — AdMob 에서 만든 실제 ID 를 넣으면 Release 에서 실제 광고가 나와요
//  - DEBUG 빌드는 항상 구글 테스트 배너 ("Test Ad" 표시) → 개발 중 실수로 내 광고를 누르는 정책 위반을 막아요
//  - Release 인데 실제 ID 가 없으면 아무것도 안 보여요
// 맞춤 광고 추적 동의(ATT) 팝업은 띄우지 않아요 → iOS 광고 식별자 없이 나가는 광고예요
//
// 이벤트 (노션 Tracking Plan 「공통」)
//  - ad.view  : 광고 결과가 나왔을 때 화면마다 1회 (ad_state = rendered 받음 / unfilled 보여줄 광고 없음 / failed 요청 실패)
//  - ad.click : 배너를 눌렀을 때

import SwiftUI
#if canImport(GoogleMobileAds)
import GoogleMobileAds
#endif

enum AdUnit {
    /// 구글 공식 테스트 배너 ID
    static let testBanner = "ca-app-pub-3940256099942544/2934735716"
    /// AdMob 에서 만든 iOS 배너 광고 단위 ID ("ca-app-pub-…/…"). 비어 있으면 Release 에서 광고를 안 보여줘요
    static let productionBanner = ""

    static var banner: String? {
        #if DEBUG
        return testBanner
        #else
        return productionBanner.isEmpty ? nil : productionBanner
        #endif
    }
}

enum Ads {
    /// 앱 시작 때 한 번 (AppDelegate)
    static func start() {
        #if canImport(GoogleMobileAds)
        MobileAds.shared.start(completionHandler: nil)
        #endif
    }
}

struct AdBannerSlot: View {
    let role: UserRole?
    /// 운영진이 보고 있는 플랜 — role 이 그대로여도 플랜을 바꾸면 다시 그려지게 입력으로 받아요
    var adminViewPlan: String? = nil
    var placement: String = "home"

    @State private var loaded = false

    private var showsAds: Bool { !(role?.isPaid ?? false) }

    var body: some View {
        #if canImport(GoogleMobileAds)
        if showsAds, let unit = AdUnit.banner {
            AdMobBanner(unitID: unit, placement: placement, loaded: $loaded)
                .frame(width: 320, height: 50)
                .frame(maxWidth: .infinity)
                // 광고가 안 오면 자리를 접어요 (빈 칸이 남지 않게)
                .frame(height: loaded ? 50 : 0)
                .clipped()
                .accessibilityLabel("광고")
        }
        #else
        EmptyView()
        #endif
    }
}

#if canImport(GoogleMobileAds)
private struct AdMobBanner: UIViewRepresentable {
    let unitID: String
    let placement: String
    @Binding var loaded: Bool

    func makeCoordinator() -> Coordinator { Coordinator(placement: placement, loaded: $loaded) }

    func makeUIView(context: Context) -> BannerView {
        let view = BannerView(adSize: AdSizeBanner)
        view.adUnitID = unitID
        view.delegate = context.coordinator
        view.rootViewController = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first?.rootViewController
        view.load(Request())
        return view
    }

    func updateUIView(_ uiView: BannerView, context: Context) {}

    final class Coordinator: NSObject, BannerViewDelegate {
        let placement: String
        @Binding var loaded: Bool
        private var viewSent = false

        init(placement: String, loaded: Binding<Bool>) {
            self.placement = placement
            _loaded = loaded
        }

        private func sendView(_ state: String) {
            guard !viewSent else { return }
            viewSent = true
            // 광고 콜백은 메인 스레드에서 와요 (Analytics 는 @MainActor)
            let placement = placement
            MainActor.assumeIsolated {
                Analytics.track(AnalyticsEvent.adView, ["ad_placement": placement, "ad_state": state])
            }
        }

        func bannerViewDidReceiveAd(_ bannerView: BannerView) {
            loaded = true
            sendView("rendered")
        }

        func bannerView(_ bannerView: BannerView, didFailToReceiveAdWithError error: Error) {
            loaded = false
            // GADErrorNoFill = 1 : 보여줄 광고가 없음
            sendView((error as NSError).code == 1 ? "unfilled" : "failed")
        }

        func bannerViewDidRecordClick(_ bannerView: BannerView) {
            let placement = placement
            MainActor.assumeIsolated {
                Analytics.track(AnalyticsEvent.adClick, ["ad_placement": placement])
            }
        }
    }
}
#endif

#Preview {
    VStack(spacing: 12) {
        AdBannerSlot(role: .free)
        AdBannerSlot(role: .pro)
    }
    .padding()
}
