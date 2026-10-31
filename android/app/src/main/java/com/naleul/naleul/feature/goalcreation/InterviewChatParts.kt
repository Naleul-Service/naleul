// feature/goalcreation/InterviewChatParts.kt
//
// iOS Features/GoalCreation/InterviewChatParts.swift
// 대화 화면 부품: 말풍선 · 입력 중 표시 · 입력창 · 질문 진행 점.
// 웹 원본: features/goal-creation/ui/ChatBubbles.tsx · ChatComposer.tsx · InterviewProgress.tsx

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

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** AI 말풍선 모양 (왼쪽 위 모서리만 덜 둥글게) */
private val InterviewAssistantShape = RoundedCornerShape(topStart = 6.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)

/** 내 말풍선 모양 (오른쪽 위 모서리만 덜 둥글게) */
private val InterviewUserShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 6.dp, bottomStart = 18.dp)

// MARK: - 말풍선

@Composable
private fun InterviewAiAvatar() {
    AIBadge(modifier = Modifier.clearAndSetSemantics { })
}

/** AI 말풍선 */
@Composable
fun InterviewAssistantBubble(content: String?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        InterviewAiAvatar()
        // 오른쪽에 최소 32 여백 (iOS Spacer(minLength: 32))
        Box(Modifier.weight(1f).padding(end = 32.dp)) {
            Text(
                content ?: "",
                style = pt(15).copy(lineHeight = 22.sp),
                color = NC.ink,
                modifier = Modifier
                    .background(NC.subtle, InterviewAssistantShape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** 내 말풍선 + 실패 시 다시 보내기 */
@Composable
fun InterviewUserBubble(
    item: InterviewChatItem,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val failed = item.sendState == InterviewSendState.failed
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 왼쪽에 최소 48 여백 (iOS Spacer(minLength: 48))
        Box(Modifier.fillMaxWidth().padding(start = 48.dp), contentAlignment = Alignment.CenterEnd) {
            if (item.skip) {
                val dash = NC.lineStrong
                Text(
                    "건너뛸게요",
                    style = pt(14),
                    color = NC.ink3,
                    modifier = Modifier
                        .drawBehind {
                            val outline = InterviewUserShape.createOutline(size, layoutDirection, this)
                            drawOutline(
                                outline,
                                color = dash,
                                style = Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                                ),
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            } else {
                Text(
                    item.content ?: "",
                    style = NType.bodyLG,
                    color = NC.textPrimary,
                    modifier = Modifier
                        .alpha(if (failed) 0.5f else if (item.sendState == InterviewSendState.sending) 0.7f else 1f)
                        .background(NC.aiTintSoft, InterviewUserShape)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                )
            }
        }

        if (failed) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SfImage("exclamationmark.circle", size = 12.dp, tint = NC.danger)
                    Text(item.failedMessage ?: "전송하지 못했어요.", style = pt(13), color = NC.danger, textAlign = TextAlign.End)
                }
                Row(
                    Modifier.clickable(role = Role.Button) { onRetry(item.key) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SfImage("arrow.clockwise", size = 12.dp, tint = NC.ink)
                    Text("다시 보내기", style = pt(13, PTWeight.semibold), color = NC.ink)
                }
                Text(
                    "지우기",
                    style = pt(13),
                    color = NC.ink3,
                    modifier = Modifier.clickable(role = Role.Button) { onDiscard(item.key) },
                )
            }
        }
    }
}

/** AI 가 답변을 만드는 중 (점 3개) */
@Composable
fun InterviewTypingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "AI가 답변을 준비하고 있어요" },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InterviewAiAvatar()
        Row(
            Modifier
                .height(46.dp)
                .background(NC.subtle, InterviewAssistantShape)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (i in 0 until 3) {
                // 2 → -3 으로 오르내림 (점마다 0.14초씩 늦게 — iOS .delay(i * 0.14))
                val y by transition.animateFloat(
                    initialValue = 2f,
                    targetValue = -3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(450, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(i * 140),
                    ),
                    label = "dot$i",
                )
                Box(
                    Modifier
                        .offset(y = y.dp)
                        .size(6.dp)
                        .background(NC.ink4, CircleShape)
                )
            }
        }
    }
}

// MARK: - 입력창

/**
 * 채팅 입력창. 내용에 맞춰 높이가 늘어나요 (최대 6줄).
 * 웹은 Enter 전송 · Shift+Enter 줄바꿈인데, 휴대폰 키보드는 줄바꿈 키만 있어서 → 오른쪽 보내기 버튼으로 보내요.
 * @param focus iOS FocusState 바인딩 대신 — 부모가 포커스를 주고(requestFocus) onFocusChange 로 상태를 받아요
 */
@Composable
fun InterviewChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    disabled: Boolean = false,
    placeholder: String = "답변을 입력하세요",
    focus: FocusRequester? = null,
    onFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit,
) {
    val canSend = !disabled && GoalCreationForm.trim(value).isNotEmpty()
    val nearLimit = value.length > InterviewChatComposerMaxLength - 50
    val shape = RoundedCornerShape(Radius.composer)
    val ink = NC.ink
    val ink4 = NC.ink4
    Row(
        modifier
            .fillMaxWidth()
            .background(if (disabled) NC.disabledBg else NC.bgSurface, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(InterviewChatComposerMaxLength)) },
            enabled = !disabled,
            singleLine = false,
            minLines = 1,
            maxLines = 6,
            textStyle = pt(15).copy(color = ink),
            cursorBrush = SolidColor(NC.brand),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 9.dp)
                .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                .onFocusChanged { onFocusChange(it.isFocused) }
                .semantics { contentDescription = "답변 입력" },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = pt(15), color = ink4)
                    inner()
                }
            },
        )

        if (nearLimit) {
            Text(
                "${value.length}/$InterviewChatComposerMaxLength",
                style = ptMono(12),
                color = NC.ink3,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (canSend) NC.actionPrimary else NC.borderSubtle, CircleShape)
                .clickable(enabled = canSend, role = Role.Button) { if (canSend) onSubmit() }
                .semantics { contentDescription = "보내기" },
            contentAlignment = Alignment.Center,
        ) { SfImage("arrow.up", size = 18.dp, tint = if (canSend) NC.actionPrimaryFg else NC.textTertiary) }
    }
}

/** iOS InterviewChatComposer.maxLength */
const val InterviewChatComposerMaxLength = 600

// MARK: - 질문 진행 (●●○○○)

@Composable
fun InterviewProgress(current: Int, max: Int, modifier: Modifier = Modifier) {
    val shown = minOf(maxOf(current, 0), max)
    // 웹은 좁은 화면에서 "질문 2 / 5" 글자를 숨기고 점만 보여줘요
    Row(
        modifier.clearAndSetSemantics { contentDescription = "질문 $shown / $max" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until maxOf(max, 0)) {
            val color = when {
                i < shown - 1 -> NC.ink
                i == shown - 1 -> NC.brand
                else -> NC.lineStrong
            }
            Box(Modifier.size(6.dp).background(color, CircleShape))
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun InterviewChatPartsPreview() {
    NaleulTheme(dark = false) {
        var text by remember { mutableStateOf("") }
        Column(
            Modifier.fillMaxSize().background(NC.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InterviewProgress(current = 2, max = 5)
            InterviewAssistantBubble(content = "좋아요! 지금 몸무게와 목표 몸무게를 알려주세요.")
            InterviewUserBubble(
                item = InterviewChatItem(key = "a", role = GoalCreationMessageRole.USER, content = "78kg인데 70kg까지", skip = false, quickReplies = emptyList(), skippable = false),
                onRetry = {}, onDiscard = {},
            )
            InterviewUserBubble(
                item = InterviewChatItem(key = "b", role = GoalCreationMessageRole.USER, content = null, skip = true, quickReplies = emptyList(), skippable = false),
                onRetry = {}, onDiscard = {},
            )
            InterviewUserBubble(
                item = InterviewChatItem(
                    key = "c", role = GoalCreationMessageRole.USER, content = "건강 때문에요", skip = false,
                    quickReplies = emptyList(), skippable = false, sendState = InterviewSendState.failed,
                    failedMessage = "AI 응답이 늦어지고 있어요.",
                ),
                onRetry = {}, onDiscard = {},
            )
            InterviewTypingIndicator()
            Spacer(Modifier.weight(1f))
            InterviewChatComposer(value = text, onValueChange = { text = it }) {}
        }
    }
}
