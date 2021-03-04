package com.ustadmobile.door

import androidx.paging.DataSource


/**
 * This is a dummy class that is not used on JDBC. It is only here so that we can compile the
 * database on JDBC. DataSource.Factory is used on Android to go along with the paging library.
 */
class DoorDataSourceJdbc<K, V> : DataSource<K, V> (){

    class Factory<Key, Value> : DataSource.Factory<Key, Value>() {

    }

}