// designsystem/AdBannerSlot.kt
//
// iOS DesignSystem/Components/AdBannerSlot.swift — 배너 광고 (Google AdMob). Free · 둘러보기 사용자에게만. Pro 는 광고 없음.
//
// 광고 ID
//  - 앱 ID: AndroidManifest 의 APPLICATION_ID ← local.properties naleul.admobAppId (비어 있으면 구글 테스트 앱 ID)
//  - 배너 단위 ID: local.properties naleul.admobBannerId → BuildConfig.ADMOB_BANNER_ID
//  - Debug 빌드는 항상 구글 테스트 배너 ("Test Ad" 표시) → 개발 중 실수로 내 광고를 누르는 정책 위반을 막아요
//  - Release 인데 실제 ID 가 없으면 아무것도 안 보여요
//
// 이벤트 (노션 Tracking Plan 「공통」, iOS 와 같음)
//  - ad.view  : 광고 결과가 나왔을 때 화면마다 1회 (ad_state = rendered 받음 / unfilled 보여줄 광고 없음 / failed 요청 실패)
//  - ad.click : 배너를 눌렀을 때

package com.naleul.naleul.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.naleul.naleul.BuildConfig
import com.naleul.naleul.core.analytics.AnalyticsEvent
import com.naleul.naleul.core.analytics.AnalyticsService
import com.naleul.naleul.core.state.UserRole

object AdUnit {
    /** 구글 공식 테스트 배너 ID */
    const val testBanner = "ca-app-pub-3940256099942544/9214589741"

    /** Debug: 테스트 배너 · Release: local.properties 의 실제 ID (없으면 null → 광고 안 보임) */
    val banner: String?
        get() = if (BuildConfig.DEBUG) testBanner else BuildConfig.ADMOB_BANNER_ID.ifEmpty { null }
}

@Composable
fun AdBannerSlot(role: UserRole?, modifier: Modifier = Modifier, placement: String = "home") {
    val showsAds = !(role?.isPaid ?: false)
    val unit = AdUnit.banner
    if (!showsAds || unit == null) return
    val context = LocalContext.current
    // 광고가 안 오면 자리를 접어요 (빈 칸이 남지 않게)
    var loaded by remember { mutableStateOf(false) }
    val adView = remember(unit) {
        var viewSent = false
        fun sendView(state: String) {
            if (viewSent) return
            viewSent = true
            AnalyticsService.track(AnalyticsEvent.adView, "ad_placement" to placement, "ad_state" to state)
        }
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = unit
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    loaded = true
                    sendView("rendered")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loaded = false
                    sendView(if (error.code == AdRequest.ERROR_CODE_NO_FILL) "unfilled" else "failed")
                }

                override fun onAdClicked() {
                    AnalyticsService.track(AnalyticsEvent.adClick, "ad_placement" to placement)
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) { onDispose { adView.destroy() } }

    Box(
        modifier
            .fillMaxWidth()
            .height(if (loaded) 50.dp else 0.dp)
            .semantics { contentDescription = "광고" },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(factory = { adView }, modifier = Modifier.width(320.dp).height(50.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun AdBannerSlotPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.padding(16.dp)) {
            AdBannerSlot(role = UserRole.free)
            AdBannerSlot(role = UserRole.pro)
        }
    }
}
