package dev.tesserakt.rdf.types

interface MutableStore: Store, MutableCollection<Quad> {

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

}
