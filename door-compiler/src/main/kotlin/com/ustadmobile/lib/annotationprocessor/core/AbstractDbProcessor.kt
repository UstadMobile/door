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

    override fun init(p0: ProcessingEnvironment) {
        super.init(p0)
        messager = p0.messager
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