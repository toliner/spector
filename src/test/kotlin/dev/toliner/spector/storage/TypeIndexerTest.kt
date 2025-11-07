package dev.toliner.spector.storage

import dev.toliner.spector.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import java.nio.file.Files

class TypeIndexerTest : FunSpec({

    test("should create database and tables") {
        val tempDb = Files.createTempFile("test-db", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            // Just verifying it doesn't throw
            indexer shouldNotBe null
        }
    }

    test("should index and retrieve class by FQCN") {
        val tempDb = Files.createTempFile("test-index-class", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val classInfo = ClassInfo(
                fqcn = "com.example.TestClass",
                packageName = "com.example",
                kind = ClassKind.CLASS,
                modifiers = setOf(ClassModifier.PUBLIC, ClassModifier.FINAL),
                superClass = "java.lang.Object",
                interfaces = listOf("java.io.Serializable")
            )

            indexer.indexClass(classInfo)

            val retrieved = indexer.findClassByFqcn("com.example.TestClass")
            retrieved.shouldNotBeNull()
            retrieved.fqcn shouldBe "com.example.TestClass"
            retrieved.packageName shouldBe "com.example"
            retrieved.kind shouldBe ClassKind.CLASS
            retrieved.modifiers shouldContain ClassModifier.PUBLIC
            retrieved.superClass shouldBe "java.lang.Object"
            retrieved.interfaces shouldContain "java.io.Serializable"
        }
    }

    test("should return null for non-existent class") {
        val tempDb = Files.createTempFile("test-not-found", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val retrieved = indexer.findClassByFqcn("com.example.NonExistent")
            retrieved.shouldBeNull()
        }
    }

    test("should find classes by package name") {
        val tempDb = Files.createTempFile("test-package", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val class1 = ClassInfo(
                fqcn = "com.example.Class1",
                packageName = "com.example",
                kind = ClassKind.CLASS,
                modifiers = setOf(ClassModifier.PUBLIC)
            )
            val class2 = ClassInfo(
                fqcn = "com.example.Class2",
                packageName = "com.example",
                kind = ClassKind.CLASS,
                modifiers = setOf(ClassModifier.PUBLIC)
            )
            val class3 = ClassInfo(
                fqcn = "com.other.Class3",
                packageName = "com.other",
                kind = ClassKind.CLASS,
                modifiers = setOf(ClassModifier.PUBLIC)
            )

            indexer.indexClass(class1)
            indexer.indexClass(class2)
            indexer.indexClass(class3)

            val results = indexer.findClassesByPackage("com.example", recursive = false)
            results shouldHaveSize 2
            results.map { it.fqcn } shouldContain "com.example.Class1"
            results.map { it.fqcn } shouldContain "com.example.Class2"
        }
    }

    test("should find classes recursively in subpackages") {
        val tempDb = Files.createTempFile("test-recursive", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val class1 = ClassInfo(
                fqcn = "com.example.Class1",
                packageName = "com.example",
                kind = ClassKind.CLASS,
                modifiers = setOf(ClassModifier.PUBLIC)
            )
            val class2 = ClassInfo(
                fqcn = "com.example.sub.Class2",
                packageName = "com.example.sub",
                kind = ClassKind.CLASS,
                modifiers = setOf(ClassModifier.PUBLIC)
            )
            val class3 = ClassInfo(
                fqcn = "com.other.Class3",
                packageName = "com.other",
                kind = ClassKind.CLASS,
                modifiers = setOf(ClassModifier.PUBLIC)
            )

            indexer.indexClass(class1)
            indexer.indexClass(class2)
            indexer.indexClass(class3)

            val results = indexer.findClassesByPackage("com.example", recursive = true)
            results shouldHaveSize 2
            results.map { it.fqcn } shouldContain "com.example.Class1"
            results.map { it.fqcn } shouldContain "com.example.sub.Class2"
        }
    }

    test("should filter by class kind") {
        val tempDb = Files.createTempFile("test-kind", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val class1 = ClassInfo(
                fqcn = "com.example.Class1",
                packageName = "com.example",
                kind = ClassKind.CLASS,
                modifiers = setOf(ClassModifier.PUBLIC)
            )
            val interface1 = ClassInfo(
                fqcn = "com.example.Interface1",
                packageName = "com.example",
                kind = ClassKind.INTERFACE,
                modifiers = setOf(ClassModifier.PUBLIC)
            )
            val enum1 = ClassInfo(
                fqcn = "com.example.Enum1",
                packageName = "com.example",
                kind = ClassKind.ENUM,
                modifiers = setOf(ClassModifier.PUBLIC)
            )

            indexer.indexClass(class1)
            indexer.indexClass(interface1)
            indexer.indexClass(enum1)

            val interfaces = indexer.findClassesByPackage(
                "com.example",
                kinds = setOf(ClassKind.INTERFACE)
            )
            interfaces shouldHaveSize 1
            interfaces[0].fqcn shouldBe "com.example.Interface1"

            val enums = indexer.findClassesByPackage(
                "com.example",
                kinds = setOf(ClassKind.ENUM)
            )
            enums shouldHaveSize 1
            enums[0].fqcn shouldBe "com.example.Enum1"
        }
    }

    test("should filter public classes only") {
        val tempDb = Files.createTempFile("test-public", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val publicClass = ClassInfo(
                fqcn = "com.example.PublicClass",
                packageName = "com.example",
                kind = ClassKind.CLASS,
                modifiers = setOf(ClassModifier.PUBLIC)
            )
            val privateClass = ClassInfo(
                fqcn = "com.example.PrivateClass",
                packageName = "com.example",
                kind = ClassKind.CLASS,
                modifiers = setOf(ClassModifier.PRIVATE)
            )
            val packageClass = ClassInfo(
                fqcn = "com.example.PackageClass",
                packageName = "com.example",
                kind = ClassKind.CLASS,
                modifiers = setOf(ClassModifier.PACKAGE)
            )

            indexer.indexClass(publicClass)
            indexer.indexClass(privateClass)
            indexer.indexClass(packageClass)

            val results = indexer.findClassesByPackage("com.example", publicOnly = true)
            results shouldHaveSize 1
            results[0].fqcn shouldBe "com.example.PublicClass"

            val allResults = indexer.findClassesByPackage("com.example", publicOnly = false)
            allResults shouldHaveSize 3
        }
    }

    test("should index and retrieve field member") {
        val tempDb = Files.createTempFile("test-field", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val field = FieldInfo(
                ownerFqcn = "com.example.TestClass",
                name = "testField",
                visibility = Visibility.PUBLIC,
                static = false,
                annotations = emptyList(),
                jvmDesc = "Ljava/lang/String;",
                type = TypeRef.classType("java.lang.String"),
                isFinal = false
            )

            indexer.indexMember(field)

            val retrieved = indexer.findMemberBySignature(
                "com.example.TestClass",
                "testField",
                "Ljava/lang/String;"
            )
            retrieved.shouldNotBeNull()
            retrieved shouldBe field
        }
    }

    test("should index and retrieve method member") {
        val tempDb = Files.createTempFile("test-method", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val method = MethodInfo(
                ownerFqcn = "com.example.TestClass",
                name = "testMethod",
                visibility = Visibility.PUBLIC,
                static = false,
                annotations = emptyList(),
                jvmDesc = "()V",
                returnType = TypeRef.primitiveType("void"),
                parameters = emptyList()
            )

            indexer.indexMember(method)

            val retrieved = indexer.findMemberBySignature(
                "com.example.TestClass",
                "testMethod",
                "()V"
            )
            retrieved.shouldNotBeNull()
            retrieved shouldBe method
        }
    }

    test("should find members by owner") {
        val tempDb = Files.createTempFile("test-members", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val field = FieldInfo(
                ownerFqcn = "com.example.TestClass",
                name = "field1",
                visibility = Visibility.PUBLIC,
                static = false,
                annotations = emptyList(),
                jvmDesc = "I",
                type = TypeRef.primitiveType("int")
            )
            val method = MethodInfo(
                ownerFqcn = "com.example.TestClass",
                name = "method1",
                visibility = Visibility.PUBLIC,
                static = false,
                annotations = emptyList(),
                jvmDesc = "()V",
                returnType = TypeRef.primitiveType("void"),
                parameters = emptyList()
            )

            indexer.indexMember(field)
            indexer.indexMember(method)

            val members = indexer.findMembersByOwner("com.example.TestClass")
            members shouldHaveSize 2
        }
    }

    test("should filter members by kind") {
        val tempDb = Files.createTempFile("test-member-kind", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val field = FieldInfo(
                ownerFqcn = "com.example.TestClass",
                name = "field1",
                visibility = Visibility.PUBLIC,
                static = false,
                annotations = emptyList(),
                jvmDesc = "I",
                type = TypeRef.primitiveType("int")
            )
            val method = MethodInfo(
                ownerFqcn = "com.example.TestClass",
                name = "method1",
                visibility = Visibility.PUBLIC,
                static = false,
                annotations = emptyList(),
                jvmDesc = "()V",
                returnType = TypeRef.primitiveType("void"),
                parameters = emptyList()
            )

            indexer.indexMember(field)
            indexer.indexMember(method)

            val fields = indexer.findMembersByOwner(
                "com.example.TestClass",
                kinds = setOf(MemberKind.FIELD)
            )
            fields shouldHaveSize 1
            (fields[0] as FieldInfo).name shouldBe "field1"

            val methods = indexer.findMembersByOwner(
                "com.example.TestClass",
                kinds = setOf(MemberKind.METHOD)
            )
            methods shouldHaveSize 1
            (methods[0] as MethodInfo).name shouldBe "method1"
        }
    }

    test("should filter members by visibility") {
        val tempDb = Files.createTempFile("test-visibility", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val publicField = FieldInfo(
                ownerFqcn = "com.example.TestClass",
                name = "publicField",
                visibility = Visibility.PUBLIC,
                static = false,
                annotations = emptyList(),
                jvmDesc = "I",
                type = TypeRef.primitiveType("int")
            )
            val privateField = FieldInfo(
                ownerFqcn = "com.example.TestClass",
                name = "privateField",
                visibility = Visibility.PRIVATE,
                static = false,
                annotations = emptyList(),
                jvmDesc = "I",
                type = TypeRef.primitiveType("int")
            )

            indexer.indexMember(publicField)
            indexer.indexMember(privateField)

            val publicMembers = indexer.findMembersByOwner(
                "com.example.TestClass",
                visibilities = setOf(Visibility.PUBLIC)
            )
            publicMembers shouldHaveSize 1
            (publicMembers[0] as FieldInfo).name shouldBe "publicField"
        }
    }

    test("should exclude synthetic members") {
        val tempDb = Files.createTempFile("test-synthetic", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val normalField = FieldInfo(
                ownerFqcn = "com.example.TestClass",
                name = "normalField",
                visibility = Visibility.PUBLIC,
                static = false,
                annotations = emptyList(),
                jvmDesc = "I",
                type = TypeRef.primitiveType("int"),
                isSynthetic = false
            )
            val syntheticField = FieldInfo(
                ownerFqcn = "com.example.TestClass",
                name = "syntheticField",
                visibility = Visibility.PUBLIC,
                static = false,
                annotations = emptyList(),
                jvmDesc = "I",
                type = TypeRef.primitiveType("int"),
                isSynthetic = true
            )

            indexer.indexMember(normalField)
            indexer.indexMember(syntheticField)

            val nonSyntheticMembers = indexer.findMembersByOwner(
                "com.example.TestClass",
                includeSynthetic = false
            )
            nonSyntheticMembers shouldHaveSize 1
            (nonSyntheticMembers[0] as FieldInfo).name shouldBe "normalField"

            val allMembers = indexer.findMembersByOwner(
                "com.example.TestClass",
                includeSynthetic = true
            )
            allMembers shouldHaveSize 2
        }
    }

    test("should clear all data") {
        val tempDb = Files.createTempFile("test-clear", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val classInfo = ClassInfo(
                fqcn = "com.example.TestClass",
                packageName = "com.example",
                kind = ClassKind.CLASS,
                modifiers = setOf(ClassModifier.PUBLIC)
            )
            val field = FieldInfo(
                ownerFqcn = "com.example.TestClass",
                name = "field1",
                visibility = Visibility.PUBLIC,
                static = false,
                annotations = emptyList(),
                jvmDesc = "I",
                type = TypeRef.primitiveType("int")
            )

            indexer.indexClass(classInfo)
            indexer.indexMember(field)

            // Verify data exists
            indexer.findClassByFqcn("com.example.TestClass").shouldNotBeNull()
            indexer.findMembersByOwner("com.example.TestClass").shouldNotBeEmpty()

            // Clear
            indexer.clear()

            // Verify data is gone
            indexer.findClassByFqcn("com.example.TestClass").shouldBeNull()
            indexer.findMembersByOwner("com.example.TestClass") shouldHaveSize 0
        }
    }

    test("should update existing class on re-index") {
        val tempDb = Files.createTempFile("test-update", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val classInfo1 = ClassInfo(
                fqcn = "com.example.TestClass",
                packageName = "com.example",
                kind = ClassKind.CLASS,
                modifiers = setOf(ClassModifier.PUBLIC)
            )

            indexer.indexClass(classInfo1)

            // Update with different modifiers
            val classInfo2 = ClassInfo(
                fqcn = "com.example.TestClass",
                packageName = "com.example",
                kind = ClassKind.CLASS,
                modifiers = setOf(ClassModifier.PUBLIC, ClassModifier.FINAL)
            )

            indexer.indexClass(classInfo2)

            val retrieved = indexer.findClassByFqcn("com.example.TestClass")
            retrieved.shouldNotBeNull()
            retrieved.modifiers shouldContain ClassModifier.FINAL
        }
    }
})
