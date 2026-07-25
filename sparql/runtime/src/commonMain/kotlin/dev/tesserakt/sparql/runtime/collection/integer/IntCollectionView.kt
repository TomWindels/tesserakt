package dev.tesserakt.sparql.runtime.collection.integer

abstract class IntCollectionView : Iterable<Int> {

    abstract val size: Int

    abstract operator fun get(index: Int): Int

    // overridable by design
    override fun iterator(): IntIterator = object: IntIterator() {

        private var i = 0

        override fun hasNext(): Boolean {
            return i < size
        }

        override fun nextInt(): Int {
            return get(i++)
        }
    }

    final override fun equals(other: Any?): Boolean {
        if (other !is IntCollectionView) {
            return false
        }
        if (other.size != size) {
            return false
        }
        // not checking the hash code, as that's typically also O(size) when not cached
        val a = iterator()
        val b = other.iterator()
        while (a.hasNext()) {
            // size check already happened; a.hasNext == b.hasNext
            if (a.nextInt() != b.nextInt()) {
                return false
            }
        }
        return true
    }

    // non-final, as it's possible for overriding collections to cache it;
    //  we can't cache it here as we cannot guarantee for the contents to remain equal over time
    override fun hashCode(): Int {
        return contentHashCode()
    }

    /**
     * Returns a hashcode based on the content currently available through the [iterator]. An implementation
     *  guaranteeing constant contents can choose to cache the result from this function.
     */
    protected fun contentHashCode(): Int {
        var result = 0
        forEach {
            result = (result + it) shl 1
        }
        return result
    }

    override fun toString() = joinToString(prefix = "[", postfix = "]")

}
