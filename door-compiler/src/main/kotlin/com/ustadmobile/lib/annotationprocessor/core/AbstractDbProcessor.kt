package com.ustadmobile.lib.annotationprocessor.core

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeName
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment


internal val CLIENT_GET_MEMBER_NAME = MemberName("io.ktor.client.request", "get")

internal val CLIENT_POST_MEMBER_NAME = MemberName("io.ktor.client.request", "post")

internal val BODY_MEMBER_NAME = MemberName("io.ktor.client.call", "body")

internal val BODY_OR_NULL_MEMBER_NAME = MemberName("com.ustadmobile.door.ext", "bodyOrNull")


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

        val CLASSNAME_PREPARED_STATEMENT = ClassName("com.ustadmobile.door.jdbc", "PreparedStatement")

        val CLASSNAME_DATASOURCE = ClassName("com.ustadmobile.door.jdbc", "DataSource")

        val CLASSNAME_ILLEGALARGUMENTEXCEPTION = ClassName("kotlin", "IllegalArgumentException")

        val CLASSNAME_ILLEGALSTATEEXCEPTION = ClassName("kotlin", "IllegalStateException")

        val MEMBERNAME_MUTABLE_LINKEDLISTOF = MemberName("com.ustadmobile.door.ext", "mutableLinkedListOf")

        val MEMBERNAME_PREPARE_AND_USE_STMT_ASYNC = MemberName("com.ustadmobile.door.ext",
            "prepareAndUseStatementAsync")

        val MEMBERNAME_PREPARE_AND_USE_STMT = MemberName("com.ustadmobile.door.ext", "prepareAndUseStatement")

        internal fun prepareAndUseStatmentMemberName(suspended: Boolean) = if(suspended)
            MEMBERNAME_PREPARE_AND_USE_STMT_ASYNC
        else
            MEMBERNAME_PREPARE_AND_USE_STMT

        const val SUFFIX_DEFAULT_RECEIVEVIEW = "_ReceiveView"



        val MEMBERNAME_EXEC_UPDATE_ASYNC = MemberName("com.ustadmobile.door.jdbc.ext", "executeUpdateAsyncKmp")

        val MEMBERNAME_ENCODED_PATH = MemberName("io.ktor.http", "encodedPath")

        val MEMBERNAME_CLIENT_SET_BODY = MemberName("io.ktor.client.request", "setBody")
    }

}