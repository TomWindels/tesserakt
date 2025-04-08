package dev.tesserakt.rdf.n3.dsl

import dev.tesserakt.rdf.n3.ExperimentalN3Api
import dev.tesserakt.rdf.n3.Quad
import dev.tesserakt.rdf.n3.Store
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import kotlin.jvm.JvmInline
import dev.tesserakt.rdf.types.Quad as RdfQuad

@ExperimentalN3Api
@Suppress("NOTHING_TO_INLINE", "PropertyName", "unused")
class N3Context internal constructor(
    private val environment: Environment,
    val consumer: Consumer
) {

    var _blank_index = 0

    interface Consumer {

        /** Regular triple receiver **/
        fun process(
            subject: Quad.Term,
            predicate: Quad.Term,
            `object`: Quad.Term
        )

        /** A method processing incoming RDF lists, returning a term pointing to that list **/
        // default impl creates blank nodes acting like the list, no optimisation in representation (like turtle)
        //  out of the box
        fun process(context: N3Context, list: List): Quad.Term = with(context) {
            return if (list.data.isEmpty()) {
                RDF.nil.toN3Term()
            } else {
                val first = genBlankNodeId()
                var current = first
                val iter = list.data.iterator()
                // actually filling in the data
                current has RDF.first.toN3Term() being iter.next()
                while (iter.hasNext()) {
                    val next = genBlankNodeId()
                    next has RDF.first.toN3Term() being iter.next()
                    current has RDF.rest.toN3Term() being next
                    current = next
                }
                // ending the list
                current has RDF.rest.toN3Term() being RDF.nil.toN3Term()
                // returning the term pointing to the start of the list
                first
            }
        }

    }

    open class Environment(
        val path: String
    ) {
        val RdfQuad.NamedTerm.relativePath: String
            get() = value.removePrefix(path)
    }

    /** Creates an environment-aware `NamedTerm`, e.g. "shape" on "localhost:3000/" becomes `http://localhost:3000/shape` **/
    fun local(name: String) = "${environment.path}$name".asNamedTerm()

    infix fun Quad.Term.has(predicate: Quad.Term) = Statement(this, predicate)

    infix fun Quad.Term.has(predicate: RdfQuad.Term) = Statement(this, predicate.toN3Term())

    inner class Statement(val _s: Quad.Term, val _p: Quad.Term) {

        inline infix fun being(literal: Int) = consumer.process(_s, _p, literal.asLiteralTerm().toN3Term())

        inline infix fun being(literal: Long) = consumer.process(_s, _p, literal.asLiteralTerm().toN3Term())

        inline infix fun being(literal: Float) = consumer.process(_s, _p, literal.asLiteralTerm().toN3Term())

        inline infix fun being(literal: Double) = consumer.process(_s, _p, literal.asLiteralTerm().toN3Term())

        inline infix fun being(value: Quad.Term) = consumer.process(_s, _p, value)

        inline infix fun being(blank: Blank) = consumer.process(_s, _p, blank._name)

        inline infix fun being(multiple: Multiple) {
            multiple.data.forEach { term ->
                consumer.process(subject = _s, predicate = _p, `object`= term)
            }
        }

        inline infix fun being(list: List) = consumer.process(_s, _p, consumer.process(this@N3Context, list))

    }

    inner class Blank(val _name: Quad.Term) {

        inline infix fun Quad.Term.being(literal: Int) {
            consumer.process(subject = _name, predicate = this, `object`= literal.asLiteralTerm().toN3Term())
        }

        inline infix fun Quad.Term.being(literal: Long) {
            consumer.process(subject = _name, predicate = this, `object`= literal.asLiteralTerm().toN3Term())
        }

        inline infix fun Quad.Term.being(literal: Float) {
            consumer.process(subject = _name, predicate = this, `object`= literal.asLiteralTerm().toN3Term())
        }

        inline infix fun Quad.Term.being(literal: Double) {
            consumer.process(subject = _name, predicate = this, `object`= literal.asLiteralTerm().toN3Term())
        }

        inline infix fun Quad.Term.being(term: Quad.Term) {
            consumer.process(subject = _name, predicate = this, `object`= term)
        }

        inline infix fun Quad.Term.being(blank: Blank) {
            consumer.process(subject = _name, predicate = this, `object`= blank._name)
        }

        inline infix fun Quad.Term.being(multiple: Multiple) {
            multiple.data.forEach { term ->
                consumer.process(subject = _name, predicate = this, `object`= term)
            }
        }

        inline infix fun Quad.Term.being(list: List) {
            consumer.process(subject = _name, predicate = this, `object`= consumer.process(this@N3Context, list))
        }

    }

    @JvmInline
    value class List internal constructor(val data: Array<out Quad.Term>)

    @JvmInline
    value class Multiple internal constructor(val data: Array<out Quad.Term>)

    inline fun blank(block: Blank.() -> Unit): Quad.Term {
        return Blank(genBlankNodeId()).apply(block)._name
    }

    inline fun statements(path: String = "", noinline block: N3Context.() -> Unit): Quad.Term {
        return Quad.Term.StatementsList(Store().apply { insert(environment = Environment(path), block = block) })
    }

    inline fun genBlankNodeId() = RdfQuad.BlankTerm(_blank_index++).toN3Term()

    fun list(data: Collection<Quad.Term>) = List(data = data.toTypedArray())

    fun list(vararg data: Quad.Term) = List(data)

    fun multiple(data: Collection<Quad.Term>) = Multiple(data.toTypedArray())

    fun multiple(vararg data: Quad.Term) = Multiple(data)

    fun String.asNamedTerm(): Quad.Term = with(dev.tesserakt.rdf.types.Quad) { asNamedTerm() }.toN3Term()

    fun String.asLiteralTerm(type: String): Quad.Term = with(dev.tesserakt.rdf.types.Quad) { dev.tesserakt.rdf.types.Quad.Literal(value = this@asLiteralTerm, type = type.asNamedTerm()) }.toN3Term()

    fun String.asLiteralTerm(type: RdfQuad.NamedTerm): Quad.Term = dev.tesserakt.rdf.types.Quad.Literal(value = this@asLiteralTerm, type = type).toN3Term()

    companion object

}
