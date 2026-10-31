// model/UsageAndColor.kt
//
// iOS Models/UsageAndColor.swift 를 옮긴 파일.
// 웹 features/usage (오늘 AI 사용량) · features/color (사용자 색상)

package com.naleul.naleul.model

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MARK: - 오늘 AI 사용량 (GET /v1/ai-usage)

/** 하루 횟수가 정해진 기능의 오늘 사용량 */
@Serializable
data class UsageItem(
    /** GOAL_SESSION · GOAL_SESSION_WEEK · GOAL_DRAFT · GOAL_EDIT · BRAIN_DUMP · TASK_PER_DAY · SESSION_REGENERATE */
    val key: String,
    val label: String,
    val description: String,
    val used: Int,
    /** null 이면 제한 없음 */
    val limit: Int? = null,
    val remaining: Int? = null,
) {
    val id: String get() = key

    companion object {}
}

@Serializable
data class AiUsage(
    val pro: Boolean,
    /** 다시 채워지는 시각 (내일 0시, 한국 시간) */
    val resetsAt: String,
    val items: List<UsageItem>,
    /** sessionId 를 넘겼을 때: 그 목표 대화에서 계획을 만들 수 있는 횟수 */
    val session: UsageItem? = null,
) {
    fun item(key: String): UsageItem? = items.firstOrNull { it.key == key }

    /** 하루 · 주 · 월 중 가장 먼저 바닥나는 한도 (2026-10 — 예: "GOAL_DRAFT" → GOAL_DRAFT · _WEEK · _MONTH) */
    fun tightest(baseKey: String): UsageItem? =
        listOf(baseKey, "${baseKey}_WEEK", "${baseKey}_MONTH")
            .mapNotNull { item(it) }
            .filter { it.remaining != null }
            .minByOrNull { it.remaining ?: Int.MAX_VALUE }

    companion object {}
}

object UsageAPI {
    /** 횟수를 쓰는 동작(목표 대화 시작·초안 생성·Task 추가) 뒤에는 Invalidation.invalidate(DataKey.usage) */
    suspend fun usage(sessionId: Int? = null): AiUsage? =
        API.get<AiUsage>("/v1/ai-usage" + (sessionId?.let { "?sessionId=$it" } ?: ""))
}

/**
 * "오늘 AI 목표 만들기 3/5회 남음" — 한도가 없으면 아무것도 안 그려요 (웹 UsageLine)
 *  - onDark: true = bg/inverse 바탕 위 (글자는 onInk)
 */
@Composable
fun UsageLine(
    items: List<UsageItem?>,
    onDark: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val limited = items.filterNotNull().filter { it.limit != null }
    if (limited.isEmpty()) return

    val baseColor: Color =
        if (onDark) NC.onInk.copy(alpha = 0.7f)
        else if (limited.any { it.remaining == 0 }) NC.danger
        else NC.ink3
    // SwiftUI 의 color.opacity(0.5) 는 원래 투명도에 곱해져요 → alpha * 0.5
    val dimmed = baseColor.copy(alpha = baseColor.alpha * 0.5f)
    val hint = baseColor.copy(alpha = baseColor.alpha * 0.7f)
    val numberColor = if (onDark) NC.onInk else NC.ink2
    val weekly = items.any { it?.key?.endsWith("_WEEK") == true }

    // iOS 는 Text + Text 로 이어 붙였어요 → Compose 는 AnnotatedString 으로 글자마다 색·굵기를 줘요
    val line = buildAnnotatedString {
        limited.forEachIndexed { n, i ->
            if (n > 0) withStyle(SpanStyle(color = dimmed)) { append("  ·  ") }
            append(i.label + " ")
            withStyle(SpanStyle(color = numberColor, fontWeight = FontWeight.Bold)) { append((i.remaining ?: 0).toString()) }
            append("/" + (i.limit ?: 0).toString() + "회 남음")
        }
        withStyle(SpanStyle(color = hint)) {
            append(if (weekly) "  (하루 횟수는 매일, 주간 횟수는 월요일 0시에 다시 채워져요)" else "  (매일 0시에 다시 채워져요)")
        }
    }

    Row(modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // 아이콘을 첫 줄 글자 높이에 맞춰요 (iOS .firstTextBaseline)
        SfImage("gauge.with.dots.needle.33percent", size = 11.dp, tint = baseColor, modifier = Modifier.padding(top = 3.dp))
        Text(line, style = pt(12), color = baseColor)
    }
}

// MARK: - 사용자 색상 (GET /v1/user-colors)

/** ⚠️ Lombok `boolean isDefault` → Jackson 이 "default" 로 내보내요. 두 이름 모두 대비 (아래 UserColorRaw) */
data class UserColor(
    val userColorId: Int,
    /** 항상 "#RRGGBB" */
    val hex: String,
    val isDefault: Boolean,
) {
    val id: Int get() = userColorId

    val color: Color get() = colorHex(hex)

    companion object {}
}

@Serializable
private data class UserColorRaw(
    val userColorId: Int,
    val colorCode: String,
    val isDefault: Boolean? = null,
    @SerialName("default") val defaultFlag: Boolean? = null,
)

object ColorAPI {
    suspend fun userColors(): List<UserColor> {
        val list = API.get<List<UserColorRaw>>("/v1/user-colors")
        return (list ?: emptyList()).map {
            UserColor(userColorId = it.userColorId, hex = hexOf(it.colorCode), isDefault = it.isDefault ?: it.defaultFlag ?: false)
        }
    }
}

/**
 * 목표 색 고르기 (원 버튼 줄).
 * iOS @Binding var selectedId → selectedId + onSelectedIdChange 두 개
 */
@Composable
fun ColorPickerRow(
    colors: List<UserColor>,
    selectedId: Int?,
    onSelectedIdChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = NC.ink
    FlowLayout(spacing = 10.dp, modifier = modifier) {
        for (c in colors) {
            val selected = selectedId == c.userColorId
            val ringColor = ring.copy(alpha = if (selected) 0.25f else 0f)
            Box(
                Modifier
                    .size(30.dp)
                    // 원 바깥 3dp 테두리 (iOS .overlay(Circle().stroke(...).padding(-3))) — 자리 크기는 그대로 30dp
                    .drawBehind {
                        val w = 3.dp.toPx()
                        drawCircle(color = ringColor, radius = size.minDimension / 2 + w, style = Stroke(width = w))
                    }
                    .clip(CircleShape)
                    .background(c.color)
                    .clickable { onSelectedIdChange(c.userColorId) }
                    .semantics { contentDescription = "색 ${c.hex}" },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) SfImage("checkmark", size = 12.dp, tint = Color.White)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UsageAndColorPreview() {
    NaleulTheme(dark = false) {
        var selected by remember { mutableStateOf<Int?>(1) }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            UsageLine(
                items = listOf(
                    UsageItem(key = "GOAL_SESSION", label = "AI 목표 만들기", description = "", used = 2, limit = 5, remaining = 3),
                    UsageItem(key = "TASK_PER_DAY", label = "Task 추가", description = "", used = 2, limit = 2, remaining = 0),
                )
            )
            ColorPickerRow(
                colors = listOf(
                    UserColor(userColorId = 1, hex = "#3D5AFE", isDefault = true),
                    UserColor(userColorId = 2, hex = "#12B76A", isDefault = false),
                    UserColor(userColorId = 3, hex = "#F59E0B", isDefault = false),
                ),
                selectedId = selected,
                onSelectedIdChange = { selected = it },
            )
        }
    }
}
