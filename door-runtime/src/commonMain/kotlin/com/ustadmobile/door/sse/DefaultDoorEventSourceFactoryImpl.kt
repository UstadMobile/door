package com.ustadmobile.door.sse

import com.ustadmobile.door.RepositoryConfig

class DefaultDoorEventSourceFactoryImpl : DoorEventSourceFactory{

    override fun makeNewDoorEventSource(
        repositoryConfig: RepositoryConfig,
        url: String,
        listener: DoorEventListener
    ) : DoorEventSource{
        return DoorEventSource(repositoryConfig, url, listener)
    }
}