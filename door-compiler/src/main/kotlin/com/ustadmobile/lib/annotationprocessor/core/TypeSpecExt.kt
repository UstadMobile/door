package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.ustadmobile.door.DoorDbType
import java.util.*
import javax.lang.model.element.ExecutableElement
import com.ustadmobile.door.RepositoryConnectivityListener
import com.ustadmobile.door.TableChangeListener
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement
import kotlin.reflect.KClass
import com.ustadmobile.door.SyncListener
import com.ustadmobile.lib.annotationprocessor.core.AbstractDbProcessor.Companion.CLASSNAME_ILLEGALSTATEEXCEPTION

/**
 * Add a method or property that overrides the given accessor. The ExecutableElement could be a
 * getter method - in which case we need to add a Kotlin property with a getter method. Otherwise we
 * add an overriding function
 */
fun TypeSpec.Builder.addAccessorOverride(methodName: String, returnType: TypeName, codeBlock: CodeBlock) {
    if(methodName.startsWith("get")) {
        val propName = methodName.substring(3, 4).lowercase() + methodName.substring(4)
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
internal fun TypeSpec.Builder.addRepositoryHelperDelegateCalls(delegatePropName: String): TypeSpec.Builder {
    addProperty(PropertySpec.builder("connectivityStatus", INT)
            .addModifiers(KModifier.OVERRIDE)
            .mutable(true)
            .getter(FunSpec.getterBuilder()
                    .addCode("return $delegatePropName.connectivityStatus\n")
                    .build())
            .setter(FunSpec.setterBuilder()
                    .addParameter("newValue", INT)
                    .addCode("$delegatePropName.connectivityStatus = newValue\n")
                    .build())
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

fun TypeSpec.Builder.addOverrideGetInvalidationTrackerVal(realDbVarName: String) : TypeSpec.Builder {
    addProperty(PropertySpec.builder("invalidationTracker", InvalidationTracker::class,
            KModifier.OVERRIDE)
        .getter(FunSpec.getterBuilder()
            .addCode("return $realDbVarName.invalidationTracker")
            .build())
        .build())
    return this
}

/**
 * Where the given TypeSpec represents an entity, generate a string for the CREATE TABLE SQL
 *
 * @param dbType Integer constant as per DoorDbType
 */
fun TypeSpec.toCreateTableSql(
    dbType: Int,
    packageName: String,
    processingEnv: ProcessingEnvironment
): String {
    var sql = "CREATE TABLE IF NOT EXISTS ${name} ("
    var commaNeeded = false

    var fieldAnnotatedPk: PropertySpec? = null
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
            fieldAnnotatedPk = fieldEl
            sql += " PRIMARY KEY "
            if(pkAutoGenerate && dbType == DoorDbType.SQLITE)
                sql += " AUTOINCREMENT "

        }

        if(!fieldEl.type.isNullableAsSelectReturnResult) {
            sql += " NOT NULL "
        }

        val columnInfoSpec = fieldEl.annotations.getAnnotationSpec(ColumnInfo::class.asClassName())
        val defaultVal = columnInfoSpec?.memberToString("defaultValue")
        if(defaultVal != null && defaultVal != ColumnInfo.VALUE_UNSPECIFIED) {
            //Postgres uses an actual boolean type. SQLite / Room is using an Integer with a 0 or 1 value.
            if(dbType == DoorDbType.POSTGRES && fieldEl.type == BOOLEAN) {
                sql += " DEFAULT " + if(defaultVal == "1") {
                    "true"
                }else {
                    "false"
                }
            }else {
                sql += " DEFAULT $defaultVal "
            }
        }

        commaNeeded = true
    }

    val typeEl = processingEnv.elementUtils.getTypeElement("$packageName.${this.name}")
    val typeElPrimaryKeyFields = typeEl?.entityPrimaryKeys
    if(typeElPrimaryKeyFields != null && typeElPrimaryKeyFields.isNotEmpty() && fieldAnnotatedPk == null) {
        sql += ", PRIMARY KEY (${typeElPrimaryKeyFields.joinToString()}) "
    }


    sql += ")"

    return sql
}

/**
 * Provide a list of all functions that require implementation (e.g. DAO functions etc)
 */
fun TypeSpec.functionsToImplement() = funSpecs.filter { KModifier.ABSTRACT in it.modifiers }

fun TypeSpec.Builder.addThrowExceptionOverride(
    funName: String = "clearAllTables",
    suspended: Boolean = false,
    exceptionMessage: String = "Cannot use this to run $funName",
    exceptionClass: ClassName = CLASSNAME_ILLEGALSTATEEXCEPTION
): TypeSpec.Builder {
    addFunction(FunSpec.builder(funName)
        .applyIf(suspended) {
            addModifiers(KModifier.SUSPEND)
        }
        .addModifiers(KModifier.OVERRIDE)
        .addCode("throw %T(%S)\n", exceptionClass,  exceptionMessage)
        .build())

    return this
}
