package com.ustadmobile.door.ext

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.replication.ReplicationEntityMetaData
import kotlin.reflect.KClass

/**
 * Metadata that is generated by the annotation processor for each door database. This provides useful properties
 * e.g. the TableIdMap, Database KClass, etc that can be used at runtime.
 *
 * The metadata can be retrieved via extension functions dbInstance.doorDatabaseMetadata() or dbClass.doorDatabaseMetadata()
 */
abstract class DoorDatabaseMetadata<T: RoomDatabase> {

    /**
     * The KClass object for this database.
     */
    abstract val dbClass: KClass<T>

    abstract val replicateEntities: Map<Int, ReplicationEntityMetaData>

    /**
     * Shorthand to get a list of all the table names that are used by replicate entities
     */
    val replicateTableNames: List<String>
        get() = replicateEntities.values.map { it.entityTableName}

    /**
     * If true, this database has a corresponding DoorDatabaseSyncableReadOnlyWrapper
     */
    abstract val hasReadOnlyWrapper: Boolean

    abstract val version: Int

    fun requireReplicateEntityMetaData(tableId: Int) = replicateEntities[tableId]
        ?: throw IllegalArgumentException("No metadata for table id $tableId")

    /**
     * Lookup the table id of a given table name
     */
    fun getTableId(tableName: String) = replicateEntities.values.first{ it.entityTableName == tableName}.tableId

    /**
     * A list of all tables on the database (whether or not they are annotated with @ReplicateEntity)
     */
    abstract val allTables: List<String>


    companion object {

        /**
         * The Suffix that is added to create the generated class. E.g. For ExampleDatabase the metadata class will be
         * ExampleDatabase_DoorMetadata
         */
        const val SUFFIX_DOOR_METADATA = "_DoorMetadata"

    }

}