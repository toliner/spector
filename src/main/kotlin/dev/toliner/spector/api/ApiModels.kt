package dev.toliner.spector.api

import dev.toliner.spector.model.*
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val ok: Boolean,
    val data: T? = null,
    val error: ApiError? = null
) {
    companion object {
        fun <T> success(data: T) = ApiResponse(ok = true, data = data)
        fun <T> error(code: String, message: String) = ApiResponse<T>(
            ok = false,
            error = ApiError(code, message)
        )
    }
}

@Serializable
data class ApiError(
    val code: String,
    val message: String
)

@Serializable
data class ListClassesRequest(
    val recursive: Boolean = true,
    val kinds: Set<ClassKind>? = null,
    val publicOnly: Boolean = true,
    val limit: Int? = null,
    val offset: Int? = null,
    val sourceSet: String = "main"
)

@Serializable
data class ListClassesResponse(
    val packageName: String,
    val classes: List<ClassSummary>,
    val hasMore: Boolean
)

@Serializable
data class ListSubpackagesResponse(
    val packageName: String,
    val subpackages: List<String>
)

@Serializable
data class ClassSummary(
    val fqcn: String,
    val kind: ClassKind,
    val modifiers: List<ClassModifier>,
    val kotlin: KotlinClassSummary? = null
)

@Serializable
data class KotlinClassSummary(
    val isData: Boolean = false,
    val isValue: Boolean = false
)

@Serializable
data class ListMembersRequest(
    val kinds: Set<MemberKind>? = null,
    val includeSynthetic: Boolean = false,
    val visibility: Set<Visibility>? = null,
    val staticOnly: Boolean? = null,
    val inherited: Boolean = true,
    val kotlinView: Boolean = true
)

@Serializable
data class ListMembersResponse(
    val fqcn: String,
    val members: MembersByKind
)

@Serializable
data class MembersByKind(
    val properties: List<PropertyInfo> = emptyList(),
    val methods: List<MethodInfo> = emptyList(),
    val fields: List<FieldInfo> = emptyList()
)

@Serializable
data class GetMemberDetailRequest(
    val ownerFqcn: String,
    val name: String,
    val jvmDesc: String
)

@Serializable
data class GetMemberDetailResponse(
    val ownerFqcn: String,
    val memberKind: MemberKind,
    val member: MemberInfo
)
