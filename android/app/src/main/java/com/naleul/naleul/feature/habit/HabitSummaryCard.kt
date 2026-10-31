// feature/habit/HabitSummaryCard.kt
//
// iOS Features/Habit/HabitSummaryCard.swift
// 웹 features/habit/ui/HabitSummaryCard.tsx — 목표 목록 맨 위 기본 생활 습관 요약.
// 오늘 몇 개 챙겼는지 + 습관 칩. 누르면 습관 화면(기본 생활 습관 목표 상세)으로 가요.
// 처음 부를 때 서버가 그릇(목표)을 만들어 줘요.
//
// iOS init() / init(preview:) → preview 인자 하나로 (null 이면 서버에서 불러와요)

package com.naleul.naleul.feature.habit

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException

@Composable
fun HabitSummaryCard(
    /** 미리보기용 (주면 네트워크 안 씀) */
    preview: HabitHome? = null,
    modifier: Modifier = Modifier,
) {
    var home by remember { mutableStateOf<HabitHome?>(null) }
    var loaded by remember { mutableStateOf(false) }

    ReloadOn(listOf(DataKey.habits)) {
        if (preview != null) return@ReloadOn
        try {
            HabitAPI.home()?.let { home = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // iOS try? — 실패하면 조용히 (카드를 안 보여줘요)
        }
        loaded = true
    }

    val data = preview ?: home
    if (data != null) {
        HabitSummaryCardContent(data = data, modifier = modifier)
    } else if (!loaded) {
        // 불러오는 동안 자리만 (iOS .redacted(.placeholder))
        val shape = RoundedCornerShape(Radius.card)
        Box(
            modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(NC.surface, shape)
                .border(1.dp, NC.borderSubtle, shape),
        )
    }
}

@Composable
private fun HabitSummaryCardContent(data: HabitHome, modifier: Modifier = Modifier) {
    val due = data.habits.filter { it.isActive && it.scheduled }
    val done = due.count { it.checked }
    val ratio = if (due.isEmpty()) 0.0 else done.toDouble() / due.size.toDouble()
    val shape = RoundedCornerShape(Radius.card)

    fun subtitle(): String {
        if (data.habits.isEmpty()) return "영양제 · 물 마시기처럼 매일 챙길 것을 추가해 보세요"
        if (due.isEmpty()) return "오늘은 해야 하는 습관이 없어요"
        if (done == due.size) return "오늘 할 습관을 모두 챙겼어요 🎉"
        return "오늘 ${due.size - done}개 남았어요"
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NC.surface, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .clickable(role = Role.Button) {
                GD.track("goal_list.habit_card_click", mapOf(
                    "habit_count" to data.habits.size, "due_today_count" to due.size, "done_count" to done,
                ))
                AppRouter.openGoal(data.goalCategoryId)
            }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).background(NC.successSoft, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(data.emoji ?: "🌱", fontSize = 20.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(data.goalName, style = NType.h4, color = NC.textPrimary)
                Text(subtitle(), style = pt(13), color = NC.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (due.isNotEmpty()) {
                val ink = NC.ink
                val ink3 = NC.ink3
                Text(
                    buildAnnotatedString {
                        withStyle(ptMono(17, PTWeight.bold).toSpanStyle().copy(color = ink)) { append("$done") }
                        withStyle(ptMono(14, PTWeight.semibold).toSpanStyle().copy(color = ink3)) { append(" / ${due.size}") }
                    },
                )
            }
            SfImage("chevron.right", size = 13.dp, tint = NC.ink4)
        }
        if (due.isNotEmpty()) {
            ProgressBar(value = ratio, height = 6.dp)
            FlowLayout(spacing = 6.dp) {
                for (h in due.take(8)) {
                    // 스펙 8.6: 연한 칩 — bg/sunken + 1dp border/soft · text/secondary
                    val fg = if (h.checked) NC.statusSuccessFg else NC.textSecondary
                    Row(
                        Modifier
                            .height(28.dp)
                            .background(NC.bgSunken, CircleShape)
                            .border(1.dp, NC.borderSoft, CircleShape)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (h.checked) SfImage("checkmark", size = 10.dp, tint = fg)
                        Text(h.title, style = pt(12, PTWeight.medium), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (due.size > 8) {
                    Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                        Text("+${due.size - 8}", style = pt(12), color = NC.ink3)
                    }
                }
            }
        }
    }
}

// MARK: - 미리보기

@Preview(showBackground = true)
@Composable
private fun HabitSummaryCardPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HabitSummaryCard(preview = HabitPreviewData.home)
            HabitSummaryCard(preview = HabitPreviewData.emptyHome)
        }
    }
}
