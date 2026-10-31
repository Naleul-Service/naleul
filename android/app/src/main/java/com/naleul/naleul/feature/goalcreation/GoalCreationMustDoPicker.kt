// feature/goalcreation/GoalCreationMustDoPicker.kt
//
// iOS Features/GoalCreation/GoalCreationMustDoPicker.swift
// "이 목표를 위해 꼭 해야 할 일" 고르기 — 추천 칩을 누르거나 입력창에 적어요.
// 웹 원본: features/goal-creation/ui/MustDoPicker.tsx
//
// 예) 다이어트 → 헬스 · 식단 기록. 고른 일은 계획 AI 가 반드시 루틴으로 넣어요.
//  - 하나만: 입력창에 적기만 하면 돼요 (부모가 GoalCreationMustDo.withDraft 로 함께 보내요)
//  - 여러 개: "+ 추가"(또는 키보드 완료)로 칩을 만들고 다음 걸 적어요

package com.naleul.naleul.feature.goalcreation

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

object GoalCreationMustDo {
    const val max = 5
    const val itemMax = 30

    /** 앞뒤 공백 제거 · 연속 공백 하나로 · 30자까지 */
    fun clean(v: String): String {
        val collapsed = v.trim().replace(Regex("\\s{2,}"), " ")
        return GoalCreationForm.prefix(collapsed, itemMax)
    }

    /** 고른 일 + 입력창에 적어 두고 아직 추가 안 한 일 → 실제로 보낼 목록 */
    fun withDraft(value: List<String>, draft: String): List<String> {
        val d = clean(draft)
        val all = if (d.isNotEmpty() && !value.contains(d)) value + d else value
        return all.take(max)
    }
}

/**
 * @param draft 입력창 내용 (부모가 "적기만 한 것"도 보내려고 알고 있어야 해요)
 * @param focus 부모가 이 입력칸으로 포커스를 옮길 때 (목표 입력에서 완료 → 여기로). iOS FocusState 바인딩 대신
 */
@Composable
fun GoalCreationMustDoPicker(
    value: List<String>,
    onValueChange: (List<String>) -> Unit,
    draft: String,
    onDraftChange: (String) -> Unit,
    suggestions: List<String> = emptyList(),
    disabled: Boolean = false,
    focus: FocusRequester? = null,
    modifier: Modifier = Modifier,
    /** 이벤트의 surface (goal_new: 시작 화면 / goal_review: 정리 확인 시트) */
    surface: String = "goal_new",
) {
    val full = value.size >= GoalCreationMustDo.max
    val custom = value.filter { !suggestions.contains(it) }

    fun toggle(v: String) {
        if (disabled) return
        val removing = value.contains(v)
        val next = if (removing) value.filter { it != v } else if (!full) value + v else return
        onValueChange(next)
        GCTracker.track(
            GCEvent.mustDoToggle,
            "surface" to surface, "must_do_action" to if (removing) "remove" else "add",
            "item_source" to if (suggestions.contains(v)) "suggestion" else "custom",
            "must_do_count" to next.size,
            "session_id" to if (surface == "goal_new") null else GCTracker.sessionId,
        )
    }

    /** method: button(+ 추가) · keyboard(완료). 빈 글 · 이미 있는 일은 조용히 무시해서 이벤트도 안 보내요 */
    fun addDraft(method: String) {
        if (disabled) return
        val v = GoalCreationMustDo.clean(draft)
        if (v.isNotEmpty() && !value.contains(v) && !full) {
            onValueChange(value + v)
            GCTracker.track(
                GCEvent.mustDoAdd,
                "surface" to surface, "submit_method" to method, "must_do_count" to value.size + 1,
                "session_id" to if (surface == "goal_new") null else GCTracker.sessionId,
            )
        }
        onDraftChange("")
    }

    Column(
        modifier.alpha(if (disabled) 0.4f else 1f),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (suggestions.isNotEmpty() || custom.isNotEmpty()) {
            FlowLayout(spacing = 8.dp) {
                for (s in suggestions) {
                    val on = value.contains(s)
                    val blocked = !on && full
                    Row(
                        Modifier
                            .height(36.dp)
                            .alpha(if (blocked) 0.4f else 1f)
                            .clip(CircleShape)
                            .background(if (on) NC.brandSoft else NC.surface, CircleShape)
                            .border(if (on) 1.5.dp else 1.dp, if (on) NC.borderFocus else NC.borderSubtle, CircleShape)
                            .clickable(enabled = !blocked && !disabled, role = Role.Checkbox) { toggle(s) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (on) SfImage("checkmark", size = 11.dp, tint = NC.textBrand)
                        Text(s, style = pt(14, PTWeight.medium), color = if (on) NC.textBrand else NC.ink2, maxLines = 1)
                    }
                }
                for (s in custom) {
                    Row(
                        Modifier
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(NC.brandSoft, CircleShape)
                            .border(1.dp, NC.brand, CircleShape)
                            .padding(start = 14.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(s, style = pt(14, PTWeight.medium), color = NC.textBrand, maxLines = 1)
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !disabled, role = Role.Button) { toggle(s) }
                                .semantics { contentDescription = "$s 빼기" },
                            contentAlignment = Alignment.Center,
                        ) { SfImage("xmark", size = 11.dp, tint = NC.textBrand) }
                    }
                }
            }
        }

        // 입력 줄
        val placeholder = when {
            full -> "최대 ${GoalCreationMustDo.max}개까지 고를 수 있어요"
            value.isEmpty() -> "직접 적어 주세요 (예: 주말 등산)"
            else -> "하나 더 있다면 적고 + 추가"
        }
        val canAdd = !full && GoalCreationForm.trim(draft).isNotEmpty() && !disabled
        val shape = RoundedCornerShape(Radius.xl)
        val ink = NC.ink
        val ink4 = NC.ink4
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(NC.surface, shape)
                .border(1.dp, NC.borderSubtle, shape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { onDraftChange(GoalCreationForm.prefix(it, GoalCreationMustDo.itemMax)) },
                enabled = !full && !disabled,
                singleLine = true,
                textStyle = pt(14).copy(color = ink),
                cursorBrush = SolidColor(NC.brand),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addDraft("keyboard") }),
                modifier = Modifier
                    .weight(1f)
                    .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                    .semantics { contentDescription = "꼭 하고 싶은 일 직접 적기" },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (draft.isEmpty()) Text(placeholder, style = pt(14), color = ink4, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    }
                },
            )
            Row(
                Modifier
                    .alpha(if (canAdd) 1f else 0.4f)
                    .clickable(enabled = canAdd, role = Role.Button) { addDraft("button") },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SfImage("plus", size = 14.dp, tint = NC.textBrand)
                Text("추가", style = NType.bodySMMedium, color = NC.textBrand)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GoalCreationMustDoPickerPreview() {
    NaleulTheme(dark = false) {
        var value by remember { mutableStateOf(listOf("헬스", "주말 등산")) }
        var draft by remember { mutableStateOf("") }
        Column(Modifier.background(NC.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GoalCreationMustDoPicker(
                value = value, onValueChange = { value = it },
                draft = draft, onDraftChange = { draft = it },
                suggestions = listOf("헬스", "식단 기록", "유산소", "체중 기록", "간헐적 단식"),
            )
            Text("보낼 목록: ${GoalCreationMustDo.withDraft(value, draft).joinToString(", ")}", style = pt(12), color = NC.ink3)
        }
    }
}
