package dev.tesserakt.rdf.types


data class MutableEncodingContextBuilder(
    /**
     * Whether concurrent modification of the mutable context is required. Off by default.
     */
    var concurrent: Boolean = false,
    /**
     * A hint for the initial capacity (# of distinct quad elements). Defaults to 10.
     */
    var initialCapacity: Int = 10,
)

expect fun MutableEncodingContext(builder: MutableEncodingContextBuilder.() -> Unit = {}): MutableEncodingContext
