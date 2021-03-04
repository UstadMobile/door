package com.ustadmobile.door

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class DoorLiveDataJdbcImpl<T>(val db: DoorDatabase, val tableNames: List<String>,
                                       val fetchFn: () -> T): DoorLiveData<T>() {

    private val dbChangeListenerRequest = DoorDatabase.ChangeListenerRequest(tableNames) {
        update()
    }

    override fun onActive() {
        super.onActive()
        db.addChangeListener(dbChangeListenerRequest)
        update()
    }

    override fun onInactive() {
        super.onInactive()
        db.removeChangeListener(dbChangeListenerRequest)
    }

    internal fun update() {
        GlobalScope.launch {
            val retVal = fetchFn()
            postValue(retVal)
        }
    }

}