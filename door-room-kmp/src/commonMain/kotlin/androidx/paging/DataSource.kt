package androidx.paging

import androidx.lifecycle.LiveData

abstract class DataSource<Key, Value> {

    abstract class Factory<Key, Value> {
        abstract fun getData(_offset: Int, _limit: Int): LiveData<List<Value>>

        abstract fun getLength(): LiveData<Int>
    }
}