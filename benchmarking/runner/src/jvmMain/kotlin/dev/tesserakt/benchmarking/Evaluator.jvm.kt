package dev.tesserakt.benchmarking

import com.google.common.reflect.ClassPath
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure


actual val references: Map<String, (String) -> Reference> = run {
    val classes = findImplementations()
    classes.associate { clazz ->
        val ctor = clazz.constructors
            .find { it.parameters.size == 1 && it.parameters.single().type.jvmErasure == String::class }
            ?: throw IllegalStateException("Did not find an appropriate constructor for `${clazz.simpleName}` (`${clazz.qualifiedName}`)")
        clazz.referenceName to { query -> ctor.call(query) as Reference }
    }
}

// src: https://www.baeldung.com/java-find-all-classes-in-package
private fun findImplementations(): List<KClass<*>> {
    return ClassPath.from(ClassLoader.getSystemClassLoader())
        .allClasses
        .mapNotNull { clazz ->
            if (!clazz.packageName.startsWith("dev.tesserakt")) {
                return@mapNotNull null
            }
            clazz.load().kotlin.takeIf {
                it.isSubclassOf(Reference::class) && !it.isAbstract
            }
        }
}

private val KClass<*>.referenceName: String
    get() = simpleName!!.removeSuffix("Reference").lowercase()
