package com.ustadmobile.door.util

import com.ustadmobile.door.ext.mutableLinkedListOf
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/**
 *
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