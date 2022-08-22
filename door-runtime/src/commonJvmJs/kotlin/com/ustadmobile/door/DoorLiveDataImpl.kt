package com.ustadmobile.door

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.lifecycle.LiveData
import com.ustadmobile.door.room.InvalidationTrackerObserver
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class LiveDataImpl<T : Any>(
    val db: RoomDatabase,
    val tableNames: List<String>,
    val fetchFn: suspend () -> T
): LiveData<T>() {

    private val observer = object: InvalidationTrackerObserver(tableNames.toTypedArray()) {
        override fun onInvalidated(tables: Set<String>) {
            update()
        }
    }

    override fun onActive() {
        super.onActive()
        db.getInvalidationTracker().addObserver(observer)
        update()
    }

    override fun onInactive() {
        super.onInactive()
        db.getInvalidationTracker().removeObserver(observer)
    }

    internal fun update() {
        GlobalScope.launch {
            val retVal = fetchFn()
            postValue(retVal)
        }
    }

}