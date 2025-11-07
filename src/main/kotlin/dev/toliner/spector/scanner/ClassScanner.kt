package dev.toliner.spector.scanner

import dev.toliner.spector.model.*
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*

/**
 * Scans Java/Kotlin class files using ASM to extract type and member information.
 */
class ClassScanner {

    typealias MyAnnotationRetention = dev.toliner.spector.model.AnnotationRetention

    fun scanClass(classBytes: ByteArray): ClassInfo? {
        val visitor = ClassInfoVisitor()
        try {
            val reader = ClassReader(classBytes)
            reader.accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG)
            return visitor.buildClassInfo()
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

            // Parse type from descriptor
            val type = parseDescriptorToTypeRef(descriptor)

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

            // Parse method descriptor
            val methodType = Type.getType(descriptor)
            val returnType = parseTypeToTypeRef(methodType.returnType)
            val parameters = methodType.argumentTypes.mapIndexed { index, type ->
                ParameterInfo(
                    name = null, // We don't have parameter names without debug info
                    type = parseTypeToTypeRef(type)
                )
            }

            val throwsTypes = exceptions?.map { TypeRef.classType(internalNameToFqcn(it)) } ?: emptyList()

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

        fun buildClassInfo(): ClassInfo {
            val fqcn = internalNameToFqcn(internalName)
            val packageName = fqcn.substringBeforeLast('.', "")
            val kind = determineClassKind(access)
            val modifiers = accessToModifiers(access)

            val superClass = superName?.let { internalNameToFqcn(it) }?.takeIf { it != "java.lang.Object" }
            val interfaces = interfaceNames.map { internalNameToFqcn(it) }

            return ClassInfo(
                fqcn = fqcn,
                packageName = packageName,
                kind = kind,
                modifiers = modifiers,
                superClass = superClass,
                interfaces = interfaces,
                annotations = annotations,
                kotlin = if (isKotlinClass) KotlinClassInfo() else null
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
