package dev.tesserakt.rdf.types

interface ObservableStore : MutableStore {

    interface Listener {
        /**
         * Called for every quad being added, except when a quad that was already part of the store is being added,
         *  as the underlying store did not change.
         */
        fun onQuadAdded(quad: Quad) {}

        /**
         * Called for every quad being removed, except when a quad that was not part of the store is being
         *  removed, as the underlying store did not change.
         */
        fun onQuadRemoved(quad: Quad) {}

        /**
         * Identical to [onQuadAdded], but called with the [EncodedQuad] representation of the newly added [Quad]
         */
        fun onQuadAddedEncoded(quad: EncodedQuad) {}

        /**
         * Identical to [onQuadRemoved], but called with the [EncodedQuad] representation of the newly removed [Quad]
         */
        fun onQuadRemovedEncoded(quad: EncodedQuad) {}
    }

    fun addListener(listener: Listener)

    fun removeListener(listener: Listener)

}
