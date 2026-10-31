// Features/Goal/GoalProgressChart.kt
//
// iOS GoalProgressChart.swift — 웹 features/goal/ui/progress/ProgressChart.tsx — 목표 진행 꺾은선
// (웹은 SVG 직접, iOS 는 Swift Charts, 여기선 Compose Canvas 로 직접 그려요).
//
// - plan  : 계획선 (점선) — 날짜 사이 값은 이어서(보간) 읽어요
// - actual: 실제 기록 (실선 + 점) — 기록한 날만 값이 있어요
// - 오늘 세로선 + 지금 위치 큰 점 + 목표값 가로선
// - 손가락으로 누르고 끌면 그날의 계획 · 기록 값을 그래프 위에 보여줘요 (웹 호버 말풍선)
//
// x 축은 "시작일로부터 며칠째"(Int) 로 그려요 — 날짜를 시각으로 바꾸면 기기 시간대에 따라 하루씩 밀릴 수 있어서요.

package com.naleul.naleul.feature.goal

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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class GoalChartPoint(
    val date: String,
    val value: Double,
    val label: String? = null,
) {
    companion object {}
}

data class GoalChartSeries(
    val key: String,
    val name: String,
    val kind: Kind,
    val points: List<GoalChartPoint>,
) {
    enum class Kind {
        plan, actual;

        companion object {}
    }

    val id: String get() = key

    companion object {}
}

data class GoalChartMarker(
    val date: String? = null,
    val value: Double,
    val label: String,
) {
    companion object {}
}

// MARK: - 계산 (화면과 분리)

/** 계열 한 점을 x(며칠째)·y 로 바꾼 것 */
private data class GoalChartXY(val x: Int, val y: Double, val label: String?)

/** 계열 하나를 x·y 로 바꾼 것 */
private class GoalChartPrepared(val s: GoalChartSeries, val xy: List<GoalChartXY>) {
    /** 그날 값 — 계획선은 이어서(보간), 기록은 그날 기록만 */
    fun value(at: Int): Double? {
        if (s.kind == GoalChartSeries.Kind.plan) return goalChartInterpolate(xy, at)
        return xy.firstOrNull { it.x == at }?.y
    }
}

private data class GoalChartScale(val lo: Double, val hi: Double, val ticks: List<Double>)

/** 보기 좋은 눈금 (1, 2, 2.5, 5 × 10^n) */
private fun goalChartNiceTicks(minV: Double, maxV: Double, count: Double = 4.0): GoalChartScale {
    var mn = minV
    var mx = maxV
    if (mn == mx) {
        val pad = if (abs(mn) * 0.1 == 0.0) 1.0 else abs(mn) * 0.1
        mn -= pad
        mx += pad
    }
    val raw = (mx - mn) / count
    val mag = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.firstOrNull { it >= raw } ?: raw
    val lo = floor(mn / step) * step
    val hi = ceil(mx / step) * step
    val ticks = mutableListOf<Double>()
    var v = lo
    while (v <= hi + step / 2 && ticks.size < 20) {
        ticks.add(kotlin.math.round(v * 1e6) / 1e6)
        v += step
    }
    return GoalChartScale(lo, hi, ticks)
}

/** 계획선 위 x 날의 값 (양 끝 밖이면 null) */
private fun goalChartInterpolate(pts: List<GoalChartXY>, x: Int): Double? {
    val first = pts.firstOrNull() ?: return null
    val last = pts.last()
    if (x < first.x || x > last.x) return null
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        if (x <= b.x) {
            if (b.x == a.x) return b.y
            return a.y + (b.y - a.y) * (x - a.x).toDouble() / (b.x - a.x).toDouble()
        }
    }
    return if (first.x == x) first.y else null
}

/** 그래프 자리 (px). 왼쪽 여백 · 오른쪽 y 눈금 · 위 이름표 · 아래 x 눈금 자리를 빼고 그려요 */
private class GoalChartGeo(
    val width: Float,
    val height: Float,
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val span: Int,
    val lo: Double,
    val hi: Double,
) {
    val plotW: Float get() = max(right - left, 1f)
    val plotH: Float get() = max(bottom - top, 1f)
    fun px(x: Int): Float = left + x.toFloat() / span.toFloat() * plotW
    fun py(y: Double): Float = top + ((hi - y) / max(hi - lo, 0.0001)).toFloat() * plotH
    fun xAt(pxv: Float): Int = (((pxv - left) / plotW) * span).roundToInt().coerceIn(0, span)
}

/** 점들을 부드럽게 잇는 곡선 (iOS .monotone — 값이 점 사이에서 튀지 않는 3차 곡선, Fritsch–Carlson) */
private fun goalChartMonotonePath(pts: List<Offset>, path: Path = Path()): Path {
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, pts[0].y)
    if (pts.size == 1) return path
    if (pts.size == 2) {
        path.lineTo(pts[1].x, pts[1].y)
        return path
    }
    val n = pts.size
    val d = FloatArray(n - 1) { i ->
        val dx = pts[i + 1].x - pts[i].x
        if (dx == 0f) 0f else (pts[i + 1].y - pts[i].y) / dx
    }
    val m = FloatArray(n)
    m[0] = d[0]
    m[n - 1] = d[n - 2]
    for (i in 1 until n - 1) {
        m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
    }
    for (i in 0 until n - 1) {
        if (d[i] == 0f) {
            m[i] = 0f
            m[i + 1] = 0f
        } else {
            val a = m[i] / d[i]
            val b = m[i + 1] / d[i]
            val s = a * a + b * b
            if (s > 9f) {
                val t = 3f / sqrt(s)
                m[i] = t * a * d[i]
                m[i + 1] = t * b * d[i]
            }
        }
    }
    for (i in 0 until n - 1) {
        val p0 = pts[i]
        val p1 = pts[i + 1]
        val dx = (p1.x - p0.x) / 3f
        path.cubicTo(p0.x + dx, p0.y + m[i] * dx, p1.x - dx, p1.y - m[i + 1] * dx, p1.x, p1.y)
    }
    return path
}

private fun goalChartPointsToMark(s: GoalChartSeries, xy: List<GoalChartXY>): List<GoalChartXY> = when (s.kind) {
    GoalChartSeries.Kind.plan -> if (xy.size <= 2) emptyList() else xy.drop(1).dropLast(1).filter { it.label != null }
    GoalChartSeries.Kind.actual -> if (xy.size <= 60) xy else emptyList()
}

/** 그래프 색 (Canvas 안에서는 NC 를 못 읽어서 밖에서 꺼내 둬요) */
private data class GoalChartColors(
    val brand: Color,
    val ink2: Color,
    val ink3: Color,
    val ink4: Color,
    val line: Color,
    val success: Color,
    val surface: Color,
)

// MARK: - 화면

/**
 * @param target 목표값 가로선
 * @param current 지금 위치 (오늘 또는 마지막 기록 날) — date 필요
 * @param zeroBased y축을 0부터 (Task 개수처럼 누적값)
 */
@Composable
fun GoalProgressChart(
    series: List<GoalChartSeries>,
    from: String,
    to: String,
    today: String,
    target: GoalChartMarker? = null,
    current: GoalChartMarker? = null,
    format: (Double) -> String,
    height: Dp = 260.dp,
    zeroBased: Boolean = false,
    modifier: Modifier = Modifier,
    /** 그래프를 눌러 값 말풍선을 볼 때 (앰플리튜드 chart_interact — 화면당 1회는 부르는 쪽에서 걸러요) */
    onInteract: (() -> Unit)? = null,
) {
    var selected by remember { mutableStateOf<Int?>(null) }
    val latestOnInteract by rememberUpdatedState(onInteract)
    LaunchedEffect(Unit) {
        snapshotFlow { selected != null }.collect { if (it) latestOnInteract?.invoke() }
    }

    val span = max(KST.diffDays(from, to), 1)
    fun xOf(date: String): Int = KST.diffDays(from, date)

    val all = series.map { s ->
        GoalChartPrepared(s, s.points.map { GoalChartXY(xOf(it.date), it.value, it.label) }.sortedBy { it.x })
    }
    val ys = all.flatMap { p -> p.xy.map { it.y } }.toMutableList()
    if (target != null) ys.add(target.value)
    if (current != null) ys.add(current.value)
    if (zeroBased) ys.add(0.0)
    val scale = goalChartNiceTicks(ys.minOrNull() ?: 0.0, ys.maxOrNull() ?: 1.0)
    val hiSafe = max(scale.hi, scale.lo + 0.0001)
    val todayX = xOf(today)
    val showToday = todayX in 0..span
    val xTicks = (0 until 4).map { (span.toDouble() * it / 3).roundToInt() }.toSet().sorted()
    val currentX = current?.date?.let { xOf(it) }

    val colors = GoalChartColors(
        brand = NC.brand, ink2 = NC.ink2, ink3 = NC.ink3, ink4 = NC.ink4,
        line = NC.line, success = NC.success, surface = NC.surface,
    )
    val measurer = rememberTextMeasurer()
    val axisStyle = pt(11).copy(color = colors.ink4)
    val todayStyle = pt(11, PTWeight.semibold).copy(color = colors.ink3)
    val targetStyle = pt(11, PTWeight.semibold).copy(color = colors.success)
    val currentStyle = pt(11, PTWeight.bold).copy(color = Color.White)
    val density = LocalDensity.current

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 그래프 위 안내 (값은 누르고 있는 자리의 말풍선으로 보여줘요)
        Row(
            Modifier.heightIn(min = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SfImage("hand.point.up.left", size = 10.dp, tint = NC.ink4)
            Text(
                if (selected == null) "그래프를 누르고 좌우로 움직이면 그날 값을 보여드려요" else "손을 떼면 닫혀요",
                style = pt(11),
                color = NC.ink4,
            )
        }

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = "목표 진행 그래프" },
        ) {
            val geo = with(density) {
                // 오른쪽 y 눈금 글자 자리: 가장 긴 눈금 글자 폭 + 여백
                val yLabelW = scale.ticks.maxOfOrNull { measurer.measure(format(it), axisStyle).size.width }?.toFloat() ?: 0f
                GoalChartGeo(
                    width = maxWidth.toPx(),
                    height = maxHeight.toPx(),
                    left = 4.dp.toPx(),
                    right = maxWidth.toPx() - yLabelW - 8.dp.toPx(),
                    top = 22.dp.toPx(),
                    bottom = maxHeight.toPx() - 20.dp.toPx(),
                    span = span,
                    lo = scale.lo,
                    hi = hiSafe,
                )
            }

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(span, geo.left, geo.right) {
                        // 누르고 끄는 동안만 선택 (손을 떼면 닫혀요 — iOS chartXSelection)
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            selected = geo.xAt(down.position.x)
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull() ?: break
                                if (!c.pressed) break
                                selected = geo.xAt(c.position.x)
                                c.consume()
                            }
                            selected = null
                        }
                    },
            ) {
                goalChartDraw(
                    geo = geo, all = all, scale = scale, xTicks = xTicks, from = from,
                    target = target, showToday = showToday, todayX = todayX,
                    current = current, currentX = currentX, selected = selected,
                    colors = colors, measurer = measurer, format = format,
                    axisStyle = axisStyle, todayStyle = todayStyle, targetStyle = targetStyle, currentStyle = currentStyle,
                )
            }

            // 누르고 있는 날의 계획 · 기록 값 말풍선 (그래프 밖으로 안 나가게 좌우를 맞춰요)
            val sel = selected
            if (sel != null) {
                val cx = geo.px(sel).roundToInt()
                Box(Modifier.matchParentSize()) {
                    GoalChartSelectionBubble(
                        all = all, x = sel, from = from, today = today, format = format,
                        modifier = Modifier.layout { measurable, constraints ->
                            val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                val x = (cx - p.width / 2).coerceIn(0, max(constraints.maxWidth - p.width, 0))
                                p.place(x, 0)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Canvas 그리기 본체 */
private fun DrawScope.goalChartDraw(
    geo: GoalChartGeo,
    all: List<GoalChartPrepared>,
    scale: GoalChartScale,
    xTicks: List<Int>,
    from: String,
    target: GoalChartMarker?,
    showToday: Boolean,
    todayX: Int,
    current: GoalChartMarker?,
    currentX: Int?,
    selected: Int?,
    colors: GoalChartColors,
    measurer: TextMeasurer,
    format: (Double) -> String,
    axisStyle: TextStyle,
    todayStyle: TextStyle,
    targetStyle: TextStyle,
    currentStyle: TextStyle,
) {
    val dp1 = 1.dp.toPx()

    // y 눈금 · 가로 격자 (오른쪽)
    for (t in scale.ticks) {
        val y = geo.py(t)
        if (y < geo.top - 1 || y > geo.bottom + 1) continue
        drawLine(colors.line, Offset(geo.left, y), Offset(geo.right, y), strokeWidth = dp1)
        val m = measurer.measure(format(t), axisStyle)
        drawText(m, topLeft = Offset(geo.right + 6.dp.toPx(), y - m.size.height / 2f))
    }
    // x 눈금 · 세로 격자 (아래)
    for (x in xTicks) {
        val px = geo.px(x)
        drawLine(colors.line.copy(alpha = colors.line.alpha * 0.6f), Offset(px, geo.top), Offset(px, geo.bottom), strokeWidth = dp1)
        val d = KST.addDays(from, x)
        val m = measurer.measure("${KST.monthOf(d)}.${KST.dayOfMonth(d)}", axisStyle)
        val lx = (px - m.size.width / 2f).coerceIn(0f, max(geo.width - m.size.width, 0f))
        drawText(m, topLeft = Offset(lx, geo.bottom + 4.dp.toPx()))
    }

    // 목표값
    if (target != null) {
        val y = geo.py(target.value)
        drawLine(
            colors.success.copy(alpha = 0.6f), Offset(geo.left, y), Offset(geo.right, y), strokeWidth = dp1,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
        )
        val m = measurer.measure(target.label, targetStyle)
        drawText(m, topLeft = Offset(geo.right - m.size.width, y - m.size.height - 2.dp.toPx()))
    }
    // 오늘
    if (showToday) {
        val x = geo.px(todayX)
        drawLine(
            colors.ink3, Offset(x, geo.top), Offset(x, geo.bottom), strokeWidth = dp1,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
        )
        val m = measurer.measure("오늘", todayStyle)
        drawText(m, topLeft = Offset(x - m.size.width / 2f, geo.top - m.size.height - 2.dp.toPx()))
    }

    // 기록 선 아래 옅은 면 (어디까지 왔는지 한눈에)
    for (item in all.filter { it.s.kind == GoalChartSeries.Kind.actual }) {
        if (item.xy.isEmpty()) continue
        val pts = item.xy.map { Offset(geo.px(it.x), geo.py(it.y)) }
        val base = geo.py(scale.lo)
        val area = goalChartMonotonePath(pts)
        area.lineTo(pts.last().x, base)
        area.lineTo(pts.first().x, base)
        area.close()
        drawPath(
            area,
            Brush.verticalGradient(
                listOf(colors.brand.copy(alpha = 0.28f), colors.brand.copy(alpha = 0.02f)),
                startY = geo.top,
                endY = base,
            ),
        )
    }
    // 선
    for (item in all) {
        if (item.xy.isEmpty()) continue
        val pts = item.xy.map { Offset(geo.px(it.x), geo.py(it.y)) }
        if (item.s.kind == GoalChartSeries.Kind.plan) {
            val p = Path()
            p.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) p.lineTo(pts[i].x, pts[i].y)
            drawPath(
                p, colors.ink4,
                style = Stroke(
                    width = 2.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                ),
            )
        } else {
            drawPath(
                goalChartMonotonePath(pts), colors.brand,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
    // 점: 계획선의 꺾이는 점(마일스톤) · 기록한 날
    for (item in all) {
        val isPlan = item.s.kind == GoalChartSeries.Kind.plan
        val c = if (isPlan) colors.ink4 else colors.brand
        val r = (if (isPlan) 7.dp else 8.dp).toPx() / 2f
        for (p in goalChartPointsToMark(item.s, item.xy)) {
            val o = Offset(geo.px(p.x), geo.py(p.y))
            drawCircle(c, r, o)
            drawCircle(colors.surface, r, o, style = Stroke(1.5.dp.toPx()))
        }
    }
    // 지금 위치
    if (current != null && currentX != null && currentX in 0..geo.span) {
        val o = Offset(geo.px(currentX), geo.py(current.value))
        drawCircle(colors.brand.copy(alpha = 0.18f), 10.dp.toPx(), o)
        drawCircle(colors.brand, 5.dp.toPx(), o)
        if (selected == null) {
            val m = measurer.measure(current.label, currentStyle)
            val padH = 8.dp.toPx()
            val padV = 2.dp.toPx()
            val w = m.size.width + padH * 2
            val h = m.size.height + padV * 2
            val left = (o.x - w / 2f).coerceIn(0f, max(geo.width - w, 0f))
            val top = o.y - 10.dp.toPx() - 6.dp.toPx() - h
            drawRoundRect(colors.brand, Offset(left, top), Size(w, h), CornerRadius(h / 2f, h / 2f))
            drawText(m, topLeft = Offset(left + padH, top + padV))
        }
    }
    // 누르고 있는 날
    if (selected != null) {
        val x = geo.px(selected)
        drawLine(colors.ink2.copy(alpha = 0.35f), Offset(x, geo.top), Offset(x, geo.bottom), strokeWidth = dp1)
    }
}

/** 누르고 있는 날의 계획 · 기록 값 말풍선 */
@Composable
private fun GoalChartSelectionBubble(
    all: List<GoalChartPrepared>,
    x: Int,
    from: String,
    today: String,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val date = KST.addDays(from, x)
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .popShadow(shape)
            .background(NC.bgInverse, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "${KST.monthOf(date)}.${KST.dayOfMonth(date)} (${KST.weekdayLabel[KST.weekdayIndex(date)]})" + (if (date == today) " · 오늘" else ""),
            style = pt(11, PTWeight.semibold),
            color = NC.onInk.copy(alpha = 0.75f),
        )
        for (item in all) {
            val y = item.value(at = x) ?: continue
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(width = 10.dp, height = 3.dp)
                        .background(
                            if (item.s.kind == GoalChartSeries.Kind.plan) NC.onInk.copy(alpha = 0.6f) else NC.textBrandOnInverse,
                            RoundedCornerShape(1.dp),
                        ),
                )
                Text(item.s.name, style = pt(12), color = NC.onInk.copy(alpha = 0.8f))
                Text(format(y), style = ptMono(12, PTWeight.bold), color = NC.onInk)
            }
        }
    }
}

/** 범례 (점선 = 계획, 실선 = 기록, 점 = 지금 위치) */
@Composable
fun GoalChartLegend(items: List<Pair<String, GoalChartSeries.Kind>>, modifier: Modifier = Modifier) {
    val ink4 = NC.ink4
    val brand = NC.brand
    FlowLayout(spacing = 16.dp, modifier = modifier) {
        for ((name, kind) in items) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.size(width = 18.dp, height = 4.dp)) {
                    val isPlan = kind == GoalChartSeries.Kind.plan
                    drawLine(
                        color = if (isPlan) ink4 else brand,
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 2.5.dp.toPx(),
                        pathEffect = if (isPlan) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
                    )
                }
                Text(name, style = pt(12), color = NC.ink3)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(10.dp).background(NC.brand, CircleShape))
            Text("지금 위치", style = pt(12), color = NC.ink3)
        }
    }
}

@Preview(showBackground = true, heightDp = 420)
@Composable
private fun GoalProgressChartPreview() {
    NaleulTheme(dark = false) {
        val p = GoalPreviewData.progress
        val m = p.metric!!
        Column(Modifier.canvasBackground().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalProgressChart(
                series = listOf(
                    GoalChartSeries(
                        key = "plan", name = "계획", kind = GoalChartSeries.Kind.plan,
                        points = m.planLine.map { GoalChartPoint(date = it.date, value = it.value, label = it.label) },
                    ),
                    GoalChartSeries(
                        key = "actual", name = "기록", kind = GoalChartSeries.Kind.actual,
                        points = m.logs.map { GoalChartPoint(date = it.date, value = it.value) },
                    ),
                ),
                from = p.startDate!!, to = p.endDate!!, today = p.today,
                target = GoalChartMarker(value = m.targetValue, label = "목표 72kg"),
                current = GoalChartMarker(date = m.lastLoggedDate, value = m.currentValue, label = "지금 77.4kg"),
                format = { GoalDetailLogic.withUnit(it, "kg") },
            )
            GoalChartLegend(items = listOf("계획" to GoalChartSeries.Kind.plan, "내 기록" to GoalChartSeries.Kind.actual))
        }
    }
}
