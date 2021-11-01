package com.ustadmobile.door.ext

import com.ustadmobile.door.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.client.features.json.defaultSerializer

/**
 * Used to implement support for returning null from a 204 response
 */
suspend inline fun <reified T> HttpStatement.receiveOrNull() : T?{
    return execute {
        if (it.status == HttpStatusCode.NoContent) {
            null
        } else {
            it.receive<T>()
        }
    }
}

/**
 * Supports returning null when the server provides a 204 response.
 */
suspend inline fun <reified T> HttpClient.getOrNull(block: HttpRequestBuilder.() -> Unit = {}) : T?{
    val httpStatement = get<HttpStatement> {
        apply(block)
    }

    return httpStatement.receiveOrNull<T>()
}

/**
 * Supports returning null when the server provides a 204 response.
 */
suspend inline fun <reified T> HttpClient.postOrNull(block: HttpRequestBuilder.() -> Unit = {}) : T? {
    val httpStatement = post<HttpStatement> {
        apply(block)
    }

    return httpStatement.receiveOrNull<T>()
}

