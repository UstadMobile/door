package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.*
import com.squareup.kotlinpoet.*
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.annotation.*
import com.ustadmobile.door.ext.minifySql
import com.ustadmobile.door.migration.DoorMigrationSync
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_MIGRATIONS_OUTPUT
import kotlinx.serialization.Serializable
import java.io.File
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

class DbProcessorReplicationMigration: AbstractDbProcessor()  {

    lateinit var outputDir: File

    fun TypeElement.prefix(): String {
        return if(simpleName.count { it.isUpperCase() } > 1) {
            simpleName.filter { it.isUpperCase() }.toString().lowercase()
        }else {
            simpleName.toString().lowercase()
        }
    }

    fun FileSpec.Builder.addTrackerClass(entity: TypeElement): FileSpec.Builder {
        val varPrefix = entity.prefix()

        addType(TypeSpec.classBuilder("${entity.simpleName}Replicate")
            .addAnnotation(AnnotationSpec.builder(Entity::class)
                .addMember("primaryKeys = arrayOf(%S, %S)", "${varPrefix}Pk", "${varPrefix}Destination")
                .addMember("indices = arrayOf(%T(value = arrayOf(%S, %S, %S)))", Index::class,
                    "${varPrefix}Destination", "${varPrefix}Processed", "${varPrefix}Pk")
                .build())
            .addAnnotation(Serializable::class)
            .addProperty(PropertySpec.builder("${varPrefix}Pk", LONG)
                .mutable()
                .initializer("0")
                .addAnnotation(ReplicationEntityForeignKey::class)
                .build())
            .addProperty(PropertySpec.builder("${varPrefix}VersionId", LONG)
                .mutable()
                .initializer("0")
                .addAnnotation(ReplicationVersionId::class)
                .build())
            .addProperty(PropertySpec.builder("${varPrefix}Destination", LONG)
                .mutable()
                .initializer("0")
                .addAnnotation(ReplicationDestinationNodeId::class)
                .build())
            .addProperty(PropertySpec.builder("${varPrefix}Processed", BOOLEAN)
                .mutable()
                .initializer("false")
                .addAnnotation(AnnotationSpec.builder(ColumnInfo::class)
                    .addMember("defaultValue = %S", "0")
                    .build())
                .addAnnotation(ReplicationTrackerProcessed::class)
                .build())
            .build())

        return this
    }

    fun FileSpec.Builder.addReplicationMigrationClass(dbTypeEl: TypeElement): FileSpec.Builder {
        val repEntities = dbTypeEl.allDbEntities(processingEnv).filter { it.hasAnnotation(ReplicateEntity::class.java) }
        if(repEntities.isEmpty())
            return this

        repEntities.forEach { repEntity ->
            //Generate the tracker itself
            val entityFields = repEntity.entityFields
            val entityPrefix = repEntity.prefix()
            val nonPkFields = repEntity.entityFields.filter { !it.hasAnnotation(PrimaryKey::class.java) }
            val entityVersionField = repEntity.enclosedElementsWithAnnotation(ReplicationVersionId::class.java).firstOrNull()

            FileSpec.builder(repEntity.packageName, "${repEntity.simpleName}Replicate")
                .addComment("""
                    @Triggers(arrayOf(
                        Trigger(
                            name = "${repEntity.simpleName.toString().lowercase()}_remote_insert",
                            order = Trigger.Order.INSTEAD_OF,
                            on = Trigger.On.RECEIVEVIEW,
                            events = [Trigger.Event.INSERT],
                            sqlStatements = [
                                ""${'"'}REPLACE INTO ${repEntity.simpleName}(${entityFields.joinToString { it.simpleName }}) 
                                VALUES (${entityFields.joinToString { "NEW." + it.simpleName }}) 
                                /*psql ON CONFLICT (${repEntity.entityPrimaryKey!!.simpleName}) DO UPDATE 
                                SET ${nonPkFields.joinToString { "$it = EXCLUDED.$it" }}
                                */""${'"'}
                            ]
                        )
                    ))            
                """.trimIndent())
                .addComment("""
                    @Query(""${'"'}
                    REPLACE INTO ${repEntity.simpleName}Replicate(${entityPrefix}Pk, ${entityPrefix}VersionId, ${entityPrefix}Destination)
                     SELECT ${repEntity.entityTableName}.${repEntity.entityPrimaryKey?.simpleName} AS ${entityPrefix}Uid,
                            ${repEntity.entityTableName}.${entityVersionField?.simpleName} AS ${entityPrefix}VersionId,
                            :newNodeId AS ${entityPrefix}Destination
                       FROM ${repEntity.entityTableName}
                      WHERE ${repEntity.entityTableName}.${entityVersionField?.simpleName} != COALESCE(
                            (SELECT ${entityPrefix}VersionId
                               FROM ${repEntity.simpleName}Replicate
                              WHERE ${entityPrefix}Pk = ${repEntity.entityTableName}.${repEntity.entityPrimaryKey?.simpleName}
                                AND ${entityPrefix}Destination = :newNodeId), 0) 
                     /*psql ON CONFLICT(${entityPrefix}Pk, ${entityPrefix}Destination) DO UPDATE
                            SET ${entityPrefix}Processed = false, ${entityPrefix}VersionId = EXCLUDED.${entityPrefix}VersionId
                     */       
                ""${'"'})
                @ReplicationRunOnNewNode
                @ReplicationCheckPendingNotificationsFor([${repEntity.simpleName}::class])
                abstract suspend fun replicateOnNewNode(@NewNodeIdParam newNodeId: Long)
                """.trimIndent())
                .addComment("""
                    
                    @Query(""${'"'}
                    REPLACE INTO ${repEntity.simpleName}Replicate(${entityPrefix}Pk, ${entityPrefix}VersionId, ${entityPrefix}Destination)
                     SELECT ${repEntity.entityTableName}.${repEntity.entityPrimaryKey?.simpleName} AS ${entityPrefix}Uid,
                            ${repEntity.entityTableName}.${entityVersionField?.simpleName} AS ${entityPrefix}VersionId,
                            UserSession.usClientNodeId AS ${entityPrefix}Destination
                       FROM ChangeLog
                            JOIN ${repEntity.entityTableName}
                                ON ChangeLog.chTableId = ${repEntity.getAnnotation(ReplicateEntity::class.java).tableId}
                                   AND ChangeLog.chEntityPk = ${repEntity.entityTableName}.${repEntity.entityPrimaryKey?.simpleName}
                            JOIN UserSession
                      WHERE UserSession.usClientNodeId != (
                            SELECT nodeClientId 
                              FROM SyncNode
                             LIMIT 1)
                        AND ${repEntity.entityTableName}.${entityVersionField?.simpleName} != COALESCE(
                            (SELECT ${entityPrefix}VersionId
                               FROM ${repEntity.simpleName}Replicate
                              WHERE ${entityPrefix}Pk = ${repEntity.simpleName}.${repEntity.entityPrimaryKey?.simpleName}
                                AND ${entityPrefix}Destination = UserSession.usClientNodeId), 0)
                    /*psql ON CONFLICT(${entityPrefix}Pk, ${entityPrefix}Destination) DO UPDATE
                        SET ${entityPrefix}Processed = false, ${entityPrefix}VersionId = EXCLUDED.${entityPrefix}VersionId
                     */               
                    ""${'"'})
                    @ReplicationRunOnChange([${repEntity.simpleName}::class])
                    @ReplicationCheckPendingNotificationsFor([${repEntity.simpleName}::class])
                    abstract suspend fun replicateOnChange()
                """.trimIndent())
                .addTrackerClass(repEntity)
                .build()
                .writeTo(outputDir)

        }


        fun CodeBlock.Builder.addCreateTrackerTableAndIndex(
            entityType: TypeElement,
            longTypeName: String,
            boolTypeName: String
        ) {
            val varPrefix = entityType.prefix()

            val defaultBoolVal = if(boolTypeName == "INTEGER") "0" else "false"
            add("_stmtList += %S\n", """
                           CREATE TABLE IF NOT EXISTS ${entityType.simpleName}Replicate ( ${varPrefix}Pk $longTypeName NOT NULL,
                                ${varPrefix}VersionId $longTypeName NOT NULL,
                                ${varPrefix}Destination $longTypeName NOT NULL,
                                ${varPrefix}Processed $boolTypeName NOT NULL DEFAULT $defaultBoolVal,
                                PRIMARY KEY (${varPrefix}Pk, ${varPrefix}Destination)) 
                        """.minifySql())
            add("_stmtList += %S\n", """
                            CREATE INDEX index_${entityType.simpleName}Replicate_${varPrefix}Destination_${varPrefix}Processed_${varPrefix}Pk ON ${entityType.simpleName}Replicate (${varPrefix}Destination, ${varPrefix}Processed, ${varPrefix}Pk)
                        """.minifySql())
        }

        addProperty(PropertySpec.builder("${dbTypeEl.simpleName}_ReplicationMigration",
                DoorMigrationSync::class)
            .initializer(CodeBlock.builder()
                .beginControlFlow("%T(1,2)", DoorMigrationSync::class)
                .add("val _stmtList = mutableListOf<String>()\n")
                .beginControlFlow("if(db.%M() == %T.SQLITE)", MemberName("com.ustadmobile.door.ext",
                    "dbType"), DoorDbType::class)
                .apply {
                    dbTypeEl.allDbEntities(processingEnv).filter{ it.hasAnnotation(ReplicateEntity::class.java) }.forEach { entityType ->
                        addCreateTrackerTableAndIndex(entityType, "INTEGER", "INTEGER")

                        addReplicateEntityChangeLogTrigger(
                            entityType, "_stmtList",
                            DoorDbType.SQLITE
                        )
                        addCreateReceiveView(entityType, "_stmtList")


                        addCreateTriggersCode(entityType, "_stmtList", DoorDbType.SQLITE)
                    }
                }
                .nextControlFlow("else")
                .apply {
                    dbTypeEl.allDbEntities(processingEnv).filter{ it.hasAnnotation(ReplicateEntity::class.java) }.forEach { entityType ->
                        addCreateTrackerTableAndIndex(entityType, "BIGINT", "BOOL")


                        addReplicateEntityChangeLogTrigger(
                            entityType, "_stmtList",
                            DoorDbType.POSTGRES
                        )
                        addCreateReceiveView(entityType, "_stmtList")


                        addCreateTriggersCode(entityType, "_stmtList", DoorDbType.POSTGRES)
                    }
                }
                .endControlFlow()
                .add("db.%M(_stmtList)\n", MemberName("com.ustadmobile.door.ext", "execSqlBatch"))
                .endControlFlow()
                .build())
            .build())


        File(outputDir, "trackers.txt")
            .writeText(repEntities.joinToString(separator = ",\n") { "${it.simpleName}Replicate::class" })



        return this
    }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        outputDir = File(processingEnv.options.get(OPTION_MIGRATIONS_OUTPUT), "replication")
        outputDir.takeIf { !it.exists() }?.mkdirs()
        roundEnv.getElementsAnnotatedWith(Database::class.java).map { it as TypeElement }.forEach { dbTypeEl ->
            FileSpec.builder(dbTypeEl.packageName, "${dbTypeEl.simpleName}_ReplicationMigration")
                .addReplicationMigrationClass(dbTypeEl)
                .build()
                .writeTo(outputDir)
        }

        return true
    }
}