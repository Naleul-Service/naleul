// designsystem/Card.kt
//
// Component_ver.2 Card · Chip · Tag · Event (캘린더 블록)
//
//   Column(Modifier.card().padding(20.dp)) { ... }     // iOS VStack { }.padding(20).card()
//   CardHeader("루틴", aside = "3개")
//   NChip("아침", selected = picked) { toggle() }        // 선택 칩 (색 코드를 주면 목표 색 칩)
//   GoalChip("운동", colorCode = goal.colorCode)         // 목표 색 칩 (tint + 색 막대)
//   NBadge("AI가 설계한 목표", tone = BadgeTone.brand)     // Tag (상태 알약)
//   EventBlock("러닝", time = "07:00–08:00", tone = goalTone(goal.colorCode), state = EventState.done)
//   FixedEventBlock("점심", time = "12:00–13:00")
//
// ⚠️ 순서: Modifier.card() 를 padding 보다 "먼저" 써야 배경이 패딩까지 칠해져요
//    (SwiftUI 는 .padding().card() 순서, Compose 는 반대)

package com.naleul.naleul.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// MARK: - Card

/** Card: bg/surface · 1px border/subtle · radius 16 · shadow-card (iOS .card()) */
@Composable
fun Modifier.card(radius: Dp = Radius.card, background: Color = NC.bgSurface): Modifier {
    val shape = RoundedCornerShape(radius)
    val line = NC.borderSubtle
    return this
        .cardShadow(shape)
        .clip(shape)
        .background(background, shape)
        .border(1.dp, line, shape)
}

/** 카드 제목 줄 (Heading/H3 + 오른쪽 보조 Caption) */
@Composable
fun CardHeader(title: String, modifier: Modifier = Modifier, aside: @Composable RowScope.() -> Unit = {}) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = NType.h4, color = NC.textPrimary)
        Spacer(Modifier.weight(1f))
        CompositionLocalProvider(LocalTextStyle provides NType.bodySM, LocalContentColor provides NC.textTertiary) {
            aside()
        }
    }
}

@Composable
fun CardHeader(title: String, aside: String, modifier: Modifier = Modifier) {
    CardHeader(title, modifier) { Text(aside, style = NType.bodySM, color = NC.textTertiary) }
}

// MARK: - Chip (MD h32 r8 · SM h24 r6)

enum class ChipSize { sm, md }

/**
 * 선택 칩. colorCode(목표 색)를 주면 목표 색 칩(tint + 색 막대)으로 그려요.
 * 색이 없으면: 기본 bg/subtle + text/secondary, 선택 시 action/secondary 바탕 + 1.5px 브랜드 테두리.
 */
@Composable
fun NChip(
    title: String,
    selected: Boolean = false,
    size: ChipSize = ChipSize.md,
    icon: String? = null,
    modifier: Modifier = Modifier,
    colorCode: String? = null,
    enabled: Boolean = true,
    action: () -> Unit,
) {
    if (colorCode != null) {
        GoalChip(title, colorCode = colorCode, size = size, selected = selected, enabled = enabled, modifier = modifier, onClick = action)
        return
    }
    val sm = size == ChipSize.sm
    val shape = RoundedCornerShape(if (sm) Radius.chipSm else Radius.chip)
    val fg = when {
        !enabled -> NC.disabledFg
        selected -> NC.actionSecondaryFg
        else -> NC.textSecondary
    }
    val bg = when {
        !enabled -> NC.disabledBg
        selected -> NC.actionSecondaryBg
        else -> NC.bgSubtle
    }
    Row(
        modifier = modifier
            .height(if (sm) 24.dp else 32.dp)
            .clip(shape)
            .background(bg, shape)
            .then(if (selected && enabled) Modifier.border(1.5.dp, NC.actionOutlineBorder, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = action)
            .padding(horizontal = if (sm) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (sm) 3.dp else 4.dp),
    ) {
        if (icon != null) SfImage(icon, size = if (sm) 12.dp else 14.dp, tint = fg)
        Text(title, style = if (sm) NType.labelSM else NType.labelMD, color = fg, maxLines = 1)
    }
}

/**
 * 목표 색 칩: bg goal/X/bg · 왼쪽 색 막대(MD 4×16 · SM 3×12) · 글자 goal/X/fg.
 * 선택 = 1.5px accent 테두리 · 비활성 = disabled 색 · onClose 를 주면 오른쪽 X.
 */
@Composable
fun GoalChip(
    title: String,
    colorCode: String?,
    size: ChipSize = ChipSize.md,
    selected: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val tone = goalTone(colorCode)
    val sm = size == ChipSize.sm
    val shape = RoundedCornerShape(if (sm) Radius.chipSm else Radius.chip)
    val bg = if (enabled) tone.bg else NC.disabledBg
    val fg = if (enabled) tone.fg else NC.disabledFg
    val bar = if (enabled) tone.accent else NC.disabledFg
    Row(
        modifier = modifier
            .height(if (sm) 24.dp else 32.dp)
            .clip(shape)
            .background(bg, shape)
            .then(if (selected && enabled) Modifier.border(1.5.dp, tone.accent, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = if (sm) 8.dp else 10.dp, end = if (sm) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (sm) 3.dp else 4.dp),
    ) {
        Box(
            Modifier
                .size(width = if (sm) 3.dp else 4.dp, height = if (sm) 12.dp else 16.dp)
                .background(bar, RoundedCornerShape(if (sm) 1.5.dp else 2.dp)),
        )
        Text(title, style = if (sm) NType.labelSM else NType.labelMD, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onClose != null) {
            Box(
                Modifier.size(if (sm) 16.dp else 20.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { NIcon.x.Icon(size = if (sm) 10.dp else 12.dp, tint = fg) }
        }
    }
}

// MARK: - QuickReply (radius 20 · padding 9/16 · 기본 bg/surface + 1px border/subtle · 선택 ai/tint-soft + 1.5px text/brand)

@Composable
fun QuickReply(title: String, selected: Boolean = false, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.quickReply)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) NC.aiTintSoft else NC.bgSurface, shape)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) NC.textBrand else NC.borderSubtle, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(title, style = NType.bodyMDMedium, color = if (selected) NC.textBrand else NC.textPrimary)
    }
}

// MARK: - AIBadge (스펙 6C: 28×28 r8 그라데이션 + 흰 sparkles 16 · 작게 20×20 r6 아이콘 12)

enum class AIBadgeSize(val box: Dp, val radius: Dp, val icon: Dp) {
    md(28.dp, 8.dp, 16.dp),
    sm(20.dp, 6.dp, 12.dp),
}

@Composable
fun AIBadge(size: AIBadgeSize = AIBadgeSize.md, icon: String = "sparkles", modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size.radius)
    Box(
        modifier.size(size.box).clip(shape).background(NC.aiGradient(), shape),
        contentAlignment = Alignment.Center,
    ) {
        SfImage(icon, size = size.icon, tint = Color.White)
    }
}

// MARK: - Tag / Badge (h24 px10 radius full · Label/SM)

enum class BadgeTone {
    brand, success, warning, danger, neutral, mint, info;

    val background: Color
        @Composable get() = when (this) {
            brand -> NC.actionSecondaryBg
            success -> NC.statusSuccessBg
            warning -> NC.bgSubtle
            danger -> NC.statusErrorBg
            neutral -> NC.bgSubtle
            mint -> NC.aiTint
            info -> NC.statusInfoBg
        }

    val foreground: Color
        @Composable get() = when (this) {
            brand -> NC.textBrand
            success -> NC.statusSuccessFg
            warning -> NC.textSecondary
            danger -> NC.statusErrorFg
            neutral -> NC.textSecondary
            mint -> NC.textBrand
            info -> NC.statusInfoFg
        }

    /** 점 색 (warning 은 늘 점을 찍어요) */
    val dot: Color
        @Composable get() = when (this) {
            warning -> NC.statusWarningFg
            mint -> NC.accentMint
            else -> foreground
        }
}

@Composable
fun NBadge(title: String, tone: BadgeTone = BadgeTone.neutral, icon: String? = null, modifier: Modifier = Modifier, dot: Boolean = false) {
    Row(
        modifier = modifier
            .height(24.dp)
            .background(tone.background, CircleShape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot || tone == BadgeTone.warning) Box(Modifier.size(6.dp).background(tone.dot, CircleShape))
        if (icon != null) SfImage(icon, size = 12.dp, tint = tone.foreground)
        Text(title, style = NType.labelSM, color = tone.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 스펙 이름 (Tag) — NBadge 와 같아요 */
@Composable
fun NTag(title: String, tone: BadgeTone = BadgeTone.neutral, dot: Boolean = false, icon: String? = null, modifier: Modifier = Modifier) =
    NBadge(title, tone = tone, icon = icon, modifier = modifier, dot = dot)

// MARK: - Event (캘린더 블록: tint + 왼쪽 색 막대)

enum class EventState { scheduled, done, incomplete, proposal, proposalActive }

/** 이벤트 블록 바깥 모양만 (배경 · 테두리 · 왼쪽 4px 막대 · 그림자). 안 내용은 직접 그릴 때 */
@Composable
fun Modifier.eventBlock(tone: GoalTone, state: EventState = EventState.scheduled, radius: Dp = Radius.event): Modifier {
    val shape = RoundedCornerShape(radius)
    val bg = when (state) {
        EventState.incomplete, EventState.proposal -> tone.bg.copy(alpha = 0.55f)
        else -> tone.bg
    }
    var m = this
    if (state == EventState.proposalActive) m = m.primaryShadow(shape)
    m = m.clip(shape).background(bg, shape)
    m = when (state) {
        EventState.incomplete -> m.border(1.dp, tone.accent, shape)
        EventState.proposal, EventState.proposalActive -> m.border(1.5.dp, tone.accent, shape)
        else -> m
    }
    val accent = tone.accent
    // 왼쪽 4px 색 막대 (모서리는 위 clip 이 잘라요)
    return m.drawWithContent {
        drawContent()
        drawRect(accent, size = Size(4.dp.toPx(), size.height))
    }
}

/** 캘린더 이벤트 블록 (제목 Label/SM goal fg · 시간 Caption/SM text/secondary) */
@Composable
fun EventBlock(
    title: String,
    tone: GoalTone,
    modifier: Modifier = Modifier,
    time: String? = null,
    state: EventState = EventState.scheduled,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .eventBlock(tone, state)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = 12.dp, top = 6.dp, end = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            when (state) {
                EventState.done -> Box(Modifier.size(14.dp).background(tone.accent, CircleShape), contentAlignment = Alignment.Center) {
                    NIcon.check.Icon(size = 10.dp, tint = Color.White)
                }
                EventState.proposal, EventState.proposalActive -> NIcon.sparkles.Icon(size = 14.dp, tint = tone.accent)
                else -> {}
            }
            Text(title, style = NType.labelSM, color = tone.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (time != null) Text(time, style = NType.captionSM, color = NC.textSecondary, maxLines = 1)
    }
}

/** 고정 일정 (점심 · 수면 등): bg/subtle · 1px border/subtle · radius 10 · 시계 14 + Caption/MD text/secondary */
@Composable
fun FixedEventBlock(title: String, modifier: Modifier = Modifier, time: String? = null, icon: String = "clock") {
    val shape = RoundedCornerShape(Radius.eventFixed)
    Row(
        modifier
            .clip(shape)
            .background(NC.bgSubtle, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SfImage(icon, size = 14.dp, tint = NC.textSecondary)
        Text(title, style = NType.captionMD, color = NC.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (time != null) Text(time, style = NType.captionSM, color = NC.textTertiary, maxLines = 1)
    }
}

@Preview(showBackground = true)
@Composable
private fun CardPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.background(NC.canvas).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardHeader("루틴", aside = "3개")
                Text("카드 내용", style = NType.bodyMD, color = NC.textSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NChip("아침", selected = true) {}
                NChip("저녁") {}
                GoalChip("운동", colorCode = "#4BC47C", selected = true)
                GoalChip("독서", colorCode = "#8468F0", size = ChipSize.sm, onClose = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NBadge("AI가 설계한 목표", tone = BadgeTone.brand, icon = "sparkles")
                NBadge("루틴", tone = BadgeTone.success)
                NBadge("마감 임박", tone = BadgeTone.warning)
            }
            Column(Modifier.width(160.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EventBlock("러닝", tone = goalTone("#4BC47C"), time = "07:00–08:00")
                EventBlock("러닝", tone = goalTone("#4BC47C"), time = "07:00–08:00", state = EventState.done)
                EventBlock("독서", tone = goalTone("#8468F0"), time = "21:00", state = EventState.incomplete)
                EventBlock("AI 제안", tone = goalTone("#3DBFE6"), time = "10:00", state = EventState.proposal)
                EventBlock("AI 제안", tone = goalTone("#3DBFE6"), time = "10:00", state = EventState.proposalActive)
                FixedEventBlock("점심", time = "12:00–13:00")
            }
        }
    }
}
