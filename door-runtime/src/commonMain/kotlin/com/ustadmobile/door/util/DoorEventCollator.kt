package com.ustadmobile.door.util

import com.ustadmobile.door.ext.mutableLinkedListOf
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/**
 * Event Collator groups up events within a specified time window, and collates everything received into one event. This
 * can be useful to deal with events (e.g. database changes) in batches.
 *
 * e.g.
 *  val eventCollator = DoorEventCollator<String>(maxWaitTime = 1000) { events ->
 *     println(events.joinToString())
 *  }
 *
 * Then call receiveEvent when an event is received e.g.
 *  eventCollator.receiveEvent("Hello")
 *  eventCollator.receiveEvent("World")
 *  eventCollator.receiveEvent("Again)
 *
 * After a maximum of 1000ms, the onCollate function will be called with a list of all events received within the
 * time window (e.g. "Hello", "World", "Again").
 */
class DoorEventCollator<T>(
    private val maxWaitTime: Long,
    private val coroutineScope: CoroutineScope,
    val onCollate: suspend (List<T>) -> Unit
) {

    private val dispatchJob: AtomicRef<Job?> = atomic(null)

    private val channel = Channel<T>(Channel.UNLIMITED)

    private fun Channel<T>.tryReceiveAll(): List<T> {
        val resultList = mutableLinkedListOf<T>()
        while(true) {
            val result = tryReceive()
            if(result.isSuccess)
                resultList += result.getOrThrow()
            else
                return resultList
        }
    }

    fun receiveEvent(event: T) {
        channel.trySend(event)
        if(dispatchJob.value == null) {
            GlobalScope.launch {
                dispatchJob.value = coroutineScope.launch {
                    delay(maxWaitTime)
                    dispatchJob.value = null
                    onCollate(channel.tryReceiveAll())
                }
            }
        }
    }
}