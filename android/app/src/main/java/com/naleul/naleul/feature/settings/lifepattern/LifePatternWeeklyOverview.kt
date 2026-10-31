// feature/settings/lifepattern/LifePatternWeeklyOverview.kt
//
// iOS Features/Settings/LifePattern/LifePatternWeeklyOverview.swift
// 웹 원본: features/life-pattern/ui/WeeklyOverview.tsx
//
// 요일별로 고정 시간이 어떻게 깔리는지 한눈에 보는 막대.
// 자정을 넘기는 패턴(수면 23:00~07:00)은 시작한 요일에 23:00~24:00, 다음 요일에 00:00~07:00 으로 나눠 그려요.
// 요일별 시간(예: 주말 수면 01:00~09:30)과 "전날/다음 날 시작"도 반영해요.
// (백엔드 FixedBlockCalculator 와 같은 규칙: 요일 판정은 블록 "소속 요일" 기준)

package com.naleul.naleul.feature.settings

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 한 요일 줄에 그릴 막대 하나 (파일 밖에서 안 써요) */
private data class LifePatternOverviewSegment(
    val id: String,
    val pattern: LifePattern,
    /** 0 ~ 1440 */
    val start: Int,
    val end: Int,
)

private val lifePatternOverviewHours = listOf(0, 6, 12, 18, 24)

/** 요일(월~일)별 막대 목록 */
private fun lifePatternOverviewRows(patterns: List<LifePattern>): List<List<LifePatternOverviewSegment>> {
    val rows = List(7) { mutableListOf<LifePatternOverviewSegment>() }
    val all = LifePatternRules.allDays
    for (p in patterns) {
        val days = p.days.ifEmpty { all }
        val times = LifePatternTimes(p)
        for (d in days) {
            val i = d.index
            val occ = LifePatternRules.occurrenceOf(times, d)   // 소속 요일 0시 기준 분 (전날·다음 날로 넘어갈 수 있음)
            // 하루(1440분)씩 잘라서 해당 요일 줄에 그려요
            var dayStart = LifePatternRules.floorDiv(occ.start, 1440) * 1440
            while (dayStart < occ.end) {
                val s = maxOf(occ.start, dayStart) - dayStart
                val e = minOf(occ.end, dayStart + 1440) - dayStart
                if (e > s) {
                    // dayStart / 1440 은 음수일 수 있어서 floorDiv 로 (Swift 는 나머지 보정으로 같은 결과)
                    val row = Math.floorMod(i + LifePatternRules.floorDiv(dayStart, 1440), 7)
                    rows[row].add(
                        LifePatternOverviewSegment(id = "${p.lifePatternId}-${d.name}-$dayStart", pattern = p, start = s, end = e)
                    )
                }
                dayStart += 1440
            }
        }
    }
    return rows
}

@Composable
fun LifePatternWeeklyOverview(patterns: List<LifePattern>, onSelect: (LifePattern) -> Unit) {
    val rows = lifePatternOverviewRows(patterns)
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CardHeader("한 주 미리보기")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LifePatternOverviewHourScale()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 7) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            LifePatternRules.allDays[i].label,
                            style = pt(12, if (i >= 5) PTWeight.regular else PTWeight.medium),
                            color = if (i >= 5) NC.ink3 else NC.ink2,
                            modifier = Modifier.width(20.dp),
                        )
                        LifePatternOverviewBar(segments = rows[i], onSelect = onSelect, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 시간 눈금 (0 · 6 · 12 · 18 · 24) */
@Composable
private fun LifePatternOverviewHourScale() {
    // 요일 글자(20) + 간격(8)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 28.dp).height(16.dp)) {
        val w = maxWidth
        for (h in lifePatternOverviewHours) {
            // 글자 가운데가 눈금 위치에 오게 (iOS .position(x:)) — 폭 24dp 상자 가운데 정렬
            Box(
                Modifier.width(24.dp).fillMaxHeight().offset(x = w * (h / 24f) - 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("$h", style = pt(10), color = NC.ink4, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** 한 요일 막대 (가로 폭 = 24시간) */
@Composable
private fun LifePatternOverviewBar(
    segments: List<LifePatternOverviewSegment>,
    onSelect: (LifePattern) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    BoxWithConstraints(modifier.height(24.dp).clip(shape).background(NC.subtle, shape)) {
        val w = maxWidth
        for (h in listOf(6, 12, 18)) {
            Box(Modifier.offset(x = w * (h / 24f)).width(1.dp).fillMaxHeight().background(NC.line))
        }
        for (seg in segments) {
            val x = w * (seg.start / 1440f)
            val width = maxOf(w * ((seg.end - seg.start) / 1440f), 2.dp)
            val showTitle = seg.end - seg.start >= 90
            val end = if (seg.end == 1440) "24:00" else KST.minutesToTime(seg.end)
            val label = "${seg.pattern.title} ${KST.minutesToTime(seg.start)}–$end"
            Box(
                Modifier
                    .offset(x = x, y = 2.dp)
                    .width(width)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(LifePatternRules.typeColor(seg.pattern.patternType))
                    .clickable(role = Role.Button) { onSelect(seg.pattern) }
                    .semantics { contentDescription = label }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (showTitle) {
                    Text(
                        seg.pattern.title,
                        style = pt(10, PTWeight.semibold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LifePatternWeeklyOverviewPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
            LifePatternWeeklyOverview(patterns = LifePatternSamples.patterns, onSelect = {})
        }
    }
}
