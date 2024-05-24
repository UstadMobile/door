package com.ustadmobile.door.annotation

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)

/**
 * Most of the time when a Query modifies a table (e.g. insert, update, delete) the query parser can
 * figure it out.
 * When it cannot detect the table name, this annotation is used to manually specify the table name.
 */
annotation class QueryTableModified(val value: String)