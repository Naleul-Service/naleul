// feature/goalcreation/InterviewChatView.kt
//
// iOS Features/GoalCreation/InterviewChatView.swift
// G-1 목표 대화 (/goal/new/{sessionId}).
// 웹 원본: features/goal-creation/ui/SessionScreen.tsx · InterviewChatView.tsx

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 세션을 불러와서 대화 화면을 그려요 (웹 SessionScreen) */
@Composable
fun InterviewChatScreen(sessionId: Int) {
    /** 409 등으로 서버와 어긋나면 이 값을 올려 대화 화면을 새로 그려요 */
    var syncKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    GoalCreationSessionGate(sessionId = sessionId, allow = listOf(GoalCreationSessionStatus.INTERVIEWING)) { session, refetch ->
        key("$sessionId-$syncKey") {
            InterviewChatView(initial = session, isResume = syncKey > 0, onResync = {
                scope.launch {
                    refetch()
                    syncKey += 1
                }
            })
        }
    }
}

@Composable
fun InterviewChatView(initial: GoalCreationSessionDetail, onResync: () -> Unit, isResume: Boolean = false) {
    val nav = LocalGoalCreationNavigator.current
    val chat = rememberScreenModel { InterviewChatModel(initial = initial, onResync = onResync) }
    var leaveOpen by remember { mutableStateOf(false) }
    val composerFocus = remember { FocusRequester() }
    var composerFocused by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 다시 그려질 때 최신 콜백을 쓰도록
    chat.onResync = onResync
    chat.onNotFound = { nav.go(GoalCreationRoute.start) }

    fun onClose() {
        GCTracker.track(
            GCEvent.chatClose,
            "session_id" to chat.sessionId, "has_confirm" to chat.interviewing, "question_count" to chat.meta.questionCount,
        )
        if (chat.interviewing) leaveOpen = true else nav.close()
    }

    // pageview (409 로 다시 그리면 is_resume) · 앱이 뒤로 갈 때 붙일 질문 수
    LaunchedEffect(Unit) {
        GCTracker.track(
            GCEvent.chatPageview,
            "session_id" to chat.sessionId, "question_count" to chat.meta.questionCount,
            "max_questions" to chat.meta.maxQuestions, "is_resume" to isResume,
        )
    }
    LaunchedEffect(chat.meta.questionCount) { GCTracker.questionCount = chat.meta.questionCount }

    // MARK: 대화 목록에 들어갈 줄 수 (맨 아래 줄 index 계산용)
    val q = chat.currentQuestion
    val showQuick = chat.interviewing && q != null && q.quickReplies.isNotEmpty() && !chat.pending
    val rejected = chat.rejectedMessage
    val bottomIndex = chat.items.size + (if (showQuick) 1 else 0) + (if (chat.pending) 1 else 0) + (if (rejected != null) 1 else 0)

    /** 새 말풍선 · 입력 중 표시 · 상태가 바뀌면 맨 아래로 스크롤 */
    val scrollKey = "${chat.items.size}-${chat.pending}-${chat.meta.status.rawValue}-${rejected ?: ""}"
    var firstScroll by remember { mutableStateOf(true) }
    LaunchedEffect(scrollKey) {
        if (firstScroll) {
            firstScroll = false
            listState.scrollToItem(bottomIndex)
        } else {
            listState.animateScrollToItem(bottomIndex)
        }
    }
    // 키보드가 올라온 뒤에 마지막 말풍선이 보이게
    LaunchedEffect(composerFocused) {
        if (!composerFocused) return@LaunchedEffect
        delay(300)
        listState.animateScrollToItem(bottomIndex)
    }
    // 처음 들어오면 바로 입력할 수 있게
    LaunchedEffect(Unit) {
        if (chat.interviewing) {
            delay(100)
            try {
                composerFocus.requestFocus()
            } catch (e: IllegalStateException) {
                // 입력창이 아직 화면에 없으면 무시
            }
        }
    }
    // 인터뷰가 끝나면 마지막 말풍선을 잠깐 보여준 뒤 정리 확인 화면(G-2)으로 이동
    LaunchedEffect(chat.done) {
        if (!chat.done) return@LaunchedEffect
        delay(1_200)
        nav.go(GoalCreationRoute.review(chat.sessionId))
    }

    Column(Modifier.fillMaxSize().background(NC.surface)) {
        GoalCreationHeader(
            onClose = { onClose() },
            center = {
                if (chat.interviewing) InterviewProgress(current = chat.meta.questionCount, max = chat.meta.maxQuestions)
            },
            action = {
                if (chat.interviewing && chat.meta.canFinishEarly) {
                    Row(
                        Modifier.nButton(NButtonVariant.ai, NButtonSize.sm, enabled = chat.canInteract) {
                            GCTracker.track(
                                GCEvent.finishEarly,
                                "session_id" to chat.sessionId, "question_count" to chat.meta.questionCount,
                                "max_questions" to chat.meta.maxQuestions,
                            )
                            chat.scope.launch { chat.finishEarly() }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SfImage("sparkles", size = 14.dp, tint = nButtonContentColor(NButtonVariant.ai, chat.canInteract))
                        Text("바로 만들기", style = NType.bodySMMedium, color = nButtonContentColor(NButtonVariant.ai, chat.canInteract))
                    }
                }
            },
        )

        // MARK: 대화 영역
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(chat.items, key = { it.key }) { item ->
                if (item.role == GoalCreationMessageRole.ASSISTANT) {
                    InterviewAssistantBubble(content = item.content)
                } else {
                    InterviewUserBubble(
                        item = item,
                        onRetry = { k ->
                            GCTracker.track(GCEvent.chatRetry, "session_id" to chat.sessionId)
                            chat.scope.launch { chat.retry(k) }
                        },
                        onDiscard = { k ->
                            GCTracker.track(GCEvent.chatDiscard, "session_id" to chat.sessionId)
                            chat.discardFailed(k)
                        },
                    )
                }
            }

            // 마지막 질문의 빠른 답변
            if (showQuick && q != null) {
                item(key = "quick-replies") {
                    FlowLayout(
                        spacing = 8.dp,
                        modifier = Modifier
                            .padding(start = 42.dp)
                            .offset(y = (-4).dp)
                            .alpha(if (chat.canInteract) 1f else 0.4f),
                    ) {
                        q.quickReplies.forEachIndexed { i, reply ->
                            QuickReply(reply) {
                                if (chat.canInteract) {
                                    GCTracker.track(
                                        GCEvent.quickReply,
                                        "session_id" to chat.sessionId, "question_index" to chat.meta.questionCount, "reply_index" to i,
                                    )
                                    chat.scope.launch { chat.sendQuickReply(reply, i) }
                                }
                            }
                        }
                    }
                }
            }

            if (chat.pending) item(key = "typing") { InterviewTypingIndicator() }

            if (rejected != null) {
                item(key = "rejected") {
                    InterviewRejectedCard(message = rejected) {
                        GCTracker.track(GCEvent.restart, "session_id" to chat.sessionId)
                        nav.go(GoalCreationRoute.start)
                    }
                }
            }

            item(key = "bottom") { Spacer(Modifier.height(1.dp)) }
        }

        HorizontalDivider(color = NC.borderSoft)

        // MARK: 하단: 입력창 또는 인터뷰 완료 패널
        Box(
            Modifier
                .fillMaxWidth()
                .background(NC.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (chat.interviewing) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (chat.currentQuestion?.skippable == true) {
                        Text(
                            "건너뛰기",
                            style = pt(13, PTWeight.medium),
                            color = NC.ink3,
                            modifier = Modifier
                                .alpha(if (chat.canInteract) 1f else 0.4f)
                                .clickable(enabled = chat.canInteract, role = Role.Button) {
                                    GCTracker.track(GCEvent.skip, "session_id" to chat.sessionId, "question_index" to chat.meta.questionCount, "extra_note" to (chat.currentQuestion?.targetSlot == "extraNote"))
                                    chat.scope.launch { chat.skipQuestion() }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    InterviewChatComposer(
                        value = chat.input,
                        onValueChange = { chat.input = it },
                        disabled = !chat.canInteract,
                        focus = composerFocus,
                        onFocusChange = { composerFocused = it },
                    ) {
                        GCTracker.track(
                            GCEvent.answerSend,
                            "session_id" to chat.sessionId, "question_index" to chat.meta.questionCount,
                            "answer_length" to GoalCreationForm.count(chat.input.trim()),
                            "extra_note" to (chat.currentQuestion?.targetSlot == "extraNote"),
                        )
                        chat.scope.launch { chat.sendText() }
                    }
                }
            } else if (chat.done) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SfImage("checkmark.circle.fill", size = 18.dp, tint = NC.success)
                        Text("필요한 정보를 모두 모았어요", style = pt(15, PTWeight.bold), color = NC.ink)
                    }
                    NButton("정리 확인하기", variant = NButtonVariant.brand) {
                        GCTracker.track(GCEvent.reviewClick, "session_id" to chat.sessionId)
                        nav.go(GoalCreationRoute.review(chat.sessionId))
                    }
                }
            } else if (chat.rejectedMessage == null) {
                Text(
                    "대화가 끝났어요.",
                    style = pt(14),
                    color = NC.ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
        }
    }

    ConfirmDialog(
        isPresented = leaveOpen,
        onDismiss = { leaveOpen = false },
        title = "대화를 잠시 멈출까요?",
        message = GoalCreationConst.leaveNotice,
        confirmLabel = "나가기",
        cancelLabel = "계속 만들기",
        onCancel = { GCTracker.track(GCEvent.leaveCancel, "session_id" to chat.sessionId, "question_count" to chat.meta.questionCount) },
    ) {
        GCTracker.track(GCEvent.leaveConfirm, "session_id" to chat.sessionId, "question_count" to chat.meta.questionCount)
        nav.close()
    }
}

@Composable
private fun InterviewRejectedCard(message: String, onRestart: () -> Unit) {
    val shape = RoundedCornerShape(Radius.xxl)
    Column(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .background(NC.bgSunken, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(message, style = pt(15).copy(lineHeight = 22.sp), color = NC.ink)
        NButton("다른 목표로 시작하기") { onRestart() }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun InterviewChatViewPreview() {
    NaleulTheme(dark = false) {
        CompositionLocalProvider(LocalGoalCreationNavigator provides remember { GoalCreationNavigator(GoalCreationRoute.chat(101)) }) {
            InterviewChatView(initial = GoalCreationSamples.session, onResync = {})
        }
    }
}
