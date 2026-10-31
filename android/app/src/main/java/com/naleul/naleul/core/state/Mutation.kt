// core/state/Mutation.kt
//
// iOS Core/State/Mutation.swift — 웹 useMutation({ mutationFn, onSuccess: toast.success, onError: toast.error, onSettled: invalidate }) 한 묶음.
//
//   // 성공하면 결과(R), 실패하면 null. 끝나면(성공·실패 모두) 관련 데이터를 다시 불러오게 표시해요.
//   val ok = Mutation.run(invalidate = listOf(DataKey.timetable), success = { "완료했어요." }) {
//       TimetableAPI.complete(taskId = t.taskId)
//   }
//   Mutation.run(invalidate = listOf(DataKey.goals), successMessage = "저장했어요.") { GoalAPI.update(...) }
//
// 실패 토스트 문구는 백엔드 message 를 그대로 써요 (없으면 errorFallback).
// suspend 함수라 코루틴 안에서 불러요: scope.launch { Mutation.run(...) { ... } }

package com.naleul.naleul.core.state

import com.naleul.naleul.core.network.ApiError
import com.naleul.naleul.core.network.errorMessage
import kotlinx.coroutines.CancellationException

object Mutation {
    /**
     * 마지막 요청이 실패했을 때의 HTTP 상태 (0 = 네트워크 등) — 성공하면 null.
     * run 이 null 을 돌려줘도 API 결과가 원래 null 일 수 있어서, 앰플리튜드 result 는 이 값으로 성공/실패를 가려요.
     */
    var lastErrorStatus: Int? = null
        private set

    suspend fun <R> run(
        invalidate: List<DataKey> = emptyList(),
        success: ((R) -> String?)? = null,
        errorFallback: String = "문제가 생겼어요. 다시 시도해 주세요.",
        showError: Boolean = true,
        operation: suspend () -> R,
    ): R? {
        lastErrorStatus = null
        try {
            val r = operation()
            val msg = success?.invoke(r)
            if (!msg.isNullOrEmpty()) Toast.success(msg)
            return r
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastErrorStatus = (e as? ApiError)?.httpStatus ?: 0
            if (showError) Toast.error(errorMessage(e, errorFallback))
            return null
        } finally {
            if (invalidate.isNotEmpty()) Invalidation.invalidate(invalidate)
        }
    }

    /** 성공 문구가 고정일 때 */
    suspend fun <R> run(
        invalidate: List<DataKey> = emptyList(),
        successMessage: String?,
        errorFallback: String = "문제가 생겼어요. 다시 시도해 주세요.",
        operation: suspend () -> R,
    ): R? = run(invalidate = invalidate, success = { successMessage }, errorFallback = errorFallback, showError = true, operation = operation)
}
