// Core/Network/APIClient.swift
//
// 웹(lib/client/api.ts)의 api.get/post/put/patch/delete 를 그대로 옮긴 네트워크 레이어.
//
// 사용 예 (웹 경로를 그대로 써요 — 앞의 "/api" 는 여기서 붙여요):
//   let goals: [GoalCategory] = try await API.get("/v1/goal-categories") ?? []
//   let turn: TurnResponse? = try await API.post("/v1/goal-creation/sessions", body: ["initialMessage": .string(text)])
//   try await API.send("PATCH", "/v1/tasks/\(id)/complete")      // 응답 data 를 안 쓰는 요청
//
// 동작 규칙 (웹과 동일)
//  - 백엔드 공통 응답 { success, status, message, data } 에서 data 만 꺼내 돌려줘요.
//  - 204(No Content) 는 nil.
//  - 실패(HTTP 4xx/5xx 또는 success=false)는 APIError.http(...) 로 던져요. message 는 백엔드 문구 그대로.
//  - 401 은 토큰 재발급을 한 번 시도하고, 그래도 실패하면 로그아웃시켜요.

import Foundation

// MARK: - 에러

enum APIError: LocalizedError {
    /// 백엔드가 실패로 응답 (httpStatus: 409, 422, 429 …)
    case http(status: Int, message: String, statusName: String?, data: JSONValue?)
    /// 인터넷 연결 문제
    case network
    /// 응답을 Swift 모델로 바꾸지 못함 (모델 정의가 백엔드와 다를 때)
    case decoding(String)
    case invalidURL
    case unauthorized

    /// 둘러보기 중에 저장하려고 함 → 로그인 안내 시트가 떠요
    static let guestStatus = "GUEST_LOGIN_REQUIRED"
    static let guestMessage = "로그인하면 저장할 수 있어요."
    static var guestLoginRequired: APIError { .http(status: 401, message: guestMessage, statusName: guestStatus, data: nil) }

    // 예전 코드 호환용 (로그인/결제 등)
    static func unknown(_ code: Int) -> APIError { .http(status: code, message: "오류가 발생했습니다 (\(code))", statusName: nil, data: nil) }
    static func businessError(message: String) -> APIError { .http(status: 400, message: message, statusName: nil, data: nil) }

    var isGuestLoginRequired: Bool {
        if case .http(_, _, let name, _) = self { return name == APIError.guestStatus }
        return false
    }

    var errorDescription: String? {
        switch self {
        case .http(_, let message, _, _): return message
        case .network: return "네트워크 연결을 확인해 주세요."
        case .decoding: return "데이터를 읽지 못했어요. 앱을 최신 버전으로 업데이트해 주세요."
        case .invalidURL: return "잘못된 주소예요."
        case .unauthorized: return "다시 로그인해 주세요."
        }
    }

    /// 숫자 HTTP 상태코드 (네트워크 오류는 0)
    var httpStatus: Int {
        switch self {
        case .http(let status, _, _, _): return status
        case .unauthorized: return 401
        default: return 0
        }
    }

    /// 실패 응답에 담긴 data (예: 422 의 { violations })
    var data: JSONValue? {
        if case .http(_, _, _, let data) = self { return data }
        return nil
    }

    /// 422 확정 검증 실패일 때 violations 꺼내기
    var violations: [Violation] {
        guard let arr = data?["violations"]?.arrayValue else { return [] }
        return arr.compactMap { v in
            guard let ruleId = v["ruleId"]?.stringValue,
                  let path = v["path"]?.stringValue,
                  let message = v["message"]?.stringValue else { return nil }
            return Violation(ruleId: ruleId, path: path, message: message)
        }
    }
}

/// 확정 API 422 응답의 data 구조
struct Violation: Hashable {
    let ruleId: String
    let path: String
    let message: String
}

/// 어떤 에러든 화면에 보여줄 문구로 (웹의 isApiError(e) ? e.message : fallback)
func errorMessage(_ error: Error, fallback: String = "문제가 생겼어요. 다시 시도해 주세요.") -> String {
    if let e = error as? APIError { return e.errorDescription ?? fallback }
    return fallback
}

// MARK: - API

enum API {
    /// 응답 data 를 T 로 받아요. data 가 없거나 204 면 nil
    static func get<T: Decodable>(_ path: String) async throws -> T? {
        try await request("GET", path, body: nil)
    }

    static func post<T: Decodable>(_ path: String, body: JSONValue? = nil) async throws -> T? {
        try await request("POST", path, body: body ?? .object([:]))
    }

    static func put<T: Decodable>(_ path: String, body: JSONValue? = nil) async throws -> T? {
        try await request("PUT", path, body: body ?? .object([:]))
    }

    static func patch<T: Decodable>(_ path: String, body: JSONValue? = nil) async throws -> T? {
        try await request("PATCH", path, body: body ?? .object([:]))
    }

    /// body 는 선택 — 회원 탈퇴(DELETE /v1/users)처럼 사유를 같이 보내야 할 때만 넘겨요
    static func delete<T: Decodable>(_ path: String, body: JSONValue? = nil) async throws -> T? {
        try await request("DELETE", path, body: body)
    }

    /// Encodable 구조체를 body 로 보낼 때
    static func request<T: Decodable, B: Encodable>(_ method: String, _ path: String, encodable body: B) async throws -> T? {
        let data = try JSONEncoder().encode(body)
        return try await raw(method, path, bodyData: data)
    }

    /// 응답 data 를 쓰지 않는 요청 (완료 처리, 삭제 등)
    static func send(_ method: String, _ path: String, body: JSONValue? = nil) async throws {
        let defaultBody: JSONValue? = (method == "GET" || method == "DELETE") ? nil : .object([:])
        let _: JSONValue? = try await request(method, path, body: body ?? defaultBody)
    }

    static func request<T: Decodable>(_ method: String, _ path: String, body: JSONValue?) async throws -> T? {
        let data = try body.map { try JSONEncoder().encode($0) }
        return try await raw(method, path, bodyData: data)
    }

    // MARK: 내부 구현

    private static func makeRequest(_ method: String, _ path: String, bodyData: Data?) throws -> URLRequest {
        guard let url = URL(string: Config.baseURL + "/api" + path) else { throw APIError.invalidURL }
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.cachePolicy = .reloadIgnoringLocalCacheData
        req.timeoutInterval = 60
        if let bodyData {
            req.httpBody = bodyData
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        if let token = TokenManager.shared.accessToken {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return req
    }

    private static func perform(_ req: URLRequest) async throws -> (Data, Int) {
        do {
            let (data, response) = try await URLSession.shared.data(for: req)
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            return (data, status)
        } catch is CancellationError {
            throw CancellationError()
        } catch let e as URLError where e.code == .cancelled {
            throw CancellationError()
        } catch {
            throw APIError.network
        }
    }

    /// 실패하면 api.error 를 남기고 그대로 다시 던져요 (둘러보기 막힘 · 취소 · 세션 만료는 따로 세요)
    private static func raw<T: Decodable>(_ method: String, _ path: String, bodyData: Data?) async throws -> T? {
        do {
            return try await rawRequest(method, path, bodyData: bodyData)
        } catch let e as APIError {
            if !e.isGuestLoginRequired, e.httpStatus != 401 {
                await MainActor.run { Self.reportError(e, path: path) }
            }
            throw e
        }
    }

    @MainActor
    private static func reportError(_ e: APIError, path: String) {
        let status = e.httpStatus
        let reason: String
        switch e {
        case .network: reason = "network"
        case .decoding: reason = "decoding"
        case .http where status == 403: reason = "plan_limit"
        case .http where status >= 500: reason = "server"
        default: reason = "client"
        }
        Analytics.track(AnalyticsEvent.apiError, [
            "error_status": status, "fail_reason": reason, "requested_path": analyticsPath(path),
        ])
    }

    /// 경로의 숫자 id 는 :id 로 (같은 API 끼리 묶여 보이게)
    static func analyticsPath(_ path: String) -> String {
        path.split(separator: "/", omittingEmptySubsequences: false)
            .map { $0.allSatisfy(\.isNumber) && !$0.isEmpty ? ":id" : String($0) }
            .joined(separator: "/")
            .components(separatedBy: "?").first ?? path
    }

    private static func rawRequest<T: Decodable>(_ method: String, _ path: String, bodyData: Data?) async throws -> T? {
        // 둘러보기(로그인 전)는 읽기만 — 저장 · 수정 · 삭제 · AI 는 보내기 전에 막고 로그인 안내를 띄워요
        // (서버도 같은 규칙으로 401 GUEST_LOGIN_REQUIRED 를 줘요)
        if method != "GET" && TokenManager.shared.isGuest {
            await MainActor.run { LoginPrompt.request(reason: "save_blocked", path: analyticsPath(path)) }
            throw APIError.guestLoginRequired
        }
        var req = try makeRequest(method, path, bodyData: bodyData)
        #if DEBUG
        print("➡️ \(method) \(path)")
        #endif
        var (data, status) = try await perform(req)

        // 401(인증 만료)만 토큰 갱신 대상. 403 은 플랜 제한 같은 정상적인 비즈니스 에러라 제외
        if status == 401 {
            if (try? JSONDecoder().decode(Envelope.self, from: data))?.status == APIError.guestStatus {
                await MainActor.run { LoginPrompt.request(reason: "guest_401", path: analyticsPath(path)) }
                throw APIError.guestLoginRequired
            }
            switch await TokenRefreshService.shared.refreshToken() {
            case .refreshed:
                if let token = TokenManager.shared.accessToken {
                    req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
                }
                (data, status) = try await perform(req)
            case .unavailable:
                // 서버가 잠깐 안 될 뿐이에요 — 로그아웃시키지 않고 이번 요청만 실패로 (예전엔 여기서 로그아웃돼서 로그인이 풀렸어요)
                throw APIError.network
            case .invalid:
                break
            }
            if status == 401 {
                await MainActor.run {
                    // 재발급이 거절됨 → 안내 없이 로그인 화면으로 가요 (session.expired)
                    Analytics.track(AnalyticsEvent.sessionExpired, ["requested_path": analyticsPath(path)])
                    AuthService.shared.logout(reason: .sessionExpired)
                }
                throw APIError.unauthorized
            }
        }

        if status == 204 || data.isEmpty {
            if (200..<300).contains(status) { return nil }
            throw APIError.http(status: status, message: "문제가 생겼어요. 다시 시도해 주세요.", statusName: nil, data: nil)
        }

        // 먼저 공통 껍데기(success/message/status)만 읽어요
        let envelope = try? JSONDecoder().decode(Envelope.self, from: data)
        let ok = (200..<300).contains(status) && (envelope?.success ?? false)
        if !ok {
            #if DEBUG
            print("❌ \(status) \(method) \(path):", String(data: data, encoding: .utf8) ?? "")
            #endif
            throw APIError.http(
                status: status,
                message: envelope?.message ?? "문제가 생겼어요. 다시 시도해 주세요.",
                statusName: envelope?.status,
                data: envelope?.data
            )
        }

        do {
            return try JSONDecoder().decode(DataEnvelope<T>.self, from: data).data
        } catch {
            #if DEBUG
            print("❌ 디코딩 실패 \(method) \(path) → \(T.self):", error)
            #endif
            throw APIError.decoding(String(describing: error))
        }
    }

    private struct Envelope: Decodable {
        let success: Bool
        let status: String?
        let message: String?
        let data: JSONValue?
    }

    private struct DataEnvelope<T: Decodable>: Decodable {
        let data: T?
    }
}

// MARK: - 예전 코드 호환 (로그인 · 결제 · 기기 토큰)

/// 백엔드 공통 응답 형식 { success, message, data, status }
struct ApiCallResult<T: Decodable>: Decodable {
    let success: Bool
    let message: String?
    let data: T?
    let status: String?
    let error: String?
}

/// 반환 데이터가 없는 응답용
struct EmptyResponse: Decodable {}
