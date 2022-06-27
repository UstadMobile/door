package com.ustadmobile.door

import androidx.lifecycle.LiveData
import com.ustadmobile.door.ext.asCommon
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class LiveDataImpl<T>(
    val db: DoorDatabase,
    val tableNames: List<String>,
    val fetchFn: suspend () -> T
): LiveData<T>() {

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