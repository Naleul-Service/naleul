// designsystem/Layout.kt
//
// iOS DesignSystem/Components/Layout.swift — 화면 공통 틀: 페이지 제목 · 로딩 · 빈 화면 · 에러 · 줄바꿈 배치 · 진행 막대.
//
//   PageHeader(title = "목표", description = "…") { NButton("추가") {} }
//   LoadState(phase = vm.phase, retry = { vm.load() }) { data -> ... }   // 로딩/에러 처리를 한 번에
//   FlowLayout(spacing = 8.dp) { items.forEach { NChip(it) {} } }        // iOS FlowLayout (웹 flex-wrap)

package com.naleul.naleul.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// MARK: - 페이지 제목 (웹 PageHeader)

@Composable
fun PageHeader(
    title: String,
    breadcrumb: String? = null,
    description: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (breadcrumb != null) Text(breadcrumb, style = NType.bodySM, color = NC.textTertiary)
            Text(title, style = NType.h2, color = NC.textPrimary)
            if (description != null) Text(description, style = NType.bodyMD, color = NC.textSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

// MARK: - 불러오기 상태

/** 화면 데이터 상태. ViewModel 에서 var phase by mutableStateOf<LoadPhase<GoalCategory>>(LoadPhase.Loading) 처럼 써요 */
sealed class LoadPhase<out T> {
    object Loading : LoadPhase<Nothing>()
    data class Loaded<T>(override val value: T) : LoadPhase<T>()
    data class Failed(val message: String) : LoadPhase<Nothing>()

    /** 불러온 값 (아직이거나 실패면 null) */
    open val value: T? get() = null
    val isLoading: Boolean get() = this is Loading
}

/** phase 에 따라 로딩 · 에러(다시 시도) · 내용을 보여줘요 */
@Composable
fun <T> LoadState(
    phase: LoadPhase<T>,
    retry: (suspend () -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    when (phase) {
        is LoadPhase.Loading -> LoadingView()
        is LoadPhase.Failed -> ErrorView(message = phase.message, retry = retry)
        is LoadPhase.Loaded -> content(phase.value)
    }
}

@Composable
fun LoadingView(minHeight: Dp = 240.dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().defaultMinSize(minHeight = minHeight), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = NC.actionPrimary, trackColor = NC.progressTrack, strokeWidth = 2.5.dp)
    }
}

@Composable
fun ErrorView(message: String, retry: (suspend () -> Unit)? = null, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    Column(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 240.dp).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyStateIcon("exclamationmark.circle")
        Text(message, style = NType.bodySM, color = NC.textSecondary, textAlign = TextAlign.Center)
        if (retry != null) {
            NButton("다시 시도", variant = NButtonVariant.outline, size = NButtonSize.sm) { scope.launch { retry() } }
        }
    }
}

/** EmptyState 아이콘: 지름 48 bg/subtle 원 + 아이콘 24 text/tertiary */
@Composable
fun EmptyStateIcon(icon: String, modifier: Modifier = Modifier) {
    Box(modifier.size(48.dp).background(NC.bgSubtle, CircleShape), contentAlignment = Alignment.Center) {
        SfImage(icon, size = 24.dp, tint = NC.textTertiary)
    }
}

/** EmptyState (padding 32 · gap 12 · 가운데): 아이콘 원 · 제목 Body/LG·Medium · 설명 Body/SM text/secondary · 버튼 */
@Composable
fun EmptyState(
    title: String,
    icon: String? = null,
    description: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) EmptyStateIcon(icon)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = NType.bodyLGMedium, color = NC.textPrimary, textAlign = TextAlign.Center)
            if (description != null) Text(description, style = NType.bodySM, color = NC.textSecondary, textAlign = TextAlign.Center)
        }
        if (action != null) Box(Modifier.padding(top = 4.dp)) { action() }
    }
}

/** 비어 있을 때 카드 (Card 안에 EmptyState) */
@Composable
fun EmptyStateCard(
    title: String,
    icon: String? = null,
    description: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxWidth().defaultMinSize(minHeight = 220.dp).card(), contentAlignment = Alignment.Center) {
        EmptyState(title = title, icon = icon, description = description, action = action)
    }
}

// MARK: - 진행 막대

@Composable
fun ProgressBar(
    value: Double,
    color: Color = NC.progressFill,
    track: Color = NC.progressTrack,
    height: Dp = 6.dp,
    modifier: Modifier = Modifier,
) {
    // 0/0 같은 NaN 이 오면 fillMaxWidth(NaN) 가 죽어요 → 0 으로
    val safe = if (value.isNaN()) 0.0 else value.coerceIn(0.0, 1.0)
    val animated by animateFloatAsState(safe.toFloat(), tween(250), label = "progress")
    Box(modifier.fillMaxWidth().height(height).background(track, CircleShape)) {
        Box(Modifier.fillMaxWidth(animated.coerceIn(0f, 1f)).fillMaxHeight().background(color, CircleShape))
    }
}

// MARK: - 줄바꿈 배치 (웹 flex-wrap gap-2)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowLayout(
    spacing: Dp = 8.dp,
    lineSpacing: Dp? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(lineSpacing ?: spacing),
    ) { content() }
}

// MARK: - 섹션 제목 (카드 밖 작은 회색 제목)

@Composable
fun SectionTitle(title: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = NType.labelMD, color = NC.textSecondary)
        Spacer(Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = NType.captionMD, color = NC.textTertiary)
    }
}

// MARK: - 페이지 좌우 여백 (Figma: 모든 최상위 페이지 20)

/** 페이지 좌우 여백 */
val PageGutter = 20.dp

/** 페이지 좌우 20 여백 — 스크롤 컨테이너 "안쪽" 내용에 써요 (바깥에 겹쳐 쓰지 않기) */
fun Modifier.pageGutter(): Modifier = this.padding(horizontal = PageGutter)

// MARK: - 맨 위 헤더 (Figma MobileHeader: h48 · bg/surface · 아래 1dp border/soft · 좌우 20 · 왼쪽 워드마크 · 오른쪽 아이콘 버튼들)

@Composable
fun MobileHeader(modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    val divider = NC.borderSoft
    Row(
        modifier
            .fillMaxWidth()
            .background(NC.bgSurface)
            // 아래 1dp border/soft (스펙 8.4)
            .drawBehind { drawRect(divider, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx())) }
            .height(48.dp)
            .padding(horizontal = PageGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wordmark(height = 24.dp)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { actions() }
    }
}

// MARK: - 밑줄 탭 (목표 상세 계획 · 진행 · 회고 / 생활 습관 오늘 · 진행 · 할 일)

@Composable
fun <T> UnderlineTabs(
    selection: T,
    onSelectionChange: (T) -> Unit,
    items: List<Pair<T, String>>,
    modifier: Modifier = Modifier,
) {
    val lineColor = NC.borderSoft
    Row(
        modifier
            .fillMaxWidth()
            .background(NC.bgSurface)
            .drawBehind {
                drawRect(lineColor, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
            },
    ) {
        for ((value, label) in items) {
            val on = value == selection
            Column(
                Modifier
                    .weight(1f)
                    .clickable(role = androidx.compose.ui.semantics.Role.Tab) { if (!on) onSelectionChange(value) }
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(label, style = if (on) NType.labelLG else NType.bodyMDMedium, color = if (on) NC.textPrimary else NC.textTertiary)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .height(2.dp)
                        .background(if (on) NC.textPrimary else Color.Transparent, CircleShape),
                )
            }
        }
    }
}

// MARK: - 화면 배경

/** 탭 화면 공통 배경 (bg-canvas) */
@Composable
fun Modifier.canvasBackground(): Modifier = this.background(NC.bgCanvas)

/** 세로 스크롤 화면 기본 틀 (iOS ScrollView { VStack(spacing:) { … }.padding(16) }) */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = 16.dp,
    horizontalPadding: Dp = PageGutter,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

// MARK: - 제목 있는 카드 (웹 pattern/ui/Section.tsx)

/** Card + 제목(17 bold) + 오른쪽 보조. 나의 패턴 · 목표 상세 섹션에서 써요 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    aside: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().card().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = NType.h4, color = NC.textPrimary)
            Spacer(Modifier.weight(1f))
            CompositionLocalProvider(LocalTextStyle provides NType.bodySM, LocalContentColor provides NC.textTertiary) { aside() }
        }
        content()
    }
}

/** 기록이 부족할 때 (웹 NotEnough) */
@Composable
fun NotEnoughView(text: String = "기록이 쌓이면 보여드려요", modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().defaultMinSize(minHeight = 120.dp), contentAlignment = Alignment.Center) {
        Text(text, style = NType.bodyMD, color = NC.textTertiary, textAlign = TextAlign.Center)
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun LayoutPreview() {
    NaleulTheme(dark = false) {
        ScreenColumn(Modifier.canvasBackground()) {
            PageHeader(title = "목표", description = "AI와 함께 세운 목표를 관리해요") {
                NButton("추가", size = NButtonSize.sm, icon = "plus") {}
            }
            ProgressBar(value = 0.62)
            FlowLayout {
                listOf("운동", "공부", "독서", "건강", "커리어", "취미").forEach { NChip(it) {} }
            }
            EmptyStateCard(title = "아직 목표가 없어요", icon = "target", description = "AI와 대화하며 첫 목표를 세워 보세요") {
                NButton("목표 만들기", variant = NButtonVariant.outline, size = NButtonSize.sm) {}
            }
            ErrorView(message = "불러오지 못했어요.", retry = {})
            SectionCard("섹션", aside = { Text("보조") }) { NotEnoughView() }
            Row { Spacer(Modifier.width(1.dp)) }
        }
    }
}
