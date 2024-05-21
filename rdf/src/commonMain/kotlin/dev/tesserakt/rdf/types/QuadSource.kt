package dev.tesserakt.rdf.types

abstract class QuadSource: Iterable<Quad> {

    abstract fun filter(subject: Quad.Term?, predicate: Quad.Term?, `object`: Quad.Term?): Iterator<Quad>

}
