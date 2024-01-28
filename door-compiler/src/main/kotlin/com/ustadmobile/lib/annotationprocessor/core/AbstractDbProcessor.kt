package com.ustadmobile.lib.annotationprocessor.core

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment


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

    }

}