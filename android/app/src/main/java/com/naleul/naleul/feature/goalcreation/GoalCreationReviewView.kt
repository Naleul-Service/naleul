// feature/goalcreation/GoalCreationReviewView.kt
//
// iOS Features/GoalCreation/GoalCreationReviewView.swift
// G-2 정리 확인 (/goal/new/{sessionId}/review) — 초안 검토에서 "조건 수정"으로 돌아와도 써요.
// 웹 원본: features/goal-creation/ui/review/ReviewScreen.tsx · ReviewView.tsx

package com.naleul.naleul.feature.goalcreation

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private fun goalCreationStyleDescription(s: PlanningStyle): String = when (s) {
    PlanningStyle.PLANNER -> "요일·시간을 정해 매일 조금씩"
    PlanningStyle.SPONTANEOUS -> "여유 있게, 몰아서 해도 괜찮게"
}

/**
 * @param reload 다시 불러오기 (409 → 서버 상태에 맞는 화면으로)
 */
@Composable
fun GoalCreationReviewView(session: GoalCreationSessionDetail, reload: suspend () -> Unit) {
    val nav = LocalGoalCreationNavigator.current
    val scope = rememberCoroutineScope()

    /** 웹은 저장 응답으로 query 캐시를 바로 고쳐요 → 여기서는 이 화면의 세션 사본을 고쳐요 */
    var current by remember { mutableStateOf(session) }
    var editing by remember { mutableStateOf<GoalCreationSlotKey?>(null) }
    /** 꼭 할 일 수정 (슬롯이 아니라 따로 저장돼요) */
    var mustDoOpen by remember { mutableStateOf(false) }
    var mustDoDraft by remember { mutableStateOf(session.mustDoItems ?: emptyList()) }
    var mustDoText by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    var style by remember { mutableStateOf(session.summary?.planningStyle ?: PlanningStyle.PLANNER) }
    var styleByUser by remember { mutableStateOf(GoalCreationStyleMemory.wasSelectedByUser(session.sessionId)) }
    var generating by remember { mutableStateOf(false) }
    /**
     * 계획 강도 — 처음엔 아무것도 고르지 않은 상태 (2026-10-25: "추천" 표시 · 기본 선택 제거 — AI 가 정한 것처럼 보이지 않게).
     * 고르지 않고 계획 만들기를 누르면 안정형으로 만들어요. 초안 화면에서 바꾸면 한 번 다시 만들어요
     */
    var intensity by remember { mutableStateOf<GoalCreationPlanIntensity?>(null) }

    val sessionId = current.sessionId
    val summary = current.summary
    val regenerate = current.status == GoalCreationSessionStatus.DRAFT_READY

    // MARK: 이벤트
    /** 지금 열린 시트의 항목 (닫힐 때 slot_edit_cancel 에 붙여요) */
    var openSlotKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val s = summary
        val filled = listOf(s?.goalStatement, s?.metricText, s?.deadlineText, s?.approach, s?.motivation, s?.preferenceText)
            .count { !it.isNullOrEmpty() } + (if (current.mustDoItems.isNullOrEmpty()) 0 else 1)
        GCTracker.track(
            GCEvent.reviewPageview,
            "session_id" to sessionId, "is_regenerate" to regenerate, "filled_slot_count" to filled,
            "ai_style" to s?.planningStyle?.name,
        )
    }

    fun trackSlotOpen(key: String, wasEmpty: Boolean) {
        openSlotKey = key
        GCTracker.sheetClose = null
        GCTracker.track(GCEvent.slotEditOpen, "session_id" to sessionId, "slot_key" to key, "was_empty" to wasEmpty)
    }

    /** 시트가 닫힌 뒤 — 저장으로 닫힌 게 아니면 취소 (cancel_button / x_button / swipe_down) */
    fun trackSheetCancel() {
        val key = openSlotKey ?: return
        openSlotKey = null
        val m = GCTracker.takeSheetClose() ?: return
        GCTracker.track(GCEvent.slotEditCancel, "session_id" to sessionId, "slot_key" to key, "close_method" to m)
    }

    fun slotEmpty(slot: GoalCreationSlotKey): Boolean {
        val s = summary
        val v = when (slot) {
            GoalCreationSlotKey.goalStatement -> s?.goalStatement
            GoalCreationSlotKey.metric -> s?.metricText
            GoalCreationSlotKey.motivation -> s?.motivation
            GoalCreationSlotKey.deadline -> s?.deadlineText
            GoalCreationSlotKey.approach -> s?.approach
            GoalCreationSlotKey.practicePreference -> s?.preferenceText
        }
        return v.isNullOrEmpty()
    }

    fun openEdit(slot: GoalCreationSlotKey) {
        saveError = null
        trackSlotOpen(slot.rawValue, slotEmpty(slot))
        editing = slot
    }

    fun chooseStyle(next: PlanningStyle) {
        style = next
        styleByUser = true
        GoalCreationStyleMemory.markSelectedByUser(sessionId)
    }

    suspend fun save(patch: GoalCreationSlotsPatch, slotKey: String) {
        saving = true
        saveError = null
        try {
            val turn = GoalCreationAPI.updateSlots(sessionId, patch)
            GCTracker.track(GCEvent.slotEditResult, "session_id" to sessionId, "slot_key" to slotKey, "result" to "success")
            // 저장으로 닫힌 거라 취소로 세지 않아요 (Android 는 시트가 화면에서 빠져 onDismiss 가 안 와서 여기서 정리)
            openSlotKey = null
            GCTracker.sheetClose = null
            // 화면 데이터를 응답으로 바로 갱신 → 다시 불러오지 않아도 요약이 바뀌어요
            current = current.copy(status = turn.status, slots = turn.slots, summary = turn.summary, mustDoItems = turn.mustDoItems)
            editing = null
            mustDoOpen = false
            Toast.success("수정했어요.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GCTracker.track(
                GCEvent.slotEditResult,
                "session_id" to sessionId, "slot_key" to slotKey, "result" to "fail", "error_status" to gcErrorStatus(e),
            )
            val api = e as? ApiError
            if (api != null && api.httpStatus == 409) {
                openSlotKey = null
                // 이미 생성이 시작됐거나 끝난 세션 → 서버 상태로 화면 이동
                Toast.show(api.errorDescription)
                editing = null
                mustDoOpen = false
                saving = false
                reload()
                return
            }
            saveError = errorMessage(e, "저장하지 못했어요. 다시 시도해 주세요.")
        } finally {
            saving = false
        }
    }

    suspend fun generate() {
        if (generating) return
        GCTracker.track(
            GCEvent.generateClick,
            "session_id" to sessionId, "planning_style" to style.name, "style_by_user" to styleByUser,
            "plan_intensity" to (intensity ?: GoalCreationPlanIntensity.STEADY).name,
            "intensity_by_user" to (intensity != null), "is_regenerate" to regenerate,
        )
        generating = true
        try {
            // 성향은 사용자가 직접 골랐을 때만 보내요 (안 보내면 서버가 대화에서 추정한 값 사용)
            val res = GoalCreationAPI.requestDraft(
                sessionId,
                GoalCreationDraftRequestBody(planningStyle = if (styleByUser) style else null, intensity = intensity ?: GoalCreationPlanIntensity.STEADY),
            )
            GCTracker.track(GCEvent.generateResult, "session_id" to sessionId, "result" to "success", "draft_id" to res.draftId)
            Invalidation.invalidate(DataKey.usage)
            GCTracker.setGenerateSource("review")
            nav.go(GoalCreationRoute.generating(sessionId, draftId = res.draftId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            generating = false
            GCTracker.track(GCEvent.generateResult, "session_id" to sessionId, "result" to "fail", "error_status" to gcErrorStatus(e))
            if ((e as? ApiError)?.httpStatus == 409) {
                GCTracker.setGenerateSource("review")
                // 이미 생성 중 (다른 기기에서 눌렀거나 두 번 누름) → 생성 중 화면으로
                nav.go(GoalCreationRoute.generating(sessionId, draftId = null))
                return
            }
            Toast.error(errorMessage(e, "계획을 만들지 못했어요. 다시 시도해 주세요."))
        }
    }

    Column(Modifier.fillMaxSize().background(NC.surface)) {
        GoalCreationHeader(onClose = {
            GCTracker.track(GCEvent.reviewClose, "session_id" to sessionId)
            nav.close()
        })
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 40.dp),
        ) {
            NBadge("정리 확인", tone = BadgeTone.mint, icon = "sparkles")
            Text("이렇게 정리했어요", style = NType.h2, color = NC.textPrimary, modifier = Modifier.padding(top = 12.dp))
            Text(
                "항목을 눌러 고칠 수 있어요. 확인되면 계획을 만들어 드릴게요.",
                style = pt(15),
                color = NC.ink3,
                modifier = Modifier.padding(top = 6.dp),
            )

            // MARK: 목표 정보
            Column(Modifier.padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("목표 정보", style = pt(15, PTWeight.bold), color = NC.ink)
                val shape = RoundedCornerShape(Radius.card)
                Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, NC.borderSubtle, shape)) {
                    ReviewSummaryRow("목표", summary?.goalStatement) { openEdit(GoalCreationSlotKey.goalStatement) }
                    HorizontalDivider(color = NC.borderSoft)
                    ReviewSummaryRow("수치", summary?.metricText) { openEdit(GoalCreationSlotKey.metric) }
                    HorizontalDivider(color = NC.borderSoft)
                    ReviewSummaryRow("기한", summary?.deadlineText) { openEdit(GoalCreationSlotKey.deadline) }
                    HorizontalDivider(color = NC.borderSoft)
                    ReviewSummaryRow("계획", summary?.approach) { openEdit(GoalCreationSlotKey.approach) }
                    HorizontalDivider(color = NC.borderSoft)
                    // 예전 세션(이유를 묻던 때)에 답한 이유가 있으면 보여줘요
                    val motivation = summary?.motivation
                    if (!motivation.isNullOrEmpty()) {
                        ReviewSummaryRow("이유", motivation) { openEdit(GoalCreationSlotKey.motivation) }
                        HorizontalDivider(color = NC.borderSoft)
                    }
                    ReviewSummaryRow("실천 시간", summary?.preferenceText) { openEdit(GoalCreationSlotKey.practicePreference) }
                    HorizontalDivider(color = NC.borderSoft)
                    val must = current.mustDoItems ?: emptyList()
                    ReviewSummaryRow("꼭 할 일", if (must.isEmpty()) null else must.joinToString(" · ")) {
                        saveError = null
                        mustDoDraft = current.mustDoItems ?: emptyList()
                        mustDoText = ""
                        trackSlotOpen("must_do", must.isEmpty())
                        mustDoOpen = true
                    }
                }
            }

            // MARK: 실행 성향
            Column(Modifier.padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReviewSectionTitle("실행 성향", aside = if (styleByUser) null else "대화를 바탕으로 AI가 골랐어요")
                // 두 카드 높이를 같게
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (value in listOf(PlanningStyle.PLANNER, PlanningStyle.SPONTANEOUS)) {
                        GoalCreationChoiceCard(
                            title = value.goalCreationLabel,
                            badge = null,
                            description = goalCreationStyleDescription(value),
                            selected = style == value,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            GCTracker.track(
                                GCEvent.styleSelect,
                                "session_id" to sessionId, "planning_style" to value.name, "ai_style" to summary?.planningStyle?.name,
                            )
                            chooseStyle(value)
                        }
                    }
                }
            }

            // MARK: 계획 강도
            Column(Modifier.padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReviewSectionTitle("계획 강도", aside = "고르지 않으면 안정형으로 만들어요")
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (value in GoalCreationPlanIntensity.entries) {
                        GoalCreationChoiceCard(
                            title = value.label,
                            badge = null,
                            description = value.descriptionText,
                            selected = intensity == value,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            GCTracker.track(GCEvent.intensitySelect, "session_id" to sessionId, "plan_intensity" to value.name)
                            intensity = value
                        }
                    }
                }
            }
        }

        // MARK: 하단 버튼 (iOS safeAreaInset(edge: .bottom))
        Column(Modifier.fillMaxWidth().background(NC.surface)) {
            HorizontalDivider(color = NC.borderSoft)
            NButton(
                if (regenerate) "이 정보로 다시 만들기" else "계획 만들기",
                variant = NButtonVariant.ai,
                size = NButtonSize.lg,
                icon = if (generating) null else "sparkles",
                loading = generating,
                fullWidth = true,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) { scope.launch { generate() } }
        }
    }

    // MARK: 시트
    editing?.let { slot ->
        GoalCreationSlotEditSheet(
            slot = slot,
            slots = current.slots,
            saving = saving,
            serverError = saveError,
            onClose = { editing = null; trackSheetCancel() },
            onSave = { patch -> scope.launch { save(patch, slot.rawValue) } },
        )
    }

    if (mustDoOpen) {
        SheetScaffold(
            onDismiss = { mustDoOpen = false; trackSheetCancel() },
            title = "꼭 하고 싶은 일",
            description = "고른 일은 계획에 꼭 루틴으로 넣어 드려요.",
            dismissible = !saving,
            footer = { close ->
                NButton("취소", variant = NButtonVariant.secondary, enabled = !saving) {
                    GCTracker.sheetClose = "cancel_button"
                    close()
                }
                NButton("저장", loading = saving) {
                    GCTracker.track(GCEvent.slotEditSave, "session_id" to sessionId, "slot_key" to "must_do")
                    val items = GoalCreationMustDo.withDraft(mustDoDraft, mustDoText)
                    scope.launch { save(GoalCreationSlotsPatch(mustDoItems = items), "must_do") }
                }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalCreationMustDoPicker(
                    value = mustDoDraft,
                    onValueChange = { mustDoDraft = it },
                    draft = mustDoText,
                    onDraftChange = { mustDoText = it },
                    disabled = saving,
                    surface = "goal_review",
                )
                saveError?.let { Text(it, style = pt(13), color = NC.danger) }
            }
        }
    }
}

@Composable
private fun ReviewSummaryRow(label: String, value: String?, action: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = action)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, style = pt(13), color = NC.ink3, modifier = Modifier.width(64.dp))
        Text(
            value ?: "AI가 알맞게 정해줄게요",
            style = pt(15, if (value == null) PTWeight.regular else PTWeight.semibold).copy(lineHeight = 21.sp),
            color = if (value == null) NC.ink4 else NC.ink,
            modifier = Modifier.weight(1f),
        )
        SfImage("chevron.right", size = 13.dp, tint = NC.ink4)
    }
}

@Composable
private fun ReviewSectionTitle(title: String, aside: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = pt(15, PTWeight.bold), color = NC.ink)
        Spacer(Modifier.weight(1f))
        if (aside != null) Text(aside, style = pt(12), color = NC.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 둘 중 하나 고르는 카드 (실행 성향 · 계획 강도) */
@Composable
fun GoalCreationChoiceCard(
    title: String,
    badge: String?,
    description: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    action: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.xxl)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) NC.brandSoft else NC.surface, shape)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) NC.borderFocus else NC.borderSubtle, shape)
            .clickable(role = Role.RadioButton, onClick = action)
            .semantics { this.selected = selected }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = pt(15, PTWeight.bold), color = if (selected) NC.textBrand else NC.ink)
            if (badge != null) Text(badge, style = pt(12, PTWeight.medium), color = NC.ink3)
        }
        Text(description, style = pt(13), color = NC.ink3)
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun GoalCreationReviewViewPreview() {
    NaleulTheme(dark = false) {
        CompositionLocalProvider(LocalGoalCreationNavigator provides remember { GoalCreationNavigator(GoalCreationRoute.review(101)) }) {
            GoalCreationReviewView(session = GoalCreationSamples.session, reload = {})
        }
    }
}
