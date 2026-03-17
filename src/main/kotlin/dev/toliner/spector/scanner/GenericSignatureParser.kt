package dev.toliner.spector.scanner

import dev.toliner.spector.model.TypeParameter
import dev.toliner.spector.model.TypeRef
import dev.toliner.spector.model.WildcardKind
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor

internal object GenericSignatureParser {

    data class ClassSignature(
        val typeParameters: List<TypeParameter>
    )

    data class MethodSignature(
        val typeParameters: List<TypeParameter>,
        val parameterTypes: List<TypeRef>,
        val returnType: TypeRef,
        val throwsTypes: List<TypeRef>
    )

    fun parseClassSignature(signature: String): ClassSignature? =
        runCatching {
            val visitor = ClassSignatureCollector()
            SignatureReader(signature).accept(visitor)
            visitor.finish()
        }.getOrNull()

    fun parseFieldSignature(signature: String): TypeRef? =
        runCatching {
            val visitor = TypeBuildingSignatureVisitor()
            SignatureReader(signature).acceptType(visitor)
            visitor.requireResult()
        }.getOrNull()

    fun parseMethodSignature(signature: String): MethodSignature? =
        runCatching {
            val visitor = MethodSignatureCollector()
            SignatureReader(signature).accept(visitor)
            visitor.finish()
        }.getOrNull()

    private class ClassSignatureCollector : SignatureVisitor(ASM9) {
        private val typeParameters = mutableListOf<TypeParameter>()
        private var currentTypeParameterName: String? = null
        private val currentBounds = mutableListOf<TypeRef>()

        override fun visitFormalTypeParameter(name: String) {
            flushTypeParameter()
            currentTypeParameterName = name
        }

        override fun visitClassBound(): SignatureVisitor = TypeBuildingSignatureVisitor { currentBounds += it }

        override fun visitInterfaceBound(): SignatureVisitor = TypeBuildingSignatureVisitor { currentBounds += it }

        override fun visitSuperclass(): SignatureVisitor {
            flushTypeParameter()
            return IgnoringSignatureVisitor()
        }

        override fun visitInterface(): SignatureVisitor = IgnoringSignatureVisitor()

        fun finish(): ClassSignature {
            flushTypeParameter()
            return ClassSignature(typeParameters = typeParameters.toList())
        }

        private fun flushTypeParameter() {
            val name = currentTypeParameterName ?: return
            typeParameters += TypeParameter(name = name, bounds = currentBounds.toList())
            currentTypeParameterName = null
            currentBounds.clear()
        }
    }

    private class MethodSignatureCollector : SignatureVisitor(ASM9) {
        private val typeParameters = mutableListOf<TypeParameter>()
        private val currentBounds = mutableListOf<TypeRef>()
        private val parameterTypes = mutableListOf<TypeRef>()
        private val throwsTypes = mutableListOf<TypeRef>()
        private var currentTypeParameterName: String? = null
        private var returnType: TypeRef? = null

        override fun visitFormalTypeParameter(name: String) {
            flushTypeParameter()
            currentTypeParameterName = name
        }

        override fun visitClassBound(): SignatureVisitor = TypeBuildingSignatureVisitor { currentBounds += it }

        override fun visitInterfaceBound(): SignatureVisitor = TypeBuildingSignatureVisitor { currentBounds += it }

        override fun visitParameterType(): SignatureVisitor {
            flushTypeParameter()
            return TypeBuildingSignatureVisitor { parameterTypes += it }
        }

        override fun visitReturnType(): SignatureVisitor {
            flushTypeParameter()
            return TypeBuildingSignatureVisitor { returnType = it }
        }

        override fun visitExceptionType(): SignatureVisitor = TypeBuildingSignatureVisitor { throwsTypes += it }

        fun finish(): MethodSignature {
            flushTypeParameter()
            return MethodSignature(
                typeParameters = typeParameters.toList(),
                parameterTypes = parameterTypes.toList(),
                returnType = returnType ?: TypeRef.primitiveType("void"),
                throwsTypes = throwsTypes.toList()
            )
        }

        private fun flushTypeParameter() {
            val name = currentTypeParameterName ?: return
            typeParameters += TypeParameter(name = name, bounds = currentBounds.toList())
            currentTypeParameterName = null
            currentBounds.clear()
        }
    }

    private open class IgnoringSignatureVisitor : SignatureVisitor(ASM9) {
        override fun visitClassBound(): SignatureVisitor = IgnoringSignatureVisitor()
        override fun visitInterfaceBound(): SignatureVisitor = IgnoringSignatureVisitor()
        override fun visitSuperclass(): SignatureVisitor = IgnoringSignatureVisitor()
        override fun visitInterface(): SignatureVisitor = IgnoringSignatureVisitor()
        override fun visitParameterType(): SignatureVisitor = IgnoringSignatureVisitor()
        override fun visitReturnType(): SignatureVisitor = IgnoringSignatureVisitor()
        override fun visitExceptionType(): SignatureVisitor = IgnoringSignatureVisitor()
        override fun visitArrayType(): SignatureVisitor = this
        override fun visitTypeArgument(wildcard: Char): SignatureVisitor = IgnoringSignatureVisitor()
    }

    private class TypeBuildingSignatureVisitor(
        private val onCompleted: ((TypeRef) -> Unit)? = null
    ) : SignatureVisitor(ASM9) {
        private var arrayDimensions = 0
        private var className: String? = null
        private val typeArguments = mutableListOf<TypeRef>()
        private var result: TypeRef? = null

        override fun visitBaseType(descriptor: Char) {
            complete(primitiveType(descriptor))
        }

        override fun visitTypeVariable(name: String) {
            complete(TypeRef.typeVar(name))
        }

        override fun visitArrayType(): SignatureVisitor {
            arrayDimensions += 1
            return this
        }

        override fun visitClassType(name: String) {
            className = internalNameToFqcn(name)
        }

        override fun visitInnerClassType(name: String) {
            className = "${className ?: error("Missing outer class name")}\$$name"
        }

        override fun visitTypeArgument() {
            typeArguments += TypeRef.wildcard(WildcardKind.UNBOUNDED)
        }

        override fun visitTypeArgument(wildcard: Char): SignatureVisitor {
            return TypeBuildingSignatureVisitor { argument ->
                typeArguments += when (wildcard) {
                    SignatureVisitor.EXTENDS -> TypeRef.wildcard(WildcardKind.OUT, argument)
                    SignatureVisitor.SUPER -> TypeRef.wildcard(WildcardKind.IN, argument)
                    else -> argument
                }
            }
        }

        override fun visitEnd() {
            val fqcn = className ?: return
            complete(TypeRef.classType(fqcn, typeArguments.toList()))
        }

        fun requireResult(): TypeRef = result ?: error("Generic signature did not produce a type")

        private fun complete(typeRef: TypeRef) {
            if (result != null) return

            var finalType = typeRef
            repeat(arrayDimensions) {
                finalType = TypeRef.arrayType(finalType)
            }

            result = finalType
            onCompleted?.invoke(finalType)
        }
    }

    private fun primitiveType(descriptor: Char): TypeRef =
        when (descriptor) {
            'V' -> TypeRef.primitiveType("void")
            'Z' -> TypeRef.primitiveType("boolean")
            'C' -> TypeRef.primitiveType("char")
            'B' -> TypeRef.primitiveType("byte")
            'S' -> TypeRef.primitiveType("short")
            'I' -> TypeRef.primitiveType("int")
            'F' -> TypeRef.primitiveType("float")
            'J' -> TypeRef.primitiveType("long")
            'D' -> TypeRef.primitiveType("double")
            else -> TypeRef.classType("java.lang.Object")
        }

    private fun internalNameToFqcn(internalName: String): String = internalName.replace('/', '.')
}
