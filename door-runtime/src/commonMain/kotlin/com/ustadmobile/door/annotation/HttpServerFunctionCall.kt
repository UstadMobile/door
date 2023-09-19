package com.ustadmobile.door.annotation

import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
/**
 * Used as part of the @HttpAccessible annotation - this can be used to specify function calls that should be made
 * by generated code on the HTTP server. Sometimes the server should return additional replication data that is not
 * part of the annotated query itself. Sometimes a http server must make an additional check that would not be required
 * for a local database call.
 *
 * @param functionName   This function MUST return be part of the same DAO as the function with @HttpAccessible
 *                       annotation. Any parameters must also be found on the function with the @RepoHttpAccessible
 *                       annotation or be listed as part of this function (the types must match), or the parameters
 *                       must be specified within functionArgs
 * @param functionArgs A list of
 */
annotation class HttpServerFunctionCall(
    val functionName: String,
    val functionArgs: Array<HttpServerFunctionParam> = arrayOf(),
    val functionDao: KClass<*> = Any::class,
)
