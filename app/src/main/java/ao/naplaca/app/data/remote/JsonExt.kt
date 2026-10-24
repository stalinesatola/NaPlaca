package ao.naplaca.app.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Leitura tolerante de JSON: qualquer campo em falta ou com tipo inesperado devolve null.

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject
internal fun JsonObject?.arr(key: String): List<JsonObject> =
    (this?.get(key) as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

internal fun JsonObject?.str(key: String): String? {
    val e = this?.get(key) ?: return null
    if (e is JsonNull) return null
    return (e as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

internal fun JsonObject?.int(key: String): Int? = str(key)?.toDoubleOrNull()?.toInt()
internal fun JsonObject?.double(key: String): Double? = str(key)?.toDoubleOrNull()
internal fun JsonObject?.bool(key: String): Boolean = str(key) == "true"

/** "score" pode ser "2" ou {"value":2.0,"displayValue":"2"}. */
internal fun JsonObject?.score(key: String = "score"): Int? {
    val e = this?.get(key) ?: return null
    return when (e) {
        is JsonPrimitive -> e.contentOrNull?.toDoubleOrNull()?.toInt()
        is JsonObject -> e.str("displayValue")?.toDoubleOrNull()?.toInt() ?: e.double("value")?.toInt()
        else -> null
    }
}
