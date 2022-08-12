package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.*
import com.squareup.kotlinpoet.*
import com.ustadmobile.door.jdbc.*
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.tools.Diagnostic
import java.io.File
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.*
import com.ustadmobile.door.annotation.*
import io.ktor.http.HttpStatusCode
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.ustadmobile.door.*
import com.ustadmobile.door.entities.ChangeLog
import com.ustadmobile.door.ext.minifySql
import com.ustadmobile.lib.annotationprocessor.core.ext.toSql
import io.github.aakira.napier.Napier
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.statement.HttpStatement
import io.ktor.http.Headers

//here in a comment because it sometimes gets removed by 'optimization of parameters'
// import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy


val SQL_NUMERIC_TYPES = listOf(BYTE, SHORT, INT, LONG, FLOAT, DOUBLE)

val PARAM_NAME_OFFSET = "offset"

val PARAM_NAME_LIMIT = "limit"

fun defaultSqlQueryVal(typeName: TypeName) = if(typeName in SQL_NUMERIC_TYPES) {
    "0"
}else if(typeName == BOOLEAN){
    "false"
}else {
    "null"
}


/**
 * Determine if the given
 */
@Deprecated("Use TypeNameExt.isHttpQueryQueryParam instead")
internal fun isQueryParam(typeName: TypeName) = typeName in QUERY_SINGULAR_TYPES
        || (typeName is ParameterizedTypeName
        && (typeName.rawType == List::class.asClassName() && typeName.typeArguments[0] in QUERY_SINGULAR_TYPES))


internal val CLIENT_GET_MEMBER_NAME = MemberName("io.ktor.client.request", "get")

internal val CLIENT_POST_MEMBER_NAME = MemberName("io.ktor.client.request", "post")

internal val BODY_MEMBER_NAME = MemberName("io.ktor.client.call", "body")

internal val BODY_OR_NULL_MEMBER_NAME = MemberName("com.ustadmobile.door.ext", "bodyOrNull")

internal val CLIENT_GET_NULLABLE_MEMBER_NAME = MemberName("com.ustadmobile.door.ext", "getOrNull")

internal val CLIENT_POST_NULLABLE_MEMBER_NAME = MemberName("com.ustadmobile.door.ext", "postOrNull")

internal val CLIENT_RECEIVE_MEMBER_NAME = MemberName("io.ktor.client.call", "receive")

internal val CLIENT_PARAMETER_MEMBER_NAME = MemberName("io.ktor.client.request", "parameter")

internal val CLIENT_HTTPSTMT_RECEIVE_MEMBER_NAME = MemberName("io.ktor.client.call", "receive")


/**
 * Will add the given codeblock, and surround it with if(varName != null) if the given typename
 * is nullable
 */
fun CodeBlock.Builder.addWithNullCheckIfNeeded(varName: String, typeName: TypeName,
                                               codeBlock: CodeBlock): CodeBlock.Builder {
    if(typeName.isNullable)
        beginControlFlow("if($varName != null)")

    add(codeBlock)

    if(typeName.isNullable)
        endControlFlow()

    return this
}


/**
 * The parent class of all processors that generate implementations for door. Child processors
 * should implement process.
 */
abstract class AbstractDbProcessor: AbstractProcessor() {

    protected lateinit var messager: Messager

    protected var dbConnection: Connection? = null

    /**
     * When we generate the code for a Query annotation function that performs an update or delete,
     * we use this so that we can match the case of the table name. This will be setup by
     * AnnotationProcessorWrapper calling processDb.
     */
    protected var allKnownEntityNames = mutableListOf<String>()

    /**
     * Provides a map that can be used to find the TypeElement for a given table name. This will be
     * setup by AnnotationProcessorWrapper calling processDb.
     */
    protected var allKnownEntityTypesMap = mutableMapOf<String, TypeElement>()

    /**
     * Initiates internal info about the databases that are being processed, then calls the main
     * process function. This is called by AnnotationProcessorWrapper
     *
     * @param annotations as per the main annotation processor process method
     * @param roundEnv as per the main annotation processor process method
     * @param dbConnection a JDBC Connection object that can be used to run queries
     * @param allKnownEntityNames as per the allKnownEntityNames property
     * @param allKnownEntityTypesMap as per the allKnownEntityTypesMap property
     */
    fun processDb(annotations: MutableSet<out TypeElement>,
                           roundEnv: RoundEnvironment,
                           dbConnection: Connection,
                           allKnownEntityNames: MutableList<String>,
                           allKnownEntityTypesMap: MutableMap<String, TypeElement>)  : Boolean {
        this.allKnownEntityNames = allKnownEntityNames
        this.allKnownEntityTypesMap = allKnownEntityTypesMap
        this.dbConnection = dbConnection
        return process(annotations, roundEnv)
    }

    override fun init(p0: ProcessingEnvironment) {
        super.init(p0)
        messager = p0.messager
    }

    /**
     * Add triggers that will insert into the ChangeLog table
     */
    protected fun CodeBlock.Builder.addReplicateEntityChangeLogTrigger(
        entityType: TypeElement,
        sqlListVar: String,
        dbProductType: Int,
    ) : CodeBlock.Builder{
        val replicateEntity = entityType.getAnnotation(ReplicateEntity::class.java)
        val primaryKeyEl = entityType.entityPrimaryKey
            ?: throw IllegalArgumentException("addReplicateEntityChangeLogTrigger ${entityType.qualifiedName} has NO PRIMARY KEY!")

        data class TriggerParams(val opName: String, val prefix: String, val opCode: Int) {
            val opPrefix = opName.lowercase().substring(0, 3)
        }

        if(dbProductType == DoorDbType.SQLITE) {
            val triggerParams = listOf(
                TriggerParams("INSERT", "NEW", ChangeLog.CHANGE_UPSERT),
                TriggerParams("UPDATE", "NEW", ChangeLog.CHANGE_UPSERT),
                TriggerParams("DELETE", "OLD", ChangeLog.CHANGE_DELETE)
            )

            triggerParams.forEach { params ->
                /*
                Note: REPLACE INTO etc. does not work because the conflict policy will be determined by the statement
                triggering this as per https://sqlite.org/lang_createtrigger.html Section 2.
                "An ON CONFLICT clause may be specified as part of an UPDATE or INSERT action within the body of the
                trigger. However if an ON CONFLICT clause is specified as part of the statement causing the trigger to
                 fire, then conflict handling policy of the outer statement is used instead."
                 */
                add("$sqlListVar += %S\n",
                    """
                CREATE TRIGGER ch_${params.opPrefix}_${replicateEntity.tableId}
                       AFTER ${params.opName} ON ${entityType.entityTableName}
                BEGIN
                       INSERT INTO ChangeLog(chTableId, chEntityPk, chType)
                       SELECT ${replicateEntity.tableId} AS chTableId, 
                              ${params.prefix}.${primaryKeyEl.simpleName} AS chEntityPk, 
                              ${params.opCode} AS chType
                        WHERE NOT EXISTS(
                              SELECT chTableId 
                                FROM ChangeLog 
                               WHERE chTableId = ${replicateEntity.tableId}
                                 AND chEntityPk = ${params.prefix}.${primaryKeyEl.simpleName}); 
                END
                """.minifySql())
            }
        }else {
            val triggerParams = listOf(
                TriggerParams("UPDATE OR INSERT", "NEW", ChangeLog.CHANGE_UPSERT),
                TriggerParams("DELETE", "OLD", ChangeLog.CHANGE_DELETE))
            triggerParams.forEach { params ->
                add("$sqlListVar += %S\n",
                    """
               CREATE OR REPLACE FUNCTION 
               ch_${params.opPrefix}_${replicateEntity.tableId}_fn() RETURNS TRIGGER AS $$
               BEGIN
               INSERT INTO ChangeLog(chTableId, chEntityPk, chType)
                       VALUES (${replicateEntity.tableId}, ${params.prefix}.${primaryKeyEl.simpleName}, ${params.opCode})
               ON CONFLICT(chTableId, chEntityPk) DO UPDATE
                       SET chType = ${params.opCode};
               RETURN NULL;
               END $$
               LANGUAGE plpgsql         
            """.minifySql())
                add("$sqlListVar += %S\n",
                    """
            CREATE TRIGGER ch_${params.opPrefix}_${replicateEntity.tableId}_trig 
                   AFTER ${params.opName} ON ${entityType.entityTableName}
                   FOR EACH ROW
                   EXECUTE PROCEDURE ch_${params.opPrefix}_${replicateEntity.tableId}_fn();
            """.minifySql())
            }


        }

        return this
    }

    /**
     * Add a ReceiveView for the given EntityTypeElement.
     */
    protected fun CodeBlock.Builder.addCreateReceiveView(
        entityTypeEl: TypeElement,
        sqlListVar: String
    ): CodeBlock.Builder {
        val trkrEl = entityTypeEl.getReplicationTracker(processingEnv)
        val receiveViewAnn = entityTypeEl.getAnnotation(ReplicateReceiveView::class.java)
        val viewName = receiveViewAnn?.name ?: "${entityTypeEl.entityTableName}$SUFFIX_DEFAULT_RECEIVEVIEW"
        val sql = receiveViewAnn?.value ?: """
            SELECT ${entityTypeEl.simpleName}.*, ${trkrEl.entityTableName}.*
              FROM ${entityTypeEl.simpleName}
                   LEFT JOIN ${trkrEl.simpleName} ON ${trkrEl.entityTableName}.${trkrEl.replicationTrackerForeignKey.simpleName} = 
                        ${entityTypeEl.entityTableName}.${entityTypeEl.entityPrimaryKey?.simpleName}
        """.minifySql()
        add("$sqlListVar += %S\n", "CREATE VIEW $viewName AS $sql")
        return this
    }



    protected fun CodeBlock.Builder.addCreateTriggersCode(
        entityType: TypeElement,
        stmtListVar: String,
        dbProductType: Int
    ): CodeBlock.Builder {
        Napier.d("Door Wrapper: addCreateTriggersCode ${entityType.simpleName}")
        entityType.getAnnotationsByType(Triggers::class.java).firstOrNull()?.value?.forEach { trigger ->
            trigger.toSql(entityType, dbProductType).forEach { sqlStr ->
                add("$stmtListVar += %S\n", sqlStr)
            }
        }

        return this
    }


    fun logMessage(kind: Diagnostic.Kind, message: String, enclosing: TypeElement? = null,
                   element: Element? = null, annotation: AnnotationMirror? = null) {
        val messageStr = "DoorDb: ${enclosing?.qualifiedName}#${element?.simpleName} $message "
        if(annotation != null && element != null) {
            messager.printMessage(kind, messageStr, element, annotation)
        }else if(element != null) {
            messager.printMessage(kind, messageStr, element)
        }else {
            messager.printMessage(kind, messageStr)
        }
    }

    /**
     * Write the given FileSpec to the directories specified in the annotation processor arguments.
     * Paths should be separated by the path separator character (platform dependent - e.g. :
     * on Unix, ; on Windows)
     */
    protected fun FileSpec.writeToDirsFromArg(argName: String, useFilerAsDefault: Boolean = true) {
        writeToDirsFromArg(listOf(argName), useFilerAsDefault)
    }

    protected fun FileSpec.writeToDirsFromArg(argNames: List<String>, useFilerAsDefault: Boolean = true) {
        val outputArgDirs = argNames.flatMap {argName ->
            processingEnv.options[argName]?.split(File.pathSeparator)
                    ?: if(useFilerAsDefault) { listOf("filer") } else { listOf() }
        }

        outputArgDirs.forEach {
            val outputPath = if(it == "filer") {
                processingEnv.options["kapt.kotlin.generated"]!!
            }else {
                it
            }

            writeTo(File(outputPath))
        }
    }

    companion object {
        val CLASSNAME_CONNECTION = ClassName("com.ustadmobile.door.jdbc", "Connection")

        val CLASSNAME_PREPARED_STATEMENT = ClassName("com.ustadmobile.door.jdbc", "PreparedStatement")

        val CLASSNAME_STATEMENT = ClassName("com.ustadmobile.door.jdbc", "Statement")

        val CLASSNAME_SQLEXCEPTION = ClassName("com.ustadmobile.door.jdbc", "SQLException")

        val CLASSNAME_DATASOURCE = ClassName("com.ustadmobile.door.jdbc", "DataSource")

        val CLASSNAME_EXCEPTION = ClassName("kotlin", "Exception")

        val CLASSNAME_RUNTIME_EXCEPTION = ClassName("kotlin", "RuntimeException")

        val CLASSNAME_ILLEGALARGUMENTEXCEPTION = ClassName("kotlin", "IllegalArgumentException")

        val CLASSNAME_ILLEGALSTATEEXCEPTION = ClassName("kotlin", "IllegalStateException")

        val MEMBERNAME_ASYNC_QUERY = MemberName("com.ustadmobile.door.jdbc.ext", "executeQueryAsyncKmp")

        val MEMBERNAME_RESULTSET_USERESULTS = MemberName("com.ustadmobile.door.jdbc.ext", "useResults")

        val MEMBERNAME_MUTABLE_LINKEDLISTOF = MemberName("com.ustadmobile.door.ext", "mutableLinkedListOf")

        val MEMBERNAME_PREPARE_AND_USE_STMT_ASYNC = MemberName("com.ustadmobile.door.ext",
            "prepareAndUseStatementAsync")

        val MEMBERNAME_PREPARE_AND_USE_STMT = MemberName("com.ustadmobile.door.ext", "prepareAndUseStatement")

        internal fun prepareAndUseStatmentMemberName(suspended: Boolean) = if(suspended)
            MEMBERNAME_PREPARE_AND_USE_STMT_ASYNC
        else
            MEMBERNAME_PREPARE_AND_USE_STMT

        const val SUFFIX_DEFAULT_RECEIVEVIEW = "_ReceiveView"

        const val PGSECTION_COMMENT_PREFIX = "/*psql"

        const val NOTPGSECTION_COMMENT_PREFIX = "--notpsql"

        const val NOTPGSECTION_END_COMMENT_PREFIX = "--endnotpsql"

        val MEMBERNAME_EXEC_UPDATE_ASYNC = MemberName("com.ustadmobile.door.jdbc.ext", "executeUpdateAsyncKmp")

        val MEMBERNAME_ENCODED_PATH = MemberName("io.ktor.http", "encodedPath")

        val MEMBERNAME_CLIENT_SET_BODY = MemberName("io.ktor.client.request", "setBody")
    }

}