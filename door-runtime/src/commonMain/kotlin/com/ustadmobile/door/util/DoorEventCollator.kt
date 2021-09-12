package com.ustadmobile.door.util

import com.ustadmobile.door.ext.mutableLinkedListOf
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.updateAndGet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 *
 */
class DoorEventCollator<T>(
    val maxWaitTime: Int,
    val coroutineScope: CoroutineScope,
    val sinkBlock: (List<T>) -> Unit) {

    private val dispatchJob: AtomicRef<Job?> = atomic(null)

    private val mutex = Mutex()

    private val channel = Channel<T>(Channel.UNLIMITED)

    fun Channel<T>.tryReceiveAll(): List<T> {
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
                    delay(maxWaitTime.toLong())
                    dispatchJob.value = null
                    dispatch()
                }
            }
        }
    }

    fun dispatch() {
        sinkBlock(channel.tryReceiveAll())
    }

}