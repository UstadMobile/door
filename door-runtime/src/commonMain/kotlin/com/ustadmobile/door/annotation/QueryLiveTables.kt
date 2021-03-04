package com.ustadmobile.door.annotation

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)

/**
 * Most of the time the the tables to monitor will be determined by the query itself. When JSQLParser
 * cannot detect the table names it is necessary to manually specify the names of tables to monitor.
 */
annotation class QueryLiveTables(val value: Array<String>)