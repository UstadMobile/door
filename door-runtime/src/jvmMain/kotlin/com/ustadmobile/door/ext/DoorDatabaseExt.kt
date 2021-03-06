package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.ext.DoorDatabaseMetadata.Companion.SUFFIX_DOOR_METADATA
import java.lang.IllegalArgumentException
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import kotlin.reflect.KClass

actual fun DoorDatabase.dbType(): Int = this.jdbcDbType

actual fun DoorDatabase.dbSchemaVersion(): Int = this.dbVersion

actual suspend inline fun <T: DoorDatabase, R> T.doorWithTransaction(crossinline block: suspend(T) -> R): R {
    //TODO: In next version, actually start a transaction here
    return block(this)
}

actual fun DoorDatabase.execSqlBatch(vararg sqlStatements: String) {
    execSQLBatch(*sqlStatements)
}

private val metadataCache = mutableMapOf<KClass<*>, DoorDatabaseMetadata<*>>()

@Suppress("UNCHECKED_CAST")
actual fun <T: DoorDatabase> KClass<T>.doorDatabaseMetadata(): DoorDatabaseMetadata<T> {
    return metadataCache.getOrPut(this) {
        Class.forName(this.java.canonicalName.substringBefore('_') + SUFFIX_DOOR_METADATA).newInstance()
        as DoorDatabaseMetadata<*>
    } as DoorDatabaseMetadata<T>
}
