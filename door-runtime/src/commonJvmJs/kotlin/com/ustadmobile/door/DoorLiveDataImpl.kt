package com.ustadmobile.door

import com.ustadmobile.door.ext.asCommon
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class DoorLiveDataImpl<T>(
    val db: DoorDatabase,
    val tableNames: List<String>,
    val fetchFn: suspend () -> T
): DoorLiveData<T>() {

    private val dbChangeListenerRequest = ChangeListenerRequest(tableNames) {
        update()
    }

    override fun onActive() {
        super.onActive()
        db.asCommon().addChangeListener(dbChangeListenerRequest)
        update()
    }

    override fun onInactive() {
        super.onInactive()
        db.asCommon().removeChangeListener(dbChangeListenerRequest)
    }

    internal fun update() {
        GlobalScope.launch {
            val retVal = fetchFn()
            postValue(retVal)
        }
    }

}