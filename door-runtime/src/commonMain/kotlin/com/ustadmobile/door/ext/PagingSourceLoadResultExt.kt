package com.ustadmobile.door.ext

import app.cash.paging.PagingSourceLoadResult
import app.cash.paging.PagingSourceLoadResultError
import app.cash.paging.PagingSourceLoadResultInvalid
import app.cash.paging.PagingSourceLoadResultPage
import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.http.DoorJsonResponse
import com.ustadmobile.door.paging.DoorRepositoryHttpRequestPagingSource
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Convert the receiver PagingSourceLoadResult into an HttpResponse that can be delivered to a rep client. This works as
 * follows:
 *
 * - LoadResultPage: Http response code = 200. The body will contain the list of items. NextKey, PrevKey, ItemsBefore,
 *   itemsAfter are added as headers on the response.
 *
 * - LoadResultError: Http response code = 500. The body will contain the error message if includeErrorMessageInResponse
 *   is true
 *
 * - LoadResultInvalid: Http response code = 400, body is empty
 *
 * Re. unchecked casting - unfortunately the expect/actual declarations of paging multiplatform for
 * PagingSourceLoadResult do not declare them as children of PagingSourceLoadResult, so they need
 * to be casted
 *
 * @param json: Kotlinx Serialization Json
 * @param localNodeId the id of this node (e.g. the server)
 * @param keySerializer the serializer for the Load Key (must be nullable)
 * @param valueSerializer the serializer for the Paging Source Value type
 * @param includeErrorMessageInResponse if true, include throwable exception in error response
 */
@Suppress("UNCHECKED_CAST", "USELESS_IS_CHECK")
fun <Key: Any, Value: Any> PagingSourceLoadResult<Key, Value>.toJsonResponse(
    json: Json,
    localNodeId: Long,
    keySerializer: SerializationStrategy<Key?>,
    valueSerializer: SerializationStrategy<List<Value>>,
    includeErrorMessageInResponse: Boolean = false,
): DoorJsonResponse {
    return when(this){
        is PagingSourceLoadResultPage<*, *> -> {
            DoorJsonResponse(
                bodyText = json.encodeToString(valueSerializer, this.data as List<Value>),
                headers = listOf(
                    DoorConstants.HEADER_NODE_ID to localNodeId.toString(),
                    DoorRepositoryHttpRequestPagingSource.HEADER_NEXT_KEY to
                        json.encodeToString(keySerializer, this.nextKey as? Key),
                    DoorRepositoryHttpRequestPagingSource.HEADER_PREV_KEY to
                        json.encodeToString(keySerializer, this.prevKey as? Key),
                    DoorRepositoryHttpRequestPagingSource.HEADER_ITEMS_BEFORE to
                        json.encodeToString(Int.serializer(), this.itemsBefore),
                    DoorRepositoryHttpRequestPagingSource.HEADER_ITEMS_AFTER to
                        json.encodeToString(Int.serializer(), this.itemsAfter)
                )
            )
        }
        is PagingSourceLoadResultError<*, *> -> {
            DoorJsonResponse(
                bodyText = if(includeErrorMessageInResponse) (this.throwable.message ?: "") else "Internal Error: see logs",
                contentType = "text/plain",
                responseCode = 500,
                headers = listOf(DoorConstants.HEADER_NODE_ID to localNodeId.toString()),
            )
        }
        is PagingSourceLoadResultInvalid<*, *> -> {
            DoorJsonResponse(
                bodyText = "",
                contentType = "text/plain",
                responseCode = 400,
                headers = listOf(DoorConstants.HEADER_NODE_ID to localNodeId.toString()),
            )
        }
        else -> throw IllegalArgumentException("PagingLoadResult is not page, error or invalid?")
    }
}