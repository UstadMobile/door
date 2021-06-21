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

/**
 * Send a list of entity acknowledgements to the server
 *
 * @param endpoint the server endpoint URL to send the acknowledgement to
 * @param path the path from the endpoint base url to post to
 * @param repo the repo for which entity acknowledgements are being sent
 */
suspend fun HttpClient.postEntityAck(ackList: List<EntityAck>, endpoint: String, path: String,
                                     repo: DoorDatabaseRepository) {
    post<Unit> {
        url {
            takeFrom(endpoint)
            encodedPath = "$encodedPath$path"
        }
        doorNodeAndVersionHeaders(repo)

        if(repo is DoorDatabaseSyncRepository) {
            header("x-nid", repo.clientId)
        }

        body = defaultSerializer().write(ackList, ContentType.Application.Json.withUtf8Charset())
    }
}
