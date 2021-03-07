package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.PrimaryKey
import com.squareup.kotlinpoet.*
import com.ustadmobile.door.annotation.*
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement

class SyncableEntityInfo {

    var syncableEntity: ClassName

    var entityPkField: PropertySpec

    var entityMasterCsnField: PropertySpec

    var entityLocalCsnField: PropertySpec

    var entityLastChangedByField: PropertySpec

    var tracker: ClassName

    var trackerCsnField: PropertySpec

    var trackerPkField: PropertySpec

    var trackerDestField: PropertySpec

    var trackerReceivedField: PropertySpec

    var trackerReqIdField: PropertySpec

    var tableId: Int = 0

    var notifyOnUpdate: Array<String>

    /**
     * Creates an SQL statement that will insert a row into the ChangeLog table. This SQL should
     * be included in the SQL that is run by the change triggers.
     */
    val sqliteChangeLogInsertSql: String
        get() = """
                INSERT INTO ChangeLog(chTableId, chEntityPk, dispatched, chTime) 
                SELECT ${tableId}, NEW.${entityPkField.name}, 0, (strftime('%s','now') * 1000) + ((strftime('%f','now') * 1000) % 1000)
            """.trimIndent()

    constructor(syncableEntityParam: ClassName, processingEnv: ProcessingEnvironment) {
        syncableEntity = syncableEntityParam
        val syncableEntityEl = processingEnv.elementUtils.getTypeElement(syncableEntity.canonicalName)
        tableId = syncableEntityEl.getAnnotation(SyncableEntity::class.java).tableId
        notifyOnUpdate = syncableEntityEl.getAnnotation(SyncableEntity::class.java).notifyOnUpdate

        val entityPkFieldEl = syncableEntityEl.enclosedElements
                .first { it.getAnnotation(PrimaryKey::class.java) != null }
        entityPkField = PropertySpec.builder("${entityPkFieldEl.simpleName}",
                entityPkFieldEl.asType().asTypeName()).build()

        val entityMasterCsnFieldEl = syncableEntityEl.enclosedElements
                .first { it.getAnnotation(MasterChangeSeqNum::class.java) != null}
        entityMasterCsnField = PropertySpec.builder("${entityMasterCsnFieldEl.simpleName}",
                entityMasterCsnFieldEl.asType().asTypeName()).build()

        val entityLocalCsnFieldEl = syncableEntityEl.enclosedElements
                .first { it.getAnnotation(LocalChangeSeqNum::class.java) != null}
        entityLocalCsnField = PropertySpec.builder("${entityLocalCsnFieldEl.simpleName}",
                entityLocalCsnFieldEl.asType().asTypeName()).build()


        val entityLastModifiedField = syncableEntityEl.enclosedElements
                .first { it.getAnnotation(LastChangedBy::class.java) != null}
        entityLastChangedByField = PropertySpec.builder("${entityLastModifiedField.simpleName}",
                entityLastModifiedField.asType().asTypeName()).build()


        tracker = ClassName(syncableEntityParam.packageName,
                "${syncableEntityParam.simpleName}${DbProcessorSync.TRACKER_SUFFIX}")

        trackerCsnField = PropertySpec.builder(DbProcessorSync.TRACKER_CHANGESEQNUM_FIELDNAME,
                LONG).build()

        trackerPkField = PropertySpec.builder(DbProcessorSync.TRACKER_ENTITY_PK_FIELDNAME,
                entityPkField.type).build()

        trackerDestField = PropertySpec.builder(DbProcessorSync.TRACKER_DESTID_FIELDNAME,
                INT).build()

        trackerReceivedField = PropertySpec.builder(DbProcessorSync.TRACKER_RECEIVED_FIELDNAME,
                BOOLEAN).build()

        trackerReqIdField = PropertySpec.builder(DbProcessorSync.TRACKER_REQUESTID_FIELDNAME,
                INT).build()
    }




}

