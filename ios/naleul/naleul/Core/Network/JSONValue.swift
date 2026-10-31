// Core/Network/JSONValue.swift
//
// 자유로운 모양의 JSON 값. 요청 body 를 딕셔너리처럼 만들 때 써요.
// (웹은 { title, emoji: null } 처럼 객체를 바로 보내는데, Swift 의 Encodable 구조체는
//  nil 을 아예 빼버려서 "null 로 지우기"를 표현하기 어려워요. 그래서 null 을 명시할 수 있는 타입을 둬요.)
//
// 사용 예:
//   let body: JSONValue = ["title": .string(title), "emoji": .null, "minutes": .int(30)]
//   let body: JSONValue = .object(["ids": .array(ids.map { .int($0) })])
//   JSONValue.from(encodable)   // Encodable 구조체 → JSONValue

import Foundation

enum JSONValue: Codable, Hashable {
    case null
    case bool(Bool)
    case int(Int)
    case double(Double)
    case string(String)
    case array([JSONValue])
    case object([String: JSONValue])

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null }
        else if let v = try? c.decode(Bool.self) { self = .bool(v) }
        else if let v = try? c.decode(Int.self) { self = .int(v) }
        else if let v = try? c.decode(Double.self) { self = .double(v) }
        else if let v = try? c.decode(String.self) { self = .string(v) }
        else if let v = try? c.decode([JSONValue].self) { self = .array(v) }
        else { self = .object(try c.decode([String: JSONValue].self)) }
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case .null: try c.encodeNil()
        case .bool(let v): try c.encode(v)
        case .int(let v): try c.encode(v)
        case .double(let v): try c.encode(v)
        case .string(let v): try c.encode(v)
        case .array(let v): try c.encode(v)
        case .object(let v): try c.encode(v)
        }
    }

    subscript(key: String) -> JSONValue? {
        if case .object(let o) = self { return o[key] }
        return nil
    }

    var stringValue: String? { if case .string(let v) = self { return v }; return nil }
    var intValue: Int? {
        switch self {
        case .int(let v): return v
        case .double(let v): return Int(v)
        default: return nil
        }
    }
    var doubleValue: Double? {
        switch self {
        case .int(let v): return Double(v)
        case .double(let v): return v
        default: return nil
        }
    }
    var boolValue: Bool? { if case .bool(let v) = self { return v }; return nil }
    var arrayValue: [JSONValue]? { if case .array(let v) = self { return v }; return nil }
    var objectValue: [String: JSONValue]? { if case .object(let v) = self { return v }; return nil }
    var isNull: Bool { if case .null = self { return true }; return false }

    /// Encodable 값을 JSONValue 로 (nil 필드는 빠져요)
    static func from<E: Encodable>(_ value: E) -> JSONValue {
        guard let data = try? JSONEncoder().encode(value),
              let v = try? JSONDecoder().decode(JSONValue.self, from: data) else { return .null }
        return v
    }

    /// JSONValue → Decodable 모델
    func decode<T: Decodable>(_ type: T.Type) -> T? {
        guard let data = try? JSONEncoder().encode(self) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }
}

// MARK: - 리터럴로 바로 만들기

extension JSONValue: ExpressibleByStringLiteral, ExpressibleByIntegerLiteral, ExpressibleByBooleanLiteral,
                     ExpressibleByFloatLiteral, ExpressibleByArrayLiteral, ExpressibleByDictionaryLiteral {
    init(stringLiteral value: String) { self = .string(value) }
    init(integerLiteral value: Int) { self = .int(value) }
    init(booleanLiteral value: Bool) { self = .bool(value) }
    init(floatLiteral value: Double) { self = .double(value) }
    init(arrayLiteral elements: JSONValue...) { self = .array(elements) }
    init(dictionaryLiteral elements: (String, JSONValue)...) {
        self = .object(Dictionary(elements, uniquingKeysWith: { _, last in last }))
    }
}

// MARK: - Optional 값 → JSONValue (nil 이면 .null)

extension JSONValue {
    static func of(_ v: String?) -> JSONValue { v.map { .string($0) } ?? .null }
    static func of(_ v: Int?) -> JSONValue { v.map { .int($0) } ?? .null }
    static func of(_ v: Double?) -> JSONValue { v.map { .double($0) } ?? .null }
    static func of(_ v: Bool?) -> JSONValue { v.map { .bool($0) } ?? .null }
    static func of(_ v: [String]?) -> JSONValue { v.map { .array($0.map { .string($0) }) } ?? .null }
    static func of(_ v: [Int]?) -> JSONValue { v.map { .array($0.map { .int($0) }) } ?? .null }

    /// 객체에서 값이 nil 인 키를 빼고 만들어요 (웹에서 undefined 로 안 보내는 필드)
    static func compact(_ pairs: [String: JSONValue?]) -> JSONValue {
        var out: [String: JSONValue] = [:]
        for (k, v) in pairs { if let v { out[k] = v } }
        return .object(out)
    }
}
