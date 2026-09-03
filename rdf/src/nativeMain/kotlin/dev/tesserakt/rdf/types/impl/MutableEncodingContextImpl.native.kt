package dev.tesserakt.rdf.types.impl

internal actual fun ConcurrentMutableEncodingContextImpl(): MutableEncodingContextImpl {
    // native does allow for multithreading, but we don't have proper structures implemented to actually
    //  correctly support it
    TODO("Not yet implemented")
}
