// feature/calendar/TimetableFixedDetailSheet.kt
//
// iOS Features/Calendar/TimetableFixedDetailSheet.swift 를 옮긴 파일.
// 고정 시간(수면 · 점심 · 저녁 · 직접 만든 생활 패턴) 블록 상세 + 시간 직접 입력 창.
// 웹 원본: features/timetable/ui/FixedDetail.tsx · TimeEditModal.tsx
//
// 시간 입력 창은 두 곳에서 써요
//  - 고정 시간 "시간 변경": 적용 범위(이날만 / 이번 주 / 앞으로 ○요일마다 / 앞으로 매일)와 함께
//  - Task "계획과 다른 시간에 했어요": 실제로 시작 · 끝낸 시각 → 완료

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private fun timetableFixedTypeLabel(t: LifePatternType): String = when (t) {
    LifePatternType.SLEEP -> "수면"
    LifePatternType.LUNCH -> "점심"
    LifePatternType.DINNER -> "저녁"
    LifePatternType.CUSTOM -> "고정 일정"
}

@Composable
fun TimetableFixedDetailSheet(
    board: TimetableBoardModel,
    block: FixedBlock,
    onDismiss: () -> Unit,
) {
    val b = block
    val covered = b.coveredBy ?: emptyList()
    // 자정을 넘긴 수면의 다음 날 조각: 시작 시각을 몰라 시간 변경은 시작한 날에서 해요
    val canEditTime = !b.clippedFromPreviousDay
    val endText = if (KST.hm(b.end) == "00:00") "24:00" else KST.hm(b.end)
    val scope = rememberCoroutineScope()

    SheetScaffold(onDismiss = onDismiss, title = null) {
        Column(Modifier.fillMaxWidth().semantics { contentDescription = "고정 시간 상세" }) {
            FlowLayout(spacing = 6.dp) {
                NBadge("고정 시간 · ${timetableFixedTypeLabel(b.patternType)}")
                if (b.overridden) NBadge("기본과 다른 시간", tone = BadgeTone.brand)
            }
            Text(
                TimetableRules.title(b.emoji, b.title),
                style = NType.h4,
                color = NC.ink,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SfImage("clock", size = 13.dp, tint = NC.ink3)
                Text("${KST.formatMonthDay(b.targetDate)} · ${KST.hm(b.start)} – $endText", style = pt(14), color = NC.ink2)
            }

            if (b.clippedFromPreviousDay) {
                TimetableFixedNote(
                    "전날 밤에 시작한 ${b.title} 시간이에요. 시간을 바꾸려면 ${KST.formatMonthDay(b.targetDate)} 블록을 눌러 주세요.",
                    background = NC.subtle, foreground = NC.ink3,
                )
            }
            if (covered.isNotEmpty()) {
                TimetableFixedNote("직접 정한 Task가 이 시간의 일부를 덮고 있어요.", background = NC.warningSoft, foreground = NC.warningInk)
            }

            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canEditTime) {
                        NButton(
                            "시간 변경", variant = NButtonVariant.secondary, icon = "clock", fullWidth = true,
                            enabled = !board.busyFixed, modifier = Modifier.weight(1f),
                        ) {
                            board.sheetTrack("fixed_detail.time_edit_click", mapOf("pattern_type" to b.patternType.rawValue))
                            board.editFixedTime(b)
                        }
                    }
                    NButton(
                        "이날만 비우기", variant = NButtonVariant.secondary, icon = "calendar.badge.minus",
                        loading = board.busyFixed, fullWidth = true, modifier = Modifier.weight(1f),
                    ) {
                        board.sheetTrack(
                            "fixed_detail.skip_click",
                            mapOf("pattern_type" to b.patternType.rawValue, "days_from_today" to KST.diffDays(KST.today(), b.targetDate)),
                        )
                        scope.launch { board.skipFixed(b) }
                    }
                }
                if (b.overridden) {
                    NButton(
                        "기본 시간으로 되돌리기", variant = NButtonVariant.ghost, icon = "arrow.counterclockwise",
                        fullWidth = true, enabled = !board.busyFixed,
                    ) {
                        board.sheetTrack("fixed_detail.reset_click", mapOf("pattern_type" to b.patternType.rawValue))
                        scope.launch { board.resetFixed(b) }
                    }
                }
                NButton("기본 패턴 수정", variant = NButtonVariant.ghost, icon = "gearshape", fullWidth = true) {
                    board.sheetTrack("fixed_detail.settings_click", mapOf("pattern_type" to b.patternType.rawValue))
                    board.close()
                    com.naleul.naleul.feature.settings.ST.pendingLifePatternEntry = "calendar_fixed_detail"
                    AppRouter.openSettings(SettingsRoute.LifePattern)
                }
            }

            Text(
                "시간을 옮기면 이날만 · 이번 주 · 앞으로 중에서 고를 수 있어요.",
                style = pt(11),
                color = NC.ink4,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun TimetableFixedNote(text: String, background: Color, foreground: Color) {
    Text(
        text,
        style = pt(12).copy(lineHeight = 17.sp),
        color = foreground,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(background, RoundedCornerShape(Radius.xl))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

// MARK: - 시간 직접 입력 (웹 TimeEditModal)

/** 종료가 시작보다 이르면 다음 날로 넘어가는 것으로 봐요 (예: 23:00 ~ 01:00). 날짜는 고정 (웹 dateEditable=false) */
@Composable
fun TimetableTimeEditSheet(
    board: TimetableBoardModel,
    editing: TimetableTimeEditing,
    onDismiss: () -> Unit,
) {
    var start by remember(editing.id) { mutableStateOf(timetableWrapMinutes(editing.initial.start)) }
    var end by remember(editing.id) { mutableStateOf(timetableWrapMinutes(editing.initial.end)) }
    var chosen by remember(editing.id) { mutableStateOf(ChangeScope.DAY) }
    val scope = rememberCoroutineScope()

    val target = editing.target
    val isFixed = target is TimetableTimeEditTarget.fixed
    val title = when (target) {
        is TimetableTimeEditTarget.actual -> "실제로 한 시간"
        is TimetableTimeEditTarget.fixed -> "${KST.formatMonthDay(target.block.targetDate)} ${target.block.title} 시간"
    }
    val description = when (target) {
        is TimetableTimeEditTarget.actual -> "실제로 시작하고 끝낸 시각을 남기면 완료로 표시되고, 나의 패턴의 시작 지연이 더 정확해져요."
        is TimetableTimeEditTarget.fixed -> "적용 범위를 골라 주세요. 이날만 바꾸면 다음 날부터는 기본 시간이에요."
    }

    val date = editing.initial.date
    val s = start
    val e = if (end <= start) end + 1440 else end
    val valid = e - s > 0 && e - s <= 1440 && e != s + 1440
    val crosses = valid && e >= 1440
    val options: List<TimetableScopeOption>? = if (isFixed) {
        TimetableScope.options(
            kind = TimetableScopeKind.fixed, isToday = date == KST.today(), sameDay = true, crossesMidnight = crosses,
            weekdayLabel = "${KST.weekdayLabel[KST.weekdayIndex(date)]}요일",
        )
    } else {
        null
    }
    val effective = TimetableScope.effective(chosen, options)
    val saving = board.editSaving

    SheetScaffold(
        onDismiss = onDismiss,
        title = title,
        description = description,
        dismissible = !saving,
        footer = { _ ->
            NButton("취소", variant = NButtonVariant.secondary, enabled = !saving) { board.close() }
            NButton("저장", loading = saving, enabled = valid) {
                if (target is TimetableTimeEditTarget.fixed) {
                    board.sheetTrack(
                        "fixed_detail.time_save",
                        mapOf(
                            "pattern_type" to target.block.patternType.rawValue,
                            "change_scope" to effective.rawValue, "crosses_midnight" to (e > 1440),
                        ),
                    )
                }
                scope.launch {
                    board.submitEdit(editing, value = TimetableTimeValue(date = date, start = s, end = e), scope = effective)
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("시작", style = pt(12, PTWeight.medium), color = NC.ink3)
                    TimetableClockPicker(minutes = start, onMinutesChange = { start = it }, label = "시작 시각")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("종료", style = pt(12, PTWeight.medium), color = NC.ink3)
                    TimetableClockPicker(minutes = end, onMinutesChange = { end = it }, label = "종료 시각")
                }
            }
            Text(
                if (valid) "${KST.formatDuration(e - s)}${if (crosses) " · 다음 날까지 이어져요" else ""}" else "시작과 종료 시각을 다르게 입력해 주세요.",
                style = pt(12),
                color = NC.ink3,
            )
            if (options != null) {
                TimetableScopeChips(options = options, value = effective, onChange = { chosen = it })
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TimetableFixedDetailSheetPreview() {
    NaleulTheme(dark = false) {
        val d = KST.today()
        val board = remember { TimetableBoardModel.sample(start = d, end = d) }
        val block = TimetableSampleData.day(d, index = 2).fixedBlocks[0]
        TimetableFixedDetailSheet(board = board, block = block, onDismiss = { })
    }
}

@Preview(showBackground = true)
@Composable
private fun TimetableTimeEditSheetPreview() {
    NaleulTheme(dark = false) {
        val d = KST.today()
        val board = remember { TimetableBoardModel.sample(start = d, end = d) }
        val block = TimetableSampleData.day(d, index = 2).fixedBlocks[0]
        val editing = TimetableTimeEditing(
            target = TimetableTimeEditTarget.fixed(block),
            initial = TimetableTimeValue(date = d, start = 720, end = 780),
        )
        TimetableTimeEditSheet(board = board, editing = editing, onDismiss = { })
    }
}
