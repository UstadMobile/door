package com.ustadmobile.door.sse

import com.ustadmobile.door.RepositoryConfig

fun interface DoorEventSourceFactory {

    fun makeNewDoorEventSource(
        repositoryConfig: RepositoryConfig,
        url: String,
        listener: DoorEventListener
    ) : DoorEventSource

}