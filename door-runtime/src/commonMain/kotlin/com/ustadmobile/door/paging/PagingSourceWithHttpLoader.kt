package com.ustadmobile.door.paging

import app.cash.paging.PagingSourceLoadParams

/**
 * This is normally implemented in DoorRepositoryReplicatePullPagingSource. It can however be implemented in
 * custom-made paging sources to enable them to work with Compose functions and React hooks such as
 * rememberDoorRepositoryPager and useDoorRemoteMediator
 */
interface PagingSourceWithHttpLoader<Key: Any> {

    /**
     * This simply triggers the onLoadHttp function (e.g. the generated function). It is invoked by
     * DoorOffsetLimitRemoteMediator
     */
    suspend fun loadHttp(
        params: PagingSourceLoadParams<Key>
    ): Boolean

}
