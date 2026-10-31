// feature/calendar/TimetableSheets.kt
//
// iOS Features/Calendar/TimetableSheets.swift 를 옮긴 파일.
// 블록을 누르면 뜨는 시트를 한 곳에서 띄워요 (웹 useTimetableInteractions 의 overlays 중 Popover · Modal 부분).
// 웹 Popover(블록 옆에 뜨는 작은 창) → 아래에서 올라오는 시트.
// 같이 쓰는 입력 부품(시각 · 날짜 고르기, 삭제 확인)도 여기 있어요.
//
//   TimetableSheets(board)   // board.sheet 가 바뀌면 그에 맞는 시트를 띄워요 (iOS .timetableSheets(board))
//                            // 화면 컴포저블 본문 어디에나 한 번 넣으면 돼요

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// MARK: - 시트 띄우기

/** TimeTable 블록 상세 · 고정 시간 · 실제로 한 일 · 시간 입력 시트 (iOS .timetableSheets(board)) */
@Composable
fun TimetableSheets(board: TimetableBoardModel) {
    val sheet = board.sheet ?: return
    // 시트를 아래로 끌어 닫거나 X 를 눌렀을 때
    val onDismiss: () -> Unit = {
        board.sheet = null
        board.sheetDismissed()
    }
    key(sheet.id) {
        when (sheet) {
            // 데이터가 새로 오면 시트 안의 Task 도 최신 값으로
            is TimetableSheet.task -> TimetableTaskDetailSheet(board = board, task = board.liveTask(sheet.task), onDismiss = onDismiss)
            is TimetableSheet.fixed -> TimetableFixedDetailSheet(board = board, block = sheet.block, onDismiss = onDismiss)
            is TimetableSheet.activity -> TimetableActivityFormSheet(board = board, draft = sheet.draft, onDismiss = onDismiss)
            is TimetableSheet.timeEdit -> {
                val e = sheet.editing
                val target = e.target
                if (target is TimetableTimeEditTarget.actual) {
                    // 실제로 한 시간 — 나눠서 한 경우 여러 구간
                    TimetableActualTimeSheet(board = board, task = board.liveTask(target.task), initial = e.initial, onDismiss = onDismiss)
                } else {
                    TimetableTimeEditSheet(board = board, editing = e, onDismiss = onDismiss)
                }
            }
        }
    }
}

// MARK: - 시각 고르기 (10분 단위 — 웹 input type="time" step=600)

/** 시 · 분을 각각 메뉴로 골라요. minutes 는 그날 0시 기준 분 (0~1439) */
@Composable
fun TimetableClockPicker(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    label: String = "",
    modifier: Modifier = Modifier,
) {
    val total = ((minutes % 1440) + 1440) % 1440
    val h = total / 60
    val m = total % 60
    val shape = RoundedCornerShape(Radius.xl)
    var hourOpen by remember { mutableStateOf(false) }
    var minuteOpen by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(NC.surface, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box {
            Text(
                TimetableClockPicker.pad(h),
                style = ptMono(15, PTWeight.medium),
                color = NC.ink,
                modifier = Modifier
                    .clickable(role = Role.Button) { hourOpen = true }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
            NDropdownMenu(expanded = hourOpen, onDismissRequest = { hourOpen = false }, containerColor = NC.surface) {
                for (v in 0 until 24) {
                    NDropdownMenuItem(
                        text = { Text(TimetableClockPicker.pad(v), style = ptMono(15), color = NC.ink) },
                        trailingIcon = if (v == h) ({ SfImage("checkmark", size = 14.dp, tint = NC.textBrand) }) else null,
                        onClick = {
                            hourOpen = false
                            onMinutesChange(v * 60 + m)
                        },
                    )
                }
            }
        }
        Text(":", style = pt(15, PTWeight.semibold), color = NC.ink3)
        Box {
            Text(
                TimetableClockPicker.pad(m),
                style = ptMono(15, PTWeight.medium),
                color = NC.ink,
                modifier = Modifier
                    .clickable(role = Role.Button) { minuteOpen = true }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
            NDropdownMenu(expanded = minuteOpen, onDismissRequest = { minuteOpen = false }, containerColor = NC.surface) {
                for (v in TimetableClockPicker.minuteOptions(m)) {
                    NDropdownMenuItem(
                        text = { Text(TimetableClockPicker.pad(v), style = ptMono(15), color = NC.ink) },
                        trailingIcon = if (v == m) ({ SfImage("checkmark", size = 14.dp, tint = NC.textBrand) }) else null,
                        onClick = {
                            minuteOpen = false
                            onMinutesChange(h * 60 + v)
                        },
                    )
                }
            }
        }
    }
}

/** iOS TimetableClockPicker 의 static 함수들 */
object TimetableClockPicker {
    /** 10분 단위 + 지금 값(10분 단위가 아니면 그것도) */
    fun minuteOptions(current: Int): List<Int> {
        val list = (0 until 60 step 10).toMutableList()
        if (current % 10 != 0) {
            list.add(current)
            list.sort()
        }
        return list
    }

    fun pad(n: Int): String = if (n < 10) "0$n" else "$n"
}

// MARK: - 날짜 고르기 ("YYYY-MM-DD" 문자열 ↔ 날짜 선택 창)

@Composable
fun TimetableDateField(
    ymd: String,
    onYmdChange: (String) -> Unit,
    /** 이 날짜까지만 고를 수 있어요 (실제로 한 일: 오늘까지) */
    maxDate: String? = null,
    modifier: Modifier = Modifier,
) {
    // 기기 시간대가 한국이 아니어도 같은 날짜로 보이게 "YYYY-MM-DD" 문자열로만 주고받아요
    NDateField(ymd = ymd, onYmdChange = onYmdChange, maxDate = maxDate, modifier = modifier)
}

// MARK: - 그 자리에서 "삭제할까요?" (웹 goal/edit/inline InlineConfirm)

@Composable
fun TimetableInlineConfirm(
    message: String,
    detail: String? = null,
    confirmLabel: String = "삭제",
    loading: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.xxl)
    Column(
        modifier
            .fillMaxWidth()
            .background(NC.dangerSoft.copy(alpha = 0.6f), shape)
            .border(1.dp, NC.borderSubtle, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = "삭제 확인" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(message, style = pt(14, PTWeight.semibold), color = NC.ink)
            if (detail != null) Text(detail, style = pt(12), color = NC.ink3)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            NButton("취소", variant = NButtonVariant.ghost, size = NButtonSize.sm, enabled = !loading, action = onCancel)
            NButton(confirmLabel, variant = NButtonVariant.danger, size = NButtonSize.sm, loading = loading, action = onConfirm)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TimetableSheetsPreview() {
    NaleulTheme(dark = false) {
        var start by remember { mutableStateOf(9 * 60) }
        var date by remember { mutableStateOf(KST.today()) }
        Column(Modifier.canvasBackground().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TimetableClockPicker(minutes = start, onMinutesChange = { start = it }, label = "시작")
            TimetableDateField(ymd = date, onYmdChange = { date = it }, maxDate = KST.today())
            TimetableInlineConfirm(
                message = "'영어 단어 외우기'을 삭제할까요?", detail = "되돌릴 수 없어요.",
                loading = false, onConfirm = { }, onCancel = { },
            )
        }
    }
}
