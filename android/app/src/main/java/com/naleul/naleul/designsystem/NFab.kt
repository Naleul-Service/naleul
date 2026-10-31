package com.naleul.naleul.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** 스펙 13.6: 앱 전체 떠 있는 버튼 — 56 원 · 아이콘 22 (캘린더 + 버튼 기준) · primaryShadow */
object FabMetrics {
    val size = 56.dp
    val icon = 22.dp
}

/** primary = 청록 채움 · ai = AI 그라데이션 · surface = 흰 바탕 + 연한 테두리 (예: 배치할 Task 없음) */
enum class NFabStyle { primary, ai, surface }

/** 모양만 (누르기는 바깥 — NMenu 트리거 · 누름 효과 래퍼 안에서 써요) */
@Composable
fun NFabFace(
    icon: String,
    modifier: Modifier = Modifier,
    style: NFabStyle = NFabStyle.primary,
    loading: Boolean = false,
) {
    val ai = NC.aiGradient()
    val bg = when (style) {
        NFabStyle.primary -> Modifier.background(NC.actionPrimary, CircleShape)
        NFabStyle.ai -> Modifier.background(ai, CircleShape)
        NFabStyle.surface -> Modifier.background(NC.bgSurface, CircleShape).border(1.dp, NC.borderSubtle, CircleShape)
    }
    val fg = when (style) {
        NFabStyle.primary -> NC.actionPrimaryFg
        NFabStyle.ai -> Color.White
        NFabStyle.surface -> NC.ink4
    }
    Box(
        modifier
            .size(FabMetrics.size)
            .primaryShadow(CircleShape)
            .clip(CircleShape)
            .then(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = fg, strokeWidth = 2.dp, modifier = Modifier.size(FabMetrics.icon))
        } else {
            SfImage(icon, size = FabMetrics.icon, tint = fg)
        }
    }
}

/** 누를 수 있는 떠 있는 버튼 (누르면 0.92 로 살짝 작아지고 진동). badge 는 오른쪽 위에 얹어요 */
@Composable
fun NFab(
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    style: NFabStyle = NFabStyle.primary,
    loading: Boolean = false,
    enabled: Boolean = true,
    badge: (@Composable BoxScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, tween(150), label = "nfab-press")
    Box(modifier.scale(scale)) {
        NFabFace(
            icon = icon, style = style, loading = loading,
            modifier = Modifier
                .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading, role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .semantics { contentDescription = label },
        )
        if (badge != null) badge()
    }
}
