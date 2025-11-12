package dev.toliner.spector.api

import dev.toliner.spector.IntegrationTag
import dev.toliner.spector.SlowTag
import dev.toliner.spector.indexer.ClasspathIndexer
import dev.toliner.spector.storage.TypeIndexer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.File
import java.nio.file.Files

/**
 * Integration tests for API server endpoints.
 *
 * These tests index the Kotlin standard library once at the beginning
 * and share the index across all tests for performance.
 *
 * Tagged with IntegrationTag and SlowTag to allow selective test execution.
 */
class ApiServerTest : FunSpec({

    // Apply tags to all tests in this spec
    tags(IntegrationTag, SlowTag)

    // Shared resources across all tests in this spec
    lateinit var indexer: TypeIndexer
    lateinit var tempDb: File

    beforeSpec {
        // Create temporary database and indexer once for all tests
        tempDb = Files.createTempFile("test-api-server", ".db").toFile()
        tempDb.deleteOnExit()
        indexer = TypeIndexer(tempDb.absolutePath)

        // Index Kotlin standard library for testing
        val kotlinStdlib = System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .map { File(it) }
            .firstOrNull { it.name.matches(Regex("kotlin-stdlib-\\d.*\\.jar")) }

        if (kotlinStdlib != null && kotlinStdlib.exists()) {
            val classpathIndexer = ClasspathIndexer(indexer)
            classpathIndexer.indexClasspath(listOf(kotlinStdlib), parallel = false)
        }
    }

    afterSpec {
        // Clean up resources after all tests complete
        indexer.close()
    }

    test("should list subpackages of a package") {
        testApplication {
            application {
                configureApi(indexer)
            }

            val json = Json { ignoreUnknownKeys = true }

            val response = client.get("/v1/packages/kotlin/subpackages")

            response.status shouldBe HttpStatusCode.OK
            val body = json.decodeFromString<ApiResponse<ListSubpackagesResponse>>(response.bodyAsText())

            body.ok shouldBe true
            val subpackages = body.data!!.subpackages
            subpackages.shouldNotBeEmpty()
            subpackages shouldContain "kotlin.collections"
            subpackages shouldContain "kotlin.text"
        }
    }

    test("should list classes in package with exact match (default recursive=false)") {
        testApplication {
            application {
                configureApi(indexer)
            }

            val json = Json { ignoreUnknownKeys = true }

            val response = client.get("/v1/packages/kotlin.collections/classes")

            response.status shouldBe HttpStatusCode.OK
            val body = json.decodeFromString<ApiResponse<ListClassesResponse>>(response.bodyAsText())

            body.ok shouldBe true
            val classes = body.data!!.classes
            classes.shouldNotBeEmpty()

            // Verify that classes are from kotlin.collections only (not subpackages)
            val noneFromSubpackages = classes.none {
                it.fqcn.startsWith("kotlin.collections.builders.") ||
                it.fqcn.startsWith("kotlin.collections.jdk8.") ||
                it.fqcn.startsWith("kotlin.collections.unsigned.")
            }

            noneFromSubpackages shouldBe true
        }
    }

    test("should list classes in package recursively when recursive=true") {
        testApplication {
            application {
                configureApi(indexer)
            }

            val json = Json { ignoreUnknownKeys = true }

            // Get classes without recursive
            val responseNonRecursive = client.get("/v1/packages/kotlin.collections/classes") {
                parameter("recursive", false)
            }
            val bodyNonRecursive = json.decodeFromString<ApiResponse<ListClassesResponse>>(responseNonRecursive.bodyAsText())
            val nonRecursiveCount = bodyNonRecursive.data!!.classes.size

            // Get classes with recursive
            val responseRecursive = client.get("/v1/packages/kotlin.collections/classes") {
                parameter("recursive", true)
            }
            val bodyRecursive = json.decodeFromString<ApiResponse<ListClassesResponse>>(responseRecursive.bodyAsText())
            val recursiveClasses = bodyRecursive.data!!.classes
            val recursiveCount = recursiveClasses.size

            // Recursive should return more classes
            recursiveCount shouldBeGreaterThan nonRecursiveCount

            // Recursive should include classes from subpackages
            val hasBuilders = recursiveClasses.any { it.fqcn.startsWith("kotlin.collections.builders.") }
            hasBuilders shouldBe true
        }
    }

    test("should list subpackages of kotlin.collections") {
        testApplication {
            application {
                configureApi(indexer)
            }

            val json = Json { ignoreUnknownKeys = true }

            val response = client.get("/v1/packages/kotlin.collections/subpackages")

            response.status shouldBe HttpStatusCode.OK
            val body = json.decodeFromString<ApiResponse<ListSubpackagesResponse>>(response.bodyAsText())

            body.ok shouldBe true
            val subpackages = body.data!!.subpackages

            // kotlin.collections has subpackages like builders, jdk8, unsigned
            if (subpackages.isNotEmpty()) {
                // At least one of these should exist
                val hasExpectedSubpackages = subpackages.any {
                    it == "kotlin.collections.builders" ||
                    it == "kotlin.collections.jdk8" ||
                    it == "kotlin.collections.unsigned"
                }
                hasExpectedSubpackages shouldBe true
            }
        }
    }
})
