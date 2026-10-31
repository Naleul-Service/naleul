// NaleulApplication.kt
//
// iOS AppDelegate.didFinishLaunching 에 해당해요. 앱 프로세스가 시작될 때 한 번 불려요.
// 카카오 SDK · 알림 채널 · 화면 모드를 준비해요. (Firebase 는 google-services 플러그인이 자동으로 초기화해요)

package com.naleul.naleul

import android.app.Application
import com.kakao.sdk.common.KakaoSdk
import com.naleul.naleul.core.util.AppContext
import com.naleul.naleul.designsystem.ThemeStore
import com.naleul.naleul.feature.notifications.NotificationChannels

class NaleulApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.init(this)
        ThemeStore.load()
        // 카카오 앱 키는 local.properties 의 naleul.kakaoNativeAppKey 에서 BuildConfig 로 들어와요
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        // 분석 (Amplitude). 키는 local.properties 의 naleul.amplitudeApiKey
        com.naleul.naleul.core.analytics.AnalyticsService.start()
        NotificationChannels.ensure(this)
        // 광고 (AdMob) — 초기화가 무거워서 메인 스레드 밖에서
        Thread { com.google.android.gms.ads.MobileAds.initialize(this) }.start()
    }
}
