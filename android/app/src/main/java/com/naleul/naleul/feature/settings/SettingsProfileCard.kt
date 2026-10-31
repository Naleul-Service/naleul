// feature/settings/SettingsProfileCard.kt
//
// iOS Features/Settings/SettingsProfileCard.swift
// 웹 원본: features/account/ui/ProfileSummaryCard.tsx (설정 맨 위 프로필 요약)
//          features/account/ui/ProfileEditModal.tsx (닉네임 수정)
//          features/account/api.ts (닉네임 규칙 · 탈퇴 사유 · 요청)
//
// 웹은 BFF(/me/nickname)를 거쳐 쿠키를 고치지만, 앱은 백엔드 PATCH /v1/users/nickname 을 바로 부르고
// 성공하면 UserStore 의 이름을 바꿔서 화면에 바로 보이게 해요.

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.UUID

// MARK: - 계정 API

object SettingsAccountAPI {
    /** 백엔드 UserNicknameUpdateRequest 의 @Pattern 과 같아요 */
    const val nicknamePattern = "^[가-힣a-zA-Z0-9]{2,10}$"
    const val nicknameHelp = "특수문자 제외 2~10자 이내로 입력해주세요."

    private val nicknameRegex = Regex(nicknamePattern)

    fun isValidNickname(s: String): Boolean = nicknameRegex.matches(s)

    /** 백엔드 UserNicknameUpdateResponse */
    @Serializable
    data class NicknameResponse(
        val userId: Int? = null,
        val nickname: String? = null,
    ) {
        companion object {}
    }

    /** PATCH /v1/users/nickname — 바뀐 닉네임을 돌려줘요 */
    suspend fun updateNickname(nickname: String): String {
        val r: NicknameResponse? = API.patch<NicknameResponse>("/v1/users/nickname", json("nickname" to nickname))
        return r?.nickname ?: nickname
    }

    /** DELETE /v1/users (body 에 사유). 성공하면 화면에서 로그아웃해요 */
    suspend fun withdraw(reason: SettingsWithdrawalReason, detail: String) {
        API.send("DELETE", "/v1/users", json("reason" to reason.rawValue, "detail" to detail))
    }
}

/** 백엔드 UserWithdrawalReason enum 과 값이 정확히 같아야 해요 */
enum class SettingsWithdrawalReason {
    LACK_OF_CONTENT, INCONVENIENT_OR_BUGGY, PREFER_OTHER_SERVICE, OTHER;

    val rawValue: String get() = name
    val id: String get() = rawValue

    val label: String
        get() = when (this) {
            LACK_OF_CONTENT -> "앱 콘텐츠가 부족하거나 만족스럽지 않아서"
            INCONVENIENT_OR_BUGGY -> "이용이 불편하고 장애가 많아서"
            PREFER_OTHER_SERVICE -> "다른 서비스가 더 좋아서"
            OTHER -> "기타"
        }

    companion object {
        fun fromRaw(raw: String?): SettingsWithdrawalReason? = entries.firstOrNull { it.rawValue == raw }
    }
}

// MARK: - 프로필 요약 카드

/** 줄 전체를 누르면 닉네임 수정 (휴대폰 설정 앱의 계정 줄처럼) */
@Composable
fun SettingsProfileSummaryCard(name: String, role: UserRole) {
    /** 시트를 열 때마다 현재 이름으로 입력칸을 새로 채우려고 id 를 새로 만들어요 (웹 modalKey) */
    var editing by remember { mutableStateOf<SettingsEditRequest?>(null) }
    val shape = RoundedCornerShape(Radius.card)
    val roleLabel = if (role == UserRole.free) "FREE" else role.raw

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NC.surface, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .clickable(role = Role.Button) {
                ST.track("settings.profile_edit_open", mapOf("is_premium" to role.isPaid))
                editing = SettingsEditRequest()
            }
            .semantics { contentDescription = "$name, $roleLabel, 닉네임 수정" }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                // 웹 bg-gradient-to-br from-[#8EA2FF] to-[#B49CFF] (아바타 장식 색)
                .background(NC.aiGradient(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1), style = pt(22, PTWeight.bold), color = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    name,
                    style = pt(19, PTWeight.bold),
                    color = NC.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                NBadge(roleLabel, tone = if (role == UserRole.free) BadgeTone.neutral else BadgeTone.brand)
            }
        }
        SfImage("chevron.right", size = 14.dp, tint = NC.ink4)
    }

    editing?.let { req ->
        key(req.id) {
            SettingsProfileEditSheet(currentName = name, onDismiss = { editing = null })
        }
    }
}

/** 시트 열기 요청 (매번 새 id) */
data class SettingsEditRequest(val id: String = UUID.randomUUID().toString()) {
    companion object {}
}

// MARK: - 닉네임 수정 시트 (웹 ProfileEditModal)

/**
 * 닉네임 수정 시트. 부르는 쪽은 `if (show) SettingsProfileEditSheet(…, onDismiss = { show = false })` 만 하면 돼요.
 * @param isPreview 미리보기에서는 저장하지 않아요
 */
@Composable
fun SettingsProfileEditSheet(
    currentName: String,
    isPreview: Boolean = false,
    onDismiss: () -> Unit,
) {
    var nickname by remember { mutableStateOf(currentName) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val trimmed = nickname.trim()
    val valid = SettingsAccountAPI.isValidNickname(trimmed)
    /** "saved" 면 닫혀도 profile_edit_close 안 보냄 · "cancel_button" */
    var outcome by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            GD.closeMethod(outcome)?.let { m ->
                ST.track("settings.profile_edit_close", mapOf("close_method" to m, "is_dirty" to (nickname.trim() != currentName)), isPreview)
            }
        }
    }

    fun submit(close: SheetClose) {
        if (!valid) {
            // [UX] 형식 오류는 버튼이 회색이라 키보드 완료를 눌러야 문구가 보여요
            error = SettingsAccountAPI.nicknameHelp
            ST.track("settings.profile_error_view", mapOf("validation_error" to "format", "text_length" to trimmed.length), isPreview)
            return
        }
        if (saving || trimmed == currentName) return
        ST.track("settings.profile_save_click", mapOf("text_length" to trimmed.length), isPreview)
        if (isPreview) { close(); return }
        val name = trimmed
        saving = true
        scope.launch {
            try {
                val saved = SettingsAccountAPI.updateNickname(name)
                UserStore.updateUserName(saved)
                Invalidation.invalidate(DataKey.account)
                ST.track("settings.profile_save_result", mapOf("result" to "success"))
                Toast.success("닉네임을 바꿨어요.")
                saving = false
                outcome = "saved"
                close()
            } catch (e: CancellationException) {
                saving = false
                throw e
            } catch (e: Exception) {
                saving = false
                error = errorMessage(e, fallback = "닉네임 수정에 실패했어요. 다시 시도해 주세요.")
                val status = ST.status(e)
                ST.track("settings.profile_save_result", mapOf("result" to "fail", "error_status" to status))
                ST.track("settings.profile_error_view", mapOf(
                    "validation_error" to if (status == 409) "duplicate" else "server", "text_length" to name.length,
                ))
            }
        }
    }

    SheetScaffold(
        onDismiss = onDismiss,
        title = "프로필 수정",
        dismissible = !saving,
        footer = { close ->
            NButton("취소", variant = NButtonVariant.secondary, enabled = !saving) {
                outcome = "cancel_button"
                close()
            }
            NButton("저장", loading = saving, enabled = valid && trimmed != currentName) { submit(close) }
        },
    ) { close ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val danger = NC.danger
            Text(
                buildAnnotatedString {
                    append("닉네임 ")
                    withStyle(SpanStyle(color = danger)) { append("*") }
                },
                style = pt(12, PTWeight.medium),
                color = NC.ink3,
            )
            NTextField(
                value = nickname,
                onValueChange = { v ->
                    nickname = v.take(10)   // maxLength 10
                    error = null
                },
                placeholder = "이름을 작성해주세요",
                invalid = error != null,
                onSubmit = { submit(close) },
            )
            FieldMessage(text = error ?: SettingsAccountAPI.nicknameHelp, isError = error != null)
        }
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, name = "프로필 카드")
@Composable
private fun SettingsProfileSummaryCardPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsProfileSummaryCard(name = "유림", role = UserRole.pro)
            SettingsProfileSummaryCard(name = "나를 사용자", role = UserRole.free)
        }
    }
}

@Preview(showBackground = true, name = "닉네임 수정")
@Composable
private fun SettingsProfileEditSheetPreview() {
    NaleulTheme(dark = false) {
        SettingsProfileEditSheet(currentName = "유림", isPreview = true, onDismiss = {})
    }
}
