package dev.tesserakt.rdf.types

interface Store : Set<Quad> {

    /**
     * Creates an [Iterator] that yields all [Quad]s present inside this [Store], for which the values [s],
     *  [p] and [o] match the parameters, when provided
     */
    fun iter(s: Quad.Subject? = null, p: Quad.Predicate? = null, o: Quad.Object? = null, g: Quad.Graph? = null): Iterator<Quad>

}
