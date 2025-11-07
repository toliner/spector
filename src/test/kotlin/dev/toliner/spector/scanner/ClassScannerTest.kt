package dev.toliner.spector.scanner

import dev.toliner.spector.model.ClassKind
import dev.toliner.spector.model.ClassModifier
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
})
