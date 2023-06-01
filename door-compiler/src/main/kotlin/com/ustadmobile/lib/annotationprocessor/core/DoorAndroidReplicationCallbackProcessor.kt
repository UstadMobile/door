package com.ustadmobile.lib.annotationprocessor.core

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.writeTo
import com.ustadmobile.door.DoorDatabaseCallbackSync
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.DoorSqlDatabase
import com.ustadmobile.door.annotation.ReplicateEntity
import com.ustadmobile.lib.annotationprocessor.core.DoorAndroidReplicationCallbackProcessor.Companion.SUFFIX_ANDROID_REPLICATION_CALLBACK
import com.ustadmobile.lib.annotationprocessor.core.ext.*


fun FileSpec.Builder.addAndroidReplicationCallbackType(
    dbKSClassDeclaration: KSClassDeclaration
): FileSpec.Builder {
    addType(TypeSpec
        .classBuilder("${dbKSClassDeclaration.simpleName.asString()}$SUFFIX_ANDROID_REPLICATION_CALLBACK")
        .addOriginatingKsFileOrThrow(dbKSClassDeclaration.containingFile)
        .addOriginatingKSClasses(dbKSClassDeclaration.allDbEntities())
        .addOriginatingKSClasses(dbKSClassDeclaration.dbEnclosedDaos())
        .addSuperinterface(DoorDatabaseCallbackSync::class)
        .addFunction(FunSpec.builder("onCreate")
            .addParameter("db", DoorSqlDatabase::class)
            .addModifiers(KModifier.OVERRIDE)
            .addCode(
                CodeBlock.builder()
                .apply {
                    add("val _stmtList = mutableListOf<String>()\n")
                    dbKSClassDeclaration.allDbEntities().forEach { entityKSClass ->
                        if(entityKSClass.hasAnnotation(ReplicateEntity::class)) {
                            addCreateReceiveView(entityKSClass, "_stmtList")
                        }

                        addCreateTriggersCode(entityKSClass, "_stmtList", DoorDbType.SQLITE)

                        if(entityKSClass.entityHasAttachments()) {
                            addGenerateAttachmentTriggerSqlite(entityKSClass, "_stmt.executeUpdate",
                                "_stmtList")
                        }
                    }
                    beginControlFlow("_stmtList.forEach")
                    add("db.execSQL(it)\n")
                    endControlFlow()
                }
                .build())
            .build())
        .addFunction(FunSpec.builder("onOpen")
            .addParameter("db", DoorSqlDatabase::class)
            .addModifiers(KModifier.OVERRIDE)
            .build())
        .build())

    return this
}


class DoorAndroidReplicationCallbackProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if(environment.doorTarget(resolver) != DoorTarget.ANDROID)
            return emptyList()

        resolver.getDatabaseSymbolsToProcess()
                .filter { it.dbHasReplicationEntities() }
                .forEach { dbKSClassDecl ->
            FileSpec.builder(dbKSClassDecl.packageName.asString(),
                dbKSClassDecl.simpleName.asString() + SUFFIX_ANDROID_REPLICATION_CALLBACK)
                .addAndroidReplicationCallbackType(dbKSClassDecl)
                .build()
                .writeTo(environment.codeGenerator, false)
        }


        return emptyList()
    }

    companion object {

        /**
         *
         */
        const val SUFFIX_ANDROID_REPLICATION_CALLBACK = "_AndroidReplicationCallback"

    }
}