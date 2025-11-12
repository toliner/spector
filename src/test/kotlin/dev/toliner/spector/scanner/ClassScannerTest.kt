package dev.toliner.spector.scanner

import dev.toliner.spector.model.ClassKind
import dev.toliner.spector.model.ClassModifier
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

class ClassScannerTest : FunSpec({

    context("Basic class scanning") {
        test("should scan a simple Java class") {
            // We'll use String class from Java stdlib as test subject
            val stringClassBytes = String::class.java.getResourceAsStream("/java/lang/String.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(stringClassBytes)

            result shouldNotBe null
            result!!.fqcn shouldBe "java.lang.String"
            result.packageName shouldBe "java.lang"
            result.kind shouldBe ClassKind.CLASS
            result.modifiers shouldContain ClassModifier.PUBLIC
            result.modifiers shouldContain ClassModifier.FINAL
        }

        test("should scan an interface") {
            val listClassBytes = List::class.java.getResourceAsStream("/java/util/List.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(listClassBytes)

            result shouldNotBe null
            result!!.kind shouldBe ClassKind.INTERFACE
            result.fqcn shouldBe "java.util.List"
        }

        test("should detect superclass and interfaces") {
            val arrayListClassBytes = ArrayList::class.java.getResourceAsStream("/java/util/ArrayList.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(arrayListClassBytes)

            result shouldNotBe null
            result!!.superClass shouldBe "java.util.AbstractList"
            result.interfaces shouldContain "java.util.List"
        }
    }

    context("Enum and annotation scanning") {
        test("should scan an enum class") {
            // Tests that enum classes are properly detected with their kind
            val enumClassBytes = Thread.State::class.java.getResourceAsStream("/java/lang/Thread\$State.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(enumClassBytes)

            result shouldNotBe null
            result!!.kind shouldBe ClassKind.ENUM
            result.fqcn shouldBe "java.lang.Thread\$State"
            result.modifiers shouldContain ClassModifier.PUBLIC
            result.superClass shouldBe "java.lang.Enum"
        }

        test("should scan an annotation class") {
            // Tests that annotation types are properly detected
            val annotationClassBytes = Deprecated::class.java.getResourceAsStream("/java/lang/Deprecated.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(annotationClassBytes)

            result shouldNotBe null
            result!!.kind shouldBe ClassKind.ANNOTATION
            result.fqcn shouldBe "java.lang.Deprecated"
            result.interfaces shouldContain "java.lang.annotation.Annotation"
        }
    }

    context("Generics and type parameters") {
        test("should scan generic classes (type parameters extraction not yet implemented)") {
            // Tests that generic classes can be scanned
            // Note: Type parameter extraction from generic signatures is not yet implemented
            val mapClassBytes = Map::class.java.getResourceAsStream("/java/util/Map.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(mapClassBytes)

            result shouldNotBe null
            result!!.fqcn shouldBe "java.util.Map"
            result.kind shouldBe ClassKind.INTERFACE
            // TODO: Implement type parameter extraction from generic signatures
            // result.typeParameters.size shouldBe 2  // Map has type parameters K and V
        }

        test("should scan generic classes with bounds (type parameters extraction not yet implemented)") {
            // Tests that generic classes with bounded type parameters can be scanned
            // Note: Type parameter extraction is not yet implemented
            val classClassBytes = Class::class.java.getResourceAsStream("/java/lang/Class.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(classClassBytes)

            result shouldNotBe null
            result!!.fqcn shouldBe "java.lang.Class"
            // TODO: Implement type parameter extraction
            // result.typeParameters.size shouldBe 1  // Class has one type parameter T
        }
    }

    context("Modifiers detection") {
        test("should detect abstract modifier") {
            // Tests that abstract modifier is properly detected
            val abstractClassBytes = java.io.InputStream::class.java.getResourceAsStream("/java/io/InputStream.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(abstractClassBytes)

            result shouldNotBe null
            result!!.modifiers shouldContain ClassModifier.ABSTRACT
            result.modifiers shouldContain ClassModifier.PUBLIC
        }

        test("should detect final modifier") {
            // Tests that final modifier is properly detected (String is final)
            val finalClassBytes = String::class.java.getResourceAsStream("/java/lang/String.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(finalClassBytes)

            result shouldNotBe null
            result!!.modifiers shouldContain ClassModifier.FINAL
        }
    }

    context("Nested and inner classes") {
        test("should scan nested classes with correct FQCN") {
            // Tests that nested classes are scanned with the correct fully qualified name
            val mapEntryBytes = Map.Entry::class.java.getResourceAsStream("/java/util/Map\$Entry.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(mapEntryBytes)

            result shouldNotBe null
            result!!.fqcn shouldBe "java.util.Map\$Entry"
            result.kind shouldBe ClassKind.INTERFACE
        }
    }
})
