// feature/settings/SettingsScreen.kt
//
// iOS Features/Settings/SettingsScreen.swift
// 웹 원본: app/(main)/settings/page.tsx (설정 탭 루트)
//          features/account/ui/LogoutButton.tsx
//
// 탭 루트라 윗줄(NavBar)이 없어요. 하위 화면은 AppRouter.push(AppRoute.Settings(...)) 로 열고,
// 실제 화면은 SettingsRouteView 가 골라요.
// 웹의 "운영 대시보드(DEV)" 버튼은 앱에 옮기지 않았어요.

package com.naleul.naleul.feature.settings

import com.naleul.naleul.feature.goal.GD
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.naleul.naleul.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 설정 탭 첫 화면 (iOS SettingsScreen) */
@Composable
fun SettingsScreen() {
    val user = UserStore.user
    val n = user?.userName ?: ""
    val displayName = if (n.isEmpty()) "나를 사용자" else n

    // 멤버십 결제 · 닉네임 변경 뒤(.account) 서버의 최신 이름 · 플랜으로 맞춰요
    ReloadOn(listOf(DataKey.account)) { refreshUser() }

    SettingsContent(
        name = displayName,
        role = user?.userRole ?: UserRole.free,
        usageSample = null,
    )
}

/** 서버의 최신 닉네임 · 플랜으로 UserStore 를 맞춰요 (실패하면 그대로 둬요) */
private suspend fun refreshUser() {
    val userId = UserStore.user?.userId ?: return
    // 둘러보기는 서버 사용자 정보가 없어서 건너뛰어요
    if (UserStore.isGuest) return
    val info = try {
        UserAPI.fetchUser(userId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return
    }
    UserStore.updateUserName(info.nickname)
    UserStore.updateAdminViewPlan(info.adminViewPlan)
    UserStore.updateUserRole(UserRole.fromRaw(info.userRole) ?: UserRole.free)
}

/**
 * 설정 화면 내용 (미리보기에서 사용자 · 사용량을 직접 넣을 수 있게 따로 뒀어요)
 *
 * 모바일 배치 (휴대폰 기본 설정 앱처럼 묶음 목록)
 *  ┌ 설정 (큰 제목)                              ┐
 *  │ [아바타] 유림  PRO                    ›     │  ← 누르면 닉네임 수정
 *  │ (무료면) Pro로 더 많이 · 업그레이드 배너       │
 *  │ 오늘 사용량                                  │
 *  │ 일정   : 기본 생활 패턴 · 외부 캘린더 가져오기 │
 *  │ 앱     : 알림 · 화면 모드 · 멤버십             │
 *  │ 정보   : 약관 · 앱 버전                       │
 *  │ 계정   : 로그아웃 · 탈퇴하기                   │
 *  └─────────────────────────────────────────────┘
 */
@Composable
fun SettingsContent(
    name: String,
    role: UserRole,
    /** 미리보기용 사용량 (null 이면 서버에서 불러와요) */
    usageSample: AiUsage?,
) {
    var logoutOpen by remember { mutableStateOf(false) }
    val preview = androidx.compose.ui.platform.LocalInspectionMode.current
    // 설정 탭이 보일 때마다 (다른 탭에 갔다 오면 다시 그려져요)
    LaunchedEffect(Unit) {
        ST.track("settings.pageview", mapOf("is_premium" to role.isPaid, "theme_pref" to ThemeStore.pref.name), preview)
    }
    // iOS 는 Bundle 의 버전 · 빌드 번호. Android 는 build.gradle 의 versionName · versionCode
    val appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    Column(
        Modifier
            .fillMaxSize()
            .canvasBackground()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = PageGutter, end = PageGutter, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text(
            "설정",
            style = NType.h2,
            color = NC.ink,
            modifier = Modifier.padding(horizontal = 4.dp).padding(top = 4.dp),
        )

        if (role == UserRole.guest) {
            GuestBanner(surface = "settings")
        } else {
            SettingsProfileSummaryCard(name = name, role = role)
        }

        // 운영진이 Free 화면으로 볼 때도 실제 Free 사용자처럼 보여요
        if (role == UserRole.free || (role == UserRole.admin && !role.isPaid)) {
            SettingsUpgradeBanner()
        }

        // 하루 횟수가 정해진 기능 (AI 목표 만들기 · AI 계획 초안 · Task 추가)
        SettingsUsageCard(sample = usageSample)

        SettingsGroup("일정") {
            SettingsLinkRow(
                route = SettingsRoute.LifePattern, icon = "clock.fill", tint = Color(GoalPalette.blue.lightAccent),
                title = "기본 생활 패턴", subtitle = "수면 · 식사 · 이동처럼 매주 반복되는 시간",
            )
            SettingsDivider()
            // 외부 캘린더 가져오기 (휴대폰 기본 캘린더 · 구글 캘린더, 웹 · iOS 와 같은 이름)
            SettingsLinkRow(
                route = SettingsRoute.CalendarImport, icon = "calendar.badge.plus", tint = Color(GoalPalette.coral.lightAccent),
                title = "외부 캘린더 가져오기", subtitle = "일정을 골라 Task로 가져와요",
            )
            SettingsDivider()
            SettingsAutoCarryRow()
        }

        SettingsGroup("앱") {
            SettingsLinkRow(
                route = SettingsRoute.Notifications, icon = "bell.fill", tint = Color(GoalPalette.orange.lightAccent),
                title = "알림", subtitle = null,
            )
            SettingsDivider()
            SettingsThemeRow()
            SettingsDivider()
            SettingsLinkRow(
                route = SettingsRoute.Membership, icon = "crown.fill", tint = Color(GoalPalette.purple.lightAccent),
                title = "멤버십", subtitle = null,
                value = if (role.isPaid) "PRO" else "무료",
            )
        }

        // 운영진만 — 결제와 상관없이 Free · Pro 화면과 횟수 제한을 바꿔 확인해요 (서버에도 적용)
        if (role == UserRole.admin) {
            SettingsGroup("운영진") {
                SettingsAdminPlanRow()
            }
        }

        SettingsGroup("정보") {
            SettingsLinkRow(
                route = SettingsRoute.Terms, icon = "doc.text.fill", tint = Color(GoalPalette.slate.lightAccent),
                title = "약관 동의", subtitle = null,
            )
            SettingsDivider()
            SettingsValueRow(icon = "info.circle.fill", tint = Color(GoalPalette.sage.lightAccent), title = "앱 버전", value = appVersion)
        }

        SettingsGroup("계정") {
            SettingsRowLabel(
                icon = "rectangle.portrait.and.arrow.right", tint = Color(GoalPalette.navy.lightAccent),
                title = if (role == UserRole.guest) "둘러보기 끝내기" else "로그아웃", subtitle = null, chevron = false,
                modifier = Modifier.settingsRowButtonStyle {
                    AnalyticsService.track(AnalyticsEvent.settingsLogoutClick, "is_guest" to (role == UserRole.guest))
                    logoutOpen = true
                },
            )
        }

        if (role != UserRole.guest) {
            Text(
                "탈퇴하기",
                style = pt(13, PTWeight.medium).copy(textDecoration = TextDecoration.Underline),
                color = NC.ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) {
                        ST.track("settings.menu_click", mapOf("destination" to "withdraw", "is_premium" to role.isPaid))
                        AppRouter.push(AppRoute.Settings(SettingsRoute.Withdraw))
                    }
                    .padding(vertical = 8.dp),
            )
        }
    }

    ConfirmDialog(
        isPresented = logoutOpen,
        onDismiss = { logoutOpen = false },
        title = if (role == UserRole.guest) "둘러보기를 끝내고 로그인 화면으로 갈까요?" else "정말 로그아웃 하시겠어요?",
        confirmLabel = if (role == UserRole.guest) "끝내기" else "로그아웃",
        tone = ConfirmTone.danger,
        onCancel = { AnalyticsService.track(AnalyticsEvent.settingsLogoutCancelClick, "is_guest" to (role == UserRole.guest)) },
    ) {
        AnalyticsService.track(AnalyticsEvent.settingsLogoutConfirmClick, "is_guest" to (role == UserRole.guest))
        AuthService.logout(AuthService.LogoutReason.logout)
    }
}

// MARK: - 묶음 · 줄

/** 회색 작은 제목 + 흰 카드 묶음 (휴대폰 설정 앱의 묶음 목록) */
@Composable
fun SettingsGroup(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = pt(13, PTWeight.semibold), color = NC.ink3, modifier = Modifier.padding(start = 16.dp))
        val shape = RoundedCornerShape(16.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(NC.surface, shape)
                .border(1.dp, NC.borderSubtle, shape),
            content = content,
        )
    }
}

/** 아이콘 뒤부터 시작하는 구분선 */
@Composable
fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 60.dp).height(0.5.dp).background(NC.line))
}

/** 줄 모양: [색 아이콘] 제목 (부제) … 값 › */
@Composable
fun SettingsRowLabel(
    icon: String,
    tint: Color,
    title: String,
    subtitle: String?,
    value: String? = null,
    chevron: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (subtitle == null) 52.dp else 60.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(30.dp).background(tint, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { SfImage(icon, size = 15.dp, tint = Color.White) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = pt(15, PTWeight.medium), color = NC.ink)
            if (subtitle != null) {
                Text(subtitle, style = pt(12), color = NC.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) {
            Text(value, style = pt(14), color = NC.ink3, maxLines = 1)
        }
        if (chevron) {
            SfImage("chevron.right", size = 14.dp, tint = NC.ink4)
        }
    }
}

/** 하위 화면으로 가는 줄 */
@Composable
fun SettingsLinkRow(
    route: SettingsRoute,
    icon: String,
    tint: Color,
    title: String,
    subtitle: String?,
    value: String? = null,
) {
    SettingsRowLabel(
        icon = icon, tint = tint, title = title, subtitle = subtitle, value = value,
        modifier = Modifier.settingsRowButtonStyle {
            val destination = when (route) {
                SettingsRoute.LifePattern -> "life_pattern"
                SettingsRoute.CalendarImport -> "calendar_import"
                SettingsRoute.Notifications -> "notifications"
                SettingsRoute.Membership -> "membership"
                SettingsRoute.Terms -> "terms"
                SettingsRoute.Withdraw -> "withdraw"
                else -> null
            }
            if (destination != null) ST.track("settings.menu_click", mapOf("destination" to destination, "is_premium" to ST.isPremium))
            if (route == SettingsRoute.Membership) ST.pendingMembershipEntry = "settings_row"
            if (route == SettingsRoute.LifePattern) ST.pendingLifePatternEntry = "settings"
            AppRouter.push(AppRoute.Settings(route))
        },
    )
}

/** 값만 보여주는 줄 (앱 버전) */
@Composable
fun SettingsValueRow(icon: String, tint: Color, title: String, value: String) {
    SettingsRowLabel(icon = icon, tint = tint, title = title, subtitle = null, value = value, chevron = false)
}

/** 화면 모드 줄 — 오른쪽 메뉴로 시스템 / 라이트 / 다크 (iOS Menu { Picker }) */
@Composable
fun SettingsThemeRow() {
    val current = ThemeStore.pref
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .settingsRowButtonStyle { open = true }
                .defaultMinSize(minHeight = 52.dp)
                .padding(horizontal = 16.dp)
                .semantics { contentDescription = "화면 모드, ${current.label}" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(30.dp).background(Color(GoalPalette.navy.lightFg), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) { SfImage("circle.lefthalf.filled", size = 15.dp, tint = Color.White) }
            Text("화면 모드", style = pt(15, PTWeight.medium), color = NC.ink, modifier = Modifier.weight(1f))
            Text(current.label, style = pt(14), color = NC.ink3)
            SfImage("chevron.up.chevron.down", size = 12.dp, tint = NC.ink4)
        }
        NDropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = NC.surface,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            for (t in ThemePref.entries) {
                NDropdownMenuItem(
                    text = { Text(t.label, style = pt(15), color = NC.ink) },
                    leadingIcon = { SfImage(t.icon, size = 16.dp, tint = NC.ink2) },
                    trailingIcon = if (t == current) ({ SfImage("checkmark", size = 14.dp, tint = NC.textBrand) }) else null,
                    onClick = {
                        open = false
                        if (t != current) ST.track("settings.theme_change", mapOf("theme_pref" to t.name))
                        ThemeStore.set(t)
                    },
                )
            }
        }
    }
}

/** 백엔드 AdminViewPlanResponse */
@kotlinx.serialization.Serializable
data class AdminViewPlanResponse(val plan: String? = null, val premium: Boolean = false)

/**
 * 운영진 플랜 바꿔 보기 (2026-10-24) — PATCH /v1/admin/view-plan
 * 서버의 isCurrentlyPremium 이 이 값을 따르므로 하루 횟수 제한 · Pro 전용 잠금도 실제 Free / Pro 사용자와 같아져요.
 */
@Composable
fun SettingsAdminPlanRow() {
    val current = if (UserStore.adminViewPlan == "FREE") "FREE" else "PRO"
    var open by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun change(next: String) {
        val from = current
        if (next == from || saving) return
        saving = true
        scope.launch {
            try {
                val r = API.patch<AdminViewPlanResponse>("/v1/admin/view-plan", json("plan" to next))
                UserStore.updateAdminViewPlan(r?.plan ?: next)
                ST.track("settings.admin_view_plan_change", mapOf("from_plan" to from, "to_plan" to next, "result" to "success"))
                Toast.success(if (next == "PRO") "이제 Pro 화면으로 보여요." else "이제 Free 화면으로 보여요. 횟수 제한도 Free 와 같아요.")
                Invalidation.invalidate(DataKey.account, DataKey.usage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ST.track("settings.admin_view_plan_change", mapOf("from_plan" to from, "to_plan" to next, "result" to "fail"))
                Toast.error("바꾸지 못했어요. 다시 시도해 주세요.")
            } finally {
                saving = false
            }
        }
    }

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .settingsRowButtonStyle { if (!saving) open = true }
                .defaultMinSize(minHeight = 60.dp)
                .padding(horizontal = 16.dp)
                .semantics { contentDescription = "플랜 바꿔 보기, ${if (current == "FREE") "Free" else "Pro"}" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(30.dp).background(Color(GoalPalette.amber.lightFg), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) { SfImage("eye", size = 15.dp, tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("플랜 바꿔 보기", style = pt(15, PTWeight.medium), color = NC.ink)
                Text("결제와 상관없이 Free · Pro 화면과 횟수 제한을 확인해요", style = pt(12), color = NC.ink3)
            }
            Text(if (saving) "바꾸는 중…" else if (current == "FREE") "Free" else "Pro", style = pt(14), color = NC.ink3)
            SfImage("chevron.up.chevron.down", size = 12.dp, tint = NC.ink4)
        }
        NDropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = NC.surface,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            for ((value, label) in listOf("FREE" to "Free", "PRO" to "Pro")) {
                NDropdownMenuItem(
                    text = { Text(label, style = pt(15), color = NC.ink) },
                    trailingIcon = if (value == current) ({ SfImage("checkmark", size = 14.dp, tint = NC.textBrand) }) else null,
                    onClick = {
                        open = false
                        change(value)
                    },
                )
            }
        }
    }
}

/** 무료 사용자에게만: Pro 업그레이드 배너 */
@Composable
fun SettingsUpgradeBanner() {
    val shape = RoundedCornerShape(Radius.card)
    val gradient = NC.aiGradient()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(gradient, shape)
            .clickable(role = Role.Button) {
                ST.track("settings.menu_click", mapOf("destination" to "membership_banner", "is_premium" to ST.isPremium))
                ST.pendingMembershipEntry = "settings_banner"
                AppRouter.push(AppRoute.Settings(SettingsRoute.Membership))
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(42.dp).background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { SfImage("crown.fill", size = 20.dp, tint = Color.White) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Pro로 더 많이", style = pt(16, PTWeight.bold), color = Color.White)
            Text(
                "AI 목표 · 계획 초안을 넉넉하게, 광고 없이",
                style = pt(12),
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SfImage("chevron.right", size = 14.dp, tint = Color.White.copy(alpha = 0.8f))
    }
}

/**
 * 설정 목록 줄을 눌렀을 때 살짝 회색 (웹 hover:bg-subtle/60)
 * iOS SettingsRowButtonStyle (ButtonStyle) → Compose 에서는 Modifier 로 만들었어요.
 */
@Composable
fun Modifier.settingsRowButtonStyle(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressedColor = NC.subtle.copy(alpha = 0.6f)
    return this
        .background(if (pressed) pressedColor else Color.Transparent)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
}

// MARK: - 미리보기

@Preview(showBackground = true, heightDp = 1100, name = "무료")
@Composable
private fun SettingsContentFreePreview() {
    NaleulTheme(dark = false) {
        SettingsContent(
            name = "유림",
            role = UserRole.free,
            usageSample = AiUsage(
                pro = false, resetsAt = "2026-10-10T00:00:00",
                items = listOf(
                    UsageItem(key = "GOAL_SESSION", label = "AI 목표 만들기", description = "목표를 대화로 설계해요", used = 2, limit = 3, remaining = 1),
                ),
                session = null,
            ),
        )
    }
}

@Preview(showBackground = true, heightDp = 1100, name = "Pro")
@Composable
private fun SettingsContentProPreview() {
    NaleulTheme(dark = false) {
        SettingsContent(
            name = "유림",
            role = UserRole.pro,
            usageSample = AiUsage(
                pro = true, resetsAt = "2026-10-10T00:00:00",
                items = listOf(
                    UsageItem(key = "GOAL_SESSION", label = "AI 목표 만들기", description = "목표를 대화로 설계해요", used = 2, limit = 10, remaining = 8),
                    UsageItem(key = "TASK_PER_DAY", label = "Task 추가", description = "하루에 추가할 수 있는 Task", used = 4, limit = null, remaining = null),
                ),
                session = null,
            ),
        )
    }
}

/** 못 한 일 자동 이월 (2026-10, 웹 설정 AutoCarryToggle · iOS SettingsAutoCarryRow) */
@Composable
fun SettingsAutoCarryRow() {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (!AutoCarrySetting.loaded) AutoCarrySetting.load() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SettingsRowLabel(
            icon = "arrow.right", tint = Color(GoalPalette.green.lightAccent),
            title = "못 한 일 자동 이월", subtitle = AutoCarrySetting.help, chevron = false,
            modifier = Modifier.weight(1f),
        )
        NSwitch(
            isOn = AutoCarrySetting.enabled,
            onIsOnChange = { v -> scope.launch { AutoCarrySetting.set(v, surface = "settings") } },
            label = "못 한 일 자동 이월",
            disabled = AutoCarrySetting.saving,
            modifier = Modifier.padding(end = 16.dp),
        )
    }
}
