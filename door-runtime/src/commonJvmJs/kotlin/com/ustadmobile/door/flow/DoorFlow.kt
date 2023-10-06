package com.ustadmobile.door.flow

import com.ustadmobile.door.room.InvalidationTrackerObserver
import com.ustadmobile.door.room.RoomDatabase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Used by generated code to return a flow that will emit a value when collected for the first time, and will continue
 * to emit updates whenever the given tables are invalidated.
 *
 * It is used by generated code. It can also be used to consume other queries as a flow.
 *
 * @receiver The database, used to listen for table changes
 * @param tables the list of tables that should be monitored for changes
 * @param block generated function that will run the query itself
 * @return Kotlin Flow that will automatically emit the result of the block, and will emit the result of the block again
 * whenever the tables are changed.
 */
@Suppress("unused") //This function is used by generated code
fun <T> RoomDatabase.doorFlow(
    tables: Array<String>,
    block: suspend () -> T
): Flow<T> = callbackFlow {
    //Capacity should be 1: if there is any invalidation that is received after the last block run, then run again once.
    val invalidationEventChannel = Channel<Boolean>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val invalidationTrackerObserver = object: InvalidationTrackerObserver(tables) {
        override fun onInvalidated(tables: Set<String>) {
            invalidationEventChannel.trySend(true)
        }
    }

    invalidationTracker.addObserver(invalidationTrackerObserver)

    launch {
        trySend(block())
        for(evt in invalidationEventChannel) {
            trySend(block())
        }
    }

    awaitClose {
        invalidationEventChannel.close()
        invalidationTracker.removeObserver(invalidationTrackerObserver)
    }
}
