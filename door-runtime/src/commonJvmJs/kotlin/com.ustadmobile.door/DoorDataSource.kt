package com.ustadmobile.door

actual abstract class DoorDataSource <Key, Value> constructor() {

    actual abstract class Factory<Key, Value> {

        abstract fun getData(_offset: Int, _limit: Int): DoorLiveData<List<Value>>

        abstract fun getLength(): DoorLiveData<Int>

    }

}