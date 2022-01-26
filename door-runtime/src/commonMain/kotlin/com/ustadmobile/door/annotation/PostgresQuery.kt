package com.ustadmobile.door.annotation

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)


/**
 * Specify an alternative version of a @Query that should be used on Postgres. The result type should be the same, the
 * parameters must also be the same and IN THE SAME ORDER.
 */
annotation class PostgresQuery(val value: String)

