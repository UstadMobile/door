package com.ustadmobile.door

import androidx.lifecycle.LiveData
import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class LiveDataImpl<T>(
    val db: RoomDatabase,
    val tableNames: List<String>,
    val fetchFn: suspend () -> T
): LiveData<T>() {

    private val observer = object: InvalidationTracker.Observer(tableNames.toTypedArray()) {
        override fun onInvalidated(tables: Set<String>) {
            update()
        }
    }

    override fun onActive() {
        super.onActive()
        db.invalidationTracker.addObserver(observer)
        update()
    }

    override fun onInactive() {
        super.onInactive()
        db.invalidationTracker.removeObserver(observer)
    }

    internal fun update() {
        GlobalScope.launch {
            val retVal = fetchFn()
            postValue(retVal)
        }
    }

}