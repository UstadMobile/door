package com.ustadmobile.lib.annotationprocessor.core.ext

import androidx.room.*
import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.annotation.*
import com.ustadmobile.lib.annotationprocessor.core.AbstractDbProcessor.Companion.SUFFIX_DEFAULT_RECEIVEVIEW
import kotlin.reflect.KClass


/**
 * Assuming that this KSClassDeclaration represents a Database, get a list of
 * all the DAOs that it references
 */
fun KSClassDeclaration.dbEnclosedDaos(): List<KSClassDeclaration> {

    @OptIn(KspExperimental::class)
    fun Iterable<KSTypeReference>.daos() = filter {
        it.resolve().declaration.isAnnotationPresent(DoorDao::class)
    }.map {
        it.resolve().declaration as KSClassDeclaration
    }


    val functionDaos =  getAllFunctions().toList().mapNotNull { it.returnType }
        .daos()
    val propertyDaos = getAllProperties().toList().map { it.type }
        .daos()

    return functionDaos + propertyDaos
}

/**
 * Get a list of the KSClassDeclarations for all entities on the given database
 */
@Suppress("UNCHECKED_CAST") // No other way, and it will always work
@OptIn(KspExperimental::class)
fun KSClassDeclaration.allDbEntities(): List<KSClassDeclaration> {
    val dbAnnotation = getKSAnnotationsByType(DoorDatabase::class).first()
    val annotationVal = dbAnnotation.arguments.first { it.name?.asString() == "entities" }
    val entityKsTypeList = annotationVal.value as List<KSType>
    return entityKsTypeList.map {
        it.declaration as KSClassDeclaration
    }
}

fun KSClassDeclaration.allDbDaos(): List<KSClassDeclaration> {
    return allDbClassDaoGetters().mapNotNull {
        it.propertyOrReturnType()?.resolve()?.declaration as? KSClassDeclaration
    }
}


/**
 * Where the receiver KSClassDeclaration is the KSClassDeclaration for a database, this will determine if any
 * entities have the @ReplicateEntity annotation.
 *
 * This is useful to indicate whether or not replication-related code should be generated for this database.
 *
 * @receiver KSClassDeclaration for a database
 * @return True if any entity for this database has the @ReplicateEntity annotation, false otherwise
 */
fun KSClassDeclaration.dbHasReplicationEntities() : Boolean {
    return allDbEntities().any { it.hasAnnotation(ReplicateEntity::class) }
}

/**
 * Determines if this database has any DAOs that contain RunOnChange or RunOnNewNode triggers. This determines
 * if the DAO should have a ReplicationRunOnChange_Runner generated.
 */
fun KSClassDeclaration.dbHasRunOnChangeTriggers() = allDbDaos().any {
    it.getAllFunctions().any {
        it.hasAnyAnnotation(ReplicationRunOnChange::class, ReplicationRunOnNewNode::class)
    }
}


fun KSClassDeclaration.allDbClassDaoGetters(): List<KSDeclaration> {
    return getAllFunctions().filter {
        it.returnType?.resolve()?.declaration?.hasAnnotation(DoorDao::class) == true
    }.toList() + getAllProperties().filter {
        it.type.resolve().declaration.hasAnnotation(DoorDao::class)
    }.toList()
}

fun KSClassDeclaration.findDbGetterForDao(daoKSClassDeclaration: KSClassDeclaration): KSDeclaration? {
    return getAllFunctions().firstOrNull {
        it.returnType?.resolve()?.declaration?.qualifiedName?.asString() ==
                daoKSClassDeclaration.qualifiedName?.asString()
    } ?: getAllProperties().firstOrNull {
        it.type.resolve().declaration.qualifiedName?.asString() ==
                daoKSClassDeclaration.qualifiedName?.asString()
    }
}

/**
 * Get a list of all functions (recursive, including all supertypes).
 **/
fun KSClassDeclaration.getAllFunctionsIncSuperTypes(
    filterFn: (KSFunctionDeclaration) -> Boolean = {true},
) : List<KSFunctionDeclaration> {
    return getAllFunctions().toList().filter(filterFn)
}

/**
 * Find all functions that have a given annotation class (by default including all supertypes).
 */
fun <T: Annotation> KSClassDeclaration.getAllFunctionsIncSuperTypesWithAnnotation(
    vararg annotationKClasses: KClass<out T>,
): List<KSFunctionDeclaration> = getAllFunctionsIncSuperTypes {
    it.hasAnyAnnotation(*annotationKClasses)
}

fun KSClassDeclaration.getAllDaoFunctionsIncSuperTypesToGenerate(): List<KSFunctionDeclaration> {
    return getAllFunctionsIncSuperTypesWithAnnotation(Query::class, Insert::class, Update::class,
        Delete::class, RawQuery::class)
}

fun KSClassDeclaration.isListDeclaration(): Boolean {
    return qualifiedName?.asString() in listOf("kotlin.collections.List", "kotlin.collections.MutableList")
}

fun KSClassDeclaration.isArrayDeclaration(): Boolean {
    return qualifiedName?.asString() == "kotlin.Array"
}

fun KSClassDeclaration.toClassNameWithSuffix(
    suffix: String
) = ClassName(packageName.asString(), simpleName.asString() + suffix)

/**
 * Where this KSClassDeclaration represents an
 */
fun KSClassDeclaration.entityHasAttachments() =  getDeclaredProperties().any { it.hasAnnotation(AttachmentUri::class) }

/**
 * Where this KSClassDeclaration represents an entity, get a list of the primary keys for this entity.
 * First look for a property annotated @PrimaryKey. If that is not present, look at the @Entity primaryKeys
 * argument
 */
val KSClassDeclaration.entityPrimaryKeyProps: List<KSPropertyDeclaration>
    get() {
        val annotatedPrimaryKey = getDeclaredProperties().firstOrNull { it.hasAnnotation(PrimaryKey::class) }
        if(annotatedPrimaryKey != null)
            return listOf(annotatedPrimaryKey)

        val entityAnnotation = getAnnotation(Entity::class)
            ?: throw IllegalArgumentException("entityPrimaryKeyProps ${qualifiedName?.asString()} has no entity annotation!")

        val primaryKeyPropNames = if(entityAnnotation.primaryKeys.isNotEmpty()) {
            entityAnnotation.primaryKeys
        }else {
            getAnnotation(DoorPrimaryAutoGenerateKeyField::class)?.value?.let { arrayOf(it) } ?: emptyArray()
        }

        val allDeclaredProps = getAllProperties()
        return primaryKeyPropNames.map {
            allDeclaredProps.first { prop -> prop.simpleName.asString() == it }
        }
    }

val KSClassDeclaration.isReplicateEntityWithAutoIncPrimaryKey: Boolean
    get() {
        return hasAnnotation(ReplicateEntity::class) && getAllProperties().any {
            it.isEntityAutoGeneratePrimaryKey
        }
    }

fun KSClassDeclaration.entityProps(
    getAutoIncLast: Boolean = true,
): List<KSPropertyDeclaration> {
    val allFields = getAllProperties().toList().filter { !it.isIgnored }

    return if(getAutoIncLast) {
        val fieldsPartitioned = allFields.partition { !it.isEntityAutoGeneratePrimaryKey }
        fieldsPartitioned.first + fieldsPartitioned.second
    }else {
        allFields
    }
}

val KSClassDeclaration.entityTableName: String
    get() {
        val annotatedTableName = getKSAnnotationByType(Entity::class)?.toEntity()?.tableName
        return if(annotatedTableName != null && annotatedTableName != "")
            annotatedTableName
        else
            simpleName.asString()
    }


/**
 * Where the given TypeSpec represents an entity, generate a string for the CREATE TABLE SQL
 *
 * @param dbType Integer constant as per DoorDbType
 */
fun KSClassDeclaration.toCreateTableSql(
    dbType: Int,
    resolver: Resolver,
): String {
    var sql = "CREATE TABLE IF NOT EXISTS $entityTableName ("
    var commaNeeded = false

    var fieldAnnotatedPk: KSPropertyDeclaration? = null

    entityProps(getAutoIncLast = true).forEach { fieldProp ->
        sql += """${if(commaNeeded) "," else " "} ${fieldProp.simpleName.asString()} """
        if(fieldProp.getAnnotation(PrimaryKey::class)?.autoGenerate == true) {
            when(dbType) {
                DoorDbType.SQLITE -> sql += " INTEGER "
                DoorDbType.POSTGRES -> sql += (if(fieldProp.type.resolve() == resolver.builtIns.longType) {
                    " BIGSERIAL "
                } else {
                    " SERIAL "
                })
            }
        }else {
            sql += " ${fieldProp.type.resolve().toSqlType(dbType, resolver)} "
        }

        val pkAnnotation = fieldProp.getAnnotation(PrimaryKey::class)
        if(pkAnnotation != null || fieldProp.isEntityAutoGeneratePrimaryKey) {
            fieldAnnotatedPk = fieldProp
            sql += " PRIMARY KEY "
            if(dbType == DoorDbType.SQLITE && fieldProp.isEntityAutoGeneratePrimaryKey)
                sql += " AUTOINCREMENT "

        }

        if(!fieldProp.type.resolve().isMarkedNullable) {
            sql += " NOT NULL "
        }

        val columnInfo = fieldProp.getAnnotation(ColumnInfo::class)
        val defaultVal = columnInfo?.defaultValue
        if(columnInfo != null && defaultVal != ColumnInfo.VALUE_UNSPECIFIED) {
            //Postgres uses an actual boolean type. SQLite / Room is using an Integer with a 0 or 1 value.
            if(dbType == DoorDbType.POSTGRES && fieldProp.type.resolve() == resolver.builtIns.booleanType) {
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

    val typeElPrimaryKeyFields = entityPrimaryKeyProps
    if(typeElPrimaryKeyFields.isNotEmpty() && fieldAnnotatedPk == null) {
        sql += ", PRIMARY KEY (${typeElPrimaryKeyFields.joinToString {it.simpleName.asString() }}) "
    }

    sql += ")"

    return sql
}

fun KSClassDeclaration.getReplicationTracker(): KSClassDeclaration {
    val repAnnotation = getKSAnnotationsByType(ReplicateEntity::class).firstOrNull()
        ?: throw IllegalArgumentException("Class ${this.qualifiedName} has no replicate entity annotation")

    val annotationVal = repAnnotation.arguments.first { it.name?.asString() == "tracker" }
    return (annotationVal.value as? KSType)?.declaration as? KSClassDeclaration
        ?: throw IllegalArgumentException("Class ${this.qualifiedName} cannot resolve tracker")
}

val KSClassDeclaration.replicationTrackerForeignKey: KSPropertyDeclaration
    get() = getAllProperties().first { it.hasAnnotation(ReplicationEntityForeignKey::class) }

val KSClassDeclaration.replicationEntityReceiveViewName: String
    get() = getAnnotation(ReplicateReceiveView::class)?.name ?: (simpleName.asString() + SUFFIX_DEFAULT_RECEIVEVIEW)

fun <A:Annotation> KSClassDeclaration.firstPropWithAnnotation(annotationClass: KClass<A>): KSPropertyDeclaration {
    return getAllProperties().first { it.hasAnnotation(annotationClass) }
}

fun <A: Annotation> KSClassDeclaration.firstPropNameWithAnnotationOrNull(
    annotationClass: KClass<A>
): CodeBlock {
    return getAllProperties().firstOrNull { it.hasAnnotation(annotationClass) }?.let {
        CodeBlock.of("%S", it.simpleName.asString())
    } ?: CodeBlock.of("null")
}

fun KSClassDeclaration.asTypeParameterizedBy(
    resolver: Resolver,
    vararg types: KSType
) : KSType {
    return asType(types.map { ksType ->
        resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(ksType), Variance.INVARIANT)
    })
}

fun KSClassDeclaration.getAllColumnProperties(
    resolver: Resolver
): List<KSPropertyDeclaration> {
    return getAllProperties().filter { !it.isIgnored && it.type.resolve() in resolver.querySingularTypes() }.toList()
}

fun KSClassDeclaration.entityEmbeddedEntities(
    resolver: Resolver
): List<KSClassDeclaration> {
    return getAllProperties().toList().filter { it.hasAnnotation(Embedded::class) }.flatMap {
        val embeddedClassDecl = it.type.resolve().declaration as KSClassDeclaration
        listOf(embeddedClassDecl) + embeddedClassDecl.entityEmbeddedEntities(resolver)
    }
}

/**
 * Get a list of all the entity KSClassDeclarations that are referenced by this DAO. Used for setting originating files
 */
fun KSClassDeclaration.allDaoEntities(resolver: Resolver): List<KSClassDeclaration> {
    val classKsType = this.asType(emptyList())
    val entityList: List<KSClassDeclaration> = getAllFunctions().map { daoFun ->
        val ksFun = daoFun.asMemberOf(classKsType)
        if(daoFun.hasAnyAnnotation(Insert::class, Update::class, Delete::class)) {
            val entityClassDecl =  ksFun.firstParamEntityType(resolver).declaration as? KSClassDeclaration
            entityClassDecl?.let { listOf(it) } ?: emptyList()
        }else if(daoFun.hasAnyAnnotation(Query::class, RawQuery::class) && ksFun.hasReturnType(resolver)) {
            val returnKSClass = ksFun.returnType?.unwrapResultType(resolver)
                ?.unwrapComponentTypeIfListOrArray(resolver)?.declaration as? KSClassDeclaration
            val parentClasses = returnKSClass?.getAllSuperTypes()?.map { it.declaration }
                ?.filterIsInstance<KSClassDeclaration>()?.toList() ?: emptyList()
            (returnKSClass?.let { listOf(it) } ?: emptyList()) + parentClasses
        }else {
            emptyList()
        }
    }.toList().flatten().distinct()

    return entityList
}

