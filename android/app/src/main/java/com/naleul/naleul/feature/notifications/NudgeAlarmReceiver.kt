// feature/notifications/NudgeAlarmReceiver.kt
//
// (Android 전용) 예약한 "실천 도우미" 알림을 띄우는 곳.
// iOS 는 예약한 시각이 되면 시스템이 알아서 알림을 띄우지만, Android 는 AlarmManager 가 이 Receiver 를 깨우고
// 여기서 직접 알림을 만들어 띄워요. (Manifest 에 .feature.notifications.NudgeAlarmReceiver 로 등록돼 있어요)
//
// 울린 알림은 알림함에도 바로 넣어요 (iOS 는 앱을 다시 열 때 모으지만, 여기선 울리는 순간 넣어도 같은 결과예요).

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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class NudgeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NudgeCenter.ACTION_ALARM) return
        val ex = intent.extras ?: return
        val entry = NudgeCenter.entryFromBundle(ex) ?: return
        // 둘러보기 · 로그아웃 상태면 앞 사람 알림이 울리지 않게 건너뛰어요
        if (TokenManager.accessToken == null || TokenManager.isGuest) return
        NotificationChannels.post(context, entry)
        // 알림함 상태(Compose 가 읽는 값)는 메인 스레드에서 바꿔요
        Handler(Looper.getMainLooper()).post { NudgeCenter.record(entry) }
    }
}
