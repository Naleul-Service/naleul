// Features/CalendarImport/GoogleCalendarClient.swift
//
// 구글 캘린더 읽기 (앱에서 직접) — 웹 features/external-calendar/google.ts 와 같은 방식
//  - ASWebAuthenticationSession 으로 구글 로그인 · "캘린더 보기" 권한만 받아 1시간짜리 access token 을 받고,
//    그 토큰으로 Google Calendar API 를 바로 읽고 버려요 (서버에 구글 토큰 · 비밀키를 저장하지 않아요).
//  - PKCE(code_verifier) 를 써서 앱에 client secret 이 필요 없어요 (Google "iOS" OAuth 클라이언트).
//  - 고른 일정만 서버 POST /v1/external-calendar/import (source = GOOGLE) 로 보내요.
//
// 필요한 설정: Core/Config.swift 의 googleIOSClientId (Google Cloud → 클라이언트 → iOS, 번들 ID 로 만든 클라이언트 ID)
//   예) "1234567890-abcdef.apps.googleusercontent.com"
//   돌아오는 주소는 그 ID 를 뒤집은 스킴(com.googleusercontent.apps.1234567890-abcdef:/oauth2redirect)이라
//   ASWebAuthenticationSession 이 직접 받아요 (Info.plist URL Types 추가 불필요).

import AuthenticationServices
import CryptoKit
import Foundation
import SwiftUI
import UIKit

/// 화면에 보여줄 실패 이유 (분석 fail_reason: access_denied · popup_closed · expired · failed — 웹과 같은 값)
struct GoogleCalendarError: LocalizedError {
    let reason: String
    let message: String
    var errorDescription: String? { message }
}

// MARK: - 로그인 (권한 받기)

@MainActor
final class GoogleCalendarAuth: NSObject, ASWebAuthenticationPresentationContextProviding {
    /// 캘린더 목록 보기 + 일정 보기만 (calendar.readonly 보다 좁은 범위 — 구글 OAuth 범위 심사)
    static let scope = ["https://www.googleapis.com/auth/calendar.calendarlist.readonly", "https://www.googleapis.com/auth/calendar.events.readonly"].joined(separator: " ")

    private var session: ASWebAuthenticationSession?

    static var clientId: String { Config.googleIOSClientId }
    static var isConfigured: Bool { !clientId.isEmpty }

    /// "1234-abc.apps.googleusercontent.com" → "com.googleusercontent.apps.1234-abc"
    private static var callbackScheme: String {
        clientId.components(separatedBy: ".").reversed().joined(separator: ".")
    }
    private static var redirectURI: String { "\(callbackScheme):/oauth2redirect" }

    nonisolated func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap(\.windows)
                .first { $0.isKeyWindow } ?? ASPresentationAnchor()
        }
    }

    /// 구글 계정 선택 · 권한 동의 → access token (1시간)
    func requestAccessToken() async throws -> String {
        guard Self.isConfigured else {
            throw GoogleCalendarError(reason: "failed", message: "구글 연동 설정(googleIOSClientId)이 아직 없어요.")
        }
        let verifier = Self.randomURLSafe(32)
        let challenge = Self.base64URL(Data(SHA256.hash(data: Data(verifier.utf8))))
        let state = Self.randomURLSafe(16)

        var c = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
        c.queryItems = [
            .init(name: "client_id", value: Self.clientId),
            .init(name: "redirect_uri", value: Self.redirectURI),
            .init(name: "response_type", value: "code"),
            .init(name: "scope", value: Self.scope),
            .init(name: "code_challenge", value: challenge),
            .init(name: "code_challenge_method", value: "S256"),
            .init(name: "state", value: state),
            .init(name: "prompt", value: "select_account"),
        ]
        let callback = try await authenticate(url: c.url!)
        let items = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems ?? []
        if items.first(where: { $0.name == "error" })?.value == "access_denied" {
            throw GoogleCalendarError(reason: "access_denied", message: "캘린더 읽기 권한을 허용해 주세요.")
        }
        guard items.first(where: { $0.name == "state" })?.value == state,
              let code = items.first(where: { $0.name == "code" })?.value else {
            throw GoogleCalendarError(reason: "failed", message: "구글 연결에 실패했어요.")
        }
        return try await exchange(code: code, verifier: verifier)
    }

    private func authenticate(url: URL) async throws -> URL {
        try await withCheckedThrowingContinuation { cont in
            let s = ASWebAuthenticationSession(url: url, callbackURLScheme: Self.callbackScheme) { callback, error in
                if let callback {
                    cont.resume(returning: callback)
                } else if let e = error as? ASWebAuthenticationSessionError, e.code == .canceledLogin {
                    cont.resume(throwing: GoogleCalendarError(reason: "popup_closed", message: "구글 연결 창을 닫았어요."))
                } else {
                    cont.resume(throwing: GoogleCalendarError(reason: "failed", message: "구글 연결에 실패했어요."))
                }
            }
            s.presentationContextProvider = self
            s.prefersEphemeralWebBrowserSession = false   // 사파리에 로그인된 구글 계정을 그대로
            session = s
            if !s.start() {
                cont.resume(throwing: GoogleCalendarError(reason: "failed", message: "구글 로그인 창을 열지 못했어요."))
            }
        }
    }

    /// 인증 코드 → access token (PKCE 라 client secret 없이)
    private func exchange(code: String, verifier: String) async throws -> String {
        var req = URLRequest(url: URL(string: "https://oauth2.googleapis.com/token")!)
        req.httpMethod = "POST"
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        var form = URLComponents()
        form.queryItems = [
            .init(name: "code", value: code),
            .init(name: "client_id", value: Self.clientId),
            .init(name: "redirect_uri", value: Self.redirectURI),
            .init(name: "grant_type", value: "authorization_code"),
            .init(name: "code_verifier", value: verifier),
        ]
        req.httpBody = form.percentEncodedQuery?.data(using: .utf8)
        struct TokenResponse: Decodable { let access_token: String?; let scope: String? }
        let (data, res) = try await URLSession.shared.data(for: req)
        guard (res as? HTTPURLResponse)?.statusCode == 200,
              let body = try? JSONDecoder().decode(TokenResponse.self, from: data),
              let token = body.access_token else {
            throw GoogleCalendarError(reason: "failed", message: "구글 연결에 실패했어요.")
        }
        // 구글 창에서 체크를 하나 빼면 일부 범위만 와요 → 둘 다 있어야 가져올 수 있어요
        let granted = Set((body.scope ?? "").split(separator: " ").map(String.init))
        guard Self.scope.split(separator: " ").allSatisfy({ granted.contains(String($0)) }) else {
            throw GoogleCalendarError(reason: "access_denied", message: "구글 창에서 캘린더 권한을 모두 체크해 주세요.")
        }
        return token
    }

    private static func randomURLSafe(_ bytes: Int) -> String {
        var b = [UInt8](repeating: 0, count: bytes)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes, &b)
        return base64URL(Data(b))
    }

    private static func base64URL(_ d: Data) -> String {
        d.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}

// MARK: - 캘린더 · 일정 읽기

enum GoogleCalendarAPI {
    private static let base = "https://www.googleapis.com/calendar/v3"

    private struct CalendarList: Decodable {
        struct Item: Decodable {
            let id: String
            let summary: String?
            let summaryOverride: String?
            let backgroundColor: String?
            let primary: Bool?
        }
        let items: [Item]?
    }

    private struct Events: Decodable {
        struct Time: Decodable { let date: String?; let dateTime: String? }
        struct Attendee: Decodable { let `self`: Bool?; let responseStatus: String? }
        struct Item: Decodable {
            let id: String
            let status: String?
            let summary: String?
            let start: Time?
            let end: Time?
            let attendees: [Attendee]?
        }
        let items: [Item]?
        let nextPageToken: String?
    }

    static func calendars(token: String) async throws -> [CalendarImportDeviceCalendar] {
        var c = URLComponents(string: base + "/users/me/calendarList")!
        c.queryItems = [.init(name: "minAccessRole", value: "reader"), .init(name: "maxResults", value: "250")]
        let r: CalendarList = try await get(c.url!, token: token)
        return (r.items ?? []).map {
            CalendarImportDeviceCalendar(
                id: $0.id,
                title: ($0.summaryOverride?.isEmpty == false ? $0.summaryOverride : $0.summary) ?? "",
                accountName: "Google",
                color: Color(hex: $0.backgroundColor ?? "#0F7A9C"),
                primary: $0.primary ?? false
            )
        }
        .sorted { $0.primary && !$1.primary }
    }

    /// from ~ to (둘 다 포함, 한국 날짜) 사이 일정. 캘린더당 최대 1000개
    static func events(token: String, calendars: [CalendarImportDeviceCalendar], from: String, to: String)
        async throws -> [CalendarImportEvent] {
        var out: [CalendarImportEvent] = []
        for cal in calendars {
            var pageToken: String?
            var count = 0
            repeat {
                var allowed = CharacterSet.urlPathAllowed
                allowed.remove(charactersIn: "/@#")
                let calPath = cal.id.addingPercentEncoding(withAllowedCharacters: allowed) ?? cal.id
                var c = URLComponents(string: base + "/calendars/\(calPath)/events")!
                c.queryItems = [
                    .init(name: "timeMin", value: "\(from)T00:00:00+09:00"),
                    .init(name: "timeMax", value: "\(KST.addDays(to, 1))T00:00:00+09:00"),
                    .init(name: "singleEvents", value: "true"),   // 반복 일정을 회차별로 펼쳐서
                    .init(name: "orderBy", value: "startTime"),
                    .init(name: "maxResults", value: "250"),
                    .init(name: "timeZone", value: "Asia/Seoul"),
                ] + (pageToken.map { [URLQueryItem(name: "pageToken", value: $0)] } ?? [])
                // URLComponents 는 쿼리의 "+" 를 그대로 둬서 구글이 공백으로 읽어요 ("+09:00" → " 09:00" → 400 badRequest)
                c.percentEncodedQuery = c.percentEncodedQuery?.replacingOccurrences(of: "+", with: "%2B")
                let r: Events = try await get(c.url!, token: token)
                for e in r.items ?? [] {
                    if let ev = toEvent(e, cal: cal) {
                        out.append(ev)
                        count += 1
                    }
                }
                pageToken = r.nextPageToken
            } while pageToken != nil && count < 1000
        }
        return out
    }

    /// 취소됐거나 내가 거절한 일정은 nil (웹 toExternalEvent — 같은 externalId 라 웹 · 앱 어디서 가져와도 중복을 막아요)
    private static func toEvent(_ e: Events.Item, cal: CalendarImportDeviceCalendar) -> CalendarImportEvent? {
        if e.status == "cancelled" { return nil }
        if e.attendees?.contains(where: { $0.`self` == true && $0.responseStatus == "declined" }) == true { return nil }
        let raw = (e.summary ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let title = String((raw.isEmpty ? "(제목 없음)" : raw).prefix(200))
        let externalId = String("\(cal.id):\(e.id)".prefix(255))
        let calendarName = String(cal.title.prefix(100))
        if let d = e.start?.date {
            return CalendarImportEvent(externalId: externalId, title: title, calendarName: calendarName,
                                       allDay: true, start: nil, end: nil, date: d)
        }
        guard let s = e.start?.dateTime.flatMap(toKst), let en = e.end?.dateTime.flatMap(toKst) else { return nil }
        return CalendarImportEvent(externalId: externalId, title: title, calendarName: calendarName,
                                   allDay: false, start: s, end: en, date: String(s.prefix(10)))
    }

    /// "2026-10-09T10:00:00+09:00" / "...Z" → 한국 시간 벽시계 "2026-10-09T10:00:00"
    private static func toKst(_ iso: String) -> String? {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        let f2 = ISO8601DateFormatter()
        f2.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let date = f.date(from: iso) ?? f2.date(from: iso) else { return nil }
        return KST.dateTime(from: date)
    }

    /// 구글 API 오류 응답 {"error": {"errors": [{"reason": …}], "status": …}} — 제네릭 함수 안에는 타입을 둘 수 없어 밖에 둬요
    private struct GoogleErrorBody: Decodable {
        struct Inner: Decodable {
            struct Item: Decodable { let reason: String? }
            let errors: [Item]?
            let status: String?
        }
        let error: Inner?
    }

    private static func get<T: Decodable>(_ url: URL, token: String) async throws -> T {
        var req = URLRequest(url: url)
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let (data, res) = try await URLSession.shared.data(for: req)
        let status = (res as? HTTPURLResponse)?.statusCode ?? 0
        if status == 401 { throw GoogleCalendarError(reason: "expired", message: "구글 연결이 만료됐어요. 다시 연결해 주세요.") }
        guard (200..<300).contains(status) else {
            // 원인을 알 수 있게 구글 오류 코드 · 이유를 붙여요 (예: 403 insufficientPermissions · accessNotConfigured)
            let g = try? JSONDecoder().decode(GoogleErrorBody.self, from: data)
            let why = g?.error?.errors?.first?.reason ?? g?.error?.status ?? ""
            throw GoogleCalendarError(reason: "failed", message: "구글 캘린더를 불러오지 못했어요. (\(status)\(why.isEmpty ? "" : " " + why))")
        }
        return try JSONDecoder().decode(T.self, from: data)
    }
}
