package dev.toliner.spector.scanner

import dev.toliner.spector.fixtures.GenericSignatureFixtures
import dev.toliner.spector.model.ClassKind
import dev.toliner.spector.model.ClassModifier
import dev.toliner.spector.model.TypeKind
import dev.toliner.spector.model.WildcardKind
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ClassScannerTest : FunSpec({

    context("Basic class scanning") {
        test("should scan a simple Java class") {
            // We'll use String class from Java stdlib as test subject
            val stringClassBytes = String::class.java.getResourceAsStream("/java/lang/String.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(stringClassBytes)

            result shouldNotBe null
            result!!.classInfo.fqcn shouldBe "java.lang.String"
            result.classInfo.packageName shouldBe "java.lang"
            result.classInfo.kind shouldBe ClassKind.CLASS
            result.classInfo.modifiers shouldContain ClassModifier.PUBLIC
            result.classInfo.modifiers shouldContain ClassModifier.FINAL
        }

        test("should scan an interface") {
            val listClassBytes = List::class.java.getResourceAsStream("/java/util/List.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(listClassBytes)

            result shouldNotBe null
            result!!.classInfo.kind shouldBe ClassKind.INTERFACE
            result.classInfo.fqcn shouldBe "java.util.List"
        }

        test("should detect superclass and interfaces") {
            val arrayListClassBytes = ArrayList::class.java.getResourceAsStream("/java/util/ArrayList.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(arrayListClassBytes)

            result shouldNotBe null
            result!!.classInfo.superClass shouldBe "java.util.AbstractList"
            result.classInfo.interfaces shouldContain "java.util.List"
        }
    }

    context("Enum and annotation scanning") {
        test("should scan an enum class") {
            // Tests that enum classes are properly detected with their kind
            val enumClassBytes = Thread.State::class.java.getResourceAsStream("/java/lang/Thread\$State.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(enumClassBytes)

            result shouldNotBe null
            result!!.classInfo.kind shouldBe ClassKind.ENUM
            result.classInfo.fqcn shouldBe "java.lang.Thread\$State"
            result.classInfo.modifiers shouldContain ClassModifier.PUBLIC
            result.classInfo.superClass shouldBe "java.lang.Enum"
        }

        test("should scan an annotation class") {
            // Tests that annotation types are properly detected
            val annotationClassBytes = Deprecated::class.java.getResourceAsStream("/java/lang/Deprecated.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(annotationClassBytes)

            result shouldNotBe null
            result!!.classInfo.kind shouldBe ClassKind.ANNOTATION
            result.classInfo.fqcn shouldBe "java.lang.Deprecated"
            result.classInfo.interfaces shouldContain "java.lang.annotation.Annotation"
        }
    }

    context("Generics and type parameters") {
        test("should scan generic classes with type parameters") {
            val mapClassBytes = Map::class.java.getResourceAsStream("/java/util/Map.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(mapClassBytes)

            result shouldNotBe null
            result!!.classInfo.fqcn shouldBe "java.util.Map"
            result.classInfo.kind shouldBe ClassKind.INTERFACE
            result.classInfo.typeParameters.map { it.name } shouldBe listOf("K", "V")
        }

        test("should scan generic classes with bounds") {
            val classClassBytes = Class::class.java.getResourceAsStream("/java/lang/Class.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(classClassBytes)

            result shouldNotBe null
            result!!.classInfo.fqcn shouldBe "java.lang.Class"
            result.classInfo.typeParameters.map { it.name } shouldBe listOf("T")
            result.classInfo.typeParameters.single().bounds.single() shouldBe
                dev.toliner.spector.model.TypeRef.classType("java.lang.Object")
        }

        test("should preserve generic field and method signatures from fixture class") {
            val fixtureClassBytes = GenericSignatureFixtures::class.java
                .getResourceAsStream("/dev/toliner/spector/fixtures/GenericSignatureFixtures.class")!!
                .readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(fixtureClassBytes)

            result shouldNotBe null
            result!!.classInfo.typeParameters.map { it.name } shouldBe listOf("T")
            result.classInfo.typeParameters.single().bounds.map { it.fqcn } shouldBe listOf(
                "java.lang.Number",
                "java.lang.Comparable"
            )

            val field = result.fields.single { it.name == "field" }
            field.type.kind shouldBe TypeKind.CLASS
            field.type.fqcn shouldBe "java.util.Map"
            field.type.args[0].fqcn shouldBe "java.lang.String"
            field.type.args[1].fqcn shouldBe "java.util.List"
            field.type.args[1].args.single().wildcard shouldBe WildcardKind.OUT
            field.type.args[1].args.single().bounds.single().fqcn shouldBe "java.lang.Number"

            val method = result.methods.single { it.name == "transform" }
            method.typeParameters.map { it.name } shouldBe listOf("E")
            method.typeParameters.single().bounds.single().fqcn shouldBe "java.lang.CharSequence"
            method.returnType.fqcn shouldBe "java.util.List"
            method.returnType.args.single().kind shouldBe TypeKind.TYPEVAR
            method.returnType.args.single().fqcn shouldBe "E"
            method.parameters.single().type.fqcn shouldBe "java.util.List"
            method.parameters.single().type.args.single().wildcard shouldBe WildcardKind.IN
            method.parameters.single().type.args.single().bounds.single().fqcn shouldBe "T"
            method.throws.single().fqcn shouldBe "java.io.IOException"
        }

        test("should preserve generic constructor parameters after synthetic outer-instance arguments") {
            val innerClassBytes = GenericSignatureFixtures.Inner::class.java
                .getResourceAsStream("/dev/toliner/spector/fixtures/GenericSignatureFixtures\$Inner.class")!!
                .readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(innerClassBytes)

            result shouldNotBe null
            val constructor = result!!.methods.single { it.isConstructor }
            constructor.parameters.size shouldBe 2
            constructor.parameters[0].type.fqcn shouldBe "dev.toliner.spector.fixtures.GenericSignatureFixtures"
            constructor.parameters[1].type.fqcn shouldBe "java.util.List"
            constructor.parameters[1].type.args.single().kind shouldBe TypeKind.TYPEVAR
            constructor.parameters[1].type.args.single().fqcn shouldBe "T"
        }
    }

    context("Modifiers detection") {
        test("should detect abstract modifier") {
            // Tests that abstract modifier is properly detected
            val abstractClassBytes = java.io.InputStream::class.java.getResourceAsStream("/java/io/InputStream.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(abstractClassBytes)

            result shouldNotBe null
            result!!.classInfo.modifiers shouldContain ClassModifier.ABSTRACT
            result.classInfo.modifiers shouldContain ClassModifier.PUBLIC
        }

        test("should detect final modifier") {
            // Tests that final modifier is properly detected (String is final)
            val finalClassBytes = String::class.java.getResourceAsStream("/java/lang/String.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(finalClassBytes)

            result shouldNotBe null
            result!!.classInfo.modifiers shouldContain ClassModifier.FINAL
        }
    }

    context("Nested and inner classes") {
        test("should scan nested classes with correct FQCN") {
            // Tests that nested classes are scanned with the correct fully qualified name
            val mapEntryBytes = Map.Entry::class.java.getResourceAsStream("/java/util/Map\$Entry.class")!!.readBytes()

            val scanner = ClassScanner()
            val result = scanner.scanClass(mapEntryBytes)

            result shouldNotBe null
            result!!.classInfo.fqcn shouldBe "java.util.Map\$Entry"
            result.classInfo.kind shouldBe ClassKind.INTERFACE
        }
    }
})
