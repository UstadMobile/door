package com.ustadmobile.door

interface SyncListener<T> {

    fun onEntitiesReceived(entities: List<T>)

}