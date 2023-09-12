package com.ustadmobile.door.ext

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.RepositoryConfig
import io.ktor.http.*
import kotlinx.serialization.SerializationStrategy
import app.cash.paging.PagingSourceLoadParams
import app.cash.paging.PagingSourceLoadParamsAppend
import app.cash.paging.PagingSourceLoadParamsPrepend
import app.cash.paging.PagingSourceLoadParamsRefresh
import com.ustadmobile.door.paging.DoorRepositoryReplicatePullPagingSource.Companion.PARAM_BATCHSIZE
import com.ustadmobile.door.paging.DoorRepositoryReplicatePullPagingSource.Companion.PARAM_KEY
import com.ustadmobile.door.paging.DoorRepositoryReplicatePullPagingSource.Companion.PARAM_LOAD_PARAM_TYPE
import io.ktor.client.request.*
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

fun HttpRequestBuilder.dbVersionHeader(db: RoomDatabase) {
    this.header(DoorConstants.HEADER_DBVERSION, db.dbSchemaVersion())
}

/**
 * Append the client ID header
 */
fun HttpRequestBuilder.doorNodeIdHeader(repo: DoorDatabaseRepository) {
    header(DoorConstants.HEADER_NODE_AND_AUTH, "${repo.config.nodeId}/${repo.config.auth}")
}

fun HttpRequestBuilder.doorNodeIdHeader(nodeId: Long, auth: String) {
    header(DoorConstants.HEADER_NODE_AND_AUTH, "$nodeId/$auth")
}

/**
 * Add required headers: version and nodeid / auth
 */
fun HttpRequestBuilder.doorNodeAndVersionHeaders(repo: DoorDatabaseRepository) {
    dbVersionHeader(repo.db)
    doorNodeIdHeader(repo)
}

/**
 * For use by repositories - set the url
 *
 * @param repoEndpoint the url of the repository as provided to the .asRepository function
 * @param repoPath the path within the repository to use for the URL e.g. DaoName/functionName etc.
 */
fun HttpRequestBuilder.setRepoUrl(
    repoEndpoint: String,
    repoPath: String
) {
    url {
        takeFrom(repoEndpoint) //repositoryConfig.endpoint will always end with '/'
        encodedPath = "${encodedPath}$repoPath"
    }
}

fun HttpRequestBuilder.setRepoUrl(
    repositoryConfig: RepositoryConfig,
    repoPath: String
) = setRepoUrl(repositoryConfig.endpoint, repoPath)

enum class LoadParamType(val paramClass: KClass<*>) {
    REFRESH(PagingSourceLoadParamsRefresh::class),
    PREPEND(PagingSourceLoadParamsPrepend::class),
    APPEND(PagingSourceLoadParamsAppend::class);

    companion object {
        fun paramTypeFor(paramClass: KClass<*>) : LoadParamType {
            return entries.first { it.paramClass == paramClass }
        }
    }
}

/**
 * Used by generated code to send paging source load params in an http request
 */
@Suppress("unused")
fun <K: Any> HttpRequestBuilder.pagingSourceLoadParameters(
    json: Json,
    keySerializer: SerializationStrategy<K?>,
    loadParams: PagingSourceLoadParams<K>,
) {
    val loadParamType = LoadParamType.paramTypeFor(loadParams::class)
    parameter(PARAM_LOAD_PARAM_TYPE, loadParamType.name)
    parameter(PARAM_KEY, json.encodeToString(keySerializer, loadParams.key))
    parameter(PARAM_BATCHSIZE, loadParams.loadSize)
}
