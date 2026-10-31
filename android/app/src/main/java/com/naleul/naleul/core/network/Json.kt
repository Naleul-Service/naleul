// core/network/Json.kt
//
// iOS Core/Network/JSONValue.swift 를 옮긴 파일.
// iOS 는 JSONValue 라는 enum 을 직접 만들었지만, Kotlin 은 kotlinx.serialization 의 JsonElement 가
// 같은 역할을 해서 그걸 그대로 써요. (JSONValue = JsonElement 별칭)
//
// 요청 body 만들기 (iOS 의 ["title": .string(title), "emoji": .null])
//   val body = json("title" to title, "emoji" to null, "minutes" to 30)
//   val body = json("ids" to ids)                       // List<Int> → JSON 배열
//   val body = jsonCompact("memo" to memo, "date" to d) // null 인 키는 아예 빼요 (iOS JSONValue.compact)
//   val body = JSONValueUtil.from(someSerializableObject)   // @Serializable 객체 → JsonElement
//
// 응답 꺼내기 (iOS 의 v["violations"]?.arrayValue)
//   el["violations"]?.arrayValue
//   el["ruleId"]?.stringValue

package com.naleul.naleul.core.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** iOS JSONValue 와 같은 뜻 — 모양이 자유로운 JSON 값 */
typealias JSONValue = JsonElement

/**
 * 앱 전체에서 쓰는 JSON 설정.
 *  - ignoreUnknownKeys: 모델에 없는 필드가 와도 무시 (Swift JSONDecoder 와 같아요)
 *  - coerceInputValues: null 이 기본값 있는 non-null 필드에 오면 기본값으로
 *  - explicitNulls = false: 보낼 때 null 필드는 빼요 (Swift Encodable 이 nil 을 빼는 것과 같아요)
 *    → "null 로 지우기"를 보내야 하면 json("emoji" to null) 처럼 JsonElement 로 직접 만들어요
 */
val NJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
    encodeDefaults = true
}

// MARK: - 값 → JsonElement

/** Kotlin 값을 JsonElement 로. 지원: null, String, Number, Boolean, Enum(name), List/Array, Map, JsonElement */
fun toJsonValue(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Enum<*> -> JsonPrimitive(v.name)
    is Map<*, *> -> JsonObject(v.entries.associate { (k, value) -> k.toString() to toJsonValue(value) })
    is Iterable<*> -> JsonArray(v.map { toJsonValue(it) })
    is Array<*> -> JsonArray(v.map { toJsonValue(it) })
    is IntArray -> JsonArray(v.map { JsonPrimitive(it) })
    else -> JsonPrimitive(v.toString())
}

/** JSON 객체 만들기. 값이 null 이면 JSON null 로 보내요 (iOS .null) */
fun json(vararg pairs: Pair<String, Any?>): JsonObject =
    JsonObject(pairs.associate { (k, v) -> k to toJsonValue(v) })

/** 값이 null 인 키는 빼고 만들어요 (iOS JSONValue.compact — 웹에서 undefined 로 안 보내는 필드) */
fun jsonCompact(vararg pairs: Pair<String, Any?>): JsonObject =
    JsonObject(pairs.filter { it.second != null }.associate { (k, v) -> k to toJsonValue(v) })

/** JSON 배열 */
fun jsonArray(items: List<Any?>): JsonArray = JsonArray(items.map { toJsonValue(it) })

object JSONValueUtil {
    /** @Serializable 객체 → JsonElement (null 필드는 빠져요) */
    inline fun <reified T> from(value: T): JsonElement = NJson.encodeToJsonElement(kotlinx.serialization.serializer<T>(), value)

    /** JsonElement → 모델 (실패하면 null) */
    inline fun <reified T> decode(el: JsonElement?): T? =
        if (el == null) null else runCatching { NJson.decodeFromJsonElement(kotlinx.serialization.serializer<T>(), el) }.getOrNull()
}

// MARK: - 꺼내기 (iOS subscript / stringValue / intValue …)

operator fun JsonElement.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

val JsonElement.stringValue: String?
    get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

val JsonElement.intValue: Int?
    get() = (this as? JsonPrimitive)?.let { p -> if (p.isString) null else p.intOrNull ?: p.doubleOrNull?.toInt() }

val JsonElement.longValue: Long?
    get() = (this as? JsonPrimitive)?.let { p -> if (p.isString) null else p.longOrNull ?: p.doubleOrNull?.toLong() }

val JsonElement.doubleValue: Double?
    get() = (this as? JsonPrimitive)?.let { p -> if (p.isString) null else p.doubleOrNull }

val JsonElement.boolValue: Boolean?
    get() = (this as? JsonPrimitive)?.let { p -> if (p.isString) null else p.booleanOrNull }

val JsonElement.arrayValue: List<JsonElement>?
    get() = this as? JsonArray

val JsonElement.objectValue: Map<String, JsonElement>?
    get() = this as? JsonObject

val JsonElement.isNull: Boolean
    get() = this is JsonNull

/** 디버그 출력용 */
fun JsonElement.toJsonString(): String = NJson.encodeToString(JsonElement.serializer(), this)

// MARK: - 모르는 값이 와도 죽지 않는 enum (iOS FallbackDecodable)
//
// iOS:
//   enum TaskStatus: String, Codable, FallbackDecodable { case TODO, COMPLETED, unknown; static let fallback = .unknown }
// Kotlin:
//   @Serializable(with = TaskStatus.Serializer::class)
//   enum class TaskStatus { TODO, COMPLETED, unknown;
//       object Serializer : FallbackEnumSerializer<TaskStatus>("TaskStatus", TaskStatus.entries.toTypedArray(), TaskStatus.unknown)
//       companion object
//   }
// rawValue 가 이름과 다르면 raw 람다를 넘겨요:  FallbackEnumSerializer<UserRole>("UserRole", UserRole.entries.toTypedArray(), UserRole.free, { it.raw })

open class FallbackEnumSerializer<E : Enum<E>>(
    serialName: String,
    private val values: Array<E>,
    private val fallback: E,
    private val raw: (E) -> String = { it.name },
) : KSerializer<E> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("naleul.$serialName", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): E {
        val s = decoder.decodeString()
        return values.firstOrNull { raw(it) == s } ?: fallback
    }

    override fun serialize(encoder: Encoder, value: E) {
        encoder.encodeString(raw(value))
    }
}
