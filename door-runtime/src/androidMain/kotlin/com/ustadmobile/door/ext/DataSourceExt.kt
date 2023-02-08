package com.ustadmobile.door.ext

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.paging.DataSource
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import com.ustadmobile.door.DoorBoundaryCallbackProvider
import com.ustadmobile.door.RepositoryLoadHelper

/**
 * This will return a LiveData<PagedList<T>> object suitable to use with a recycler adapter, using
 * the normal LivePagedListBuilder. This will link the LivePagedListBuilder to the repository's
 * BoundaryCallback so that items are loaded automatically. It will also wrap the live data using
 * the loadHelper so that the LoadHelper can detect if data is being actively observed.
 */
fun <T:Any> DataSource.Factory<Int, T>.asRepositoryLiveData(dao: Any) : LiveData<PagedList<T>> {
    val boundaryCallback = (dao as? DoorBoundaryCallbackProvider)?.getBoundaryCallback(this)
    val pagedListBuilder = LivePagedListBuilder(this, 20)

    if(boundaryCallback != null) {
        pagedListBuilder.setBoundaryCallback(boundaryCallback)
    }

    var liveData: LiveData<PagedList<T>> = pagedListBuilder.build()
    if(boundaryCallback != null) {
        liveData = boundaryCallback.loadHelper.wrapLiveData(liveData)
    }

    return liveData
}