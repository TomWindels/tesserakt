package dev.tesserakt.rdf.turtle.serialization

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import kotlin.jvm.JvmInline

// TODO: blank node syntax `[]` support
// TODO: improved exception handling

internal class Deserializer(
    source: Iterator<TurtleToken>,
    base: String = DEFAULT_BASE,
) : Iterator<Quad> {

    data class Base(
        // scheme + host + path + file part of the path only, e.g. `http://example.org/my/path/my-file.ttl`
        private val full: String
    ) {

        // scheme part of the path only, e.g. `http:`
        private val scheme = full.substring(
            startIndex = 0,
            endIndex = full.indexOf(':') + 1
        )

        // scheme + host part of the path only, e.g. `http://example.org`
        private val host = run {
            val delimiterPos = full.indexOf('/', startIndex = scheme.length + 2)
            when {
                delimiterPos != -1 -> full.take(delimiterPos)
                else -> full
            }
        }

        // scheme + host + path part of the path only, e.g. `http://example.org/my/path/`
        private val path = run {
            val delimiterPos = full.lastIndexOf('/')
            when {
                delimiterPos != -1 -> full.take(delimiterPos + 1)
                else -> "$full/"
            }
        }

        fun update(new: TurtleToken.TermToken): Base {
            return when (new) {
                is TurtleToken.RelativeTerm -> {
                    Base(full = resolve(new).value)
                }

                is TurtleToken.Term -> {
                    Base(full = new.value)
                }

                else -> throw IllegalArgumentException("Invalid base declaration: $new")
            }
        }

        fun resolve(term: TurtleToken.RelativeTerm): Quad.NamedTerm {
            val value = when {
                term.value.isEmpty() -> full

                term.value.length > 1 && term.value[0] == '/' && term.value[1] == '/' -> {
                    scheme + term.value
                }

                term.value[0] == PathDelimiter -> {
                    host + term.value
                }

                term.value[0] in OtherDelimiters -> {
                    full.substringBeforeLast(term.value[0]) + term.value
                }

                else -> {
                    path + term.value
                }
            }
            return Quad.NamedTerm(value)
        }

        companion object {
            val PathDelimiter = '/'
            val OtherDelimiters = charArrayOf('#', '?')
        }

    }

    /**
     * An abstraction representing a more complex structure at the subject or object position defined within
     *  the spec: a blank node `[]`, or a list `()`
     */
    private sealed class Structure : Iterator<Quad> {

        /**
         * The identifying name of the structure, which should be used when referencing this structure in subsequent
         *  quads. For example, `<term> <term> []` would have to emit `<term> <term> Structure::name`
         */
        abstract val name: Quad.Term

        class BlankNode(
            private val parent: Deserializer,
        ) : Structure() {

            override val name = Quad.BlankTerm(id = parent.blankTermCount++)
            private var p: Quad.NamedTerm? = null
            private var child: Structure? = null
            private var next: Quad? = null

            init {
                check(parent.source.peek() == TurtleToken.Structural.BlankStart) {
                    "Expected ${TurtleToken.Structural.BlankStart}, got ${parent.source.peek()}"
                }
                parent.source.consume()
            }

            override fun hasNext(): Boolean {
                if (next != null) {
                    return true
                }
                next = prepareNext()
                return next != null
            }

            override fun next(): Quad {
                val next = next ?: prepareNext()
                this.next = null
                return next ?: throw NoSuchElementException()
            }

            private fun prepareNext(): Quad? {
                while (true) {
                    val token = parent.source.peek()
                    val child = this.child
                    when {
                        child != null -> {
                            if (child.hasNext()) {
                                return child.next()
                            }
                            // the inner structure has finished, emitting its name as the next quad
                            val result = Quad(s = name, p = p!!, o = child.name)
                            // and cleaning up
                            this.child = null
                            // with the object position terminated, we have to check the next token
                            if (parent.source.peek() == TurtleToken.Structural.ObjectTermination) {
                                parent.source.consume()
                            } else if (parent.source.peek() == TurtleToken.Structural.PredicateTermination) {
                                p = null
                                parent.source.consume()
                            }
                            return result
                        }

                        token == TurtleToken.EOF -> unexpectedToken(TurtleToken.EOF)

                        token == TurtleToken.Structural.BlankEnd -> {
                            parent.source.consume()
                            return null
                        }

                        p == null -> {
                            p = when (token) {
                                is TurtleToken.TermToken -> {
                                    parent.resolve(token).into()
                                }

                                TurtleToken.Keyword.TypePredicate -> {
                                    RDF.type
                                }

                                else -> unexpectedToken(token)
                            }
                            parent.source.consume()
                        }

                        token == TurtleToken.Structural.BlankStart -> {
                            this.child = BlankNode(parent)
                        }

                        token == TurtleToken.Structural.ListStart -> {
                            this.child = List(parent)
                        }

                        token is TurtleToken.TermToken -> {
                            val o = parent.resolve(token)
                            val result = Quad(s = name, p = p!!, o = o)
                            // consuming token `o`
                            parent.source.consume()
                            when (parent.source.peek()) {
                                TurtleToken.Structural.BlankEnd -> {
                                    // not consuming this token, next iteration will yield null
                                }

                                TurtleToken.Structural.ObjectTermination -> {
                                    parent.source.consume()
                                }

                                TurtleToken.Structural.PredicateTermination -> {
                                    parent.source.consume()
                                    p = null
                                }

                                else -> unexpectedToken(parent.source.peek())
                            }
                            return result
                        }

                        else -> unexpectedToken(token)
                    }
                }
            }

        }

        class RegularList(private val parent: Deserializer) : Structure() {

            sealed interface ListItem {
                @JvmInline
                value class StructureItem(val structure: Structure) : ListItem

                @JvmInline
                value class SimpleItem(val item: Quad.Term) : ListItem
            }

            override val name = Quad.BlankTerm(id = parent.blankTermCount++)

            // the currently consumed value, made nullable to indicate when it's finished
            private var currentValue: ListItem? = nextItemValue()

            private var currentNode: Quad.BlankTerm? = name

            override fun hasNext(): Boolean {
                return currentNode != null
            }

            override fun next(): Quad {
                val currentNode = currentNode ?: throw NoSuchElementException()
                // first ensuring, if a structural child, the child is complete
                (currentValue as? ListItem.StructureItem)?.let {
                    if (it.structure.hasNext()) {
                        return it.structure.next()
                    }
                }
                // then ensuring there's a link between the child's value and its associated list node
                when (val current = currentValue.also { currentValue = null }) {
                    is ListItem.SimpleItem -> {
                        return Quad(currentNode, RDF.first, current.item)
                    }

                    is ListItem.StructureItem -> {
                        return Quad(currentNode, RDF.first, current.structure.name)
                    }

                    null -> {
                        // value is already emitted, continuing to the next block
                    }
                }
                // then ensuring we emitted the link to the next node
                if (parent.source.peek() == TurtleToken.Structural.ListEnd) {
                    parent.source.consume()
                    // indicating we're finished here
                    this.currentNode = null
                    return Quad(currentNode, RDF.rest, RDF.nil)
                }
                val nextNode = Quad.BlankTerm(id = parent.blankTermCount++)
                // already updating the local value, which will be consumed in subsequent iterations
                this.currentValue = nextItemValue()
                this.currentNode = nextNode
                return Quad(currentNode, RDF.rest, nextNode)
            }

            private fun nextItemValue(): ListItem {
                return when (val current = parent.source.peek()) {
                    TurtleToken.Structural.ListStart -> {
                        ListItem.StructureItem(List(parent))
                    }

                    TurtleToken.Structural.BlankStart -> {
                        ListItem.StructureItem(BlankNode(parent))
                    }

                    is TurtleToken.TermToken -> {
                        ListItem.SimpleItem(item = parent.resolve(current)).also { parent.source.consume() }
                    }

                    else -> unexpectedToken(current)
                }
            }

        }

        data object EmptyList : Structure() {

            override val name: Quad.Term
                get() = RDF.nil

            override fun hasNext(): Boolean = false

            override fun next(): Quad {
                throw NoSuchElementException()
            }

        }

        companion object {

            fun List(parent: Deserializer): Structure {
                check(parent.source.peek() == TurtleToken.Structural.ListStart) {
                    "Expected ${TurtleToken.Structural.ListStart}, got ${parent.source.peek()}"
                }
                parent.source.consume()
                return if (parent.source.peek() == TurtleToken.Structural.ListEnd) {
                    parent.source.consume()
                    EmptyList
                } else {
                    RegularList(parent)
                }
            }

        }

    }

    /* state/input logic */

    private val source = TokenBuffer(source)

    private val prefixes = mutableMapOf<String /* prefix */, String /* uri */>()
    private val namedBlankNodes = mutableMapOf<String /* serialized label */, Quad.BlankTerm>()
    private var blankTermCount = 0
    private var base = Base(base)
    private var child: Structure? = null
    private var s: Quad.Term? = null
    private var p: Quad.NamedTerm? = null

    /* iterator/output logic */

    private var next: Quad? = null

    override fun hasNext(): Boolean {
        if (next != null) {
            return true
        }
        next = prepareNext()
        return next != null
    }

    override fun next(): Quad {
        val result = next ?: prepareNext() ?: throw NoSuchElementException("No quads available!")
        next = null
        return result
    }

    private fun prepareNext(): Quad? {
        while (true) {
            val child = child
            val token = source.peek()
            when {
                child != null -> {
                    if (child.hasNext()) {
                        return child.next()
                    }
                    // clearing out the child as it's finished
                    this.child = null
                    // using the child from this iteration one last time - to reference its name in the appropriate
                    //  quad
                    when {
                        s == null -> {
                            val next = source.peek()
                            if (next == TurtleToken.Structural.StatementTermination || next == TurtleToken.EOF) {
                                // state was already cleared
                                source.consume()
                            } else {
                                // subsequent terms use this blank node as a subject
                                s = child.name
                            }
                        }

                        else -> {
                            return onObjectElement(o = child.name)
                        }
                    }
                }

                token == TurtleToken.EOF -> return null

                token == TurtleToken.Structural.BlankStart -> {
                    this.child = Structure.BlankNode(parent = this)
                }

                token == TurtleToken.Structural.ListStart -> {
                    this.child = Structure.List(parent = this)
                }

                s == null -> {
                    if (source.peek().isBaseOrPrefixDeclaration()) {
                        processPrefix()
                        continue
                    }

                    s = resolve(source.peek().into())
                    source.consume()
                }

                p == null -> {
                    p = if (source.peek() == TurtleToken.Keyword.TypePredicate) {
                        RDF.type
                    } else {
                        resolve(source.peek().into()).into()
                    }
                    source.consume()
                }

                else -> {
                    val o = when (token) {
                        is TurtleToken.TermToken -> {
                            resolve(token)
                        }

                        TurtleToken.Keyword.TrueLiteral -> {
                            Quad.Literal(value = "true", type = XSD.boolean)
                        }

                        TurtleToken.Keyword.FalseLiteral -> {
                            Quad.Literal(value = "false", type = XSD.boolean)
                        }

                        else -> unexpectedToken(token)
                    }
                    // the object's token
                    source.consume()
                    return onObjectElement(o)
                }
            }
        }
    }

    private fun onObjectElement(o: Quad.Term): Quad {
        val result = Quad(s = s!!, p = p!!, o = o)
        when (val terminator = source.consume()) {
            TurtleToken.Structural.StatementTermination -> {
                s = null
                p = null
            }

            TurtleToken.Structural.PredicateTermination -> {
                p = null
            }

            TurtleToken.Structural.ObjectTermination, TurtleToken.EOF -> {
                /* nothing to do */
            }

            else -> unexpectedToken(terminator)
        }
        // it's possible for other termination characters to occur now, so repeating in case this happens
        while (true) {
            when (source.peek()) {
                TurtleToken.Structural.StatementTermination -> {
                    source.consume()
                    s = null
                    p = null
                }

                TurtleToken.Structural.PredicateTermination -> {
                    source.consume()
                    p = null
                }

                TurtleToken.Structural.ObjectTermination -> {
                    source.consume()
                }

                // finished
                else -> return result
            }
        }
    }

    private fun TurtleToken.isBaseOrPrefixDeclaration(): Boolean =
        this == TurtleToken.Keyword.BaseAnnotationA || this == TurtleToken.Keyword.BaseAnnotationB ||
        this == TurtleToken.Keyword.PrefixAnnotationA || this == TurtleToken.Keyword.PrefixAnnotationB

    private fun processPrefix() {
        when (val token = source.consume()) {
            TurtleToken.Keyword.BaseAnnotationA,
            TurtleToken.Keyword.BaseAnnotationB -> {
                val uri = source.consume().into<TurtleToken.TermToken>()
                base = base.update(uri)
                if (source.peek() == TurtleToken.Structural.StatementTermination) {
                    source.consume()
                }
            }

            TurtleToken.Keyword.PrefixAnnotationA,
            TurtleToken.Keyword.PrefixAnnotationB -> {
                val prefix = source.consume()
                check(prefix is TurtleToken.PrefixedTerm && prefix.value.isEmpty())
                prefixes[prefix.prefix] = when (val uri = source.consume()) {
                    is TurtleToken.Term -> uri.value

                    is TurtleToken.RelativeTerm -> base.resolve(uri).value

                    else -> unexpectedToken(uri)
                }
                if (source.peek() == TurtleToken.Structural.StatementTermination) {
                    source.consume()
                }
            }

            else -> unexpectedToken(token)
        }
    }

    private fun resolve(term: TurtleToken.TermToken): Quad.Term {
        return when (term) {
            is TurtleToken.LiteralTerm -> {
                val type = resolve(term.type) as? Quad.NamedTerm
                    ?: throw IllegalStateException("Invalid literal type `${term.type}` in token $term")
                Quad.Literal(value = term.value, type = type)
            }

            is TurtleToken.LocalizedLiteralTerm -> {
                // FIXME
                Quad.Literal(value = term.value, type = RDF.langString)
            }

            is TurtleToken.PrefixedTerm -> {
                if (term.prefix == "_") {
                    namedBlankNodes.getOrPut(term.value) { Quad.BlankTerm(id = blankTermCount++) }
                } else {
                    val uri = prefixes[term.prefix]
                        ?: throw IllegalStateException("Unknown prefix `${term.prefix}` in token $term")
                    Quad.NamedTerm(value = "$uri${term.value}")
                }
            }

            is TurtleToken.RelativeTerm -> base.resolve(term)
            is TurtleToken.Term -> Quad.NamedTerm(value = term.value)
        }
    }

    internal companion object {

        fun unexpectedToken(token: TurtleToken?): Nothing {
            if (token == null) {
                throw IllegalStateException("Unexpected end of input")
            }
            throw IllegalStateException("Unexpected token $token")
        }

        inline fun <reified T> Any.into(): T {
            if (this !is T) {
                throw IllegalStateException("Unexpected token $this, expected ${T::class.simpleName}")
            }
            return this
        }

    }

}
