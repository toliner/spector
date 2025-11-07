package dev.toliner.spector.scanner

import dev.toliner.spector.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File

class ClassScannerTest : FunSpec({

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

    test("should scan fields from a class") {
        val stringClassBytes = String::class.java.getResourceAsStream("/java/lang/String.class")!!.readBytes()

        val scanner = ClassScanner()
        val result = scanner.scanClassWithMembers(stringClassBytes)

        result shouldNotBe null
        result!!.members.shouldNotBeEmpty()

        // String class should have at least the "value" field
        val fields = result.members.filterIsInstance<FieldInfo>()
        fields.shouldNotBeEmpty()

        // Check that we can find the value field (char[] or byte[] depending on JDK version)
        val valueField = fields.find { it.name == "value" }
        valueField shouldNotBe null
        valueField!!.ownerFqcn shouldBe "java.lang.String"
        valueField.visibility shouldBe Visibility.PRIVATE
        valueField.isFinal shouldBe true
    }

    test("should scan methods from a class") {
        val stringClassBytes = String::class.java.getResourceAsStream("/java/lang/String.class")!!.readBytes()

        val scanner = ClassScanner()
        val result = scanner.scanClassWithMembers(stringClassBytes)

        result shouldNotBe null
        result!!.members.shouldNotBeEmpty()

        val methods = result.members.filterIsInstance<MethodInfo>()
        methods.shouldNotBeEmpty()

        // String class should have a length() method
        val lengthMethod = methods.find { it.name == "length" && !it.isConstructor }
        lengthMethod shouldNotBe null
        lengthMethod!!.ownerFqcn shouldBe "java.lang.String"
        lengthMethod.visibility shouldBe Visibility.PUBLIC
        lengthMethod.returnType.kind shouldBe TypeKind.PRIMITIVE
        lengthMethod.parameters.size shouldBe 0
    }

    test("should detect constructors") {
        val stringClassBytes = String::class.java.getResourceAsStream("/java/lang/String.class")!!.readBytes()

        val scanner = ClassScanner()
        val result = scanner.scanClassWithMembers(stringClassBytes)

        result shouldNotBe null

        val methods = result.members.filterIsInstance<MethodInfo>()
        val constructors = methods.filter { it.isConstructor }

        constructors.shouldNotBeEmpty()
        constructors.forEach { constructor ->
            constructor.name shouldBe "<init>"
            constructor.isConstructor shouldBe true
        }
    }

    test("should detect static methods") {
        val stringClassBytes = String::class.java.getResourceAsStream("/java/lang/String.class")!!.readBytes()

        val scanner = ClassScanner()
        val result = scanner.scanClassWithMembers(stringClassBytes)

        result shouldNotBe null

        val methods = result.members.filterIsInstance<MethodInfo>()
        val staticMethods = methods.filter { it.static && !it.isConstructor }

        // String has static methods like valueOf
        staticMethods.shouldNotBeEmpty()

        val valueOfMethod = staticMethods.find { it.name == "valueOf" }
        valueOfMethod shouldNotBe null
        valueOfMethod!!.static shouldBe true
        valueOfMethod.visibility shouldBe Visibility.PUBLIC
    }

    test("should scan enum class") {
        val threadStateBytes = Thread.State::class.java.getResourceAsStream("/java/lang/Thread\$State.class")!!.readBytes()

        val scanner = ClassScanner()
        val result = scanner.scanClass(threadStateBytes)

        result shouldNotBe null
        result!!.kind shouldBe ClassKind.ENUM
        result.fqcn shouldBe "java.lang.Thread\$State"
    }

    test("should detect enum constants as fields") {
        val threadStateBytes = Thread.State::class.java.getResourceAsStream("/java/lang/Thread\$State.class")!!.readBytes()

        val scanner = ClassScanner()
        val result = scanner.scanClassWithMembers(threadStateBytes)

        result shouldNotBe null

        val fields = result!!.members.filterIsInstance<FieldInfo>()
        val enumConstants = fields.filter { it.isEnumConstant }

        enumConstants.shouldNotBeEmpty()
        // Thread.State should have constants like NEW, RUNNABLE, etc.
        enumConstants.any { it.name == "NEW" } shouldBe true
    }

    test("should parse method parameters") {
        val stringClassBytes = String::class.java.getResourceAsStream("/java/lang/String.class")!!.readBytes()

        val scanner = ClassScanner()
        val result = scanner.scanClassWithMembers(stringClassBytes)

        result shouldNotBe null

        val methods = result!!.members.filterIsInstance<MethodInfo>()
        // Find charAt method which takes an int parameter
        val charAtMethod = methods.find { it.name == "charAt" && !it.isConstructor }

        charAtMethod shouldNotBe null
        charAtMethod!!.parameters.size shouldBe 1
        charAtMethod.parameters[0].type.kind shouldBe TypeKind.PRIMITIVE
    }

    test("should handle abstract methods") {
        val listClassBytes = List::class.java.getResourceAsStream("/java/util/List.class")!!.readBytes()

        val scanner = ClassScanner()
        val result = scanner.scanClassWithMembers(listClassBytes)

        result shouldNotBe null

        val methods = result!!.members.filterIsInstance<MethodInfo>()
        val abstractMethods = methods.filter { it.isAbstract }

        // Interface methods are abstract
        abstractMethods.shouldNotBeEmpty()
    }

    test("should detect annotation type") {
        val deprecatedBytes = Deprecated::class.java.getResourceAsStream("/java/lang/Deprecated.class")!!.readBytes()

        val scanner = ClassScanner()
        val result = scanner.scanClass(deprecatedBytes)

        result shouldNotBe null
        result!!.kind shouldBe ClassKind.ANNOTATION
        result.fqcn shouldBe "java.lang.Deprecated"
    }
})
