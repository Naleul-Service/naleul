// feature/record/RecordJournalView.kt
//
// iOS Features/Record/RecordJournalView.swift
// 웹 features/record/ui/RecordJournal.tsx (+ api.ts useRecordJournal · useDeleteRecord, journalText.ts)
// 업무 일지 — 날짜별로 "그날 완료한 Task"와 "직접 남긴 기록"을 한 목록으로.
//  - 2주씩 불러오고 "이전 2주 더 보기"로 과거를 이어 불러요 (웹 useInfiniteQuery)
//  - 하루 / 이번 주 단위로 글로 복사 (업무 보고·회고에 바로 붙여 넣기)
//  - 직접 남긴 기록만 여기서 지울 수 있어요 (Task 완료 취소는 Task 목록에서)
//
// 목표 상세 화면의 스크롤 안에 들어가서 LazyColumn 대신 Column 으로 그려요.

package com.naleul.naleul.feature.record

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// MARK: - 상태

class RecordJournalViewModel(preview: RecordJournal? = null) : ScreenModel() {
    var days by mutableStateOf<List<RecordJournalDay>>(preview?.days ?: emptyList())
        private set
    var nextBefore by mutableStateOf<String?>(preview?.nextBefore)
        private set
    var loaded by mutableStateOf(preview != null)
        private set
    var failed by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    /** 지금까지 불러온 쪽 수 — 다시 불러올 때 같은 만큼 다시 받아요 (웹 infinite query 의 refetch 와 같게) */
    private var pages = 1
    private var goalId: Int? = null
    private val isPreview: Boolean = preview != null

    suspend fun load(goalId: Int) {
        if (isPreview) return
        if (this.goalId != goalId) {
            // 다른 목표로 바뀌면 처음부터
            this.goalId = goalId
            days = emptyList()
            nextBefore = null
            loaded = false
            failed = false
            pages = 1
        }
        try {
            val all = mutableListOf<RecordJournalDay>()
            var before: String? = null
            var count = 0
            do {
                val page = RecordAPI.journal(goalId = goalId, before = before)
                all += page?.days ?: emptyList()
                before = page?.nextBefore
                count += 1
            } while (count < pages && before != null)
            days = all
            nextBefore = before
            pages = maxOf(count, 1)
            loaded = true
            failed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 이미 보여주던 일지가 있으면 그대로 두고, 처음이면 오류 상자를 보여줘요
            if (!loaded) {
                failed = true
                WK.track("work_journal.load_fail", goalId = goalId, props = mapOf("error_status" to ((e as? ApiError)?.httpStatus ?: 0)))
            }
        }
    }

    suspend fun loadMore() {
        val id = goalId ?: return
        val before = nextBefore ?: return
        if (loadingMore) return
        loadingMore = true
        try {
            val page = RecordAPI.journal(goalId = id, before = before)
            days = days + (page?.days ?: emptyList())
            nextBefore = page?.nextBefore
            pages += 1
            WK.track("work_journal.load_more_result", goalId = id, props = mapOf("day_count" to days.size, "result" to "success"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Toast.error(errorMessage(e))
            WK.track("work_journal.load_more_result", goalId = id, props = mapOf(
                "day_count" to days.size, "result" to "fail", "error_status" to ((e as? ApiError)?.httpStatus ?: 0),
            ))
        } finally {
            loadingMore = false
        }
    }

    suspend fun retry() {
        val id = goalId ?: return
        failed = false
        load(goalId = id)
    }
}

// MARK: - 화면

/** preview 를 넘기면 네트워크 없이 그려요 (미리보기용) */
@Composable
fun RecordJournalView(goalId: Int, goalName: String, preview: RecordJournal? = null, modifier: Modifier = Modifier) {
    val vm = rememberScreenModel { RecordJournalViewModel(preview) }
    var deleteTarget by remember { mutableStateOf<RecordJournalItem?>(null) }
    var confirmOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var emptySent by remember { mutableStateOf(false) }
    /** 지우려는 기록의 날짜 (days_ago) */
    var deleteDay by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    fun track(event: String, props: Map<String, Any?> = emptyMap()) {
        if (preview != null) return
        WK.track(event, goalId = goalId, props = props)
    }

    // Task 완료 토글(timetable)이나 기록 추가/삭제(record)가 있으면 다시 불러와요
    ReloadOn(listOf(DataKey.record, DataKey.activities, DataKey.timetable), id = goalId) { vm.load(goalId = goalId) }

    /** 웹 navigator.clipboard.writeText → 클립보드 */
    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        Toast.success("복사했어요. 원하는 곳에 붙여 넣으세요.")
    }

    fun copyWeek() {
        val today = KST.today()
        val weekStart = KST.startOfWeek(today)
        val week = vm.days.filter { it.date >= weekStart }
        track("work_journal.copy_week_click", mapOf(
            "day_count" to week.size, "item_count" to week.sumOf { it.items.size }, "was_empty" to week.isEmpty(),
        ))
        if (week.isEmpty()) {
            Toast.show("이번 주 일지가 아직 없어요.")
            return
        }
        copy(
            RecordJournalText.text(
                "$goalName · 업무 일지 · ${KST.formatMonthDay(weekStart)} – ${KST.formatMonthDay(today)}",
                week,
            ),
        )
    }

    suspend fun delete(item: RecordJournalItem) {
        deleting = true
        try {
            Mutation.run(invalidate = listOf(DataKey.activities, DataKey.record), successMessage = "기록을 지웠어요.") {
                RecordAPI.deleteRecord(activityId = item.id)
            }
            track("work_journal.record_delete_result", GD.result())
        } finally {
            deleting = false
            deleteTarget = null
        }
    }

    Column(modifier.fillMaxWidth().card().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("업무 일지", style = NType.h4, color = NC.ink)
            Spacer(Modifier.weight(1f))
            NButton(
                "이번 주 복사",
                variant = NButtonVariant.secondary,
                size = NButtonSize.sm,
                icon = "doc.on.doc",
                enabled = vm.days.isNotEmpty(),
            ) { copyWeek() }
        }
        Row(
            Modifier.padding(top = 4.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SfImage("checkmark.circle", size = 12.dp, tint = NC.textBrand)
                Text("완료한 Task", style = pt(12), color = NC.ink3)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SfImage("pencil.line", size = 12.dp, tint = NC.ink3)
                Text("직접 남긴 기록", style = pt(12), color = NC.ink3)
            }
        }

        when {
            !vm.loaded && !vm.failed -> {
                Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 96.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NC.ink3, strokeWidth = 2.5.dp)
                }
            }
            vm.failed -> {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(NC.bgSunken, RoundedCornerShape(Radius.xxl))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("일지를 불러오지 못했어요.", style = pt(14), color = NC.ink2, modifier = Modifier.weight(1f))
                    NButton("다시 시도", variant = NButtonVariant.secondary, size = NButtonSize.sm) {
                        track("work_journal.retry_click")
                        vm.scope.launch { vm.retry() }
                    }
                }
            }
            vm.days.isEmpty() -> {
                LaunchedEffect(Unit) {
                    if (!emptySent) {
                        emptySent = true
                        track("work_journal.empty_view", mapOf("has_older" to (vm.nextBefore != null)))
                    }
                }
                Text(
                    if (vm.nextBefore != null) {
                        "최근 2주에는 일지가 없어요. 아래에서 이전 일지를 볼 수 있어요."
                    } else {
                        "아직 일지가 없어요. 이 목표의 Task를 완료하거나 위에서 한 일을 남기면 날짜별로 쌓여요."
                    },
                    style = pt(14).copy(lineHeight = 20.sp),
                    color = NC.ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NC.bgSunken, RoundedCornerShape(Radius.xxl))
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }
            else -> {
                val today = KST.today()
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    for (d in vm.days) {
                        key(d.id) {
                            RecordJournalDaySection(
                                day = d,
                                today = today,
                                deleting = deleting,
                                onCopy = {
                                    track("work_journal.copy_day_click", mapOf("days_ago" to GD.daysAgo(d.date), "item_count" to d.items.size))
                                    copy(RecordJournalText.text("$goalName · 업무 일지", listOf(d)))
                                },
                                onDelete = { item ->
                                    track("work_journal.record_delete_click", mapOf("days_ago" to GD.daysAgo(d.date)))
                                    deleteDay = d.date
                                    deleteTarget = item
                                    confirmOpen = true
                                },
                            )
                        }
                    }
                }
            }
        }

        if (vm.nextBefore != null) {
            NButton(
                "이전 2주 더 보기",
                variant = NButtonVariant.ghost,
                loading = vm.loadingMore,
                fullWidth = true,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                track("work_journal.load_more_click", mapOf("day_count" to vm.days.size))
                vm.scope.launch { vm.loadMore() }
            }
        }
    }

    ConfirmDialog(
        isPresented = confirmOpen,
        onDismiss = { confirmOpen = false },
        title = "이 기록을 지울까요?",
        message = deleteTarget?.let { "\"${it.title}\" 기록이 사라져요." },
        confirmLabel = "지우기",
        tone = ConfirmTone.danger,
        onCancel = {
            track("work_journal.record_delete_cancel", mapOf("close_method" to "cancel_button"))
            deleteTarget = null
        },
    ) {
        confirmOpen = false
        val item = deleteTarget
        track("work_journal.record_delete_confirm", mapOf("days_ago" to GD.daysAgo(deleteDay)))
        // 일지 카드가 화면에서 사라져도 지우기가 끝나게 화면(vm) 범위에서 돌려요
        if (item != null) vm.scope.launch { delete(item) }
    }
}

// MARK: - 하루

@Composable
private fun RecordJournalDaySection(
    day: RecordJournalDay,
    today: String,
    deleting: Boolean,
    onCopy: () -> Unit,
    onDelete: (RecordJournalItem) -> Unit,
) {
    val dayLabel = when (day.date) {
        today -> "오늘 · ${KST.formatMonthDay(day.date)}"
        KST.addDays(today, -1) -> "어제 · ${KST.formatMonthDay(day.date)}"
        else -> KST.formatMonthDay(day.date)
    }
    val summary = buildList {
        if (day.taskCount > 0) add("Task ${day.taskCount}")
        if (day.recordCount > 0) add("기록 ${day.recordCount}")
        if (day.minutes > 0) add(KST.formatDuration(day.minutes))
    }.joinToString(" · ")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(dayLabel, style = pt(14, PTWeight.bold), color = NC.ink)
            Spacer(Modifier.weight(1f))
            Text(summary, style = ptMono(12), color = NC.ink3)
            NIconButton(
                "doc.on.doc",
                size = 13.dp,
                color = NC.ink4,
                label = "${KST.formatMonthDay(day.date)} 일지 복사",
            ) { onCopy() }
        }
        Column(Modifier.fillMaxWidth().border(1.dp, NC.borderSubtle, RoundedCornerShape(Radius.xxl))) {
            day.items.forEachIndexed { i, item ->
                key(item.key) {
                    if (i > 0) HorizontalDivider(color = NC.borderSoft)
                    RecordJournalItemRow(item = item, deleting = deleting, onDelete = { onDelete(item) })
                }
            }
        }
    }
}

@Composable
private fun RecordJournalItemRow(item: RecordJournalItem, deleting: Boolean, onDelete: () -> Unit) {
    val s = item.startAt
    val e = item.endAt
    val timeLabel = if (s != null && e != null) "${KST.hm(s)}–${KST.hm(e)}" else "시간 미정"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(timeLabel, style = ptMono(13), color = NC.ink3, maxLines = 1, modifier = Modifier.width(84.dp).padding(top = 1.dp))
        if (item.type == RecordJournalItemType.RECORD) {
            SfImage("pencil.line", size = 14.dp, tint = NC.ink3, contentDescription = "직접 남긴 기록", modifier = Modifier.padding(top = 2.dp))
        } else {
            SfImage("checkmark.circle", size = 14.dp, tint = NC.textBrand, contentDescription = "완료한 Task", modifier = Modifier.padding(top = 2.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    (item.emoji?.let { "$it " } ?: "") + item.title,
                    style = pt(15, PTWeight.medium),
                    color = NC.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.routine) {
                    Row(
                        Modifier
                            .background(NC.brandSoft, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        SfImage("repeat", size = 8.dp, tint = NC.textBrand)
                        Text("루틴", style = pt(10, PTWeight.bold), color = NC.textBrand, maxLines = 1)
                    }
                }
            }
            val memo = item.memo
            if (!memo.isNullOrEmpty()) {
                Text(memo, style = pt(13), color = NC.ink3)
            }
            val m = item.minutes
            if (m != null && m > 0) {
                Text(KST.formatDuration(m), style = pt(12), color = NC.ink3)
            }
        }
        if (item.type == RecordJournalItemType.RECORD) {
            NIconButton(
                "trash",
                size = 14.dp,
                color = NC.ink4,
                label = "${item.title} 기록 지우기",
                enabled = !deleting,
                modifier = Modifier.offset(y = (-6).dp),
            ) { onDelete() }
        }
    }
}

@Preview(showBackground = true, heightDp = 1300)
@Composable
private fun RecordJournalViewPreview() {
    NaleulTheme(dark = false) {
        Column(
            Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RecordJournalView(goalId = 7, goalName = "나를 업무", preview = RecordSampleData.journal)
            RecordJournalView(goalId = 8, goalName = "빈 목표", preview = RecordJournal(days = emptyList(), nextBefore = null))
        }
    }
}
