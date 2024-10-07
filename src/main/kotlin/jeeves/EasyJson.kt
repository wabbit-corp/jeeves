package jeeves

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object EasyJson {
    private fun of(value: Any): JsonElement {
        return when (value) {
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> map(value)
            is List<*> -> list(value)
            else -> {
                error("Unsupported type: ${value::class.simpleName}")
            }
        }
    }

    @JvmName("ofAnyPairs")
    private fun of(vararg pairs: Pair<String, Any>): JsonObject =
        JsonObject(pairs.map { (k, v) -> k to of(v) }.toMap())

    fun of(vararg pairs: Pair<String, String>): JsonObject =
        of(*pairs.map { (k, v) -> k to v as Any }.toTypedArray())

    private fun list(list: List<*>): JsonElement {
        return list.map { of(it!!) }.let(::JsonArray)
    }

    private fun map(map: Map<*, *>): JsonObject {
        val result = mutableMapOf<String, JsonElement>()
        for ((k, v) in map) {
            if (k !is String) error("Unsupported key type: ${k?.javaClass?.simpleName}")
            result[k] = of(v!!)
        }
        return JsonObject(result)
    }
}
