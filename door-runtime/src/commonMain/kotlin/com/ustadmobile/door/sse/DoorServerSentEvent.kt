package com.ustadmobile.door.sse

class DoorServerSentEvent(val id: String, val event: String, val data: String) {
    override fun toString() = "DoorServerSentEvent id='$id' event='$event' data='$data'"
}
