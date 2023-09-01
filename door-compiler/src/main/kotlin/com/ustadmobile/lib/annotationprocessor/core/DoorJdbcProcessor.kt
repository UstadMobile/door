package com.ustadmobile.lib.annotationprocessor.core

import com.ustadmobile.door.room.*
import androidx.room.*
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import com.ustadmobile.door.*
import kotlin.reflect.jvm.internal.impl.name.FqName
import kotlin.reflect.jvm.internal.impl.builtins.jvm.JavaToKotlinClassMap
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.annotation.*
import com.ustadmobile.door.attachments.AttachmentFilter
import com.ustadmobile.door.ext.DoorDatabaseMetadata
import com.ustadmobile.door.ext.DoorDatabaseMetadata.Companion.SUFFIX_DOOR_METADATA
import com.ustadmobile.door.ext.minifySql
import com.ustadmobile.door.paging.DoorLimitOffsetPagingSource
import com.ustadmobile.door.replication.ReplicationEntityMetaData
import com.ustadmobile.door.replication.ReplicationFieldMetaData
import com.ustadmobile.door.room.RoomJdbcImpl
import com.ustadmobile.door.util.DeleteZombieAttachmentsListener
import kotlin.reflect.KClass
import com.ustadmobile.door.util.NodeIdAuthCache
import com.ustadmobile.lib.annotationprocessor.core.AbstractDbProcessor.Companion.MEMBERNAME_EXEC_UPDATE_ASYNC
import com.ustadmobile.lib.annotationprocessor.core.DoorJdbcProcessor.Companion.SUFFIX_JDBC_KT2
import com.ustadmobile.lib.annotationprocessor.core.DoorJdbcProcessor.Companion.SUFFIX_JS_IMPLEMENTATION_CLASSES
import com.ustadmobile.lib.annotationprocessor.core.DoorRepositoryProcessor.Companion.SUFFIX_REPOSITORY2
import com.ustadmobile.lib.annotationprocessor.core.ext.*
import io.github.aakira.napier.Napier
import kotlinx.coroutines.DelicateCoroutinesApi
import java.io.File
import kotlin.math.abs

val QUERY_SINGULAR_TYPES = listOf(INT, LONG, SHORT, BYTE, BOOLEAN, FLOAT, DOUBLE,
        String::class.asTypeName(), String::class.asTypeName().copy(nullable = true))

/**
 * Given an input result type (e.g. Entity, Entity[], List<Entity>, String, int, etc), figure out
 * what the actual entity type is
 */
fun resolveEntityFromResultType(type: TypeName) =
        if(type is ParameterizedTypeName && type.rawType.canonicalName == "kotlin.collections.List") {
            val typeArg = type.typeArguments[0]
            if(typeArg is WildcardTypeName) {
                typeArg.outTypes[0]
            }else {
                typeArg
            }
        }else {
            type
        }


//As per https://github.com/square/kotlinpoet/issues/236
internal fun TypeName.javaToKotlinType(): TypeName = if (this is ParameterizedTypeName) {
    (rawType.javaToKotlinType() as ClassName).parameterizedBy(
            *typeArguments.map { it.javaToKotlinType() }.toTypedArray()
    )
} else {
    val className = JavaToKotlinClassMap.INSTANCE
            .mapJavaToKotlin(FqName(toString()))?.asSingleFqName()?.asString()
    if (className == null) this
    else ClassName.bestGuess(className)
}

/**
 * Remove <out T>
 */
fun removeTypeProjection(typeName: TypeName) =
    if(typeName is ParameterizedTypeName && typeName.typeArguments[0] is WildcardTypeName) {
        typeName.rawType.parameterizedBy((typeName.typeArguments[0] as WildcardTypeName).outTypes[0]).javaToKotlinType()
    }else {
        typeName.javaToKotlinType()
    }


fun makeInsertAdapterMethodName(
    paramType: KSType,
    returnType: KSType?,
    async: Boolean,
    resolver: Resolver,
): String {
    var methodName = "insert"
    if(paramType.isListOrArrayType(resolver)) {
        methodName += "List"
        if(returnType != null && returnType != resolver.builtIns.unitType)
            methodName += "AndReturnIds"
    }else {
        if(returnType != null && returnType != resolver.builtIns.unitType) {
            methodName += "AndReturnId"
        }
    }

    if(async)
        methodName += "Async"

    return methodName
}

/**
 * Generate the DatabaseMetadata object for the given database.
 */
private fun FileSpec.Builder.addDatabaseMetadataType(
    dbKSClass: KSClassDeclaration,
): FileSpec.Builder {
    addType(TypeSpec.classBuilder("${dbKSClass.simpleName.asString()}$SUFFIX_DOOR_METADATA")
        .superclass(DoorDatabaseMetadata::class.asClassName().parameterizedBy(dbKSClass.toClassName()))
        .addOriginatingKsFileOrThrow(dbKSClass.containingFile)
        .addOriginatingKSClasses(dbKSClass.allDbEntities())
        .addProperty(PropertySpec.builder("dbClass", KClass::class.asClassName().parameterizedBy(dbKSClass.toClassName()))
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                .addCode("return %T::class\n", dbKSClass.toClassName())
                .build())
            .build())
        .addProperty(PropertySpec.builder("hasReadOnlyWrapper", Boolean::class)
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                .addCode("return %L\n", dbKSClass.dbHasReplicationEntities())
                .build())
            .build())
        .addProperty(PropertySpec.builder("hasAttachments", Boolean::class)
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                .addCode("return %L\n", dbKSClass.allDbEntities().any { it.entityHasAttachments() })
                .build())
            .build())
        .addProperty(PropertySpec.builder("syncableTableIdMap", Map::class.parameterizedBy(String::class, Int::class))
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                .addCode("return TABLE_ID_MAP\n", dbKSClass.toClassNameWithSuffix(SUFFIX_REPOSITORY2))
                .build())
            .build())
        .addProperty(PropertySpec.builder("version", INT)
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                .addCode("return ${dbKSClass.getAnnotation(DoorDatabase::class)?.version ?: -1}\n")
                .build())
            .build())
        .addProperty(PropertySpec.builder("allTables", List::class.parameterizedBy(String::class))
            .addModifiers(KModifier.OVERRIDE)
            .initializer(CodeBlock.builder()
                .add("listOf(%L)\n",
                    dbKSClass.allDbEntities()
                        .joinToString(prefix = "\"", postfix = "\"", separator = "\", \"") { it.simpleName.asString() })
                .build())
            .build())
        .addProperty(PropertySpec.builder("replicateEntities", Map::class.parameterizedBy(Int::class, ReplicationEntityMetaData::class))
            .addModifiers(KModifier.OVERRIDE)
            .delegate(
                CodeBlock.builder()
                    .beginControlFlow("lazy(%T.NONE)", LazyThreadSafetyMode::class)
                    .add("mapOf<%T, %T>(\n", INT, ReplicationEntityMetaData::class)
                    .apply {
                        dbKSClass.allDbEntities()
                            .filter { it.hasAnnotation(ReplicateEntity::class)}.forEach { replicateEntity ->
                                add("%L to ", replicateEntity.getAnnotation(ReplicateEntity::class)?.tableId ?: -1)
                                addReplicateEntityMetaDataCode(replicateEntity)
                                add(",\n")
                            }
                    }
                    .add(")\n")
                    .endControlFlow()
                    .build())
            .build())
        .addType(TypeSpec.companionObjectBuilder()
            .addTableIdMapProperty()
            .build())
        .build())

    return this
}

private fun FileSpec.Builder.addJsImplementationsClassesObject(
    dbKSClass: KSClassDeclaration,
) : FileSpec.Builder {
    val jsImplClassName = ClassName("com.ustadmobile.door.util", "DoorJsImplClasses")
    addType(TypeSpec.objectBuilder(dbKSClass.simpleName.asString() + SUFFIX_JS_IMPLEMENTATION_CLASSES)
        .addOriginatingKSClass(dbKSClass)
        .superclass(jsImplClassName.parameterizedBy(dbKSClass.toClassName()))
        .addProperty(PropertySpec.builder("dbKClass",
            KClass::class.asClassName().parameterizedBy(dbKSClass.toClassName()))
            .addModifiers(KModifier.OVERRIDE)
            .initializer("%T::class", dbKSClass.toClassName())
            .build())
        .addProperty(PropertySpec.builder("dbImplKClass", KClass::class.asTypeName().parameterizedBy(STAR))
            .addModifiers(KModifier.OVERRIDE)
            .initializer("%T::class", dbKSClass.toClassNameWithSuffix(SUFFIX_JDBC_KT2))
            .build())
        .addProperty(PropertySpec.builder("replicateWrapperImplClass", KClass::class.asTypeName()
            .parameterizedBy(STAR).copy(nullable = true))
            .addModifiers(KModifier.OVERRIDE)
            .initializer(CodeBlock.builder()
                .apply {
                    if(dbKSClass.dbHasReplicationEntities()) {
                        add("%T::class", dbKSClass.toClassNameWithSuffix(DoorDatabaseWrapper.SUFFIX))
                    }else {
                        add("null")
                    }
                }
                .build())
            .build())
        .addProperty(PropertySpec.builder("repositoryImplClass",
            KClass::class.asTypeName().parameterizedBy(STAR).copy(nullable = true))
            .addModifiers(KModifier.OVERRIDE)
            .initializer(CodeBlock.builder()
                .apply {
                    if(dbKSClass.dbEnclosedDaos().any { it.hasAnnotation(Repository::class) }) {
                        add("%T::class", dbKSClass.toClassNameWithSuffix(SUFFIX_REPOSITORY2))
                    }else {
                        add("null")
                    }
                }
                .build())
            .build())
        .addProperty(PropertySpec.builder("metadata",
            DoorDatabaseMetadata::class.asClassName().parameterizedBy(dbKSClass.toClassName()))
            .addModifiers(KModifier.OVERRIDE)
            .initializer("%T()", dbKSClass.toClassNameWithSuffix(SUFFIX_DOOR_METADATA))
            .build())
        .build())


    return this
}

private fun CodeBlock.Builder.addReplicateEntityMetaDataCode(
    entity: KSClassDeclaration,
): CodeBlock.Builder {

    fun CodeBlock.Builder.addFieldsCodeBlock(typeEl: KSClassDeclaration) : CodeBlock.Builder{
        add("listOf(")
        typeEl.entityProps().forEach {
            add("%T(%S, %L),", ReplicationFieldMetaData::class, it.simpleName.asString(),
                it.type.resolve().toTypeName().toSqlTypesInt())
        }
        add(")")
        return this
    }

    val repEntityAnnotation = entity.getAnnotation(ReplicateEntity::class)
    add("%T(", ReplicationEntityMetaData::class)
    add("%L, ", repEntityAnnotation?.tableId)
    add("%L, ", repEntityAnnotation?.priority)
    add("%S, ", entity.entityTableName)
    add("%S, ", entity.replicationEntityReceiveViewName)
    add("%S, ", entity.entityPrimaryKeyProps.first().simpleName.asString())
    add("%S, ", entity.firstPropWithAnnotation(ReplicationVersionId::class).simpleName.asString())
    addFieldsCodeBlock(entity).add(",\n")
    add(entity.firstPropNameWithAnnotationOrNull(AttachmentUri::class)).add(",\n")
    add(entity.firstPropNameWithAnnotationOrNull(AttachmentMd5::class)).add(",\n")
    add(entity.firstPropNameWithAnnotationOrNull(AttachmentSize::class)).add(",\n")
    add("%L", repEntityAnnotation?.batchSize ?: 1000)
    add(")")
    return this
}


val SQL_COMPONENT_TYPE_MAP = mapOf(LONG to "BIGINT",
        INT to "INTEGER",
        SHORT to "SMALLINT",
        BOOLEAN to "BOOLEAN",
        FLOAT to "FLOAT",
        DOUBLE to "DOUBLE",
        String::class.asClassName() to "TEXT")

fun sqlArrayComponentTypeOf(typeName: TypeName): String {
    if(typeName is ParameterizedTypeName) {
        return SQL_COMPONENT_TYPE_MAP[typeName.typeArguments[0]]!!
    }

    return "UNKNOWN"
}


val PRIMITIVE = listOf(INT, LONG, BOOLEAN, SHORT, BYTE, FLOAT, DOUBLE)

@OptIn(DelicateCoroutinesApi::class)
fun FileSpec.Builder.addJdbcDbImplType(
    dbKSClass: KSClassDeclaration,
    target: DoorTarget,
    resolver: Resolver,
) : FileSpec.Builder {
    addImport("com.ustadmobile.door.util", "systemTimeInMillis")
    addType(TypeSpec.classBuilder(dbKSClass.toClassNameWithSuffix(SUFFIX_JDBC_KT2))
        .addOriginatingKSClasses(listOf(dbKSClass))
        .superclass(dbKSClass.toClassName())
        .addSuperinterface(DoorDatabaseJdbc::class)
        .addSuperinterface(RoomJdbcImpl::class)
        .primaryConstructor(FunSpec.constructorBuilder()
            .addParameter("doorJdbcSourceDatabase", RoomDatabase::class.asTypeName().copy(nullable = true))
            .addParameter(ParameterSpec("dataSource", AbstractDbProcessor.CLASSNAME_DATASOURCE))
            .addParameter("dbName", String::class)
            .applyIf(target == DoorTarget.JVM) {
                addParameter("attachmentDir", File::class.asTypeName().copy(nullable = true))
            }
            .addParameter("realAttachmentFilters", List::class.parameterizedBy(AttachmentFilter::class))
            .addParameter("jdbcQueryTimeout", Int::class)
            .addParameter("jdbcDbType", Int::class)
            .build())
        .addDbVersionProperty(dbKSClass)
        .addProperty(PropertySpec.builder("dataSource", AbstractDbProcessor.CLASSNAME_DATASOURCE, KModifier.OVERRIDE)
            .initializer("dataSource")
            .build())
        .addProperty(PropertySpec.builder("jdbcImplHelper", RoomDatabaseJdbcImplHelper::class, KModifier.OVERRIDE)
            .initializer("%T(dataSource, this, this::class.%M().allTables, %T(*this::class.%M().allTables.toTypedArray()), jdbcDbType)\n",
                RoomDatabaseJdbcImplHelper::class,
                MemberName("com.ustadmobile.door.ext", "doorDatabaseMetadata"),
                InvalidationTracker::class,
                MemberName("com.ustadmobile.door.ext", "doorDatabaseMetadata"),
                )
            .build())
        .applyIf(target == DoorTarget.JVM) {
            addProperty(PropertySpec.builder("realAttachmentStorageUri",
                DoorUri::class.asTypeName().copy(nullable = true))
                .addModifiers(KModifier.OVERRIDE)
                .initializer("attachmentDir?.%M()\n",
                    MemberName("com.ustadmobile.door.ext", "toDoorUri"))
                .build())
        }
        .applyIf(target == DoorTarget.JS) {
            addProperty(PropertySpec.builder("realAttachmentStorageUri",
                DoorUri::class.asTypeName().copy(nullable = true))
                .addModifiers(KModifier.OVERRIDE)
                .initializer("null")
                .build())
        }
        .addProperty(PropertySpec.builder("realAttachmentFilters",
            List::class.parameterizedBy(AttachmentFilter::class))
            .addModifiers(KModifier.OVERRIDE)
            .initializer("realAttachmentFilters")
            .build())
        .addProperty(PropertySpec.builder("doorJdbcSourceDatabase",
            RoomDatabase::class.asTypeName().copy(nullable = true), KModifier.OVERRIDE)
            .initializer("doorJdbcSourceDatabase")
            .build())
        .addProperty(PropertySpec.builder("dbName", String::class, KModifier.OVERRIDE)
            .initializer("dbName")
            .build())
        .addProperty(PropertySpec.builder("jdbcQueryTimeout", Int::class, KModifier.OVERRIDE)
            .initializer("jdbcQueryTimeout")
            .build())
        .addProperty(PropertySpec.builder("_deleteZombieAttachmentsListener",
            DeleteZombieAttachmentsListener::class.asTypeName().copy(nullable = true))
            .addModifiers(KModifier.PRIVATE)
            .initializer("if(this == %M) { %T(this) } else { null }",
                MemberName("com.ustadmobile.door.ext", "rootDatabase"),
                DeleteZombieAttachmentsListener::class)
            .build())
        .addProperty(PropertySpec.builder("realIncomingReplicationListenerHelper",
            IncomingReplicationListenerHelper::class)
            .addModifiers(KModifier.OVERRIDE)
            .initializer(CodeBlock.of("%T()\n", IncomingReplicationListenerHelper::class))
            .build())
        .addProperty(PropertySpec.builder("realNodeIdAuthCache", NodeIdAuthCache::class,
            KModifier.OVERRIDE)
            .delegate(CodeBlock.builder()
                .beginControlFlow("lazy")
                .beginControlFlow("if(this == %M)",
                    MemberName("com.ustadmobile.door.ext", "rootDatabase"))
                .add("val nodeIdAuthCache = %T(this)\n", NodeIdAuthCache::class)
                .add("nodeIdAuthCache\n")
                .nextControlFlow("else")
                .add("rootDatabase.%M\n", MemberName("com.ustadmobile.door.ext", "nodeIdAuthCache"))
                .endControlFlow()
                .endControlFlow()
                .build())
            .build())
        .addProperty(PropertySpec.builder("realPrimaryKeyManager", DoorPrimaryKeyManager::class,
            KModifier.OVERRIDE)
            .delegate(CodeBlock.builder()
                .beginControlFlow("lazy")
                .add("%T(%T::class.%M().replicateEntities.keys)\n", DoorPrimaryKeyManager::class,
                    dbKSClass.toClassName(), MemberName("com.ustadmobile.door.ext", "doorDatabaseMetadata"))
                .endControlFlow()
                .build())
            .build())
        .addCreateAllTablesFunction(dbKSClass, resolver)
        .addClearAllTablesFunction(dbKSClass, target)
        .addProperty(PropertySpec.builder("invalidationTracker", InvalidationTracker::class,
                    KModifier.OVERRIDE)
            .getter(FunSpec
                .getterBuilder()
                .addCode("return jdbcImplHelper.invalidationTracker\n")
                .build()
            )
            .build())
        .apply {
            dbKSClass.allDbClassDaoGetters().forEach { daoGetterOrProp ->
                val daoKSClass = daoGetterOrProp.propertyOrReturnType()?.resolve()?.declaration as? KSClassDeclaration
                    ?: return@forEach
                val daoImplClassName = daoKSClass.toClassNameWithSuffix(SUFFIX_JDBC_KT2)
                addProperty(PropertySpec.builder("_${daoKSClass.simpleName.asString()}",
                    daoImplClassName).delegate("lazy·{·%T(this)·}", daoImplClassName).build())
                addDaoPropOrGetterOverride(daoGetterOrProp, CodeBlock.of("return _${daoKSClass.simpleName.asString()}"))
            }
        }
        .build())
    return this
}


/**
 * Add a ReceiveView for the given EntityTypeElement.
 */
internal fun CodeBlock.Builder.addCreateReceiveView(
    entityKSClass: KSClassDeclaration,
    sqlListVar: String
): CodeBlock.Builder {
    val receiveViewAnn = entityKSClass.getAnnotation(ReplicateReceiveView::class)
    val viewName = receiveViewAnn?.name ?: "${entityKSClass.entityTableName}${AbstractDbProcessor.SUFFIX_DEFAULT_RECEIVEVIEW}"
    val sql = receiveViewAnn?.value ?: """
            SELECT ${entityKSClass.entityTableName}.*, 0 AS fromNodeId
              FROM ${entityKSClass.entityTableName}
        """.minifySql()
    add("$sqlListVar += %S\n", "CREATE VIEW $viewName AS $sql")
    return this
}

fun TypeSpec.Builder.addCreateAllTablesFunction(
    dbKSClass: KSClassDeclaration,
    resolver: Resolver,
) : TypeSpec.Builder {
    val initDbVersion = dbKSClass.getAnnotation(DoorDatabase::class)?.version ?: -1
    Napier.d("Door Wrapper: add create all tables function for ${dbKSClass.simpleName.asString()}")
    addFunction(FunSpec.builder("createAllTables")
        .addModifiers(KModifier.OVERRIDE)
        .returns(List::class.parameterizedBy(String::class))
        .addCode(CodeBlock.builder()
            .add("val _stmtList = %M<String>()\n", AbstractDbProcessor.MEMBERNAME_MUTABLE_LINKEDLISTOF)
            .beginControlFlow("when(jdbcImplHelper.dbType)")
            .apply {
                for(dbProductType in DoorDbType.SUPPORTED_TYPES) {
                    val dbTypeName = DoorDbType.PRODUCT_INT_TO_NAME_MAP[dbProductType] as String
                    beginControlFlow("$dbProductType -> ")
                    add("// - create for this $dbTypeName \n")
                    add("_stmtList += \"CREATE·TABLE·IF·NOT·EXISTS·${DoorConstants.DBINFO_TABLENAME}" +
                            "·(dbVersion·int·primary·key,·dbHash·varchar(255))\"\n")
                    add(" _stmtList += \"INSERT·INTO·${DoorConstants.DBINFO_TABLENAME}·" +
                            "VALUES·($initDbVersion,·'')\"\n")

                    //All entities MUST be created first, triggers etc. can only be created after all entities exist
                    val createEntitiesCodeBlock = CodeBlock.builder()
                    val createTriggersAndViewsBlock = CodeBlock.builder()

                    dbKSClass.allDbEntities().forEach { entityKSClass ->
                        val fieldListStr = entityKSClass.entityProps().joinToString { it.simpleName.asString() }
                        createEntitiesCodeBlock.add("//Begin: Create table ${entityKSClass.entityTableName} for $dbTypeName\n")
                            .add("/* START MIGRATION: \n")
                            .add("_stmt.executeUpdate(%S)\n",
                                "ALTER TABLE ${entityKSClass.entityTableName} RENAME to ${entityKSClass.entityTableName}_OLD")
                            .add("END MIGRATION */\n")
                            .addCreateTableCode(entityKSClass, "_stmt.executeUpdate", dbProductType,
                                sqlListVar = "_stmtList", resolver = resolver)
                            .add("/* START MIGRATION: \n")
                            .add("_stmt.executeUpdate(%S)\n", "INSERT INTO ${entityKSClass.entityTableName} ($fieldListStr) " +
                                    "SELECT $fieldListStr FROM ${entityKSClass.entityTableName}_OLD")
                            .add("_stmt.executeUpdate(%S)\n", "DROP TABLE ${entityKSClass.entityTableName}_OLD")
                            .add("END MIGRATION*/\n")


                        if(entityKSClass.hasAnnotation(ReplicateEntity::class)) {
                            createTriggersAndViewsBlock
                                .addCreateReceiveView(entityKSClass, "_stmtList")
                        }

                        createTriggersAndViewsBlock.addCreateTriggersCode(entityKSClass, "_stmtList",
                            dbProductType)


                        if(entityKSClass.entityHasAttachments()) {
                            if(dbProductType == DoorDbType.SQLITE) {
                                createTriggersAndViewsBlock.addGenerateAttachmentTriggerSqlite(entityKSClass,
                                    "_stmt.executeUpdate", "_stmtList")
                            }else {
                                createTriggersAndViewsBlock.addGenerateAttachmentTriggerPostgres(
                                    entityKSClass, "_stmtList")
                            }
                        }

                        createEntitiesCodeBlock.add("//End: Create table ${entityKSClass.entityTableName} for $dbTypeName\n\n")
                    }
                    add(createEntitiesCodeBlock.build())
                    add(createTriggersAndViewsBlock.build())

                    endControlFlow()
                }
            }
            .endControlFlow()
            .add("return _stmtList\n")
            .build())
        .build())

    Napier.d("Door Wrapper: done with tables function for ${dbKSClass.simpleName}")
    return this
}


internal fun CodeBlock.Builder.addCreateTriggersCode(
    entityKSClass: KSClassDeclaration,
    stmtListVar: String,
    dbProductType: Int
): CodeBlock.Builder {
    Napier.d("Door Wrapper: addCreateTriggersCode ${entityKSClass.simpleName.asString()}")
    entityKSClass.getKSAnnotationByType(Triggers::class)?.toTriggers()?.value?.forEach { trigger ->
        trigger.toSql(entityKSClass, dbProductType).forEach { sqlStr ->
            add("$stmtListVar += %S\n", sqlStr)
        }
    }

    return this
}

private fun TypeSpec.Builder.addClearAllTablesFunction(
    dbKSClass: KSClassDeclaration,
    target: DoorTarget
) : TypeSpec.Builder {

    addFunction(FunSpec.builder("makeClearAllTablesSql")
        .returns(List::class.parameterizedBy(String::class))
        .addCode("val _stmtList = mutableListOf<String>()\n")
        .apply {
            dbKSClass.allDbEntities().forEach {  entityType ->
                addCode("_stmtList += %S\n", "DELETE FROM ${entityType.entityTableName}")
            }
        }
        .addCode("return _stmtList\n")
        .build())
    addFunction(FunSpec.builder("clearAllTables")
        .addModifiers(KModifier.OVERRIDE)
        .applyIf(target == DoorTarget.JVM) {
            addCode("%M(*makeClearAllTablesSql().%M())\n",
                MemberName("com.ustadmobile.door.ext", "execSqlBatch"),
                MemberName("kotlin.collections", "toTypedArray"))
        }
        .applyIf(target == DoorTarget.JS) {
            addCode("throw %T(%S)\n", AbstractDbProcessor.CLASSNAME_ILLEGALSTATEEXCEPTION,
                "clearAllTables synchronous not supported on Javascript")
        }
        .build())

    if(target == DoorTarget.JS) {
        addFunction(FunSpec.builder("clearAllTablesAsync")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addCode("execSQLBatchAsyncJs(*makeClearAllTablesSql().%M())\n",
                MemberName("kotlin.collections", "toTypedArray"))
            .build())
    }
    return this
}

fun CodeBlock.Builder.addDaoJdbcInsertCodeBlock(
    daoKSFun: KSFunctionDeclaration,
    daoKSClass: KSClassDeclaration,
    daoTypeBuilder: TypeSpec.Builder,
    resolver: Resolver,
    target: DoorTarget,
): CodeBlock.Builder {
    val daoFunction = daoKSFun.asMemberOf(daoKSClass.asType(emptyList()))
    val paramType = daoFunction.parameterTypes.first()
        ?: throw IllegalArgumentException("${daoKSFun.simpleName.asString()} has no param type")
    val entityKSClass = daoFunction.firstParamEntityType(resolver).declaration as KSClassDeclaration
    val pgOnConflict = daoKSFun.getAnnotation(PgOnConflict::class)?.value
    val pgOnConflictHash = pgOnConflict?.hashCode()?.let { abs(it) }?.toString() ?: ""
    val upsertMode = daoKSFun.getAnnotation(Insert::class)?.onConflict == OnConflictStrategy.REPLACE
    val entityInserterPropName = "_insertAdapter${entityKSClass.simpleName.asString()}_${if(upsertMode) "upsert" else ""}$pgOnConflictHash"
    if(!daoTypeBuilder.propertySpecs.any { it.name == entityInserterPropName }) {
        daoTypeBuilder.addDaoJdbcEntityInsertAdapter(entityKSClass, entityInserterPropName, upsertMode, target,
            pgOnConflict, resolver)
    }

    if(daoFunction.hasReturnType(resolver)) {
        add("val _retVal = ")
    }


    val insertMethodName = makeInsertAdapterMethodName(paramType, daoFunction.returnType,
        daoKSFun.modifiers.contains(Modifier.SUSPEND), resolver)
    add("$entityInserterPropName.$insertMethodName(${daoKSFun.parameters.first().name?.asString()})")

    val returnType = daoKSFun.returnType?.resolve()
    if(daoFunction.hasReturnType(resolver)) {
        if(returnType?.isListOrArrayType(resolver) == true
                && returnType.unwrapComponentTypeIfListOrArray(resolver) == resolver.builtIns.intType) {
            add(".map { it.toInt() }")
        }else if(returnType == resolver.builtIns.intType) {
            add(".toInt()")
        }
    }

    add("\n")

    if(daoFunction.hasReturnType(resolver)) {
        add("return _retVal")

        if(returnType?.isArrayType() == true) {
            add(".toTypedArray()")
        }else if(returnType?.isLongArray() == true) {
            add(".toLongArray()")
        }else if(returnType?.isIntArray() == true) {
            add(".toIntArray()")
        }
    }

    return this
}

/**
 * Genetes an EntityInsertAdapter for use with JDBC code
 */
fun TypeSpec.Builder.addDaoJdbcEntityInsertAdapter(
    entityKSClass: KSClassDeclaration,
    propertyName: String,
    upsertMode: Boolean,
    target: DoorTarget,
    pgOnConflict: String? = null,
    resolver: Resolver,
) : TypeSpec.Builder {
    val entityFields = entityKSClass.entityProps(false)
    val entityClassName = entityKSClass.toClassName()
    val pkFields = entityKSClass.entityPrimaryKeyProps
    val insertAdapterSpec = TypeSpec.anonymousClassBuilder()
        .superclass(EntityInsertionAdapter::class.asClassName().parameterizedBy(entityKSClass.toClassName()))
        .addSuperclassConstructorParameter("_db")
        .addFunction(FunSpec.builder("makeSql")
            .returns(String::class)
            .addParameter("returnsId", BOOLEAN)
            .addModifiers(KModifier.OVERRIDE)
            .addCode(CodeBlock.builder()
                .apply {
                    if(target.supportedDbs.size != 1) {
                        beginControlFlow("return when(dbType)")
                    }else{
                        add("return ")
                    }
                }
                .applyIf(target.supportedDbs.size != 1 && DoorDbType.SQLITE in target.supportedDbs) {
                    beginControlFlow("%T.SQLITE ->", DoorDbType::class)
                }
                .applyIf(DoorDbType.SQLITE in target.supportedDbs) {
                    var insertSql = "INSERT "
                    if(upsertMode)
                        insertSql += "OR REPLACE "
                    insertSql += "INTO ${entityKSClass.entityTableName} (${entityFields.joinToString { it.entityPropColumnName }}) "
                    insertSql += "VALUES(${entityFields.joinToString { "?" }})"
                    add("%S", insertSql)
                }
                .applyIf(target.supportedDbs.size != 1 && DoorDbType.SQLITE in target.supportedDbs) {
                    add("\n")
                    endControlFlow()
                }
                .applyIf(target.supportedDbs.size != 1 && DoorDbType.POSTGRES in target.supportedDbs) {
                    beginControlFlow("%T.POSTGRES -> ", DoorDbType::class)
                }
                .applyIf(DoorDbType.POSTGRES in target.supportedDbs) {
                    var insertSql = "INSERT "
                    insertSql += "INTO ${entityKSClass.entityTableName} (${entityFields.joinToString { it.entityPropColumnName }}) "
                    insertSql += "VALUES("
                    insertSql += entityFields.joinToString { prop ->
                        if(prop.isEntityAutoGeneratePrimaryKey) {
                            "COALESCE(?,nextval('${entityKSClass.entityTableName}_${prop.entityPropColumnName}_seq'))"
                        }else {
                            "?"
                        }
                    }
                    insertSql += ")"

                    when {
                        pgOnConflict != null -> {
                            insertSql += pgOnConflict.replace(" ", " ")
                        }
                        upsertMode -> {
                            insertSql += " ON CONFLICT (${pkFields.joinToString {it.entityPropColumnName } }) DO UPDATE SET "
                            insertSql += entityFields.filter { it !in pkFields }
                                .joinToString(separator = ",") {
                                    "${it.entityPropColumnName} = excluded.${it.entityPropColumnName}"
                                }
                        }
                    }
                    add("%S·+·if(returnsId)·{·%S·}·else·\"\"·", insertSql, " RETURNING ${pkFields.first().entityPropColumnName}")
                }
                .applyIf(target.supportedDbs.size != 1 && DoorDbType.POSTGRES in target.supportedDbs) {
                    add("\n")
                    endControlFlow()
                }
                .apply {
                    if(target.supportedDbs.size != 1) {
                        beginControlFlow("else ->")
                        add("throw %T(%S)\n", AbstractDbProcessor.CLASSNAME_ILLEGALARGUMENTEXCEPTION, "Unsupported db type")
                        endControlFlow()
                        endControlFlow()
                    }else {
                        add("\n")
                    }
                }
                .build())
            .build())
        .addFunction(FunSpec.builder("bindPreparedStmtToEntity")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("stmt", AbstractDbProcessor.CLASSNAME_PREPARED_STATEMENT)
            .addParameter("entity", entityClassName)
            .addCode(CodeBlock.builder()
                .apply {
                    entityFields.forEachIndexed { index, field ->
                        if(field.isEntityAutoGeneratePrimaryKey) {
                            beginControlFlow("if(entity.${field.simpleName.asString()} == %L)",
                                field.type.resolve().defaultTypeValueCode(resolver))
                            add("stmt.setObject(%L, null)\n", index + 1)
                            nextControlFlow("else")
                        }
                        add("stmt.")
                        addPreparedStatementSetCall(field.type.resolve(), resolver)
                        add("(%L, entity.%L)\n", index + 1, field.simpleName.asString())
                        if(field.isEntityAutoGeneratePrimaryKey) {
                            endControlFlow()
                        }
                    }
                }
                .build())
            .build())

    addProperty(PropertySpec.builder(propertyName,
        EntityInsertionAdapter::class.asClassName().parameterizedBy(entityClassName))
        .initializer("%L", insertAdapterSpec.build())
        .build())

    return this
}


fun FileSpec.Builder.addDaoJdbcImplType(
    daoKSClass: KSClassDeclaration,
    resolver: Resolver,
    environment: SymbolProcessorEnvironment,
    target: DoorTarget,
) : FileSpec.Builder{
    Napier.d("DbProcessorJdbcKotlin: addDaoJdbcImplType: start ${daoKSClass.simpleName.asString()}")
    val allFunctions = daoKSClass.getAllFunctions()
    addImport("com.ustadmobile.door", "DoorDbType")
    addType(TypeSpec.classBuilder(daoKSClass.toClassNameWithSuffix(SUFFIX_JDBC_KT2))
        .addOriginatingKSClass(daoKSClass)
        .addOriginatingKSClasses(daoKSClass.allDaoEntities(resolver))
        .primaryConstructor(FunSpec.constructorBuilder().addParameter("_db",
            RoomDatabase::class).build())
        .addProperty(PropertySpec.builder("_db", RoomDatabase::class).initializer("_db").build())
        .addSuperClassOrInterface(daoKSClass)
        .apply {
            allFunctions.filter { it.hasAnnotation(Insert::class) }.forEach { daoFun ->
                addDaoInsertFunction(daoFun, daoKSClass, resolver, target, environment.logger)
            }
            allFunctions.filter { it.hasAnnotation(Update::class) }.forEach { daoFun ->
                addDaoUpdateFunction(daoFun, daoKSClass, resolver, environment.logger)
            }
            allFunctions.filter { it.hasAnnotation(Delete::class) }.forEach { daoFun ->
                addDaoDeleteFunction(daoFun, daoKSClass, resolver, environment.logger)
            }
            allFunctions.filter { it.hasAnnotation(Query::class) || it.hasAnnotation(RawQuery::class) }.forEach { daoFun ->
                addDaoQueryFunction(daoFun, daoKSClass, resolver, environment, environment.logger)
            }
        }
        .build())

    Napier.d("DbProcessorJdbcKotlin: addDaoJdbcImplType: finish ${daoKSClass.simpleName.asString()}")
    return this
}

fun TypeSpec.Builder.addDaoInsertFunction(
    daoFun: KSFunctionDeclaration,
    daoKSClass: KSClassDeclaration,
    resolver: Resolver,
    target: DoorTarget,
    logger: KSPLogger,
): TypeSpec.Builder {
    Napier.d("Start add dao insert function: ${daoFun.simpleName.asString()} on ${daoKSClass.simpleName.asString()}")

    addFunction(daoFun.toFunSpecBuilder(resolver, daoKSClass.asType(emptyList()), logger)
        .removeAbstractModifier()
        .removeAnnotations()
        .addModifiers(KModifier.OVERRIDE)
        .addCode(CodeBlock.builder()
            .addDaoJdbcInsertCodeBlock(daoFun, daoKSClass, this, resolver, target)
            .build())
        .build())
    Napier.d("Finish dao insert function: ${daoFun.simpleName.asString()} on ${daoKSClass.simpleName.asString()}")
    return this
}

fun TypeSpec.Builder.addDaoUpdateFunction(
    daoFunDecl: KSFunctionDeclaration,
    daoKSClass: KSClassDeclaration,
    resolver: Resolver,
    logger: KSPLogger
) : TypeSpec.Builder {
    val funLogName = "${daoKSClass.simpleName.asString()}#${daoFunDecl.simpleName.asString()}"
    Napier.d("DbProcessorJdbcKotlin: addDaoUpdateFunction: start $funLogName")
    val funSpec = daoFunDecl.toFunSpecBuilder(resolver, daoKSClass.asType(emptyList()), logger)

    val daoFun = daoFunDecl.asMemberOf(daoKSClass.asType(emptyList()))
    val entityType = daoFun.parameterTypes.firstOrNull()
        ?.unwrapComponentTypeIfListOrArray(resolver) ?: throw IllegalArgumentException("$funLogName cannot find param type")
    val entityKSClass = entityType.declaration as KSClassDeclaration
    val pkProps = entityKSClass.entityPrimaryKeyProps
    val nonPkFields = entityKSClass.entityProps(false).filter {
        it !in pkProps
    }
    val sqlSetPart = nonPkFields.joinToString { "${it.simpleName.asString()} = ?" }
    val sqlStmt  = "UPDATE ${entityKSClass.entityTableName} SET $sqlSetPart " +
            "WHERE ${pkProps.joinToString(separator = " AND ") { "${it.simpleName.asString()} = ?" }}"
    val firstParam = funSpec.parameters.first()
    var entityVarName = firstParam.name

    addFunction(funSpec
        .removeAnnotations()
        .removeAbstractModifier()
        .addModifiers(KModifier.OVERRIDE)
        .addCode(CodeBlock.builder()
            .applyIf(daoFunDecl.hasReturnType(resolver)) {
                add("var _result = %L\n", daoFun.returnType?.defaultTypeValueCode(resolver))
            }
            .add("val _sql = %S\n", sqlStmt)
            .beginControlFlow("_db.%M(_sql)",
                AbstractDbProcessor.prepareAndUseStatmentMemberName(daoFunDecl.isSuspended))
            .add(" _stmt ->\n")
            .applyIf(firstParam.type.isListOrArray()) {
                add("_stmt.getConnection().setAutoCommit(false)\n")
                    .beginControlFlow("for(_entity in ${firstParam.name})")
                entityVarName = "_entity"
            }
            .apply {
                var fieldIndex = 1
                nonPkFields.forEach {
                    add("_stmt.")
                    addPreparedStatementSetCall(it.type.resolve(), resolver)
                    add("(%L, %L)\n", fieldIndex++, "$entityVarName.${it.simpleName.asString()}")
                }
                pkProps.forEach { pkEl ->
                    add("_stmt.")
                    addPreparedStatementSetCall(pkEl.type.resolve(), resolver)
                    add("(%L, %L)\n", fieldIndex++, "$entityVarName.${pkEl.simpleName.asString()}")
                }
            }
            .applyIf(daoFun.hasReturnType(resolver)) {
                add("_result += ")
            }
            .applyIf(daoFunDecl.isSuspended) {
                add("_stmt.%M()\n", MEMBERNAME_EXEC_UPDATE_ASYNC)
            }
            .applyIf(!daoFunDecl.isSuspended) {
                add("_stmt.executeUpdate()\n")
            }
            .applyIf(firstParam.type.isListOrArray()) {
                endControlFlow()
                add("_stmt.getConnection().commit()\n")
            }
            .endControlFlow()
            .applyIf(daoFunDecl.hasReturnType(resolver)) {
                add("return _result")
            }
            .build())

        .build())

    Napier.d("DbProcessorJdbcKotlin: addDaoUpdateFunction: finish $funLogName")
    return this
}

fun TypeSpec.Builder.addDaoDeleteFunction(
    daoFunDecl: KSFunctionDeclaration,
    daoDecl: KSClassDeclaration,
    resolver: Resolver,
    logger: KSPLogger,
) : TypeSpec.Builder {
    val logName = "${daoDecl.simpleName.asString()}#${daoFunDecl.simpleName.asString()}"
    Napier.d("DbProcessorJdbcKotlin: addDaoDeleteFunction: start $logName")
    val funSpec = daoFunDecl.toFunSpecBuilder(resolver, daoDecl.asType(emptyList()), logger)
    val entityType = daoFunDecl.asMemberOf(daoDecl.asType(emptyList())).firstParamEntityType(resolver)
    val entityClassDecl = entityType.declaration as KSClassDeclaration
    val pkEls = entityClassDecl.entityPrimaryKeyProps
    val stmtSql = "DELETE FROM ${entityClassDecl.entityTableName} WHERE " +
            pkEls.joinToString(separator = " AND ") { "${it.simpleName.asString()} = ?" }
    val firstParam = funSpec.parameters.first()
    var entityVarName = firstParam.name

    addFunction(funSpec
        .removeAbstractModifier()
        .removeAnnotations()
        .addModifiers(KModifier.OVERRIDE)
        .addCode(CodeBlock.builder()
            .add("var _numChanges = 0\n")
            .beginControlFlow("_db.%M(%S)",
                AbstractDbProcessor.prepareAndUseStatmentMemberName(daoFunDecl.isSuspended),
                stmtSql)
            .add(" _stmt ->\n")
            .applyIf(firstParam.type.isListOrArray()) {
                add("_stmt.getConnection().setAutoCommit(false)\n")
                beginControlFlow("for(_entity in ${firstParam.name})")
                entityVarName = "_entity"
            }
            .apply {
                pkEls.forEachIndexed { index, pkProp ->
                    add("_stmt.")
                    addPreparedStatementSetCall(pkProp.type.resolve(), resolver)
                    add("(%L, %L)\n",index + 1, "$entityVarName.${pkProp.simpleName.asString()}")
                }
            }
            .apply {
                if(daoFunDecl.isSuspended) {
                    add("_numChanges += _stmt.%M()\n", MEMBERNAME_EXEC_UPDATE_ASYNC)
                }else {
                    add("_numChanges += _stmt.executeUpdate()\n")
                }
            }
            .applyIf(firstParam.type.isListOrArray()) {
                endControlFlow()
                add("_stmt.getConnection().commit()\n")
            }
            .endControlFlow()
            .applyIf(daoFunDecl.hasReturnType(resolver)) {
                add("return _numChanges\n")
            }
            .build())
        .build())

    Napier.d("DbProcessorJdbcKotlin: addDaoDeleteFunction: finish $logName")
    return this
}

fun TypeSpec.Builder.addDaoQueryFunction(
    daoFunDecl: KSFunctionDeclaration,
    daoDecl: KSClassDeclaration,
    resolver: Resolver,
    environment: SymbolProcessorEnvironment,
    logger: KSPLogger,
): TypeSpec.Builder {
    val logName = "${daoDecl.simpleName.asString()}#${daoFunDecl.simpleName.asString()}"
    Napier.d("DbProcessorJdbcKotlin: addDaoQueryFunction: start $logName")
    val daoKSType = daoDecl.asType(emptyList())
    val funSpec = daoFunDecl.toFunSpecBuilder(resolver, daoKSType, logger)
    val daoFun = daoFunDecl.asMemberOf(daoKSType)

    val queryVarsMap = daoFunDecl.parameters.mapIndexed { index, ksValueParameter ->
        ksValueParameter.name!!.asString() to daoFun.parameterTypes[index]!!
    }.toMap()

    val querySql = daoFunDecl.getAnnotation(Query::class)?.value
    val resultType = daoFun.returnType?.unwrapResultType(resolver) ?: resolver.builtIns.unitType

    val rawQueryParamName = if(daoFunDecl.hasAnnotation(RawQuery::class))
        daoFunDecl.parameters.first().name?.asString()
    else
        null

    addFunction(funSpec
        .removeAbstractModifier()
        .removeAnnotations()
        .addModifiers(KModifier.OVERRIDE)
        .applyIf(daoFun.returnType?.isPagingSource() == true) {
            val resultComponentType = resultType.unwrapComponentTypeIfListOrArray(resolver)
            val pagingSourceQueryVarsMap = queryVarsMap + mapOf("_offset" to resolver.builtIns.intType,
                "_limit" to resolver.builtIns.intType)
            addCode(CodeBlock.builder()
                .add("return %L\n",
                    TypeSpec.anonymousClassBuilder()
                        .addSuperclassConstructorParameter("_db")
                        .superclass(DoorLimitOffsetPagingSource::class.asClassName()
                            .parameterizedBy(resultComponentType.toTypeName()))
                        .addFunction(FunSpec.builder("loadRows")
                            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                            .returns(List::class.asTypeName().parameterizedBy(resultComponentType.toTypeName()))
                            .addParameter("_limit", INT)
                            .addParameter("_offset", INT)
                            .addCode(CodeBlock.builder()
                                .applyIf(rawQueryParamName != null) {
                                    add("val $rawQueryParamName = $rawQueryParamName.%M(\n",
                                        MemberName("com.ustadmobile.door.ext", "copyWithExtraParams"))
                                    add("sql = \"SELECT * FROM (\${$rawQueryParamName.sql}) LIMIT ? OFFSET ?\",\n")
                                    add("extraParams = arrayOf(_limit, _offset))\n")
                                }
                                .add("return ")
                                .addJdbcQueryCode(daoFunDecl, daoDecl, pagingSourceQueryVarsMap, resolver,
                                    querySql = querySql?.let { "SELECT * FROM ($it) LIMIT :_limit OFFSET :_offset" })
                                .build()
                            )
                            .build()
                        )
                        .addFunction(FunSpec.builder("countRows")
                            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                            .returns(INT)
                            .addCode(CodeBlock.builder()
                                .applyIf(rawQueryParamName != null) {
                                    add("val $rawQueryParamName = $rawQueryParamName.%M(\n",
                                        MemberName("com.ustadmobile.door.ext", "copy"))
                                    add("sql = \"SELECT COUNT(*) FROM (\${$rawQueryParamName.sql})\")\n")
                                }
                                .add("return ")
                                .addJdbcQueryCode(daoFunDecl, daoDecl, queryVarsMap, resolver,
                                    resultType = resolver.builtIns.intType,
                                    querySql = querySql?.let { "SELECT COUNT(*) FROM ($querySql) " })
                                .build()
                            )
                            .build()
                        )

                        .build()
                )
                .build()
            )
        }
        .applyIf(daoFun.returnType?.isFlow() == true) {
            addCode(CodeBlock.builder()
                .add("return ")
                .beginControlFlow("_db.%M(arrayOf(%L))",
                    MemberName("com.ustadmobile.door.flow", "doorFlow"),
                    daoFunDecl.getQueryTables(environment.logger).joinToString { "\"$it\"" })
                .addJdbcQueryCode(daoFunDecl, daoDecl, queryVarsMap, resolver)
                .endControlFlow()
                .build())
        }.applyIf(daoFun.returnType?.isPagingSourceOrFlow() != true) {
            if(daoFun.hasReturnType(resolver))
                addCode("return ")
            addCode(CodeBlock.builder()
                .addJdbcQueryCode(daoFunDecl, daoDecl, queryVarsMap, resolver)
                .build())
        }
        .build())

    return this
}

fun CodeBlock.Builder.beginPrepareAndUseStatementFlow(
    daoFunDecl: KSFunctionDeclaration,
    daoClassDecl: KSClassDeclaration,
    resolver: Resolver,
    statementVarName: String = "_stmt",
    querySql: String? = daoFunDecl.getAnnotation(Query::class)?.value,
): CodeBlock.Builder {
    add("_db.%M(", AbstractDbProcessor.prepareAndUseStatmentMemberName(daoFunDecl.useSuspendedQuery))
    addPreparedStatementConfig(daoFunDecl, daoClassDecl, resolver, querySql)
    add(") { $statementVarName -> \n")
    indent()

    return this
}

private fun CodeBlock.Builder.beginExecQueryOrUpdateFlow(
    suspended: Boolean,
    stmtVarName: String = "_stmt",
    resultVarName: String = "_result",
): CodeBlock.Builder {
    if(suspended) {
        add("$stmtVarName.%M().%M",
            MemberName("com.ustadmobile.door.jdbc.ext", "executeQueryAsyncKmp"),
            MemberName("com.ustadmobile.door.jdbc.ext", "useResults"))
    }else {
        add("$stmtVarName.executeQuery().%M", MemberName("com.ustadmobile.door.jdbc.ext", "useResults"))
    }

    add("{ $resultVarName -> \n")
    indent()

    return this
}

fun CodeBlock.Builder.addMapResultRowCode(
    resultType: KSType,
    resolver: Resolver,
    resultVarName: String = "_result",
): CodeBlock.Builder {
    val queryReturnType = resultType.unwrapResultType(resolver)
    val resultComponentType = queryReturnType.unwrapComponentTypeIfListOrArray(resolver)
    if(queryReturnType.isListOrArrayType(resolver)) {
        beginControlFlow("$resultVarName.%M", MemberName("com.ustadmobile.door.jdbc.ext", "mapRows"))
    }else {
        beginControlFlow("$resultVarName.%M(%L)", MemberName("com.ustadmobile.door.jdbc.ext", "mapNextRow"),
            queryReturnType.defaultTypeValueCode(resolver))
    }

    if(resultComponentType in resolver.querySingularTypes()){
        add("$resultVarName.")
        addGetResultOrSetQueryParamCall(resultComponentType, PreparedStatementOp.GET, resolver)
        add("(1)\n")
    }else {
        fun addResultSetTmpVals(entityType: KSClassDeclaration, checkAllNull: Boolean) {
            if(checkAllNull) {
                add("var _tmp_${entityType.entityTableName}_nullCount = 0\n")
            }

            val allResultSetCols = entityType.getAllColumnProperties(resolver)
            allResultSetCols.forEach { prop ->
                add("val _tmp_${prop.entityPropColumnName} = $resultVarName.")
                addGetResultOrSetQueryParamCall(prop.type.resolve(), PreparedStatementOp.GET, resolver)
                add("(%S)\n", prop.entityPropColumnName)
                if(checkAllNull)
                    add("if($resultVarName.wasNull()) _tmp_${entityType.entityTableName}_nullCount++\n")
            }

            if(checkAllNull) {
                add("val·_tmp_${entityType.entityTableName}_isAllNull·=·_tmp_${entityType.entityTableName}_nullCount·==·${allResultSetCols.size}\n")
            }
        }

        val resultSetKSClass = resultComponentType.declaration as KSClassDeclaration
        addResultSetTmpVals(resultSetKSClass, false)
        resultSetKSClass.entityEmbeddedEntities(resolver).forEach {
            addResultSetTmpVals(it, true)
        }

        addResultSetToEntityCode(resultSetKSClass, resolver)
    }

    endControlFlow()

    return this
}

fun CodeBlock.Builder.addResultSetToEntityCode(
    entityKSClass: KSClassDeclaration,
    resolver: Resolver,
): CodeBlock.Builder {
    beginControlFlow("%T().apply", entityKSClass.toClassName())
    entityKSClass.getAllColumnProperties(resolver).forEach { columnProp ->
        add("this.${columnProp.simpleName.asString()} = _tmp_${columnProp.entityPropColumnName}\n")
    }

    entityKSClass.getAllProperties().filter { it.hasAnnotation(Embedded::class) }.forEach { embeddedProp ->
        val embeddedClassDecl = embeddedProp.type.resolve().declaration as KSClassDeclaration
        beginControlFlow("if(!_tmp_${embeddedClassDecl.entityTableName}_isAllNull)")
        add("this.${embeddedProp.simpleName.asString()} = ")
        addResultSetToEntityCode(embeddedClassDecl, resolver)
        endControlFlow()
    }

    endControlFlow()
    return this
}


fun CodeBlock.Builder.addPreparedStatementConfig(
    daoFunDecl: KSFunctionDeclaration,
    daoClassDecl: KSClassDeclaration,
    resolver: Resolver,
    querySql: String? = daoFunDecl.getAnnotation(Query::class)?.value
): CodeBlock.Builder {
    val daoFun = daoFunDecl.asMemberOf(daoClassDecl.asType(emptyList()))
    val querySqlPostgres = daoFunDecl.getAnnotation(PostgresQuery::class)?.value
    val queryVars = daoFunDecl.parameters.mapIndexed { index, ksValueParameter ->
        ksValueParameter.name!!.asString() to daoFun.parameterTypes[index]!!
    }.toMap()

    val preparedStatementSql = querySql?.replaceQueryNamedParamsWithQuestionMarks()
    val rawQueryVarName = daoFunDecl.takeIf { it.hasAnnotation(RawQuery::class) }?.parameters?.first()?.name?.asString()

    val preparedStatementSqlPostgres = querySqlPostgres?.replaceQueryNamedParamsWithQuestionMarks()
        ?: querySql?.replaceQueryNamedParamsWithQuestionMarks()?.sqlToPostgresSql()

    if(rawQueryVarName == null) {
        add("%T(%S ", PreparedStatementConfig::class, preparedStatementSql)
        if(queryVars.any { it.value.isListOrArrayType(resolver) })
            add(",hasListParams = true")

        if(preparedStatementSql?.trim() != preparedStatementSqlPostgres?.trim())
            add(", postgreSql = %S", preparedStatementSqlPostgres)

        add(")")
    }else {
        add("%T($rawQueryVarName.sql, hasListParams = $rawQueryVarName.%M())\n",
            PreparedStatementConfig::class, MemberName("com.ustadmobile.door.ext", "hasListOrArrayParams"))
    }
    return this
}

/**
 * Creates a CodeBlock that will set the query parameters for a prepared statement
 * e.g.
 * _stmt.setLong(1, someVar)
 * _stmt.setString(2, name)
 *
 * @param querySql the original Query SQL with named parameters
 * @param queryVars a map of the name of each query to its type
 * @param resolver the resolver
 */
fun CodeBlock.Builder.addSetPreparedStatementParams(
    querySql: String,
    queryVars: Map<String, KSType>,
    resolver: Resolver,
    statementVarName: String = "_stmt"
): CodeBlock.Builder {
    querySql.getSqlQueryNamedParameters().forEachIndexed { index, paramVarName ->
        val paramType = queryVars[paramVarName]
        if(paramType == null) {
            add("//ERROR! Could not find type for - $paramVarName\n")
        }else if(paramType.isListOrArrayType(resolver)) {
            val arrayTypeName = sqlArrayComponentTypeOf(paramType.toTypeName())
            add("$statementVarName.setArray(${index + 1}, _stmt.getConnection().%M(%S, %L.toTypedArray()))\n",
                MemberName("com.ustadmobile.door.ext", "createArrayOrProxyArrayOf"),
                arrayTypeName, paramVarName)
        }else {
            add("$statementVarName.")
            addPreparedStatementSetCall(paramType, resolver)
            add("(${index + 1},${paramVarName})\n")
        }
    }

    return this
}

/**
 * Generate the code that will execute a query and turn it into objects or a list of objects
 * Normally looks like:
 *
 * _db.prepareAndUseStatement(PreparedStatementConfig(..)) { stmt ->
 *    stmt.setLong(...)
 *    stmt.executeQuery().useResults { result ->
 *        result.mapRows {
 *           val tmp_colName = result.getField("colName")
 *           val tmp_id = result.getField("id")
 *           EntityType().apply {
 *              this.colName = tmp_colName
 *              this.id = tmp_id
 *           }
 *        }
 *    }
 * }
 */
fun CodeBlock.Builder.addJdbcQueryCode(
    daoFunDecl: KSFunctionDeclaration,
    daoClassDecl: KSClassDeclaration,
    queryVarsMap: Map<String, KSType>,
    resolver: Resolver,
    querySql: String? = daoFunDecl.getAnnotation(Query::class)?.value,
    resultType: KSType? = daoFunDecl.asMemberOf(daoClassDecl.asType(emptyList())).returnType,
): CodeBlock.Builder {
    beginPrepareAndUseStatementFlow(daoFunDecl, daoClassDecl, resolver, querySql = querySql)
    if(querySql != null)
        addSetPreparedStatementParams(querySql, queryVarsMap, resolver)
    else if(daoFunDecl.hasAnnotation(RawQuery::class)){
        add("${daoFunDecl.parameters.first().name?.asString()}.bindToPreparedStmt(_stmt, _db)\n")
    }


    if(querySql?.isSQLAModifyingQuery() != true) {
        beginExecQueryOrUpdateFlow(suspended = daoFunDecl.useSuspendedQuery)
        addMapResultRowCode(resultType!!, resolver)
        endControlFlow()
    }else {
        if(daoFunDecl.useSuspendedQuery) {
            add("_stmt.%M()\n", MEMBERNAME_EXEC_UPDATE_ASYNC)
        }else {
            add("_stmt.executeUpdate()\n")
        }
    }

    endControlFlow()
    return this
}


class DoorJdbcProcessor(
    private val environment: SymbolProcessorEnvironment,
): SymbolProcessor {

    //Messy, but better than changing the superclass. This MUST be set by the SymbolProcessorWrapper first
    internal lateinit var dbConnection: java.sql.Connection

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val dbSymbols = resolver.getDatabaseSymbolsToProcess()

        val daoSymbols = resolver.getDaoSymbolsToProcess()

        val target = environment.doorTarget(resolver)

        dbSymbols.forEach {  dbKSClass ->
            FileSpec.builder(dbKSClass.packageName.asString(), dbKSClass.simpleName.asString() + SUFFIX_DOOR_METADATA)
                .addDatabaseMetadataType(dbKSClass)
                .build()
                .writeTo(environment.codeGenerator, false)

            if(target in JDBC_TARGETS) {
                FileSpec.builder(dbKSClass.packageName.asString(), dbKSClass.simpleName.asString() + SUFFIX_JDBC_KT2)
                    .addJdbcDbImplType(dbKSClass, target, resolver)
                    .build()
                    .writeTo(environment.codeGenerator, false)
            }

            if(target == DoorTarget.JS)
                FileSpec.builder(dbKSClass.packageName.asString(),
                        dbKSClass.simpleName.asString() + SUFFIX_JS_IMPLEMENTATION_CLASSES)
                    .addJsImplementationsClassesObject(dbKSClass)
                    .build()
                    .writeTo(environment.codeGenerator, false)
        }

        if(target in JDBC_TARGETS) {daoSymbols.forEach { daoKSClass ->
            FileSpec.builder(daoKSClass.packageName.asString(),
                    daoKSClass.simpleName.asString() + SUFFIX_JDBC_KT2)
                    .addDaoJdbcImplType(daoKSClass, resolver, environment, target)
                    .build()
                    .writeTo(environment.codeGenerator, false)
            }
        }

        return emptyList()
    }

    companion object {

        val JDBC_TARGETS = listOf(DoorTarget.JVM, DoorTarget.JS)

        //As it should be including the underscore - the above will be deprecated
        const val SUFFIX_JDBC_KT2 = "_JdbcKt"

        const val SUFFIX_JS_IMPLEMENTATION_CLASSES = "JsImplementations"
    }

}
