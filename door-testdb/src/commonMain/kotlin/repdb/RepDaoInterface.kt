package repdb

import androidx.room.Insert

interface RepDaoInterface<T> {

    @Insert
    fun interfaceInsertFun(entity: T)

}