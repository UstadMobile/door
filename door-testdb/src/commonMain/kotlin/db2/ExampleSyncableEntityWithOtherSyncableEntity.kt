package db2

import androidx.room.Embedded

class ExampleSyncableEntityWithOtherSyncableEntity(esUid: Long = 0, esLcsn: Int = 0, esMcsn: Int = 0, esLcb: Int = 0, esNumber: Int = 0) :
        ExampleSyncableEntity(esUid, esLcsn, esMcsn, esLcb, esNumber) {

    @Embedded
    var embeddedOse: OtherSyncableEntity? = null

}