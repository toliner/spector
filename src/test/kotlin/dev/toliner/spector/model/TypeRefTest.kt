package dev.toliner.spector.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldBeEmpty

class TypeRefTest : FunSpec({

    test("classType should create class type reference") {
        val typeRef = TypeRef.classType("java.lang.String")

        typeRef.kind shouldBe TypeKind.CLASS
        typeRef.fqcn shouldBe "java.lang.String"
        typeRef.args.shouldBeEmpty()
        typeRef.nullable shouldBe null
    }

    test("classType should support type arguments") {
        val stringType = TypeRef.classType("java.lang.String")
        val listType = TypeRef.classType("java.util.List", args = listOf(stringType))

        listType.kind shouldBe TypeKind.CLASS
        listType.fqcn shouldBe "java.util.List"
        listType.args.size shouldBe 1
        listType.args[0] shouldBe stringType
    }

    test("classType should support Kotlin nullability") {
        val nullableString = TypeRef.classType("java.lang.String", nullable = true)
        val nonNullString = TypeRef.classType("java.lang.String", nullable = false)

        nullableString.nullable shouldBe true
        nonNullString.nullable shouldBe false
    }

    test("primitiveType should create primitive type reference") {
        val intType = TypeRef.primitiveType("int")

        intType.kind shouldBe TypeKind.PRIMITIVE
        intType.fqcn shouldBe "int"
        intType.nullable shouldBe false
    }

    test("primitiveType should handle all Java primitives") {
        val primitives = listOf("boolean", "byte", "char", "short", "int", "long", "float", "double")

        primitives.forEach { primitiveName ->
            val typeRef = TypeRef.primitiveType(primitiveName)
            typeRef.kind shouldBe TypeKind.PRIMITIVE
            typeRef.fqcn shouldBe primitiveName
            typeRef.nullable shouldBe false
        }
    }

    test("arrayType should create array type reference") {
        val stringType = TypeRef.classType("java.lang.String")
        val arrayType = TypeRef.arrayType(stringType)

        arrayType.kind shouldBe TypeKind.ARRAY
        arrayType.args.size shouldBe 1
        arrayType.args[0] shouldBe stringType
    }

    test("arrayType should support nullable arrays in Kotlin") {
        val intType = TypeRef.primitiveType("int")
        val nullableArray = TypeRef.arrayType(intType, nullable = true)
        val nonNullArray = TypeRef.arrayType(intType, nullable = false)

        nullableArray.nullable shouldBe true
        nonNullArray.nullable shouldBe false
    }

    test("arrayType should support multi-dimensional arrays") {
        val intType = TypeRef.primitiveType("int")
        val oneDimArray = TypeRef.arrayType(intType)
        val twoDimArray = TypeRef.arrayType(oneDimArray)

        twoDimArray.kind shouldBe TypeKind.ARRAY
        twoDimArray.args[0].kind shouldBe TypeKind.ARRAY
        twoDimArray.args[0].args[0] shouldBe intType
    }

    test("typeVar should create type variable reference") {
        val typeVar = TypeRef.typeVar("T")

        typeVar.kind shouldBe TypeKind.TYPEVAR
        typeVar.fqcn shouldBe "T"
        typeVar.bounds.shouldBeEmpty()
    }

    test("typeVar should support bounds") {
        val numberBound = TypeRef.classType("java.lang.Number")
        val boundedTypeVar = TypeRef.typeVar("T", bounds = listOf(numberBound))

        boundedTypeVar.kind shouldBe TypeKind.TYPEVAR
        boundedTypeVar.fqcn shouldBe "T"
        boundedTypeVar.bounds.size shouldBe 1
        boundedTypeVar.bounds[0] shouldBe numberBound
    }

    test("typeVar should support multiple bounds") {
        val comparableBound = TypeRef.classType("java.lang.Comparable")
        val serializableBound = TypeRef.classType("java.io.Serializable")
        val multiTypeVar = TypeRef.typeVar("T", bounds = listOf(comparableBound, serializableBound))

        multiTypeVar.bounds.size shouldBe 2
        multiTypeVar.bounds shouldContain comparableBound
        multiTypeVar.bounds shouldContain serializableBound
    }

    test("wildcard should create unbounded wildcard") {
        val wildcard = TypeRef.wildcard(WildcardKind.UNBOUNDED)

        wildcard.kind shouldBe TypeKind.WILDCARD
        wildcard.wildcard shouldBe WildcardKind.UNBOUNDED
        wildcard.bounds.shouldBeEmpty()
    }

    test("wildcard should create extends wildcard (covariant)") {
        val numberBound = TypeRef.classType("java.lang.Number")
        val wildcard = TypeRef.wildcard(WildcardKind.OUT, numberBound)

        wildcard.kind shouldBe TypeKind.WILDCARD
        wildcard.wildcard shouldBe WildcardKind.OUT
        wildcard.bounds.size shouldBe 1
        wildcard.bounds[0] shouldBe numberBound
    }

    test("wildcard should create super wildcard (contravariant)") {
        val integerBound = TypeRef.classType("java.lang.Integer")
        val wildcard = TypeRef.wildcard(WildcardKind.IN, integerBound)

        wildcard.kind shouldBe TypeKind.WILDCARD
        wildcard.wildcard shouldBe WildcardKind.IN
        wildcard.bounds.size shouldBe 1
        wildcard.bounds[0] shouldBe integerBound
    }

    test("complex generic type with wildcards") {
        // Represents: List<? extends Map<String, ? super Integer>>
        val stringType = TypeRef.classType("java.lang.String")
        val integerType = TypeRef.classType("java.lang.Integer")
        val superWildcard = TypeRef.wildcard(WildcardKind.IN, integerType)
        val mapType = TypeRef.classType("java.util.Map", args = listOf(stringType, superWildcard))
        val extendsWildcard = TypeRef.wildcard(WildcardKind.OUT, mapType)
        val listType = TypeRef.classType("java.util.List", args = listOf(extendsWildcard))

        listType.kind shouldBe TypeKind.CLASS
        listType.fqcn shouldBe "java.util.List"
        listType.args[0].kind shouldBe TypeKind.WILDCARD
        listType.args[0].wildcard shouldBe WildcardKind.OUT
        listType.args[0].bounds[0].fqcn shouldBe "java.util.Map"
    }
})
