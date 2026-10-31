// feature/work/WorkShareSheet.kt
//
// iOS Features/Work/WorkShareSheet.swift
// 웹 features/work/ui/WorkShareModal.tsx
// 공유하기 — 서버가 정리해 준 텍스트를 카톡 · 슬랙에 붙여 넣기 좋게 보여주고 복사 · 공유해요.
// 열 때마다 · 범위를 바꿀 때마다 새로 받아요 (보드가 바뀌었을 수 있어서).
//
// iOS ShareLink → Android 공유 창(Intent.ACTION_SEND + createChooser)
// iOS UIPasteboard → LocalClipboardManager

package com.naleul.naleul.feature.work

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

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException

@Composable
fun WorkShareSheet(
    goalId: Int,
    /** 미리보기용 글 (주면 네트워크 안 씀) */
    previewText: String? = null,
    onDismiss: () -> Unit,
) {
    var scope by remember { mutableStateOf(WorkShareScope.REMAINING) }
    var text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf<String?>(null) }
    /** 닫기 전에 복사 · 공유를 했는지 (work_share.close 의 copied) */
    var copied by remember { mutableStateOf(false) }
    val latestCopied by rememberUpdatedState(copied)

    fun track(event: String, props: EventProps = emptyMap()) {
        if (previewText != null) return
        WK.track(event, goalId, props)
    }

    // 취소 버튼이 없어서 X 아니면 아래로 쓸어 닫기 · 뒤로 가기 (swipe_down)
    DisposableEffect(Unit) {
        onDispose {
            val m = GD.closeMethod(null) ?: return@onDispose
            track("work_share.close", mapOf("close_method" to m, "copied" to latestCopied))
        }
    }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    val scopeOptions: List<Pair<WorkShareScope, String>> = WorkShareScope.entries.map { it to it.label }

    fun hint(s: WorkShareScope): String = when (s) {
        WorkShareScope.TODAY -> "오늘 할 일 + 오늘 끝낸 일"
        WorkShareScope.REMAINING -> "진행 중 + 시작 전 (세부 목표별)"
        WorkShareScope.ALL -> "남은 일 + 최근 7일 완료"
    }

    // 열 때 · 범위를 바꿀 때마다 새로 받아요 (iOS .task(id: scope))
    LaunchedEffect(scope) {
        if (previewText != null) {
            text = previewText
            loading = false
            return@LaunchedEffect
        }
        loading = true
        failed = null
        try {
            text = WorkAPI.shareText(goalId = goalId, scope = scope)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            text = ""
            failed = errorMessage(e)
            track("work_share.text_load_fail", mapOf("share_scope" to scope.name, "error_status" to ((e as? ApiError)?.httpStatus ?: 0)))
        }
        loading = false
    }

    fun copy() {
        track("work_share.copy_click", mapOf("share_scope" to scope.name, "text_length" to text.length))
        copied = true
        clipboard.setText(AnnotatedString(text))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        Toast.success("복사했어요. 원하는 곳에 붙여 넣으세요.")
    }

    fun share() {
        // 시스템 공유 창은 실제로 보냈는지 알려주지 않아요 (그래서 share_result 는 없어요)
        track("work_share.native_share_click", mapOf("share_scope" to scope.name, "text_length" to text.length))
        copied = true
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, "Task 공유하기")
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    SheetScaffold(
        onDismiss = onDismiss,
        title = "Task 공유하기",
        description = "팀 채팅방에 붙여 넣기 좋게 정리했어요.",
        fullHeight = true,
        footer = {
            NButton("공유", variant = NButtonVariant.secondary, icon = "square.and.arrow.up", enabled = text.isNotEmpty()) { share() }
            NButton("복사", icon = "doc.on.doc", enabled = text.isNotEmpty()) { copy() }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SegmentPicker(
                selection = scope,
                onSelectionChange = { s ->
                    if (s != scope) {
                        scope = s
                        track("work_share.scope_select", mapOf("share_scope" to s.name))
                    }
                },
                options = scopeOptions,
                fullWidth = true,
            )
            Text(hint(scope), style = pt(12), color = NC.ink3)

            // 글 상자
            val shape = RoundedCornerShape(Radius.xxl)
            val msg = failed
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 160.dp)
                    .alpha(if (loading && text.isNotEmpty()) 0.5f else 1f)
                    .background(NC.bgSunken, shape)
                    .border(1.dp, NC.borderSubtle, shape)
                    .padding(16.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                when {
                    loading && text.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 128.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NC.ink3, strokeWidth = 2.5.dp)
                        }
                    }
                    msg != null -> Text(msg, style = pt(14), color = NC.danger)
                    text.isEmpty() -> Text("공유할 Task가 없어요", style = pt(14), color = NC.ink3)
                    else -> {
                        SelectionContainer {
                            Text(
                                text,
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 21.sp),
                                color = NC.ink,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun WorkShareSheetPreview() {
    NaleulTheme(dark = false) {
        WorkShareSheet(goalId = 2, previewText = WorkSampleData.shareText, onDismiss = {})
    }
}
