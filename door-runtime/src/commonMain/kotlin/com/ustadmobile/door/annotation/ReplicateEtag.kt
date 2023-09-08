package com.ustadmobile.door.annotation

/**
 * Any ReplicateEntity must have one (and only one) property annotated as ReplicateEtag. The property can be any type.
 * This works like an Etag as per http:
 *
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/ETag
 *
 * The ReplicateEntity etag is used for offline-first http data pull requests. Requests that return a single entity will
 * have the etag of the entity itself. Requests that return multiple ReplicateEntities will have an etag that is a hash
 * of all the etags in the response. This will be used for cache validation.
 *
 * If the data in an entity is changed, then the etag MUST change. Door also uses the etag value to determine if a
 * replication update was accepted by a remote node.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FIELD)
annotation class ReplicateEtag()
