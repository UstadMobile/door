package db2

import androidx.room.Insert

interface ExampleDaoInterface<T> {

    @Insert
    fun insertOne(entity: T): Long

}