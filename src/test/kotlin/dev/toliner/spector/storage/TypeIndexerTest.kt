package dev.toliner.spector.storage

import dev.toliner.spector.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.nio.file.Files

/**
 * Tests for TypeIndexer.
 *
 * Purpose: Verifies that the SQLite-based type indexer correctly stores and retrieves
 * class and member information, with proper filtering and query capabilities.
 */
class TypeIndexerTest : FunSpec({

    context("Basic class indexing and retrieval") {
        test("should index and retrieve a class by FQCN") {
            val tempDb = Files.createTempFile("test-indexer", ".db").toFile()
            tempDb.deleteOnExit()

            TypeIndexer(tempDb.absolutePath).use { indexer ->
                val classInfo = ClassInfo(
                    fqcn = "com.example.TestClass",
                    packageName = "com.example",
                    kind = ClassKind.CLASS,
                    modifiers = setOf(ClassModifier.PUBLIC),
                    superClass = "java.lang.Object",
                    interfaces = emptyList(),
                    typeParameters = emptyList(),
                    annotations = emptyList(),
                    kotlin = null
                )

                indexer.indexClass(classInfo)

                val retrieved = indexer.findClassByFqcn("com.example.TestClass")
                retrieved.shouldNotBeNull()
                retrieved.fqcn shouldBe "com.example.TestClass"
                retrieved.packageName shouldBe "com.example"
                retrieved.kind shouldBe ClassKind.CLASS
            }
        }

        test("should return null for non-existent class") {
            val tempDb = Files.createTempFile("test-indexer", ".db").toFile()
            tempDb.deleteOnExit()

            TypeIndexer(tempDb.absolutePath).use { indexer ->
                val result = indexer.findClassByFqcn("com.example.NonExistent")
                result.shouldBeNull()
            }
        }

        test("should update existing class on re-index") {
            val tempDb = Files.createTempFile("test-indexer", ".db").toFile()
            tempDb.deleteOnExit()

            TypeIndexer(tempDb.absolutePath).use { indexer ->
                val classInfo1 = ClassInfo(
                    fqcn = "com.example.TestClass",
                    packageName = "com.example",
                    kind = ClassKind.CLASS,
                    modifiers = setOf(ClassModifier.PUBLIC),
                    superClass = "java.lang.Object",
                    interfaces = emptyList(),
                    typeParameters = emptyList(),
                    annotations = emptyList(),
                    kotlin = null
                )

                indexer.indexClass(classInfo1)

                // Re-index with updated information
                val classInfo2 = classInfo1.copy(
                    modifiers = setOf(ClassModifier.PUBLIC, ClassModifier.FINAL)
                )

                indexer.indexClass(classInfo2)

                val retrieved = indexer.findClassByFqcn("com.example.TestClass")
                retrieved.shouldNotBeNull()
                retrieved.modifiers shouldContain ClassModifier.FINAL
            }
        }
    }

    context("Package-based queries") {
        test("should find classes by package (non-recursive)") {
            val tempDb = Files.createTempFile("test-indexer", ".db").toFile()
            tempDb.deleteOnExit()

            TypeIndexer(tempDb.absolutePath).use { indexer ->
                // Index classes in different packages
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.Class1",
                        packageName = "com.example",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = null,
                        interfaces = emptyList(),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.Class2",
                        packageName = "com.example",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = null,
                        interfaces = emptyList(),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.sub.Class3",
                        packageName = "com.example.sub",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = null,
                        interfaces = emptyList(),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                // Non-recursive query
                val classes = indexer.findClassesByPackage("com.example", recursive = false)
                classes shouldHaveSize 2
                classes.map { it.fqcn } shouldContain "com.example.Class1"
                classes.map { it.fqcn } shouldContain "com.example.Class2"
            }
        }

        test("should find classes by package (recursive)") {
            val tempDb = Files.createTempFile("test-indexer", ".db").toFile()
            tempDb.deleteOnExit()

            TypeIndexer(tempDb.absolutePath).use { indexer ->
                // Index classes in different packages
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.Class1",
                        packageName = "com.example",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = null,
                        interfaces = emptyList(),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.sub.Class2",
                        packageName = "com.example.sub",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = null,
                        interfaces = emptyList(),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                // Recursive query
                val classes = indexer.findClassesByPackage("com.example", recursive = true)
                classes shouldHaveSize 2
                classes.map { it.fqcn } shouldContain "com.example.Class1"
                classes.map { it.fqcn } shouldContain "com.example.sub.Class2"
            }
        }

        test("should return empty list for non-existent package") {
            val tempDb = Files.createTempFile("test-indexer", ".db").toFile()
            tempDb.deleteOnExit()

            TypeIndexer(tempDb.absolutePath).use { indexer ->
                val classes = indexer.findClassesByPackage("com.nonexistent")
                classes.shouldBeEmpty()
            }
        }
    }

    context("Filtering by class kind") {
        test("should filter classes by kind") {
            val tempDb = Files.createTempFile("test-indexer", ".db").toFile()
            tempDb.deleteOnExit()

            TypeIndexer(tempDb.absolutePath).use { indexer ->
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.MyClass",
                        packageName = "com.example",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = null,
                        interfaces = emptyList(),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.MyInterface",
                        packageName = "com.example",
                        kind = ClassKind.INTERFACE,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = null,
                        interfaces = emptyList(),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.MyEnum",
                        packageName = "com.example",
                        kind = ClassKind.ENUM,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = null,
                        interfaces = emptyList(),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                // Filter for classes only
                val classes = indexer.findClassesByPackage(
                    "com.example",
                    recursive = false,
                    kinds = setOf(ClassKind.CLASS)
                )
                classes shouldHaveSize 1
                classes.first().kind shouldBe ClassKind.CLASS

                // Filter for interfaces only
                val interfaces = indexer.findClassesByPackage(
                    "com.example",
                    recursive = false,
                    kinds = setOf(ClassKind.INTERFACE)
                )
                interfaces shouldHaveSize 1
                interfaces.first().kind shouldBe ClassKind.INTERFACE
            }
        }
    }

    context("Filtering by visibility") {
        test("should filter public-only classes") {
            val tempDb = Files.createTempFile("test-indexer", ".db").toFile()
            tempDb.deleteOnExit()

            TypeIndexer(tempDb.absolutePath).use { indexer ->
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.PublicClass",
                        packageName = "com.example",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = null,
                        interfaces = emptyList(),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.PackagePrivateClass",
                        packageName = "com.example",
                        kind = ClassKind.CLASS,
                        modifiers = emptySet(), // Package-private
                        superClass = null,
                        interfaces = emptyList(),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                // Query with publicOnly = true
                val publicClasses = indexer.findClassesByPackage(
                    "com.example",
                    recursive = false,
                    publicOnly = true
                )
                publicClasses shouldHaveSize 1
                publicClasses.first().fqcn shouldBe "com.example.PublicClass"

                // Query with publicOnly = false
                val allClasses = indexer.findClassesByPackage(
                    "com.example",
                    recursive = false,
                    publicOnly = false
                )
                allClasses shouldHaveSize 2
            }
        }
    }

    context("Member indexing") {
        test("should index and retrieve members by owner") {
            val tempDb = Files.createTempFile("test-indexer", ".db").toFile()
            tempDb.deleteOnExit()

            TypeIndexer(tempDb.absolutePath).use { indexer ->
                // Index a class first
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.TestClass",
                        packageName = "com.example",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = null,
                        interfaces = emptyList(),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                // Index a field
                val field = FieldInfo(
                    ownerFqcn = "com.example.TestClass",
                    name = "testField",
                    type = TypeRef.classType("java.lang.String"),
                    visibility = Visibility.PUBLIC,
                    static = false,
                    isFinal = false,
                    isSynthetic = false,
                    jvmDesc = "Ljava/lang/String;",
                    annotations = emptyList()
                )

                indexer.indexMember(field)

                // Retrieve members
                val members = indexer.findMembersByOwner("com.example.TestClass")
                members shouldHaveSize 1
                members.first().name shouldBe "testField"
            }
        }

        test("should filter members by kind") {
            val tempDb = Files.createTempFile("test-indexer", ".db").toFile()
            tempDb.deleteOnExit()

            TypeIndexer(tempDb.absolutePath).use { indexer ->
                // Index a field
                indexer.indexMember(
                    FieldInfo(
                        ownerFqcn = "com.example.TestClass",
                        name = "field",
                        type = TypeRef.classType("java.lang.String"),
                        visibility = Visibility.PUBLIC,
                        static = false,
                        isFinal = false,
                        isSynthetic = false,
                        jvmDesc = "Ljava/lang/String;",
                        annotations = emptyList()
                    )
                )

                // Index a method
                indexer.indexMember(
                    MethodInfo(
                        ownerFqcn = "com.example.TestClass",
                        name = "method",
                        returnType = TypeRef.primitiveType("void"),
                        parameters = emptyList(),
                        visibility = Visibility.PUBLIC,
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

                // Filter for fields only
                val fields = indexer.findMembersByOwner(
                    "com.example.TestClass",
                    kinds = setOf(MemberKind.FIELD)
                )
                fields shouldHaveSize 1
                fields.first().name shouldBe "field"

                // Filter for methods only
                val methods = indexer.findMembersByOwner(
                    "com.example.TestClass",
                    kinds = setOf(MemberKind.METHOD)
                )
                methods shouldHaveSize 1
                methods.first().name shouldBe "method"
            }
        }
    }
})
