package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.EntityAck
import com.ustadmobile.door.SyncResult
import com.ustadmobile.door.annotation.*
import com.ustadmobile.door.entities.UpdateNotificationSummary
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorJdbcKotlin.Companion.SUFFIX_JDBC_KT
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorJdbcKotlin.Companion.SUFFIX_JDBC_KT2
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_KTOR_HELPER
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_KTOR_HELPER_LOCAL
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_KTOR_HELPER_MASTER
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_KTOR_ROUTE
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorRepository.Companion.SUFFIX_REPOSITORY2
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorSync.Companion.SUFFIX_ENTITY_TRK
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorSync.Companion.SUFFIX_SYNCDAO_ABSTRACT
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement
import com.ustadmobile.door.SyncSettings

/**
 * Generate a Tracker Entity for a Syncable Entity
 */
internal fun generateTrackerEntity(entityClass: TypeElement, processingEnv: ProcessingEnvironment) : TypeSpec {
    val pkFieldTypeName = getEntityPrimaryKey(entityClass)!!.asType().asTypeName()
    return TypeSpec.classBuilder("${entityClass.simpleName}_trk")
            .addAnnotation(AnnotationSpec.builder(Suppress::class)
                    .addMember("%S", "ClassName")
                    .build())
            .addProperties(listOf(
                    PropertySpec.builder(DbProcessorSync.TRACKER_PK_FIELDNAME, LONG)
                            .addAnnotation(AnnotationSpec.builder(PrimaryKey::class).addMember("autoGenerate = true").build())
                            .initializer(DbProcessorSync.TRACKER_PK_FIELDNAME)
                            .build(),
                    PropertySpec.builder(DbProcessorSync.TRACKER_ENTITY_PK_FIELDNAME, pkFieldTypeName)
                            .initializer(DbProcessorSync.TRACKER_ENTITY_PK_FIELDNAME)
                            .build(),
                    PropertySpec.builder(DbProcessorSync.TRACKER_DESTID_FIELDNAME, INT)
                            .initializer(DbProcessorSync.TRACKER_DESTID_FIELDNAME)
                            .build(),
                    PropertySpec.builder(DbProcessorSync.TRACKER_CHANGESEQNUM_FIELDNAME, INT)
                            .initializer(DbProcessorSync.TRACKER_CHANGESEQNUM_FIELDNAME)
                            .build(),
                    PropertySpec.builder(DbProcessorSync.TRACKER_RECEIVED_FIELDNAME, BOOLEAN)
                            .initializer(DbProcessorSync.TRACKER_RECEIVED_FIELDNAME)
                            .build(),
                    PropertySpec.builder(DbProcessorSync.TRACKER_REQUESTID_FIELDNAME, INT)
                            .initializer(DbProcessorSync.TRACKER_REQUESTID_FIELDNAME)
                            .build(),
                    PropertySpec.builder(DbProcessorSync.TRACKER_TIMESTAMP_FIELDNAME, LONG)
                            .initializer(DbProcessorSync.TRACKER_TIMESTAMP_FIELDNAME)
                            .build()
            ))
            .addAnnotation(AnnotationSpec.builder(Entity::class)
                    .addMember("indices = [%T(value = [%S, %S, %S]),%T(value = [%S, %S], unique = true)]",
                            //Index for query speed linking the destid, entity pk, and the change seq num
                            Index::class,
                            DbProcessorSync.TRACKER_DESTID_FIELDNAME,
                            DbProcessorSync.TRACKER_ENTITY_PK_FIELDNAME,
                            DbProcessorSync.TRACKER_CHANGESEQNUM_FIELDNAME,
                            //Unique index to enforce that there should be one tracker per entity pk / destination id combo
                            Index::class,
                            DbProcessorSync.TRACKER_ENTITY_PK_FIELDNAME,
                            DbProcessorSync.TRACKER_DESTID_FIELDNAME)
                    .build())
            .primaryConstructor(FunSpec.constructorBuilder()
                    .addParameter(ParameterSpec.builder(DbProcessorSync.TRACKER_PK_FIELDNAME, LONG)
                            .defaultValue("0L").build())
                    .addParameter(ParameterSpec.builder(DbProcessorSync.TRACKER_ENTITY_PK_FIELDNAME,
                            pkFieldTypeName).defaultValue("0L").build())
                    .addParameter(ParameterSpec.builder(DbProcessorSync.TRACKER_DESTID_FIELDNAME,
                            INT).defaultValue("0").build())
                    .addParameter(ParameterSpec.builder(DbProcessorSync.TRACKER_CHANGESEQNUM_FIELDNAME,
                            INT).defaultValue("0").build())
                    .addParameter(ParameterSpec.builder(DbProcessorSync.TRACKER_RECEIVED_FIELDNAME,
                            BOOLEAN).defaultValue("false").build())
                    .addParameter(ParameterSpec.builder(DbProcessorSync.TRACKER_REQUESTID_FIELDNAME,
                            INT).defaultValue("0").build())
                    .addParameter(ParameterSpec.builder(DbProcessorSync.TRACKER_TIMESTAMP_FIELDNAME,
                            LONG).defaultValue("0L").build())
                    .build())
            .addModifiers(KModifier.DATA)
            .build()
}

fun FileSpec.Builder.addSyncableEntityToTrackFunction(entityClass: TypeElement, processingEnv: ProcessingEnvironment)
: FileSpec.Builder{

    val syncEntityInfo = SyncableEntityInfo(entityClass.asClassName(), processingEnv)
    addFunction(FunSpec.builder("toSyncableTrk")
            .receiver(List::class.asClassName().parameterizedBy(entityClass.asClassName()))
            .addParameter(ParameterSpec.builder("primary", BOOLEAN)
                    .defaultValue("false")
                    .build())
            .addParameter(ParameterSpec.builder("clientId", INT)
                    .defaultValue("0")
                    .build())
            .returns(List::class.asClassName().parameterizedBy(syncEntityInfo.tracker))
            .addCode(CodeBlock.builder()
                    .add("return·map·{\n") //Avoid this being put on the next line
                    .indent()
                    .add("%T(epk = it.${syncEntityInfo.entityPkField.name}," +
                            "clientId = clientId,", syncEntityInfo.tracker)
                    .beginControlFlow("csn = if(primary)")
                    .add("it.${syncEntityInfo.entityMasterCsnField.name}")
                    .applyIf(syncEntityInfo.entityMasterCsnField.type == LONG) {
                        add(".toInt()")
                    }
                    .add("\n")
                    .nextControlFlow("else")
                    .add("it.${syncEntityInfo.entityLocalCsnField.name}")
                    .applyIf(syncEntityInfo.entityMasterCsnField.type == LONG) {
                        add(".toInt()")
                    }
                    .add("\n")
                    .endControlFlow()
                    .add(")\n")
                    .endControlFlow()
                    .build())
            .build())

    return this
}

fun FileSpec.Builder.addSyncableEntitytoEntityAckFunction(entityClass: TypeElement,
                                                          processingEnv: ProcessingEnvironment) : FileSpec.Builder {
    val syncEntityInfo = SyncableEntityInfo(entityClass.asClassName(), processingEnv)
    addFunction(FunSpec.builder("toEntityAck")
            .receiver(List::class.asClassName().parameterizedBy(entityClass.asClassName()))
            .addParameter("primary", BOOLEAN)
            .returns(List::class.parameterizedBy(EntityAck::class))
            .addCode(CodeBlock.builder()
                    .add("return·map{\n").indent()
                    .add("%T(epk·=·it.${syncEntityInfo.entityPkField.name},\n",
                        EntityAck::class)
                    .beginControlFlow("csn = if(primary)")
                        .add("it.${syncEntityInfo.entityMasterCsnField.name}")
                        .applyIf(syncEntityInfo.entityMasterCsnField.type == LONG) {
                            add(".toInt()")
                        }
                        .add("\n")
                    .nextControlFlow("else")
                        .add("it.${syncEntityInfo.entityLocalCsnField.name}")
                        .applyIf(syncEntityInfo.entityMasterCsnField.type == LONG) {
                            add(".toInt()")
                        }
                        .add("\n")
                    .endControlFlow()
                    .add(")\n")
                    .endControlFlow()
                    .build())
            .build())
    return this
}


/**
 * Where this TypeElement represents a Database, generate a TypeSpec for the SyncDao
 * with all the query functions required for syncable entities that are on the given
 * database.
 */
fun TypeElement.toSyncDaoTypeSpec(processingEnv: ProcessingEnvironment) : TypeSpec {
    val dbTypeEl = this
    return TypeSpec.classBuilder("$simpleName$SUFFIX_SYNCDAO_ABSTRACT")
            .addModifiers(KModifier.ABSTRACT)
            .addAnnotation(Dao::class.java)
            .addSuperinterface(ClassName(dbTypeEl.packageName, "I${dbTypeEl.simpleName}$SUFFIX_SYNCDAO_ABSTRACT"))
            .apply {
                dbTypeEl.allDbEntities(processingEnv).filter { it.hasAnnotation(SyncableEntity::class.java) }.forEach {entityType ->
                    addSyncDaoFunsForEntity(entityType, isOverride = true, processingEnv = processingEnv)
                }

                dbTypeEl.dbEnclosedDaos(processingEnv).filter { it.isDaoThatRequiresSyncHelper(processingEnv) }.forEach {
                    addSuperinterface(it.asClassNameWithSuffix("_SyncHelper"))
                }
            }
            .build()
}

/**
 * Where this TypeElement represents a database, generate a TypeSpec for the SyncDao
 * interface. This is really just here to ensure that all functions are overrides.
 */
fun TypeElement.toSyncDaoInterfaceTypeSpec(processingEnv: ProcessingEnvironment): TypeSpec {
    val dbTypeEl = this
    return TypeSpec.interfaceBuilder("I$simpleName$SUFFIX_SYNCDAO_ABSTRACT")
            .apply {
                dbTypeEl.allDbEntities(processingEnv).filter { it.hasAnnotation(SyncableEntity::class.java) }.forEach { entityType ->
                    addSyncDaoFunsForEntity(entityType, isOverride = false, processingEnv = processingEnv)
                }
            }
            .build()
}

/**
 * The functions required to the SyncDao for the given syncable entity. This will include queries
 * that find the remote changes, local changes, insert/replace the entity itself, and
 * insert/replace the trk entity, etc.
 */
fun TypeSpec.Builder.addSyncDaoFunsForEntity(entityType: TypeElement, isOverride: Boolean,
        processingEnv: ProcessingEnvironment) : TypeSpec.Builder{
    val syncFindAllSql = entityType.getAnnotation(SyncableEntity::class.java)?.syncFindAllQuery
    val syncableEntityInfo = SyncableEntityInfo(entityType.asClassName(), processingEnv)
    val findAllRemoteSql = if(syncFindAllSql?.isNotEmpty() == true) {
        syncFindAllSql
    }else {
        "SELECT * FROM ${entityType.simpleName} LIMIT :maxResults"
    }

    val findLocalUnsentSql = "SELECT * FROM " +
            "(SELECT * FROM ${entityType.simpleName} ) AS ${entityType.simpleName} " +
            "WHERE " +
            "${syncableEntityInfo.entityLastChangedByField.name} = (SELECT nodeClientId FROM SyncNode) AND " +
            "(${entityType.simpleName}.${syncableEntityInfo.entityLocalCsnField.name} > " +
            "COALESCE((SELECT ${syncableEntityInfo.trackerCsnField.name} FROM ${syncableEntityInfo.tracker.simpleName} " +
            "WHERE ${syncableEntityInfo.trackerPkField.name} = ${entityType.simpleName}.${syncableEntityInfo.entityPkField.name} " +
            "AND ${syncableEntityInfo.trackerDestField.name} = :destClientId), 0)" +
            ") LIMIT :limit"

    addFunction(FunSpec.builder("_findMasterUnsent${entityType.simpleName}")
            .addModifiers(KModifier.ABSTRACT, KModifier.SUSPEND)
            .applyIf(isOverride) {
                addModifiers(KModifier.OVERRIDE)
            }
            .applyIf(findAllRemoteSql.contains(":maxResults")) {
                addParameter(ParameterSpec.builder("maxResults", INT)
                        .addAnnotation(SyncableLimitParam::class)
                        .build())
            }.applyIf(findAllRemoteSql.contains(":clientId")) {
                addParameter("clientId", INT)
            }
            .returns(List::class.asClassName().parameterizedBy(entityType.asClassName()))
            .addAnnotation(AnnotationSpec.builder(Query::class)
                    .addMember("%S", findAllRemoteSql).build())
            .addAnnotation(AnnotationSpec.builder(Repository::class)
                    .addMember("methodType = ${Repository.METHOD_DELEGATE_TO_WEB}")
                    .build())
            .build())


    addFunction(FunSpec.builder("_findLocalUnsent${entityType.simpleName}")
            .addAnnotation(AnnotationSpec.builder(Query::class)
                    .addMember("%S", findLocalUnsentSql)
                    .build())
            .addAnnotation(AnnotationSpec.builder(Repository::class)
                    .addMember("methodType = ${Repository.METHOD_DELEGATE_TO_DAO}")
                    .build())
            .addModifiers(KModifier.ABSTRACT, KModifier.SUSPEND)
            .applyIf(isOverride) {
                addModifiers(KModifier.OVERRIDE)
            }
            .addParameter("destClientId", INT)
            .addParameter("limit", INT)
            .returns(List::class.asClassName().parameterizedBy(entityType.asClassName()))
            .build())


    addFunction(FunSpec.builder("_replace${entityType.simpleName}")
            .addModifiers(KModifier.ABSTRACT, KModifier.SUSPEND)
            .applyIf(isOverride) {
                addModifiers(KModifier.OVERRIDE)
            }
            .addAnnotation(AnnotationSpec.builder(Insert::class)
                    .addMember("onConflict = ${OnConflictStrategy.REPLACE}")
                    .build())
            .addParameter("entities",
                    List::class.asClassName().parameterizedBy(entityType.asClassName()))
            .build())

    addFunction(FunSpec.builder("_replace${entityType.simpleName}_trk")
            .addModifiers(KModifier.ABSTRACT, KModifier.SUSPEND)
            .applyIf(isOverride) {
                addModifiers(KModifier.OVERRIDE)
            }
            .addAnnotation(AnnotationSpec.builder(Insert::class)
                    .addMember("onConflict = ${OnConflictStrategy.REPLACE}")
                    .build())
            .addAnnotation(AnnotationSpec.builder(PgOnConflict::class)
                    .addMember("%S", "ON CONFLICT(epk, clientId) DO UPDATE SET csn = excluded.csn")
                    .build())
            .addParameter("trkEntities",
                    List::class.asClassName().parameterizedBy(entityType.asClassNameWithSuffix(SUFFIX_ENTITY_TRK)))
            .build())

    //Add functions to the DAO to handle notifyOnUpdate
    entityType.getAnnotation(SyncableEntity::class.java).notifyOnUpdate.forEachIndexed { index, queryStr ->
        addFunction(FunSpec.builder("_find${entityType.simpleName}NotifyOnUpdate_$index")
                .returns(List::class.parameterizedBy(UpdateNotificationSummary::class))
                .applyIf(isOverride) {
                    addModifiers(KModifier.OVERRIDE)
                }
                .addModifiers(KModifier.ABSTRACT)
                .addAnnotation(AnnotationSpec.builder(Query::class)
                        .addMember("%S", queryStr)
                        .build())
                .build())
    }

    return this
}

/**
 * Add a function that runs the sync of the given entity in the repo. Each entity sync needs to go
 * in it's own function, otherwise the main 'sync' function can become too large for the compiler to
 * handle.
 *
 * @param entityType TypeElement representing the SyncableEntity
 * @param syncRepoVarName The variable name of the repo for the SyncDao
 */
fun TypeSpec.Builder.addRepoSyncEntityFunction(entityType: TypeElement, syncRepoVarName: String) : TypeSpec.Builder{
    addFunction(FunSpec.builder("_sync${entityType.simpleName}")
            .addModifiers(KModifier.PRIVATE, KModifier.SUSPEND)
            .returns(SyncResult::class)
            .addCode(CodeBlock.builder()
                    .apply {
                        val syncableEntity = entityType.getAnnotation(SyncableEntity::class.java)
                        val tableId = syncableEntity.tableId
                        add("return %M<%T, %T>($tableId, %T(receiveBatchSize·=·%L,·sendBatchSize·=·%L), ",
                                MemberName("com.ustadmobile.door.ext", "syncEntity"),
                                entityType, entityType.asClassNameWithSuffix("_trk"),
                                SyncSettings::class, syncableEntity.receiveBatchSize,
                                syncableEntity.sendBatchSize)
                                .applyIf(entityType.syncableEntityFindAllHasClientIdParam) {
                                    beginControlFlow("receiveRemoteEntitiesFn = ")
                                    val hasMaxResults = entityType.syncableEntityFindAllHasMaxResultsParam
                                    if(hasMaxResults) {
                                        add("maxResults")
                                    }else {
                                        add("_")
                                    }
                                    add(" -> \n")

                                    add("$syncRepoVarName._findMasterUnsent${entityType.simpleName}(")
                                    if(hasMaxResults)
                                        add("maxResults,")

                                    add("clientId)\n")
                                    endControlFlow()
                                    add(",")
                                }
                                .applyIf(!entityType.syncableEntityFindAllHasClientIdParam) {
                                    add("receiveRemoteEntitiesFn = " +
                                            "$syncRepoVarName::_findMasterUnsent${entityType.simpleName},\n ")
                                }
                        add("storeEntitiesFn = _syncDao::_replace${entityType.simpleName},\n")
                        beginControlFlow("findLocalUnsentEntitiesFn =")
                        add("maxResults -> ")
                        add("_syncDao._findLocalUnsent${entityType.simpleName}(0, maxResults)\n")
                        endControlFlow()
                        add(",")
                        beginControlFlow("entityToAckFn = ")
                        add("_entities, primary -> \n")
                        add("_entities.%M(primary)\n",
                                MemberName(entityType.packageName, "toEntityAck"))
                        endControlFlow()
                        add(",")
                        beginControlFlow("entityToTrkFn = ")
                        add("_entities, _primary ->")
                        add("_entities.%M(primary = _primary)\n",
                                MemberName(entityType.packageName, "toSyncableTrk"))
                        endControlFlow()
                        add(",")
                        add("storeTrkFn = _syncDao::_replace${entityType.simpleName}_trk")
                        .apply {
                            add(",\n")
                            beginControlFlow("entityToEntityWithAttachmentFn = ")
                            if(entityType.entityHasAttachments) {
                                add("entity : %T -> entity.%M()\n", entityType,
                                        MemberName(entityType.packageName, "asEntityWithAttachment"))
                            }else {
                                add("_ -> null\n")
                            }

                            endControlFlow()
                        }
                        add(")\n")
                    }
                    .build()
            )
            .build())

    return this
}

/**
 * Generate a sync function that will take the list of tables to sync and call the sync function
 * for the required tables
 */
fun TypeSpec.Builder.addRepoSyncFunction(dbTypeEl: TypeElement,
                                         processingEnv: ProcessingEnvironment) : TypeSpec.Builder{
    addFunction(FunSpec.builder("sync")
            .addModifiers(KModifier.SUSPEND, KModifier.OVERRIDE)
            .returns(List::class.parameterizedBy(SyncResult::class))
            .addParameter("tablesToSync", List::class.parameterizedBy(Int::class)
                    .copy(nullable = true))
            .addCode(CodeBlock.builder()
                    .add("val _allResults = mutableListOf<%T>()\n", SyncResult::class)
                    .apply {
                        dbTypeEl.allDbEntities(processingEnv)
                                .filter { it.hasAnnotation(SyncableEntity::class.java) }
                                .forEach { entityType ->
                                    val tableId = entityType.getAnnotation(SyncableEntity::class.java).tableId
                                    beginControlFlow("if(tablesToSync == null || $tableId in tablesToSync)")
                                    add("_allResults += _sync${entityType.simpleName}()\n")
                                    endControlFlow()
                                }
                    }
                    .add("%M(_allResults)\n",
                            MemberName("com.ustadmobile.door.ext", "recordSyncRunResult"))
                    .add("return _allResults\n")
                    .build())
            .build())

    return this
}


/**
 * Where the TypeSpec builder represents a Repo for a syncable database, generate the
 * dispatchUpdateNotifications function.
 */
fun TypeSpec.Builder.addRepoDispatchUpdatesFunction(dbTypeEl: TypeElement,
                                                    processingEnv: ProcessingEnvironment) : TypeSpec.Builder{
    addFunction(FunSpec.builder("dispatchUpdateNotifications")
            .addParameter("tableId", INT)
            .addModifiers(KModifier.SUSPEND, KModifier.OVERRIDE)
            .addCode(CodeBlock.builder()
                    .beginControlFlow("when(tableId)")
                    .apply {
                        dbTypeEl.allDbEntities(processingEnv)
                            .filter { it.getAnnotation(SyncableEntity::class.java)?.notifyOnUpdate?.isNotEmpty() == true }
                            .forEach { entityType ->
                                val syncableEntityAnnotation = entityType.getAnnotation(SyncableEntity::class.java)
                                val tableId = syncableEntityAnnotation.tableId
                                beginControlFlow("$tableId -> ")
                                syncableEntityAnnotation.notifyOnUpdate.forEachIndexed {index, _ ->
                                    add("%M(tableId = $tableId, updateNotificationManager = _updateNotificationManager\n,",
                                        MemberName("com.ustadmobile.door.ext", "sendUpdates"))
                                    add("findDevicesFn = _syncDao::_find${entityType.simpleName}NotifyOnUpdate_$index)\n")
                                }
                                endControlFlow()
                            }
                    }
                    .endControlFlow()
                    .add("syncHelperEntitiesDao.deleteChangeLogs(tableId)\n")
                    .build())
            .build())

    return this
}

class DbProcessorSync: AbstractDbProcessor() {

    /**
     * Add a JDBC implementation of the given TypeSpec to the FileSpec
     */
    fun FileSpec.Builder.addJdbcDaoImplType(daoTypeSpec: TypeSpec, daoClassName: ClassName): FileSpec.Builder{
        addType(TypeSpec.classBuilder(daoClassName.withSuffix(SUFFIX_JDBC_KT2))
                .superclass(daoClassName)
                .addAnnotation(AnnotationSpec.builder(Suppress::class)
                        .addMember("%S, %S, %S, %S", "LocalVariableName", "SpellCheckingInspection",
                                "ClassName", "PropertyName")
                        .build())
                .primaryConstructor(FunSpec.constructorBuilder()
                        .addParameter("_db", DoorDatabase::class)
                        .build())
                .addProperty(PropertySpec.builder("_db", DoorDatabase::class)
                        .initializer("_db")
                        .build())
                .apply {
                    daoTypeSpec.funSpecs.filter { KModifier.ABSTRACT in it.modifiers }.forEach { daoFunSpec ->
                        when {
                            daoFunSpec.hasAnnotation(Query::class.java) ->
                                addJdbcQueryFun(daoFunSpec)
                            daoFunSpec.hasAnnotation(Insert::class.java) ->
                                addJdbcInsertFun(daoFunSpec)
                        }
                    }
                }
                .build())
        return this
    }

    fun TypeSpec.Builder.addJdbcQueryFun(queryFunSpec: FunSpec): TypeSpec.Builder {
        addFunction(queryFunSpec.toBuilder()
                .removeAbstractModifier()
                .addCode(generateQueryCodeBlock(queryFunSpec.returnType ?: UNIT,
                    queryFunSpec.parameters.map { it.name to it.type}.toMap(),
                        queryFunSpec.daoQuerySql(), null, null))
                .applyIf(queryFunSpec.hasReturnType) {
                    addCode("return _result\n")
                }
                .build())
        return this
    }

    fun TypeSpec.Builder.addJdbcInsertFun(insertFunSpec: FunSpec): TypeSpec.Builder {
        val entityType = insertFunSpec.parameters.first().type.unwrapListOrArrayComponentType() as ClassName
        val entityTypeSpec = entityType.asEntityTypeSpec(processingEnv)
                ?: throw IllegalArgumentException("no entity typ spec for ${insertFunSpec.name}")

        val isUpsert = insertFunSpec.getAnnotationSpec(Insert::class.java)
                ?.memberToString("onConflict")?.toInt() == OnConflictStrategy.REPLACE
        val pgOnConflict = insertFunSpec.getAnnotationSpec(PgOnConflict::class.java)?.memberToString()

        addFunction(insertFunSpec.toBuilder()
                .removeAbstractModifier()
                .addCode(generateInsertCodeBlock(insertFunSpec.parameters[0],
                    insertFunSpec.returnType ?: UNIT, entityTypeSpec, this,
                    isUpsert, insertFunSpec.hasReturnType, pgOnConflict ))
                .build())
        return this
    }


    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        val dbs = roundEnv.getElementsAnnotatedWith(Database::class.java).map { it as TypeElement}

        //For all databases that are being compiled now, find those entities that require tracker entities
        // to be generated. Filter out any for which the entity was already generated.
        dbs.flatMap { entityTypesOnDb(it as TypeElement, processingEnv) }
                .filter { it.getAnnotation(SyncableEntity::class.java) != null
                        && processingEnv.elementUtils
                        .getTypeElement("${it.asClassName().packageName}.${it.simpleName}$TRACKER_SUFFIX") == null}
                .forEach {
                    val trackerFileSpec = FileSpec.builder(it.asClassName().packageName, "${it.simpleName}$TRACKER_SUFFIX")
                            .addType(generateTrackerEntity(it, processingEnv))
                            .addSyncableEntityToTrackFunction(it, processingEnv)
                            .addSyncableEntitytoEntityAckFunction(it, processingEnv)
                            .build()

                    writeFileSpecToOutputDirs(trackerFileSpec, AnnotationProcessorWrapper.OPTION_JVM_DIRS)
                    writeFileSpecToOutputDirs(trackerFileSpec, AnnotationProcessorWrapper.OPTION_ANDROID_OUTPUT,
                            useFilerAsDefault = false)
                }

        for(dbTypeEl in dbs) {
            val syncDaoType = dbTypeEl.toSyncDaoTypeSpec(processingEnv)
            val syncDaoClassName = dbTypeEl.asClassNameWithSuffix(SUFFIX_SYNCDAO_ABSTRACT)
            FileSpec.builder(dbTypeEl.packageName, "$dbTypeEl$SUFFIX_SYNCDAO_ABSTRACT")
                    .addType(dbTypeEl.toSyncDaoInterfaceTypeSpec(processingEnv))
                    .addType(syncDaoType)
                    .build()
                    .writeToDirsFromArg(listOf(AnnotationProcessorWrapper.OPTION_JVM_DIRS,
                            AnnotationProcessorWrapper.OPTION_ANDROID_OUTPUT))

            FileSpec.builder(dbTypeEl.packageName, "$dbTypeEl$SUFFIX_SYNCDAO_ABSTRACT$SUFFIX_JDBC_KT2")
                    .addJdbcDaoImplType(syncDaoType, syncDaoClassName)
                    .addImport("com.ustadmobile.door", "DoorDbType")
                    .build()
                    .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_JVM_DIRS)

            //Generate a repo for the SyncDao

            FileSpec.builder(dbTypeEl.packageName, "${dbTypeEl.simpleName}$SUFFIX_SYNCDAO_ABSTRACT$SUFFIX_REPOSITORY2")
                    .addDaoRepoType(dbTypeEl.toSyncDaoTypeSpec(processingEnv),
                            syncDaoClassName,
                        processingEnv, allKnownEntityTypesMap, false,
                            false, syncHelperClassName = syncDaoClassName)
                    .build()
                    .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_JVM_DIRS)

            FileSpec.builder(dbTypeEl.packageName, "${dbTypeEl.simpleName}$SUFFIX_SYNCDAO_ABSTRACT$SUFFIX_REPOSITORY2")
                    .addDaoRepoType(dbTypeEl.toSyncDaoTypeSpec(processingEnv),
                            syncDaoClassName,
                            processingEnv, allKnownEntityTypesMap,
                            pagingBoundaryCallbackEnabled = true,
                            isAlwaysSqlite = true,
                            syncHelperClassName = syncDaoClassName)
                    .build()
                    .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_ANDROID_OUTPUT)

            FileSpec.builder(dbTypeEl.packageName,
                    "${dbTypeEl.simpleName}$SUFFIX_SYNCDAO_ABSTRACT$SUFFIX_KTOR_ROUTE")
                    .addDaoKtorRouteFun(syncDaoType, syncDaoClassName,
                            syncDaoClassName, processingEnv)
                    .build()
                    .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_KTOR_OUTPUT)

            FileSpec.builder(dbTypeEl.packageName,
                "${dbTypeEl.simpleName}$SUFFIX_SYNCDAO_ABSTRACT$SUFFIX_KTOR_HELPER")
                    .addKtorHelperInterface(syncDaoType, syncDaoClassName, processingEnv)
                    .build()
                    .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_KTOR_OUTPUT)

            FileSpec.builder(dbTypeEl.packageName,
                    "${dbTypeEl.simpleName}$SUFFIX_SYNCDAO_ABSTRACT$SUFFIX_KTOR_HELPER_MASTER")
                    .addKtorAbstractDao(syncDaoType, syncDaoClassName,
                            MasterChangeSeqNum::class, processingEnv)
                    .build()
                    .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_KTOR_OUTPUT)

            FileSpec.builder(dbTypeEl.packageName,
                    "${dbTypeEl.simpleName}$SUFFIX_SYNCDAO_ABSTRACT$SUFFIX_KTOR_HELPER_LOCAL")
                    .addKtorAbstractDao(syncDaoType, syncDaoClassName,
                        LocalChangeSeqNum::class, processingEnv)
                    .build()
                    .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_KTOR_OUTPUT)

            FileSpec.builder(dbTypeEl.packageName,
                "${dbTypeEl.simpleName}${SUFFIX_SYNCDAO_ABSTRACT}${SUFFIX_KTOR_HELPER_MASTER}_${SUFFIX_JDBC_KT}")
                    .addKtorHelperDaoImplementation(syncDaoType, syncDaoClassName,
                        MasterChangeSeqNum::class, processingEnv)
                    .build()
                    .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_KTOR_OUTPUT)

            FileSpec.builder(dbTypeEl.packageName,
                    "${dbTypeEl.simpleName}${SUFFIX_SYNCDAO_ABSTRACT}${SUFFIX_KTOR_HELPER_LOCAL}_${SUFFIX_JDBC_KT}")
                    .addKtorHelperDaoImplementation(syncDaoType, syncDaoClassName,
                            LocalChangeSeqNum::class, processingEnv)
                    .build()
                    .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_KTOR_OUTPUT)

        }

        val daos = roundEnv.getElementsAnnotatedWith(Dao::class.java)
        daos.filter { !it.simpleName.endsWith(SUFFIX_SYNCDAO_ABSTRACT) }.forEach {daoElement ->
            val daoTypeEl = daoElement as TypeElement
            val daoFileSpec = generateDaoSyncHelperInterface(daoTypeEl)
            writeFileSpecToOutputDirs(daoFileSpec, AnnotationProcessorWrapper.OPTION_JVM_DIRS)
            writeFileSpecToOutputDirs(daoFileSpec, AnnotationProcessorWrapper.OPTION_ANDROID_OUTPUT,
                    useFilerAsDefault = false)
        }


        return true
    }

    /**
     * Generates an interface that will be used for
     */
    fun generateDaoSyncHelperInterface(daoType: TypeElement): FileSpec {
        val syncableEntitiesOnDao = syncableEntitiesOnDao(daoType.asClassName(), processingEnv)
        val syncHelperInterface = TypeSpec.interfaceBuilder("${daoType.simpleName}_SyncHelper")

        syncableEntitiesOnDao.forEach {
            syncHelperInterface.addFunction(
                    FunSpec.builder("_replace${it.simpleName}")
                            .addParameter("entityList", List::class.asClassName().parameterizedBy(it))
                            .addModifiers(KModifier.ABSTRACT, KModifier.SUSPEND)
                            .build())

            val entitySyncTrackerClassName = ClassName(it.packageName,
                    "${it.simpleName}$TRACKER_SUFFIX")
            syncHelperInterface.addFunction(
                    FunSpec.builder("_replace${entitySyncTrackerClassName.simpleName}")
                            .addParameter("entityTrackerList",
                                    List::class.asClassName().parameterizedBy(entitySyncTrackerClassName))
                            .addModifiers(KModifier.ABSTRACT, KModifier.SUSPEND)
                            .build())

        }

        return FileSpec.builder(pkgNameOfElement(daoType, processingEnv),
                "${daoType.simpleName}_SyncHelper")
                .addType(syncHelperInterface.build())
                .build()
    }

    companion object {

        const val SUFFIX_SYNCDAO_ABSTRACT = "SyncDao"

        const val SUFFIX_SYNCDAO_IMPL = "SyncDao_JdbcKt"

        const val SUFFIX_SYNC_ROUTE = "SyncDao_KtorRoute"

        const val SUFFIX_ENTITY_TRK = "_trk"

        /**
         * The Suffix of the generated tracker entity which is created for each entity annotated
         * with SyncableEntity
         */
        const val TRACKER_SUFFIX = "_trk"

        const val TRACKER_PK_FIELDNAME = "pk"

        const val TRACKER_ENTITY_PK_FIELDNAME = "epk"

        const val TRACKER_DESTID_FIELDNAME = "clientId"

        const val TRACKER_CHANGESEQNUM_FIELDNAME = "csn"

        const val TRACKER_RECEIVED_FIELDNAME = "rx"

        const val TRACKER_REQUESTID_FIELDNAME = "reqId"

        const val TRACKER_TIMESTAMP_FIELDNAME = "ts"

        val CLASSNAME_SYNC_HELPERENTITIES_DAO = ClassName("com.ustadmobile.door.daos",
            "SyncHelperEntitiesDao")
    }

}