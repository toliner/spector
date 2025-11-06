package dev.toliner.spector.model

import kotlinx.serialization.Serializable

/**
 * Normalized class/interface/enum/object representation.
 */
@Serializable
data class ClassInfo(
    val fqcn: String,
    val packageName: String,
    val kind: ClassKind,
    val modifiers: Set<ClassModifier>,
    val superClass: String? = null,
    val interfaces: List<String> = emptyList(),
    val typeParameters: List<TypeParameter> = emptyList(),
    val annotations: List<AnnotationInfo> = emptyList(),
    val kotlin: KotlinClassInfo? = null
)

@Serializable
data class TypeParameter(
    val name: String,
    val bounds: List<TypeRef> = emptyList()
)

@Serializable
data class KotlinClassInfo(
    val isData: Boolean = false,
    val isSealed: Boolean = false,
    val isValue: Boolean = false,
    val isFunInterface: Boolean = false,
    val isInner: Boolean = false,
    val primaryConstructor: KotlinConstructor? = null,
    val properties: List<KotlinPropertyRef> = emptyList()
)

@Serializable
data class KotlinConstructor(
    val parameters: List<KotlinParameter> = emptyList()
)

@Serializable
data class KotlinParameter(
    val name: String,
    val type: TypeRef,
    val hasDefault: Boolean = false,
    val isVararg: Boolean = false
)

@Serializable
data class KotlinPropertyRef(
    val name: String,
    val jvmFieldName: String? = null,
    val getterName: String? = null,
    val setterName: String? = null
)

@Serializable
enum class ClassKind {
    CLASS,
    INTERFACE,
    ENUM,
    ANNOTATION,
    KOTLIN_OBJECT,
    KOTLIN_COMPANION
}

@Serializable
enum class ClassModifier {
    PUBLIC,
    PROTECTED,
    PRIVATE,
    INTERNAL,
    PACKAGE,
    FINAL,
    ABSTRACT,
    SEALED,
    STATIC
}
