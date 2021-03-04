package com.ustadmobile.door.sse

interface DoorEventListener {

    fun onOpen()

    fun onMessage(message: DoorServerSentEvent)

    fun onError(e: Exception)

}