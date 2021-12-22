package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Database
import androidx.room.Entity
import com.squareup.kotlinpoet.*
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.annotation.*
import com.ustadmobile.door.migration.DoorMigrationSync
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_MIGRATIONS_OUTPUT
import java.io.File
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

class DbProcessorReplicationMigration: AbstractDbProcessor()  {

    lateinit var outputDir: File

    fun FileSpec.Builder.addTrackerClass(entity: TypeElement): FileSpec.Builder {
        val simpleName = entity.simpleName.toString()
        val varPrefix = if(simpleName.count { it.isUpperCase() } > 1) {
            simpleName.filter { it.isUpperCase() }.lowercase()
        }else {
            simpleName.lowercase()
        }

        addType(TypeSpec.classBuilder("${entity.simpleName}Tracker")
            .addAnnotation(Entity::class)
            .addProperty(PropertySpec.builder("${varPrefix}Fk", LONG)
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
            FileSpec.builder(repEntity.packageName, "${repEntity.simpleName}Tracker")
                .addTrackerClass(repEntity)
                .build()
                .writeTo(outputDir)

        }


        addProperty(PropertySpec.builder("${dbTypeEl.simpleName}_ReplicationMigration",
                DoorMigrationSync::class)
            .initializer(CodeBlock.builder()
                .beginControlFlow("%T(1,2)", DoorMigrationSync::class)
                .add("val _stmtList = mutableListOf<String>()\n")
                .beginControlFlow("if(db.%M() == %T.SQLITE)", MemberName("com.ustadmobile.door.ext",
                    "dbType"), DoorDbType::class)
                .apply {
                    dbTypeEl.allDbEntities(processingEnv).forEach { entityType ->
                        if (entityType.hasAnnotation(ReplicateEntity::class.java)) {
                            addReplicateEntityChangeLogTrigger(
                                entityType, "_stmtList",
                                DoorDbType.SQLITE
                            )
                            addCreateReceiveView(entityType, "_stmtList")
                        }

                        addCreateTriggersCode(entityType, "_stmtList", DoorDbType.SQLITE)
                    }
                }
                .nextControlFlow("else")
                .apply {
                    dbTypeEl.allDbEntities(processingEnv).forEach { entityType ->
                        if (entityType.hasAnnotation(ReplicateEntity::class.java)) {
                            addReplicateEntityChangeLogTrigger(
                                entityType, "_stmtList",
                                DoorDbType.POSTGRES
                            )
                            addCreateReceiveView(entityType, "_stmtList")
                        }

                        addCreateTriggersCode(entityType, "_stmtList", DoorDbType.POSTGRES)
                    }
                }
                .endControlFlow()
                .add("db.%M(_stmtList)\n", MemberName("com.ustadmobile.door.ext", "execSqlBatch"))
                .endControlFlow()
                .build())
            .build())


        File(outputDir, "trackers.txt")
            .writeText(repEntities.joinToString(separator = ",\n") { "${it.simpleName}Tracker::class" })



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