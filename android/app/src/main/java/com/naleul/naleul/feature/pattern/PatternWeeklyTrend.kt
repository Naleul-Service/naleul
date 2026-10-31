// feature/pattern/PatternWeeklyTrend.kt
//
// iOS Features/Pattern/PatternWeeklyTrend.swift (웹 features/pattern/ui/WeeklyTrend.tsx)
// 주차별 실행률 선 그래프. 웹은 SVG 로 직접 그려서, 여기서도 Canvas 로 같은 계산을 그대로 옮겼어요.
// (차트 라이브러리는 표본이 부족한 주에서 선을 끊고, 최고점 · 첫 점에만 라벨을 다는 걸 맞추기 번거로워서)
//
// ⚠️ NC 색은 Canvas 그리기 블록 안에서 못 읽어서 밖에서 꺼내 두고 써요.
//    글자는 rememberTextMeasurer() 로 크기를 재서 drawText 로 그려요 (iOS ctx.draw(Text, at:, anchor:)).

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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Canvas 안에 글자를 그려요. (ax, ay) 는 글자 상자에서 at 에 맞출 지점 비율이에요.
 *   trailing(오른쪽 가운데) = (1, 0.5) · bottom(아래 가운데) = (0.5, 1) · topLeading = (0, 0) · leading = (0, 0.5)
 * (iOS ctx.draw(Text(...), at:, anchor:) 대신 — 기록형 목표 그래프도 같이 써요)
 */
fun DrawScope.patternDrawText(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    at: Offset,
    ax: Float,
    ay: Float,
) {
    val layout = measurer.measure(text, style)
    drawText(
        layout,
        topLeft = Offset(at.x - layout.size.width * ax, at.y - layout.size.height * ay),
    )
}

@Composable
fun PatternWeeklyTrend(weekly: PatternWeekly?, modifier: Modifier = Modifier) {
    val points = weekly?.points ?: emptyList()
    val valuedCount = points.count { it.rate != null }
    SectionCard("주차별 흐름", modifier = modifier, aside = { Text("실행률 %") }) {
        if (valuedCount < 2) {
            NotEnoughView(text = "2주 이상 기록이 쌓이면 흐름을 보여드려요")
        } else {
            PatternTrendCanvas(
                points = points,
                maxIndex = weekly?.maxIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .patternChartTap("weekly")
                    .semantics { contentDescription = "주차별 실행률 그래프" },
            )
        }
    }
}

@Composable
private fun PatternTrendCanvas(points: List<PatternWeeklyPoint>, maxIndex: Int?, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    // 그리기 블록 안에서는 NC 를 못 읽어서 미리 꺼내 둬요
    val lineColor = NC.line
    val ink = NC.ink
    val ink3 = NC.ink3
    val brand = NC.brand
    val surface = NC.surface

    Canvas(modifier) {
        val padL = 32.dp.toPx()
        val padR = 12.dp.toPx()
        val padT = 24.dp.toPx()
        val padB = 28.dp.toPx()
        val w = size.width
        val h = size.height
        val innerW = w - padL - padR
        val innerH = h - padT - padB
        val rates = points.mapNotNull { it.rate }
        val minRate = rates.minOrNull() ?: return@Canvas

        val yMin = maxOf(0, floor((minRate - 10) / 10.0).toInt() * 10)
        val yMax = 100
        val span = maxOf(yMax - yMin, 1).toDouble()
        val tickCount = ((yMax - yMin) / 10.0).roundToInt() + 1
        val allTicks = (0 until tickCount).map { yMin + it * 10 }
        val ticks = allTicks.filterIndexed { i, _ -> allTicks.size <= 6 || i % 2 == 0 || i == allTicks.size - 1 }

        fun x(i: Int): Float =
            if (points.size == 1) padL + innerW / 2 else padL + i.toFloat() / (points.size - 1) * innerW

        fun y(r: Int): Float = padT + (1 - (r - yMin) / span).toFloat() * innerH

        // 가로 눈금
        for (t in ticks) {
            drawLine(lineColor, Offset(padL, y(t)), Offset(w - padR, y(t)), strokeWidth = 1.dp.toPx())
            patternDrawText(measurer, "$t", pt(11).copy(color = ink3), Offset(padL - 8.dp.toPx(), y(t)), 1f, 0.5f)
        }

        // 표본이 부족한 주(null)에서 선을 끊어요
        val segments = mutableListOf<List<Pair<Int, Int>>>()
        var cur = mutableListOf<Pair<Int, Int>>()
        points.forEachIndexed { i, p ->
            val r = p.rate
            if (r != null) {
                cur.add(i to r)
            } else {
                if (cur.isNotEmpty()) segments.add(cur)
                cur = mutableListOf()
            }
        }
        if (cur.isNotEmpty()) segments.add(cur)

        val bottom = padT + innerH
        val gradient = Brush.verticalGradient(
            listOf(brand.copy(alpha = 0.16f), brand.copy(alpha = 0f)),
            startY = padT,
            endY = bottom,
        )
        for (seg in segments) {
            val line = Path()
            seg.forEachIndexed { k, (i, r) ->
                if (k == 0) line.moveTo(x(i), y(r)) else line.lineTo(x(i), y(r))
            }
            if (seg.size > 1) {
                val first = seg.first()
                val last = seg.last()
                // 선과 같은 점을 따라간 뒤 바닥까지 내려 닫아요
                val area = Path()
                seg.forEachIndexed { k, (i, r) ->
                    if (k == 0) area.moveTo(x(i), y(r)) else area.lineTo(x(i), y(r))
                }
                area.lineTo(x(last.first), bottom)
                area.lineTo(x(first.first), bottom)
                area.close()
                drawPath(area, brush = gradient)
            }
            drawPath(line, color = brand, style = Stroke(width = 2.5.dp.toPx(), join = StrokeJoin.Round))
            for ((i, r) in seg) {
                val c = Offset(x(i), y(r))
                drawCircle(brand, radius = 4.5.dp.toPx(), center = c)
                drawCircle(surface, radius = 4.5.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
            }
        }

        // 최고점 · 첫 점 라벨
        val firstIdx = points.indexOfFirst { it.rate != null }.takeIf { it >= 0 }
        val maxIdx = maxIndex?.let { mi -> points.indexOfFirst { it.index == mi }.takeIf { it >= 0 } }
        if (maxIdx != null) {
            val r = points[maxIdx].rate
            if (r != null) {
                patternDrawText(
                    measurer, "$r%", pt(12, PTWeight.bold).copy(color = ink),
                    Offset(x(maxIdx), y(r) - 12.dp.toPx()), 0.5f, 1f,
                )
            }
        }
        if (firstIdx != null && firstIdx != maxIdx) {
            val r = points[firstIdx].rate
            if (r != null) {
                patternDrawText(
                    measurer, "$r%", pt(11).copy(color = ink3),
                    Offset(x(firstIdx) + 8.dp.toPx(), y(r) + 14.dp.toPx()), 0f, 0f,
                )
            }
        }

        // 아래 "n주" 라벨 (주가 많으면 건너뛰며)
        val lastIdx = points.size - 1
        val labelEvery = if (points.size > 12) ceil(points.size / 8.0).toInt() else 1
        points.forEachIndexed { i, p ->
            if (i % labelEvery == 0 || i == lastIdx) {
                val isLast = i == lastIdx
                patternDrawText(
                    measurer, "${p.index}주",
                    pt(11, if (isLast) PTWeight.bold else PTWeight.regular).copy(color = if (isLast) ink else ink3),
                    Offset(x(i), h - 4.dp.toPx()), 0.5f, 1f,
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun PatternWeeklyTrendPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PatternWeeklyTrend(weekly = PatternSampleData.report.weekly)
            PatternWeeklyTrend(weekly = null)
        }
    }
}
