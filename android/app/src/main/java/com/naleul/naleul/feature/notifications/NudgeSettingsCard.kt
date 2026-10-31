// feature/notifications/NudgeSettingsCard.kt
//
// iOS Features/Notifications/NudgeSettingsCard.swift
// 설정 > 알림 설정 맨 위 "실천 도우미 알림" — 묶음별로 켜고 꺼요 (이 기기에만 저장).
// 바꾸면 바로 다시 예약해요. 알림함으로 가는 줄도 여기 있어요.
//
//   NudgeSettingsCard(locked = UserStore.isGuest)   // 둘러보기 중이면 잠가요 (누르면 로그인 안내)

package com.naleul.naleul.feature.notifications

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun NudgeSettingsCard(locked: Boolean, modifier: Modifier = Modifier) {
    NudgeSettingsCardContent(
        locked = locked,
        unreadCount = NudgeCenter.unreadCount,
        next = NudgeCenter.planned.firstOrNull(),
        onToggle = { g, v -> NudgeCenter.setEnabled(g, v) },
        modifier = modifier,
    )
}

/** 카드 내용 (NudgeCenter 와 떼어 놔서 미리보기에서 샘플로 그려요 — iOS init(locked:center:)) */
@Composable
private fun NudgeSettingsCardContent(
    locked: Boolean,
    unreadCount: Int,
    next: NudgeEntry?,
    onToggle: (NudgeGroup, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 켜진 묶음 (iOS @State on: Set<NudgeGroup>)
    var on by remember { mutableStateOf(NudgeGroup.entries.filter { it.isEnabled }.toSet()) }

    Column(modifier.fillMaxWidth().card()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("실천 도우미 알림", style = pt(16, PTWeight.bold), color = NC.ink)
            Text(
                "계획만 세우고 끝나지 않게, 필요한 순간에 한마디씩 걸어요. 이 기기에서만 울려요.",
                style = pt(13), color = NC.ink3,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(NC.line))

        NudgeGroup.entries.forEachIndexed { i, g ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(NC.line))
            NudgeSettingsRow(
                group = g,
                locked = locked,
                isOn = on.contains(g),
                onChange = { v ->
                    on = if (v) on + g else on - g
                    // 권한이 꺼져 있는데 켜면 예약만 되고 울리지 않아요 → push_authorized 로 같이 봐요
                    AnalyticsService.track(
                        NotiEvent.nudgeToggle,
                        "nudge_group" to g.rawValue, "is_on" to v,
                        "enabled_group_count" to on.size, "push_authorized" to NudgeCenter.isAuthorized,
                    )
                    onToggle(g, v)
                },
            )
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(NC.line))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    AnalyticsService.track(NotiEvent.nudgeInboxClick, "unread_count" to unreadCount, "planned_count" to NudgeCenter.planned.size)
                    InboxTracker.pendingEntry = "settings"
                    AppRouter.openNotifications()
                }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SfImage("tray.full", size = 14.dp, tint = NC.ink)
            Text("알림함 보기", style = pt(15, PTWeight.semibold), color = NC.ink)
            if (unreadCount > 0) {
                Box(
                    Modifier
                        .defaultMinSize(minHeight = 18.dp)
                        .background(NC.actionDanger, CircleShape)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$unreadCount", style = pt(11, PTWeight.bold), color = Color.White)
                }
            }
            Spacer(Modifier.weight(1f))
            if (next != null) {
                Text("다음 · ${NudgeFormat.`when`(next.date)}", style = pt(12), color = NC.ink3)
            }
            SfImage("chevron.right", size = 12.dp, tint = NC.ink4)
        }
    }
}

@Composable
private fun NudgeSettingsRow(group: NudgeGroup, locked: Boolean, isOn: Boolean, onChange: (Boolean) -> Unit) {
    val g = group
    Row(
        Modifier
            .fillMaxWidth()
            // 둘러보기 중이면 줄을 눌러 로그인 안내를 띄워요
            .then(
                if (locked) Modifier.clickable {
                    AnalyticsService.track(NotiEvent.nudgeLockedClick, "nudge_group" to g.rawValue)
                    LoginPrompt.show("notification_settings")
                } else Modifier
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(g.title, style = pt(15, PTWeight.semibold), color = if (locked) NC.ink4 else NC.ink)
            Text(g.description, style = pt(13), color = if (locked) NC.ink4 else NC.ink3)
        }
        if (locked) {
            SfImage("lock", size = 15.dp, tint = NC.ink4)
        } else {
            NSwitch(isOn = isOn, onIsOnChange = onChange, label = g.title)
        }
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun NudgeSettingsCardPreview() {
    NaleulTheme(dark = false) {
        Column(
            Modifier.canvasBackground().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NudgeSettingsCardContent(
                locked = false,
                unreadCount = NudgePreviewData.entries.count { !it.read },
                next = NudgePreviewData.planned.firstOrNull(),
                onToggle = { _, _ -> },
            )
            NudgeSettingsCardContent(locked = true, unreadCount = 0, next = null, onToggle = { _, _ -> })
        }
    }
}
