package repdb

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.doortestdbserver.VirtualHostScope
import com.ustadmobile.door.entities.NodeIdAndAuth
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.bindNewSqliteDataSourceIfNotExisting
import com.ustadmobile.door.ext.nodeIdAuthCache
import com.ustadmobile.door.ext.sanitizeDbName
import com.ustadmobile.door.util.NodeIdAuthCache
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import org.kodein.di.*
import org.kodein.di.ktor.DIFeature
import java.nio.file.Files
import javax.naming.InitialContext

fun Application.doorTestDbApplication() {

    Napier.base(DebugAntilog())
    install(CallLogging)

    val attachmentDir = Files.createTempDirectory("door-testdb-server-attachments").toFile()

    install(CORS) {
        method(HttpMethod.Get)
        method(HttpMethod.Post)
        method(HttpMethod.Put)
        method(HttpMethod.Options)
        header("door-dbversion")
        header("door-node")
        header(HttpHeaders.ContentType)
        anyHost()
    }


    install(DIFeature) {
        bind<RepDb>(tag = DoorTag.TAG_DB) with scoped(VirtualHostScope.Default).singleton {
            val dbHostName = context.sanitizeDbName()
            InitialContext().bindNewSqliteDataSourceIfNotExisting(dbHostName)
            DatabaseBuilder.databaseBuilder(Any(), RepDb::class, dbHostName, attachmentDir)
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
            RepDb_KtorRoute()
        }

        get("/") {
            call.respondText { "Hello" }
        }
    }





}