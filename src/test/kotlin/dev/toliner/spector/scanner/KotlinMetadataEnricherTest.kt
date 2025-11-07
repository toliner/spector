package dev.toliner.spector.scanner

import dev.toliner.spector.model.ClassInfo
import dev.toliner.spector.model.ClassKind
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldNotBeEmpty

// Test fixtures - simple Kotlin classes for testing
data class TestDataClass(val name: String, val age: Int)
sealed class TestSealedClass
object TestObject
class TestRegularClass

class KotlinMetadataEnricherTest : FunSpec({

    test("should enrich data class metadata") {
        // Scan the data class first
        val testClassBytes = TestDataClass::class.java.getResourceAsStream(
            "/dev/toliner/spector/scanner/TestDataClass.class"
        )!!.readBytes()

        val scanner = ClassScanner()
        val classInfo = scanner.scanClass(testClassBytes)

        classInfo.shouldNotBeNull()
        classInfo.kotlin.shouldNotBeNull()

        // Enrich with Kotlin metadata
        val enricher = KotlinMetadataEnricher()
        val enrichedInfo = enricher.enrichClassInfo(testClassBytes, classInfo)

        enrichedInfo.kotlin.shouldNotBeNull()
        enrichedInfo.kotlin!!.isData shouldBe true
        enrichedInfo.kotlin!!.isSealed shouldBe false
    }

    test("should enrich sealed class metadata") {
        val testClassBytes = TestSealedClass::class.java.getResourceAsStream(
            "/dev/toliner/spector/scanner/TestSealedClass.class"
        )!!.readBytes()

        val scanner = ClassScanner()
        val classInfo = scanner.scanClass(testClassBytes)

        classInfo.shouldNotBeNull()
        classInfo.kotlin.shouldNotBeNull()

        val enricher = KotlinMetadataEnricher()
        val enrichedInfo = enricher.enrichClassInfo(testClassBytes, classInfo)

        enrichedInfo.kotlin.shouldNotBeNull()
        enrichedInfo.kotlin!!.isSealed shouldBe true
        enrichedInfo.kotlin!!.isData shouldBe false
    }

    test("should detect Kotlin object") {
        val testClassBytes = TestObject::class.java.getResourceAsStream(
            "/dev/toliner/spector/scanner/TestObject.class"
        )!!.readBytes()

        val scanner = ClassScanner()
        val classInfo = scanner.scanClass(testClassBytes)

        classInfo.shouldNotBeNull()
        classInfo.kotlin.shouldNotBeNull()

        val enricher = KotlinMetadataEnricher()
        val enrichedInfo = enricher.enrichClassInfo(testClassBytes, classInfo)

        enrichedInfo.kind shouldBe ClassKind.KOTLIN_OBJECT
    }

    test("should extract primary constructor parameters") {
        val testClassBytes = TestDataClass::class.java.getResourceAsStream(
            "/dev/toliner/spector/scanner/TestDataClass.class"
        )!!.readBytes()

        val scanner = ClassScanner()
        val classInfo = scanner.scanClass(testClassBytes)

        classInfo.shouldNotBeNull()

        val enricher = KotlinMetadataEnricher()
        val enrichedInfo = enricher.enrichClassInfo(testClassBytes, classInfo)

        enrichedInfo.kotlin.shouldNotBeNull()
        enrichedInfo.kotlin!!.primaryConstructor.shouldNotBeNull()

        val constructor = enrichedInfo.kotlin!!.primaryConstructor!!
        constructor.parameters.size shouldBe 2

        val nameParam = constructor.parameters.find { it.name == "name" }
        nameParam.shouldNotBeNull()

        val ageParam = constructor.parameters.find { it.name == "age" }
        ageParam.shouldNotBeNull()
    }

    test("should extract Kotlin properties") {
        val testClassBytes = TestDataClass::class.java.getResourceAsStream(
            "/dev/toliner/spector/scanner/TestDataClass.class"
        )!!.readBytes()

        val scanner = ClassScanner()
        val classInfo = scanner.scanClass(testClassBytes)

        classInfo.shouldNotBeNull()

        val enricher = KotlinMetadataEnricher()
        val enrichedInfo = enricher.enrichClassInfo(testClassBytes, classInfo)

        enrichedInfo.kotlin.shouldNotBeNull()
        enrichedInfo.kotlin!!.properties.shouldNotBeEmpty()

        val nameProperty = enrichedInfo.kotlin!!.properties.find { it.name == "name" }
        nameProperty.shouldNotBeNull()

        val ageProperty = enrichedInfo.kotlin!!.properties.find { it.name == "age" }
        ageProperty.shouldNotBeNull()
    }

    test("should handle regular Kotlin class") {
        val testClassBytes = TestRegularClass::class.java.getResourceAsStream(
            "/dev/toliner/spector/scanner/TestRegularClass.class"
        )!!.readBytes()

        val scanner = ClassScanner()
        val classInfo = scanner.scanClass(testClassBytes)

        classInfo.shouldNotBeNull()
        classInfo.kotlin.shouldNotBeNull()

        val enricher = KotlinMetadataEnricher()
        val enrichedInfo = enricher.enrichClassInfo(testClassBytes, classInfo)

        enrichedInfo.kotlin.shouldNotBeNull()
        enrichedInfo.kotlin!!.isData shouldBe false
        enrichedInfo.kotlin!!.isSealed shouldBe false
        enrichedInfo.kind shouldBe ClassKind.CLASS
    }

    test("should enrich Kotlin stdlib Pair class") {
        // Pair is a Kotlin data class from stdlib
        val pairClassBytes = Pair::class.java.getResourceAsStream(
            "/kotlin/Pair.class"
        )!!.readBytes()

        val scanner = ClassScanner()
        val classInfo = scanner.scanClass(pairClassBytes)

        classInfo.shouldNotBeNull()
        classInfo.kotlin.shouldNotBeNull()

        val enricher = KotlinMetadataEnricher()
        val enrichedInfo = enricher.enrichClassInfo(pairClassBytes, classInfo)

        enrichedInfo.kotlin.shouldNotBeNull()
        enrichedInfo.kotlin!!.isData shouldBe true
        enrichedInfo.kotlin!!.primaryConstructor.shouldNotBeNull()
        enrichedInfo.kotlin!!.primaryConstructor!!.parameters.size shouldBe 2
    }

    test("should not crash on non-Kotlin classes") {
        // Test with a Java class
        val stringClassBytes = String::class.java.getResourceAsStream("/java/lang/String.class")!!.readBytes()

        val scanner = ClassScanner()
        val classInfo = scanner.scanClass(stringClassBytes)

        classInfo.shouldNotBeNull()
        // String is not a Kotlin class, so kotlin info should be null
        classInfo.kotlin shouldBe null

        val enricher = KotlinMetadataEnricher()
        val enrichedInfo = enricher.enrichClassInfo(stringClassBytes, classInfo)

        // Should return the same classInfo unchanged
        enrichedInfo shouldBe classInfo
    }
})
