package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Database
import androidx.room.PrimaryKey
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.ustadmobile.door.DoorDbType
import java.util.*
import javax.lang.model.element.ExecutableElement
import com.ustadmobile.door.RepositoryConnectivityListener
import com.ustadmobile.door.TableChangeListener
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement
import androidx.room.Query
import kotlin.reflect.KClass
import com.ustadmobile.door.SyncListener

/**
 * Add a method or property that overrides the given accessor. The ExecutableElement could be a
 * getter method - in which case we need to add a Kotlin property with a getter method. Otherwise we
 * add an overriding function
 */
fun TypeSpec.Builder.addAccessorOverride(methodName: String, returnType: TypeName, codeBlock: CodeBlock) {
    if(methodName.startsWith("get")) {
        val propName = methodName.substring(3, 4).toLowerCase(Locale.ROOT) + methodName.substring(4)
        val getterFunSpec = FunSpec.getterBuilder().addCode(codeBlock)
        addProperty(PropertySpec.builder(propName, returnType,
                KModifier.OVERRIDE).getter(getterFunSpec.build()).build())
    }else {
        addFunction(FunSpec.builder(methodName)
                .addModifiers(KModifier.OVERRIDE)
                .returns(returnType)
                .addCode(codeBlock)
                .build())
    }
}

fun TypeSpec.Builder.addAccessorOverride(executableElement: ExecutableElement, codeBlock: CodeBlock)  =
        addAccessorOverride(executableElement.simpleName.toString(), executableElement.returnType.asTypeName(), codeBlock)

/**
 * Implement the DoorDatabaseRepository methods for add/remove mirror etc. by delegating to a
 * RepositoryHelper.
 */
internal fun TypeSpec.Builder.addRepositoryHelperDelegateCalls(delegatePropName: String,
    clientSyncMgrVarName: String? = null): TypeSpec.Builder {
    addProperty(PropertySpec.builder("connectivityStatus", INT)
            .addModifiers(KModifier.OVERRIDE)
            .mutable(true)
            .getter(FunSpec.getterBuilder()
                    .addCode("return $delegatePropName.connectivityStatus\n")
                    .build())
            .setter(FunSpec.setterBuilder()
                    .addParameter("newValue", INT)
                    .addCode("$delegatePropName.connectivityStatus = newValue\n")
                    .apply { takeIf { clientSyncMgrVarName != null }
                            ?.addCode("$clientSyncMgrVarName?.connectivityStatus = newValue\n") }
                    .build())
            .build())
    addFunction(FunSpec.builder("addMirror")
            .returns(INT)
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter("mirrorEndpoint", String::class)
            .addParameter("initialPriority", INT)
            .addCode("return $delegatePropName.addMirror(mirrorEndpoint, initialPriority)\n")
            .build())
    addFunction(FunSpec.builder("removeMirror")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter("mirrorId", INT)
            .addCode("$delegatePropName.removeMirror(mirrorId)\n")
            .build())
    addFunction(FunSpec.builder("updateMirrorPriorities")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter("newPriorities", Map::class.asClassName().parameterizedBy(INT, INT))
            .addCode("$delegatePropName.updateMirrorPriorities(newPriorities)\n")
            .build())
    addFunction(FunSpec.builder("activeMirrors")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addCode("return $delegatePropName.activeMirrors()\n")
            .build())
    addFunction(FunSpec.builder("addWeakConnectivityListener")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("listener", RepositoryConnectivityListener::class)
            .addCode("$delegatePropName.addWeakConnectivityListener(listener)\n")
            .build())
    addFunction(FunSpec.builder("removeWeakConnectivityListener")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("listener", RepositoryConnectivityListener::class)
            .addCode("$delegatePropName.removeWeakConnectivityListener(listener)\n")
            .build())
    addFunction(FunSpec.builder("addTableChangeListener")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("listener", TableChangeListener::class)
            .addCode("$delegatePropName.addTableChangeListener(listener)\n")
            .build())
    addFunction(FunSpec.builder("removeTableChangeListener")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("listener", TableChangeListener::class)
            .addCode("$delegatePropName.removeTableChangeListener(listener)\n")
            .build())
    addFunction(FunSpec.builder("handleTableChanged")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("tableName", String::class)
            .addCode("$delegatePropName.handleTableChanged(tableName)\n")
            .build())

    val syncListenerTypeVar = TypeVariableName.Companion.invoke("T", Any::class)
    addFunction(FunSpec.builder("addSyncListener")
            .addModifiers(KModifier.OVERRIDE)
            .addTypeVariable(syncListenerTypeVar)
            .addParameter("entityClass", KClass::class.asClassName().parameterizedBy(syncListenerTypeVar))
            .addParameter("listener", SyncListener::class.asClassName().parameterizedBy(syncListenerTypeVar))
            .addCode("$delegatePropName.addSyncListener(entityClass, listener)\n")
            .build())

    addFunction(FunSpec.builder("removeSyncListener")
        .addModifiers(KModifier.OVERRIDE)
        .addTypeVariable(syncListenerTypeVar)
        .addParameter("entityClass", KClass::class.asClassName().parameterizedBy(syncListenerTypeVar))
        .addParameter("listener", SyncListener::class.asClassName().parameterizedBy(syncListenerTypeVar))
        .addCode("$delegatePropName.removeSyncListener(entityClass, listener)\n")
        .build())

    addFunction(FunSpec.builder("handleSyncEntitiesReceived")
        .addModifiers(KModifier.OVERRIDE)
        .addTypeVariable(syncListenerTypeVar)
        .addParameter("entityClass", KClass::class.asClassName().parameterizedBy(syncListenerTypeVar))
        .addParameter("entitiesIncoming", List::class.asClassName().parameterizedBy(syncListenerTypeVar))
        .addCode("$delegatePropName.handleSyncEntitiesReceived(entityClass, entitiesIncoming)\n")
        .build())


    return this
}

/**
 * Add a property
 */
fun TypeSpec.Builder.addDbVersionProperty(dbTypeElement: TypeElement): TypeSpec.Builder {
    addProperty(PropertySpec.builder("dbVersion", INT)
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                    .addCode("return ${dbTypeElement.getAnnotation(Database::class.java).version}")
                    .build())
            .build())

    return this
}

/**
 * Add an override for the room database createOpenHelper function which is required for any
 * child class of the database on Android
 */
fun TypeSpec.Builder.addRoomDatabaseCreateOpenHelperFunction() : TypeSpec.Builder {
    addFunction(FunSpec.builder("createOpenHelper")
            .addParameter("config", ClassName("androidx.room", "DatabaseConfiguration"))
            .returns(ClassName("androidx.sqlite.db", "SupportSQLiteOpenHelper"))
            .addModifiers(KModifier.OVERRIDE, KModifier.PROTECTED)
            .addCode("throw IllegalAccessException(%S)\n", "Cannot use open helper on repository")
            .build())

    return this
}

/**
 * Add an override for the room database createInvalidationTracker which is required for any
 * child class of the database on Android
 */
fun TypeSpec.Builder.addRoomCreateInvalidationTrackerFunction() : TypeSpec.Builder {
    addFunction(FunSpec.builder("createInvalidationTracker")
            .returns(ClassName("androidx.room", "InvalidationTracker"))
            .addModifiers(KModifier.OVERRIDE, KModifier.PROTECTED)
            .addCode("return %T.createDummyInvalidationTracker(this)\n",
                    ClassName("com.ustadmobile.door","DummyInvalidationTracker"))
            .build())

    return this
}

/**
 * Returns a list of the entity fields of a particular object. If getAutoIncLast is true, then
 * any autoincrement primary key will always be returned at the end of the list, e.g. so that a
 * preparedstatement insert with or without an autoincrement id can share the same code to set
 * all other parameters.
 *
 * @param entityTypeElement The TypeElement representing the entity, from which we wish to get
 * the field names
 * @param getAutoIncLast if true, then always return any field that is auto increment at the very end
 * @return List of VariableElement representing the entity fields that are persisted
 */
fun TypeSpec.entityFields(getAutoIncLast: Boolean = true): List<PropertySpec> {
    val propertyList = propertySpecs.toMutableList()

    if(getAutoIncLast) {
        val autoIncPropIdx = propertyList
                .indexOfFirst { it.annotations.any { it.className == PrimaryKey::class.asClassName()
                        && it.members.any { it.toString().contains("autoGenerate") }} }

        if(autoIncPropIdx >= 0) {
            val autoIncField = propertyList.removeAt(autoIncPropIdx)
            propertyList.add(autoIncField)
        }
    }

    return propertyList
}

/**
 * If code actually wants to use the invalidation tracker, it should use the real database invalidation tracker, not
 * a dummy.
 */
fun TypeSpec.Builder.addOverrideGetRoomInvalidationTracker(realDbVarName: String) : TypeSpec.Builder {
    addFunction(FunSpec.builder("getInvalidationTracker")
        .returns(ClassName("androidx.room", "InvalidationTracker"))
        .addModifiers(KModifier.OVERRIDE)
        .addCode("return $realDbVarName.getInvalidationTracker()\n")
        .build())
    return this
}

/**
 * Where the given TypeSpec represents an entity, generate a string for the CREATE TABLE SQL
 *
 * @param dbType Integer constant as per DoorDbType
 */
fun TypeSpec.toCreateTableSql(dbType: Int): String {
    var sql = "CREATE TABLE IF NOT EXISTS ${name} ("
    var commaNeeded = false

    entityFields(getAutoIncLast = true).forEach {fieldEl ->
        sql += """${if(commaNeeded) "," else " "} ${fieldEl.name} """
        val pkAutoGenerate = fieldEl.annotations
                .firstOrNull { it.className == PrimaryKey::class.asClassName() }
                ?.members?.findBooleanMemberValue("autoGenerate") ?: false
        if(pkAutoGenerate) {
            when(dbType) {
                DoorDbType.SQLITE -> sql += " INTEGER "
                DoorDbType.POSTGRES -> sql += (if(fieldEl.type == LONG) { " BIGSERIAL " } else { " SERIAL " })
            }
        }else {
            sql += " ${fieldEl.type.toSqlType(dbType)} "
        }

        if(fieldEl.annotations.any { it.className == PrimaryKey::class.asClassName()} ) {
            sql += " PRIMARY KEY "
            if(pkAutoGenerate && dbType == DoorDbType.SQLITE)
                sql += " AUTOINCREMENT "

        }

        if(!fieldEl.type.isNullableAsSelectReturnResult) {
            sql += " NOT NULL "
        }

        commaNeeded = true
    }

    sql += ")"

    return sql
}

/**
 * Where the given TypeSpec represents a DAO, get a list of all the syncable entities that are
 * part of this DAO
 */
fun TypeSpec.daoSyncableEntitiesInSelectResults(processingEnv: ProcessingEnvironment) : List<ClassName> {
    val syncableEntities = mutableSetOf<ClassName>()
    funSpecs.filter { it.hasAnnotation(Query::class.java) }.forEach { funSpec ->
        val returnType = funSpec.returnType?.unwrapQueryResultComponentType()
        if (!funSpec.daoQuerySql().isSQLAModifyingQuery() && returnType is ClassName) {
            syncableEntities += returnType.entitySyncableTypes(processingEnv)
        }
    }

    return syncableEntities.toList()
}

/**
 * Returns whether or not this DAO has Syncable Entities in select results. If there are syncable
 * entities in the return type, then a SyncHelper is required.
 */
fun TypeSpec.isDaoWithSyncableEntitiesInSelectResults(processingEnv: ProcessingEnvironment): Boolean =
        daoSyncableEntitiesInSelectResults(processingEnv).isNotEmpty()

/**
 * Provide a list of all functions that require implementation (e.g. DAO functions etc)
 */
fun TypeSpec.functionsToImplement() = funSpecs.filter { KModifier.ABSTRACT in it.modifiers }


/**
 * Convenience wrapper to get a list of all FunSpecs that represent a function annotated with
 * Query that have SyncableEntity in their results
 */
fun TypeSpec.funSpecsWithSyncableSelectResults(processingEnv: ProcessingEnvironment): List<FunSpec>
        = funSpecs.filter { it.isQueryWithSyncableResults(processingEnv) }

//Add a property that will provide the required datasource abstract val by delegating to the 'real' database
fun TypeSpec.Builder.addDataSourceProperty(dbVarName: String): TypeSpec.Builder {
    addProperty(PropertySpec.builder("dataSource", AbstractDbProcessor.CLASSNAME_DATASOURCE,
        KModifier.OVERRIDE)
        .getter(FunSpec.getterBuilder()
            .addCode("return $dbVarName.dataSource\n")
            .build())
        .build())
    return this
}
