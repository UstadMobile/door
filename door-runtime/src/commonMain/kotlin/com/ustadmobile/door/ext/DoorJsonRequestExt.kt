package com.ustadmobile.door.ext

import app.cash.paging.PagingSourceLoadParams
import app.cash.paging.PagingSourceLoadParamsAppend
import app.cash.paging.PagingSourceLoadParamsPrepend
import app.cash.paging.PagingSourceLoadParamsRefresh
import com.ustadmobile.door.http.DoorJsonRequest
import com.ustadmobile.door.paging.DoorRepositoryReplicatePullPagingSource
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json


/**
 * Used by generated functions on the server side to deserialize the PagingSourceLoadParams that were sent for a
 * PagingSource that is HttpAccessible See HttpRequestBuilderExt#pagingSourceLoadParameters.
 *
 * Note: explicit casting is required because the expect classes for PagingSourceLoadParams do not declare
 * parent class.
 */
@Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST", "NO_CAST_NEEDED")
fun <K: Any> DoorJsonRequest.requirePagingSourceLoadParams(
    json: Json,
    keyDeserializationStrategy: DeserializationStrategy<K?>,
): PagingSourceLoadParams<K> {
    val loadParamsType = LoadParamType.valueOf(
        requireParam(DoorRepositoryReplicatePullPagingSource.PARAM_LOAD_PARAM_TYPE)
    )
    val loadSize = requireParam(DoorRepositoryReplicatePullPagingSource.PARAM_BATCHSIZE).toInt()
    val key: K? = json.decodeFromString(keyDeserializationStrategy, requireParam(DoorRepositoryReplicatePullPagingSource.PARAM_KEY))

    return when(loadParamsType) {
        LoadParamType.REFRESH -> {
            PagingSourceLoadParamsRefresh(key = key, loadSize = loadSize, placeholdersEnabled = false)
        }
        LoadParamType.APPEND -> {
            PagingSourceLoadParamsAppend(
                key = key ?: throw IllegalArgumentException("Loading append type requires key: received null key"),
                loadSize = loadSize,
                placeholdersEnabled = false
            )
        }
        LoadParamType.PREPEND -> {
            PagingSourceLoadParamsPrepend(
                key = key ?: throw IllegalArgumentException("Loading prepend type requires key: received null key"),
                loadSize = loadSize,
                placeholdersEnabled = false
            )
        }
    } as PagingSourceLoadParams<K>
}
