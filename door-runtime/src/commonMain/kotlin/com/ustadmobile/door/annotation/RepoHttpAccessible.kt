package com.ustadmobile.door.annotation

/**
 * Indicates that the given function will have an endpoint that will be accessible by http. The endpoint will respond to
 * get if all parameters are primitives/strings and/or arrays thereof. If any parameter is an object, it will be a post
 * endpoint (where the body of the request should be the object in json form).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class RepoHttpAccessible(
    val repoStrategy: Strategy = Strategy.AUTO,
    val replicateData: Array<HttpReplicateData> = arrayOf(),
) {


    enum class Strategy {
        AUTO, HTTP_ONLY, LOCAL_DB_ONLY
    }
}
