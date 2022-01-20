package com.ustadmobile.door.sse

/**
 * Unfortunately, using the normal id and event fields is not working with the KTOR server and web browsers.
 *
 * Door Server Sent events therefor have everything in the data line as follows:
 *
 * data: evtid;event;data-content
 *
 *
 */
class DoorServerSentEvent(val id: String, val event: String, val data: String) {
    override fun toString() = "DoorServerSentEvent id='$id' event='$event' data='$data'"

    fun stringify() : String{
        return "$id;$event;$data"
    }

    companion object {

        fun parse(str: String) : DoorServerSentEvent {
            val parts = str.split(';', limit = 3)
            if(parts.size != 3)
                throw IllegalArgumentException("DoorServerSentEvent parse: must have three parts - id;event;data")

            return DoorServerSentEvent(parts[0], parts[1], parts[2])
        }

    }

}
