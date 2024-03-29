package com.ustadmobile.door.jdbc

import kotlin.Array

expect interface Connection {

    fun setAutoCommit(commit: Boolean)

    fun prepareStatement(sql: String): PreparedStatement

    fun prepareStatement(sql: String, autoGeneratedKeys: Int): PreparedStatement

    fun createStatement(): Statement

    fun createArrayOf(arrayType: String, objects: Array<out Any?>): com.ustadmobile.door.jdbc.Array

    fun getMetaData(): DatabaseMetadata

    fun commit()

    fun rollback()

    fun close()

    fun isClosed(): Boolean

    fun getAutoCommit(): Boolean

}