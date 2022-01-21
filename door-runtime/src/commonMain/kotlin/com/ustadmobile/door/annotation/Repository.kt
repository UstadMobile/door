package com.ustadmobile.door.annotation

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)

/**
 * Indicates that a repository should be generated for this DAO. The repository manages getting
 * data over http. It leads to the generation of an HTTP server endpoint and a client repository
 * that can fetch data over http.
 *
 * This annotation must be added to any DAO where one wishes to use a repository.
 */
annotation class Repository(val methodType: Int = 0) {

    companion object {

        const val METHOD_AUTO = 0

        const val METHOD_DELEGATE_TO_DAO = 1

        const val METHOD_DELEGATE_TO_WEB = 2

        @Deprecated("Syncable entity is removed")
        const val METHOD_SYNCABLE_GET = 3

        const val METHOD_NOT_ALLOWED = 4
    }
}