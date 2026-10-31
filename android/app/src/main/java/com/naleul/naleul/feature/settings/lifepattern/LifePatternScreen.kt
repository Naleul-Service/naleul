// feature/settings/lifepattern/LifePatternScreen.kt
//
// iOS Features/Settings/LifePattern/LifePatternScreen.swift
// 웹 원본: features/life-pattern/ui/LifePatternView.tsx (/settings/life-pattern)
//          features/life-pattern/api.ts (추가·수정·삭제 → lifePatterns + timetable 다시 불러오기)
//
// 기본 생활 패턴(고정 시간) 한 화면에서 추가·수정·삭제.
// 여기서 바꾸면 "오늘부터 모든 날짜"의 기본값이 바뀌어요. (그날만 바꾸기는 캘린더에서)

package com.naleul.naleul.feature.settings

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

/** 시트로 여는 폼 요청 (열 때마다 새 id → 시트가 초기값으로 새로 그려져요) */
data class LifePatternFormRequest(
    /** null 이면 추가 */
    val editing: LifePattern? = null,
    /** 추가할 때 처음 채울 값 (추천 칩에서 열었을 때) */
    val initial: LifePatternFormValue? = null,
    val id: String = UUID.randomUUID().toString(),
) {
    companion object {}
}

class LifePatternViewModel(
    /** 미리보기용: 샘플을 넣으면 네트워크를 쓰지 않아요 */
    private val sample: List<LifePattern>? = null,
) : ScreenModel() {
    var phase by mutableStateOf<LoadPhase<List<LifePattern>>>(LoadPhase.Loading)

    init {
        if (sample != null) phase = LoadPhase.Loaded(sample)
    }

    val patterns: List<LifePattern> get() = phase.value ?: emptyList()

    var lastErrorStatus: Int? = null
        private set
    private var pageviewSent = false
    private var limitSent = false

    suspend fun load() {
        if (sample != null) { phase = LoadPhase.Loaded(sample); return }
        // 이미 보여주고 있으면 로딩 화면으로 깜빡이지 않게 그대로 두고 바꿔요
        try {
            phase = LoadPhase.Loaded(TimetableAPI.lifePatterns())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (phase.value == null) {
                phase = LoadPhase.Failed(errorMessage(e))
                lastErrorStatus = ST.status(e)
                ST.track("life_pattern.load_fail", mapOf("error_status" to lastErrorStatus))
            }
        }
        val list = phase.value
        if (!pageviewSent && list != null) {
            pageviewSent = true
            ST.track("life_pattern.pageview", mapOf(
                "pattern_count" to list.size,
                "has_sleep" to list.any { it.patternType == LifePatternType.SLEEP },
                "entry_source" to (ST.pendingLifePatternEntry ?: "settings"),
            ))
            ST.pendingLifePatternEntry = null
        }
        // 10개라 추가 버튼 · 추천 칩이 꺼질 때 (화면마다 한 번)
        if (list != null && list.size >= LifePatternRules.maxPatterns && !limitSent) {
            limitSent = true
            ST.track("life_pattern.limit_view", mapOf("pattern_count" to list.size))
        }
    }

    fun trackRetry() = ST.track("life_pattern.retry_click", mapOf("error_status" to lastErrorStatus))

    /** 성공하면 true */
    suspend fun submit(input: LifePatternInput, editing: LifePattern?): Boolean {
        if (sample != null) return true
        val keys = listOf(DataKey.lifePatterns, DataKey.timetable)
        val ok = if (editing != null) {
            val r: Unit? = Mutation.run(invalidate = keys, successMessage = "고정 시간을 수정했어요.") {
                LifePatternAPI.update(lifePatternId = editing.lifePatternId, v = input)
            }
            r != null
        } else {
            val r: Unit? = Mutation.run(invalidate = keys, successMessage = "고정 시간을 추가했어요.") {
                LifePatternAPI.create(input)
            }
            r != null
        }
        ST.track("life_pattern.save_result", mapOf(
            "is_new" to (editing == null), "pattern_type" to (editing?.patternType ?: input.patternType).rawValue,
        ) + GD.result())
        return ok
    }

    suspend fun delete(p: LifePattern) {
        if (sample != null) return
        val r: Unit? = Mutation.run(invalidate = listOf(DataKey.lifePatterns, DataKey.timetable), successMessage = "고정 시간을 삭제했어요.") {
            LifePatternAPI.delete(lifePatternId = p.lifePatternId)
        }
        ST.track("life_pattern.delete_result", mapOf("pattern_type" to p.patternType.rawValue) + GD.result())
    }

    companion object {}
}

/**
 * 기본 생활 패턴 (쌓이는 화면)
 * @param sample 미리보기용 (null 이면 서버에서 불러와요)
 */
@Composable
fun LifePatternScreen(sample: List<LifePattern>? = null) {
    val vm = rememberScreenModel(sample) { LifePatternViewModel(sample) }
    var form by remember { mutableStateOf<LifePatternFormRequest?>(null) }
    var deleting by remember { mutableStateOf<LifePattern?>(null) }
    val scope = rememberCoroutineScope()

    ReloadOn(listOf(DataKey.lifePatterns)) { vm.load() }

    val patterns = vm.patterns
    val full = patterns.size >= LifePatternRules.maxPatterns
    val isError = vm.phase is LoadPhase.Failed

    fun openCreate(initial: LifePatternFormValue?) {
        form = LifePatternFormRequest(editing = null, initial = initial)
    }

    /** entryPoint: list · overview · preset · form_suggestion */
    fun openEdit(p: LifePattern, entryPoint: String = "list") {
        ST.track("life_pattern.edit_click", mapOf("pattern_type" to p.patternType.rawValue, "entry_point" to entryPoint))
        form = LifePatternFormRequest(editing = p, initial = null)
    }

    fun openPreset(preset: LifePatternPreset) {
        val existing = LifePatternRules.existingOfType(patterns, preset)
        ST.track("life_pattern.preset_click", mapOf(
            "pattern_type" to preset.patternType.rawValue, "is_new" to (existing == null), "pattern_count" to patterns.size,
        ))
        if (existing != null) openEdit(existing, "preset") else openCreate(LifePatternFormValue.from(preset = preset))
    }

    PushedScreen(title = "기본 생활 패턴") {
        ScreenColumn {
            // 머리말
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("매주 반복되는 고정 시간이에요. 이 시간은 비워두고 Task를 배치해요.", style = pt(14), color = NC.ink3)
                NButton("고정 시간 추가", icon = "plus", enabled = !(full || isError || vm.phase.isLoading)) {
                    ST.track("life_pattern.add_click", mapOf("pattern_count" to patterns.size))
                    openCreate(null)
                }
                if (full) {
                    FieldMessage(text = "고정 시간은 최대 ${LifePatternRules.maxPatterns}개까지 만들 수 있어요")
                }
            }

            when (vm.phase) {
                is LoadPhase.Loading -> LoadingView(minHeight = 420.dp)
                is LoadPhase.Failed -> EmptyStateCard(title = "고정 시간을 불러오지 못했어요") {
                    NButton("다시 시도", variant = NButtonVariant.secondary) {
                        vm.trackRetry()
                        scope.launch { vm.load() }
                    }
                }
                is LoadPhase.Loaded -> {
                    LifePatternPresetCard(patterns = patterns, full = full, onPreset = { openPreset(it) })
                    LifePatternListCard(patterns = patterns, onEdit = { openEdit(it, "list") }, onDelete = {
                        ST.track("life_pattern.delete_click", mapOf("pattern_type" to it.patternType.rawValue))
                        deleting = it
                    })
                    if (patterns.isNotEmpty()) {
                        LifePatternWeeklyOverview(patterns = patterns, onSelect = { openEdit(it, "overview") })
                    }
                    Text(
                        "특정 날짜만 바꾸거나 비우려면 캘린더에서 고정 시간 블록을 눌러 주세요.",
                        style = pt(12),
                        color = NC.ink4,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    form?.let { req ->
        key(req.id) {
            LifePatternFormSheet(
                editing = req.editing,
                initial = req.initial,
                patterns = patterns,
                // 성공하면 시트가 스스로 닫히는 애니메이션을 하고 onDismiss 로 form 을 비워요
                onSubmit = { input -> vm.submit(input, editing = req.editing) },
                onEditExisting = { p -> openEdit(p, "form_suggestion") },
                onDismiss = { form = null },
            )
        }
    }

    // 삭제 확인창 (보여줄 패턴을 지금 값으로 붙잡아 둬요 — 확인을 누르는 순간 deleting 이 null 이 될 수 있어서)
    val target = deleting
    ConfirmDialog(
        isPresented = target != null,
        onDismiss = { deleting = null },
        title = "'${target?.title ?: ""}'을 삭제할까요?",
        message = if (target?.patternType == LifePatternType.SLEEP) {
            "수면 시간이 없으면 하루 범위를 00:00~24:00으로 보고 Task를 배치해요."
        } else {
            "오늘부터 이 시간에도 Task가 배치될 수 있어요."
        },
        confirmLabel = "삭제",
        tone = ConfirmTone.danger,
        onCancel = { target?.let { ST.track("life_pattern.delete_cancel", mapOf("pattern_type" to it.patternType.rawValue, "close_method" to "cancel_button")) } },
    ) {
        if (target != null) {
            ST.track("life_pattern.delete_confirm", mapOf("pattern_type" to target.patternType.rawValue))
            scope.launch { vm.delete(target) }
        }
    }
}

/** 추천 */
@Composable
private fun LifePatternPresetCard(patterns: List<LifePattern>, full: Boolean, onPreset: (LifePatternPreset) -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SfImage("sparkles", size = 14.dp, tint = NC.textBrand)
            Text("추천으로 빠르게 추가", style = pt(14, PTWeight.bold), color = NC.ink)
        }
        FlowLayout(spacing = 6.dp) {
            for (p in LifePatternRules.presets) {
                val existing = LifePatternRules.existingOfType(patterns, p)
                val disabled = existing == null && full
                NChip(
                    LifePatternRules.presetChipTitle(p, existing = existing),
                    size = ChipSize.sm,
                    modifier = Modifier.alpha(if (disabled) 0.4f else 1f),
                ) { if (!disabled) onPreset(p) }
            }
        }
    }
}

/** 목록 */
@Composable
private fun LifePatternListCard(patterns: List<LifePattern>, onEdit: (LifePattern) -> Unit, onDelete: (LifePattern) -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardHeader("고정 시간", aside = "${patterns.size} / ${LifePatternRules.maxPatterns}")
        if (patterns.isEmpty()) {
            val ink2 = NC.ink2
            Text(
                buildAnnotatedString {
                    append("등록된 고정 시간이 없어요. 위 추천이나 ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ink2)) { append("고정 시간 추가") }
                    append("로 만들어 보세요.")
                },
                style = pt(14),
                color = NC.ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
            )
        } else {
            Column {
                patterns.forEachIndexed { i, p ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(NC.line))
                    LifePatternRow(pattern = p, onEdit = { onEdit(p) }, onDelete = { onDelete(p) })
                }
            }
        }
    }
}

/** 목록 한 줄 */
@Composable
private fun LifePatternRow(pattern: LifePattern, onEdit: () -> Unit, onDelete: () -> Unit) {
    val p = pattern
    val ink2 = NC.ink2
    val brand = NC.brand
    val detail = buildAnnotatedString {
        append("${LifePatternRules.toHm(p.startTime)} – ${LifePatternRules.toHm(p.endTime)}")
        if (p.crossesMidnight) append(" (다음 날)")
        append(" · ${LifePatternRules.duration(p)} · ")
        withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = ink2)) { append(LifePatternRules.formatDays(p.days)) }
        val n = p.dayTimes?.size ?: 0
        if (n > 0) {
            withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = brand)) { append(" · 요일별 시간 ${n}일") }
        }
    }
    val emoji = p.emoji
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).background(NC.subtle, RoundedCornerShape(Radius.xl)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (emoji.isNullOrEmpty()) "📌" else emoji, fontSize = 18.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    p.title,
                    style = pt(15, PTWeight.bold),
                    color = NC.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (p.patternType != LifePatternType.CUSTOM) {
                    NBadge(LifePatternRules.typeLabel(p.patternType))
                }
            }
            Text(detail, style = pt(13), color = NC.ink3)
        }
        NIconButton("pencil", size = 15.dp, label = "${p.title} 수정") { onEdit() }
        NIconButton("trash", size = 15.dp, label = "${p.title} 삭제") { onDelete() }
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun LifePatternScreenPreview() {
    NaleulTheme(dark = false) {
        LifePatternScreen(sample = LifePatternSamples.patterns)
    }
}

@Preview(showBackground = true, heightDp = 900, name = "비어 있을 때")
@Composable
private fun LifePatternScreenEmptyPreview() {
    NaleulTheme(dark = false) {
        LifePatternScreen(sample = emptyList())
    }
}
