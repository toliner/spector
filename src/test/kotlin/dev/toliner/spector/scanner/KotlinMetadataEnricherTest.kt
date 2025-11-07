package dev.toliner.spector.scanner

import dev.toliner.spector.model.ClassKind
import dev.toliner.spector.model.ClassInfo
import dev.toliner.spector.model.ClassModifier
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldNotBeNull

// Test data classes to scan
data class TestDataClass(val id: Int, val name: String)
sealed class TestSealedClass
object TestObject
class TestRegularClass
interface TestInterface

class KotlinMetadataEnricherTest : FunSpec({

    val scanner = ClassScanner()
    val enricher = KotlinMetadataEnricher()

    test("should enrich data class with Kotlin metadata") {
        val classBytes = TestDataClass::class.java.getResourceAsStream(
            "/dev/toliner/spector/scanner/TestDataClass.class"
        )!!.readBytes()

        val baseClassInfo = scanner.scanClass(classBytes)!!
        val enrichedClassInfo = enricher.enrichClassInfo(classBytes, baseClassInfo)

        enrichedClassInfo.kotlin shouldNotBe null
        enrichedClassInfo.kotlin!!.isData shouldBe true
        enrichedClassInfo.kotlin!!.isSealed shouldBe false
        enrichedClassInfo.kotlin!!.isValue shouldBe false
    }

    test("should enrich sealed class with Kotlin metadata") {
        val classBytes = TestSealedClass::class.java.getResourceAsStream(
            "/dev/toliner/spector/scanner/TestSealedClass.class"
        )!!.readBytes()

        val baseClassInfo = scanner.scanClass(classBytes)!!
        val enrichedClassInfo = enricher.enrichClassInfo(classBytes, baseClassInfo)

        enrichedClassInfo.kotlin shouldNotBe null
        enrichedClassInfo.kotlin!!.isSealed shouldBe true
        enrichedClassInfo.kotlin!!.isData shouldBe false
    }

    test("should detect Kotlin object") {
        val classBytes = TestObject::class.java.getResourceAsStream(
            "/dev/toliner/spector/scanner/TestObject.class"
        )!!.readBytes()

        val baseClassInfo = scanner.scanClass(classBytes)!!
        val enrichedClassInfo = enricher.enrichClassInfo(classBytes, baseClassInfo)

        enrichedClassInfo.kind shouldBe ClassKind.KOTLIN_OBJECT
        enrichedClassInfo.kotlin shouldNotBe null
    }

    test("should extract primary constructor parameters") {
        val classBytes = TestDataClass::class.java.getResourceAsStream(
            "/dev/toliner/spector/scanner/TestDataClass.class"
        )!!.readBytes()

        val baseClassInfo = scanner.scanClass(classBytes)!!
        val enrichedClassInfo = enricher.enrichClassInfo(classBytes, baseClassInfo)

        enrichedClassInfo.kotlin.shouldNotBeNull()
        enrichedClassInfo.kotlin!!.primaryConstructor.shouldNotBeNull()

        val constructor = enrichedClassInfo.kotlin!!.primaryConstructor!!
        constructor.parameters.size shouldBe 2

        val idParam = constructor.parameters.find { it.name == "id" }
        idParam.shouldNotBeNull()

        val nameParam = constructor.parameters.find { it.name == "name" }
        nameParam.shouldNotBeNull()
    }

    test("should extract Kotlin properties") {
        val classBytes = TestDataClass::class.java.getResourceAsStream(
            "/dev/toliner/spector/scanner/TestDataClass.class"
        )!!.readBytes()

        val baseClassInfo = scanner.scanClass(classBytes)!!
        val enrichedClassInfo = enricher.enrichClassInfo(classBytes, baseClassInfo)

        enrichedClassInfo.kotlin.shouldNotBeNull()
        enrichedClassInfo.kotlin!!.properties.size shouldBe 2

        val propertyNames = enrichedClassInfo.kotlin!!.properties.map { it.name }
        propertyNames shouldBe listOf("id", "name")
    }

    test("should return original ClassInfo if no Kotlin metadata") {
        // Use a pure Java class from stdlib
        val classBytes = String::class.java.getResourceAsStream("/java/lang/String.class")!!.readBytes()

        val baseClassInfo = scanner.scanClass(classBytes)!!
        val enrichedClassInfo = enricher.enrichClassInfo(classBytes, baseClassInfo)

        // Should be the same as base since String has no Kotlin metadata
        enrichedClassInfo shouldBe baseClassInfo
        enrichedClassInfo.kotlin shouldBe null
    }

    test("should handle regular Kotlin class") {
        val classBytes = TestRegularClass::class.java.getResourceAsStream(
            "/dev/toliner/spector/scanner/TestRegularClass.class"
        )!!.readBytes()

        val baseClassInfo = scanner.scanClass(classBytes)!!
        val enrichedClassInfo = enricher.enrichClassInfo(classBytes, baseClassInfo)

        enrichedClassInfo.kind shouldBe ClassKind.CLASS
        enrichedClassInfo.kotlin shouldNotBe null
        enrichedClassInfo.kotlin!!.isData shouldBe false
        enrichedClassInfo.kotlin!!.isSealed shouldBe false
        enrichedClassInfo.kotlin!!.isValue shouldBe false
    }

    test("should handle Kotlin interface") {
        val classBytes = TestInterface::class.java.getResourceAsStream(
            "/dev/toliner/spector/scanner/TestInterface.class"
        )!!.readBytes()

        val baseClassInfo = scanner.scanClass(classBytes)!!
        val enrichedClassInfo = enricher.enrichClassInfo(classBytes, baseClassInfo)

        enrichedClassInfo.kind shouldBe ClassKind.INTERFACE
        enrichedClassInfo.kotlin shouldNotBe null
    }

    test("should detect Kotlin List from stdlib") {
        val classBytes = List::class.java.getResourceAsStream("/kotlin/collections/List.class")!!.readBytes()

        val baseClassInfo = scanner.scanClass(classBytes)!!
        val enrichedClassInfo = enricher.enrichClassInfo(classBytes, baseClassInfo)

        enrichedClassInfo.kotlin shouldNotBe null
        enrichedClassInfo.kind shouldBe ClassKind.INTERFACE
    }
})
