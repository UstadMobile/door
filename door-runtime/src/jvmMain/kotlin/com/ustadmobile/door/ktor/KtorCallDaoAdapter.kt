package com.ustadmobile.door.ktor

import com.ustadmobile.door.http.DbAndDao
import io.ktor.server.application.*

/**
 * Simple adapter that is provided to generated Routes for DAOs
 */
fun interface KtorCallDaoAdapter<T : Any> {

    operator fun invoke(call: ApplicationCall): DbAndDao<T>

}