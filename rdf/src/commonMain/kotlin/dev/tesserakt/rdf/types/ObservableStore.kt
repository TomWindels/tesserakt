package dev.tesserakt.rdf.types

interface ObservableStore : MutableStore {

    interface Listener {
        fun onQuadAdded(quad: Quad)
        fun onQuadRemoved(quad: Quad)
    }

    fun addListener(listener: Listener)

    fun removeListener(listener: Listener)

}
