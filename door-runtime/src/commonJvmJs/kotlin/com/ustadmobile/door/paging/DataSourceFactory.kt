package com.ustadmobile.door.paging

import com.ustadmobile.door.lifecycle.LiveData


actual abstract class DataSourceFactory<Key: Any, Value: Any> {
    abstract fun getData(_offset: Int, _limit: Int): LiveData<List<Value>>

    abstract fun getLength(): LiveData<Int>
}
