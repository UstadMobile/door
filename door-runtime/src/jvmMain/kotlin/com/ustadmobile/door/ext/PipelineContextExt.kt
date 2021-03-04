package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseSyncableReadOnlyWrapper
import io.ktor.application.*
import io.ktor.util.pipeline.*
import org.kodein.di.direct
import org.kodein.di.ktor.di
import org.kodein.di.on
import org.kodein.type.TypeToken

/**
 * Gets the database (unwrapped it is using DoorDatabaseSyncableReadOnlyWrapper) for the current
 * application call pipeline context.
 *
 * This is used by generated code in Ktor routes.
 */
fun <T: DoorDatabase> PipelineContext<Unit, ApplicationCall>.unwrappedDbOnCall(typeToken: TypeToken<T>): T{
    val db = di().on(call).direct.Instance(typeToken, tag = DoorTag.TAG_DB)
    return if(db is DoorDatabaseSyncableReadOnlyWrapper) {
        db.realDatabase as T
    }else {
        db
    }
}
