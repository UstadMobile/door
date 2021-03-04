package com.ustadmobile.door

class DoorDaoProvider<T: DoorDatabase, D>(val providerFn: (T) -> D) {

    fun getDao(db: T) = providerFn(db)

}