package com.ustadmobile.lib.annotationprocessor.core


/**
 * This is the annotation processor as far as the compiler sees it. It will delegate the actual
 * work to classes that are children of AbstractDbProcessor. It will create a shared SQLite database
 * where all tables have been created (so that child processors can check queries etc).
 */
class AnnotationProcessorWrapper {

    companion object {

        const val OPTION_SOURCE_PATH = "doordb_source_path"

        const val OPTION_JVM_DIRS = "doordb_jvm_out"

        const val OPTION_ANDROID_OUTPUT = "doordb_android_out"

        const val OPTION_KTOR_OUTPUT = "doordb_ktor_out"

        const val OPTION_NANOHTTPD_OUTPUT = "doordb_nanohttpd_out"

        const val OPTION_JS_OUTPUT = "doordb_js_out"

        const val OPTION_MIGRATIONS_OUTPUT = "doordb_migrations_out"

        const val OPTION_POSTGRES_TESTDB = "doordb_postgres_url"

        const val OPTION_POSTGRES_TESTUSER = "doordb_postgres_user"

        const val OPTION_POSTGRES_TESTPASS = "doordb_postgres_password"

    }

}