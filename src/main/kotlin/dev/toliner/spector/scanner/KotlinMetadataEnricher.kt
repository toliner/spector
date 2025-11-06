package dev.toliner.spector.scanner

import dev.toliner.spector.model.*
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.AnnotationNode

/**
 * Enriches ClassInfo with Kotlin-specific metadata using kotlinx-metadata-jvm.
 */
class KotlinMetadataEnricher {

    fun enrichClassInfo(classBytes: ByteArray, classInfo: ClassInfo): ClassInfo {
        if (classInfo.kotlin == null) {
            return classInfo
        }

        val metadata = extractKotlinMetadata(classBytes) ?: return classInfo

        return when (metadata) {
            is KotlinClassMetadata.Class -> enrichFromKmClass(classInfo, metadata.kmClass)
            is KotlinClassMetadata.FileFacade -> enrichFromKmPackage(classInfo, metadata.kmPackage)
            is KotlinClassMetadata.MultiFileClassPart -> classInfo // TODO: Handle multi-file class parts
            is KotlinClassMetadata.SyntheticClass -> classInfo // Synthetic classes have no additional metadata
            is KotlinClassMetadata.Unknown -> classInfo
        }
    }

    private fun extractKotlinMetadata(classBytes: ByteArray): KotlinClassMetadata? {
        val classNode = ClassNode()
        ClassReader(classBytes).accept(classNode, 0)

        val metadataAnnotation = classNode.visibleAnnotations
            ?.firstOrNull { it.desc == "Lkotlin/Metadata;" }
            ?: return null

        val metadata = parseMetadataAnnotation(metadataAnnotation)
        return try {
            KotlinClassMetadata.readStrict(metadata)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseMetadataAnnotation(annotation: AnnotationNode): Metadata {
        val values = annotation.values?.chunked(2)?.associate {
            it[0] as String to it[1]
        } ?: emptyMap()

        return Metadata(
            kind = values["k"] as? Int,
            metadataVersion = (values["mv"] as? List<*>)?.map { it as Int }?.toIntArray(),
            data1 = (values["d1"] as? List<*>)?.map { it as String }?.toTypedArray(),
            data2 = (values["d2"] as? List<*>)?.map { it as String }?.toTypedArray(),
            extraString = values["xs"] as? String,
            packageName = values["pn"] as? String,
            extraInt = values["xi"] as? Int
        )
    }

    private fun enrichFromKmClass(classInfo: ClassInfo, kmClass: KmClass): ClassInfo {
        val kotlinInfo = KotlinClassInfo(
            isData = Flag.Class.IS_DATA(kmClass.flags),
            isSealed = Flag.Class.IS_SEALED(kmClass.flags),
            isValue = Flag.Class.IS_VALUE(kmClass.flags),
            isFunInterface = Flag.Class.IS_FUN(kmClass.flags),
            isInner = Flag.Class.IS_INNER(kmClass.flags),
            primaryConstructor = extractPrimaryConstructor(kmClass),
            properties = extractKotlinProperties(kmClass)
        )

        // Determine if this is a companion object
        val kind = if (Flag.Class.IS_COMPANION_OBJECT(kmClass.flags)) {
            ClassKind.KOTLIN_COMPANION
        } else if (kmClass.companionObject != null) {
            classInfo.kind
        } else {
            classInfo.kind
        }

        // Check if it's an object
        val finalKind = if (Flag.Class.IS_OBJECT(kmClass.flags)) {
            ClassKind.KOTLIN_OBJECT
        } else {
            kind
        }

        return classInfo.copy(
            kind = finalKind,
            kotlin = kotlinInfo
        )
    }

    private fun enrichFromKmPackage(classInfo: ClassInfo, kmPackage: KmPackage): ClassInfo {
        // For top-level functions/properties in Kotlin, stored in *Kt classes
        return classInfo
    }

    private fun extractPrimaryConstructor(kmClass: KmClass): KotlinConstructor? {
        val constructor = kmClass.constructors.firstOrNull {
            !Flag.Constructor.IS_SECONDARY(it.flags)
        } ?: return null

        val parameters = constructor.valueParameters.map { param ->
            KotlinParameter(
                name = param.name,
                type = kmTypeToTypeRef(param.type!!),
                hasDefault = Flag.ValueParameter.DECLARES_DEFAULT_VALUE(param.flags),
                isVararg = param.varargElementType != null
            )
        }

        return KotlinConstructor(parameters = parameters)
    }

    private fun extractKotlinProperties(kmClass: KmClass): List<KotlinPropertyRef> {
        return kmClass.properties.map { prop ->
            KotlinPropertyRef(
                name = prop.name,
                jvmFieldName = prop.fieldSignature?.name,
                getterName = prop.getterSignature?.name,
                setterName = prop.setterSignature?.name
            )
        }
    }

    private fun kmTypeToTypeRef(kmType: KmType): TypeRef {
        val classifier = kmType.classifier
        val isNullable = Flag.Type.IS_NULLABLE(kmType.flags)

        return when (classifier) {
            is KmClassifier.Class -> {
                val fqcn = classifier.name.replace('/', '.')
                val args = kmType.arguments.map { arg ->
                    when (arg) {
                        is KmTypeProjection.Invariant -> kmTypeToTypeRef(arg.type)
                        is KmTypeProjection.Covariant -> TypeRef.wildcard(WildcardKind.OUT, kmTypeToTypeRef(arg.type))
                        is KmTypeProjection.Contravariant -> TypeRef.wildcard(WildcardKind.IN, kmTypeToTypeRef(arg.type))
                        KmTypeProjection.Star -> TypeRef.wildcard(WildcardKind.UNBOUNDED)
                    }
                }
                TypeRef.classType(fqcn, args, isNullable)
            }
            is KmClassifier.TypeParameter -> {
                TypeRef.typeVar(classifier.name)
            }
            is KmClassifier.TypeAlias -> {
                // Expand type alias
                TypeRef.classType("kotlin.Any", nullable = isNullable)
            }
        }
    }
}
