package dev.toliner.spector.scanner

import dev.toliner.spector.model.*
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*

/**
 * Result of scanning a class file, containing class info and member information.
 */
data class ClassScanResult(
    val classInfo: ClassInfo,
    val fields: List<FieldInfo>,
    val methods: List<MethodInfo>
)

/**
 * Scans Java/Kotlin class files using ASM to extract type and member information.
 */
class ClassScanner {

    typealias MyAnnotationRetention = dev.toliner.spector.model.AnnotationRetention

    fun scanClass(classBytes: ByteArray): ClassScanResult? {
        val visitor = ClassInfoVisitor()
        try {
            val reader = ClassReader(classBytes)
            reader.accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG)
            return visitor.buildClassScanResult()
        } catch (e: Exception) {
            // Handle corrupted or unsupported class files
            return null
        }
    }

    private class ClassInfoVisitor : ClassVisitor(ASM9) {
        private var internalName: String = ""
        private var access: Int = 0
        private var signature: String? = null
        private var superName: String? = null
        private var interfaceNames: Array<String> = emptyArray()
        private val annotations = mutableListOf<AnnotationInfo>()
        private val fields = mutableListOf<FieldInfo>()
        private val methods = mutableListOf<MethodInfo>()
        private var isKotlinClass = false

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<String>?
        ) {
            this.internalName = name
            this.access = access
            this.signature = signature
            this.superName = superName
            this.interfaceNames = interfaces ?: emptyArray()
        }

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            if (descriptor == "Lkotlin/Metadata;") {
                isKotlinClass = true
            }
            val retention = if (visible) MyAnnotationRetention.RUNTIME else MyAnnotationRetention.CLASS
            annotations.add(AnnotationInfo(desc = descriptor, retention = retention))
            return null // For now, we don't parse annotation values
        }

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?
        ): FieldVisitor? {
            val visibility = accessToVisibility(access)
            val static = (access and ACC_STATIC) != 0
            val isFinal = (access and ACC_FINAL) != 0
            val isSynthetic = (access and ACC_SYNTHETIC) != 0
            val isEnumConstant = (access and ACC_ENUM) != 0

            val type = signature
                ?.let(GenericSignatureParser::parseFieldSignature)
                ?: parseDescriptorToTypeRef(descriptor)

            fields.add(
                FieldInfo(
                    ownerFqcn = internalNameToFqcn(internalName),
                    name = name,
                    visibility = visibility,
                    static = static,
                    annotations = emptyList(),
                    jvmDesc = descriptor,
                    type = type,
                    isEnumConstant = isEnumConstant,
                    isFinal = isFinal,
                    isSynthetic = isSynthetic
                )
            )
            return null
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor? {
            val visibility = accessToVisibility(access)
            val static = (access and ACC_STATIC) != 0
            val isFinal = (access and ACC_FINAL) != 0
            val isAbstract = (access and ACC_ABSTRACT) != 0
            val isSynthetic = (access and ACC_SYNTHETIC) != 0
            val isBridge = (access and ACC_BRIDGE) != 0
            val isConstructor = name == "<init>"

            val methodType = Type.getType(descriptor)
            val signatureInfo = signature?.let(GenericSignatureParser::parseMethodSignature)
            val descriptorReturnType = parseTypeToTypeRef(methodType.returnType)
            val descriptorParameters = methodType.argumentTypes.map { type ->
                ParameterInfo(
                    name = null, // We don't have parameter names without debug info
                    type = parseTypeToTypeRef(type)
                )
            }
            val returnType = signatureInfo?.returnType ?: descriptorReturnType
            val parameters = mergeParameterTypes(
                descriptorParameters = descriptorParameters,
                signatureParameterTypes = signatureInfo?.parameterTypes,
                isConstructor = isConstructor
            )

            val throwsTypes = signatureInfo?.throwsTypes?.takeIf { it.isNotEmpty() }
                ?: exceptions?.map { TypeRef.classType(internalNameToFqcn(it)) }
                ?: emptyList()

            methods.add(
                MethodInfo(
                    ownerFqcn = internalNameToFqcn(internalName),
                    name = name,
                    visibility = visibility,
                    static = static,
                    annotations = emptyList(),
                    jvmDesc = descriptor,
                    returnType = returnType,
                    parameters = parameters,
                    typeParameters = signatureInfo?.typeParameters ?: emptyList(),
                    throws = throwsTypes,
                    isConstructor = isConstructor,
                    isSynthetic = isSynthetic,
                    isBridge = isBridge,
                    isFinal = isFinal,
                    isAbstract = isAbstract
                )
            )
            return null
        }

        fun buildClassScanResult(): ClassScanResult {
            val fqcn = internalNameToFqcn(internalName)
            val packageName = fqcn.substringBeforeLast('.', "")
            val kind = determineClassKind(access)
            val modifiers = accessToModifiers(access)
            val parsedClassSignature = signature?.let(GenericSignatureParser::parseClassSignature)

            val superClass = superName?.let { internalNameToFqcn(it) }?.takeIf { it != "java.lang.Object" }
            val interfaces = interfaceNames.map { internalNameToFqcn(it) }

            val classInfo = ClassInfo(
                fqcn = fqcn,
                packageName = packageName,
                kind = kind,
                modifiers = modifiers,
                superClass = superClass,
                interfaces = interfaces,
                typeParameters = parsedClassSignature?.typeParameters ?: emptyList(),
                annotations = annotations,
                kotlin = if (isKotlinClass) KotlinClassInfo() else null
            )

            return ClassScanResult(
                classInfo = classInfo,
                fields = fields.toList(),
                methods = methods.toList()
            )
        }

        private fun determineClassKind(access: Int): ClassKind {
            return when {
                (access and ACC_ANNOTATION) != 0 -> ClassKind.ANNOTATION
                (access and ACC_ENUM) != 0 -> ClassKind.ENUM
                (access and ACC_INTERFACE) != 0 -> ClassKind.INTERFACE
                else -> ClassKind.CLASS
            }
        }

        private fun accessToModifiers(access: Int): Set<ClassModifier> {
            val modifiers = mutableSetOf<ClassModifier>()
            if ((access and ACC_PUBLIC) != 0) modifiers.add(ClassModifier.PUBLIC)
            if ((access and ACC_PROTECTED) != 0) modifiers.add(ClassModifier.PROTECTED)
            if ((access and ACC_PRIVATE) != 0) modifiers.add(ClassModifier.PRIVATE)
            if ((access and ACC_FINAL) != 0) modifiers.add(ClassModifier.FINAL)
            if ((access and ACC_ABSTRACT) != 0) modifiers.add(ClassModifier.ABSTRACT)
            if ((access and ACC_STATIC) != 0) modifiers.add(ClassModifier.STATIC)

            // Package-private if no visibility modifier
            if (modifiers.none { it in setOf(ClassModifier.PUBLIC, ClassModifier.PROTECTED, ClassModifier.PRIVATE) }) {
                modifiers.add(ClassModifier.PACKAGE)
            }

            return modifiers
        }

        private fun accessToVisibility(access: Int): Visibility {
            return when {
                (access and ACC_PUBLIC) != 0 -> Visibility.PUBLIC
                (access and ACC_PROTECTED) != 0 -> Visibility.PROTECTED
                (access and ACC_PRIVATE) != 0 -> Visibility.PRIVATE
                else -> Visibility.PACKAGE
            }
        }

        private fun internalNameToFqcn(internalName: String): String {
            return internalName.replace('/', '.')
        }

        private fun mergeParameterTypes(
            descriptorParameters: List<ParameterInfo>,
            signatureParameterTypes: List<TypeRef>?,
            isConstructor: Boolean
        ): List<ParameterInfo> {
            val signatureTypes = signatureParameterTypes ?: return descriptorParameters

            if (signatureTypes.size == descriptorParameters.size) {
                return signatureTypes.map { type ->
                    ParameterInfo(
                        name = null,
                        type = type
                    )
                }
            }

            if (isConstructor && signatureTypes.size < descriptorParameters.size) {
                val syntheticPrefixCount = descriptorParameters.size - signatureTypes.size
                return descriptorParameters.mapIndexed { index, parameter ->
                    if (index < syntheticPrefixCount) {
                        parameter
                    } else {
                        parameter.copy(type = signatureTypes[index - syntheticPrefixCount])
                    }
                }
            }

            return descriptorParameters
        }

        private fun parseDescriptorToTypeRef(descriptor: String): TypeRef {
            val type = Type.getType(descriptor)
            return parseTypeToTypeRef(type)
        }

        private fun parseTypeToTypeRef(type: Type): TypeRef {
            return when (type.sort) {
                Type.OBJECT -> TypeRef.classType(type.className)
                Type.ARRAY -> TypeRef.arrayType(parseTypeToTypeRef(type.elementType))
                Type.VOID -> TypeRef.primitiveType("void")
                Type.BOOLEAN -> TypeRef.primitiveType("boolean")
                Type.CHAR -> TypeRef.primitiveType("char")
                Type.BYTE -> TypeRef.primitiveType("byte")
                Type.SHORT -> TypeRef.primitiveType("short")
                Type.INT -> TypeRef.primitiveType("int")
                Type.FLOAT -> TypeRef.primitiveType("float")
                Type.LONG -> TypeRef.primitiveType("long")
                Type.DOUBLE -> TypeRef.primitiveType("double")
                else -> TypeRef.classType("java.lang.Object") // fallback
            }
        }
    }
}
