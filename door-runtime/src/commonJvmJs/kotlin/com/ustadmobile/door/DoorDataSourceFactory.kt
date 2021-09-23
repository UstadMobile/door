package com.ustadmobile.door

actual abstract class DoorDataSourceFactory<Key, Value> constructor() {

    abstract fun getData(_offset: Int, _limit: Int): DoorLiveData<List<Value>>

    abstract fun getLength(): DoorLiveData<Int>

}
