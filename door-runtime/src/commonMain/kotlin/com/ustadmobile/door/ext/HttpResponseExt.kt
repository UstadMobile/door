package com.ustadmobile.door.ext

import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

suspend fun HttpResponse.bodyAsJsonObject(json: Json): JsonObject {
    return json.decodeFromString(JsonObject.serializer(), bodyAsText())
}
