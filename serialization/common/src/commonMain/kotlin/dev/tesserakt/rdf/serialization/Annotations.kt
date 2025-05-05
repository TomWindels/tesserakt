package dev.tesserakt.rdf.serialization


@RequiresOptIn("Opt-in is required, as use of this method is not recommended")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class DelicateSerializationApi

@RequiresOptIn("Opt-in is required, as this is part of the internal serialization API, and is subject to change")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.TYPEALIAS)
annotation class InternalSerializationApi
