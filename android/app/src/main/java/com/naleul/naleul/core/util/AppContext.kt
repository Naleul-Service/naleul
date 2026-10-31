// core/util/AppContext.kt
//
// 화면 밖(토큰 저장소 · 알림 예약 등)에서 Context 가 필요할 때 쓰는 전역 접근점.
// NaleulApplication.onCreate() 에서 한 번 채워요.
// (Application context 는 앱이 살아 있는 동안 계속 유효해서 전역으로 들고 있어도 메모리 누수가 없어요)

package com.naleul.naleul.core.util

import android.app.Application

object AppContext {
    lateinit var app: Application
        private set

    fun init(application: Application) {
        app = application
    }
}
