package dev.toliner.spector

import dev.toliner.spector.api.ApiServer
import dev.toliner.spector.indexer.ClasspathIndexer
import dev.toliner.spector.storage.TypeIndexer
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Main entry point for the Spector JVM Type Indexer PoC.
 */
fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("Main")

    val command = args.getOrNull(0) ?: "help"

    when (command) {
        "index" -> {
            if (args.size < 3) {
                println("Usage: spector index <db-path> <classpath-entries...>")
                return
            }

            val dbPath = args[1]
            val classpathEntries = args.drop(2).map { File(it) }

            logger.info("Starting indexing to database: $dbPath")
            logger.info("Classpath entries: ${classpathEntries.size}")

            TypeIndexer(dbPath).use { indexer ->
                val classpathIndexer = ClasspathIndexer(indexer)
                classpathIndexer.indexClasspath(classpathEntries, parallel = true)
            }

            logger.info("Indexing complete!")
        }

        "serve" -> {
            if (args.size < 2) {
                println("Usage: spector serve <db-path> [port]")
                return
            }

            val dbPath = args[1]
            val port = args.getOrNull(2)?.toIntOrNull() ?: 8080

            logger.info("Starting API server on port $port with database: $dbPath")

            val indexer = TypeIndexer(dbPath)
            val server = ApiServer(indexer, port)

            // Add shutdown hook to clean up resources
            Runtime.getRuntime().addShutdownHook(Thread {
                logger.info("Shutdown hook triggered - cleaning up resources")
                server.stop()
                indexer.close()
            })

            logger.info("Server starting. Send POST request to http://localhost:$port/shutdown to stop")
            server.start(wait = true)
        }

        "index-and-serve" -> {
            if (args.size < 3) {
                println("Usage: spector index-and-serve <db-path> <classpath-entries...>")
                return
            }

            val dbPath = args[1]
            val classpathEntries = args.drop(2).map { File(it) }
            val port = 8080

            logger.info("Starting indexing to database: $dbPath")

            val indexer = TypeIndexer(dbPath)
            val classpathIndexer = ClasspathIndexer(indexer)
            classpathIndexer.indexClasspath(classpathEntries, parallel = true)

            logger.info("Indexing complete! Starting API server on port $port")

            val server = ApiServer(indexer, port)

            // Add shutdown hook to clean up resources
            Runtime.getRuntime().addShutdownHook(Thread {
                logger.info("Shutdown hook triggered - cleaning up resources")
                server.stop()
                indexer.close()
            })

            logger.info("Server starting. Send POST request to http://localhost:$port/shutdown to stop")
            server.start(wait = true)
        }

        "help" -> {
            println(
                """
                Spector - JVM Type Indexer PoC

                Commands:
                  index <db-path> <classpath-entries...>
                      Index the given classpath into the database

                  serve <db-path> [port]
                      Start the API server using an existing database
                      Default port: 8080

                  index-and-serve <db-path> <classpath-entries...>
                      Index the classpath and start the API server

                  help
                      Show this help message

                Examples:
                  # Index a project's runtime classpath
                  spector index types.db $(./gradlew -q printRuntimeCp | tail -1 | tr ':' ' ')

                  # Start the API server
                  spector serve types.db 8080

                  # Index and serve in one command
                  spector index-and-serve types.db $(./gradlew -q printRuntimeCp | tail -1 | tr ':' ' ')
                """.trimIndent()
            )
        }

        else -> {
            println("Unknown command: $command")
            println("Run 'spector help' for usage information")
        }
    }
}
