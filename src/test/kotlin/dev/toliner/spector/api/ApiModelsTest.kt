package dev.toliner.spector.api

import dev.toliner.spector.model.ClassKind
import dev.toliner.spector.model.ClassModifier
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

class ApiModelsTest : FunSpec({

    test("ApiResponse.success should create successful response") {
        val data = "test data"
        val response = ApiResponse.success(data)

        response.ok shouldBe true
        response.data shouldBe "test data"
        response.error.shouldBeNull()
    }

    test("ApiResponse.error should create error response") {
        val response = ApiResponse.error<String>("NOT_FOUND", "Resource not found")

        response.ok shouldBe false
        response.data.shouldBeNull()
        response.error.shouldNotBeNull()
        response.error!!.code shouldBe "NOT_FOUND"
        response.error!!.message shouldBe "Resource not found"
    }

    test("ApiResponse should handle different data types") {
        val intResponse = ApiResponse.success(42)
        intResponse.data shouldBe 42

        val listResponse = ApiResponse.success(listOf("a", "b", "c"))
        listResponse.data shouldBe listOf("a", "b", "c")

        val nullResponse = ApiResponse.success<String?>(null)
        nullResponse.ok shouldBe true
        nullResponse.data.shouldBeNull()
    }

    test("ApiError should contain code and message") {
        val error = ApiError("VALIDATION_ERROR", "Invalid input parameters")

        error.code shouldBe "VALIDATION_ERROR"
        error.message shouldBe "Invalid input parameters"
    }

    test("ListClassesRequest should have default values") {
        val request = ListClassesRequest()

        request.recursive shouldBe true
        request.kinds.shouldBeNull()
        request.publicOnly shouldBe true
        request.limit.shouldBeNull()
        request.offset.shouldBeNull()
        request.sourceSet shouldBe "main"
    }

    test("ListClassesRequest should allow custom values") {
        val request = ListClassesRequest(
            recursive = false,
            kinds = setOf(ClassKind.INTERFACE),
            publicOnly = false,
            limit = 100,
            offset = 50,
            sourceSet = "test"
        )

        request.recursive shouldBe false
        request.kinds shouldBe setOf(ClassKind.INTERFACE)
        request.publicOnly shouldBe false
        request.limit shouldBe 100
        request.offset shouldBe 50
        request.sourceSet shouldBe "test"
    }

    test("ListClassesResponse should contain classes and pagination info") {
        val classSummaries = listOf(
            ClassSummary(
                fqcn = "com.example.Class1",
                kind = ClassKind.CLASS,
                modifiers = listOf(ClassModifier.PUBLIC)
            )
        )
        val response = ListClassesResponse(
            packageName = "com.example",
            classes = classSummaries,
            hasMore = true
        )

        response.packageName shouldBe "com.example"
        response.classes.size shouldBe 1
        response.hasMore shouldBe true
    }

    test("ClassSummary should represent class metadata") {
        val summary = ClassSummary(
            fqcn = "com.example.MyClass",
            kind = ClassKind.CLASS,
            modifiers = listOf(ClassModifier.PUBLIC, ClassModifier.FINAL),
            kotlin = KotlinClassSummary(isData = true, isValue = false)
        )

        summary.fqcn shouldBe "com.example.MyClass"
        summary.kind shouldBe ClassKind.CLASS
        summary.modifiers.size shouldBe 2
        summary.kotlin.shouldNotBeNull()
        summary.kotlin!!.isData shouldBe true
        summary.kotlin!!.isValue shouldBe false
    }

    test("ClassSummary should allow null Kotlin info for Java classes") {
        val summary = ClassSummary(
            fqcn = "java.lang.String",
            kind = ClassKind.CLASS,
            modifiers = listOf(ClassModifier.PUBLIC, ClassModifier.FINAL)
        )

        summary.kotlin.shouldBeNull()
    }

    test("KotlinClassSummary should have default values") {
        val kotlinSummary = KotlinClassSummary()

        kotlinSummary.isData shouldBe false
        kotlinSummary.isValue shouldBe false
    }

    test("KotlinClassSummary should support custom values") {
        val kotlinSummary = KotlinClassSummary(isData = true, isValue = true)

        kotlinSummary.isData shouldBe true
        kotlinSummary.isValue shouldBe true
    }

    test("ListMembersRequest should have default values") {
        val request = ListMembersRequest()

        request.kinds.shouldBeNull()
        request.includeSynthetic shouldBe false
        request.visibility.shouldBeNull()
        request.staticOnly.shouldBeNull()
        request.inherited shouldBe true
        request.kotlinView shouldBe true
    }

    test("ListMembersResponse should contain members by kind") {
        val response = ListMembersResponse(
            fqcn = "com.example.TestClass",
            members = MembersByKind(
                properties = emptyList(),
                methods = emptyList(),
                fields = emptyList()
            )
        )

        response.fqcn shouldBe "com.example.TestClass"
        response.members.properties.size shouldBe 0
        response.members.methods.size shouldBe 0
        response.members.fields.size shouldBe 0
    }

    test("MembersByKind should have default empty lists") {
        val membersByKind = MembersByKind()

        membersByKind.properties.size shouldBe 0
        membersByKind.methods.size shouldBe 0
        membersByKind.fields.size shouldBe 0
    }

    test("GetMemberDetailRequest should contain member identification") {
        val request = GetMemberDetailRequest(
            ownerFqcn = "com.example.TestClass",
            name = "testMethod",
            jvmDesc = "()V"
        )

        request.ownerFqcn shouldBe "com.example.TestClass"
        request.name shouldBe "testMethod"
        request.jvmDesc shouldBe "()V"
    }
})
