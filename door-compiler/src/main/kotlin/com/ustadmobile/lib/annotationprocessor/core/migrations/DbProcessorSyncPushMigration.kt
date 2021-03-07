package com.ustadmobile.lib.annotationprocessor.core.migrations

import com.squareup.kotlinpoet.*
import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorMigration
import com.ustadmobile.door.DoorSqlDatabase
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.entities.*
import com.ustadmobile.lib.annotationprocessor.core.*
import androidx.room.Database
import com.ustadmobile.door.annotation.SyncableEntity
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_MIGRATIONS_OUTPUT

/**
 * Adds a class that implements DoorMigration to migrate the given database as per dbTypeElement
 * to use the new syncpush system.
 */
fun FileSpec.Builder.addSyncPushMigrationType(dbTypeElement: TypeElement,
                                              processingEnv: ProcessingEnvironment) : FileSpec.Builder {
    val dbVersionNum: Int = dbTypeElement.getAnnotation(Database::class.java).version
    addType(TypeSpec.classBuilder(dbTypeElement.asClassNameWithSuffix("_SyncPushMigration"))
            .superclass(DoorMigration::class)
            .addSuperclassConstructorParameter("%L", dbVersionNum - 1)
            .addSuperclassConstructorParameter("%L", dbVersionNum)
            .addSyncPushMigrationFunction(dbTypeElement, processingEnv)
            .build())

    return this
}

/**
 * Generates the main Migration.migrate function that will migrate the given database to the new
 * syncpush system
 */
private val NEW_SYNC_HELPER_ENTITIES = listOf(ChangeLog::class, SqliteChangeSeqNums::class,
        TableSyncStatus::class, UpdateNotification::class)

private fun TypeSpec.Builder.addSyncPushMigrationFunction(dbTypeElement: TypeElement,
                                                  processingEnv: ProcessingEnvironment) : TypeSpec.Builder {

    //Drop the old index for entity tracker tables and create the new ones. This function is only
    //required within addSyncPushMigrationFunction, hence encapsulating it here.
    fun CodeBlock.Builder.addRecreateTrkIndexes(entityName: String) : CodeBlock.Builder {
        add("database.execSQL(%S)\n",
                "DROP INDEX IF EXISTS index_${entityName}_trk_clientId_epk_rx_csn")

        //Create a temporary, non-unique index to avoid a performance bottleneck on deleting
        add("database.execSQL(%S)\n",
                "CREATE INDEX index_${entityName}_trk_epk_clientId_tmp " +
                        "ON ${entityName}_trk (epk, clientId)")
        add("database.execSQL(%S)\n", """
            DELETE FROM ${entityName}_trk 
              WHERE 
              pk != 
              (SELECT ${entityName}_trk_nest.pk FROM ${entityName}_trk ${entityName}_trk_nest 
              WHERE ${entityName}_trk_nest.clientId = ${entityName}_trk.clientId AND
              ${entityName}_trk_nest.epk = ${entityName}_trk.epk ORDER BY CSN DESC LIMIT 1) 
        """.trimIndent())
        add("database.execSQL(%S)\n",
                "CREATE INDEX index_${entityName}_trk_clientId_epk_csn " +
                        " ON ${entityName}_trk (clientId, epk, csn)")
        add("database.execSQL(%S)\n",
                "CREATE UNIQUE INDEX index_${entityName}_trk_epk_clientId " +
                        "ON ${entityName}_trk (epk, clientId)")

        //Drop temporary index
        add("database.execSQL(%S)\n",
                "DROP INDEX index_${entityName}_trk_epk_clientId_tmp")

        return this
    }

    addFunction(FunSpec.builder("migrate")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("database", DoorSqlDatabase::class)
            .addCode(CodeBlock.builder()
                    .beginControlFlow("if(database.%M() == %T.SQLITE)",
                        MemberName("com.ustadmobile.door.ext", "dbType"),
                        DoorDbType::class)
                    .apply {
                        //Add the helper entities
                        NEW_SYNC_HELPER_ENTITIES.forEach {entityClass ->
                            val entityTypeEl = entityClass.asTypeElement(processingEnv)
                            addCreateTableCode(entityTypeEl.asEntityTypeSpec(),
                                    "database.execSQL", DoorDbType.SQLITE,
                                    entityTypeEl.indicesAsIndexMirrorList())
                        }

                        //recreate all the triggers
                        dbTypeElement.allDbEntities(processingEnv)
                                .filter { it.hasAnnotation(SyncableEntity::class.java) }
                                .forEach {syncableEntity ->
                                    val syncableEntityInfo = SyncableEntityInfo(
                                            syncableEntity.asClassName(), processingEnv)
                                    addRecreateSqliteTriggerCode(syncableEntity, processingEnv)
                                    addReplaceSqliteChangeSeqNums("database.execSQL",
                                        syncableEntityInfo, preserveCurrentMaxLocalCsn = true)
                                    addInsertTableSyncStatus(syncableEntityInfo,
                                            "database.execSQL", processingEnv)

                                    addRecreateTrkIndexes(syncableEntity.simpleName.toString())
                                }
                    }
                    .nextControlFlow("else")
                    .apply {
                        //Handle postgres
                        NEW_SYNC_HELPER_ENTITIES.forEach {entityClass ->
                            val entityTypeEl = entityClass.asTypeElement(processingEnv)
                            addCreateTableCode(entityTypeEl.asEntityTypeSpec(),
                                    "database.execSQL", DoorDbType.POSTGRES,
                                    entityTypeEl.indicesAsIndexMirrorList())
                        }

                        dbTypeElement.allDbEntities(processingEnv)
                                .filter { it.hasAnnotation(SyncableEntity::class.java) }
                                .forEach { syncableEntity ->

                            val syncableEntityInfo = SyncableEntityInfo(
                                    syncableEntity.asClassName(), processingEnv)
                            addSyncableEntityFunctionPostgres("database.execSQL",
                                    syncableEntityInfo)
                            addRecreateTrkIndexes(syncableEntity.simpleName.toString())
                        }

                    }
                    .endControlFlow()
                    .build())
            .build())
    return this
}

/**
 * Adds the code required that will drop old SQLite triggers and create the new style triggers for
 * syncpush
 */
private fun CodeBlock.Builder.addRecreateSqliteTriggerCode(typeElement: TypeElement,
                                                           processingEnv: ProcessingEnvironment) : CodeBlock.Builder{
    val syncableEntityInfo = SyncableEntityInfo(typeElement.asClassName(), processingEnv)
    add("database.execSQL(%S)\n", "DROP TRIGGER IF EXISTS INS_${syncableEntityInfo.tableId}")
    add("database.execSQL(%S)\n", "DROP TRIGGER IF EXISTS UPD_${syncableEntityInfo.tableId}")
    addSyncableEntityInsertTriggersSqlite("database.execSQL", syncableEntityInfo)
    addSyncableEntityUpdateTriggersSqlite("database.execSQL", syncableEntityInfo)
    return this
}

/**
 * Generates a migration that handles a database being upgraded to the new instant sync (syncpush)
 * system.
 */
class DbProcessorSyncPushMigration : AbstractDbProcessor() {

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        //iterate over databases
        roundEnv.getElementsAnnotatedWith(Database::class.java).map { it as TypeElement }.forEach {dbTypeEl ->
            FileSpec.builder(dbTypeEl.packageName,
                    "${dbTypeEl.simpleName}_SyncPushMigration")
                    .addSyncPushMigrationType(dbTypeEl, processingEnv)
                    .build()
                    .writeToDirsFromArg(OPTION_MIGRATIONS_OUTPUT, useFilerAsDefault = false)

        }


        return true
    }

}