package com.ustadmobile.door

import androidx.paging.DataSource

/**
 * This interface is implemented by repositories on Android. The repository keeps a WeakHashMap
 * with a generated boundarycallback for each DataSource.Factory that is provided.
 */
interface DoorBoundaryCallbackProvider {

    fun <T> getBoundaryCallback(dataSource: DataSource.Factory<Int, T>): RepositoryBoundaryCallback<T>?

}