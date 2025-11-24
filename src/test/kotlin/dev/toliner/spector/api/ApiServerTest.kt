package dev.toliner.spector.api

import dev.toliner.spector.Integration
import dev.toliner.spector.Slow
import dev.toliner.spector.indexer.ClasspathIndexer
import dev.toliner.spector.storage.TypeIndexer
import io.kotest.core.annotation.RequiresTag
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
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

    // Shared resources across all tests in this spec
    lateinit var indexer: TypeIndexer
    lateinit var tempDb: File

    fun createTestInheritanceHierarchy() {
        // Create test classes with inheritance relationships
        indexer.indexClass(
            dev.toliner.spector.model.ClassInfo(
                fqcn = "test.BaseInterface",
                packageName = "test",
                kind = dev.toliner.spector.model.ClassKind.INTERFACE,
                modifiers = setOf(dev.toliner.spector.model.ClassModifier.PUBLIC),
                superClass = null,
                interfaces = emptyList()
            )
        )

        indexer.indexClass(
            dev.toliner.spector.model.ClassInfo(
                fqcn = "test.DerivedInterface",
                packageName = "test",
                kind = dev.toliner.spector.model.ClassKind.INTERFACE,
                modifiers = setOf(dev.toliner.spector.model.ClassModifier.PUBLIC),
                superClass = null,
                interfaces = listOf("test.BaseInterface")
            )
        )

        indexer.indexClass(
            dev.toliner.spector.model.ClassInfo(
                fqcn = "test.AbstractBase",
                packageName = "test",
                kind = dev.toliner.spector.model.ClassKind.CLASS,
                modifiers = setOf(dev.toliner.spector.model.ClassModifier.PUBLIC, dev.toliner.spector.model.ClassModifier.ABSTRACT),
                superClass = null,
                interfaces = listOf("test.BaseInterface")
            )
        )

        indexer.indexClass(
            dev.toliner.spector.model.ClassInfo(
                fqcn = "test.ConcreteChild",
                packageName = "test",
                kind = dev.toliner.spector.model.ClassKind.CLASS,
                modifiers = setOf(dev.toliner.spector.model.ClassModifier.PUBLIC),
                superClass = "test.AbstractBase",
                interfaces = listOf("test.DerivedInterface")
            )
        )

        indexer.indexClass(
            dev.toliner.spector.model.ClassInfo(
                fqcn = "test.AnotherChild",
                packageName = "test",
                kind = dev.toliner.spector.model.ClassKind.CLASS,
                modifiers = setOf(dev.toliner.spector.model.ClassModifier.PUBLIC),
                superClass = "test.AbstractBase",
                interfaces = emptyList()
            )
        )

        // Add some members for testing inherited members
        indexer.indexMember(
            dev.toliner.spector.model.MethodInfo(
                ownerFqcn = "test.AbstractBase",
                name = "baseMethod",
                returnType = dev.toliner.spector.model.TypeRef.primitiveType("void"),
                parameters = emptyList(),
                visibility = dev.toliner.spector.model.Visibility.PUBLIC,
                static = false,
                isFinal = false,
                isAbstract = false,
                isSynthetic = false,
                isConstructor = false,
                jvmDesc = "()V",
                annotations = emptyList(),
                kotlin = null
            )
        )

        indexer.indexMember(
            dev.toliner.spector.model.MethodInfo(
                ownerFqcn = "test.ConcreteChild",
                name = "childMethod",
                returnType = dev.toliner.spector.model.TypeRef.primitiveType("void"),
                parameters = emptyList(),
                visibility = dev.toliner.spector.model.Visibility.PUBLIC,
                static = false,
                isFinal = false,
                isAbstract = false,
                isSynthetic = false,
                isConstructor = false,
                jvmDesc = "()V",
                annotations = emptyList(),
                kotlin = null
            )
        )
    }

    beforeSpec {
        println(System.getProperties())
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

        // Create test classes for inheritance hierarchy testing
        createTestInheritanceHierarchy()
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

    test("should get class hierarchy") {
        testApplication {
            application {
                configureApi(indexer)
            }

            val json = Json { ignoreUnknownKeys = true }

            // Test with our test class hierarchy
            val response = client.get("/v1/classes/test.ConcreteChild/hierarchy")

            response.status shouldBe HttpStatusCode.OK
            val body = json.decodeFromString<ApiResponse<ClassHierarchyResponse>>(response.bodyAsText())

            body.ok shouldBe true
            val hierarchy = body.data!!

            hierarchy.fqcn shouldBe "test.ConcreteChild"
            hierarchy.superClass shouldBe "test.AbstractBase"
            hierarchy.superclassChain.shouldNotBeEmpty()
            hierarchy.superclassChain shouldContain "test.AbstractBase"
            hierarchy.allInterfaces.shouldNotBeEmpty()
            hierarchy.allInterfaces shouldContain "test.BaseInterface"
            hierarchy.allInterfaces shouldContain "test.DerivedInterface"
        }
    }

    test("should get class hierarchy with subclasses") {
        testApplication {
            application {
                configureApi(indexer)
            }

            val json = Json { ignoreUnknownKeys = true }

            val response = client.get("/v1/classes/test.AbstractBase/hierarchy") {
                parameter("includeSubclasses", true)
            }

            response.status shouldBe HttpStatusCode.OK
            val body = json.decodeFromString<ApiResponse<ClassHierarchyResponse>>(response.bodyAsText())

            body.ok shouldBe true
            val hierarchy = body.data!!

            hierarchy.fqcn shouldBe "test.AbstractBase"
            hierarchy.directSubclasses!!.shouldNotBeEmpty()
            // ConcreteChild and AnotherChild should be subclasses of AbstractBase
            hierarchy.directSubclasses!! shouldContain "test.ConcreteChild"
            hierarchy.directSubclasses!! shouldContain "test.AnotherChild"
        }
    }

    test("should return 404 for non-existent class hierarchy") {
        testApplication {
            application {
                configureApi(indexer)
            }

            val response = client.get("/v1/classes/com.example.NonExistent/hierarchy")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("should get direct subclasses") {
        testApplication {
            application {
                configureApi(indexer)
            }

            val json = Json { ignoreUnknownKeys = true }

            val response = client.get("/v1/classes/test.AbstractBase/subclasses") {
                parameter("recursive", false)
            }

            response.status shouldBe HttpStatusCode.OK
            val body = json.decodeFromString<ApiResponse<ListSubclassesResponse>>(response.bodyAsText())

            body.ok shouldBe true
            val data = body.data!!

            data.fqcn shouldBe "test.AbstractBase"
            data.directSubclasses.shouldNotBeEmpty()
            data.directSubclasses shouldContain "test.ConcreteChild"
            data.directSubclasses shouldContain "test.AnotherChild"
            data.allSubclasses shouldBe null // Not requested
        }
    }

    test("should get all subclasses recursively") {
        testApplication {
            application {
                configureApi(indexer)
            }

            val json = Json { ignoreUnknownKeys = true }

            val response = client.get("/v1/classes/test.AbstractBase/subclasses") {
                parameter("recursive", true)
            }

            response.status shouldBe HttpStatusCode.OK
            val body = json.decodeFromString<ApiResponse<ListSubclassesResponse>>(response.bodyAsText())

            body.ok shouldBe true
            val data = body.data!!

            data.fqcn shouldBe "test.AbstractBase"
            data.directSubclasses.shouldNotBeEmpty()
            // Since our test hierarchy is shallow, recursive and direct should be the same
            data.allSubclasses!! shouldContain "test.ConcreteChild"
            data.allSubclasses!! shouldContain "test.AnotherChild"
        }
    }

    test("should get interface implementations") {
        testApplication {
            application {
                configureApi(indexer)
            }

            val json = Json { ignoreUnknownKeys = true }

            val response = client.get("/v1/interfaces/test.BaseInterface/implementations")

            response.status shouldBe HttpStatusCode.OK
            val body = json.decodeFromString<ApiResponse<ListImplementationsResponse>>(response.bodyAsText())

            body.ok shouldBe true
            val data = body.data!!

            data.interfaceFqcn shouldBe "test.BaseInterface"
            data.implementations.shouldNotBeEmpty()
            data.implementations.map { it.fqcn } shouldContain "test.AbstractBase"
        }
    }

    test("should return error when requesting implementations for non-interface") {
        testApplication {
            application {
                configureApi(indexer)
            }

            val json = Json { ignoreUnknownKeys = true }

            val response = client.get("/v1/interfaces/test.ConcreteChild/implementations")

            response.status shouldBe HttpStatusCode.BadRequest
            val body = json.decodeFromString<ApiResponse<ListImplementationsResponse>>(response.bodyAsText())

            body.ok shouldBe false
            body.error?.code shouldBe "INVALID_KIND"
        }
    }

    test("should list members with inherited=false (default behavior)") {
        testApplication {
            application {
                configureApi(indexer)
            }

            val json = Json { ignoreUnknownKeys = true }

            val response = client.get("/v1/classes/test.ConcreteChild/members") {
                parameter("inherited", false)
            }

            response.status shouldBe HttpStatusCode.OK
            val body = json.decodeFromString<ApiResponse<ListMembersResponse>>(response.bodyAsText())

            body.ok shouldBe true
            val data = body.data!!

            data.fqcn shouldBe "test.ConcreteChild"
            // Should only have childMethod, not baseMethod
            data.members.methods.map { it.name } shouldContain "childMethod"
            data.members.methods.map { it.name }.shouldNotContain("baseMethod")
        }
    }

    test("should list members with inherited=true") {
        testApplication {
            application {
                configureApi(indexer)
            }

            val json = Json { ignoreUnknownKeys = true }

            // Get members without inheritance
            val responseNoInherit = client.get("/v1/classes/test.ConcreteChild/members") {
                parameter("inherited", false)
            }
            val bodyNoInherit = json.decodeFromString<ApiResponse<ListMembersResponse>>(responseNoInherit.bodyAsText())
            val noInheritCount = bodyNoInherit.data!!.members.methods.size

            // Get members with inheritance
            val responseWithInherit = client.get("/v1/classes/test.ConcreteChild/members") {
                parameter("inherited", true)
            }
            val bodyWithInherit = json.decodeFromString<ApiResponse<ListMembersResponse>>(responseWithInherit.bodyAsText())

            // Should have both childMethod and baseMethod
            bodyWithInherit.data!!.members.methods.map { it.name } shouldContain "childMethod"
            bodyWithInherit.data!!.members.methods.map { it.name } shouldContain "baseMethod"

            // With inheritance should have more methods
            val withInheritCount = bodyWithInherit.data!!.members.methods.size
            withInheritCount shouldBeGreaterThan noInheritCount
        }
    }
})
