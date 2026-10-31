// Core/Purchase/IapAPI.swift
import Foundation

enum IapAPI {
    /// 결제 검증 요청 — 백엔드가 Apple 서버 공개키로 재검증 후 Pro 권한을 줘요
    static func verifyPurchase(signedTransaction: String) async throws {
        try await API.send("POST", "/v1/iap/verify", body: ["signedTransaction": .string(signedTransaction)])
    }
}
