package com.ustadmobile.door.paging

import app.cash.paging.*
import com.ustadmobile.door.log.DoorLogLevel

@Suppress("CAST_NEVER_SUCCEEDS")
@OptIn(ExperimentalPagingApi::class)
class DoorRepositoryRemoteMediator<Value: Any> (
    private val pagingSource: () -> PagingSource<Int, Value>,
    private val loadSize: Int = 50,
): RemoteMediator<Int, Value>() {

    override suspend fun initialize(): RemoteMediatorInitializeAction {
        return RemoteMediatorInitializeAction.LAUNCH_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, Value>
    ): RemoteMediatorMediatorResult {
        val doorHttpPagingSource = pagingSource() as? DoorRepositoryReplicatePullPagingSource<Value>
        if(doorHttpPagingSource != null) {
            val logger = doorHttpPagingSource.repo.config.logger

            return when(loadType) {
                LoadType.APPEND -> {
                    val keyToLoad = state.pages.lastOrNull()?.let { lastPage ->
                        lastPage.nextKey ?: state.pages.sumOf { it.data.size }
                    }

                    logger.log(DoorLogLevel.VERBOSE) { "DoorRepositoryRemoteMediator: APPEND FROM $keyToLoad loadSize=$loadSize" }
                    val endOfPaginationReached = if(keyToLoad != null) {
                        doorHttpPagingSource.loadHttp(
                            PagingSourceLoadParamsAppend(keyToLoad, loadSize, false) as PagingSourceLoadParams<Int>
                        )
                    }else {
                        true
                    }
                    RemoteMediatorMediatorResultSuccess(endOfPaginationReached = endOfPaginationReached) as RemoteMediatorMediatorResult
                }

                LoadType.PREPEND -> {
                    val keyToLoad = state.pages.firstOrNull()?.prevKey
                    logger.log(DoorLogLevel.VERBOSE) { "DoorRepositoryRemoteMediator: PREPEND FROM $keyToLoad loadSize=$loadSize" }
                    val endOfPaginationReached = if(keyToLoad != null) {
                        doorHttpPagingSource.loadHttp(
                            PagingSourceLoadParamsPrepend(keyToLoad, loadSize, false) as PagingSourceLoadParams<Int>
                        )
                    }else {
                        true
                    }
                    RemoteMediatorMediatorResultSuccess(endOfPaginationReached = endOfPaginationReached) as RemoteMediatorMediatorResult
                }

                LoadType.REFRESH -> {
                    val keyToLoad = state.anchorPosition ?: 0
                    logger.log(DoorLogLevel.VERBOSE) { "DoorRepositoryRemoteMediator: REFRESH FROM $keyToLoad loadSize=$loadSize" }
                    val endOfPaginationReached = doorHttpPagingSource.loadHttp(
                        PagingSourceLoadParamsRefresh(keyToLoad, loadSize, false) as PagingSourceLoadParams<Int>
                    )

                    RemoteMediatorMediatorResultSuccess(endOfPaginationReached = endOfPaginationReached) as RemoteMediatorMediatorResult
                }

                else -> throw IllegalStateException()
            }
        }else {
            println("DoorRepositoryRemoteMediator: Not a DoorRepositoryReplicatePullPagingSource")
        }

        return RemoteMediatorMediatorResultSuccess(endOfPaginationReached = false) as RemoteMediatorMediatorResult
    }
}