// feature/record/RecordGoalForm.kt
//
// iOS Features/Record/RecordGoalForm.swift
// 웹 features/record/ui/RecordGoalForm.tsx (+ goal/edit/inline.tsx InlineForm 모양)
// 업무형 목표 만들기 (goal_mode = RECORD).
// 회사 업무 · 취업 준비 · 동아리처럼 종류가 다양해서 종류를 고르지 않고 이름만 받아요.
// 기한 · 수치 · 세부 목표도 묻지 않아요 — 만든 뒤 주제마다 마감을 정하면 로드맵이 그려져요 (V53).
//
// 웹은 만든 뒤 router.push(`/goal/{id}`) → 앱은 지금 화면(목표 추가)을 목표 상세로 바꿔요 (AppRouter.replaceWithGoal).
// 웹의 Enter 저장 · Esc 취소 단축키 대신 버튼 + 키보드 완료 버튼으로 저장해요.

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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 이름 예시 — 누르면 이름과 이모지가 같이 채워져요 (웹 EXAMPLES) */
private data class RecordNameExample(val emoji: String, val name: String) {
    companion object {
        val all: List<RecordNameExample> = listOf(
            RecordNameExample(emoji = "💼", name = "회사 업무"),
            RecordNameExample(emoji = "🎯", name = "취업 준비"),
            RecordNameExample(emoji = "🚀", name = "사이드 프로젝트"),
            RecordNameExample(emoji = "🎸", name = "동아리 활동"),
            RecordNameExample(emoji = "🌍", name = "대외활동"),
            RecordNameExample(emoji = "📚", name = "스터디 운영"),
            RecordNameExample(emoji = "🎓", name = "졸업 프로젝트"),
        )
    }
}

private val recordEmojis = listOf("💼", "🎯", "🚀", "🎸", "🌍", "📚", "🎓", "🧑‍💻", "📊", "✍️")

@Composable
fun RecordGoalForm(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** 목표 추가 화면에서만 goal_add.record_* 이벤트를 보내요 */
    analytics: Boolean = false,
    /** 만든 뒤 (넘기면 상세로 바로 가지 않고 부르는 쪽이 "보러 가기" 토스트를 띄워요 · 2026-10) */
    onCreated: ((Int) -> Unit)? = null,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf(RecordNameExample.all[0].emoji) }
    var colorId by remember { mutableStateOf<Int?>(null) }
    var styleOpen by remember { mutableStateOf(false) }
    var colors by remember { mutableStateOf<List<UserColor>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }
    /** 마지막으로 누른 이름 예시 (record_submit 의 name_from_example · emoji_changed) */
    var pickedExample by remember { mutableStateOf<RecordNameExample?>(null) }
    val nameFocus = remember { FocusRequester() }

    fun track(event: String, props: Map<String, Any?> = emptyMap()) {
        if (analytics) GD.track(event, props)
    }
    val scope = rememberCoroutineScope()

    val shownEmoji = emoji
    val value = name.trim()

    ReloadOn(listOf(DataKey.userColors)) {
        colors = try {
            ColorAPI.userColors()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    // 처음 뜨면 이름 칸에 바로 커서 (iOS .onAppear { nameFocused = true })
    LaunchedEffect(Unit) { runCatching { nameFocus.requestFocus() } }

    suspend fun submit(method: String) {
        if (value.isEmpty() || saving) return
        track("goal_add.record_submit", mapOf(
            "name_from_example" to (pickedExample?.name == value), "has_description" to description.trim().isNotEmpty(),
            "emoji_changed" to (shownEmoji != (pickedExample?.emoji ?: RecordNameExample.all[0].emoji)),
            "color_changed" to (colorId != null), "submit_method" to method,
        ))
        saving = true
        try {
            val desc = description.trim()
            val result = Mutation.run(
                invalidate = listOf(DataKey.goals),
                successMessage = if (onCreated != null) null else "업무형 목표를 만들었어요. 세부 목표와 마감부터 정해 보세요.",
            ) {
                RecordAPI.createRecordGoal(
                    name = value,
                    emoji = shownEmoji,
                    colorId = colorId,
                    description = if (desc.isEmpty()) null else desc,
                )
            }
            track("goal_add.create_result", GD.result() + mapOf(
                "goal_mode" to "RECORD", "goal_category_id" to result?.goalCategoryId,
            ))
            if (result != null) {
                val created = onCreated
                if (created != null) created(result.goalCategoryId) else AppRouter.replaceWithGoal(result.goalCategoryId)
            }
        } finally {
            saving = false
        }
    }

    val brand = NC.brand
    Column(
        modifier
            .fillMaxWidth()
            .background(NC.brandSoft.copy(alpha = 0.4f), RoundedCornerShape(Radius.xxl))
            .border(1.dp, NC.borderSubtle, RoundedCornerShape(Radius.xxl))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // MARK: ① 이름
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RecordFormLabel(text = "이름")
            NTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "예: 취업 준비 · 동아리 활동 · OO팀 업무",
                minHeight = 40.dp,
                maxLength = 40,
                onSubmit = { scope.launch { submit("keyboard_done") } },
                textStyle = pt(14),
                modifier = Modifier.focusRequester(nameFocus),
            )
            FlowLayout(spacing = 6.dp, modifier = Modifier.padding(top = 4.dp)) {
                for (ex in RecordNameExample.all) {
                    Text(
                        "${ex.emoji} ${ex.name}",
                        style = pt(12),
                        color = NC.ink3,
                        modifier = Modifier
                            .height(28.dp)
                            .clip(CircleShape)
                            .border(1.dp, NC.borderSubtle, CircleShape)
                            .clickable(role = Role.Button) {
                                track("goal_add.record_example_click", mapOf("example_index" to RecordNameExample.all.indexOf(ex)))
                                pickedExample = ex
                                name = ex.name
                                emoji = ex.emoji
                            }
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = 10.dp),
                    )
                }
            }
        }

        // MARK: ② 한 줄 설명
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RecordFormLabel(text = "무엇을 하나요? (선택)")
            NTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = "예: 하반기 백엔드 개발자 공채 지원",
                minHeight = 40.dp,
                maxLength = 100,
                textStyle = pt(14),
            )
        }

        // MARK: ③ 꾸미기 — 기본값이 있어서 접어 둬요
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) {
                        track("goal_add.record_style_toggle", mapOf("expanded" to !styleOpen))
                        styleOpen = !styleOpen
                    }
                    .semantics { contentDescription = if (styleOpen) "이모지·색상 바꾸기, 펼침" else "이모지·색상 바꾸기, 접힘" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(shownEmoji, fontSize = 16.sp)
                Text("이모지·색상 바꾸기", style = pt(13, PTWeight.semibold), color = NC.ink2)
                SfImage(
                    "chevron.down",
                    size = 12.dp,
                    tint = NC.ink2,
                    modifier = Modifier.rotate(if (styleOpen) 180f else 0f),
                )
            }

            AnimatedVisibility(visible = styleOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowLayout(spacing = 6.dp, modifier = Modifier.semantics { contentDescription = "이모지" }) {
                        for (e in recordEmojis) {
                            val on = shownEmoji == e
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(Radius.xl))
                                    .background(if (on) NC.subtle else Color.Transparent, RoundedCornerShape(Radius.xl))
                                    .border(if (on) 1.5.dp else 1.dp, if (on) NC.borderFocus else NC.borderSubtle, RoundedCornerShape(Radius.xl))
                                    .clickable(role = Role.Button) { emoji = e }
                                    .semantics { selected = on },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(e, fontSize = 18.sp)
                            }
                        }
                    }
                    if (colors.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            RecordFormLabel(text = "색상 (선택 안 하면 자동)")
                            ColorPickerRow(colors = colors, selectedId = colorId, onSelectedIdChange = { colorId = it })
                        }
                    }
                }
            }
        }

        // MARK: 예시 — 만든 뒤 어떻게 쓰는지
        Text(
            buildAnnotatedString {
                append("전체 마감 없이 만들어요. 만든 뒤 ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("포트폴리오 → 이력서 제출 → 면접") }
                append("처럼 세부 목표마다 마감을 정하면 마감 순서대로 로드맵이 그려지고, 세부 목표마다 남은 할 일을 체크해요.")
            },
            style = pt(13).copy(lineHeight = 19.sp),
            color = NC.ink2,
            modifier = Modifier
                .fillMaxWidth()
                .background(NC.subtle, RoundedCornerShape(Radius.xl))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )

        // MARK: 버튼
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NButton("취소", variant = NButtonVariant.ghost, size = NButtonSize.sm, enabled = !saving) {
                track("goal_add.record_cancel", mapOf("has_input" to (value.isNotEmpty() || description.isNotEmpty())))
                onCancel()
            }
            NButton("업무형 목표 만들기", size = NButtonSize.sm, loading = saving, enabled = value.isNotEmpty()) {
                scope.launch { submit("button") }
            }
        }
    }
}

/** 입력칸 위 작은 회색 라벨 (웹 inline Field: text-ink-3 text-[12px] font-medium) */
@Composable
private fun RecordFormLabel(text: String) {
    Text(text, style = pt(12, PTWeight.medium), color = NC.ink3)
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun RecordGoalFormPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
            RecordGoalForm(onCancel = {})
        }
    }
}
