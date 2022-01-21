package com.ustadmobile.door.annotation

/**
 * Annotation that indicates the minimum version that will be accepted for an incoming replication client.
 * The client adds the "door-dbversion" header to each request.
 *
 * Any HTTP request that does not meet the MinSyncVersion will receive an HTTP 400 (Bad Request)
 * response.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class MinReplicationVersion(val value: Int)
