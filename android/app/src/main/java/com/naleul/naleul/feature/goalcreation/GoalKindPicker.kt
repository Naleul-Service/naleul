// feature/goalcreation/GoalKindPicker.kt
//
// iOS Features/GoalCreation/GoalKindPicker.swift
// 목표 카테고리 2단계 선택 (명세 6.A.1.2).
// 웹 원본: features/goal/ui/GoalKindPicker.tsx
//
// 1단계 카드(건강 / 학습·자기개발 / 생활습관 / 기타) → 2단계 칩 → "기타"면 이름 직접 입력(10자).
// 1단계 "기타"는 2단계 없이 바로 이름을 적어요.
// value 는 2단계까지 고른 뒤에만 채워지고, 고르는 중에는 null 이에요 (GoalKind.isComplete 로 "기타 이름"까지 확인).
//
// 목표 추가 · 목표 헤더 수정(Goal 기능)에서도 GoalKindPicker(value = kind, onValueChange = { kind = it }, compact = true) 로 써요.

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * @param compact 목표 헤더 수정처럼 좁은 곳 — 1단계를 칩으로
 */
@Composable
fun GoalKindPicker(
    value: GoalKindValue?,
    onValueChange: (GoalKindValue?) -> Unit,
    disabled: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    /** 이벤트 기록용 (목표 만들기 시작 화면만 넘겨요) */
    onTypePick: ((GoalType) -> Unit)? = null,
    onSubPick: ((GoalType, GoalSubType) -> Unit)? = null,
    /** 고를 수 있는 1단계 (AI 목표형은 GoalKind.aiTypes). 3개 이하면 카드 대신 칩으로 */
    types: List<GoalTypeOption> = GoalKind.types,
) {
    /** 1단계만 고르고 2단계는 아직인 상태 */
    var pendingType by remember { mutableStateOf(value?.goalType) }

    val activeType: GoalType? = value?.goalType ?: pendingType
    val typeOption: GoalTypeOption? = types.firstOrNull { it.value == activeType }
    val isCustom = value?.let { GoalKind.subOption(it.goalSubType).custom } ?: false

    fun pickType(t: GoalType) {
        if (disabled) return
        onTypePick?.invoke(t)
        pendingType = t
        val opt = types.firstOrNull { it.value == t } ?: return
        // 2단계가 "기타" 하나뿐(1단계 기타)이면 바로 그걸로
        val only = opt.subs.firstOrNull()
        if (opt.subs.size == 1 && only != null) {
            onValueChange(GoalKindValue(goalType = t, goalSubType = only.value, goalKindLabel = ""))
        } else {
            onValueChange(null)
        }
    }

    fun pickSub(s: GoalSubType) {
        if (disabled) return
        val t = activeType ?: return
        onSubPick?.invoke(t, s)
        onValueChange(
            GoalKindValue(
                goalType = t,
                goalSubType = s,
                goalKindLabel = if (GoalKind.subOption(s).custom) (value?.goalKindLabel ?: "") else null,
            )
        )
    }

    Column(
        modifier.alpha(if (disabled) 0.4f else 1f),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (compact || types.size <= 3) {
            FlowLayout(spacing = if (compact) 6.dp else 8.dp, modifier = Modifier.semantics { contentDescription = "목표 카테고리" }) {
                for (t in types) {
                    NChip("${t.emoji} ${t.label}", selected = activeType == t.value, size = if (compact) ChipSize.sm else ChipSize.md) { pickType(t.value) }
                }
            }
        } else {
            // 2열 카드 (iOS LazyVGrid — 스크롤 안이라 행을 직접 만들어요. 같은 줄 카드 높이를 같게)
            Column(
                Modifier.semantics { contentDescription = "목표 카테고리" },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (row in types.chunked(2)) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (t in row) {
                            GoalKindTypeCard(
                                option = t,
                                selected = activeType == t.value,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) { pickType(t.value) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        val type = typeOption
        if (type != null && type.subs.size > 1) {
            FlowLayout(
                spacing = if (compact) 6.dp else 8.dp,
                modifier = Modifier.semantics { contentDescription = "${type.label} 세부 카테고리" },
            ) {
                for (s in type.subs) {
                    NChip(
                        "${s.emoji} ${s.label}",
                        selected = value?.goalSubType == s.value,
                        size = if (compact) ChipSize.sm else ChipSize.md,
                    ) { pickSub(s.value) }
                }
            }
        }

        if (isCustom) {
            GoalKindCustomLabelField(
                label = value?.goalKindLabel ?: "",
                placeholder = when (typeOption?.value) {
                    GoalType.ETC -> "예) 악기 연습"
                    GoalType.HEALTH -> "예) 필라테스"
                    else -> "예) 주식 공부"
                },
                enabled = !disabled,
                onLabelChange = { new ->
                    val v = value ?: return@GoalKindCustomLabelField
                    // 웹 maxLength — 10자까지만
                    onValueChange(v.copy(goalKindLabel = GoalCreationForm.prefix(new, GoalKind.labelMax)))
                },
            )
        }
    }
}

/** "기타" 이름 직접 입력 칸 */
@Composable
private fun GoalKindCustomLabelField(
    label: String,
    placeholder: String,
    enabled: Boolean,
    onLabelChange: (String) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Radius.xl)
    val ink = NC.ink
    val ink4 = NC.ink4
    // 웹 autoFocus — 이름 칸이 나타나면 바로 입력
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: IllegalStateException) {
            // 아직 화면에 붙기 전이면 무시
        }
    }
    Row(
        Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .height(40.dp)
            .background(NC.surface, shape)
            .border(if (focused) 1.5.dp else 1.dp, if (focused) NC.borderFocus else NC.borderSubtle, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = label,
            onValueChange = onLabelChange,
            enabled = enabled,
            singleLine = true,
            textStyle = pt(14).copy(color = ink),
            cursorBrush = SolidColor(NC.brand),
            interactionSource = interaction,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .semantics { contentDescription = "카테고리 이름 직접 입력" },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (label.isEmpty()) Text(placeholder, style = pt(14), color = ink4, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
            },
        )
        Text("${GoalCreationForm.count(label)}/${GoalKind.labelMax}", style = ptMono(12), color = NC.ink4)
    }
}

/** 1단계 카드 (이모지 · 이름 · 힌트) */
@Composable
private fun GoalKindTypeCard(
    option: GoalTypeOption,
    selected: Boolean,
    modifier: Modifier = Modifier,
    action: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.xxl)
    Column(
        modifier
            .defaultMinSize(minHeight = 92.dp)
            .clip(shape)
            .background(if (selected) NC.brandSoft else NC.surface, shape)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) NC.borderFocus else NC.borderSubtle, shape)
            .clickable(role = Role.RadioButton, onClick = action)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(option.emoji, style = TextStyle(fontSize = 22.sp))
        Text(
            option.label,
            style = pt(15, PTWeight.bold),
            color = if (selected) NC.textBrand else NC.ink,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            option.hint,
            style = pt(12),
            color = NC.ink3,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun GoalKindPickerPreview() {
    NaleulTheme(dark = false) {
        var kind by remember { mutableStateOf<GoalKindValue?>(GoalKindValue(GoalType.HEALTH, GoalSubType.DIET, null)) }
        var custom by remember { mutableStateOf<GoalKindValue?>(GoalKindValue(GoalType.ETC, GoalSubType.OTHER, "악기 연습")) }
        var empty by remember { mutableStateOf<GoalKindValue?>(null) }
        Column(
            Modifier.background(NC.surface).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            GoalKindPicker(value = kind, onValueChange = { kind = it })
            Text(GoalKind.name(kind) ?: "-", style = pt(13), color = NC.ink3)
            GoalKindPicker(value = custom, onValueChange = { custom = it }, compact = true)
            GoalKindPicker(value = empty, onValueChange = { empty = it }, disabled = true)
        }
    }
}
