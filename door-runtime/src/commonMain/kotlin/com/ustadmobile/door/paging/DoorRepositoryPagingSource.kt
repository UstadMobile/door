package com.ustadmobile.door.paging

import app.cash.paging.PagingSource
import app.cash.paging.PagingSourceLoadParams
import app.cash.paging.PagingSourceLoadResult
import app.cash.paging.PagingState
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.ext.DoorTag
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*

/**
 *
 */
class DoorRepositoryPagingSource<Value: Any>(
    private val repo: DoorDatabaseRepository,
    private val repoPath: String,
    private val dbPagingSource: PagingSource<Int, Value>,
    private val onLoadHttp: suspend (params: PagingSourceLoadParams<Int>) -> Unit,
) : PagingSource<Int, Value>(){

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val onDbInvalidatedCallback: () -> Unit = {
        onDbInvalidated()
    }

    init {
        dbPagingSource.registerInvalidatedCallback(onDbInvalidatedCallback)
    }


    private fun onDbInvalidated() {
        dbPagingSource.unregisterInvalidatedCallback(onDbInvalidatedCallback)
        scope.cancel()
        super.invalidate()
    }

    override fun getRefreshKey(state: PagingState<Int, Value>): Int? {
        return dbPagingSource.getRefreshKey(state)
    }

    override suspend fun load(params: PagingSourceLoadParams<Int>): PagingSourceLoadResult<Int, Value> {
        scope.launch {
            try {
                onLoadHttp(params)
            }catch(e: Exception) {
                Napier.v(tag = DoorTag.LOG_TAG) { "" }
            }
        }

        return dbPagingSource.load(params)
    }

    companion object {

        const val PARAM_BATCHSIZE = "pagingBatchSize"

        const val PARAM_KEY = "pagingKey"

    }
}