package dev.tesserakt.rdf.serialization.common

fun <F: Format<*>> serializer(format: F): Serializer {
    return Format.serializer(format)
}

fun <C, F: Format<C>> serializer(format: F, config: C.() -> Unit): Serializer {
    return Format.serializer(format, config)
}
