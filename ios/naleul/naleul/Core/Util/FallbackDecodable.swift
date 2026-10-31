// Core/Util/FallbackDecodable.swift
//
// 백엔드 enum 문자열을 Swift enum 으로 받을 때, 앱이 모르는 새 값이 와도 앱이 죽지 않게 해요.
// (Swift 기본 Decodable enum 은 모르는 값이 오면 디코딩 전체가 실패해서 화면이 통째로 안 떠요)
//
//   enum TaskStatus: String, Codable, FallbackDecodable {
//       case TODO, COMPLETED, SKIPPED, DELETED, unknown
//       static let fallback: TaskStatus = .unknown
//   }

import Foundation

protocol FallbackDecodable: RawRepresentable, Decodable where RawValue == String {
    static var fallback: Self { get }
}

extension FallbackDecodable {
    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = Self(rawValue: raw) ?? Self.fallback
    }
}
