package com.ustadmobile.door.annotation

/**
 * This annotation can be used on one (and no more) DAO parameters on a DAO function that is
 * annotated @RepoHttpAccessible. This will cause the annotated parameter to be serialized into the http body of the
 * request. It will also make the request run using POST instead of GET.
 *
 * All other function parameters will be sent as http query parameters.
 *
 * If a parameter is expected to be large, then it can be better to use the http body. Most servers
 * have default limits on the length of URL that will be accepted.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class RepoHttpBodyParam()
