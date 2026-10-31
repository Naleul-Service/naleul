// feature/settings/SettingsWithdrawScreen.kt
//
// iOS Features/Settings/SettingsWithdrawScreen.swift
// 웹 원본: features/account/ui/WithdrawView.tsx (/settings/withdraw)
//
// 회원 탈퇴: 사유 고르기 → (기타면 상세 사유) → 유의 사항 동의 → 확인창 → DELETE /v1/users → 로그아웃

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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SettingsWithdrawScreen() {
    var reason by remember { mutableStateOf<SettingsWithdrawalReason?>(null) }
    var detail by remember { mutableStateOf("") }
    var agreed by remember { mutableStateOf(false) }
    var confirmOpen by remember { mutableStateOf(false) }
    var withdrawing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // "기타"일 때만 상세 사유가 필수 (백엔드 서비스 단 검증과 같은 규칙)
    val r = reason
    val reasonOk = r != null && (r != SettingsWithdrawalReason.OTHER || detail.trim().isNotEmpty())
    val canSubmit = reasonOk && agreed
    val preview = androidx.compose.ui.platform.LocalInspectionMode.current
    /** 탈퇴가 끝나 화면을 떠나면 withdraw.abandon 을 안 보내요 */
    var withdrawn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ST.track("withdraw.pageview", mapOf("is_premium" to ST.isPremium, "membership_state" to ST.roleState), preview)
    }
    DisposableEffect(Unit) {
        onDispose {
            // [UX] 탈퇴하지 않고 떠남 — 어디까지 쓰다 마음을 바꿨는지
            if (!withdrawn) ST.track("withdraw.abandon", mapOf(
                "withdraw_reason" to reason?.rawValue, "checked" to agreed, "has_input" to detail.isNotBlank(),
            ), preview)
        }
    }

    fun submit() {
        val picked = reason ?: return
        if (!canSubmit || withdrawing) return
        val detailText = if (picked == SettingsWithdrawalReason.OTHER) detail.trim() else ""
        ST.track("withdraw.confirm_click", mapOf(
            "withdraw_reason" to picked.rawValue, "is_premium" to ST.isPremium, "membership_state" to ST.roleState,
        ))
        withdrawing = true
        scope.launch {
            try {
                SettingsAccountAPI.withdraw(reason = picked, detail = detailText)
                ST.track("withdraw.result", mapOf("result" to "success", "withdraw_reason" to picked.rawValue))
                withdrawn = true
                // 성공하면 토큰을 지우고 로그인 화면으로 (RootScreen 이 isLoggedIn 변화를 보고 바꿔요)
                AuthService.logout(AuthService.LogoutReason.withdraw)
            } catch (e: CancellationException) {
                withdrawing = false
                throw e
            } catch (e: Exception) {
                withdrawing = false
                ST.track("withdraw.result", mapOf("result" to "fail", "error_status" to ST.status(e), "withdraw_reason" to picked.rawValue))
                Toast.error(errorMessage(e, fallback = "탈퇴에 실패했어요. 잠시 후 다시 시도해 주세요."))
            }
        }
    }

    PushedScreen(title = "회원 탈퇴") {
        ScreenColumn {
            Column(Modifier.fillMaxWidth().card().padding(20.dp)) {
                Text("정말 탈퇴하실건가요?", style = NType.h4, color = NC.ink)
                Text("탈퇴 시 유의사항", style = pt(13, PTWeight.medium), color = NC.ink3, modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.padding(top = 4.dp, start = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("•", style = pt(13), color = NC.ink3)
                    Text(
                        "수집된 개인정보 및 나를 계정에 저장된 모든 정보(목표, 루틴, 일정, 미션과 관련된 모든 컨텐츠)는 삭제되어 복구할 수 없고 연결된 기기의 연결이 해제됩니다.",
                        style = pt(13).copy(lineHeight = 19.sp),
                        color = NC.ink3,
                    )
                }

                WithdrawReasonPicker(
                    reason = reason,
                    onPick = { picked ->
                        if (picked != reason) ST.track("withdraw.reason_select", mapOf("withdraw_reason" to picked.rawValue), preview)
                        reason = picked
                        if (picked != SettingsWithdrawalReason.OTHER) detail = ""
                    },
                    modifier = Modifier.padding(top = 24.dp),
                )

                if (reason == SettingsWithdrawalReason.OTHER) {
                    NTextField(
                        value = detail,
                        onValueChange = { detail = it },
                        placeholder = "탈퇴 사유를 자유롭게 적어주세요",
                        minHeight = 104.dp,
                        singleLine = false,
                        maxLength = 500,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .semantics { contentDescription = "탈퇴 상세 사유" },
                    )
                }

                WithdrawAgreeCheckbox(agreed = agreed, onToggle = {
                    agreed = !agreed
                    ST.track("withdraw.agree_toggle", mapOf("checked" to agreed, "withdraw_reason" to reason?.rawValue), preview)
                }, modifier = Modifier.padding(top = 24.dp))

                NButton(
                    "탈퇴하기",
                    variant = NButtonVariant.danger,
                    size = NButtonSize.lg,
                    loading = withdrawing,
                    fullWidth = true,
                    // 예전엔 사유 · 동의 전에는 버튼이 꺼져 있어서 "눌리지 않는다"고 느껴졌어요
                    // → 항상 누를 수 있게 두고, 빠진 게 있으면 무엇을 해야 하는지 알려줘요
                    modifier = Modifier.padding(top = 20.dp).alpha(if (canSubmit) 1f else 0.55f),
                ) {
                    val validationError = when {
                        reason == null -> "no_reason"
                        reason == SettingsWithdrawalReason.OTHER && detail.isBlank() -> "no_detail"
                        !agreed -> "not_agreed"
                        else -> null
                    }
                    ST.track(
                        "withdraw.submit_click",
                        mapOf("withdraw_reason" to reason?.rawValue, "text_length" to detail.trim().length, "validation_error" to validationError),
                        preview,
                    )
                    when (validationError) {
                        "no_reason" -> Toast.show("떠나시는 이유를 선택해 주세요.")
                        "no_detail" -> Toast.show("탈퇴 사유를 적어 주세요.")
                        "not_agreed" -> Toast.show("탈퇴 유의 사항에 동의해 주세요.")
                        else -> confirmOpen = true
                    }
                }
            }
        }
    }

    ConfirmDialog(
        isPresented = confirmOpen,
        onDismiss = { confirmOpen = false },
        title = "정말 탈퇴하시겠어요?",
        message = "이 작업은 되돌릴 수 없어요.",
        confirmLabel = "탈퇴하기",
        tone = ConfirmTone.danger,
        onCancel = { ST.track("withdraw.confirm_cancel", mapOf("withdraw_reason" to reason?.rawValue)) },
    ) { submit() }
}

/** 웹 <select> → 메뉴 */
@Composable
private fun WithdrawReasonPicker(
    reason: SettingsWithdrawalReason?,
    onPick: (SettingsWithdrawalReason) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val danger = NC.danger
        Text(
            buildAnnotatedString {
                append("떠나시는 이유를 알려주세요 ")
                withStyle(SpanStyle(color = danger)) { append("*") }
            },
            style = pt(14, PTWeight.semibold),
            color = NC.ink2,
        )
        Box(Modifier.fillMaxWidth()) {
            val shape = RoundedCornerShape(Radius.xl)
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(shape)
                    .background(NC.surface, shape)
                    .border(1.dp, NC.borderSubtle, shape)
                    .clickable(role = Role.Button) { open = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    reason?.label ?: "탈퇴 사유 선택",
                    style = pt(15),
                    color = if (reason == null) NC.ink4 else NC.ink,
                    modifier = Modifier.weight(1f),
                )
                SfImage("chevron.up.chevron.down", size = 12.dp, tint = NC.ink3)
            }
            NDropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = NC.surface) {
                for (r in SettingsWithdrawalReason.entries) {
                    NDropdownMenuItem(
                        text = { Text(r.label, style = pt(15), color = NC.ink) },
                        trailingIcon = if (r == reason) ({ SfImage("checkmark", size = 14.dp, tint = NC.textBrand) }) else null,
                        onClick = {
                            open = false
                            onPick(r)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WithdrawAgreeCheckbox(agreed: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val boxShape = RoundedCornerShape(6.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox) { onToggle() }
            .semantics { selected = agreed },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(if (agreed) NC.brand else NC.surface, boxShape)
                .border(if (agreed) 1.5.dp else 1.dp, if (agreed) NC.borderFocus else NC.borderSubtle, boxShape),
            contentAlignment = Alignment.Center,
        ) {
            if (agreed) SfImage("checkmark", size = 12.dp, tint = Color.White)
        }
        Text("회원 탈퇴 유의 사항을 확인했으며 동의합니다.", style = pt(14), color = NC.ink2)
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun SettingsWithdrawScreenPreview() {
    NaleulTheme(dark = false) {
        SettingsWithdrawScreen()
    }
}
