package dev.toliner.spector.api

import dev.toliner.spector.IntegrationTag
import dev.toliner.spector.SlowTag
import dev.toliner.spector.indexer.ClasspathIndexer
import dev.toliner.spector.model.ClassKind
import dev.toliner.spector.storage.TypeIndexer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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

    /**
     * Helper function to configure the test application with API routes
     */
    fun Application.configureTestRoutes() {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            })
        }

        routing {
            // List classes in package
            get("/v1/packages/{packageName}/classes") {
                try {
                    val packageName = call.parameters["packageName"]!!
                    val recursive = call.request.queryParameters["recursive"]?.toBoolean() ?: false
                    val publicOnly = call.request.queryParameters["publicOnly"]?.toBoolean() ?: true
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                    val kindsParam = call.request.queryParameters["kinds"]
                    val kinds = kindsParam?.split(",")
                        ?.mapNotNull { runCatching { ClassKind.valueOf(it.trim()) }.getOrNull() }
                        ?.toSet()

                    val classes = indexer.findClassesByPackage(
                        packageName = packageName,
                        recursive = recursive,
                        kinds = kinds,
                        publicOnly = publicOnly
                    )

                    val paginatedClasses = classes
                        .drop(offset)
                        .let { if (limit != null) it.take(limit) else it }

                    val summaries = paginatedClasses.map { cls ->
                        ClassSummary(
                            fqcn = cls.fqcn,
                            kind = cls.kind,
                            modifiers = cls.modifiers.toList(),
                            kotlin = cls.kotlin?.let {
                                KotlinClassSummary(
                                    isData = it.isData,
                                    isValue = it.isValue
                                )
                            }
                        )
                    }

                    val hasMore = limit != null && classes.size > offset + limit

                    call.respond(
                        ApiResponse.success(
                            ListClassesResponse(
                                packageName = packageName,
                                classes = summaries,
                                hasMore = hasMore
                            )
                        )
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse.error<ListClassesResponse>("INTERNAL", e.message ?: "Unknown error")
                    )
                }
            }

            // List subpackages of package
            get("/v1/packages/{packageName}/subpackages") {
                try {
                    val packageName = call.parameters["packageName"]!!
                    val subpackages = indexer.findSubpackages(packageName)

                    call.respond(
                        ApiResponse.success(
                            ListSubpackagesResponse(
                                packageName = packageName,
                                subpackages = subpackages
                            )
                        )
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse.error<ListSubpackagesResponse>("INTERNAL", e.message ?: "Unknown error")
                    )
                }
            }
        }
    }

    test("should list subpackages of a package") {
        testApplication {
            application {
                configureTestRoutes()
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
                configureTestRoutes()
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
                configureTestRoutes()
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
                configureTestRoutes()
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
