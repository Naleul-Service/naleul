// feature/pattern/PatternStyleCard.kt
//
// iOS Features/Pattern/PatternStyleCard.swift (웹 features/pattern/ui/StyleCard.tsx)
// 상단 검은 카드: AI가 분석한 나의 실행 스타일.
// 기록이 부족하면 빈 화면 대신 "얼마나 더 하면 열리는지" 진행 막대를 보여줘요 (Locked).

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PatternStyleCard(style: PatternStyle?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .aiBorder(RoundedCornerShape(Radius.card), glass = false, tint = NC.aiTintSoft)
            .padding(24.dp)
            .semantics { contentDescription = "나의 실행 스타일" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AIBadge(AIBadgeSize.sm)
            Text("AI가 분석한 나의 실행 스타일", style = NType.labelMD, color = NC.textSecondary)
        }
        val title = style?.title
        if (style != null && !title.isNullOrEmpty()) {
            Text(
                patternStyleTitle(title, highlight = style.highlight, accent = NC.aiGradient()),
                style = pt(22, PTWeight.bold),  // 22 · Bold · −0.02em (pt 기본 자간)
                color = NC.textPrimary,
                modifier = Modifier.padding(top = 8.dp).patternDeadTap("style_sentence"),
            )
            Column(Modifier.padding(top = 20.dp).patternDeadTap("style_sentence"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (ins in style.insights ?: emptyList()) {
                    PatternInsightRow(insight = ins)
                }
            }
        } else {
            PatternStyleLocked(style = style)
        }
    }
}

/** "저녁에 강한 꾸준형" 에서 "저녁에 강한" 부분만 파란색 */
private fun patternStyleTitle(title: String, highlight: String?, accent: Brush): AnnotatedString {
    if (highlight.isNullOrEmpty() || !title.startsWith(highlight)) return AnnotatedString(title)
    return buildAnnotatedString {
        withStyle(SpanStyle(brush = accent)) { append(highlight) }
        append(title.drop(highlight.length))
    }
}

@Composable
private fun PatternInsightRow(insight: PatternInsight) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).background(NC.bgSurface, RoundedCornerShape(Radius.lg)),
            contentAlignment = Alignment.Center,
        ) {
            Text(PatternFormat.insightIcon[insight.icon] ?: "•", fontSize = 14.sp)
        }
        Text(
            PatternEmphasis.text(insight.text, color = NC.textPrimary),
            style = pt(14).copy(lineHeight = 20.sp),
            color = NC.textSecondary,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** 아직 기록이 부족할 때 — 예) 실행 스타일까지 Task 15개 · 4일 더 */
@Composable
fun PatternStyleLocked(style: PatternStyle?, modifier: Modifier = Modifier) {
    val p = style?.progress
    val remain: String? = p?.let {
        val parts = mutableListOf<String>()
        if (it.remainingTasks > 0) parts.add("Task ${it.remainingTasks}개")
        if (it.remainingDays > 0) parts.add("${it.remainingDays}일")
        parts.joinToString(" · ")
    }
    val n = style?.remainingForStyle
    val message = when {
        p != null -> "Task ${p.requiredTasks}개를 ${p.requiredDays}일 이상에 걸쳐 실행하면 아침형·저녁형, 꾸준형·몰아치기형 같은 나만의 스타일을 알려드려요. 오늘 완료한 Task도 바로 반영돼요."
        n != null && n > 0 -> "Task를 ${n}개 더 실행하면 나의 실행 스타일을 알려드릴게요."
        else -> "Task를 꾸준히 실행하면 나의 실행 스타일을 알려드릴게요."
    }
    val headline: AnnotatedString = if (!remain.isNullOrEmpty()) {
        buildAnnotatedString {
            append("실행 스타일까지 ")
            withStyle(SpanStyle(color = NC.textBrand)) { append(remain) }
            append(" 더")
        }
    } else {
        AnnotatedString("아직 스타일을 알기엔 기록이 적어요")
    }

    Column(modifier.padding(top = 12.dp)) {
        Text(headline, style = pt(22, PTWeight.bold), color = NC.textPrimary)
        Text(
            message,
            style = pt(14).copy(lineHeight = 20.sp),
            color = NC.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (p != null) {
            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PatternLockedBar(label = "실행한 Task", value = p.evaluated, max = p.requiredTasks, unit = "개")
                PatternLockedBar(label = "실행한 날", value = p.activeDays, max = p.requiredDays, unit = "일")
            }
        }
    }
}

@Composable
private fun PatternLockedBar(label: String, value: Int, max: Int, unit: String) {
    val ratio = minOf(value.toDouble() / maxOf(max, 1).toDouble(), 1.0)
    Column(
        Modifier.semantics { contentDescription = "$label $value / $max$unit" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = pt(13), color = NC.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = NC.textPrimary, fontWeight = FontWeight.SemiBold)) { append("${minOf(value, max)}") }
                    withStyle(SpanStyle(color = NC.textTertiary)) { append(" / $max$unit") }
                },
                style = ptMono(13),
            )
        }
        ProgressBar(value = ratio, color = NC.progressFill, track = NC.progressTrack, height = 6.dp)
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun PatternStyleCardPreview() {
    NaleulTheme(dark = false) {
        Column(
            Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PatternStyleCard(style = PatternSampleData.report.style)
            PatternStyleCard(style = PatternSampleData.emptyReport.style)
            PatternStyleCard(style = null)
        }
    }
}
