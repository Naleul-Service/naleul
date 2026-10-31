// feature/pattern/PatternHeatmapCard.kt
//
// iOS Features/Pattern/PatternHeatmapCard.swift (웹 features/pattern/ui/Heatmap.tsx)
// 언제 가장 잘 실행하나요? — 요일 × 시간(6~23시)에 실제로 한 횟수 표.
// 계획한 시간과 상관없이 그 시간대에 Task · 루틴을 했으면 +1. 가장 많이 한 칸이 가장 진하고, 한 게 없으면 색이 없어요.
// 웹은 가로(시간 18칸) × 세로(요일 7줄)지만, 휴대폰 세로 화면에서는 18칸이 너무 좁아 숫자가 안 읽혀요.
// 그래서 표를 돌렸어요: 가로 = 요일 7칸, 세로 = 시간 줄. 칸이 넓어져서 숫자를 그대로 읽을 수 있어요.
// 골든 타임 · 취약 시간 요약을 표보다 먼저 보여줘요 (한눈에 결론 → 자세한 표).
// 칸을 누르면 그 칸이 테두리로 표시되고, 위에 자세한 값이 떠요.

package com.naleul.naleul.feature.pattern

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.delay

@Composable
fun PatternHeatmapCard(heatmap: PatternHeatmap?, modifier: Modifier = Modifier) {
    var picked by remember { mutableStateOf<String?>(null) }
    var pickedKey by remember { mutableStateOf<String?>(null) }
    /** 고른 칸의 자리 (표 기준 좌표) — 말풍선을 그 위에 띄워요 */
    var anchor by remember { mutableStateOf<Rect?>(null) }
    val tableCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val haptic = LocalHapticFeedback.current

    val hasValue = (heatmap?.rows ?: emptyList()).any { row -> (row.cells ?: emptyList()).any { it.state == PatternHeatCellState.VALUE } }

    fun dismiss() {
        pickedKey = null
        picked = null
        anchor = null
    }

    fun pick(key: String, text: String, cell: LayoutCoordinates?) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        PT.chart("heatmap")
        if (pickedKey == key) {
            dismiss()
        } else {
            pickedKey = key
            picked = text
            val t = tableCoords[0]
            anchor = if (t != null && cell != null && t.isAttached && cell.isAttached) t.localBoundingBoxOf(cell) else null
        }
    }

    // 말풍선은 3초 뒤 저절로 닫혀요 (다른 칸을 누르면 다시 3초)
    LaunchedEffect(pickedKey) {
        if (pickedKey != null) {
            delay(3_000)
            dismiss()
        }
    }

    SectionCard("언제 가장 잘 실행하나요?", modifier = modifier, aside = { Text("한 횟수") }) {
        if (heatmap != null && hasValue) {
            Column {
                PatternHeatWindows(heatmap)
                // 칸을 누르면 그 위에 말풍선 (빈 곳을 누르면 닫혀요)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { tableCoords[0] = it }
                        .pointerInput(pickedKey) { detectTapGestures { if (pickedKey != null) dismiss() } },
                ) {
                    PatternHeatTable(heatmap, pickedKey = pickedKey, onPick = { k, t, c -> pick(k, t, c) })
                    val a = anchor
                    val t = picked
                    if (a != null && t != null) PatternHeatTooltip(text = t, anchor = a, modifier = Modifier.matchParentSize())
                }
                PatternHeatLegend(heatmap, Modifier.padding(top = 12.dp))
            }
        } else {
            NotEnoughView(text = "Task나 루틴을 완료하면 언제 많이 했는지 보여요")
        }
    }
}

/**
 * 히트맵 말풍선: bg/inverse · text/inverse · Caption/MD · padding 6/10 · radius 8 · 꼬리.
 * 칸 위에 띄우고, 위쪽 끝이면 칸 아래로 뒤집어요. 좌우는 표 안으로 맞춰요.
 */
@Composable
private fun PatternHeatTooltip(text: String, anchor: Rect, modifier: Modifier = Modifier) {
    val bg = NC.bgInverse
    val fg = NC.textInverse
    val density = LocalDensity.current
    val gap = with(density) { 4.dp.toPx() }
    val tailW = with(density) { 10.dp.toPx() }
    val tailH = with(density) { 5.dp.toPx() }
    // 말풍선 높이 어림 (12sp · 132% + 위아래 6) — 위에 자리가 없으면 아래로
    val estimate = with(density) { (12.sp.toPx() * 1.32f) + 12.dp.toPx() }
    val below = anchor.top < estimate + tailH + gap
    Layout(
        modifier = modifier,
        content = {
            Text(
                text,
                style = NType.captionMD,
                color = fg,
                maxLines = 2,
                modifier = Modifier.background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Canvas(Modifier.size(10.dp, 5.dp)) {
                val p = Path().apply {
                    if (below) {
                        moveTo(0f, size.height); lineTo(size.width / 2, 0f); lineTo(size.width, size.height)
                    } else {
                        moveTo(0f, 0f); lineTo(size.width / 2, size.height); lineTo(size.width, 0f)
                    }
                    close()
                }
                drawPath(p, bg)
            }
        },
    ) { measurables, constraints ->
        val maxW = constraints.maxWidth
        val bubble = measurables[0].measure(Constraints(maxWidth = maxW))
        val tail = measurables[1].measure(Constraints())
        val cx = anchor.center.x
        val x = (cx - bubble.width / 2f).coerceIn(0f, maxOf(0f, (maxW - bubble.width).toFloat()))
        val r = 8.dp.toPx()
        val tailX = (cx - tailW / 2f).coerceIn(x + r, maxOf(x + r, x + bubble.width - r - tailW))
        val tailY: Float
        val bubbleY: Float
        if (below) {
            tailY = anchor.bottom + gap
            bubbleY = tailY + tailH
        } else {
            tailY = anchor.top - gap - tailH
            bubbleY = tailY - bubble.height
        }
        layout(maxW, constraints.maxHeight) {
            bubble.place(x.toInt(), bubbleY.toInt())
            tail.place(tailX.toInt(), tailY.toInt())
        }
    }
}

/** "화 · 09시" */
private fun patternHeatSlot(day: JavaDayOfWeek, hour: Int): String =
    "${PatternFormat.dayShort(day)} · ${hour.toString().padStart(2, '0')}시"

// MARK: 표 (가로 = 요일, 세로 = 시간)

@Composable
private fun PatternHeatTable(h: PatternHeatmap, pickedKey: String?, onPick: (String, String, LayoutCoordinates?) -> Unit) {
    val hours = h.hours ?: emptyList()
    val rows = h.rows ?: emptyList()
    // [요일][시] → 칸
    val lookup: Map<JavaDayOfWeek, Map<Int, PatternHeatCell>> =
        rows.associate { r -> r.dayOfWeek to (r.cells ?: emptyList()).associateBy { it.hour } }
    val days = rows.map { it.dayOfWeek }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Spacer(Modifier.size(width = 30.dp, height = 16.dp))
            for (d in days) {
                Text(
                    PatternFormat.dayShort(d),
                    style = pt(12, PTWeight.semibold),
                    color = when (d) {
                        JavaDayOfWeek.SATURDAY -> NC.brand
                        JavaDayOfWeek.SUNDAY -> NC.danger
                        else -> NC.ink2
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        for (hour in hours) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${hour}시", style = ptMono(10), color = NC.ink3, modifier = Modifier.width(30.dp))
                for (d in days) {
                    val c = lookup[d]?.get(hour) ?: PatternHeatCell(hour = hour, state = PatternHeatCellState.EMPTY)
                    PatternHeatCellView(
                        c = c,
                        day = d,
                        fixedLabel = h.fixedLabel,
                        pickedKey = pickedKey,
                        onPick = onPick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PatternHeatCellView(
    c: PatternHeatCell,
    day: JavaDayOfWeek,
    fixedLabel: String?,
    pickedKey: String?,
    onPick: (String, String, LayoutCoordinates?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = patternHeatSlot(day, c.hour)
    // 누른 칸의 자리 (말풍선 위치)
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val track = Modifier.onGloballyPositioned { coords[0] = it }
    val shape = RoundedCornerShape(5.dp)
    val key = "${day.name}-${c.hour}"
    val on = pickedKey == key
    val ink = NC.ink
    val selected = if (on) Modifier.border(2.dp, ink, shape) else Modifier
    val level = c.level ?: 0
    when {
        c.state == PatternHeatCellState.VALUE && level > 0 -> {
            Box(
                modifier
                    .height(24.dp)
                    .clip(shape)
                    .background(NC.heat(level), shape)
                    .then(selected)
                    .then(track)
                    .clickable {
                        val n = c.count ?: 0
                        val rate = c.rate
                        val samples = c.samples
                        onPick(
                            key,
                            if (rate != null) "$label · 실행률 $rate%" + (if (samples != null) " ($n/$samples)" else "")
                            else "$label · ${n}번 했어요",
                            coords[0],
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${c.count ?: 0}",
                    style = ptMono(11, PTWeight.semibold),
                    color = if (level >= 3) Color.White else NC.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        c.state == PatternHeatCellState.FIXED -> {
            Box(
                modifier
                    .height(24.dp)
                    .clip(shape)
                    .then(selected)
                    .then(track)
                    .clickable { onPick(key, "$label · ${fixedLabel ?: "고정 시간"}", coords[0]) },
            ) {
                PatternHatchFill(Modifier.matchParentSize())
            }
        }
        else -> {
            // 한 게 없는 칸은 색 없이 점선 테두리만
            Box(
                modifier
                    .height(24.dp)
                    .patternDashedBorder(NC.line, 5.dp)
                    .clip(shape)
                    .then(selected)
                    .then(track)
                    .clickable { onPick(key, "$label · 한 게 없어요", coords[0]) },
            )
        }
    }
}

/** 점선 둥근 테두리 (iOS strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [3, 2]))) */
fun Modifier.patternDashedBorder(color: Color, radius: Dp): Modifier = this.drawBehind {
    val w = 1.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
    )
}

// MARK: 범례

@Composable
private fun PatternHeatLegend(h: PatternHeatmap, modifier: Modifier = Modifier) {
    FlowLayout(spacing = 16.dp, lineSpacing = 8.dp, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("적게", style = pt(12), color = NC.ink3)
            for (l in 1..5) {
                Box(Modifier.size(width = 16.dp, height = 12.dp).background(NC.heat(l), RoundedCornerShape(3.dp)))
            }
            Text(h.maxCount?.let { "많이 (최대 ${it}번)" } ?: "많이", style = pt(12), color = NC.ink3)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PatternHatchFill(Modifier.size(width = 16.dp, height = 12.dp).clip(RoundedCornerShape(3.dp)))
            Text(h.fixedLabel ?: "고정 시간", style = pt(12), color = NC.ink3)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(width = 16.dp, height = 12.dp).patternDashedBorder(NC.line, 3.dp))
            Text("한 게 없음", style = pt(12), color = NC.ink3)
        }
    }
}

// MARK: 골든 타임 · 취약 시간

@Composable
private fun PatternHeatWindows(h: PatternHeatmap) {
    val g = h.goldenTime
    val w = h.weakTime
    if (g == null && w == null) return
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (g != null) {
            PatternWindowBox(
                badge = "골든 타임", icon = NIcon.trendingUp, iconBg = NC.statusSuccessBg, iconFg = NC.statusSuccessFg, window = g,
                modifier = Modifier.weight(1f).fillMaxHeight().patternDeadTap("golden_time"),
            )
        }
        if (w != null) {
            PatternWindowBox(
                badge = "취약 시간", icon = NIcon.trendingDown, iconBg = NC.statusWarningBg, iconFg = NC.statusWarningFg, window = w,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun PatternWindowBox(
    badge: String,
    icon: NIcon,
    iconBg: Color,
    iconFg: Color,
    window: PatternTimeWindow,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(NC.bgSunken, RoundedCornerShape(Radius.xxl))
            .border(1.dp, NC.borderSubtle, RoundedCornerShape(Radius.xxl))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PatternIconLabel(badge, icon = icon, tileBg = iconBg, tileFg = iconFg)
        Text(
            PatternFormat.windowLabel(window),
            style = NType.h4,
            color = NC.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("실행률 ${window.rate}%", style = NType.captionMD, color = NC.textSecondary)
    }
}

/** 28×28 r8 아이콘 칸 + 라벨 (골든 타임 · 취약 시간 등, 예전 이모지 대신) */
@Composable
fun PatternIconLabel(text: String, icon: NIcon, tileBg: Color, tileFg: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(28.dp).background(tileBg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            icon.Icon(size = 16.dp, tint = tileFg)
        }
        Text(text, style = NType.labelMD, color = NC.textSecondary)
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun PatternHeatmapCardPreview() {
    NaleulTheme(dark = false) {
        Column(
            Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PatternHeatmapCard(heatmap = PatternSampleData.report.heatmap)
            PatternHeatmapCard(heatmap = null)
        }
    }
}
