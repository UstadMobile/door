package com.ustadmobile.door.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
/**
 * Used as part of the @RepoHttpAccessible annotation
 *
 * @param functionName   This function MUST return be part of the same DAO as the function with @RepoHttpAccessible
 *                       annotation. Any parameters must also be found on the function with the @RepoHttpAccessible
 *                       annotation or be listed as part of this function (the types must match), or the parameters
 *                       must be part of this annotation (e.g. literals, headers, etc)
 */
annotation class HttpReplicateData(
    val functionName: String,
)
