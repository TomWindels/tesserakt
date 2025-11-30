package dev.tesserakt.rdf.trig.serialization

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import kotlin.jvm.JvmInline


internal class TriGDeserializer(
    source: Iterator<TriGToken>,
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

        fun update(new: TriGToken.TermToken): Base {
            return when (new) {
                is TriGToken.RelativeTerm -> {
                    Base(full = resolve(new).value)
                }

                is TriGToken.Term -> {
                    Base(full = new.value)
                }

                else -> throw IllegalArgumentException("Invalid base declaration: $new")
            }
        }

        fun resolve(term: TriGToken.RelativeTerm): Quad.NamedTerm {
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

                // preventing `<term>` w/o a base to be converted into `</term>`
                path == "/" -> term.value

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
        abstract val name: Quad.Subject // all subjects are valid objects, but not all objects are valid subjects

        class BlankNode(
            private val parent: TriGDeserializer,
        ) : Structure() {

            override val name = Quad.BlankTerm(id = parent.blankTermCount++)
            private var p: Quad.NamedTerm? = null
            private var child: Structure? = null
            private var next: Quad? = null

            init {
                check(parent.source.peek() == TriGToken.Structural.BlankStart) {
                    "Expected ${TriGToken.Structural.BlankStart}, got ${parent.source.peek()}"
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
                            val result = Quad(s = name, p = p!!, o = child.name as Quad.Object, g = parent.g)
                            // and cleaning up
                            this.child = null
                            // with the object position terminated, we have to check the next token
                            if (parent.source.peek() == TriGToken.Structural.ObjectTermination) {
                                parent.source.consume()
                            } else if (parent.source.peek() == TriGToken.Structural.PredicateTermination) {
                                p = null
                                parent.source.consume()
                            }
                            return result
                        }

                        token == TriGToken.EOF -> unexpectedToken(TriGToken.EOF)

                        token == TriGToken.Structural.BlankEnd -> {
                            parent.source.consume()
                            return null
                        }

                        p == null -> {
                            p = when (token) {
                                is TriGToken.TermToken -> {
                                    parent.resolve(token).into()
                                }

                                TriGToken.Keyword.TypePredicate -> {
                                    RDF.type
                                }

                                else -> unexpectedToken(token)
                            }
                            parent.source.consume()
                        }

                        token == TriGToken.Structural.BlankStart -> {
                            this.child = BlankNode(parent)
                        }

                        token == TriGToken.Structural.ListStart -> {
                            this.child = List(parent)
                        }

                        token is TriGToken.TermToken -> {
                            val o = parent.resolve(token) as Quad.Object
                            val result = Quad(s = name, p = p!!, o = o, g = parent.g)
                            // consuming token `o`
                            parent.source.consume()
                            when (parent.source.peek()) {
                                TriGToken.Structural.BlankEnd -> {
                                    // not consuming this token, next iteration will yield null
                                }

                                TriGToken.Structural.ObjectTermination -> {
                                    parent.source.consume()
                                }

                                TriGToken.Structural.PredicateTermination -> {
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

        class RegularList(private val parent: TriGDeserializer) : Structure() {

            sealed interface ListItem {
                @JvmInline
                value class StructureItem(val structure: Structure) : ListItem

                @JvmInline
                value class SimpleItem(val item: Quad.Object) : ListItem
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
                        return Quad(currentNode, RDF.first, current.item, g = parent.g)
                    }

                    is ListItem.StructureItem -> {
                        return Quad(currentNode, RDF.first, current.structure.name as Quad.Object, g = parent.g)
                    }

                    null -> {
                        // value is already emitted, continuing to the next block
                    }
                }
                // then ensuring we emitted the link to the next node
                if (parent.source.peek() == TriGToken.Structural.ListEnd) {
                    parent.source.consume()
                    // indicating we're finished here
                    this.currentNode = null
                    return Quad(currentNode, RDF.rest, RDF.nil, g = parent.g)
                }
                val nextNode = Quad.BlankTerm(id = parent.blankTermCount++)
                // already updating the local value, which will be consumed in subsequent iterations
                this.currentValue = nextItemValue()
                this.currentNode = nextNode
                return Quad(currentNode, RDF.rest, nextNode, g = parent.g)
            }

            private fun nextItemValue(): ListItem {
                return when (val current = parent.source.peek()) {
                    TriGToken.Structural.ListStart -> {
                        ListItem.StructureItem(List(parent))
                    }

                    TriGToken.Structural.BlankStart -> {
                        ListItem.StructureItem(BlankNode(parent))
                    }

                    is TriGToken.TermToken -> {
                        ListItem.SimpleItem(item = parent.resolve(current) as Quad.Object).also { parent.source.consume() }
                    }

                    else -> unexpectedToken(current)
                }
            }

        }

        data object EmptyList : Structure() {

            override val name: Quad.Subject // all subjects are valid objects, but not all objects are valid subjects
                get() = RDF.nil

            override fun hasNext(): Boolean = false

            override fun next(): Quad {
                throw NoSuchElementException()
            }

        }

        companion object {

            fun List(parent: TriGDeserializer): Structure {
                check(parent.source.peek() == TriGToken.Structural.ListStart) {
                    "Expected ${TriGToken.Structural.ListStart}, got ${parent.source.peek()}"
                }
                parent.source.consume()
                return if (parent.source.peek() == TriGToken.Structural.ListEnd) {
                    parent.source.consume()
                    EmptyList
                } else {
                    RegularList(parent)
                }
            }

        }

    }

    /* state/input logic */

    private val source = TriGTokenBuffer(source)

    private val prefixes = mutableMapOf<String /* prefix */, String /* uri */>()
    private val namedBlankNodes = mutableMapOf<String /* serialized label */, Quad.BlankTerm>()
    private var blankTermCount = 0
    private var base = Base(base)
    private var child: Structure? = null
    private var s: Quad.Subject? = null
    private var p: Quad.Predicate? = null
    internal var g: Quad.Graph = Quad.DefaultGraph
        private set
    // specifically keeping track of whether we're in a graph block, as the current graph being the default graph
    //  can also be done syntactically (e.g. `{ <s> <p> <o> }`)
    private var inGraphBlock = false

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
                        !inGraphBlock && source.peek() == TriGToken.Structural.GraphStatementStart -> {
                            source.consume()
                            g = child.name.into()
                            inGraphBlock = true
                        }

                        s == null -> {
                            val next = source.peek()
                            if (next == TriGToken.Structural.StatementTermination || next == TriGToken.EOF) {
                                // state was already cleared
                                source.consume()
                            } else {
                                // subsequent terms use this blank node as a subject
                                s = child.name
                            }
                        }

                        else -> {
                            return onObjectElement(o = child.name as Quad.Object)
                        }
                    }
                }

                token == TriGToken.EOF -> return null

                token == TriGToken.Structural.BlankStart -> {
                    this.child = Structure.BlankNode(parent = this)
                }

                token == TriGToken.Structural.ListStart -> {
                    this.child = Structure.List(parent = this)
                }

                token == TriGToken.Structural.GraphStatementStart -> {
                    if (inGraphBlock) {
                        unexpectedToken(token)
                    }
                    source.consume()
                    inGraphBlock = true
                    // the active subject, if any, becomes the graph
                    g = s?.into() ?: Quad.DefaultGraph
                    s = null
                }

                token == TriGToken.Keyword.GraphAnnotation -> {
                    source.consume()
                    g = resolve(source.consume().into()).into()
                    check(source.consume() == TriGToken.Structural.GraphStatementStart)
                    inGraphBlock = true
                }

                token == TriGToken.Structural.GraphStatementEnd -> {
                    if (!inGraphBlock) {
                        unexpectedToken(token)
                    }
                    source.consume()
                    // resetting the rest of the state too
                    g = Quad.DefaultGraph
                    inGraphBlock = false
                    s = null
                    p = null
                }

                s == null -> {
                    if (source.peek().isBaseOrPrefixDeclaration()) {
                        check(!inGraphBlock)
                        processPrefix()
                        continue
                    }

                    s = resolve(source.consume().into()) as Quad.Subject
                }

                p == null -> {
                    p = if (source.peek() == TriGToken.Keyword.TypePredicate) {
                        RDF.type
                    } else {
                        resolve(source.peek().into()).into()
                    }
                    source.consume()
                }

                else -> {
                    val o = when (token) {
                        is TriGToken.TermToken -> {
                            resolve(token)
                        }

                        TriGToken.Keyword.TrueLiteral -> {
                            Quad.Literal(value = "true", type = XSD.boolean)
                        }

                        TriGToken.Keyword.FalseLiteral -> {
                            Quad.Literal(value = "false", type = XSD.boolean)
                        }

                        else -> unexpectedToken(token)
                    } as Quad.Object
                    // the object's token
                    source.consume()
                    return onObjectElement(o)
                }
            }
        }
    }

    private fun onObjectElement(o: Quad.Object): Quad {
        val result = Quad(s = s!!, p = p!!, o = o, g = g)
        when (val terminator = source.peek()) {
            TriGToken.Structural.StatementTermination -> {
                s = null
                p = null
                source.consume()
            }

            TriGToken.Structural.PredicateTermination -> {
                p = null
                source.consume()
            }

            TriGToken.Structural.ObjectTermination -> {
                source.consume()
            }

            TriGToken.EOF, TriGToken.Structural.GraphStatementEnd -> {
                /* nothing else to do */
                return result
            }

            else -> unexpectedToken(terminator)
        }
        // it's possible for other termination characters to occur now, so repeating in case this happens
        while (true) {
            when (source.peek()) {
                TriGToken.Structural.StatementTermination -> {
                    source.consume()
                    s = null
                    p = null
                }

                TriGToken.Structural.PredicateTermination -> {
                    source.consume()
                    p = null
                }

                TriGToken.Structural.ObjectTermination -> {
                    source.consume()
                }

                // finished
                else -> return result
            }
        }
    }

    private fun TriGToken.isBaseOrPrefixDeclaration(): Boolean =
        this == TriGToken.Keyword.BaseAnnotationA || this == TriGToken.Keyword.BaseAnnotationB ||
        this == TriGToken.Keyword.PrefixAnnotationA || this == TriGToken.Keyword.PrefixAnnotationB

    private fun processPrefix() {
        when (val token = source.consume()) {
            TriGToken.Keyword.BaseAnnotationA,
            TriGToken.Keyword.BaseAnnotationB -> {
                val uri = source.consume().into<TriGToken.TermToken>()
                base = base.update(uri)
                if (source.peek() == TriGToken.Structural.StatementTermination) {
                    source.consume()
                }
            }

            TriGToken.Keyword.PrefixAnnotationA,
            TriGToken.Keyword.PrefixAnnotationB -> {
                val prefix = source.consume()
                check(prefix is TriGToken.PrefixedTerm && prefix.value.isEmpty())
                prefixes[prefix.prefix] = when (val uri = source.consume()) {
                    is TriGToken.Term -> uri.value

                    is TriGToken.RelativeTerm -> base.resolve(uri).value

                    else -> unexpectedToken(uri)
                }
                if (source.peek() == TriGToken.Structural.StatementTermination) {
                    source.consume()
                }
            }

            else -> unexpectedToken(token)
        }
    }

    private fun resolve(term: TriGToken.TermToken): Quad.Element {
        return when (term) {
            is TriGToken.LiteralTerm -> {
                val type = resolve(term.type) as? Quad.NamedTerm
                    ?: throw IllegalStateException("Invalid literal type `${term.type}` in token $term")
                Quad.Literal(value = term.value, type = type)
            }

            is TriGToken.LocalizedLiteralTerm -> {
                Quad.LangString(value = term.value, language = term.language)
            }

            is TriGToken.PrefixedTerm -> {
                if (term.prefix == "_") {
                    namedBlankNodes.getOrPut(term.value) { Quad.BlankTerm(id = blankTermCount++) }
                } else {
                    val uri = prefixes[term.prefix]
                        ?: throw IllegalStateException("Unknown prefix `${term.prefix}` in token $term")
                    Quad.NamedTerm(value = "$uri${term.value}")
                }
            }

            is TriGToken.RelativeTerm -> base.resolve(term)
            is TriGToken.Term -> Quad.NamedTerm(value = term.value)
        }
    }

    internal companion object {

        fun unexpectedToken(token: TriGToken?): Nothing {
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
