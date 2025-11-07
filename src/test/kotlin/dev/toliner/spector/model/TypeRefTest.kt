package dev.toliner.spector.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

class TypeRefTest : FunSpec({

    test("classType factory should create class type reference") {
        val stringType = TypeRef.classType("java.lang.String")

        stringType.kind shouldBe TypeKind.CLASS
        stringType.fqcn shouldBe "java.lang.String"
        stringType.args.shouldBeEmpty()
        stringType.nullable.shouldBeNull()
    }

    test("classType factory should support generic arguments") {
        val stringArg = TypeRef.classType("java.lang.String")
        val listType = TypeRef.classType("java.util.List", args = listOf(stringArg))

        listType.kind shouldBe TypeKind.CLASS
        listType.fqcn shouldBe "java.util.List"
        listType.args.size shouldBe 1
        listType.args[0] shouldBe stringArg
    }

    test("classType factory should support nullability") {
        val nullableString = TypeRef.classType("java.lang.String", nullable = true)
        val nonNullableString = TypeRef.classType("java.lang.String", nullable = false)

        nullableString.nullable shouldBe true
        nonNullableString.nullable shouldBe false
    }

    test("primitiveType factory should create primitive type reference") {
        val intType = TypeRef.primitiveType("int")

        intType.kind shouldBe TypeKind.PRIMITIVE
        intType.fqcn shouldBe "int"
        intType.nullable shouldBe false
    }

    test("primitiveType factory should work for all primitive types") {
        val primitives = listOf("boolean", "char", "byte", "short", "int", "long", "float", "double", "void")

        primitives.forEach { primitiveName ->
            val type = TypeRef.primitiveType(primitiveName)
            type.kind shouldBe TypeKind.PRIMITIVE
            type.fqcn shouldBe primitiveName
            type.nullable shouldBe false
        }
    }

    test("arrayType factory should create array type reference") {
        val intType = TypeRef.primitiveType("int")
        val intArrayType = TypeRef.arrayType(intType)

        intArrayType.kind shouldBe TypeKind.ARRAY
        intArrayType.args.size shouldBe 1
        intArrayType.args[0] shouldBe intType
    }

    test("arrayType factory should support nested arrays") {
        val intType = TypeRef.primitiveType("int")
        val intArrayType = TypeRef.arrayType(intType)
        val int2DArrayType = TypeRef.arrayType(intArrayType)

        int2DArrayType.kind shouldBe TypeKind.ARRAY
        int2DArrayType.args.size shouldBe 1
        int2DArrayType.args[0] shouldBe intArrayType
        int2DArrayType.args[0].args[0] shouldBe intType
    }

    test("arrayType factory should support object arrays") {
        val stringType = TypeRef.classType("java.lang.String")
        val stringArrayType = TypeRef.arrayType(stringType)

        stringArrayType.kind shouldBe TypeKind.ARRAY
        stringArrayType.args.size shouldBe 1
        stringArrayType.args[0] shouldBe stringType
    }

    test("arrayType factory should support nullability") {
        val intType = TypeRef.primitiveType("int")
        val nullableIntArray = TypeRef.arrayType(intType, nullable = true)

        nullableIntArray.nullable shouldBe true
    }

    test("typeVar factory should create type variable reference") {
        val typeVar = TypeRef.typeVar("T")

        typeVar.kind shouldBe TypeKind.TYPEVAR
        typeVar.fqcn shouldBe "T"
        typeVar.bounds.shouldBeEmpty()
    }

    test("typeVar factory should support bounds") {
        val numberType = TypeRef.classType("java.lang.Number")
        val boundedTypeVar = TypeRef.typeVar("T", bounds = listOf(numberType))

        boundedTypeVar.kind shouldBe TypeKind.TYPEVAR
        boundedTypeVar.fqcn shouldBe "T"
        boundedTypeVar.bounds.size shouldBe 1
        boundedTypeVar.bounds[0] shouldBe numberType
    }

    test("typeVar factory should support multiple bounds") {
        val charSequenceType = TypeRef.classType("java.lang.CharSequence")
        val comparableType = TypeRef.classType("java.lang.Comparable")
        val boundedTypeVar = TypeRef.typeVar("T", bounds = listOf(charSequenceType, comparableType))

        boundedTypeVar.bounds.size shouldBe 2
        boundedTypeVar.bounds shouldContain charSequenceType
        boundedTypeVar.bounds shouldContain comparableType
    }

    test("wildcard factory should create unbounded wildcard") {
        val unboundedWildcard = TypeRef.wildcard(WildcardKind.UNBOUNDED)

        unboundedWildcard.kind shouldBe TypeKind.WILDCARD
        unboundedWildcard.wildcard shouldBe WildcardKind.UNBOUNDED
        unboundedWildcard.bounds.shouldBeEmpty()
    }

    test("wildcard factory should create extends wildcard") {
        val numberType = TypeRef.classType("java.lang.Number")
        val extendsWildcard = TypeRef.wildcard(WildcardKind.OUT, numberType)

        extendsWildcard.kind shouldBe TypeKind.WILDCARD
        extendsWildcard.wildcard shouldBe WildcardKind.OUT
        extendsWildcard.bounds.size shouldBe 1
        extendsWildcard.bounds[0] shouldBe numberType
    }

    test("wildcard factory should create super wildcard") {
        val integerType = TypeRef.classType("java.lang.Integer")
        val superWildcard = TypeRef.wildcard(WildcardKind.IN, integerType)

        superWildcard.kind shouldBe TypeKind.WILDCARD
        superWildcard.wildcard shouldBe WildcardKind.IN
        superWildcard.bounds.size shouldBe 1
        superWildcard.bounds[0] shouldBe integerType
    }

    test("should support complex nested generic types") {
        // Map<String, List<Integer>>
        val stringType = TypeRef.classType("java.lang.String")
        val integerType = TypeRef.classType("java.lang.Integer")
        val listOfInteger = TypeRef.classType("java.util.List", args = listOf(integerType))
        val mapType = TypeRef.classType("java.util.Map", args = listOf(stringType, listOfInteger))

        mapType.kind shouldBe TypeKind.CLASS
        mapType.fqcn shouldBe "java.util.Map"
        mapType.args.size shouldBe 2
        mapType.args[0] shouldBe stringType
        mapType.args[1].kind shouldBe TypeKind.CLASS
        mapType.args[1].fqcn shouldBe "java.util.List"
        mapType.args[1].args.size shouldBe 1
        mapType.args[1].args[0] shouldBe integerType
    }

    test("should support wildcards in generic types") {
        // List<? extends Number>
        val numberType = TypeRef.classType("java.lang.Number")
        val extendsNumberWildcard = TypeRef.wildcard(WildcardKind.OUT, numberType)
        val listType = TypeRef.classType("java.util.List", args = listOf(extendsNumberWildcard))

        listType.args.size shouldBe 1
        listType.args[0].kind shouldBe TypeKind.WILDCARD
        listType.args[0].wildcard shouldBe WildcardKind.OUT
        listType.args[0].bounds.size shouldBe 1
        listType.args[0].bounds[0] shouldBe numberType
    }

    test("should support type variables in generic types") {
        // List<T>
        val typeVarT = TypeRef.typeVar("T")
        val listOfT = TypeRef.classType("java.util.List", args = listOf(typeVarT))

        listOfT.args.size shouldBe 1
        listOfT.args[0].kind shouldBe TypeKind.TYPEVAR
        listOfT.args[0].fqcn shouldBe "T"
    }

    test("should support array of generic types") {
        // List<String>[]
        val stringType = TypeRef.classType("java.lang.String")
        val listOfString = TypeRef.classType("java.util.List", args = listOf(stringType))
        val arrayOfListOfString = TypeRef.arrayType(listOfString)

        arrayOfListOfString.kind shouldBe TypeKind.ARRAY
        arrayOfListOfString.args.size shouldBe 1
        arrayOfListOfString.args[0] shouldBe listOfString
    }
})
