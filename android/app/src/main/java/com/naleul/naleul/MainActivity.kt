// MainActivity.kt
//
// 앱의 유일한 화면(Activity). 모든 화면은 Compose 로 이 안에서 바뀌어요 (iOS WindowGroup { RootView() }).
// 알림을 눌러 앱이 열리면 NudgeCenter.handleIntent 가 알맞은 화면으로 보내요.

package com.naleul.naleul

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.naleul.naleul.app.RootScreen
import com.naleul.naleul.core.analytics.AnalyticsService
import com.naleul.naleul.core.network.TokenManager
import com.naleul.naleul.designsystem.NaleulTheme
import com.naleul.naleul.feature.notifications.NudgeCenter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 상태바 · 내비게이션바 뒤까지 화면을 그려요 (각 화면이 statusBarsPadding 등으로 여백을 줘요)
        enableEdgeToEdge()
        setContent {
            NaleulTheme {
                RootScreen()
            }
        }
        NudgeCenter.handleIntent(intent)
    }

    /**
     * 앱이 앞으로 올 때 — app.open. 알림을 눌러 열면 onNewIntent 가 onResume 보다 먼저 와서 open_source 가 정해져 있어요.
     * (권한 창 · 화면 회전으로 다시 불려도 이미 앞에 있으면 한 번만 보내요)
     */
    override fun onResume() {
        super.onResume()
        AnalyticsService.appDidBecomeActive(isLoggedIn = TokenManager.accessToken != null)
    }

    /** 앱이 뒤로 갈 때 — app.background (화면 회전은 빼요) */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) AnalyticsService.appDidEnterBackground()
    }

    /** 앱이 이미 켜져 있을 때 알림을 누르면 (launchMode=singleTop) 여기로 와요 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        NudgeCenter.handleIntent(intent)
    }
}
