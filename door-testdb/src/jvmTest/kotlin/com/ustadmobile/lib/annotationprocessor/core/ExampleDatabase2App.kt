package com.ustadmobile.lib.annotationprocessor.core

import com.google.gson.Gson
import db2.ExampleDatabase2
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.GsonConverter
import io.ktor.gson.gson
import io.ktor.http.ContentType
import io.ktor.routing.Routing
import db2.ExampleDatabase2_KtorRoute
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.ext.DoorTag
import db2.ExampleEntity2
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.features.CORS
import io.ktor.features.DefaultHeaders
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.response.respond
import io.ktor.routing.get
import org.kodein.di.bind
import org.kodein.di.ktor.DIFeature
import org.kodein.di.registerContextTranslator
import org.kodein.di.scoped
import org.kodein.di.singleton
import java.io.File

private val serverDb = DatabaseBuilder.databaseBuilder(Any(), ExampleDatabase2::class, "ExampleDatabase2")
    .build() as ExampleDatabase2

fun Application.ExampleDatabase2App(devMode: Boolean = true, attachmentsDir: String = "attachments") {

    if(serverDb.exampleDao2().findByUid(5000L) == null) {
        serverDb.exampleDao2().insertAndReturnId(ExampleEntity2(uid = 5000L, name = "Initial Entry"))
    }

    val virtualHostScope = TestDbRoute.VirtualHostScope()

    install(DIFeature) {
        bind<ExampleDatabase2>(tag = DoorTag.TAG_DB) with scoped(virtualHostScope).singleton {
            serverDb
        }

        bind<Gson>() with singleton { Gson() }

        bind<String>(tag = DoorTag.TAG_ATTACHMENT_DIR) with scoped(virtualHostScope).singleton {
            attachmentsDir
        }

        registerContextTranslator { call: ApplicationCall -> "localhost" }
    }

    install(ContentNegotiation) {
        gson {
            register(ContentType.Application.Json, GsonConverter())
            register(ContentType.Any, GsonConverter())
        }
    }

    if(devMode) {
        System.err.println("WARNING: Server dev mode enabled. Clear tables allowed over http! CORS enabled")
        install(CORS) {
            method(HttpMethod.Get)
            method(HttpMethod.Post)
            method(HttpMethod.Put)
            header(HttpHeaders.ContentType)
            header(DoorConstants.HEADER_DBVERSION)
            anyHost()
        }

    }

    install(Routing) {
        ExampleDatabase2_KtorRoute()
        get("ExampleDatabase2/clearAllTables") {
            serverDb.clearAllTables()
            call.respond("OK - cleared")
        }
    }
}