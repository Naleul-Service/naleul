// feature/notifications/NotificationChannels.kt
//
// (Android 전용) 알림 채널 · 알림 띄우기 · 권한 요청 도우미.
// iOS 는 UNUserNotificationCenter 하나로 다 되지만, Android 는
//  1) 알림 "채널"을 먼저 만들어야 알림이 떠요 (Android 8 이상) — 사용자가 설정에서 채널별로 끌 수 있어요
//  2) Android 13 이상은 POST_NOTIFICATIONS 권한을 받아야 알림이 떠요 (iOS requestAuthorization 과 같은 역할)
//  3) 알림을 누르면 열릴 화면(MainActivity)을 PendingIntent 로 직접 정해 줘야 해요
//
//   NotificationChannels.ensure(context)                 // 채널 만들기 (여러 번 불러도 괜찮아요)
//   NotificationChannels.post(context, entry, type)      // 알림 띄우기 (실천 도우미 · 서버 푸시 공통)
//   NotificationPermissionRequest()                      // (컴포저블) Android 13+ 알림 권한을 한 번 물어봐요

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

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.naleul.naleul.R
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

object NotificationChannels {
    /** 기본 채널 (실천 도우미 · 일정 알림 모두) */
    const val DEFAULT = "naleul_default"

    /** 알림을 눌러 앱이 열릴 때 Intent 에 담는 값의 이름 (NudgeCenter.handleIntent 가 읽어요) */
    const val EXTRA_ID = "nudge.id"
    const val EXTRA_KIND = "nudge.kind"
    const val EXTRA_TITLE = "nudge.title"
    const val EXTRA_BODY = "nudge.body"
    const val EXTRA_ROUTE = "nudge.route"
    const val EXTRA_DATE = "nudge.date"
    const val EXTRA_TYPE = "nudge.type"
    const val EXTRA_ROUTINE_KIND = "nudge.routine_kind"

    /** 채널 만들기 — 이미 있으면 아무 일도 안 해요 */
    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(DEFAULT) != null) return
        val ch = NotificationChannel(DEFAULT, "나를 알림", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "일정 알림과 실천 도우미 알림"
        }
        nm.createNotificationChannel(ch)
    }

    /** 알림을 띄울 수 있는지 (권한 + 앱 알림 켜짐) */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** 알림 한 건을 Intent extras 로 (누르면 MainActivity 가 이 값을 받아 NudgeCenter.handleIntent 로 넘겨요) */
    fun extrasOf(e: NudgeEntry, type: String? = null): Bundle = Bundle().apply {
        putString(EXTRA_ID, e.id)
        putString(EXTRA_KIND, e.kind.rawValue)
        putString(EXTRA_TITLE, e.title)
        putString(EXTRA_BODY, e.body)
        putString(EXTRA_ROUTE, e.route ?: "")
        putLong(EXTRA_DATE, e.date)
        putString(EXTRA_TYPE, type ?: e.kind.rawValue)
        putString(EXTRA_ROUTINE_KIND, e.routineKind ?: "")
    }

    /** 앱을 여는 Intent (com.naleul.naleul.MainActivity) */
    fun openAppIntent(context: Context, e: NudgeEntry, type: String? = null): Intent =
        Intent().apply {
            setClassName(context.packageName, "com.naleul.naleul.MainActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // 알림마다 다른 Intent 로 보이게 (extras 만 다르면 같은 PendingIntent 로 합쳐져요)
            action = "com.naleul.naleul.OPEN_NOTIFICATION.${e.id}"
            putExtras(extrasOf(e, type))
        }

    /** 알림 띄우기. 권한이 없으면 조용히 건너뛰어요 */
    fun post(context: Context, e: NudgeEntry, type: String? = null) {
        ensure(context)
        if (!canPost(context)) return
        val pi = PendingIntent.getActivity(
            context,
            e.id.hashCode(),
            openAppIntent(context, e, type),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, DEFAULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(e.title)
            .setContentText(e.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(e.body))
            .setAutoCancel(true)
            .setWhen(e.date)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            // 알림함으로 다시 모을 때(harvest) 같은 알림인지 알아보려고 값을 같이 넣어 둬요
            .addExtras(extrasOf(e, type))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(e.id, 0, n)
        } catch (e: SecurityException) {
            // 그사이 권한이 꺼졌으면 무시
        }
    }
}

/** Android 13+ 에서 알림 권한을 앱이 떠 있는 동안 한 번만 물어요 (iOS AppDelegate requestAuthorization) */
private var notificationPermissionAsked = false

/**
 * 알림 권한 요청 (컴포저블). 로그인 후 첫 화면(HomeScreen)에서 불러요.
 * 허락하면 바로 실천 도우미 알림을 다시 예약해요.
 */
@Composable
fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        AnalyticsService.track(
            AnalyticsEvent.pushPermissionResult,
            "result" to if (granted) "granted" else "denied", "prompt_timing" to "home_first",
        )
        if (granted) scope.launch { NudgeCenter.refresh(force = true) }
    }
    LaunchedEffect(Unit) {
        if (notificationPermissionAsked) return@LaunchedEffect
        notificationPermissionAsked = true
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            // ⚠ 두 번 거절하면 시스템이 창을 더 띄우지 않고 바로 denied 로 돌려줘요 (그때도 prompt_view 는 나가요)
            AnalyticsService.track(AnalyticsEvent.pushPermissionPromptView, "prompt_timing" to "home_first")
            runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
}
