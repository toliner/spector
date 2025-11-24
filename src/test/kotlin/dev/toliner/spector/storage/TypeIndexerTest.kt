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

    context("Inheritance hierarchy") {
        test("should find direct subclasses") {
            val tempDb = Files.createTempFile("test-indexer", ".db").toFile()
            tempDb.deleteOnExit()

            TypeIndexer(tempDb.absolutePath).use { indexer ->
                // Index base class
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.BaseClass",
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

                // Index subclass 1
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.SubClass1",
                        packageName = "com.example",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = "com.example.BaseClass",
                        interfaces = emptyList(),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                // Index subclass 2
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.SubClass2",
                        packageName = "com.example",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = "com.example.BaseClass",
                        interfaces = emptyList(),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                val subclasses = indexer.findSubclasses("com.example.BaseClass")
                subclasses shouldHaveSize 2
                subclasses shouldContain "com.example.SubClass1"
                subclasses shouldContain "com.example.SubClass2"
            }
        }

        test("should return empty list for class with no subclasses") {
            val tempDb = Files.createTempFile("test-indexer", ".db").toFile()
            tempDb.deleteOnExit()

            TypeIndexer(tempDb.absolutePath).use { indexer ->
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.FinalClass",
                        packageName = "com.example",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC, ClassModifier.FINAL),
                        superClass = null,
                        interfaces = emptyList(),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                val subclasses = indexer.findSubclasses("com.example.FinalClass")
                subclasses.shouldBeEmpty()
            }
        }

        test("should find interface implementations") {
            val tempDb = Files.createTempFile("test-indexer", ".db").toFile()
            tempDb.deleteOnExit()

            TypeIndexer(tempDb.absolutePath).use { indexer ->
                // Index interface
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

                // Index implementation 1
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.Impl1",
                        packageName = "com.example",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = null,
                        interfaces = listOf("com.example.MyInterface"),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                // Index implementation 2
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.Impl2",
                        packageName = "com.example",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = null,
                        interfaces = listOf("com.example.MyInterface", "com.example.OtherInterface"),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                val implementations = indexer.findImplementations("com.example.MyInterface")
                implementations shouldHaveSize 2
                implementations.map { it.fqcn } shouldContain "com.example.Impl1"
                implementations.map { it.fqcn } shouldContain "com.example.Impl2"
            }
        }

        test("should find superclass chain") {
            val tempDb = Files.createTempFile("test-indexer", ".db").toFile()
            tempDb.deleteOnExit()

            TypeIndexer(tempDb.absolutePath).use { indexer ->
                // Index class hierarchy: GrandParent -> Parent -> Child
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.GrandParent",
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
                        fqcn = "com.example.Parent",
                        packageName = "com.example",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = "com.example.GrandParent",
                        interfaces = emptyList(),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.Child",
                        packageName = "com.example",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = "com.example.Parent",
                        interfaces = emptyList(),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                val chain = indexer.findSuperclassChain("com.example.Child")
                chain shouldHaveSize 2
                chain[0] shouldBe "com.example.Parent"
                chain[1] shouldBe "com.example.GrandParent"
            }
        }

        test("should find all interfaces including inherited") {
            val tempDb = Files.createTempFile("test-indexer", ".db").toFile()
            tempDb.deleteOnExit()

            TypeIndexer(tempDb.absolutePath).use { indexer ->
                // Index interfaces: Interface1 <- Interface2
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.Interface1",
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
                        fqcn = "com.example.Interface2",
                        packageName = "com.example",
                        kind = ClassKind.INTERFACE,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = null,
                        interfaces = listOf("com.example.Interface1"),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                // Index parent class implementing Interface1
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.Parent",
                        packageName = "com.example",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = null,
                        interfaces = listOf("com.example.Interface1"),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                // Index child class implementing Interface2
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.Child",
                        packageName = "com.example",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = "com.example.Parent",
                        interfaces = listOf("com.example.Interface2"),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                val allInterfaces = indexer.findAllInterfaces("com.example.Child")
                allInterfaces shouldHaveSize 2
                allInterfaces shouldContain "com.example.Interface1"
                allInterfaces shouldContain "com.example.Interface2"
            }
        }

        test("should find inherited members") {
            val tempDb = Files.createTempFile("test-indexer", ".db").toFile()
            tempDb.deleteOnExit()

            TypeIndexer(tempDb.absolutePath).use { indexer ->
                // Index parent class
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.Parent",
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

                // Index child class
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.Child",
                        packageName = "com.example",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = "com.example.Parent",
                        interfaces = emptyList(),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                // Index public method in parent
                indexer.indexMember(
                    MethodInfo(
                        ownerFqcn = "com.example.Parent",
                        name = "publicMethod",
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

                // Index private method in parent (should not be inherited)
                indexer.indexMember(
                    MethodInfo(
                        ownerFqcn = "com.example.Parent",
                        name = "privateMethod",
                        returnType = TypeRef.primitiveType("void"),
                        parameters = emptyList(),
                        visibility = Visibility.PRIVATE,
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

                // Index constructor in parent (should not be inherited)
                indexer.indexMember(
                    MethodInfo(
                        ownerFqcn = "com.example.Parent",
                        name = "<init>",
                        returnType = TypeRef.primitiveType("void"),
                        parameters = emptyList(),
                        visibility = Visibility.PUBLIC,
                        static = false,
                        isFinal = false,
                        isAbstract = false,
                        isSynthetic = false,
                        isConstructor = true,
                        jvmDesc = "()V",
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                val inheritedMembers = indexer.findInheritedMembers("com.example.Child")
                inheritedMembers shouldHaveSize 1
                inheritedMembers.first().name shouldBe "publicMethod"
            }
        }

        test("should handle method overriding") {
            val tempDb = Files.createTempFile("test-indexer", ".db").toFile()
            tempDb.deleteOnExit()

            TypeIndexer(tempDb.absolutePath).use { indexer ->
                // Index parent class
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.Parent",
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

                // Index child class
                indexer.indexClass(
                    ClassInfo(
                        fqcn = "com.example.Child",
                        packageName = "com.example",
                        kind = ClassKind.CLASS,
                        modifiers = setOf(ClassModifier.PUBLIC),
                        superClass = "com.example.Parent",
                        interfaces = emptyList(),
                        typeParameters = emptyList(),
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                // Index method in parent
                indexer.indexMember(
                    MethodInfo(
                        ownerFqcn = "com.example.Parent",
                        name = "toString",
                        returnType = TypeRef.classType("java.lang.String"),
                        parameters = emptyList(),
                        visibility = Visibility.PUBLIC,
                        static = false,
                        isFinal = false,
                        isAbstract = false,
                        isSynthetic = false,
                        isConstructor = false,
                        jvmDesc = "()Ljava/lang/String;",
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                // Index overridden method in child (same signature)
                indexer.indexMember(
                    MethodInfo(
                        ownerFqcn = "com.example.Child",
                        name = "toString",
                        returnType = TypeRef.classType("java.lang.String"),
                        parameters = emptyList(),
                        visibility = Visibility.PUBLIC,
                        static = false,
                        isFinal = false,
                        isAbstract = false,
                        isSynthetic = false,
                        isConstructor = false,
                        jvmDesc = "()Ljava/lang/String;",
                        annotations = emptyList(),
                        kotlin = null
                    )
                )

                // When querying inherited members, the overridden method should not appear
                // because it's already defined in the child class
                val inheritedMembers = indexer.findInheritedMembers("com.example.Child")

                // The toString from parent should not be in inherited members
                // because it's overridden in the child
                val toStringMethods = inheritedMembers.filter { it.name == "toString" }
                toStringMethods.shouldBeEmpty()
            }
        }
    }
})
