package com.ustadmobile.door.ktor

import com.ustadmobile.door.room.RoomDatabase
import io.ktor.server.application.*

fun interface KtorCallDbAdapter<T: RoomDatabase> {

    operator fun invoke(call: ApplicationCall): T

}
