// feature/record/RecordPatternCards.kt
//
// iOS Features/Record/RecordPatternCards.swift
// 웹 features/record/ui/pattern/RecordWeeklyCard.tsx · RecordRhythmCard.tsx · RecordWhenCard.tsx · RecordRoutineCard.tsx
// 기록형 목표의 "나의 패턴" 카드들. 카드 틀은 나의 패턴과 같은 SectionCard 를 써요.

package com.naleul.naleul.feature.record

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// MARK: - 주차별로 완료한 Task (막대) + 처음부터의 누적 (선)
//
// 막대는 주마다 오르내려도, 누적 선은 절대 내려가지 않아요 → "계속 쌓이고 있다"는 걸 보여주려고 같이 그렸어요.
// 웹 SVG 계산을 Canvas 로 그대로 옮겼어요.

private fun recordAvg(list: List<RecordWeekStat>): Double =
    list.sumOf { it.count }.toDouble() / maxOf(list.size, 1).toDouble()

private fun recordRound1(n: Double): Double = Math.round(n * 10).toDouble() / 10

/** 웹 weeks.slice(-8, -4) */
private fun recordPreviousFour(weeks: List<RecordWeekStat>): List<RecordWeekStat> {
    val end = maxOf(weeks.size - 4, 0)
    val start = maxOf(weeks.size - 8, 0)
    return weeks.subList(start, end)
}

@Composable
fun RecordWeeklyCard(weeks: List<RecordWeekStat>, modifier: Modifier = Modifier) {
    val last = weeks.lastOrNull()
    if (last != null && last.cumulativeCount > 0) {
        val recentAvg = recordRound1(recordAvg(weeks.takeLast(4)))
        val prevAvg = recordRound1(recordAvg(recordPreviousFour(weeks)))
        SectionCard("주차별로 완료한 Task", modifier = modifier, aside = { Text("누적 ${last.cumulativeCount}개") }) {
            Column {
                RecordWeeklyCanvas(
                    weeks = weeks,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .semantics { contentDescription = "주차별 완료한 Task와 누적 그래프" },
                )
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(12.dp).background(NC.heat2, RoundedCornerShape(3.dp)))
                        Text("그 주에 완료한 Task", style = pt(12), color = NC.ink3)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(width = 16.dp, height = 2.dp).background(NC.ink, CircleShape))
                        Text("처음부터 누적", style = pt(12), color = NC.ink3)
                    }
                }
                if (recentAvg > 0) {
                    RecordAverageNote(recentAvg = recentAvg, prevAvg = prevAvg, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    } else {
        SectionCard("주차별로 완료한 Task", modifier = modifier, aside = { Text("최근 12주") }) {
            NotEnoughView(text = "Task를 완료하면 주마다 얼마나 했는지 보여드려요")
        }
    }
}

@Composable
private fun RecordAverageNote(recentAvg: Double, prevAvg: Double, modifier: Modifier = Modifier) {
    val brand = NC.brand
    val strong = SpanStyle(fontWeight = FontWeight.Bold, color = brand)
    Text(
        buildAnnotatedString {
            append("최근 4주는 한 주에 평균 ")
            withStyle(strong) { append("${PatternFormat.hours1(recentAvg)}개") }
            if (prevAvg > 0 && recentAvg > prevAvg) {
                append(", 그 전 4주보다 ")
                withStyle(strong) { append("${PatternFormat.hours1(recordRound1(recentAvg - prevAvg))}개 더") }
                append(" 완료하고 있어요")
            } else {
                append(" 완료하고 있어요")
            }
        },
        style = pt(14).copy(lineHeight = 20.sp),
        color = NC.ink2,
        modifier = modifier
            .fillMaxWidth()
            .background(NC.subtle, RoundedCornerShape(Radius.xxl))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun RecordWeeklyCanvas(weeks: List<RecordWeekStat>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    // 그리기 블록 안에서는 NC 를 못 읽어서 미리 꺼내 둬요
    val lineColor = NC.line
    val ink = NC.ink
    val ink3 = NC.ink3
    val brand = NC.brand
    val heat2 = NC.heat2
    val surface = NC.surface

    Canvas(modifier) {
        val last = weeks.lastOrNull() ?: return@Canvas
        val padL = 28.dp.toPx()
        val padR = 36.dp.toPx()
        val padT = 20.dp.toPx()
        val padB = 26.dp.toPx()
        val w = size.width
        val h = size.height
        val innerW = w - padL - padR
        val innerH = h - padT - padB
        val slot = innerW / weeks.size
        val barW = minOf(24.dp.toPx(), slot * 0.56f)
        val maxWeek = maxOf(4, weeks.maxOfOrNull { it.count } ?: 0).toDouble()
        val maxCum = maxOf(4, last.cumulativeCount).toDouble()
        val lastIdx = weeks.size - 1
        val bottom = padT + innerH

        fun cx(i: Int): Float = padL + slot * i + slot / 2
        fun yBar(m: Double): Float = bottom - (m / maxWeek).toFloat() * innerH
        fun yCum(m: Int): Float = bottom - (m.toDouble() / maxCum).toFloat() * innerH

        // 눈금 (0 · 절반 · 최대)
        for (t in listOf(0.0, 0.5, 1.0)) {
            val y = yBar(maxWeek * t)
            drawLine(lineColor, Offset(padL, y), Offset(w - padR, y), strokeWidth = 1.dp.toPx())
            patternDrawText(
                measurer, "${Math.round(maxWeek * t)}", pt(11).copy(color = ink3),
                Offset(padL - 8.dp.toPx(), y), 1f, 0.5f,
            )
        }

        // 막대 (이번 주만 진한 파랑)
        weeks.forEachIndexed { i, wk ->
            val barH = maxOf(if (wk.count > 0) 3.dp.toPx() else 0f, bottom - yBar(wk.count.toDouble()))
            if (barH > 0f) {
                drawRoundRect(
                    color = if (i == lastIdx) brand else heat2,
                    topLeft = Offset(cx(i) - barW / 2, bottom - barH),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(5.dp.toPx()),
                )
            }
        }

        // 누적 선
        val line = Path()
        weeks.forEachIndexed { i, wk ->
            if (i == 0) line.moveTo(cx(i), yCum(wk.cumulativeCount)) else line.lineTo(cx(i), yCum(wk.cumulativeCount))
        }
        drawPath(line, color = ink, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
        weeks.forEachIndexed { i, wk ->
            val r = (if (i == lastIdx) 4.5 else 2.5).dp.toPx()
            val c = Offset(cx(i), yCum(wk.cumulativeCount))
            drawCircle(ink, radius = r, center = c)
            drawCircle(surface, radius = r, center = c, style = Stroke(width = 1.5.dp.toPx()))
        }
        patternDrawText(
            measurer, "${last.cumulativeCount}개", pt(11, PTWeight.bold).copy(color = ink),
            Offset(cx(lastIdx) + 8.dp.toPx(), yCum(last.cumulativeCount)), 0f, 0.5f,
        )

        // 아래 날짜 라벨 (하나 걸러, 마지막은 "이번 주")
        weeks.forEachIndexed { i, wk ->
            if (i % 2 == lastIdx % 2) {
                val isLast = i == lastIdx
                val label = if (isLast) "이번 주" else "${KST.monthOf(wk.weekStart)}/${KST.dayOfMonth(wk.weekStart)}"
                patternDrawText(
                    measurer, label,
                    pt(11, if (isLast) PTWeight.bold else PTWeight.regular).copy(color = if (isLast) ink else ink3),
                    Offset(cx(i), h - 4.dp.toPx()), 0.5f, 1f,
                )
            }
        }
    }
}

// MARK: - 완료의 리듬 (최근 16주 잔디)
//
// 기록형 목표의 핵심은 "얼마나"보다 "끊기지 않았는지"라서, 빈칸·채운 칸이 한눈에 보이게 했어요.
// 웹 hover 설명은 칸을 탭하면 아래에 한 줄로 보여줘요.

private val recordRhythmRowLabel = listOf("월", "", "수", "", "금", "", "일")

private fun recordRhythmDescribe(date: String, stat: RecordDayStat?): String {
    if (stat == null) return "${KST.formatMonthDay(date)} · 완료 없음"
    val min = if (stat.minutes > 0) " · ${RecordPatternFormat.shortDuration(stat.minutes)}" else ""
    return "${KST.formatMonthDay(date)} · Task ${stat.count}개$min"
}

@Composable
fun RecordRhythmCard(rhythm: RecordRhythm, modifier: Modifier = Modifier) {
    var picked by remember { mutableStateOf<String?>(null) }
    // 칸을 처음 눌렀을 때 한 번 (record_stats.cell_tap) — 목표 id 는 통계 섹션이 내려 줘요
    var tapSent by remember { mutableStateOf(false) }
    val statsGoalId = LocalRecordStatsGoalId.current
    SectionCard("완료의 리듬", modifier = modifier, aside = { Text("최근 16주") }) {
        if (rhythm.days.isEmpty()) {
            NotEnoughView(text = "Task를 완료하면 하루 한 칸씩 채워져요")
        } else {
            RecordRhythmFilled(rhythm = rhythm, picked = picked, onPick = {
                picked = it
                if (!tapSent && statsGoalId != null) {
                    tapSent = true
                    WK.track("record_stats.cell_tap", goalId = statsGoalId, props = mapOf("stats_card" to "rhythm"))
                }
            })
        }
    }
}

@Composable
private fun RecordRhythmFilled(rhythm: RecordRhythm, picked: String?, onPick: (String) -> Unit) {
    val today = KST.today()
    val byDate: Map<String, RecordDayStat> = rhythm.days.groupBy { it.date }.mapValues { it.value.first() }
    val maxCount = rhythm.days.maxOfOrNull { it.count } ?: 0
    val weeks = (0 until 16).map { KST.addDays(rhythm.heatmapStart, it * 7) }
    val recordedWeeks = weeks.count { w -> (0 until 7).any { byDate[KST.addDays(w, it)] != null } }
    // 같은 개수면 먼저 나온 날 (웹 reduce 와 같게 — maxByOrNull 도 처음 것을 남겨요)
    val best = rhythm.days.maxByOrNull { it.count }
    val rhythmNote = when {
        recordedWeeks >= 12 -> ". 거의 매주 이어오고 있어요"
        recordedWeeks >= 6 -> ". 리듬이 생기고 있어요"
        else -> ""
    }
    val brand = NC.brand

    Column {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // 위: 달 표시 (그 주에 달이 바뀌면). 칸 폭에 영향을 주지 않게 칸 위에 겹쳐 그려요
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Spacer(Modifier.size(14.dp))
                weeks.forEachIndexed { wi, w ->
                    val showMonth = wi == 0 || KST.monthOf(w) != KST.monthOf(weeks[wi - 1])
                    Box(Modifier.weight(1f).height(14.dp)) {
                        if (showMonth) {
                            Text(
                                "${KST.monthOf(w)}월",
                                style = pt(11),
                                color = NC.ink3,
                                maxLines = 1,
                                softWrap = false,
                                // 옆 칸까지 넘쳐도 되게 (iOS .fixedSize())
                                modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                            )
                        }
                    }
                }
            }
            // 요일 줄 × 16주 (하루 한 칸)
            for (i in 0 until 7) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(recordRhythmRowLabel[i], style = pt(11), color = NC.ink3, modifier = Modifier.width(14.dp))
                    for (w in weeks) {
                        val d = KST.addDays(w, i)
                        RecordRhythmCell(
                            date = d,
                            stat = byDate[d],
                            max = maxCount,
                            future = d > today,
                            onPick = onPick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (picked != null) {
            Text(picked, style = pt(12, PTWeight.medium), color = NC.ink2, modifier = Modifier.padding(top = 8.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("적게", style = pt(12), color = NC.ink3)
                Box(Modifier.size(12.dp).background(NC.subtle, RoundedCornerShape(3.dp)))
                for (l in 1..4) {
                    Box(Modifier.size(12.dp).background(RecordPatternFormat.levelColor(l), RoundedCornerShape(3.dp)))
                }
                Text("많이", style = pt(12), color = NC.ink3)
            }
            Spacer(Modifier.weight(1f))
            if (best != null) {
                Text(
                    "가장 많이 끝낸 날 · ${KST.formatMonthDay(best.date)} ${best.count}개",
                    style = pt(12),
                    color = NC.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }

        Text(
            buildAnnotatedString {
                append("16주 중 ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = brand)) { append("${recordedWeeks}주") }
                append(" Task를 완료했어요")
                append(rhythmNote)
            },
            style = pt(14).copy(lineHeight = 20.sp),
            color = NC.ink2,
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .background(NC.subtle, RoundedCornerShape(Radius.xxl))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun RecordRhythmCell(
    date: String,
    stat: RecordDayStat?,
    max: Int,
    future: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val level = stat?.let { RecordPatternFormat.levelOf(it.count, max = max) } ?: 0
    if (future) {
        // 아직 오지 않은 날은 빈칸 (색 없음)
        Spacer(modifier.aspectRatio(1f))
    } else {
        val desc = recordRhythmDescribe(date, stat)
        val shape = RoundedCornerShape(4.dp)
        Box(
            modifier
                .aspectRatio(1f)
                .clip(shape)
                .background(if (level > 0) RecordPatternFormat.levelColor(level) else NC.subtle, shape)
                .clickable { onPick(desc) }
                .semantics { contentDescription = desc },
        )
    }
}

// MARK: - 언제 이 일을 하나요? (요일 × 시간대)

private fun recordWhenKey(d: JavaDayOfWeek, b: RecordTimeBand): String = "${d.name}-${b.rawValue}"

@Composable
fun RecordWhenCard(`when`: RecordWhen, modifier: Modifier = Modifier) {
    var picked by remember { mutableStateOf<String?>(null) }
    // 칸을 처음 눌렀을 때 한 번 (record_stats.cell_tap)
    var tapSent by remember { mutableStateOf(false) }
    val statsGoalId = LocalRecordStatsGoalId.current
    val total = `when`.bands.sumOf { it.count }
    SectionCard("언제 이 일을 하나요?", modifier = modifier, aside = { Text("요일 × 시간대") }) {
        if (total < 3) {
            NotEnoughView(text = "시간을 정한 Task를 3개 이상 완료하면 주로 언제 하는지 보여드려요")
        } else {
            RecordWhenFilled(`when` = `when`, total = total, picked = picked, onPick = {
                picked = it
                if (!tapSent && statsGoalId != null) {
                    tapSent = true
                    WK.track("record_stats.cell_tap", goalId = statsGoalId, props = mapOf("stats_card" to "when"))
                }
            })
        }
    }
}

@Composable
private fun RecordWhenFilled(`when`: RecordWhen, total: Int, picked: String?, onPick: (String) -> Unit) {
    val cells: Map<String, RecordWhenCell> =
        `when`.cells.groupBy { recordWhenKey(it.dayOfWeek, it.band) }.mapValues { it.value.first() }
    val maxCount = `when`.cells.maxOfOrNull { it.count } ?: 0
    val pd = `when`.peakDay
    val pb = `when`.peakBand
    val peak: RecordWhenCell? = if (pd != null && pb != null) cells[recordWhenKey(pd, pb)] else null
    val topBand = `when`.bands.maxByOrNull { it.count }
    val totalCount = maxOf(total, 1)

    Column {
        // 머리줄: 시간대
        Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Spacer(Modifier.width(18.dp))
            for (b in RecordTimeBand.entries) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(b.label, style = pt(11), color = NC.ink3)
                    Text(b.range, style = pt(10), color = NC.ink4)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for (d in JavaDayOfWeek.entries) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.label, style = pt(12, PTWeight.medium), color = NC.ink2, modifier = Modifier.width(18.dp))
                    for (b in RecordTimeBand.entries) {
                        RecordWhenCellView(
                            day = d,
                            band = b,
                            cell = cells[recordWhenKey(d, b)],
                            max = maxCount,
                            onPick = onPick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (picked != null) {
            Text(picked, style = pt(12, PTWeight.medium), color = NC.ink2, modifier = Modifier.padding(top = 8.dp))
        }

        if (peak != null && pd != null && pb != null) {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(NC.brandSoft, RoundedCornerShape(Radius.xxl))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    PatternIconLabel("가장 많이 끝내는 때", icon = NIcon.trendingUp, tileBg = NC.statusSuccessBg, tileFg = NC.statusSuccessFg, modifier = Modifier.padding(bottom = 4.dp))
                    Text("${pd.label}요일 ${pb.label}", style = pt(16, PTWeight.bold), color = NC.ink)
                    Text(
                        "Task ${peak.count}개 · 전체의 ${Math.round(peak.count.toDouble() / totalCount * 100)}%",
                        style = pt(13),
                        color = NC.ink3,
                    )
                }
                if (topBand != null && topBand.count > 0) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(NC.subtle, RoundedCornerShape(Radius.xxl))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        PatternIconLabel("주로 하는 시간대", icon = NIcon.clock, tileBg = NC.bgSurface, tileFg = NC.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
                        Text("${topBand.band.label} ${topBand.band.range}", style = pt(16, PTWeight.bold), color = NC.ink)
                        Text(
                            "완료한 Task의 ${Math.round(topBand.count.toDouble() / totalCount * 100)}%",
                            style = pt(13),
                            color = NC.ink3,
                        )
                    }
                }
            }
        }

        if (`when`.untimedCount > 0) {
            Text(
                "시간을 정하지 않고 완료한 Task ${`when`.untimedCount}개는 이 표에서 뺐어요.",
                style = pt(12),
                color = NC.ink3,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun RecordWhenCellView(
    day: JavaDayOfWeek,
    band: RecordTimeBand,
    cell: RecordWhenCell?,
    max: Int,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = cell?.count ?: 0
    val level = RecordPatternFormat.levelOf(count, max = max)
    val minutes = cell?.minutes ?: 0
    val desc = if (count > 0) {
        "${day.label} ${band.label} · Task ${count}개" + (if (minutes > 0) " · ${RecordPatternFormat.shortDuration(minutes)}" else "")
    } else {
        "${day.label} ${band.label} · 완료 없음"
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .height(34.dp)
            .clip(shape)
            .background(if (level > 0) RecordPatternFormat.levelColor(level) else NC.subtle, shape)
            .clickable { onPick(desc) }
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 0) "${count}개" else "",
            style = pt(11, PTWeight.semibold),
            color = if (level >= 3) Color.White else NC.ink2,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

// MARK: - 무엇에 시간을 썼나요? (루틴별 완료 횟수 · 쌓인 시간)
//
// 루틴이 없는 목표에서는 그리지 않아요 (RecordPatternSectionView 에서 숨김).
// 최근 4주에 그 전보다 눈에 띄게 더 한 루틴에는 "요즘 늘었어요" 표시.

@Composable
fun RecordRoutineCard(routines: RecordRoutines, modifier: Modifier = Modifier) {
    val items = routines.items
    val totalMinutes = items.sumOf { it.minutes }
    // 루틴에 시간 정보가 하나도 없으면 막대를 횟수로 그려요
    val byTime = totalMinutes > 0
    val maxValue = maxOf(1, items.maxOfOrNull { if (byTime) it.minutes else it.completedCount } ?: 0)
    val brand = NC.brand
    SectionCard("무엇에 시간을 썼나요?", modifier = modifier, aside = { Text("루틴 · 처음부터") }) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items.forEachIndexed { i, r ->
                key(r.routineId) {
                    RecordRoutineRow(
                        routine = r,
                        first = i == 0,
                        byTime = byTime,
                        maxValue = maxValue,
                        rising = r.routineId == routines.risingRoutineId,
                    )
                }
            }
            val top = items.firstOrNull()
            if (byTime && top != null && top.minutes > 0 && items.size > 1) {
                Text(
                    buildAnnotatedString {
                        append("루틴 시간의 ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = brand)) {
                            append("${Math.round(top.minutes.toDouble() / totalMinutes * 100)}%")
                        }
                        append("를 ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(top.routineName) }
                        append("에 썼어요")
                    },
                    style = pt(14).copy(lineHeight = 20.sp),
                    color = NC.ink2,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .background(NC.subtle, RoundedCornerShape(Radius.xxl))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun RecordRoutineRow(routine: RecordRoutineStat, first: Boolean, byTime: Boolean, maxValue: Int, rising: Boolean) {
    val r = routine
    val value = if (byTime) r.minutes else r.completedCount
    val more = r.recentCount - r.previousCount
    val ink = NC.ink
    val ink3 = NC.ink3
    val success = NC.success
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                (r.emoji?.let { "$it " } ?: "") + r.routineName,
                style = pt(14, PTWeight.semibold),
                color = NC.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (rising) {
                Row(
                    Modifier
                        .background(NC.successSoft, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SfImage("chart.line.uptrend.xyaxis", size = 9.dp, tint = NC.success)
                    Text("요즘 늘었어요", style = pt(11, PTWeight.bold), color = NC.success, maxLines = 1)
                }
            }
            Text(
                buildAnnotatedString {
                    if (byTime) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ink)) { append(KST.formatDuration(r.minutes)) }
                        withStyle(SpanStyle(color = ink3)) { append(" · ${r.completedCount}번") }
                    } else {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ink)) { append("${r.completedCount}번") }
                    }
                },
                style = ptMono(13),
                maxLines = 1,
            )
        }
        ProgressBar(
            value = value.toDouble() / maxOf(maxValue, 1).toDouble(),
            color = if (first && value > 0) NC.brand else NC.heat2,
            height = 8.dp,
        )
        if (r.completedCount == 0) {
            Text("아직 완료한 날이 없어요", style = pt(12), color = NC.ink3)
        } else {
            Text(
                buildAnnotatedString {
                    append("최근 4주 ${r.recentCount}번")
                    if (more > 0 && r.previousCount > 0) {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = success)) {
                            append(" · 그 전 4주보다 ${more}번 더")
                        }
                    }
                },
                style = pt(12),
                color = NC.ink3,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 2400)
@Composable
private fun RecordPatternCardsPreview() {
    val p = RecordSampleData.pattern
    NaleulTheme(dark = false) {
        Column(
            Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RecordWeeklyCard(weeks = p.rhythm.weeks)
            RecordRhythmCard(rhythm = p.rhythm)
            RecordWhenCard(`when` = p.`when`)
            RecordRoutineCard(routines = p.routines)
            RecordWeeklyCard(weeks = emptyList())
        }
    }
}
