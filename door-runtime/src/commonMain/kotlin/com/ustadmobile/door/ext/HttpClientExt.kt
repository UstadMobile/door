package com.ustadmobile.door.ext

import com.ustadmobile.door.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Used to implement support for returning null from a 204 response
 */
suspend inline fun <reified T> HttpStatement.receiveOrNull() : T?{
    return execute {
        if (it.status == HttpStatusCode.NoContent) {
            null
        } else {
            it.body()
        }
    }
}

suspend inline fun <reified T> HttpResponse.bodyOrNull() : T? {
    return if(status == HttpStatusCode.NoContent){
        null
    }else {
        body()
    }
}

/**
 * Supports returning null when the server provides a 204 response.
 */
suspend inline fun <reified T> HttpClient.getOrNull(block: HttpRequestBuilder.() -> Unit = {}) : T?{
    return get {
        block()
    }.bodyOrNull()
}

/**
 * Supports returning null when the server provides a 204 response.
 */
suspend inline fun <reified T> HttpClient.postOrNull(block: HttpRequestBuilder.() -> Unit = {}) : T? {
    return post {
        block()
    }.bodyOrNull()
}

