

package com.ustadmobile.door.annotation

/**
 * Annotation that indicates any client connecting over HTTP must present its nodeid and auth string.
 *
 * NodeIdAuthCache must be provided via the DI in this case.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Suppress("unused")
annotation class DoorNodeIdAuthRequired
