// feature/auth/GuestBanner.kt
//
// iOS Features/Auth/GuestBanner.swift
// 둘러보기(로그인 전) 중임을 알려주는 띠. 누르면 로그인 시트가 떠요.
// 홈 맨 위 · 설정 맨 위에 보여요 (웹 GuestBanner 와 같은 문구).

package com.naleul.naleul.feature.auth

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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** surface: 띠가 있는 화면 — home · settings (guest_banner.login_click) */
@Composable
fun GuestBanner(modifier: Modifier = Modifier, surface: String = "home") {
    val shape = RoundedCornerShape(Radius.xl)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NC.brandSoft, shape)
            .clickable(role = Role.Button) {
                AnalyticsService.track(AnalyticsEvent.guestBannerLoginClick, "surface" to surface)
                LoginPrompt.show("guest_banner")
            }
            .semantics { contentDescription = "둘러보는 중이에요. 로그인하면 저장돼요. 로그인하기" }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SfImage("eye", size = 16.dp, tint = NC.textBrand)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("둘러보는 중이에요", style = pt(14, PTWeight.bold), color = NC.textBrand)
            Text("로그인하면 내 목표와 계획이 저장돼요", style = pt(12), color = NC.textBrand.copy(alpha = 0.8f))
        }
        Box(
            Modifier
                .background(NC.brand, CircleShape)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text("로그인", style = pt(13, PTWeight.bold), color = Color.White)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GuestBannerPreview() {
    NaleulTheme(dark = false) {
        Box(Modifier.padding(16.dp)) { GuestBanner() }
    }
}
