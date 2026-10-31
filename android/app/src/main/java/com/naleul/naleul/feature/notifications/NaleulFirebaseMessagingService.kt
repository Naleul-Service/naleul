// feature/notifications/NaleulFirebaseMessagingService.kt
//
// (Android 전용) Firebase 푸시(FCM) 받는 곳. iOS AppDelegate 의 MessagingDelegate + willPresent 를 옮겼어요.
// (Manifest 에 .feature.notifications.NaleulFirebaseMessagingService 로 등록돼 있어요)
//
//  - onNewToken: 새 FCM 토큰 → 서버에 등록 (DeviceTokenService)
//  - onMessageReceived: 앱을 보고 있을 때 온 푸시(또는 data 만 있는 푸시) → 알림을 직접 띄우고 알림함에 넣어요
//    ⚠️ 앱이 꺼져 있거나 뒤에 있을 때 온 "notification" 푸시는 이 함수가 불리지 않고 시스템이 바로 띄워요.
//       그 알림은 다음에 앱을 열 때 NudgeCenter.refresh() 가 알림 영역에서 모아 와요.

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

import android.os.Handler
import android.os.Looper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class NaleulFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        DeviceTokenService.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = message.notification?.title ?: data["title"] ?: "나를"
        val body = message.notification?.body ?: data["body"] ?: data["message"] ?: ""
        if (title.isEmpty() && body.isEmpty()) return
        val route = data["route"]
        val type = data["type"] ?: data["kind"] ?: ""
        val pushId = "push.${message.messageId ?: System.currentTimeMillis().toString()}"
        val sentAt = if (message.sentTime > 0) message.sentTime else System.currentTimeMillis()
        // 서버 루틴 실천 알림 — data 의 kind 는 YESTERDAY_TIME · RATE_RESCHEDULE (로컬 NudgeKind 가 아니에요)
        val entry = if (data["type"] == "ROUTINE_NUDGE") NudgeEntry.routine(pushId, title, body, sentAt) { data[it] } else NudgeEntry(
            id = pushId,
            kind = NudgeKind.fromRaw(data["kind"]) ?: NudgeKind.server,
            title = title.ifEmpty { "나를" },
            body = body,
            date = if (message.sentTime > 0) message.sentTime else System.currentTimeMillis(),
            route = if (route.isNullOrEmpty()) null else route,
            serverType = data["type"]?.ifEmpty { null },
        )
        NotificationChannels.post(applicationContext, entry, type = type)
        // push.receive — 앱을 보고 있을 때(또는 data 푸시) 받은 알림
        AnalyticsService.track(
            AnalyticsEvent.pushReceive,
            "notification_kind" to if (entry.kind != NudgeKind.server) entry.analyticsKind else type.ifEmpty { "server" },
            "routine_kind" to entry.analyticsRoutineKind,
        )
        Handler(Looper.getMainLooper()).post { NudgeCenter.record(entry) }
    }
}
