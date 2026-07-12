package dev.tesserakt.rdf.types

interface MutableStore: Store, MutableCollection<Quad> {

    /**
     * The active [EncodingContext].
     * Exposed as a mutable variant, so systems using the encoded representation (such as SPARQL querying) can create
     *  encoded quad element representations using the same context. This allows for encoded representations of quad
     *  elements to exists, regardless of whether a quad with the corresponding quad element exists at the time.
     */
    override val context: MutableEncodingContext

    override fun addAll(elements: Collection<Quad>): Boolean {
        var result = false
        elements.forEach { result = add(it) || result }
        return result
    }

    override fun removeAll(elements: Collection<Quad>): Boolean {
        var result = false
        elements.forEach { result = remove(it) || result }
        return result
    }

    /**
     * Similar to [iterator], yielding the [EncodedQuad] representation of the various elements present in this store
     */
    override fun encodedIterator(): MutableIterator<EncodedQuad>

}
