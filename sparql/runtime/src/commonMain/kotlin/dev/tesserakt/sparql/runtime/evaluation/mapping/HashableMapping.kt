package dev.tesserakt.sparql.runtime.evaluation.mapping

/**
 * A value-aware wrapper for [Mapping], having value-derived implementations for [Any.hashCode] and [Any.equals],
 *  allowing the wrapped instance to be used as the key for hash-based data structures.
 */
class HashableMapping(val inner: Mapping) {

    // we already have a 4 byte reference we are wrapping, so storing the hash code does not make us bigger
    // even though this is more expensive if it is never actually required; wrapping a mapping in this instance
    //  can only mean it is intended to be used in a hash-based collection
    private val hashCode = inner.data.contentHashCode()

    override fun equals(other: Any?): Boolean {
        // we don't want to be considered equal to regular mapping instances, as it isn't a reflective property,
        //  meaning
        return other is HashableMapping && inner.data.contentEquals(other.inner.data)
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun toString(): String {
        return "$inner (hashable)"
    }

    companion object {
        val EMPTY = HashableMapping(Mapping.EMPTY)
    }

}
