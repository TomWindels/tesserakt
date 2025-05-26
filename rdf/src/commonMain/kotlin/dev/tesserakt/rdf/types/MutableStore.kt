package dev.tesserakt.rdf.types

interface MutableStore: Store, MutableCollection<Quad> {

    override fun addAll(elements: Collection<Quad>): Boolean {
        var result = false
        elements.forEach { result = result || add(it) }
        return result
    }

    override fun removeAll(elements: Collection<Quad>): Boolean {
        var result = false
        elements.forEach { result = result || remove(it) }
        return result
    }

}
