// feature/settings/SettingsRouteView.kt
//
// iOS Features/Settings/SettingsRouteView.swift
// 웹 /settings/… 하위 주소 → 앱 화면. 앱 골격(MainTab)이 AppRoute.Settings(route) 를 그릴 때 불러요.
//   /settings/life-pattern     → LifePatternScreen
//   /settings/notifications    → SettingsNotificationScreen
//   /settings/calendar-import  → CalendarImportScreen (feature.calendarimport)
//   /settings/membership       → MembershipScreen (Android 결제는 준비 중 — 플랜 안내만)
//   /settings/terms            → TermsListScreen
//   /settings/terms/[type]     → TermsDetailScreen
//   /settings/withdraw         → SettingsWithdrawScreen

package com.naleul.naleul.feature.settings

import com.naleul.naleul.app.*
import com.naleul.naleul.core.analytics.*
import com.naleul.naleul.core.network.*
import com.naleul.naleul.core.state.*
import com.naleul.naleul.core.util.*
import com.naleul.naleul.designsystem.*
import com.naleul.naleul.model.*
import com.naleul.naleul.feature.auth.*
import com.naleul.naleul.feature.calendar.*
import com.naleul.naleul.feature.calendarimport.*
import com.naleul.naleul.feature.goal.*
import com.naleul.naleul.feature.goalcreation.*
import com.naleul.naleul.feature.habit.*
import com.naleul.naleul.feature.home.*
import com.naleul.naleul.feature.notifications.*
import com.naleul.naleul.feature.pattern.*
import com.naleul.naleul.feature.record.*
import com.naleul.naleul.feature.settings.*
import com.naleul.naleul.feature.taskadd.*
import com.naleul.naleul.feature.work.*

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SettingsRouteView(route: SettingsRoute) {
    when (route) {
        SettingsRoute.LifePattern -> LifePatternScreen()
        SettingsRoute.Notifications -> SettingsNotificationScreen()
        SettingsRoute.CalendarImport -> CalendarImportScreen()
        SettingsRoute.Membership -> MembershipScreen()
        SettingsRoute.Terms -> TermsListScreen()
        is SettingsRoute.TermsDetail -> TermsDetailScreen(type = route.type)
        SettingsRoute.Withdraw -> SettingsWithdrawScreen()
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SettingsRouteViewPreview() {
    NaleulTheme(dark = false) {
        SettingsRouteView(route = SettingsRoute.TermsDetail("privacy"))
    }
}
