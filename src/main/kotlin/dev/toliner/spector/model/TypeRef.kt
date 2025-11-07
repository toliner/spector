package dev.toliner.spector.model

import kotlinx.serialization.Serializable

/**
 * Type reference representation supporting Java generics and Kotlin nullability.
 */
@Serializable
data class TypeRef(
    val kind: TypeKind,
    val fqcn: String? = null,
    val args: List<TypeRef> = emptyList(),
    val nullable: Boolean? = null,
    val wildcard: WildcardKind? = null,
    val bounds: List<TypeRef> = emptyList()
) {
    companion object {
        fun classType(fqcn: String, args: List<TypeRef> = emptyList(), nullable: Boolean? = null) =
            TypeRef(kind = TypeKind.CLASS, fqcn = fqcn, args = args, nullable = nullable)

        fun primitiveType(fqcn: String) =
            TypeRef(kind = TypeKind.PRIMITIVE, fqcn = fqcn, nullable = false)

        fun arrayType(componentType: TypeRef, nullable: Boolean? = null) =
            TypeRef(kind = TypeKind.ARRAY, args = listOf(componentType), nullable = nullable)

        fun typeVar(name: String, bounds: List<TypeRef> = emptyList()) =
            TypeRef(kind = TypeKind.TYPEVAR, fqcn = name, bounds = bounds)

        fun wildcard(kind: WildcardKind, bound: TypeRef? = null) =
            TypeRef(kind = TypeKind.WILDCARD, wildcard = kind, bounds = bound?.let { listOf(it) } ?: emptyList())
    }
}

@Serializable
enum class TypeKind {
    CLASS,
    ARRAY,
    PRIMITIVE,
    TYPEVAR,
    WILDCARD
}

@Serializable
enum class WildcardKind {
    OUT,    // extends in Java, out in Kotlin
    IN,     // super in Java, in in Kotlin
    UNBOUNDED  // ? in Java
}
