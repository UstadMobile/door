package com.ustadmobile.door.ktor

import com.ustadmobile.door.room.RoomDatabase
import io.ktor.server.application.*

/**
 * This adapter should provide the database for the given call. This could be done using virtual hosting, DI, etc.
 */
fun interface DatabaseProvider<T : RoomDatabase> {

    fun databaseForCall(call: ApplicationCall) : T

}