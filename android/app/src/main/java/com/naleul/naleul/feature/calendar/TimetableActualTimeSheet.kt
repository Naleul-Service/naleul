// feature/calendar/TimetableActualTimeSheet.kt
//
// iOS Features/Calendar/TimetableActualTimeSheet.swift 를 옮긴 파일.
// "실제로 한 시간" — 한 번에 했으면 한 줄, 중간에 쉬었다 이어서 했으면 "나눠서 했어요"로 여러 줄 (웹 ActualTimeModal).
// 예) 계획 13:00~18:00 → 실제 13:00~15:00, 16:00~18:00
// 자정을 넘겨도 돼요: 끝이 시작보다 이르면 끝은 다음 날 (23:00~00:00),
// 앞 구간보다 이른 시각에 시작하는 구간은 통째로 다음 날로 봐요 (21:30~22:00, 23:00~00:00, 00:30~01:00).

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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlinx.coroutines.launch

/** iOS TimetableActualTimeSheet 의 중첩 타입 · static 함수 */
object TimetableActualTime {
    data class Row(
        val start: Int,
        val end: Int,
        val id: String = UUID.randomUUID().toString(),
    ) {
        companion object {}
    }

    const val maxSegments = 10

    /** 화면의 시계는 0~1439분만 보여줘요 — 다음 날 시각(1440 이상)도 시계 값으로 */
    fun wrap(m: Int): Int = ((m % 1440) + 1440) % 1440

    /**
     * 화면 값 → 실제 구간.
     * 끝 ≤ 시작이면 끝은 다음 날, 앞 구간보다 이르게 시작하면 그 구간부터 다음 날로 (자정을 지나 이어서 한 경우)
     */
    fun parse(rows: List<Row>, date: String): List<TimetableTimeValue> {
        var offset = 0
        var prevStart = Int.MIN_VALUE
        return rows.map { r ->
            if (prevStart != Int.MIN_VALUE && r.start + offset < prevStart) offset += 1440
            val s = r.start + offset
            var e = r.end + offset
            if (e <= s) e += 1440
            prevStart = s
            TimetableTimeValue(date = date, start = s, end = e)
        }
    }
}

@Composable
fun TimetableActualTimeSheet(
    board: TimetableBoardModel,
    task: TimeBlockTask,
    initial: TimetableTimeValue,
    onDismiss: () -> Unit,
) {
    /** 기준 날짜 (실제로 한 날 — 없으면 계획한 날) */
    val date = initial.date
    val rows = remember(task.taskId, initial) {
        val saved = (task.actualSegments ?: emptyList()).map {
            TimetableActualTime.Row(
                start = TimetableActualTime.wrap(KST.minutesFrom(initial.date, it.start)),
                end = TimetableActualTime.wrap(KST.minutesFrom(initial.date, it.end)),
            )
        }
        val first = if (saved.size > 1) {
            saved
        } else {
            listOf(TimetableActualTime.Row(start = TimetableActualTime.wrap(initial.start), end = TimetableActualTime.wrap(initial.end)))
        }
        mutableStateListOf<TimetableActualTime.Row>().apply { addAll(first) }
    }
    val scope = rememberCoroutineScope()

    val parsed = TimetableActualTime.parse(rows.toList(), date = date)
    val error: String? = run {
        if (parsed.any { it.end <= it.start }) return@run "종료 시각이 시작보다 뒤여야 해요."
        if (parsed.any { it.end - it.start > 1440 }) return@run "한 구간은 하루를 넘을 수 없어요."
        val sorted = parsed.sortedBy { it.start }
        for (i in 1 until sorted.size) {
            if (sorted[i].start < sorted[i - 1].end) return@run "구간끼리 겹치지 않게 입력해 주세요."
        }
        null
    }
    val total = parsed.sumOf { maxOf(it.end - it.start, 0) }
    val saving = board.editSaving
    /** "saved" · "cancel_button" — 없으면 X 또는 아래로 쓸어 닫기 · 뒤로 가기 */
    var outcome by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(error) {
        if (error != null) board.sheetTrack("actual_time.error_view", mapOf("validation_error" to error))
    }
    DisposableEffect(task.taskId) {
        onDispose {
            GD.closeMethod(outcome)?.let { m -> board.sheetTrack("actual_time.close", mapOf("close_method" to m)) }
        }
    }

    fun addRow() {
        board.sheetTrack("actual_time.segment_add_click", mapOf("segment_count" to rows.size + 1))
        // 직전 구간이 끝나고 30분 뒤부터 1시간으로 제안
        val last = rows.lastOrNull()
        val s = TimetableActualTime.wrap((last?.end ?: (12 * 60)) + 30)
        rows.add(TimetableActualTime.Row(start = s, end = TimetableActualTime.wrap(s + 60)))
    }

    SheetScaffold(
        onDismiss = onDismiss,
        title = "실제로 한 시간",
        description = "실제로 시작하고 끝낸 시각을 남기면 완료로 표시되고, 나의 패턴이 더 정확해져요. 중간에 쉬었다가 이어서 했으면 나눠서 적어 주세요.",
        dismissible = !saving,
        footer = { _ ->
            NButton("취소", variant = NButtonVariant.secondary, enabled = !saving) {
                outcome = "cancel_button"
                board.close()
            }
            NButton("저장", loading = saving, enabled = error == null) {
                val segs = parsed.sortedBy { it.start }
                board.sheetTrack(
                    "actual_time.save_click",
                    mapOf(
                        "segment_count" to segs.size, "duration_minutes" to total,
                        "crosses_midnight" to segs.any { it.end > 1440 },
                        "is_edit" to (task.taskStatus == TaskStatus.COMPLETED && task.actualStartAt != null && task.actualEndAt != null),
                    ),
                )
                scope.launch {
                    if (board.submitActual(task, date = date, segments = segs)) outcome = "saved"
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEachIndexed { index, row ->
                key(row.id) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (index == 0) Text("시작", style = pt(12, PTWeight.medium), color = NC.ink3)
                            TimetableClockPicker(
                                minutes = row.start,
                                onMinutesChange = { v ->
                                    val i = rows.indexOfFirst { it.id == row.id }
                                    if (i >= 0) rows[i] = rows[i].copy(start = v)
                                },
                                label = "시작 시각",
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (index == 0) Text("종료", style = pt(12, PTWeight.medium), color = NC.ink3)
                            TimetableClockPicker(
                                minutes = row.end,
                                onMinutesChange = { v ->
                                    val i = rows.indexOfFirst { it.id == row.id }
                                    if (i >= 0) rows[i] = rows[i].copy(end = v)
                                },
                                label = "종료 시각",
                            )
                        }
                        if (rows.size > 1) {
                            Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                                NIconButton("xmark", size = 15.dp, label = "구간 지우기") {
                                    board.sheetTrack("actual_time.segment_remove_click", mapOf("segment_count" to rows.size - 1))
                                    rows.removeAll { it.id == row.id }
                                }
                            }
                        }
                    }
                }
            }
            if (rows.size < TimetableActualTime.maxSegments) {
                Row(
                    Modifier
                        .padding(top = 2.dp)
                        .clickable(role = Role.Button) { addRow() },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SfImage("plus", size = 13.dp, tint = NC.textBrand)
                    Text(
                        if (rows.size == 1) "나눠서 했어요 (구간 추가)" else "구간 추가",
                        style = pt(13, PTWeight.semibold),
                        color = NC.textBrand,
                    )
                }
            }
            Text(
                error ?: "${if (rows.size > 1) "${rows.size}번에 나눠서 · " else ""}총 ${KST.formatDuration(total)}",
                style = pt(12),
                color = if (error == null) NC.ink3 else NC.danger,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TimetableActualTimeSheetPreview() {
    NaleulTheme(dark = false) {
        val d = KST.today()
        val board = remember { TimetableBoardModel.sample(start = d, end = d) }
        val t = TimetableSampleData.day(d, index = 1).tasks[0].copy(
            taskStatus = TaskStatus.COMPLETED,
            actualSegments = listOf(
                ActualSegment(start = "${d}T21:30:00", end = "${d}T22:00:00"),
                ActualSegment(start = "${d}T23:00:00", end = "${KST.addDays(d, 1)}T00:00:00"),
            ),
        )
        TimetableActualTimeSheet(
            board = board, task = t,
            initial = TimetableTimeValue(date = d, start = 21 * 60 + 30, end = 24 * 60),
            onDismiss = { },
        )
    }
}
