//IntelliJ doesn't understand the expect/actuals, so incorrectly thinks there are unused imports
@file:Suppress("UnusedImport")

package com.ustadmobile.door.paging

import app.cash.paging.*
import com.ustadmobile.door.ext.DoorTag
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * This client connects to generated paging source http endpoints that do not use the replicate strategy. RE
 *
 * This class will have a lot of (incorrect) red underlines due to IntelliJ's failure to understand expect/actuals
 * in many instances.
 *
 * See PagingSourceLoadResultExt#toJsonResponse which contains the server side logic to serialize a
 * PagingSourceLoadResult as an HttpResponse
 */
@Suppress("unused") // Used by generated code.
class DoorRepositoryHttpPagingSource<Value: Any>(
    private val valueDeserializationStrategy: DeserializationStrategy<List<Value>>,
    private val json: Json,
    private val onLoadHttp: suspend (PagingSourceLoadParams<Int>) -> HttpResponse,
    private val fallbackPagingSource: PagingSource<Int, Value>? = null,
) : PagingSource<Int, Value>(){

    class HttpPagingSourceRemoteException(
        @Suppress("unused") // part of the API - can be used by consumers
        val httpStatusCode: HttpStatusCode,
        message: String?
    ): Exception(message)


    override fun getRefreshKey(state: PagingState<Int, Value>): Int? {
        return state.anchorPosition
    }

    override suspend fun load(params: PagingSourceLoadParams<Int>): PagingSourceLoadResult<Int, Value> {
        try {
            val httpResponse = onLoadHttp(params)
            return when(httpResponse.status) {
                HttpStatusCode.OK -> {
                    PagingSourceLoadResultPage(
                        data = json.decodeFromString(
                            deserializer = valueDeserializationStrategy,
                            httpResponse.bodyAsText()
                        ),
                        prevKey = json.decodeFromString(
                            deserializer = Int.serializer().nullable,
                            httpResponse.headers[HEADER_PREV_KEY] ?: return PagingSourceLoadResultInvalid()
                        ),
                        nextKey = json.decodeFromString(
                            deserializer = Int.serializer().nullable,
                            httpResponse.headers[HEADER_NEXT_KEY] ?: return PagingSourceLoadResultInvalid()
                        ),
                        itemsBefore = httpResponse.headers[HEADER_ITEMS_BEFORE]?.let {
                            json.decodeFromString(Int.serializer(), it)
                        } ?: COUNT_UNDEFINED,
                        itemsAfter = httpResponse.headers[HEADER_ITEMS_AFTER]?.let {
                            json.decodeFromString(Int.serializer(), it)
                        } ?: COUNT_UNDEFINED
                    )
                }
                HttpStatusCode.InternalServerError -> {
                    PagingSourceLoadResultError(
                        throwable = HttpPagingSourceRemoteException(
                            httpStatusCode = httpResponse.status,
                            message = httpResponse.bodyAsText()
                        )
                    )
                }
                else -> {
                    PagingSourceLoadResultInvalid()
                }
            }
        }catch(e: Exception) {
            Napier.w(tag = DoorTag.LOG_TAG, throwable = e) {
                "DoorRepositoryHttpPagingSource: could not load"
            }
            return fallbackPagingSource?.load(params) ?: throw e
        }
    }

    companion object {

        const val HEADER_NEXT_KEY = "door-paging-next-key"

        const val HEADER_PREV_KEY = "door-paging-prev-key"

        const val HEADER_ITEMS_BEFORE = "door-paging-items-before"

        const val HEADER_ITEMS_AFTER = "door-paging-items-after"

    }
}