// feature/calendar/TimetableRangePreview.kt
//
// iOS Features/Calendar/TimetableRangePreview.swift 를 옮긴 파일.
// 정한 기간(최대 7일)의 TimeTable 격자 미리보기 — Task 추가 결과 화면(StepResult)에서 써요.
// 캘린더와 같은 격자 · 같은 동작이라 블록을 누르면 같은 상세 시트가 뜨고, 길게 눌러 끌어 옮길 수도 있어요.
// 웹 원본: features/brain-dump/ui/StepResult.tsx 의 미리보기 격자 부분
//          (TimeGrid + useTimetableInteractions, maxHeightClass="max-h-[560px]")
//
//   TimetableRangePreview(startDate = "2026-11-18", endDate = "2026-11-20", highlightTaskIds = setOf(12, 13))
//
// 격자 높이를 520dp 로 정해 두어서 바깥 세로 스크롤 안에 넣어도 돼요.

package com.naleul.naleul.feature.calendar

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun TimetableRangePreview(
    startDate: String,
    endDate: String,
    /** "NEW" 로 표시할 Task (방금 만든 것) */
    highlightTaskIds: Set<Int> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val board = rememberScreenModel { TimetableBoardModel() }
    TimetableRangePreviewContent(board, startDate, endDate, highlightTaskIds, modifier)
}

@Composable
private fun TimetableRangePreviewContent(
    board: TimetableBoardModel,
    startDate: String,
    endDate: String,
    highlightTaskIds: Set<Int>,
    modifier: Modifier = Modifier,
) {
    /** 보여줄 기간: 최대 7일 (TimeTable API 제한) */
    val end = run {
        val last = KST.addDays(startDate, 6)
        val wanted = if (endDate < startDate) startDate else endDate
        if (wanted > last) last else wanted
    }
    ReloadOn(listOf(DataKey.timetable), id = "${startDate}_$end") {
        board.setRange(startDate, end)
        board.load(withActivities = false)
    }
    // 블록을 누르면 뜨는 시트
    TimetableSheets(board)
    // 화면을 떠날 때 고르지 않은 범위는 "이날만"으로 저장 (iOS .onDisappear)
    DisposableEffect(board) {
        onDispose { board.saveAskOnLeave() }
    }

    val days = board.days
    val shape = RoundedCornerShape(Radius.xxl)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val error = board.loadError
        if (days.isEmpty() && (board.isFetching || !board.loadedOnce) && error == null) {
            LoadingView(minHeight = 320.dp, modifier = Modifier.card(radius = Radius.xxl))
        } else if (days.isEmpty() && error != null) {
            ErrorView(message = error, retry = { board.load(withActivities = false) }, modifier = Modifier.card(radius = Radius.xxl))
        } else {
            val hours = TimetableLayout.visibleHours(days)
            TimetableGridView(
                board = board, days = days, startHour = hours.startHour, endHour = hours.endHour,
                highlightIds = highlightTaskIds,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp)
                    .clip(shape)
                    .background(NC.surface, shape)
                    .border(1.dp, NC.borderSubtle, shape),
            )
        }
        if (endDate > end) {
            Text(
                "${KST.formatMonthDay(KST.addDays(end, 1))} 이후에 놓인 Task는 캘린더에서 확인할 수 있어요.",
                style = pt(12),
                color = NC.ink3,
            )
        }
        // 끌어서 옮긴 루틴 · 고정 시간의 범위 고르기 / 묶음 안내 (이 화면 안에 붙여서 보여줘요)
        TimetableBottomBars(board = board, showCarry = false, showFill = false)
    }
}

@Preview(showBackground = true, heightDp = 760)
@Composable
private fun TimetableRangePreviewPreview() {
    NaleulTheme(dark = false) {
        val today = KST.today()
        val board = remember { TimetableBoardModel.sample(start = today, end = KST.addDays(today, 2)) }
        Column(Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
            TimetableRangePreviewContent(
                board = board, startDate = today, endDate = KST.addDays(today, 2), highlightTaskIds = setOf(2000, 2001),
            )
        }
    }
}
