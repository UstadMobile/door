package com.ustadmobile.door.flow

import com.ustadmobile.door.room.InvalidationTrackerObserver
import com.ustadmobile.door.room.RoomDatabase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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
    val invalidationEventChannel = Channel<Boolean>(Channel.CONFLATED)
    val invalidationTrackerObserver = object: InvalidationTrackerObserver(tables) {
        override fun onInvalidated(tables: Set<String>) {
            invalidationEventChannel.trySend(true)
        }
    }

    invalidationTracker.addObserver(invalidationTrackerObserver)
    invalidationEventChannel.trySend(true)

    try {
        for(evt in invalidationEventChannel) {
            trySend(block())
        }
    }finally {
        invalidationEventChannel.close()
    }
}
