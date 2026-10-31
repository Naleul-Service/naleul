// Core/State/Mutation.swift
//
// 웹 useMutation({ mutationFn, onSuccess: toast.success, onError: toast.error, onSettled: invalidate }) 한 묶음.
//
//   // 성공하면 결과(R), 실패하면 nil. 끝나면(성공·실패 모두) 관련 데이터를 다시 불러오게 표시해요.
//   let ok = await Mutation.run(invalidate: [.timetable], success: { _ in "완료했어요." }) {
//       try await TimetableAPI.complete(taskId: t.taskId)
//   }
//
// 실패 토스트 문구는 백엔드 message 를 그대로 써요 (없으면 fallback).

import Foundation

@MainActor
enum Mutation {
    /// 마지막으로 실패한 요청의 HTTP 상태 (0 = 네트워크 · 알 수 없음). 화면이 *_result 이벤트에 error_status 로 붙여요
    static var lastErrorStatus: Int?

    @discardableResult
    static func run<R>(
        invalidate keys: [DataKey] = [],
        success: ((R) -> String?)? = nil,
        errorFallback: String = "문제가 생겼어요. 다시 시도해 주세요.",
        showError: Bool = true,
        _ operation: () async throws -> R
    ) async -> R? {
        defer { if !keys.isEmpty { Invalidation.shared.invalidate(keys) } }
        lastErrorStatus = nil
        do {
            let r = try await operation()
            if let msg = success?(r), !msg.isEmpty { Toast.success(msg) }
            return r
        } catch is CancellationError {
            return nil
        } catch {
            lastErrorStatus = (error as? APIError)?.httpStatus ?? 0
            if showError { Toast.error(errorMessage(error, fallback: errorFallback)) }
            return nil
        }
    }

    /// 성공 문구가 고정일 때
    @discardableResult
    static func run<R>(
        invalidate keys: [DataKey] = [],
        successMessage: String?,
        errorFallback: String = "문제가 생겼어요. 다시 시도해 주세요.",
        _ operation: () async throws -> R
    ) async -> R? {
        await run(invalidate: keys, success: { _ in successMessage }, errorFallback: errorFallback, operation)
    }
}
