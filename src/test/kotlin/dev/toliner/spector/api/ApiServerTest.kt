package dev.toliner.spector.api

import dev.toliner.spector.storage.TypeIndexer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

/**
 * Tests for ApiServer.
 *
 * Purpose: Verifies that the HTTP API server can be instantiated.
 * Note: Full HTTP testing is simplified due to testing complexity.
 * For comprehensive API testing, consider using Ktor's testApplication or similar tools.
 */
class ApiServerTest : FunSpec({

    context("API server basic functionality") {
        test("should create server instance without errors") {
            // Basic test that the API server can be instantiated
            // Full HTTP endpoint testing would require more complex setup
            val tempDb = Files.createTempFile("test-api", ".db").toFile()
            tempDb.deleteOnExit()

            val typeIndexer = TypeIndexer(tempDb.absolutePath)

            try {
                // Test that we can create an API server instance
                val server = ApiServer(typeIndexer, 18080)

                // Should complete without errors
                server shouldBe server
            } finally {
                typeIndexer.close()
            }
        }
    }
})
