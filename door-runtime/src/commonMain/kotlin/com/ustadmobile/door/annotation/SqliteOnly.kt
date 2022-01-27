package com.ustadmobile.door.annotation


@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)

/**
 * Marks that the given query will run ONLY on SQLite. This might be the case for queries that only run on the client.
 * This will skip postgres query validation.
 */
annotation class SqliteOnly()
