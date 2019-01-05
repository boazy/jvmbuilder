/*
 * Copyright (C) 2018 Jose Francisco Fiorillo Verenzuela
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.boazy.kbuilder.annotations.processor

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.boazy.kbuilder.annotations.GenerateBuilder
import me.eugeniomarletti.kotlin.metadata.*
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.deserialization.NameResolver
import me.eugeniomarletti.kotlin.processing.KotlinAbstractProcessor
import java.io.File
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedOptions
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic.Kind.ERROR

private val builderAnnotation = GenerateBuilder::class.java
private val annotationName = builderAnnotation.canonicalName

private object Options {
    const val LINE_SEPARATOR = "lineSeparator"
    const val BUILDER_CLASS_SUFFIX = "Builder"
}

@AutoService(Processor::class)
@SupportedOptions(Options.LINE_SEPARATOR)
class GenerateBuilderProcessor : KotlinAbstractProcessor(), KotlinMetadataUtils {
    private lateinit var lineSeparator: String
    private lateinit var builderClassSuffix: String
    private lateinit var metadataHelper: MetadataHelper

    override fun init(processingEnv: ProcessingEnvironment) {
        lineSeparator = processingEnv.options[Options.LINE_SEPARATOR] ?: System.lineSeparator()
        builderClassSuffix = processingEnv.options[Options.BUILDER_CLASS_SUFFIX] ?: "Builder"
        super.init(processingEnv)
    }

    override fun getSupportedAnnotationTypes() = setOf(annotationName)

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        val annotatedElements = roundEnv.getElementsAnnotatedWith(elementUtils.getTypeElement(annotationName))
        if (annotatedElements.isEmpty()) return false

        annotatedElements.filter { it.kotlinMetadata !is KotlinClassMetadata }.forEach { errorMustBeDataClass(it) }
        annotatedElements.filter { it.kotlinMetadata is KotlinClassMetadata }.map { generateBuilder(it) }
        return true
    }

    private fun generateBuilder(element: Element) {
        val metadata = element.kotlinMetadata

        if (metadata !is KotlinClassMetadata) {
            errorMustBeDataClass(element)
            return
        }

        val classData = metadata.data
        metadataHelper = MetadataHelper(classData.classProto, classData.nameResolver, element as TypeElement, messager)
        with(metadataHelper) {
            // Determine package name and class name for target data class
            val fqClassName = getNameUsingNameResolver().replace('/', '.')
            val `package` = getNameUsingNameResolver().substringBeforeLast('/').replace('/', '.')
            val className = fqClassName.substringAfter(`package`).replace(".", "")

            // Determine builder class
            val builderConfig = element.getAnnotation(builderAnnotation)
            val builderClassName = builderConfig.className.takeIf { it.isNotEmpty() } ?: className+builderClassSuffix

            // Determine builder type names and parameters
            val typeArguments = generateParameterizedTypes()
            val setterReturnClass = generateFunctionsReturnClass(typeArguments, "$`package`.$builderClassName")
            val buildReturnClass = generateBuildFunctionReturnsClass(typeArguments, element)

            val (optionalValues, requiredValues) = getConstructorParams(classProto)
            val hasDefaultInstance = requiredValues.isEmpty() && builderConfig.optimizeCopy
            val parameters = generatePropertyAndBuilderFunPerProperty(builderConfig.prefix, setterReturnClass)
            val builderFun = generateBuildFunction(
                requiredValues, optionalValues,
                buildReturnClass, element, hasDefaultInstance
            )

            // Create target file
            val fileName = "$builderClassName.kt"
            val file = File(generatedDir, fileName)

            FileSpec.builder(`package`, builderClassName)
                .addComment("Code auto-generated by KBuilder. Do not edit.")
                .addType(TypeSpec.classBuilder(builderClassName)
                    .apply { if (hasDefaultInstance) addDefaultInstance(buildReturnClass) }
                    .addProperties(parameters.map { it.first })
                    .addFunctions(parameters.map { it.second })
                    .addFunction(builderFun)
                    .addTypeVariables(typeArguments)
                    .build()
                )
                .build()
                .writeTo(file)
        }
    }

    private fun TypeSpec.Builder.addDefaultInstance(type: TypeName) {
        val rawType = (type as? ParameterizedTypeName)?.rawType ?: type
        val defaultInstanceProp = PropertySpec.builder("defaultInstance", type)
            .addModifiers(KModifier.PRIVATE)
            .initializer("$rawType()")
            .build()
        val companion = TypeSpec.companionObjectBuilder()
            .addModifiers(KModifier.PRIVATE)
            .addProperty(defaultInstanceProp)
            .build()
        addType(companion)
    }

    private fun generatePropertyAndBuilderFunPerProperty(prefix: String, typeName: TypeName) = with(metadataHelper) {
        classProto.constructorList
            .single { it.isPrimary }
            .valueParameterList
            .map { valueParameter ->
                val name = valueParameter.getNameUsingNameResolver()
                val type = valueParameter.resolveType()
                val functionName = if (prefix.isNotEmpty()) prefix + name.capitalize() else name
                Pair(PropertySpec.varBuilder(name,
                    type.resolve(), KModifier.PRIVATE)
                    .initializer("null").build(),
                    FunSpec.builder(functionName)
                        .addParameter(ParameterSpec.builder(name, type).build())
                        .returns(typeName)
                        .addStatement("this.$name = $name")
                        .addStatement("return this").build()
                )
            }
    }

    private fun generateParameterizedTypes() = with(metadataHelper) {
        classProto.typeParameterList
            .map { typeArgument ->
                val parameterizedTypeClass = typeArgument.upperBoundOrBuilderList
                    .map {
                        when (it) {
                            is ProtoBuf.Type.Builder -> it.build().resolveType()
                            is ProtoBuf.Type -> it.resolveType()
                            else -> {
                                throw IllegalArgumentException("$it bounds")
                            }
                        }
                    }.toTypedArray()
                return@map if (parameterizedTypeClass.isNotEmpty())
                    TypeVariableName(typeArgument.getNameUsingNameResolver(), *parameterizedTypeClass)
                else
                    TypeVariableName(typeArgument.getNameUsingNameResolver())
            }
    }

    private fun Iterable<ProtoBuf.ValueParameter>.asOptionalArguments(call: String, source: String) =
        with(metadataHelper) {
            joinToString(prefix = "return $call(", postfix = ")") {
                val name = it.getNameUsingNameResolver()
                "$name = this.$name ?: $source.$name"
            }
        }

    private fun Iterable<ProtoBuf.ValueParameter>.asRequiredArguments(prefix: String, postfix: String) =
        with(metadataHelper) {
            joinToString(prefix = prefix, postfix = postfix) {
                val name = it.getNameUsingNameResolver()
                val type = it.resolveType()
                "$name = ${if (type.nullable) "this.$name" else "this.$name ?: " +
                    """throw IllegalArgumentException("Property $name is mandatory and must be set in builder")"""}"
            }
        }

    /**
     * Generate simple builder with only required arguments:
     *
     * e.g.
     * ```
     * fun build() {
     *     return Foo(a, b)
     * }
     * ```
     */
    private fun generateBuildBody(
        element: TypeElement,
        requiredValues: List<ProtoBuf.ValueParameter>
    ) = requiredValues
        .asRequiredArguments(prefix = "return ${element.asClassName()}(", postfix = ")$lineSeparator")

    /**
     * Generate a builder with only optional values based on the default instance
     */
    private fun generateBuildFromDefault(
        element: TypeElement,
        optionalValues: List<ProtoBuf.ValueParameter>
    ) = optionalValues.asOptionalArguments(
        element.asClassName().toString(),
        "defaultInstance"
    )

    /**
     * Generate builder with both required and optional arguments.
     *
     * e.g.
     * ```
     * fun build() {
     *     val result = Foo(a, b)
     *     return result.copy(x = x ?: result.x, y = y ?: result.y) // override default values
     * }
     * ```
     */
    private fun generateBuildBodyWithDefaults(
        element: TypeElement,
        requiredValues: List<ProtoBuf.ValueParameter>,
        optionalValues: List<ProtoBuf.ValueParameter>
    ) = with(metadataHelper) {
        val creationAssignment = requiredValues
            .asRequiredArguments(prefix = "var result = ${element.asClassName()}(", postfix = ")")
        val useProvidedValuesForDefaultValues = optionalValues
            .asOptionalArguments("result.copy", "result")

        "$creationAssignment$lineSeparator$useProvidedValuesForDefaultValues$lineSeparator"
    }

    private fun getConstructorParams(classProto: ProtoBuf.Class) =
// Get value parameters from primary constructor
// These are the data class properties used for the builder
        classProto
            .constructorList
            .single { it.isPrimary }
            .valueParameterList
            // Separate optional values (with default value) and required values (without default)
            .partition { it.declaresDefaultValue }

    private fun generateBuildFunction(
        requiredValues: List<ProtoBuf.ValueParameter>,
        optionalValues: List<ProtoBuf.ValueParameter>,
        returnClassName: TypeName,
        element: TypeElement,
        hasDefaultInstance: Boolean
    ): FunSpec {
        val body = when {
            hasDefaultInstance -> generateBuildFromDefault(element, optionalValues)
            optionalValues.isEmpty() -> generateBuildBody(element, requiredValues)
            else -> generateBuildBodyWithDefaults(element, requiredValues, optionalValues)
        }

        return FunSpec.builder("build")
            .returns(returnClassName)
            .addCode(body)
            .build()
    }

    private fun generateFunctionsReturnClass(typeArguments: List<TypeVariableName>, fqBuilderClassName: String): TypeName =
        if (typeArguments.isNotEmpty())
            ClassName.bestGuess(fqBuilderClassName).parameterizedBy(*typeArguments.toTypedArray())
        else
            ClassName.bestGuess(fqBuilderClassName)

    private fun generateBuildFunctionReturnsClass(typeArguments: List<TypeVariableName>, element: TypeElement): TypeName =
        if (typeArguments.isNotEmpty())
            element.asClassName().parameterizedBy(*typeArguments.toTypedArray())
        else
            element.asClassName()

    private fun errorMustBeDataClass(element: Element) {
        messager.printMessage(ERROR,
            "@${builderAnnotation.simpleName} can't be applied to $element: must be a Kotlin data class", element)
    }

    internal class MetadataHelper(
        val classProto: ProtoBuf.Class,
        private val nameResolver: NameResolver,
        private val element: TypeElement,
        private val messager: Messager) {

        private val appliedType = AppliedType.get(element)
        private val builder = element.getAnnotation(builderAnnotation)

        fun ProtoBuf.ValueParameterOrBuilder.getNameUsingNameResolver() = nameResolver.getString(this.name)
        fun ProtoBuf.TypeParameterOrBuilder.getNameUsingNameResolver() = nameResolver.getString(this.name)
        fun getNameUsingNameResolver() = nameResolver.getString(classProto.fqName)
        fun ProtoBuf.ValueParameterOrBuilder.resolveType() = type.asTypeName(nameResolver, classProto::getTypeParameter, false)
        fun ProtoBuf.Type.resolveType() = asTypeName(nameResolver, classProto::getTypeParameter, false)
        fun TypeName.resolve() = appliedType.resolver.resolve(this.asNullable())
    }
}