package tesserakt.rdf.types

abstract class TripleSource {

    abstract fun filter(subject: Triple.Term?, predicate: Triple.Term?, `object`: Triple.Term?): Iterator<Triple>

}
