package repdb

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.doortestdbserver.VirtualHostScope
import com.ustadmobile.door.entities.NodeIdAndAuth
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.bindNewSqliteDataSourceIfNotExisting
import com.ustadmobile.door.ext.nodeIdAuthCache
import com.ustadmobile.door.ext.sanitizeDbName
import com.ustadmobile.door.httpsql.HttpSql
import com.ustadmobile.door.util.NodeIdAuthCache
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.kodein.di.*
import org.kodein.di.ktor.closestDI
import org.kodein.di.ktor.di
import java.nio.file.Files


@Suppress("unused") //This is specified using application.conf
fun Application.doorTestDbApplication() {

    Napier.base(DebugAntilog())
    install(CallLogging)

    val attachmentDir = Files.createTempDirectory("door-testdb-server-attachments").toFile()

    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Options)
        allowHeader("door-dbversion")
        allowHeader("door-node")
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }

    install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
        gson {
            register(ContentType.Application.Json, GsonConverter())
            register(ContentType.Any, GsonConverter())
        }
    }


    di {
        bind<RepDb>(tag = DoorTag.TAG_DB) with scoped(VirtualHostScope.Default).singleton {
            val dbHostName = context.sanitizeDbName()
            DatabaseBuilder.databaseBuilder(RepDb::class, "jdbc:sqlite:build/tmp/$dbHostName.sqlite", attachmentDir = attachmentDir)
                .build().also {
                    it.clearAllTables()
                }
        }

        bind<NodeIdAuthCache>() with scoped(VirtualHostScope.Default).singleton {
            instance<RepDb>(tag = DoorTag.TAG_DB).nodeIdAuthCache
        }


        bind<NodeIdAndAuth>() with scoped(VirtualHostScope.Default).singleton {
            NodeIdAndAuth(1000, "secret")
        }

        registerContextTranslator { _ : ApplicationCall ->
            "localhost"
        }
    }


    routing {
        route("httpsql") {
            HttpSql({ call ->
                closestDI().direct.on(call).instance<RepDb>(tag = DoorTag.TAG_DB)
            }, { true }, json)
        }

        route("RepDb") {
            RepDb_KtorRoute()
        }

        get("/") {
            call.respondText { "Hello" }
        }

        get("/insertRepEntity") {
            val uid = call.request.queryParameters["rePrimaryKey"]?.toLong() ?: throw IllegalArgumentException("No rePrimaryKey")
            val str = call.request.queryParameters["reString"]

            val repDb: RepDb = closestDI().direct.on(call).instance(tag = DoorTag.TAG_DB)
            repDb.repDao.insertAsync(RepEntity().apply {
                rePrimaryKey = uid
                reString = str
            })
            call.respondText { "Inserted $uid" }
        }
    }





}