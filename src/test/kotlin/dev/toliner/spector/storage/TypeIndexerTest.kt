package dev.toliner.spector.storage

import dev.toliner.spector.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import java.nio.file.Files

class TypeIndexerTest : FunSpec({

    test("should create tables on initialization") {
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        TypeIndexer(tempDb).use { indexer ->
            // If initialization succeeds, tables were created
            // We can verify by trying to query
            val result = indexer.findClassesByPackage("test")
            result.shouldBeEmpty()
        }

        Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
    }

    test("should index and retrieve a class") {
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        TypeIndexer(tempDb).use { indexer ->
            val classInfo = ClassInfo(
                fqcn = "com.example.TestClass",
                packageName = "com.example",
                kind = ClassKind.CLASS,
                modifiers = setOf(ClassModifier.PUBLIC, ClassModifier.FINAL)
            )

            indexer.indexClass(classInfo)

            val retrieved = indexer.findClassByFqcn("com.example.TestClass")
            retrieved.shouldNotBeNull()
            retrieved.fqcn shouldBe "com.example.TestClass"
            retrieved.packageName shouldBe "com.example"
            retrieved.kind shouldBe ClassKind.CLASS
            retrieved.modifiers shouldContain ClassModifier.PUBLIC
            retrieved.modifiers shouldContain ClassModifier.FINAL
        }

        Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
    }

    test("should find classes by package name (non-recursive)") {
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        TypeIndexer(tempDb).use { indexer ->
            indexer.indexClass(
                ClassInfo(
                    fqcn = "com.example.Class1",
                    packageName = "com.example",
                    kind = ClassKind.CLASS,
                    modifiers = setOf(ClassModifier.PUBLIC)
                )
            )
            indexer.indexClass(
                ClassInfo(
                    fqcn = "com.example.Class2",
                    packageName = "com.example",
                    kind = ClassKind.CLASS,
                    modifiers = setOf(ClassModifier.PUBLIC)
                )
            )
            indexer.indexClass(
                ClassInfo(
                    fqcn = "com.example.sub.Class3",
                    packageName = "com.example.sub",
                    kind = ClassKind.CLASS,
                    modifiers = setOf(ClassModifier.PUBLIC)
                )
            )

            val results = indexer.findClassesByPackage("com.example", recursive = false)
            results shouldHaveSize 2
            results.map { it.fqcn }.shouldContain("com.example.Class1")
            results.map { it.fqcn }.shouldContain("com.example.Class2")
        }

        Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
    }

    test("should find classes by package name (recursive)") {
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        TypeIndexer(tempDb).use { indexer ->
            indexer.indexClass(
                ClassInfo(
                    fqcn = "com.example.Class1",
                    packageName = "com.example",
                    kind = ClassKind.CLASS,
                    modifiers = setOf(ClassModifier.PUBLIC)
                )
            )
            indexer.indexClass(
                ClassInfo(
                    fqcn = "com.example.sub.Class2",
                    packageName = "com.example.sub",
                    kind = ClassKind.CLASS,
                    modifiers = setOf(ClassModifier.PUBLIC)
                )
            )

            val results = indexer.findClassesByPackage("com.example", recursive = true)
            results shouldHaveSize 2
            results.map { it.fqcn }.shouldContain("com.example.Class1")
            results.map { it.fqcn }.shouldContain("com.example.sub.Class2")
        }

        Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
    }

    test("should filter classes by kind") {
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        TypeIndexer(tempDb).use { indexer ->
            indexer.indexClass(
                ClassInfo(
                    fqcn = "com.example.MyClass",
                    packageName = "com.example",
                    kind = ClassKind.CLASS,
                    modifiers = setOf(ClassModifier.PUBLIC)
                )
            )
            indexer.indexClass(
                ClassInfo(
                    fqcn = "com.example.MyInterface",
                    packageName = "com.example",
                    kind = ClassKind.INTERFACE,
                    modifiers = setOf(ClassModifier.PUBLIC)
                )
            )

            val classes = indexer.findClassesByPackage(
                "com.example",
                kinds = setOf(ClassKind.CLASS)
            )
            classes shouldHaveSize 1
            classes[0].fqcn shouldBe "com.example.MyClass"

            val interfaces = indexer.findClassesByPackage(
                "com.example",
                kinds = setOf(ClassKind.INTERFACE)
            )
            interfaces shouldHaveSize 1
            interfaces[0].fqcn shouldBe "com.example.MyInterface"
        }

        Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
    }

    test("should filter public-only classes") {
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        TypeIndexer(tempDb).use { indexer ->
            indexer.indexClass(
                ClassInfo(
                    fqcn = "com.example.PublicClass",
                    packageName = "com.example",
                    kind = ClassKind.CLASS,
                    modifiers = setOf(ClassModifier.PUBLIC)
                )
            )
            indexer.indexClass(
                ClassInfo(
                    fqcn = "com.example.PrivateClass",
                    packageName = "com.example",
                    kind = ClassKind.CLASS,
                    modifiers = setOf(ClassModifier.PRIVATE)
                )
            )

            val publicOnly = indexer.findClassesByPackage("com.example", publicOnly = true)
            publicOnly shouldHaveSize 1
            publicOnly[0].fqcn shouldBe "com.example.PublicClass"

            val all = indexer.findClassesByPackage("com.example", publicOnly = false)
            all shouldHaveSize 2
        }

        Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
    }

    test("should index and retrieve members") {
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        TypeIndexer(tempDb).use { indexer ->
            val field = FieldInfo(
                ownerFqcn = "com.example.TestClass",
                name = "myField",
                visibility = Visibility.PUBLIC,
                static = false,
                annotations = emptyList(),
                jvmDesc = "Ljava/lang/String;",
                type = TypeRef.classType("java.lang.String"),
                isFinal = true
            )

            indexer.indexMember(field)

            val members = indexer.findMembersByOwner("com.example.TestClass")
            members shouldHaveSize 1
            members[0] shouldBe field
        }

        Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
    }

    test("should index different member types") {
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        TypeIndexer(tempDb).use { indexer ->
            val field = FieldInfo(
                ownerFqcn = "com.example.TestClass",
                name = "field",
                visibility = Visibility.PUBLIC,
                static = false,
                annotations = emptyList(),
                jvmDesc = "I",
                type = TypeRef.primitiveType("int")
            )

            val method = MethodInfo(
                ownerFqcn = "com.example.TestClass",
                name = "doSomething",
                visibility = Visibility.PUBLIC,
                static = false,
                annotations = emptyList(),
                jvmDesc = "()V",
                returnType = TypeRef.primitiveType("void"),
                parameters = emptyList()
            )

            indexer.indexMember(field)
            indexer.indexMember(method)

            val allMembers = indexer.findMembersByOwner("com.example.TestClass")
            allMembers shouldHaveSize 2

            val fields = indexer.findMembersByOwner(
                "com.example.TestClass",
                kinds = setOf(MemberKind.FIELD)
            )
            fields shouldHaveSize 1
            fields[0].name shouldBe "field"

            val methods = indexer.findMembersByOwner(
                "com.example.TestClass",
                kinds = setOf(MemberKind.METHOD)
            )
            methods shouldHaveSize 1
            methods[0].name shouldBe "doSomething"
        }

        Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
    }

    test("should filter members by visibility") {
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        TypeIndexer(tempDb).use { indexer ->
            indexer.indexMember(
                FieldInfo(
                    ownerFqcn = "com.example.TestClass",
                    name = "publicField",
                    visibility = Visibility.PUBLIC,
                    static = false,
                    annotations = emptyList(),
                    jvmDesc = "I",
                    type = TypeRef.primitiveType("int")
                )
            )
            indexer.indexMember(
                FieldInfo(
                    ownerFqcn = "com.example.TestClass",
                    name = "privateField",
                    visibility = Visibility.PRIVATE,
                    static = false,
                    annotations = emptyList(),
                    jvmDesc = "I",
                    type = TypeRef.primitiveType("int")
                )
            )

            val publicMembers = indexer.findMembersByOwner(
                "com.example.TestClass",
                visibilities = setOf(Visibility.PUBLIC)
            )
            publicMembers shouldHaveSize 1
            publicMembers[0].name shouldBe "publicField"
        }

        Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
    }

    test("should filter synthetic members") {
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        TypeIndexer(tempDb).use { indexer ->
            indexer.indexMember(
                FieldInfo(
                    ownerFqcn = "com.example.TestClass",
                    name = "normalField",
                    visibility = Visibility.PUBLIC,
                    static = false,
                    annotations = emptyList(),
                    jvmDesc = "I",
                    type = TypeRef.primitiveType("int"),
                    isSynthetic = false
                )
            )
            indexer.indexMember(
                FieldInfo(
                    ownerFqcn = "com.example.TestClass",
                    name = "syntheticField",
                    visibility = Visibility.PUBLIC,
                    static = false,
                    annotations = emptyList(),
                    jvmDesc = "I",
                    type = TypeRef.primitiveType("int"),
                    isSynthetic = true
                )
            )

            val withoutSynthetic = indexer.findMembersByOwner(
                "com.example.TestClass",
                includeSynthetic = false
            )
            withoutSynthetic shouldHaveSize 1
            withoutSynthetic[0].name shouldBe "normalField"

            val withSynthetic = indexer.findMembersByOwner(
                "com.example.TestClass",
                includeSynthetic = true
            )
            withSynthetic shouldHaveSize 2
        }

        Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
    }

    test("should find member by signature") {
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        TypeIndexer(tempDb).use { indexer ->
            val method = MethodInfo(
                ownerFqcn = "com.example.TestClass",
                name = "doSomething",
                visibility = Visibility.PUBLIC,
                static = false,
                annotations = emptyList(),
                jvmDesc = "(I)V",
                returnType = TypeRef.primitiveType("void"),
                parameters = listOf(
                    ParameterInfo(name = "arg", type = TypeRef.primitiveType("int"))
                )
            )

            indexer.indexMember(method)

            val found = indexer.findMemberBySignature(
                "com.example.TestClass",
                "doSomething",
                "(I)V"
            )
            found.shouldNotBeNull()
            found shouldBe method
        }

        Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
    }

    test("should return null for non-existent class") {
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        TypeIndexer(tempDb).use { indexer ->
            val result = indexer.findClassByFqcn("com.example.NonExistent")
            result.shouldBeNull()
        }

        Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
    }

    test("should return empty list for non-existent package") {
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        TypeIndexer(tempDb).use { indexer ->
            val result = indexer.findClassesByPackage("com.nonexistent")
            result.shouldBeEmpty()
        }

        Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
    }

    test("should clear database") {
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        TypeIndexer(tempDb).use { indexer ->
            indexer.indexClass(
                ClassInfo(
                    fqcn = "com.example.TestClass",
                    packageName = "com.example",
                    kind = ClassKind.CLASS,
                    modifiers = setOf(ClassModifier.PUBLIC)
                )
            )
            indexer.indexMember(
                FieldInfo(
                    ownerFqcn = "com.example.TestClass",
                    name = "field",
                    visibility = Visibility.PUBLIC,
                    static = false,
                    annotations = emptyList(),
                    jvmDesc = "I",
                    type = TypeRef.primitiveType("int")
                )
            )

            indexer.clear()

            val classes = indexer.findClassesByPackage("com.example")
            classes.shouldBeEmpty()

            val members = indexer.findMembersByOwner("com.example.TestClass")
            members.shouldBeEmpty()
        }

        Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
    }

    test("should replace existing class on re-indexing") {
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        TypeIndexer(tempDb).use { indexer ->
            val class1 = ClassInfo(
                fqcn = "com.example.TestClass",
                packageName = "com.example",
                kind = ClassKind.CLASS,
                modifiers = setOf(ClassModifier.PUBLIC)
            )

            indexer.indexClass(class1)

            val class2 = ClassInfo(
                fqcn = "com.example.TestClass",
                packageName = "com.example",
                kind = ClassKind.INTERFACE,
                modifiers = setOf(ClassModifier.PUBLIC, ClassModifier.ABSTRACT)
            )

            indexer.indexClass(class2)

            val retrieved = indexer.findClassByFqcn("com.example.TestClass")
            retrieved.shouldNotBeNull()
            retrieved.kind shouldBe ClassKind.INTERFACE
        }

        Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
    }
})
