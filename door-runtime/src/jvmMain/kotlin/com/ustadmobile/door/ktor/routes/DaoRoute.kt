package com.ustadmobile.door.ktor.routes

import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlin.reflect.KClass

fun <T: Any> Route.DoorDaoRoute(
    daoClass: KClass<T>,
    adapter: (ApplicationCall) -> T,
) {
    val ktClass = Class.forName("${daoClass.qualifiedName}_DoorRoute")
    val method = ktClass.getMethod("${daoClass.simpleName}_DoorRoute", /* DaoAdapter */)
    method.invoke(null, this)
}