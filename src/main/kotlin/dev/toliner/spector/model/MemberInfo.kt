package dev.toliner.spector.model

import kotlinx.serialization.Serializable

/**
 * Sealed hierarchy for class members (fields, methods, properties).
 */
@Serializable
sealed class MemberInfo {
    abstract val ownerFqcn: String
    abstract val name: String
    abstract val visibility: Visibility
    abstract val static: Boolean
    abstract val annotations: List<AnnotationInfo>
}

@Serializable
data class FieldInfo(
    override val ownerFqcn: String,
    override val name: String,
    override val visibility: Visibility,
    override val static: Boolean,
    override val annotations: List<AnnotationInfo>,
    val jvmDesc: String,
    val type: TypeRef,
    val isEnumConstant: Boolean = false,
    val isFinal: Boolean = false,
    val isSynthetic: Boolean = false
) : MemberInfo()

@Serializable
data class MethodInfo(
    override val ownerFqcn: String,
    override val name: String,
    override val visibility: Visibility,
    override val static: Boolean,
    override val annotations: List<AnnotationInfo>,
    val jvmDesc: String,
    val returnType: TypeRef,
    val parameters: List<ParameterInfo>,
    val typeParameters: List<TypeParameter> = emptyList(),
    val throws: List<TypeRef> = emptyList(),
    val isConstructor: Boolean = false,
    val isSynthetic: Boolean = false,
    val isBridge: Boolean = false,
    val isFinal: Boolean = false,
    val isAbstract: Boolean = false,
    val kotlin: KotlinMethodInfo? = null
) : MemberInfo()

@Serializable
data class PropertyInfo(
    override val ownerFqcn: String,
    override val name: String,
    override val visibility: Visibility,
    override val static: Boolean,
    override val annotations: List<AnnotationInfo>,
    val type: TypeRef,
    val isMutable: Boolean,
    val isLateinit: Boolean = false,
    val getterJvmName: String? = null,
    val setterJvmName: String? = null,
    val backingField: String? = null
) : MemberInfo()

@Serializable
data class ParameterInfo(
    val name: String?,
    val type: TypeRef,
    val hasDefault: Boolean = false,
    val isVararg: Boolean = false,
    val annotations: List<AnnotationInfo> = emptyList()
)

@Serializable
data class KotlinMethodInfo(
    val isSuspend: Boolean = false,
    val extensionReceiver: TypeRef? = null,
    val hasDefaultArgsMask: Boolean = false
)

@Serializable
data class AnnotationInfo(
    val desc: String,
    val values: Map<String, String> = emptyMap(),
    val retention: AnnotationRetention = AnnotationRetention.RUNTIME
)

@Serializable
enum class Visibility {
    PUBLIC,
    PROTECTED,
    PRIVATE,
    INTERNAL,
    PACKAGE
}

@Serializable
enum class AnnotationRetention {
    SOURCE,
    CLASS,
    RUNTIME
}

@Serializable
enum class MemberKind {
    FIELD,
    METHOD,
    PROPERTY,
    CONSTRUCTOR
}
