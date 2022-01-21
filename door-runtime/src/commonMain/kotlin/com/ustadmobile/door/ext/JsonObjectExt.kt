package com.ustadmobile.door.ext

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Convenience function that will get a JsonElement from a JsonObject, or throw an exception if it does not exist
 */
fun JsonObject.getOrThrow(key: String): JsonElement {
    return get(key) ?: throw IllegalArgumentException("JsonObject.getOrThrow: no key $key")
}