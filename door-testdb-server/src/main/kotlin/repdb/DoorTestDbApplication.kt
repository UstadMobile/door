package repdb

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.doortestdbserver.VirtualHostScope
import com.ustadmobile.door.entities.NodeIdAndAuth
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.nodeIdAuthCache
import com.ustadmobile.door.ext.sanitizeDbName
import com.ustadmobile.door.http.DoorHttpServerConfig
import com.ustadmobile.door.log.NapierDoorLogger
import com.ustadmobile.door.util.NodeIdAuthCache
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.kodein.di.*
import org.kodein.di.ktor.closestDI
import org.kodein.di.ktor.di

@Suppress("unused") //This is specified by the application.conf file as the module to run
fun Application.doorTestDbApplication() {

    Napier.base(DebugAntilog())
    install(CallLogging)

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

    val json = Json {
        encodeDefaults = true
    }


    di {
        bind<RepDb>(tag = DoorTag.TAG_DB) with scoped(VirtualHostScope.Default).singleton {
            val dbHostName = context.sanitizeDbName()
            DatabaseBuilder.databaseBuilder(RepDb::class, "jdbc:sqlite:build/tmp/${dbHostName}.sqlite",
                    1L)
                .build().also {
                    it.clearAllTables()
                    it.repDao.insert(RepEntity().apply {
                        this.rePrimaryKey = 42
                        this.reString = "Hello World"
                    })
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
        route("RepDb") {
            RepDb_KtorRoute(DoorHttpServerConfig(json = json, logger = NapierDoorLogger())) {
                val di : DI by  it.closestDI()
                di.on(it).direct.instance<RepDb>(tag = DoorTag.TAG_DB)
            }
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