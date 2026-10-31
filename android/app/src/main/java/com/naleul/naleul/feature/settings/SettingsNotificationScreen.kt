// feature/settings/SettingsNotificationScreen.kt
//
// iOS Features/Settings/SettingsNotificationScreen.swift
// 웹 원본: app/(main)/settings/notifications/page.tsx
//          features/notification-settings/ui/NotificationSettingsView.tsx
//          features/notification-settings/api.ts
//
// Task 푸시 알림 on/off (미션 알림 설정은 미션 기능 종료로 제외). 둘러보기(로그인 전)면 스위치 대신 자물쇠.
// 스위치는 먼저 바꿔서 바로 반응하고(낙관적 업데이트), 서버가 실패하면 원래 값으로 되돌려요.
// Android 에서는 "휴대폰 알림 설정 열기" 버튼을 하나 더 둬요 (앱 알림 자체가 꺼져 있으면 아무것도 안 울려서).

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

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// MARK: - 모델 · API

/** GET /v1/notification-settings 응답 (백엔드 NotificationSettingsResponse — 모두 boolean 이라 항상 와요) */
@Serializable
data class SettingsNotificationSettings(
    val missionAlertEnabled: Boolean = false,
    val missionCertificationEnabled: Boolean = false,
    val wishlistEnabled: Boolean = false,
    val missionProgressEnabled: Boolean = false,
    val missionEditEnabled: Boolean = false,
    val badgeEnabled: Boolean = false,
    val pokeEnabled: Boolean = false,
    val taskAlertEnabled: Boolean = false,
) {
    companion object {}
}

/**
 * 설정 값 하나를 읽고 바꾸는 방법 (iOS WritableKeyPath<SettingsNotificationSettings, Bool> 대신)
 * data class 는 val 이라 바꿀 때 copy() 한 새 값을 돌려줘요.
 */
class SettingsNotificationField(
    val get: (SettingsNotificationSettings) -> Boolean,
    val set: (SettingsNotificationSettings, Boolean) -> SettingsNotificationSettings,
) {
    companion object {
        val taskAlertEnabled = SettingsNotificationField({ it.taskAlertEnabled }, { s, v -> s.copy(taskAlertEnabled = v) })
    }
}

/** 미션 알림 종류 — 문구는 백엔드 NotificationCategory 와 같아요 (지금 화면에서는 안 써요) */
class SettingsNotificationCategory(
    /** 백엔드 NotificationCategory 이름 */
    val category: String,
    val field: SettingsNotificationField,
    val label: String,
    val description: String,
) {
    val id: String get() = category

    companion object {
        val all: List<SettingsNotificationCategory> = listOf(
            SettingsNotificationCategory(
                "MISSION_ALERT", SettingsNotificationField({ it.missionAlertEnabled }, { s, v -> s.copy(missionAlertEnabled = v) }),
                "미션 알림", "미션 신청 현황과 승인이 필요할 때 알려드려요",
            ),
            SettingsNotificationCategory(
                "MISSION_CERTIFICATION", SettingsNotificationField({ it.missionCertificationEnabled }, { s, v -> s.copy(missionCertificationEnabled = v) }),
                "미션 인증", "팀원들이 미션을 인증할 때마다 알려드려요",
            ),
            SettingsNotificationCategory(
                "WISHLIST", SettingsNotificationField({ it.wishlistEnabled }, { s, v -> s.copy(wishlistEnabled = v) }),
                "찜하기", "찜한 미션의 중요한 소식을 알려드려요",
            ),
            SettingsNotificationCategory(
                "MISSION_PROGRESS", SettingsNotificationField({ it.missionProgressEnabled }, { s, v -> s.copy(missionProgressEnabled = v) }),
                "미션 진행", "미션이 시작되거나 완료되면 알려드려요.",
            ),
            SettingsNotificationCategory(
                "MISSION_EDIT", SettingsNotificationField({ it.missionEditEnabled }, { s, v -> s.copy(missionEditEnabled = v) }),
                "미션 내용 수정", "미션 일정이나 내용이 변경되면 알려드려요",
            ),
            SettingsNotificationCategory(
                "BADGE", SettingsNotificationField({ it.badgeEnabled }, { s, v -> s.copy(badgeEnabled = v) }),
                "뱃지 획득", "새로운 뱃지를 획득하면 알려드려요.",
            ),
            SettingsNotificationCategory(
                "POKE", SettingsNotificationField({ it.pokeEnabled }, { s, v -> s.copy(pokeEnabled = v) }),
                "쪼기", "팀원이 미션 참여를 독려하면 알려드려요",
            ),
        )
    }
}

object SettingsNotificationAPI {
    private const val base = "/v1/notification-settings"

    suspend fun fetch(): SettingsNotificationSettings? = API.get<SettingsNotificationSettings>(base)

    /** Task 알림은 카테고리가 아니라 별도 엔드포인트(/task-alert)로 바꿔요 */
    suspend fun updateTaskAlert(enabled: Boolean): SettingsNotificationSettings? =
        API.patch<SettingsNotificationSettings>("$base/task-alert", json("enabled" to enabled))

    suspend fun updateCategory(category: String, enabled: Boolean): SettingsNotificationSettings? =
        API.patch<SettingsNotificationSettings>(base, json("category" to category, "enabled" to enabled))
}

// MARK: - 화면 상태

class SettingsNotificationViewModel(sample: SettingsNotificationSettings? = null) : ScreenModel() {
    var phase by mutableStateOf<LoadPhase<SettingsNotificationSettings>>(LoadPhase.Loading)
    private val isSample: Boolean = sample != null

    init {
        if (sample != null) phase = LoadPhase.Loaded(sample)
    }

    var lastErrorStatus: Int? = null
        private set
    private var pageviewSent = false

    suspend fun load() {
        if (isSample) return
        if (phase.value == null) phase = LoadPhase.Loading
        try {
            val s = SettingsNotificationAPI.fetch()
            phase = if (s != null) LoadPhase.Loaded(s) else LoadPhase.Failed("설정을 불러오지 못했어요.")
            if (s == null) fail(0)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            phase = LoadPhase.Failed("설정을 불러오지 못했어요.")
            fail(ST.status(e))
        }
        // 처음 불러온 직후 한 번 (실패해도)
        if (!pageviewSent) {
            pageviewSent = true
            ST.track("notification_settings.pageview", mapOf("is_premium" to ST.isPremium, "is_guest" to ST.isGuest))
        }
    }

    private fun fail(status: Int) {
        lastErrorStatus = status
        ST.track("notification_settings.load_fail", mapOf("error_status" to status))
    }

    fun trackRetry() = ST.track("notification_settings.retry_click", mapOf("error_status" to lastErrorStatus))

    /** 토글 하나 바꾸기. category 가 null 이면 Task 알림 */
    fun toggle(category: String?, field: SettingsNotificationField, enabled: Boolean) {
        val prev = phase.value ?: return
        phase = LoadPhase.Loaded(field.set(prev, enabled))   // 먼저 바꿔서 바로 반응
        if (isSample) return
        val kind = category ?: "TASK_ALERT"
        ST.track("notification_settings.toggle", mapOf("notification_kind" to kind, "checked" to enabled))
        scope.launch {
            try {
                val res = if (category != null) {
                    SettingsNotificationAPI.updateCategory(category, enabled)
                } else {
                    SettingsNotificationAPI.updateTaskAlert(enabled)
                }
                if (res != null) phase = LoadPhase.Loaded(res)
                ST.track("notification_settings.toggle_result", mapOf("notification_kind" to kind, "checked" to enabled, "result" to "success"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ST.track("notification_settings.toggle_result", mapOf(
                    "notification_kind" to kind, "checked" to enabled, "result" to "fail", "error_status" to ST.status(e),
                ))
                phase = LoadPhase.Loaded(prev)
                Toast.error("설정 변경에 실패했어요. 다시 시도해 주세요.")
            }
        }
    }

    companion object {}
}

// MARK: - 화면

/**
 * 알림 설정 (쌓이는 화면)
 * @param sample 미리보기용 설정 값 (null 이면 서버에서 불러와요)
 * @param locked 미리보기에서 잠김 상태를 강제로 보여줄 때 (null 이면 둘러보기 여부)
 */
@Composable
fun SettingsNotificationScreen(sample: SettingsNotificationSettings? = null, locked: Boolean? = null) {
    val vm = rememberScreenModel(sample) { SettingsNotificationViewModel(sample) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // BM(2026-10): 알림은 Free 도 쓸 수 있어요 (AI 만 횟수 제한). 둘러보기만 잠금 (같은 계정을 모두 같이 써서)
    val isLocked = locked ?: UserStore.isGuest

    ReloadOn(listOf(DataKey.notificationSettings)) { vm.load() }

    PushedScreen(title = "알림 설정") {
        ScreenColumn {
            Text("여기서 바꾼 설정은 나를 앱의 푸시 알림에 적용돼요.", style = pt(14), color = NC.ink3)

            // Android 전용: 휴대폰 설정에서 이 앱 알림이 꺼져 있으면 아무 알림도 안 울려요
            NButton("휴대폰 알림 설정 열기", variant = NButtonVariant.secondary, size = NButtonSize.sm, icon = "bell") {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }

            // 이 기기에서 울리는 실천 도우미 알림 (하루 마무리 · 아침 첫걸음 · 시작한 이유 …)
            NudgeSettingsCard(locked = isLocked)

            when (val phase = vm.phase) {
                is LoadPhase.Loading -> LoadingView(minHeight = 192.dp)
                is LoadPhase.Failed -> {
                    Column(
                        Modifier.fillMaxWidth().card().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("설정을 불러오지 못했어요.", style = pt(15), color = NC.ink2)
                        NButton("다시 시도", variant = NButtonVariant.secondary) {
                            vm.trackRetry()
                            scope.launch { vm.load() }
                        }
                    }
                }
                is LoadPhase.Loaded -> SettingsNotificationContent(data = phase.value, locked = isLocked, vm = vm)
            }
        }
    }
}

@Composable
private fun SettingsNotificationContent(data: SettingsNotificationSettings, locked: Boolean, vm: SettingsNotificationViewModel) {
    Column(Modifier.fillMaxWidth().card()) {
        NotificationSectionTitle(title = "Task 알림 설정", locked = locked)
        NotificationToggleRow(
            title = "Task 알림",
            description = "개인이 설정한 Task 푸시 알림을 받을 수 있어요.",
            isOn = data.taskAlertEnabled,
            locked = locked,
            kind = "TASK_ALERT",
        ) { enabled ->
            vm.toggle(category = null, field = SettingsNotificationField.taskAlertEnabled, enabled = enabled)
        }
    }

    // 미션 알림 설정은 미션 기능 종료로 뺐어요 (서버 값은 그대로 두고 화면에서만 숨겨요)

    if (locked) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("로그인하면 푸시 알림을 설정할 수 있어요.", style = pt(13), color = NC.ink3)
            // 바로 멤버십 화면으로 갈 수 있게 버튼을 하나 더 뒀어요
            NButton("멤버십 보기", variant = NButtonVariant.brand, size = NButtonSize.sm, icon = "crown") {
                // [UX] '로그인하면…' 문구인데 멤버십으로 가요
                ST.track("notification_settings.locked_click", mapOf("notification_kind" to null, "destination" to "membership"))
                ST.pendingMembershipEntry = "notification_settings"
                AppRouter.push(AppRoute.Settings(SettingsRoute.Membership))
            }
        }
    }
}

@Composable
private fun NotificationSectionTitle(title: String, locked: Boolean) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = pt(16, PTWeight.bold), color = NC.ink)
            if (locked) NBadge("PRO", tone = BadgeTone.brand)
            Spacer(Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(NC.line))
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    description: String,
    isOn: Boolean,
    locked: Boolean,
    kind: String,
    onChange: (Boolean) -> Unit,
) {
    if (locked) {
        // 잠긴 상태 — 누르면 토글 대신 로그인 안내
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) {
                    ST.track("notification_settings.locked_click", mapOf("notification_kind" to kind, "destination" to "login_prompt"))
                    LoginPrompt.show("notification_settings")
                }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NotificationToggleText(title, description, locked, Modifier.weight(1f))
            SfImage("lock", size = 16.dp, tint = NC.ink4)
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NotificationToggleText(title, description, locked, Modifier.weight(1f))
            NSwitch(isOn = isOn, onIsOnChange = { onChange(it) }, label = title)
        }
    }
}

@Composable
private fun NotificationToggleText(title: String, description: String, locked: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = pt(15, PTWeight.semibold), color = if (locked) NC.ink4 else NC.ink)
        Text(description, style = pt(13), color = if (locked) NC.ink4 else NC.ink3)
    }
}

private val sampleNotificationSettings = SettingsNotificationSettings(
    missionAlertEnabled = true, missionCertificationEnabled = true, wishlistEnabled = false,
    missionProgressEnabled = true, missionEditEnabled = false, badgeEnabled = false,
    pokeEnabled = true, taskAlertEnabled = true,
)

@Preview(showBackground = true, heightDp = 1400, name = "PRO")
@Composable
private fun SettingsNotificationScreenPreview() {
    NaleulTheme(dark = false) {
        SettingsNotificationScreen(sample = sampleNotificationSettings, locked = false)
    }
}

@Preview(showBackground = true, heightDp = 1400, name = "둘러보기 (잠김)")
@Composable
private fun SettingsNotificationScreenLockedPreview() {
    NaleulTheme(dark = false) {
        SettingsNotificationScreen(sample = sampleNotificationSettings, locked = true)
    }
}
