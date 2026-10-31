// feature/pattern/PatternSuggestionBarView.kt
//
// iOS Features/Pattern/PatternSuggestionBarView.swift
// 웹 features/pattern/ui/SuggestionBar.tsx (+ api.ts useApplySuggestions · useDismissSuggestions)
// 제안 바 + [TimeBlock에 반영하기] 확인 시트 (웹 Modal → 아래에서 올라오는 시트)

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
import androidx.compose.runtime.*
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PatternSuggestionBarView(suggestion: PatternSuggestionBar?, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    /** 시트가 어떻게 끝났는지 — "saved"(반영) · "cancel_button" · null(X 는 sheetCloseHint, 나머지는 아래로 쓸어 닫기 · 뒤로 가기) */
    var outcome by remember { mutableStateOf<String?>(null) }
    var openedAt by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()

    val items = suggestion?.items
    if (suggestion == null || items.isNullOrEmpty()) return

    /**
     * 제안 여러 개를 차례로 반영해요 (하나 실패해도 나머지는 계속).
     * 루틴 · TimeTable · 목표 화면이 모두 바뀌므로 관련 데이터를 전부 다시 불러와요.
     */
    suspend fun applyAll(keys: List<String>) {
        applying = true
        var applied = 0
        val failed = mutableListOf<String>()
        var firstStatus: Int? = null
        for (key in keys) {
            try {
                PatternAPI.applySuggestion(key = key)
                applied += 1
            } catch (e: CancellationException) {
                applying = false
                throw e
            } catch (e: Exception) {
                failed.add(errorMessage(e))
                if (firstStatus == null) firstStatus = PT.status(e)
            }
        }
        PT.track("pattern.suggestion_apply_result", mapOf(
            "result" to if (failed.isEmpty()) "success" else "fail",
            "applied_count" to applied, "skipped_count" to failed.size, "error_status" to firstStatus,
        ))
        if (applied > 0 && failed.isEmpty()) {
            Toast.success("TimeBlock에 반영했어요. 내일부터 적용돼요.")
        } else if (applied > 0) {
            Toast.error("${applied}개는 반영했지만 ${failed.size}개는 못 했어요. ${failed[0]}")
        } else if (failed.isNotEmpty()) {
            Toast.error(failed.first())
        }
        Invalidation.invalidate(DataKey.pattern, DataKey.timetable, DataKey.goals)
        applying = false
    }

    suspend fun dismissAll(keys: List<String>) {
        dismissing = true
        try {
            Mutation.run(invalidate = listOf(DataKey.pattern), successMessage = "이 제안은 일주일 동안 보이지 않아요.") {
                for (key in keys) PatternAPI.dismissSuggestion(key = key)
            }
            PT.track("pattern.suggestion_dismiss_result", GD.result() + mapOf("item_count" to keys.size))
        } finally {
            dismissing = false
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .aiBorder()
            .padding(20.dp)
            .semantics { contentDescription = "패턴 기반 제안" },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            AIBadge()
            Text(
                PatternEmphasis.text(suggestion.text, color = NC.textBrand),
                style = pt(15).copy(lineHeight = 21.sp),
                color = NC.ink2,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NButton("TimeBlock에 반영하기", size = NButtonSize.lg, fullWidth = true, modifier = Modifier.weight(1f)) {
                PT.track("pattern.suggestion_apply_open", mapOf("item_count" to items.size, "suggestion_types" to PT.suggestionTypes(items)))
                outcome = null
                openedAt = System.currentTimeMillis()
                AnalyticsService.sheetCloseHint = null
                open = true
            }
            NButton("다음에", variant = NButtonVariant.secondary, size = NButtonSize.lg, loading = dismissing) {
                // [UX] 확인 · 되돌리기 없이 일주일 동안 숨겨요
                PT.track("pattern.suggestion_dismiss_click", mapOf(
                    "item_count" to items.size, "suggestion_types" to PT.suggestionTypes(items), "period" to PT.period.name,
                ))
                scope.launch { dismissAll(items.map { it.key }) }
            }
        }
    }

    if (open) {
        SheetScaffold(
            onDismiss = {
                // 반영하지 않고 닫음 — '취소' · X · 아래로 쓸어 닫기 · 뒤로 가기(swipe_down 으로 셈)
                GD.closeMethod(outcome)?.let { method ->
                    PT.track("pattern.suggestion_apply_cancel", mapOf(
                        "item_count" to items.size, "close_method" to method,
                        "open_duration_seconds" to ((System.currentTimeMillis() - openedAt) / 1000.0).roundToInt(),
                    ))
                }
                open = false
            },
            title = "이렇게 바꿀게요",
            description = "내일부터 적용돼요. 지난 기록과 오늘 일정은 그대로예요.",
            dismissible = !applying,
            footer = { close ->
                NButton("취소", variant = NButtonVariant.secondary, enabled = !applying) {
                    outcome = "cancel_button"
                    close()
                }
                NButton("반영하기", variant = NButtonVariant.brand, loading = applying) {
                    outcome = "saved"
                    PT.track("pattern.suggestion_apply_click", mapOf("item_count" to items.size, "suggestion_types" to PT.suggestionTypes(items)))
                    scope.launch {
                        applyAll(items.map { it.key })
                        close()
                    }
                }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (item in items) {
                    key(item.id) { PatternChangeRow(item = item) }
                }
                Text(
                    "그날만 직접 옮긴 블록은 그대로 두고, 새 시간과 겹치는 자동 배치 블록은 다시 배치해요.",
                    style = pt(13).copy(lineHeight = 19.sp),
                    color = NC.ink3,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PatternChangeRow(item: PatternSuggestionItem) {
    val isTime = item.type == PatternSuggestionType.ROUTINE_TIME
    fun describe(c: PatternSuggestionChange): String {
        if (isTime) return "${c.startTime ?: ""}–${c.endTime ?: ""}"
        return (c.days ?: emptyList()).joinToString("·") { PatternFormat.dayShort(it) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(NC.subtle, RoundedCornerShape(Radius.xxl))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(item.routineName, style = pt(15, PTWeight.bold), color = NC.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (isTime) "시간" else "요일", style = pt(14), color = NC.ink3)
            Text(describe(item.before), style = pt(14).copy(textDecoration = TextDecoration.LineThrough), color = NC.ink4)
            Text("→", style = pt(14), color = NC.ink2)
            Text(describe(item.after), style = pt(14, PTWeight.bold), color = NC.textBrand)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PatternSuggestionBarViewPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PatternSuggestionBarView(suggestion = PatternSampleData.report.suggestion)
            PatternSampleData.report.suggestion?.items?.forEach { PatternChangeRow(item = it) }
        }
    }
}
