// feature/pattern/PatternWeekdayBars.kt
//
// iOS Features/Pattern/PatternWeekdayBars.swift (웹 features/pattern/ui/WeekdayBars.tsx)
// 요일별 실행률 막대 — 가장 낮은 요일만 파란색

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val PATTERN_WEEKDAY_BAR_MAX = 128f

@Composable
fun PatternWeekdayBars(weekday: PatternWeekday?, modifier: Modifier = Modifier) {
    val hasData = (weekday?.days ?: emptyList()).any { it.rate != null }
    SectionCard("요일별 실행률", modifier = modifier, aside = {
        val avg = weekday?.average
        if (avg != null) Text("평균 $avg%")
    }) {
        if (weekday != null && hasData) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 24.dp).patternChartTap("weekday"),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (d in weekday.days ?: emptyList()) {
                        PatternWeekdayBar(
                            day = d,
                            low = d.dayOfWeek == weekday.lowest,
                            barMax = PATTERN_WEEKDAY_BAR_MAX,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                val insight = weekday.insight
                if (!insight.isNullOrEmpty()) {
                    Text(
                        PatternEmphasis.text(insight, color = NC.textBrand),
                        style = pt(14).copy(lineHeight = 20.sp),
                        color = NC.ink2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NC.subtle, RoundedCornerShape(Radius.xxl))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        } else {
            NotEnoughView()
        }
    }
}

@Composable
private fun PatternWeekdayBar(day: PatternWeekdayDay, low: Boolean, barMax: Float, modifier: Modifier = Modifier) {
    val height: Float = day.rate?.let { maxOf(6f, it / 100f * barMax) } ?: 4f
    Column(
        modifier.clearAndSetSemantics {
            contentDescription = "${PatternFormat.dayShort(day.dayOfWeek)} ${day.rate?.toString() ?: "-"}%"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            day.rate?.let { "$it%" } ?: "–",
            style = pt(12, PTWeight.semibold),
            color = if (low) NC.ink else NC.ink3,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Box(
            Modifier
                .widthIn(max = 36.dp)
                .fillMaxWidth()
                .height(height.dp)
                .background(if (low) NC.brand else NC.lineStrong, RoundedCornerShape(6.dp)),
        )
        Text(
            PatternFormat.dayShort(day.dayOfWeek),
            style = pt(13, if (low) PTWeight.bold else PTWeight.regular),
            color = if (low) NC.ink else NC.ink3,
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun PatternWeekdayBarsPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PatternWeekdayBars(weekday = PatternSampleData.report.weekday)
            PatternWeekdayBars(weekday = null)
        }
    }
}
