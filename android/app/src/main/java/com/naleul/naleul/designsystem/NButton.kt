// designsystem/NButton.kt
//
// Component_ver.2 Button — "Style A Soft Flat". Type × Size × State × Icon(None/Left/Right)
//
//   NButton("저장", variant = NButtonVariant.primary) { save() }
//   NButton("다시 배치", variant = NButtonVariant.secondary, size = NButtonSize.sm, icon = "arrow.clockwise", loading = isSaving) { ... }
//   NButton("다음", icon = "chevron.right", iconPosition = NIconPosition.right) { ... }
//   NButton("AI로 배치하기", variant = NButtonVariant.ai, icon = "sparkles") { ... }
//   NIconButton("xmark", label = "닫기") { close() }
//   Row(Modifier.nButton(NButtonVariant.primary, NButtonSize.lg, fullWidth = true) { ... }) { 직접 만든 라벨 }
//
// 타입: primary(채운 브랜드) · secondary(연한 틸 + 테두리) · outline(흰 바탕 + 브랜드 테두리) · ghost(투명)
//       · danger(빨강) · ai(그라데이션). 예전 brand 는 primary 와 같아요.

package com.naleul.naleul.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class NButtonVariant {
    /** 브랜드 채움 (action/primary) */
    primary,
    /** 예전 이름 — primary 와 같아요 */
    brand,
    /** 연한 틸 바탕 + 테두리 (action/secondary) */
    secondary,
    /** 흰 바탕 + 브랜드 테두리 (action/outline) */
    outline,
    /** 투명 (text/brand) */
    ghost,
    /** 빨간 채움 (action/danger) */
    danger,
    /** AI 그라데이션 */
    ai,
}

enum class NIconPosition { left, right }

/** LG h48 px20 · MD h40 px16 · SM h32 px12. 아이콘 쪽 패딩은 base−4 */
enum class NButtonSize(val height: Dp, val hPadding: Dp, val fontSize: Int, val radius: Dp) {
    sm(32.dp, 12.dp, 13, 10.dp),
    md(40.dp, 16.dp, 13, 12.dp),
    lg(48.dp, 20.dp, 14, 12.dp);

    /** 아이콘–글자 간격 (SM 4 · 나머지 6) */
    val spacing: Dp get() = if (this == sm) 4.dp else 6.dp
    /** 아이콘이 있는 쪽 패딩 (LG16 / MD12 / SM10) */
    val iconSidePadding: Dp get() = if (this == sm) 10.dp else hPadding - 4.dp
    val iconSize: Dp get() = if (this == sm) 14.dp else 16.dp
    val textStyle: TextStyle get() = if (this == lg) NType.bodyMDMedium else NType.bodySMMedium
}

@Composable
private fun nButtonForeground(variant: NButtonVariant, enabled: Boolean = true): Color = when {
    !enabled -> NC.disabledFg
    else -> when (variant) {
        NButtonVariant.primary, NButtonVariant.brand -> NC.actionPrimaryFg
        NButtonVariant.danger -> NC.actionDangerFg
        NButtonVariant.ai -> Color.White
        NButtonVariant.secondary -> NC.actionSecondaryFg
        NButtonVariant.outline, NButtonVariant.ghost -> NC.textBrand
    }
}

@Composable
private fun nButtonBackground(variant: NButtonVariant, pressed: Boolean): Color = when (variant) {
    NButtonVariant.primary, NButtonVariant.brand -> if (pressed) NC.actionPrimaryPressed else NC.actionPrimary
    NButtonVariant.secondary -> if (pressed) NC.actionSecondaryBgPressed else NC.actionSecondaryBg
    NButtonVariant.outline -> if (pressed) NC.ghostPressed else NC.bgSurface
    NButtonVariant.ghost -> if (pressed) NC.ghostPressed else Color.Transparent
    NButtonVariant.danger -> if (pressed) NC.actionDangerPressed else NC.actionDanger
    NButtonVariant.ai -> Color.Transparent
}

/**
 * 버튼 모양을 아무 Row 에나 입혀요 (iOS .buttonStyle(.n(variant, size, fullWidth:)))
 * 안의 글자색은 nButtonContentColor(variant, enabled) 로 맞춰 주세요.
 * @param disabledLook false 면 눌리지 않아도 비활성 회색으로 칠하지 않아요 (로딩 중)
 */
@Composable
fun Modifier.nButton(
    variant: NButtonVariant = NButtonVariant.primary,
    size: NButtonSize = NButtonSize.md,
    fullWidth: Boolean = false,
    enabled: Boolean = true,
    disabledLook: Boolean = !enabled,
    startPadding: Dp = size.hPadding,
    endPadding: Dp = size.hPadding,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(size.radius)
    var m = this.then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
    if (!disabledLook && (variant == NButtonVariant.primary || variant == NButtonVariant.brand)) m = m.primaryShadow(shape)
    // AI 버튼: 0 4 12 #087F8E40 그림자, 눌렀을 땐 없음 (스펙 6A)
    if (!disabledLook && variant == NButtonVariant.ai && !pressed) m = m.primaryShadow(shape)
    m = m.height(size.height).clip(shape)
    m = when {
        disabledLook -> m.background(NC.disabledBg, shape)
        variant == NButtonVariant.ai -> m.background(NC.aiGradient(pressed), shape)
        else -> m.background(nButtonBackground(variant, pressed), shape)
    }
    if (!disabledLook) {
        when (variant) {
            NButtonVariant.secondary -> m = m.border(1.dp, NC.actionSecondaryBorder, shape)
            NButtonVariant.outline -> m = m.border(1.dp, NC.actionOutlineBorder, shape)
            else -> {}
        }
    }
    return m
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(start = startPadding, end = endPadding)
}

@Composable
fun nButtonContentColor(variant: NButtonVariant, enabled: Boolean = true): Color = nButtonForeground(variant, enabled)

/** 글자(+아이콘) 버튼. loading 이면 스피너가 돌고 눌리지 않아요 */
@Composable
fun NButton(
    title: String,
    variant: NButtonVariant = NButtonVariant.primary,
    size: NButtonSize = NButtonSize.md,
    icon: String? = null,
    loading: Boolean = false,
    fullWidth: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    iconPosition: NIconPosition = NIconPosition.left,
    action: () -> Unit,
) {
    // 스펙 13.2: 시트 아래 버튼 영역이면 LG · 꽉 차게 (나란히면 폭을 나눠요)
    if (LocalSheetFooter.current) {
        androidx.compose.runtime.CompositionLocalProvider(LocalSheetFooter provides false) {
            NButton(
                title, variant = variant, size = NButtonSize.lg, icon = icon, loading = loading, fullWidth = true,
                enabled = enabled, modifier = modifier.then(SheetFooterFill), iconPosition = iconPosition, action = action,
            )
        }
        return
    }
    val fg = nButtonForeground(variant, enabled)
    val hasIcon = icon != null || loading
    // 아이콘이 있는 쪽은 패딩을 4 줄여요 (LG16 / MD12 / SM10)
    val startPad = if (hasIcon && iconPosition == NIconPosition.left) size.iconSidePadding else size.hPadding
    val endPad = if (hasIcon && iconPosition == NIconPosition.right) size.iconSidePadding else size.hPadding
    Row(
        modifier = modifier.nButton(
            variant, size, fullWidth, enabled = enabled && !loading, disabledLook = !enabled,
            startPadding = startPad, endPadding = endPad, onClick = action,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(size.spacing, Alignment.CenterHorizontally),
    ) {
        val iconView: @Composable () -> Unit = {
            if (loading) {
                CircularProgressIndicator(color = fg, strokeWidth = 2.dp, modifier = Modifier.size(size.iconSize))
            } else if (icon != null) {
                SfImage(icon, size = size.iconSize, tint = fg)
            }
        }
        if (hasIcon && iconPosition == NIconPosition.left) iconView()
        Text(title, style = size.textStyle, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (hasIcon && iconPosition == NIconPosition.right) iconView()
    }
}

/** IconButton: MD 32×32 (아이콘 18) · SM 28×28 (아이콘 16) · radius 6 · 누르면 bg/subtle · 아이콘 text/secondary */
@Composable
fun NIconButton(
    systemName: String,
    size: Dp = 16.dp,
    color: Color = NC.textSecondary,
    label: String = "",
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    action: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(Radius.iconBtn)
    Box(
        modifier = modifier
            .size(size + 14.dp)
            .clip(shape)
            .background(if (pressed) NC.bgSubtle else Color.Transparent, shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = action)
            .semantics { if (label.isNotEmpty()) contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        SfImage(systemName, size = size, tint = if (enabled) color else NC.disabledFg)
    }
}

@Preview(showBackground = true)
@Composable
private fun NButtonPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.background(NC.canvas).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NButton("저장", variant = NButtonVariant.primary) {}
            NButton("AI로 배치하기", variant = NButtonVariant.ai, icon = "sparkles") {}
            NButton("보조", variant = NButtonVariant.secondary) {}
            NButton("취소", variant = NButtonVariant.outline) {}
            NButton("더보기", variant = NButtonVariant.ghost, size = NButtonSize.sm, icon = "chevron.right", iconPosition = NIconPosition.right) {}
            NButton("삭제", variant = NButtonVariant.danger, loading = true) {}
            NButton("비활성", enabled = false) {}
            NButton("Task 추가", size = NButtonSize.lg, icon = "plus", fullWidth = true) {}
            NIconButton("xmark", label = "닫기") {}
        }
    }
}
