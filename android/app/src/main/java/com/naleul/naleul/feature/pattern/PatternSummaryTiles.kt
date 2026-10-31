// feature/pattern/PatternSummaryTiles.kt
//
// iOS Features/Pattern/PatternSummaryTiles.swift (웹 features/pattern/ui/SummaryTiles.tsx)
// 요약 4칸 (2×2): 전체 실행률 · 최고 연속 실행 · 평균 시작 지연 · AI 배치 수정률
//
// 두 칸의 높이를 맞추려고(iOS .fixedSize + maxHeight: .infinity) Row 에 Modifier.height(IntrinsicSize.Min) 을 주고
// 칸마다 Modifier.weight(1f).fillMaxHeight() 를 줘요.

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

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

enum class PatternTileTone {
    up, down;

    companion object {}
}

/**
 * 요약 칸 하나 (기록형 목표의 요약 칸도 같은 모양이라 같이 써요)
 * Row 안에서 두 칸 높이를 맞추려면 modifier = Modifier.weight(1f).fillMaxHeight() 를 넘겨요.
 */
@Composable
fun PatternTile(
    label: String,
    value: String,
    sub: String? = null,
    tone: PatternTileTone? = null,
    valueSize: Int = 26,
    modifier: Modifier = Modifier,
) {
    val subColor: Color = when (tone) {
        PatternTileTone.up -> NC.success
        PatternTileTone.down -> NC.danger
        null -> NC.ink3
    }
    Column(
        modifier
            .defaultMinSize(minHeight = 112.dp)
            .card()
            .padding(16.dp),
    ) {
        Text(label, style = NType.captionMD, color = NC.textSecondary)
        // iOS Spacer(minLength: 4) — 최소 4 띄우고 남는 높이는 여기서 채워요
        Spacer(Modifier.height(4.dp))
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = NType.h2,
            color = NC.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (sub != null) {
            Text(
                sub,
                style = pt(13, PTWeight.semibold),
                color = subColor,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** 칸 하나의 내용 (iOS 는 PatternTile 뷰를 바로 만들었지만, Compose 는 값만 모아 두고 나중에 그려요) */
private data class PatternTileSpec(
    val label: String,
    val value: String,
    val sub: String? = null,
    val tone: PatternTileTone? = null,
)

private const val PATTERN_TILE_DASH = "–"

private fun patternRateTile(s: PatternSummary?): PatternTileSpec {
    val value = s?.executionRate?.let { "$it%" } ?: PATTERN_TILE_DASH
    val delta = s?.firstWeekDelta
    if (delta != null) {
        return PatternTileSpec(
            label = "전체 실행률",
            value = value,
            sub = "${if (delta >= 0) "▲" else "▼"} ${abs(delta)}%p 첫 주 대비",
            tone = if (delta >= 0) PatternTileTone.up else PatternTileTone.down,
        )
    }
    return PatternTileSpec(label = "전체 실행률", value = value, sub = "기록이 쌓이면 보여드려요")
}

private fun patternStreakTile(s: PatternSummary?): PatternTileSpec {
    val best = s?.bestStreak ?: 0
    val current = s?.currentStreak ?: 0
    return PatternTileSpec(
        label = "최고 연속 실행",
        value = (if (best > 0) "🔥 " else "") + "${best}일",
        sub = if (current > 0) "현재 ${current}일째" else "오늘부터 다시 시작해봐요",
    )
}

private fun patternDelayTile(s: PatternSummary?): PatternTileSpec {
    val value = s?.avgStartDelayMinutes?.let { PatternFormat.signedMinutes(it) } ?: PATTERN_TILE_DASH
    val parts = mutableListOf<String>()
    s?.morningDelayMinutes?.let { parts.add("아침 ${PatternFormat.signedMinutes(it)}") }
    s?.eveningDelayMinutes?.let { parts.add("저녁 ${PatternFormat.signedMinutes(it)}") }
    val sub = if (parts.isEmpty()) "완료할 때 실제 시각을 남기면 더 정확해요" else parts.joinToString(" · ")
    return PatternTileSpec(label = "평균 시작 지연", value = value, sub = sub)
}

private fun patternAiTile(s: PatternSummary?): PatternTileSpec {
    val value = s?.aiPlacementEditRate?.let { "$it%" } ?: PATTERN_TILE_DASH
    val sub = s?.aiPlacementKeptPerTen?.let { "10개 중 ${it}개는 그대로 사용" } ?: "AI 배치 기록이 쌓이면 보여드려요"
    return PatternTileSpec(label = "AI 배치 수정률", value = value, sub = sub)
}

@Composable
fun PatternSummaryTiles(summary: PatternSummary?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PatternTileRow(patternRateTile(summary), patternStreakTile(summary))
        PatternTileRow(patternDelayTile(summary), patternAiTile(summary))
    }
}

/** 두 칸을 같은 높이로 */
@Composable
private fun PatternTileRow(a: PatternTileSpec, b: PatternTileSpec) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (t in listOf(a, b)) {
            PatternTile(
                label = t.label,
                value = t.value,
                sub = t.sub,
                tone = t.tone,
                modifier = Modifier.weight(1f).fillMaxHeight().patternDeadTap("summary_tile"),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PatternSummaryTilesPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PatternSummaryTiles(summary = PatternSampleData.report.summary)
            PatternSummaryTiles(summary = null)
        }
    }
}
