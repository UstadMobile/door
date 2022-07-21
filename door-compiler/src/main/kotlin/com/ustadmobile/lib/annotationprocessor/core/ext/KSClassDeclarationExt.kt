package com.ustadmobile.lib.annotationprocessor.core.ext

import androidx.room.*
import com.google.devtools.ksp.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ClassName
import com.ustadmobile.door.annotation.AttachmentUri
import com.ustadmobile.door.annotation.ReplicateEntity
import com.ustadmobile.door.annotation.Repository
import kotlin.reflect.KClass


/**
 * Assuming that this KSClassDeclaration represents a Database, get a list of
 * all the DAOs that it references
 */
fun KSClassDeclaration.dbEnclosedDaos(): List<KSClassDeclaration> {

    @OptIn(KspExperimental::class)
    fun Iterable<KSTypeReference>.daos() = filter {
        it.resolve().declaration.isAnnotationPresent(Dao::class)
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
    val dbAnnotation = getKSAnnotationsByType(Database::class).first()
    val annotationVal = dbAnnotation.arguments.first { it.name?.asString() == "entities" }
    val entityKsTypeList = annotationVal.value as List<KSType>
    return entityKsTypeList.map {
        it.declaration as KSClassDeclaration
    }
}

/**
 * Indicates whether or not this database should have a read only wrapper generated. The ReadOnlyWrapper should be
 * be generated for databses that have repositories and replicated entities.
 *
 */
fun KSClassDeclaration.dbHasReplicateWrapper() : Boolean {
    val hasRepos = dbEnclosedDaos().any { it.hasAnnotation(Repository::class) }
    val hasReplicatedEntities = allDbEntities().any { it.hasAnnotation(ReplicateEntity::class) }
    return hasRepos && hasReplicatedEntities
}

fun KSClassDeclaration.allDbClassDaoGetters(): List<KSDeclaration> {
    return getAllFunctions().filter {
        it.returnType?.resolve()?.declaration?.hasAnnotation(Dao::class) == true
    }.toList() + getAllProperties().filter {
        it.type.resolve().declaration.hasAnnotation(Dao::class)
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

fun KSClassDeclaration.getAllFunctionsIncSuperTypesToGenerate(): List<KSFunctionDeclaration> {
    return getAllFunctionsIncSuperTypesWithAnnotation(Query::class, Insert::class, Update::class,
        Delete::class, RawQuery::class)
}

fun KSClassDeclaration.isListDeclaration(): Boolean {
    return qualifiedName?.asString() in listOf("kotlin.collections.List", "kotlin.collections.MutableList")
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
        val allDeclaredProps = getAllProperties()
        return entityAnnotation.primaryKeys.map {
            allDeclaredProps.first { prop -> prop.simpleName.asString() == it }
        }
    }

val KSClassDeclaration.isReplicateEntityWithAutoIncPrimaryKey: Boolean
    get() {
        return hasAnnotation(ReplicateEntity::class) && getAllProperties().any {
            it.getAnnotationOrNull(PrimaryKey::class)?.autoGenerate == true
        }
    }



