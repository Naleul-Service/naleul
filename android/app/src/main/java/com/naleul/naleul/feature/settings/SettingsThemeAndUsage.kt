// feature/settings/SettingsThemeAndUsage.kt
//
// iOS Features/Settings/SettingsThemeAndUsage.swift
// 웹 원본: features/theme/ThemePicker.tsx (설정 > 화면 모드)
//          features/usage/ui/UsageCard.tsx (설정 > 오늘 사용량)
//
// 화면 모드는 ThemeStore.set(...) 으로 저장만 하면 NaleulTheme 이 바로 적용해요 (iOS @AppStorage).

package com.naleul.naleul.feature.settings

import com.naleul.naleul.feature.goal.GD
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

// MARK: - 화면 모드

@Composable
fun SettingsThemePicker() {
    val options = ThemePref.entries.map { it to it.label }
    val icons = ThemePref.entries.associateWith { it.icon }
    // 웹은 한 줄(제목 | 버튼)이지만 휴대폰 폭에 맞게 위아래로 놓았어요
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("화면 모드", style = pt(15, PTWeight.bold), color = NC.ink)
            Text("시스템을 고르면 기기 설정(라이트·다크)을 따라가요", style = pt(13), color = NC.ink3)
        }
        SegmentPicker(
            selection = ThemeStore.pref,
            onSelectionChange = { ThemeStore.set(it) },
            options = options,
            icons = icons,
            fullWidth = true,
            modifier = Modifier.semantics { contentDescription = "화면 모드" },
        )
    }
}

// MARK: - 오늘 사용량

class SettingsUsageViewModel(sample: AiUsage? = null) : ScreenModel() {
    var usage by mutableStateOf<AiUsage?>(null)
    var isPending by mutableStateOf(true)
    private val isSample: Boolean = sample != null

    init {
        if (sample != null) {
            usage = sample
            isPending = false
        }
    }

    private var viewSent = false

    suspend fun load() {
        if (isSample) return
        var ok = true
        try {
            usage = UsageAPI.usage()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 웹처럼 실패하면 빈 목록으로 (설정 화면 전체를 막지 않아요)
            ok = false
        }
        isPending = false
        // [UX] 한도를 다 쓴 기능이 있어도 카드 안에 Pro 로 가는 버튼이 없어요 — 처음 불러왔을 때 한 번
        if (!viewSent) {
            viewSent = true
            val exhausted = usage?.items?.filter { it.limit != null && (it.remaining ?: 0) <= 0 }?.joinToString(",") { it.key }
            ST.track("settings.usage_view", mapOf(
                "is_premium" to (usage?.pro ?: ST.isPremium),
                "exhausted_keys" to exhausted,
                "result" to if (ok) "success" else "fail",
            ))
        }
    }

    companion object {}
}

/**
 * 하루 횟수가 정해진 기능 (AI 목표 만들기 · AI 계획 초안 · Task 추가)
 * @param sample 미리보기용 (null 이면 서버에서 불러와요)
 */
@Composable
fun SettingsUsageCard(sample: AiUsage? = null) {
    val vm = rememberScreenModel(sample) { SettingsUsageViewModel(sample) }
    ReloadOn(listOf(DataKey.usage)) { vm.load() }

    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(NC.surface, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("오늘 사용량", style = pt(16, PTWeight.bold), color = NC.ink)
            val usage = vm.usage
            if (usage != null) {
                Text(
                    if (usage.pro) "Pro 한도" else "무료 한도",
                    style = pt(11, PTWeight.semibold),
                    color = NC.ink2,
                    modifier = Modifier.background(NC.subtle, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
        }
        Text("매일 0시(한국 시간)에 다시 채워져요", style = pt(12), color = NC.ink3, modifier = Modifier.padding(top = 2.dp))

        if (vm.isPending) {
            Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NC.ink3, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
        } else {
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (item in vm.usage?.items ?: emptyList()) {
                    SettingsUsageRow(item = item)
                }
            }
        }
    }
}

@Composable
private fun SettingsUsageRow(item: UsageItem) {
    val limit = item.limit
    val ratio: Double = if (limit == null || limit <= 0) 0.0 else minOf(item.used.toDouble() / limit.toDouble(), 1.0)
    val barColor = if (ratio >= 1) NC.actionDanger else if (ratio >= 0.7) NC.warning else NC.progressFill

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.label, style = pt(14, PTWeight.semibold), color = NC.ink)
                if (item.description.isNotEmpty()) {
                    Text(item.description, style = pt(12), color = NC.ink3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (limit != null) {
                val left = item.remaining ?: 0
                val leftColor = if (left == 0) NC.danger else NC.ink
                val restColor = NC.ink3
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = leftColor)) { append("$left") }
                        withStyle(SpanStyle(color = restColor)) { append(" / ${limit}회 남음") }
                    },
                    style = ptMono(13),
                    maxLines = 1,
                )
            } else {
                Text("제한 없음", style = pt(13), color = NC.ink3)
            }
        }
        if (limit != null) {
            ProgressBar(value = ratio, color = barColor, height = 6.dp)
        }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun SettingsThemeAndUsagePreview() {
    NaleulTheme(dark = false) {
        Column(
            Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.card()) { SettingsThemePicker() }
            SettingsUsageCard(
                sample = AiUsage(
                    pro = false, resetsAt = "2026-10-10T00:00:00",
                    items = listOf(
                        UsageItem(key = "GOAL_SESSION", label = "AI 목표 만들기", description = "목표를 대화로 설계해요", used = 2, limit = 3, remaining = 1),
                        UsageItem(key = "GOAL_DRAFT", label = "AI 계획 초안", description = "목표마다 계획 초안을 만들어요", used = 3, limit = 3, remaining = 0),
                        UsageItem(key = "TASK_PER_DAY", label = "Task 추가", description = "하루에 추가할 수 있는 Task", used = 1, limit = null, remaining = null),
                    ),
                    session = null,
                ),
            )
        }
    }
}
