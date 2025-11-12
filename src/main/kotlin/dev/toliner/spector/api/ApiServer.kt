package dev.toliner.spector.api

import dev.toliner.spector.storage.TypeIndexer
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.slf4j.LoggerFactory

/**
 * HTTP API server for querying type information.
 */
class ApiServer(
    private val typeIndexer: TypeIndexer,
    private val port: Int = 8080
) {
    private val logger = LoggerFactory.getLogger(ApiServer::class.java)
    private var server: ApplicationEngine? = null

    fun start(wait: Boolean = false) {
        server = embeddedServer(Netty, port = port) {
            install(ShutDownUrl.ApplicationCallPlugin) {
                shutDownUrl = "/shutdown"
                exitCodeSupplier = { 0 }
            }

            configureApi(typeIndexer)
        }

        server?.start(wait = wait)
        logger.info("API server started on port $port")
    }

    fun stop() {
        server?.stop(1000, 2000)
        logger.info("API server stopped")
    }
}
