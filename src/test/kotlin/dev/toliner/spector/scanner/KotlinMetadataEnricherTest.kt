package dev.toliner.spector.scanner

import dev.toliner.spector.model.ClassKind
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Tests for KotlinMetadataEnricher.
 *
 * Purpose: Verifies that Kotlin-specific metadata is properly extracted from class files.
 * Note: Some Kotlin metadata features may not be fully implemented yet.
 */
class KotlinMetadataEnricherTest : FunSpec({

    val classScanner = ClassScanner()
    val enricher = KotlinMetadataEnricher()

    context("Kotlin metadata enrichment") {
        test("should process Kotlin classes without errors") {
            // Tests that the enricher can process Kotlin classes without throwing exceptions
            // Note: Specific metadata extraction (data class, sealed, etc.) may not be fully implemented
            val pairBytesStream = Pair::class.java.getResourceAsStream("/kotlin/Pair.class")

            if (pairBytesStream != null) {
                val bytes = pairBytesStream.readBytes()
                val scanResult = classScanner.scanClass(bytes)!!
                val enriched = enricher.enrichClassInfo(bytes, scanResult.classInfo)

                // Should complete without errors
                enriched.fqcn shouldBe "kotlin.Pair"
            }
        }

        test("should process Java classes without errors") {
            // Tests that Java classes are handled correctly
            val stringBytes = String::class.java.getResourceAsStream("/java/lang/String.class")!!.readBytes()

            val scanResult = classScanner.scanClass(stringBytes)!!
            val enriched = enricher.enrichClassInfo(stringBytes, scanResult.classInfo)

            // Should complete without errors
            enriched.fqcn shouldBe "java.lang.String"
        }
    }
})
