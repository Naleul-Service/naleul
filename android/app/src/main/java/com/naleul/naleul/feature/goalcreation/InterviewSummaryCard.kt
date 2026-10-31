// feature/goalcreation/InterviewSummaryCard.kt
//
// iOS Features/GoalCreation/InterviewSummaryCard.swift
// 인터뷰 결과 요약 (목표 · 수치 · 기한 · 계획 · 실천 시간 · 성향). 예전 세션은 이유도.
// 웹 원본: features/goal-creation/ui/InterviewSummaryCard.tsx
// (웹에서도 아직 쓰는 곳은 없고 "재사용 예정"이에요. 정리 확인 화면은 고칠 수 있는 줄을 따로 그려요)

package com.naleul.naleul.feature.goalcreation

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun InterviewSummaryCard(summary: GoalCreationSummary, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        InterviewSummaryRow("목표") { InterviewSummaryValue(summary.goalStatement) }
        HorizontalDivider(color = NC.borderSoft)
        InterviewSummaryRow("수치") { InterviewSummaryValue(summary.metricText) }
        HorizontalDivider(color = NC.borderSoft)
        InterviewSummaryRow("기한") { InterviewSummaryValue(summary.deadlineText) }
        HorizontalDivider(color = NC.borderSoft)
        InterviewSummaryRow("계획") { InterviewSummaryValue(summary.approach) }
        HorizontalDivider(color = NC.borderSoft)
        // 예전 세션(이유를 묻던 때)에 답한 이유가 있으면 보여줘요
        val motivation = summary.motivation
        if (!motivation.isNullOrEmpty()) {
            InterviewSummaryRow("이유") { InterviewSummaryValue(motivation) }
            HorizontalDivider(color = NC.borderSoft)
        }
        InterviewSummaryRow("실천 시간") { InterviewSummaryValue(summary.preferenceText) }
        HorizontalDivider(color = NC.borderSoft)
        InterviewSummaryRow("성향") {
            val style = summary.planningStyle
            if (style != null) NBadge(style.goalCreationLabel, tone = BadgeTone.brand) else InterviewSummaryEmpty()
        }
    }
}

@Composable
private fun InterviewSummaryEmpty() {
    Text("AI가 알맞게 정해줄게요", style = pt(15, PTWeight.medium), color = NC.ink4)
}

@Composable
private fun InterviewSummaryValue(v: String?) {
    if (v != null) Text(v, style = pt(15, PTWeight.medium), color = NC.ink) else InterviewSummaryEmpty()
}

@Composable
private fun InterviewSummaryRow(label: String, value: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, style = pt(13), color = NC.ink3, modifier = Modifier.width(64.dp).padding(top = 2.dp))
        Box(Modifier.weight(1f)) { value() }
    }
}

@Preview(showBackground = true)
@Composable
private fun InterviewSummaryCardPreview() {
    NaleulTheme(dark = false) {
        Box(Modifier.background(NC.surface).padding(16.dp)) {
            GoalCreationSamples.session.summary?.let { InterviewSummaryCard(summary = it) }
        }
    }
}
