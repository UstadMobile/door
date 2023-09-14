package com.ustadmobile.door.paging

import app.cash.paging.PagingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class DoorRepositoryPagingSource<Key: Any, Value: Any> : PagingSource<Key, Value>() {

    protected val _loadState = MutableStateFlow(PagingSourceLoadState<Key>())

    val loadState: Flow<PagingSourceLoadState<Key>> = _loadState.asStateFlow()

}