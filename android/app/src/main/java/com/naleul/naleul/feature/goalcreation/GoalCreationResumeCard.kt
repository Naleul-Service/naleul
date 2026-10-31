// feature/goalcreation/GoalCreationResumeCard.kt
//
// iOS Features/GoalCreation/GoalCreationResumeCard.swift
// 만들던 목표가 있으면 "이어서 만들기" 카드 (없으면 아무것도 안 그려요).
// 웹 원본: features/goal-creation/ui/ResumeCard.tsx
//
// 목표 목록 등 플로우 밖에서는 GoalCreationResumeCard() → AppRouter.resumeGoalCreation(sessionId) 로 전체 화면을 띄워요.
// 플로우 안(시작 화면)에서는 onOpen 으로 받아서 같은 화면 안에서 단계만 바꿔요.

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

/** 웹 STEP_LABEL */
private fun goalCreationResumeStepLabel(s: GoalCreationSessionStatus): String = when (s) {
    GoalCreationSessionStatus.INTERVIEWING -> "AI와 대화하는 중"
    GoalCreationSessionStatus.READY_TO_GENERATE -> "정리 확인 단계"
    GoalCreationSessionStatus.GENERATING -> "계획을 만드는 중"
    GoalCreationSessionStatus.DRAFT_READY -> "초안이 준비됐어요"
    else -> ""
}

/**
 * @param onOpen 목표 만들기 플로우 안에서 쓸 때. null 이면 AppRouter.resumeGoalCreation 을 불러요
 */
@Composable
fun GoalCreationResumeCard(
    onOpen: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** 이어갈 세션이 있는지 다 불러왔을 때 (목표 만들기 시작 화면 pageview 의 has_resume) */
    onLoaded: ((Boolean) -> Unit)? = null,
) {
    var session by remember { mutableStateOf<GoalCreationSessionDetail?>(null) }
    ReloadOn(listOf(DataKey.goalCreation)) {
        // 실패하면 카드만 안 보여요 (웹 useQuery 실패 시 data 없음)
        try {
            session = GoalCreationAPI.getActive()
        } catch (e: CancellationException) {
            // 다시 불러오는 중에 취소됨 — 지금 값을 그대로 둬요
            throw e
        } catch (e: Exception) {
            session = null
        }
        onLoaded?.invoke(session?.let { GoalCreationRoute.forStatus(it.sessionId, it.status) != null } ?: false)
    }
    GoalCreationResumeCardContent(session = session, modifier = modifier) { id ->
        if (onOpen != null) {
            // 목표 만들기 시작 화면 안에서만 (목표 목록의 같은 카드는 목표 목록 분류에서)
            GCTracker.track(GCEvent.resumeClick, "session_id" to id, "resume_status" to session?.status?.name)
            onOpen(id)
        } else {
            // 목표 목록의 같은 카드
            GD.track("goal_list.resume_click", mapOf("session_id" to id, "resume_status" to session?.status?.name))
            AppRouter.resumeGoalCreation(sessionId = id)
        }
    }
}

/** 미리보기용 (네트워크 없이) */
@Composable
fun GoalCreationResumeCard(preview: GoalCreationSessionDetail, modifier: Modifier = Modifier) {
    GoalCreationResumeCardContent(session = preview, modifier = modifier) { }
}

@Composable
private fun GoalCreationResumeCardContent(
    session: GoalCreationSessionDetail?,
    modifier: Modifier,
    onClick: (Int) -> Unit,
) {
    val s = session
    // 이어갈 단계가 없으면 높이 0 — 목록 사이에 빈 칸을 남기지 않아요
    if (s == null || GoalCreationRoute.forStatus(s.sessionId, s.status) == null) return
    val shape = RoundedCornerShape(Radius.xxl)
    Row(
        modifier
            .fillMaxWidth()
            .aiBorder(shape, glass = false)
            .clickable(role = Role.Button) { onClick(s.sessionId) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AIBadge()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("만들던 목표가 있어요 · ${goalCreationResumeStepLabel(s.status)}", style = pt(13), color = NC.ink3)
            Text(
                s.slots.goalStatement.value ?: "이름 없는 목표",
                style = pt(15, PTWeight.bold),
                color = NC.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("이어서 만들기", style = pt(14, PTWeight.semibold), color = NC.ink, maxLines = 1)
            SfImage("arrow.right", size = 13.dp, tint = NC.ink)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GoalCreationResumeCardPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.background(NC.canvas).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GoalCreationResumeCard(preview = GoalCreationSamples.session)
        }
    }
}
