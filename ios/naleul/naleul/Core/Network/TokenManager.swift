// Core/Network/TokenManager.swift
import Foundation
import Security

final class TokenManager {
    static let shared = TokenManager()
    private init() {}

    private let accessTokenKey  = "naleul.accessToken"
    private let refreshTokenKey = "naleul.refreshToken"
    private let userIdKey       = "naleul.userId"
    /// 로그인 없이 둘러보기 중인지 ("1"). 둘러보기 토큰은 읽기만 되고 refresh token 이 없어요
    private let guestKey        = "naleul.guest"
    /// 마지막으로 확인한 사용자 정보 — 앱을 켤 때 서버가 잠깐 안 돼도 로그인 화면으로 튕기지 않게
    private let cachedUserKey   = "naleul.cachedUser"

    // App Group — 예전 워치 앱과 공유하던 저장소.
    // 워치가 없어져서 이제는 Keychain 에만 저장해요. 예전 버전에서 로그인한 사용자를 위해 "읽기"만 마지막 수단으로 남겨뒀어요.
    // (App Group 을 열 때마다 콘솔에 'Couldn't read values in CFPrefsPlistSource…' 경고가 찍혀서 평소엔 안 열어요)
    private let appGroupId = "group.naleul.naleul"
    private var sharedDefaults: UserDefaults? {
        UserDefaults(suiteName: appGroupId)
    }

    // MARK: - Save
    func saveAccessToken(_ token: String) {
        save(key: accessTokenKey, value: token)
    }

    func saveRefreshToken(_ token: String) {
        save(key: refreshTokenKey, value: token)
    }

    func saveUserId(_ userId: Int) {
        save(key: userIdKey, value: String(userId))
    }

    func save(accessToken: String, refreshToken: String) {
        saveAccessToken(accessToken)
        saveRefreshToken(refreshToken)
        delete(key: guestKey)   // 진짜 로그인 → 둘러보기 끝
    }

    /// 둘러보기 토큰 저장 (refresh token 없음)
    func saveGuest(accessToken: String, userId: Int) {
        saveAccessToken(accessToken)
        delete(key: refreshTokenKey)
        saveUserId(userId)
        save(key: guestKey, value: "1")
    }

    var isGuest: Bool { read(key: guestKey) == "1" }

    // MARK: - 사용자 정보 캐시 (민감하지 않은 표시용이라 UserDefaults)
    func cacheUser(name: String, email: String, role: String) {
        UserDefaults.standard.set(["name": name, "email": email, "role": role], forKey: cachedUserKey)
    }

    var cachedUser: (name: String, email: String, role: String)? {
        guard let d = UserDefaults.standard.dictionary(forKey: cachedUserKey) as? [String: String],
              let name = d["name"], let role = d["role"] else { return nil }
        return (name, d["email"] ?? "", role)
    }

    func save(accessToken: String, refreshToken: String, userId: Int) {
        save(accessToken: accessToken, refreshToken: refreshToken)
        saveUserId(userId)
    }

    // MARK: - Read
    // Keychain 먼저 → 없을 때만 예전 App Group
    var accessToken: String? {
        read(key: accessTokenKey) ?? sharedDefaults?.string(forKey: accessTokenKey)
    }

    var refreshToken: String? {
        read(key: refreshTokenKey) ?? sharedDefaults?.string(forKey: refreshTokenKey)
    }

    var userId: Int? {
        if let value = read(key: userIdKey), let id = Int(value) { return id }
        return sharedDefaults?.object(forKey: userIdKey) as? Int
    }

    // MARK: - Delete
    func clearAll() {
        delete(key: accessTokenKey)
        delete(key: refreshTokenKey)
        delete(key: userIdKey)
        delete(key: guestKey)
        UserDefaults.standard.removeObject(forKey: cachedUserKey)

        // 예전 App Group 에 남아 있던 값도 같이 지워요 (지우지 않으면 로그아웃해도 다시 읽혀요)
        sharedDefaults?.removeObject(forKey: accessTokenKey)
        sharedDefaults?.removeObject(forKey: refreshTokenKey)
        sharedDefaults?.removeObject(forKey: userIdKey)
    }

    // MARK: - Keychain
    private func save(key: String, value: String) {
        let data = Data(value.utf8)
        let query: [CFString: Any] = [
            kSecClass:       kSecClassGenericPassword,
            kSecAttrAccount: key,
            kSecValueData:   data,
            // 잠금 화면 상태(푸시 처리 · 백그라운드 새로고침)에서도 읽을 수 있게 — 기본값(WhenUnlocked)이면
            // 잠긴 동안 토큰이 "없음"으로 읽혀서 401 → 재발급 실패 → 로그아웃이 될 수 있었어요
            kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlock,
        ]
        // 지울 때는 이름(account)만으로 찾아요 — 예전에 다른 접근 설정으로 저장된 항목도 지워야 새로 넣을 수 있어요
        delete(key: key)
        SecItemAdd(query as CFDictionary, nil)
    }

    private func read(key: String) -> String? {
        let query: [CFString: Any] = [
            kSecClass:       kSecClassGenericPassword,
            kSecAttrAccount: key,
            kSecReturnData:  true,
            kSecMatchLimit:  kSecMatchLimitOne,
        ]
        var result: AnyObject?
        SecItemCopyMatching(query as CFDictionary, &result)
        guard let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func delete(key: String) {
        let query: [CFString: Any] = [
            kSecClass:       kSecClassGenericPassword,
            kSecAttrAccount: key,
        ]
        SecItemDelete(query as CFDictionary)
    }
}
