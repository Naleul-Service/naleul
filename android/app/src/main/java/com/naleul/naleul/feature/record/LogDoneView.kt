// feature/record/LogDoneView.kt
//
// iOS Features/Record/LogDoneView.swift (웹 features/record/ui/LogDoneView.tsx)
// Task 추가 › "한 일 기록" — 이미 한 일을 Brain dump 처럼 한 번에 적어요.
//   1. 한 줄에 하나씩 적기 → AI 가 제목·시간을 정리하고 목표를 연결 (있는 목표, 없으면 임시 목표 제안)
//   2. 연결만 확인하고 저장 → TimeTable 의 "실제" 칸과 각 목표(기록형이면 업무 일지)에 쌓여요
//   3. 완료 — 무엇을 기록했는지 보여주고 "더 기록하기"
//
// 웹 <select> 목표 고르기 → NMenu (드롭다운 메뉴). 웹 ⌘+Enter 단축키는 휴대폰에 없어서 뺐어요.
// 이 화면은 Task 추가 시트 안에 들어가요 (시트가 스크롤을 맡으므로 여기서는 스크롤을 만들지 않아요).

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** 검토 화면의 한 줄 */
private data class RecordLogRow(
    val clientKey: String,
    val text: String,
    val title: String,
    val date: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val minutes: Int? = null,
    /** "goal:12" · "temp:t1" · "" (목표 없이) */
    val link: String,
) {
    val id: String get() = clientKey

    companion object {}
}

private const val LOG_DONE_MAX_LINES = 20
private const val LOG_DONE_PLACEHOLDER = "오전 10시 주간 회의 1시간\n기획서 초안 작성 2시간\n어제 코드 리뷰 30분\n운동 30분"
private val logDonePastWord = Regex("(어제|그제|그저께)")

private fun logDoneHasPastWord(s: String): Boolean = logDonePastWord.containsMatchIn(s)

/** Task 추가 시트의 "한 일 기록" (다른 기능에서 부르는 입구) */
@Composable
fun LogDoneView(
    goals: List<GoalCategory>,
    modifier: Modifier = Modifier,
    /** 입력칸에 글자가 있는지 (Task 추가 시트의 has_input — 방식을 바꾸거나 닫으면 확인 없이 사라져요) */
    onInputChange: (Boolean) -> Unit = {},
) {
    LogDoneViewContent(
        goals = goals, previewRows = null, previewTemps = emptyList(), previewDone = null, modifier = modifier,
        onInputChange = onInputChange,
    )
}

/** 미리보기용 — 단계별 화면을 네트워크 없이 그려 보려고 (iOS fileprivate init) */
@Composable
private fun LogDoneViewContent(
    goals: List<GoalCategory>,
    previewRows: List<RecordLogRow>?,
    previewTemps: List<TempGoalInfo>,
    previewDone: RecordActivityLogResponse?,
    modifier: Modifier = Modifier,
    onInputChange: (Boolean) -> Unit = {},
) {
    var text by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(previewRows) }
    var temps by remember { mutableStateOf(previewTemps) }
    var done by remember { mutableStateOf(previewDone) }
    var parsing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val lines: List<String> = text.split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(LOG_DONE_MAX_LINES)

    fun update(key: String, change: (RecordLogRow) -> RecordLogRow) {
        val rs = rows ?: return
        if (rs.none { it.clientKey == key }) return
        rows = rs.map { if (it.clientKey == key) change(it) else it }
    }

    fun reset() {
        text = ""
        rows = null
        temps = emptyList()
        done = null
    }

    suspend fun goParse() {
        val ls = lines
        if (ls.isEmpty() || parsing) return
        val today = KST.today()
        val req: List<ParseItemRequest> = ls.map { line ->
            val p = LineParser.parse(line, today)
            ParseItemRequest(
                clientKey = RecordAPI.newClientKey(),
                text = line,
                startTime = p.startTime,
                endTime = p.endTime,
                estimatedMinutes = p.minutes,
            )
        }
        val allLines = text.split("\n").count { it.isNotBlank() }
        TA.plain("task_add_log.parse_click", mapOf(
            "line_count" to ls.size, "dropped_count" to allLines - ls.size,
            "has_parsed_time" to req.any { it.startTime != null }, "submit_method" to "button",
        ))
        parsing = true
        try {
            val parsed = Mutation.run(errorFallback = "정리하지 못했어요. 다시 시도해 주세요.") {
                BrainDumpAPI.parse(req)
            }
            if (parsed == null) {
                TA.plain("task_add_log.parse_result", mapOf("result" to "fail", "error_status" to (Mutation.lastErrorStatus ?: 0)))
                return
            }
            rows = parsed.items.map { i ->
                val original = req.firstOrNull { it.clientKey == i.clientKey }?.text ?: i.title
                var link = ""
                val gid = i.goal.goalCategoryId
                val tk = i.goal.tempKey
                if (i.goal.type == GoalMatchType.EXISTING && gid != null) {
                    link = "goal:$gid"
                } else if (i.goal.type == GoalMatchType.TEMP && tk != null) {
                    link = "temp:$tk"
                }
                RecordLogRow(
                    clientKey = i.clientKey,
                    text = original,
                    title = i.title,
                    // "어제"는 Task 기준 AI 가 오늘로 당겨 오니 서버가 문장으로 다시 읽게 비워요
                    date = if (logDoneHasPastWord(original)) null else i.scheduledDate,
                    startTime = i.startTime?.take(5),
                    endTime = i.endTime?.take(5),
                    minutes = i.estimatedMinutes,
                    link = link,
                )
            }
            temps = parsed.tempGoals
            TA.plain("task_add_log.parse_result", mapOf(
                "result" to "success", "item_count" to parsed.items.size, "ai_failed" to parsed.aiFailed,
                "temp_count" to parsed.tempGoals.size, "missing_goal_count" to (rows ?: emptyList()).count { it.link.isEmpty() },
            ))
        } finally {
            parsing = false
        }
    }

    suspend fun goSave() {
        val rs = rows
        if (rs.isNullOrEmpty() || saving) return
        val usedTemps = rs.filter { it.link.startsWith("temp:") }.map { it.link.drop(5) }.toSet()
        val items = rs.map { r ->
            val trimmed = r.title.trim()
            RecordActivityLogItem(
                clientKey = r.clientKey,
                text = r.text,
                title = if (trimmed.isEmpty()) r.text else trimmed,
                date = r.date,
                startTime = r.startTime,
                endTime = if (r.startTime != null) r.endTime else null,
                minutes = r.minutes,
                goalCategoryId = if (r.link.startsWith("goal:")) r.link.drop(5).toIntOrNull() else null,
                tempGoalKey = if (r.link.startsWith("temp:")) r.link.drop(5) else null,
            )
        }
        val tempGoals = temps.filter { it.tempKey in usedTemps }
            .map { TempGoalInput(tempKey = it.tempKey, name = it.name, emoji = it.emoji) }
        val request = RecordActivityLogRequest(items = items, tempGoals = tempGoals)
        TA.plain("task_add_log.save_click", mapOf(
            "item_count" to rs.size, "missing_goal_count" to rs.count { it.link.isEmpty() }, "temp_count" to usedTemps.size,
        ))

        saving = true
        try {
            val res = Mutation.run {
                RecordAPI.logActivities(request)
            }
            if (res == null) {
                TA.plain("task_add_log.save_result", mapOf("result" to "fail", "error_status" to (Mutation.lastErrorStatus ?: 0)))
                return
            }
            TA.plain("task_add_log.save_result", mapOf(
                "result" to "success", "created_count" to res.activities.size, "temp_count" to res.tempGoals.size,
            ))
            val keys = mutableListOf(DataKey.activities, DataKey.record)
            if (res.tempGoals.isNotEmpty()) keys.add(DataKey.goals)
            Invalidation.invalidate(keys)
            done = res
            Toast.success("${res.activities.size}개를 기록했어요.")
        } finally {
            saving = false
        }
    }

    Column(modifier.fillMaxWidth().card().padding(20.dp)) {
        val d = done
        val rs = rows
        when {
            d != null -> LogDoneDoneStep(done = d, onReset = {
                TA.plain("task_add_log.more_click", mapOf("created_count" to d.activities.size))
                reset()
            })
            rs != null -> LogDoneReviewStep(
                rows = rs,
                goals = goals,
                temps = temps,
                saving = saving,
                onBack = {
                    TA.plain("task_add_log.rewrite_click", mapOf("item_count" to rs.size))
                    rows = null
                },
                onTitle = { key, v -> update(key) { it.copy(title = v.take(60)) } },
                onLink = { key, link ->
                    TA.plain("task_add_log.link_change", mapOf(
                        "goal_match" to if (link.startsWith("goal:")) "existing" else if (link.startsWith("temp:")) "temp" else "none",
                    ))
                    update(key) { it.copy(link = link) }
                },
                onRemove = { key ->
                    TA.plain("task_add_log.row_remove", mapOf("line_count" to rs.size - 1))
                    rows = rows?.filter { it.clientKey != key }
                },
                onSave = { scope.launch { goSave() } },
            )
            else -> LogDoneWriteStep(
                text = text,
                onTextChange = {
                    text = it
                    onInputChange(it.isNotBlank())
                },
                lineCount = lines.size,
                parsing = parsing,
                onParse = { scope.launch { goParse() } },
            )
        }
    }
}

// MARK: 1. 적기

@Composable
private fun LogDoneWriteStep(
    text: String,
    onTextChange: (String) -> Unit,
    lineCount: Int,
    parsing: Boolean,
    onParse: () -> Unit,
) {
    Column {
        Text("오늘 한 일을 한 줄씩 적어 주세요", style = NType.h4, color = NC.ink)
        Text(
            "시각(오후 2시)·걸린 시간(30분)·날짜(어제)를 같이 적으면 그대로 기록해요. 목표는 AI가 알아서 연결해요.",
            style = pt(13),
            color = NC.ink3,
            modifier = Modifier.padding(top = 4.dp),
        )
        NTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = LOG_DONE_PLACEHOLDER,
            minHeight = 150.dp,
            singleLine = false,
            textStyle = pt(15).copy(lineHeight = 22.sp),
            modifier = Modifier.padding(top = 16.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("$lineCount/${LOG_DONE_MAX_LINES}줄", style = ptMono(12), color = NC.ink3)
            Spacer(Modifier.weight(1f))
            NButton("정리하기", variant = NButtonVariant.ai, icon = "sparkles", loading = parsing, enabled = lineCount > 0) {
                onParse()
            }
        }
    }
}

// MARK: 2. 목표 연결 확인

@Composable
private fun LogDoneReviewStep(
    rows: List<RecordLogRow>,
    goals: List<GoalCategory>,
    temps: List<TempGoalInfo>,
    saving: Boolean,
    onBack: () -> Unit,
    onTitle: (String, String) -> Unit,
    onLink: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("이렇게 기록할게요", style = NType.h4, color = NC.ink)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onBack).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SfImage("arrow.left", size = 13.dp, tint = NC.ink3)
                Text("다시 적기", style = pt(14), color = NC.ink3)
            }
        }
        Text(
            "목표 연결만 확인하세요. 시각이 없는 일은 방금 끝낸 일로, 다른 기록과 겹치지 않게 채워요.",
            style = pt(13),
            color = NC.ink3,
            modifier = Modifier.padding(top = 4.dp),
        )
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in rows) {
                key(r.id) {
                    RecordLogRowEditor(
                        row = r,
                        goals = goals,
                        temps = temps,
                        title = r.title,
                        onTitleChange = { v -> onTitle(r.clientKey, v) },
                        onLink = { link -> onLink(r.clientKey, link) },
                        onRemove = { onRemove(r.clientKey) },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
            NButton("${rows.size}개 기록하기", loading = saving, enabled = rows.isNotEmpty()) { onSave() }
        }
    }
}

// MARK: 3. 완료

@Composable
private fun LogDoneDoneStep(done: RecordActivityLogResponse, onReset: () -> Unit) {
    val today = KST.today()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(24.dp).background(NC.actionPrimary, CircleShape), contentAlignment = Alignment.Center) {
                SfImage("checkmark", size = 12.dp, tint = Color.White)
            }
            Text("${done.activities.size}개를 기록했어요", style = NType.h4, color = NC.ink)
        }
        Column(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .border(1.dp, NC.borderSubtle, RoundedCornerShape(Radius.xxl)),
        ) {
            done.activities.forEachIndexed { i, a ->
                key(a.activityId) {
                    if (i > 0) HorizontalDivider(color = NC.borderSoft)
                    RecordLoggedActivityRow(activity = a, today = today)
                }
            }
        }
        Row(
            Modifier.padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NButton("더 기록하기") { onReset() }
            NButton("TimeTable에서 보기", variant = NButtonVariant.ghost, size = NButtonSize.sm) {
                // 웹 Link href="/" — Task 추가 시트를 닫고 캘린더로
                TA.track("task_add_log.done_link_click", mapOf("destination" to "calendar"))
                AnalyticsService.sheetCloseHint = "next"
                AppRouter.taskAdd = null
                AppRouter.openCalendar()
            }
        }
    }
}

// MARK: - 검토 줄 하나

@Composable
private fun RecordLogRowEditor(
    row: RecordLogRow,
    goals: List<GoalCategory>,
    temps: List<TempGoalInfo>,
    title: String,
    onTitleChange: (String) -> Unit,
    onLink: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val whenLabel: String = run {
        val today = KST.today()
        var prefix = ""
        val d = row.date
        if (d != null && d != today) {
            prefix = "${KST.formatMonthDay(d)} "
        } else if (logDoneHasPastWord(row.text)) {
            prefix = "지난 날 "
        }
        val s = row.startTime
        if (s != null) prefix + s + (row.endTime?.let { "–$it" } ?: "") else prefix + "방금"
    }

    val linkLabel: String = run {
        if (row.link.startsWith("goal:")) {
            val id = row.link.drop(5).toIntOrNull()
            val g = goals.firstOrNull { it.goalCategoryId == id }
            if (id != null && g != null) return@run (g.emoji?.let { "$it " } ?: "") + g.goalCategoryName
        }
        if (row.link.startsWith("temp:")) {
            val key = row.link.drop(5)
            val t = temps.firstOrNull { it.tempKey == key }
            if (t != null) return@run "＋ 새 임시 목표: ${t.name}"
        }
        "목표 없이"
    }

    val ink = NC.ink
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, NC.borderSubtle, RoundedCornerShape(Radius.xxl))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                singleLine = true,
                textStyle = pt(15, PTWeight.semibold).copy(color = ink),
                cursorBrush = SolidColor(NC.brand),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .semantics { contentDescription = "제목" },
                decorationBox = { inner ->
                    Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) { inner() }
                },
            )
            NIconButton("xmark", size = 14.dp, color = NC.ink4, label = "빼기") { onRemove() }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RecordLogChip(icon = "clock", text = whenLabel)
            val m = row.minutes
            if (m != null && row.endTime == null) {
                RecordLogChip(icon = "timer", text = KST.formatDuration(m))
            }
            Spacer(Modifier.weight(1f))
            LogDoneGoalMenu(
                linkLabel = linkLabel,
                isEmpty = row.link.isEmpty(),
                goals = goals,
                temps = temps,
                onLink = onLink,
            )
        }
    }
}

/** 연결할 목표 고르기 (iOS Menu) */
@Composable
private fun LogDoneGoalMenu(
    linkLabel: String,
    isEmpty: Boolean,
    goals: List<GoalCategory>,
    temps: List<TempGoalInfo>,
    onLink: (String) -> Unit,
) {
    val items = buildList {
        add(MenuItem(title = "목표 없이") { onLink("") })
        for (g in goals) {
            add(MenuItem(title = (g.emoji?.let { "$it " } ?: "") + g.goalCategoryName) { onLink("goal:${g.goalCategoryId}") })
        }
        for (t in temps) {
            add(MenuItem(title = "＋ 새 임시 목표: ${t.name}") { onLink("temp:${t.tempKey}") })
        }
    }
    val shape = RoundedCornerShape(Radius.lg)
    NMenu(items = items, modifier = Modifier.semantics { contentDescription = "연결할 목표" }) {
        Row(
            Modifier
                .widthIn(max = 200.dp)
                .height(32.dp)
                .background(NC.surface, shape)
                .border(1.dp, NC.borderSubtle, shape)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                linkLabel,
                style = pt(13),
                color = if (isEmpty) NC.ink3 else NC.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            SfImage("chevron.up.chevron.down", size = 10.dp, tint = if (isEmpty) NC.ink3 else NC.ink)
        }
    }
}

/** 회색 알약 (시각 · 걸린 시간) */
@Composable
private fun RecordLogChip(icon: String, text: String) {
    Row(
        Modifier
            .height(24.dp)
            .background(NC.subtle, CircleShape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SfImage(icon, size = 10.dp, tint = NC.ink2)
        Text(text, style = pt(12, PTWeight.semibold), color = NC.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// MARK: - 기록한 한 줄 (완료 화면)

@Composable
private fun RecordLoggedActivityRow(activity: ActualActivity, today: String) {
    val day = activity.startAt.take(10)
    val dayLabel = if (day == today) "오늘" else KST.formatMonthDay(day)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "$dayLabel ${KST.hm(activity.startAt)}–${KST.hm(activity.endAt)}",
            style = ptMono(13),
            color = NC.ink3,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            activity.title,
            style = pt(14, PTWeight.medium),
            color = NC.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val gid = activity.goalCategoryId
        if (gid != null) {
            Text(
                activity.goalCategoryName ?: "",
                style = pt(12),
                color = NC.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 120.dp)
                    .clickable(role = Role.Button) {
                        // 웹 Link /goal/{id} — 시트를 닫고 목표 상세로
                        TA.track("task_add_log.done_link_click", mapOf("destination" to "goal"))
                        AnalyticsService.sheetCloseHint = "next"
                        AppRouter.taskAdd = null
                        AppRouter.openGoal(gid)
                    },
            )
        }
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, heightDp = 600)
@Composable
private fun LogDoneViewWritePreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
            LogDoneView(goals = RecordSampleData.goals)
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun LogDoneViewReviewPreview() {
    val rows = listOf(
        RecordLogRow(
            clientKey = "a", text = "오전 10시 주간 회의 1시간", title = "주간 회의", date = null,
            startTime = "10:00", endTime = "11:00", minutes = 60, link = "goal:7",
        ),
        RecordLogRow(
            clientKey = "b", text = "어제 코드 리뷰 30분", title = "코드 리뷰", date = null,
            startTime = null, endTime = null, minutes = 30, link = "",
        ),
        RecordLogRow(
            clientKey = "c", text = "블로그 글 쓰기", title = "블로그 글 쓰기", date = null,
            startTime = null, endTime = null, minutes = null, link = "temp:t1",
        ),
    )
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
            LogDoneViewContent(
                goals = RecordSampleData.goals,
                previewRows = rows,
                previewTemps = listOf(TempGoalInfo(tempKey = "t1", name = "블로그", emoji = "✍️")),
                previewDone = null,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 600)
@Composable
private fun LogDoneViewDonePreview() {
    val today = KST.today()
    val y = KST.addDays(today, -1)
    val json = """
    {"activities": [
      {"activityId": 1, "title": "주간 회의", "startAt": "${today}T10:00:00", "endAt": "${today}T11:00:00",
       "durationMinutes": 60, "goalCategoryId": 7, "goalCategoryName": "나를 업무"},
      {"activityId": 2, "title": "코드 리뷰", "startAt": "${y}T16:00:00",
       "endAt": "${y}T16:30:00", "durationMinutes": 30}
    ], "tempGoals": []}
    """
    val done = NJson.decodeFromString(RecordActivityLogResponse.serializer(), json)
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
            LogDoneViewContent(goals = RecordSampleData.goals, previewRows = null, previewTemps = emptyList(), previewDone = done)
        }
    }
}
