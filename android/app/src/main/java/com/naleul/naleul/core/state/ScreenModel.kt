// core/state/ScreenModel.kt
//
// iOS 의 @Observable final class XViewModel 을 옮길 때 쓰는 바탕 클래스.
//
//   class GoalDetailViewModel(val goalId: Int) : ScreenModel() {
//       var phase by mutableStateOf<LoadPhase<GoalCategory>>(LoadPhase.Loading)   // iOS var phase: LoadPhase<…>
//       suspend fun load() { … }                                                  // iOS func load() async
//       fun refreshLater() = scope.launch { load() }                              // iOS Task { await load() }
//   }
//
//   @Composable fun GoalDetailScreen(goalId: Int, justCreated: Boolean = false) {
//       val vm = rememberScreenModel(goalId) { GoalDetailViewModel(goalId) }      // iOS @State private var vm = …
//       ReloadOn(listOf(DataKey.goals), id = goalId) { vm.load() }
//       …
//   }
//
// 왜 androidx ViewModel 이 아닌가요?
//  이 앱은 화면 이동 라이브러리 없이 AppRouter 로 화면을 바꿔서, androidx ViewModel 을 쓰면 화면을 떠나도 정리되지 않아요.
//  SwiftUI @State 처럼 "화면이 떠 있는 동안만 살아 있는" 객체가 필요해서 remember 로 들고,
//  화면이 사라지면 scope(코루틴)를 취소해서 하던 일을 멈춰요.
//  (화면 회전 · 다크 모드 전환 때도 화면이 다시 만들어지지 않게 Manifest 에 configChanges 를 넣어 뒀어요)

package com.naleul.naleul.core.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

abstract class ScreenModel {
    /** 이 화면이 살아 있는 동안 쓰는 코루틴 범위 (iOS Task { } 대신 scope.launch { }) */
    // iOS Task { } 는 잡지 않은 에러를 그냥 버려요. Kotlin 은 앱이 죽어서, 혹시 놓친 에러는 로그만 남겨요
    val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e -> android.util.Log.e("ScreenModel", "잡지 않은 에러", e) }
    )

    /** 화면이 사라질 때 불려요. 더 정리할 게 있으면 override 하세요 (super.onDispose() 꼭 호출) */
    open fun onDispose() {
        scope.cancel()
    }
}

/** keys 가 바뀌면 새로 만들어요 (예: 다른 목표로 이동). 화면이 사라지면 onDispose() 가 불려요 */
@Composable
fun <T : ScreenModel> rememberScreenModel(vararg keys: Any?, factory: () -> T): T {
    val model = remember(*keys) { factory() }
    DisposableEffect(model) {
        onDispose { model.onDispose() }
    }
    return model
}
