package com.ustadmobile.lib.annotationprocessor.core

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.ustadmobile.door.annotation.ReplicateEtag
import com.ustadmobile.door.annotation.ReplicateLastModified
import com.ustadmobile.lib.annotationprocessor.core.ext.entityPrimaryKeyProps
import com.ustadmobile.lib.annotationprocessor.core.ext.entityProps
import com.ustadmobile.lib.annotationprocessor.core.ext.entityTableName
import com.ustadmobile.lib.annotationprocessor.core.ext.hasAnnotation

/**
 * Determine if the given SQL runs a query that could modify a table. This will return true
 * for insert, update, delete, and replace. It will return false for other (e.g. select) queries
 */
fun String.isSQLAModifyingQuery() : Boolean {
    val queryTrim = lowercase().trim()
    return listOf("update", "delete", "insert", "replace").any { queryTrim.startsWith(it) }
}


/**
 * Shorthand to replace all named parameters in a query (e.g. :param) with ? placeholders
 */
fun String.replaceQueryNamedParamsWithQuestionMarks(
    queryNamedParams: List<String> = getSqlQueryNamedParameters()
): String {
    var newSql = this
    queryNamedParams.forEach { newSql = newSql.replace(":$it", "?") }
    return newSql
}

/**
 * For SQL with named parameters (e.g. "SELECT * FROM Table WHERE uid = :paramName") return a
 * list of all named parameters.
 *
 * @receiver String representing SQL that may have named parameters e.g. :paramName
 * @returns list of named parameters contained within the query
 */
fun String.getSqlQueryNamedParameters(): List<String> {
    val namedParams = mutableListOf<String>()
    var insideQuote = false
    var insideDoubleQuote = false
    var lastC: Char = 0.toChar()
    var startNamedParam = -1
    for (i in 0 until this.length) {
        val c = this[i]
        if (c == '\'' && lastC != '\\')
            insideQuote = !insideQuote
        if (c == '\"' && lastC != '\\')
            insideDoubleQuote = !insideDoubleQuote

        if (!insideQuote && !insideDoubleQuote) {
            if (c == ':') {
                startNamedParam = i
            } else if (!(Character.isLetterOrDigit(c) || c == '_') && startNamedParam != -1) {
                //process the parameter
                namedParams.add(this.substring(startNamedParam + 1, i))
                startNamedParam = -1
            } else if (i == this.length - 1 && startNamedParam != -1) {
                namedParams.add(this.substring(startNamedParam + 1, i + 1))
                startNamedParam = -1
            }
        }


        lastC = c
    }

    return namedParams
}

/**
 * Used where we need to test a query, but a field name does not exist because it's not inside a trigger
 * (e.g. NEW.someField). This uses a regex to avoid issues where one field name is a substring of another.
 */
fun String.replaceColNameWithDefaultValueInSql(fieldName: String, substitution: String) : String {
    return "([\\s,)(])$fieldName([\\s,)(])".toRegex().replace(this) {
        it.groupValues[1] + substitution + it.groupValues[2]
    }
}

/**
 *
 */
fun String.expandSqlTemplates(
    entityKSClass: KSClassDeclaration,
) : String{
    val entityProps = entityKSClass.entityProps(getAutoIncLast = false)

    //Where clause checks that all primary keys match
    val selectExistingWhereClause = entityKSClass.entityPrimaryKeyProps.joinToString(separator = " AND ") {
        "${entityKSClass.entityTableName}_Existing.${it.simpleName.asString()} = NEW.${it.simpleName.asString()}"
    }

    val triggerTemplateReplacements = mapOf(
        DoorJdbcProcessor.TRIGGER_TEMPLATE_TABLE_AND_FIELD_NAMES to buildString {
            append("${entityKSClass.entityTableName} (")
            append(entityProps.joinToString(separator = ", "))
            append(")")
        },
        DoorJdbcProcessor.TRIGGER_TEMPLATE_NEW_VALUES to
            entityProps.joinToString(separator = ", ") { "NEW.${it.simpleName.asString()}" },
        DoorJdbcProcessor.TRIGGER_TEMPLATE_NEW_LAST_MODIFIED_GREATER_THAN_EXISTING to buildString {
            val replicateLastModifiedPropName = entityKSClass.entityProps(false).firstOrNull {
                it.hasAnnotation(ReplicateLastModified::class)
            }?.simpleName?.asString() ?: "INVALID_TEMPLATE_NO_LAST_MODIFIED_FIELD_ON_ENTITY"

            append("""
                   NEW.$replicateLastModifiedPropName >
                       COALESCE((SELECT ${entityKSClass.entityTableName}_Existing.$replicateLastModifiedPropName
                                   FROM ${entityKSClass.entityTableName} ${entityKSClass.entityTableName}_Existing
                                  WHERE $selectExistingWhereClause), 0)
                   """)
        },
        DoorJdbcProcessor.TRIGGER_TEMPLATE_NEW_ETAG_NOT_EQUAL_TO_EXISTING to buildString {
            val etagFieldPropName = entityKSClass.entityProps(false).firstOrNull {
                it.hasAnnotation(ReplicateEtag::class)
            }?.simpleName?.asString() ?: "INVALID_NO_ETAG"

            append("""
               NEW.$etagFieldPropName != 
                   COALESCE((SELECT ${entityKSClass.entityTableName}_Existing.$etagFieldPropName
                                   FROM ${entityKSClass.entityTableName} ${entityKSClass.entityTableName}_Existing
                                  WHERE $selectExistingWhereClause), 0)
            """)
        }
    )

    var expanded = this
    triggerTemplateReplacements.forEach {
        expanded = expanded.replace(it.key, it.value)
    }
    return expanded
}

