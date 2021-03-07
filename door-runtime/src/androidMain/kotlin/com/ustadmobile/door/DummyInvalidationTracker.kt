package com.ustadmobile.door

import androidx.room.InvalidationTracker

/**
 * Because a repository is a child class the database, which on Android is a child class of RoomDatabase
 * we have to implement the createInvalidationTracker method. We don't really want to do that on a
 * repository, so we create a dummy here instead.
 */
class DummyInvalidationTracker {

    companion object {
        fun createDummyInvalidationTracker(db: DoorDatabase)  = object: InvalidationTracker(db){
            //do nothing
        }
    }
}

