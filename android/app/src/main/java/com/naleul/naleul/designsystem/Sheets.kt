// designsystem/Sheets.kt
//
// iOS DesignSystem/Components/Sheets.swift — 웹 Modal · ConfirmDialog 를 Android 방식으로.
//  - Modal / Popover   → SheetScaffold (아래에서 올라오는 시트, 머티리얼 ModalBottomSheet)
//  - ConfirmDialog     → ConfirmDialog (머티리얼 AlertDialog)
//
// iOS 는 .sheet(item: $editing) { … } 로 "값이 있으면 시트가 뜬다"를 표현해요. Compose 는 그냥 if 로 그려요:
//
//   editing?.let { item ->
//       SheetScaffold(
//           onDismiss = { editing = null },
//           title = "시간 바꾸기", description = "…",
//           footer = { close ->
//               NButton("취소", variant = NButtonVariant.secondary) { close() }
//               NButton("저장") { save(); close() }
//           },
//       ) {
//           내용
//       }
//   }
//
// footer 의 close() 를 부르면 시트가 아래로 내려가는 애니메이션이 끝난 뒤 onDismiss 가 불려요.
// (iOS @Environment(\.dismiss) 의 dismiss() 와 같은 역할)

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.naleul.naleul.designsystem

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.Layout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.naleul.naleul.core.state.ToastCenter
import com.naleul.naleul.core.state.ToastHost
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 시트를 코드로 닫을 때 쓰는 함수 (애니메이션 후 onDismiss 호출) */
typealias SheetClose = () -> Unit

/**
 * 시트 기본 틀: 제목 · 설명 · 닫기(X) · 스크롤되는 내용 · 아래 버튼 영역
 *
 * @param dismissible 닫기 버튼 · 아래로 끌어 닫기 허용 (저장 중엔 false)
 * @param fullHeight true 면 처음부터 화면 높이만큼 (iOS detents: [.large]). false 면 내용 높이 → 끌어 올리면 커져요
 * @param scrollable 내용을 세로 스크롤로 감쌀지 (안에 LazyColumn 이 있으면 false)
 */
@Composable
fun SheetScaffold(
    onDismiss: () -> Unit,
    title: String? = null,
    description: String? = null,
    dismissible: Boolean = true,
    fullHeight: Boolean = false,
    scrollable: Boolean = true,
    footer: (@Composable RowScope.(close: SheetClose) -> Unit)? = null,
    content: @Composable ColumnScope.(close: SheetClose) -> Unit,
) {
    NSheet(onDismiss = onDismiss, dismissible = dismissible, fullHeight = fullHeight) { close ->
        if (title != null || dismissible) {
            NSheetHeader(
                title = title, subtitle = description,
                onClose = if (dismissible) ({
                    com.naleul.naleul.core.analytics.AnalyticsService.sheetCloseHint = "x_button"
                    close()
                }) else null,
            )
        }
        // 스펙 13.2: 내용 좌우 24 · 머리 아래 20
        val scroll = rememberScrollState()
        Column(
            Modifier
                .weight(1f, fill = false)
                .then(if (scrollable) Modifier.verticalScroll(scroll) else Modifier)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = if (footer != null) 8.dp else 24.dp),
        ) { content(close) }
        if (footer != null) {
            // 스펙 13.2: 아래 버튼 영역은 붙박이 — 16/24 · 내용이 스크롤될 때만 위 1px border/soft · 버튼은 LG 꽉 차게 (2개면 나란히 8)
            val scrolls = scrollable && (scroll.canScrollForward || scroll.canScrollBackward)
            if (scrolls) Box(Modifier.fillMaxWidth().height(1.dp).background(NC.borderSoft))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val rowScope = this
                SheetFooterRow {
                    CompositionLocalProvider(LocalSheetFooter provides true) { rowScope.footer(close) }
                }
            }
        }
    }
}

/**
 * 스펙 13.2 공용 시트 머리: (뒤로) · 제목 Heading/H3 왼쪽 · 설명 Body/SM text/secondary 4 아래 · 오른쪽 닫기 X (MD).
 * 좌우 24 (아이콘 버튼은 누르는 칸 여백만큼 16). SheetScaffold · 직접 그리는 시트(Task 추가 · 로그인) 가 같이 써요.
 */
@Composable
fun NSheetHeader(
    title: String?,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    closeLabel: String = "닫기",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(start = if (onBack != null) 16.dp else 24.dp, end = if (onClose != null) 16.dp else 24.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(if (onBack != null) 4.dp else 16.dp),
    ) {
        if (onBack != null) NIconButton("chevron.left", size = 20.dp, color = NC.textPrimary, label = "뒤로", action = onBack)
        Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (title != null) Text(title, style = NType.h3, color = NC.textPrimary)
            if (subtitle != null) Text(subtitle, style = NType.bodySM, color = NC.textSecondary)
        }
        if (onClose != null) NIconButton("xmark", size = 20.dp, color = NC.textSecondary, label = closeLabel, action = onClose)
    }
}

/** 시트 손잡이 (스펙 13.2) */
@Composable
fun SheetGrabber() {
    Box(
        Modifier
            .padding(top = 16.dp, bottom = 12.dp)
            .size(width = 36.dp, height = 4.dp)
            .background(NC.borderSubtle, RoundedCornerShape(2.dp))
    )
}

/** 시트 아래 버튼 영역 안이면 true — NButton 이 LG · 꽉 차게로 바뀌어요 */
val LocalSheetFooter = staticCompositionLocalOf { false }

private object SheetFooterFillMarker

/** 시트 아래 버튼 영역에서 남은 폭을 나눠 가질 칸 표시 */
internal val SheetFooterFill: Modifier = object : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = SheetFooterFillMarker
}

/** 표시된 칸 — 부르는 쪽이 RowScope.weight 를 겹쳐 써서 표시가 덮였어도 (weight = 나눠 가지라는 뜻) 채움으로 */
private fun isFooterFill(parentData: Any?): Boolean =
    parentData === SheetFooterFillMarker || (parentData != null && parentData.javaClass.simpleName == "RowColumnParentData")

/** 버튼(표시된 칸)은 남은 폭을 똑같이 나누고, 나머지(지우기 글자 등)는 제 크기. 사이 8 */
@Composable
private fun SheetFooterRow(content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, c ->
        val gap = 8.dp.roundToPx()
        val width = c.maxWidth
        val loose = Constraints(maxWidth = width, maxHeight = c.maxHeight)
        val placed = arrayOfNulls<Placeable>(measurables.size)
        var used = gap * (measurables.size - 1).coerceAtLeast(0)
        var fillCount = 0
        measurables.forEachIndexed { i, m ->
            if (isFooterFill(m.parentData)) fillCount++ else placed[i] = m.measure(loose).also { used += it.width }
        }
        val each = if (fillCount > 0) ((width - used).coerceAtLeast(0) / fillCount) else 0
        measurables.forEachIndexed { i, m ->
            if (placed[i] == null) placed[i] = m.measure(Constraints(minWidth = each, maxWidth = each, maxHeight = c.maxHeight))
        }
        val h = placed.maxOfOrNull { it?.height ?: 0 } ?: 0
        layout(width, h) {
            var x = 0
            for (p in placed) {
                if (p == null) continue
                p.placeRelative(x, (h - p.height) / 2)
                x += p.width + gap
            }
        }
    }
}

/** 틀 없이 시트만 (내용을 전부 직접 그릴 때) */
@Composable
fun NSheet(
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    fullHeight: Boolean = false,
    content: @Composable ColumnScope.(close: SheetClose) -> Unit,
) {
    val state: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { dismissible || it != SheetValue.Hidden },
    )
    val scope = rememberCoroutineScope()
    val close: SheetClose = {
        scope.launch { state.hide() }.invokeOnCompletion { if (!state.isVisible) onDismiss() }
    }
    // 이 시트가 몇 번째로 열린 시트인지 (토스트를 가장 위 시트 안에 그리려고)
    val level = remember { ToastCenter.sheetDepth + 1 }
    DisposableEffect(Unit) {
        ToastCenter.sheetDepth += 1
        onDispose { ToastCenter.sheetDepth -= 1 }
    }
    ModalBottomSheet(
        onDismissRequest = { if (dismissible) onDismiss() },
        sheetState = state,
        containerColor = NC.bgSurface,
        contentColor = NC.textPrimary,
        scrimColor = NC.bgOverlay,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        // 스펙 13.2: 손잡이 36×4 · radius 2 · border/subtle — 위 16 · 아래 12 (윗변에 붙지 않게)
        dragHandle = { SheetGrabber() },
    ) {
        Box {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (fullHeight) Modifier.fillMaxHeight(0.94f) else Modifier)
                    .imePadding()
                    .navigationBarsPadding(),
            ) { content(close) }
            ToastHost(level = level, bottomPadding = 16.dp, modifier = Modifier.matchParentSize())
        }
    }
}

// MARK: - 확인창 ("정말 ~할까요?")

enum class ConfirmTone { primary, danger }

/**
 * Component_ver.2 Dialog: 폭 340 · padding 20 · gap 16 · radius 16 · glass (glass/bg + 1px glass/border + 그림자).
 * 제목 Heading/H3 · 설명 Body/MD text/secondary · 버튼 줄 gap 8.
 */
@Composable
fun NDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String? = null,
    dismissible: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible, usePlatformDefaultWidth = false),
    ) {
        val shape = RoundedCornerShape(Radius.dialog)
        // 안드로이드 Dialog 창은 뒤를 흐리게(blur) 못 해서, glass/bg 를 surface 위에 겹쳐 거의 불투명하게 그려요
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .popShadow(shape)
                .clip(shape)
                .background(NC.bgSurface, shape)
                .background(NC.glassBg, shape)
                .border(1.dp, NC.glassBorder, shape),
        ) {
            // 스펙 13.2: 시트와 같은 머리 · 내용 · 아래 버튼 여백 (좌우 24)
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = NType.h3, color = NC.textPrimary)
                if (message != null) Text(message, style = NType.bodyMD, color = NC.textSecondary)
            }
            if (content != null) {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) { actions() }
        }
    }
}

/** 웹 ConfirmDialog (iOS .confirmDialog(isPresented:…)). isPresented 가 true 일 때만 떠요 */
@Composable
fun ConfirmDialog(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    title: String,
    message: String? = null,
    confirmLabel: String = "확인",
    cancelLabel: String = "취소",
    tone: ConfirmTone = ConfirmTone.primary,
    /** 확인 없이 닫힐 때 (취소 버튼 · 바깥 탭 · 뒤로가기) — 이벤트를 남길 때만 넘겨요 */
    onCancel: (() -> Unit)? = null,
    onConfirm: () -> Unit,
) {
    if (!isPresented) return
    NDialog(
        onDismissRequest = { onCancel?.invoke(); onDismiss() },
        title = title,
        message = message,
    ) {
        // 스펙 13.2: Secondary + Primary/Danger 나란히 (LG)
        NButton(cancelLabel, variant = NButtonVariant.secondary, size = NButtonSize.lg, modifier = Modifier.weight(1f)) {
            onCancel?.invoke(); onDismiss()
        }
        NButton(
            confirmLabel,
            variant = if (tone == ConfirmTone.danger) NButtonVariant.danger else NButtonVariant.primary,
            size = NButtonSize.lg,
            modifier = Modifier.weight(1f),
        ) { onDismiss(); onConfirm() }
    }
}

/** 시트 안 아래 여백 등 */
@Composable
fun SheetSpacer() = Spacer(Modifier.padding(4.dp))
